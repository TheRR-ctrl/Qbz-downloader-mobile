package com.qbdlx.mobile.ipod

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Artwork files in memory. The real .ithmb files (110 MB) are represented by their exact sizes; only appended bytes are stored. */
class FakeArt(var db: ByteArray, private val base: MutableMap<String, Long>) : ArtFiles {
    val tail = HashMap<String, ByteArray>()
    var failAppendOn: String? = null
    var failReplace = false
    override fun size(name: String) = base[name]?.plus(tail[name]?.size ?: 0)
    override fun read(name: String): ByteArray = if (name == "ArtworkDB") db else error("only ArtworkDB is read")
    override fun replace(name: String, bytes: ByteArray) { if (failReplace) error("disk full"); db = bytes }
    override fun append(name: String, bytes: ByteArray) {
        if (name == failAppendOn) error("write failed")
        tail[name] = (tail[name] ?: ByteArray(0)) + bytes
    }
    override fun truncate(name: String, size: Long) { tail[name] = (tail[name] ?: ByteArray(0)).copyOf((size - base[name]!!).toInt()) }
}

class Nano6GArtworkUpdaterTest {
    @get:Rule val tmp = TemporaryFolder()
    private val realDb = javaClass.getResourceAsStream("/ipod/ArtworkDB.nano6g")!!.readBytes()
    private fun sizes() = mutableMapOf("F1073_1.ithmb" to 110_361_600L, "F1085_1.ithmb" to 6_381_056L, "F1074_1.ithmb" to 2_060_000L, "F1089_1.ithmb" to 2_771_936L)
    private fun frame(f: Int, fill: Int) = ByteArray(Nano6GArtworkDb.parse(realDb).profiles[f]!!) { fill.toByte() }
    private fun cover(key: String, pid: Long, fill: Int, refs: Int = 1) = CoverInput(key, pid, refs, Nano6GArtworkUpdater.FORMATS.associateWith { frame(it, fill) })

    @Test fun addsOneCoverWithFourLinkedFrames() {
        val files = FakeArt(realDb.copyOf(), sizes())
        val r = Nano6GArtworkUpdater(files, tmp.newFolder()).apply(listOf(cover("a", 42L, 7, refs = 3)))
        assertEquals(mapOf("a" to 512), r.ids)
        val db = Nano6GArtworkDb.parse(files.db)
        val img = db.find(512)!!
        assertEquals(413, db.all.size); assertEquals(513, db.nextId); assertEquals(42L, img.songPid)
        // each frame points to the old end of its file and the bytes there are the cover's
        val old = sizes()
        for (f in img.frames) {
            val name = Nano6GArtworkUpdater.fileOf(f.format)
            assertEquals(old[name]!!, f.offset)
            assertEquals(f.offset + f.size, files.size(name))
            assertTrue(files.tail[name]!!.all { it == 7.toByte() })
        }
        assertEquals(emptyList<String>(), db.validate(Nano6GArtworkUpdater.FORMATS.associateWith { files.size(Nano6GArtworkUpdater.fileOf(it))!! }))
    }

    @Test fun severalCoversGetConsecutiveIdsAndOffsetsAndKeepTheOldOnes() {
        val files = FakeArt(realDb.copyOf(), sizes())
        val before = Nano6GArtworkDb.parse(realDb).all.map { it.raw.toList() }
        val r = Nano6GArtworkUpdater(files, tmp.newFolder()).apply(listOf(cover("a", 1L, 1), cover("b", 2L, 2)))
        assertEquals(mapOf("a" to 512, "b" to 513), r.ids)
        val db = Nano6GArtworkDb.parse(files.db)
        assertEquals(before, db.all.take(412).map { it.raw.toList() })
        assertEquals(db.find(512)!!.frames[0].offset + db.profiles[1085]!!, db.find(513)!!.frames[0].offset)
    }

    @Test fun failureWhileAppendingRestoresEverything() {
        val files = FakeArt(realDb.copyOf(), sizes()).also { it.failAppendOn = "F1074_1.ithmb" }
        assertThrows(IllegalStateException::class.java) { Nano6GArtworkUpdater(files, tmp.newFolder()).apply(listOf(cover("a", 1L, 1))) }
        assertArrayEquals(realDb, files.db)
        sizes().forEach { (n, s) -> assertEquals(s, files.size(n)) }
    }

