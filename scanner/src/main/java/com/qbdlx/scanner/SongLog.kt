package com.qbdlx.scanner

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** How a song's title and artist were found. */
enum class Source(val id: String) { Tags("tags"), AcoustId("acoustid"), Spotify("spotify"), Unknown("unknown") }

/** One song found on the phone. [file] is only for the person reading the log. */
data class Song(
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val file: String,
    val source: Source,
    /** 1.0 for the file's own tags, AcoustID's score for a sound match, 0 when nothing was found. */
    val confidence: Double,
    /** Folder on the phone (relative path), so a whole folder can be skipped. */
    val folder: String = "",
)

/**
 * The list the scanner shares and QBDLX Mobile imports. Format (version 1):
 * `{"app":"qbdlx-scanner","version":1,"created":"…","songs":[{"title","artist","album","durationSec","file","source","confidence"}]}`
 */
object SongLog {
    fun toJson(songs: List<Song>, now: Date = Date(), name: String = ""): String {
        val created = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(now)
        return JSONObject()
            .put("app", "qbdlx-scanner")
            .put("version", 1)
            .put("created", created)
            .apply { if (name.isNotBlank()) put("name", name) }
            .put("songs", JSONArray().apply {
                songs.forEach { s ->
                    put(
                        JSONObject().put("title", s.title).put("artist", s.artist).put("album", s.album)
                            .put("durationSec", s.durationSec).put("file", s.file)
                            .put("source", s.source.id).put("confidence", Math.round(s.confidence * 100) / 100.0).put("folder", s.folder),
                    )
                }
            })
            .toString(1)
    }

    /** Reads a list written by [toJson] (the last scan, kept on the phone). Returns an empty list when the text is not one. */
    fun fromJson(text: String): List<Song> = try {
        val arr = JSONObject(text).getJSONArray("songs")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Song(o.optString("title"), o.optString("artist"), o.optString("album"), o.optInt("durationSec"), o.optString("file"),
                Source.entries.firstOrNull { it.id == o.optString("source") } ?: Source.Unknown, o.optDouble("confidence", 0.0), o.optString("folder"))
        }
    } catch (e: Exception) { emptyList() }
}
