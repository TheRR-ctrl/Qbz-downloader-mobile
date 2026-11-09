package com.qbdlx.mobile

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Owns the [MainViewModel] for the whole process instead of for one Activity, so the download
 * queue (and the session it signs requests with) survives the Activity being closed while the
 * foreground [DownloadService] keeps the process alive.
 */
class QbdlxApp : Application(), ViewModelStoreOwner, coil.ImageLoaderFactory {
    override val viewModelStore = ViewModelStore()

    /** Covers fade in when they arrive from the network or the disk (not from memory, so lists that scroll back do not flicker). */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this).crossfade(220).build()

    override fun onCreate() {
        super.onCreate()
        DefaultFolder.init(this)
        DiagnosticLog.start(this)
        org.ostrichbat2k.usbdac.streaming.UsbTelemetry.sinRecuperacionAutomatica = getSharedPreferences("qbdlx", 0).getBoolean("diag_sin_recuperacion", false)
        org.ostrichbat2k.usbdac.streaming.UsbTelemetry.sinFeedback = getSharedPreferences("qbdlx", 0).getBoolean("diag_sin_feedback", true)
        org.ostrichbat2k.usbdac.streaming.UsbTelemetry.sinPistaSilenciosa = getSharedPreferences("qbdlx", 0).getBoolean("diag_sin_pista_silenciosa", false)
        org.ostrichbat2k.usbdac.streaming.UsbTelemetry.bloquesMitad = getSharedPreferences("qbdlx", 0).getBoolean("diag_bloques_mitad", false)
        org.ostrichbat2k.usbdac.streaming.UsbTelemetry.paquetes120 = getSharedPreferences("qbdlx", 0).getBoolean("diag_paquetes_120", false)
        org.ostrichbat2k.usbdac.streaming.UsbTelemetry.reabrirAntes255 = getSharedPreferences("qbdlx", 0).getBoolean("diag_reabrir_255", false)
        org.ostrichbat2k.usbdac.streaming.UsbTelemetry.evitarBorde255 = getSharedPreferences("qbdlx", 0).getBoolean("diag_evitar_borde_255", true)
        org.ostrichbat2k.usbdac.streaming.UsbTelemetry.masMargen = getSharedPreferences("qbdlx", 0).getBoolean("diag_mas_margen", false)
        org.ostrichbat2k.usbdac.streaming.UsbTelemetry.comoDecent = getSharedPreferences("qbdlx", 0).getBoolean("diag_como_decent", true)
        org.ostrichbat2k.usbdac.streaming.UsbTelemetry.pedirRendimiento = getSharedPreferences("qbdlx", 0).getBoolean("diag_pedir_rendimiento", true)
        org.ostrichbat2k.usbdac.streaming.UsbTelemetry.abrirSesionRendimiento = { tid, objetivoNs ->
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                getSystemService(android.os.PerformanceHintManager::class.java)
                    ?.createHintSession(intArrayOf(tid), objetivoNs)
                    ?.let { sesion -> { realNs: Long -> sesion.reportActualWorkDuration(realNs) } }
            } else null
        }
        // Keep the last crash, so it can be shared from Settings and the cause found.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                DiagnosticLog.event("CRASH ${t.name}: ${e}")
                java.io.File(filesDir, CRASH_FILE).writeText("${java.util.Date()} thread ${t.name}\n${android.util.Log.getStackTraceString(e)}")
            }
            previous?.uncaughtException(t, e)
        }
    }

    companion object {
        const val CRASH_FILE = "last-crash.txt"

        /** Set by the ViewModel; read by [DownloadService] to show progress. */
        @Volatile var downloads: DownloadManager? = null

        /** Set by the ViewModel; read by [PlaybackService] for the "now playing" notification. */
        @Volatile var playback: PlaybackControls? = null
    }
}
