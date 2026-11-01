package com.qbdlx.mobile

import org.json.JSONArray
import org.json.JSONObject

/** The play queue and the listening history as JSON text, kept in the app's preferences. Metadata only, no addresses or tokens. */
object QueueStore {
    class Saved(val items: List<Pair<Album, Track>>, val position: Int, val positionMs: Long)

    private fun encodeItems(items: List<Pair<Album, Track>>): JSONArray = JSONArray().apply {
        items.forEach { (a, t) -> put(JSONObject().put("a", FileTaskStore.albumJson(a)).put("t", FileTaskStore.trackJson(t))) }
    }

    private fun decodeItems(arr: JSONArray?): List<Pair<Album, Track>> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { val o = arr.getJSONObject(i); FileTaskStore.albumFrom(o.getJSONObject("a")) to FileTaskStore.trackFrom(o.getJSONObject("t")) }.getOrNull()
        }
    }

    fun encodeQueue(items: List<Pair<Album, Track>>, position: Int, positionMs: Long): String =
        JSONObject().put("pos", position).put("ms", positionMs).put("items", encodeItems(items)).toString()

    fun decodeQueue(text: String?): Saved? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(text)
            val items = decodeItems(o.optJSONArray("items"))
            if (items.isEmpty()) null else Saved(items, o.optInt("pos").coerceIn(0, items.size - 1), o.optLong("ms"))
        }.getOrNull()
    }

    fun encodeList(items: List<Pair<Album, Track>>): String = encodeItems(items).toString()

    fun decodeList(text: String?): List<Pair<Album, Track>> =
        if (text.isNullOrBlank()) emptyList() else runCatching { decodeItems(JSONArray(text)) }.getOrDefault(emptyList())
}
