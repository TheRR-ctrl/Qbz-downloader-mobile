package com.qbdlx.mobile

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/** The language picked in Settings; [SYSTEM] follows the phone. */
enum class Language(val code: String) {
    SYSTEM("system"), ENGLISH("en"), SPANISH("es");

    fun resolve(): Strings = when (this) {
        ENGLISH -> English
        SPANISH -> Spanish
        SYSTEM -> if (Locale.getDefault().language == "es") Spanish else English
    }

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/** Every user-visible text in the app, so it can be switched without recreating the activity. */
interface Strings {
    // Login
    val logInWithQobuz: String
    val signingIn: String

    // Web login
    val logInToQobuz: String
    val loadingSignIn: String
    val socialNotSupported: String

    // Navigation
    fun tab(tab: Tab): String
    val back: String

    // Home
    val discover: String
    val qobuzPlaylists: String
    val all: String
    fun section(key: String): String
    fun discoverFailed(reason: String?): String

    // Search
    val search: String
    val searchAlbums: String
    val searchHint: String
    val sortBy: String
    val searchLibrary: String
    val recentSearches: String
    val recentSearchesClear: String
    val localFolders: String
    val localFolderAdd: String
    val localFolderRemove: String
    val localFoldersEmpty: String
    fun localPlayAll(n: Int): String
    val sortAdded: String
    val sortTitle: String
    val sortArtist: String
    val sortNewest: String
    val sortOldest: String
    fun searchAlbumsTab(n: Int): String
    fun searchSongsTab(n: Int): String

    // Albums, playlists, tracks
    fun tracks(n: Int): String
    val download: String
    val downloadAlbum: String
    val downloaded: String
    val playAll: String
    val noneDownloaded: String
    val details: String
    val detailTracks: String
    val detailOwner: String
    fun queuedAll(n: Int): String
    fun addedToPlaylist(name: String): String
    val playNext: String
    val addToQueue: String
    val goToAlbum: String
    val goToArtist: String
    val sendToIpod: String
    val shareTrack: String
    val copyLink: String
    val linkCopied: String
    val trackDetails: String
    val addToPlaylist: String
    val addedNext: String
    val addedToQueue: String
    val downloadAgain: String
    val removeDownload: String
    val removeDownloadAsk: String
    val sentToIpod: String
    val noPlaylists: String
    val close: String
    val detailQuality: String
    val detailDuration: String
    val detailAlbum: String
    val detailArtist: String
    val detailYear: String
    val detailFile: String
    val notDownloaded: String
    val play: String
    val playPause: String

    // Downloads
    val inProgress: String
    val completed: String
    val queued: String
    val retry: String
    fun queuedTracks(n: Int): String
    val nothingToDownload: String
    val badSignature: String

    // Library
    val library: String
    val playlists: String
    val albums: String
    val tracksTab: String
    val artistsTab: String
    val topSongs: String
    val epsSingles: String
    val liveAlbums: String
    val similarArtists: String
    val seeAll: String
    val readMore: String
    val nothingHere: String
    val downloadAll: String
    val everythingTitle: String
    val everythingHelp: String
    val everythingConfirm: String
    val everythingButton: String
    val refresh: String

    // Quality
    val selectQuality: String
    val chooseQuality: String
    val qualityOnWifi: String
    val qualityOnData: String
    val cancel: String
    fun qualityDescription(q: Quality): String

    // Settings
    val settings: String
    val defaultQuality: String
    val defaultQualityHelp: String
    val askAlways: String
    val askAlwaysDesc: String
    val amoledBlack: String
    val language: String
    fun languageName(l: Language): String
    val save: String
    fun signedInAs(name: String): String
    val logOut: String
    val disclaimer: String

    // Diagnostics
    val diagOpen: String
    val diagTitle: String
    val diagIntro: String
    val diagRunTest: String
    val diagTestDone: String
    val diagCopy: String
    val diagCopied: String
    val diagClear: String
    val diagCleared: String
    val diagEmpty: String

    // Qobuz credentials refresh
    val updateCreds: String
    val updateCredsHelp: String
    fun credsUpdated(timezone: String): String
    val credsAutoUpdated: String

    // Download manager
    val dlPause: String
    val dlResume: String
    val dlCancel: String
    val dlDelete: String
    val dlPauseAll: String
    val dlResumeAll: String
    val dlRetryFailed: String
    val dlClearDone: String
    val dlTabFailed: String
    val dlDeleteHint: String
    val statusPaused: String
    val statusFailed: String
    val statusCanceled: String
    fun dlSummary(done: Int, total: Int, active: Int): String
    fun noteRetrying(n: Int, max: Int): String
    val noteRefreshing: String
    val noteSystemLimit: String
    val noteWaitingNetwork: String
    fun alreadyQueued(n: Int): String
    fun queuedTracksSkipped(added: Int, skipped: Int): String
    fun preparingAlbums(n: Int): String
    fun confirmBulkAlbums(n: Int): String
    fun confirmBulkPlaylists(n: Int): String
    val confirmBulkYes: String
    fun preparingPlaylists(n: Int): String
    fun bulkDone(added: Int, skipped: Int, failed: Int): String
    val maxConcurrentTitle: String
    val maxConcurrentHelp: String

    // Download folder
    val folderTitle: String
    val folderHelp: String
    val folderDefault: String
    fun folderCurrent(isInternal: Boolean, volume: String, path: String): String
    val folderChoose: String
    val knownTitle: String
    val dupScan: String
    val crashShare: String
    val dupScanning: String
    val dupNone: String
    val dupDownloads: String
    val dupOther: String
    fun dupTitle(n: Int): String
    fun dupDelete(n: Int): String
    fun dupDeleted(done: Int, failed: Int): String
    val dupHint: String
    val knownHelp: String
    val knownChoose: String
    val knownRemove: String
    val knownSaved: String
    val folderResetButton: String
    val folderSaved: String
    val folderResetDone: String
    fun folderNotWritable(reason: String?): String
    val folderBanner: String
    val folderBannerChoose: String
    val folderBannerDismiss: String
    val wifiOnlyTitle: String
    val lyricsListTitle: String
    val lyricsListDesc: String
    val lyricsListEmpty: String
    val lyricsListGathering: String
    val lyricsListMissingApp: String
    val wifiOnlyDesc: String
    val skipExistingTitle: String
    val skipExistingDesc: String
    val noteExisted: String
    val noteReplaced: String
    val upgradeExistingTitle: String
    val upgradeExistingDesc: String
    val playerExplore: String
    val playerInfo: String
    val playerQueue: String
    val queueTitle: String
    val queueCurrent: String
    val queueNext: String
    val queueHistory: String
    val queueClear: String
    val queueEmpty: String
    val removeFromQueue: String
    val exploreTitle: String
    fun exploreMoreOf(artist: String): String
    val exploreEmpty: String
    val lyricsTitle: String
    val lyricsSync: String
    val lyricsResync: String
    val shareDrive: String
    val shareHint: String
    val shareWindowHour: String
    val shareWindowToday: String
    val shareWindowWeek: String
    val shareWindowAll: String
    val shareNone: String
    fun shareButton(n: Int): String
    val shareAll: String
    val shareTooMany: String
    fun shareProgress(done: Int, total: Int): String
    fun shareNext(n: Int): String
    fun shareUnreadable(n: Int): String
    val lyricsNone: String
    val pressBackAgain: String
    val sleepTimer: String
    val sleepOff: String
    fun sleepMinutes(n: Int): String
    fun sleepRemaining(min: Int): String
    val equalizer: String
    val noEqualizer: String
    val history: String
    fun exploreWith(artist: String): String
    val exploreTopTracks: String
    val exploreLabelOf: String
    fun exploreLabelName(label: String): String
    val creditsTitle: String
    val noCredits: String
    val aboutRelease: String
    val infoGenre: String
    val infoReleaseDate: String
    val infoTracks: String
    val infoDuration: String
    val infoQuality: String
    val infoLabel: String
    val shuffle: String
    val repeat: String
    val previous: String
    val next: String
    val more: String
    val viewAlbum: String
    val playbackSection: String
    val crossfadeTitle: String
    val crossfadeDesc: String
    val crossfadeOff: String
    val bitPerfectTitle: String
    val usbDiagButton: String
    val usbDirectTitle: String
    val usbDirectDesc: String
    val usbDirectNoDevice: String
    val usbDiagCopy: String
    val usbDiagClose: String
    val bitPerfectDesc: String
    fun bitPerfectStatus(st: BitPerfectState): String
    fun seconds(n: Int): String
    val importEntry: String
    val importTitle: String
    val importHelp: String
    val importPick: String
    val importBadFile: String
    val importEmpty: String
    val importSure: String
    val importCheck: String
    val importMissing: String
    val importFix: String
    val importForget: String
    val importForgotten: String
    val importRetry: String
    val importWorking: String
    fun importDeleteFromIpod(n: Int): String
    fun ipodClearButton(n: Int): String
    val ipodClearTitle: String
    fun ipodClearWarning(n: Int): String
    val ipodClearType: String
    val importDeleteTitle: String
    fun importDeleteWarning(n: Int): String
    val importDeleteConfirm: String
    fun importDeleteDone(n: Int): String
    fun importDeleteFailed(why: String): String
    fun importReplaceOnIpod(n: Int): String
    fun importPlaylistReplace(n: Int): String
    val importNoIpod: String
    val importReplaceStarted: String
    val importReplaceNothing: String
    val importSearchAgain: String
    val importArtistField: String
    val importTitleField: String
    val importNothingFound: String
    val importSearching: (Int, Int) -> String
    val importDownload: (Int) -> String
    val importPickBest: String
    val importFavorites: (Int) -> String
    val importFavoritesDone: (Int) -> String
    val ipodLookup: String
    val ipodReplaceLabel: String
    val ipodLyricsLabel: String
    fun ipodLyricsDone(n: Int): String
    val ipodFixAlbumsButton: String
    fun ipodFixAlbumsDone(n: Int): String
    val ipodTaskFixing: String
    fun ipodTaskDeleting(n: Int): String
    val ipodTaskIndeterminateHint: String
    val stopButton: String
    val stoppingNow: String
    fun ipodStoppedSend(sent: Int, left: Int): String
    fun ipodLyricsStopped(added: Int): String
    val importStopped: String
    fun ipodFixAlbumsFailed(error: String): String
    val ipodPlaylistButton: String
    val ipodPlaylistTitle: String
    val ipodPlaylistLegend: String
    val aiMixButton: String
    val aiMixTitle: String
    val aiMixHint: String
    val aiIdeasButton: String
    val aiIdeasAsking: String
    val aiIdeasStop: String
    val aiIdeasStopped: String
    val aiIdeasLocal: String
    val aiMixIntro: String
    val aiSeedPick: String
    fun aiSeedChosen(t: String): String
    val aiSeedTitle: String
    val aiSeedSearch: String
    val aiSeedClear: String
    fun aiSizeLabel(n: Int): String
    val aiGenerate: String
    val aiPreviewTitle: String
    val aiPreviewHint: String
    fun aiPreviewSave(n: Int): String
    val aiPreviewRegen: String
    fun aiPreviewSongs(n: Int): String
    val aiMixCreate: String
    val aiMixTooFew: String
    val aiSaveNeedId: String
    val aiMixAsking: String
    val aiMixNoKey: String
    val aiMixBadAnswer: String
    val aiMixByAi: String
    val aiMixLocal: String
    val aiKeyTitle: String
    val aiKeyHow: String
    val aiKeySteps: String
    val aiKeyOpen: String
    val aiKeyPaste: String
    val aiKeySave: String
    val aiKeyTesting: String
    val aiKeyChange: String
    val aiKeyForget: String
    val aiKeyPrivacy: String
    fun aiKeyProblem(kind: String): String
    val ipodPlaylistNone: String
    val ipodPlaylistLoadFailed: String
    fun ipodPlaylistDownloading(done: Int, total: Int): String
    val ipodPlaylistDownloadStopped: String
    fun ipodPlaylistLoading(name: String, i: Int, n: Int): String
    fun ipodPlaylistMaking(name: String, i: Int, n: Int): String
    fun ipodPlaylistSendN(n: Int): String
    fun ipodPlaylistDone(name: String, added: Int, missing: Int, error: String?): String
    fun ipodLyricsSearching(done: Int, total: Int): String
    fun ipodLyricsNothing(n: Int): String
    fun ipodLyricsWriting(done: Int, total: Int): String
    fun ipodAddLyricsButton(n: Int): String
    fun ipodLyricsDeviceDone(added: Int, skipped: Int, failed: Int): String
    fun ipodLyricsFailed(added: Int, error: String): String
    val ipodReport: String
    val toTop: String
    val shareGeneric: String
    fun ipodInterrupted(left: Int, reason: String): String
    fun ipodResumeBanner(n: Int): String
    val ipodResumeButton: String
    val ipodDiscard: String
    val ipodNotReadyYet: String
    val ipodNothingLeft: String
    val nowButton: String
    val nowAccess: String
    val nowIdentifying: String
    val nowTapToDownload: String
    val nowAllow: String
    val nowNothing: String
    fun nowSearching(title: String): String
    fun nowNotFound(title: String): String
    fun nowDownloading(title: String, artist: String, sure: Boolean): String
    val ipodSendDownloaded: String
    val ipodChooseFirst: String
    fun ipodNothingToSend(noMp3: Boolean): String
    val ipodNotifChannel: String
    fun ipodNotifTitle(done: Int, total: Int): String
    fun ipodNotifDone(sent: Int, failed: Int): String
    val importToPlaylist: (Int) -> String
    val importPlaylistDone: (String, Int) -> String
    val importPlaylistFailed: (String) -> String
    val ipodEntry: String
    val ipodBusy: String
    val ipodTitle: String
    val ipodHelp: String
    val ipodChoose: String
    val ipodChange: String
    val ipodNone: String
    fun ipodProblem(kind: com.qbdlx.mobile.ipod.IpodDevice.Problem, detail: String?): String
    val ipodTabSend: String
    val ipodTabOn: String
    val ipodViewList: String
    val ipodViewRows: String
    val ipodViewGrid: String
    val ipodViewLibrary: String
    val ipodViewArtists: String
    fun ipodLibArtistCount(albums: Int, songs: Int): String
    val ipodTabPlaylists: String
    fun ipodPlaylistSongs(n: Int, missing: Int): String
    val ipodPlaylistsEmpty: String
    val ipodSelectPlaylist: String
    val ipodSearch: String
    val ipodDeleteSelected: String
    fun ipodSelectedCount(n: Int): String
    val ipodSelectAllVisible: String
    val ipodDeleteSelectedTitle: String
    fun ipodDeleteSelectedWarning(n: Int): String
    fun ipodDeleteListsNote(n: Int): String
    val ipodDeleteListCheck: String
    fun ipodSummary(on: Int, pending: Int, already: Int): String
    fun ipodSpace(free: String, total: String): String
    val ipodSpaceUnknown: String
    val ipodArtistAll: String
    val ipodDateLabels: List<String>
    val ipodTickAll: String
    val ipodGroupTitle: String
    val ipodGroupLabels: List<String>
    val ipodUnknownGroup: String
    val ipodTickNone: String
    val ipodUsbNotFound: String
    val ipodUsbNoSerial: String
    val ipodUsbDenied: String
    val ipodNoMp3: String
    val ipodPending: String
    fun ipodAlready(n: Int): String
    fun ipodSendButton(n: Int): String
    fun ipodSending(done: Int, total: Int): String
    fun ipodDone(sent: Int, failed: Int): String
    fun ipodCovers(added: Int, note: String?): String
    val ipodRestore: String
    val ipodRestoreDone: String
    val ipodNoBackup: String
    val ipodSafeEject: String