    @Test fun failureWritingTheDatabaseRestoresEverything() {
        val files = FakeArt(realDb.copyOf(), sizes()).also { it.failReplace = true }
        assertThrows(IllegalStateException::class.java) { Nano6GArtworkUpdater(files, tmp.newFolder()).apply(listOf(cover("a", 1L, 1))) }
        assertArrayEquals(realDb, files.db)
        sizes().forEach { (n, s) -> assertEquals(s, files.size(n)) }
    }

    @Test fun rollbackAfterSuccessGoesBackToTheOriginal() {
        val files = FakeArt(realDb.copyOf(), sizes())
        val r = Nano6GArtworkUpdater(files, tmp.newFolder()).apply(listOf(cover("a", 1L, 1)))
        r.rollback(); r.rollback()
        assertArrayEquals(realDb, files.db)
        sizes().forEach { (n, s) -> assertEquals(s, files.size(n)) }
    }

    @Test fun refusesWithoutWritingWhenAnIthmbHasUnexplainedBytes() {
        val s = sizes(); s["F1089_1.ithmb"] = s["F1089_1.ithmb"]!! + 100
        val files = FakeArt(realDb.copyOf(), s)
        assertThrows(ArtworkDbException::class.java) { Nano6GArtworkUpdater(files, tmp.newFolder()).apply(listOf(cover("a", 1L, 1))) }
        assertTrue(files.tail.isEmpty()); assertArrayEquals(realDb, files.db)
    }

    @Test fun refusesAWrongFrameSizeAndAnUnknownDatabase() {
        val files = FakeArt(realDb.copyOf(), sizes())
        val bad = CoverInput("x", 1L, 1, Nano6GArtworkUpdater.FORMATS.associateWith { ByteArray(10) })
        assertThrows(ArtworkDbException::class.java) { Nano6GArtworkUpdater(files, tmp.newFolder()).apply(listOf(bad)) }
        val junk = FakeArt(ByteArray(500), sizes())
        assertThrows(ArtworkDbException::class.java) { Nano6GArtworkUpdater(junk, tmp.newFolder()).apply(listOf(cover("a", 1L, 1))) }
        assertTrue(files.tail.isEmpty() && junk.tail.isEmpty())
    }

    @Test fun keepsABackupOfTheDatabase() {
        val dir = tmp.newFolder()
        Nano6GArtworkUpdater(FakeArt(realDb.copyOf(), sizes()), dir).apply(listOf(cover("a", 1L, 1)))
        assertArrayEquals(realDb, java.io.File(dir, "ArtworkDB").readBytes())
    }

    @Test fun coverFramesHaveTheProfileSizesAndLittleEndianPixels() {
        val px = IntArray(300 * 200) { 0xFFFF0000.toInt() } // pure red, not square: cropped
        val f = Nano6GCoverFrames.build(px, 300, 200)
        assertEquals(setOf(1085, 1073, 1074, 1089), f.keys)
        assertEquals(115200, f[1073]!!.size); assertEquals(15488, f[1085]!!.size); assertEquals(5000, f[1074]!!.size); assertEquals(6728, f[1089]!!.size)
        assertEquals(0x00.toByte(), f[1073]!![0]); assertEquals(0xF8.toByte(), f[1073]!![1]) // red = 0xF800 stored 00 F8
    }

    /** The same channel calls the device implementation makes (append at the end, cut back), on a real file. */
    @Test fun writableChannelAppendsAndTruncatesLikeTheDeviceCode() {
        val f = tmp.newFile("x.ithmb"); f.writeBytes(ByteArray(100) { 1 })
        fun ch(block: (java.nio.channels.FileChannel) -> Unit) = java.io.FileOutputStream(java.io.RandomAccessFile(f, "rw").fd).use { block(it.channel) }
        ch { c -> c.position(c.size()); c.write(java.nio.ByteBuffer.wrap(ByteArray(50) { 2 })) }
        assertEquals(150L, f.length()); assertEquals(2.toByte(), f.readBytes()[149]); assertEquals(1.toByte(), f.readBytes()[99])
        ch { c -> c.truncate(100) }
        assertEquals(100L, f.length())
    }
}
