package com.qbdlx.scanner

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Chromaprint's default fingerprint ("algorithm 2" on acoustid.org; `CHROMAPRINT_ALGORITHM_DEFAULT`), the audio
 * fingerprint AcoustID looks songs up with. It is a port of the algorithm of acoustid/chromaprint (LGPL-2.1+, see
 * THIRD_PARTY.md): 11025 Hz mono, FFT frames of 4096 samples every 1365, 12 chroma bands, a 5-frame smoothing,
 * normalisation, and 16 filter classifiers that give 2 bits each, so one 32-bit value per frame. The classifier table
 * is the one of the original; the unit test compares the result bit for bit with the real library's.
 */
object Chromaprint {
    const val SAMPLE_RATE = 11025
    private const val FRAME = 4096
    private const val HOP = 1365
    private const val BANDS = 12
    private const val MIN_FREQ = 28.0
    private const val MAX_FREQ = 3520.0
    private val SMOOTH = doubleArrayOf(0.25, 0.75, 1.0, 0.75, 0.25)

    /** type, y, height, width of each classifier, and its three quantiser thresholds. */
    private val FILTERS = intArrayOf(
        0, 4, 3, 15, 4, 4, 6, 15, 1, 0, 4, 16, 3, 8, 2, 12, 3, 4, 4, 8, 4, 0, 3, 5, 1, 2, 2, 9, 2, 7, 3, 4,
        2, 6, 2, 16, 2, 1, 3, 2, 5, 10, 1, 15, 3, 6, 2, 10, 2, 1, 1, 14, 3, 5, 6, 4, 1, 9, 2, 12, 3, 4, 2, 14,
    )
    private val THRESHOLDS = doubleArrayOf(
        1.98215, 2.35817, 2.63523, -1.03809, -0.651211, -0.282167, -0.298702, 0.119262, 0.558497,
        -0.105439, 0.0153946, 0.135898, -0.142891, 0.0258736, 0.200632, -0.826319, -0.590612, -0.368214,
        -0.557409, -0.233035, 0.0534525, -0.0646826, 0.00620476, 0.078485, -0.192387, -0.029699, 0.215855,
        -0.0397818, -0.00568076, 0.029203, -0.53823, -0.369934, -0.190235, -0.124877, 0.0296483, 0.139239,
        -0.101475, 0.0225617, 0.231971, -0.0799915, -0.00729616, 0.063262, -0.272556, 0.019424, 0.302559,
        -0.164292, -0.0321188, 0.084634,
    )
    private val GRAY = intArrayOf(0, 1, 3, 2)

    private val window = DoubleArray(FRAME) { 0.54 - 0.46 * cos(it * 2 * PI / (FRAME - 1)) }
    private val noteOfBin: IntArray
    private val firstBin = maxOf(1, round(FRAME * MIN_FREQ / SAMPLE_RATE).toInt())
    private val lastBin = minOf(FRAME / 2, round(FRAME * MAX_FREQ / SAMPLE_RATE).toInt())

    init {
        noteOfBin = IntArray(lastBin) { i ->
            if (i < firstBin) 0 else {
                val octave = ln(i.toDouble() * SAMPLE_RATE / FRAME / 27.5) / ln(2.0)
                (BANDS * (octave - kotlin.math.floor(octave))).toInt()
            }
        }
    }

