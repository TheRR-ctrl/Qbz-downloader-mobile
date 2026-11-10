package org.ostrichbat2k.usbdac.streaming

/**
 * Lo que está saliendo ahora, para dibujarlo: pico y energía (RMS) por canal desde la última lectura, y un anillo
 * con las últimas muestras en mono (a unos 44-48 kHz) para el espectro y el osciloscopio. Lo apunta quien manda el
 * audio (el motor USB, o el envoltorio de la salida normal) y lo lee la pantalla. Las carreras entre hilos solo
 * pueden perder unos milisegundos: es un medidor.
 */
object Niveles {
    @Volatile private var izq = 0f
    @Volatile private var der = 0f
    @Volatile private var sumaL = 0.0
    @Volatile private var sumaR = 0.0
    @Volatile private var cuenta = 0

    const val ANILLO = 4096
    private val anillo = ShortArray(ANILLO)
    @Volatile private var pos = 0

    fun anotar(l: Float, r: Float) { if (l > izq) izq = l; if (r > der) der = r }

    /** Suma de cuadrados (muestras en 0..1) de [n] tramas, para el RMS. */
    fun anotarEnergia(l: Double, r: Double, n: Int) { sumaL += l; sumaR += r; cuenta += n }

    /** Una muestra mono de 16 bits al anillo. */
    fun muestra(v: Int) { val p = pos; anillo[p] = v.toShort(); pos = (p + 1) and (ANILLO - 1) }

    /** Cada cuántas tramas se guarda una en el anillo para que quede a 44-48 kHz. */
    fun paso(frecuenciaHz: Int): Int = (frecuenciaHz / 44_100).coerceAtLeast(1)

    /** Pico (izquierdo, derecho) y, si caben, RMS (izquierdo, derecho) desde la última lectura; lo pone todo a cero. */
    fun tomar(out: FloatArray) {
        out[0] = izq; out[1] = der; izq = 0f; der = 0f
        val n = cuenta
        if (out.size >= 4) {
            out[2] = if (n > 0) kotlin.math.sqrt(sumaL / n).toFloat() else 0f
            out[3] = if (n > 0) kotlin.math.sqrt(sumaR / n).toFloat() else 0f
        }
        sumaL = 0.0; sumaR = 0.0; cuenta = 0
    }

    /** Las últimas [dest].size muestras del anillo, en -1..1, la más nueva al final. */
    fun copiar(dest: FloatArray) {
        var p = (pos - dest.size) and (ANILLO - 1)
        for (i in dest.indices) { dest[i] = anillo[p] / 32768f; p = (p + 1) and (ANILLO - 1) }
    }

    /** PCM entero little-endian intercalado: mira solo los dos bytes altos de cada muestra, que sobra para un medidor. */
    fun medirEnteros(buf: ByteArray, length: Int, subslot: Int, canales: Int, paso: Int = 1) {
        if (subslot < 2 || canales < 1) return
        val trama = subslot * canales
        var l = 0; var r = 0
        var sl = 0L; var sr = 0L
        var i = 0; var n = 0
        val fin = length - length % trama
        while (i < fin) {
            val a = s16(buf, i + subslot - 2)
            val b = if (canales > 1) s16(buf, i + 2 * subslot - 2) else a
            val aa = if (a < 0) -a else a; val bb = if (b < 0) -b else b
            if (aa > l) l = aa
            if (bb > r) r = bb
            sl += a.toLong() * a; sr += b.toLong() * b
            if (n % paso == 0) muestra((a + b) shr 1)
            n++; i += trama
        }
        if (n == 0) return
        anotar(l / 32768f, r / 32768f)
        anotarEnergia(sl / 1073741824.0, sr / 1073741824.0, n)
    }

    private fun s16(buf: ByteArray, i: Int): Int = (buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)
}

/** De pico lineal a dBFS, con suelo en [suelo]. */
fun aDb(pico: Float, suelo: Float = -60f): Float =
    if (pico <= 0f) suelo else (20f * kotlin.math.log10(pico)).coerceIn(suelo, 6f)
