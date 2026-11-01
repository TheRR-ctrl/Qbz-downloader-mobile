package com.qbdlx.scanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.Locale

/** Keeps the process alive, with a notification, while a scan runs. Stops itself when the scan ends. */
class ScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("scan", if (es) "Escaneo" else "Scan", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        scope.launch {
            ScanRunner.progress.collect { p ->
                if (p == null) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
                else nm.notify(1, notification(p))
            }
        }
        return START_NOT_STICKY
    }

    private val es get() = Locale.getDefault().language == "es"

    private fun notification(p: Progress?): Notification = Notification.Builder(this, "scan")
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle(if (es) "Escaneando canciones" else "Scanning songs")
        .setContentText(p?.takeIf { it.total > 0 }?.let { "${it.done}/${it.total}" } ?: "")
        .setOngoing(true).setOnlyAlertOnce(true)
        .setProgress(p?.total ?: 0, p?.done ?: 0, p == null || p.total == 0)
        .setContentIntent(android.app.PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE))
        .build()

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, ScanService::class.java))
    }
}
