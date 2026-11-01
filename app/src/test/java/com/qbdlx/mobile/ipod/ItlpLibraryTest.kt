package com.qbdlx.mobile.ipod

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.util.Random

/** A real SQLite behind the [Sql] interface. */
class JdbcSql(private val cx: Connection) : Sql {
    override fun query(sql: String, args: List<Any?>): List<Row> = cx.prepareStatement(sql).use { st ->
        args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
        st.executeQuery().use { rs ->
            val md = rs.metaData
            val out = ArrayList<Row>()
            while (rs.next()) out.add(Row().also { r -> for (c in 1..md.columnCount) r[md.getColumnLabel(c)] = rs.getObject(c) })
            out
        }
    }

    override fun exec(sql: String, args: List<Any?>) {
        cx.prepareStatement(sql).use { st -> args.forEachIndexed { i, a -> st.setObject(i + 1, a) }; st.executeUpdate() }
    }
}

/**
 * The tables of an iPod library as far as the code touches them. The real databases have more; the columns of the five big
 * tables come from the template rows, so a column the code sets is always there.
 */
class ItlpLibraryTest {
    private lateinit var cxs: List<Connection>
    private lateinit var lib: JdbcSql; private lateinit var loc: JdbcSql; private lateinit var dyn: JdbcSql; private lateinit var ext: JdbcSql

    private fun create(cx: Connection, table: String, cols: Collection<String>) =
        cx.createStatement().use { it.execute("CREATE TABLE \"$table\" (" + cols.joinToString(",") { c -> "\"$c\"" } + ")") }

    private fun library(withExistingTrack: Boolean): ItlpLibrary {
        cxs = List(4) { DriverManager.getConnection("jdbc:sqlite::memory:") }
        val (l, lo, d, e) = cxs
        create(l, "item", IpodTemplates.item.keys); create(l, "artist", IpodTemplates.artist.keys)
        create(l, "album", IpodTemplates.album.keys); create(l, "track_artist", IpodTemplates.trackArtist.keys)
        create(l, "composer", listOf("pid", "name"))
        create(l, "genre_map", listOf("id", "genre", "has_music", "artist_count_calc", "album_count_calc", "album_artist_count_calc"))
        create(l, "avformat_info", listOf("item_pid", "audio_format", "bit_rate", "sample_rate"))
        create(l, "db_info", listOf("primary_container_pid"))
        create(l, "container", listOf("pid", "distinguished_kind", "date_created", "date_modified", "name", "name_order", "parent_pid", "media_kinds", "is_hidden", "smart_is_dynamic"))
        create(l, "item_to_container", listOf("item_pid", "container_pid", "physical_order"))
        create(l, "track_size_calc", listOf("kind", "size"))
        create(lo, "location", IpodTemplates.location.keys)
        create(d, "item_stats", listOf("item_pid", "play_count_user")); create(d, "container_ui", listOf("container_pid", "play_order")); create(e, "chapter", listOf("item_pid", "data")); create(e, "lyrics", listOf("item_pid", "checksum", "lyrics"))
        lib = JdbcSql(l); loc = JdbcSql(lo); dyn = JdbcSql(d); ext = JdbcSql(e)
        lib.exec("INSERT INTO db_info VALUES (777)")
        lib.exec("INSERT INTO track_size_calc VALUES ('audio', 100)")
        val library = ItlpLibrary(lib, loc, dyn, ext, Random(42))
        if (withExistingTrack) {
            library.addTrack(TrackMeta("F00/AAAA.mp3", "Old", "Old Artist", "Old Album", genre = "Rock", fileSize = 1000), 1)
            dyn.exec("INSERT INTO item_stats VALUES (?, 3)", listOf(lib.query("SELECT pid FROM item").first()["pid"]))
        }
        return library
    }

    @Before fun noop() {}
    @After fun close() { if (this::cxs.isInitialized) cxs.forEach { it.close() } }

    private fun count(db: JdbcSql, table: String) = (db.query("SELECT COUNT(*) AS n FROM \"$table\"").first()["n"] as Number).toInt()

