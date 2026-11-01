package com.qbdlx.mobile.ipod

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Nano6GArtworkEncoderTest {
    @Test fun exposesFourObservedNano6GProfiles() {
        assertEquals(
            listOf("F1073_1.ithmb" to 240, "F1085_1.ithmb" to 88, "F1074_1.ithmb" to 50, "F1089_1.ithmb" to 58),
            Nano6GArtworkEncoder.profiles.map { it.fileName to it.width }
        )
        assertTrue(Nano6GArtworkEncoder.profiles.all { it.width == it.height })
    }

    @Test fun encodesRgb565LittleEndian() {
        val p = Nano6GArtworkEncoder.Profile("test", 3, 1)
        val bytes = Nano6GArtworkEncoder.encodeArgb8888(
            intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt()), p
        )
        assertArrayEquals(byteArrayOf(0x00, 0xf8.toByte(), 0xe0.toByte(), 0x07, 0x1f, 0x00), bytes)
    }

    @Test fun rejectsWrongPixelCount() {
        val p = Nano6GArtworkEncoder.Profile("test", 2, 2)
        assertFalse(runCatching { Nano6GArtworkEncoder.encodeArgb8888(intArrayOf(0), p) }.isSuccess)
    }

    @Test fun frameSizeMatchesProfile() {
        Nano6GArtworkEncoder.profiles.forEach { p ->
            assertEquals(p.width * p.height * 2, p.bytesPerFrame)
            assertTrue(Nano6GArtworkEncoder.validateFrame(ByteArray(p.bytesPerFrame), p))
            assertFalse(Nano6GArtworkEncoder.validateFrame(ByteArray(p.bytesPerFrame - 1), p))
        }
    }
}
