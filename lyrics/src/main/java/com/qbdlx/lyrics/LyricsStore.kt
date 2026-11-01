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
        return LrcLib.parseOne(o.let { JSONObject().put("syncedLyrics", it.optString("s")).put("plainLyrics", it.optString("p")) })
    }

    @Synchronized fun has(key: String) = data.has(key)

    @Synchronized fun put(key: String, l: Lyrics, flush: Boolean = true) {
        data.put(key, JSONObject().put("s", l.synced ?: "").put("p", l.plain ?: ""))
        if (flush) flush()
    }

    @Synchronized fun flush() { runCatching { file?.writeText(data.toString()) } }

    // Songs searched without result, with the time: not searched again for a week.
    private var missFile: File? = null
    private var miss = JSONObject()
    @Synchronized private fun missing(): JSONObject {
        if (missFile == null) { missFile = File(file?.parentFile, "lyrics-missing.json"); miss = runCatching { JSONObject(missFile!!.readText()) }.getOrDefault(JSONObject()) }
        return miss
    }
    @Synchronized fun missedRecently(key: String) = System.currentTimeMillis() - missing().optLong(key, 0L) < 7L * 24 * 3600 * 1000
    @Synchronized fun markMissing(key: String) { missing().put(key, System.currentTimeMillis()) }
    @Synchronized fun flushMissing() { runCatching { missFile?.writeText(miss.toString()) } }

    @Synchronized fun remove(key: String) {
        data.remove(key)
        runCatching { file?.writeText(data.toString()) }
    }

    @Synchronized fun count() = data.length()
}
