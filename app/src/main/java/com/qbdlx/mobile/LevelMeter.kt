package com.qbdlx.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import org.ostrichbat2k.usbdac.streaming.Niveles
import org.ostrichbat2k.usbdac.streaming.aDb
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Mide el pico de lo que la salida normal de Android acepta en cada llamada. Con el DAC por USB directo mide el
 * motor USB, justo al mandar (aquí iría segundos por delante de lo que suena, por el búfer).
 */
@UnstableApi
class MeterSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    private var encoding = C.ENCODING_INVALID
    private var canales = 2

    override fun configure(config: AudioSink.AudioSinkConfig) {
        encoding = config.format.pcmEncoding; canales = config.format.channelCount.coerceAtLeast(1)
        super.configure(config)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        val desde = buffer.position()
        val hecho = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        // Solo lo que se aceptó en esta llamada: el mismo búfer vuelve varias veces hasta que cabe entero.
        runCatching { medir(buffer, desde, buffer.position()) }
        return hecho
    }

    private fun medir(b: ByteBuffer, desde: Int, hasta: Int) {
        val bytes = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2; C.ENCODING_PCM_24BIT -> 3; C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4; else -> return
        }
        val trama = bytes * canales
        var l = 0f; var r = 0f
        var i = desde
        while (i + trama <= hasta) {
            val a = muestra(b, i, bytes); if (a > l) l = a
            if (canales > 1) { val c = muestra(b, i + bytes, bytes); if (c > r) r = c }
            i += trama
        }
        if (canales == 1) r = l
        Niveles.anotar(l, r)
    }

    private fun muestra(b: ByteBuffer, i: Int, bytes: Int): Float = when {
        encoding == C.ENCODING_PCM_FLOAT -> abs(b.getFloat(i)).let { if (it.isNaN()) 0f else it }
        bytes == 2 -> abs(b.getShort(i).toInt()) / 32768f
        // 24 y 32 bits: bastan los dos bytes altos (el búfer va en el orden nativo, little-endian).
        else -> abs(((b.get(i + bytes - 1).toInt() shl 8) or (b.get(i + bytes - 2).toInt() and 0xFF))) / 32768f
    }
}

private const val SUELO_DB = -60f
private const val CAIDA_DB_S = 24f
private const val RETENCION_NS = 1_500_000_000L

/**
 * Medidor de nivel como el de UAPP: una barra por canal de -60 a 0 dBFS, que sube al instante y cae despacio, con
 * una marca que retiene el pico 1,5 s y el pico máximo en números (rojo si llegó a 0 dB). Tocar los números los
 * pone a cero.
 */
@Composable
fun LevelMeter(activo: Boolean, modifier: Modifier = Modifier) {
    // Estado fuera de Compose: se dibuja en cada cuadro leyendo [tick], sin recomponer nada.
    val st = remember { MeterState() }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(activo) {
        var antes = 0L
        while (true) withFrameNanos { t ->
            val dt = if (antes == 0L) 0f else (t - antes) / 1e9f
            antes = t
            if (st.avanzar(t, dt, activo)) tick = t
        }
    }
    Column(modifier) {
        for (ch in 0..1) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (ch == 0) "L" else "R", fontSize = 11.sp, color = Color.White.copy(alpha = .7f), fontFamily = FontFamily.Monospace, modifier = Modifier.width(14.dp))
            Canvas(Modifier.weight(1f).height(8.dp)) {
                tick // lectura en la fase de dibujo
                val w = size.width; val h = size.height
                val radio = CornerRadius(h / 2)
                drawRoundRect(Color.White.copy(alpha = .12f), size = size, cornerRadius = radio)
                val x = w * fraccion(st.nivel[ch])
                if (x > 0f) drawRoundRect(
                    Brush.horizontalGradient(0f to Color(0xFF2ECC71), .70f to Color(0xFF2ECC71), .88f to Color(0xFFF1C40F), 1f to Color(0xFFE74C3C), startX = 0f, endX = w),
                    size = Size(x, h), cornerRadius = radio,
                )
                val p = w * fraccion(st.retenido[ch])
                if (p > 2f) drawRect(if (st.retenido[ch] >= -0.1f) Color(0xFFE74C3C) else Color.White, Offset(p - 2f, 0f), Size(2f, h))
            }
            val max = st.maximo[ch].also { tick }
            Text(
                if (max <= SUELO_DB) "  -∞ " else "%5.1f".format(java.util.Locale.US, max),
                fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = if (max >= -0.1f) Color(0xFFE74C3C) else Color.White.copy(alpha = .7f),
                modifier = Modifier.padding(start = 6.dp).width(40.dp).clickableSinOnda { st.reiniciar(); tick++ },
            )
        }
        Canvas(Modifier.fillMaxWidth().padding(start = 14.dp, end = 46.dp).height(4.dp)) {
            for (db in listOf(-48, -36, -24, -12, -6, -3, 0)) {
                val x = size.width * fraccion(db.toFloat())
                drawRect(Color.White.copy(alpha = if (db == 0) .7f else .35f), Offset(x.coerceAtMost(size.width - 1f), 0f), Size(1f, size.height))
            }
        }
    }
}

private fun fraccion(db: Float) = ((db - SUELO_DB) / -SUELO_DB).coerceIn(0f, 1f)

private fun Modifier.clickableSinOnda(f: () -> Unit): Modifier =
    pointerInput(Unit) { detectTapGestures { f() } }

internal class MeterState {
    val nivel = floatArrayOf(SUELO_DB, SUELO_DB)
    val retenido = floatArrayOf(SUELO_DB, SUELO_DB)
    val maximo = floatArrayOf(SUELO_DB, SUELO_DB)
    private val retenidoEn = LongArray(2)
    private val picos = FloatArray(2)

    fun reiniciar() { maximo.fill(SUELO_DB) }

    /** Avanza un cuadro; devuelve si cambió algo que haya que volver a dibujar. */
    fun avanzar(ahora: Long, dt: Float, activo: Boolean, leer: (FloatArray) -> Unit = Niveles::tomar): Boolean {
        leer(picos)
        var cambio = false
        for (ch in 0..1) {
            val db = if (activo) aDb(picos[ch], SUELO_DB) else SUELO_DB
            val n = maxOf(db, nivel[ch] - CAIDA_DB_S * dt).coerceAtLeast(SUELO_DB)
            if (n != nivel[ch]) { nivel[ch] = n; cambio = true }
            if (db >= retenido[ch]) { retenido[ch] = db; retenidoEn[ch] = ahora }
            else if (ahora - retenidoEn[ch] > RETENCION_NS && retenido[ch] > SUELO_DB) {
                retenido[ch] = maxOf(n, retenido[ch] - CAIDA_DB_S * dt); cambio = true
            }
            if (db > maximo[ch]) { maximo[ch] = db; cambio = true }
        }
        return cambio
    }
}
