package com.qbdlx.mobile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * The tag writer, checked with a reader written separately in this file so the code is not validated
 * by itself. Every case runs with 1-byte, 7-byte and large writes: a download arrives in arbitrary chunks.
 */
class AudioTagsTest {
    private val chunkSizes = listOf(1, 7, 4096, 1 shl 20)

    private fun tags() = TrackTags(
        title = "Canción — ñandú 日本 (Live)", artist = "Artista", album = "Álbum (Deluxe)", albumArtist = "Artista & Co",
        trackNumber = 3, trackTotal = 12, discNumber = 1, discTotal = 2,
        date = "1991-03-12", year = "1991", genre = "Rock", composer = "Compositor", copyright = "(c) 1991 Label",
        isrc = "USWB19900001", barcode = "0093624899927", label = "Warner Records", explicit = true, mediaType = "album",
    )

    private fun jpeg(): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0,
        1, 1, 0, 0, 1, 0, 1, 0, 0,
        0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 8, 0x01, 0x2C, 0x01, 0x90.toByte(), 3,
    ) + ByteArray(600) { (it * 13).toByte() }

    private fun cover() = Cover.sniff(jpeg())!!

    private fun run(input: ByteArray, chunk: Int, cover: Cover? = cover(), errors: MutableList<Throwable> = ArrayList()): ByteArray {
        val sink = ByteArrayOutputStream()
        TaggingOutputStream(sink, tags(), cover, onTagError = { errors += it }).use { out ->
            var o = 0
            while (o < input.size) {
                val n = minOf(chunk, input.size - o)
                out.write(input, o, n); o += n
            }
        }
        return sink.toByteArray()
    }

    // ================================================================== independent readers

    private class FlacBlock(val type: Int, val last: Boolean, val data: ByteArray)

    private fun readFlac(b: ByteArray): Pair<List<FlacBlock>, ByteArray> {
        assertEquals("fLaC", String(b, 0, 4, Charsets.US_ASCII))
        var p = 4
        val blocks = ArrayList<FlacBlock>()
        while (true) {
            val h = b[p].toInt() and 0xFF
            val len = ((b[p + 1].toInt() and 0xFF) shl 16) or ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)
            blocks += FlacBlock(h and 0x7F, h and 0x80 != 0, b.copyOfRange(p + 4, p + 4 + len))
            p += 4 + len
            if (h and 0x80 != 0) break
        }
        return blocks to b.copyOfRange(p, b.size)
    }

    private fun le32(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
    private fun be32(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    /** Vorbis comment: little-endian lengths. Returns the vendor and the KEY=value entries. */
    private fun readVorbis(d: ByteArray): Pair<String, List<String>> {
        var p = 0
        val vlen = le32(d, p); p += 4
        val vendor = String(d, p, vlen, Charsets.UTF_8); p += vlen
        val n = le32(d, p); p += 4
        val entries = ArrayList<String>()
        repeat(n) { val l = le32(d, p); p += 4; entries += String(d, p, l, Charsets.UTF_8); p += l }
        assertEquals("no framing bit and no trailing bytes", d.size, p)
        return vendor to entries
    }

    private class Picture(val type: Int, val mime: String, val desc: String, val w: Int, val h: Int, val depth: Int, val colors: Int, val data: ByteArray)

    /** PICTURE block: big-endian fields. */
    private fun readPicture(d: ByteArray): Picture {
        var p = 0
        val type = be32(d, p); p += 4
        val ml = be32(d, p); p += 4
        val mime = String(d, p, ml, Charsets.US_ASCII); p += ml
        val dl = be32(d, p); p += 4
        val desc = String(d, p, dl, Charsets.UTF_8); p += dl
        val w = be32(d, p); val h = be32(d, p + 4); val depth = be32(d, p + 8); val colors = be32(d, p + 12); p += 16
        val len = be32(d, p); p += 4
        val data = d.copyOfRange(p, p + len)
        assertEquals(d.size, p + len)
        return Picture(type, mime, desc, w, h, depth, colors, data)
    }

    private fun flacFile(blocks: List<Pair<Int, ByteArray>>, audio: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray())
        blocks.forEachIndexed { i, (type, data) ->
            out.write((if (i == blocks.lastIndex) 0x80 else 0) or type)
            out.write(data.size ushr 16); out.write(data.size ushr 8); out.write(data.size)
            out.write(data)
        }
        out.write(audio)
        return out.toByteArray()
    }

    private fun oldVorbis(): ByteArray {
        val out = ByteArrayOutputStream()
        fun le(n: Int) = byteArrayOf(n.toByte(), (n ushr 8).toByte(), (n ushr 16).toByte(), (n ushr 24).toByte())
        val vendor = "reference libFLAC 1.3.3".toByteArray()
        out.write(le(vendor.size)); out.write(vendor)
        val entries = listOf("TITLE=Old Title", "ARTIST=Old Artist", "COMMENT=left over")
        out.write(le(entries.size))
        entries.forEach { e -> val b = e.toByteArray(); out.write(le(b.size)); out.write(b) }
        return out.toByteArray()
    }

    private val streamInfo = ByteArray(34) { (it * 7 + 1).toByte() }
    private val seekTable = ByteArray(36) { (it * 3).toByte() }
    private val padding = ByteArray(300)
    private val oldPicture = ByteArray(80) { 0x55 }
    private val audio = ByteArray(6000) { (it * 31 + 7).toByte() }

    /** What a real encoder emits: STREAMINFO, its own VORBIS_COMMENT, a SEEKTABLE, an old PICTURE, PADDING last. */
    private fun typicalFlac() = flacFile(
        listOf(0 to streamInfo, 4 to oldVorbis(), 3 to seekTable, 6 to oldPicture, 1 to padding), audio,
    )

    // ================================================================== FLAC

    @Test fun flacGetsExactlyOneCommentAndOnePictureAndKeepsEverythingElse() {
        for (chunk in chunkSizes) {
            val (blocks, tail) = readFlac(run(typicalFlac(), chunk))
            val types = blocks.map { it.type }
            assertEquals("chunk $chunk: kept blocks in order, then ours", listOf(0, 3, 1, 4, 6), types)
            assertEquals("last-block flag only on the final block", listOf(false, false, false, false, true), blocks.map { it.last })
            assertArrayEquals("STREAMINFO untouched and first", streamInfo, blocks[0].data)
            assertArrayEquals("SEEKTABLE kept (offsets are relative to the first frame)", seekTable, blocks[1].data)
            assertArrayEquals(padding, blocks[2].data)
            assertArrayEquals("the audio is byte-identical", audio, tail)
        }
    }

    @Test fun theVorbisCommentHasTheRightFieldsInUtf8AndNothingEmpty() {
        for (chunk in chunkSizes) {
            val (blocks, _) = readFlac(run(typicalFlac(), chunk))
            val (vendor, entries) = readVorbis(blocks.single { it.type == 4 }.data)
            assertEquals("QBDLX Mobile", vendor)
            val map = entries.associate { it.substringBefore('=') to it.substringAfter('=') }
            assertEquals("Canción — ñandú 日本 (Live)", map["TITLE"])
            assertEquals("Artista", map["ARTIST"]); assertEquals("Álbum (Deluxe)", map["ALBUM"]); assertEquals("Artista & Co", map["ALBUMARTIST"])
            assertEquals("3", map["TRACKNUMBER"]); assertEquals("12", map["TRACKTOTAL"])
            assertEquals("1", map["DISCNUMBER"]); assertEquals("2", map["DISCTOTAL"])
            assertEquals("1991-03-12", map["DATE"]); assertEquals("1991", map["YEAR"])
            assertEquals("Rock", map["GENRE"]); assertEquals("Compositor", map["COMPOSER"]); assertEquals("(c) 1991 Label", map["COPYRIGHT"])
            assertEquals("USWB19900001", map["ISRC"]); assertEquals("0093624899927", map["BARCODE"]); assertEquals("Warner Records", map["LABEL"])
            assertEquals("1", map["ITUNESADVISORY"]); assertEquals("ALBUM", map["MEDIATYPE"])
            assertFalse("old tags must not survive", entries.any { it.contains("Old") || it.startsWith("COMMENT") })
            assertTrue("no empty values", entries.none { it.endsWith("=") })
        }
    }

    @Test fun thePictureIsAFrontCoverWithItsRealSize() {
        val (blocks, _) = readFlac(run(typicalFlac(), 7))
        val pic = readPicture(blocks.single { it.type == 6 }.data)
        assertEquals(3, pic.type); assertEquals("image/jpeg", pic.mime); assertEquals("", pic.desc)
        assertEquals(400, pic.w); assertEquals(300, pic.h); assertEquals(24, pic.depth); assertEquals(0, pic.colors)
        assertArrayEquals(jpeg(), pic.data)
        assertFalse("the old picture is gone", blocks.any { it.type == 6 && it.data.contentEquals(oldPicture) })
    }

    @Test fun withoutACoverThereIsNoPictureBlockAndTheCommentIsLast() {
        val (blocks, tail) = readFlac(run(typicalFlac(), 64, cover = null))
        assertEquals(listOf(0, 3, 1, 4), blocks.map { it.type })
        assertTrue(blocks.last().last)
        assertArrayEquals(audio, tail)
    }

    @Test fun aFlacWithOnlyStreaminfoIsTaggedToo() {
        val input = flacFile(listOf(0 to streamInfo), audio)
        for (chunk in chunkSizes) {
            val (blocks, tail) = readFlac(run(input, chunk))
            assertEquals(listOf(0, 4, 6), blocks.map { it.type })
            assertArrayEquals(audio, tail)
        }
    }

    @Test fun singleByteWritesThroughWriteIntWork() {
        val sink = ByteArrayOutputStream()
        TaggingOutputStream(sink, tags(), cover()).use { out -> typicalFlac().forEach { out.write(it.toInt() and 0xFF) } }
        val (blocks, tail) = readFlac(sink.toByteArray())
        assertEquals(listOf(0, 3, 1, 4, 6), blocks.map { it.type })
        assertArrayEquals(audio, tail)
    }

    @Test fun aFlacThatDoesNotStartWithStreaminfoIsLeftExactlyAsItCame() {
        val broken = flacFile(listOf(3 to seekTable, 0 to streamInfo), audio)
        for (chunk in chunkSizes) {
            val errors = ArrayList<Throwable>()
            assertArrayEquals(broken, run(broken, chunk, errors = errors))
            assertEquals("the problem is reported once", 1, errors.size)
        }
    }

    @Test fun aReservedBlockTypeLeavesTheFileUntouched() {
        val bad = "fLaC".toByteArray() + byteArrayOf(0xFF.toByte(), 0, 0, 4, 1, 2, 3, 4) + audio
        for (chunk in chunkSizes) assertArrayEquals(bad, run(bad, chunk))
    }

    @Test fun aTruncatedFlacIsWrittenAsReceived() {
        val whole = typicalFlac()
        for (cut in listOf(2, 4, 6, 20, 60, 150, 400)) {
            val part = whole.copyOf(cut)
            for (chunk in listOf(1, 7, 4096)) assertArrayEquals("cut at $cut, chunk $chunk", part, run(part, chunk))
        }
    }

    @Test fun aFlacHeaderBiggerThanTheMemoryCapIsLeftUntouchedAndReported() {
        val input = typicalFlac() // its metadata is a few hundred bytes
        val errors = ArrayList<Throwable>()
        val sink = ByteArrayOutputStream()
        TaggingOutputStream(sink, tags(), cover(), { errors += it }, maxHeaderBytes = 200).use { it.write(input) }
        assertArrayEquals("written exactly as received", input, sink.toByteArray())
        assertEquals(1, errors.size)
    }

    // ================================================================== other formats pass through

    @Test fun unknownDataIsWrittenUnchanged() {
        val wav = "RIFF".toByteArray() + ByteArray(2000) { it.toByte() }
        for (chunk in chunkSizes) assertArrayEquals(wav, run(wav, chunk))
        assertArrayEquals(ByteArray(0), run(ByteArray(0), 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), run(byteArrayOf(1, 2, 3), 1))
    }

    // ================================================================== MP3 / ID3v2.3

    private class Id3(val version: Int, val tagSize: Int, val frames: List<Triple<String, Int, ByteArray>>)

    /** ID3v2.3 reader: the header size is syncsafe, frame sizes are plain big-endian. */
    private fun readId3(b: ByteArray): Id3 {
        assertEquals("ID3", String(b, 0, 3, Charsets.US_ASCII))
        val size = ((b[6].toInt() and 0x7F) shl 21) or ((b[7].toInt() and 0x7F) shl 14) or ((b[8].toInt() and 0x7F) shl 7) or (b[9].toInt() and 0x7F)
        var p = 10
        val frames = ArrayList<Triple<String, Int, ByteArray>>()
        while (p < 10 + size) {
            val id = String(b, p, 4, Charsets.ISO_8859_1)
            val len = be32(b, p + 4)
            frames += Triple(id, (b[p + 8].toInt() shl 8) or b[p + 9].toInt(), b.copyOfRange(p + 10, p + 10 + len))
            p += 10 + len
        }
        assertEquals("frames fill the declared size exactly", 10 + size, p)
        return Id3(b[3].toInt(), 10 + size, frames)
    }

    private fun Id3.text(id: String): String {
        val body = frames.single { it.first == id }.third
        assertEquals("UTF-16 with BOM", 1, body[0].toInt())
        assertEquals(0xFF, body[1].toInt() and 0xFF); assertEquals(0xFE, body[2].toInt() and 0xFF)
        return String(body, 3, body.size - 3, Charsets.UTF_16LE)
    }

    private val mp3Audio = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64) + ByteArray(3000) { (it * 17 + 3).toByte() }

    private fun syncsafe(n: Int) = byteArrayOf(((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(), ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte())

    @Test fun anMp3WithoutATagGetsOneInFrontAndKeepsItsAudio() {
        for (chunk in chunkSizes) {
            val out = run(mp3Audio, chunk)
            val id3 = readId3(out)
            assertEquals(3, id3.version)
            assertArrayEquals("audio identical after the tag", mp3Audio, out.copyOfRange(id3.tagSize, out.size))
            assertEquals("Canción — ñandú 日本 (Live)", id3.text("TIT2"))
            assertEquals("Artista", id3.text("TPE1")); assertEquals("Artista & Co", id3.text("TPE2")); assertEquals("Álbum (Deluxe)", id3.text("TALB"))
            assertEquals("3/12", id3.text("TRCK")); assertEquals("1/2", id3.text("TPOS")); assertEquals("1991", id3.text("TYER"))
            assertEquals("Rock", id3.text("TCON")); assertEquals("Compositor", id3.text("TCOM")); assertEquals("(c) 1991 Label", id3.text("TCOP"))
            assertEquals("USWB19900001", id3.text("TSRC")); assertEquals("Warner Records", id3.text("TPUB")); assertEquals("Album", id3.text("TMED"))
            assertTrue("no v2.4-only frames in a v2.3 tag", id3.frames.none { it.first == "TDRC" || it.first == "TDRL" })
        }
    }

    @Test fun theId3PictureIsAFrontCoverAndCustomFieldsUseTxxx() {
        val out = run(mp3Audio, 7)
        val id3 = readId3(out)
        val apic = id3.frames.single { it.first == "APIC" }.third
        assertEquals("description encoding is Latin-1", 0, apic[0].toInt())
        val mimeEnd = (1 until apic.size).first { apic[it].toInt() == 0 }
        assertEquals("image/jpeg", String(apic, 1, mimeEnd - 1, Charsets.ISO_8859_1))
        assertEquals("front cover", 3, apic[mimeEnd + 1].toInt())
        assertEquals("empty description + terminator", 0, apic[mimeEnd + 2].toInt())
        assertArrayEquals(jpeg(), apic.copyOfRange(mimeEnd + 3, apic.size))

        val txxx = id3.frames.filter { it.first == "TXXX" }.map { it.third }
        assertEquals(2, txxx.size)
        fun decode(body: ByteArray): Pair<String, String> {
            assertEquals(1, body[0].toInt())
            var sep = 3
            while (!(body[sep].toInt() == 0 && body[sep + 1].toInt() == 0 && (sep - 3) % 2 == 0)) sep++
            val desc = String(body, 3, sep - 3, Charsets.UTF_16LE)
            return desc to String(body, sep + 2 + 2, body.size - sep - 4, Charsets.UTF_16LE) // skip terminator and the value's BOM
        }
        assertEquals(mapOf("BARCODE" to "0093624899927", "ITUNESADVISORY" to "1"), txxx.map(::decode).toMap())
    }

    @Test fun anExistingId3v23TagIsReplacedNotStacked() {
        val oldBody = ByteArray(137) { 0x41 }
        val old = "ID3".toByteArray() + byteArrayOf(3, 0, 0) + syncsafe(oldBody.size) + oldBody
        val input = old + mp3Audio
        for (chunk in chunkSizes) {
            val out = run(input, chunk)
            val id3 = readId3(out)
            assertArrayEquals("chunk $chunk: old tag gone, audio intact", mp3Audio, out.copyOfRange(id3.tagSize, out.size))
            assertEquals("Artista", id3.text("TPE1"))
        }
    }

    @Test fun anExistingId3v24TagWithAFooterIsSkippedWholeIncludingTheFooter() {
        val oldBody = ByteArray(40) { 0x42 }
        val old = "ID3".toByteArray() + byteArrayOf(4, 0, 0x10) + syncsafe(oldBody.size) + oldBody + ByteArray(10) { 0x33 }
        for (chunk in chunkSizes) {
            val out = run(old + mp3Audio, chunk)
            val id3 = readId3(out)
            assertArrayEquals("chunk $chunk", mp3Audio, out.copyOfRange(id3.tagSize, out.size))
        }
    }

    @Test fun anExistingTagLargerThanTheFirstWritesIsSkippedAcrossWrites() {
        val oldBody = ByteArray(5000) { 0x44 }
        val old = "ID3".toByteArray() + byteArrayOf(3, 0, 0) + syncsafe(oldBody.size) + oldBody
        val out = run(old + mp3Audio, 64)
        val id3 = readId3(out)
        assertArrayEquals(mp3Audio, out.copyOfRange(id3.tagSize, out.size))
    }

    @Test fun aMalformedId3HeaderLeavesTheFileUntouched() {
        val bad = "ID3".toByteArray() + byteArrayOf(3, 0, 0, 0x80.toByte(), 0, 0, 5) + ByteArray(100) { 1 }
        for (chunk in chunkSizes) assertArrayEquals(bad, run(bad, chunk))
    }

    @Test fun aTruncatedId3HeaderIsWrittenAsReceived() {
        val part = "ID3".toByteArray() + byteArrayOf(3, 0, 0, 0)
        assertArrayEquals(part, run(part, 1))
    }

    // ================================================================== cover sniffing

    @Test fun onlyRealJpegAndPngCoversAreAccepted() {
        val j = Cover.sniff(jpeg())!!
        assertEquals("image/jpeg", j.mime); assertEquals(400, j.width); assertEquals(300, j.height)

        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A) +
            byteArrayOf(0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(), 0, 0, 0x02, 0x58, 0, 0, 0x01, 0x90.toByte()) + ByteArray(20)
        val p = Cover.sniff(png)!!
        assertEquals("image/png", p.mime); assertEquals(600, p.width); assertEquals(400, p.height)

        assertNull(Cover.sniff("<html>Service unavailable</html>".toByteArray()))
        assertNull(Cover.sniff(ByteArray(0)))
        assertNull(Cover.sniff(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
    }

    // ================================================================== what goes into the tags

    private fun album() = Album("a1", "Out of Time", "R.E.M.", null, 11, "1991", "16/44", false)
    private fun track() = Track(9L, "Losing My Religion", null, 7, 1, "R.E.M.", 269, true, "16/44", false)

    @Test fun aPlaylistTrackKeepsItsOwnAlbumNumberNotThePlaylistPosition() {
        val inPlaylist = track().copy(number = 41, albumTrackNumber = 3) // the playlist rewrote number to its position
        assertEquals(3, TrackTags.from(album(), inPlaylist).trackNumber)
        assertEquals("no album number known: fall back", 7, TrackTags.from(album(), track()).trackNumber)
    }

    @Test fun titlesAlbumsAndDatesAreFormattedLikeTheDesktopApp() {
        val t = TrackTags.from(
            album().copy(version = "Deluxe", releaseDate = "1991-03-12", label = "  Warner   Records ", albumArtist = ""),
            track().copy(version = "Live"),
        )
        assertEquals("Losing My Religion (Live)", t.title)
        assertEquals("Out of Time (Deluxe)", t.album)
        assertEquals("1991-03-12", t.date); assertEquals("1991", t.year)
        assertEquals("Warner Records", t.label)
        assertEquals("falls back to the album's artist", "R.E.M.", t.albumArtist)
    }

    @Test fun withoutAFullDateTheYearStillFillsTheDateTag() {
        val t = TrackTags.from(album(), track())
        assertEquals("", t.date); assertEquals("1991", t.year)
        val (blocks, _) = readFlac(
            ByteArrayOutputStream().also { s -> TaggingOutputStream(s, t, null).use { it.write(flacFile(listOf(0 to streamInfo), audio)) } }.toByteArray(),
        )
        val entries = readVorbis(blocks.single { it.type == 4 }.data).second
        assertTrue(entries.contains("DATE=1991")); assertTrue(entries.contains("YEAR=1991"))
        assertTrue("ITUNESADVISORY is written only for explicit tracks", entries.none { it.startsWith("ITUNESADVISORY") })
        assertNotNull(entries.firstOrNull { it.startsWith("TRACKNUMBER=7") })
    }
}
