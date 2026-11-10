package com.qbdlx.lyrics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lo que oye Whisper: la voz más sola. Casi siempre va al centro, así que se queda el centro (mid) y se baja lo que también suena
 * a los lados (los instrumentos abiertos), y fuera de la banda de la voz (bajo, platillos). Es una máscara suave: no busca una voz
 * limpia para oír, solo que Whisper reconozca y sitúe mejor las palabras.
 */
object VozCentro {
    private const val N = 1024
    private const val HOP = N / 4

    fun realzar(l: FloatArray, r: FloatArray, frames: Int): FloatArray {
        val out = FloatArray(frames)
        val win = FloatArray(N) { sqrt(0.5 - 0.5 * cos(2 * PI * it / N)).toFloat() }
        val cs = FloatArray(N / 2) { cos(2 * PI * it / N).toFloat() }; val sn = FloatArray(N / 2) { sin(2 * PI * it / N).toFloat() }
        val bits = Integer.numberOfTrailingZeros(N); val rev = IntArray(N) { Integer.reverse(it) ushr (32 - bits) }
        val aRe = FloatArray(N); val aIm = FloatArray(N); val bRe = FloatArray(N); val bIm = FloatArray(N)
        val hz = 16_000f / N; val lo = (100f / hz).toInt(); val hi = (7000f / hz).toInt()
        var s = -(N - HOP)
        while (s < frames) {
            for (j in 0 until N) { val i = s + j; val ok = i in 0 until frames
                aRe[j] = if (ok) l[i] * win[j] else 0f; bRe[j] = if (ok) r[i] * win[j] else 0f; aIm[j] = 0f; bIm[j] = 0f }
            fft(aRe, aIm, false, cs, sn, rev); fft(bRe, bIm, false, cs, sn, rev)
            for (k in 0..N / 2) {
                val mr = (aRe[k] + bRe[k]) * .5f; val mi = (aIm[k] + bIm[k]) * .5f
                val sr = (aRe[k] - bRe[k]) * .5f; val si = (aIm[k] - bIm[k]) * .5f
                val pm = mr * mr + mi * mi; val ps = sr * sr + si * si
                var m = sqrt(pm / (pm + 1.5f * ps + 1e-12f)).coerceAtLeast(0.15f)
                if (k < lo || k > hi) m *= 0.3f
                aRe[k] = mr * m; aIm[k] = mi * m
                if (k in 1 until N / 2) { aRe[N - k] = aRe[k]; aIm[N - k] = -aIm[k] }
            }
            fft(aRe, aIm, true, cs, sn, rev)
            for (j in 0 until N) { val i = s + j; if (i in 0 until frames) out[i] += aRe[j] * win[j] / N * 0.5f }
            s += HOP
        }
        var peak = 1e-6f; for (v in out) if (v > peak) peak = v else if (-v > peak) peak = -v
        val g = 0.9f / peak; for (i in out.indices) out[i] *= g
        return out
    }

    private fun fft(re: FloatArray, im: FloatArray, inv: Boolean, cs: FloatArray, sn: FloatArray, rev: IntArray) {
        val n = re.size
        for (i in 0 until n) { val j = rev[i]; if (j > i) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t } }
        var len = 2
        while (len <= n) {
            val step = n / len
            for (i in 0 until n step len) for (k in 0 until len / 2) {
                val wr = cs[k * step]; val wi = if (inv) sn[k * step] else -sn[k * step]
                val a = i + k; val b = a + len / 2
                val xr = re[b] * wr - im[b] * wi; val xi = re[b] * wi + im[b] * wr
                re[b] = re[a] - xr; im[b] = im[a] - xi; re[a] += xr; im[a] += xi
            }
            len = len shl 1
        }
    }
}
