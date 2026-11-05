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
    /** Lyrics without times are timed by Whisper on their own the first time they play (once per song). */
    @Volatile var autoWhisper = true
    private val triedAuto = HashSet<String>()
    @Volatile var lastError = ""

    /** What the audio timing is doing, for the screen: null when idle. */
    val timing = MutableStateFlow<String?>(null)
    private var timingJob: Job? = null
    private var app: Context? = null

    /** Times the current unsynced lyrics from the song's audio file on the phone; the result is saved like any synced lyric. */
    fun timeFromAudio() {
        val ctx = app ?: return
        val t = _state.value.track ?: return
        val lines = _state.value.lines.map { it.text }
        if (_state.value.status != Status.PLAIN || timingJob?.isActive == true) return
        timingJob = scope.launch {
            timing.value = "Buscando el archivo de la canción…"
            val result = withContext(Dispatchers.IO) {
                val uri = AudioTiming.findOrBorrow(ctx, t.title, t.artist, t.durationMs)
                    ?: return@withContext "No encuentro esta canción como archivo en el teléfono (o falta el permiso de audio). Prueba con «Estimar» o «Marcar mientras suena»."
                withContext(Dispatchers.Main) { timing.value = "Analizando el audio…" }
                val env = AudioTiming.envelope(ctx, uri, cancelled = { !isActive })
                    ?: return@withContext "No se pudo leer el audio de ese archivo."
                val timed = AudioTiming.align(env, lines)
                withContext(Dispatchers.Main) { if (_state.value.track?.key == t.key) saveTimed(timed) }
                null
            }
            timing.value = result
        }
    }

    /** Whisper: listens to the song's audio file, matches the words it hears to the lyric and times every line. Works on synced or unsynced lyrics. */
    @Volatile private var ytWhy = ""

    fun timeFromWhisper() {
        val ctx = app ?: return
        val t = _state.value.track ?: return
        val lines = _state.value.lines.map { it.text }
        if ((_state.value.status != Status.PLAIN && _state.value.status != Status.SYNCED) || timingJob?.isActive == true) return
        timingJob = scope.launch {
            timing.value = "Buscando el archivo de la canción…"
            val result = withContext(Dispatchers.Default) {
                if (!Whisper.available(ctx)) return@withContext "Whisper no está en esta versión de la app."
                val uri = AudioTiming.findOrBorrow(ctx, t.title, t.artist, t.durationMs)
                    ?: return@withContext "No encuentro esta canción como archivo en el teléfono (o falta el permiso de audio)."
                val asr = Whisper.listen(ctx, t.storeKey, uri, { !isActive }) { m -> timing.value = m } ?: return@withContext if (!isActive) "Cancelado." else "Whisper no pudo con esta canción."
                var timed = WhisperAlign.align(lines, asr.words)
                ytWhy = ""
                // The lyric and the voice use different alphabets: YouTube Music often writes the same song in the alphabet Whisper heard.
                // Its lines only serve to find the times; they are moved onto the user's lines by position.
                if ((timed == null || WhisperAlign.approximate) && _state.value.source != YtMusic.SOURCE) {
                    withContext(Dispatchers.Main) { timing.value = "Buscando la letra en otro alfabeto…" }
                    val other = runCatching { YtMusic.find(t.title, t.artist, (t.durationMs / 1000).toInt()) }.getOrNull()
                    val otherLines = (other?.plain ?: other?.synced?.let { Lrc.parse(it).map { l -> l.text }.joinToString("\n") })?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
                    if (other == null) ytWhy = YtMusic.lastFail
                    val better = otherLines?.let { WhisperAlign.align(it, asr.words, 0.12) }?.takeIf { !WhisperAlign.approximate }
                    if (other != null && better == null) ytWhy = "encontró la letra, pero solo coincidieron ${WhisperAlign.lastMatched.first} de ${WhisperAlign.lastMatched.second} palabras con lo que oyó Whisper"
                    // The lyric did not match the voice but YouTube's is the same alphabet as what was heard: it replaces the user's one.
                    if (better == null && other != null && otherLines != null && otherLines.size >= 2) {
                        val replaced = other.synced?.let { Lrc.parse(it) }?.takeIf { it.size >= 2 }
                            ?: WhisperAlign.align(otherLines, asr.words, 0.12)
                        if (replaced != null) {
                            val approx = other.synced == null && WhisperAlign.approximate
                            withContext(Dispatchers.Main) { if (_state.value.track?.key == t.key) saveTimed(replaced, YtMusic.SOURCE) }
                            return@withContext "Listo: se usó la letra de YouTube Music" + (if (other.synced != null) " con sus propios tiempos." else if (approx) ", repartida por cantidad de palabras (aproximado)." else ", sincronizada con la voz.")
                        }
                    }
                    if (better != null && lines.isNotEmpty()) timed = lines.mapIndexed { i, text -> better[(i.toLong() * (better.size - 1) / (lines.size - 1).coerceAtLeast(1)).toInt()].let { LyricLine(it.timeMs, text, it.endMs) } }
                    else if (timed != null) WhisperAlign.markApproximate()
                }
                // Nothing matches and no other lyric helps: what Whisper heard becomes the lyric, with its real times.
                if (timed == null && asr.words.size >= 8) {
                    val heard = WhisperAlign.linesFromHeard(asr.words)
                    if (heard.size >= 2) { withContext(Dispatchers.Main) { if (_state.value.track?.key == t.key) saveTimed(heard, "Whisper (lo que se oyó)") }; return@withContext "Listo: no coincidía con la letra, así que se escribió lo que Whisper oyó. Puede tener errores." }
                }
                val done = timed ?: return@withContext "Whisper entendió muy poco de la voz (${asr.words.size} palabras, idioma ${asr.lang}). Prueba «Detectar con el audio»."
                withContext(Dispatchers.Main) { if (_state.value.track?.key == t.key) saveTimed(done); if (!WhisperAlign.approximate) SyncCheck.markVerified(t.storeKey) }
                null
            }
            timing.value = result ?: if (WhisperAlign.approximate) "Listo, aproximado: la letra y la voz están en alfabetos distintos, así que se repartió por cantidad de palabras." else "Listo: letra sincronizada con la voz."
            if (result == null && WhisperAlign.approximate && ytWhy.isNotEmpty()) timing.value = "Listo, aproximado. Antes probé YouTube Music y $ytWhy."
        }
    }

    /** For a song with no lyrics: Whisper writes what it hears; if it understands too little, the lyric comes from YouTube Music instead. */
    fun writeWithWhisper() {
        val ctx = app ?: return
        val t = _state.value.track ?: return
        if (timingJob?.isActive == true) return
        timingJob = scope.launch {
            timing.value = "Buscando el archivo de la canción…"
            val result = withContext(Dispatchers.Default) {
                fun fromYoutube(): String? {
                    val yt = runCatching { YtMusic.find(t.title, t.artist, (t.durationMs / 1000).toInt()) }.getOrNull() ?: return null
                    LyricsStore.put(t.storeKey, yt); cache[t.key] = yt
                    scope.launch(Dispatchers.Main) { if (_state.value.track?.key == t.key) { _saved.value = LyricsStore.count(); publish(t, yt, _state.value.playing) } }
                    return "Whisper no entendió la voz; la letra salió de YouTube Music."
                }
                val uri = AudioTiming.findOrBorrow(ctx, t.title, t.artist, t.durationMs) ?: return@withContext fromYoutube() ?: "No encuentro esta canción como archivo en el teléfono (o falta el permiso de audio)."
                val asr = Whisper.listen(ctx, t.storeKey, uri, { !isActive }) { m -> timing.value = m } ?: return@withContext if (!isActive) "Cancelado." else (fromYoutube() ?: "Whisper no pudo con esta canción.")
                val heard = WhisperAlign.linesFromHeard(asr.words)
                if (asr.words.size < 8 || heard.size < 2) return@withContext fromYoutube() ?: "Whisper entendió muy poco (${asr.words.size} palabras) y YouTube Music no tiene esta letra."
                withContext(Dispatchers.Main) { if (_state.value.track?.key == t.key) saveTimed(heard, "Whisper (lo que se oyó)") }
                "Listo: letra escrita con lo que Whisper oyó. Puede tener errores."
            }
            timing.value = result
        }
    }

    /** True while a song is being timed by hand: the background check waits. */
    fun busy() = timingJob?.isActive == true

    /** Saved lyrics were changed behind the screen's back: the next refresh reads them again. */
    fun lyricsChanged() { cache.clear(); loadedKey = null }

    fun cancelTiming() { Whisper.cancel(); timingJob?.cancel(); timing.value = "Cancelado." }

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
        this.app = app
        LyricsStore.init(app); SyncCheck.init(app)
        // The sync settings must be read here and not only when the screen opens: the car can start the service without the app ever being opened.
        app.getSharedPreferences("lyrics", Context.MODE_PRIVATE).let { p ->
            offsetMs = p.getLong("offset", 0); carLeadMs = p.getLong("car_lead", 1000); karaoke = p.getBoolean("karaoke", true); autoWhisper = p.getBoolean("auto_whisper", true)
        }
        scope.launch { var tick = 0
            while (isActive) { runCatching { refresh(app) }; if (++tick % 150 == 0) runCatching { SyncCheck.autoTick(app) }; delay(400) } }
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
            if (found == null) found = withContext(Dispatchers.IO) { runCatching { YtMusic.find(track.title, track.artist, (track.durationMs / 1000).toInt()) }.getOrNull() }
            cache[track.key] = found
            found?.let { LyricsStore.put(track.storeKey, it); _saved.value = LyricsStore.count(); SyncCheck.enqueue(track.storeKey, it, PrefetchTrack(track.title, track.artist, track.album, (track.durationMs / 1000).toInt())) }
            publish(track, found, playing)
        }
    }

    private fun publish(track: TrackInfo, l: Lyrics?, playing: Boolean) {
        _state.value = when {
            l?.synced != null -> LyricsState(Status.SYNCED, track, Lrc.parse(l.synced), playing, l.source)
            l?.plain != null -> LyricsState(Status.PLAIN, track, Lrc.plain(l.plain), playing, l.source)
            else -> LyricsState(Status.NOT_FOUND, track, playing = playing)
        }
        val ctx = app
        if (autoWhisper && _state.value.status == Status.PLAIN && ctx != null && playing && triedAuto.add(track.key) && Whisper.available(ctx)) timeFromWhisper()
    }

    /** The song's own clock, without the user's sync offset: what a tap-made timing is saved against. */
    fun rawPositionMs() = (positionMs() - offsetMs).coerceAtLeast(0)

    /** Saves lines timed by the user as this song's synced lyrics (kept on the phone like any found lyrics) and shows them at once. */
    fun saveTimed(lines: List<LyricLine>, source: String? = _state.value.source) {
        val t = _state.value.track ?: return
        val l = Lyrics(Lrc.toLrc(lines), lines.joinToString("\n") { it.text }, source)
        LyricsStore.put(t.storeKey, l); cache[t.key] = l; _saved.value = LyricsStore.count()
        publish(t, l, _state.value.playing)
    }

    /** Car buttons (play/pause, next, previous) are passed on to the music app that is playing. */
    fun controls() = controller?.transportControls
}