    // Download notification
    val dlNotifChannel: String
    fun dlNotifTitle(done: Int, total: Int): String
    fun dlNotifDone(n: Int, failed: Int): String
}

object English : Strings {
    override val logInWithQobuz = "Log in with Qobuz"
    override val signingIn = "Signing in…"

    override val logInToQobuz = "Log in to Qobuz"
    override val loadingSignIn = "Loading Qobuz sign-in…"
    override val socialNotSupported = "Google, Apple and Facebook sign-in don't work inside the app. Go back and sign in " +
        "with your Qobuz email and password. If you only used Google, tap \"Forgot your password?\" on the Qobuz " +
        "sign-in page to set one."

    override fun tab(tab: Tab) = when (tab) {
        Tab.Home -> "Home"
        Tab.Search -> "Search"
        Tab.Library -> "Library"
        Tab.Downloads -> "Downloads"
        Tab.Settings -> "Settings"
    }
    override val back = "Back"

    override val discover = "Discover"
    override val qobuzPlaylists = "Qobuz Playlists"
    override val all = "All"
    override fun section(key: String) = when (key) {
        "new_releases" -> "New Releases"
        "album_of_the_week" -> "Album of the Week"
        "qobuzissims" -> "Qobuzissime"
        "ideal_discography" -> "Ideal Discography"
        "most_streamed" -> "Most Streamed"
        "press_awards" -> "Press Awards"
        else -> key
    }
    override fun discoverFailed(reason: String?) = "Couldn't load Discover ($reason); showing new releases only"

    override val search = "Search"
    override val searchAlbums = "Search albums"
    override val searchHint = "Search albums and songs"
    override val sortBy = "Sort"
    override val searchLibrary = "Search your library"
    override val recentSearches = "Recent searches"
    override val recentSearchesClear = "Clear recent searches"
    override val localFolders = "Folders"
    override val localFolderAdd = "Add a folder from this phone"
    override val localFolderRemove = "Remove folder"
    override val localFoldersEmpty = "Add a folder with your music to play its files here."
    override fun localPlayAll(n: Int) = "Play all ($n)"
    override val sortAdded = "As added"
    override val sortTitle = "Title A–Z"
    override val sortArtist = "Artist A–Z"
    override val sortNewest = "Newest first"
    override val sortOldest = "Oldest first"
    override fun searchAlbumsTab(n: Int) = "Albums ($n)"
    override fun searchSongsTab(n: Int) = "Songs ($n)"

    override fun tracks(n: Int) = if (n == 1) "1 track" else "$n tracks"
    override val download = "Download"
    override val downloadAlbum = "Download album"
    override val downloaded = "Downloaded"
    override val playAll = "Play"
    override val noneDownloaded = "None of its songs are downloaded"
    override val details = "Details"
    override val detailTracks = "Tracks"
    override val detailOwner = "Created by"
    override fun queuedAll(n: Int) = "Added $n songs to the queue"
    override fun addedToPlaylist(name: String) = "Added to $name"
    override val playNext = "Play next"
    override val addToQueue = "Add to queue"
    override val goToAlbum = "Go to album"
    override val goToArtist = "Go to artist"
    override val sendToIpod = "Send to iPod"
    override val shareTrack = "Share"
    override val copyLink = "Copy link"
    override val linkCopied = "Link copied"
    override val trackDetails = "Track details"
    override val addToPlaylist = "Add to playlist"
    override val addedNext = "Plays next"
    override val addedToQueue = "Added to the queue"
    override val downloadAgain = "Download again"
    override val removeDownload = "Remove download"
    override val removeDownloadAsk = "Delete the downloaded file from the device?"
    override val sentToIpod = "Marked to send to the iPod"
    override val noPlaylists = "You have no playlists yet"
    override val close = "Close"
    override val detailQuality = "Quality"
    override val detailDuration = "Duration"
    override val detailAlbum = "Album"
    override val detailArtist = "Artist"
    override val detailYear = "Year"
    override val detailFile = "File"
    override val notDownloaded = "Not downloaded"
    override val play = "Play"
    override val playPause = "Play/pause"

    override val inProgress = "In Progress"
    override val completed = "Completed"
    override val queued = "Queued"
    override val retry = "Retry"
    override fun queuedTracks(n: Int) = if (n == 1) "1 track added to Downloads" else "$n tracks added to Downloads"
    override val nothingToDownload = "Nothing to download"
    override val badSignature = "Qobuz rejected the download signature: the App Secret doesn't match the App ID. Set the right one in Settings."

    override val library = "Library"
    override val playlists = "Playlists"
    override val albums = "Albums"
    override val tracksTab = "Tracks"
    override val artistsTab = "Artists"
    override val topSongs = "Most listened"
    override val epsSingles = "EPs & Singles"
    override val liveAlbums = "Live"
    override val similarArtists = "Similar artists"
    override val seeAll = "See all"
    override val readMore = "READ MORE"
    override val nothingHere = "Nothing here yet"
    override val downloadAll = "Download all"
    override val everythingTitle = "Download my whole account"
    override val everythingHelp = "Queues every favorite track, every favorite album and every playlist of your Qobuz account, in the quality chosen above. Songs already downloaded are skipped."
    override val everythingConfirm = "Download EVERYTHING in your account? It can be many gigabytes."
    override val everythingButton = "Download everything"
    override val refresh = "Refresh"

    override val selectQuality = "Select Quality"
    override val chooseQuality = "Choose your preferred audio quality"
    override val qualityOnWifi = "On Wi-Fi"
    override val qualityOnData = "On mobile data"
    override val cancel = "Cancel"
    override fun qualityDescription(q: Quality) = when (q) {
        Quality.HI_RES_MAX -> "24-bit up to 192kHz"
        Quality.HI_RES -> "24-bit up to 96kHz"
        Quality.LOSSLESS -> "16-bit 44.1kHz"
        Quality.MP3 -> "320kbps"
    }

    override val settings = "Settings"
    override val defaultQuality = "Default quality"
    override val defaultQualityHelp = "Downloads start right away in the quality you pick here."
    override val askAlways = "Ask every time"
    override val askAlwaysDesc = "Choose the quality for each download"
    override val amoledBlack = "AMOLED black"
    override val language = "Language"
    override fun languageName(l: Language) = when (l) {
        Language.SYSTEM -> "System"
        Language.ENGLISH -> "English"
        Language.SPANISH -> "Español"
    }
    override val save = "Save"
    override fun signedInAs(name: String) = "Signed in as ${name.ifBlank { "Qobuz user" }}"
    override val logOut = "Log out"
    override val disclaimer = "QBDLX is not endorsed, certified or approved by Qobuz. Use it according to the Qobuz API Terms of Use."

    override val diagOpen = "Qobuz Diagnostics"
    override val diagTitle = "Qobuz Diagnostics"
    override val diagIntro = "Captures what Qobuz really answers at track/getFileUrl when a download fails " +
        "(HTTP code, status, message and signature data). Passwords, App Secret, tokens and signed URLs are hidden. " +
        "Try a download, then come back and copy the report."
    override val diagRunTest = "Run test now"
    override val diagTestDone = "Test finished — see the report below"
    override val diagCopy = "Copy"
    override val diagCopied = "Diagnostics copied to clipboard"
    override val diagClear = "Clear"
    override val diagCleared = "Diagnostics cleared"
    override val diagEmpty = "No diagnostics yet. Run the test above, or try downloading a song, then come back here."