    /** The raw fingerprint (one value per frame) of [pcm]: mono samples at [SAMPLE_RATE]. */
    fun fingerprint(pcm: ShortArray): IntArray {
        if (pcm.size < FRAME) return IntArray(0)
        val chroma = ArrayList<DoubleArray>()
        val re = DoubleArray(FRAME)
        val im = DoubleArray(FRAME)
        var start = 0
        while (start + FRAME <= pcm.size) {
            for (i in 0 until FRAME) { re[i] = pcm[start + i] * window[i]; im[i] = 0.0 }
            fft(re, im)
            val bands = DoubleArray(BANDS)
            for (i in firstBin until lastBin) bands[noteOfBin[i]] += re[i] * re[i] + im[i] * im[i]
            chroma += bands
            start += HOP
        }
        // Smooth over 5 frames, then scale every frame to length 1 (silence stays zero).
        val rows = chroma.size - SMOOTH.size + 1
        if (rows < 16) return IntArray(0)
        val image = Array(rows) { r ->
            val v = DoubleArray(BANDS)
            for (j in SMOOTH.indices) for (b in 0 until BANDS) v[b] += SMOOTH[j] * chroma[r + j][b]
            var sq = 0.0
            for (b in 0 until BANDS) sq += v[b] * v[b]
            val norm = sqrt(sq)
            if (norm < 0.01) DoubleArray(BANDS) else DoubleArray(BANDS) { v[it] / norm }
        }
        // Integral image: sum of everything above and to the left.
        val integral = Array(rows) { DoubleArray(BANDS) }
        for (r in 0 until rows) {
            var rowSum = 0.0
            for (c in 0 until BANDS) {
                rowSum += image[r][c]
                integral[r][c] = rowSum + if (r > 0) integral[r - 1][c] else 0.0
            }
        }
        fun area(x1: Int, y1: Int, x2: Int, y2: Int): Double {
            if (x2 < x1 || y2 < y1) return 0.0
            var a = integral[x2][y2]
            if (x1 > 0) { a -= integral[x1 - 1][y2]; if (y1 > 0) a += integral[x1 - 1][y1 - 1] }
            if (y1 > 0) a -= integral[x2][y1 - 1]
            return a
        }
        return IntArray(rows - 15) { x ->
            var bits = 0
            for (k in 0 until 16) {
                val type = FILTERS[k * 4]; val y = FILTERS[k * 4 + 1]; val h = FILTERS[k * 4 + 2]; val w = FILTERS[k * 4 + 3]
                val a: Double; val b: Double
                when (type) {
                    0 -> { a = area(x, y, x + w - 1, y + h - 1); b = 0.0 }
                    1 -> { val h2 = h / 2; a = area(x, y + h2, x + w - 1, y + h - 1); b = area(x, y, x + w - 1, y + h2 - 1) }
                    2 -> { val w2 = w / 2; a = area(x + w2, y, x + w - 1, y + h - 1); b = area(x, y, x + w2 - 1, y + h - 1) }
                    3 -> {
                        val w2 = w / 2; val h2 = h / 2
                        a = area(x, y + h2, x + w2 - 1, y + h - 1) + area(x + w2, y, x + w - 1, y + h2 - 1)
                        b = area(x, y, x + w2 - 1, y + h2 - 1) + area(x + w2, y + h2, x + w - 1, y + h - 1)
                    }
                    4 -> {
                        val h3 = h / 3
                        a = area(x, y + h3, x + w - 1, y + 2 * h3 - 1)
                        b = area(x, y, x + w - 1, y + h3 - 1) + area(x, y + 2 * h3, x + w - 1, y + h - 1)
                    }
                    else -> {
                        val w3 = w / 3
                        a = area(x + w3, y, x + 2 * w3 - 1, y + h - 1)
                        b = area(x, y, x + w3 - 1, y + h - 1) + area(x + 2 * w3, y, x + w - 1, y + h - 1)
                    }
                }
                val v = ln1p(a) - ln1p(b)
                val t = k * 3
                val q = if (v < THRESHOLDS[t]) 0 else if (v < THRESHOLDS[t + 1]) 1 else if (v < THRESHOLDS[t + 2]) 2 else 3
                bits = (bits shl 2) or GRAY[q]
            }
            bits
        }
    }

    /** The compressed, URL-safe base64 form AcoustID's lookup takes. */
    fun encode(raw: IntArray): String {
        val normal = ArrayList<Int>()
        val exceptional = ArrayList<Int>()
        var last = 0
        for (v in raw) {
            val x = v xor last
            last = v
            var bit = 1
            var previous = 0
            var rest = x
            while (rest != 0) {
                if (rest and 1 != 0) {
                    val d = bit - previous
                    if (d >= 7) { normal += 7; exceptional += d - 7 } else normal += d
                    previous = bit
                }
                rest = rest ushr 1
                bit++
            }
            normal += 0
        }
        val out = java.io.ByteArrayOutputStream()
        out.write(1) // algorithm id of the default fingerprint
        out.write(raw.size ushr 16 and 0xFF); out.write(raw.size ushr 8 and 0xFF); out.write(raw.size and 0xFF)
        packBits(normal, 3, out)
        packBits(exceptional, 5, out)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    private fun packBits(values: List<Int>, width: Int, out: java.io.ByteArrayOutputStream) {
        var acc = 0
        var have = 0
        for (v in values) {
            acc = acc or (v shl have)
            have += width
            while (have >= 8) { out.write(acc and 0xFF); acc = acc ushr 8; have -= 8 }
        }
        if (have > 0) out.write(acc and 0xFF)
    }

    /** In-place radix-2 FFT. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = twiddle(len)
            var i = 0
            while (i < n) {
                for (k in 0 until half) {
                    val wr = step[2 * k]; val wi = step[2 * k + 1]
                    val a = i + k; val b = a + half
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                }
                i += len
            }
            len = len shl 1
        }
    }

    private val twiddles = HashMap<Int, DoubleArray>()
    private fun twiddle(len: Int): DoubleArray = synchronized(twiddles) {
        twiddles.getOrPut(len) { DoubleArray(len) { i -> val k = i / 2; val a = -2 * PI * k / len; if (i % 2 == 0) cos(a) else sin(a) } }
    }
}
