package com.qbdlx.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterStateTest {
    @Test fun laBarraSigueLaEnergiaYLaMarcaElPico() {
        val st = MeterState()
        var pico = 1f; var rms = 0.5f
        val leer = { o: FloatArray -> o[0] = pico; o[1] = pico; o[2] = rms; o[3] = rms / 2 }
        st.avanzar(0, 0f, true, leer)
        assertEquals(-6.02f, st.nivel[0], 0.01f)
        assertEquals(-12.04f, st.nivel[1], 0.01f)
        assertEquals(0f, st.retenido[0], 0.01f)
        pico = 0f; rms = 0f
        st.avanzar(100_000_000, 0.1f, true, leer)
        assertEquals(-12.02f, st.nivel[0], 0.01f)       // 60 dB/s
        assertEquals(0f, st.retenido[0], 0.01f)         // el pico se retiene 1 s
        st.avanzar(1_500_000_000, 0.1f, true, leer)
        assertTrue(st.retenido[0] < 0f)
        assertEquals(0f, st.maximo[0], 0.01f)           // el máximo no baja hasta reiniciar
        st.reiniciar()
        assertEquals(-60f, st.maximo[0], 0f)
    }

    @Test fun unTonoEnciendeSuBanda() {
        val e = Espectro()
        // Bin 64 de 1024 (3 kHz a 48 kHz), a fondo de escala.
        e.avanzar(0f, true) { o -> for (i in o.indices) o[i] = kotlin.math.sin(2 * Math.PI * 64 * i / 1024).toFloat() }
        val max = e.barras.indices.maxByOrNull { e.barras[it] }!!
        assertEquals(1f, e.barras[max], 0.001f)
        assertTrue(e.barras[0] < 0.2f)
        assertTrue(max in 18..21)
    }

    @Test fun elAmbitoArrancaEnUnCrucePorCero() {
        val e = Espectro()
        e.onda(true) { o -> for (i in o.indices) o[i] = kotlin.math.sin(2 * Math.PI * 8 * (i + 37) / 1024).toFloat() }
        assertTrue(kotlin.math.abs(e.trazo[0]) < 0.06f && e.trazo[4] > e.trazo[0])
    }
}
