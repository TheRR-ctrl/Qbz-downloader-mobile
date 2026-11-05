package com.qbdlx.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lyrics from YouTube Music, for songs LRCLIB does not have. It is not an official API (the same private calls the YouTube Music
 * website and app make), so any step may stop working: everything here fails quietly and the caller keeps going without lyrics.
 */
object YtMusic {
    const val SOURCE = "YouTube Music"
    private const val BASE = "https://music.youtube.com/youtubei/v1/"
    private const val KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

    /** The song's lyrics (timed when YouTube Music has them, else plain), or null when it cannot be found or is not the same song. */
    fun find(title: String, artist: String, durationSec: Int): Lyrics? {
        val q = "${TrackText.clean(title)} ${artist.split(',', '&', ';', '/').first().trim()}".trim()
        val hit = pickSong(JSONObject(post("search", JSONObject().put("query", q), web = true)), title, artist, durationSec) ?: return null
        val id = lyricsBrowseId(JSONObject(post("next", JSONObject().put("videoId", hit), web = true))) ?: return null
        val timed = runCatching { parseTimed(JSONObject(post("browse", JSONObject().put("browseId", id), web = false))) }.getOrNull()
        if (timed != null) return Lyrics(Lrc.toLrc(timed), timed.joinToString("\n") { it.text }, SOURCE)
        val plain = parsePlain(JSONObject(post("browse", JSONObject().put("browseId", id), web = true))) ?: return null
        return Lyrics(null, plain, SOURCE)
    }

    /** Collects every value stored under [key], at any depth. */
    private fun walk(o: Any?, key: String, out: MutableList<Any>) {
        when (o) {
            is JSONObject -> o.keys().forEach { k -> if (k == key) out.add(o.get(k)) else walk(o.opt(k), key, out) }
            is JSONArray -> for (i in 0 until o.length()) walk(o.opt(i), key, out)
        }
    }

    private fun firstString(o: Any?, key: String): String? { val r = ArrayList<Any>(); walk(o, key, r); return r.firstOrNull { it is String && it.isNotBlank() } as String? }

    /** Video id of the first search result that is this song: same title and artist, and a duration within 10 s when both are known. */
    internal fun pickSong(response: JSONObject, title: String, artist: String, durationSec: Int): String? {
        val items = ArrayList<Any>(); walk(response, "musicResponsiveListItemRenderer", items)
        for (it in items) {
            val item = it as? JSONObject ?: continue
            val vid = firstString(item, "videoId") ?: continue
            val cols = item.optJSONArray("flexColumns") ?: continue
            val texts = (0 until cols.length()).map { c ->
                val runs = cols.optJSONObject(c)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")?.optJSONArray("runs")
                (0 until (runs?.length() ?: 0)).map { r -> runs!!.getJSONObject(r).optString("text") }
            }
            val name = texts.getOrNull(0)?.joinToString("").orEmpty()
            val details = texts.getOrNull(1).orEmpty().map { it.trim() }.filter { it.isNotEmpty() && it != "•" }
            val dur = details.lastOrNull()?.let { clock(it) }
            // the artist is one of the runs ("Song • Daddy Yankee • 2:49"); the kind and the duration never match a name
            if (!LrcLib.sameSong(name, details.joinToString(","), title, artist)) continue
            if (durationSec > 0 && dur != null && kotlin.math.abs(dur - durationSec) > 10) continue
            return vid
        }
        return null
    }

    private fun clock(s: String): Int? {
        val p = s.split(':').map { it.toIntOrNull() ?: return null }
        return if (p.size in 2..3) p.fold(0) { a, b -> a * 60 + b } else null
    }

    /** The lyrics page id (starts with MPLY) of a song's "next" answer, or null when the song has no lyrics tab. */
    internal fun lyricsBrowseId(next: JSONObject): String? {
        val ids = ArrayList<Any>(); walk(next, "browseId", ids)
        return ids.firstOrNull { it is String && it.startsWith("MPLY") } as String?
    }

    /** Timed lines from the Android app's answer, or null when it has none (or only untimed ones). */
    internal fun parseTimed(browse: JSONObject): List<LyricLine>? {
        val data = ArrayList<Any>(); walk(browse, "timedLyricsData", data)
        val arr = data.firstOrNull() as? JSONArray ?: return null
        val out = ArrayList<LyricLine>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: return null
            val start = o.optJSONObject("cueRange")?.optString("startTimeMilliseconds")?.toLongOrNull() ?: return null
            val text = o.optString("lyricLine").trim()
            if (text.isNotEmpty()) out.add(LyricLine(start, text))
        }
        return out.takeIf { it.size >= 2 }
    }

    /** Untimed lyrics from the web answer. */
    internal fun parsePlain(browse: JSONObject): String? {
        val shelf = ArrayList<Any>(); walk(browse, "musicDescriptionShelfRenderer", shelf)
        val desc = (shelf.firstOrNull() as? JSONObject)?.optJSONObject("description")?.optJSONArray("runs") ?: return null
        val text = (0 until desc.length()).joinToString("") { desc.getJSONObject(it).optString("text") }.trim()
        return text.takeIf { it.length > 20 }
    }

    private fun post(endpoint: String, body: JSONObject, web: Boolean): String {
        val client = if (web) JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", "1." + java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()) + ".01.00")
        else JSONObject().put("clientName", "ANDROID_MUSIC").put("clientVersion", "7.21.50")
        body.put("context", JSONObject().put("client", client).put("user", JSONObject()))
        val c = URL("${BASE}$endpoint?alt=json&key=$KEY").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15_000; c.readTimeout = 20_000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Origin", "https://music.youtube.com")
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) throw java.io.IOException("YouTube Music ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }
}