    @Test fun addingATrackFillsEveryTable() {
        val library = library(withExistingTrack = true)
        val pid = library.addTrack(TrackMeta("F07/BBBB.mp3", "Song", "Artist", "Album", genre = "Pop", totalTimeMs = 200_000, trackNumber = 3, year = 2020,
            bitRate = 320, fileSize = 5_000_000), 700_000_000)
        val item = lib.query("SELECT * FROM item WHERE pid=?", listOf(pid)).single()
        assertEquals("Song", item["title"]); assertEquals("Artist", item["artist"]); assertEquals("Album", item["album"])
        assertEquals(200_000L, (item["total_time_ms"] as Number).toLong()); assertEquals(3L, (item["track_number"] as Number).toLong())
        assertEquals("one row per table", 1, lib.query("SELECT * FROM avformat_info WHERE item_pid=?", listOf(pid)).size)
        assertEquals("F07/BBBB.mp3", loc.query("SELECT location FROM location WHERE item_pid=?", listOf(pid)).single()["location"])
        assertEquals(777L, (lib.query("SELECT container_pid AS c FROM item_to_container WHERE item_pid=?", listOf(pid)).single()["c"] as Number).toLong())
        assertEquals(1, dyn.query("SELECT * FROM item_stats WHERE item_pid=?", listOf(pid)).size)
        assertEquals("size counts the new file", 100L + 1000 + 5_000_000, (lib.query("SELECT size FROM track_size_calc").single()["size"] as Number).toLong())
        assertEquals(2, library.listTracks().size)
    }

    @Test fun lyricsFlagAndRowFollowTheSongAndNewSongsDoNotInheritIt() {
        val library = library(withExistingTrack = true)
        lib.exec("UPDATE item SET has_lyrics=1 WHERE title='Old'") // the template song has lyrics
        val pid = library.addTrack(TrackMeta("F07/CCCC.mp3", "Song", "Artist", "Album"), 1)
        assertEquals("not inherited", 0L, (lib.query("SELECT has_lyrics FROM item WHERE pid=?", listOf(pid)).single()["has_lyrics"] as Number).toLong())
        library.setLyrics(pid, true)
        assertEquals(1L, (lib.query("SELECT has_lyrics FROM item WHERE pid=?", listOf(pid)).single()["has_lyrics"] as Number).toLong())
        assertEquals(1, ext.query("SELECT * FROM lyrics WHERE item_pid=?", listOf(pid)).size)
        library.setLyrics(pid, true) // twice: still one row
        assertEquals(1, count(ext, "lyrics"))
        library.removeTrack(pid)
        assertEquals("row goes with the song", 0, count(ext, "lyrics"))
    }

    @Test fun aPlaylistIsCreatedFilledInOrderAndRefilledByName() {
        val library = library(withExistingTrack = true)
        val a = library.addTrack(TrackMeta("F01/A.mp3", "A", "X", "Al"), 1); val b = library.addTrack(TrackMeta("F01/B.mp3", "B", "X", "Al"), 1)
        val pl = library.setPlaylist("Mi lista", listOf(b, a, b), 5)
        assertEquals(pl, library.userPlaylistPid("Mi lista"))
        val rows = lib.query("SELECT item_pid, physical_order FROM item_to_container WHERE container_pid=? ORDER BY physical_order", listOf(pl))
        assertEquals(listOf(b, a), rows.map { (it["item_pid"] as Number).toLong() })
        assertEquals(1, dyn.query("SELECT * FROM container_ui WHERE container_pid=?", listOf(pl)).size)
        assertEquals("same name = same playlist", pl, library.setPlaylist("Mi lista", listOf(a), 6))
        assertEquals(1, lib.query("SELECT * FROM item_to_container WHERE container_pid=?", listOf(pl)).size)
        assertEquals(1, lib.query("SELECT * FROM container WHERE name='Mi lista'").size)
        library.removeTrack(a)
        assertEquals("a removed song leaves its playlists", 0, lib.query("SELECT * FROM item_to_container WHERE container_pid=?", listOf(pl)).size)
    }

    @Test fun albumsPointAtACoveredSongAndOldOnesCanBeRepaired() {
        val library = library(withExistingTrack = true)
        val a = library.addTrack(TrackMeta("F01/A.mp3", "A", "X", "Al"), 1); val b = library.addTrack(TrackMeta("F01/B.mp3", "B", "X", "Al"), 1)
        fun albumPointer() = (lib.query("SELECT artwork_item_pid AS p FROM album WHERE name='Al'").single()["p"] as Number).toLong()
        assertEquals("no cover yet", 0L, albumPointer())
        library.setArtwork(a, 512)
        assertEquals("the album points at its covered song", a, albumPointer())
        library.setArtwork(b, 512)
        assertEquals("a valid pointer is kept", a, albumPointer())
        lib.exec("UPDATE album SET artwork_item_pid=0 WHERE name='Al'") // as songs sent by 0.33-0.36 left it
        assertEquals(1, library.fixAlbumArtwork()); assertEquals(a, albumPointer())
        assertEquals("nothing left to fix", 0, library.fixAlbumArtwork())
        library.removeTrack(a)
        assertEquals("the cover song was removed: another covered song takes over", b, albumPointer())
    }

