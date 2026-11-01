package com.qbdlx.scanner

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ScanViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("scanner", 0)

    var apiKey by mutableStateOf(prefs.getString("acoustid_key", "").orEmpty()); private set
    var onlyCommon by mutableStateOf(prefs.getBoolean("only_common", true)); private set
    var allBySound by mutableStateOf(prefs.getBoolean("all_by_sound", false)); private set
    var progress by mutableStateOf<Progress?>(null); private set
    var songs by mutableStateOf<List<Song>?>(null); private set
    var message by mutableStateOf<String?>(null)
    var resumable by mutableStateOf(false); private set

    /** Extra folders picked with Android's folder picker (the SD card, for example). */
    val trees = androidx.compose.runtime.mutableStateListOf<String>().apply {
        addAll(prefs.getString("trees", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty())
    }

    init {
        ScanRunner.loadLast(app)
        viewModelScope.launch { ScanRunner.progress.collect { progress = it } }
        viewModelScope.launch { ScanRunner.songs.collect { songs = it } }
        viewModelScope.launch { ScanRunner.resumable.collect { resumable = it } }
        viewModelScope.launch { ScanRunner.message.collect { if (it != null || message?.startsWith("error") == true || message?.startsWith("acoustid") == true) message = it } }
    }

    fun addTree(uri: android.net.Uri) {
        runCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if (uri.toString() !in trees) { trees.add(uri.toString()); prefs.edit().putString("trees", trees.joinToString("\n")).apply() }
    }

    fun removeTree(uri: String) { trees.remove(uri); prefs.edit().putString("trees", trees.joinToString("\n")).apply() }

    /** The key as typed, until it is saved. */
    var keyDraft by mutableStateOf(apiKey)
    var keyStatus by mutableStateOf<String?>(null); private set
    var keyDetail by mutableStateOf(""); private set
    var checkingKey by mutableStateOf(false); private set

    /** Folders to skip: seeded with WhatsApp and Telegram the first time. */
    val omitted = androidx.compose.runtime.mutableStateListOf<String>().apply {
        addAll(prefs.getString("omitted", null)?.split('\n')?.filter { it.isNotBlank() } ?: OmittedFolders.defaults)
    }

    /** Saves the typed key and asks AcoustID whether it works. */
    fun saveKey() {
        apiKey = keyDraft.filter { !it.isWhitespace() && it != '"' && it != '\'' }; keyDraft = apiKey // pasted keys often carry spaces, line breaks or quotes
        prefs.edit().putString("acoustid_key", apiKey).apply()
        if (apiKey.isBlank()) { keyStatus = "empty"; return }
        keyStatus = "saved"
        checkingKey = true
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AcoustId.checkKey(apiKey) }
            keyDetail = "${result.detail} · ${apiKey.length} " + (if (apiKey.length == 1) "char" else "chars")
            keyStatus = when (result.verdict) { Verdict.Valid -> "valid"; Verdict.Invalid -> "invalid"; Verdict.NoConnection -> "saved-offline" }
            checkingKey = false
        }
    }

    fun editKey(k: String) { keyDraft = k; keyStatus = null }

    private fun saveOmitted() { prefs.edit().putString("omitted", omitted.joinToString("\n")).apply() }

    fun omitAdd(name: String) {
        val n = name.trim().trim('/')
        if (n.isBlank() || omitted.any { it.equals(n, ignoreCase = true) }) return
        omitted.add(n); saveOmitted()
        ScanRunner.songs.value = songs?.filterNot { OmittedFolders.matches(it.folder, omitted) } // drop what was already listed
    }

    fun omitRemove(name: String) { omitted.remove(name); saveOmitted() }
    fun updateOnlyCommon(on: Boolean) { onlyCommon = on; prefs.edit().putBoolean("only_common", on).apply() }
    fun updateAllBySound(on: Boolean) { allBySound = on; prefs.edit().putBoolean("all_by_sound", on).apply() }

    val scanning get() = progress != null

    fun scan() {
        if (ScanRunner.running) return
        message = null
        ScanRunner.start(
            getApplication(),
            ScanParams(apiKey, allBySound, onlyCommon, omitted.toList(), trees.map { android.net.Uri.parse(it) }),
        )
        runCatching { ScanService.start(getApplication()) }
    }

    var spotifyLink by mutableStateOf("")
    var spotify by mutableStateOf<SpotifyList?>(null); private set
    var spotifyBusy by mutableStateOf(false); private set
    /** "link", "private" (not public or not found), "network" or null. */
    var spotifyError by mutableStateOf<String?>(null); private set

    fun readSpotify() {
        val (kind, id) = SpotifyLists.parseLink(spotifyLink) ?: run { spotifyError = "link"; return }
        spotifyBusy = true; spotifyError = null; spotify = null
        viewModelScope.launch {
            val r = withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { Result.success(SpotifyLists.fetch(kind, id)) } catch (e: Exception) { Result.failure(e) }
            }
            r.onSuccess { if (it.songs.isEmpty()) spotifyError = "private" else spotify = it }
                .onFailure { spotifyError = if (it is IllegalArgumentException) "private" else "network" }
            spotifyBusy = false
        }
    }

    fun cancel() { ScanRunner.cancel() }

    fun startOver() = ScanRunner.discardPartial(getApplication())

    /** Writes the list to the cache and returns the intent that opens Android's share menu. */
    fun shareIntent(): Intent? = shareList(songs)

    /** The list read from a Spotify link, shared the same way. */
    fun shareSpotifyIntent(): Intent? = shareList(spotify?.songs, spotify?.name.orEmpty())

    private fun shareList(songs: List<Song>?, name: String = ""): Intent? {
        val list = songs?.takeIf { it.isNotEmpty() } ?: return null
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "qbdlx-songs.json").apply { writeText(SongLog.toJson(list, name = name)) }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "qbdlx-songs.json")
            clipData = android.content.ClipData.newRawUri("qbdlx-songs.json", uri) // makes the read grant reach the chosen app
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
