package com.qbdlx.mobile.ipod

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Thrown when an ArtworkDB does not look exactly like the nano 6G files analysed (docs/ARTWORKDB_NANO6G.md): nothing is written then. */
class ArtworkDbException(message: String) : Exception(message)

/** One frame of an image: where it is inside its .ithmb (mhni record). */
class ArtFrame(val format: Int, val offset: Long, val size: Int, val width: Int, val height: Int)

/** An image (mhii): its id, the library item that created it, the raw bytes (kept verbatim) and its frames. */
class ArtImage(val id: Int, val songPid: Long, val raw: ByteArray, val frames: List<ArtFrame>)

/** What to add: the frames' offsets inside each .ithmb, keyed by format id. */
class NewArtwork(val songPid: Long, val offsets: Map<Int, Long>, val references: Int)

/**
 * Reader/writer of the nano 6G ArtworkDB. Existing records (and every field not understood) are kept byte-for-byte;
 * new ones are cloned from an existing five-frame record so unknown fields get the values real records have.
 * Structure (little-endian): mhfd → mhsd#1(mhli → mhii…), mhsd#2(mhla, empty), mhsd#3(mhlf → mhif×4).
 */
class Nano6GArtworkDb private constructor(
    private val head: ByteArray,          // mhfd header (132 B)
    private val imageSectionHead: ByteArray, // mhsd (96) + mhli (92)
    private val images: MutableList<ArtImage>,
    private val tail: ByteArray,          // mhsd#2 and mhsd#3, untouched
    val profiles: Map<Int, Int>,          // format id -> frame size (from the mhif records)
    private val template: ArtImage,
) {
    val all: List<ArtImage> get() = images
    val nextId: Int get() = u32(head, NEXT_ID).toInt()

    fun find(id: Int) = images.firstOrNull { it.id == id }

    /** Adds one image with one frame per profile. Returns the new record. Throws on a collision or an unusable template. */
    fun add(n: NewArtwork): ArtImage {
        val id = nextId
        if (images.any { it.id == id }) throw ArtworkDbException("next id $id already used")
        val raw = ByteArray(template.raw.size)
        template.raw.copyInto(raw)
        val b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(16, id)
        b.putLong(20, n.songPid)
        b.putInt(48, 0)
        b.putInt(56, n.references.coerceAtLeast(1))
        var pos = MHII_HEADER
        val frames = ArrayList<ArtFrame>()
        for (f in template.frames) {
            val off = n.offsets[f.format] ?: throw ArtworkDbException("no offset for format ${f.format}")
            val mhni = pos + MHOD_HEADER
            b.putInt(mhni + 20, off.toInt()) // offsets are u32 in the file
            frames += ArtFrame(f.format, off, f.size, f.width, f.height)
            pos += u32(raw, pos + 8).toInt()
        }
        val img = ArtImage(id, n.songPid, raw, frames)
        images += img
        ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN).putInt(NEXT_ID, id + 1)
        return img
    }

    fun serialize(): ByteArray {
        val imagesLen = images.sumOf { it.raw.size }
        val sectionLen = imageSectionHead.size + imagesLen
        val total = head.size + sectionLen + tail.size
        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.put(head); out.putInt(8, total)
        val sh = imageSectionHead.copyOf()
        val sb = ByteBuffer.wrap(sh).order(ByteOrder.LITTLE_ENDIAN)
        sb.putInt(8, sectionLen)               // mhsd total
        sb.putInt(MHSD_HEADER + 8, images.size) // mhli child count
        out.position(head.size); out.put(sh)
        images.forEach { out.put(it.raw) }
        out.put(tail)
        return out.array()
    }

    /** Checks every reference: ids unique and below the next id, frames inside the .ithmb files, none overlapping. Returns the problems. */
    fun validate(ithmbSizes: Map<Int, Long>): List<String> {
        val bad = ArrayList<String>()
        if (images.map { it.id }.toSet().size != images.size) bad += "duplicate image ids"
        if (images.any { it.id >= nextId }) bad += "an id is not below next id $nextId"
        val seen = HashSet<Pair<Int, Long>>()
        for (img in images) for (f in img.frames) {
            val size = profiles[f.format]
            if (size == null || size != f.size) { bad += "image ${img.id}: format ${f.format} not a known profile/size"; continue }
            val len = ithmbSizes[f.format]
            if (len == null || f.offset + f.size > len) bad += "image ${img.id}: frame ${f.format}@${f.offset} outside its .ithmb"
            if (f.offset % f.size != 0L) bad += "image ${img.id}: frame ${f.format}@${f.offset} not aligned"
            if (!seen.add(f.format to f.offset)) bad += "image ${img.id}: frame ${f.format}@${f.offset} shared with another record"
        }
        return bad
    }

    companion object {
        const val NEXT_ID = 28
        const val MHSD_HEADER = 96
        const val MHLI_HEADER = 92
        const val MHII_HEADER = 152
        const val MHOD_HEADER = 24
        const val MHNI_HEADER = 76

        private fun u32(a: ByteArray, o: Int) = ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN).getInt(o).toLong() and 0xFFFFFFFFL
        private fun u16(a: ByteArray, o: Int) = ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN).getShort(o).toInt() and 0xFFFF
        private fun tag(a: ByteArray, o: Int) = String(a, o, 4, Charsets.ISO_8859_1)
        private fun need(c: Boolean, m: () -> String) { if (!c) throw ArtworkDbException(m()) }

        fun parse(d: ByteArray): Nano6GArtworkDb {
            need(d.size > 132 && tag(d, 0) == "mhfd", { "not an ArtworkDB" })
            need(u32(d, 4) == 132L && u32(d, 8) == d.size.toLong(), { "unexpected mhfd size" })
            need(u32(d, 20) == 3L, { "expected 3 datasets" })
            // dataset 1: mhli with the images
            val s1 = 132
            need(tag(d, s1) == "mhsd" && u32(d, s1 + 4) == 96L && u32(d, s1 + 12) == 1L, { "dataset 1 not found" })
            val s1end = s1 + u32(d, s1 + 8).toInt()
            need(s1end <= d.size, { "dataset 1 overruns the file" })
            val li = s1 + MHSD_HEADER
            need(tag(d, li) == "mhli" && u32(d, li + 4) == 92L, { "mhli not found" })
            val count = u32(d, li + 8).toInt()
            var o = li + MHLI_HEADER
            val images = ArrayList<ArtImage>(count)
            repeat(count) {
                need(o + MHII_HEADER <= s1end && tag(d, o) == "mhii" && u32(d, o + 4) == 152L, { "mhii expected at $o" })
                val len = u32(d, o + 8).toInt()
                need(len > MHII_HEADER && o + len <= s1end, { "mhii at $o has a bad size" })
                images += parseImage(d.copyOfRange(o, o + len), o)
                o += len
            }
            need(o == s1end, { "dataset 1 has ${s1end - o} unexplained bytes" })
            // dataset 2 (empty mhla) and 3 (mhlf with the four mhif) are kept as they are
            val tailStart = s1end
            need(tag(d, tailStart) == "mhsd" && u32(d, tailStart + 12) == 2L, { "dataset 2 not found" })
            val s2end = tailStart + u32(d, tailStart + 8).toInt()
            need(tag(d, tailStart + 96) == "mhla" && u32(d, tailStart + 96 + 8) == 0L && s2end == tailStart + 188, { "dataset 2 is not an empty mhla" })
            need(tag(d, s2end) == "mhsd" && u32(d, s2end + 12) == 3L, { "dataset 3 not found" })
            need(s2end + u32(d, s2end + 8).toInt() == d.size, { "dataset 3 does not end the file" })
            val lf = s2end + 96
            need(tag(d, lf) == "mhlf", { "mhlf not found" })
            var p = lf + 92
            val profiles = LinkedHashMap<Int, Int>()
            repeat(u32(d, lf + 8).toInt()) {
                need(tag(d, p) == "mhif" && u32(d, p + 4) == 124L && u32(d, p + 8) == 124L, { "mhif expected at $p" })
                profiles[u32(d, p + 16).toInt()] = u32(d, p + 20).toInt(); p += 124
            }
            need(p == d.size, { "unexplained bytes after mhlf" })
            val template = images.firstOrNull { it.frames.map { f -> f.format } == listOf(1085, 1073, 1074, 1089) }
                ?: throw ArtworkDbException("no five-record template (1085,1073,1074,1089) to clone")
            val db = Nano6GArtworkDb(d.copyOfRange(0, 132), d.copyOfRange(s1, li + MHLI_HEADER), images, d.copyOfRange(tailStart, d.size), profiles, template)
            val maxId = images.maxOfOrNull { it.id } ?: 99
            need(db.nextId > maxId, { "next id ${db.nextId} collides with existing id $maxId" })
            need(images.map { it.id }.toSet().size == images.size, { "duplicate image ids" })
            return db
        }

        private fun parseImage(r: ByteArray, at: Int): ArtImage {
            val n = u32(r, 12).toInt()
            var p = MHII_HEADER
            val frames = ArrayList<ArtFrame>()
            repeat(n) {
                need(p + MHOD_HEADER <= r.size && tag(r, p) == "mhod" && u32(r, p + 4) == 24L, { "mhod expected in mhii at $at" })
                val len = u32(r, p + 8).toInt()
                need(len >= MHOD_HEADER && p + len <= r.size, { "mhod size bad in mhii at $at" })
                when (u32(r, p + 12).toInt()) {
                    2 -> {
                        val m = p + MHOD_HEADER
                        need(tag(r, m) == "mhni" && u32(r, m + 4) == 76L && u32(r, m + 8) == 140L && len == 164, { "unknown mhni layout in mhii at $at" })
                        val str = m + 76
                        need(tag(r, str) == "mhod" && u32(r, str + 12) == 3L, { "mhni filename mhod missing in mhii at $at" })
                        val size = u32(r, m + 24).toInt()
                        need(u32(r, m + 40).toInt() == size, { "mhni sizes differ in mhii at $at" })
                        frames += ArtFrame(u32(r, m + 16).toInt(), u32(r, m + 20), size, u16(r, m + 34), u16(r, m + 32))
                    }
                    6 -> need(tag(r, p + 24) == "mhaf" && len == 120, { "unknown mhod type 6 layout in mhii at $at" })
                    else -> throw ArtworkDbException("unknown mhod type ${u32(r, p + 12)} in mhii at $at")
                }
                p += len
            }
            need(p == r.size, { "mhii at $at has unexplained bytes" })
            return ArtImage(u32(r, 16).toInt(), ByteBuffer.wrap(r).order(ByteOrder.LITTLE_ENDIAN).getLong(20), r, frames)
        }
    }
}
