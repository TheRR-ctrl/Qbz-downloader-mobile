package com.qbdlx.lyrics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/** What one song's check said. [kind]: "ok", "off" (all lines early or late by about [medianMs]), "drift" (the error changes along the song) or "unk" (could not be judged). */
class SyncVerdict(val kind: String, val medianMs: Long, val within: Int, val total: Int, val fix: List<LyricLine>?)

class SyncProgress(
    val running: Boolean = false, val total: Int = 0, val done: Int = 0,
    val ok: Int = 0, val off: Int = 0, val unknown: Int = 0, val fixed: Int = 0, val current: String = "", val message: String? = null,
)

/**
 * Checks that the lyrics saved on the phone really are in time with the song: Whisper listens to the song's own file and its times
 * are compared line by line with the saved ones. A lyric that is early or late by a steady amount is shifted; one that drifts
 * takes Whisper's times. Runs on every saved synced lyric when asked, and on each new lyric once the phone is on Wi-Fi.
 */
object SyncCheck {
    const val TOLERANCE_MS = 2500L
    const val MIN_SHARE = 0.6

    private val _state = MutableStateFlow(SyncProgress())
    val state: StateFlow<SyncProgress> = _state
    /** Lyrics waiting for their check (new ones, saved while the feature is on). */
    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending
    /** Lyrics found out of sync and not fixed yet. */
    private val _toFix = MutableStateFlow(0)
    val toFix: StateFlow<Int> = _toFix

    @Volatile var autoOnWifi = true
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var results = JSONObject()
    private var queue = JSONArray()
    private var dir: File? = null
    private var lastAuto = 0L

    @Synchronized fun init(context: Context) {
        if (dir != null) return
        dir = context.applicationContext.filesDir
        results = runCatching { JSONObject(File(dir, "sync-check.json").readText()) }.getOrDefault(JSONObject())
        queue = runCatching { JSONArray(File(dir, "sync-pending.json").readText()) }.getOrDefault(JSONArray())
        autoOnWifi = context.getSharedPreferences("lyrics", Context.MODE_PRIVATE).getBoolean("sync_auto", true)
        _pending.value = queue.length(); _toFix.value = countFixable()
    }

    fun setAuto(context: Context, on: Boolean) { autoOnWifi = on; context.getSharedPreferences("lyrics", Context.MODE_PRIVATE).edit().putBoolean("sync_auto", on).apply() }

    private fun countFixable(): Int = results.keys().asSequence().count { results.optJSONObject(it)?.let { o -> o.optString("v") in listOf("off", "drift") && o.has("fix") } == true }

    @Synchronized private fun save() {
        val d = dir ?: return
        runCatching { File(d, "sync-check.json").writeText(results.toString()); File(d, "sync-pending.json").writeText(queue.toString()) }
        _pending.value = queue.length(); _toFix.value = countFixable()
    }

    /** A lyric just saved: its check runs when the phone is on Wi-Fi. Only synced ones can be checked. */
    @Synchronized fun enqueue(key: String, l: Lyrics) {
        if (dir == null || l.synced == null || !autoOnWifi || results.has(key)) return
        if (l.source?.startsWith("Whisper") == true) return // made from Whisper's own times
        for (i in 0 until queue.length()) if (queue.optString(i) == key) return
        queue.put(key); save()
    }

    /** Pure judgement: [stored] are the saved lines, [ref] Whisper's times for the same non-blank lines in the same order. */
    fun judge(stored: List<LyricLine>, ref: List<LyricLine>): SyncVerdict {
        val n = minOf(stored.size, ref.size)
        if (n < 4) return SyncVerdict("unk", 0, 0, n, null)
        val deltas = (0 until n).map { stored[it].timeMs - ref[it].timeMs }
        val median = deltas.sorted()[n / 2]
        val within = deltas.count { abs(it) <= TOLERANCE_MS }
        if (abs(median) <= 1500 && within >= n * MIN_SHARE) return SyncVerdict("ok", median, within, n, null)
        val shiftedWithin = deltas.count { abs(it - median) <= TOLERANCE_MS }
        if (shiftedWithin >= n * MIN_SHARE) {
            val fix = stored.map { LyricLine((it.timeMs - median).coerceAtLeast(0), it.text, it.endMs?.let { e -> (e - median).coerceAtLeast(0) }) }
            return SyncVerdict("off", median, shiftedWithin, n, fix)
        }
        return SyncVerdict("drift", median, within, n, ref.take(n).mapIndexed { i, r -> LyricLine(r.timeMs, stored[i].text, r.endMs) })
    }

    /** Listens to one song and judges its saved lyric. Slow (minutes). Null when there is no audio file or it could not be read. */
    private fun verdictFor(context: Context, t: PrefetchTrack, lyrics: Lyrics, cancelled: () -> Boolean): SyncVerdict? {
        val synced = lyrics.synced ?: return null
        val uri = AudioTiming.find(context, t.title, t.artist, t.durationSec * 1000L) ?: downloaderCopy(context, t) ?: return null
        val lines = Lrc.parse(synced).filter { it.text.isNotBlank() }
        if (lines.size < 4) return null
        val pcm = Whisper.pcm16k(context, uri, cancelled = cancelled) ?: return null
        val asr = Whisper.transcribe(context, pcm) ?: return null
        val ref = WhisperAlign.align(lines.map { it.text }, asr.words)
        if (ref == null || WhisperAlign.approximate) return SyncVerdict("unk", 0, 0, lines.size, null) // another alphabet: nothing to compare
        return judge(lines, ref)
    }

