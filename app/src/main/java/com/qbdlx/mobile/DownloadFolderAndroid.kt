package com.qbdlx.mobile

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException
import java.io.OutputStream

/** [TreeOps] on top of Android's DocumentsContract: the thin, Android-only layer under the testable [TreeWriter]. */
class ResolverTreeOps(private val resolver: ContentResolver, private val treeUri: Uri) : TreeOps {
    private fun doc(docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    override fun listChildren(parentDocId: String): List<TreeChild> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val out = ArrayList<TreeChild>()
        resolver.query(
            children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE), null, null, null,
        )?.use { c ->
            while (c.moveToNext()) out += TreeChild(c.getString(0), c.getString(1).orEmpty(), c.getString(2) == Document.MIME_TYPE_DIR)
        }
        return out
    }

    override fun createDir(parentDocId: String, name: String) = create(parentDocId, Document.MIME_TYPE_DIR, name)
    override fun createFile(parentDocId: String, mime: String, name: String) = create(parentDocId, mime, name)

    private fun create(parentDocId: String, mime: String, name: String): String {
        val created = DocumentsContract.createDocument(resolver, doc(parentDocId), mime, name)
            ?: throw IOException("The folder would not create \"$name\"")
        return DocumentsContract.getDocumentId(created)
    }

    // ContentResolver.delete() on a document URI is not supported by document providers; this is the right call.
    override fun delete(docId: String) { DocumentsContract.deleteDocument(resolver, doc(docId)) }

    override fun openInput(docId: String): java.io.InputStream =
        resolver.openInputStream(doc(docId)) ?: throw IOException("Could not open the file")

    override fun rename(docId: String, name: String): String {
        val renamed = DocumentsContract.renameDocument(resolver, doc(docId), name) ?: throw IOException("The folder would not rename the file")
        return DocumentsContract.getDocumentId(renamed)
    }

    override fun openOutput(docId: String): OutputStream =
        resolver.openOutputStream(doc(docId), "wt") ?: throw IOException("Could not open the output file") // "wt": truncate when overwriting
}

/** What to do with the next download as far as the folder is concerned. */
sealed interface FolderResolution {
    /** No folder chosen: use Music/QobuzDownloaderX as always. */
    data object Default : FolderResolution
    /** A chosen folder that is there and writable right now. */
    class Ready(val treeUri: Uri, val writer: TreeWriter) : FolderResolution {
        fun documentUri(docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    }
    /** A folder was chosen but cannot be used now (permission lost, card removed, folder deleted). */
    class Unavailable(val reason: String) : FolderResolution
}

/** The folder the user picked for downloads, kept as a persistable permission plus its address. */
class DownloadFolder(private val context: Context, private val prefs: SharedPreferences) {
    private val resolver: ContentResolver get() = context.contentResolver
    @Volatile private var cached: Pair<String, TreeWriter>? = null

    val treeUri: Uri? get() = prefs.getString(KEY, null)?.let(Uri::parse)

    /** Where the chosen folder points, for the Settings screen. */
    val location: TreePaths.Location? get() = treeUri?.let { runCatching { TreePaths.locate(DocumentsContract.getTreeDocumentId(it)) }.getOrNull() }

    /** Checked before every download, off the main thread: permission still granted and the root still answering. */
    fun resolve(): FolderResolution {
        val uri = treeUri ?: return FolderResolution.Default
        return try {
            check(resolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }) { "Permission to the folder was lost" }
            val rootDoc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            val canCreate = resolver.query(rootDoc, arrayOf(Document.COLUMN_FLAGS), null, null, null)?.use { c ->
                c.moveToFirst() && (c.getInt(0) and Document.FLAG_DIR_SUPPORTS_CREATE) != 0
            } ?: false
            check(canCreate) { "The folder is not available" }
            val key = uri.toString()
            val writer = cached?.takeIf { it.first == key }?.second
                ?: TreeWriter(ResolverTreeOps(resolver, uri), DocumentsContract.getTreeDocumentId(uri)).also { cached = key to it }
            FolderResolution.Ready(uri, writer)
        } catch (e: Exception) {
            FolderResolution.Unavailable(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Remembers a folder the user just picked. Throws (leaving the old setting alone) if we can't really write there. */
    fun save(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val previous = treeUri
        try {
            resolver.takePersistableUriPermission(uri, flags)
            val writer = TreeWriter(ResolverTreeOps(resolver, uri), DocumentsContract.getTreeDocumentId(uri))
            writer.probe() // a real create + delete: a permission alone doesn't prove the folder accepts files
            prefs.edit().putString(KEY, uri.toString()).apply()
            cached = uri.toString() to writer
            if (previous != null && previous != uri) release(previous)
        } catch (e: Exception) {
            if (previous != uri) release(uri) // don't keep a permission for a folder we won't use
            throw e
        }
    }

    /** Back to the default Music folder. */
    fun clear() {
        val previous = treeUri
        prefs.edit().remove(KEY).apply()
        cached = null
        previous?.let(::release)
    }

    private fun release(uri: Uri) {
        runCatching {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    private companion object { const val KEY = "download_tree_uri" }
}
