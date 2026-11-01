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
    val orManually: String
    val appId: String
    val appSecret: String
    val email: String
    val password: String
    val userId: String
    val authToken: String
    val useToken: String
    val logIn: String
    val fillAllFields: String
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
    val nothingHere: String
    val downloadAll: String
    val refresh: String

    // Quality
    val selectQuality: String
    val chooseQuality: String
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
    val appSecretTitle: String
    fun appSecretHelp(appId: String): String
    val save: String
    val secretSaved: String
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
    val statusDownloading: String
    val statusFailed: String
    val statusCanceled: String
    fun dlSummary(done: Int, total: Int, active: Int): String
    fun noteRetrying(n: Int, max: Int): String
    val noteRefreshing: String
    val noteSystemLimit: String
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
    val playQualityTitle: String
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
    val closePlayer: String
    val more: String
    val viewAlbum: String
    val playbackSection: String
    val crossfadeTitle: String
    val crossfadeDesc: String
    val crossfadeOff: String
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
    val ipodWarning: String
    val ipodHelp: String
    val ipodChoose: String
    val ipodChange: String
    val ipodNone: String
    fun ipodProblem(kind: com.qbdlx.mobile.ipod.IpodDevice.Problem, detail: String?): String
    fun ipodReady(count: Int): String
    val ipodGuidFound: String
    val ipodGuidMissing: String
    val ipodGuidLabel: String
    val ipodUsbRead: String
    val ipodTabSend: String
    val ipodTabOn: String
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
    fun ipodSummary(on: Int, pending: Int, already: Int): String
    fun ipodSpace(free: String, total: String): String
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
    override val orManually = "Or enter your details manually"
    override val appId = "App ID"
    override val appSecret = "App Secret"
    override val email = "Email"
    override val password = "Password"
    override val userId = "User ID"
    override val authToken = "Auth token"
    override val useToken = "Log in with user ID + token"
    override val logIn = "Log in"
    override val fillAllFields = "Fill in all fields"
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
    override val nothingHere = "Nothing here yet"
    override val downloadAll = "Download all"
    override val refresh = "Refresh"

    override val selectQuality = "Select Quality"
    override val chooseQuality = "Choose your preferred audio quality"
    override val cancel = "Cancel"
    override fun qualityDescription(q: Quality) = when (q) {
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
    override val appSecretTitle = "App Secret (download signature)"
    override fun appSecretHelp(appId: String) = "Downloads are signed with the App Secret of App ID $appId. If they fail " +
        "with \"Invalid Request Signature\", paste the secret QBDLX desktop uses for that App ID (it writes it as " +
        "\"App secret:\" in its logs folder). Leave empty to use the default."
    override val save = "Save"
    override val secretSaved = "App Secret saved"
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
    override val statusDownloading = "Downloading"
    override val statusFailed = "Failed"
    override val statusCanceled = "Canceled"
    override fun dlSummary(done: Int, total: Int, active: Int) = "$done of $total done · $active active"
    override fun noteRetrying(n: Int, max: Int) = "Connection problem, retrying ($n/$max)…"
    override val noteRefreshing = "Renewing Qobuz credentials…"
    override val noteSystemLimit = "Paused by Android's background limit. Tap Resume."
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
    override val folderHelp = "Where new downloads are saved, with a folder per artist and album. Without a choice, Music/QobuzDownloaderX is used. " +
        "Android doesn't allow choosing the storage root or the Download folder."
    override val folderDefault = "Default: Music/QobuzDownloaderX"
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
    override val folderBanner = "The chosen download folder isn't available. Downloads are going to Music/QobuzDownloaderX meanwhile."
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
    override val playQualityTitle = "Listening quality"
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
    override val closePlayer = "Close player"
    override val more = "More"
    override val viewAlbum = "Go to album"
    override val playbackSection = "Playback"
    override val crossfadeTitle = "Fade between songs"
    override val crossfadeDesc = "Songs always follow each other without a gap. With a fade, the end of one song fades out while the next fades in. Off by default; very short songs fade less."
    override val crossfadeOff = "Off"
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
    override val ipodWarning = "Experimental. It writes to the iPod's own library. Covers and the library method were confirmed on a real nano 6G (one cover per album, shared by its songs). If a track shows the generic icon, tell me. A backup of the library is made before every change, and you can restore it here."
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
    override fun ipodReady(count: Int) = if (count < 0) "iPod found." else "iPod found · $count songs on it."
    override val ipodGuidFound = "Device identifier read from the iPod."
    override val ipodGuidMissing = "The iPod's identifier could not be read. Type its 16 characters (the FireWire GUID, like 000A27001234ABCD)."
    override val ipodGuidLabel = "FireWire GUID"
    override val ipodUsbRead = "Read the identifier over USB"
    override val ipodTabSend = "To send"
    override val ipodTabOn = "On the iPod"
    override val ipodTabPlaylists = "Playlists"
    override fun ipodPlaylistSongs(n: Int, missing: Int) = "$n songs" + if (missing > 0) " · $missing not found" else ""
    override val ipodPlaylistsEmpty = "There are no playlists on the iPod."
    override val ipodSelectPlaylist = "Select all its songs"
    override val ipodSearch = "Search title, artist or album"
    override val ipodDeleteSelected = "Delete selected files"
    override fun ipodSelectedCount(n: Int) = "$n selected"
    override val ipodSelectAllVisible = "Select all shown"
    override val ipodDeleteSelectedTitle = "Delete selected songs?"
    override fun ipodDeleteSelectedWarning(n: Int) = "$n songs and their audio files will be removed from the iPod. A library backup is made first. This cannot restore the audio files."
    override fun ipodSpace(free: String, total: String) = "$free free of $total"
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
    override val orManually = "O ingresa tus datos manualmente"
    override val appId = "App ID"
    override val appSecret = "App Secret"
    override val email = "Correo"
    override val password = "Contraseña"
    override val userId = "ID de usuario"
    override val authToken = "Token de acceso"
    override val useToken = "Iniciar sesión con ID de usuario + token"
    override val logIn = "Iniciar sesión"
    override val fillAllFields = "Completa todos los campos"
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
    override val nothingHere = "Aún no hay nada aquí"
    override val downloadAll = "Descargar todo"
    override val refresh = "Actualizar"

    override val selectQuality = "Seleccionar calidad"
    override val chooseQuality = "Elige la calidad de audio que prefieras"
    override val cancel = "Cancelar"
    override fun qualityDescription(q: Quality) = when (q) {
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
    override val appSecretTitle = "App Secret (firma de descargas)"
    override fun appSecretHelp(appId: String) = "Las descargas se firman con el App Secret del App ID $appId. Si fallan " +
        "con \"Invalid Request Signature\", pega aquí el secret que usa QBDLX de escritorio para ese App ID (lo " +
        "escribe como \"App secret:\" en su carpeta logs). Déjalo vacío para usar el predeterminado."
    override val save = "Guardar"
    override val secretSaved = "App Secret guardado"
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
    override val statusDownloading = "Descargando"
    override val statusFailed = "Fallida"
    override val statusCanceled = "Cancelada"
    override fun dlSummary(done: Int, total: Int, active: Int) = "$done de $total listas · $active activas"
    override fun noteRetrying(n: Int, max: Int) = "Problema de conexión, reintentando ($n/$max)…"
    override val noteRefreshing = "Renovando credenciales de Qobuz…"
    override val noteSystemLimit = "Pausada por el límite de Android en segundo plano. Pulsa Reanudar."
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
    override val folderHelp = "Dónde se guardan las descargas nuevas, con una carpeta por artista y álbum. Si no eliges ninguna, se usa Música/QobuzDownloaderX. " +
        "Android no permite elegir la raíz del almacenamiento ni la carpeta Descargas."
    override val folderDefault = "Predeterminada: Música/QobuzDownloaderX"
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
    override val folderBanner = "La carpeta de descargas elegida no está disponible. Mientras tanto, las descargas se guardan en Música/QobuzDownloaderX."
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
    override val playQualityTitle = "Calidad al escuchar"
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
    override val closePlayer = "Cerrar reproductor"
    override val more = "Más"
    override val viewAlbum = "Ir al álbum"
    override val playbackSection = "Reproducción"
    override val crossfadeTitle = "Fundido entre canciones"
    override val crossfadeDesc = "Las canciones siempre se encadenan sin pausa. Con fundido, el final de una baja de volumen mientras sube la siguiente. Apagado por defecto; las canciones muy cortas se funden menos."
    override val crossfadeOff = "Desactivado"
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
    override val ipodPlaylistNone = "No tienes playlists en Qobuz."
    override val ipodPlaylistLoadFailed = "No se pudo cargar la playlist de Qobuz."
    override fun ipodPlaylistDownloading(done: Int, total: Int) = "Descargando en MP3 las canciones que faltan de la playlist… $done de $total"
    override fun ipodPlaylistLoading(name: String, i: Int, n: Int) = "Leyendo la playlist \"$name\" (${i + 1} de $n)…"
    override fun ipodPlaylistMaking(name: String, i: Int, n: Int) = "Creando la playlist \"$name\" en el iPod (${i + 1} de $n)…"
    override fun ipodPlaylistSendN(n: Int) = if (n == 0) "Elige al menos una" else "Enviar $n playlist(s)"
    override val ipodPlaylistDownloadStopped = "Detenido. Las canciones ya descargadas se conservan; no se envió nada al iPod."
    override fun ipodPlaylistDone(name: String, added: Int, missing: Int, error: String?) =
        if (error != null) "Playlist \"$name\" no creada: $error" else if (added == 0) "Playlist \"$name\" no creada: ninguna de sus canciones está en el iPod (solo se envían MP3 descargados)."
        else "Playlist \"$name\" en el iPod: $added canciones" + (if (missing > 0) ", $missing sin incluir (no descargadas como MP3)." else ".")
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
    override val ipodWarning = "Experimental. Escribe en la biblioteca del propio iPod. Las carátulas y el método de biblioteca están confirmados en un nano 6G real (una carátula por álbum, compartida por sus canciones). Si una pista muestra el icono genérico, avísame. Antes de cada cambio se hace una copia de seguridad y aquí puedes restaurarla."
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
    override fun ipodReady(count: Int) = if (count < 0) "iPod encontrado." else "iPod encontrado · $count canciones."
    override val ipodGuidFound = "Identificador del dispositivo leído del iPod."
    override val ipodGuidMissing = "No se pudo leer el identificador del iPod. Escribe sus 16 caracteres (el FireWire GUID, como 000A27001234ABCD)."
    override val ipodGuidLabel = "FireWire GUID"
    override val ipodUsbRead = "Leer el identificador por USB"
    override val ipodTabSend = "Para enviar"
    override val ipodTabOn = "En el iPod"
    override val ipodTabPlaylists = "Playlists"
    override fun ipodPlaylistSongs(n: Int, missing: Int) = "$n canciones" + if (missing > 0) " · $missing no encontradas" else ""
    override val ipodPlaylistsEmpty = "No hay playlists en el iPod."
    override val ipodSelectPlaylist = "Seleccionar todas sus canciones"
    override val ipodSearch = "Buscar título, artista o álbum"
    override val ipodDeleteSelected = "Borrar archivos seleccionados"
    override fun ipodSelectedCount(n: Int) = "$n seleccionadas"
    override val ipodSelectAllVisible = "Seleccionar todos los visibles"
    override val ipodDeleteSelectedTitle = "¿Borrar las canciones seleccionadas?"
    override fun ipodDeleteSelectedWarning(n: Int) = "Se eliminarán del iPod $n canciones y sus archivos de audio. Primero se guardará una copia de la biblioteca. Los archivos de audio no se pueden recuperar desde esa copia."
    override fun ipodSpace(free: String, total: String) = "$free libres de $total"
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
