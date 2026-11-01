package com.qbdlx.lyrics

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/** Keeps the lyrics download running with the screen off, with a progress notification. */
class PrefetchService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Descarga de letras", NotificationManager.IMPORTANCE_LOW))
        val n = notification(this, 0, 0)
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "prefetch"; private const val ID = 77
        private fun notification(c: Context, done: Int, total: Int) = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Descargando letras")
            .setContentText(if (total > 0) "$done de $total canciones" else "Preparando…").setOnlyAlertOnce(true).setOngoing(true)
            .apply { if (total > 0) setProgress(total, done, false) }.build()
        fun start(c: Context) { runCatching { c.startForegroundService(Intent(c, PrefetchService::class.java)) } }
        fun stop(c: Context) { runCatching { c.stopService(Intent(c, PrefetchService::class.java)) } }
        fun update(c: Context, done: Int, total: Int) {
            if (done % 5 == 0 || done == total) runCatching { c.getSystemService(NotificationManager::class.java).notify(ID, notification(c, done, total)) }
        }
    }
}
