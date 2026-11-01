package com.qbdlx.mobile.ipod

import java.security.MessageDigest

/**
 * `Locations.itdb.cbk`: the anti-tamper signature the iPod nano 6G/7G checks on its SQLite library.
 *
 *     cbk = signature(57) + master_sha1(20) + block_sha1[i](20) * N
 *
 * Locations.itdb is cut into 1024-byte blocks (the last one padded with zeros), each block is hashed with SHA-1, the
 * hashes are concatenated and hashed again to give the master hash, and the signature is hashAB of the master hash, the
 * device's FireWire GUID and a fixed 23-byte value. Only Locations.itdb is protected; the other .itdb files are not.
 * The layout was worked out by TunesReloaded and ipodsync (see THIRD_PARTY.md).
 */
object Cbk {
    const val BLOCK_SIZE = 1024

    /** The fixed randomness TunesReloaded, libgpod and the reference vectors use, which makes the file reproducible. */
    private val FIXED_RND = "ABCDEFGHIJKLMNOPQRSTUVW".toByteArray(Charsets.US_ASCII)

    fun build(locationsItdb: ByteArray, firewireGuid: ByteArray): ByteArray {
        val sha1 = { b: ByteArray -> MessageDigest.getInstance("SHA-1").digest(b) }
        val digests = ArrayList<ByteArray>()
        var i = 0
        while (i < locationsItdb.size) {
            val block = ByteArray(BLOCK_SIZE)
            locationsItdb.copyInto(block, 0, i, minOf(i + BLOCK_SIZE, locationsItdb.size))
            digests.add(sha1(block))
            i += BLOCK_SIZE
        }
        val concat = ByteArray(digests.size * 20).also { out -> digests.forEachIndexed { n, d -> d.copyInto(out, n * 20) } }
        val master = sha1(concat)
        return HashAb.sign(master, firewireGuid, FIXED_RND) + master + concat
    }
}
