package com.qbdlx.lyrics

import org.junit.Assert.*
import org.junit.Test

class PaletteTest {
    private fun px(r: Int, g: Int, b: Int) = (255 shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun picksTheColourfulPixelsOverGreysAndBlack() {
        val c = Palette.dominant(intArrayOf(px(0, 0, 0), px(128, 128, 128), px(230, 30, 30), px(240, 240, 240)))
        assertTrue((c shr 16 and 255) > 220)
        assertTrue((c shr 8 and 255) < 60)
    }

    @Test fun zeroWhenThereIsNoColour() {
        assertEquals(0, Palette.dominant(intArrayOf(px(0, 0, 0), px(250, 250, 250))))
    }
}
