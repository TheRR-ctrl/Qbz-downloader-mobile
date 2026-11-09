package com.qbdlx.lyrics

import com.qbdlx.lyrics.karaoke.Estereo
import com.qbdlx.lyrics.karaoke.PistaSinVoz
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PistaSinVozTest {
    private val rate = 44100
    private fun rms(d: FloatArray, from: Int) = Math.sqrt((from until d.size).sumOf { (d[it] * d[it]).toDouble() } / (d.size - from))

    @Test fun laVozCentradaDesaparece() {
        val n = rate
        val d = FloatArray(n * 2) { i -> 0.5f * sin(2 * PI * 1000 * (i / 2) / rate).toFloat() } // 1 kHz idéntico en L y R
        val out = PistaSinVoz.quitarVoz(Estereo(rate, d))
        assertTrue(rms(out.data, rate) < 0.2 * rms(d, rate)) // el paso-bajo de 150 Hz deja pasar ~15 % de 1 kHz
    }

    @Test fun loLateralSeConserva() {
        val n = rate
        val d = FloatArray(n * 2) { i -> if (i % 2 == 0) 0.5f * sin(2 * PI * 1000 * (i / 2) / rate).toFloat() else 0f } // solo en L
        val out = PistaSinVoz.quitarVoz(Estereo(rate, d))
        assertTrue(rms(out.data, rate) > 0.4 * rms(d, rate))
    }

    @Test fun elBajoCentradoSeConserva() {
        val n = rate * 2
        val d = FloatArray(n * 2) { i -> 0.5f * sin(2 * PI * 50 * (i / 2) / rate).toFloat() }
        val out = PistaSinVoz.quitarVoz(Estereo(rate, d))
        assertTrue(rms(out.data, rate) > 0.6 * rms(d, rate))
    }

    @Test fun wavTieneCabeceraYTamanoCorrectos() {
        val bos = java.io.ByteArrayOutputStream()
        PistaSinVoz.escribirWav(Estereo(rate, FloatArray(200)), bos)
        val b = bos.toByteArray()
        assertEquals(44 + 400, b.size)
        assertEquals("RIFF", String(b, 0, 4)); assertEquals("WAVE", String(b, 8, 4))
    }
}
