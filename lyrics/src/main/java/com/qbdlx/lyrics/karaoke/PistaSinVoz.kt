package com.qbdlx.lyrics.karaoke

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Audio estéreo decodificado: muestras intercaladas L,R en [-1,1]. */
class Estereo(val rate: Int, val data: FloatArray) { val frames get() = data.size / 2 }

/**
 * Canción sin voz, versión rápida y sin modelo: la voz suele ir centrada, así que se resta un canal del otro (L−R)
 * y se devuelve el bajo (que también va al centro) con un filtro paso‑bajo sumado de nuevo. Es una aproximación:
 * quita la voz principal pero deja eco y algo de percusión central. La versión con IA vendrá después.
 */
object PistaSinVoz {
    /** Frecuencia por debajo de la cual se conserva el centro (bajo y bombo). */
    const val CORTE_BAJO_HZ = 150f

    /** Quita el centro. Devuelve estéreo nuevo; la entrada no se toca. Con audio mono no hay nada que quitar: lo devuelve igual. */
    fun quitarVoz(src: Estereo, corteHz: Float = CORTE_BAJO_HZ): Estereo {
        val n = src.frames
        val out = FloatArray(n * 2)
        val a = (1.0 - Math.exp(-2.0 * Math.PI * corteHz / src.rate)).toFloat() // filtro de un polo
        var low = 0f
        for (i in 0 until n) {
            val l = src.data[2 * i]; val r = src.data[2 * i + 1]
            val mid = (l + r) * 0.5f
            low += a * (mid - low)
            val side = (l - r) * 0.5f
            // lo lateral (sin voz) en los dos canales, más el bajo del centro
            out[2 * i] = (side + low).coerceIn(-1f, 1f)
            out[2 * i + 1] = (-side + low).coerceIn(-1f, 1f)
        }
        return Estereo(src.rate, out)
    }

    /** Decodifica toda la pista a estéreo. Nulo si no se puede o se cancela. */
    fun decodificar(context: Context, uri: Uri, cancelled: () -> Boolean = { false }, maxSegundos: Int = 900): Estereo? {
        val ex = MediaExtractor(); var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, uri, null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0); codec.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var out = FloatArray(rate * 2 * 240); var size = 0
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
                    val sb = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder()).asShortBuffer()
                    while (sb.remaining() >= ch) {
                        val l = sb.get() / 32768f
                        var r = l
                        if (ch >= 2) r = sb.get() / 32768f
                        for (k in 2 until ch) sb.get() // canales extra se descartan
                        if (size + 2 > out.size) out = out.copyOf(out.size * 2)
                        out[size++] = l; out[size++] = r
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || size > rate * 2 * maxSegundos) outDone = true
                }
            }
            return if (size > rate * 2 * 5) Estereo(rate, out.copyOf(size)) else null
        } catch (e: Exception) { return null }
        finally { runCatching { codec?.stop() }; runCatching { codec?.release() }; ex.release() }
    }

    /** WAV de 16 bits. */
    fun escribirWav(e: Estereo, os: OutputStream) {
        val bytes = e.data.size * 2
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVE".toByteArray()).put("fmt ".toByteArray())
        h.putInt(16).putShort(1).putShort(2).putInt(e.rate).putInt(e.rate * 4).putShort(4).putShort(16)
        h.put("data".toByteArray()).putInt(bytes)
        os.write(h.array())
        val buf = ByteBuffer.allocate(1 shl 16).order(ByteOrder.LITTLE_ENDIAN)
        for (v in e.data) {
            if (!buf.hasRemaining()) { os.write(buf.array(), 0, buf.position()); buf.clear() }
            buf.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
        os.write(buf.array(), 0, buf.position())
    }

    private fun limpiar(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "pista" }

    /** Guarda el WAV en Música/Q Lyrics y devuelve su Uri (nulo si falla). */
    fun exportar(context: Context, e: Estereo, titulo: String, artista: String): Uri? {
        val nombre = limpiar("$artista - $titulo (sin voz)") + ".wav"
        val cv = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, nombre)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Q Lyrics"); put(MediaStore.Audio.Media.IS_PENDING, 1) }
        }
        val res = context.contentResolver
        val uri = res.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv) ?: return null
        return try {
            res.openOutputStream(uri)!!.use { escribirWav(e, it.buffered()) }
            if (Build.VERSION.SDK_INT >= 29) res.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (ex: Exception) { runCatching { res.delete(uri, null, null) }; null }
    }
}
