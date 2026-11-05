package com.qbdlx.lyrics

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File

/** The JNI side (lyrics/src/main/cpp). The library may be missing in a build made without it; [loaded] says. */
object WhisperNative {
    @Volatile var loaded = false; private set
    init { loaded = runCatching { System.loadLibrary("qlwhisper") }.isSuccess }
    external fun init(path: String): Long
    external fun transcribe(ctx: Long, pcm: FloatArray, lang: String, threads: Int): String
    external fun cancel()
    external fun free(ctx: Long)
}

class AsrWord(val startMs: Long, val endMs: Long, val text: String)
class Asr(val lang: String, val words: List<AsrWord>)

/** Whisper on the phone: listens to the song's own audio file and says which word is sung when. The model travels inside the APK. */
object Whisper {
    const val MODEL = "ggml-base-q5_1.bin"
    private const val RATE = 16_000
    private const val MAX_SECONDS = 600

    fun available(context: Context): Boolean = WhisperNative.loaded && runCatching { context.assets.open(MODEL).close(); true }.getOrDefault(false)

    /** Whisper needs the model as a real file: copied out of the APK once (and again if the APK brings another size). */
    private fun modelFile(context: Context): File {
        val f = File(context.filesDir, "whisper/$MODEL"); f.parentFile?.mkdirs()
        val size = context.assets.openFd(MODEL).use { it.length }
        if (!f.exists() || f.length() != size) {
            val tmp = File(f.path + ".tmp")
            context.assets.open(MODEL).use { i -> tmp.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
            tmp.renameTo(f)
        }
        return f
    }

    /** The song as 16 kHz mono floats (what Whisper reads). Null when it cannot be decoded. */
    fun pcm16k(context: Context, uri: Uri, cancelled: () -> Boolean = { false }): FloatArray? {
        val ex = MediaExtractor(); var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, uri, null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0); codec.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var out = FloatArray(RATE * 240); var size = 0
            var acc = 0f; var cnt = 0; var phase = 0.0
            val info = MediaCodec.BufferInfo(); var inDone = false; var outDone = false
            while (!outDone) {
                if (cancelled()) return null
                if (!inDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    codec.outputFormat.let { rate = it.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = it.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                } else if (o >= 0) {
                    val step = rate.toDouble() / RATE
                    val sb = codec.getOutputBuffer(o)!!.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
                    while (sb.remaining() >= ch) {
                        var m = 0f; for (k in 0 until ch) m += sb.get() / 32768f
                        acc += m / ch; cnt++; phase += 1.0
                        if (phase >= step) { // box filter down to 16 kHz
                            if (size == out.size) out = out.copyOf(out.size * 2)
                            out[size++] = acc / cnt; acc = 0f; cnt = 0; phase -= step
                        }
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || size > RATE * MAX_SECONDS) outDone = true
                }
            }
            return if (size > RATE * 5) out.copyOf(size) else null
        } catch (e: Exception) { return null }
        finally { runCatching { codec?.stop() }; runCatching { codec?.release() }; ex.release() }
    }

    /** Runs Whisper over [pcm]. Takes a while (a few minutes for a song on a phone): call it off the main thread. Null if it failed or was cancelled. */
    fun transcribe(context: Context, pcm: FloatArray, lang: String = "auto"): Asr? {
        if (!WhisperNative.loaded) return null
        val ctx = WhisperNative.init(modelFile(context).path)
        if (ctx == 0L) return null
        try {
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            return parse(WhisperNative.transcribe(ctx, pcm, lang, threads))
        } finally { WhisperNative.free(ctx) }
    }

    fun cancel() { if (WhisperNative.loaded) WhisperNative.cancel() }

    /** "lang=es\n" then "<start>\t<end>\t<word>" lines. */
    fun parse(raw: String): Asr? {
        if (raw.isBlank()) return null
        var lang = ""; val words = ArrayList<AsrWord>()
        for (line in raw.lines()) {
            if (line.startsWith("lang=")) { lang = line.removePrefix("lang=").trim(); continue }
            val p = line.split('\t', limit = 3)
            if (p.size < 3) continue
            val a = p[0].toLongOrNull() ?: continue; val b = p[1].toLongOrNull() ?: continue
            val w = p[2].trim()
            if (w.isNotEmpty()) words.add(AsrWord(a, b, w))
        }
        return Asr(lang, words)
    }
}
