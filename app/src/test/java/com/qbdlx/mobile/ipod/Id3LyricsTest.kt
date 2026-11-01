package com.qbdlx.mobile.ipod

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class Id3LyricsTest {
    private val audio = ByteArray(300) { (0xFF - it % 7).toByte() }
    private fun fr(v: Int, id: String, body: ByteArray): ByteArray {
        val n = body.size
        val sz = if (v == 4) ByteArray(4) { ((n shr (7 * (3 - it))) and 0x7F).toByte() } else ByteArray(4) { ((n shr (8 * (3 - it))) and 0xFF).toByte() }
        return id.toByteArray() + sz + byteArrayOf(0, 0) + body
    }
    private fun tag(v: Int, frames: ByteArray, pad: Int = 0): ByteArray {
        val n = frames.size + pad
        return "ID3".toByteArray() + byteArrayOf(v.toByte(), 0, 0) + ByteArray(4) { ((n shr (7 * (3 - it))) and 0x7F).toByte() } + frames + ByteArray(pad)
    }
    private fun run(file: ByteArray, text: String): Pair<ByteArray, Id3Lyrics.Result> {
        val out = ByteArrayOutputStream(); val r = Id3Lyrics.copy(file.inputStream(), out, text); return out.toByteArray() to r
    }
    /** (frame id -> body) of the tag, and where the audio starts. */
    private fun read(b: ByteArray): Pair<Map<String, ByteArray>, Int> {
        val v = b[3].toInt(); val size = (0..3).fold(0) { a, i -> (a shl 7) or b[6 + i].toInt() }
        val m = LinkedHashMap<String, ByteArray>(); var p = 10
        while (p + 10 <= 10 + size && b[p].toInt() != 0) {
            val len = if (v == 4) (0..3).fold(0) { a, i -> (a shl 7) or b[p + 4 + i].toInt() } else (0..3).fold(0) { a, i -> (a shl 8) or (b[p + 4 + i].toInt() and 0xFF) }
            m[String(b, p, 4)] = b.copyOfRange(p + 10, p + 10 + len); p += 10 + len
        }
        return m to 10 + size
    }

    @Test fun noTagGetsANewOneAndKeepsTheAudio() {
        val (o, r) = run(audio, "hola\nmundo")
        assertTrue(r.embedded); assertEquals(o.size.toLong(), r.bytes)
        val (frames, start) = read(o)
        assertArrayEquals(audio, o.copyOfRange(start, o.size))
        val u = frames.getValue("USLT"); assertEquals(0, u[0].toInt()); assertEquals("eng", String(u, 1, 3)); assertEquals("hola\nmundo", String(u, 5, u.size - 5, Charsets.ISO_8859_1))
    }

    @Test fun v23And24KeepOtherFramesAndReplaceOldLyrics() {
        for (v in listOf(3, 4)) {
            val old = fr(v, "TIT2", byteArrayOf(0, 'S'.code.toByte())) + fr(v, "USLT", byteArrayOf(0, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0, 'x'.code.toByte())) + fr(v, "TPE1", byteArrayOf(0, 'A'.code.toByte()))
            val (o, r) = run(tag(v, old, pad = 40) + audio, "nueva")
            assertTrue(r.embedded); assertEquals(o.size.toLong(), r.bytes)
            val (frames, start) = read(o)
            assertEquals(listOf("TIT2", "TPE1", "USLT"), frames.keys.toList())
            assertEquals("nueva", String(frames.getValue("USLT").let { it.copyOfRange(5, it.size) }, Charsets.ISO_8859_1))
            assertArrayEquals(audio, o.copyOfRange(start, o.size))
        }
    }

    @Test fun nonLatinTextUsesUtf16() {
        val (o, _) = run(audio, "日本語")
        val u = read(o).first.getValue("USLT")
        assertEquals(1, u[0].toInt())
        assertEquals("日本語", String(u, 10, u.size - 10, Charsets.UTF_16LE))
    }

    @Test fun unknownTagIsCopiedUnchanged() {
        val weird = "ID3".toByteArray() + byteArrayOf(2, 0, 0, 0, 0, 0, 5) + ByteArray(5) + audio
        val (o, r) = run(weird, "x")
        assertFalse(r.embedded); assertArrayEquals(weird, o)
    }

    @Test fun longLyricsAreCutAtALineToFitTheFirmwareLimit() {
        val long = (1..2000).joinToString("\n") { "línea número $it de la canción" }
        val (o, r) = run(audio, long)
        assertTrue(r.embedded)
        val u = read(o).first.getValue("USLT")
        assertTrue("frame body ${u.size}", u.size <= Id3Lyrics.MAX_FRAME)
        val text = String(u, 5, u.size - 5, Charsets.ISO_8859_1)
        assertTrue(long.startsWith(text)); assertTrue(text.lines().last().endsWith("canción"))
    }
}
