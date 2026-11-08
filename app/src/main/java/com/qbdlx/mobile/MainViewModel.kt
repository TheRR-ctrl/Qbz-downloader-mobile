package com.qbdlx.mobile

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import android.net.Uri
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { Home, Search, Library, Downloads, Settings }

sealed interface DownloadRequest {
    /** Tracks already in hand, each with its album (album page, playlist page). */
    data class Tracks(val items: List<Pair<Album, Track>>) : DownloadRequest
    /** A whole album from a card; its tracks are fetched once a quality is picked. */
    data class WholeAlbum(val album: Album) : DownloadRequest
    data class WholePlaylist(val playlist: Playlist) : DownloadRequest
    /** "Download all" in the Library's Albums tab: every album is looked up once a quality is known. */
    data class Albums(val albums: List<Album>) : DownloadRequest
    /** "Download all" in the Library's Playlists tab. */
    data class Playlists(val playlists: List<Playlist>) : DownloadRequest
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("qbdlx", 0)
    private val api = QobuzApi()

    var loggedIn by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    /** A non-error message for the snackbar. */
    var notice by mutableStateOf<String?>(null)
    var userName by mutableStateOf("")
    var tab by mutableStateOf(Tab.Home)
    /** The text in the search box; set when an artist is opened from the player. */
    var searchQuery by mutableStateOf("")
    var webLogin by mutableStateOf(false); private set
    var signingIn by mutableStateOf(false); private set
    var homeLoading by mutableStateOf(false); private set

    var discover by mutableStateOf<Discover?>(null); private set
    var selectedTag by mutableStateOf<PlaylistTag?>(null); private set
    var tagPlaylists by mutableStateOf<List<Playlist>>(emptyList()); private set
    var playlistDetail by mutableStateOf<PlaylistDetail?>(null); private set
    var artistPage by mutableStateOf<ArtistPage?>(null); private set

