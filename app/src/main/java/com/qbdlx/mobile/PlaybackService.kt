package com.qbdlx.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.MediaDescription
import android.net.Uri
import android.os.Bundle
import android.service.media.MediaBrowserService
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** What the playback notification needs from the player; implemented by [MainViewModel] and set in [QbdlxApp.playback]. */
interface PlaybackControls {
    val nowPlaying: Pair<Album, Track>?
    val isPlaying: Boolean
    val positionMs: Long
    val durationMs: Long
    fun togglePlay()
    fun play()
    fun pause()
    fun next()
    fun previous()
    fun seekTo(ms: Long)
    /** The user swiped the notification away or pressed stop: end playback. */
    fun stopPlayback()
    /** Plays the song at [index] of the recently-played list (Android Auto's list). */
    fun playFromHistory(index: Int)
    /** Songs that are fully downloaded (they play without using data), for Android Auto's "Downloaded" list. */
    fun downloadedSongs(): List<Pair<Album, Track>>
    /** Plays [items] from [index] for Android Auto: songs that are not downloaded stream as MP3 to spare mobile data. */
    fun playFromCar(items: List<Pair<Album, Track>>, index: Int)
    /** Lyrics of the playing song (loaded on demand by [ensureLyrics]), for Android Auto. */
    val lyrics: Lyrics?
    val shuffleOn: Boolean
    /** 0 = off, 1 = repeat all, 2 = repeat one. */
    val repeatMode: Int
    fun toggleShuffle()
    fun cycleRepeat()
    /** Moves the playing position by [ms] (negative goes back). */
    fun seekBy(ms: Long)
    /** The user's lyric sync adjustment: added to the position when picking the current line. */
    val lyricsOffsetMs: Long
    fun ensureLyrics()
}

/**
 * Shows the "now playing" notification with the cover and previous / play-pause / next buttons, and registers a media
 * session so the lock screen, Bluetooth and headset buttons control the player. While music plays it is a foreground
 * service, which also keeps the process (and so the music) alive when the app is left.
 */