    @Test fun updatingAFileSizeKeepsTheAudioTotalRight() {
        val library = library(withExistingTrack = true)
        val pid = library.addTrack(TrackMeta("F07/BBBB.mp3", "Song", "Artist", "Album", fileSize = 5_000_000), 1)
        library.updateFileSize(pid, 5_004_000)
        assertEquals(5_004_000L, (loc.query("SELECT file_size FROM location WHERE item_pid=?", listOf(pid)).single()["file_size"] as Number).toLong())
        assertEquals(100L + 1000 + 5_004_000, (lib.query("SELECT size FROM track_size_calc").single()["size"] as Number).toLong())
    }

    @Test fun clonedTrackNeverInheritsArtworkFromTemplateSong() {
        val library = library(withExistingTrack = true)
        lib.exec("UPDATE item SET artwork_status=1, artwork_cache_id=987654 WHERE title='Old'")
        val pid = library.addTrack(TrackMeta("F01/NEW.mp3", "New", "New Artist", "New Album"), 5)
        val item = lib.query("SELECT artwork_status, artwork_cache_id FROM item WHERE pid=?", listOf(pid)).single()
        assertEquals(0L, (item["artwork_status"] as Number).toLong())
        assertEquals(0L, (item["artwork_cache_id"] as Number).toLong())
    }

    @Test fun theSameArtistAndAlbumAreReusedNotDuplicated() {
        val library = library(withExistingTrack = true)
        library.addTrack(TrackMeta("F01/1.mp3", "One", "Artist", "Album", genre = "Pop"), 1)
        library.addTrack(TrackMeta("F01/2.mp3", "Two", "Artist", "Album", genre = "Pop"), 1)
        assertEquals("old artist + new artist", 2, count(lib, "artist"))
        assertEquals(2, count(lib, "album")); assertEquals(2, count(lib, "genre_map")); assertEquals(2, count(lib, "track_artist"))
        val items = lib.query("SELECT artist_pid, album_pid FROM item WHERE artist='Artist'")
        assertEquals(1, items.map { it["artist_pid"] }.toSet().size); assertEquals(1, items.map { it["album_pid"] }.toSet().size)
    }

    @Test fun newTracksSortAfterTheExistingOnes() {
        val library = library(withExistingTrack = true)
        val p = library.addTrack(TrackMeta("F01/1.mp3", "New", "New Artist", "New Album"), 1)
        val old = lib.query("SELECT title_order, artist_order FROM item WHERE title='Old'").single()
        val new = lib.query("SELECT title_order, artist_order FROM item WHERE pid=?", listOf(p)).single()
        assertTrue((new["title_order"] as Number).toLong() > (old["title_order"] as Number).toLong())
        assertTrue((new["artist_order"] as Number).toLong() > (old["artist_order"] as Number).toLong())
    }

    @Test fun anEmptyLibraryIsFilledFromTheBuiltInTemplates() {
        val library = library(withExistingTrack = false)
        assertEquals(0, count(lib, "item"))
        val pid = library.addTrack(TrackMeta("F02/X.mp3", "First", "A", "B", fileSize = 10), 5)
        assertEquals(1, count(lib, "item")); assertEquals(1, count(loc, "location"))
        assertEquals("First", lib.query("SELECT title FROM item WHERE pid=?", listOf(pid)).single()["title"])
        assertEquals(0, count(dyn, "item_stats")); assertEquals("no template row to clone: nothing invented", 0, count(ext, "chapter"))
    }

    @Test fun noGenreMeansNoGenreRow() {
        val library = library(withExistingTrack = false)
        val pid = library.addTrack(TrackMeta("F02/X.mp3", "T", "A", "B"), 5)
        assertNull(lib.query("SELECT genre_id FROM item WHERE pid=?", listOf(pid)).single()["genre_id"])
        assertEquals(0, count(lib, "genre_map"))
    }

    @Test fun everyTrackGetsItsOwnPid() {
        val library = library(withExistingTrack = true)
        val pids = (1..20).map { library.addTrack(TrackMeta("F03/$it.mp3", "T$it", "A", "B"), 1) }
        assertEquals(20, pids.toSet().size)
    }