    override val updateCreds = "Update Qobuz credentials"
    override val updateCredsHelp = "Re-reads the official web player to get a fresh App ID + App Secret, checks them against a real track, and saves them together. Use this if downloads fail with \"Invalid Request Signature\"."
    override fun credsUpdated(timezone: String) = "Qobuz credentials updated and verified (zone: $timezone)"
    override val credsAutoUpdated = "Qobuz credentials were renewed automatically; retrying the download"

    override val dlPause = "Pause"
    override val dlResume = "Resume"
    override val dlCancel = "Cancel"
    override val dlDelete = "Remove from list"
    override val dlPauseAll = "Pause all"
    override val dlResumeAll = "Resume all"
    override val dlRetryFailed = "Retry failed"
    override val dlClearDone = "Clear completed"
    override val dlTabFailed = "Failed"
    override val dlDeleteHint = "Removing an item only takes it off this list; the music file stays on your phone."
    override val statusPaused = "Paused"
    override val statusFailed = "Failed"
    override val statusCanceled = "Canceled"
    override fun dlSummary(done: Int, total: Int, active: Int) = "$done of $total done · $active active"
    override fun noteRetrying(n: Int, max: Int) = "Connection problem, retrying ($n/$max)…"
    override val noteRefreshing = "Renewing Qobuz credentials…"
    override val noteSystemLimit = "Paused by Android's background limit. Tap Resume."
    override val noteWaitingNetwork = "No connection. Waiting to continue…"
    override fun alreadyQueued(n: Int) = if (n == 1) "Already queued or downloaded (remove it from Downloads to repeat it)"
        else "$n tracks were already queued or downloaded"
    override fun queuedTracksSkipped(added: Int, skipped: Int) = "${queuedTracks(added)} ($skipped already queued or downloaded)"
    override fun confirmBulkAlbums(n: Int) = "Download all tracks of $n album" + (if (n == 1) "?" else "s?")
    override fun confirmBulkPlaylists(n: Int) = "Download all tracks of $n playlist" + (if (n == 1) "?" else "s?")
    override val confirmBulkYes = "Download"
    override fun preparingAlbums(n: Int) = if (n == 1) "Preparing 1 album…" else "Preparing $n albums…"
    override fun preparingPlaylists(n: Int) = if (n == 1) "Preparing 1 playlist…" else "Preparing $n playlists…"
    override fun bulkDone(added: Int, skipped: Int, failed: Int) = buildString {
        append(if (added == 1) "1 track added to Downloads" else "$added tracks added to Downloads")
        if (skipped > 0) append(" · $skipped already queued or downloaded")
        if (failed > 0) append(" · $failed couldn't be read")
    }
    override val maxConcurrentTitle = "Simultaneous downloads"
    override val maxConcurrentHelp = "How many tracks download at the same time. Lowering it never interrupts a running download."

