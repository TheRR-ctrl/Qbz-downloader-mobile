package com.qbdlx.mobile

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract

/** What a file name in a folder tells about the song: "03 - Title.mp3" -> "title", plus the folders above it. */
/** [uri] is set only for files the app may delete (the download folder). */
class KnownSong(val stem: String, val path: String, val label: String = "", val uri: String? = null)

/** Decides whether a track is among already-known songs (matched by name, not by folder layout). Pure logic. */
class KnownSongs(private val songs: List<KnownSong>) {
    val size get() = songs.size
    fun has(title: String, artist: String): Boolean {
        val t = SongImport.normalize(title); val a = SongImport.normalize(artist)
        if (t.isBlank()) return false
        val firstArtist = a.split(' ').firstOrNull { it.length >= 3 } ?: a
        return songs.any { k ->
            val whole = k.stem == t || k.stem.endsWith(" $t") || k.stem.startsWith("$t ")
            whole && (a.isBlank() || k.path.contains(a) || k.stem.contains(a) || (firstArtist.isNotBlank() && (k.path.contains(firstArtist) || k.stem.contains(firstArtist))))
        }
    }
    companion object {
        private val audio = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "alac")
        fun stemOf(fileName: String): String? {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext !in audio) return null
            val base = fileName.substringBeforeLast('.').replace(Regex("^\\s*\\d{1,3}\\s*[-.)]?\\s*"), "")
            return SongImport.normalize(base).ifBlank { null }
        }
    }
}

/** A second folder, read only, whose songs must not be downloaded again. */
class KnownFolder(private val context: Context, private val prefs: SharedPreferences) {
    @Volatile private var cache: Pair<String, KnownSongs>? = null
    val treeUri: Uri? get() = prefs.getString(KEY, null)?.let(Uri::parse)
    val name: String? get() = treeUri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }

    fun save(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val old = treeUri
        prefs.edit().putString(KEY, uri.toString()).apply()
        cache = null
        if (old != null && old != uri) runCatching { context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun clear() {
        val old = treeUri
        prefs.edit().remove(KEY).apply(); cache = null
        old?.let { runCatching { context.contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    }

    /** Every audio file in the folder, labelled with where it is (for the duplicates report). */
    fun listAll(tag: String): List<KnownSong> {
        val uri = treeUri ?: return emptyList()
        return walkTree(context, uri, tag)
    }

    /** The songs in the folder, read once per run (folders can be large). Empty if it cannot be read: then nothing is skipped. */
    @Synchronized fun songs(): KnownSongs {
        val uri = treeUri ?: return KnownSongs(emptyList())
        cache?.takeIf { it.first == uri.toString() }?.let { return it.second }
        val ops = ResolverTreeOps(context.contentResolver, uri)
        val out = ArrayList<KnownSong>()
        fun walk(docId: String, path: String, depth: Int) {
            if (depth > 6) return
            for (c in runCatching { ops.listChildren(docId) }.getOrDefault(emptyList())) {
                if (c.isDir) walk(c.docId, path + " " + SongImport.normalize(c.name), depth + 1)
                else KnownSongs.stemOf(c.name)?.let { out += KnownSong(it, path.trim()) }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(uri), "", 0)
        return KnownSongs(out).also { cache = uri.toString() to it }
    }

    private companion object { const val KEY = "known_tree_uri" }
}

/** Songs that are in more than one place, as groups of the files. */
object Duplicates {
    /** Which copies to remove by default: all the app may delete when a copy it can't delete is kept elsewhere, else all but the best (FLAC first). */
    fun defaultDeletions(group: List<KnownSong>): List<KnownSong> {
        val deletable = group.filter { it.uri != null }
        if (deletable.isEmpty()) return emptyList()
        if (deletable.size < group.size) return deletable
        val keep = deletable.firstOrNull { it.label.endsWith(".flac", true) } ?: deletable.first()
        return deletable - keep
    }

    /** Title-only names match when one is the other with up to three words in front ("yuridia en su lugar" ~ "en su lugar"). */
    fun find(songs: List<KnownSong>): List<List<KnownSong>> {
        val byStem = songs.groupBy { it.stem }
        val used = HashSet<KnownSong>()
        val out = ArrayList<List<KnownSong>>()
        for (s in songs) {
            if (s in used) continue
            val words = s.stem.split(' ')
            // The shortest form is the key: every longer name that ends with it joins the group.
            val group = LinkedHashSet<KnownSong>(byStem[s.stem].orEmpty())
            for (n in 1..3) if (words.size - n >= 2) byStem[words.drop(n).joinToString(" ")]?.let { group += it }
            if (group.size >= 2) { used += group; out += group.toList() }
        }
        return out.filter { it.size >= 2 }
    }
}

/** Audio files under a picked folder, up to six levels deep. */
fun walkTree(context: Context, uri: Uri, tag: String, deletable: Boolean = false): List<KnownSong> {
    val ops = ResolverTreeOps(context.contentResolver, uri)
    val out = ArrayList<KnownSong>()
    fun walk(docId: String, shown: String, path: String, depth: Int) {
        if (depth > 6) return
        for (c in runCatching { ops.listChildren(docId) }.getOrDefault(emptyList())) {
            if (c.isDir) walk(c.docId, "$shown/${c.name}", path + " " + SongImport.normalize(c.name), depth + 1)
            else KnownSongs.stemOf(c.name)?.let { out += KnownSong(it, path.trim(), "$tag: $shown/${c.name}".replace(": /", ": "), if (deletable) DocumentsContract.buildDocumentUriUsingTree(uri, c.docId).toString() else null) }
        }
    }
    walk(DocumentsContract.getTreeDocumentId(uri), "", "", 0)
    return out
}

/** The songs in the default Music/QobuzDownloaderX folder (MediaStore), for when no download folder was picked. */
fun listMusicDownloads(context: Context, tag: String): List<KnownSong> {
    val out = ArrayList<KnownSong>()
    val collection = android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
    context.contentResolver.query(
        collection, arrayOf(android.provider.MediaStore.Audio.Media._ID, android.provider.MediaStore.Audio.Media.DISPLAY_NAME, android.provider.MediaStore.Audio.Media.RELATIVE_PATH),
        "${android.provider.MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?", arrayOf("Music/${DefaultFolder.name}%"), null,
    )?.use { c ->
        while (c.moveToNext()) {
            val name = c.getString(1).orEmpty(); val dir = c.getString(2).orEmpty().trim('/')
            KnownSongs.stemOf(name)?.let { out += KnownSong(it, SongImport.normalize(dir), "$tag: $dir/$name", android.content.ContentUris.withAppendedId(collection, c.getLong(0)).toString()) }
        }
    }
    return out
}
