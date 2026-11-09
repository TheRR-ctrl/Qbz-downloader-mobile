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
     * Diagnóstico: no abre el hilo del feedback. Audio y feedback comparten el mismo descriptor USB y
     * REAPURB devuelve el primer bloque terminado de cualquiera de los dos; en el registro el hilo del
     * feedback recogió bloques de audio (128 paquetes) al arrancar cada salida.
     */
    @Volatile var sinFeedback: Boolean = false

    /**
     * Diagnóstico: no crea la pista silenciosa de 8000 Hz (setDirectPlaybackActive). `dumpsys
     * media.audio_flinger` mostró que esa pista es la que mantiene AudioOut_15 (USB_HEADSET) fuera de
     * standby y al HAL escribiendo al mismo DAC que libusbdac maneja directamente. Sin ella se pierde el
     * enrutado de los botones multimedia; es solo para medir.
     */
    @Volatile var sinPistaSilenciosa: Boolean = false
}