    override val folderTitle = "Download folder"
    override val folderHelp = "Where new downloads are saved, with a folder per artist and album. Without a choice, Music/${DefaultFolder.name} is used. " +
        "Android doesn't allow choosing the storage root or the Download folder."
    override val folderDefault get() = "Default: Music/${DefaultFolder.name}"
    override fun folderCurrent(isInternal: Boolean, volume: String, path: String) =
        (if (isInternal) "Internal storage" else "SD card ($volume)") + if (path.isEmpty()) "" else "/$path"
    override val folderChoose = "Choose folder"
    override val knownTitle = "Also check another folder"
    override val dupScan = "Find songs saved twice"
    override val crashShare = "Share the last error report"
    override val dupScanning = "Searching…"
    override val dupNone = "No songs saved twice were found."
    override val dupDownloads = "Downloads"
    override val dupOther = "Other folder"
    override fun dupTitle(n: Int) = "Saved twice ($n)"
    override fun dupDelete(n: Int) = "Delete $n ticked"
    override fun dupDeleted(done: Int, failed: Int) = if (failed == 0) "Deleted $done files" else "Deleted $done files, $failed could not be deleted"
    override val dupHint = "Ticked copies will be deleted for good. Copies in the other folder are never touched. Check the list first: different songs with the same title may appear here."
    override val knownHelp = "Songs already in this folder (any layout) are skipped, never downloaded again. The app only reads it. Needs \"Skip files that already exist\" on."
    override val knownChoose = "Choose folder to check"
    override val knownRemove = "Remove"
    override val knownSaved = "Folder added: its songs will be skipped"
    override val folderResetButton = "Use the default folder"
    override val folderSaved = "Download folder saved"
    override val folderResetDone = "Using the default folder"
    override fun folderNotWritable(reason: String?) = "Can't save files in that folder" + if (reason.isNullOrBlank()) "" else ": $reason"
    override val folderBanner get() = "The chosen download folder isn't available. Downloads are going to Music/${DefaultFolder.name} meanwhile."
    override val folderBannerChoose = "Choose another"
    override val folderBannerDismiss = "Got it"
    override val wifiOnlyTitle = "Download only on Wi-Fi"
    override val lyricsListTitle = "Send song list to Q Lyrics"
    override val lyricsListDesc = "Q Lyrics downloads the lyrics of your downloads, favorite tracks, favorite albums and playlists ahead of time, so they are ready when the song plays."
    override val lyricsListGathering = "Collecting the songs of your playlists and albums…"
    override val lyricsListEmpty = "No downloaded or favorite songs to send yet."
    override val lyricsListMissingApp = "Install Q Lyrics first."
    override val wifiOnlyDesc = "Downloads wait while you are on mobile data and carry on by themselves when Wi-Fi is back."
    override val skipExistingTitle = "Skip files that already exist"
    override val skipExistingDesc = "Before downloading a track, check whether its file is already in the download folder and skip it if so."
    override val noteExisted = "Already in the folder, skipped"
    override val noteReplaced = "Replaced with a higher quality version"
    override val upgradeExistingTitle = "Replace with higher quality"
    override val upgradeExistingDesc = "If the saved file has a lower quality than the one being downloaded (for example MP3 or CD quality and you now choose Hi-Res), replace it. It is only replaced once the new file has downloaded completely and is checked to be better. Needs \"Skip files that already exist\"."
    override val playerExplore = "Explore"
    override val playerInfo = "Info"
    override val playerQueue = "Queue"
    override val queueTitle = "Play queue"
    override val queueCurrent = "Current"
    override val queueNext = "Up next"
    override val queueHistory = "History"
    override val queueClear = "Clear queue"
    override val queueEmpty = "Nothing up next"
    override val removeFromQueue = "Remove from queue"
    override val exploreTitle = "Explore"
    override fun exploreMoreOf(artist: String) = "More from $artist"
    override val exploreEmpty = "Nothing to show"
    override val lyricsTitle = "Lyrics"
    override val lyricsSync = "Sync"
    override val lyricsResync = "Resync"
    override val shareDrive = "Share to Google Drive"
    override val shareHint = "Pick the songs to share. Songs downloaded close together are grouped; tap a group to see them. Drive asks which folder to save to."
    override val shareWindowHour = "Last hour"
    override val shareWindowToday = "Today"
    override val shareWindowWeek = "7 days"
    override val shareWindowAll = "All"
    override val shareNone = "No downloaded songs in this period"
    override fun shareButton(n: Int) = if (n == 0) "Share" else "Share $n"
    override val shareAll = "Select all"
    override val shareTooMany = "Songs go in batches of 100: when Drive finishes, come back here for the next batch"
    override fun shareProgress(done: Int, total: Int) = "Sent $done of $total. When Drive finishes, send the next batch."
    override fun shareNext(n: Int) = "Send next $n"
    override fun shareUnreadable(n: Int) = "$n files skipped: this app can no longer read them (moved or replaced by another app)"
    override val lyricsNone = "No lyrics found for this song"
    override val pressBackAgain = "Press back again to exit"
    override val sleepTimer = "Sleep timer"
    override val sleepOff = "Turn off timer"
    override fun sleepMinutes(n: Int) = "$n minutes"
    override fun sleepRemaining(min: Int) = "Sleep timer: $min min left"
    override val equalizer = "Equalizer"
    override val noEqualizer = "No equalizer app found on this phone"
    override val history = "History"
    override fun exploreWith(artist: String) = "With $artist"
    override val exploreTopTracks = "MOST PLAYED"
    override val exploreLabelOf = "More from the label"
    override fun exploreLabelName(label: String) = label
    override val creditsTitle = "Credits"
    override val noCredits = "No credits available for this track"
    override val aboutRelease = "About the release"
    override val infoGenre = "Genre"
    override val infoReleaseDate = "Release date"
    override val infoTracks = "Tracks"
    override val infoDuration = "Duration"
    override val infoQuality = "Quality"
    override val infoLabel = "Label"
    override val shuffle = "Shuffle"
    override val repeat = "Repeat"
    override val previous = "Previous"
    override val next = "Next"
    override val more = "More"
    override val viewAlbum = "Go to album"
    override val playbackSection = "Playback"
    override val crossfadeTitle = "Fade between songs"
    override val crossfadeDesc = "Songs always follow each other without a gap. With a fade, the end of one song fades out while the next fades in. Off by default; very short songs fade less."
    override val crossfadeOff = "Off"
    override val bitPerfectTitle = "Bit-perfect USB output"
    override val usbDiagButton = "Diagnose USB output"
    override val usbDirectTitle = "Direct USB DAC driver"
    override val usbDirectDesc = "Talks to the DAC itself over USB, skipping Android's mixer, so the song reaches it exactly as it is (works even where Android offers no bit-perfect mode). Experimental. Android asks for USB permission; a DAC with both USB modes works best in the one that lists your rate (44.1/48 kHz: UAC1; hi-res: UAC2). Volume is then the DAC's own. No fade or equalizer while on."
    override val usbDirectNoDevice = "No USB DAC found yet: plug it in and accept the permission."
    override val usbDiagCopy = "Copy"
    override val usbDiagClose = "Close"
    override val bitPerfectDesc = "Sends each song to a USB DAC at its own sample rate and bit depth, without Android resampling it. Needs Android 14 or newer and a USB DAC. Turns off the fade and gapless changes of sample rate; for 24-bit pick a Hi-Res quality in the player. Equalizers and sound effects must be off."
    override fun bitPerfectStatus(st: BitPerfectState): String {
        val fmt = "${st.bits}-bit / ${st.sampleRate / 1000.0} kHz"
        return when (st.kind) {
            BitPerfectKind.OFF -> ""
            BitPerfectKind.UNSUPPORTED -> "This phone has Android 13 or older: Android cannot do bit-perfect output."
            BitPerfectKind.NO_USB -> "No USB DAC connected."
            BitPerfectKind.WAITING -> "${st.device} connected. Play a song to check."
            BitPerfectKind.ACTIVE -> "Bit-perfect active: $fmt → ${st.device}"
            BitPerfectKind.NO_MATCH -> "${st.device} does not offer bit-perfect for $fmt: Android resamples this song."
            BitPerfectKind.REFUSED -> "Android refused bit-perfect for $fmt on ${st.device}."
        }
    }
    override fun seconds(n: Int) = "$n s"
    override val importEntry = "Import a song list (QBDLX Scanner)"
    override val importTitle = "Import songs"
    override val importHelp = "Share the list made by QBDLX Scanner with this app, or pick the file here. Each song is searched on Qobuz; sure matches are ticked, doubtful ones wait for you."
    override val importPick = "Pick a list file"
    override val importBadFile = "That file is not a QBDLX Scanner list"
    override val importEmpty = "The list has no songs"
    override val importSure = "Sure matches"
    override val importCheck = "Check these"
    override val importMissing = "Not found"
    override val importFix = "Fix / search again"
    override val importForget = "Forget saved matches"
    override val importForgotten = "Saved matches forgotten: the next import searches everything again"
    override val importRetry = "Search these again (Qobuz may have been busy)"
    override fun importPlaylistReplace(n: Int) = "Create \"iPod\" playlist, download $n and replace on the iPod"
    override val importWorking = "Working…"
    override fun importDeleteFromIpod(n: Int) = "Delete $n found songs from the iPod"
    override fun ipodClearButton(n: Int) = "Delete ALL $n songs from the iPod"
    override val ipodClearTitle = "Empty the whole iPod?"
    override fun ipodClearWarning(n: Int) = "All $n songs will be deleted from the iPod, with their files, including the ones Qobuz does not have. This cannot be undone except by restoring the library backup (which does not bring back the music files). Type BORRAR to confirm."
    override val ipodClearType = "Type BORRAR"
    override val importDeleteTitle = "Delete from the iPod?"
    override fun importDeleteWarning(n: Int) = "$n songs will be deleted from the iPod, with their files. Only the ticked songs found on Qobuz. Make sure you have the playlist or the files elsewhere. A backup of the iPod library is made first and can be restored from the iPod screen."
    override val importDeleteConfirm = "Delete"
    override fun importDeleteDone(n: Int) = "Deleted $n songs from the iPod"
    override fun importDeleteFailed(why: String) = "Could not delete: $why"
    override fun importReplaceOnIpod(n: Int) = "Download $n and replace them on the iPod"
    override val importNoIpod = "Choose the iPod first (iPod screen)"
    override val importReplaceStarted = "Downloading as MP3. When they finish they go to the iPod, replacing the old ones. Keep the iPod connected."
    override val importReplaceNothing = "Nothing finished downloading, so nothing was sent to the iPod"
    override val importSearchAgain = "Search"
    override val importArtistField = "Artist"
    override val importTitleField = "Title"
    override val importNothingFound = "Nothing found, try other words"
    override val importSearching = { done: Int, total: Int -> "Searching Qobuz… $done/$total" }
    override val importDownload = { n: Int -> "Download $n selected" }
    override val importPickBest = "Pick the most probable one for all of these"
    override val importFavorites = { n: Int -> "Add $n selected to my Qobuz tracks" }
    override val importFavoritesDone = { n: Int -> "$n songs added to your Qobuz tracks" }
    override val ipodLookup = "Find these songs on Qobuz (download, favourites, playlist)"
    override val ipodLyricsLabel = "Put the lyrics inside each song (plain text, from LRCLIB). On the nano 6G/7G tap the cover and swipe right twice to see them (not yet tested by me)"
    override fun ipodLyricsDone(n: Int) = "Lyrics added: $n."
    override val ipodFixAlbumsButton = "Repair albums, covers and artists on the iPod"
    override val ipodTaskFixing = "Repairing albums, covers and artists… (backing up and rewriting the library)"
    override fun ipodTaskDeleting(n: Int) = "Deleting $n songs from the iPod… (backing up and rewriting the library)"
    override val ipodTaskIndeterminateHint = "This step has no exact percentage and cannot be stopped half way (it would damage the iPod's library); keep the iPod connected until it finishes."
    override val stopButton = "Stop"
    override val stoppingNow = "Stopping… it ends at the next safe point."
    override fun ipodStoppedSend(sent: Int, left: Int) = "Stopped. $sent songs sent; $left are left and can be resumed."
    override fun ipodLyricsStopped(added: Int) = "Stopped. Lyrics saved in $added songs."
    override val importStopped = "Search stopped."
    override fun ipodFixAlbumsDone(n: Int) = if (n == 0) "Done. Covers were already right; albums are now linked to their artists. Restart the iPod to refresh the Artists view." else "Fixed $n albums. Unplug and replug the iPod (or restart it) if the Albums view does not update."
    override fun ipodFixAlbumsFailed(error: String) = "Could not fix the albums: $error"
    override val ipodPlaylistButton = "Send a Qobuz playlist to the iPod…"
    override val ipodPlaylistTitle = "Choose one or more playlists"
    override val ipodPlaylistLegend = "🟢 already on the iPod · 🔴 not on the iPod (or empty)"
    override val aiMixButton = "AI mixes for the iPod…"
    override val aiMixTitle = "AI mixes"
    override val aiMixIntro = "AI mixes for your iPod: pick a song to start from, or an idea, and the AI builds mixes from the music you already have on it."
    override val aiSeedPick = "🎵 Start from a song (optional)"
    override fun aiSeedChosen(t: String) = "🎵 Starting from: $t"
    override val aiSeedTitle = "Song to start from"
    override val aiSeedSearch = "Search your iPod…"
    override val aiSeedClear = "Clear"
    override fun aiSizeLabel(n: Int) = "$n songs"
    override val aiGenerate = "Make mixes"
    override val aiPreviewTitle = "Your AI mixes"
    override val aiPreviewHint = "Check the ones you want. Nothing is saved to the iPod until you press Save."
    override fun aiPreviewSave(n: Int) = "Save to iPod ($n)"
    override val aiPreviewRegen = "↻ Make new ones"
    override fun aiPreviewSongs(n: Int) = "$n songs"
    override val aiIdeasButton = "💡 Suggest mixes"
    override val aiIdeasAsking = "Thinking of ideas…"
    override val aiIdeasStop = "Stop"
    override val aiIdeasStopped = "Stopped."
    override val aiIdeasLocal = "Ideas taken from your library."
    override val aiMixHint = "What kind of mix? (optional) e.g. \"for running\", \"calm night\", \"similar to <song>\""
    override val aiMixCreate = "Create mixes"
    override val aiMixTooFew = "You need at least 5 songs on the iPod."
    override val aiSaveNeedId = "The iPod ID is needed to write playlists. Allow USB access if Android asks, then press Create again."
    override val aiMixAsking = "Asking the AI…"
    override val aiMixNoKey = "No AI key yet, so these mixes were made on the phone."
    override val aiMixBadAnswer = "The AI did not give a usable answer, so these mixes were made on the phone."
    override val aiMixByAi = "Made with AI."
    override val aiMixLocal = "Made by artist and at random."
    override val aiKeyTitle = "Gemini key (free)"
    override val aiKeyHow = "How do I get it?"
    override val aiKeySteps = "1. Open Google AI Studio and sign in with your Google account.\n2. Tap \"Create API key\".\n3. Copy the key (starts with AIza… or AQ.) and paste it here."
    override val aiKeyOpen = "Open Google AI Studio"
    override val aiKeyPaste = "Paste from clipboard"
    override val aiKeySave = "Test and save"
    override val aiKeyTesting = "Testing the key…"
    override val aiKeyChange = "Gemini key…"
    override val aiKeyForget = "Remove key"
    override val aiKeyPrivacy = "Only titles, artists and albums are sent to Google. The key stays on this phone."
    override fun aiKeyProblem(kind: String) = when (kind) {
        "BadKey" -> "Google rejected the key. Check that you copied all of it."
        "Quota" -> "The AI is busy or out of free quota right now."
        "Network" -> "No connection to Google."
        "Blocked" -> "Google refused this request."
        "NoKey" -> "Paste your key first."
        else -> "The AI did not answer correctly."
    }
    override val ipodPlaylistNone = "You have no playlists in Qobuz."
    override val ipodPlaylistLoadFailed = "Could not load the playlist from Qobuz."
    override fun ipodPlaylistDownloading(done: Int, total: Int) = "Downloading the playlist's missing songs as MP3… $done of $total"
    override fun ipodPlaylistLoading(name: String, i: Int, n: Int) = "Reading playlist \"$name\" (${i + 1} of $n)…"
    override fun ipodPlaylistMaking(name: String, i: Int, n: Int) = "Creating playlist \"$name\" on the iPod (${i + 1} of $n)…"
    override fun ipodPlaylistSendN(n: Int) = if (n == 0) "Choose at least one" else "Send $n playlist(s)"
    override val ipodPlaylistDownloadStopped = "Stopped. The songs already downloaded are kept; nothing was sent to the iPod."
    override fun ipodPlaylistDone(name: String, added: Int, missing: Int, error: String?) =
        if (error != null) "Playlist \"$name\" not created: $error" else if (added == 0) "Playlist \"$name\" not created: none of its songs is on the iPod (only downloaded MP3s can be sent)."
        else "Playlist \"$name\" on the iPod: $added songs" + (if (missing > 0) ", $missing left out (not downloaded as MP3)." else ".")
    override fun ipodLyricsNothing(n: Int) = "Nothing to do: all $n songs already have lyrics."
    override fun ipodLyricsSearching(done: Int, total: Int) = "Looking up lyrics… $done/$total"
    override fun ipodLyricsWriting(done: Int, total: Int) = "Downloading and writing lyrics, song by song… $done/$total"
    override fun ipodAddLyricsButton(n: Int) = "Add lyrics to the songs on the iPod ($n)"
    override fun ipodLyricsDeviceDone(added: Int, skipped: Int, failed: Int) = "Lyrics added to $added songs. Without lyrics or not MP3: $skipped. Failed: $failed."
    override fun ipodLyricsFailed(added: Int, error: String) = "Lyrics stopped ($added songs done): $error"
    override val ipodReplaceLabel = "Also replace songs already on the iPod with the Qobuz version (the old file is deleted)"
    override val ipodReport = "Share a technical report of the iPod"
    override val toTop = "Back to top"
    override val shareGeneric = "Share"
    override fun ipodInterrupted(left: Int, reason: String) = "Interrupted: $left songs left ($reason). It continues when the iPod is plugged in again."
    override fun ipodResumeBanner(n: Int) = "An interrupted send has $n songs left"
    override val ipodResumeButton = "Resume"
    override val ipodDiscard = "Discard"
    override val ipodNotReadyYet = "The iPod is not ready yet: plug it in and try again"
    override val ipodNothingLeft = "Nothing left to send: it is all on the iPod"
    override val nowButton = "Playing"
    override val nowIdentifying = "Identifying the song…"
    override val nowTapToDownload = "Tap to download"
    override val nowAllow = "Allow notification access"
    override val nowAccess = "Allow notification access for QBDLX Mobile in the screen that opens, then tap again"
    override val nowNothing = "Nothing is playing in the Qobuz app (start a song there first)"
    override fun nowSearching(title: String) = "Looking for \"$title\" in Qobuz…"
    override fun nowNotFound(title: String) = "Could not find \"$title\" in Qobuz"
    override fun nowDownloading(title: String, artist: String, sure: Boolean) = (if (sure) "Downloading: " else "Downloading the closest match: ") + "$title — $artist"
    override val ipodSendDownloaded = "Send to iPod"
    override val ipodChooseFirst = "Choose the iPod first, then tap again"
    override fun ipodNothingToSend(noMp3: Boolean) = if (noMp3) "No finished MP3 downloads to send (the iPod needs MP3)" else "Everything is already on the iPod"
    override val ipodNotifChannel = "iPod sync"
    override fun ipodNotifTitle(done: Int, total: Int) = "Syncing iPod: $done of $total"
    override fun ipodNotifDone(sent: Int, failed: Int) = if (failed == 0) "iPod synced: $sent songs. You can disconnect it when Android says it is safe." else "iPod synced: $sent songs, $failed failed"
    override val importToPlaylist = { n: Int -> "Create Qobuz playlist with $n selected" }
    override val importPlaylistDone = { name: String, n: Int -> "Playlist \"$name\" created in Qobuz with $n songs" }
    override val importPlaylistFailed = { why: String -> "Could not create the playlist: $why" }
    override val ipodEntry = "iPod"
    override val ipodBusy = "Error: a transfer to the iPod is already running. Wait for it to finish (or open the iPod screen to follow it)."
    override val ipodTitle = "iPod"
    override val ipodHelp = "Connect the iPod with a USB OTG adapter, wait until Android shows it as a drive, then choose its top folder (the one that contains iPod_Control). Android cannot read iPods formatted for Mac (HFS+): they won't show up. Only MP3 downloads can be sent. Keep the iPod on \"manually manage music\" and don't sync it with iTunes afterwards."
    override val ipodChoose = "Choose the iPod"
    override val ipodChange = "Select iPod"
    override val ipodNone = "No iPod chosen yet."
    override fun ipodProblem(kind: com.qbdlx.mobile.ipod.IpodDevice.Problem, detail: String?) = when (kind) {
        com.qbdlx.mobile.ipod.IpodDevice.Problem.NOT_IPOD -> "This folder has no iPod_Control: it is not the top folder of an iPod."
        com.qbdlx.mobile.ipod.IpodDevice.Problem.OLD_FORMAT -> "This iPod uses the older database format. Only the nano 6G/7G are supported."
        com.qbdlx.mobile.ipod.IpodDevice.Problem.MISSING_FILES -> "The iPod's library files are missing" + (detail?.let { ": $it" } ?: "") + "."
        com.qbdlx.mobile.ipod.IpodDevice.Problem.LEFTOVER_FILES -> "The iPod has unfinished database files (${detail ?: ""}). Eject it safely from a computer first."
        com.qbdlx.mobile.ipod.IpodDevice.Problem.UNREADABLE -> "The iPod could not be read" + (detail?.let { ": $it" } ?: "") + "."
    }
    override val ipodTabSend = "To send"
    override val ipodTabOn = "On the iPod"
    override val ipodViewList = "List"
    override val ipodViewRows = "Rows"
    override val ipodViewGrid = "Grid"
    override val ipodViewLibrary = "Library"
    override val ipodViewArtists = "Artists"
    override fun ipodLibArtistCount(albums: Int, songs: Int) = "$albums album(s) · $songs songs"
    override val ipodTabPlaylists = "Playlists"
    override fun ipodPlaylistSongs(n: Int, missing: Int) = "$n songs" + if (missing > 0) " · $missing not found" else ""
    override val ipodPlaylistsEmpty = "There are no playlists on the iPod."
    override val ipodSelectPlaylist = "Select all its songs"
    override val ipodSearch = "Search title, artist or album"
    override val ipodDeleteSelected = "Delete selected files"
    override fun ipodSelectedCount(n: Int) = "$n selected"
    override val ipodSelectAllVisible = "Select all shown"
    override val ipodDeleteSelectedTitle = "Delete selected songs?"
    override fun ipodDeleteListsNote(n: Int) = if (n > 0) " $n playlist(s) will be deleted too." else ""
    override val ipodDeleteListCheck = "Delete the list and its songs"
    override fun ipodDeleteSelectedWarning(n: Int) = "$n songs and their audio files will be removed from the iPod. A library backup is made first. This cannot restore the audio files."
    override fun ipodSpace(free: String, total: String) = "$free free of $total"
    override val ipodSpaceUnknown = "Storage: Android doesn't report free/total space for this iPod."
    override fun ipodSummary(on: Int, pending: Int, already: Int) = "$on on the iPod · $pending to send" + if (already > 0) " · $already already there" else ""
    override val ipodArtistAll = "All artists"
    override val ipodDateLabels = listOf("Any time", "Today", "7 days", "30 days")
    override val ipodTickAll = "Tick all"
    override val ipodGroupTitle = "Group by"
    override val ipodGroupLabels = listOf("None", "Album", "Artist", "Download date", "Release year")
    override val ipodUnknownGroup = "Unknown"
    override val ipodTickNone = "Untick all"
    override val ipodUsbNotFound = "No iPod found on USB. Reconnect it with the OTG adapter."
    override val ipodUsbNoSerial = "The iPod did not give its serial number. Type the identifier by hand."
    override val ipodUsbDenied = "USB access was denied. Try again and tap Allow."
    override val ipodNoMp3 = "No MP3 downloads to send. Set the quality to MP3 in Settings and download songs first."
    override val ipodPending = "Ready to send (MP3)"
    override fun ipodAlready(n: Int) = "$n already on the iPod (skipped)"
    override fun ipodSendButton(n: Int) = if (n == 1) "Send 1 song" else "Send $n songs"
    override fun ipodSending(done: Int, total: Int) = "Sending $done of $total…"
    override fun ipodCovers(added: Int, note: String?) = (if (added > 0) "Covers written: $added (not tested on every iPod). " else "") + (note?.let { "Some covers were skipped: $it." } ?: "")
    override fun ipodDone(sent: Int, failed: Int) = "Sent $sent" + (if (failed > 0) ", $failed failed" else "") + ". Eject the iPod safely before unplugging."
    override val ipodRestore = "Restore the last backup"
    override val ipodRestoreDone = "Backup restored."
    override val ipodNoBackup = "There is no backup to restore."
    override val ipodSafeEject = "Disconnect the iPod only after Android says it is safe."

