package org.ostrichbat2k.usbdac.streaming

/**
 * Pico de la señal que está saliendo ahora, por canal (0..1, 1 = fondo de escala). Lo apuntan quien manda el audio
 * (el motor USB, o el envoltorio de la salida normal) y lo lee la pantalla, que se queda con el máximo desde su
 * última lectura. Las carreras entre hilos solo pueden perder un pico de unos milisegundos: es un medidor.
 */
object Niveles {
    @Volatile private var izq = 0f
    @Volatile private var der = 0f

    fun anotar(l: Float, r: Float) { if (l > izq) izq = l; if (r > der) der = r }

    /** Devuelve el pico acumulado (izquierdo, derecho) y lo pone a cero. */
    fun tomar(out: FloatArray) { out[0] = izq; out[1] = der; izq = 0f; der = 0f }

    /** PCM entero little-endian intercalado: mira solo los dos bytes altos de cada muestra, que sobra para un medidor. */
    fun medirEnteros(buf: ByteArray, length: Int, subslot: Int, canales: Int) {
        if (subslot < 2 || canales < 1) return
        val trama = subslot * canales
        var l = 0; var r = 0
        var i = 0
        val fin = length - length % trama
        while (i < fin) {
            val a = abs16(buf, i + subslot - 2)
            if (a > l) l = a
            if (canales > 1) { val b = abs16(buf, i + 2 * subslot - 2); if (b > r) r = b }
            i += trama
        }
        if (canales == 1) r = l
        anotar(l / 32768f, r / 32768f)
    }

    private fun abs16(buf: ByteArray, i: Int): Int {
        val v = (buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)
        return if (v < 0) -v else v
    }
}

/** De pico lineal a dBFS, con suelo en [suelo]. */
fun aDb(pico: Float, suelo: Float = -60f): Float =
    if (pico <= 0f) suelo else (20f * kotlin.math.log10(pico)).coerceIn(suelo, 6f)
