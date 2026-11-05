package com.qbdlx.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What the Library screen shows, kept on disk so it opens at once and is refreshed in the background. Metadata only, no credentials. */
class LibrarySnapshot(
    val playlists: List<Playlist>, val albums: List<Album>, val tracks: List<Pair<Album, Track>>, val artists: List<FavArtist>,
)

class LibraryCache(private val file: File) {
    fun load(): LibrarySnapshot? = runCatching {
        if (!file.exists()) return null
        val o = JSONObject(file.readText())
        if (o.optInt("v") != VERSION) return null
        fun <T> list(key: String, f: (JSONObject) -> T) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { f(a.getJSONObject(it)) } }.orEmpty()
        LibrarySnapshot(
            list("playlists") { p ->
                Playlist(p.getString("id"), p.optString("name"), p.optString("cover").takeIf { it.isNotBlank() }, p.optInt("n"), p.optString("owner"),
                    p.optJSONArray("covers")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty())
            },
            list("albums") { FileTaskStore.albumFrom(it) },
            list("tracks") { FileTaskStore.albumFrom(it.getJSONObject("album")) to FileTaskStore.trackFrom(it.getJSONObject("track")) },
            list("artists") { FavArtist(it.getString("name"), it.optString("cover").takeIf { c -> c.isNotBlank() }) },
        )
    }.getOrNull()

    @Synchronized
    fun save(s: LibrarySnapshot) {
        runCatching {
            val o = JSONObject().put("v", VERSION)
            o.put("playlists", JSONArray().also { a -> s.playlists.forEach { p ->
                a.put(JSONObject().put("id", p.id).put("name", p.name).put("cover", p.coverUrl ?: "").put("n", p.tracksCount).put("owner", p.owner)
                    .put("covers", JSONArray(p.covers))) } })
            o.put("albums", JSONArray().also { a -> s.albums.forEach { a.put(FileTaskStore.albumJson(it)) } })
            o.put("tracks", JSONArray().also { a -> s.tracks.forEach { (al, t) -> a.put(JSONObject().put("album", FileTaskStore.albumJson(al)).put("track", FileTaskStore.trackJson(t))) } })
            o.put("artists", JSONArray().also { a -> s.artists.forEach { a.put(JSONObject().put("name", it.name).put("cover", it.cover ?: "")) } })
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    fun clear() { file.delete() }

    private companion object { const val VERSION = 1 }
}
