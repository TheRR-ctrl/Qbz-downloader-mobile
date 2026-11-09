package org.ostrichbat2k.usbdac.streaming

/**
 * Instantánea de una línea del estado del envío USB, que la app lee cada segundo para su registro en disco.
 * La fija la salida USB que está viva y la quita al liberarse.
 */
object UsbTelemetry {
    @Volatile var snapshot: (() -> String)? = null

    /** Diagnóstico: si es true, un atasco no se recupera solo (ni reactiva ni recrea la salida), para verlo tal cual. */
    @Volatile var sinRecuperacionAutomatica: Boolean = false

    /**
     * Por omisión true (medido: con el hilo del feedback el cuelgue era cada ~8 s; sin él, uno en ~11 min). No abre el hilo del feedback. Audio y feedback comparten el mismo descriptor USB y
     * REAPURB devuelve el primer bloque terminado de cualquiera de los dos; en el registro el hilo del
     * feedback recogió bloques de audio (128 paquetes) al arrancar cada salida.
     */
    @Volatile var sinFeedback: Boolean = true

    /**
     * Diagnóstico: no crea la pista silenciosa de 8000 Hz (setDirectPlaybackActive). `dumpsys
     * media.audio_flinger` mostró que esa pista es la que mantiene AudioOut_15 (USB_HEADSET) fuera de
     * standby y al HAL escribiendo al mismo DAC que libusbdac maneja directamente. Sin ella se pierde el
     * enrutado de los botones multimedia; es solo para medir.
     */
    @Volatile var sinPistaSilenciosa: Boolean = false

    /**
     * Diagnóstico: bloques de la mitad de tamaño (8 en vez de 16 paquetes base; 64 en vez de 128 en USB 2.0).
     * Tras cada arranque el primer cuelgue llegó siempre a ~512 bloques enviados (508 + 4 de inicio), a 48 y
     * a 88,2 kHz, con y sin feedback: 512 × 128 = 65536 paquetes ≈ 8,19 s. Con bloques a la mitad se separa
     * un límite por número de bloques (cuelgue a ~4,1 s) de uno por paquetes o tiempo del bus (~8,2 s).
     */
    @Volatile var bloquesMitad: Boolean = false

    /**
     * Diagnóstico: 15 paquetes base por bloque (120 en USB 2.0) en vez de 16. Medido: todos los cuelgues
     * caen en el bloque nº 255·n (contando los 4 iniciales), con 128 y con 64 paquetes por bloque. Si el
     * disparador es el inicio de un bloque alineado con un segmento de 255 TRB del anillo del xHCI, con
     * 120 paquetes (mcd(120, 255) = 15) la alineación ocurre cada 17 bloques y el cuelgue caerá en 17·n.
     * Tiene prioridad sobre [bloquesMitad].
     */
    @Volatile var paquetes120: Boolean = false

    /**
     * Prueba de arreglo: antes de que el número total de bloques enviados (contando los iniciales)
     * llegue a un múltiplo de 255 -- medido: ahí caen todos los cuelgues --, el hilo deja de enviar,
     * espera a que terminen los bloques en vuelo (sin cancelarlos ni perder audio del búfer), pasa la
     * interfaz a alt 0 y de vuelta al alt de la pista, y sigue. No toca la reclamación ni el reloj.
     * Supone que el cambio de alt reinicia lo que cuenta hasta 255 (la reactivación completa sí lo
     * reinicia; esto no está comprobado). Obliga a «sin feedback»: con el hilo del feedback, un reap
     * podría devolver un bloque suyo en medio del vaciado.
     */
    @Volatile var reabrirAntes255: Boolean = false

    /**
     * Prueba de arreglo sin cortes: medido, los cuelgues caen en el bloque nº 255·n, que con 128 o 64
     * paquetes por bloque es el que termina justo en un múltiplo de 255 paquetes (el último hueco de un
     * segmento del anillo del xHCI de Linux: 256 TRB, uno de enlace). Con esto, ese bloque lleva un
     * paquete más y termina un hueco después. No reabre nada. Hipótesis: si los cuelgues siguen, el
     * borde no es la causa.
     */
    @Volatile var evitarBorde255: Boolean = true

    /**
     * Prueba de arreglo: 12 bloques en vuelo en vez de 4. Medido: con el teléfono quieto, 11 min sin
     * cortes; al usarlo, el hilo de envío llegó a retrasarse 67 ms, más que los 32-64 ms que cubren 4
     * bloques, y la cola del controlador se vació justo antes de un cuelgue. Con 12 caben 96-192 ms.
     */
    @Volatile var masMargen: Boolean = false

    /**
     * Prueba de arreglo: la forma de envío de decent-player (MIT), probada por su autor en un Galaxy
     * S26 Ultra: 80 bloques en vuelo de 1 ms (8 paquetes en USB 2.0), ~80 ms de margen. Manda sobre
     * «mitad», «120 paquetes» y «más margen».
     */
    @Volatile var comoDecent: Boolean = true

    /** Prueba de arreglo: pedir rendimiento al sistema para el hilo de envío (Performance Hint API, Android 12+). */
    @Volatile var pedirRendimiento: Boolean = true

    /**
     * La pone la app: abre una sesión de Performance Hint para el hilo [tid] con un objetivo de
     * [objetivoNs] y devuelve la función que informa del trabajo real (ns), o null si no se pudo.
     * El módulo no depende de un Context, por eso lo hace la app.
     */
    @Volatile var abrirSesionRendimiento: ((tid: Int, objetivoNs: Long) -> ((Long) -> Unit)?)? = null

    /**
     * Volumen digital (0..1) del controlador USB propio, para DAC que no exponen volumen por hardware
     * (el BTR5 no lo hace). Con 1 la señal va tal cual (bit-perfect); por debajo se escala antes de
     * convertir a entero, como el volumen por software de UAPP.
     */
    @Volatile var volumenSoftware: Float = 1f
}
