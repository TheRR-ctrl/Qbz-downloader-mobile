package com.qbdlx.mobile

import org.json.JSONObject
import java.text.Normalizer
import kotlin.math.abs

/** One entry of a list made by QBDLX Scanner. */
data class ImportedSong(val title: String, val artist: String, val album: String, val durationSec: Int, val file: String)

/** How sure a search result is the wanted song. */
enum class MatchLevel { Sure, Check, Missing }

/** A wanted song with what Qobuz offered for it, best first. [chosen] is the candidate to download (null = none). */
class ImportItem(val song: ImportedSong, val candidates: List<Pair<Album, Track>>, val level: MatchLevel, val chosen: Int?, val also: Set<Int> = emptySet(), val searchFailed: Boolean = false) {
    /** Every candidate to take: more than one when a title has several versions worth having. */
    val picks: List<Pair<Album, Track>> get() = (listOfNotNull(chosen) + also).distinct().mapNotNull { candidates.getOrNull(it) }
}

/** Reads the scanner's list and decides which Qobuz results are the same songs. Pure logic, no network. */
object SongImport {
    /** Throws [IllegalArgumentException] when the text is not a scanner list. */
    fun parse(text: String): List<ImportedSong> {
        val json = try { JSONObject(text) } catch (e: Exception) { throw IllegalArgumentException("not JSON") }
        require(json.optString("app") == "qbdlx-scanner") { "not a QBDLX Scanner list" }
        val songs = json.optJSONArray("songs") ?: return emptyList()
        return (0 until songs.length()).mapNotNull { i ->
            val s = songs.optJSONObject(i) ?: return@mapNotNull null
            val title = s.optString("title").trim()
            if (title.isBlank()) null
            else ImportedSong(title.unescape(), s.optString("artist").trim().unescape(), s.optString("album").trim().unescape(), s.optInt("durationSec"), s.optString("file"))
        }.distinctBy { normalize(it.title) + "|" + normalize(it.artist) }
    }

    /** File names scanned from tags sometimes carry escaped quotes ("Don\\'t"). */
    private fun String.unescape() = replace("\\'", "'").replace("\\\"", "\"")

    /** What to ask Qobuz: artist and title when the artist is known. */
    fun query(s: ImportedSong) = listOf(s.artist, s.title).filter { it.isNotBlank() }.joinToString(" ")

    private val noise = "official|video|videoclip|music video|oficial|letra|lyrics?|lyric video|audio|visualizer|hd|4k|traduc\\w*|subtitulad\\w*|sub español|slowed|reverb|tiktok|tikt\\w*|full|extended|version|ver"
    private val noiseWord = Regex("\\b($noise)\\b", RegexOption.IGNORE_CASE)

    /** Strips what file and video names add: site names, "(Official Video)", ids, "VEVO", underscores. */
    fun clean(raw: String): String {
        var s = raw.replace(Regex("\\s+\\S{1,4}(…|\\.\\.\\.)\\s*$"), " ").replace('_', ' ').replace(Regex("^\\s*y2mate\\.com\\s*-?\\s*", RegexOption.IGNORE_CASE), "").replace("…", " ").replace("...", " ")
        s = s.replace(Regex("[\\(\\[][^)\\]]*[)\\]]")) { if (noiseWord.containsMatchIn(it.value)) " " else it.value }
        s = s.replace(Regex("\\s+[A-Za-z0-9-]{10,11}$")) { m -> val t = m.value.trim(); if (t.any { it.isDigit() } && t.any { it.isUpperCase() }) " " else m.value }
        s = s.replace(Regex("(?i)(bc)?vevo$|\\s*-\\s*topic$"), " ")
        s = s.replace(Regex("\\s*\\|.*$"), " ").replace(noiseWord, " ")
        s = s.replace(Regex("(?i)slowedreverb|slowed ?\\+ ?reverb|nightcore|sped ?up"), " ")
        return s.replace(Regex("\\s+"), " ").trim(' ', '-', '—', '|')
    }

