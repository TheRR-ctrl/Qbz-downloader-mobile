package com.qbdlx.mobile.ipod

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The songs still to be put on the iPod, kept on disk so an interrupted send can be taken up again. */
object IpodJobStore {
    private fun file(context: Context) = File(context.filesDir, "ipod-job.json")

    fun encode(items: List<SendItem>): String = JSONArray().apply {
        items.forEach { i -> put(JSONObject().put("key", i.key).put("uri", i.sourceUri).put("title", i.title).put("artist", i.artist)
            .put("album", i.album).put("genre", i.genre).put("ms", i.durationMs).put("no", i.trackNumber).put("year", i.year)
            .put("br", i.bitRate).put("rep", JSONArray(i.replacePids)).put("cover", i.coverUrl ?: "")) }
    }.toString()

    fun decode(text: String): List<SendItem> = runCatching {
        val a = JSONArray(text)
        (0 until a.length()).map { n ->
            val o = a.getJSONObject(n)
            val rep = o.optJSONArray("rep")?.let { r -> (0 until r.length()).map { r.getLong(it) } }.orEmpty()
            SendItem(o.getString("key"), o.getString("uri"), o.getString("title"), o.optString("artist"), o.optString("album"), o.optString("genre"),
                o.optLong("ms"), o.optInt("no"), o.optInt("year"), o.optInt("br", 320), rep, o.optString("cover").ifBlank { null })
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, items: List<SendItem>) { if (items.isEmpty()) clear(context) else runCatching { file(context).writeText(encode(items)) } }
    fun load(context: Context): List<SendItem> = runCatching { decode(file(context).readText()) }.getOrDefault(emptyList())
    fun clear(context: Context) { runCatching { file(context).delete() } }
}
