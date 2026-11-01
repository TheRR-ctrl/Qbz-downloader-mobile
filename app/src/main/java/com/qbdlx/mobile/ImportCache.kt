package com.qbdlx.mobile

import org.json.JSONArray
import org.json.JSONObject

/** What was found for wanted songs, kept between runs so a list is never searched twice. Pure logic, no Android. */
object ImportCache {
    fun key(song: ImportedSong) = SongImport.normalize(song.title) + "|" + SongImport.normalize(song.artist)

    fun toJson(items: Map<String, ImportItem>): String {
        val out = JSONObject()
        for ((k, it) in items) {
            if (it.searchFailed) continue // a failed search must be tried again, not remembered
            out.put(k, JSONObject()
                .put("level", it.level.name)
                .put("chosen", it.chosen ?: -1)
                .put("also", JSONArray(it.also.toList()))
                .put("c", JSONArray().apply { it.candidates.forEach { (a, t) -> put(JSONObject()
                    .put("a", JSONObject().put("id", a.id).put("t", a.title).put("ar", a.artist).put("cv", a.coverUrl ?: "").put("n", a.tracksCount)
                        .put("y", a.year).put("q", a.quality).put("h", a.hiRes).put("g", a.genre))
                    .put("k", JSONObject().put("id", t.id).put("t", t.title).put("v", t.version ?: "").put("n", t.number).put("d", t.disc)
                        .put("ar", t.artist).put("s", t.durationSec).put("st", t.streamable).put("q", t.quality).put("h", t.hiRes)
                        .put("isrc", t.isrc).put("an", t.albumTrackNumber))) } }))
        }
        return out.toString()
    }

    /** The remembered results, keyed by [key]; a damaged file just gives an empty map. */
    fun fromJson(text: String, songs: (String) -> ImportedSong? = { null }): Map<String, StoredItem> = runCatching {
        val j = JSONObject(text); val out = HashMap<String, StoredItem>()
        for (k in j.keys()) {
            val o = j.getJSONObject(k)
            val cands = o.getJSONArray("c").let { arr -> (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i); val a = c.getJSONObject("a"); val t = c.getJSONObject("k")
                Album(a.getString("id"), a.getString("t"), a.getString("ar"), a.optString("cv").ifBlank { null }, a.optInt("n"), a.optString("y"), a.optString("q"), a.optBoolean("h"), genre = a.optString("g")) to
                    Track(t.getLong("id"), t.getString("t"), t.optString("v").ifBlank { null }, t.optInt("n"), t.optInt("d"), t.getString("ar"), t.optInt("s"), t.optBoolean("st", true), t.optString("q"), t.optBoolean("h"), t.optString("isrc"), albumTrackNumber = t.optInt("an"))
            } }
            val also = o.getJSONArray("also").let { arr -> (0 until arr.length()).map { arr.getInt(it) }.toSet() }
            out[k] = StoredItem(MatchLevel.valueOf(o.getString("level")), o.getInt("chosen").takeIf { it >= 0 }, also, cands)
        }
        out
    }.getOrDefault(emptyMap())

    class StoredItem(val level: MatchLevel, val chosen: Int?, val also: Set<Int>, val candidates: List<Pair<Album, Track>>) {
        fun toItem(song: ImportedSong) = ImportItem(song, candidates, level, chosen, also)
    }
}