    /** Other ways to read a song nobody found: cleaned up, and "Artist - Title" taken out of the title. */
    fun variants(song: ImportedSong): List<ImportedSong> {
        val title = clean(song.title); val artist = clean(song.artist)
        val out = LinkedHashSet<Pair<String, String>>()
        val parts = title.split(Regex("\\s+[-–—]\\s+"), 2)
        if (parts.size == 2) { out += parts[0] to parts[1]; out += parts[1] to parts[0] }
        out += artist to title
        out += "" to title
        // "Letra — Yuridia | En su lugar": the real title hides after the bar, in either field.
        for (raw in listOf(song.title, song.artist)) {
            val seg = raw.split('|').drop(1).map { clean(it) }.filter { it.isNotBlank() }
            for (t in seg) { out += artist.substringBefore('|').trim() to t; out += "" to t }
        }
        // Long video names: the front or the back of the title is often the song.
        val words = title.split(' ').filter { it.isNotBlank() }
        if (words.size >= 3) {
            for (n in 2..minOf(4, words.size - 1)) { out += artist to words.take(n).joinToString(" "); out += words.take(n).joinToString(" ") to "" }
            for (n in 1..minOf(5, words.size - 2)) out += "" to words.drop(n).joinToString(" ")
        }
        // Last resort: the artist alone, so their songs can be browsed by hand.
        if (artist.isNotBlank()) out += "" to artist
        return out.map { (a, t) -> if (t.isBlank()) "" to a else a to t }
            .filter { it.second.isNotBlank() && !(it.first == song.artist && it.second == song.title) }.distinct()
            .map { ImportedSong(it.second, it.first, song.album, song.durationSec, song.file) }
    }

    /** Orders the results by how well they fit and says how sure the best one is. */
    fun rank(song: ImportedSong, results: List<Pair<Album, Track>>): ImportItem {
        val scored = results.map { it to score(song, it.first, it.second) }.sortedByDescending { it.second.total }
        val top = scored.take(5)
        val best = top.firstOrNull()?.second
        val level = when {
            best == null -> MatchLevel.Missing
            best.title >= 0.85 && (best.artist >= 0.6 || (song.artist.isBlank() && best.title >= 0.95)) && best.durationOk -> MatchLevel.Sure
            best.title >= 0.5 -> MatchLevel.Check
            else -> MatchLevel.Missing
        }
        // Even for "missing" the closest results are kept, so they can be picked by hand.
        val candidates = top.map { it.first }
        return ImportItem(song, candidates, level, if (level == MatchLevel.Sure) 0 else null)
    }

    class Score(val title: Double, val artist: Double, val durationOk: Boolean) {
        val total get() = title * 2 + artist + (if (durationOk) 0.5 else 0.0)
    }

    fun score(song: ImportedSong, album: Album, track: Track): Score {
        val wantTitle = normalize(song.title)
        val title = maxOf(similarity(wantTitle, normalize(track.displayTitle)), similarity(wantTitle, normalize(track.title)))
        val artist = if (song.artist.isBlank()) 0.0 else maxOf(similarity(normalize(song.artist), normalize(track.artist)), similarity(normalize(song.artist), normalize(album.artist)))
        // Live, remix and other versions run at different lengths: a few seconds of slack, none when either length is unknown.
        val durationOk = song.durationSec <= 0 || track.durationSec <= 0 || abs(song.durationSec - track.durationSec) <= 4
        return Score(title, artist, durationOk)
    }

    /** Lower case, no accents, no "(Remastered 2011)"-style brackets, no punctuation. */
    fun normalize(s: String): String {
        val plain = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
        return plain.replace(Regex("[\\(\\[][^)\\]]*[)\\]]"), " ").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    /** At most one substituted, added or missing letter. */
    private fun near(a: String, b: String): Boolean {
        if (abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when { a.length > b.length -> i++; a.length < b.length -> j++; else -> { i++; j++ } }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }

    /** 1.0 for equal words in any order, less for partly shared words. */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val wa = a.split(' ').toSet(); val wb = b.split(' ').toSet()
        // Words one typo apart count as shared ("chaggy" ~ "shaggy").
        val shared = wa.count { w -> w in wb || (w.length >= 4 && wb.any { it.length >= 4 && near(w, it) }) }
        // Containment, so "Clocks" still fits "Clocks Live" a little, but equal sets fit best.
        return shared.toDouble() / maxOf(wa.size, wb.size).coerceAtLeast(1) * 0.5 + shared.toDouble() / minOf(wa.size, wb.size) * 0.5
    }
}