    override val dlNotifChannel = "Downloads"
    override fun dlNotifTitle(done: Int, total: Int) = "Downloading · $done of $total"
    override fun dlNotifDone(n: Int, failed: Int) = if (failed > 0) "Downloads finished: $n done, $failed failed" else "Downloads finished: $n done"
}

object Spanish : Strings {
    override val logInWithQobuz = "Iniciar sesión con Qobuz"
    override val signingIn = "Iniciando sesión…"

    override val logInToQobuz = "Iniciar sesión en Qobuz"
    override val loadingSignIn = "Cargando el inicio de sesión de Qobuz…"
    override val socialNotSupported = "El inicio de sesión con Google, Apple y Facebook no funciona dentro de la app. " +
        "Regresa e inicia sesión con tu correo y contraseña de Qobuz. Si solo usabas Google, toca \"¿Has olvidado " +
        "tu clave?\" en la página de Qobuz para crear una."

    override fun tab(tab: Tab) = when (tab) {
        Tab.Home -> "Inicio"
        Tab.Search -> "Buscar"
        Tab.Library -> "Biblioteca"
        Tab.Downloads -> "Descargas"
        Tab.Settings -> "Ajustes"
    }
    override val back = "Atrás"

    override val discover = "Descubrir"
    override val qobuzPlaylists = "Playlists de Qobuz"
    override val all = "Todas"
    override fun section(key: String) = when (key) {
        "new_releases" -> "Novedades"
        "album_of_the_week" -> "Álbum de la semana"
        "qobuzissims" -> "Qobuzissime"
        "ideal_discography" -> "Discografía ideal"
        "most_streamed" -> "Lo más escuchado"
        "press_awards" -> "Premios de la prensa"
        else -> key
    }
    override fun discoverFailed(reason: String?) = "No se pudo cargar Descubrir ($reason); solo se muestran las novedades"

    override val search = "Buscar"
    override val searchAlbums = "Buscar álbumes"
    override val searchHint = "Buscar álbumes y canciones"
    override val sortBy = "Ordenar"
    override val searchLibrary = "Buscar en tu biblioteca"
    override val recentSearches = "Búsquedas recientes"
    override val recentSearchesClear = "Borrar búsquedas recientes"
    override val localFolders = "Carpetas"
    override val localFolderAdd = "Añadir una carpeta del teléfono"
    override val localFolderRemove = "Quitar carpeta"
    override val localFoldersEmpty = "Añade una carpeta con tu música para reproducir sus archivos aquí."
    override fun localPlayAll(n: Int) = "Reproducir todo ($n)"
    override val sortAdded = "Como se agregaron"
    override val sortTitle = "Título A–Z"
    override val sortArtist = "Artista A–Z"
    override val sortNewest = "Más recientes primero"
    override val sortOldest = "Más antiguos primero"
    override fun searchAlbumsTab(n: Int) = "Álbumes ($n)"
    override fun searchSongsTab(n: Int) = "Canciones ($n)"

    override fun tracks(n: Int) = if (n == 1) "1 pista" else "$n pistas"
    override val download = "Descargar"
    override val downloadAlbum = "Descargar álbum"
    override val downloaded = "Descargado"
    override val playAll = "Reproducir"
    override val noneDownloaded = "Ninguna de sus canciones está descargada"
    override val details = "Detalles"
    override val detailTracks = "Canciones"
    override val detailOwner = "Creada por"
    override fun queuedAll(n: Int) = "Se añadieron $n canciones a la cola"
    override fun addedToPlaylist(name: String) = "Agregada a $name"
    override val playNext = "Reproducir a continuación"
    override val addToQueue = "Añadir a la cola"
    override val goToAlbum = "Ir al álbum"
    override val goToArtist = "Ir al artista"
    override val sendToIpod = "Enviar al iPod"
    override val shareTrack = "Compartir"
    override val copyLink = "Copiar enlace"
    override val linkCopied = "Enlace copiado"
    override val trackDetails = "Detalles de la pista"
    override val addToPlaylist = "Agregar a playlist"
    override val addedNext = "Suena a continuación"
    override val addedToQueue = "Añadida a la cola"
    override val downloadAgain = "Descargar de nuevo"
    override val removeDownload = "Quitar descarga"
    override val removeDownloadAsk = "¿Borrar del dispositivo el archivo descargado?"
    override val sentToIpod = "Marcada para enviar al iPod"
    override val noPlaylists = "Aún no tienes playlists"
    override val close = "Cerrar"
    override val detailQuality = "Calidad"
    override val detailDuration = "Duración"
    override val detailAlbum = "Álbum"
    override val detailArtist = "Artista"
    override val detailYear = "Año"
    override val detailFile = "Archivo"
    override val notDownloaded = "Sin descargar"
    override val play = "Reproducir"
    override val playPause = "Reproducir/pausar"

    override val inProgress = "En curso"
    override val completed = "Completadas"
    override val queued = "En cola"
    override val retry = "Reintentar"
    override fun queuedTracks(n: Int) = if (n == 1) "1 pista añadida a Descargas" else "$n pistas añadidas a Descargas"
    override val nothingToDownload = "No hay nada que descargar"
    override val badSignature = "Qobuz rechazó la firma de la descarga: el App Secret no corresponde al App ID. Configura el correcto en Ajustes."

    override val library = "Biblioteca"
    override val playlists = "Playlists"
    override val albums = "Álbumes"
    override val tracksTab = "Pistas"
    override val artistsTab = "Artistas"
    override val topSongs = "Lo más escuchado"
    override val epsSingles = "EPs y sencillos"
    override val liveAlbums = "En directo"
    override val similarArtists = "Artistas similares"
    override val seeAll = "Ver todo"
    override val readMore = "LEER MÁS"
    override val nothingHere = "Aún no hay nada aquí"
    override val downloadAll = "Descargar todo"
    override val everythingTitle = "Descargar toda mi cuenta"
    override val everythingHelp = "Pone en la cola todas tus pistas favoritas, todos tus álbumes favoritos y todas tus playlists de Qobuz, en la calidad elegida arriba. Lo que ya está descargado se salta."
    override val everythingConfirm = "¿Descargar TODO lo de tu cuenta? Pueden ser muchos gigabytes."
    override val everythingButton = "Descargar todo"
    override val refresh = "Actualizar"

    override val selectQuality = "Seleccionar calidad"
    override val chooseQuality = "Elige la calidad de audio que prefieras"
    override val qualityOnWifi = "Con WiFi"
    override val qualityOnData = "Con datos"
    override val cancel = "Cancelar"
    override fun qualityDescription(q: Quality) = when (q) {
        Quality.HI_RES_MAX -> "24 bits hasta 192 kHz"
        Quality.HI_RES -> "24 bits hasta 96 kHz"
        Quality.LOSSLESS -> "16 bits 44,1 kHz"
        Quality.MP3 -> "320 kbps"
    }

