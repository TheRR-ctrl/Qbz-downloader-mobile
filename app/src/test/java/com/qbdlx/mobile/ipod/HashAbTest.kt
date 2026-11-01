package com.qbdlx.mobile.ipod

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun String.hex(): ByteArray = ByteArray(length / 2) { substring(2 * it, 2 * it + 2).toInt(16).toByte() }
private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

class HashAbTest {
    /** The fixed 23 bytes the reference vectors were made with. */
    private val rnd = "ABCDEFGHIJKLMNOPQRSTUVW".toByteArray(Charsets.US_ASCII)

    private fun vectors(): JSONArray =
        JSONArray(HashAbTest::class.java.classLoader!!.getResourceAsStream("hashab-vectors.json")!!.bufferedReader().readText())

    @Test fun matchesAllReferenceVectors() {
        val v = vectors()
        assertEquals(100, v.length())
        for (i in 0 until v.length()) {
            val o = v.getJSONObject(i)
            val got = HashAb.sign(o.getString("sha1").hex(), o.getString("uuid").hex(), rnd).hex()
            assertEquals("vector $i", o.getString("target"), got)
        }
    }

    @Test fun theSignatureIs57BytesAndStartsWith0300() {
        val o = vectors().getJSONObject(0)
        val sig = HashAb.sign(o.getString("sha1").hex(), o.getString("uuid").hex(), rnd)
        assertEquals(57, sig.size)
        assertTrue(sig[0].toInt() == 3 && sig[1].toInt() == 0)
    }

    @Test fun differentRandomnessGivesADifferentSignature() {
        val o = vectors().getJSONObject(0)
        val other = ByteArray(23) { (it * 7 + 1).toByte() }
        val a = HashAb.sign(o.getString("sha1").hex(), o.getString("uuid").hex(), rnd).hex()
        val b = HashAb.sign(o.getString("sha1").hex(), o.getString("uuid").hex(), other).hex()
        assertTrue(a != b)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsWrongLengths() { HashAb.sign(ByteArray(19), ByteArray(8), rnd) }
}
