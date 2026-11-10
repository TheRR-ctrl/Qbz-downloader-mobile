package org.ostrichbat2k.usbdac.streaming

import org.junit.Assert.assertEquals
import org.junit.Test

class GananciaTest {
    private fun le(v: Int, n: Int) = ByteArray(n) { (v shr (8 * it)).toByte() }
    private fun leer(b: ByteArray, off: Int, n: Int): Int {
        var v = 0L
        for (i in 0 until n) v = v or ((b[off + i].toLong() and 0xFF) shl (8 * i))
        val bit = 1L shl (n * 8 - 1)
        return (if (v and bit != 0L) v - (bit shl 1) else v).toInt()
    }

    @Test fun mitadEn16Y32Bits() {
        val b16 = le(1000, 2) + le(-1000, 2)
        aplicarGanancia(b16, b16.size, 2, 0.5f)
        assertEquals(500, leer(b16, 0, 2)); assertEquals(-500, leer(b16, 2, 2))
        val b32 = le(1_000_000_000, 4) + le(-1_000_000_000, 4)
        aplicarGanancia(b32, b32.size, 4, 0.5f)
        assertEquals(500_000_000, leer(b32, 0, 4)); assertEquals(-500_000_000, leer(b32, 4, 4))
    }

    @Test fun subirRecortaSinDarLaVuelta() {
        val b = le(30000, 2) + le(-30000, 2)
        aplicarGanancia(b, b.size, 2, 2f)
        assertEquals(32767, leer(b, 0, 2)); assertEquals(-32768, leer(b, 2, 2))
    }
}
