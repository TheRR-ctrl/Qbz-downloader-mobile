package com.qbdlx.mobile.ipod

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import com.qbdlx.mobile.ResolverTreeOps
import com.qbdlx.mobile.TreeWriter
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Two tracks are "the same" for the iPod when title, artist and album match, ignoring case and spaces at the ends. */
fun trackKey(title: String, artist: String, album: String) =
    listOf(title, artist, album).joinToString("\u0001") { it.trim().lowercase(Locale.ROOT) }

/** One MP3 to put on the iPod: where it is now and what to call it. */
class SendItem(
    val key: String,
    val sourceUri: String,
    val title: String, val artist: String, val album: String, val genre: String,
    val durationMs: Long, val trackNumber: Int, val year: Int, val bitRate: Int,
    /** Tracks already on the iPod (their library ids) that this one replaces: removed once this one is on the iPod. */
    val replacePids: List<Long> = emptyList(),
    /** The album's cover image address (shared by its songs); null when there is none. */
    val coverUrl: String? = null,
)

class SendResult(val sent: List<String>, val failed: Map<String, String>, val error: String?, val artworkNote: String? = null, val covers: Int = 0, val lyrics: Int = 0)

/**
 * An iPod nano 6G/7G, reached through the folder the user picked for it (Android's folder picker): the iPod must be
 * mounted as a drive Android can read, which means FAT32/exFAT, not Mac-formatted HFS+. The work follows ipodsync
 * (MIT, https://github.com/buldiei/ipodsync) and TunesReloaded: copy the MP3 into iPod_Control/Music/Fxx, add it to
 * the SQLite library, rebuild the signature of Locations.itdb, and write the files back. EXPERIMENTAL: ipodsync's
 * author confirmed this on a nano 7G only, and this code has not run on a device.
 */
class IpodDevice(private val context: Context, private val treeUri: Uri) {
    enum class Problem { NOT_IPOD, OLD_FORMAT, MISSING_FILES, LEFTOVER_FILES, UNREADABLE }

    class Inspection(val problem: Problem?, val guid: ByteArray?, val detail: String? = null) { val ready get() = problem == null }

    private val resolver = context.contentResolver
    private val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    private val writer = TreeWriter(ResolverTreeOps(resolver, treeUri), rootId)
    private val workDir = File(context.cacheDir, "ipod-work")
    private val backupRoot = File(context.filesDir, "ipod-backups")
    private val random = SecureRandom()

    private fun itlpDir() = writer.findDir(listOf("iPod_Control", "iTunes", "iTunes Library.itlp"))

    /** Looks at the picked folder: is it an iPod this code can write to, and is the GUID known? */
    fun inspect(): Inspection = try {
        if (writer.findDir(listOf("iPod_Control")) == null) Inspection(Problem.NOT_IPOD, null)
        else {
            val itlp = itlpDir()
            if (itlp == null) {
                val itunes = writer.findDir(listOf("iPod_Control", "iTunes"))
                val old = itunes != null && writer.list(itunes).any { it.name == "iTunesDB" || it.name == "iTunesCDB" }
                Inspection(if (old) Problem.OLD_FORMAT else Problem.MISSING_FILES, null)
            } else {
                val names = writer.list(itlp).map { it.name }
                val leftovers = names.filter { it.endsWith("-wal") || it.endsWith("-shm") || it.endsWith("-journal") }
                when {
                    DB_NAMES.any { it !in names } -> Inspection(Problem.MISSING_FILES, null, DB_NAMES.filter { it !in names }.joinToString())
                    leftovers.isNotEmpty() -> Inspection(Problem.LEFTOVER_FILES, null, leftovers.joinToString())
                    else -> Inspection(null, readGuid())
                }
            }
        }
    } catch (e: Exception) {
        Inspection(Problem.UNREADABLE, null, e.message)
    }

    private fun readGuid(): ByteArray? {
        for (name in listOf("SysInfoExtended", "SysInfo")) {
            val dir = writer.findDir(listOf("iPod_Control", "Device")) ?: return null
            val id = writer.list(dir).firstOrNull { !it.isDir && it.name == name }?.docId ?: continue
            val text = runCatching { writer.openForRead(id).use { it.readBytes().toString(Charsets.ISO_8859_1) } }.getOrNull() ?: continue
            IpodSysInfo.parse(text)?.let { return it }
        }
        return null
    }

