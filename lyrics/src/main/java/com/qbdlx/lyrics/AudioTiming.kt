package com.qbdlx.lyrics

import android.content.ContentUris
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.MediaStore
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Times unsynced lyrics from the song's own audio file: measures how loud the sound is every [FRAME_MS] ms, finds the short dips
 * between phrases and snaps each line to the nearest dip. It cannot tell a voice from an instrument, so it is an estimate that
 * follows the real phrasing (the manual sync offset still applies on top).
 */
object AudioTiming {
    const val FRAME_MS = 40

    /** The phone's copy of the song (needs the audio permission), matched by title, artist and length. Null when there is none. */
    fun find(context: Context, title: String, artist: String, durationMs: Long): Uri? {
        val cols = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
        var best: Uri? = null; var bestGap = Long.MAX_VALUE
        context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, "${MediaStore.Audio.Media.IS_MUSIC}!=0 AND ${MediaStore.Audio.Media.TITLE} LIKE ?", arrayOf("%${title.trim()}%"), null)?.use { c ->
            while (c.moveToNext()) {
                if (!LrcLib.sameSong(c.getString(1).orEmpty(), c.getString(2).orEmpty(), title, artist)) continue
                val gap = if (durationMs > 0) abs(c.getLong(3) - durationMs) else 0L
                if (gap < bestGap && gap <= 5000) { bestGap = gap; best = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) }
            }
        }
        return best
    }

    /** The phone's copy, or else a temporary MP3 lent by QBDLX Mobile (from Qobuz, nothing kept on disk). Null when neither exists. */
    fun findOrBorrow(context: Context, title: String, artist: String, durationMs: Long): Uri? =
        find(context, title, artist, durationMs) ?: borrowed(context, title, artist, durationMs / 1000)

    fun borrowed(context: Context, title: String, artist: String, durationSec: Long): Uri? {
        if (context.packageManager.resolveContentProvider("com.qbdlx.mobile.lyricsaudio", 0) == null) return null
        return Uri.Builder().scheme("content").authority("com.qbdlx.mobile.lyricsaudio").path("track")
            .appendQueryParameter("title", title).appendQueryParameter("artist", artist).appendQueryParameter("dur", durationSec.toString()).build()
    }

    /** Loudness of every frame (RMS of the first difference of the signal, which sets the bass and the kick drum aside). Null if it cannot be decoded. */
    fun envelope(context: Context, uri: Uri, maxSeconds: Int = 900, cancelled: () -> Boolean = { false }): FloatArray? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, uri, null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0); codec.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val out = ArrayList<Float>()
            var sum = 0.0; var n = 0; var prev = 0f; var total = 0L
            val info = MediaCodec.BufferInfo(); var inDone = false; var outDone = false
            while (!outDone) {
                if (cancelled()) return null
                if (!inDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val size = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (size < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    codec.outputFormat.let { rate = it.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = it.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                } else if (o >= 0) {
                    val frameSamples = (rate * FRAME_MS / 1000).coerceAtLeast(1)
                    val sb = codec.getOutputBuffer(o)!!.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
                    while (sb.remaining() >= ch) {
                        var m = 0f; for (k in 0 until ch) m += sb.get() / 32768f
                        m /= ch
                        val d = m - prev; prev = m
                        sum += d * d; n++; total++
                        if (n >= frameSamples) { out.add(sqrt(sum / n).toFloat()); sum = 0.0; n = 0 }
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || total > rate.toLong() * maxSeconds) outDone = true
                }
            }
            return out.toFloatArray().takeIf { it.size > 50 }
        } catch (e: Exception) { return null }
        finally { runCatching { codec?.stop() }; runCatching { codec?.release() }; ex.release() }
    }

    /** Moving average over [w] frames. */
    private fun smooth(a: FloatArray, w: Int): FloatArray {
        val r = FloatArray(a.size); var acc = 0.0
        for (i in a.indices) { acc += a[i]; if (i >= w) acc -= a[i - w]; r[i] = (acc / minOf(i + 1, w)).toFloat() }
        return r
    }

    /**
     * Gives each of [lines] a start time from the loudness envelope [env]. Starts from the proportional estimate, then moves each line
     * to the nearest quiet gap (the moment the next phrase starts) within a tolerance, tracking drift from the line before.
     */
    fun align(env: FloatArray, lines: List<String>): List<LyricLine> {
        val durationMs = env.size.toLong() * FRAME_MS
        val est = Lrc.estimate(lines.map { LyricLine(0, it) }, durationMs)
        if (lines.size < 2 || env.size < 100) return est
        val d = smooth(env, 5).map { 20 * log10(it + 1e-5f) }
        val sorted = d.sorted(); val lo = sorted[sorted.size / 10]; val hi = sorted[sorted.size * 9 / 10]
        if (hi - lo < 6f) return est // no dynamics to follow
        val gate = lo + 0.45f * (hi - lo)
        // quiet runs of at least 4 frames (160 ms): the phrase starts when each one ends
        class Gap(val startMs: Long, val endMs: Long)
        val gaps = ArrayList<Gap>(); var i = 0
        while (i < d.size) {
            if (d[i] < gate) { var j = i; while (j < d.size && d[j] < gate) j++; if (j - i >= 4 && j < d.size) gaps.add(Gap(i.toLong() * FRAME_MS, j.toLong() * FRAME_MS)); i = j } else i++
        }
        val avgSpan = (est.last().timeMs - est.first().timeMs) / (lines.size - 1).coerceAtLeast(1)
        val tol = maxOf(2500L, (avgSpan * 0.9).toLong())
        val out = ArrayList<LyricLine>(); var prevT = Long.MIN_VALUE
        for (k in lines.indices) {
            val expected = if (k == 0) est[0].timeMs else prevT + (est[k].timeMs - est[k - 1].timeMs)
            val minT = if (k == 0) 0L else prevT + 800
            val pick = gaps.filter { it.endMs >= minT && abs(it.endMs - expected) <= (if (k == 0) 8000L else tol) }
                .minByOrNull { abs(it.endMs - expected).toDouble() / tol - minOf(it.endMs - it.startMs, 1500L) / 3000.0 }
            val t = maxOf(pick?.endMs ?: expected, minT).coerceAtMost(durationMs)
            out.add(LyricLine(t, lines[k])); prevT = t
        }
        return out
    }
}
