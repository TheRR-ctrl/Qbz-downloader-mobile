package com.qbdlx.mobile.ipod

import java.security.SecureRandom
import java.util.Random

/**
 * Editing the iPod nano 6G/7G library: the SQLite databases in `iPod_Control/iTunes/iTunes Library.itlp`
 * (Library.itdb, Locations.itdb, Dynamic.itdb, Extras.itdb). Adding a track = inserting rows into all the tables that
 * describe it; a row of an existing track is cloned as the template so that the dozens of columns nobody needs to set
 * keep sensible values. Follows ipodsync's `library.py` (MIT, https://github.com/buldiei/ipodsync; see THIRD_PARTY.md).
 *
 * After any change to Locations.itdb its signature file must be rebuilt ([Cbk]).
 */
typealias Row = LinkedHashMap<String, Any?>

/** The little SQL access the library needs; Android's SQLiteDatabase implements it on the phone, JDBC in the tests. */
interface Sql {
    fun query(sql: String, args: List<Any?> = emptyList()): List<Row>
    fun exec(sql: String, args: List<Any?> = emptyList())
}

class TrackMeta(
    /** "Fxx/NAME.ext", relative to iPod_Control/Music. */
    val location: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String = "",
    val totalTimeMs: Long = 0,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val bitRate: Int = 0,
    val sampleRate: Int = 44100,
    val audioFormat: Int = MP3_AUDIO_FORMAT,
    val fileSize: Long = 0,
    val extension: Long = MP3_EXTENSION_4CC,
) {
    companion object {
        const val MP3_AUDIO_FORMAT = 301
        const val MP3_EXTENSION_4CC = 1297101600L // 'MP3 '
    }
}

class IpodTrack(val pid: Long, val title: String, val artist: String, val album: String, val trackNumber: Int, val location: String?, val durationMs: Long = 0, val hasLyrics: Boolean = false)

