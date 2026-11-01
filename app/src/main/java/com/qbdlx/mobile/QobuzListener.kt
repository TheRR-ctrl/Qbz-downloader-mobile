package com.qbdlx.mobile

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * Its only job is to exist: with "Notification access" granted to it, Android lets the app read the media sessions,
 * and so what the official Qobuz app is playing.
 */
class QobuzListener : NotificationListenerService()

/** What another app is playing right now. */
class PlayingNow(val title: String, val artist: String, val album: String, val durationSec: Int, val app: String)

object QobuzNowPlaying {
    private const val QOBUZ = "com.qobuz.music"

    fun accessGranted(context: Context) = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** The song the official Qobuz app has on (playing or paused); null if it has none or access was not given. */
    fun read(context: Context): PlayingNow? = runCatching {
        val sessions = context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, QobuzListener::class.java))
        val s = sessions.firstOrNull { it.packageName.startsWith(QOBUZ) } ?: return null
        val m = s.metadata ?: return null
        val title = m.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().trim()
        if (title.isBlank()) return null
        PlayingNow(
            title,
            (m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty().trim(),
            m.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty().trim(),
            (m.getLong(MediaMetadata.METADATA_KEY_DURATION) / 1000).toInt(),
            s.packageName,
        )
    }.getOrNull()
}
