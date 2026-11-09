package org.ostrichbat2k.usbdac.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedbackRateDecoderTest {
    private fun le32(v: Long) = ByteArray(4) { ((v shr (8 * it)) and 0xFF).toByte() }

    @Test
    fun altaVelocidadPorMicrotrama() {
        // 48 kHz = 6 muestras por microtrama en 16.16
        assertEquals(48000, FeedbackRateDecoder.decode(le32(6L shl 16), 48000))
    }

    @Test
    fun porTramaDe1ms() {
        // 48 muestras por trama en 16.16
        assertEquals(48000, FeedbackRateDecoder.decode(le32(48L shl 16), 48000))
    }

    @Test
    fun basuraSeRechaza() {
        assertNull(FeedbackRateDecoder.decode(le32(1L), 48000))
        assertNull(FeedbackRateDecoder.decode(ByteArray(5), 48000))
    }
}
