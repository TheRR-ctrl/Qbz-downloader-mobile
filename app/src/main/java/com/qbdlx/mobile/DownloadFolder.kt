package com.qbdlx.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import java.io.OutputStream

/**
 * Saving into a folder the user picked (Android's folder picker), as opposed to the default Music folder.
 * Everything here is plain Kotlin so it can be unit-tested; the few calls that need Android sit behind [TreeOps]
 * in DownloadFolderAndroid.kt.
 */

/** Reads up to [n] bytes (fewer at end of stream); `readNBytes` only exists from Android 13. */
fun java.io.InputStream.readUpTo(n: Int): ByteArray {
    val buf = ByteArray(n)
    var got = 0
    while (got < n) { val r = read(buf, got, n - got); if (r < 0) break; got += r }
    return if (got == n) buf else buf.copyOf(got)
}

/** A file or folder name that is safe to create: no path separators or characters FAT/SD cards reject. */
fun cleanName(s: String): String =
    s.replace(Regex("""[\\/:*?"<>|]"""), "_").replace(Regex("\\s+"), " ").trim().trimEnd('.').ifEmpty { "_" }

/**
 * "NN - Title.ext". NN is the track's number on its album: a playlist rewrites [Track.number] to the position in the
 * list, which would give the same track a different name depending on where it was downloaded from (and defeat the
 * "skip files that already exist" check). Formatted with a fixed locale so the digits are always 0-9.
 */
fun trackFileName(track: Track, ext: String): String =
    String.format(java.util.Locale.ROOT, "%02d - %s.%s", track.albumTrackNumber.takeIf { it > 0 } ?: track.number, cleanName(track.displayTitle), ext)

class TreeChild(val docId: String, val name: String, val isDir: Boolean)

/** The handful of operations a folder tree needs. Android implements them with DocumentsContract; tests use a fake. */
interface TreeOps {
    fun listChildren(parentDocId: String): List<TreeChild>
    fun createDir(parentDocId: String, name: String): String
    fun createFile(parentDocId: String, mime: String, name: String): String
    fun delete(docId: String)
    fun openOutput(docId: String): OutputStream
    fun openInput(docId: String): java.io.InputStream
    /** Renames a document and returns its (possibly new) document id. */
    fun rename(docId: String, name: String): String
}

/** Writing to the chosen folder failed (it was removed, the card is gone, permission was lost): not a network problem. */
class FolderWriteException(cause: Throwable) : IOException("Could not write to the chosen folder: ${cause.message}", cause)

/**
 * Creates `<root>/<Artist>/<Album>/<file>` in a picked folder.
 *
 * Ten downloads of one album start together, so finding-or-creating a folder is done under a lock and remembered:
 * without it each worker would see "no such folder" and create its own, leaving "Album (1)", "Album (2)"…
 */
class TreeWriter(private val ops: TreeOps, private val rootDocId: String) {
    private val lock = Any()
    private val known = HashMap<String, String>() // "Artist/Album" -> document id

    fun ensureDir(names: List<String>): String = synchronized(lock) {
        var parent = rootDocId
        var path = ""
        for (name in names) {
            path = if (path.isEmpty()) name else "$path/$name"
            val cached = known[path]
            parent = cached ?: (ops.listChildren(parent).firstOrNull { it.isDir && it.name == name }?.docId ?: ops.createDir(parent, name))
                .also { known[path] = it }
        }
        parent
    }

    /** Whether `<dirs>/<name>` is already there. It only looks: a folder is never created just to check. */
    fun exists(dirs: List<String>, name: String): Boolean = find(dirs, name) != null

    /** The document id of `<dirs>/<name>`, or null. Like [exists] it never creates anything. */
    fun find(dirs: List<String>, name: String): String? = synchronized(lock) {
        var parent = rootDocId
        var path = ""
        for (dir in dirs) {
            path = if (path.isEmpty()) dir else "$path/$dir"
            val id = known[path] ?: ops.listChildren(parent).firstOrNull { it.isDir && it.name == dir }?.docId?.also { known[path] = it }
            parent = id ?: return null
        }
        ops.listChildren(parent).firstOrNull { !it.isDir && it.name == name }?.docId
    }

    /** The first [n] bytes of a file (fewer if it is shorter), or null if it can't be read. */
    fun readHead(docId: String, n: Int): ByteArray? = try {
        ops.openInput(docId).use { it.readUpTo(n) }
    } catch (e: Exception) { null }

    /** Renames a file; the new document id, or null if the provider refused. */
    fun rename(docId: String, name: String): String? = try { ops.rename(docId, name) } catch (e: Exception) { null }

