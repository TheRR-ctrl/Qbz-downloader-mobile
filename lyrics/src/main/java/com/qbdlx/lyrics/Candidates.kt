package com.qbdlx.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class Candidate(val lyrics: Lyrics, val label: String, val preview: String)

/** Every lyric that can be found for a song, so the user can pick instead of trusting the automatic choice. */
object Candidates {
    private fun preview(l: Lyrics): String {
        val lines = if (l.synced != null) Lrc.parse(l.synced).map { it.text } else l.plain.orEmpty().lines()
        return lines.map { it.trim() }.filter { it.isNotEmpty() }.take(4).joinToString(" / ")
    }

    fun search(t: TrackInfo): Pair<List<Candidate>, String> {
        val out = ArrayList<Candidate>(); var why = ""
        val dur = (t.durationMs / 1000).toInt()
        val cleaned = TrackText.clean(t.title)
        val first = t.artist.split(',', '&', ';', '/').first().trim()
        val seen = HashSet<Int>()
        for (q in listOf("${t.artist} $cleaned", "$first $cleaned", cleaned).filter { it.isNotBlank() }.distinct()) {
            val body = try { LrcLib.rawSearch(q) } catch (e: java.io.IOException) { why = "LRCLIB no contestó."; null } ?: continue
            for (i in 0 until JSONArray(body).length()) {
                val o = JSONArray(body).optJSONObject(i) ?: continue
                val l = LrcLib.parseOne(o) ?: continue
                if (!LrcLib.sameSong(o.optString("trackName"), o.optString("artistName"), t.title, t.artist)) continue
                if (!seen.add(o.optInt("id", i * 31 + q.hashCode()))) continue
                val d = o.optDouble("duration", 0.0).toInt()
                val label = "LRCLIB · ${if (l.synced != null) "sincronizada" else "sin sincronizar"} · %d:%02d".format(d / 60, d % 60) +
                    (if (dur > 0) " (${if (d >= dur) "+" else "−"}${kotlin.math.abs(d - dur)} s)" else "") + o.optString("albumName", "").takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                out += Candidate(Lyrics(l.synced, l.plain, "LRCLIB"), label, preview(l))
            }
            if (out.size >= 8) break
        }
        val yt = runCatching { YtMusic.find(t.title, t.artist, dur) }.getOrNull()
        if (yt != null) out += Candidate(yt, "YouTube Music · ${if (yt.synced != null) "sincronizada" else "sin sincronizar"}", preview(yt))
        else if (YtMusic.lastFail.isNotEmpty()) why += " YouTube Music: ${YtMusic.lastFail}."
        return out.sortedByDescending { it.lyrics.synced != null } to why.trim()
    }
}
