package com.qbdlx.mobile

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.text.Normalizer

/**
 * Lends Q Lyrics a short-lived, low-quality (MP3) copy of a song that is not on the phone, so it can listen to it
 * and check that the saved lyric is in sync. Only Q Lyrics may ask (checked by calling package); the Qobuz
 * session never leaves this app. The temp file is unlinked as soon as the descriptor is open, so nothing stays on disk.
 *
 * content://<applicationId>.lyricsaudio/track?title=..&artist=..&dur=<seconds>[&q=lossless]
 *
 * With q=lossless it lends the CD-quality FLAC instead (for analysis that suffers from MP3 artefacts); if Qobuz
 * does not give it, the call fails so the caller can ask again without q.
 */
class LyricsAudioProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri): String = if (uri.getQueryParameter("q") == "lossless") "audio/flac" else "audio/mpeg"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, v: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("sin contexto")
        val allowed = ctx.packageManager.getPackagesForUid(Binder.getCallingUid())?.contains("com.qbdlx.lyrics") == true
        if (!allowed) throw SecurityException("Solo Q Lyrics")
        val title = uri.getQueryParameter("title").orEmpty()
        val artist = uri.getQueryParameter("artist").orEmpty()
        val dur = uri.getQueryParameter("dur")?.toIntOrNull() ?: 0
        if (title.isBlank()) throw FileNotFoundException("sin título")

        val prefs = ctx.getSharedPreferences("qbdlx", 0)
        val userId = prefs.getString("user_id", null); val token = prefs.getString("token", null)
        val appId = prefs.getString("app_id", null); val secret = prefs.getString("app_secret", null)
        if (userId.isNullOrBlank() || token.isNullOrBlank() || appId.isNullOrBlank() || secret.isNullOrBlank())
            throw FileNotFoundException("QBDLX Mobile no tiene la sesión iniciada")

        val http = OkHttpClient()
        val api = QobuzApi(http).apply { this.appId = appId; appSecret = secret; authToken = token }
        val hit = try {
            api.searchTracks("$artist $title".trim(), 10).map { it.second }.firstOrNull { sameSong(it, title, artist, dur) }
        } catch (e: Exception) { throw FileNotFoundException("Búsqueda: ${e.message}") } ?: throw FileNotFoundException("No está en Qobuz")
        val lossless = uri.getQueryParameter("q") == "lossless"
        val file = try { api.getFileUrl(hit.id, if (lossless) Quality.LOSSLESS.formatId else Quality.MP3.formatId) } catch (e: Exception) { throw FileNotFoundException("Qobuz: ${e.message}") }

        val dir = File(ctx.cacheDir, "lyricsaudio").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        if (lossless && !file.mimeType.contains("flac", true)) throw FileNotFoundException("Qobuz no dio la versión sin pérdida")
        val tmp = File(dir, "${hit.id}.${if (lossless) "flac" else "mp3"}")
        try {
            http.newCall(Request.Builder().url(file.url).build()).execute().use { r ->
                if (!r.isSuccessful) throw FileNotFoundException("HTTP ${r.code}")
                val body = r.body ?: throw FileNotFoundException("vacío")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
            tmp.delete() // the open descriptor keeps it readable; nothing is left on disk
            return pfd
        } catch (e: FileNotFoundException) { tmp.delete(); throw e }
        catch (e: Exception) { tmp.delete(); throw FileNotFoundException("Descarga: ${e.message}") }
    }

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").filter { it.isLetterOrDigit() }

    private fun sameSong(t: Track, title: String, artist: String, dur: Int): Boolean {
        val a = norm(t.title); val b = norm(title)
        val titleOk = a.isNotEmpty() && b.isNotEmpty() && (a == b || a.contains(b) || b.contains(a))
        val art = norm(artist); val ta = norm(t.artist)
        val artistOk = art.isEmpty() || ta.contains(art) || art.contains(ta)
        val durOk = dur <= 0 || t.durationSec <= 0 || kotlin.math.abs(t.durationSec - dur) <= 5
        return titleOk && artistOk && durOk
    }
}
