package com.qbdlx.mobile

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** What gets written into a downloaded file (empty values are left out), mirroring the desktop app's tag set. */
class TrackTags(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val trackNumber: Int,
    val trackTotal: Int,
    val discNumber: Int,
    val discTotal: Int,
    val date: String,
    val year: String,
    val genre: String,
    val composer: String,
    val copyright: String,
    val isrc: String,
    val barcode: String,
    val label: String,
    val explicit: Boolean,
    val mediaType: String,
) {
    companion object {
        fun from(album: Album, track: Track): TrackTags {
            val date = album.releaseDate.trim()
            val year = date.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) } ?: album.year.trim()
            return TrackTags(
                title = track.displayTitle,
                artist = track.artist,
                album = if (album.version.isBlank()) album.title else "${album.title.trimEnd()} (${album.version})",
                albumArtist = album.albumArtist.ifBlank { album.artist },
                // A playlist rewrites Track.number to the list position; the tag needs the album's own number.
                trackNumber = track.albumTrackNumber.takeIf { it > 0 } ?: track.number,
                trackTotal = album.tracksCount,
                discNumber = track.disc,
                discTotal = album.mediaCount,
                date = date,
                year = year,
                genre = album.genre,
                composer = track.composer,
                copyright = album.copyright,
                isrc = track.isrc,
                barcode = album.upc,
                label = album.label.replace(Regex("\\s+"), " ").trim(),
                explicit = track.explicit,
                mediaType = album.productType.trim(),
            )
        }
    }
}

/** Cover art ready to embed. Only real JPEG/PNG data is accepted, so an HTML error page is never embedded. */
class Cover(val data: ByteArray, val mime: String, val width: Int, val height: Int) {
    companion object {
        fun sniff(b: ByteArray): Cover? = when {
            b.size > 4 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() -> {
                val (w, h) = jpegSize(b)
                Cover(b, "image/jpeg", w, h)
            }
            b.size > 24 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() &&
                b[3] == 'G'.code.toByte() && b[4] == 0x0D.toByte() && b[5] == 0x0A.toByte() &&
                b[6] == 0x1A.toByte() && b[7] == 0x0A.toByte() -> Cover(b, "image/png", be32(b, 16), be32(b, 20))
            else -> null
        }

        private fun jpegSize(b: ByteArray): Pair<Int, Int> {
            var p = 2
            while (p + 9 < b.size) {
                if (b[p] != 0xFF.toByte()) return 0 to 0
                var marker = b[p + 1].toInt() and 0xFF
                while (marker == 0xFF && p + 2 < b.size) { p++; marker = b[p + 1].toInt() and 0xFF }
                // Start-of-frame markers carry the size (C4, C8 and CC are not frames).
                if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                    return be16(b, p + 7) to be16(b, p + 5)
                }
                p += 2 + be16(b, p + 2)
            }
            return 0 to 0
        }

        private fun be16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
        private fun be32(b: ByteArray, o: Int) = (be16(b, o) shl 16) or be16(b, o + 2)
    }
}

/**
 * Writes tags into a track while it is being downloaded, with no temporary file: the first bytes are
 * inspected, a new header is built, and every audio byte after it passes through untouched.
 *
 * The format is chosen from the data, not the file name: `fLaC` gets Vorbis comments and a picture
 * block; `ID3` or an MPEG frame gets an ID3v2.3 tag (replacing any existing one); anything else is
 * written unchanged. If tags cannot be built or the header is malformed, the original bytes are written
 * as they came, so tagging can never corrupt or fail a download. Only tagging problems are swallowed:
 * errors writing to [sink] still propagate, since those are real storage failures.
 */
