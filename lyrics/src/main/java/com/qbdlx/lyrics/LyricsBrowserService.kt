package com.qbdlx.lyrics

import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser.MediaItem
import android.media.session.MediaSession
import android.os.Bundle
import android.service.media.MediaBrowserService
import kotlinx.coroutines.*

/**
 * What Android Auto lists for this app: the song and its lyrics around the line being sung. The list is refreshed
 * whenever the line changes. The media session is deliberately never made active, so the car's buttons and audio stay with
 * the music app that is playing; the buttons are only forwarded to it.
 */
class LyricsBrowserService : MediaBrowserService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var session: MediaSession
    private var shown: List<String> = emptyList()

    override fun onCreate() {
        super.onCreate()
        Tracker.start(this)
        session = MediaSession(this, "QLyrics").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { Tracker.controls()?.play() }
                override fun onPause() { Tracker.controls()?.pause() }
                override fun onSkipToNext() { Tracker.controls()?.skipToNext() }
                override fun onSkipToPrevious() { Tracker.controls()?.skipToPrevious() }
            })
        }
        sessionToken = session.sessionToken
        scope.launch {
            while (isActive) {
                val state = Tracker.state.value
                val rows = CarView.rows(state, Tracker.positionMs() + LEAD_MS)
                val signature = rows.map { it.id + it.title } + (Tracker.cover?.hashCode() ?: 0).toString()
                if (signature != shown) {
                    shown = signature
                    session.setMetadata(
                        MediaMetadata.Builder().putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, Tracker.cover ?: accentBitmap())
                            .putString(MediaMetadata.METADATA_KEY_TITLE, rows.firstOrNull { it.current }?.title ?: state.track?.title ?: "")
                            .putString(MediaMetadata.METADATA_KEY_ARTIST, state.track?.artist ?: "").build(),
                    )
                    notifyChildrenChanged(ROOT_ID)
                }
                delay(250)
            }
        }
    }

    override fun onDestroy() { scope.cancel(); session.release(); super.onDestroy() }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) = BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        if (parentId != ROOT_ID) { result.sendResult(mutableListOf()); return }
        val rows = CarView.rows(Tracker.state.value, Tracker.positionMs() + LEAD_MS)
        result.sendResult(rows.map { r ->
            MediaItem(
                MediaDescription.Builder().setMediaId(r.id).setTitle(r.title)
                    .apply { if (r.subtitle.isNotBlank()) setSubtitle(r.subtitle); if (r.cover) setIconBitmap(Tracker.cover ?: accentBitmap()) }.build(),
                MediaItem.FLAG_PLAYABLE,
            )
        }.toMutableList())
    }

    private var accentArt: android.graphics.Bitmap? = null
    private var accentArtFor = 0

    /** A square in the colour of the playing app's notification: the car takes its screen tint and the song row's icon from it. */
    private fun accentBitmap(): android.graphics.Bitmap {
        val color = Tracker.accent.value.takeIf { it != 0 } ?: 0xFF455A64.toInt()
        if (accentArt == null || accentArtFor != color) {
            accentArt = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }
            accentArtFor = color
        }
        return accentArt!!
    }

    private companion object {
        const val ROOT_ID = "root"
        /** The car redraws a moment after the list changes, so the line is switched slightly early to look in time. */
        const val LEAD_MS = 350L
    }
}
