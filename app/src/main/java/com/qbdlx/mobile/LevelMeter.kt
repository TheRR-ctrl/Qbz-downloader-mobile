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
    private var paso = 1

    override fun configure(config: AudioSink.AudioSinkConfig) {
        encoding = config.format.pcmEncoding; canales = config.format.channelCount.coerceAtLeast(1)
        paso = Niveles.paso(config.format.sampleRate)
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
        var sl = 0.0; var sr = 0.0
        var i = desde; var n = 0
        while (i + trama <= hasta) {
            val a = muestra(b, i, bytes)
            val c = if (canales > 1) muestra(b, i + bytes, bytes) else a
            if (abs(a) > l) l = abs(a)
            if (abs(c) > r) r = abs(c)
            sl += a * a; sr += c * c
            if (n % paso == 0) Niveles.muestra(((a + c) * 16383.5f).toInt().coerceIn(-32768, 32767))
            n++; i += trama
        }
        if (n == 0) return
        Niveles.anotar(l, r)
        Niveles.anotarEnergia(sl, sr, n)
    }

    private fun muestra(b: ByteBuffer, i: Int, bytes: Int): Float = when {
        encoding == C.ENCODING_PCM_FLOAT -> b.getFloat(i).let { if (it.isNaN()) 0f else it }
        bytes == 2 -> b.getShort(i) / 32768f
        // 24 y 32 bits: bastan los dos bytes altos (el búfer va en el orden nativo, little-endian).
        else -> ((b.get(i + bytes - 1).toInt() shl 8) or (b.get(i + bytes - 2).toInt() and 0xFF)) / 32768f
    }
}

private const val SUELO_DB = -60f
/** La barra enseña la energía (RMS) entre este suelo y 0 dB: con picos y -60 dB, la música de hoy la tenía siempre llena. */
private const val SUELO_BARRA = -36f
private const val CAIDA_DB_S = 60f
private const val RETENCION_NS = 1_000_000_000L
private val VERDE = Color(0xFF2ECC71)
private val AMARILLO = Color(0xFFF1C40F)
private val ROJO = Color(0xFFE74C3C)

/**
 * Visualización del reproductor. [modo] 0: medidor de nivel como el de UAPP (barra = energía de cada canal, marca =
 * pico retenido 1 s, número = pico máximo en dB, rojo si llegó a 0; tocar el número lo pone a cero). 1: barras de
 * espectro con topes que caen. 2: ámbito, la onda tal cual. Tocarla pasa a la siguiente.
 */
