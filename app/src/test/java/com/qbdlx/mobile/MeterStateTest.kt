package com.qbdlx.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterStateTest {
    @Test fun subeAlInstanteYCaeDespacio() {
        val st = MeterState()
        var pico = 1f
        val leer = { o: FloatArray -> o[0] = pico; o[1] = pico / 2 }
        st.avanzar(0, 0f, true, leer)
        assertEquals(0f, st.nivel[0], 0.01f)
        assertEquals(-6.02f, st.nivel[1], 0.01f)
        pico = 0f
        st.avanzar(500_000_000, 0.5f, true, leer)
        assertEquals(-12f, st.nivel[0], 0.01f)          // 24 dB/s
        assertEquals(0f, st.retenido[0], 0.01f)         // el pico se retiene 1,5 s
        st.avanzar(2_000_000_000, 0.5f, true, leer)
        assertTrue(st.retenido[0] < 0f)
        assertEquals(0f, st.maximo[0], 0.01f)           // el máximo no baja hasta reiniciar
        st.reiniciar()
        assertEquals(-60f, st.maximo[0], 0f)
    }
}