    // Library; null until loaded.
    var myPlaylists by mutableStateOf<List<Playlist>?>(null); private set
    /** How the Library lists are ordered: 0 as added (Qobuz's order), 1 title, 2 artist, 3 newest, 4 oldest, 5 A-Z by length is not offered. */
    var wifiOnly by mutableStateOf(prefs.getBoolean("wifi_only", false)); private set
    fun updateWifiOnly(on: Boolean) { wifiOnly = on; prefs.edit().putBoolean("wifi_only", on).apply(); downloads.recheckAllowed() }
    private fun isOnline(): Boolean {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    private fun onWifi(): Boolean {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    }
    var librarySort by mutableIntStateOf(prefs.getInt("library_sort", 0)); private set
    fun updateLibrarySort(i: Int) { librarySort = i; prefs.edit().putInt("library_sort", i).apply() }
    var favAlbums by mutableStateOf<List<Album>?>(null); private set
    var favTracks by mutableStateOf<List<Pair<Album, Track>>?>(null); private set
    var favArtists by mutableStateOf<List<FavArtist>?>(null); private set
    var libraryLoading by mutableStateOf(false); private set

    /** App secret typed in Settings; overrides the one read from the web player when set. */
    // The manual App Secret field is gone; an old saved override must not keep silently winning over the real secret.
    init { prefs.edit().remove("custom_app_secret").apply() }
    val appId: String get() = api.appId
    var results by mutableStateOf<List<Album>>(emptyList()); private set
    /** Songs matching the last search, shown under the "Songs" tab of Search. */
    var trackResults by mutableStateOf<List<Pair<Album, Track>>>(emptyList()); private set
    var searchTab by mutableIntStateOf(0)
    var detail by mutableStateOf<AlbumDetail?>(null); private set

    var amoled by mutableStateOf(prefs.getBoolean("amoled", true)); private set
    var language by mutableStateOf(Language.fromCode(prefs.getString("language", null))); private set
    val strings: Strings get() = language.resolve()
    var quality by mutableStateOf(Quality.entries.firstOrNull { it.formatId == prefs.getInt("quality", 6) } ?: Quality.LOSSLESS)
        private set
    /** True when "Preguntar siempre" is chosen: every download shows the quality dialog. Otherwise [quality] is used straight away. */
    var askQuality by mutableStateOf(
        initialAskQuality(
            savedAsk = if (prefs.contains("ask_quality")) prefs.getBoolean("ask_quality", true) else null,
            hasSavedQuality = prefs.contains("quality"),
        ),
    )
        private set

    /** Simultaneous downloads (1, 2, 3, 5 or 10), chosen in Settings. */
    var maxConcurrent by mutableStateOf(prefs.getInt("max_concurrent", 2).takeIf { it in MAX_CONCURRENT_OPTIONS } ?: 2)
        private set
    /** Set after the first download is queued so the Activity can ask for notification permission once. */
    var askNotifications by mutableStateOf(false)

    // The folder the user picked for downloads (none = the default Music folder) and whether it can be used.
    val downloadFolder = DownloadFolder(app, prefs)
    val folderStatus = FolderStatus()
    /** A second folder, read only, whose songs are never downloaded again. */
    val knownFolder = KnownFolder(app, prefs)
    var knownName by mutableStateOf(knownFolder.name); private set
    var pickKnown by mutableStateOf(false)
    fun chooseKnownFolder() { pickKnown = true }
    fun onKnownPicked(uri: android.net.Uri) {
        runCatching { knownFolder.save(uri) }.onFailure { error = it.message; return }
        knownName = knownFolder.name; notice = strings.knownSaved
    }
    /** Groups of the same song found in more than one place (null = not scanned yet). */
    var duplicates by mutableStateOf<List<List<KnownSong>>?>(null)
    /** Addresses of the copies ticked for deletion. */
    var dupSelected by mutableStateOf<Set<String>>(emptySet())
    fun dupToggle(uri: String) { dupSelected = if (uri in dupSelected) dupSelected - uri else dupSelected + uri }
    fun deleteSelectedDuplicates() {
        val groups = duplicates ?: return
        val chosen = dupSelected
        viewModelScope.launch {
            var deleted = 0; var failed = 0
            val gone = HashSet<String>()
            withContext(Dispatchers.IO) {
                for (u in chosen) {
                    val uri = android.net.Uri.parse(u)
                    val ok = runCatching {
                        if (android.provider.DocumentsContract.isDocumentUri(context, uri)) { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
                        else context.contentResolver.delete(uri, null, null) > 0
                    }.getOrDefault(false)
                    if (ok) { deleted++; gone += u } else failed++
                }
            }
            downloads.forgetFiles(gone)
            duplicates = groups.map { g -> g.filter { it.uri !in gone } }.filter { it.size >= 2 }
            dupSelected = dupSelected - gone
            notice = strings.dupDeleted(deleted, failed)
        }
    }
    var duplicatesBusy by mutableStateOf(false); private set
    fun scanDuplicates() {
        if (duplicatesBusy) return
        duplicatesBusy = true
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                val mine = downloadFolder.treeUri?.let { walkTree(context, it, strings.dupDownloads, deletable = true) } ?: listMusicDownloads(context, strings.dupDownloads)
                Duplicates.find(mine + knownFolder.listAll(strings.dupOther))
            }
            duplicates = found; duplicatesBusy = false
            dupSelected = found.flatMap { Duplicates.defaultDeletions(it) }.mapNotNull { it.uri }.toSet()
        }
    }
    /** Shares the saved report of the last crash (or failed share), if any, as a text file. */
    val hasCrashReport: Boolean get() = java.io.File(context.filesDir, QbdlxApp.CRASH_FILE).exists()
    fun shareCrashReport() {
        val src = java.io.File(context.filesDir, QbdlxApp.CRASH_FILE).takeIf { it.exists() } ?: return
        val file = java.io.File(java.io.File(context.cacheDir, "exports").apply { mkdirs() }, "qbdlx-error.txt").apply { writeText(src.readText()) }
        val shared = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(android.content.Intent.EXTRA_STREAM, shared); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun clearKnownFolder() { knownFolder.clear(); knownName = null }
    var customFolder by mutableStateOf(downloadFolder.location); private set
    /** When on (the default), a track whose file is already in the download folder is not downloaded again. */
    var skipExisting by mutableStateOf(prefs.getBoolean("skip_existing", true)); private set
    /** Off by default. When on (and skipping is on), a better-quality version replaces the saved file. */
    var upgradeExisting by mutableStateOf(prefs.getBoolean("upgrade_existing", false)); private set
    /** Set by "Elegir carpeta"; the Activity opens the system folder picker and clears it. */
    var pickFolder by mutableStateOf(false)

    // The queue lives for the whole process (not this screen) and keeps going in DownloadService.
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val downloads = DownloadManager(
        worker = MediaStoreWorker(app, api, downloadFolder, skipExisting = { skipExisting }, upgradeExisting = { upgradeExisting }, onFolderUsable = { usable, reason -> onFolderUsable(usable, reason) }, alreadyKnown = { t -> knownFolder.songs().has(t.track.displayTitle, t.track.artist) }),
        store = FileTaskStore(java.io.File(app.filesDir, "download_queue.json")),
        scope = downloadScope,
        // What the current signature was made with: lets tasks that failed together share one refresh.
        allowed = { !wifiOnly || onWifi() },
        online = { isOnline() },
        secretVersion = { api.appId + ":" + QobuzApi.fingerprintOf(api.appSecret) },
        // Qobuz rejects the signature when the app secret doesn't match the app id.
        errorMessage = { e ->
            val msg = e.message ?: e.javaClass.simpleName
            if ("request_sig" in msg) strings.badSignature else msg
        },
    )
    var pendingRequest by mutableStateOf<DownloadRequest?>(null)
    /** A whole-library download waiting for a yes: with a fixed quality there is no quality dialog to act as one. */
    var pendingBulk by mutableStateOf<DownloadRequest?>(null)

    // Built on first use: creating it at startup slows down opening the app.
    private val engineDelegate = lazy {
        PlayerEngine(app, object : PlayerEngine.Listener {
            override fun onPlayingChanged(playing: Boolean) { isPlaying = playing; if (!playing) saveQueue(); syncNotification() }
            override fun onAdvanced() = advancedByEngine()
            override fun onEnded() { skipDir = 1; playQueue.next(auto = true)?.let { playCurrent() } }
            override fun onBitPerfect(state: BitPerfectState) { bitPerfectState = state }
            override fun onError(e: Exception) {
                // A prepared track's signed address can expire (a long pause near the end): load the track again, once.
                val item = playQueue.current
                if (item != null && errorRetriedFor !== item) { errorRetriedFor = item; playCurrent() }
                else error = e.message ?: e.javaClass.simpleName
            }
        }).also { it.fadeMs = crossfadeMs; it.bitPerfect = bitPerfect }
    }
    val engine: PlayerEngine by engineDelegate
    var nowPlaying by mutableStateOf<Pair<Album, Track>?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    /** What the playing file really is (from Qobuz's answer), e.g. "24 Bit / 96 kHz"; null until known. */
    var nowQuality by mutableStateOf<AudioQuality?>(null); private set

    private val playQueue = PlayQueue<Pair<Album, Track>>()
    var queueState by mutableStateOf(playQueue.state()); private set
    /** The full-screen player (Qobuz-style) is open. */
    var playerOpen by mutableStateOf(false)
    /** Fade between songs in milliseconds; 0 (the default) = no fade, only gapless. */
    var crossfadeMs by mutableIntStateOf(prefs.getInt("crossfade_ms", 0).takeIf { it in CROSSFADE_OPTIONS_MS } ?: 0); private set

    val savedAppId: String get() = prefs.getString("app_id", "").orEmpty()
    val savedAppSecret: String get() = prefs.getString("app_secret", "").orEmpty()

    init {
        QbdlxApp.downloads = downloads
        downloads.maxConcurrent = maxConcurrent
        // On a bad signature the queue renews the credentials with the same verified routine as the
        // "Actualizar credenciales Qobuz" button, then retries the track.
        downloads.refresher = { doRefreshCredentials() }
        downloads.onCredentialsRefreshed = { notice = strings.credsAutoUpdated }
        downloads.onActiveChanged = { active -> if (active) DownloadService.start(app) }
        downloadScope.launch { downloads.restore() }

        // Resume the previous session from the saved token, if there is one.
        val id = prefs.getString("user_id", null)
        val token = prefs.getString("token", null)
        if (!id.isNullOrBlank() && !token.isNullOrBlank() && savedAppId.isNotBlank() &&
            savedAppSecret.isNotBlank()
        ) {
            signingIn = true
            run { tokenLogin(savedAppId, savedAppSecret, id, token, "sesión restaurada (guardado)") }
        }
    }

    override fun onCleared() {
        if (engineDelegate.isInitialized()) engine.release()
    }

    /**
     * This ViewModel now lives as long as the process (so downloads outlive the screen). When the user
     * leaves the app for good, stop playback as it used to stop when the ViewModel was cleared.
     */
    fun onActivityFinished() {
        playerOpen = false // the music keeps playing: the notification has the controls
        webLogin = false
        showDiagnostics = false
        showIpod = false
        showImport = false
    }

    /**
     * Android only lets a foreground service start while the app is visible. If queued work became
     * active while the app was in the background (e.g. the session finished restoring), start it now.
     */
    fun onAppVisible() {
        if (downloads.isActive()) DownloadService.start(getApplication())
        // Notice a lost folder (or a returned one) as soon as the app opens, not only when a download starts.
        if (downloadFolder.treeUri != null) viewModelScope.launch(Dispatchers.IO) {
            when (val r = downloadFolder.resolve()) {
                is FolderResolution.Ready -> onFolderUsable(true, null)
                is FolderResolution.Unavailable -> onFolderUsable(false, r.reason)
                FolderResolution.Default -> Unit
            }
        }
    }

    /** Called by the downloader for every track; the log line and the banner appear once per problem, not per track. */
    private fun onFolderUsable(usable: Boolean, reason: String?) {
        if (!usable && !folderStatus.problem) {
            Diagnostics.log("[carpeta] la carpeta elegida no está disponible (${reason ?: "sin detalle"}); se usa Música/QobuzDownloaderX")
        }
        folderStatus.report(usable)
    }

    fun updateUpgradeExisting(on: Boolean) { upgradeExisting = on; prefs.edit().putBoolean("upgrade_existing", on).apply() }
    fun updateSkipExisting(on: Boolean) { skipExisting = on; prefs.edit().putBoolean("skip_existing", on).apply() }

    fun chooseFolder() { pickFolder = true }

    /** The system picker returned a folder: keep it only if we can really write there. */
    fun onFolderPicked(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { downloadFolder.save(uri) }
                customFolder = downloadFolder.location
                folderStatus.report(true)
                notice = strings.folderSaved
            } catch (e: Exception) {
                error = strings.folderNotWritable(e.message)
            }
        }
    }

    /** Back to Music/QobuzDownloaderX. */
    fun resetFolder() {
        downloadFolder.clear()
        customFolder = null
        folderStatus.report(true)
        notice = strings.folderResetDone
    }

    fun updateMaxConcurrent(n: Int) {
        if (n !in MAX_CONCURRENT_OPTIONS) return
        maxConcurrent = n
        prefs.edit().putInt("max_concurrent", n).apply()
        downloads.maxConcurrent = n
    }

    var showDiagnostics by mutableStateOf(false); private set

    // ---- iPod (experimental): see ipod/IpodDevice.kt ----
    private val context: Application get() = getApplication()
    // ---- Import a song list made by QBDLX Scanner -------------------------------------------------------------------
    var showImport by mutableStateOf(false); private set
    var importItems by mutableStateOf<List<ImportItem>?>(null); private set
    /** Songs looked up so far and in total, while the search runs. */
    var importProgress by mutableStateOf<Pair<Int, Int>?>(null); private set
    /** Indexes of [importItems] ticked for download. */
    val importTicked = mutableStateListOf<Int>()
    private var importJob: kotlinx.coroutines.Job? = null
    private var importRetryJob: kotlinx.coroutines.Job? = null
    /** Stops the Qobuz search of the imported list (what was found so far is kept in the memory for the next try). */
    fun importStop() { importJob?.cancel(); importRetryJob?.cancel(); importProgress = null; notice = strings.importStopped }

    fun openImport() { closeAlbum(); closePlaylist(); closeDiagnostics(); closeIpod(); showImport = true }
    /** Closing the import screen also closes "Sonando": every navigation path already calls this, and both are full-screen extras. */
    fun closeImport() { showImport = false; showListen = false }

    /** Reads the list the scanner shared (or the user picked) and looks every song up on Qobuz. */
    fun importList(uri: android.net.Uri) = importFrom {
        getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }

    /** The list as shared text (some apps hand over the content instead of a file). */
    fun importText(text: String) = importFrom { text }

    /** Looks the songs that are on the iPod up on Qobuz, to download them again, add them to favourites or to a playlist. */
    fun importFromIpod() {
        val songs = ipodTracks.filter { it.title.isNotBlank() }
        if (songs.isEmpty()) return
        val json = org.json.JSONObject().put("app", "qbdlx-scanner").put("version", 1).put("name", "iPod").put("songs", org.json.JSONArray().apply {
            songs.forEach { put(org.json.JSONObject().put("title", it.title).put("artist", it.artist).put("album", it.album).put("durationSec", 0).put("file", "ipod")) }
        }).toString()
        importFrom { json }
    }

    /** Adds the ticked songs to the user's favourite tracks in Qobuz. */
    fun importToFavorites() {
        val items = importItems ?: return
        val ids = importTicked.sorted().flatMap { i -> items.getOrNull(i)?.picks.orEmpty() }.map { it.second.id }
        if (ids.isEmpty()) return
        if (importBusy != null) return
        importBusy = "fav"
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.addFavoriteTracks(ids) }
                notice = strings.importFavoritesDone(ids.size)
            } catch (e: Exception) { error = strings.importPlaylistFailed(e.message.orEmpty()) }
            finally { importBusy = null }
        }
    }

    /** True when the song is in the user's Qobuz favourite tracks (as far as the library already loaded says). */
    fun isFavorite(trackId: Long): Boolean = favTracks?.any { it.second.id == trackId } == true

    /** The heart of the player: adds or removes the song in Qobuz, showing the change at once and undoing it if Qobuz refuses. */
    fun toggleFavorite(album: Album, track: Track) {
        val before = favTracks.orEmpty()
        val adding = before.none { it.second.id == track.id }
        favTracks = if (adding) listOf(album to track) + before else before.filter { it.second.id != track.id }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { if (adding) api.addFavoriteTracks(listOf(track.id)) else api.removeFavoriteTracks(listOf(track.id)) }
            } catch (e: Exception) {
                favTracks = before
                error = e.message
            }
        }
    }

    private fun importFrom(read: () -> String?) {
        openImport()
        importJob?.cancel()
        importJob = viewModelScope.launch {
            val songs = try {
                withContext(Dispatchers.IO) {
                    val text = read() ?: throw IllegalArgumentException("cannot open")
                    importListName = runCatching { org.json.JSONObject(text).optString("name") }.getOrDefault("")
                    SongImport.parse(text)
                }
            } catch (e: Exception) {
                error = strings.importBadFile; return@launch
            }
            if (songs.isEmpty()) { notice = strings.importEmpty; return@launch }
            importItems = null; importTicked.clear()
            importProgress = 0 to songs.size
            val gate = kotlinx.coroutines.sync.Semaphore(3)
            var done = 0
            val cache = loadImportCache()
            val items = songs.map { applyAliases(it) }.map { song ->
                async(Dispatchers.IO) {
                    val remembered = cache[ImportCache.key(song)]
                    if (remembered != null) { importProgress = ++done to songs.size; remembered.toItem(song) }
                    else gate.withPermit { searchSong(song).also { importProgress = ++done to songs.size } }
                }
            }.awaitAll()
            importItems = items
            items.forEach { importMemory[ImportCache.key(it.song)] = it }
            saveImportCache()
            items.forEachIndexed { i, it -> if (it.level == MatchLevel.Sure || (it.chosen != null && it.level != MatchLevel.Missing)) importTicked += i }
            importProgress = null
        }
    }

    /** Everything ever found for a wanted song, so the same list (or the same songs in another list) is not searched again. */
    private val importMemory = HashMap<String, ImportItem>()
    private var importMemoryLoaded = false
    private val importCacheFile get() = java.io.File(context.filesDir, "import-cache.json")
    private fun loadImportCache(): Map<String, ImportCache.StoredItem> {
        val stored = runCatching { ImportCache.fromJson(importCacheFile.readText()) }.getOrDefault(emptyMap())
        if (!importMemoryLoaded) { importMemoryLoaded = true; stored.forEach { (k, v) -> importMemory[k] = v.toItem(ImportedSong("", "", "", 0, "")) } }
        return stored
    }
    private var saveJob: kotlinx.coroutines.Job? = null
    /** Writes the memory shortly after the last change, so a burst of taps is one write. */
    private fun saveImportCache() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(1200)
            runCatching { importCacheFile.writeText(ImportCache.toJson(HashMap(importMemory))) }
        }
    }
    private fun rememberImport(i: Int) {
        val it = importItems?.getOrNull(i) ?: return
        importMemory[ImportCache.key(it.song)] = it
        saveImportCache()
    }
    /** Forgets what was found, so the next import searches everything again. */
    fun importForgetAll() { importMemory.clear(); importCacheFile.delete(); notice = strings.importForgotten }

    /** A search that is tried again when Qobuz answers with an error (too many requests in a row); null if every try failed. */
    private suspend fun searchRetry(query: String): List<Pair<Album, Track>>? {
        repeat(3) { n ->
            runCatching { api.searchTracks(query, 25) }.onSuccess { return it }
            kotlinx.coroutines.delay(700L * (n + 1))
        }
        return null
    }

    /** Looks one wanted song up, trying cleaned-up variants when the plain search does not settle it. */
    private suspend fun searchSong(song: ImportedSong): ImportItem {
        var failed = false
        val first = searchRetry(SongImport.query(song))
        if (first == null) failed = true
        val found = first.orEmpty().ifEmpty { if (song.artist.isNotBlank()) searchRetry(song.title).also { if (it == null) failed = true }.orEmpty() else emptyList() }
        var best = SongImport.rank(song, found)
        if (best.level != MatchLevel.Sure && !failed) for (v in SongImport.variants(song).take(6)) {
            val more = searchRetry(SongImport.query(v))
            if (more == null) { failed = true; break }
            val r = SongImport.rank(v, more)
            if (r.level.ordinal < best.level.ordinal || (best.candidates.isEmpty() && r.candidates.isNotEmpty())) best = ImportItem(song, r.candidates, r.level, r.chosen)
            if (best.level == MatchLevel.Sure) break
        }
        return if (failed && best.level == MatchLevel.Missing && best.candidates.isEmpty()) ImportItem(song, emptyList(), MatchLevel.Missing, null, emptySet(), searchFailed = true) else best
    }

    /** Searches again for the songs that were not found, most of all those where Qobuz had refused to answer. */
    fun importRetryMissing() {
        val items = importItems ?: return
        val todo = items.withIndex().filter { it.value.level == MatchLevel.Missing && it.value.candidates.isEmpty() }
        if (todo.isEmpty() || importProgress != null) return
        importRetryJob = viewModelScope.launch {
            importProgress = 0 to todo.size
            val gate = kotlinx.coroutines.sync.Semaphore(2)
            var done = 0
            val results = todo.map { (i, it) -> async(Dispatchers.IO) { gate.withPermit { i to searchSong(it.song) }.also { importProgress = ++done to todo.size } } }.awaitAll()
            val now = (importItems ?: items).toMutableList()
            for ((i, r) in results) { now[i] = r; if (r.level == MatchLevel.Sure) importTicked.add(i) }
            importItems = now
            results.forEach { (i, _) -> rememberImport(i) }
            importProgress = null
        }
    }

    /** Corrections the user taught: a wrong spelling (normalized) and what it should be, e.g. "chaggy" -> "Shaggy". */
    private fun aliases(): MutableMap<String, String> {
        val j = runCatching { org.json.JSONObject(prefs.getString("import_aliases", "{}").orEmpty()) }.getOrDefault(org.json.JSONObject())
        return j.keys().asSequence().associateWith { j.getString(it) }.toMutableMap()
    }
    private fun learnAlias(wrong: String, right: String) {
        val key = SongImport.normalize(wrong)
        if (key.isBlank() || right.isBlank() || key == SongImport.normalize(right)) return
        val m = aliases().also { it[key] = right.trim() }
        prefs.edit().putString("import_aliases", org.json.JSONObject(m as Map<*, *>).toString()).apply()
    }
    private fun applyAliases(s: ImportedSong): ImportedSong {
        val m = aliases(); if (m.isEmpty()) return s
        return s.copy(title = m[SongImport.normalize(s.title)] ?: s.title, artist = m[SongImport.normalize(s.artist)] ?: s.artist)
    }

    /** Searches again for one song with the artist and title the user typed, and remembers the correction. */
    fun importResearch(i: Int, artist: String, title: String) {
        val items = importItems ?: return
        val old = items.getOrNull(i) ?: return
        learnAlias(old.song.artist, artist); learnAlias(old.song.title, title)
        val song = old.song.copy(title = title.trim(), artist = artist.trim())
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { api.searchTracks(SongImport.query(song), 25) }.getOrDefault(emptyList()) }
            val r = SongImport.rank(song, found)
            // The user looked, so whatever was found is theirs to choose from: never "missing" while there are results.
            val level = if (r.candidates.isEmpty()) MatchLevel.Missing else if (r.level == MatchLevel.Missing) MatchLevel.Check else r.level
            val now = importItems ?: return@launch
            importItems = now.toMutableList().also { l -> l[i] = ImportItem(song, r.candidates, level, if (level == MatchLevel.Sure) 0 else null) }
            if (level == MatchLevel.Sure) importTicked.add(i)
            rememberImport(i)
            if (r.candidates.isEmpty()) notice = strings.importNothingFound
        }
    }

    fun importToggle(i: Int) { if (i in importTicked) importTicked.remove(i) else importTicked.add(i) }

    /** Picks which Qobuz result to download for a song that needed checking, and ticks it. */
    fun importChoose(i: Int, candidate: Int) {
        val items = importItems ?: return
        val it = items.getOrNull(i) ?: return
        // A tap toggles that version: none, one or several can be taken for one wanted song.
        val now = (listOfNotNull(it.chosen) + it.also).distinct().toMutableList()
        if (candidate in now) now.remove(candidate) else now.add(candidate)
        importItems = items.toMutableList().also { l -> l[i] = ImportItem(it.song, it.candidates, it.level, now.firstOrNull(), now.drop(1).toSet()) }
        if (now.isEmpty()) importTicked.remove(i) else importTicked.add(i)
        rememberImport(i)
    }

    /** The name the scanner gave the list (a Spotify playlist's title), used for the Qobuz playlist. */
    var importListName by mutableStateOf("")

    /** Puts the ticked songs in a new private Qobuz playlist. */
    /** Which import button is working now ("playlist", "fav", "plreplace", "replace"), so it can show a spinner. */
    var importBusy by mutableStateOf<String?>(null); private set

    fun importToPlaylist() {
        val items = importItems ?: return
        val ids = importTicked.sorted().flatMap { i -> items.getOrNull(i)?.picks.orEmpty() }.map { it.second.id }
        if (ids.isEmpty()) return
        val name = importListName.ifBlank { "QBDLX " + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date()) }
        if (importBusy != null) return
        importBusy = "playlist"
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.createPlaylist(name, ids) }
                notice = strings.importPlaylistDone(name, ids.size)
            } catch (e: Exception) { error = strings.importPlaylistFailed(e.message.orEmpty()) }
            finally { importBusy = null }
        }
    }

    /** For every doubtful song still without a choice, takes Qobuz's most probable result (the list is sorted by fit) and ticks it. */
    fun importChooseBest() {
        val items = importItems ?: return
        importItems = items.map { if (it.level == MatchLevel.Check && it.chosen == null && it.candidates.isNotEmpty()) ImportItem(it.song, it.candidates, it.level, 0) else it }
        importItems!!.forEachIndexed { i, it -> if (it.level == MatchLevel.Check && it.chosen != null) { importTicked += i; rememberImport(i) } }
    }

    /** Sections of the import list that are folded away. */
    var importCollapsed by mutableStateOf<Set<MatchLevel>>(emptySet()); private set
    fun importToggleSection(level: MatchLevel) { importCollapsed = if (level in importCollapsed) importCollapsed - level else importCollapsed + level }

    fun importDownload() {
        val items = importItems ?: return
        val picked = importTicked.sorted().flatMap { i -> items.getOrNull(i)?.picks.orEmpty() }
        if (picked.isEmpty()) return
        requestDownload(DownloadRequest.Tracks(picked))
    }

    var confirmIpodClear by mutableStateOf(false)
    var ipodPlaylistPicker by mutableStateOf(false)
    /** A Qobuz playlist waiting for its songs to finish arriving on the iPod: (name, track keys in order). */
    private var playlistAfterSend: List<Pair<String, List<String>>>? = null
    var ipodClearing by mutableStateOf(false); private set
    /** What the iPod is doing right now when there is no exact percentage (clear, delete, fix covers): shown as a bar without a number. */
    var ipodTask by mutableStateOf<String?>(null); private set
    /** True once the person pressed Stop: the running iPod job ends at the next safe point (never in the middle of writing the library). */
    var ipodStopping by mutableStateOf(false); private set
    fun ipodStop() { ipodStopping = true; aiJob?.cancel() }

    // ---- AI mixes for the iPod (see AiMix.kt): the AI proposes, the code validates, and with no key/answer local mixes are made ----
    var aiKey by mutableStateOf(prefs.getString("ai_key", "") ?: ""); private set
    var aiMixDialog by mutableStateOf(false)
    var aiKeyDialog by mutableStateOf(false)
    var aiKeyTesting by mutableStateOf(false); private set
    var aiKeyError by mutableStateOf<String?>(null); private set
    private var aiJob: kotlinx.coroutines.Job? = null

    /** Tests the key against Google and saves it only if it works. The key stays in the app's private storage, never in the repo or the APK. */
    fun aiSaveKey(raw: String) {
        val key = raw.trim()
        if (key.isBlank() || aiKeyTesting) return
        aiKeyTesting = true; aiKeyError = null
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { AiMix.testKey(api.http, key) }
            aiKeyTesting = false
            if (err == null) { aiKey = key; prefs.edit().putString("ai_key", key).apply(); aiKeyDialog = false }
            else aiKeyError = strings.aiKeyProblem(err.kind.name)
        }
    }
    fun aiForgetKey() { aiKey = ""; prefs.edit().remove("ai_key").apply() }

    var aiIdeas by mutableStateOf<List<String>>(emptyList()); private set
    var aiIdeasBusy by mutableStateOf(false); private set
    var aiIdeasNote by mutableStateOf<String?>(null); private set
    private var aiIdeasJob: kotlinx.coroutines.Job? = null
    fun aiIdeasStop() { aiIdeasJob?.cancel() }
    /** Asks the AI for a few mix ideas from a sample of the library. Only proposes: nothing is written until "Crear" is pressed. Falls back to ideas from the library itself. */
    fun aiSuggest() {
        if (aiIdeasBusy) return
        val tracks = ipodTracks.filter { it.title.isNotBlank() }
        if (tracks.size < AiMix.MIN_MIX) { aiIdeasNote = strings.aiMixTooFew; return }
        aiIdeasBusy = true; aiIdeasNote = null
        aiIdeasJob = viewModelScope.launch {
            val songs = tracks.shuffled().take(AiMix.MAX_SONGS_SENT / 2).mapIndexed { i, t -> MixSong(i + 1, t.title, t.artist, t.album) }
            try {
                var ideas: List<String> = emptyList()
                if (aiKey.isBlank()) aiIdeasNote = strings.aiMixNoKey
                else {
                    ideas = try { AiMix.validateIdeas(withContext(Dispatchers.IO) { AiMix.askIdeas(api.http, aiKey, AiMix.catalog(songs)) }) }
                    catch (e: AiMix.AiError) { aiIdeasNote = aiProblem(e); emptyList() }
                    if (ideas.isEmpty() && aiIdeasNote == null) aiIdeasNote = strings.aiMixBadAnswer
                }
                if (ideas.isEmpty()) ideas = AiMix.localIdeas(songs).also { aiIdeasNote = (aiIdeasNote?.plus(" ") ?: "") + strings.aiIdeasLocal }
                aiIdeas = ideas
            } catch (e: kotlinx.coroutines.CancellationException) {
                aiIdeasNote = strings.aiIdeasStopped
            } finally { aiIdeasBusy = false; aiIdeasJob = null }
        }
    }
    class AiPreviewMix(val name: String, val songs: List<MixSong>)
    var aiPreview by mutableStateOf<List<AiPreviewMix>?>(null); private set
    var aiPreviewNote by mutableStateOf<String?>(null); private set
    var aiGenerating by mutableStateOf(false); private set
    private var aiLast: Triple<List<String>, Int, com.qbdlx.mobile.ipod.IpodTrack?>? = null
    fun aiPreviewClose() { aiPreview = null; aiPreviewNote = null; aiMixDialog = false }
    fun aiGenerateStop() { aiJob?.cancel() }
    fun aiRegenerate() { aiLast?.let { aiGenerate(it.first, it.second, it.third) } }
    private fun aiProblem(e: AiMix.AiError) = strings.aiKeyProblem(e.kind.name) + if (e.kind == AiMix.AiError.Kind.Bad) " (${e.message})" else ""

    /**
     * AI mix step 1: the AI (or, failing that, the phone) proposes mixes from the iPod's songs, one per request, optionally
     * starting from a seed song. Nothing is written to the iPod: the result is a preview the user can keep, drop or regenerate.
     */
    fun aiGenerate(requests: List<String>, size: Int, seed: com.qbdlx.mobile.ipod.IpodTrack?) {
        if (aiGenerating) return
        val tracks = ipodTracks.filter { it.title.isNotBlank() }
        if (tracks.size < AiMix.MIN_MIX) { aiPreviewNote = strings.aiMixTooFew; return }
        aiLast = Triple(requests, size, seed)
        aiGenerating = true; aiPreviewNote = null
        aiJob = viewModelScope.launch {
            try {
                val seedSong = seed?.takeIf { s -> tracks.any { it.pid == s.pid } }
                val rest = tracks.filter { it.pid != seedSong?.pid }.shuffled().take(AiMix.MAX_SONGS_SENT - (if (seedSong != null) 1 else 0))
                val ordered = listOfNotNull(seedSong) + rest
                val songs = ordered.mapIndexed { i, t -> MixSong(i + 1, t.title, t.artist, t.album) }
                val first = if (seedSong != null) songs.first() else null
                val wanted = if (requests.isEmpty()) 3 else requests.size.coerceIn(1, AiMix.MAX_MIXES)
                var usedAi = true; var note: String? = null
                var mixes: List<Mix> = emptyList()
                if (aiKey.isBlank()) { usedAi = false; note = strings.aiMixNoKey }
                else {
                    val seedLine = first?.let { "${it.n}|${it.title}|${it.artist}|${it.album}" }
                    val reqs = if (requests.isEmpty() && first != null) listOf("Música parecida a la canción de partida") else requests
                    mixes = try {
                        val json = withContext(Dispatchers.IO) { AiMix.ask(api.http, aiKey, reqs, AiMix.catalog(songs), size, seedLine) }
                        AiMix.validate(json, songs.map { it.n }.toSet(), wanted, first?.n, size)
                    } catch (e: AiMix.AiError) { note = aiProblem(e); emptyList() }
                    if (mixes.isEmpty()) { usedAi = false; if (note == null) note = strings.aiMixBadAnswer }
                }
                if (mixes.isEmpty()) mixes = AiMix.localMixes(songs, wanted, System.nanoTime(), size, first)
                val byN = songs.associateBy { it.n }
                aiPreview = mixes.map { m -> AiPreviewMix(m.name, m.ns.mapNotNull { byN[it] }) }
                aiPreviewNote = listOfNotNull(note, if (usedAi) strings.aiMixByAi else strings.aiMixLocal).joinToString(" ")
            } catch (e: kotlinx.coroutines.CancellationException) {
                aiPreviewNote = strings.aiIdeasStopped
            } finally { aiGenerating = false; aiJob = null }
        }
    }

    /** AI mix step 2: writes the chosen previews to the iPod as playlists (progress and Stop like every iPod task). */
    fun aiSave(chosen: Set<Int>) {
        val uri = ipodUri ?: return
        val list = aiPreview ?: return
        if (ipodProgress != null || ipodLyricsStatus != null || ipodTask != null) { aiPreviewNote = strings.ipodBusy; return }
        val guid = ipodGuid()
        if (guid == null) { aiPreviewNote = strings.aiSaveNeedId; ipodReadUsbGuid(); return }
        val picked = list.filterIndexed { i, _ -> i in chosen }
        if (picked.isEmpty()) return
        aiPreview = null; aiMixDialog = false; ipodMessage = null; ipodStopping = false
        aiJob = viewModelScope.launch {
            try {
                ipodLyricsStatus = strings.aiMixAsking; ipodLyricsProgress = 0 to picked.size
                val msgs = ArrayList<String>()
                for ((i, m) in picked.withIndex()) {
                    if (ipodStopping) break
                    ipodLyricsStatus = strings.ipodPlaylistMaking(m.name, i, picked.size)
                    ipodLyricsProgress = i to picked.size
                    val keys = m.songs.map { com.qbdlx.mobile.ipod.trackKey(it.title, it.artist, it.album) }
                    val r = withContext(Dispatchers.IO) { com.qbdlx.mobile.ipod.IpodDevice(context, uri).makePlaylist(m.name, keys, guid) }
                    msgs.add(strings.ipodPlaylistDone(m.name, r.added, r.missing, r.error))
                }
                ipodMessage = msgs.joinToString(" ")
                if (msgs.isNotEmpty()) ipodDone++
            } catch (e: kotlinx.coroutines.CancellationException) {
                ipodMessage = strings.ipodPlaylistDownloadStopped
            } finally {
                ipodLyricsStatus = null; ipodLyricsProgress = null; ipodStopping = false; aiJob = null
                ipodRefresh()
            }
        }
    }
    /** (done, total) of the lyrics job, for a real progress bar. */
    var ipodLyricsProgress by mutableStateOf<Pair<Int, Int>?>(null); private set

    /** Deletes EVERY track on the iPod (library entries and files). A backup of the library is made first. */
    fun ipodClearAll() {
        confirmIpodClear = false
        val uri = ipodUri
        if (uri == null) { error = strings.importNoIpod; return }
        if (ipodClearing || ipodProgress != null) return
        val pids = ipodTracks.map { it.pid }
        if (pids.isEmpty()) return
        val guid = ipodGuid()
        if (guid == null) { ipodReadUsbGuid(); return }
        ipodClearing = true; ipodMessage = null; ipodTask = strings.ipodTaskDeleting(pids.size)
        viewModelScope.launch {
            val (n, err) = withContext(Dispatchers.IO) { com.qbdlx.mobile.ipod.IpodDevice(context, uri).removeTracks(pids, guid) }
            ipodClearing = false; ipodTask = null
            ipodMessage = if (err != null) strings.importDeleteFailed(err) else strings.importDeleteDone(n)
            ipodRefresh()
        }
    }

    /** Asks "are you sure?" before [importDeleteFromIpod]. */
    var confirmIpodDelete by mutableStateOf(false)

    /** The iPod copies of the ticked songs (found by title, artist and album as the iPod has them). */
    val importIpodCopies: List<Long> get() {
        val items = importItems ?: return emptyList()
        val onIpod = ipodTracks.groupBy { Triple(it.title, it.artist, it.album) }
        return importTicked.sorted().flatMap { i -> items.getOrNull(i)?.takeIf { it.picks.isNotEmpty() }?.let { onIpod[Triple(it.song.title, it.song.artist, it.song.album)].orEmpty().map { t -> t.pid } }.orEmpty() }.distinct()
    }

    /** Deletes from the iPod every ticked song that was found on Qobuz (their files too). A backup of the library is made first. */
    fun importDeleteFromIpod() {
        confirmIpodDelete = false
        val uri = ipodUri
        if (uri == null) { error = strings.importNoIpod; return }
        val pids = importIpodCopies
        if (pids.isEmpty() || importBusy != null || ipodProgress != null) return
        val guid = ipodGuid()
        if (guid == null) { ipodReadUsbGuid(); return }
        importBusy = "delete"
        viewModelScope.launch {
            val (n, err) = withContext(Dispatchers.IO) { com.qbdlx.mobile.ipod.IpodDevice(context, uri).removeTracks(pids, guid) }
            importBusy = null
            if (err != null) error = strings.importDeleteFailed(err) else notice = strings.importDeleteDone(n)
            ipodRefresh()
        }
    }

    /** Whether the imported list is the iPod's own, so "download and replace on the iPod" makes sense. */
    val importIsIpod: Boolean get() = importListName == "iPod"

    /** Saves the ticked songs as a private Qobuz playlist first, then downloads them as MP3 and replaces them on the iPod. */
    fun importPlaylistThenReplace() {
        val items = importItems ?: return
        val ids = importTicked.sorted().flatMap { i -> items.getOrNull(i)?.picks.orEmpty() }.map { it.second.id }
        if (ids.isEmpty()) return
        if (ipodUri == null) { error = strings.importNoIpod; return }
        val name = "iPod " + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())
        if (importBusy != null) return
        importBusy = "plreplace"
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.createPlaylist(name, ids) }
            } catch (e: Exception) { error = strings.importPlaylistFailed(e.message.orEmpty()); importBusy = null; return@launch } // nothing is downloaded or deleted if the playlist failed
            importReplaceOnIpod(keepBusy = "plreplace")
        }
    }

    /** Downloads the ticked songs as MP3, then, when they are all finished, sends them to the iPod in place of the old copies. */
    fun importReplaceOnIpod(keepBusy: String = "replace") {
        val items = importItems ?: return
        if (ipodUri == null) { error = strings.importNoIpod; return }
        val picked = importTicked.sorted().flatMap { i -> items.getOrNull(i)?.picks.orEmpty() }
        if (picked.isEmpty()) return
        val ids = picked.map { it.second.id }.toSet()
        // The copy on the iPod each chosen song came from (found by its own title, artist and album) is the one to delete.
        val onIpod = ipodTracks.groupBy { Triple(it.title, it.artist, it.album) }
        val originals = HashMap<Long, List<Long>>()
        importTicked.forEach { i -> items.getOrNull(i)?.let { it -> val pids = onIpod[Triple(it.song.title, it.song.artist, it.song.album)].orEmpty().map { t -> t.pid }; it.picks.forEach { p -> originals[p.second.id] = pids } } }
        if (importBusy != null && importBusy != keepBusy) return
        importBusy = keepBusy
        ipodReplace = true
        startDownload(DownloadRequest.Tracks(picked), Quality.MP3)
        notice = strings.importReplaceStarted
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            while (downloads.tasks.any { it.track.id in ids && (it.status == TaskStatus.PENDING || it.status == TaskStatus.DOWNLOADING) }) kotlinx.coroutines.delay(2000)
            importBusy = null
            val done = downloads.tasks.filter { it.track.id in ids && it.status == TaskStatus.COMPLETED && it.quality == Quality.MP3 && it.savedUri != null }
                .distinctBy { ipodKey(it) }
            if (done.isNotEmpty()) ipodSend(done, originals) else notice = strings.importReplaceNothing
        }
    }

    var showIpod by mutableStateOf(false); private set
    fun openIpod() { showIpod = true; ipodRefresh() }
    fun closeIpod() { showIpod = false }
    /** Set by "Elegir el iPod"; the Activity opens the folder picker and clears it. */
    var pickIpod by mutableStateOf(false)
    fun chooseIpod() { pickIpod = true }
    var ipodUri by mutableStateOf(prefs.getString("ipod_tree_uri", null)?.let(android.net.Uri::parse)); private set
    var ipodInspection by mutableStateOf<com.qbdlx.mobile.ipod.IpodDevice.Inspection?>(null); private set
    var ipodOnDevice by mutableStateOf<Set<String>>(emptySet()); private set
    var ipodSongCount by mutableIntStateOf(-1); private set
    /** What is on the iPod now, for the "On the iPod" tab. */
    var ipodTracks by mutableStateOf<List<com.qbdlx.mobile.ipod.IpodTrack>>(emptyList()); private set
    var ipodTab by mutableIntStateOf(0)
    var ipodQuery by mutableStateOf("")
    var ipodSelectedPids by mutableStateOf<Set<Long>>(emptySet())
    var confirmIpodDeleteSelected by mutableStateOf(false)
    fun ipodToggleTrack(pid: Long) { ipodSelectedPids = if (pid in ipodSelectedPids) ipodSelectedPids - pid else ipodSelectedPids + pid }
    fun ipodSelectVisible(pids: List<Long>, selected: Boolean) { ipodSelectedPids = if (selected) ipodSelectedPids + pids else ipodSelectedPids - pids.toSet() }
    /** Playlists ticked to be removed together with their songs. */
    var ipodSelectedPlaylists by mutableStateOf<Set<Long>>(emptySet()); private set
    fun ipodTogglePlaylist(pl: com.qbdlx.mobile.ipod.IpodPlaylist) {
        if (pl.pid in ipodSelectedPlaylists) { ipodSelectedPlaylists = ipodSelectedPlaylists - pl.pid; ipodSelectedPids = ipodSelectedPids - pl.trackPids.toSet() }
        else { ipodSelectedPlaylists = ipodSelectedPlaylists + pl.pid; ipodSelectedPids = ipodSelectedPids + pl.trackPids }
    }
    fun ipodDeleteSelected() {
        confirmIpodDeleteSelected = false
        val uri = ipodUri ?: return
        val pids = ipodSelectedPids.toList()
        val lists = ipodSelectedPlaylists.toList()
        if ((pids.isEmpty() && lists.isEmpty()) || ipodClearing || ipodProgress != null) return
        val guid = ipodGuid()
        if (guid == null) { ipodReadUsbGuid(); return }
        ipodClearing = true; ipodMessage = null; ipodTask = strings.ipodTaskDeleting(pids.size)
        viewModelScope.launch {
            val (n, err) = withContext(Dispatchers.IO) { com.qbdlx.mobile.ipod.IpodDevice(context, uri).removeTracks(pids, guid, lists) }
            ipodClearing = false; ipodTask = null
            ipodSelectedPids = emptySet(); ipodSelectedPlaylists = emptySet()
            ipodMessage = if (err != null) strings.importDeleteFailed(err) else strings.importDeleteDone(n)
            ipodRefresh()
        }
    }
    var ipodChecking by mutableStateOf(false); private set
    var ipodGuidText by mutableStateOf(prefs.getString("ipod_guid", "").orEmpty())
    var ipodProgress by mutableStateOf<Pair<Int, Int>?>(null); private set
    private var ipodMessageState by mutableStateOf<String?>(null)
    /** The result of the last iPod action: shown in a card under the tabs and also as a floating message. */
    var ipodMessage: String?
        get() = ipodMessageState
        set(v) { ipodMessageState = v; if (v != null) notice = v }

    /** The GUID to sign with: the one read from the iPod, or the one typed by the user. */
    private fun ipodGuid(): ByteArray? = ipodInspection?.guid ?: com.qbdlx.mobile.ipod.IpodSysInfo.fromUserInput(ipodGuidText)
    val ipodGuidKnown: Boolean get() = ipodGuid() != null

    /**
     * Shares the list of downloaded and favorite songs with Q Lyrics (a small JSON file, "title/artist/album/seconds"), which downloads
     * their lyrics ahead of time. No account data goes in it: only song names.
     */
    fun sendLyricsList() {
        viewModelScope.launch {
            if (favTracks == null) { loadLibrary(); var n = 0; while (libraryLoading && n++ < 100) kotlinx.coroutines.delay(300) }
            val rows = LinkedHashMap<String, org.json.JSONObject>()
            fun add(title: String, artist: String, album: String, sec: Int) {
                if (title.isBlank()) return
                rows.getOrPut("${title.lowercase()}|${artist.lowercase()}") {
                    org.json.JSONObject().put("t", title).put("a", artist).put("al", album).put("d", sec)
                }
            }
            downloads.tasks.filter { it.status == TaskStatus.COMPLETED }.forEach { add(it.track.displayTitle, it.track.artist, it.album.title, it.track.durationSec) }
            favTracks.orEmpty().forEach { (al, t) -> add(t.displayTitle, t.artist, al.title, t.durationSec) }
            // The songs of every playlist and favorite album: one request each, six at a time.
            notice = strings.lyricsListGathering
            val gate = kotlinx.coroutines.sync.Semaphore(6)
            val extra = withContext(Dispatchers.IO) {
                val pl = myPlaylists.orEmpty().map { p -> async { gate.withPermit { runCatching { api.getPlaylist(p.id).tracks.map { (al, t) -> Triple(al, t, null as Unit?) } }.getOrDefault(emptyList()) } } }
                val ab = favAlbums.orEmpty().map { a -> async { gate.withPermit { runCatching { api.getAlbum(a.id).let { d -> d.tracks.map { t -> Triple(d.album, t, null as Unit?) } } }.getOrDefault(emptyList()) } } }
                (pl + ab).awaitAll().flatten()
            }
            extra.forEach { (al, t, _) -> add(t.displayTitle, t.artist.ifBlank { al.artist }, al.title, t.durationSec) }
            if (rows.isEmpty()) { notice = strings.lyricsListEmpty; return@launch }
            val file = withContext(Dispatchers.IO) {
                java.io.File(context.cacheDir, "exports").apply { mkdirs() }.resolve("lyrics-list.json")
                    .also { it.writeText(org.json.JSONObject().put("tracks", org.json.JSONArray(rows.values.toList())).toString()) }
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/json").setPackage("com.qbdlx.lyrics")
                .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(send) } catch (e: android.content.ActivityNotFoundException) { notice = strings.lyricsListMissingApp }
        }
    }

    /** Finished MP3 downloads, one per song, that can be sent. */
    val ipodCandidates: List<DownloadTask>
        get() = downloads.tasks.filter { it.status == TaskStatus.COMPLETED && it.quality == Quality.MP3 && it.savedUri != null }
            .distinctBy { com.qbdlx.mobile.ipod.trackKey(it.track.displayTitle, it.track.artist, it.album.title) }

    private fun ipodKey(t: DownloadTask) = com.qbdlx.mobile.ipod.trackKey(t.track.displayTitle, t.track.artist, t.album.title)
    val ipodPending: List<DownloadTask> get() = if (ipodReplace) ipodCandidates else ipodCandidates.filter { ipodKey(it) !in ipodOnDevice }

    /** Songs the person unticked (everything is ticked by default, so new downloads are included). */
    var ipodUnticked by mutableStateOf<Set<String>>(emptySet()); private set
    /** Show only this artist (null = all) and only downloads from the last [ipodDays] days (0 = any time). */
    var ipodArtist by mutableStateOf<String?>(null); private set
    var ipodDays by mutableIntStateOf(0); private set
    fun ipodFilterArtist(a: String?) { ipodArtist = a }
    fun ipodFilterDays(d: Int) { ipodDays = d }

    /** How the songs to send are ordered and grouped under headings. */
    enum class IpodGroup { None, Album, Artist, Date, Year }
    var ipodGroup by mutableStateOf(IpodGroup.None); private set
    fun ipodSetGroup(g: IpodGroup) { ipodGroup = g }

    private fun trackNo(t: DownloadTask) = t.track.albumTrackNumber.takeIf { it > 0 } ?: t.track.number
    private fun dayOf(t: DownloadTask) = if (t.finishedAt <= 0L) "" else java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date(t.finishedAt))

    /** The heading a song sits under for the chosen grouping (null = no headings). Empty = unknown. */
    fun ipodGroupOf(t: DownloadTask): String? = when (ipodGroup) {
        IpodGroup.None -> null
        IpodGroup.Album -> "${t.album.title} · ${t.album.artist}"
        IpodGroup.Artist -> t.track.artist
        IpodGroup.Date -> dayOf(t)
        IpodGroup.Year -> t.album.year.take(4)
    }

    /** The pending songs that pass the artist and date filters, in the order of the chosen grouping (default: newest download first). */
    val ipodShown: List<DownloadTask> get() {
        val since = if (ipodDays > 0) System.currentTimeMillis() - ipodDays * 86_400_000L else 0L
        val list = ipodPending.filter { (ipodArtist == null || it.track.artist == ipodArtist) && (ipodDays == 0 || it.finishedAt >= since) }
        val inAlbum = compareBy<DownloadTask>({ it.album.title.lowercase() }, { it.album.id }, { trackNo(it) })
        return when (ipodGroup) {
            IpodGroup.None -> list.sortedByDescending { it.finishedAt }
            IpodGroup.Album -> list.sortedWith(inAlbum)
            IpodGroup.Artist -> list.sortedWith(compareBy<DownloadTask> { it.track.artist.lowercase() }.then(inAlbum))
            IpodGroup.Date -> list.sortedWith(compareByDescending<DownloadTask> { dayOf(it) }.then(inAlbum))
            IpodGroup.Year -> list.sortedWith(compareByDescending<DownloadTask> { it.album.year.take(4) }.then(inAlbum))
        }
    }
    /** Ticks or unticks every shown song under one heading. */
    fun ipodTickGroup(heading: String, on: Boolean) {
        val keys = ipodShown.filter { ipodGroupOf(it) == heading }.map { ipodKey(it) }.toSet()
        ipodUnticked = if (on) ipodUnticked - keys else ipodUnticked + keys
    }
    val ipodSelected: List<DownloadTask> get() = ipodShown.filter { ipodKey(it) !in ipodUnticked }
    /** The "to send" list as a library (artists → albums → songs) like the iPod's, or as the grouped list. */
    var ipodSendLib by mutableStateOf(true)
    var ipodSendArtist by mutableStateOf<String?>(null)
    var ipodSendAlbum by mutableStateOf<String?>(null)
    fun ipodTickTasks(list: List<DownloadTask>, on: Boolean) { val keys = list.map { ipodKey(it) }.toSet(); ipodUnticked = if (on) ipodUnticked - keys else ipodUnticked + keys }
    fun ipodToggle(t: DownloadTask) { val k = ipodKey(t); ipodUnticked = if (k in ipodUnticked) ipodUnticked - k else ipodUnticked + k }
    fun ipodTickShown(on: Boolean) { val keys = ipodShown.map { ipodKey(it) }.toSet(); ipodUnticked = if (on) ipodUnticked - keys else ipodUnticked + keys }
    fun ipodIsTicked(t: DownloadTask) = ipodKey(t) !in ipodUnticked

    /** Set when reading the identifier over USB failed, so the text box for typing it is shown. */
    var ipodGuidAutoFailed by mutableStateOf(false); private set

    fun onIpodPicked(uri: android.net.Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        prefs.edit().putString("ipod_tree_uri", uri.toString()).apply()
        ipodUri = uri
        ipodRefresh()
    }

    /**
     * An iPod's USB serial number is its FireWire GUID: ask Android for it when the folder did not hold it. Android asks the
     * person to allow this app to use the USB device first. [ipodMessage] says what happened.
     */
    fun ipodReadUsbGuid() {
        val usb = context.getSystemService(android.hardware.usb.UsbManager::class.java)
        val ipod = usb.deviceList.values.firstOrNull { it.vendorId == 0x05AC }
        if (ipod == null) { ipodGuidAutoFailed = true; ipodMessage = strings.ipodUsbNotFound; return }
        fun read() {
            val guid = try { com.qbdlx.mobile.ipod.IpodSysInfo.fromUsbSerial(ipod.serialNumber) } catch (e: SecurityException) { null }
            if (guid != null) { updateIpodGuid(com.qbdlx.mobile.ipod.IpodSysInfo.toHex(guid).uppercase()); ipodMessage = null }
            else { ipodGuidAutoFailed = true; ipodMessage = strings.ipodUsbNoSerial }
        }
        if (usb.hasPermission(ipod)) { read(); return }
        val action = "${context.packageName}.USB_PERMISSION"
        context.registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                context.unregisterReceiver(this)
                if (i.getBooleanExtra(android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, false)) read() else { ipodGuidAutoFailed = true; ipodMessage = strings.ipodUsbDenied }
            }
        }, android.content.IntentFilter(action), android.content.Context.RECEIVER_NOT_EXPORTED)
        usb.requestPermission(ipod, android.app.PendingIntent.getBroadcast(context, 0, android.content.Intent(action).setPackage(context.packageName), android.app.PendingIntent.FLAG_MUTABLE))
    }

    fun updateIpodGuid(text: String) { ipodGuidText = text; prefs.edit().putString("ipod_guid", text).apply() }

    /** (free bytes, total bytes) of the iPod's storage, or null when Android won't say. */
    var ipodSpace by mutableStateOf<Pair<Long, Long>?>(null); private set
    private fun statOf(path: String?): Pair<Long, Long>? = path?.let {
        runCatching { android.os.StatFs(it).let { st -> st.availableBytes to st.totalBytes } }.getOrNull()?.takeIf { p -> p.second > 0 }
    }
    /** Space through the folder permission the app already holds (what file managers use): fstatvfs on the root document's descriptor, no paths needed. */
    private fun statViaSaf(uri: android.net.Uri): Pair<Long, Long>? = runCatching {
        val root = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, android.provider.DocumentsContract.getTreeDocumentId(uri))
        context.contentResolver.openFileDescriptor(root, "r")?.use { pfd ->
            val st = android.system.Os.fstatvfs(pfd.fileDescriptor)
            (st.f_bavail * st.f_frsize) to (st.f_blocks * st.f_frsize)
        }
    }.getOrNull()?.takeIf { it.second > 0 }

    private fun readIpodSpace(uri: android.net.Uri): Pair<Long, Long>? {
        statViaSaf(uri)?.let { return it }
        val volId = runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri).substringBefore(':') }.getOrNull() ?: return null
        val paths = mutableListOf<String?>()
        runCatching {
            val sm = context.getSystemService(android.os.storage.StorageManager::class.java)
            val vol = sm.storageVolumes.firstOrNull { it.uuid.equals(volId, ignoreCase = true) || (volId == "primary" && it.isPrimary) }
            if (android.os.Build.VERSION.SDK_INT >= 30) paths += vol?.directory?.path
            paths += vol?.uuid?.let { "/storage/$it" }
        }
        paths += "/storage/$volId"
        paths += "/mnt/media_rw/$volId"
        runCatching { context.getExternalFilesDirs(null).forEach { d -> if (d != null && (volId == "primary" || d.path.contains(volId, true))) paths += d.path } }
        if (volId == "primary") paths += android.os.Environment.getExternalStorageDirectory().path
        return paths.firstNotNullOfOrNull { statOf(it) }
    }

    var ipodPlaylists by mutableStateOf<List<com.qbdlx.mobile.ipod.IpodPlaylist>>(emptyList()); private set
    var ipodOpenPlaylist by mutableStateOf<Long?>(null)
    /** Library view of the songs on the iPod: artists → albums → songs. */
    var ipodLibMode by mutableStateOf(false)
    /** Library view as a cover grid (true) or as rows (false). */
    private var ipodGridState by mutableStateOf(prefs.getBoolean("ipod_grid", true))
    var ipodGrid: Boolean
        get() = ipodGridState
        set(v) { ipodGridState = v; prefs.edit().putBoolean("ipod_grid", v).apply() }
    class LibAlbum(val name: String, val tracks: List<com.qbdlx.mobile.ipod.IpodTrack>, val cover: String?) { val pids = tracks.map { it.pid } }
    class LibArtist(val name: String, val albums: List<LibAlbum>) {
        val songCount = albums.sumOf { it.tracks.size }
        val cover = albums.firstNotNullOfOrNull { it.cover }
        val pids = albums.flatMap { it.pids }
    }
    /** The iPod's songs grouped artist → album with covers, built once (not on every frame) so the library view scrolls smoothly. */
    var ipodLibrary by mutableStateOf<List<LibArtist>>(emptyList()); private set
    var ipodSortedTracks by mutableStateOf<List<com.qbdlx.mobile.ipod.IpodTrack>>(emptyList()); private set

    /** Covers come from the same album in the app's downloads (the iPod doesn't give them to the app); small versions, to keep scrolling light. */
    private fun buildIpodLibrary(tracks: List<com.qbdlx.mobile.ipod.IpodTrack>) {
        val covers = HashMap<String, String>()
        downloads.tasks.toList().forEach { t -> t.album.coverUrl?.let { covers.putIfAbsent(com.qbdlx.mobile.ipod.trackKey("", t.track.artist, t.album.title), it) } }
        val sorted = tracks.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() }))
        ipodSortedTracks = sorted
        ipodLibrary = sorted.groupBy { it.artist }.map { (artist, ts) ->
            LibArtist(artist, ts.groupBy { it.album }.map { (album, at) ->
                LibAlbum(album, at, covers[com.qbdlx.mobile.ipod.trackKey("", artist, album)]?.replace("_600", "_230"))
            })
        }
    }
    fun ipodRebuildLibrary() { val t = ipodTracks; viewModelScope.launch { withContext(Dispatchers.Default) { buildIpodLibrary(t) } } }
    var ipodLibArtist by mutableStateOf<String?>(null)
    var ipodLibAlbum by mutableStateOf<String?>(null)

    fun ipodRefresh() {
        val uri = ipodUri ?: return
        viewModelScope.launch {
            ipodChecking = true
            // Space is read on its own: a slow USB answer must never hold up the song list.
            launch { ipodSpace = withContext(Dispatchers.IO) { readIpodSpace(uri) } }
            val device = com.qbdlx.mobile.ipod.IpodDevice(context, uri)
            val insp = withContext(Dispatchers.IO) { device.inspect() }
            ipodInspection = insp
            if (insp.ready) {
                val tracks = withContext(Dispatchers.IO) { runCatching { device.listTracks() }.getOrNull() }
                ipodOnDevice = tracks?.map { com.qbdlx.mobile.ipod.trackKey(it.title, it.artist, it.album) }?.toSet().orEmpty()
                ipodSongCount = tracks?.size ?: -1
                ipodTracks = tracks.orEmpty()
                withContext(Dispatchers.Default) { buildIpodLibrary(tracks.orEmpty()) }
                ipodPlaylists = withContext(Dispatchers.IO) { runCatching { device.listPlaylists() }.getOrNull() }.orEmpty()
                if (insp.guid == null && !ipodGuidKnown) ipodReadUsbGuid() // the serial number is the identifier: no need to ask the person
            }
            ipodChecking = false
        }
    }

    /** Share a technical report of the iPod's library (read-only) so artwork and metadata problems can be diagnosed. */
    fun ipodShareReport() {
        val uri = ipodUri ?: return
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { com.qbdlx.mobile.ipod.IpodDevice(context, uri).report() }
            val file = java.io.File(java.io.File(context.cacheDir, "exports").apply { mkdirs() }, "ipod-report.txt").apply { writeText(text) }
            val shared = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(android.content.Intent.EXTRA_STREAM, shared); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Also offer the downloads whose song is already on the iPod, replacing the iPod's copy with the Qobuz one. */
    var ipodReplace by mutableStateOf(false)
    /** Put each song's lyrics (plain text, from LRCLIB) inside the MP3 copy sent to the iPod. */
    var ipodLyrics by mutableStateOf(false)
    /** What the lyrics work is doing right now ("Buscando letras… 12/40"), or null. */
    var ipodLyricsStatus by mutableStateOf<String?>(null); private set
    private fun titleArtistKey(title: String, artist: String) = (title.trim() + "\u0001" + artist.trim()).lowercase(java.util.Locale.ROOT)
    private val ipodOldByTitleArtist: Map<String, List<Long>> get() = ipodTracks.groupBy({ titleArtistKey(it.title, it.artist) }, { it.pid })
    /** Downloads whose title and artist match a track already on the iPod. */
    val ipodReplaceable: List<DownloadTask> get() {
        val old = ipodOldByTitleArtist
        return ipodCandidates.filter { ipodKey(it) in ipodOnDevice || titleArtistKey(it.track.displayTitle, it.track.artist) in old }
    }

    fun ipodSend(list: List<DownloadTask> = ipodSelected, originals: Map<Long, List<Long>> = emptyMap()) {
        val uri = ipodUri ?: return
        val guid = ipodGuid()
        if (guid == null) { ipodReadUsbGuid(); return } // read in the background; the person just taps Send again
        // Never rewrite what is already on the iPod with the same title, artist and album (e.g. copied before from this app).
        val pending = list.filter { ipodKey(it) !in ipodOnDevice }.distinctBy { ipodKey(it) }
        if (pending.isEmpty()) { if (list.isNotEmpty()) notice = strings.ipodNothingToSend(false); return }
        if (ipodProgress != null) { notice = strings.ipodBusy; return }
        val old = ipodOldByTitleArtist
        val items = pending.map { t ->
            com.qbdlx.mobile.ipod.SendItem(
                replacePids = if (ipodReplace) (old[titleArtistKey(t.track.displayTitle, t.track.artist)].orEmpty() + originals[t.track.id].orEmpty()).distinct() else emptyList(),
                key = ipodKey(t), sourceUri = t.savedUri!!, title = t.track.displayTitle, artist = t.track.artist, album = t.album.title,
                genre = t.album.genre, durationMs = t.track.durationSec * 1000L,
                trackNumber = t.track.albumTrackNumber.takeIf { it > 0 } ?: t.track.number,
                year = t.album.year.take(4).toIntOrNull() ?: 0, bitRate = 320, coverUrl = t.album.coverUrl,
            )
        }
        runIpodJob(items, guid, uri, resumed = false)
    }

    private val ipodCovers by lazy { com.qbdlx.mobile.ipod.IpodCovers(context) }

    /** Songs left from a send that was interrupted (0 = nothing to resume). */
    var ipodJobLeft by mutableIntStateOf(com.qbdlx.mobile.ipod.IpodJobStore.load(getApplication()).size); private set

    /**
     * Copies the songs in batches of 40; each batch is written into the iPod's library before the next starts, so what
     * was done survives an interruption. The list still to do is kept on disk and the foreground service keeps the
     * transfer going with the screen off or another app open.
     */
    private fun runIpodJob(all: List<com.qbdlx.mobile.ipod.SendItem>, guid: ByteArray, uri: android.net.Uri, resumed: Boolean) {
        viewModelScope.launch {
            ipodMessage = null; ipodStopping = false
            ipodProgress = 0 to all.size
            askNotifications = true
            com.qbdlx.mobile.ipod.IpodJobStore.save(context, all); ipodJobLeft = all.size
            IpodSyncService.start(context)
            ipodNotify(strings.ipodNotifTitle(0, all.size), 0, all.size, ongoing = true)
            val device = com.qbdlx.mobile.ipod.IpodDevice(context, uri)
            var remaining = all; var sent = 0; var failed = 0; var error: String? = null; var covers = 0; var artworkNote: String? = null; var lyricsAdded = 0
            var first = true; var stopped = false
            while (remaining.isNotEmpty()) {
                if (ipodStopping) { stopped = true; break }
                val chunk = remaining.take(IPOD_BATCH)
                val base = all.size - remaining.size
                val r = withContext(Dispatchers.IO) {
                    val lyricTexts = if (ipodLyrics) fetchLyricTexts(chunk) else emptyMap()
                    device.send(chunk, guid, makeBackup = first && !resumed, coverFrames = { url -> ipodCovers.frames(url) }, lyricsFor = if (ipodLyrics) { item -> lyricTexts[item.key] } else null) { d, _ ->
                        ipodProgress = (base + d) to all.size
                        ipodNotify(strings.ipodNotifTitle(base + d, all.size), base + d, all.size, ongoing = true)
                    }
                }
                first = false
                sent += r.sent.size; failed += r.failed.size; covers += r.covers; lyricsAdded += r.lyrics
                if (r.artworkNote != null) artworkNote = r.artworkNote
                if (r.error != null && r.sent.isEmpty()) { error = r.error; break } // iPod gone or library unreadable: keep the rest for later
                remaining = remaining.drop(chunk.size)
                com.qbdlx.mobile.ipod.IpodJobStore.save(context, remaining); ipodJobLeft = remaining.size
            }
            if (error == null && !stopped) { com.qbdlx.mobile.ipod.IpodJobStore.clear(context); ipodJobLeft = 0 }
            IpodSyncService.stop(context)
            ipodProgress = null; ipodStopping = false
            val text = if (error != null) strings.ipodInterrupted(remaining.size, error) else if (stopped) strings.ipodStoppedSend(sent, remaining.size) else strings.ipodDone(sent, failed)
            ipodNotify(if (error != null || stopped) text else strings.ipodNotifDone(sent, failed), 0, 0, ongoing = false)
            if (stopped) playlistAfterSend = null // an error (e.g. the iPod is full) still builds the playlists from the songs that are there
            ipodMessage = (if (error == null && (covers > 0 || artworkNote != null)) text + " " + strings.ipodCovers(covers, artworkNote) else text) +
                (if (ipodLyrics) " " + strings.ipodLyricsDone(lyricsAdded) else "")
            if (error == null && !stopped) ipodDone++
            if (!stopped) finishIpodPlaylist() else { playlistAfterSend = null; ipodRefresh() }
        }
    }

    /** Repairs the album cover pointers of songs sent by earlier versions, so the iPod's Albums view shows their covers. */
    fun ipodFixAlbumCovers() {
        val uri = ipodUri ?: return
        if (ipodProgress != null || ipodLyricsStatus != null || ipodTask != null) { notice = strings.ipodBusy; return }
        val guid = ipodGuid()
        if (guid == null) { ipodReadUsbGuid(); return }
        ipodMessage = null; ipodTask = strings.ipodTaskFixing
        viewModelScope.launch {
            val (n, err) = try { withContext(Dispatchers.IO) { com.qbdlx.mobile.ipod.IpodDevice(context, uri).fixAlbumCovers(guid) } } finally { ipodTask = null }
            ipodMessage = if (err != null) strings.ipodFixAlbumsFailed(err) else strings.ipodFixAlbumsDone(n)
            ipodRefresh()
        }
    }

    /** Sends a Qobuz playlist to the iPod: its downloaded MP3s that are not there yet are sent, then the playlist is created with every one of its songs found on the iPod. */
    fun ipodSendPlaylists(selected: List<Playlist>) {
        if (selected.isEmpty()) return
        val uri = ipodUri ?: return
        if (ipodProgress != null || ipodLyricsStatus != null || ipodTask != null) { notice = strings.ipodBusy; return }
        if (ipodGuid() == null) { ipodReadUsbGuid(); return }
        ipodPlaylistPicker = false
        viewModelScope.launch {
            ipodMessage = null
            val loaded = ArrayList<Pair<String, List<Pair<Album, Track>>>>()
            for ((i, p) in selected.withIndex()) {
                ipodLyricsStatus = strings.ipodPlaylistLoading(p.name, i, selected.size); ipodLyricsProgress = i to selected.size
                val detail = withContext(Dispatchers.IO) { runCatching { api.getPlaylist(p.id) }.getOrNull() }
                if (detail == null) { ipodLyricsStatus = null; ipodLyricsProgress = null; ipodMessage = strings.ipodPlaylistLoadFailed; return@launch }
                loaded.add(p.name to detail.tracks)
            }
            ipodLyricsStatus = null; ipodLyricsProgress = null
            val allTracks = loaded.flatMap { it.second }
            val keyLists = loaded.map { (name, tr) -> name to tr.map { (al, t) -> com.qbdlx.mobile.ipod.trackKey(t.displayTitle, t.artist, al.title) } }
            val keys = keyLists.flatMap { it.second }
            // Songs that are neither on the iPod nor downloaded as MP3 yet are downloaded first (with progress and a Stop button), then sent.
            val have = ipodCandidates.map { ipodKey(it) }.toSet()
            val missing = allTracks.filter { (al, t) ->
                val k = com.qbdlx.mobile.ipod.trackKey(t.displayTitle, t.artist, al.title); k !in ipodOnDevice && k !in have && t.streamable
            }.distinctBy { (al, t) -> com.qbdlx.mobile.ipod.trackKey(t.displayTitle, t.artist, al.title) }
            if (missing.isNotEmpty()) {
                ipodStopping = false
                askNotifications = true
                downloads.enqueue(missing, Quality.MP3)
                val ids = missing.map { "${it.second.id}:${Quality.MP3.formatId}" }.toSet()
                while (true) {
                    val mine = downloads.tasks.filter { it.key in ids }
                    val done = mine.count { !it.status.isOpen }
                    ipodLyricsStatus = strings.ipodPlaylistDownloading(done, missing.size)
                    ipodLyricsProgress = done to missing.size
                    if (ipodStopping) break
                    if (mine.none { it.status.isOpen }) break
                    kotlinx.coroutines.delay(700)
                }
                val stopped = ipodStopping
                ipodLyricsStatus = null; ipodLyricsProgress = null; ipodStopping = false
                if (stopped) {
                    downloads.tasks.filter { it.key in ids && it.status.isOpen }.forEach { downloads.cancel(it) }
                    ipodMessage = strings.ipodPlaylistDownloadStopped; return@launch
                }
            }
            val candidates = ipodCandidates.associateBy { ipodKey(it) }
            val toSend = keys.distinct().filter { it !in ipodOnDevice }.mapNotNull { candidates[it] }
            playlistAfterSend = keyLists
            if (toSend.isEmpty()) finishIpodPlaylist() else ipodSend(toSend)
        }
    }

    /** Bumped when a send or a playlist build finishes well; the iPod screen plays its check animation. */
    var ipodDone by mutableStateOf(0)
    private fun finishIpodPlaylist() {
        val pending = playlistAfterSend
        if (pending == null) { ipodRefresh(); return }
        playlistAfterSend = null
        val uri = ipodUri; val guid = ipodGuid()
        if (uri == null || guid == null) { ipodRefresh(); return }
        viewModelScope.launch {
            val msgs = ArrayList<String>()
            ipodMessage?.let { msgs.add(it) }
            for ((i, pl) in pending.withIndex()) {
                val (name, keys) = pl
                ipodLyricsStatus = strings.ipodPlaylistMaking(name, i, pending.size); ipodLyricsProgress = i to pending.size
                val r = withContext(Dispatchers.IO) { com.qbdlx.mobile.ipod.IpodDevice(context, uri).makePlaylist(name, keys, guid) }
                msgs.add(strings.ipodPlaylistDone(name, r.added, r.missing, r.error))
            }
            ipodLyricsStatus = null; ipodLyricsProgress = null
            ipodMessage = msgs.joinToString(" ")
            ipodDone++
            ipodRefresh()
        }
    }

    /** Plain lyrics from LRCLIB for these songs (key, artist, title, album, durationMs), keyed by [key]; songs without lyrics are left out. Fifty requests at a time. */
    private suspend fun <K> fetchLyricTexts(items: List<K>, report: Boolean = true, spec: (K) -> List<Any>): Map<Any, String> = coroutineScope {
        val gate = kotlinx.coroutines.sync.Semaphore(50)
        val counter = java.util.concurrent.atomic.AtomicInteger(0)
        if (report) { ipodLyricsStatus = strings.ipodLyricsSearching(0, items.size); ipodLyricsProgress = 0 to items.size }
        items.map { item ->
            async {
                gate.withPermit {
                    val (key, artist, title, album, ms) = spec(item).let { Quint(it[0], it[1] as String, it[2] as String, it[3] as String, it[4] as Long) }
                    var found: Lyrics? = null
                    for (attempt in 0 until 3) {
                        if (attempt > 0) kotlinx.coroutines.delay(1000L * attempt)
                        val r = runCatching { LyricsClient.fetch(artist, title, album, (ms / 1000).toInt()) }
                        if (r.isSuccess) { found = r.getOrNull(); break }
                    }
                    counter.incrementAndGet().let { c -> if (report) { ipodLyricsStatus = strings.ipodLyricsSearching(c, items.size); ipodLyricsProgress = c to items.size } }
                    found?.lines?.joinToString("\n") { it.text }?.takeIf { it.isNotBlank() }?.let { key to it }
                }
            }
        }.awaitAll().filterNotNull().toMap()
    }
    private data class Quint(val a: Any, val b: String, val c: String, val d: String, val e: Long)

    private suspend fun fetchLyricTexts(items: List<com.qbdlx.mobile.ipod.SendItem>): Map<String, String> =
        fetchLyricTexts(items) { listOf(it.key, it.artist, it.title, it.album, it.durationMs) }.mapKeys { it.key as String }.also { ipodLyricsStatus = null; ipodLyricsProgress = null }

    /** Writes lyrics into the songs already on the iPod (the ones in the list on screen). */
    fun ipodAddLyricsToDevice(all: List<com.qbdlx.mobile.ipod.IpodTrack>) {
        val uri = ipodUri ?: return
        val tracks = all.filter { !it.hasLyrics } // songs the iPod already lists with lyrics are not written again
        if (tracks.isEmpty()) { ipodMessage = strings.ipodLyricsNothing(all.size); return }
        if (ipodProgress != null || ipodLyricsStatus != null) { notice = strings.ipodBusy; return }
        val guid = ipodGuid()
        if (guid == null) { ipodReadUsbGuid(); return }
        viewModelScope.launch {
            ipodStopping = false
            ipodLyricsStatus = strings.ipodLyricsSearching(0, tracks.size)
            val device = com.qbdlx.mobile.ipod.IpodDevice(context, uri)
            val r = withContext(Dispatchers.IO) {
                device.addLyrics(tracks, guid, stop = { ipodStopping }, lyricsFor = { chunk ->
                    runBlocking { fetchLyricTexts(chunk, report = false) { listOf(it.pid, it.artist, it.title, it.album, it.durationMs) } }.mapKeys { it.key as Long }
                }) { d, t -> ipodLyricsStatus = strings.ipodLyricsWriting(d, t); ipodLyricsProgress = d to t }
            }
            ipodLyricsStatus = null; ipodLyricsProgress = null; ipodStopping = false
            ipodMessage = if (r.error != null) strings.ipodLyricsFailed(r.added, r.error) else if (r.stopped) strings.ipodLyricsStopped(r.added) else strings.ipodLyricsDeviceDone(r.added, r.skipped, r.failed)
            ipodRefresh()
        }
    }

    /** Takes up an interrupted send again: only the songs that are not on the iPod yet and whose files still exist. */
    fun ipodResume() {
        val uri = ipodUri ?: return
        if (ipodProgress != null) { notice = strings.ipodBusy; return }
        val guid = ipodGuid()
        if (guid == null) { ipodReadUsbGuid(); return }
        viewModelScope.launch {
            ipodRefresh()
            var waited = 0
            while (ipodChecking && waited < 40) { kotlinx.coroutines.delay(500); waited++ }
            if (ipodInspection?.ready != true) { ipodMessage = strings.ipodNotReadyYet; return@launch }
            val items = withContext(Dispatchers.IO) {
                com.qbdlx.mobile.ipod.IpodJobStore.load(context).filter { it.key !in ipodOnDevice }.filter { i ->
                    runCatching { context.contentResolver.openAssetFileDescriptor(android.net.Uri.parse(i.sourceUri), "r")?.close() != null }.getOrDefault(false)
                }
            }
            if (items.isEmpty()) { com.qbdlx.mobile.ipod.IpodJobStore.clear(context); ipodJobLeft = 0; ipodMessage = strings.ipodNothingLeft; return@launch }
            runIpodJob(items, guid, uri, resumed = true)
        }
    }

    fun ipodDiscardJob() { com.qbdlx.mobile.ipod.IpodJobStore.clear(context); ipodJobLeft = 0 }

    /** True while an Apple USB device (an iPod) is plugged into the phone: the iPod button in the bottom bar shows only then. */
    var ipodPlugged by mutableStateOf(false); private set
    private fun checkIpodPlugged() {
        ipodPlugged = runCatching {
            context.getSystemService(android.hardware.usb.UsbManager::class.java).deviceList.values.any { it.vendorId == 0x05AC }
        }.getOrDefault(false)
    }

    init {
        checkIpodPlugged()
        val usbFilter = android.content.IntentFilter().apply {
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        androidx.core.content.ContextCompat.registerReceiver(context, object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                val gone = i.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED &&
                    (i.getParcelableExtra<android.hardware.usb.UsbDevice>(android.hardware.usb.UsbManager.EXTRA_DEVICE))?.vendorId == 0x05AC
                if (gone) ipodPlugged = false else checkIpodPlugged()
            }
        }, usbFilter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
    }

    init {
        // Wi-Fi only: pause when it drops, carry on when it is back.
        runCatching {
            context.getSystemService(android.net.ConnectivityManager::class.java)?.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) { if (wifiOnly) downloads.recheckAllowed() }
                override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) { if (wifiOnly) downloads.recheckAllowed() }
                override fun onLost(network: android.net.Network) { if (wifiOnly) downloads.recheckAllowed() }
            })
        }
    }

    init {
        // Plugging the iPod in again carries on an interrupted send by itself.
        val filter = android.content.IntentFilter(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED)
        androidx.core.content.ContextCompat.registerReceiver(context, object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                if (ipodJobLeft > 0 && ipodProgress == null) viewModelScope.launch { kotlinx.coroutines.delay(4000); ipodResume() }
            }
        }, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
    }

    /** The iPod sync shown in the notification shade, with a bar while it runs and a result when it ends. */
    private fun ipodNotify(text: String, done: Int, total: Int, ongoing: Boolean) {
        runCatching {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            nm.createNotificationChannel(android.app.NotificationChannel("ipod", strings.ipodNotifChannel, android.app.NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
            val open = android.app.PendingIntent.getActivity(
                context, 0, android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = androidx.core.app.NotificationCompat.Builder(context, "ipod")
                .setSmallIcon(R.drawable.ic_stat).setContentTitle("iPod").setContentText(text).setContentIntent(open)
                .setOngoing(ongoing).setOnlyAlertOnce(true).setAutoCancel(!ongoing)
                .apply { if (ongoing && total > 0) setProgress(total, done, false) }
                .build()
            nm.notify(IPOD_NOTIF_ID, n)
        }
    }

    /**
     * Sends finished MP3 downloads (the given ones, or all) to the iPod right away, skipping what is already there.
     * Without an iPod chosen it opens the iPod screen instead.
     */
    fun ipodSendDownloaded(list: List<DownloadTask>? = null) {
        if (ipodUri == null) { openIpod(); notice = strings.ipodChooseFirst; return }
        showIpod = true // go to the iPod screen, where the progress is shown
        if (ipodProgress != null) { notice = strings.ipodBusy; return }
        viewModelScope.launch {
            ipodRefresh()
            var waited = 0
            while (ipodChecking && waited < 40) { kotlinx.coroutines.delay(500); waited++ } // what is on the iPod now decides what is new
            val src = (list ?: downloads.tasks.toList()).filter { it.status == TaskStatus.COMPLETED && it.quality == Quality.MP3 && it.savedUri != null }
                .distinctBy { ipodKey(it) }
            val fresh = src.filter { ipodKey(it) !in ipodOnDevice }
            if (fresh.isEmpty()) { notice = strings.ipodNothingToSend(src.isEmpty()); return@launch }
            ipodSend(fresh)
        }
    }

    /** Set when the notification-access screen must open (the Activity does it and clears the flag). */
    var openListenerSettings by mutableStateOf(false)

    var showListen by mutableStateOf(false); private set
    /** The song the Qobuz app has on, and the Qobuz track it was matched to (null while looking or when none). */
    var listenSong by mutableStateOf<PlayingNow?>(null); private set
    var listenMatch by mutableStateOf<ImportItem?>(null); private set
    var listenSearching by mutableStateOf(false); private set
    var listenAccess by mutableStateOf(true); private set
    private var listenKey: String? = null

    /** "Sonando": shows the identified song; nothing is downloaded until the round button is pressed. */
    fun openListening() {
        closeAlbum(); closePlaylist(); closeDiagnostics(); closeIpod(); closeImport(); closeArtist()
        showListen = true
        refreshListening()
    }

    /** Reads what Qobuz is playing; looks the song up on Qobuz only when it changed. Called every couple of seconds while the screen is open. */
    fun refreshListening() {
        listenAccess = QobuzNowPlaying.accessGranted(context)
        if (!listenAccess) { listenSong = null; listenMatch = null; listenKey = null; return }
        val playing = QobuzNowPlaying.read(context)
        listenSong = playing
        val key = playing?.let { "${it.title}|${it.artist}|${it.album}" }
        if (key == listenKey) return
        listenKey = key; listenMatch = null
        if (playing == null) return
        viewModelScope.launch {
            listenSearching = true
            try {
                val item = withContext(Dispatchers.IO) { searchSong(applyAliases(ImportedSong(playing.title, playing.artist, playing.album, playing.durationSec, ""))) }
                if (listenKey == key) listenMatch = item
            } catch (e: Exception) { if (listenKey == key) listenKey = null } finally { listenSearching = false }
        }
    }

    fun askListenAccess() { notice = strings.nowAccess; openListenerSettings = true }

    /** The round button of "Sonando": downloads the identified song. */
    fun downloadListening() {
        val song = listenSong ?: return
        val item = listenMatch; val pick = item?.candidates?.firstOrNull()
        if (item == null || item.level == MatchLevel.Missing || pick == null) { notice = strings.nowNotFound(song.title); return }
        requestDownload(DownloadRequest.Tracks(listOf(pick)))
        notice = strings.nowDownloading(pick.second.displayTitle, pick.second.artist, item.level == MatchLevel.Sure)
    }

    fun ipodRestore() {
        val uri = ipodUri ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { com.qbdlx.mobile.ipod.IpodDevice(context, uri).restoreLatestBackup() }.getOrDefault(false) }
            ipodMessage = if (ok) strings.ipodRestoreDone else strings.ipodNoBackup
            if (ok) ipodRefresh()
        }
    }
    fun openDiagnostics() { showDiagnostics = true }
    fun closeDiagnostics() { showDiagnostics = false }

    fun updateAmoled(v: Boolean) { amoled = v; prefs.edit().putBoolean("amoled", v).apply() }

    private val libraryCache by lazy { LibraryCache(java.io.File(context.filesDir, "library.json")) }
    private var libraryRefreshed = false

    /**
     * Opens at once with what was saved last time, then refreshes in the background: each list shows as soon as it arrives,
     * and favorites only fetch the newest pages (the old ones are kept). [force] (the refresh button) re-reads everything.
     */
    fun loadLibrary(force: Boolean = false) {
        if (libraryLoading || (!force && libraryRefreshed && myPlaylists != null)) return
        viewModelScope.launch {
            libraryLoading = true
            try {
                if (myPlaylists == null) {
                    withContext(Dispatchers.IO) { libraryCache.load() }?.let {
                        myPlaylists = it.playlists; favAlbums = it.albums; favTracks = it.tracks; favArtists = it.artists
                    }
                }
                val oldAlbums = if (force) null else favAlbums?.map { it.id }?.toSet()?.takeIf { it.isNotEmpty() }
                val oldTracks = if (force) null else favTracks?.map { it.second.id }?.toSet()?.takeIf { it.isNotEmpty() }
                // Each list on its own, so one failing doesn't hide the others; each shows when it is ready.
                val pl = async(Dispatchers.IO) { runCatching { api.userPlaylists() } }
                val al = async(Dispatchers.IO) { runCatching { api.favoriteAlbums(oldAlbums) } }
                val tr = async(Dispatchers.IO) { runCatching { api.favoriteTracks(oldTracks) } }
                val ar = async(Dispatchers.IO) { runCatching { api.favoriteArtists() } }
                val old = favAlbums; val oldT = favTracks
                pl.await().onSuccess { myPlaylists = it }.onFailure { if (myPlaylists == null) { error = it.message; myPlaylists = emptyList() } }
                al.await().onSuccess { fresh ->
                    favAlbums = if (oldAlbums == null || old == null) fresh else fresh + old.filter { o -> fresh.none { it.id == o.id } }
                }.onFailure { if (favAlbums == null) { error = it.message; favAlbums = emptyList() } }
                tr.await().onSuccess { fresh ->
                    favTracks = if (oldTracks == null || oldT == null) fresh else fresh + oldT.filter { o -> fresh.none { it.second.id == o.second.id } }
                }.onFailure { if (favTracks == null) { error = it.message; favTracks = emptyList() } }
                ar.await().onSuccess { favArtists = it }.onFailure { if (favArtists == null) favArtists = emptyList() }
                libraryRefreshed = true
                val snap = LibrarySnapshot(myPlaylists.orEmpty(), favAlbums.orEmpty(), favTracks.orEmpty(), favArtists.orEmpty())
                withContext(Dispatchers.IO) { libraryCache.save(snap) }
            } finally {
                libraryLoading = false
            }
        }
    }

    fun updateLanguage(l: Language) { language = l; prefs.edit().putString("language", l.code).apply() }
    /** Picking a concrete quality means downloads use it without asking. */
    fun updateQuality(q: Quality) {
        quality = q; askQuality = false
        prefs.edit().putInt("quality", q.formatId).putBoolean("ask_quality", false).apply()
    }
    /** "Preguntar siempre": the stored quality is kept (playback and the dialog's highlight still use it). */
    fun chooseAskAlways() { askQuality = true; prefs.edit().putBoolean("ask_quality", true).apply() }

    fun startWebLogin() { error = null; webLogin = true }
    fun cancelWebLogin() { webLogin = false }

    private var prefetch: Deferred<Unit>? = null

    /** Fetches the web player's app id/secret in the background so login doesn't wait for it. */
    fun prefetchCredentials() {
        if (prefs.contains("web_app_id") || prefetch?.isActive == true) return
        prefetch = viewModelScope.async(Dispatchers.IO) {
            runCatching { api.fetchWebCredentials() }.getOrNull()?.let { saveWebCredentials(it) }
        }
    }

    private fun saveWebCredentials(c: Pair<String, String>) {
        prefs.edit().putString("web_app_id", c.first).putString("web_app_secret", c.second).apply()
    }

    private suspend fun webCredentials(): Pair<String, String> {
        prefetch?.await()
        val id = prefs.getString("web_app_id", null)
        val secret = prefs.getString("web_app_secret", null)
        if (id != null && secret != null) return id to secret
        return api.fetchWebCredentials().also { saveWebCredentials(it) }
    }

    /** Called by the in-app browser once it has read the web player session. */
    fun finishWebLogin(userId: String, token: String) {
        webLogin = false
        signingIn = true
        run {
            val (appId, secret) = webCredentials()
            try {
                tokenLogin(appId, secret, userId, token, "automático (reproductor web)")
            } catch (e: Exception) {
                // The cached credentials may be stale; fetch fresh ones next time.
                prefs.edit().remove("web_app_id").remove("web_app_secret").apply()
                throw e
            }
        }
    }

    private fun tokenLogin(appId: String, appSecret: String, userId: String, token: String, source: String) {
        val effectiveAppId = appId.trim()
        val baseSecret = appSecret.trim()
        val effectiveSecret = baseSecret
        if (effectiveAppId.isBlank() || effectiveSecret.isBlank() || userId.isBlank() || token.isBlank()) {
            throw IllegalArgumentException("Missing App ID, App Secret, user ID, or session token")
        }

        api.appId = effectiveAppId
        api.appSecret = effectiveSecret
        api.secretSource = source
        userName = api.loginWithToken(userId, token)
        prefs.edit().putString("app_id", effectiveAppId).putString("app_secret", baseSecret)
            .putString("user_id", userId).putString("token", token).apply()
        onLoggedIn()
    }

    private fun onLoggedIn() {
        loggedIn = true
        downloads.setEnabled(true) // resumes whatever was waiting in the saved queue
        tab = Tab.Home
        loadHome()
    }

    private fun loadHome() {
        discover = null
        selectedTag = null
        viewModelScope.launch {
            homeLoading = true
            try {
                discover = withContext(Dispatchers.IO) {
                    try {
                        api.discover().takeIf { it.newReleases != null || it.sections.isNotEmpty() }
                            ?: throw ApiException("Discover came back empty")
                    } catch (e: Exception) {
                        // Fall back to the plain new-releases list so Home is never blank.
                        error = strings.discoverFailed(e.message)
                        Discover(HomeSection("new_releases", api.getFeatured("new-releases")), emptyList(), emptyList(), emptyList())
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                homeLoading = false
            }
        }
    }

    /** Filters the "Qobuz Playlists" row by tag; null shows Discover's own selection. */
    fun selectTag(tag: PlaylistTag?) {
        selectedTag = tag
        tagPlaylists = emptyList()
        if (tag == null) return
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { api.discoverPlaylists(tag.slug) }
                if (selectedTag == tag) tagPlaylists = list
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun openPlaylist(id: String) = run { playlistDetail = api.getPlaylist(id) }
    fun closePlaylist() { playlistDetail = null }

    fun logout() {
        downloads.setEnabled(false) // running transfers go back to pending; nothing starts without a session
        stopPlayback()
        api.authToken = null
        prefs.edit().remove("user_id").remove("token").apply()
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        loggedIn = false; results = emptyList(); discover = null; detail = null; playlistDetail = null; artistPage = null
        myPlaylists = null; favAlbums = null; favTracks = null; favArtists = null
        libraryRefreshed = false; libraryCache.clear()
    }

    fun search(query: String) {
        if (query.isBlank()) return
        run {
            results = api.searchAlbums(query.trim())
            trackResults = runCatching { api.searchTracks(query.trim(), 40) }.getOrDefault(emptyList())
        }
    }

    fun openAlbum(id: String) = run { detail = api.getAlbum(id) }

    /** Covers for playlist mosaics, loaded a few at a time for the playlists whose list entry had only one. */
    val playlistCovers = androidx.compose.runtime.mutableStateMapOf<String, List<String>>()
    private val coverLoads = HashSet<String>()
    private val coverSlots = kotlinx.coroutines.sync.Semaphore(3)

    fun ensurePlaylistCovers(p: Playlist) {
        if (p.covers.size >= 2 || p.id in playlistCovers || !coverLoads.add(p.id)) return
        viewModelScope.launch {
            val covers = coverSlots.let { slots ->
                slots.acquire()
                try { withContext(Dispatchers.IO) { runCatching { api.playlistCovers(p.id) }.getOrDefault(emptyList()) } } finally { slots.release() }
            }
            playlistCovers[p.id] = covers.ifEmpty { listOfNotNull(p.coverUrl) }
        }
    }

    /** The search screen, opened from the search button in the page headers. */
    fun openSearch() {
        closeAlbum(); closePlaylist(); closeDiagnostics(); closeIpod(); closeImport(); closeArtist()
        tab = Tab.Search
    }

    /** An artist's name opens their page (picture and albums). If Qobuz can't find them, it falls back to a search for the name. */
    fun openArtist(name: String) {
        viewModelScope.launch {
            busy = true; error = null
            try {
                val found = withContext(Dispatchers.IO) { api.findArtist(name)?.let { (id, n) -> api.artistPage(id, n) } }
                if (found != null && found.albums.isNotEmpty()) {
                    closeAlbum(); closePlaylist(); closeDiagnostics(); closeIpod(); closeImport()
                    artistPage = found
                } else {
                    searchQuery = name
                    closeAlbum(); closePlaylist(); closeDiagnostics(); closeIpod(); closeImport(); closeArtist()
                    tab = Tab.Search; search(name)
                }
            } catch (e: Exception) { error = e.message ?: e.javaClass.simpleName } finally { busy = false }
        }
    }
    fun closeArtist() { artistPage = null }
    fun closeAlbum() { detail = null }

    /** Every download button ends up here: ask for the quality only when the user chose "Preguntar siempre". */
    fun requestDownload(r: DownloadRequest) {
        when {
            askQuality -> pendingRequest = r
            r is DownloadRequest.Albums || r is DownloadRequest.Playlists -> pendingBulk = r
            else -> startDownload(r, quality)
        }
    }

    fun confirmBulk() {
        val r = pendingBulk ?: return
        pendingBulk = null
        startDownload(r, quality)
    }

    fun confirmDownload(q: Quality) {
        val r = pendingRequest ?: return
        pendingRequest = null
        startDownload(r, q)
    }

    private fun startDownload(r: DownloadRequest, q: Quality) {
        viewModelScope.launch {
            try {
                askNotifications = true
                when (r) {
                    is DownloadRequest.Albums -> {
                        notice = strings.preparingAlbums(r.albums.size)
                        notice = bulkNotice(enqueueBulk(r.albums, { al -> withContext(Dispatchers.IO) { albumTracks(al.id) } }, { downloads.enqueue(it, q) }))
                    }
                    is DownloadRequest.Playlists -> {
                        notice = strings.preparingPlaylists(r.playlists.size)
                        notice = bulkNotice(enqueueBulk(r.playlists, { pl -> withContext(Dispatchers.IO) { api.getPlaylist(pl.id).tracks } }, { downloads.enqueue(it, q) }))
                    }
                    else -> {
                        val items = withContext(Dispatchers.IO) {
                            when (r) {
                                is DownloadRequest.Tracks -> r.items
                                is DownloadRequest.WholeAlbum -> albumTracks(r.album.id)
                                is DownloadRequest.WholePlaylist -> api.getPlaylist(r.playlist.id).tracks
                                else -> emptyList()
                            }.filter { it.second.streamable }
                        }
                        val result = downloads.enqueue(items, q)
                        notice = when {
                            items.isEmpty() -> strings.nothingToDownload
                            result.added == 0 -> strings.alreadyQueued(result.skipped)
                            result.skipped > 0 -> strings.queuedTracksSkipped(result.added, result.skipped)
                            else -> strings.queuedTracks(result.added)
                        }
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
        }
    }

    private fun albumTracks(albumId: String): List<Pair<Album, Track>> =
        api.getAlbum(albumId).let { d -> d.tracks.map { d.album to it } }

    private fun bulkNotice(r: BulkResult): String = when {
        r.added == 0 && r.skipped == 0 && r.failedSources == 0 -> strings.nothingToDownload
        else -> strings.bulkDone(r.added, r.skipped, r.failedSources)
    }

    /**
     * Fetches a real track and asks Qobuz for its download URL, so the user can produce a
     * diagnostic entry without browsing the catalogue. It tries MP3 (format 5) and the quality
     * chosen in Settings: failing on every format points to a wrong App Secret (signature),
     * while failing on only the higher one points to an account/entitlement limit. Every
     * attempt is recorded by [QobuzApi.getFileUrl] into [Diagnostics].
     */
    fun runDiagnosticTest() {
        viewModelScope.launch {
            busy = true; error = null
            try {
                withContext(Dispatchers.IO) {
                    val album = try {
                        api.getFeatured("new-releases", 5).firstOrNull()
                            ?: api.searchAlbums("hits", 5).firstOrNull()
                            ?: throw ApiException("No se pudo obtener un álbum de ejemplo para la prueba")
                    } catch (e: Exception) {
                        Diagnostics.log("[prueba] no se pudo listar álbumes de ejemplo: ${e.message ?: e.javaClass.simpleName}")
                        throw e
                    }
                    val detail = try {
                        api.getAlbum(album.id)
                    } catch (e: Exception) {
                        Diagnostics.log("[prueba] no se pudieron leer las pistas del álbum \"${album.title}\": ${e.message ?: e.javaClass.simpleName}")
                        throw e
                    }
                    val track = detail.tracks.firstOrNull { it.streamable } ?: detail.tracks.firstOrNull()
                        ?: throw ApiException("El álbum de ejemplo no tiene pistas")
                    // Test MP3 and the selected quality; ignore failures here, they are logged by getFileUrl.
                    linkedSetOf(Quality.MP3.formatId, quality.formatId).forEach { fmt ->
                        runCatching { api.getFileUrl(track.id, fmt) }
                    }
                }
                notice = strings.diagTestDone
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }

    /**
     * Forces a fresh extraction of the App ID + App Secret from the official web player (ignoring
     * any cache), validates the reconstructed secret candidates against a real track using the
     * current session, and — only on success — saves the matching App ID and Secret together.
     * Never mixes a new App ID with an old Secret, never touches the user id or session token, and
     * logs every step by fingerprint (never the secret itself).
     */
    fun refreshQobuzCredentials() {
        viewModelScope.launch {
            busy = true; error = null
            try {
                val tz = withContext(Dispatchers.IO) { doRefreshCredentials() }
                notice = strings.credsUpdated(tz)
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }

    private fun doRefreshCredentials(): String {
        val token = api.authToken?.takeIf { it.isNotBlank() }
            ?: throw ApiException("Inicia sesión antes de actualizar las credenciales")

        val creds = api.fetchFreshWebCredentials()

        // The session token belongs to the App ID it was issued for; a different App ID would make
        // every candidate look rejected. If the bundle's App ID changed, stop and ask for re-login.
        val sessionAppId = api.appId.ifBlank { savedAppId }
        if (sessionAppId.isNotBlank() && creds.appId != sessionAppId) {
            Diagnostics.log("[credenciales] el bundle trae app_id=${creds.appId}, distinto del de la sesión ($sessionAppId). Cierra sesión y vuelve a entrar.")
            throw ApiException("El App ID del reproductor web cambió (${creds.appId}); cierra sesión y vuelve a iniciarla.")
        }

        Diagnostics.log(buildString {
            appendLine("[credenciales] bundle ${creds.bundleVersion} · app_id=${creds.appId}")
            append("  candidatos: " + creds.candidates.joinToString { "${it.timezone}=${QobuzApi.fingerprintOf(it.secret)}" })
        })

        val track = pickValidationTrack()
        // Validate on a separate client so the live session is never mutated mid-use.
        val probe = QobuzApi(api.http).apply { appId = creds.appId; authToken = token }

        var chosen: SecretCandidate? = null
        for (c in creds.candidates) {
            probe.appSecret = c.secret
            probe.secretSource = "candidato ${c.timezone} (validando)"
            try {
                probe.getFileUrl(track.id, Quality.MP3.formatId) // logged to Diagnostics by fingerprint
                chosen = c
                break
            } catch (e: ApiException) {
                val m = e.message ?: ""
                when {
                    "request_sig" in m -> continue               // wrong secret → try the next candidate
                    "no download URL" in m -> { chosen = c; break } // HTTP 200: signature accepted, track just not streamable
                    else -> throw e                              // expired token / network → abort, save nothing
                }
            }
        }
        val sel = chosen
            ?: throw ApiException("Ningún App Secret reconstruido fue aceptado por Qobuz (request_sig). Puede que Qobuz haya cambiado el método.")

        // Save the matching pair together.
        prefs.edit()
            .putString("app_id", creds.appId)
            .putString("app_secret", sel.secret)
            .putString("web_app_id", creds.appId)
            .putString("web_app_secret", sel.secret)
            .remove("custom_app_secret")
            .apply()
        api.appId = creds.appId
        api.appSecret = sel.secret
        api.secretSource = "reproductor web (${sel.timezone}, verificado)"
        Diagnostics.log("[credenciales] GUARDADO app_id=${creds.appId} secret huella=${QobuzApi.fingerprintOf(sel.secret)} origen=reproductor web (${sel.timezone}, verificado)")
        return sel.timezone
    }

    /** A real, streamable track used only to validate a secret candidate during a refresh. */
    private fun pickValidationTrack(): Track {
        val album = api.getFeatured("new-releases", 5).firstOrNull()
            ?: api.searchAlbums("hits", 5).firstOrNull()
            ?: throw ApiException("No se pudo obtener una pista de prueba para validar el secret")
        val detail = api.getAlbum(album.id)
        return detail.tracks.firstOrNull { it.streamable } ?: detail.tracks.firstOrNull()
            ?: throw ApiException("La pista de prueba no tiene contenido reproducible")
    }

    private var playJob: Job? = null
    private var errorRetriedFor: Any? = null
    private var prefetchJob: Job? = null
    /** The next track whose address was already handed to the engine, with the quality Qobuz announced for it. */
    private var prefetched: Pair<Pair<Album, Track>, AudioQuality?>? = null

    /** The quality songs are streamed in; chosen from the quality box in the player (not the same as the download quality). */
    var playQuality by mutableStateOf(Quality.entries.firstOrNull { it.formatId == prefs.getInt("play_quality", 6) } ?: Quality.LOSSLESS); private set
    private fun playFormat() = if (carStream) Quality.MP3.formatId else playQuality.formatId

    /** Changes the listening quality and restarts the current song at the same spot in the new quality. */
    fun updatePlayQuality(q: Quality) {
        if (q == playQuality) return
        playQuality = q; prefs.edit().putInt("play_quality", q.formatId).apply()
        if (playQueue.current != null && restoredPosMs < 0) playCurrent(if (engineDelegate.isInitialized()) engine.position else 0L)
    }

    /** Address and quality to play a track from: the downloaded file when there is one that is good enough, else Qobuz. */
    private fun resolve(trackId: Long): Pair<String, AudioQuality?> {
        val local = downloads.tasks.firstOrNull { it.track.id == trackId && it.status == TaskStatus.COMPLETED && it.quality.formatId >= playFormat() && it.savedUri != null }?.savedUri
        if (local != null && runCatching { context.contentResolver.openInputStream(Uri.parse(local))?.close() ?: error("") }.isSuccess) return local to null
        val file = api.getFileUrl(trackId, playFormat())
        return file.url to file.quality
    }

    // ---- queue that survives closing the app, history, sleep timer, lyrics -----------------------------------------
    private var restoredPosMs = -1L // >= 0: a queue was restored and nothing has been started yet
    var history by mutableStateOf(QueueStore.decodeList(prefs.getString("history", null))); private set
    var sleepEndsAt by mutableLongStateOf(0L); private set
    private var sleepJob: Job? = null
    var lyrics by mutableStateOf<Lyrics?>(null); private set
    var lyricsLoading by mutableStateOf(false); private set
    private var lyricsFor = 0L
    var lyricsOffsetMs by mutableLongStateOf(prefs.getLong("lyrics_offset", 0L)); private set
    fun updateLyricsOffset(ms: Long) { lyricsOffsetMs = ms.coerceIn(-10_000, 10_000); prefs.edit().putLong("lyrics_offset", lyricsOffsetMs).apply() }

    private fun saveQueue() {
        val s = playQueue.state()
        val all = s.history + listOfNotNull(s.current) + s.upcoming
        if (all.isEmpty()) { prefs.edit().remove("queue").apply(); return }
        // Nothing started since a restore: keep the position that was restored, the engine knows nothing yet.
        val ms = if (restoredPosMs >= 0) restoredPosMs else if (engineDelegate.isInitialized()) engine.position else 0L
        // Every 5 s while playing only the position changes: the up to 300 songs are encoded again only when the queue itself changed.
        val items = all.take(300)
        val cached = savedItems
        val itemsJson = if (cached != null && cached.size == items.size && items.indices.all { items[it] === cached[it] }) savedItemsJson
            else QueueStore.encodeItemsText(items).also { savedItems = items; savedItemsJson = it }
        prefs.edit().putString("queue", QueueStore.encodeQueue(itemsJson, s.history.size.coerceAtMost(299), ms)).apply()
    }
    private var savedItems: List<Pair<Album, Track>>? = null
    private var savedItemsJson = ""

    private fun restoreQueue() {
        val saved = QueueStore.decodeQueue(prefs.getString("queue", null)) ?: return
        playQueue.set(saved.items, saved.position)
        refreshQueue()
        nowPlaying = playQueue.current
        restoredPosMs = saved.positionMs
    }

    private fun recordHistory(item: Pair<Album, Track>) {
        history = (listOf(item) + history.filter { it.second.id != item.second.id }).take(50)
        prefs.edit().putString("history", QueueStore.encodeList(history)).apply()
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { sleepEndsAt = 0L; return }
        sleepEndsAt = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            if (engineDelegate.isInitialized()) engine.pause()
            sleepEndsAt = 0L
        }
    }

    fun loadLyrics(album: Album, track: Track) {
        if (lyricsFor == track.id) return
        lyricsFor = track.id; lyrics = null; lyricsLoading = true
        viewModelScope.launch {
            // LRCLIB is often briefly overloaded: a few quick retries before giving up
            var found: Lyrics? = null
            for (attempt in 0 until 4) {
                if (attempt > 0) kotlinx.coroutines.delay(1500L * attempt)
                val r = withContext(Dispatchers.IO) { runCatching { LyricsClient.fetch(track.artist.ifBlank { album.artist }, track.title, album.title, track.durationSec) } }
                if (r.isSuccess) { found = r.getOrNull(); break }
            }
            if (lyricsFor == track.id) { lyrics = found; lyricsLoading = false }
        }
    }

    private fun refreshQueue() { queueState = playQueue.state() }

    /** Plays [items] starting at the tapped one; tracks that can't be streamed are left out. */
    /** True while the queue was started from Android Auto: songs that are not downloaded stream as MP3 (less mobile data). */
    private var carStream = false

    fun playFromCar(items: List<Pair<Album, Track>>, index: Int) { playList(items, index); carStream = true }

    /** Finished downloads, one per song, newest first. */
    fun downloadedSongs(): List<Pair<Album, Track>> =
        downloads.tasks.filter { it.status == TaskStatus.COMPLETED && it.savedUri != null }.distinctBy { it.track.id }.map { it.album to it.track }.asReversed()

    fun playList(items: List<Pair<Album, Track>>, tapped: Int) {
        carStream = false
        val (list, start) = playableQueue(items, tapped) { it.second.streamable }
        if (list.isEmpty()) return
        askNotifications = true // the "now playing" notification needs the notification permission (asked once)
        playQueue.set(list, start)
        playCurrent()
    }

    fun play(album: Album, track: Track) = playList(listOf(album to track), 0)

    /** "Play next": right after the current track (starts playing when nothing is). */
    fun playNext(album: Album, track: Track) {
        if (!track.streamable) return
        if (playQueue.current == null) { play(album, track); return }
        playQueue.insertNext(album to track); refreshQueue(); resetNext(); notice = strings.addedNext
    }

    fun addToQueue(album: Album, track: Track) {
        if (!track.streamable) return
        if (playQueue.current == null) { play(album, track); return }
        playQueue.append(album to track); refreshQueue(); resetNext(); notice = strings.addedToQueue
    }

    fun addTrackToPlaylist(pl: Playlist, track: Track) {
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { api.addTrackToPlaylist(pl.id, track.id) }; notice = strings.addedToPlaylist(pl.name) }
            catch (e: Exception) { error = e.message }
        }
    }

    private fun enqueueAll(items: List<Pair<Album, Track>>, next: Boolean) {
        val list = items.filter { it.second.streamable }
        if (list.isEmpty()) return
        if (playQueue.current == null) { playList(list, 0); return }
        if (next) list.asReversed().forEach { playQueue.insertNext(it) } else list.forEach { playQueue.append(it) }
        refreshQueue(); resetNext(); notice = strings.queuedAll(list.size)
    }

    /** What an album's or playlist's menu does with its songs: play now, play next, or add to the end of the queue. */
    fun playCollection(album: Album?, playlist: Playlist?, mode: Int) {
        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { if (album != null) albumTracks(album.id) else api.getPlaylist(playlist!!.id).tracks }
                when (mode) { 0 -> playList(items, 0); 1 -> enqueueAll(items, true); else -> enqueueAll(items, false) }
            } catch (e: Exception) { error = e.message }
        }
    }

    /** Ticks only the downloaded songs of [album] in "Para enviar" and opens the iPod screen. */
    fun sendAlbumToIpod(album: Album) {
        val done = downloads.tasks.filter { it.status == TaskStatus.COMPLETED && it.album.id == album.id }
        if (done.isEmpty()) { notice = strings.noneDownloaded; return }
        ipodTickShown(false); ipodTickTasks(done, true)
        ipodTab = 0; openIpod()
    }

    /** Marks only this downloaded song as "to send" and opens the iPod screen on that tab. */
    fun sendToIpod(task: DownloadTask) {
        ipodTickShown(false); ipodTickTasks(listOf(task), true)
        ipodTab = 0; openIpod()
    }

    /** Deletes the saved audio file and forgets the download entry. */
    fun removeDownload(task: DownloadTask) {
        val uri = task.savedUri
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { if (uri != null) getApplication<Application>().contentResolver.delete(android.net.Uri.parse(uri), null, null) } }
            downloads.delete(task)
        }
    }

    private fun playCurrent(resumeMs: Long = 0L) {
        val item = playQueue.current ?: return
        restoredPosMs = -1L
        playJob?.cancel(); prefetchJob?.cancel(); prefetched = null
        nowPlaying = item; nowQuality = null; refreshQueue()
        syncNotification()
        playJob = viewModelScope.launch {
            try {
                val (url, q) = withContext(Dispatchers.IO) { resolve(item.second.id) }
                engine.start(url)
                if (resumeMs > 0) engine.seekTo(resumeMs)
                nowQuality = q
                recordHistory(item); saveQueue()
                startPrefetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
        }
    }

    /** The engine moved on to the prepared next track by itself: gapless, or after a fade. */
    private fun advancedByEngine() {
        skipDir = 1
        val item = playQueue.next(auto = true) ?: return
        val ready = prefetched?.takeIf { it.first === item }
        // The queue was edited while the next track was already loading: what plays isn't what the queue says.
        if (ready == null) { playCurrent(); return }
        prefetched = null
        nowPlaying = item; nowQuality = ready?.second; refreshQueue()
        recordHistory(item); saveQueue()
        syncNotification()
        startPrefetch()
    }

    /** Fetches the next track's address shortly before the current one ends and gives it to the engine, so it is ready in time. */
    private fun startPrefetch() {
        prefetchJob?.cancel()
        if (playQueue.state().repeat == RepeatMode.ONE) return // the engine loops the same track itself
        val target = playQueue.peekNext() ?: return
        if (target === playQueue.current) return
        prefetchJob = viewModelScope.launch {
            while (!shouldPrefetch(engine.position, engine.duration, crossfadeMs)) delay(500)
            repeat(2) { attempt ->
                try {
                    val (url, q) = withContext(Dispatchers.IO) { resolve(target.second.id) }
                    if (playQueue.peekNext() !== target) return@launch // the queue changed while we were asking
                    engine.setNext(url)
                    prefetched = target to q
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (attempt == 0) delay(3000) // one more try; otherwise the normal path loads it when the track ends
                }
            }
        }
    }

    /** The queue changed (shuffle, repeat, removal): what was prepared as "next" may be wrong. */
    private fun resetNext() {
        if (!engineDelegate.isInitialized() || engine.isFading) return // after the fade, advancedByEngine() looks again
        engine.clearNext(); prefetched = null
        if (playQueue.current != null) startPrefetch()
    }

    /** Which way the cover slides when the song changes: 1 forward, -1 back. Set by every way of changing song (buttons, swipe, notification). */
    @Volatile var skipDir = 1

    fun next() { skipDir = 1; if (playQueue.next(auto = false) != null) playCurrent() }

    /** Like most players: after a few seconds in, "previous" restarts the track; near its start it goes back one. */
    fun previous() {
        if (engineDelegate.isInitialized() && engine.position > 3000) engine.seekTo(0)
        else { skipDir = -1; if (playQueue.previous() != null) playCurrent() }
    }

    fun jumpTo(index: Int) { skipDir = 1; if (playQueue.jump(index) != null) playCurrent() }

    /** Saves the queue and the position now: called when the app leaves the screen, and every few seconds while playing, so an update or a kill loses almost nothing. */
    fun persistQueue() = saveQueue()
    fun seekTo(ms: Long) { if (engineDelegate.isInitialized()) { engine.seekTo(ms); syncNotification() } }
    fun togglePlay() {
        if (restoredPosMs >= 0) { playCurrent(restoredPosMs); return }
        if (engineDelegate.isInitialized()) engine.togglePlay()
    }
    fun toggleShuffle() { playQueue.toggleShuffle(); refreshQueue(); resetNext(); saveQueue(); syncNotification() }
    fun cycleRepeat() {
        playQueue.cycleRepeat(); refreshQueue()
        if (engineDelegate.isInitialized()) engine.repeatOne = playQueue.repeat == RepeatMode.ONE
        resetNext(); syncNotification()
    }
    fun removeUpcoming(k: Int) { playQueue.removeUpcoming(k); refreshQueue(); resetNext(); saveQueue() }
    fun moveUpcoming(from: Int, to: Int) { playQueue.moveUpcoming(from, to); refreshQueue(); resetNext(); saveQueue() }
    fun clearUpcoming() { playQueue.clearUpcoming(); refreshQueue(); resetNext(); saveQueue() }

    /** Bit-perfect USB output (Android 14+), see [BitPerfectOutput]. */
    var bitPerfect by mutableStateOf(prefs.getBoolean("bit_perfect", false)); private set
    var bitPerfectState by mutableStateOf(BitPerfectState(BitPerfectKind.OFF)); private set
    private val bitPerfectProbe by lazy { BitPerfectOutput(context) }

    /** What the Settings line says before anything plays: is it possible, is a DAC connected? */
    fun refreshBitPerfect() {
        if (!bitPerfect) { bitPerfectState = BitPerfectState(BitPerfectKind.OFF); return }
        if (bitPerfectState.kind == BitPerfectKind.OFF || bitPerfectState.kind == BitPerfectKind.NO_USB || bitPerfectState.kind == BitPerfectKind.WAITING) bitPerfectState = bitPerfectProbe.probe()
    }

    fun updateBitPerfect(on: Boolean) {
        bitPerfect = on; prefs.edit().putBoolean("bit_perfect", on).apply()
        bitPerfectState = if (on) bitPerfectProbe.probe() else BitPerfectState(BitPerfectKind.OFF)
        if (engineDelegate.isInitialized()) { engine.bitPerfect = on; resetNext() }
        // The song that is playing was made under the old setting: start it again at the same spot.
        if (playQueue.current != null && restoredPosMs < 0 && isPlaying) playCurrent(if (engineDelegate.isInitialized()) engine.position else 0L)
    }

    fun updateCrossfade(ms: Int) {
        crossfadeMs = ms; prefs.edit().putInt("crossfade_ms", ms).apply()
        if (engineDelegate.isInitialized()) { engine.fadeMs = ms; resetNext() }
    }

    // Pages of the full-screen player that need data of their own.
    var exploreAlbums by mutableStateOf<List<Album>?>(null); private set
    var exploreTop by mutableStateOf<List<Pair<Album, Track>>?>(null); private set
    var exploreLabelAlbums by mutableStateOf<List<Album>?>(null); private set
    private var exploreLabel: String? = null
    private var exploreArtist: String? = null
    var infoDetail by mutableStateOf<AlbumDetail?>(null); private set
    private var infoAlbumId: String? = null

    /** More albums by the artist now playing (a normal search, filtered to that artist). */
    fun loadExplore(artist: String) {
        if (artist == exploreArtist) return
        exploreArtist = artist; exploreAlbums = null; exploreTop = null
        viewModelScope.launch {
            val top = withContext(Dispatchers.IO) {
                runCatching { api.searchTracks(artist, 30).filter { it.second.artist.contains(artist, ignoreCase = true) && it.second.streamable }.take(10) }.getOrDefault(emptyList())
            }
            if (exploreArtist == artist) exploreTop = top
        }
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { api.searchAlbums(artist, 30).filter { it.artist.contains(artist, ignoreCase = true) }.take(12) }.getOrDefault(emptyList())
            }
            if (exploreArtist == artist) exploreAlbums = found
        }
    }

    /** More albums from [label] (a search for its name, kept to albums that carry that label). */
    fun loadLabelAlbums(label: String, notId: String) {
        if (label == exploreLabel) return
        exploreLabel = label; exploreLabelAlbums = null
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { api.searchAlbums(label, 40).filter { it.id != notId && it.label.equals(label, ignoreCase = true) }.take(12) }.getOrDefault(emptyList())
            }
            if (exploreLabel == label) exploreLabelAlbums = found
        }
    }

    /** The full album behind the playing track: its credits and release details. */
    fun loadInfo(albumId: String) {
        if (albumId == infoAlbumId) return
        infoAlbumId = albumId; infoDetail = null
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { api.getAlbum(albumId) }.getOrNull() }
            if (infoAlbumId == albumId) infoDetail = found
        }
    }

    /** What the "now playing" notification and the media session (lock screen, headset buttons) call. */
    private val playbackControls: PlaybackControls by lazy { object : PlaybackControls {
        override val nowPlaying get() = this@MainViewModel.nowPlaying
        override val isPlaying get() = this@MainViewModel.isPlaying
        override val positionMs get() = if (engineDelegate.isInitialized()) engine.position else 0L
        override val durationMs get() = if (engineDelegate.isInitialized()) engine.duration else -1L
        override fun togglePlay() = this@MainViewModel.togglePlay()
        override fun play() { if (restoredPosMs >= 0) playCurrent(restoredPosMs) else if (engineDelegate.isInitialized()) engine.resume() }
        override fun pause() { if (engineDelegate.isInitialized()) engine.pause() }
        override fun next() = this@MainViewModel.next()
        override fun previous() = this@MainViewModel.previous()
        override fun seekTo(ms: Long) = this@MainViewModel.seekTo(ms)
        override fun stopPlayback() = this@MainViewModel.stopPlayback()
        override val lyrics get() = this@MainViewModel.lyrics
        override val lyricsOffsetMs get() = this@MainViewModel.lyricsOffsetMs
        override val shuffleOn get() = queueState.shuffle
        override val repeatMode get() = when (queueState.repeat) { RepeatMode.OFF -> 0; RepeatMode.ALL -> 1; RepeatMode.ONE -> 2 }
        override fun toggleShuffle() = this@MainViewModel.toggleShuffle()
        override fun cycleRepeat() = this@MainViewModel.cycleRepeat()
        override fun seekBy(ms: Long) {
            if (!engineDelegate.isInitialized()) return
            val len = engine.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            this@MainViewModel.seekTo((engine.position + ms).coerceIn(0, (len - 500).coerceAtLeast(0)))
        }
        override fun ensureLyrics() { nowPlaying?.let { (a, t) -> loadLyrics(a, t) } }
        override fun playFromHistory(index: Int) { if (index in history.indices) playFromCar(history, index) }
        override fun downloadedSongs() = this@MainViewModel.downloadedSongs()
        override fun playFromCar(items: List<Pair<Album, Track>>, index: Int) = this@MainViewModel.playFromCar(items, index)
    } }

    init {
        QbdlxApp.playback = playbackControls; restoreQueue()
        viewModelScope.launch { while (true) { delay(5000); if (isPlaying) saveQueue() } }
    }

    private fun syncNotification() = PlaybackService.refresh(getApplication())

    private fun stopPlayback() {
        playJob?.cancel(); prefetchJob?.cancel(); prefetched = null
        if (engineDelegate.isInitialized()) engine.stop()
        playQueue.clear(); refreshQueue()
        nowPlaying = null; nowQuality = null; isPlaying = false; playerOpen = false; restoredPosMs = -1L
        prefs.edit().remove("queue").apply()
        syncNotification()
        exploreArtist = null; exploreAlbums = null; exploreTop = null; exploreLabel = null; exploreLabelAlbums = null; infoAlbumId = null; infoDetail = null
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true; error = null
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
                signingIn = false
            }
        }
    }
}

/** The simultaneous-download limits offered in Settings. */
val MAX_CONCURRENT_OPTIONS = listOf(1, 2, 3, 5, 10)
private const val IPOD_NOTIF_ID = 4217

private const val IPOD_BATCH = 40
