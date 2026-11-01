package com.qbdlx.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** An audio file as the phone's media index knows it. */
class AudioFile(val uri: Uri, val fileName: String, val title: String?, val artist: String?, val album: String?, val durationSec: Int, val folder: String)

class ScanResult(val songs: List<Song>, val acoustError: String?)

class Progress(val done: Int, val total: Int, val current: String)

/** Identifies one file across scans, so a resumed scan can skip the ones already done. */
fun AudioFile.key() = "$folder|$fileName|$durationSec"
fun Song.key() = "$folder|$file|$durationSec"

object Scanner {
    /** Every song-length audio file the media index knows, on every volume (internal storage and the SD card), without ringtones, notifications and alarms. */
    fun listAudio(context: Context): List<AudioFile> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH,
        )
        val selection = "${MediaStore.Audio.Media.DURATION} >= 30000 AND ${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0"
        val out = ArrayList<AudioFile>()
        val volumes = runCatching { MediaStore.getExternalVolumeNames(context).toList() }.getOrDefault(emptyList()).ifEmpty { listOf(MediaStore.VOLUME_EXTERNAL) }
        for (volume in volumes) {
            val uri = MediaStore.Audio.Media.getContentUri(volume)
            runCatching {
                context.contentResolver.query(uri, projection, selection, null, "${MediaStore.Audio.Media.DISPLAY_NAME} COLLATE NOCASE")?.use { c ->
                    while (c.moveToNext()) {
                        out += AudioFile(
                            uri = ContentUris.withAppendedId(uri, c.getLong(0)),
                            fileName = c.getString(1).orEmpty(), title = c.getString(2), artist = c.getString(3), album = c.getString(4),
                            durationSec = (c.getLong(5) / 1000).toInt(), folder = c.getString(6).orEmpty(),
                        )
                    }
                }
            }
        }
        return out
    }

    /**
     * Turns the audio files into songs. Files with usable tags are taken as they are; the others are recognised by their
     * sound through AcoustID when [apiKey] is given (and all of them when [allBySound] is on).
     */
    suspend fun identify(
        context: Context, files: List<AudioFile>, apiKey: String, allBySound: Boolean,
        /** Songs from an interrupted scan: their files are not looked at again. */
        known: Map<String, Song> = emptyMap(),
        /** Called with everything found so far every few files and when the scan is stopped, so progress survives a closed app. */
        checkpoint: (List<Song>) -> Unit = {},
        onProgress: (Progress) -> Unit,
    ): ScanResult = withContext(Dispatchers.IO) {
        val songs = ArrayList<Song>()
        var acoustFailed = false
        var acoustError: String? = null
        try {
        files.forEachIndexed { i, f ->
            onProgress(Progress(i, files.size, f.fileName))
            ensureActive()
            known[f.key()]?.let { songs += it; return@forEachIndexed }
            if (songs.size % 10 == 9) checkpoint(songs.toList())
            val tagged = !Tags.needsFingerprint(f.title, f.artist)
            val bySound = (!tagged || allBySound) && apiKey.isNotBlank() && !acoustFailed
            var song: Song? = null
            if (bySound) {
                // One odd file (broken tags, a codec that fails) must not stop the scan: it just stays unrecognised.
                val raw = try { AudioDecoder.decode(context, f.uri)?.let { Chromaprint.fingerprint(it) } } catch (e: CancellationException) { throw e } catch (e: Throwable) { null }
                if (raw != null && raw.isNotEmpty()) {
                    try {
                        AcoustId.lookup(apiKey, f.durationSec, Chromaprint.encode(raw))?.let {
                            song = Song(it.title, it.artist, it.album, f.durationSec, f.fileName, Source.AcoustId, it.score, f.folder)
                        }
                    } catch (e: AcoustIdException) {
                        acoustFailed = true // a wrong key fails every file: stop asking
                        acoustError = e.message
                    } catch (e: java.io.IOException) {
                        // no connection for this one: fall back to the tags
                    }
                    delay(350) // AcoustID allows 3 requests per second
                }
            }
            songs += song ?: if (tagged) {
                Song(f.title.orEmpty().trim(), f.artist.orEmpty().trim(), f.album.orEmpty().takeUnless(Tags::isUnknown).orEmpty(), f.durationSec, f.fileName, Source.Tags, 1.0, f.folder)
            } else {
                Song(f.title?.takeUnless(Tags::isUnknown) ?: f.fileName.substringBeforeLast('.'), f.artist?.takeUnless(Tags::isUnknown).orEmpty(), "", f.durationSec, f.fileName, Source.Unknown, 0.0, f.folder)
            }
        }
        } finally { if (songs.isNotEmpty()) checkpoint(songs.toList()) }
        onProgress(Progress(files.size, files.size, ""))
        ScanResult(songs, acoustError)
    }
}