class TaggingOutputStream(
    private val sink: OutputStream,
    private val tags: TrackTags,
    private val cover: Cover?,
    private val onTagError: (Throwable) -> Unit = {},
    /** Most metadata we will hold in memory while looking for the end of a FLAC header. */
    private val maxHeaderBytes: Int = MAX_HEADER_BYTES,
) : OutputStream() {

    private enum class Mode { SNIFF, FLAC, ID3, SKIP, PASS }

    private class Block(val type: Int, val data: ByteArray)

    private var mode = Mode.SNIFF
    private var buf = ByteArray(8 * 1024)
    private var len = 0
    private var skipLeft = 0L
    private var flacPos = 4
    private val flacBlocks = ArrayList<Block>()

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, n: Int) {
        var o = off
        var left = n
        while (left > 0) {
            when (mode) {
                Mode.PASS -> { sink.write(b, o, left); return }
                Mode.SKIP -> {
                    val d = minOf(skipLeft, left.toLong()).toInt()
                    skipLeft -= d; o += d; left -= d
                    if (skipLeft == 0L) mode = Mode.PASS
                }
                else -> { append(b, o, left); left = 0; advance() }
            }
        }
    }

    override fun flush() { if (mode == Mode.PASS) sink.flush() }

    override fun close() {
        try {
            // A stream that ends before the header is understood (truncated or tiny): write what we held.
            if (mode != Mode.PASS && mode != Mode.SKIP && len > 0) { sink.write(buf, 0, len); len = 0 }
            mode = Mode.PASS
        } finally {
            sink.close()
        }
    }

    private fun append(b: ByteArray, off: Int, n: Int) {
        if (len + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, len + n))
        System.arraycopy(b, off, buf, len, n)
        len += n
    }

    /** Writes everything held back, unchanged, and passes the rest through. */
    private fun giveUp(error: Throwable? = null) {
        error?.let(onTagError)
        sink.write(buf, 0, len)
        len = 0
        mode = Mode.PASS
    }

    private fun advance() {
        if (mode == Mode.SNIFF) {
            if (len < 4) return
            val b0 = buf[0].toInt() and 0xFF
            val b1 = buf[1].toInt() and 0xFF
            mode = when {
                buf[0] == 'f'.code.toByte() && buf[1] == 'L'.code.toByte() && buf[2] == 'a'.code.toByte() && buf[3] == 'C'.code.toByte() -> Mode.FLAC
                buf[0] == 'I'.code.toByte() && buf[1] == 'D'.code.toByte() && buf[2] == '3'.code.toByte() -> Mode.ID3
                b0 == 0xFF && (b1 and 0xE0) == 0xE0 -> { startMp3WithoutTag(); return }
                else -> { giveUp(); return }
            }
        }
        when (mode) {
            Mode.FLAC -> advanceFlac()
            Mode.ID3 -> advanceId3()
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ FLAC

    private fun advanceFlac() {
        while (true) {
            val p = flacPos
            if (len < p + 4) return
            val header = buf[p].toInt() and 0xFF
            val last = header and 0x80 != 0
            val type = header and 0x7F
            val blen = ((buf[p + 1].toInt() and 0xFF) shl 16) or ((buf[p + 2].toInt() and 0xFF) shl 8) or (buf[p + 3].toInt() and 0xFF)
            // A block length is only 24 bits, so the cap is on what has piled up, not on one block.
            if (type == 127 || p.toLong() + 4 + blen > maxHeaderBytes) { giveUp(IllegalStateException("Unexpected FLAC metadata block")); return }
            if (len < p + 4 + blen) return
            flacBlocks += Block(type, buf.copyOfRange(p + 4, p + 4 + blen))
            flacPos = p + 4 + blen
            if (last) { finishFlac(flacPos); return }
        }
    }

    private fun finishFlac(end: Int) {
        val header = try { buildFlacHeader() } catch (e: Exception) { onTagError(e); null }
        if (header == null) { giveUp(); return }
        sink.write(header)
        sink.write(buf, end, len - end) // audio frames that arrived together with the header
        len = 0
        flacBlocks.clear()
        mode = Mode.PASS
    }

    private fun buildFlacHeader(): ByteArray {
        val first = flacBlocks.firstOrNull()
        require(first != null && first.type == 0 && first.data.size == 34) { "FLAC does not start with STREAMINFO" }

        // One VORBIS_COMMENT and our PICTURE replace the old ones; everything else (STREAMINFO, SEEKTABLE,
        // APPLICATION, CUESHEET, PADDING) is kept as is. Seek offsets are relative to the first frame.
        val blocks = ArrayList<Block>(flacBlocks.filter { it.type != 4 && it.type != 6 })
        blocks += Block(4, vorbisComment())
        pictureBlock()?.let { blocks += Block(6, it) }

        val out = ByteArrayOutputStream()
        out.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
        blocks.forEachIndexed { i, b ->
            require(b.data.size <= 0xFFFFFF) { "FLAC metadata block too large" }
            out.write((if (i == blocks.lastIndex) 0x80 else 0) or b.type)
            out.write(b.data.size ushr 16); out.write(b.data.size ushr 8); out.write(b.data.size)
            out.write(b.data)
        }
        return out.toByteArray()
    }

    /** Vorbis comment: every length in this block is LITTLE-endian (unlike the rest of FLAC), with no framing bit. */
    private fun vorbisComment(): ByteArray {
        val fields = ArrayList<Pair<String, String>>()
        fun add(key: String, value: String) { if (value.isNotBlank()) fields += key to value.trim() }
        fun count(key: String, n: Int) { if (n > 0) fields += key to n.toString() }
        add("TITLE", tags.title); add("ARTIST", tags.artist); add("ALBUM", tags.album); add("ALBUMARTIST", tags.albumArtist)
        count("TRACKNUMBER", tags.trackNumber); count("TRACKTOTAL", tags.trackTotal)
        count("DISCNUMBER", tags.discNumber); count("DISCTOTAL", tags.discTotal)
        add("DATE", tags.date.ifBlank { tags.year }); add("YEAR", tags.year)
        add("GENRE", tags.genre); add("COMPOSER", tags.composer); add("COPYRIGHT", tags.copyright)
        add("ISRC", tags.isrc); add("BARCODE", tags.barcode); add("LABEL", tags.label)
        if (tags.explicit) add("ITUNESADVISORY", "1")
        add("MEDIATYPE", tags.mediaType.uppercase())

        val out = ByteArrayOutputStream()
        val vendor = "QBDLX Mobile".toByteArray(Charsets.UTF_8)
        out.write(le32(vendor.size)); out.write(vendor)
        out.write(le32(fields.size))
        fields.forEach { (k, v) ->
            val entry = "$k=$v".toByteArray(Charsets.UTF_8)
            out.write(le32(entry.size)); out.write(entry)
        }
        return out.toByteArray()
    }

    /** PICTURE block: every field is BIG-endian. Type 3 is the front cover. */
    private fun pictureBlock(): ByteArray? {
        val c = cover ?: return null
        val mime = c.mime.toByteArray(Charsets.US_ASCII)
        val out = ByteArrayOutputStream()
        out.write(be32(3))
        out.write(be32(mime.size)); out.write(mime)
        out.write(be32(0)) // description
        out.write(be32(c.width)); out.write(be32(c.height))
        out.write(be32(if (c.width > 0) 24 else 0)); out.write(be32(0)) // colour depth, indexed colours
        out.write(be32(c.data.size)); out.write(c.data)
        return out.toByteArray().takeIf { it.size <= 0xFFFFFF } // too large for a FLAC block: tag without art
    }

    // ------------------------------------------------------------------ MP3 / ID3v2.3

    /** An MPEG stream with no ID3 header: our tag goes in front of the first frame. */
    private fun startMp3WithoutTag() {
        val tag = try { buildId3() } catch (e: Exception) { onTagError(e); null }
        if (tag != null) sink.write(tag)
        sink.write(buf, 0, len)
        len = 0
        mode = Mode.PASS
    }

    private fun advanceId3() {
        if (len < 10) return
        val size = syncsafeAt(buf, 6)
        val sane = (buf[3].toInt() in 2..4) && (6..9).all { buf[it].toInt() and 0x80 == 0 }
        if (!sane) { giveUp(IllegalStateException("Malformed ID3 header")); return }
        val footer = if (buf[3].toInt() == 4 && (buf[5].toInt() and 0x10) != 0) 10 else 0
        val oldLen = 10L + size + footer

        val tag = try { buildId3() } catch (e: Exception) { onTagError(e); null }
        if (tag == null) { giveUp(); return } // keep the original tag rather than lose it
        sink.write(tag)
        if (len >= oldLen) {
            sink.write(buf, oldLen.toInt(), len - oldLen.toInt())
            mode = Mode.PASS
        } else {
            skipLeft = oldLen - len
            mode = Mode.SKIP
        }
        len = 0
    }

    /** ID3v2.3: text as UTF-16 with BOM, plain big-endian frame sizes, syncsafe tag size, year in TYER. */
    private fun buildId3(): ByteArray {
        val frames = ByteArrayOutputStream()
        fun frame(id: String, body: ByteArray) {
            frames.write(id.toByteArray(Charsets.ISO_8859_1)); frames.write(be32(body.size)); frames.write(byteArrayOf(0, 0)); frames.write(body)
        }
        fun u16(s: String) = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + s.toByteArray(Charsets.UTF_16LE)
        fun text(id: String, value: String) { if (value.isNotBlank()) frame(id, byteArrayOf(1) + u16(value.trim())) }
        fun txxx(desc: String, value: String) {
            if (value.isNotBlank()) frame("TXXX", byteArrayOf(1) + u16(desc) + byteArrayOf(0, 0) + u16(value.trim()))
        }
        fun ofTotal(n: Int, total: Int) = if (n <= 0) "" else if (total > 0) "$n/$total" else "$n"

        text("TIT2", tags.title); text("TPE1", tags.artist); text("TPE2", tags.albumArtist); text("TALB", tags.album)
        text("TRCK", ofTotal(tags.trackNumber, tags.trackTotal)); text("TPOS", ofTotal(tags.discNumber, tags.discTotal))
        text("TYER", tags.year); text("TCON", tags.genre); text("TCOM", tags.composer); text("TCOP", tags.copyright)
        text("TSRC", tags.isrc); text("TPUB", tags.label)
        text("TMED", tags.mediaType.lowercase().replaceFirstChar { it.titlecase() })
        txxx("BARCODE", tags.barcode)
        if (tags.explicit) txxx("ITUNESADVISORY", "1")
        cover?.let { c ->
            val body = ByteArrayOutputStream()
            body.write(0) // description is Latin-1
            body.write(c.mime.toByteArray(Charsets.ISO_8859_1)); body.write(0)
            body.write(3) // front cover
            body.write(0) // empty description
            body.write(c.data)
            frame("APIC", body.toByteArray())
        }

        val out = ByteArrayOutputStream()
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0))
        val n = frames.size()
        require(n < (1 shl 28)) { "ID3 tag too large" }
        out.write(byteArrayOf(((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(), ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte()))
        out.write(frames.toByteArray())
        return out.toByteArray()
    }

    private companion object {
        /** A genuine FLAC header is tiny; this only stops a corrupt file from buffering without bound. */
        const val MAX_HEADER_BYTES = 32 * 1024 * 1024

        fun be32(n: Int) = byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())
        fun le32(n: Int) = byteArrayOf(n.toByte(), (n ushr 8).toByte(), (n ushr 16).toByte(), (n ushr 24).toByte())
        fun syncsafeAt(b: ByteArray, o: Int) =
            ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)
    }
}
