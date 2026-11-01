package com.qbdlx.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Audio files in folders the user picked (for example the SD card), for files the media index does not list. */
object Trees {
    private val audioExt = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "alac", "aif", "aiff")

    suspend fun list(context: Context, trees: List<Uri>): List<AudioFile> {
        val out = ArrayList<AudioFile>()
        for (tree in trees) {
            val root = DocumentsContract.getTreeDocumentId(tree)
            walk(context, tree, root, root.substringAfter(':', ""), out)
        }
        return out
    }

    private suspend fun walk(context: Context, tree: Uri, docId: String, path: String, out: MutableList<AudioFile>) {
        coroutineContext.ensureActive()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        val rows = ArrayList<Triple<String, String, String>>()
        runCatching {
            context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) rows += Triple(c.getString(0), c.getString(1).orEmpty(), c.getString(2).orEmpty())
            }
        }
        for ((id, name, mime) in rows) {
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) { walk(context, tree, id, "$path/$name".trimStart('/'), out); continue }
            if (!(mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in audioExt)) continue
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
            val meta = readTags(context, uri) ?: continue
            if (meta.durationSec < 30) continue
            out += AudioFile(uri, name, meta.title, meta.artist, meta.album, meta.durationSec, "$path/")
        }
    }

    private class Meta(val title: String?, val artist: String?, val album: String?, val durationSec: Int)

    private fun readTags(context: Context, uri: Uri): Meta? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            Meta(
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE), r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                ((r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000).toInt(),
            )
        } catch (e: Exception) { null } finally { runCatching { r.release() } }
    }
}
