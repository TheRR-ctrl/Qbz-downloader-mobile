package com.qbdlx.lyrics

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Follows whatever music app is playing through Android's media sessions, which
 * needs notification access, looks its lyrics up and keeps the playback position. One instance for the whole process.
 */
object Tracker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(LyricsState())
    val state: StateFlow<LyricsState> = _state

    /** How many songs have saved lyrics (changes when one is saved or forgotten, so the screen refreshes). */
    private val _saved = MutableStateFlow(0)
    val saved: StateFlow<Int> = _saved

    /** Forgets the saved lyrics of the current song and searches for them again. */
    fun forgetAndSearch() {
        val t = _state.value.track ?: return
        LyricsStore.remove(t.storeKey); LyricsStore.remove(t.key); cache.remove(t.key); _saved.value = LyricsStore.count()
        loadedKey = null // the next refresh loads it again
    }

    @Volatile var offsetMs = 0L
    /** How early the car's list switches line (the car redraws a moment after the list changes), in ms. Adjustable in Ajustes. */
    @Volatile var carLeadMs = 1000L
    /** Fill the line word by word while it is sung. */
    @Volatile var karaoke = true
    @Volatile var lastError = ""

    /** The playing app's colour (ARGB, 0 when unknown): its notification's colour, else the cover's main colour. */
    val accent = MutableStateFlow(0)
    private var accentKey: String? = null
    /** The playing song's cover (scaled down) for the car's background and thumbnail; null when the app gives none. */
    @Volatile var cover: android.graphics.Bitmap? = null
    private var started = false
    private var controller: MediaController? = null
    private var loadedKey: String? = null
    private var lookup: Job? = null
    private var retryAt = 0L
    private val cache = object : LinkedHashMap<String, Lyrics?>(16, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Lyrics?>?) = size > 30
    }

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        LyricsStore.init(app)
        // The sync settings must be read here and not only when the screen opens: the car can start the service without the app ever being opened.
        app.getSharedPreferences("lyrics", Context.MODE_PRIVATE).let { p ->
            offsetMs = p.getLong("offset", 0); carLeadMs = p.getLong("car_lead", 1000); karaoke = p.getBoolean("karaoke", true)
        }
        scope.launch { while (isActive) { runCatching { refresh(app) }; delay(400) } }
    }

    /** The playback position now, in ms: the session's last report plus the time that has passed since, plus the user's offset. */
    fun positionMs(): Long {
        val s = controller?.playbackState ?: return 0
        val advance = if (s.state == PlaybackState.STATE_PLAYING) ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong() else 0
        return (s.position + advance + offsetMs).coerceAtLeast(0)
    }

    private fun refresh(context: Context) {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val sessions = try {
            manager.getActiveSessions(ComponentName(context, ListenerService::class.java))
        } catch (e: SecurityException) {
            controller = null
            if (_state.value.status != Status.NO_ACCESS) _state.value = LyricsState(Status.NO_ACCESS)
            return
        }
        val c = pick(sessions.filter { it.packageName != context.packageName })
        controller = c
        val meta = c?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        if (c == null || meta == null || title.isBlank()) {
            loadedKey = null; lookup?.cancel()
            if (_state.value.status != Status.NO_MEDIA) _state.value = LyricsState(Status.NO_MEDIA)
            return
        }
        val track = TrackInfo(
            title, (meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
            meta.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(), meta.getLong(MediaMetadata.METADATA_KEY_DURATION), c.packageName,
        )
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        if (accentKey != track.key || accent.value == 0) { accentKey = track.key; cover = findCover(meta); accent.value = findAccent(c, meta) }
        if (track.key != loadedKey && SystemClock.elapsedRealtime() >= retryAt) { loadedKey = track.key; load(track, playing) }
        else if (_state.value.playing != playing) _state.value = LyricsState(_state.value.status, _state.value.track, _state.value.lines, playing)
    }

    private fun findCover(meta: MediaMetadata): android.graphics.Bitmap? = runCatching {
        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) ?: return@runCatching null
        val f = 320f / maxOf(art.width, art.height)
        if (f >= 1f) art else android.graphics.Bitmap.createScaledBitmap(art, (art.width * f).toInt().coerceAtLeast(1), (art.height * f).toInt().coerceAtLeast(1), true)
    }.getOrNull()

    private fun findAccent(c: MediaController, meta: MediaMetadata): Int {
        val fromNotification = runCatching {
            ListenerService.instance?.activeNotifications.orEmpty()
                .filter { it.packageName == c.packageName }
                .firstOrNull { it.notification.extras.containsKey("android.mediaSession") && it.notification.color != 0 }?.notification?.color
        }.getOrNull()
        if (fromNotification != null) return fromNotification
        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: return 0
        val small = android.graphics.Bitmap.createScaledBitmap(art, 24, 24, true)
        return Palette.dominant(IntArray(24 * 24).also { small.getPixels(it, 0, 24, 0, 0, 24, 24) })
    }

    /** The app that is playing, else the first one with a song. */
    private fun pick(list: List<MediaController>): MediaController? =
        list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull { it.metadata != null }

    private fun load(track: TrackInfo, playing: Boolean) {
        lookup?.cancel()
        if (cache.containsKey(track.key)) { publish(track, cache[track.key], playing); return }
        (LyricsStore.get(track.storeKey) ?: LyricsStore.get(track.key))?.let { cache[track.key] = it; publish(track, it, playing); return } // saved before: no search
        _state.value = LyricsState(Status.LOADING, track, playing = playing)
        lookup = scope.launch {
            // LRCLIB is often briefly overloaded: keep showing "loading" through a few quick retries before giving up
            var found: Lyrics? = null
            var failure: Exception? = null
            for (attempt in 0 until 4) {
                if (attempt > 0) delay(1500L * attempt)
                try {
                    found = withContext(Dispatchers.IO) { LrcLib.find(track.title, track.artist, track.album, (track.durationMs / 1000).toInt()) }
                    failure = null
                    break
                } catch (e: CancellationException) { throw e } catch (e: Exception) { failure = e }
            }
            if (failure != null) {
                lastError = failure.message ?: failure.javaClass.simpleName
                _state.value = LyricsState(Status.ERROR, track, playing = playing)
                loadedKey = null; retryAt = SystemClock.elapsedRealtime() + 8000 // keep trying in the background
                return@launch
            }
            cache[track.key] = found
            found?.let { LyricsStore.put(track.storeKey, it); _saved.value = LyricsStore.count() }
            publish(track, found, playing)
        }
    }

    private fun publish(track: TrackInfo, l: Lyrics?, playing: Boolean) {
        _state.value = when {
            l?.synced != null -> LyricsState(Status.SYNCED, track, Lrc.parse(l.synced), playing)
            l?.plain != null -> LyricsState(Status.PLAIN, track, Lrc.plain(l.plain), playing)
            else -> LyricsState(Status.NOT_FOUND, track, playing = playing)
        }
    }

    /** Car buttons (play/pause, next, previous) are passed on to the music app that is playing. */
    fun controls() = controller?.transportControls
}
