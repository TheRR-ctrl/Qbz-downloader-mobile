package org.ostrichbat2k.usbdac.streaming

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmRingBufferTest {
    @Test fun leeLoEscritoConVueltaAlFinal() {
        val rb = PcmRingBuffer(8)
        assertEquals(6, rb.write(ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5, 6))))
        val out = ByteArray(4)
        assertEquals(4, rb.readOrZeroPad(out))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), out)
        assertEquals(6, rb.write(ByteBuffer.wrap(byteArrayOf(7, 8, 9, 10, 11, 12))))
        assertEquals(0, rb.availableToWrite())
        val out2 = ByteArray(10)
        assertEquals(8, rb.readOrZeroPad(out2))
        assertArrayEquals(byteArrayOf(5, 6, 7, 8, 9, 10, 11, 12, 0, 0), out2)
    }

    @Test fun clearVaciaYNoSeLeeLoViejo() {
        val rb = PcmRingBuffer(8)
        rb.write(ByteBuffer.wrap(byteArrayOf(1, 2, 3)))
        rb.clear()
        assertEquals(0, rb.availableToRead())
        val out = ByteArray(3) { 9 }
        assertEquals(0, rb.readOrZeroPad(out))
        assertArrayEquals(byteArrayOf(0, 0, 0), out)
        rb.write(ByteBuffer.wrap(byteArrayOf(4, 5)))
        assertEquals(2, rb.readOrZeroPad(out, 2))
        assertEquals(4.toByte(), out[0])
    }
}
