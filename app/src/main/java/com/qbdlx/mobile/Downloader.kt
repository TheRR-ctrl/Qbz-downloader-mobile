package com.qbdlx.mobile

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.OutputStream

/** Streams one HTTP body into an output stream, abortable from another thread via [CancelHandle]. */
object StreamCopier {
    fun copy(
        http: OkHttpClient,
        url: String,
        handle: CancelHandle,
        open: () -> OutputStream,
        onProgress: (Float) -> Unit,
    ) {
        handle.throwIfCancelled()
        val call = http.newCall(Request.Builder().url(url).build())
        handle.call = call
        if (handle.cancelled) call.cancel() // cancelled between the check above and publishing the call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response")
                val total = body.contentLength()
                open().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            handle.throwIfCancelled()
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                        if (total > 0 && done != total) throw IOException("Incomplete download ($done of $total bytes)")
                    }
                }
            }
        } finally {
            handle.call = null
        }
    }
}

/**
 * Saves tracks into the folder the user picked or, when none is chosen, into the shared Music collection:
 * Music/QobuzDownloaderX/<Artist>/<Album>/. If the chosen folder cannot be used (card removed, permission lost),
 * that track is saved in the default location instead and [onFolderUsable] reports it so the app can warn.
 */
class MediaStoreWorker(
    private val context: Context,
    private val api: QobuzApi,
    private val folder: DownloadFolder? = null,
    /** True when "Omitir archivos que ya existen" is on. */
    private val skipExisting: () -> Boolean = { true },
    /** True when "Reemplazar si la calidad es mayor" is on. Only used together with [skipExisting]. */
    private val upgradeExisting: () -> Boolean = { false },
    private val onFolderUsable: (usable: Boolean, reason: String?) -> Unit = { _, _ -> },
    /** True when the song is already in the extra folder the user asked to check. */
    private val alreadyKnown: (DownloadTask) -> Boolean = { false },
) : TaskWorker {

    // Cover art and full album details for the tags: retried a couple of times, one fetch per album.
    private val sources = TagSources(
        http = api.http,
        loadAlbum = { id -> api.getAlbum(id).album },
        log = { Diagnostics.log(it) },
    )

    /** Everything one track needs to be written, wherever it goes. */
    private class Plan(
        val task: DownloadTask, val tags: TrackTags, val cover: Cover?, val file: QobuzApi.FileUrl,
        val name: String, val mime: String, val handle: CancelHandle, val listener: TaskListener,
    ) {
        val mp3 get() = mime == "audio/mpeg"
        val track get() = task.track
        val album get() = task.album
        val artistDir get() = cleanName(album.artist)
        val albumDir get() = cleanName(album.title)
    }

    override fun download(task: DownloadTask, handle: CancelHandle, listener: TaskListener) {
        handle.throwIfCancelled()
        val track = task.track

        val resolution = folder?.resolve() ?: FolderResolution.Default
        if (resolution is FolderResolution.Unavailable) onFolderUsable(false, resolution.reason)

        // Already saved where it would go? Then there is nothing to do, and no need to ask Qobuz for anything,
        // unless "replace with higher quality" is on and this download could be an upgrade.
        if (skipExisting() && runCatching { alreadyKnown(task) }.getOrDefault(false)) {
            listener.onSkipped(); listener.onProgress(1f)
            return
        }
        val upgrade = skipExisting() && upgradeExisting()
        val existing = if (skipExisting()) findExisting(resolution, task, upgrade) else null
        var replacing: Pair<FileRef, AudioQuality>? = null
        var prefetched: QobuzApi.FileUrl? = null
        if (existing != null) {
            val old = if (upgrade) existing.quality() else null // unreadable -> never touch it
            val offered = if (old != null) api.getFileUrl(track.id, task.quality.formatId) else null
            handle.throwIfCancelled()
            if (old == null || offered == null || upgradeVerdict(old, offered.quality) == Upgrade.KEEP) {
                listener.onSaved(existing.uri())
                listener.onSkipped(); listener.onProgress(1f)
                if (resolution !is FolderResolution.Unavailable) onFolderUsable(true, null)
                return
            }
            replacing = existing to old
            prefetched = offered
        }

        // Tag data first, so the signed download URL below is as fresh as possible. All of this is best
        // effort: a missing cover or album detail only means fewer tags, never a failed download.
        val detailed = sources.detailedAlbum(task.album) { handle.cancelled }
        val tags = TrackTags.from(detailed, track)
        val cover = sources.coverFor(detailed.coverUrl ?: task.album.coverUrl) { handle.cancelled }
        handle.throwIfCancelled()

        val file = prefetched ?: api.getFileUrl(track.id, task.quality.formatId)
        handle.throwIfCancelled()

        val ext = if (file.mimeType.contains("mpeg")) "mp3" else "flac"
        val plan = Plan(
            task, tags, cover, file,
            name = trackFileName(track, ext),
            mime = if (ext == "mp3") "audio/mpeg" else "audio/flac",
            handle = handle, listener = listener,
        )

        var saved: FileRef? = null
        val chosen = (resolution as? FolderResolution.Ready)?.let { ready -> { saved = writeToFolder(ready, plan); true } }
        val usedChosenFolder = writeWithFallback(
            tree = chosen,
            fallback = { saved = writeToMusic(plan); false },
            onTreeFailure = { e -> onFolderUsable(false, e.cause?.message ?: e.message) },
        )
        val old = replacing
        val fresh = saved
        if (old != null && fresh != null) finishSwap(old.first, old.second, fresh, plan)
        // Where the file ended up: the kept old one when a replacement was refused, otherwise the new one.
        (if (old != null && task.skippedExisting) old.first else fresh)?.let { listener.onSaved(it.uri()) }
        // Anything that went wrong was reported above; tell the app when the chosen (or default) place worked.
        if (resolution is FolderResolution.Default || (resolution is FolderResolution.Ready && usedChosenFolder)) onFolderUsable(true, null)
    }

    /**
     * A file saved by a past download, or null. Normally only the name for the requested format counts (MP3 or FLAC);
     * when upgrading, the other format is looked for too, so a saved MP3 can be replaced by a FLAC. If anything can't
     * be checked the answer is "none", which simply downloads the track.
     */
    private fun findExisting(resolution: FolderResolution, task: DownloadTask, alsoOtherFormat: Boolean): FileRef? = try {
        val requested = if (task.quality == Quality.MP3) "mp3" else "flac"
        val formats = if (alsoOtherFormat) listOf("flac", "mp3") else listOf(requested)
        val artist = cleanName(task.album.artist)
        val album = cleanName(task.album.title)
        formats.firstNotNullOfOrNull { ext ->
            val name = trackFileName(task.track, ext)
            if (resolution is FolderResolution.Ready) {
                resolution.writer.find(listOf(artist, album), name)?.let { treeRef(resolution.writer, resolution.treeUri, it, name, ext == "mp3") }
            } else {
                findInMusic("Music/QobuzDownloaderX/$artist/$album", name)?.let { mediaRef(it, name, ext == "mp3") }
            }
        }
    } catch (e: Exception) {
        null
    }

    /** MediaStore stores the folder with a trailing slash; both spellings are asked for. Pending (half-written) files don't count. */
    private fun findInMusic(relativeDir: String, name: String): Uri? {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.query(
            collection, arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.RELATIVE_PATH} IN (?, ?) AND ${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.IS_PENDING} = 0",
            arrayOf("$relativeDir/", relativeDir, name), null,
        )?.use { c -> if (c.moveToFirst()) return ContentUris.withAppendedId(collection, c.getLong(0)) }
        return null
    }

    /** A saved file, wherever it lives: enough to judge its quality, delete it, or rename it. */
    private class FileRef(
        val inTree: Boolean, val mp3: Boolean,
        val readHead: () -> ByteArray?, val delete: () -> Boolean, val rename: (String) -> Boolean,
        /** The file's address now (a rename can change it). */
        val uri: () -> String,
    ) {
        fun quality(): AudioQuality? = if (mp3) AudioQuality.MP3 else readHead()?.let { FlacInfo.parse(it) }
    }

    private fun treeRef(writer: TreeWriter, treeUri: Uri, docId: String, name: String, mp3: Boolean): FileRef {
        var id = docId
        return FileRef(
            inTree = true, mp3 = mp3,
            readHead = { writer.readHead(id, FlacInfo.HEAD_BYTES) },
            delete = { writer.delete(id) },
            rename = { newName -> writer.rename(id, newName)?.also { id = it } != null },
            uri = { DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString() },
        )
    }

    private fun mediaRef(uri: Uri, name: String, mp3: Boolean): FileRef {
        val resolver = context.contentResolver
        return FileRef(
            inTree = false, mp3 = mp3,
            readHead = { runCatching { resolver.openInputStream(uri)?.use { it.readUpTo(FlacInfo.HEAD_BYTES) } }.getOrNull() },
            delete = { runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false) },
            rename = { newName ->
                runCatching { resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.DISPLAY_NAME, newName) }, null, null) > 0 }.getOrDefault(false)
            },
            uri = { uri.toString() },
        )
    }

    /**
     * The new file is completely saved. Keep it only if what is really inside it is a better, full-length file; then
     * remove the old one and give the new one the old one's name. At any doubt the old file stays untouched, and a
     * file that didn't earn its place is deleted instead.
     */
    private fun finishSwap(old: FileRef, oldQuality: AudioQuality, saved: FileRef, plan: Plan) {
        val label = "\"${plan.track.displayTitle}\""
        if (old.inTree != saved.inTree) {
            Diagnostics.log("[calidad] $label se guardó en otro lugar que la copia anterior; se conservan las dos")
            return
        }
        if (!verifySwap(oldQuality, saved.quality(), plan.track.durationSec)) {
            saved.delete()
            plan.listener.onSkipped()
            Diagnostics.log("[calidad] $label: lo descargado no es mejor o está incompleto; se conserva el archivo anterior")
            return
        }
        if (!old.delete()) {
            Diagnostics.log("[calidad] $label: no se pudo borrar la copia anterior; se conservan las dos")
            return
        }
        // Same format -> the new file was saved as "name (1)" while the old one existed; now the name is free.
        if (old.mp3 == plan.mp3 && !saved.rename(plan.name)) {
            Diagnostics.log("[calidad] $label: reemplazada, pero no se pudo quitar el \"(1)\" del nombre")
        }
        plan.listener.onReplaced()
    }

    /** The folder the user picked: Artist/Album subfolders inside it. Throws [FolderWriteException] if it fails. */
    private fun writeToFolder(ready: FolderResolution.Ready, plan: Plan): FileRef {
        val writer = ready.writer
        val docId = try {
            writer.createFile(listOf(plan.artistDir, plan.albumDir), plan.mime, plan.name)
        } catch (e: Exception) {
            throw FolderWriteException(e)
        }
        // Unlike MediaStore there is no "pending" state: a half-written file is visible until it is deleted, so
        // it is remembered (to clean up after a crash) and removed on any failure.
        plan.listener.onPendingUri(ready.documentUri(docId).toString())
        try {
            StreamCopier.copy(
                api.http, plan.file.url, plan.handle,
                open = { tagged(writer.open(docId), plan) },
                onProgress = plan.listener::onProgress,
            )
            plan.handle.throwIfCancelled()
            plan.listener.onPendingUri(null)
            scan(ready.documentUri(docId), docId, plan.mime)
        } catch (e: Exception) {
            writer.delete(docId)
            plan.listener.onPendingUri(null)
            throw e
        }
        return treeRef(writer, ready.treeUri, docId, plan.name, plan.mp3)
    }

    /** The default place: the shared Music collection through MediaStore. */
    private fun writeToMusic(plan: Plan): FileRef {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, plan.name)
            put(MediaStore.Audio.Media.MIME_TYPE, plan.file.mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/QobuzDownloaderX/${plan.artistDir}/${plan.albumDir}")
            put(MediaStore.Audio.Media.TITLE, plan.track.displayTitle)
            put(MediaStore.Audio.Media.ARTIST, plan.track.artist)
            put(MediaStore.Audio.Media.ALBUM, plan.album.title)
            put(MediaStore.Audio.Media.TRACK, plan.tags.trackNumber)
            plan.tags.year.toIntOrNull()?.let { put(MediaStore.Audio.Media.YEAR, it) }
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw StorageException("Could not create file in Music folder")
        // Remembered so a crash or restart can delete this half-written file (see discard()).
        plan.listener.onPendingUri(uri.toString())
        try {
            StreamCopier.copy(
                api.http, plan.file.url, plan.handle,
                open = { tagged(resolver.openOutputStream(uri) ?: throw StorageException("Could not open the output file"), plan) },
                onProgress = plan.listener::onProgress,
            )
            plan.handle.throwIfCancelled() // don't publish a file the user just cancelled
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            plan.listener.onPendingUri(null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            plan.listener.onPendingUri(null)
            throw e
        }
        return mediaRef(uri, plan.name, plan.mp3)
    }

    /** Writes the tags into the file as it downloads; a tagging problem is only logged. */
    private fun tagged(out: OutputStream, plan: Plan): OutputStream = TaggingOutputStream(
        out, plan.tags, plan.cover,
        onTagError = { e ->
            Diagnostics.log("[etiquetas] no se pudieron escribir en \"${plan.track.displayTitle}\": ${e.javaClass.simpleName}: ${e.message}")
        },
    )

    /** Asks the media scanner to index a file saved in a picked folder. Best effort: it is not verified that every phone shows it at once. */
    private fun scan(docUri: Uri, docId: String, mime: String) {
        runCatching {
            if (docUri.authority == "com.android.externalstorage.documents") {
                val path = TreePaths.fsPath(docId, Environment.getExternalStorageDirectory().absolutePath)
                MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mime), null)
            }
        }
    }

    /** Deletes a half-written file left by an interrupted run: it may be a MediaStore row or a document in the picked folder. */
    override fun discard(uri: String) {
        val u = Uri.parse(uri)
        runCatching {
            if (DocumentsContract.isDocumentUri(context, u)) DocumentsContract.deleteDocument(context.contentResolver, u)
            else context.contentResolver.delete(u, null, null)
        }
    }
}