    /** Not on the phone: QBDLX Mobile lends a temporary MP3 from Qobuz (opened once, by the decoder; no file stays on disk). Null when it is not installed; if it is logged out or lacks the song, the decoder simply fails and the verdict is "can't judge". */
    private fun downloaderCopy(context: Context, t: PrefetchTrack): android.net.Uri? {
        if (context.packageManager.resolveContentProvider("com.qbdlx.mobile.lyricsaudio", 0) == null) return null
        return android.net.Uri.Builder().scheme("content").authority("com.qbdlx.mobile.lyricsaudio").path("track")
            .appendQueryParameter("title", t.title).appendQueryParameter("artist", t.artist).appendQueryParameter("dur", t.durationSec.toString()).build()
    }

    private fun onWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun cancel() { job?.cancel(); Whisper.cancel() }

    /**
     * Checks [keys] (null: every saved synced lyric that has an audio file on the phone and was not checked yet, plus the ones to re-check).
     * With [fix] the ones found out of sync are corrected right away.
     */
    fun scan(context: Context, keys: Set<String>? = null, fix: Boolean = false, auto: Boolean = false) {
        val app = context.applicationContext
        init(app); LyricsStore.init(app)
        if (job?.isActive == true || !Whisper.available(app)) return
        job = scope.launch {
            _state.value = SyncProgress(running = true, message = "Buscando canciones con letra…")
            val library = runCatching { Prefetch.fromMediaStore(app) }.getOrDefault(emptyList()).associateBy { Prefetch.storeKey(it.title, it.artist) }
            val todo = (keys ?: library.keys.filter { k -> LyricsStore.get(k)?.synced != null && !results.has(k) }.toSet())
                .filter { library.containsKey(it) && LyricsStore.get(it)?.synced != null }
            var ok = 0; var off = 0; var unk = 0; var fixed = 0; var done = 0
            fun publish(cur: String = "", msg: String? = null) { _state.value = SyncProgress(true, todo.size, done, ok, off, unk, fixed, cur, msg) }
            try {
                for (k in todo) {
                    if (!isActive) break
                    while (Tracker.busy()) delay(3000) // one Whisper at a time: the song being timed by hand goes first
                    if (auto && !onWifi(app)) { publish(msg = "Esperando el wifi…"); break }
                    val t = library[k]!!; val l = LyricsStore.get(k)!!
                    publish("${t.title} — ${t.artist}")
                    val v = withContext(Dispatchers.Default) { runCatching { verdictFor(app, t, l) { !isActive } }.getOrNull() }
                    if (!isActive) break
                    val o = JSONObject().put("t", System.currentTimeMillis())
                    if (v == null) { unk++; o.put("v", "unk") }
                    else {
                        o.put("v", v.kind).put("d", v.medianMs).put("w", v.within).put("n", v.total)
                        when (v.kind) { "ok" -> ok++; "unk" -> unk++; else -> {
                            off++
                            if (v.fix != null) { if (fix) { apply(k, l, v.fix); fixed++; o.put("v", "fixed") } else o.put("fix", Lrc.toLrc(v.fix)) }
                        } }
                    }
                    synchronized(this@SyncCheck) { results.put(k, o); removeFromQueue(k); save() }
                    done++; publish()
                }
            } finally {
                withContext(NonCancellable) {
                    // songs of the queue with no audio file here cannot be checked: dropped, not kept waiting for ever
                    synchronized(this@SyncCheck) { for (k in (0 until queue.length()).map { queue.optString(it) }) if (!library.containsKey(k)) { results.put(k, JSONObject().put("v", "unk").put("t", System.currentTimeMillis())); removeFromQueue(k) }; save() }
                    _state.value = SyncProgress(false, todo.size, done, ok, off, unk, fixed, "", if (todo.isEmpty()) "No hay letras sincronizadas nuevas que comprobar." else null)
                }
            }
        }
    }

    private fun removeFromQueue(k: String) {
        val keep = JSONArray(); for (i in 0 until queue.length()) if (queue.optString(i) != k) keep.put(queue.optString(i)); queue = keep
    }

    private fun apply(key: String, old: Lyrics, lines: List<LyricLine>) {
        LyricsStore.put(key, Lyrics(Lrc.toLrc(lines), old.plain, old.source))
        Tracker.lyricsChanged()
    }

    /** Applies the corrections found by a scan without listening again. */
    fun fixAll(context: Context): Int {
        init(context); LyricsStore.init(context)
        var n = 0
        synchronized(this) {
            for (k in results.keys().asSequence().toList()) {
                val o = results.optJSONObject(k) ?: continue
                val fixLrc = o.optString("fix").takeIf { it.isNotBlank() } ?: continue
                val old = LyricsStore.get(k) ?: continue
                apply(k, old, Lrc.parse(fixLrc)); o.remove("fix"); o.put("v", "fixed"); n++
            }
            save()
        }
        return n
    }

    /** Called from the app's clock: starts the check of new lyrics when the phone is on Wi-Fi and nothing else is using Whisper. */
    fun autoTick(context: Context) {
        if (!autoOnWifi || dir == null || queue.length() == 0 || job?.isActive == true || Tracker.busy()) return
        if (System.currentTimeMillis() - lastAuto < 60_000) return
        lastAuto = System.currentTimeMillis()
        if (!onWifi(context)) return
        val keys = (0 until queue.length()).map { queue.optString(it) }.toSet()
        scan(context, keys, fix = true, auto = true)
    }
}
