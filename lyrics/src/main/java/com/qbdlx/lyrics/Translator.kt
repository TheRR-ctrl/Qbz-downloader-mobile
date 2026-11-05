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
    @Volatile var lastError = ""

    /** Every line ever asked (text -> [detected language, Spanish text]), kept on the phone: a repeated chorus, a song heard again or a retry after a cut connection never asks Google twice. */
    private var memo: JSONObject? = null
    private var unsaved = 0
    private fun memoFile(context: Context) = File(context.applicationContext.filesDir, "tr-lines.json")
    @Synchronized private fun memo(context: Context): JSONObject = memo ?: (runCatching { JSONObject(memoFile(context).readText()) }.getOrNull() ?: JSONObject()).also { memo = it }
    @Synchronized private fun flush(context: Context) { runCatching { memoFile(context).writeText(memo(context).toString()) }; unsaved = 0 }

    /** Translation (by line index) of the lines not in Spanish. Empty when nothing needs it or there is no connection; what was already translated is kept, so a retry only asks for the rest. */
    fun translate(context: Context, songKey: String, lines: List<String>, cancelled: () -> Boolean): Map<Int, String> {
        lastError = ""
        val m = memo(context)
        val out = HashMap<Int, String>(); var failed = 0
        try {
            for ((i, raw) in lines.withIndex()) {
                if (cancelled()) return out
                val text = raw.trim()
                if (text.count { it.isLetter() } < 3) continue
                var e = synchronized(this) { m.optJSONArray(text) }
                if (e == null) {
                    val r = runCatching { one(text) }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrNull()
                    if (r == null) { if (++failed >= 3) return out; continue } // offline: nothing saved for this line, asked again next time
                    e = org.json.JSONArray().put(r.first).put(r.second)
                    synchronized(this) { m.put(text, e); if (++unsaved >= 10) flush(context) }
                }
                val lang = e.optString(0); val tr = e.optString(1)
                if (!lang.startsWith("es") && tr.isNotBlank() && norm(tr) != norm(text)) out[i] = tr
            }
        } finally { if (unsaved > 0) flush(context) }
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
