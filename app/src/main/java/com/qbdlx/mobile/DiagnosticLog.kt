package com.qbdlx.mobile

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import org.ostrichbat2k.usbdac.streaming.UsbTelemetry
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro continuo en disco, una línea por segundo y con fsync, para que sobreviva a un cuelgue o
 * apagado brusco del teléfono. Al arrancar la app siguiente, el final de la sesión anterior sale en
 * el informe, con la hora de su última línea.
 */
object DiagnosticLog {
    private const val DIR = "diag"
    private const val KEEP = 5
    private const val MAX_BYTES = 5L * 1024 * 1024

    @Volatile private var out: FileOutputStream? = null
    @Volatile private var started = false
    private val hora = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val dir = File(app.filesDir, DIR).apply { mkdirs() }
        // Una sesión que termina bien lo escribe; así se distingue de un apagado.
        dir.listFiles()?.sortedBy { it.name }?.dropLast(KEEP - 1)?.forEach { it.delete() }
        val file = File(dir, "sesion-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".log")
        out = runCatching { FileOutputStream(file, true) }.getOrNull()
        event("INICIO app=${runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()} android=${Build.VERSION.SDK_INT} ${Build.MODEL}")
        val t = Thread({
            while (true) {
                runCatching { Thread.sleep(1000) }
                runCatching { line(app) }
            }
        }, "diag-log")
        t.isDaemon = true
        t.start()
    }

    @Synchronized fun event(text: String) {
        val o = out ?: return
        runCatching {
            if (o.channel.size() > MAX_BYTES) return
            o.write("${hora.format(Date())} $text\n".toByteArray())
            o.fd.sync()
        }
    }

    private fun line(context: Context) {
        val usb = UsbTelemetry.snapshot?.let { runCatching { it() }.getOrDefault("(error al leer)") } ?: "sin salida USB"
        event("$usb · ${aparato(context)}")
    }

    private fun aparato(context: Context): String {
        val b: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val nivel = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / 10.0
        val carga = b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val termico = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
        }.getOrDefault(-1)
        val mem = ActivityManager.MemoryInfo().also { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        return "bat=$nivel% ${temp}°C enchufado=$carga térmico=$termico memLibre=${mem.availMem / 1048576}MB bajaMem=${mem.lowMemory}"
    }

    /** Texto para el informe: el final de la sesión anterior y por qué murió el proceso la última vez. */
    fun previousSession(context: Context): String {
        val files = File(context.filesDir, DIR).listFiles()?.sortedBy { it.name }.orEmpty()
        val prev = files.dropLast(1).lastOrNull() ?: return "(no hay sesión anterior)"
        val lines = runCatching { prev.readLines() }.getOrDefault(emptyList())
        val sb = StringBuilder("${prev.name} · última línea: ${lines.lastOrNull()?.take(8) ?: "(vacío)"} (si es mucho antes que ahora, el teléfono o la app se pararon ahí)")
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getHistoricalProcessExitReasons(context.packageName, 0, 3).forEach {
                sb.append("\nsalida del proceso: ${motivo(it.reason)} (${it.description}) ${Date(it.timestamp)}")
            }
        }
        sb.append("\n").append(lines.takeLast(200).joinToString("\n"))
        return sb.toString()
    }

    private fun motivo(r: Int): String = when (r) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash nativo"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "poca memoria"
        ApplicationExitInfo.REASON_SIGNALED -> "señal"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "pedido por el usuario"
        ApplicationExitInfo.REASON_USER_STOPPED -> "detenido por el usuario"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "uso excesivo de recursos"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "murió una dependencia"
        ApplicationExitInfo.REASON_OTHER -> "otro"
        else -> "código $r"
    }
}