@Composable
fun LevelMeter(activo: Boolean, modo: Int, alTocar: () -> Unit, modifier: Modifier = Modifier) {
    // Estado fuera de Compose: se dibuja en cada cuadro leyendo [tick], sin recomponer nada.
    val st = remember { MeterState() }
    val esp = remember { Espectro() }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(activo, modo) {
        var antes = 0L
        while (true) withFrameNanos { t ->
            val dt = if (antes == 0L) 0f else ((t - antes) / 1e9f).coerceAtMost(.1f)
            antes = t
            val c = st.avanzar(t, dt, activo)
            when (modo) {
                1 -> { esp.avanzar(dt, activo); tick = t }
                2 -> { esp.onda(activo); tick = t }
                else -> if (c) tick = t
            }
        }
    }
    val toque = Modifier.clickableSinOnda(alTocar)
    when (modo) {
        1 -> Canvas(modifier.height(72.dp).then(toque)) {
            tick
            val n = esp.barras.size
            val hueco = size.width / n * .22f
            val ancho = size.width / n - hueco
            for (k in 0 until n) {
                val x = k * (ancho + hueco) + hueco / 2
                val h = size.height * esp.barras[k]
                if (h > 1f) drawRoundRect(
                    Brush.verticalGradient(0f to ROJO, .25f to AMARILLO, .5f to VERDE, 1f to VERDE, startY = 0f, endY = size.height),
                    Offset(x, size.height - h), Size(ancho, h), CornerRadius(2f),
                )
                val y = size.height * (1f - esp.topes[k])
                drawRect(Color.White.copy(alpha = .85f), Offset(x, (y - 3f).coerceIn(0f, size.height - 3f)), Size(ancho, 3f))
            }
        }
        2 -> Canvas(modifier.height(72.dp).then(toque)) {
            tick
            val o = esp.trazo
            val medio = size.height / 2
            drawRect(Color.White.copy(alpha = .15f), Offset(0f, medio), Size(size.width, 1f))
            val paso = size.width / (o.size - 1)
            var x0 = 0f; var y0 = medio - o[0] * medio
            for (k in 1 until o.size) {
                val x1 = k * paso; val y1 = medio - o[k].coerceIn(-1f, 1f) * medio
                drawLine(VERDE, Offset(x0, y0), Offset(x1, y1), strokeWidth = 3f)
                x0 = x1; y0 = y1
            }
        }
        else -> Column(modifier) {
            for (ch in 0..1) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (ch == 0) "L" else "R", fontSize = 11.sp, color = Color.White.copy(alpha = .7f), fontFamily = FontFamily.Monospace, modifier = Modifier.width(14.dp))
                Canvas(Modifier.weight(1f).height(8.dp).then(toque)) {
                    tick // lectura en la fase de dibujo
                    val w = size.width; val h = size.height
                    val radio = CornerRadius(h / 2)
                    drawRoundRect(Color.White.copy(alpha = .12f), size = size, cornerRadius = radio)
                    val x = w * fraccion(st.nivel[ch])
                    if (x > 0f) drawRoundRect(
                        Brush.horizontalGradient(0f to VERDE, .67f to VERDE, .84f to AMARILLO, 1f to ROJO, startX = 0f, endX = w),
                        size = Size(x, h), cornerRadius = radio,
                    )
                    val p = w * fraccion(st.retenido[ch])
                    if (p > 2f) drawRect(if (st.retenido[ch] >= -0.1f) ROJO else Color.White, Offset(p - 2f, 0f), Size(2f, h))
                }
                val max = st.maximo[ch].also { tick }
                Text(
                    if (max <= SUELO_DB) "  -∞ " else "%5.1f".format(java.util.Locale.US, max),
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = if (max >= -0.1f) ROJO else Color.White.copy(alpha = .7f),
                    modifier = Modifier.padding(start = 6.dp).width(40.dp).clickableSinOnda { st.reiniciar(); tick++ },
                )
            }
            Canvas(Modifier.fillMaxWidth().padding(start = 14.dp, end = 46.dp).height(4.dp)) {
                for (db in listOf(-30, -24, -18, -12, -6, -3, 0)) {
                    val x = size.width * fraccion(db.toFloat())
                    drawRect(Color.White.copy(alpha = if (db == 0) .7f else .35f), Offset(x.coerceAtMost(size.width - 1f), 0f), Size(1f, size.height))
                }
            }
        }
    }
}

private fun fraccion(db: Float) = ((db - SUELO_BARRA) / -SUELO_BARRA).coerceIn(0f, 1f)

private fun Modifier.clickableSinOnda(f: () -> Unit): Modifier =
    pointerInput(f) { detectTapGestures { f() } }

internal class MeterState {
    /** Energía (RMS) de cada canal en dB, con subida inmediata y caída de [CAIDA_DB_S]. */
    val nivel = floatArrayOf(SUELO_DB, SUELO_DB)
    val retenido = floatArrayOf(SUELO_DB, SUELO_DB)
    val maximo = floatArrayOf(SUELO_DB, SUELO_DB)
    private val retenidoEn = LongArray(2)
    private val leido = FloatArray(4)

    fun reiniciar() { maximo.fill(SUELO_DB) }

