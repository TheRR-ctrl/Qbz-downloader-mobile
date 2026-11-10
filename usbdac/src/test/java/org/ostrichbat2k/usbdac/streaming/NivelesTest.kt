package org.ostrichbat2k.usbdac.streaming

import org.junit.Assert.assertEquals
import org.junit.Test

class NivelesTest {
    @Test fun elLectorVaAlRitmoDelRelojYMidePorCanal() {
        Niveles.paso(48_000)
        val lector = Niveles.Lector()
        lector.avanzar(0f)                                   // se pone al día
        // Un tirón de 4800 tramas de 24 bits: izquierdo a media escala negativa, derecho a un cuarto.
        val buf = ByteArray(4800 * 6)
        fun pon(i: Int, v: Int) { buf[i] = v.toByte(); buf[i + 1] = (v shr 8).toByte(); buf[i + 2] = (v shr 16).toByte() }
        for (t in 0 until 4800) { pon(t * 6, -0x400000); pon(t * 6 + 3, 0x200000) }
        Niveles.medirEnteros(buf, buf.size, 3, 2, 1)
        val out = FloatArray(4)
        lector.avanzar(0.016f)                               // un cuadro: 768 muestras, no las 4800 de golpe
        lector.nivel(out)
        assertEquals(0.5f, out[0], 0.001f); assertEquals(0.25f, out[1], 0.001f)
        assertEquals(0.5f, out[2], 0.001f); assertEquals(0.25f, out[3], 0.001f)
        val v = FloatArray(8); lector.ventana(v)
        assertEquals(-0.125f, v[7], 0.001f)                  // mono: (-0.5 + 0.25) / 2
        lector.avanzar(1f)                                   // no pasa de lo escrito
        lector.avanzar(0.016f); lector.nivel(out)
        assertEquals(0f, out[0], 0f)                         // sin datos nuevos, silencio
    }

    @Test fun decibelios() {
        assertEquals(-6.02f, aDb(0.5f), 0.01f)
        assertEquals(-60f, aDb(0f), 0f)
    }
}
