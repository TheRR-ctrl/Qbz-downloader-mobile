package com.qbdlx.lyrics

object Palette {
    /** The most "colourful" colour of [pixels] (ARGB): grey, near-black and near-white pixels count for little. 0 when there is none. */
    fun dominant(pixels: IntArray): Int {
        var r = 0.0; var g = 0.0; var b = 0.0; var total = 0.0
        for (p in pixels) {
            if (p ushr 24 < 200) continue
            val pr = p shr 16 and 255; val pg = p shr 8 and 255; val pb = p and 255
            val mx = maxOf(pr, pg, pb); val mn = minOf(pr, pg, pb)
            if (mx < 40 || mn > 225) continue
            val sat = (mx - mn) / 255.0
            val w = sat * sat + 0.01
            r += pr * w; g += pg * w; b += pb * w; total += w
        }
        if (total == 0.0) return 0
        return (255 shl 24) or ((r / total).toInt() shl 16) or ((g / total).toInt() shl 8) or (b / total).toInt()
    }
}