    /** Avanza un cuadro; devuelve si cambió algo que haya que volver a dibujar. [leer] da pico L, R y RMS L, R. */
    fun avanzar(ahora: Long, dt: Float, activo: Boolean, leer: (FloatArray) -> Unit = Niveles::tomar): Boolean {
        leer(leido)
        var cambio = false
        for (ch in 0..1) {
            val pico = if (activo) aDb(leido[ch], SUELO_DB) else SUELO_DB
            val rms = if (activo) aDb(leido[2 + ch], SUELO_DB) else SUELO_DB
            val n = maxOf(rms, nivel[ch] - CAIDA_DB_S * dt).coerceAtLeast(SUELO_DB)
            if (n != nivel[ch]) { nivel[ch] = n; cambio = true }
            if (pico >= retenido[ch]) { if (pico != retenido[ch]) cambio = true; retenido[ch] = pico; retenidoEn[ch] = ahora }
            else if (ahora - retenidoEn[ch] > RETENCION_NS && retenido[ch] > SUELO_DB) {
                retenido[ch] = maxOf(pico, retenido[ch] - CAIDA_DB_S * dt); cambio = true
            }
            if (pico > maximo[ch]) { maximo[ch] = pico; cambio = true }
        }
        return cambio
    }
}

/** Espectro en barras (FFT de 1024 muestras con ventana de Hann, bandas logarítmicas) y la onda para el ámbito. */
internal class Espectro(bandas: Int = 28) {
    val barras = FloatArray(bandas)
    val topes = FloatArray(bandas)
    val trazo = FloatArray(256)
    private val n = 1024
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val onda = FloatArray(n)
    private val ventana = FloatArray(n) { (0.5 - 0.5 * kotlin.math.cos(2 * Math.PI * it / (n - 1))).toFloat() }
    // De ~45 Hz a ~17 kHz (a 44-48 kHz): del bin 1 al 380, repartidos en escala logarítmica.
    private val borde = IntArray(bandas + 1) { Math.round(Math.pow(380.0, it / bandas.toDouble())).toInt() }

    fun avanzar(dt: Float, activo: Boolean, leer: (FloatArray) -> Unit = Niveles::copiar) {
        if (activo) leer(onda) else onda.fill(0f)
        for (i in 0 until n) { re[i] = onda[i] * ventana[i]; im[i] = 0f }
        fft(re, im)
        for (k in barras.indices) {
            val lo = borde[k]; val hi = maxOf(borde[k + 1], lo + 1)
            var m = 0f
            for (b in lo until hi) { val v = re[b] * re[b] + im[b] * im[b]; if (v > m) m = v }
            // Un seno a fondo de escala da n/4 con Hann. Los agudos llevan menos energía: +0,6 dB por banda los iguala a la vista.
            val db = 10f * kotlin.math.log10(m / (n / 4f * n / 4f) + 1e-12f) + .6f * k
            val v = ((db + 60f) / 54f).coerceIn(0f, 1f)
            barras[k] = maxOf(v, barras[k] - 2.2f * dt)
            topes[k] = maxOf(barras[k], topes[k] - .45f * dt)
        }
    }

    /** La onda para el ámbito: empieza en un cruce por cero hacia arriba, para que no baile de lado. */
    fun onda(activo: Boolean, leer: (FloatArray) -> Unit = Niveles::copiar) {
        if (!activo) { trazo.fill(0f); return }
        leer(onda)
        var ini = 0
        for (i in 1 until n - trazo.size * 2) if (onda[i - 1] < 0f && onda[i] >= 0f) { ini = i; break }
        for (i in trazo.indices) trazo[i] = onda[ini + i * 2]
    }
}

/** FFT radix-2 en sitio; el tamaño tiene que ser potencia de dos. */
internal fun fft(re: FloatArray, im: FloatArray) {
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
        val ang = -2 * Math.PI / len
        val wr = kotlin.math.cos(ang).toFloat(); val wi = kotlin.math.sin(ang).toFloat()
        var i = 0
        while (i < n) {
            var cr = 1f; var ci = 0f
            for (k in 0 until len / 2) {
                val a = i + k; val b = a + len / 2
                val xr = re[b] * cr - im[b] * ci; val xi = re[b] * ci + im[b] * cr
                re[b] = re[a] - xr; im[b] = im[a] - xi
                re[a] += xr; im[a] += xi
                val t = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = t
            }
            i += len
        }
        len = len shl 1
    }
}