    /** The tracks the iPod already has, read from a copy of its library. */
    fun listTracks(): List<IpodTrack> = withLibrary(readOnly = true) { lib, _ -> lib.listTracks() }

    /** The playlists stored on the iPod. */
    fun listPlaylists(): List<IpodPlaylist> = withLibrary(readOnly = true) { lib, _ -> lib.listPlaylists() }

    /** A read-only technical report (table layouts, artwork folder, one old and one new track row) to find out why artwork/metadata are missing. */
    fun report(): String {
        val art = runCatching {
            writer.findDir(listOf("iPod_Control", "Artwork"))?.let { d ->
                writer.list(d).joinToString("\n") { f ->
                    if (f.isDir) "[dir] ${f.name}" else runCatching {
                        writer.openForRead(f.docId).use { s ->
                            val head = ByteArray(if (f.name == "ArtworkDB") 640 else 48)
                            val n = s.read(head).coerceAtLeast(0)
                            var total = n.toLong(); val buf = ByteArray(1 shl 16)
                            while (true) { val r = s.read(buf); if (r < 0) break; total += r }
                            "${f.name}  size=$total  head=" + head.take(n).joinToString("") { "%02x".format(it) }
                        }
                    }.getOrElse { "${f.name}  (unreadable: ${it.message})" }
                }
            } ?: "(no Artwork folder)"
        }.getOrElse { "error: ${it.message}" }
        val lib = runCatching { withLibrary(readOnly = true) { l, _ -> l.report() } }.getOrElse { "error: ${it.message}" }
        return "== iPod_Control/Artwork ==\n$art\n\n$lib"
    }

