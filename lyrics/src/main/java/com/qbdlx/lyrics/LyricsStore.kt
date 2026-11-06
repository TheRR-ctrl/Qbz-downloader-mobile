package com.qbdlx.lyrics

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Lyrics already found, kept on the phone so the same song is never searched twice. Only songs with lyrics are stored. */
object LyricsStore {
    private var file: File? = null
    private var data = JSONObject()

    @Synchronized fun init(context: Context) {
        if (file != null) return
        file = File(context.filesDir, "lyrics-saved.json")
        data = runCatching { JSONObject(file!!.readText()) }.getOrDefault(JSONObject())
    }

    @Synchronized fun get(key: String): Lyrics? {
        val o = data.optJSONObject(key) ?: return null
        val l = LrcLib.parseOne(o.let { JSONObject().put("syncedLyrics", it.optString("s")).put("plainLyrics", it.optString("p")) }) ?: return null
        return o.optString("src").takeIf { it.isNotBlank() }?.let { Lyrics(l.synced, l.plain, it) } ?: l
    }

    @Synchronized fun has(key: String) = data.has(key)

    /** The last writes (who wrote what), to trace a lyric that goes back to an old text. Shown under the result of "Resincronizar". */
    private val writes = ArrayDeque<String>()
    fun recentWrites(): String = synchronized(this) { writes.joinToString("\n") }

    @Synchronized fun put(key: String, l: Lyrics, flush: Boolean = true) {
        val who = Throwable().stackTrace.drop(1).firstOrNull { !it.className.contains("LyricsStore") }?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" } ?: "?"
        val first = (l.plain ?: "").lineSequence().firstOrNull { it.isNotBlank() }?.take(24) ?: ""
        writes.addLast("${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())} $who · ${l.source ?: "?"} · «$first»")
        while (writes.size > 8) writes.removeFirst()
        data.put(key, JSONObject().put("s", l.synced ?: "").put("p", l.plain ?: "").put("src", l.source ?: ""))
        if (flush) flush()
    }

    @Synchronized fun flush() { runCatching { file?.let { f -> val tmp = File(f.parentFile, f.name + ".tmp"); tmp.writeText(data.toString()); if (!tmp.renameTo(f)) { f.writeText(data.toString()); tmp.delete() } } } }

    // Songs searched without result, with the time: not searched again for two days (a new SEARCH_VERSION clears them all).
    private const val SEARCH_VERSION = 2
    private var missFile: File? = null
    private var miss = JSONObject()
    @Synchronized private fun missing(): JSONObject {
        if (missFile == null) { missFile = File(file?.parentFile, "lyrics-missing.json"); miss = runCatching { JSONObject(missFile!!.readText()) }.getOrDefault(JSONObject())
            // Songs marked "without lyrics" by an older, weaker search get another chance once.
            if (miss.optInt("__v", 0) < SEARCH_VERSION) miss = JSONObject().put("__v", SEARCH_VERSION) }
        return miss
    }
    @Synchronized fun missedRecently(key: String) = System.currentTimeMillis() - missing().optLong(key, 0L) < 2L * 24 * 3600 * 1000
    @Synchronized fun markMissing(key: String) { missing().put(key, System.currentTimeMillis()) }
    /** The song may be searched again right away (the user asked for it). */
    @Synchronized fun unmarkMissing(key: String) { if (missing().has(key)) { missing().remove(key); flushMissing() } }
    @Synchronized fun flushMissing() { runCatching { missFile?.writeText(miss.toString()) } }

    @Synchronized fun remove(key: String) {
        data.remove(key)
        runCatching { file?.writeText(data.toString()) }
    }

    @Synchronized fun count() = data.length()
}
