package com.qbdlx.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** What LRCLIB (https://lrclib.net) has for a song: synced LRC text and/or plain text. */
class Lyrics(val synced: String?, val plain: String?)

object LrcLib {
    private const val BASE = "https://lrclib.net/api"

    /** Exact match first (title, artist, album, duration), then a keyword search. Returns null when nothing is found; throws on network errors. */
    fun find(title: String, artist: String, album: String, durationSec: Int): Lyrics? {
        val exact = "$BASE/get?track_name=${enc(title)}&artist_name=${enc(artist)}" +
            (if (album.isNotBlank()) "&album_name=${enc(album)}" else "") + (if (durationSec > 0) "&duration=$durationSec" else "")
        // The exact endpoint is often overloaded (HTTP 503): any failure of it just moves on to the search.
        try { get(exact)?.let { parseOne(JSONObject(it))?.let { l -> return l } } } catch (e: java.io.IOException) { /* fall through to search */ }
        val cleaned = TrackText.clean(title)
        for (q in listOf("$artist $cleaned", cleaned).distinct()) {
            val search = get("$BASE/search?q=${enc(q)}") ?: continue
            pickBest(JSONArray(search), durationSec, title, artist)?.let { return it }
        }
        return null
    }

    internal fun parseOne(o: JSONObject): Lyrics? {
        val synced = o.optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" }
        val plain = o.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }
        if (synced == null && plain == null && o.optBoolean("instrumental", false)) return Lyrics(null, "♪ Instrumental ♪")
        return if (synced == null && plain == null) null else Lyrics(synced, plain)
    }

    /** Among search results, prefers synced ones whose duration is within 10 s of the song's, nearest first. */
    internal fun pickBest(results: JSONArray, durationSec: Int, title: String = "", artist: String = ""): Lyrics? {
        val items = (0 until results.length()).mapNotNull { results.optJSONObject(it) }.filter { parseOne(it) != null }
            .filter { title.isBlank() || sameSong(it.optString("trackName"), it.optString("artistName"), title, artist) }
        if (items.isEmpty()) return null
        fun gap(o: JSONObject) = if (durationSec > 0) abs(o.optDouble("duration", 0.0) - durationSec) else 0.0
        val best = items.filter { gap(it) <= 10 }.minByOrNull { (if (it.optString("syncedLyrics", "").isBlank()) 1000 else 0) + gap(it) }
            ?: return null
        return parseOne(best)
    }


    private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").filter { it.isLetterOrDigit() }

    /** True when a search result is really this song: same title (ignoring "(feat. X)" / "(Remix)" style tags) and a shared artist. Stops a same-length song with a similar name from being shown. */
    fun sameSong(resultTitle: String, resultArtist: String, title: String, artist: String): Boolean {
        fun t(x: String) = norm(TrackText.clean(x))
        val a = t(resultTitle); val b = t(title)
        val titleOk = a.isNotEmpty() && b.isNotEmpty() && (a == b || a.contains(b) || b.contains(a))
        if (artist.isBlank() || resultArtist.isBlank()) return titleOk
        fun names(x: String) = x.split(',', '&', ';', '/').flatMap { it.split(Regex("(?i)\\b(?:feat|ft|with|and|y)\\b\\.?")) }.map(::norm).filter { it.isNotEmpty() }
        val ra = names(resultArtist); val rb = names(artist)
        val artistOk = ra.any { x -> rb.any { y -> x == y || x.contains(y) || y.contains(x) } }
        return titleOk && artistOk
    }

    /** Time before which the service asked us not to call again (from a 429 Retry-After). */
    @Volatile var retryAfterUntil = 0L

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "Q-Lyrics/0.8.0 (https://github.com/therr-ctrl/qbz-downloader-mobile)")
            if (c.responseCode == 404) return null
            if (c.responseCode == 429) {
                val wait = c.getHeaderField("Retry-After")?.trim()?.toLongOrNull() ?: 5L
                retryAfterUntil = System.currentTimeMillis() + wait.coerceIn(1, 60) * 1000
            }
            if (c.responseCode !in 200..299) throw java.io.IOException("LRCLIB ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

object TrackText {
    private val tags = Regex("""\s*[(\[](?:[^)\]]*\b(?:remaster\w*|hi-?res|lossless|mono|stereo|deluxe|explicit|bonus|live|version|\d{4})\b[^)\]]*)[)\]]""", RegexOption.IGNORE_CASE)

    /** Drops the "(Remastered 2011)" / "[Hi-Res]" style suffixes streaming apps add, for the fallback search. */
    fun clean(title: String) = tags.replace(title, "").trim().ifBlank { title }
}
