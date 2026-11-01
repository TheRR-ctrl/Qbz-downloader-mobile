package com.qbdlx.lyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.*

/**
 * Enabled by the user under "Notification access": that is what lets the app read the media sessions. It also keeps
 * a quiet notification with the song and the line being sung, tinted with the playing app's colour.
 */
class ListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var shown = ""

    override fun onListenerConnected() {
        instance = this
        Tracker.start(this)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Letra", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        scope.coroutineContext.cancelChildren()
        scope.launch {
            while (isActive) { runCatching { update() }; delay(500) }
        }
    }

    override fun onListenerDisconnected() { instance = null; scope.coroutineContext.cancelChildren() }

    private fun update() {
        val nm = getSystemService(NotificationManager::class.java)
        val st = Tracker.state.value
        val track = st.track
        if (track == null || st.status == Status.NO_MEDIA || st.status == Status.NO_ACCESS) {
            if (shown != "") { nm.cancel(ID); shown = "" }
            return
        }
        val line = when (st.status) {
            Status.SYNCED -> Lrc.indexAt(st.lines, Tracker.positionMs()).let { if (it < 0) "♪ …" else st.lines[it].text }
            Status.PLAIN -> "Letra sin sincronizar: ábrela en la app"
            Status.LOADING -> "Buscando la letra…"
            Status.NOT_FOUND -> "No hay letra para esta canción"
            else -> "No se pudo cargar la letra"
        }
        val accent = Tracker.accent.value
        val sig = "${track.key}|$line|$accent"
        if (sig == shown) return
        shown = sig
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(ID, Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(line.ifBlank { "♪" })
            .setContentText("${track.title} — ${track.artist}")
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .apply { if (accent != 0) setColor(accent) }
            .setContentIntent(open)
            .build())
    }

    override fun onDestroy() { scope.cancel(); if (instance === this) instance = null; super.onDestroy() }

    companion object {
        private const val CHANNEL = "lyrics"
        private const val ID = 21
        @Volatile var instance: ListenerService? = null
    }
}
