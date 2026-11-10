package org.ostrichbat2k.usbdac.streaming

import org.junit.Assert.assertEquals
import org.junit.Test

class NivelesTest {
    @Test fun picoPorCanalEn24Bits() {
        // Dos tramas estéreo de 24 bits: izquierdo a media escala negativa, derecho a un cuarto.
        val buf = ByteArray(12)
        fun pon(i: Int, v: Int) { buf[i] = v.toByte(); buf[i + 1] = (v shr 8).toByte(); buf[i + 2] = (v shr 16).toByte() }
        pon(0, -0x400000); pon(3, 0x200000); pon(6, 0x100000); pon(9, -0x100000)
        val out = FloatArray(2)
        Niveles.tomar(out)
        Niveles.medirEnteros(buf, buf.size, 3, 2)
        Niveles.tomar(out)
        assertEquals(0.5f, out[0], 0.001f)
        assertEquals(0.25f, out[1], 0.001f)
        Niveles.tomar(out)
        assertEquals(0f, out[0], 0f)
    }

    @Test fun decibelios() {
        assertEquals(-6.02f, aDb(0.5f), 0.01f)
        assertEquals(-60f, aDb(0f), 0f)
    }
}