    /**
     * Creates the file inside [dirs]. If the remembered folder has since been deleted (the user cleaned up while the
     * app was open), forget what we know and try once more from the root.
     */
    fun createFile(dirs: List<String>, mime: String, name: String): String =
        try {
            ops.createFile(ensureDir(dirs), mime, name)
        } catch (e: Exception) {
            synchronized(lock) { known.clear() }
            ops.createFile(ensureDir(dirs), mime, name)
        }

    /** Opens the file for writing; any failure of the destination is reported as [FolderWriteException]. */
    fun open(docId: String): OutputStream =
        try { GuardedOutputStream(ops.openOutput(docId)) } catch (e: Exception) { throw FolderWriteException(e) }

    fun delete(docId: String): Boolean = runCatching { ops.delete(docId) }.isSuccess

    /** What is inside the folder [docId] (an id from [find], [findDir] or the tree root). */
    fun list(docId: String): List<TreeChild> = ops.listChildren(docId)

    /** The document id of the folder reached by [names] from the root, or null. Never creates anything. */
    fun findDir(names: List<String>): String? = synchronized(lock) {
        var parent = rootDocId
        for (n in names) parent = ops.listChildren(parent).firstOrNull { it.isDir && it.name == n }?.docId ?: return null
        parent
    }

    fun createIn(parentDocId: String, mime: String, name: String): String = ops.createFile(parentDocId, mime, name)

    fun openForRead(docId: String): java.io.InputStream = ops.openInput(docId)

    /** Overwrites a file's content (truncating it). */
    fun openForOverwrite(docId: String): OutputStream = ops.openOutput(docId)

    /** Proves the folder is really writable by creating and removing a tiny file; throws if it is not. */
    fun probe() {
        val id = ops.createFile(rootDocId, "application/octet-stream", "qbdlx-write-test.tmp")
        ops.delete(id)
    }
}

/** Turns any failure of the destination into [FolderWriteException], so it can be told apart from a network error. */
class GuardedOutputStream(private val out: OutputStream) : OutputStream() {
    override fun write(b: Int) = guard { out.write(b) }
    override fun write(b: ByteArray, off: Int, len: Int) = guard { out.write(b, off, len) }
    override fun flush() = guard { out.flush() }
    override fun close() = guard { out.close() }

    private inline fun guard(block: () -> Unit) {
        try { block() } catch (e: FolderWriteException) { throw e } catch (e: IOException) { throw FolderWriteException(e) }
    }
}

/**
 * Runs [tree] (when a folder is chosen and usable); if the folder itself fails, [onTreeFailure] is told and the same
 * work is done by [fallback] in the default location. Network errors and cancellations are not the folder's fault, so
 * they pass through untouched.
 */
fun <R> writeWithFallback(tree: (() -> R)?, fallback: () -> R, onTreeFailure: (Throwable) -> Unit): R {
    if (tree != null) {
        try { return tree() } catch (e: FolderWriteException) { onTreeFailure(e) }
    }
    return fallback()
}

object TreePaths {
    /** Where a document id points: the volume ("primary" is the phone's storage) and the path inside it. */
    class Location(val volume: String, val isPrimary: Boolean, val path: String)

    /** Document ids look like `primary:Music/Qobuz` or `1A2B-3C4D:Music`. */
    fun locate(docId: String): Location {
        val i = docId.indexOf(':')
        val volume = if (i < 0) docId else docId.substring(0, i)
        val path = if (i < 0) "" else docId.substring(i + 1).trim('/')
        return Location(volume, volume == "primary", path)
    }

    /** The real path of a document on shared storage, which the media scanner needs. [primaryRoot] is the phone's storage root. */
    fun fsPath(docId: String, primaryRoot: String = "/storage/emulated/0"): String {
        val l = locate(docId)
        val base = if (l.isPrimary) primaryRoot.trimEnd('/') else "/storage/${l.volume}"
        return if (l.path.isEmpty()) base else "$base/${l.path}"
    }
}

/**
 * Whether to warn that the chosen folder can't be used. The warning shows once, stays until the user dismisses it or
 * the folder works again, and a later problem shows it again.
 */
class FolderStatus {
    var problem by mutableStateOf(false); private set
    var dismissed by mutableStateOf(false); private set
    val visible: Boolean get() = problem && !dismissed

    fun report(usable: Boolean) {
        if (usable) { problem = false; dismissed = false } else problem = true
    }

    fun dismiss() { dismissed = true }
}