class PlaybackService : MediaBrowserService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var session: MediaSession
    private var coverJob: Job? = null
    private var coverUrl: String? = null
    private var cover: Bitmap? = null
    private var foreground = false
    private var carShown = ""

    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, label("Reproducción", "Playback"), NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        })
        session = MediaSession(this, "QBDLX").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { QbdlxApp.playback?.play() }
                override fun onPause() { QbdlxApp.playback?.pause() }
                override fun onSkipToNext() { QbdlxApp.playback?.next() }
                override fun onSkipToPrevious() { QbdlxApp.playback?.previous() }
                override fun onSeekTo(pos: Long) { QbdlxApp.playback?.seekTo(pos) }
                override fun onStop() { QbdlxApp.playback?.stopPlayback() }
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    val c = QbdlxApp.playback
                    val index = mediaId?.toIntOrNull()
                    val downloaded = if (mediaId?.startsWith("d") == true) mediaId.drop(1).toIntOrNull() else null
                    if (c != null && downloaded != null) c.downloadedSongs().let { l -> if (downloaded in l.indices) c.playFromCar(l, downloaded) }
                    else if (c != null && index != null) c.playFromHistory(index)
                    else if (mediaId?.startsWith("l") == true) Unit // a lyric row: nothing to play
                    else session.setPlaybackState(
                        PlaybackState.Builder().setState(PlaybackState.STATE_ERROR, 0, 0f)
                            .setErrorMessage(label("Abre QBDLX en el teléfono para empezar", "Open QBDLX on the phone to start")).build(),
                    )
                }
            })
            isActive = true
        }
        sessionToken = session.sessionToken
        // Android Auto's list shows the lyrics: refresh it when the line being sung changes.
        scope.launch {
            while (true) {
                val c = QbdlxApp.playback
                val np = c?.nowPlaying
                var sig = ""
                if (c != null && np != null) {
                    if (c.isPlaying) c.ensureLyrics()
                    sig = CarLyrics.rows(np.second.displayTitle, np.first.artist, c.lyrics, c.positionMs + c.lyricsOffsetMs + CAR_LEAD_MS).joinToString("|") { it.id + it.title }
                }
                if (sig != carShown) { carShown = sig; notifyChildrenChanged(LYRICS_ID) }
                delay(300)
            }
        }
    }

    // ---- Android Auto: the browse tree is the list of recently played songs --------------------------------------------

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) = BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<android.media.browse.MediaBrowser.MediaItem>>) {
        val c = QbdlxApp.playback
        fun item(id: String, title: String, flag: Int, subtitle: String = "", cover: String? = null) = android.media.browse.MediaBrowser.MediaItem(
            MediaDescription.Builder().setMediaId(id).setTitle(title).apply { if (subtitle.isNotBlank()) setSubtitle(subtitle); cover?.let { setIconUri(Uri.parse(it)) } }.build(), flag,
        )
        val browsable = android.media.browse.MediaBrowser.MediaItem.FLAG_BROWSABLE
        val playable = android.media.browse.MediaBrowser.MediaItem.FLAG_PLAYABLE
        when (parentId) {
            ROOT_ID -> result.sendResult(mutableListOf(
                item(LYRICS_ID, label("Letra", "Lyrics"), browsable, label("La canción que suena, en vivo", "The song playing, live")),
                item(DOWNLOADED_ID, label("Descargadas", "Downloaded"), browsable, label("Sin gastar datos", "Uses no data")),
                item(HISTORY_ID, label("Reproducidas recientemente", "Recently played"), browsable, label("Las que no están descargadas van en MP3", "Songs not downloaded stream as MP3")),
            ))
            LYRICS_ID -> {
                val np = c?.nowPlaying
                val rows = if (c != null && np != null) CarLyrics.rows(np.second.displayTitle, np.first.artist, c.lyrics, c.positionMs + c.lyricsOffsetMs + CAR_LEAD_MS) else emptyList()
                result.sendResult(
                    if (rows.isEmpty()) mutableListOf(item("lmsg", if (np == null) label("Pon una canción para ver su letra", "Play a song to see its lyrics") else label("Esta canción no tiene letra sincronizada", "This song has no synced lyrics"), playable))
                    else rows.map { r -> item(r.id, r.title, playable, r.subtitle) }.toMutableList(),
                )
            }
            DOWNLOADED_ID -> result.sendResult(
                (c?.downloadedSongs().orEmpty()).take(200).mapIndexed { i, (album, track) -> item("d$i", track.displayTitle, playable, album.artist, album.coverUrl) }.toMutableList(),
            )
            HISTORY_ID -> {
                val history = QueueStore.decodeList(getSharedPreferences("qbdlx", 0).getString("history", null))
                result.sendResult(history.mapIndexed { i, (album, track) -> item(i.toString(), track.displayTitle, playable, album.artist, album.coverUrl) }.toMutableList())
            }
            else -> result.sendResult(mutableListOf())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val c = QbdlxApp.playback
        when (intent?.action) {
            ACTION_TOGGLE -> c?.togglePlay()
            ACTION_NEXT -> c?.next()
            ACTION_PREVIOUS -> c?.previous()
            ACTION_SHUFFLE -> c?.toggleShuffle()
            ACTION_REPEAT -> c?.cycleRepeat()
            ACTION_BACK -> c?.seekBy(-10_000)
            ACTION_FORWARD -> c?.seekBy(10_000)
            ACTION_STOP -> c?.stopPlayback()
        }
        update()
        return START_NOT_STICKY
    }

    /** Rebuilds the notification and the media session from the player's current state. */
    fun update() {
        val c = QbdlxApp.playback
        val playing = c?.nowPlaying
        if (c == null || playing == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            NowPlayingWidget.render(this, null, false, null)
            stopSelf()
            return
        }
        val (album, track) = playing
        loadCover(album.coverUrl)

        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.displayTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, album.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album.title)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, c.durationMs.takeIf { it > 0 } ?: (track.durationSec * 1000L))
                .apply { cover?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build(),
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP,
                )
                .setState(if (c.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, c.positionMs, if (c.isPlaying) 1f else 0f)
                .build(),
        )

        NowPlayingWidget.render(this, playing, c.isPlaying, cover?.let { Bitmap.createScaledBitmap(it, 160, 160, true) }, cover?.let { if (it.width > 480) Bitmap.createScaledBitmap(it, 480, 480, true) else it })
        val notification = build(album, track, c.isPlaying)
        if (c.isPlaying) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            foreground = true
        } else {
            // Paused: the notification stays but can be swiped away (which ends playback), and the service may stop.
            if (foreground) { stopForeground(STOP_FOREGROUND_DETACH); foreground = false }
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun build(album: Album, track: Track, playing: Boolean): Notification {
        val shuffleOn = QbdlxApp.playback?.shuffleOn == true
        val repeat = QbdlxApp.playback?.repeatMode ?: 0
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(track.displayTitle)
            .setContentText(album.artist)
            .setSubText(album.title)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(action(ACTION_STOP))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setShowWhen(false)
            .addAction(action(if (shuffleOn) R.drawable.ic_widget_shuffle else R.drawable.ic_widget_shuffle_off, label(if (shuffleOn) "Aleatorio: sí" else "Aleatorio: no", if (shuffleOn) "Shuffle: on" else "Shuffle: off"), ACTION_SHUFFLE))
            .addAction(action(android.R.drawable.ic_media_previous, label("Anterior", "Previous"), ACTION_PREVIOUS))
            .addAction(
                if (playing) action(android.R.drawable.ic_media_pause, label("Pausa", "Pause"), ACTION_TOGGLE)
                else action(android.R.drawable.ic_media_play, label("Reproducir", "Play"), ACTION_TOGGLE),
            )
            .addAction(action(android.R.drawable.ic_media_next, label("Siguiente", "Next"), ACTION_NEXT))
            .addAction(action(when (repeat) { 2 -> R.drawable.ic_widget_repeat_one; 1 -> R.drawable.ic_widget_repeat; else -> R.drawable.ic_widget_repeat_off }, label("Repetir", "Repeat"), ACTION_REPEAT))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(1, 2, 3))
        cover?.let { builder.setLargeIcon(it) }
        return builder.build()
    }

    /** Loads the cover for the notification; when it arrives the notification is rebuilt with it. */
    private fun loadCover(url: String?) {
        if (url == coverUrl) return
        coverUrl = url
        cover = null
        coverJob?.cancel()
        if (url == null) return
        coverJob = scope.launch {
            val bitmap = runCatching {
                val result = imageLoader.execute(ImageRequest.Builder(this@PlaybackService).data(url).size(512).allowHardware(false).build())
                ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            }.getOrNull()
            if (bitmap != null && url == coverUrl) { cover = bitmap; update() }
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(act: String): PendingIntent = PendingIntent.getService(
        this, act.hashCode(), Intent(this, PlaybackService::class.java).setAction(act), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(icon: Int, title: String, act: String): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(this, icon), title, action(act)).build()

    private fun label(es: String, en: String) = if (Locale.getDefault().language == "es") es else en

    override fun onDestroy() {
        if (instance === this) instance = null
        scope.cancel()
        session.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 4711
        private const val ROOT_ID = "root"
        private const val HISTORY_ID = "history"
        private const val LYRICS_ID = "lyrics"
        private const val DOWNLOADED_ID = "downloaded"
        /** The car redraws a moment after the list changes, so the line is switched slightly early to look in time. */
        private const val CAR_LEAD_MS = 350L
        const val ACTION_TOGGLE = "com.qbdlx.mobile.PLAY_PAUSE"
        const val ACTION_NEXT = "com.qbdlx.mobile.NEXT"
        const val ACTION_PREVIOUS = "com.qbdlx.mobile.PREVIOUS"
        const val ACTION_STOP = "com.qbdlx.mobile.STOP"
        const val ACTION_SHUFFLE = "com.qbdlx.mobile.SHUFFLE"
        const val ACTION_REPEAT = "com.qbdlx.mobile.REPEAT"
        const val ACTION_BACK = "com.qbdlx.mobile.BACK_10"
        const val ACTION_FORWARD = "com.qbdlx.mobile.FORWARD_10"

        @Volatile private var instance: PlaybackService? = null

        /** A button press (widget, notification) that the service handles. */
        fun actionIntent(context: Context, act: String): PendingIntent = PendingIntent.getService(
            context, act.hashCode(), Intent(context, PlaybackService::class.java).setAction(act), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Call whenever the track, the play/pause state or the position (after a seek) changes. */
        fun refresh(context: Context) {
            val c = QbdlxApp.playback ?: return
            val running = instance
            when {
                running != null -> running.update()
                c.nowPlaying != null && c.isPlaying ->
                    // Android only lets a foreground service start while the app is visible or music is already playing.
                    runCatching { ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java)) }
            }
        }
    }
}
