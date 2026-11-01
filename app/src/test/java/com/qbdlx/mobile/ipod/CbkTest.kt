package com.qbdlx.mobile.ipod

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CbkTest {
    private fun resource(name: String) = CbkTest::class.java.classLoader!!.getResourceAsStream(name)!!.use { it.readBytes() }
    private val guid = "000a270024bd17a1".chunked(2).map { it.toInt(16).toByte() }.toByteArray() // FireWire GUID of the reference device

    @Test fun reproducesTheReferenceCbkByteForByte() {
        assertArrayEquals(resource("Locations.itdb.cbk"), Cbk.build(resource("Locations.itdb"), guid))
    }

    @Test fun layoutIsSignatureThenMasterThenOneHashPerBlock() {
        val loc = resource("Locations.itdb")
        val cbk = Cbk.build(loc, guid)
        val blocks = (loc.size + Cbk.BLOCK_SIZE - 1) / Cbk.BLOCK_SIZE
        assertEquals(57 + 20 + 20 * blocks, cbk.size)
    }

    @Test fun aShortFileIsPaddedWithZerosToOneBlock() {
        val cbk = Cbk.build(ByteArray(10) { 7 }, guid)
        assertEquals(57 + 20 + 20, cbk.size)
        val padded = ByteArray(1024).also { for (i in 0 until 10) it[i] = 7 }
        assertArrayEquals(cbk, Cbk.build(padded, guid))
    }

    @Test fun aDifferentGuidGivesADifferentSignature() {
        val loc = resource("Locations.itdb")
        val other = guid.copyOf().also { it[7] = (it[7] + 1).toByte() }
        assert(!Cbk.build(loc, guid).contentEquals(Cbk.build(loc, other)))
    }
}
