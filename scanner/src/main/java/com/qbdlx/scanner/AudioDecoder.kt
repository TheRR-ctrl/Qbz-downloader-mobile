package com.qbdlx.scanner

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Decodes the start of an audio file to mono samples at [Chromaprint.SAMPLE_RATE]. */
object AudioDecoder {
    /** AcoustID only needs the first two minutes. */
    const val MAX_SECONDS = 120

    /** Returns null when the file cannot be decoded. */
    fun decode(context: Context, uri: Uri): ShortArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var float = false
            var mono = ShortArray(rate * 30)
            var count = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone && count < rate * MAX_SECONDS) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        float = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    o >= 0 -> {
                        val out = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                        out.position(info.offset); out.limit(info.offset + info.size)
                        val frames = info.size / (channels * if (float) 4 else 2)
                        if (count + frames > mono.size) mono = mono.copyOf(maxOf(mono.size * 2, count + frames))
                        for (f in 0 until frames) {
                            var sum = 0f
                            for (c in 0 until channels) sum += if (float) out.getFloat() * 32768f else out.getShort().toFloat()
                            mono[count++] = (sum / channels).toInt().coerceIn(-32768, 32767).toShort()
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            return Resampler.toFingerprintRate(mono.copyOf(minOf(count, rate * MAX_SECONDS)), rate)
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
