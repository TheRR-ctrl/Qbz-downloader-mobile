package com.qbdlx.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Keeps the download queue in a JSON file so it survives the app being closed or the phone restarted.
 * Written to a temp file and renamed, so an interrupted write can't corrupt the saved queue.
 * Holds only track metadata and states — never credentials or tokens.
 */
class FileTaskStore(private val file: File) : TaskStore {

    override fun load(): List<DownloadTask> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        if (text.isBlank()) return emptyList()
        return runCatching { parse(text) }.getOrElse {
            // Unreadable: keep a copy before the next save replaces it, rather than silently losing the queue.
            runCatching { file.copyTo(File(file.parentFile, file.name + ".corrupt"), overwrite = true) }
            emptyList()
        }
    }

    @Synchronized
    override fun save(tasks: List<DownloadTask>) {
        // Keep the temporary file beside the destination: this makes an atomic rename possible
        // on the same filesystem and prevents a crash from exposing partially-written JSON.
        val parent = (file.absoluteFile.parentFile ?: throw java.io.IOException("Queue has no parent directory"))
        if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
            throw java.io.IOException("Could not create the download queue directory")
        }
        if (!parent.isDirectory) throw java.io.IOException("Download queue parent is not a directory")

        val prefix = "${file.name}.".padEnd(3, '_')
        val tmp = File.createTempFile(prefix, ".tmp", parent)
        try {
            // Flush the bytes to storage before publishing the new queue file.
            FileOutputStream(tmp).use { stream ->
                stream.write(serialize(tasks).toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                // Never delete the old queue first. A normal replace is safer than the former
                // delete-then-rename sequence, which could permanently lose the queue on failure.
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            // After a successful move this is already absent; after any failure remove only the
            // temporary file and leave the last known-good queue untouched.
            runCatching { Files.deleteIfExists(tmp.toPath()) }
        }
    }

    companion object {
        private const val VERSION = 1

        fun serialize(tasks: List<DownloadTask>): String {
            val arr = JSONArray()
            tasks.forEach { t ->
                arr.put(JSONObject().apply {
                    put("key", t.key)
                    put("status", t.status.name)
                    put("quality", t.quality.formatId)
                    put("batch", t.batchId)
                    t.error?.let { put("error", it) }
                    t.pendingUri?.let { put("pendingUri", it) }
                    t.savedUri?.let { put("savedUri", it) }
                    if (t.finishedAt > 0) put("finishedAt", t.finishedAt)
                    put("album", albumJson(t.album))
                    put("track", trackJson(t.track))
                })
            }
            return JSONObject().put("v", VERSION).put("tasks", arr).toString()
        }

        /** Tasks that can't be read are skipped rather than losing the whole queue. */
        fun parse(text: String): List<DownloadTask> {
            val arr = JSONObject(text).optJSONArray("tasks") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    val quality = Quality.entries.first { it.formatId == o.getInt("quality") }
                    DownloadTask(
                        key = o.getString("key"),
                        album = albumFrom(o.getJSONObject("album")),
                        track = trackFrom(o.getJSONObject("track")),
                        quality = quality,
                        batchId = o.optLong("batch", 0L),
                        status = TaskStatus.valueOf(o.getString("status")),
                        error = o.optString("error").takeIf { o.has("error") && it.isNotBlank() },
                    ).also { t ->
                        t.pendingUri = o.optString("pendingUri").takeIf { o.has("pendingUri") && it.isNotBlank() }
                        t.savedUri = o.optString("savedUri").takeIf { o.has("savedUri") && it.isNotBlank() }
                        t.finishedAt = o.optLong("finishedAt", 0L)
                    }
                }.getOrNull()
            }
        }

        internal fun albumJson(a: Album) = JSONObject().apply {
            put("id", a.id); put("title", a.title); put("artist", a.artist)
            a.coverUrl?.let { put("coverUrl", it) }
            put("tracksCount", a.tracksCount); put("year", a.year); put("quality", a.quality); put("hiRes", a.hiRes)
            // Tag data; empty values are left out, and old queue files simply don't have them.
            putIfNotBlank("albumArtist", a.albumArtist); putIfNotBlank("genre", a.genre); putIfNotBlank("label", a.label)
            putIfNotBlank("upc", a.upc); putIfNotBlank("copyright", a.copyright); putIfNotBlank("productType", a.productType)
            putIfNotBlank("releaseDate", a.releaseDate); putIfNotBlank("version", a.version)
            if (a.mediaCount > 0) put("mediaCount", a.mediaCount)
        }

        internal fun albumFrom(o: JSONObject) = Album(
            id = o.getString("id"), title = o.optString("title"), artist = o.optString("artist"),
            coverUrl = if (o.has("coverUrl")) o.getString("coverUrl") else null,
            tracksCount = o.optInt("tracksCount"), year = o.optString("year"),
            quality = o.optString("quality"), hiRes = o.optBoolean("hiRes"),
            albumArtist = o.str("albumArtist"), genre = o.str("genre"), label = o.str("label"),
            upc = o.str("upc"), copyright = o.str("copyright"), mediaCount = o.optInt("mediaCount"),
            productType = o.str("productType"), releaseDate = o.str("releaseDate"), version = o.str("version"),
        )

        internal fun trackJson(t: Track) = JSONObject().apply {
            put("id", t.id); put("title", t.title)
            t.version?.let { put("version", it) }
            put("number", t.number); put("disc", t.disc); put("artist", t.artist)
            put("durationSec", t.durationSec); put("streamable", t.streamable)
            put("quality", t.quality); put("hiRes", t.hiRes)
            putIfNotBlank("isrc", t.isrc); putIfNotBlank("composer", t.composer)
            if (t.explicit) put("explicit", true)
            if (t.albumTrackNumber > 0) put("albumTrackNumber", t.albumTrackNumber)
        }

        internal fun trackFrom(o: JSONObject) = Track(
            id = o.getLong("id"), title = o.optString("title"),
            version = if (o.has("version")) o.getString("version") else null,
            number = o.optInt("number"), disc = o.optInt("disc", 1), artist = o.optString("artist"),
            durationSec = o.optInt("durationSec"), streamable = o.optBoolean("streamable", true),
            quality = o.optString("quality"), hiRes = o.optBoolean("hiRes"),
            isrc = o.str("isrc"), composer = o.str("composer"), explicit = o.optBoolean("explicit"),
            albumTrackNumber = o.optInt("albumTrackNumber"),
        )

        private fun JSONObject.putIfNotBlank(key: String, value: String) { if (value.isNotBlank()) put(key, value) }
        private fun JSONObject.str(key: String): String = if (has(key)) optString(key) else ""
    }
}
