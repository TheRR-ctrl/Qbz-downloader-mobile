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
class Asr(val lang: String, val words: List<AsrWord>, val audioMs: Long = 0, val scale: Double = 1.0)

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

    private fun cacheFile(context: Context, key: String) = File(File(context.applicationContext.filesDir, "asr").apply { mkdirs() }, key.hashCode().toUInt().toString(16) + "-" + key.length + ".tsv")

    /** What Whisper heard in this song if it was listened to before (kept on the phone, so no song is listened to twice). */
    /**
     * The decoded audio can come out shorter or longer than the song (HE-AAC, odd sample rates): then every word time is off by the same ratio,
     * which looks like "the lyric drifts". When the two lengths differ by more than 4 % (but not so much that the decode was cut short) the times are rescaled.
     */
    fun fit(asr: Asr, songMs: Long): Asr {
        if (asr.audioMs <= 0 || songMs <= 0 || asr.scale != 1.0) return asr
        val r = songMs.toDouble() / asr.audioMs
        if (kotlin.math.abs(r - 1) <= 0.04 || r < 0.8 || r > 1.25) return asr
        return Asr(asr.lang, asr.words.map { AsrWord((it.startMs * r).toLong(), (it.endMs * r).toLong(), it.text) }, asr.audioMs, r)
    }

    /** One line that says what Whisper heard, to show next to the result so a bad sync can be traced to its cause. */
    fun describe(asr: Asr, songMs: Long, firstLineMs: Long?, matched: Pair<Int, Int>?): String {
        val first = asr.words.firstOrNull()?.startMs
        val sb = StringBuilder("Oyó ${asr.words.size} palabras (idioma ${asr.lang.ifBlank { "?" }})")
        if (matched != null && matched.second > 0) sb.append("; coincidieron ${matched.first} de ${matched.second}")
        if (first != null) sb.append("; primera voz a los ${first / 1000}s")
        if (firstLineMs != null) sb.append(", primera línea a los ${firstLineMs / 1000}s")
        if (asr.audioMs > 0 && songMs > 0) sb.append("; audio leído ${asr.audioMs / 1000}s de ${songMs / 1000}s" + (if (asr.scale != 1.0) " (tiempos reescalados ×${"%.3f".format(asr.scale)})" else ""))
        return sb.append(".").toString()
    }

    fun heardBefore(context: Context, key: String): Asr? = runCatching { cacheFile(context, key).takeIf { it.exists() }?.readText()?.let { parse(it) }?.takeIf { it.words.isNotEmpty() } }.getOrNull()

    /** Drops what was heard of this song, so the next check listens again from scratch. */
    fun forget(context: Context, key: String) { runCatching { cacheFile(context, key).delete() } }

    /** Listens to [uri] (or reuses the saved result for [key]). [status] tells the screen what is happening. Null if it failed or was cancelled. */
    fun listen(context: Context, key: String, uri: Uri, cancelled: () -> Boolean, songMs: Long = 0, status: (String) -> Unit = {}): Asr? {
        heardBefore(context, key)?.let { return fit(it, songMs) }
        status("Preparando el audio…")
        val pcm = pcm16k(context, uri, cancelled = cancelled) ?: return null
        status("Whisper está escuchando la canción… puede tardar unos minutos.")
        val asr = transcribe(context, pcm)?.let { Asr(it.lang, it.words, pcm.size * 1000L / RATE) } ?: return null
        if (asr.words.isNotEmpty()) runCatching { cacheFile(context, key).writeText("lang=${asr.lang}\nms=${asr.audioMs}\n" + asr.words.joinToString("\n") { "${it.startMs}\t${it.endMs}\t${it.text}" }) }
        return fit(asr, songMs)
    }

    /** "lang=es\n" then "<start>\t<end>\t<word>" lines. */
    fun parse(raw: String): Asr? {
        if (raw.isBlank()) return null
        var lang = ""; var ms = 0L; val words = ArrayList<AsrWord>()
        for (line in raw.lines()) {
            if (line.startsWith("lang=")) { lang = line.removePrefix("lang=").trim(); continue }
            if (line.startsWith("ms=")) { ms = line.removePrefix("ms=").trim().toLongOrNull() ?: 0; continue }
            val p = line.split('\t', limit = 3)
            if (p.size < 3) continue
            val a = p[0].toLongOrNull() ?: continue; val b = p[1].toLongOrNull() ?: continue
            val w = p[2].trim()
            if (w.isNotEmpty()) words.add(AsrWord(a, b, w))
        }
        return Asr(lang, words, ms)
    }
}
