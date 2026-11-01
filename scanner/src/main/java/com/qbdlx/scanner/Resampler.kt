package com.qbdlx.scanner

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Mono samples of any rate to [Chromaprint.SAMPLE_RATE], with a windowed-sinc low-pass so nothing folds back. */
object Resampler {
    private const val ZEROS = 8        // sinc zero crossings on each side
    private const val PER_ZERO = 64    // table entries per zero crossing

    private val table: FloatArray by lazy {
        // sinc(x) * Hann window, sampled at 1/PER_ZERO steps up to ZEROS.
        FloatArray(ZEROS * PER_ZERO + 2) { i ->
            val x = i.toDouble() / PER_ZERO
            val s = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)
            (s * (0.5 + 0.5 * cos(PI * x / ZEROS))).toFloat()
        }
    }

    fun toFingerprintRate(input: ShortArray, rate: Int): ShortArray {
        if (rate == Chromaprint.SAMPLE_RATE) return input
        val ratio = rate.toDouble() / Chromaprint.SAMPLE_RATE
        val outSize = (input.size / ratio).toInt()
        val out = ShortArray(outSize)
        // Going down, the filter is stretched so its cut-off is at the new Nyquist (a little below, for the transition).
        val stretch = if (ratio > 1) ratio / 0.95 else 1.0
        val reach = ZEROS * stretch
        val t = table
        for (o in 0 until outSize) {
            val center = o * ratio
            val from = kotlin.math.ceil(center - reach).toInt().coerceAtLeast(0)
            val to = floor(center + reach).toInt().coerceAtMost(input.size - 1)
            var sum = 0.0
            var weights = 0.0
            for (i in from..to) {
                val pos = kotlin.math.abs(i - center) / stretch * PER_ZERO
                val k = pos.toInt()
                val w = t[k] + (t[k + 1] - t[k]) * (pos - k)
                sum += input[i] * w
                weights += w
            }
            out[o] = (if (weights != 0.0) sum / weights else 0.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }
}
