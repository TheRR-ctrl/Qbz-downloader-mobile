package com.qbdlx.lyrics

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Spanish translation of the lines that are in another language. Each line is sent on its own to Google's free translate
 * endpoint (no key), which also says the language it detected: lines already in Spanish are left alone, so a Spanish song with
 * an English phrase stays as it is and only really foreign lines get a translation. The result is kept per lyric on the phone.
 */
object Translator {
    private fun cacheFile(context: Context, key: String) = File(File(context.applicationContext.filesDir, "tr").apply { mkdirs() }, key.hashCode().toUInt().toString(16) + ".json")

    /** Translation (by line index) of the lines not in Spanish. Empty when nothing needs it or there is no connection. */
    @Volatile var lastError = ""

    fun translate(context: Context, songKey: String, lines: List<String>, cancelled: () -> Boolean): Map<Int, String> {
        lastError = ""
        val key = songKey + "|" + lines.joinToString("\n").hashCode()
        val f = cacheFile(context, key)
        runCatching { if (f.exists()) JSONObject(f.readText()).let { o -> if (o.optString("k") == key) return o.optJSONObject("t")?.let { t -> t.keys().asSequence().associate { it.toInt() to t.getString(it) } }.orEmpty() } }
        val out = HashMap<Int, String>(); var failed = 0
        for ((i, raw) in lines.withIndex()) {
            if (cancelled()) return out
            val text = raw.trim()
            if (text.count { it.isLetter() } < 3) continue
            val r = runCatching { one(text) }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrNull()
            if (r == null) { if (++failed >= 3) return out; continue } // offline: try again another time, nothing saved
            val (lang, tr) = r
            if (!lang.startsWith("es") && tr.isNotBlank() && norm(tr) != norm(text)) out[i] = tr
        }
        runCatching { f.writeText(JSONObject().put("k", key).put("t", JSONObject().also { t -> out.forEach { (i, s) -> t.put(i.toString(), s) } }).toString()) }
        return out
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** (detected language, Spanish text) */
    private fun one(text: String): Pair<String, String> {
        val c = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=es&dt=t&q=" + URLEncoder.encode(text, "UTF-8")).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
        if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
        try {
            val a = org.json.JSONArray(c.inputStream.bufferedReader().readText())
            val segs = a.getJSONArray(0)
            val tr = (0 until segs.length()).joinToString("") { segs.getJSONArray(it).optString(0) }
            return a.optString(2) to tr.trim()
        } finally { c.disconnect() }
    }
}