    /** Puts [items] on the iPod. The library is backed up first; if anything goes wrong the iPod's own files are not touched. */
    fun send(items: List<SendItem>, guid: ByteArray, makeBackup: Boolean = true, coverFrames: ((String) -> Map<Int, ByteArray>?)? = null, lyricsFor: ((SendItem) -> String?)? = null, progress: (done: Int, total: Int) -> Unit): SendResult {
        val itlp = itlpDir() ?: return SendResult(emptyList(), emptyMap(), "iPod library not found")
        val music = writer.findDir(listOf("iPod_Control", "Music")) ?: return SendResult(emptyList(), emptyMap(), "iPod_Control/Music not found")
        val folders = writer.list(music).filter { it.isDir && Regex("F\\d\\d").matches(it.name) }
        if (folders.isEmpty()) return SendResult(emptyList(), emptyMap(), "no Fxx folders in iPod_Control/Music")

        if (makeBackup) backup(itlp)
        val copied = ArrayList<String>() // document ids of the MP3s written, to delete again if the library step fails
        val sent = ArrayList<String>(); val failed = LinkedHashMap<String, String>()
        val pidsOf = LinkedHashMap<String, MutableList<Pair<Long, SendItem>>>() // cover url -> songs added in this call
        var artwork: ArtworkApplied? = null; var artworkNote: String? = null; var coverCount = 0
        var lyricCount = 0
        var removedOld = 0 // replaced tracks already taken off the iPod (their space is freed before the new copy is written)
        try {
            val ok = withLibrary(readOnly = false, itlp = itlp) { lib, work ->
                items.forEachIndexed { i, item ->
                    progress(i, items.size)
                    var docId: String? = null
                    try {
                        // Replacing: the old copy goes first so its space is free for the new one.
                        item.replacePids.forEach { pid ->
                            runCatching { lib.removeTrack(pid) }.getOrNull()?.let { loc ->
                                runCatching { deleteMusicFile(music, loc) }
                                removedOld++
                            }
                        }
                        val folder = folders[random.nextInt(folders.size)]
                        val name = freshName(folder.docId)
                        docId = writer.createIn(folder.docId, "audio/mpeg", name)
                        val text = lyricsFor?.let { f -> runCatching { f(item) }.getOrNull() }
                        val (size, hasLyrics) = copy(item.sourceUri, docId, text)
                        copied += docId
                        val pid = lib.addTrack(
                            TrackMeta(
                                location = "${folder.name}/$name", title = item.title.ifBlank { item.key }, artist = item.artist, album = item.album,
                                genre = item.genre, totalTimeMs = item.durationMs, trackNumber = item.trackNumber, year = item.year,
                                bitRate = item.bitRate, fileSize = size,
                            ),
                            macNow(),
                        )
                        if (hasLyrics) { lib.setLyrics(pid, true); lyricCount++ }
                        sent += item.key
                        item.coverUrl?.let { pidsOf.getOrPut(it) { ArrayList() } += pid to item }
                    } catch (e: Exception) {
                        docId?.let { writer.delete(it); copied.remove(it) }
                        failed[item.key] = e.message ?: e.javaClass.simpleName
                    }
                }
                progress(items.size, items.size)
                if (sent.isEmpty() && removedOld == 0) return@withLibrary false
                if (coverFrames != null && pidsOf.isNotEmpty()) {
                    val r = addArtwork(lib, pidsOf, coverFrames)
                    artwork = r.first; artworkNote = r.second; coverCount = r.third
                }
                work.finish(guid)
                true
            }
            if (!ok) return SendResult(sent, failed, failed.values.firstOrNull() ?: "nothing was sent")
            writeBack(itlp)
            return SendResult(sent, failed, null, artworkNote, coverCount, lyricCount)
        } catch (e: Exception) {
            artwork?.rollback() // the library was not written: the artwork files go back too
            copied.forEach { writer.delete(it) } // the library was not changed: take the copied files off again
            return SendResult(emptyList(), failed, e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Gives the songs just added their album's cover. Covers already on the iPod (same album and artist) are reused; the others are
     * written in one transaction ([Nano6GArtworkUpdater]). Returns (the transaction to undo if the library write fails later, a note
     * when something was skipped, covers added). A failure here never fails the transfer: the songs simply stay without cover.
     */
    private fun addArtwork(lib: ItlpLibrary, byCover: Map<String, List<Pair<Long, SendItem>>>, frames: (String) -> Map<Int, ByteArray>?): Triple<ArtworkApplied?, String?, Int> {
        val dir = writer.findDir(listOf("iPod_Control", "Artwork")) ?: return Triple(null, "no Artwork folder on the iPod", 0)
        var note: String? = null
        val fresh = ArrayList<CoverInput>(); val users = HashMap<String, List<Long>>()
        for ((url, songs) in byCover) {
            val existing = songs.firstNotNullOfOrNull { (_, it) -> lib.artworkIdFor(it.album, it.artist) }
            if (existing != null) { songs.forEach { lib.setArtwork(it.first, existing) }; continue }
            val f = runCatching { frames(url) }.getOrNull()
            if (f == null) { note = "cover not available"; continue }
            fresh += CoverInput(url, songs.first().first, songs.size, f); users[url] = songs.map { it.first }
        }
        if (fresh.isEmpty()) return Triple(null, note, 0)
        return try {
            val applied = Nano6GArtworkUpdater(SafArtFiles(dir), File(context.filesDir, "ipod-artwork-backups/" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()))).apply(fresh)
            try {
                applied.ids.forEach { (url, id) -> users.getValue(url).forEach { lib.setArtwork(it, id) } }
            } catch (e: Exception) { applied.rollback(); throw e }
            Triple(applied, note, fresh.size)
        } catch (e: Exception) {
            Triple(null, "covers not written: ${e.message ?: e.javaClass.simpleName}", 0)
        }
    }

    /** iPod_Control/Artwork through Android's folder access. Appending and cutting need a seekable file; if the iPod's provider has none, the first call fails before anything is written. */
    private inner class SafArtFiles(private val dir: String) : ArtFiles {
        private fun uri(name: String): Uri {
            val id = writer.list(dir).firstOrNull { !it.isDir && it.name == name }?.docId ?: error("$name not found")
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
        }
        private fun <T> channel(name: String, block: (java.nio.channels.FileChannel) -> T): T =
            (resolver.openFileDescriptor(uri(name), "rw") ?: error("cannot open $name")).use { pfd ->
                java.io.FileOutputStream(pfd.fileDescriptor).use { block(it.channel) } // a writable channel (size, position, write, truncate)
            }
        override fun size(name: String): Long? = runCatching { channel(name) { it.size() } }.getOrNull()
        override fun read(name: String): ByteArray = readFile(dir, name)
        override fun replace(name: String, bytes: ByteArray) = overwrite(dir, name, bytes)
        override fun append(name: String, bytes: ByteArray) = channel(name) { ch ->
            ch.position(ch.size()); val buf = java.nio.ByteBuffer.wrap(bytes); while (buf.hasRemaining()) ch.write(buf)
            runCatching { ch.force(true) }; Unit
        }
        override fun truncate(name: String, size: Long) = channel(name) { ch -> ch.truncate(size); runCatching { ch.force(true) }; Unit }
    }

    /** Takes these tracks off the iPod: the library is backed up and written first, the song files are deleted after. Returns (removed, error). */
    fun removeTracks(pids: List<Long>, guid: ByteArray, playlistPids: List<Long> = emptyList()): Pair<Int, String?> {
        val itlp = itlpDir() ?: return 0 to "iPod library not found"
        val music = writer.findDir(listOf("iPod_Control", "Music")) ?: return 0 to "iPod_Control/Music not found"
        backup(itlp)
        val files = ArrayList<String>()
        try {
            val ok = withLibrary(readOnly = false, itlp = itlp) { lib, work ->
                pids.forEach { pid -> runCatching { lib.removeTrack(pid) }.getOrNull()?.let { files += it } }
                playlistPids.forEach { runCatching { lib.deletePlaylist(it) } }
                if (files.isEmpty() && playlistPids.isEmpty()) return@withLibrary false
                work.finish(guid)
                true
            }
            if (!ok) return 0 to null
            writeBack(itlp)
            val folders = writer.list(music).filter { it.isDir }.associateBy { it.name }
            files.groupBy { it.substringBefore('/') }.forEach { (folder, locs) ->
                val dir = folders[folder]?.docId ?: return@forEach
                val names = locs.map { it.substringAfter('/') }.toSet()
                runCatching { writer.list(dir).filter { !it.isDir && it.name in names }.forEach { runCatching { writer.delete(it.docId) } } }
            }
            return files.size to null
        } catch (e: Exception) {
            return 0 to (e.message ?: e.javaClass.simpleName)
        }
    }

    /** Gives every album that has covered songs the pointer the iPod's Albums view needs and links every album to its artist (for songs sent by earlier versions). Returns (albums fixed, error). */
    fun fixAlbumCovers(guid: ByteArray): Pair<Int, String?> {
        val itlp = itlpDir() ?: return 0 to "iPod library not found"
        backup(itlp)
        return try {
            var n = 0
            val ok = withLibrary(readOnly = false, itlp = itlp) { lib, work ->
                n = lib.fixAlbumArtwork()
                lib.repairArtistLinks() // also point every album at its own artist, so the iPod's Artists view lists them right
                work.finish(guid); true
            }
            if (ok) writeBack(itlp)
            (if (ok) n else 0) to null
        } catch (e: Exception) { 0 to (e.message ?: e.javaClass.simpleName) }
    }

    class PlaylistResult(val added: Int, val missing: Int, val error: String?)

    /** Creates (or refills) the playlist [name] with the songs on the iPod whose [trackKey]s are [keys], in that order. */
    fun makePlaylist(name: String, keys: List<String>, guid: ByteArray): PlaylistResult {
        val itlp = itlpDir() ?: return PlaylistResult(0, 0, "iPod library not found")
        backup(itlp)
        return try {
            var added = 0; var missing = 0
            val ok = withLibrary(readOnly = false, itlp = itlp) { lib, work ->
                val byKey = LinkedHashMap<String, Long>()
                lib.listTracks().forEach { byKey.putIfAbsent(trackKey(it.title, it.artist, it.album), it.pid) }
                val pids = keys.mapNotNull { byKey[it] }
                added = pids.distinct().size; missing = keys.distinct().size - added
                if (pids.isEmpty()) return@withLibrary false
                lib.setPlaylist(name, pids, macNow())
                work.finish(guid)
                true
            }
            if (ok) writeBack(itlp)
            PlaylistResult(if (ok) added else 0, missing, null)
        } catch (e: Exception) { PlaylistResult(0, 0, e.message ?: e.javaClass.simpleName) }
    }

    class LyricsResult(val added: Int, val skipped: Int, val failed: Int, val error: String?, val stopped: Boolean = false)

    /**
     * Writes lyrics into MP3s that are already on the iPod. Each file is rebuilt in the phone's cache first (tag with lyrics + the same audio),
     * then put back in place; the library is backed up first and gets the new file sizes every 60 songs (and at the end, or when it fails), so work is never lost. [lyricsFor] returns the text or null
     * (null/blank = no lyrics found: the file is not touched). [progress] gets (songs handled, total).
     */
    fun addLyrics(tracks: List<IpodTrack>, guid: ByteArray, stop: () -> Boolean = { false }, lyricsFor: (List<IpodTrack>) -> Map<Long, String>, progress: (Int, Int) -> Unit): LyricsResult {
        val itlp = itlpDir() ?: return LyricsResult(0, 0, 0, "iPod library not found")
        val music = writer.findDir(listOf("iPod_Control", "Music")) ?: return LyricsResult(0, 0, 0, "iPod_Control/Music not found")
        val folders = writer.list(music).filter { it.isDir }.associateBy { it.name }
        val mp3s = tracks.filter { it.location?.endsWith(".mp3", ignoreCase = true) == true }
        var skipped = tracks.size - mp3s.size; var failed = 0
        val newSizes = LinkedHashMap<Long, Long>()
        val tmp = File(context.cacheDir, "ipod-lyrics-tmp").also { it.mkdirs() }
        backup(itlp)
        var done = 0
        val unsaved = LinkedHashMap<Long, Long>()
        // Saves what has been written so far into the library, so stopping half way (cable out, app closed) keeps the work done.
        fun flush() {
            if (unsaved.isEmpty()) return
            withLibrary(readOnly = false, itlp = itlp) { lib, work ->
                unsaved.forEach { (pid, size) -> lib.updateFileSize(pid, size); lib.setLyrics(pid, true) }
                work.finish(guid)
            }
            writeBack(itlp)
            unsaved.clear()
        }
        try {
            progress(0, mp3s.size)
            var stopped = false
            for (chunk in mp3s.chunked(20)) {
                if (stop()) { stopped = true; break }
                val texts = lyricsFor(chunk)
                for (t in chunk) {
                    val text = texts[t.pid]
                    try {
                        if (text.isNullOrBlank()) { skipped++; continue }
                        val loc = t.location!!
                        val dir = folders[loc.substringBefore('/')]?.docId ?: error("folder not found")
                        val file = writer.list(dir).firstOrNull { !it.isDir && it.name == loc.substringAfter('/') } ?: error("file not found")
                        val out = File(tmp, "w.mp3")
                        val r = writer.openForRead(file.docId).use { src -> out.outputStream().use { Id3Lyrics.copy(src, it, text) } }
                        if (!r.embedded || out.length() != r.bytes) { skipped++; continue }
                        writer.openForOverwrite(file.docId).use { dst -> out.inputStream().use { it.copyTo(dst) } }
                        newSizes[t.pid] = r.bytes; unsaved[t.pid] = r.bytes
                    } catch (e: Exception) { failed++ } finally { done++; progress(done, mp3s.size) }
                }
                if (unsaved.size >= 60) flush()
            }
            flush()
            return LyricsResult(newSizes.size, skipped, failed, null, stopped)
        } catch (e: Exception) {
            val saveError = runCatching { flush() }.exceptionOrNull()
            return LyricsResult(newSizes.size - (if (saveError != null) unsaved.size else 0), skipped, failed, e.message ?: e.javaClass.simpleName)
        } finally { tmp.deleteRecursively() }
    }

    private fun deleteMusicFile(music: String, loc: String) {
        val dir = writer.list(music).firstOrNull { it.isDir && it.name == loc.substringBefore('/') }?.docId ?: return
        writer.list(dir).firstOrNull { !it.isDir && it.name == loc.substringAfter('/') }?.let { writer.delete(it.docId) }
    }

    /** Puts the library files back as they were before the last [send]. Returns false when there is no backup. */
    fun restoreLatestBackup(): Boolean {
        val itlp = itlpDir() ?: return false
        val latest = backupRoot.listFiles { f -> f.isDirectory }?.maxByOrNull { it.name } ?: return false
        for (name in BACKED_UP) {
            val f = File(latest, name).takeIf { it.exists() } ?: continue
            overwrite(itlp, name, f.readBytes())
        }
        return true
    }

    // ---- internals ------------------------------------------------------------------------------------------------

    private class Work(val dir: File, val dbs: List<SQLiteDatabase>) {
        fun finish(guid: ByteArray) {
            dbs.forEach { it.setTransactionSuccessful() }
            dbs.forEach { it.endTransaction() }
            dbs.forEach { it.close() }
            check(!File(dir, "Library.itdb-wal").exists() && !File(dir, "Locations.itdb-wal").exists()) { "SQLite left a -wal file" }
            File(dir, "Locations.itdb.cbk").writeBytes(Cbk.build(File(dir, "Locations.itdb").readBytes(), guid))
        }
    }

    /** Copies the four databases to the phone, opens them and runs [block]; the iPod's files stay untouched until [writeBack]. */
    private fun <T> withLibrary(readOnly: Boolean, itlp: String? = itlpDir(), block: (ItlpLibrary, Work) -> T): T {
        itlp ?: error("iPod library not found")
        workDir.deleteRecursively(); workDir.mkdirs()
        for (name in DB_NAMES) File(workDir, name).writeBytes(readFile(itlp, name))
        val flags = if (readOnly) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE
        val dbs = DB_NAMES.map { SQLiteDatabase.openDatabase(File(workDir, it).path, null, flags) }
        if (!readOnly) dbs.forEach { it.beginTransaction() }
        try {
            if (!readOnly) check(AndroidSql(dbs[0]).query("PRAGMA quick_check").firstOrNull()?.values?.firstOrNull() == "ok") { "the iPod's library failed its integrity check" }
            val (lib, loc, dyn, ext) = dbs.map { AndroidSql(it) }
            val work = Work(workDir, dbs)
            return block(ItlpLibrary(lib, loc, dyn, ext, random), work)
        } finally {
            // on success Work.finish() already closed them; on failure the changes are rolled back (never marked successful)
            dbs.forEach { db -> runCatching { if (db.inTransaction()) db.endTransaction() }; runCatching { if (db.isOpen) db.close() } }
        }
    }

    private fun writeBack(itlp: String) {
        // Library first, the signature last: a device that loses power in between ignores the half-written set.
        for (name in listOf("Library.itdb", "Dynamic.itdb", "Extras.itdb", "Locations.itdb", "Locations.itdb.cbk")) {
            overwrite(itlp, name, File(workDir, name).readBytes())
        }
    }

    private fun readFile(dir: String, name: String): ByteArray {
        val id = writer.list(dir).firstOrNull { !it.isDir && it.name == name }?.docId ?: error("$name not found on the iPod")
        return writer.openForRead(id).use { it.readBytes() }
    }

    private fun overwrite(dir: String, name: String, bytes: ByteArray) {
        val id = writer.list(dir).firstOrNull { !it.isDir && it.name == name }?.docId ?: writer.createIn(dir, "application/octet-stream", name)
        writer.openForOverwrite(id).use { it.write(bytes) }
    }

    private fun backup(itlp: String) {
        val dir = File(backupRoot, SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())).also { it.mkdirs() }
        for (name in BACKED_UP) {
            val id = writer.list(itlp).firstOrNull { !it.isDir && it.name == name }?.docId ?: continue
            File(dir, name).writeBytes(writer.openForRead(id).use { it.readBytes() })
        }
        backupRoot.listFiles { f -> f.isDirectory }?.sortedBy { it.name }?.dropLast(5)?.forEach { it.deleteRecursively() } // keep the last 5
    }

    private fun freshName(folderId: String): String {
        val existing = writer.list(folderId).map { it.name }.toSet()
        repeat(50) {
            val name = "idmix${100000 + random.nextInt(9_900_000)}.mp3"
            if (name !in existing) return name
        }
        error("could not find a free file name")
    }

    /** Copies the MP3; with [lyrics] they go into its tag on the way. Returns (bytes written, lyrics embedded). */
    private fun copy(sourceUri: String, docId: String, lyrics: String? = null): Pair<Long, Boolean> {
        val input = resolver.openInputStream(Uri.parse(sourceUri)) ?: error("cannot open the downloaded file")
        if (!lyrics.isNullOrBlank()) {
            val r = input.use { src -> writer.openForOverwrite(docId).use { out -> Id3Lyrics.copy(src, out, lyrics) } }
            return r.bytes to r.embedded
        }
        var total = 0L
        input.use { src -> writer.openForOverwrite(docId).use { out ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = src.read(buf); if (n < 0) break; out.write(buf, 0, n); total += n }
        } }
        return total to false
    }

    private fun macNow() = System.currentTimeMillis() / 1000 - MAC_EPOCH

    private companion object {
        val DB_NAMES = listOf("Library.itdb", "Locations.itdb", "Dynamic.itdb", "Extras.itdb")
        val BACKED_UP = DB_NAMES + "Locations.itdb.cbk"
        const val MAC_EPOCH = 978307200L
    }
}