    override val settings = "Ajustes"
    override val defaultQuality = "Calidad predeterminada"
    override val defaultQualityHelp = "Las descargas empiezan de inmediato con la calidad que elijas aquí."
    override val askAlways = "Preguntar siempre"
    override val askAlwaysDesc = "Elegir la calidad en cada descarga"
    override val amoledBlack = "Negro AMOLED"
    override val language = "Idioma"
    override fun languageName(l: Language) = when (l) {
        Language.SYSTEM -> "Sistema"
        Language.ENGLISH -> "English"
        Language.SPANISH -> "Español"
    }
    override val save = "Guardar"
    override fun signedInAs(name: String) = "Sesión iniciada como ${name.ifBlank { "usuario de Qobuz" }}"
    override val logOut = "Cerrar sesión"
    override val disclaimer = "QBDLX no está respaldado, certificado ni aprobado por Qobuz. Úsalo conforme a los Términos de uso de la API de Qobuz."

    override val diagOpen = "Diagnóstico Qobuz"
    override val diagTitle = "Diagnóstico Qobuz"
    override val diagIntro = "Captura lo que Qobuz responde realmente en track/getFileUrl cuando falla una descarga " +
        "(código HTTP, status, mensaje y datos de la firma). Se ocultan contraseñas, App Secret, tokens y URLs firmadas. " +
        "Intenta una descarga y luego vuelve aquí para copiar el informe."
    override val diagRunTest = "Probar ahora"
    override val diagTestDone = "Prueba finalizada — revisa el informe abajo"
    override val diagCopy = "Copiar"
    override val diagCopied = "Diagnóstico copiado al portapapeles"
    override val diagClear = "Borrar"
    override val diagCleared = "Diagnóstico borrado"
    override val diagEmpty = "Aún no hay diagnósticos. Pulsa «Probar ahora», o intenta descargar una canción y vuelve aquí."

    override val updateCreds = "Actualizar credenciales Qobuz"
    override val updateCredsHelp = "Vuelve a leer el reproductor web oficial para obtener un App ID + App Secret nuevos, los comprueba contra una pista real y los guarda juntos. Úsalo si las descargas fallan con «Invalid Request Signature»."
    override fun credsUpdated(timezone: String) = "Credenciales de Qobuz actualizadas y verificadas (zona: $timezone)"
    override val credsAutoUpdated = "Las credenciales de Qobuz se renovaron automáticamente; reintentando la descarga"

    override val dlPause = "Pausar"
    override val dlResume = "Reanudar"
    override val dlCancel = "Cancelar"
    override val dlDelete = "Quitar de la lista"
    override val dlPauseAll = "Pausar todo"
    override val dlResumeAll = "Reanudar todo"
    override val dlRetryFailed = "Reintentar fallidas"
    override val dlClearDone = "Limpiar completadas"
    override val dlTabFailed = "Fallidas"
    override val dlDeleteHint = "Quitar un elemento solo lo saca de esta lista; el archivo de música se queda en tu teléfono."
    override val statusPaused = "Pausada"
    override val statusFailed = "Fallida"
    override val statusCanceled = "Cancelada"
    override fun dlSummary(done: Int, total: Int, active: Int) = "$done de $total listas · $active activas"
    override fun noteRetrying(n: Int, max: Int) = "Problema de conexión, reintentando ($n/$max)…"
    override val noteRefreshing = "Renovando credenciales de Qobuz…"
    override val noteSystemLimit = "Pausada por el límite de Android en segundo plano. Pulsa Reanudar."
    override val noteWaitingNetwork = "Sin conexión. Esperando para continuar…"
    override fun alreadyQueued(n: Int) = if (n == 1) "Ya está en cola o descargada (quítala de Descargas para repetirla)"
        else "$n pistas ya estaban en cola o descargadas"
    override fun queuedTracksSkipped(added: Int, skipped: Int) = "${queuedTracks(added)} ($skipped ya estaban en cola o descargadas)"
    override fun confirmBulkAlbums(n: Int) = if (n == 1) "¿Descargar todas las pistas de 1 álbum?" else "¿Descargar todas las pistas de $n álbumes?"
    override fun confirmBulkPlaylists(n: Int) = if (n == 1) "¿Descargar todas las pistas de 1 playlist?" else "¿Descargar todas las pistas de $n playlists?"
    override val confirmBulkYes = "Descargar"
    override fun preparingAlbums(n: Int) = if (n == 1) "Preparando 1 álbum…" else "Preparando $n álbumes…"
    override fun preparingPlaylists(n: Int) = if (n == 1) "Preparando 1 playlist…" else "Preparando $n playlists…"
    override fun bulkDone(added: Int, skipped: Int, failed: Int) = buildString {
        append(if (added == 1) "1 pista añadida a Descargas" else "$added pistas añadidas a Descargas")
        if (skipped > 0) append(" · $skipped ya estaban en cola o descargadas")
        if (failed > 0) append(" · $failed no se pudieron leer")
    }
    override val maxConcurrentTitle = "Descargas simultáneas"
    override val maxConcurrentHelp = "Cuántas pistas se descargan al mismo tiempo. Bajar el número nunca interrumpe una descarga en curso."

