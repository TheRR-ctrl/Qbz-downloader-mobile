package com.qbdlx.mobile

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The player's background: [top] fading to a slightly darker [bottom]. Both are opaque ARGB. */
class CoverTone(val top: Int, val bottom: Int)

/**
 * The background tone for a cover, like the player's background that follows the artwork. Plain Kotlin on an array
 * of ARGB pixels (a small thumbnail is enough) so it can be unit-tested; the Android part only hands the pixels over.
 *
 * The colour that wins is the most *vivid* one that is clearly present, not simply the most common: a grey-blue
 * cover with a small red logo gives red. Its hue is kept, its saturation and brightness are set from how vivid it
 * is, and the result is darkened until white text on it stays easy to read. Returns null when the cover has no real colour
 * (black and white, grey, empty), so the caller keeps the normal background.
 */
fun backgroundTone(pixels: IntArray): CoverTone? {
    if (pixels.isEmpty()) return null
    // 12 hue buckets of 30 degrees, centred on the primary and secondary colours so that red doesn't straddle a seam.
    val count = IntArray(12)
    val sumS = DoubleArray(12); val sumS2 = DoubleArray(12); val sumS3 = DoubleArray(12)
    val sumX = DoubleArray(12); val sumY = DoubleArray(12)
    for (p in pixels) {
        if ((p ushr 24) < 128) continue
        val r = ((p shr 16) and 0xFF) / 255.0; val g = ((p shr 8) and 0xFF) / 255.0; val b = (p and 0xFF) / 255.0
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        val s = if (mx == 0.0) 0.0 else d / mx
        if (s < MIN_SATURATION || mx < 0.15) continue // grey, near-white or near-black pixels carry no colour
        val h = when (mx) {
            r -> ((g - b) / d) % 6.0
            g -> (b - r) / d + 2.0
            else -> (r - g) / d + 4.0
        }.let { (it * 60.0 + 360.0) % 360.0 }
        val k = (((h + 15.0) % 360.0) / 30.0).toInt().coerceIn(0, 11)
        count[k]++
        sumS[k] += s; sumS2[k] += s * s; sumS3[k] += s * s * s
        sumX[k] += cos(Math.toRadians(h)) * s; sumY[k] += sin(Math.toRadians(h)) * s
    }
    // The colour that wins is the most vivid one that is clearly present, not simply the most common: saturation
    // counts a lot, how much of the cover it covers only a little. (Chosen so that three real covers, including a
    // grey-blue one with a small red logo, give the colour Qobuz's own player showed for them.)
    val n = pixels.size.toDouble()
    val best = (0 until 12).filter { count[it] >= n * MIN_COVERAGE }
        .maxByOrNull { (sumS[it] / count[it]).pow(4) * (count[it] / n).pow(0.3) } ?: return null
    val hue = (Math.toDegrees(atan2(sumY[best], sumX[best])) + 360.0) % 360.0
    val vivid = sumS3[best] / sumS2[best] // saturation as seen in the most vivid pixels
    // Washed-out covers give a deeper, quieter background; vivid ones a richer, brighter one.
    val sat = (0.5 + 0.45 * vivid).coerceIn(0.5, 0.92)
    var v = (0.22 + 0.24 * ((vivid - 0.15) / 0.5).coerceIn(0.0, 1.0))
    while (v > 0.1 && contrastWithWhite(hsvToArgb(hue, sat, v)) < MIN_CONTRAST) v *= 0.95
    return CoverTone(hsvToArgb(hue, sat, v), hsvToArgb(hue, sat, v * 0.8))
}

private const val MIN_SATURATION = 0.12
private const val MIN_COVERAGE = 0.008
const val MIN_CONTRAST = 5.5

/** WCAG contrast ratio between white text and the opaque colour [argb]. */
fun contrastWithWhite(argb: Int): Double {
    fun lin(c: Int): Double = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    val l = 0.2126 * lin((argb shr 16) and 0xFF) + 0.7152 * lin((argb shr 8) and 0xFF) + 0.0722 * lin(argb and 0xFF)
    return 1.05 / (l + 0.05)
}

internal fun hsvToArgb(h: Double, s: Double, v: Double): Int {
    val c = v * s
    val x = c * (1 - kotlin.math.abs((h / 60.0) % 2 - 1))
    val m = v - c
    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0.0)
        h < 120 -> Triple(x, c, 0.0)
        h < 180 -> Triple(0.0, c, x)
        h < 240 -> Triple(0.0, x, c)
        h < 300 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    fun ch(f: Double) = Math.round((f + m) * 255).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}
