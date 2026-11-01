package com.qbdlx.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

class LyricLine(val timeMs: Long, val text: String)

/** [synced] lines carry the time they are sung at; plain lyrics have no times. */
class Lyrics(val lines: List<LyricLine>, val synced: Boolean)

object LyricsParser {
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    /** Reads LRC text ("[01:23.45] words"); a line may carry several time stamps. Lines come out sorted by time. */
    fun parseLrc(text: String): List<LyricLine> = text.lineSequence().flatMap { raw ->
        val stamps = stamp.findAll(raw).toList()
        if (stamps.isEmpty()) return@flatMap emptySequence()
        val words = raw.substring(stamps.last().range.last + 1).trim()
        stamps.asSequence().map { m ->
            val frac = m.groupValues[3]
            val ms = if (frac.isEmpty()) 0L else (frac.padEnd(3, '0').take(3)).toLong()
            LyricLine(m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toLong() * 1000 + ms, words)
        }
    }.sortedBy { it.timeMs }.toList()

    fun parsePlain(text: String): List<LyricLine> = text.lines().map { LyricLine(0, it.trim()) }

    /** Index of the line being sung at [posMs], or -1 before the first one. */
    fun currentIndex(lines: List<LyricLine>, posMs: Long): Int = lines.indexOfLast { it.timeMs <= posMs }

    fun fromJson(o: JSONObject): Lyrics? {
        val synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        if (synced != null) parseLrc(synced).takeIf { it.isNotEmpty() }?.let { return Lyrics(it, true) }
        val plain = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" } ?: return null
        return Lyrics(parsePlain(plain), false)
    }
}

/** Lyrics from LRCLIB (https://lrclib.net), a free and open lyrics database. */
object LyricsClient {
    private const val BASE = "https://lrclib.net/api"

    /** Exact match first, then a keyword search (streaming titles like "Song (feat. X) (2017 Remix)" rarely match exactly). Null when nothing is found; throws on network errors. */
    fun fetch(artist: String, title: String, album: String, durationSec: Int): Lyrics? {
        val exact = "$BASE/get?artist_name=${enc(artist)}&track_name=${enc(title)}&album_name=${enc(album)}&duration=$durationSec"
        // The exact endpoint is often overloaded (HTTP 503): a failure there just moves on to the search.
        try { get(exact)?.let { LyricsParser.fromJson(JSONObject(it))?.let { l -> return l } } } catch (e: java.io.IOException) { /* fall through */ }
        val cleaned = cleanTitle(title)
        for (q in listOf("$artist $cleaned", cleaned, "$artist ${cleanTitle(title, dropFeat = true)}").distinct()) {
            val body = get("$BASE/search?q=${enc(q)}") ?: continue
            pickBest(JSONArray(body), durationSec, title, artist)?.let { return it }
        }
        return null
    }

    private val tags = Regex("""\s*[(\[](?:[^)\]]*\b(?:remaster\w*|hi-?res|lossless|mono|stereo|deluxe|explicit|bonus|live|version|remix|\d{4})\b[^)\]]*)[)\]]""", RegexOption.IGNORE_CASE)
    private val feat = Regex("""\s*[(\[]\s*(?:feat|ft|with)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)

    /** Drops "(Remastered 2011)" / "(2017 Remix)" style suffixes, and with [dropFeat] "(feat. X)" too. */
    fun cleanTitle(title: String, dropFeat: Boolean = false): String {
        val t = tags.replace(title, "").let { if (dropFeat) feat.replace(it, "") else it }.trim()
        return t.ifBlank { title }
    }

    /** Among search results, prefers synced ones whose duration is within 10 s of the song's, nearest first. */
    fun pickBest(results: JSONArray, durationSec: Int, title: String = "", artist: String = ""): Lyrics? {
        val items = (0 until results.length()).mapNotNull { results.optJSONObject(it) }.filter { LyricsParser.fromJson(it) != null }
            .filter { title.isBlank() || sameSong(it.optString("trackName"), it.optString("artistName"), title, artist) }
        fun gap(o: JSONObject) = if (durationSec > 0) abs(o.optDouble("duration", 0.0) - durationSec) else 0.0
        val best = items.filter { gap(it) <= 10 }
            .minByOrNull { (if (it.optString("syncedLyrics", "").let { s -> s.isBlank() || s == "null" }) 1000 else 0) + gap(it) } ?: return null
        return LyricsParser.fromJson(best)
    }


    private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").filter { it.isLetterOrDigit() }

    /** True when a search result is really this song: same title (ignoring "(feat. X)" / "(Remix)" style tags) and a shared artist. Stops a same-length song with a similar name from being shown. */
    fun sameSong(resultTitle: String, resultArtist: String, title: String, artist: String): Boolean {
        fun t(x: String) = norm(cleanTitle(x))
        val a = t(resultTitle); val b = t(title)
        val titleOk = a.isNotEmpty() && b.isNotEmpty() && (a == b || a.contains(b) || b.contains(a))
        if (artist.isBlank() || resultArtist.isBlank()) return titleOk
        fun names(x: String) = x.split(',', '&', ';', '/').flatMap { it.split(Regex("(?i)\\b(?:feat|ft|with|and|y)\\b\\.?")) }.map(::norm).filter { it.isNotEmpty() }
        val ra = names(resultArtist); val rb = names(artist)
        val artistOk = ra.any { x -> rb.any { y -> x == y || x.contains(y) || y.contains(x) } }
        return titleOk && artistOk
    }

    private fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000; conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "QBDLX-Mobile")
            if (conn.responseCode == 404) null
            else if (conn.responseCode !in 200..299) throw java.io.IOException("LRCLIB ${conn.responseCode}")
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
