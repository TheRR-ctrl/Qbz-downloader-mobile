package com.qbdlx.lyrics

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class PrefetchTrack(val title: String, val artist: String, val album: String, val durationSec: Int)

class PrefetchState(val running: Boolean = false, val total: Int = 0, val done: Int = 0, val found: Int = 0, val missing: Int = 0, val error: String? = null, val skipped: Int = 0)

/**
 * Downloads the lyrics of a list of songs ahead of time, at a steady pace, so they are on the phone when the song plays.
 * Sources of the list: the phone's own music ([fromMediaStore]) and the list QBDLX Mobile sends ([parse]).
 * Songs already saved, or searched without result in the last two days, are skipped.
 */
object Prefetch {
    private val _state = MutableStateFlow(PrefetchState())
    val state: StateFlow<PrefetchState> = _state
    /** Most requests in flight at once. The real number adapts: it starts at [START], halves when the service complains and creeps back up while it answers. */
    const val PARALLEL = 50
    private const val START = 15
    /** Songs per block, and the minimum time between two requests (the service slows everything down by itself when it complains). */
    const val BLOCK = 200
    private const val GAP_MS = 200L
    private const val MIN_PARALLEL = 2
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Same song, same key, whatever the app: lower case letters and digits of the title and the first artist. */
    fun storeKey(title: String, artist: String): String {
        fun n(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val first = artist.split(',', '&', ';', '/').first().trim().removeSuffix(" feat").let { it.split(" feat", " ft.", " featuring").first() }
        return n(title) + "|" + n(first)
    }

    /** The list QBDLX Mobile shares: {"tracks":[{"t":title,"a":artist,"al":album,"d":seconds}]}. Null when it is not that. */
    fun parse(json: String): List<PrefetchTrack>? = runCatching {
        val a = JSONObject(json).getJSONArray("tracks")
        (0 until a.length()).map { a.getJSONObject(it) }
            .filter { it.optString("t").isNotBlank() }
            .map { PrefetchTrack(it.getString("t"), it.optString("a"), it.optString("al"), it.optInt("d")) }
    }.getOrNull()

    fun encode(tracks: List<PrefetchTrack>): String = JSONObject().put("tracks", JSONArray().also { arr ->
        tracks.forEach { arr.put(JSONObject().put("t", it.title).put("a", it.artist).put("al", it.album).put("d", it.durationSec)) }
    }).toString()

    /** The songs of the phone's music library (needs the audio permission). */
    fun fromMediaStore(context: Context): List<PrefetchTrack> {
        val out = ArrayList<PrefetchTrack>()
        val cols = arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
        context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, "${MediaStore.Audio.Media.IS_MUSIC}!=0", null, null)?.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(0).orEmpty(); val artist = c.getString(1).orEmpty()
                if (title.isBlank() || artist == "<unknown>") continue
                out += PrefetchTrack(title, artist, c.getString(2).orEmpty(), (c.getLong(3) / 1000).toInt())
            }
        }
        return out.distinctBy { storeKey(it.title, it.artist) }
    }

    /** Which run owns the pending-list file: an older run that ends must not delete the file of the one that replaced it. */
    @Volatile private var owner: Any? = null
    private fun pendingFile(c: Context) = java.io.File(c.applicationContext.filesDir, "prefetch_pending.json")

    /** Picks the download up again after Android closed the app (or the service restarted): what was already saved is skipped without any request. */
    fun resume(context: Context): Boolean {
        if (_state.value.running) return true
        val list = runCatching { parse(pendingFile(context).readText()) }.getOrNull()
        if (list.isNullOrEmpty()) return false
        start(context, list)
        return true
    }

    fun cancel() { job?.cancel(); _state.value = PrefetchState(running = false, total = _state.value.total, done = _state.value.done, found = _state.value.found, missing = _state.value.missing, skipped = _state.value.skipped) }

    /** Starts (or replaces) the download of [tracks] and the foreground service that keeps it alive. */
    fun start(context: Context, tracks: List<PrefetchTrack>) {
        val app = context.applicationContext
        LyricsStore.init(app); SyncCheck.init(app)
        job?.cancel()
        val list = tracks.distinctBy { storeKey(it.title, it.artist) }
        val mine = Any(); owner = mine
        // The list goes to a file so a download that Android cuts short (app closed, process killed) can continue by itself.
        runCatching { pendingFile(app).writeText(encode(list)) }
        _state.value = PrefetchState(running = true, total = list.size)
        PrefetchService.start(app)
        job = scope.launch {
            val done = java.util.concurrent.atomic.AtomicInteger(); val found = java.util.concurrent.atomic.AtomicInteger(); val missing = java.util.concurrent.atomic.AtomicInteger(); val skipped = java.util.concurrent.atomic.AtomicInteger()
            val failures = java.util.concurrent.atomic.AtomicInteger() // in a row, across all workers
            val pauseUntil = java.util.concurrent.atomic.AtomicLong(0)
            var error: String? = null
            var lastStart = 0L
            val limit = java.util.concurrent.atomic.AtomicInteger(START)
            val active = java.util.concurrent.atomic.AtomicInteger(0)
            val streak = java.util.concurrent.atomic.AtomicInteger(0) // answers in a row without trouble
            fun publish() { _state.value = PrefetchState(true, list.size, done.get(), found.get(), missing.get(), skipped = skipped.get()); PrefetchService.update(app, done.get(), list.size) }
            try {
                // In blocks of BLOCK songs: each block runs in parallel and is fully saved to disk before the next one starts.
                for (block in list.chunked(BLOCK)) {
                  if (error != null) break
                  coroutineScope {
                    for (t in block) {
                        if (failures.get() >= 30) { error = "sin conexión con el servicio de letras"; break }
                        // Already saved (or searched without result lately): counted at once, no waiting and no request.
                        val key0 = storeKey(t.title, t.artist)
                        if (LyricsStore.has(key0) || LyricsStore.missedRecently(key0)) {
                            skipped.incrementAndGet(); val n0 = done.incrementAndGet()
                            if (n0 % 50 == 0) publish()
                            continue
                        }
                        while (active.get() >= limit.get()) delay(50)
                        // Steady pace (~5 requests/s) and honor Retry-After from the service.
                        val gate = maxOf(LrcLib.retryAfterUntil - System.currentTimeMillis(), GAP_MS - (System.currentTimeMillis() - lastStart))
                        if (gate > 0) delay(gate)
                        lastStart = System.currentTimeMillis()
                        active.incrementAndGet()
                        launch {
                            try {
                                val key = storeKey(t.title, t.artist)
                                if (!LyricsStore.has(key) && !LyricsStore.missedRecently(key)) {
                                    var result: Lyrics? = null; var ok = false
                                    for (attempt in 1..5) {
                                        val wait = pauseUntil.get() - System.currentTimeMillis(); if (wait > 0) delay(wait)
                                        try { result = LrcLib.find(t.title, t.artist, t.album, t.durationSec); ok = true; break }
                                        catch (e: CancellationException) { throw e }
                                        catch (e: Exception) {
                                            streak.set(0); limit.updateAndGet { (it / 2).coerceAtLeast(MIN_PARALLEL) } // fewer at once
                                            pauseUntil.set(System.currentTimeMillis() + 3000L * attempt); delay(2000L * attempt) // everyone slows down
                                        }
                                    }
                                    if (!ok) failures.incrementAndGet()
                                    else {
                                        failures.set(0)
                                        if (streak.incrementAndGet() >= 20) { streak.set(0); limit.updateAndGet { (it + 2).coerceAtMost(PARALLEL) } }
                                        if (result != null) { LyricsStore.put(key, result, flush = false); SyncCheck.enqueue(key, result, t); found.incrementAndGet() } else { LyricsStore.markMissing(key); missing.incrementAndGet() }
                                    }
                                }
                                val n = done.incrementAndGet()
                                if (n % 25 == 0) { LyricsStore.flush(); LyricsStore.flushMissing() }
                                publish()
                            } finally { active.decrementAndGet() }
                        }
                    }
                  }
                  LyricsStore.flush(); LyricsStore.flushMissing()
                }
            } finally {
                withContext(NonCancellable) {
                    LyricsStore.flush(); LyricsStore.flushMissing()
                    _state.value = PrefetchState(false, list.size, done.get(), found.get(), missing.get(), error, skipped.get())
                    if (owner === mine) { runCatching { pendingFile(app).delete() }; PrefetchService.stop(app) }
                }
            }
        }
    }
}