    override val folderTitle = "Carpeta de descargas"
    override val folderHelp = "Dónde se guardan las descargas nuevas, con una carpeta por artista y álbum. Si no eliges ninguna, se usa Música/${DefaultFolder.name}. " +
        "Android no permite elegir la raíz del almacenamiento ni la carpeta Descargas."
    override val folderDefault get() = "Predeterminada: Música/${DefaultFolder.name}"
    override fun folderCurrent(isInternal: Boolean, volume: String, path: String) =
        (if (isInternal) "Almacenamiento interno" else "Tarjeta SD ($volume)") + if (path.isEmpty()) "" else "/$path"
    override val folderChoose = "Elegir carpeta"
    override val knownTitle = "Comprobar también otra carpeta"
    override val dupScan = "Buscar canciones repetidas"
    override val crashShare = "Compartir el último informe de error"
    override val dupScanning = "Buscando…"
    override val dupNone = "No se encontraron canciones repetidas."
    override val dupDownloads = "Descargas"
    override val dupOther = "Otra carpeta"
    override fun dupTitle(n: Int) = "Repetidas ($n)"
    override fun dupDelete(n: Int) = "Borrar $n marcadas"
    override fun dupDeleted(done: Int, failed: Int) = if (failed == 0) "Se borraron $done archivos" else "Se borraron $done archivos, $failed no se pudieron borrar"
    override val dupHint = "Las copias marcadas se borran para siempre. Las de la otra carpeta nunca se tocan. Revisa la lista antes: canciones distintas con el mismo título pueden aparecer aquí."
    override val knownHelp = "Las canciones que ya estén en esta carpeta (con cualquier organización) se omiten y no se descargan otra vez. La app solo la lee. Requiere tener activado \"Omitir archivos que ya existen\"."
    override val knownChoose = "Elegir carpeta a comprobar"
    override val knownRemove = "Quitar"
    override val knownSaved = "Carpeta agregada: se omitirán sus canciones"
    override val folderResetButton = "Usar la carpeta predeterminada"
    override val folderSaved = "Carpeta de descargas guardada"
    override val folderResetDone = "Usando la carpeta predeterminada"
    override fun folderNotWritable(reason: String?) = "No se puede guardar en esa carpeta" + if (reason.isNullOrBlank()) "" else ": $reason"
    override val folderBanner get() = "La carpeta de descargas elegida no está disponible. Mientras tanto, las descargas se guardan en Música/${DefaultFolder.name}."
    override val folderBannerChoose = "Elegir otra"
    override val folderBannerDismiss = "Entendido"
    override val wifiOnlyTitle = "Descargar solo con Wi-Fi"
    override val lyricsListTitle = "Enviar lista de canciones a Q Lyrics"
    override val lyricsListDesc = "Q Lyrics baja de antemano las letras de tus descargas, canciones favoritas, álbumes favoritos y playlists, para que estén listas cuando suene la canción."
    override val lyricsListGathering = "Reuniendo las canciones de tus listas y álbumes…"
    override val lyricsListEmpty = "Aún no hay canciones descargadas ni favoritas que enviar."
    override val lyricsListMissingApp = "Instala primero Q Lyrics."
    override val wifiOnlyDesc = "Las descargas esperan mientras uses datos móviles y siguen solas cuando vuelva el Wi-Fi."
    override val skipExistingTitle = "Omitir archivos que ya existen"
    override val skipExistingDesc = "Antes de descargar una pista, comprueba si su archivo ya está en la carpeta de descargas y, si es así, la omite."
    override val noteExisted = "Ya estaba en la carpeta, omitida"
    override val noteReplaced = "Reemplazada por una versión de mayor calidad"
    override val upgradeExistingTitle = "Reemplazar si la calidad es mayor"
    override val upgradeExistingDesc = "Si el archivo guardado tiene menos calidad que la que se descarga (por ejemplo MP3 o calidad CD y ahora eliges Hi-Res), lo reemplaza. Solo lo reemplaza cuando el archivo nuevo se ha descargado completo y se comprueba que es mejor. Necesita \"Omitir archivos que ya existen\"."
    override val playerExplore = "Explora"
    override val playerInfo = "Info"
    override val playerQueue = "Cola"
    override val queueTitle = "Cola de reproducción"
    override val queueCurrent = "Actual"
    override val queueNext = "A continuación"
    override val queueHistory = "Historial"
    override val queueClear = "Vaciar cola"
    override val queueEmpty = "No hay nada a continuación"
    override val removeFromQueue = "Quitar de la cola"
    override val exploreTitle = "Explora"
    override fun exploreMoreOf(artist: String) = "Más de $artist"
    override val exploreEmpty = "No hay nada que mostrar"
    override val lyricsTitle = "Letra"
    override val lyricsSync = "Sincronía"
    override val lyricsResync = "Resincronizar"
    override val shareDrive = "Compartir a Google Drive"
    override val shareHint = "Elige las canciones. Las descargadas con poca diferencia de tiempo van agrupadas; toca un grupo para verlas. Drive te pregunta en qué carpeta guardarlas."
    override val shareWindowHour = "Última hora"
    override val shareWindowToday = "Hoy"
    override val shareWindowWeek = "7 días"
    override val shareWindowAll = "Todas"
    override val shareNone = "No hay canciones descargadas en este periodo"
    override fun shareButton(n: Int) = if (n == 0) "Compartir" else "Compartir $n"
    override val shareAll = "Seleccionar todo"
    override val shareTooMany = "Se envían por tandas de 100: cuando Drive termine, vuelve aquí por la siguiente"
    override fun shareProgress(done: Int, total: Int) = "Enviadas $done de $total. Cuando Drive termine, envía la siguiente tanda."
    override fun shareNext(n: Int) = "Enviar las siguientes $n"
    override fun shareUnreadable(n: Int) = "$n archivos omitidos: la app ya no puede leerlos (los movió o reemplazó otra app)"
    override val lyricsNone = "No se encontró la letra de esta canción"
    override val pressBackAgain = "Presiona atrás otra vez para salir"
    override val sleepTimer = "Temporizador de apagado"
    override val sleepOff = "Quitar temporizador"
    override fun sleepMinutes(n: Int) = "$n minutos"
    override fun sleepRemaining(min: Int) = "Temporizador: faltan $min min"
    override val equalizer = "Ecualizador"
    override val noEqualizer = "No hay ecualizador en este teléfono"
    override val history = "Historial"
    override fun exploreWith(artist: String) = "Con $artist"
    override val exploreTopTracks = "LO MÁS ESCUCHADO"
    override val exploreLabelOf = "Más del sello"
    override fun exploreLabelName(label: String) = label
    override val creditsTitle = "Créditos"
    override val noCredits = "No hay créditos para esta pista"
    override val aboutRelease = "Acerca del lanzamiento"
    override val infoGenre = "Género"
    override val infoReleaseDate = "Fecha de lanzamiento"
    override val infoTracks = "Pistas"
    override val infoDuration = "Duración"
    override val infoQuality = "Calidad"
    override val infoLabel = "Sello"
    override val shuffle = "Aleatorio"
    override val repeat = "Repetir"
    override val previous = "Anterior"
    override val next = "Siguiente"
    override val more = "Más"
    override val viewAlbum = "Ir al álbum"
    override val playbackSection = "Reproducción"
    override val crossfadeTitle = "Fundido entre canciones"
    override val crossfadeDesc = "Las canciones siempre se encadenan sin pausa. Con fundido, el final de una baja de volumen mientras sube la siguiente. Apagado por defecto; las canciones muy cortas se funden menos."
    override val crossfadeOff = "Desactivado"
    override val bitPerfectTitle = "Salida USB bit-perfect"
    override val usbDiagButton = "Diagnosticar salida USB"
    override val usbDirectTitle = "Controlador USB directo al DAC"
    override val usbDirectDesc = "Habla con el DAC él mismo por USB, sin pasar por el mezclador de Android, así la canción llega tal cual (funciona aunque Android no ofrezca bit-perfect). Experimental. Android pedirá permiso USB; si el DAC tiene dos modos USB, va mejor el que lista tu frecuencia (44,1/48 kHz: UAC1; hi-res: UAC2). El volumen pasa a ser el del propio DAC. Sin fundido ni ecualizador mientras esté activo."
    override val usbDirectNoDevice = "Aún no hay DAC USB: conéctalo y acepta el permiso."
    override val usbDiagCopy = "Copiar"
    override val usbDiagClose = "Cerrar"
    override val bitPerfectDesc = "Envía cada canción a un DAC USB con su propia frecuencia y profundidad de bits, sin que Android la remuestree. Necesita Android 14 o superior y un DAC USB. Quita el fundido y el cambio de frecuencia sin pausa; para 24 bits elige una calidad Hi-Res en el reproductor. Ecualizadores y efectos de sonido deben estar apagados."
    override fun bitPerfectStatus(st: BitPerfectState): String {
        val fmt = "${st.bits} bits / ${st.sampleRate / 1000.0} kHz"
        return when (st.kind) {
            BitPerfectKind.OFF -> ""
            BitPerfectKind.UNSUPPORTED -> "Este teléfono tiene Android 13 o anterior: Android no puede hacer salida bit-perfect."
            BitPerfectKind.NO_USB -> "No hay un DAC USB conectado."
            BitPerfectKind.WAITING -> "${st.device} conectado. Reproduce una canción para comprobarlo."
            BitPerfectKind.ACTIVE -> "Bit-perfect activo: $fmt → ${st.device}"
            BitPerfectKind.NO_MATCH -> "${st.device} no ofrece bit-perfect para $fmt: Android remuestrea esta canción."
            BitPerfectKind.REFUSED -> "Android rechazó el bit-perfect para $fmt en ${st.device}."
        }
    }
    override fun seconds(n: Int) = "$n s"
    override val importEntry = "Importar lista de canciones (QBDLX Scanner)"
    override val importTitle = "Importar canciones"
    override val importHelp = "Comparte con esta app la lista que hizo QBDLX Scanner, o elige el archivo aquí. Cada canción se busca en Qobuz; las seguras quedan marcadas y las dudosas esperan tu decisión."
    override val importPick = "Elegir archivo de lista"
    override val importBadFile = "Ese archivo no es una lista de QBDLX Scanner"
    override val importEmpty = "La lista no tiene canciones"
    override val importSure = "Coincidencias seguras"
    override val importCheck = "Revisar estas"
    override val importMissing = "No encontradas"
    override val importFix = "Corregir / buscar de nuevo"
    override val importForget = "Olvidar coincidencias guardadas"
    override val importForgotten = "Coincidencias olvidadas: la próxima importación buscará todo otra vez"
    override val importRetry = "Volver a buscar estas (Qobuz pudo estar saturado)"
    override fun importPlaylistReplace(n: Int) = "Crear playlist \"iPod\", descargar $n y reemplazar en el iPod"
    override fun importDeleteFromIpod(n: Int) = "Borrar del iPod las $n encontradas"
    override fun ipodClearButton(n: Int) = "Borrar TODAS las $n canciones del iPod"
    override val ipodClearTitle = "¿Vaciar todo el iPod?"
    override fun ipodClearWarning(n: Int) = "Se borrarán las $n canciones del iPod, con sus archivos, incluidas las que Qobuz no tiene. No se puede deshacer: restaurar la copia de seguridad solo recupera la biblioteca, no los archivos de música. Escribe BORRAR para confirmar."
    override val ipodClearType = "Escribe BORRAR"
    override val importDeleteTitle = "¿Borrar del iPod?"
    override fun importDeleteWarning(n: Int) = "Se borrarán $n canciones del iPod, junto con sus archivos. Solo las marcadas que se encontraron en Qobuz. Asegúrate de tener la playlist o los archivos en otro lugar. Antes se hace una copia de seguridad de la biblioteca del iPod, que puedes restaurar desde la pantalla del iPod."
    override val importDeleteConfirm = "Borrar"
    override fun importDeleteDone(n: Int) = "Se borraron $n canciones del iPod"
    override fun importDeleteFailed(why: String) = "No se pudo borrar: $why"
    override val importWorking = "Trabajando…"
    override fun importReplaceOnIpod(n: Int) = "Descargar $n y reemplazarlas en el iPod"
    override val importNoIpod = "Primero elige el iPod (pantalla iPod)"
    override val importReplaceStarted = "Descargando en MP3. Al terminar pasan al iPod, reemplazando las viejas. Mantén el iPod conectado."
    override val importReplaceNothing = "No terminó ninguna descarga, así que no se envió nada al iPod"
    override val importSearchAgain = "Buscar"
    override val importArtistField = "Artista"
    override val importTitleField = "Título"
    override val importNothingFound = "No se encontró nada, prueba otras palabras"
    override val importSearching = { done: Int, total: Int -> "Buscando en Qobuz… $done/$total" }
    override val importDownload = { n: Int -> "Descargar $n seleccionadas" }
    override val importPickBest = "Elegir la más probable en todas estas"
    override val importFavorites = { n: Int -> "Agregar $n seleccionadas a mis pistas de Qobuz" }
    override val importFavoritesDone = { n: Int -> "$n canciones agregadas a tus pistas de Qobuz" }
    override val ipodLookup = "Buscar estas canciones en Qobuz (descargar, favoritas, playlist)"
    override val ipodLyricsLabel = "Incluir la letra dentro de cada canción (texto simple, de LRCLIB). En el nano 6G/7G, toca la carátula y desliza a la derecha dos veces para verlas (aún sin probar por mí)"
    override fun ipodLyricsDone(n: Int) = "Letras añadidas: $n."
    override val ipodFixAlbumsButton = "Reparar álbumes, carátulas y artistas en el iPod"
    override val ipodTaskFixing = "Reparando álbumes, carátulas y artistas… (copia de seguridad y reescritura de la biblioteca)"
    override fun ipodTaskDeleting(n: Int) = "Borrando $n canciones del iPod… (copia de seguridad y reescritura de la biblioteca)"
    override val ipodTaskIndeterminateHint = "Este paso no tiene porcentaje exacto y no se puede detener a medias (dañaría la biblioteca del iPod); mantén el iPod conectado hasta que termine."
    override val stopButton = "Detener"
    override val stoppingNow = "Deteniendo… termina en el siguiente punto seguro."
    override fun ipodStoppedSend(sent: Int, left: Int) = "Detenido. $sent canciones enviadas; faltan $left y se pueden reanudar."
    override fun ipodLyricsStopped(added: Int) = "Detenido. Letras guardadas en $added canciones."
    override val importStopped = "Búsqueda detenida."
    override fun ipodFixAlbumsDone(n: Int) = if (n == 0) "Listo. Las carátulas ya estaban bien; los álbumes quedaron enlazados a sus artistas. Reinicia el iPod para refrescar la vista de Artistas." else "Reparados $n álbumes. Si la vista de Álbumes no cambia, desconecta y vuelve a conectar el iPod (o reinícialo)."
    override fun ipodFixAlbumsFailed(error: String) = "No se pudieron reparar los álbumes: $error"
    override val ipodPlaylistButton = "Enviar una playlist de Qobuz al iPod…"
    override val ipodPlaylistTitle = "Elige una o varias playlists"
    override val ipodPlaylistLegend = "🟢 ya está en el iPod · 🔴 no está en el iPod (o está vacía)"
    override val aiMixButton = "Mixes con IA para el iPod…"
    override val aiMixTitle = "Mixes con IA"
    override val aiMixIntro = "Mixes para tu iPod: elige una canción de partida o una idea, y la IA arma mixes con la música que ya tienes en él."
    override val aiSeedPick = "🎵 Partir de una canción (opcional)"
    override fun aiSeedChosen(t: String) = "🎵 Partiendo de: $t"
    override val aiSeedTitle = "Canción de partida"
    override val aiSeedSearch = "Buscar en tu iPod…"
    override val aiSeedClear = "Quitar"
    override fun aiSizeLabel(n: Int) = "$n canciones"
    override val aiGenerate = "Armar mixes"
    override val aiPreviewTitle = "Tus mixes con IA"
    override val aiPreviewHint = "Marca los que quieras. No se guarda nada en el iPod hasta que pulses Guardar."
    override fun aiPreviewSave(n: Int) = "Guardar en el iPod ($n)"
    override val aiPreviewRegen = "↻ Armar otros"
    override fun aiPreviewSongs(n: Int) = "$n canciones"
    override val aiIdeasButton = "💡 Sugerirme mixes"
    override val aiIdeasAsking = "Pensando ideas…"
    override val aiIdeasStop = "Parar"
    override val aiIdeasStopped = "Detenido."
    override val aiIdeasLocal = "Ideas sacadas de tu biblioteca."
    override val aiMixHint = "¿Qué tipo de mix? (opcional) ej. \"para correr\", \"noche tranquila\", \"parecidas a <canción>\""
    override val aiMixCreate = "Crear mixes"
    override val aiMixTooFew = "Necesitas al menos 5 canciones en el iPod."
    override val aiSaveNeedId = "Hace falta el ID del iPod para escribir las playlists. Acepta el permiso USB si Android lo pide y vuelve a pulsar Crear."
    override val aiMixAsking = "Preguntándole a la IA…"
    override val aiMixNoKey = "Aún no hay clave de IA, así que los mixes se hicieron en el teléfono."
    override val aiMixBadAnswer = "La IA no dio una respuesta útil, así que los mixes se hicieron en el teléfono."
    override val aiMixByAi = "Hechos con IA."
    override val aiMixLocal = "Hechos por artista y al azar."
    override val aiKeyTitle = "Clave de Gemini (gratis)"
    override val aiKeyHow = "¿Cómo la consigo?"
    override val aiKeySteps = "1. Abre Google AI Studio e inicia sesión con tu cuenta de Google.\n2. Pulsa \"Create API key\" (Crear clave).\n3. Copia la clave (empieza con AIza… o AQ.) y pégala aquí."
    override val aiKeyOpen = "Abrir Google AI Studio"
    override val aiKeyPaste = "Pegar del portapapeles"
    override val aiKeySave = "Probar y guardar"
    override val aiKeyTesting = "Probando la clave…"
    override val aiKeyChange = "Clave de Gemini…"
    override val aiKeyForget = "Quitar la clave"
    override val aiKeyPrivacy = "A Google solo se envían títulos, artistas y álbumes. La clave se queda en este teléfono."
    override fun aiKeyProblem(kind: String) = when (kind) {
        "BadKey" -> "Google rechazó la clave. Revisa que la copiaste completa."
        "Quota" -> "La IA está ocupada o sin cuota gratis por ahora."
        "Network" -> "Sin conexión con Google."
        "Blocked" -> "Google rechazó esta petición."
        "NoKey" -> "Pega primero tu clave."
        else -> "La IA no respondió bien."
    }
    override val ipodPlaylistNone = "No tienes playlists en Qobuz."
    override val ipodPlaylistLoadFailed = "No se pudo cargar la playlist de Qobuz."
    override fun ipodPlaylistDownloading(done: Int, total: Int) = "Descargando en MP3 las canciones que faltan de la playlist… $done de $total"
    override fun ipodPlaylistLoading(name: String, i: Int, n: Int) = "Leyendo la playlist \"$name\" (${i + 1} de $n)…"
    override fun ipodPlaylistMaking(name: String, i: Int, n: Int) = "Creando la playlist \"$name\" en el iPod (${i + 1} de $n)…"
    override fun ipodPlaylistSendN(n: Int) = if (n == 0) "Elige al menos una" else "Enviar $n playlist(s)"
    override val ipodPlaylistDownloadStopped = "Detenido. Las canciones ya descargadas se conservan; no se envió nada al iPod."
    override fun ipodPlaylistDone(name: String, added: Int, missing: Int, error: String?) =
        if (error != null) "Playlist \"$name\" no creada: $error" else if (added == 0) "Playlist \"$name\" no creada: ninguna de sus canciones está en el iPod (solo se envían MP3 descargados)."
        else "Playlist \"$name\" en el iPod: $added canciones" + (if (missing > 0) ", $missing sin incluir (no están en el iPod: sin espacio o no enviadas)." else ".")
    override fun ipodLyricsNothing(n: Int) = "Nada que hacer: las $n canciones ya tienen letra."
    override fun ipodLyricsSearching(done: Int, total: Int) = "Buscando letras… $done/$total"
    override fun ipodLyricsWriting(done: Int, total: Int) = "Descargando y escribiendo letras, canción por canción… $done/$total"
    override fun ipodAddLyricsButton(n: Int) = "Añadir letras a las canciones del iPod ($n)"
    override fun ipodLyricsDeviceDone(added: Int, skipped: Int, failed: Int) = "Letras añadidas a $added canciones. Sin letra o que no son MP3: $skipped. Con error: $failed."
    override fun ipodLyricsFailed(added: Int, error: String) = "Las letras se detuvieron ($added canciones hechas): $error"
    override val ipodReplaceLabel = "También reemplazar las que ya están en el iPod por la versión de Qobuz (se borra el archivo viejo)"
    override val ipodReport = "Compartir un informe técnico del iPod"
    override val toTop = "Subir al inicio"
    override val shareGeneric = "Compartir"
    override fun ipodInterrupted(left: Int, reason: String) = "Interrumpido: faltan $left canciones ($reason). Continúa al volver a conectar el iPod."
    override fun ipodResumeBanner(n: Int) = "Un envío interrumpido tiene $n canciones pendientes"
    override val ipodResumeButton = "Reanudar"
    override val ipodDiscard = "Descartar"
    override val ipodNotReadyYet = "El iPod aún no está listo: conéctalo e inténtalo de nuevo"
    override val ipodNothingLeft = "No queda nada por enviar: todo está en el iPod"
    override val nowButton = "Sonando"
    override val nowIdentifying = "Identificando la canción…"
    override val nowTapToDownload = "Pulsa para descargar"
    override val nowAllow = "Dar acceso a las notificaciones"
    override val nowAccess = "Da acceso a las notificaciones a QBDLX Mobile en la pantalla que se abre y vuelve a pulsar"
    override val nowNothing = "No suena nada en la app de Qobuz (empieza una canción allí primero)"
    override fun nowSearching(title: String) = "Buscando \"$title\" en Qobuz…"
    override fun nowNotFound(title: String) = "No se encontró \"$title\" en Qobuz"
    override fun nowDownloading(title: String, artist: String, sure: Boolean) = (if (sure) "Descargando: " else "Descargando la coincidencia más cercana: ") + "$title — $artist"
    override val ipodSendDownloaded = "Enviar al iPod"
    override val ipodChooseFirst = "Primero elige el iPod y vuelve a pulsar"
    override fun ipodNothingToSend(noMp3: Boolean) = if (noMp3) "No hay descargas MP3 terminadas para enviar (el iPod necesita MP3)" else "Todo ya está en el iPod"
    override val ipodNotifChannel = "Sincronización del iPod"
    override fun ipodNotifTitle(done: Int, total: Int) = "Sincronizando iPod: $done de $total"
    override fun ipodNotifDone(sent: Int, failed: Int) = if (failed == 0) "iPod sincronizado: $sent canciones. Puedes desconectarlo cuando Android indique que es seguro." else "iPod sincronizado: $sent canciones, $failed fallaron"
    override val importToPlaylist = { n: Int -> "Crear playlist en Qobuz con $n seleccionadas" }
    override val importPlaylistDone = { name: String, n: Int -> "Playlist \"$name\" creada en Qobuz con $n canciones" }
    override val importPlaylistFailed = { why: String -> "No se pudo crear la playlist: $why" }
    override val ipodEntry = "iPod"
    override val ipodBusy = "Error: ya hay una transferencia al iPod en curso. Espera a que termine (o abre la pantalla del iPod para seguirla)."
    override val ipodTitle = "iPod"
    override val ipodHelp = "Conecta el iPod con un adaptador USB OTG, espera a que Android lo muestre como unidad y elige su carpeta principal (la que contiene iPod_Control). Android no puede leer iPods con formato de Mac (HFS+): no aparecerán. Solo se pueden enviar descargas en MP3. Deja el iPod en \"administrar música manualmente\" y no lo sincronices con iTunes después."
    override val ipodChoose = "Elegir el iPod"
    override val ipodChange = "Seleccionar iPod"
    override val ipodNone = "Aún no has elegido el iPod."
    override fun ipodProblem(kind: com.qbdlx.mobile.ipod.IpodDevice.Problem, detail: String?) = when (kind) {
        com.qbdlx.mobile.ipod.IpodDevice.Problem.NOT_IPOD -> "Esta carpeta no tiene iPod_Control: no es la carpeta principal de un iPod."
        com.qbdlx.mobile.ipod.IpodDevice.Problem.OLD_FORMAT -> "Este iPod usa el formato antiguo de base de datos. Solo se admiten el nano 6G y 7G."
        com.qbdlx.mobile.ipod.IpodDevice.Problem.MISSING_FILES -> "Faltan archivos de la biblioteca del iPod" + (detail?.let { ": $it" } ?: "") + "."
        com.qbdlx.mobile.ipod.IpodDevice.Problem.LEFTOVER_FILES -> "El iPod tiene archivos de base de datos sin terminar (${detail ?: ""}). Expúlsalo antes de forma segura desde una computadora."
        com.qbdlx.mobile.ipod.IpodDevice.Problem.UNREADABLE -> "No se pudo leer el iPod" + (detail?.let { ": $it" } ?: "") + "."
    }
    override val ipodTabSend = "Para enviar"
    override val ipodTabOn = "En el iPod"
    override val ipodViewList = "Lista"
    override val ipodViewRows = "Lista"
    override val ipodViewGrid = "Cuadrícula"
    override val ipodViewLibrary = "Biblioteca"
    override val ipodViewArtists = "Artistas"
    override fun ipodLibArtistCount(albums: Int, songs: Int) = "$albums álbum(es) · $songs canciones"
    override val ipodTabPlaylists = "Playlists"
    override fun ipodPlaylistSongs(n: Int, missing: Int) = "$n canciones" + if (missing > 0) " · $missing no encontradas" else ""
    override val ipodPlaylistsEmpty = "No hay playlists en el iPod."
    override val ipodSelectPlaylist = "Seleccionar todas sus canciones"
    override val ipodSearch = "Buscar título, artista o álbum"
    override val ipodDeleteSelected = "Borrar archivos seleccionados"
    override fun ipodSelectedCount(n: Int) = "$n seleccionadas"
    override val ipodSelectAllVisible = "Seleccionar todos los visibles"
    override val ipodDeleteSelectedTitle = "¿Borrar las canciones seleccionadas?"
    override fun ipodDeleteListsNote(n: Int) = if (n > 0) " También se borrarán $n playlist(s)." else ""
    override val ipodDeleteListCheck = "Borrar la lista y sus canciones"
    override fun ipodDeleteSelectedWarning(n: Int) = "Se eliminarán del iPod $n canciones y sus archivos de audio. Primero se guardará una copia de la biblioteca. Los archivos de audio no se pueden recuperar desde esa copia."
    override fun ipodSpace(free: String, total: String) = "$free libres de $total"
    override val ipodSpaceUnknown = "Almacenamiento: Android no informa el espacio libre/total de este iPod."
    override fun ipodSummary(on: Int, pending: Int, already: Int) = "$on en el iPod · $pending por enviar" + if (already > 0) " · $already ya estaban" else ""
    override val ipodArtistAll = "Todos los artistas"
    override val ipodDateLabels = listOf("Siempre", "Hoy", "7 días", "30 días")
    override val ipodTickAll = "Marcar todas"
    override val ipodGroupTitle = "Agrupar por"
    override val ipodGroupLabels = listOf("Sin agrupar", "Álbum", "Artista", "Fecha de descarga", "Año de lanzamiento")
    override val ipodUnknownGroup = "Desconocido"
    override val ipodTickNone = "Quitar marcas"
    override val ipodUsbNotFound = "No encontré el iPod por USB. Vuelve a conectarlo con el adaptador OTG."
    override val ipodUsbNoSerial = "El iPod no entregó su número de serie. Escribe el identificador a mano."
    override val ipodUsbDenied = "Se negó el acceso USB. Inténtalo de nuevo y toca Permitir."
    override val ipodNoMp3 = "No hay descargas MP3 para enviar. Pon la calidad en MP3 en Ajustes y descarga canciones primero."
    override val ipodPending = "Listas para enviar (MP3)"
    override fun ipodAlready(n: Int) = "$n ya están en el iPod (se omiten)"
    override fun ipodSendButton(n: Int) = if (n == 1) "Enviar 1 canción" else "Enviar $n canciones"
    override fun ipodSending(done: Int, total: Int) = "Enviando $done de $total…"
    override fun ipodCovers(added: Int, note: String?) = (if (added > 0) "Carátulas escritas: $added (no probado en todos los iPod). " else "") + (note?.let { "Algunas carátulas se omitieron: $it." } ?: "")
    override fun ipodDone(sent: Int, failed: Int) = "Enviadas $sent" + (if (failed > 0) ", $failed fallaron" else "") + ". Expulsa el iPod de forma segura antes de desconectarlo."
    override val ipodRestore = "Restaurar la última copia de seguridad"
    override val ipodRestoreDone = "Copia de seguridad restaurada."
    override val ipodNoBackup = "No hay ninguna copia de seguridad que restaurar."
    override val ipodSafeEject = "Desconecta el iPod solo cuando Android indique que es seguro."

    override val dlNotifChannel = "Descargas"
    override fun dlNotifTitle(done: Int, total: Int) = "Descargando · $done de $total"
    override fun dlNotifDone(n: Int, failed: Int) = if (failed > 0) "Descargas terminadas: $n listas, $failed fallidas" else "Descargas terminadas: $n listas"
}

val LocalStrings = staticCompositionLocalOf<Strings> { English }
