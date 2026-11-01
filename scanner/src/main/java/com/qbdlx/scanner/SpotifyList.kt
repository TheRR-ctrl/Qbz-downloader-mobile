package com.qbdlx.scanner

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A playlist or album read from a Spotify link. */
data class SpotifyList(val name: String, val songs: List<Song>)

/**
 * Reads a public Spotify playlist or album from its link, without logging in: Spotify's own embed page carries the track
 * list (title, artist, length). It holds the first ~100 tracks of a long playlist.
 */
object SpotifyLists {
    private val link = Regex("""open\.spotify\.com/(?:intl-[a-z]+/)?(?:embed/)?(playlist|album)/([A-Za-z0-9]{22})""")
    private val uri = Regex("""spotify:(playlist|album):([A-Za-z0-9]{22})""")

    /** "playlist" or "album" and the id found in a pasted link, or null when the text is not one. */
    fun parseLink(text: String): Pair<String, String>? {
        val m = link.find(text) ?: uri.find(text) ?: return null
        return m.groupValues[1] to m.groupValues[2]
    }

    /** Throws [IllegalArgumentException] when the page does not hold a track list. */
    fun parsePage(html: String, kind: String): SpotifyList {
        val start = html.indexOf("""<script id="__NEXT_DATA__"""").takeIf { it >= 0 } ?: throw IllegalArgumentException("no data")
        val from = html.indexOf('>', start) + 1
        val to = html.indexOf("</script>", from)
        val entity = try {
            JSONObject(html.substring(from, to)).getJSONObject("props").getJSONObject("pageProps").getJSONObject("state").getJSONObject("data").getJSONObject("entity")
        } catch (e: Exception) { throw IllegalArgumentException("no data") }
        val name = entity.optString("name").ifBlank { entity.optString("title") }
        val tracks = entity.optJSONArray("trackList") ?: throw IllegalArgumentException("no tracks")
        val album = if (kind == "album") name else ""
        val songs = (0 until tracks.length()).mapNotNull { i ->
            val t = tracks.optJSONObject(i) ?: return@mapNotNull null
            val title = t.optString("title").trim()
            if (title.isBlank()) null
            else Song(title, t.optString("subtitle").replace(' ', ' ').trim(), album, t.optInt("duration") / 1000, "spotify:" + t.optString("uri").substringAfterLast(':'), Source.Spotify, 1.0)
        }
        return SpotifyList(name, songs)
    }

    /** Downloads and reads the list. Call it off the main thread. */
    fun fetch(kind: String, id: String): SpotifyList {
        val c = URL("https://open.spotify.com/embed/$kind/$id").openConnection() as HttpURLConnection
        try {
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            c.setRequestProperty("Accept-Language", "en")
            c.connectTimeout = 15000; c.readTimeout = 20000
            if (c.responseCode != 200) throw IllegalArgumentException("http ${c.responseCode}")
            return parsePage(c.inputStream.bufferedReader().readText(), kind)
        } finally { c.disconnect() }
    }
}
