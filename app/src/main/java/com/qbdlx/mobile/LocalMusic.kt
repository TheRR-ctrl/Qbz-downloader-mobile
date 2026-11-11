package com.qbdlx.mobile

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract

/** Carpetas locales (como UAPP): explorar carpetas del teléfono elegidas con el selector de Android y reproducir sus archivos. */
object LocalMusic {
    private val AUDIO = setOf("flac", "mp3", "m4a", "aac", "wav", "ogg", "opus", "alac", "aiff", "aif", "wv", "ape", "dsf")

    class Entry(val name: String, val docId: String, val isDir: Boolean, val uri: Uri)

    fun isAudio(name: String) = name.substringAfterLast('.', "").lowercase() in AUDIO

    /** Lo que hay dentro de [docId] (o de la raíz del árbol si es null): carpetas primero, luego audio, por nombre. */
    fun list(context: Context, tree: Uri, docId: String?): List<Entry> {
        val parent = docId ?: DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val out = ArrayList<Entry>()
        context.contentResolver.query(children, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
        ), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0); val name = c.getString(1) ?: continue; val mime = c.getString(2).orEmpty()
                val dir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                if (dir || isAudio(name) || mime.startsWith("audio/")) out += Entry(name, id, dir, DocumentsContract.buildDocumentUriUsingTree(tree, id))
            }
        }
        return out.sortedWith(compareByDescending<Entry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    fun rootName(tree: Uri): String = Uri.decode(DocumentsContract.getTreeDocumentId(tree)).substringAfterLast('/').substringAfterLast(':').ifBlank { "Almacenamiento" }

    /** Id negativo y estable para un archivo local, para no chocar con los de Qobuz. */
    fun idFor(uri: Uri): Long = -((uri.toString().hashCode().toLong() and 0x7fffffffL) + 1L)

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Album, Track>>()

    /** La pista sin abrir el archivo: lo ya leído antes, o el nombre del archivo como título. Instantáneo. */
    fun quickItem(e: Entry, folder: String, index: Int): Pair<Album, Track> = cache[e.uri.toString()]?.let { it.first to it.second.copy(number = index + 1) }
        ?: (Album(id = "local:$folder", title = folder, artist = "", coverUrl = null, tracksCount = 0, year = "", quality = "", hiRes = false) to
            Track(id = idFor(e.uri), title = e.name.substringBeforeLast('.'), version = null, number = index + 1, disc = 1, artist = "", durationSec = 0, streamable = true, quality = "", hiRes = false))

    fun isRead(e: Entry) = cache.containsKey(e.uri.toString())

    /** Guarda la carátula incrustada en la caché (una por álbum) y devuelve su dirección, o null si el archivo no trae. */
    private fun saveCover(context: Context, key: String, picture: ByteArray?): String? {
        val f = java.io.File(java.io.File(context.cacheDir, "local_covers").apply { mkdirs() }, Integer.toHexString(key.hashCode()) + ".img")
        if (f.length() > 0) return Uri.fromFile(f).toString()
        if (picture == null || picture.isEmpty()) return null
        return runCatching { f.writeBytes(picture); Uri.fromFile(f).toString() }.getOrNull()
    }

    /** La pista con sus etiquetas (título, artista, álbum, duración, carátula); si no las tiene, el nombre del archivo. */
    fun toItem(context: Context, e: Entry, folder: String, index: Int): Pair<Album, Track> {
        cache[e.uri.toString()]?.let { return it.first to it.second.copy(number = index + 1) }
        var title = e.name.substringBeforeLast('.'); var artist = ""; var album = folder; var durSec = 0; var year = ""
        var bits = 0; var rate = 0; var cover: String? = null
        runCatching {
            MediaMetadataRetriever().apply {
                try {
                    setDataSource(context, e.uri)
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it }
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.let { year = it }
                    durSec = (extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L).div(1000).toInt()
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        bits = extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0
                        rate = extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
                    }
                    val key = "$folder|$album"
                    cover = saveCover(context, key, null) ?: saveCover(context, key, embeddedPicture)
                } finally { release() }
            }
        }
        val quality = if (bits > 0 && rate > 0) "$bits/${if (rate % 1000 == 0) rate / 1000 else rate / 1000.0}" else ""
        val hiRes = bits > 16 || rate > 48000
        val al = Album(id = "local:$folder", title = album, artist = artist, coverUrl = cover, tracksCount = 0, year = year, quality = quality, hiRes = hiRes)
        val tr = Track(id = idFor(e.uri), title = title, version = null, number = index + 1, disc = 1, artist = artist, durationSec = durSec,
            streamable = true, quality = quality, hiRes = hiRes)
        return (al to tr).also { cache[e.uri.toString()] = it }
    }
}
