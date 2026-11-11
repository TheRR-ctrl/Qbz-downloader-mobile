package org.ostrichbat2k.usbdac.streaming

/**
 * Lo que está saliendo ahora, para dibujarlo: un anillo con el último segundo y medio de audio en estéreo, a unos
 * 44-48 kHz. Lo escribe quien manda el audio (el motor USB, o el envoltorio de la salida normal) y lo lee la
 * pantalla con un [Lector]. Las carreras entre hilos solo pueden torcer unos milisegundos: es un medidor.
 */
object Niveles {
    const val ANILLO = 65536
    private val anilloL = ShortArray(ANILLO)
    private val anilloR = ShortArray(ANILLO)

    /** Muestras escritas desde que arrancó la app. */
    @Volatile var escritas = 0L; private set

    /** Frecuencia de lo que hay en el anillo. */
    @Volatile var frecuencia = 44_100; private set

    /**
     * Hasta qué muestra se puede enseñar: la salida normal de Android acepta el audio antes de que suene (llena su
     * búfer al cargar la pista), y sin este tope el medidor se movía antes que la música. Sin tope, MAX_VALUE.
     */
    @Volatile var limite = Long.MAX_VALUE

    /** Una trama de 16 bits al anillo. */
    fun muestra(l: Int, r: Int) {
        val w = escritas
        val p = (w and (ANILLO - 1).toLong()).toInt()
        anilloL[p] = l.toShort(); anilloR[p] = r.toShort()
        escritas = w + 1
    }

    /** Cada cuántas tramas se guarda una para que el anillo quede a 44-48 kHz; apunta la frecuencia que resulta. */
    fun paso(frecuenciaHz: Int): Int = (frecuenciaHz / 44_100).coerceAtLeast(1).also { frecuencia = frecuenciaHz / it }

    /** PCM entero little-endian intercalado: guarda los dos bytes altos de cada muestra, que sobra para un medidor. */
    fun medirEnteros(buf: ByteArray, length: Int, subslot: Int, canales: Int, paso: Int = 1) {
        if (subslot < 2 || canales < 1) return
        val trama = subslot * canales
        var i = 0; var n = 0
        val fin = length - length % trama
        while (i < fin) {
            if (n % paso == 0) {
                val a = s16(buf, i + subslot - 2)
                muestra(a, if (canales > 1) s16(buf, i + 2 * subslot - 2) else a)
            }
            n++; i += trama
        }
    }

    private fun s16(buf: ByteArray, i: Int): Int = (buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)

    /**
     * Lee el anillo al ritmo del reloj, no al de quien escribe: la salida de Android entrega el audio a tirones
     * (un bloque grande cada vez que hay sitio) y dibujar a ese ritmo se veía lento y a saltos.
     */
    class Lector {
        private var cursor = -1.0
        private var antes = 0L

        /** Avanza [dt] segundos. Si se queda sin datos espera; si se queda muy atrás, salta a lo último. */
        fun avanzar(dt: Float) {
            val w = minOf(escritas, limite).coerceAtLeast(0)
            antes = cursor.toLong().coerceAtLeast(0)
            if (cursor < 0 || w - cursor > ANILLO * 3 / 4 || cursor > w) { cursor = w.toDouble(); antes = w; return }
            cursor = minOf(cursor + frecuencia * dt.toDouble(), w.toDouble())
        }

        /** Pico L, R y RMS L, R (0..1) de lo que pasó en el último [avanzar]. */
        fun nivel(out: FloatArray) {
            val hasta = cursor.toLong()
            var pl = 0; var pr = 0; var sl = 0L; var sr = 0L
            var i = antes
            while (i < hasta) {
                val p = (i and (ANILLO - 1).toLong()).toInt()
                val a = anilloL[p].toInt(); val b = anilloR[p].toInt()
                val aa = if (a < 0) -a else a; val bb = if (b < 0) -b else b
                if (aa > pl) pl = aa
                if (bb > pr) pr = bb
                sl += a.toLong() * a; sr += b.toLong() * b
                i++
            }
            val n = hasta - antes
            out[0] = pl / 32768f; out[1] = pr / 32768f
            out[2] = if (n > 0) kotlin.math.sqrt(sl.toDouble() / n).toFloat() / 32768f else 0f
            out[3] = if (n > 0) kotlin.math.sqrt(sr.toDouble() / n).toFloat() / 32768f else 0f
        }

        /** Las [dest].size muestras en mono (-1..1) que terminan donde va el lector, la más nueva al final. */
        fun ventana(dest: FloatArray) {
            var i = cursor.toLong() - dest.size
            for (k in dest.indices) {
                if (i < 0) dest[k] = 0f else {
                    val p = (i and (ANILLO - 1).toLong()).toInt()
                    dest[k] = (anilloL[p] + anilloR[p]) / 65536f
                }
                i++
            }
        }
    }
}

/** De pico lineal a dBFS, con suelo en [suelo]. */
fun aDb(pico: Float, suelo: Float = -60f): Float =
    if (pico <= 0f) suelo else (20f * kotlin.math.log10(pico)).coerceIn(suelo, 6f)