class ItlpLibrary(
    private val lib: Sql,
    private val loc: Sql,
    private val dyn: Sql,
    private val ext: Sql,
    private val random: Random = SecureRandom(),
) {
    private fun randPid(): Long = random.nextLong()

    private fun row(db: Sql, sql: String, args: List<Any?> = emptyList()): Row? = db.query(sql, args).firstOrNull()

    private fun rowOrTemplate(db: Sql, sql: String, template: Map<String, Any?>): Row = row(db, sql) ?: Row(template)

    /** Columns each table really has: an iPod nano 6G's tables lack columns that a template written for the 7G carries. */
    private val columnCache = HashMap<Pair<Sql, String>, Set<String>>()
    private fun columnsOf(db: Sql, table: String): Set<String> = columnCache.getOrPut(db to table) {
        runCatching { db.query("PRAGMA table_info(\"$table\")").mapNotNull { it["name"] as? String }.toSet() }.getOrDefault(emptySet())
    }

    private fun insert(db: Sql, table: String, row: Map<String, Any?>) {
        val have = columnsOf(db, table)
        val use = if (have.isEmpty()) row else row.filterKeys { it in have }
        val cols = use.keys.joinToString(",") { "\"$it\"" }
        val placeholders = use.keys.joinToString(",") { "?" }
        db.exec("INSERT INTO \"$table\" ($cols) VALUES ($placeholders)", use.values.toList())
    }

    private fun maxOrder(db: Sql, table: String, col: String): Long =
        (db.query("SELECT COALESCE(MAX(\"$col\"),0) AS m FROM \"$table\"").first()["m"] as Number?)?.toLong() ?: 0L

    private fun lookup(table: String, template: Map<String, Any?>, name: String, nameOrder: Long): Long {
        row(lib, "SELECT pid FROM \"$table\" WHERE name=?", listOf(name))?.let { return (it["pid"] as Number).toLong() }
        val tmpl = rowOrTemplate(lib, "SELECT * FROM \"$table\" LIMIT 1", template)
        val pid = randPid()
        tmpl["pid"] = pid; tmpl["name"] = name; tmpl["sort_name"] = name; tmpl["name_order"] = nameOrder
        if ("artwork_status" in tmpl) tmpl["artwork_status"] = 0L
        if ("artwork_cache_id" in tmpl) tmpl["artwork_cache_id"] = 0L
        if ("artwork_item_pid" in tmpl) tmpl["artwork_item_pid"] = 0L
        if ("artwork_album_pid" in tmpl) tmpl["artwork_album_pid"] = 0L
        insert(lib, table, tmpl)
        return pid
    }

    /** Adds the track to all tables and returns its pid. The caller wraps this in a transaction. */
    fun addTrack(m: TrackMeta, dateMacEpoch: Long): Long {
        if (!artistLinksRepaired) { artistLinksRepaired = true; repairArtistLinks() } // once per library session, not once per song
        val tmplItem = rowOrTemplate(lib, "SELECT * FROM item LIMIT 1", IpodTemplates.item)
        val itemPid = randPid()

        var genreId: Long? = null
        if (m.genre.isNotEmpty()) {
            val g = row(lib, "SELECT id FROM genre_map WHERE genre=?", listOf(m.genre))
            genreId = if (g != null) (g["id"] as Number).toLong() else {
                val id = maxOrder(lib, "genre_map", "id") + 1
                insert(lib, "genre_map", linkedMapOf(
                    "id" to id, "genre" to m.genre, "has_music" to 1L, "artist_count_calc" to 1L,
                    "album_count_calc" to 1L, "album_artist_count_calc" to 1L,
                ))
                id
            }
        }
        val nord = maxOrder(lib, "artist", "name_order") + 100
        val artistPid = lookup("artist", IpodTemplates.artist, m.artist, nord)
        // an artist that already exists keeps its own sort position, so the Artists view groups its songs together
        val artistOrd = (row(lib, "SELECT name_order AS o FROM artist WHERE pid=?", listOf(artistPid))?.get("o") as Number?)?.toLong() ?: nord
        val aord = maxOrder(lib, "album", "name_order") + 100
        val albumPid = lookup("album", IpodTemplates.album, m.album, aord)
        // the album row is cloned from a template that carries another artist's pid: point it at this song's artist
        val albumCols = columnsOf(lib, "album")
        if ("artist_pid" in albumCols) lib.exec("UPDATE album SET artist_pid=? WHERE pid=?", listOf(artistPid, albumPid))
        if ("artist_order" in albumCols) lib.exec("UPDATE album SET artist_order=? WHERE pid=?", listOf(artistOrd, albumPid))

        // track_artist uses small sequential pids
        val ta = row(lib, "SELECT pid FROM track_artist WHERE name=?", listOf(m.artist))
        val trackArtistPid = if (ta != null) (ta["pid"] as Number).toLong() else maxOrder(lib, "track_artist", "pid") + 1
        if (ta == null) {
            val t = rowOrTemplate(lib, "SELECT * FROM track_artist LIMIT 1", IpodTemplates.trackArtist)
            t["pid"] = trackArtistPid; t["name"] = m.artist; t["sort_name"] = m.artist; t["name_order"] = nord
            insert(lib, "track_artist", t)
        }

        val end = maxOrder(lib, "item", "title_order") + 100
        tmplItem.putAll(linkedMapOf<String, Any?>(
            "pid" to itemPid, "title" to m.title, "artist" to m.artist, "album" to m.album,
            "sort_title" to m.title, "sort_artist" to m.artist, "sort_album" to m.album,
            "year" to m.year.toLong(), "total_time_ms" to m.totalTimeMs, "track_number" to m.trackNumber.toLong(),
            "genre_id" to genreId, "album_pid" to albumPid, "artist_pid" to artistPid,
            "track_artist_pid" to trackArtistPid, "date_modified" to dateMacEpoch,
            // A cloned template may come from a song with artwork. Never inherit another song's cover reference.
            "artwork_status" to 0L, "artwork_cache_id" to 0L, "has_lyrics" to 0L, // likewise: never inherit the template song's lyrics flag
            "title_order" to end, "album_order" to end, "artist_order" to artistOrd,
            "album_artist_order" to artistOrd, "album_by_artist_order" to artistOrd,
        ))
        insert(lib, "item", tmplItem)
        refreshCounts(artistPid, albumPid)

        insert(lib, "avformat_info", linkedMapOf(
            "item_pid" to itemPid, "audio_format" to m.audioFormat.toLong(), "bit_rate" to m.bitRate.toLong(), "sample_rate" to m.sampleRate.toLong(),
        ))
        val master = (row(lib, "SELECT primary_container_pid AS p FROM db_info")?.get("p") as Number?)?.toLong()
            ?: error("db_info has no master playlist")
        val phys = maxOrder(lib, "item_to_container", "physical_order") + 1
        insert(lib, "item_to_container", linkedMapOf("item_pid" to itemPid, "container_pid" to master, "physical_order" to phys))
        lib.exec("UPDATE track_size_calc SET size=COALESCE(size,0)+? WHERE kind='audio'", listOf(m.fileSize))

        val tmplLoc = rowOrTemplate(loc, "SELECT * FROM location LIMIT 1", IpodTemplates.location)
        tmplLoc["item_pid"] = itemPid; tmplLoc["location"] = m.location; tmplLoc["file_size"] = m.fileSize
        tmplLoc["extension"] = m.extension; tmplLoc["date_created"] = dateMacEpoch
        insert(loc, "location", tmplLoc)

        // item_stats (Dynamic) and chapter (Extras): clone a row onto the new item when the table has one
        for ((db, table) in listOf(dyn to "item_stats", ext to "chapter")) {
            val t = row(db, "SELECT * FROM $table LIMIT 1") ?: continue
            t["item_pid"] = itemPid
            insert(db, table, t)
        }
        return itemPid
    }

    private var artistLinksRepaired = false

    /** Points every album at the artist of its songs and recomputes the counters, fixing albums added before this was done. */
    fun repairArtistLinks() {
        val albumCols = columnsOf(lib, "album")
        if ("artist_pid" in albumCols) runCatching {
            lib.exec("UPDATE album SET artist_pid=(SELECT i.artist_pid FROM item i WHERE i.album_pid=album.pid AND i.artist_pid IS NOT NULL LIMIT 1) " +
                "WHERE EXISTS (SELECT 1 FROM item i WHERE i.album_pid=album.pid AND i.artist_pid IS NOT NULL) " +
                "AND (artist_pid IS NULL OR artist_pid NOT IN (SELECT pid FROM artist))")
        }
        if ("item_count" in albumCols) runCatching { lib.exec("UPDATE album SET item_count=(SELECT COUNT(*) FROM item WHERE item.album_pid=album.pid)") }
        if ("album_count" in columnsOf(lib, "artist")) runCatching {
            lib.exec("UPDATE artist SET album_count=(SELECT COUNT(DISTINCT album_pid) FROM item WHERE item.artist_pid=artist.pid)")
        }
    }

    private fun refreshCounts(artistPid: Long, albumPid: Long) {
        if ("item_count" in columnsOf(lib, "album"))
            lib.exec("UPDATE album SET item_count=(SELECT COUNT(*) FROM item WHERE album_pid=?) WHERE pid=?", listOf(albumPid, albumPid))
        if ("album_count" in columnsOf(lib, "artist"))
            lib.exec("UPDATE artist SET album_count=(SELECT COUNT(DISTINCT album_pid) FROM item WHERE artist_pid=?) WHERE pid=?", listOf(artistPid, artistPid))
    }

    /** Table layouts and sample rows, for diagnosing a device (no personal data beyond song titles). */
    fun report(): String {
        val out = StringBuilder()
        val tables = lib.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").mapNotNull { it["name"] as? String }
        for (t in tables) {
            val cols = lib.query("PRAGMA table_info(\"$t\")").joinToString(",") { it["name"].toString() }
            val n = runCatching { lib.query("SELECT COUNT(*) AS n FROM \"$t\"").first()["n"] }.getOrNull()
            out.appendLine("table $t ($n rows): $cols")
        }
        for (t in tables.filter { it.contains("artwork", true) || it.contains("image", true) }) {
            out.appendLine("\n-- first rows of $t")
            runCatching { lib.query("SELECT * FROM \"$t\" LIMIT 3").forEach { out.appendLine(it.toString()) } }
        }
        runCatching {
            out.appendLine("\n-- artwork_status counts"); lib.query("SELECT artwork_status, COUNT(*) AS n FROM item GROUP BY artwork_status").forEach { out.appendLine(it.toString()) }
            out.appendLine("\n-- last 2 added items (highest physical_order)"); lib.query("SELECT * FROM item ORDER BY physical_order DESC LIMIT 2").forEach { out.appendLine(it.toString()) }
            out.appendLine("\n-- 2 items with artwork (artwork_status=1)"); val withArt = lib.query("SELECT * FROM item WHERE artwork_status=1 ORDER BY physical_order DESC LIMIT 2")
            withArt.forEach { out.appendLine(it.toString()) }
            withArt.firstOrNull()?.let { r ->
                out.appendLine("\n-- its album row"); lib.query("SELECT * FROM album WHERE pid=${(r["album_pid"] as Number).toLong()}").forEach { out.appendLine(it.toString()) }
                out.appendLine("\n-- its artist row"); lib.query("SELECT * FROM artist WHERE pid=${(r["artist_pid"] as Number).toLong()}").forEach { out.appendLine(it.toString()) }
            }
            out.appendLine("\n-- album rows of the last added item"); lib.query("SELECT * FROM album WHERE pid IN (SELECT album_pid FROM item ORDER BY physical_order DESC LIMIT 1)").forEach { out.appendLine(it.toString()) }
        }
        return out.toString()
    }

    fun listTracks(): List<IpodTrack> {
        val locations = loc.query("SELECT item_pid, location FROM location").associate { (it["item_pid"] as Number).toLong() to it["location"] as String? }
        return lib.query("SELECT pid, title, artist, album, track_number, total_time_ms, has_lyrics FROM item ORDER BY artist, album, track_number").map {
            val pid = (it["pid"] as Number).toLong()
            IpodTrack(pid, it["title"] as String? ?: "", it["artist"] as String? ?: "", it["album"] as String? ?: "",
                ((it["track_number"] as Number?)?.toInt() ?: 0), locations[pid], (it["total_time_ms"] as Number?)?.toLong() ?: 0L, ((it["has_lyrics"] as Number?)?.toLong() ?: 0L) != 0L)
        }
    }

    /** The ArtworkDB image id of a song of this album and artist that already has a cover on the iPod, if any (so the cover is not added twice). */
    fun artworkIdFor(album: String, artist: String): Int? =
        (row(lib, "SELECT artwork_cache_id AS id FROM item WHERE album=? AND artist=? AND artwork_status=1 AND artwork_cache_id>0 LIMIT 1", listOf(album, artist))?.get("id") as Number?)?.toInt()

    /** Points the song at the ArtworkDB image [cacheId] (item.artwork_status = 1, item.artwork_cache_id = id). False when there is no such song. */
    fun setArtwork(pid: Long, cacheId: Int): Boolean {
        if (row(lib, "SELECT pid FROM item WHERE pid=?", listOf(pid)) == null) return false
        lib.exec("UPDATE item SET artwork_status=1, artwork_cache_id=? WHERE pid=?", listOf(cacheId.toLong(), pid))
        // The Albums view finds a cover through album.artwork_item_pid (a song of the album that has one), as iTunes writes it
        // (ipodsync's set_album_artwork; in a real nano library 212 of 212 albums with a pointer point at a song with artwork).
        lib.exec("UPDATE album SET artwork_item_pid=? WHERE pid=(SELECT album_pid FROM item WHERE pid=?) AND $NO_ALBUM_ART", listOf(pid, pid))
        return true
    }

    /** Points every album that has a song with a cover, but no valid cover pointer, at such a song. Returns how many albums were fixed. */
    fun fixAlbumArtwork(): Int {
        val has = "EXISTS(SELECT 1 FROM item WHERE item.album_pid=album.pid AND item.artwork_status=1)"
        val n = (row(lib, "SELECT COUNT(*) AS n FROM album WHERE $NO_ALBUM_ART AND $has")?.get("n") as Number?)?.toInt() ?: 0
        if (n > 0) lib.exec("UPDATE album SET artwork_item_pid=(SELECT MIN(pid) FROM item WHERE item.album_pid=album.pid AND item.artwork_status=1) WHERE $NO_ALBUM_ART AND $has")
        return n
    }

    private val NO_ALBUM_ART = "(COALESCE(artwork_item_pid,0)=0 OR artwork_item_pid NOT IN (SELECT pid FROM item WHERE artwork_status=1))"

    /** (item pid, artwork_cache_id) of every song that says it has a cover. */
    fun artworkReferences(): List<Pair<Long, Int>> =
        lib.query("SELECT pid, artwork_cache_id AS id FROM item WHERE artwork_status=1").map { (it["pid"] as Number).toLong() to (it["id"] as Number).toInt() }

    /** pid of the user playlist called [name] (not the master list, not a smart or built-in one), if there is one. */
    /** The user's own playlists on the iPod (no smart or system lists) with the pids of their songs, in order. */
    fun listPlaylists(): List<IpodPlaylist> {
        val master = (row(lib, "SELECT primary_container_pid AS p FROM db_info")?.get("p") as Number?)?.toLong() ?: 0L
        val items = lib.query("SELECT container_pid, item_pid FROM item_to_container ORDER BY container_pid, physical_order")
            .groupBy({ (it["container_pid"] as Number).toLong() }, { (it["item_pid"] as Number).toLong() })
        return lib.query("SELECT pid, name FROM container WHERE COALESCE(distinguished_kind,0)=0 AND COALESCE(smart_is_dynamic,0)=0 ORDER BY name_order")
            .map { (it["pid"] as Number).toLong() to (it["name"] as String? ?: "") }
            .filter { it.first != master }
            .map { (pid, name) -> IpodPlaylist(pid, name, items[pid].orEmpty()) }
    }

    /** Removes a user playlist (the list only; its songs stay in the library). */
    fun deletePlaylist(pid: Long) {
        lib.exec("DELETE FROM item_to_container WHERE container_pid=?", listOf(pid))
        lib.exec("DELETE FROM container WHERE pid=?", listOf(pid))
        runCatching { dyn.exec("DELETE FROM container_ui WHERE container_pid=?", listOf(pid)) }
    }

    fun userPlaylistPid(name: String): Long? =
        (row(lib, "SELECT pid FROM container WHERE name=? AND COALESCE(distinguished_kind,0)=0 AND COALESCE(smart_is_dynamic,0)=0 AND pid<>COALESCE((SELECT primary_container_pid FROM db_info),0) LIMIT 1", listOf(name))?.get("pid") as Number?)?.toLong()

    /**
     * Makes (or refills) the user playlist [name] with these songs, in this order, and returns its pid. Follows ipodsync's playlist
     * code (MIT, confirmed on a nano 7G): a row in `container`, one `item_to_container` row per song, and a `container_ui` row in
     * Dynamic.itdb. The container row is built from scratch (no smart-playlist fields), because a real nano may have no user playlist to copy.
     */
    fun setPlaylist(name: String, itemPids: List<Long>, dateMacEpoch: Long): Long {
        var cpid = userPlaylistPid(name)
        if (cpid == null) {
            cpid = randPid()
            insert(lib, "container", linkedMapOf(
                "pid" to cpid, "distinguished_kind" to 0L, "date_created" to dateMacEpoch, "date_modified" to dateMacEpoch,
                "name" to name, "name_order" to maxOrder(lib, "container", "name_order") + 100, "parent_pid" to 0L,
                "media_kinds" to 1L, "is_hidden" to 0L,
            ))
            val master = (row(lib, "SELECT primary_container_pid AS p FROM db_info")?.get("p") as Number?)?.toLong()
            val ui = row(dyn, "SELECT * FROM container_ui WHERE container_pid=?", listOf(master)) ?: row(dyn, "SELECT * FROM container_ui LIMIT 1")
            if (ui != null) { ui["container_pid"] = cpid; insert(dyn, "container_ui", ui) }
            else insert(dyn, "container_ui", linkedMapOf("container_pid" to cpid, "play_order" to 7L, "is_reversed" to 0L, "album_field_order" to 1L)) // what every list of a real nano has
        } else {
            lib.exec("DELETE FROM item_to_container WHERE container_pid=?", listOf(cpid))
            lib.exec("UPDATE container SET date_modified=? WHERE pid=?", listOf(dateMacEpoch, cpid))
        }
        itemPids.distinct().forEachIndexed { i, pid ->
            insert(lib, "item_to_container", linkedMapOf("item_pid" to pid, "container_pid" to cpid, "physical_order" to i.toLong()))
        }
        return cpid
    }

    /** The song's file now has [newSize] bytes (its tag changed): keeps the file size and the audio total of the library right. */
    fun updateFileSize(pid: Long, newSize: Long) {
        val old = (row(loc, "SELECT file_size FROM location WHERE item_pid=?", listOf(pid))?.get("file_size") as Number?)?.toLong() ?: return
        loc.exec("UPDATE location SET file_size=? WHERE item_pid=?", listOf(newSize, pid))
        lib.exec("UPDATE track_size_calc SET size=MAX(0,COALESCE(size,0)+?) WHERE kind='audio'", listOf(newSize - old))
    }

    /**
     * Tells the iPod whether the song's file carries lyrics (item.has_lyrics, plus the row iTunes keeps in Extras.itdb `lyrics`).
     * The text itself stays in the file's ID3 tag. In a real nano library every song with has_lyrics=1 has such a row, with the text
     * empty and a checksum whose meaning is unknown: new rows leave the checksum empty rather than invent one.
     */
    fun setLyrics(pid: Long, has: Boolean) {
        lib.exec("UPDATE item SET has_lyrics=? WHERE pid=?", listOf(if (has) 1L else 0L, pid))
        if (columnsOf(ext, "lyrics").isEmpty()) return
        ext.exec("DELETE FROM lyrics WHERE item_pid=?", listOf(pid))
        if (has) insert(ext, "lyrics", linkedMapOf("item_pid" to pid, "checksum" to null, "lyrics" to null))
    }

    /** Removes the track from every table and returns its file location (the caller deletes the file). */
    fun removeTrack(pid: Long): String? {
        val r = row(loc, "SELECT location, file_size FROM location WHERE item_pid=?", listOf(pid))
        val location = r?.get("location") as String?
        val size = (r?.get("file_size") as Number?)?.toLong() ?: 0L
        lib.exec("DELETE FROM item WHERE pid=?", listOf(pid))
        lib.exec("DELETE FROM avformat_info WHERE item_pid=?", listOf(pid))
        lib.exec("DELETE FROM item_to_container WHERE item_pid=?", listOf(pid))
        lib.exec("UPDATE track_size_calc SET size=MAX(0,COALESCE(size,0)-?) WHERE kind='audio'", listOf(size))
        loc.exec("DELETE FROM location WHERE item_pid=?", listOf(pid))
        dyn.exec("DELETE FROM item_stats WHERE item_pid=?", listOf(pid))
        ext.exec("DELETE FROM chapter WHERE item_pid=?", listOf(pid))
        if (columnsOf(ext, "lyrics").isNotEmpty()) ext.exec("DELETE FROM lyrics WHERE item_pid=?", listOf(pid))
        gcOrphanLookups()
        fixAlbumArtwork() // an album whose cover song was removed gets another one
        return location
    }

    /** Deletes albums, artists, composers and genres that no track uses any more (they would show up empty). */
    fun gcOrphanLookups() {
        for ((table, key, ref) in LOOKUPS) {
            runCatching { lib.exec("DELETE FROM \"$table\" WHERE \"$key\" NOT IN (SELECT \"$ref\" FROM item WHERE \"$ref\" IS NOT NULL)") }
        }
    }

    private companion object {
        val LOOKUPS = listOf(
            Triple("album", "pid", "album_pid"), Triple("artist", "pid", "artist_pid"), Triple("composer", "pid", "composer_pid"),
            Triple("track_artist", "pid", "track_artist_pid"), Triple("genre_map", "id", "genre_id"),
        )
    }
}

/** A playlist stored on the iPod: its name and the pids of its songs. */
class IpodPlaylist(val pid: Long, val name: String, val trackPids: List<Long>)
