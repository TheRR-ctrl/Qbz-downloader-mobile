package com.qbdlx.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the process alive and shows progress while the [DownloadManager] works, so downloads
 * continue with the screen off or the app closed. The transfers themselves run in the manager;
 * this service only owns the notification and the wake/Wi‑Fi locks.
 *
 * Started only while the app is visible (a user action or opening the app with queued work), and it
 * stays up while anything is pending, downloading, retrying or renewing credentials, so it never has
 * to be started from the background (which Android 12+ forbids).
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null
    private var lastStartId = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, strings().dlNotifChannel, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val downloads = QbdlxApp.downloads
        // Must be promoted within a few seconds of startForegroundService(), or Android kills the app.
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, progressNotification(downloads?.summary()),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } catch (e: Exception) {
            Diagnostics.log("[descargas] no se pudo iniciar el servicio en primer plano: ${e.javaClass.simpleName}: ${e.message}")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // A finished loop must be restartable: new work can arrive just as the previous run was winding down.
        if (loop?.isActive != true) loop = scope.launch { watch() }
        // Not sticky: a killed process is not resurrected from the background; the saved queue
        // continues the next time the user opens the app.
        return START_NOT_STICKY
    }

    /** Android 15 caps data-sync foreground services (6 h per 24 h); it must stop within seconds. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        QbdlxApp.downloads?.let { it.pauseForSystemLimit(); it.flush() }
        stopSelf(startId)
    }

    private suspend fun watch() {
        var idleTicks = 0
        var lastSummary: DownloadSummary? = null
        val nm = getSystemService(NotificationManager::class.java)
        while (scope.isActive) {
            val downloads = QbdlxApp.downloads
            val summary = downloads?.summary()
            if (summary != null) lastSummary = summary
            if (downloads == null || !downloads.isActive()) {
                // A couple of quiet ticks before stopping, so a task moving between states doesn't flap the service.
                if (++idleTicks >= 2) break
            } else {
                idleTicks = 0
                setLocks(held = (summary?.downloading ?: 0) > 0)
                nm.notify(NOTIF_ID, progressNotification(summary))
            }
            delay(1_000)
        }
        finish(lastSummary)
    }

    private fun finish(last: DownloadSummary?) {
        setLocks(held = false)
        QbdlxApp.downloads?.flush()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (last != null && last.done > 0) {
            getSystemService(NotificationManager::class.java).notify(
                NOTIF_DONE_ID,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(strings().dlNotifDone(last.done, last.failed))
                    .setContentIntent(openAppIntent())
                    .setAutoCancel(true)
                    .setSilent(true)
                    .build(),
            )
        }
        // Only stops if no newer start request arrived meanwhile; otherwise that one re-promotes the service.
        stopSelf(lastStartId)
    }

    private fun progressNotification(s: DownloadSummary?): Notification {
        val strings = strings()
        val title = if (s == null || s.total == 0) strings.dlNotifChannel else strings.dlNotifTitle(s.done, s.total)
        val text = s?.currentTitles?.take(3)?.joinToString(" · ").orEmpty()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, ((s?.overall ?: 0f) * 100).toInt(), s == null || s.total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Without these the CPU (and on some phones the Wi‑Fi radio) can sleep once the screen is off,
     * even inside a foreground service. They are held only while bytes are actually moving, and the
     * wake lock has a timeout so a bug can never leave it on.
     */
    @Suppress("DEPRECATION")
    private fun setLocks(held: Boolean) {
        runCatching {
            if (held) {
                val power = wakeLock ?: getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "qbdlx:downloads")
                    .apply { setReferenceCounted(false) }
                    .also { wakeLock = it }
                power.acquire(5 * 60_000L) // renewed every tick while downloading

                val wifi = wifiLock ?: applicationContext.getSystemService(WifiManager::class.java)
                    .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "qbdlx:downloads")
                    .apply { setReferenceCounted(false) }
                    .also { wifiLock = it }
                if (!wifi.isHeld) wifi.acquire()
            } else {
                wakeLock?.takeIf { it.isHeld }?.release()
                wifiLock?.takeIf { it.isHeld }?.release()
            }
        }
    }

    override fun onDestroy() {
        setLocks(held = false)
        scope.cancel()
        super.onDestroy()
    }

    private fun strings(): Strings =
        Language.fromCode(getSharedPreferences("qbdlx", 0).getString("language", null)).resolve()

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIF_ID = 1001
        private const val NOTIF_DONE_ID = 1002

        /** Safe to call from anywhere; Android forbids this from the background, so failures are logged, not thrown. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
            } catch (e: Exception) {
                Diagnostics.log("[descargas] Android no permitió iniciar el servicio: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }
}
