package com.qbdlx.mobile.ipod

import java.io.File

/** The files of iPod_Control/Artwork as the updater needs them (the phone's folder on tests, the iPod through Android's folder access on the device). */
interface ArtFiles {
    fun size(name: String): Long?
    fun read(name: String): ByteArray
    /** Replaces a whole (small) file. */
    fun replace(name: String, bytes: ByteArray)
    fun append(name: String, bytes: ByteArray)
    /** Cuts a file back to [size] bytes (undoing an append). */
    fun truncate(name: String, size: Long)
}

/** A cover ready for the iPod: its RGB565-LE frame for each format id, the library item that creates it and how many items will use it. */
class CoverInput(val key: String, val songPid: Long, val references: Int, val frames: Map<Int, ByteArray>)

/** The result of [Nano6GArtworkUpdater.apply]: the ArtworkDB image id of each cover key, and the way back. */
class ArtworkApplied(val ids: Map<String, Int>, private val undo: () -> Unit) {
    private var undone = false
    /** Puts ArtworkDB and the .ithmb files back as they were. Safe to call more than once. */
    fun rollback() { if (!undone) { undone = true; undo() } }
}

/**
 * Adds covers to the iPod's artwork files, step by step so that a failure at any point can be undone:
 * check → back up ArtworkDB → append frames to the four .ithmb → write the new ArtworkDB → read it back and verify.
 * If anything goes wrong the .ithmb files are cut back to their old size and ArtworkDB is restored from the backup.
 * An unknown structure aborts before a single byte is written.
 */
class Nano6GArtworkUpdater(private val files: ArtFiles, private val backupDir: File) {
    companion object {
        const val DB = "ArtworkDB"
        val FORMATS = listOf(1085, 1073, 1074, 1089)
        fun fileOf(format: Int) = "F${format}_1.ithmb"
    }

    fun apply(covers: List<CoverInput>): ArtworkApplied {
        if (covers.isEmpty()) return ArtworkApplied(emptyMap()) {}
        val original = files.read(DB)
        val db = Nano6GArtworkDb.parse(original)
        // every frame must match its profile and every .ithmb must end exactly where its last record ends
        val sizes = HashMap<Int, Long>()
        for (f in FORMATS) {
            val frame = db.profiles[f] ?: throw ArtworkDbException("format $f is not a profile of this ArtworkDB")
            val len = files.size(fileOf(f)) ?: throw ArtworkDbException("${fileOf(f)} is missing")
            val end = db.all.flatMap { it.frames }.filter { it.format == f }.maxOfOrNull { it.offset + it.size } ?: 0L
            if (len != end) throw ArtworkDbException("${fileOf(f)} has $len bytes but its records end at $end")
            sizes[f] = len
            covers.forEach { c -> if (c.frames[f]?.size != frame) throw ArtworkDbException("cover ${c.key}: frame for $f must be $frame bytes") }
        }
        db.validate(sizes).firstOrNull()?.let { throw ArtworkDbException("the existing ArtworkDB is inconsistent: $it") }

        backupDir.mkdirs()
        File(backupDir, DB).writeBytes(original)
        var dbWritten = false
        val undo = {
            runCatching { for (f in FORMATS) if ((files.size(fileOf(f)) ?: 0L) != sizes[f]) files.truncate(fileOf(f), sizes[f]!!) }
            if (dbWritten) runCatching { files.replace(DB, original) }
        }
        try {
            for (f in FORMATS) {
                val frame = db.profiles[f]!!
                files.append(fileOf(f), covers.fold(ByteArray(0)) { acc, c -> acc + c.frames[f]!! })
                check(files.size(fileOf(f)) == sizes[f]!! + frame.toLong() * covers.size) { "${fileOf(f)} did not grow as expected" }
            }
            val ids = LinkedHashMap<String, Int>()
            covers.forEachIndexed { i, c ->
                val offsets = FORMATS.associateWith { f -> sizes[f]!! + db.profiles[f]!!.toLong() * i }
                ids[c.key] = db.add(NewArtwork(c.songPid, offsets, c.references)).id
            }
            val grown = FORMATS.associateWith { f -> sizes[f]!! + db.profiles[f]!!.toLong() * covers.size }
            val bytes = db.serialize()
            val reread = Nano6GArtworkDb.parse(bytes)
            check(reread.validate(grown).isEmpty()) { "the new ArtworkDB failed its own validation" }
            dbWritten = true
            files.replace(DB, bytes)
            check(files.read(DB).contentEquals(bytes)) { "ArtworkDB read back differently" }
            return ArtworkApplied(ids, undo)
        } catch (e: Exception) {
            undo()
            throw e
        }
    }
}
