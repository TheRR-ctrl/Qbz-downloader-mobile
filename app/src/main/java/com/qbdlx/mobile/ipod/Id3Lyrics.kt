package com.qbdlx.mobile.ipod

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Copies an MP3 while putting the song's lyrics into its ID3v2 tag (frame USLT, plain unsynchronised lyrics), which is where the
 * iPod classic and nano 3G–5G read lyrics from. Any lyrics the file already had are replaced. Everything else in the tag and
 * all the audio are copied byte for byte. A tag this code does not understand (ID3v2.2, unsynchronisation, extended header,
 * footer, absurd size) is left alone: the file is copied unchanged and [Result.embedded] is false.
 */
object Id3Lyrics {
    class Result(val bytes: Long, val embedded: Boolean)

    private const val MAX_TAG = 16 * 1024 * 1024
    /** The iPod classic firmware reads at most 10,240 bytes of a lyrics frame (iOpenPod's firmware trace); longer text is cut at a whole line so the frame stays within it. */
    const val MAX_FRAME = 10_240

    fun copy(source: InputStream, out: OutputStream, lyrics: String, language: String = "eng"): Result {
        val input = BufferedInputStream(source, 64 * 1024)
        val head = ByteArray(10)
        val got = readFully(input, head)
        var total = 0L
        fun write(b: ByteArray, off: Int = 0, len: Int = b.size) { out.write(b, off, len); total += len }
        fun rest() { val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if (n < 0) break; write(buf, 0, n) } }

        val hasTag = got == 10 && head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()
        if (!hasTag) {
            // no tag: a fresh ID3v2.3 tag with only the lyrics, then the file as it was
            write(tag(3, listOf(frame(3, uslt(lyrics, language)))))
            if (got > 0) write(head, 0, got)
            rest()
            return Result(total, true)
        }
        val version = head[3].toInt()
        val flags = head[5].toInt() and 0xFF
        val size = syncsafe(head, 6)
        if ((version != 3 && version != 4) || flags != 0 || size < 0 || size > MAX_TAG) {
            write(head); rest(); return Result(total, false)
        }
        val body = ByteArray(size)
        if (readFully(input, body) != size) { write(head); write(body); rest(); return Result(total, false) }

        val kept = java.io.ByteArrayOutputStream()
        var p = 0
        while (p + 10 <= size && body[p].toInt() != 0) {
            val id = String(body, p, 4, Charsets.ISO_8859_1)
            val len = if (version == 4) syncsafe(body, p + 4) else be32(body, p + 4)
            if (len < 0 || p + 10 + len > size) break
            if (id != "USLT") kept.write(body, p, 10 + len)
            p += 10 + len
        }
        kept.write(frame(version, uslt(lyrics, language)))
        write(tag(version, listOf(kept.toByteArray())))
        rest()
        return Result(total, true)
    }

    /** USLT body: encoding, language, empty content descriptor, text. Latin-1 when every character fits, else UTF-16 with BOM. */
    internal fun uslt(lyrics: String, language: String): ByteArray {
        val text = fit(lyrics.replace("\r\n", "\n").replace('\r', '\n'))
        val lang = language.padEnd(3, ' ').take(3).toByteArray(Charsets.ISO_8859_1)
        val latin = text.all { it.code < 256 }
        val o = java.io.ByteArrayOutputStream()
        if (latin) {
            o.write(0); o.write(lang); o.write(0); o.write(text.toByteArray(Charsets.ISO_8859_1))
        } else {
            o.write(1); o.write(lang)
            o.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0)) // BOM + terminator: empty descriptor
            o.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte())); o.write(text.toByteArray(Charsets.UTF_16LE))
        }
        return o.toByteArray()
    }

    /** [text] cut at a line end so that its frame body (worst case: UTF-16, 2 bytes per char) fits [MAX_FRAME]. Latin-1 text gets twice the room. */
    internal fun fit(text: String): String {
        val perChar = if (text.all { it.code < 256 }) 1 else 2
        val room = (MAX_FRAME - 12) / perChar
        if (text.length <= room) return text
        val cut = text.substring(0, room)
        return cut.substring(0, cut.lastIndexOf('\n').takeIf { it > 0 } ?: room).trimEnd()
    }

    private fun frame(version: Int, body: ByteArray): ByteArray {
        val h = ByteArray(10)
        "USLT".toByteArray(Charsets.ISO_8859_1).copyInto(h)
        val n = body.size
        if (version == 4) { for (i in 0..3) h[4 + i] = ((n shr (7 * (3 - i))) and 0x7F).toByte() }
        else { for (i in 0..3) h[4 + i] = ((n shr (8 * (3 - i))) and 0xFF).toByte() }
        return h + body
    }

    private fun tag(version: Int, parts: List<ByteArray>): ByteArray {
        val frames = parts.fold(ByteArray(0)) { a, b -> a + b }
        val h = ByteArray(10)
        "ID3".toByteArray(Charsets.ISO_8859_1).copyInto(h)
        h[3] = version.toByte()
        val n = frames.size
        for (i in 0..3) h[6 + i] = ((n shr (7 * (3 - i))) and 0x7F).toByte()
        return h + frames
    }

    private fun syncsafe(b: ByteArray, o: Int): Int {
        var v = 0
        for (i in 0..3) { val x = b[o + i].toInt() and 0xFF; if (x and 0x80 != 0) return -1; v = (v shl 7) or x }
        return v
    }
    private fun be32(b: ByteArray, o: Int): Int {
        var v = 0L
        for (i in 0..3) v = (v shl 8) or (b[o + i].toLong() and 0xFF)
        return if (v > Int.MAX_VALUE) -1 else v.toInt()
    }
    private fun readFully(i: InputStream, b: ByteArray): Int {
        var n = 0
        while (n < b.size) { val r = i.read(b, n, b.size - n); if (r < 0) break; n += r }
        return n
    }
}
