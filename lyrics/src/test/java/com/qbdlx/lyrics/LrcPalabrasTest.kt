package com.qbdlx.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcPalabrasTest {
    @Test fun leeLasMarcasDePalabra() {
        val l = Lrc.parse("[00:10.00]<00:10.00>Hola <00:10.50>mi <00:11.20>amor").single()
        assertEquals("Hola mi amor", l.text)
        assertEquals(listOf(0 to 10_000L, 5 to 10_500L, 8 to 11_200L), l.marks)
        assertEquals(4, Lrc.litChars(l.text, l.timeMs, 15_000, 10_300, null, l.marks))
        assertEquals(7, Lrc.litChars(l.text, l.timeMs, 15_000, 10_600, null, l.marks))
        assertEquals(12, Lrc.litChars(l.text, l.timeMs, 15_000, 11_300, null, l.marks))
    }

    @Test fun sinMarcasSigueIgual() { assertNull(Lrc.parse("[00:10.00]Hola mi amor").single().marks) }

    @Test fun moverYGuardarConservaLasMarcas() {
        val l = Lrc.parse("[00:10.00]<00:10.00>Hola <00:10.50>mi <00:11.20>amor").single().shifted(500)
        val back = Lrc.parse(Lrc.toLrc(listOf(l))).single()
        assertEquals(10_500L, back.timeMs); assertEquals("Hola mi amor", back.text)
        assertEquals(listOf(0 to 10_500L, 5 to 11_000L, 8 to 11_700L), back.marks)
    }
}