    @Test fun removingATrackRemovesItsRowsAndLeavesNoEmptyAlbumOrArtist() {
        val library = library(withExistingTrack = true)
        val pid = library.addTrack(TrackMeta("F04/Z.mp3", "Gone", "Solo Artist", "Solo Album", genre = "Jazz", fileSize = 50), 1)
        assertEquals("F04/Z.mp3", library.removeTrack(pid))
        assertEquals(1, count(lib, "item")); assertEquals(1, count(loc, "location")); assertEquals(1, count(lib, "avformat_info"))
        assertEquals(1, count(lib, "artist")); assertEquals(1, count(lib, "album")); assertEquals(1, count(lib, "genre_map")); assertEquals(1, count(lib, "track_artist"))
        assertEquals(100L + 1000, (lib.query("SELECT size FROM track_size_calc").single()["size"] as Number).toLong())
        assertEquals(listOf("Old"), library.listTracks().map { it.title })
    }

    @Test fun removingOneOfTwoTracksOfAnAlbumKeepsTheAlbum() {
        val library = library(withExistingTrack = true)
        val a = library.addTrack(TrackMeta("F05/1.mp3", "One", "Artist", "Album"), 1)
        library.addTrack(TrackMeta("F05/2.mp3", "Two", "Artist", "Album"), 1)
        library.removeTrack(a)
        assertEquals(2, count(lib, "album")); assertEquals(2, count(lib, "artist"))
    }

    @Test fun removingAnUnknownTrackChangesNothing() {
        val library = library(withExistingTrack = true)
        assertNull(library.removeTrack(123456789L))
        assertEquals(1, count(lib, "item"))
        assertEquals(100L + 1000, (lib.query("SELECT size FROM track_size_calc").single()["size"] as Number).toLong())
    }

    @Test fun listTracksShowsLocationsForFindingDuplicates() {
        val library = library(withExistingTrack = true)
        library.addTrack(TrackMeta("F06/Q.mp3", "Q", "Artist", "Album", trackNumber = 2), 1)
        val t = library.listTracks().single { it.title == "Q" }
        assertEquals("F06/Q.mp3", t.location); assertEquals(2, t.trackNumber)
        assertNotEquals(0L, t.pid)
        assertFalse(library.listTracks().any { it.location == null })
    }

    @Test fun artworkReferencesPointToValidFramesAndAreReused() {
        val realDb = javaClass.getResourceAsStream("/ipod/ArtworkDB.nano6g")!!.readBytes()
        val files = FakeArt(realDb.copyOf(), mutableMapOf("F1073_1.ithmb" to 110_361_600L, "F1085_1.ithmb" to 6_381_056L, "F1074_1.ithmb" to 2_060_000L, "F1089_1.ithmb" to 2_771_936L))
        val library = library(withExistingTrack = true)
        val p1 = library.addTrack(TrackMeta("F01/1.mp3", "One", "Artist", "Album"), 1)
        val p2 = library.addTrack(TrackMeta("F01/2.mp3", "Two", "Artist", "Album"), 1)
        val p3 = library.addTrack(TrackMeta("F01/3.mp3", "Three", "Other", "No cover"), 1)
        assertNull(library.artworkIdFor("Album", "Artist"))
        val frames = Nano6GArtworkUpdater.FORMATS.associateWith { f -> ByteArray(Nano6GArtworkDb.parse(realDb).profiles[f]!!) }
        val tmp = java.nio.file.Files.createTempDirectory("art").toFile()
        val applied = Nano6GArtworkUpdater(files, tmp).apply(listOf(CoverInput("album", p1, 2, frames)))
        val id = applied.ids.getValue("album")
        assertTrue(library.setArtwork(p1, id)); assertTrue(library.setArtwork(p2, id))
        assertFalse(library.setArtwork(-1L, id))
        assertEquals(id, library.artworkIdFor("Album", "Artist"))
        // every reference of the library points to an image of the new ArtworkDB, whose frames are inside the grown files
        val db = Nano6GArtworkDb.parse(files.db)
        val refs = library.artworkReferences()
        assertEquals(setOf(p1, p2), refs.map { it.first }.toSet())
        refs.forEach { assertNotNull(db.find(it.second)) }
        assertEquals(emptyList<String>(), db.validate(Nano6GArtworkUpdater.FORMATS.associateWith { files.size(Nano6GArtworkUpdater.fileOf(it))!! }))
        // the song without a cover and the untouched old song keep status 0
        assertEquals(0L, (lib.query("SELECT artwork_status FROM item WHERE pid=?", listOf(p3)).single()["artwork_status"] as Number).toLong())
    }
}
