package com.qbdlx.scanner

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class ScanParams(val apiKey: String, val allBySound: Boolean, val onlyCommon: Boolean, val omitted: List<String>, val trees: List<Uri>)

/**
 * Runs the scan outside the screen's life: it goes on with the screen off or the app in the background (the foreground
 * service in [ScanService] keeps the process alive), and the result is also written to the phone so a closed app does not lose it.
 */
object ScanRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val progress = MutableStateFlow<Progress?>(null)
    val songs = MutableStateFlow<List<Song>?>(null)
    val message = MutableStateFlow<String?>(null)
    /** True when an earlier scan was interrupted and its songs are kept: the next scan continues from them. */
    val resumable = MutableStateFlow(false)
    private var job: Job? = null

    private fun partialFile(c: Context) = File(c.filesDir, "scan-partial.json")

    val running get() = job?.isActive == true

    fun start(context: Context, p: ScanParams) {
        if (running) return
        val app = context.applicationContext
        message.value = null
        progress.value = Progress(0, 0, "")
        job = scope.launch {
            try {
                var files = Scanner.listAudio(app)
                files = files + Trees.list(app, p.trees)
                files = files.distinctBy { it.fileName + "|" + it.durationSec + "|" + it.folder }
                    .filterNot { OmittedFolders.matches(it.folder, p.omitted) || OmittedFolders.isRecording(it.folder) }
                    .filter { f -> !p.onlyCommon || f.uri.toString().startsWith("content://com.android.externalstorage") || OmittedFolders.isCommon(f.folder) }
                val known = partialFile(app).takeIf { it.exists() }?.let { SongLog.fromJson(it.readText()) }.orEmpty().associateBy { it.key() }
                val result = Scanner.identify(app, files, p.apiKey, p.allBySound, known, checkpoint = { list ->
                    runCatching { partialFile(app).writeText(SongLog.toJson(list)) }
                }) { progress.value = it }
                songs.value = result.songs
                partialFile(app).delete(); resumable.value = false
                message.value = result.acoustError?.let { "acoustid:$it" }
                runCatching { File(app.filesDir, "last-scan.json").writeText(SongLog.toJson(result.songs)) }
            } catch (e: CancellationException) {
                loadLast(app, force = true)
                throw e
            } catch (e: Throwable) {
                message.value = "error:${e.message ?: e.javaClass.simpleName}"
            } finally {
                progress.value = null
            }
        }
    }

    fun cancel() { job?.cancel() }

    /** Forgets an interrupted scan so the next one starts from zero. */
    fun discardPartial(context: Context) {
        partialFile(context).delete(); resumable.value = false; songs.value = null
        loadLast(context, force = true)
    }

    /** The last finished scan, if the phone still has it. */
    fun loadLast(context: Context, force: Boolean = false) {
        if (songs.value != null && !force) return
        val partial = partialFile(context).takeIf { it.exists() }?.let { SongLog.fromJson(it.readText()) }.orEmpty()
        resumable.value = partial.isNotEmpty()
        if (partial.isNotEmpty()) { songs.value = partial; return } // an interrupted scan: show what it had found
        val f = File(context.filesDir, "last-scan.json")
        if (f.exists()) songs.value = SongLog.fromJson(f.readText()).takeIf { it.isNotEmpty() }
    }
}
