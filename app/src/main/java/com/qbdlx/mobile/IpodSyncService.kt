package com.qbdlx.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the process (and the CPU) awake while songs are copied to the iPod, so switching apps or locking the phone
 * does not stop the transfer. The notification itself is updated by the ViewModel under the same id.
 */
class IpodSyncService : Service() {
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("ipod", "iPod", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val n = Notification.Builder(this, "ipod").setSmallIcon(R.drawable.ic_stat).setContentTitle("iPod").setOngoing(true).build()
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        if (wake == null) wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "qbdlx:ipod").apply { acquire(6 * 60 * 60 * 1000L) }
        return START_NOT_STICKY
    }

    override fun onDestroy() { runCatching { wake?.takeIf { it.isHeld }?.release() }; wake = null; super.onDestroy() }

    companion object {
        const val NOTIF_ID = 4217
        fun start(context: Context) { runCatching { ContextCompat.startForegroundService(context, Intent(context, IpodSyncService::class.java)) } }
        fun stop(context: Context) { context.stopService(Intent(context, IpodSyncService::class.java)) }
    }
}
