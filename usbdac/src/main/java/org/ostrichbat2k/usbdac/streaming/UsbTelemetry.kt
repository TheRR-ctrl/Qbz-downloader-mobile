package org.ostrichbat2k.usbdac.streaming

/**
 * Instantánea de una línea del estado del envío USB, que la app lee cada segundo para su registro en disco.
 * La fija la salida USB que está viva y la quita al liberarse.
 */
object UsbTelemetry {
    @Volatile var snapshot: (() -> String)? = null

    /** Diagnóstico: si es true, un atasco no se recupera solo (ni reactiva ni recrea la salida), para verlo tal cual. */
    @Volatile var sinRecuperacionAutomatica: Boolean = false
}
