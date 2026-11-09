package com.qbdlx.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

class MainActivity : ComponentActivity() {
    // Owned by the Application, not this Activity, so the download queue survives closing the screen.
    private val vm: MainViewModel by lazy {
        ViewModelProvider(application as QbdlxApp, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[MainViewModel::class.java]
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // The system folder picker; the answer is validated (a real test write) before it is saved.
    private val ipodPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onIpodPicked(uri)
    }

    private val knownPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onKnownPicked(uri)
    }

    private val localPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onLocalPicked(uri)
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onFolderPicked(uri)
    }

    /** QBDLX Scanner's list arrives through Android's share menu. */
    private fun handleShared(intent: android.content.Intent?) {
        intent ?: return
        val isSend = intent.action == android.content.Intent.ACTION_SEND
        val isView = intent.action == android.content.Intent.ACTION_VIEW
        if (!isSend && !isView) return
        @Suppress("DEPRECATION")
        val uri = (if (isSend) intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM) else intent.data)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        val text = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
        if (uri == null && text.isNullOrBlank()) return
        intent.action = null // handled: do not import again when the activity is recreated
        if (uri != null) vm.importList(uri) else vm.importText(text!!)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleShared(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.onAppVisible()
    }

    override fun onStop() {
        vm.persistQueue()
        super.onStop()
    }

    private var lastBack = 0L

    override fun onDestroy() {
        if (isFinishing) vm.onActivityFinished()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShared(intent)
        setContent {
            // Asked once, the first time something is queued. Without it downloads still run, but the
            // progress notification stays hidden on Android 13+.
            LaunchedEffect(vm.pickIpod) {
                if (vm.pickIpod) { vm.pickIpod = false; ipodPicker.launch(null) }
            }
            LaunchedEffect(vm.openListenerSettings) {
                if (vm.openListenerSettings) {
                    vm.openListenerSettings = false
                    runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                }
            }
            LaunchedEffect(vm.pickLocal) {
                if (vm.pickLocal) { vm.pickLocal = false; localPicker.launch(null) }
            }
            LaunchedEffect(vm.pickKnown) {
                if (vm.pickKnown) { vm.pickKnown = false; knownPicker.launch(null) }
            }
            LaunchedEffect(vm.pickFolder) {
                if (vm.pickFolder) { vm.pickFolder = false; folderPicker.launch(null) }
            }
            LaunchedEffect(vm.askNotifications) {
                if (vm.askNotifications) {
                    vm.askNotifications = false
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            // Registered first, so every screen's own back handling wins; this one only runs on the home screen.
            androidx.activity.compose.BackHandler {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastBack < 2000) finish()
                else { lastBack = now; android.widget.Toast.makeText(this@MainActivity, vm.strings.pressBackAgain, android.widget.Toast.LENGTH_SHORT).show() }
            }
            QbdlxTheme(vm.amoled) {
                CompositionLocalProvider(LocalStrings provides vm.strings) {
                    Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                        when {
                            vm.loggedIn -> AppShell(vm)
                            vm.webLogin -> WebLoginScreen(vm)
                            vm.signingIn -> SigningInScreen()
                            else -> LoginScreen(vm)
                        }
                    }
                }
            }
        }
    }
}

private fun Tab.icon(): ImageVector = when (this) {
    Tab.Home -> Icons.Default.Home
    Tab.Search -> Icons.Default.Search
    Tab.Library -> Icons.Default.LibraryMusic
    Tab.Downloads -> Icons.Default.Download
    Tab.Settings -> Icons.Default.Settings
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@androidx.compose.runtime.Composable
private fun AppShell(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    val haze = remember { HazeState() }
    // While the full-screen player is open it shows messages itself (this snackbar would be underneath it).
    LaunchedEffect(vm.error, vm.playerOpen) {
        if (!vm.playerOpen) vm.error?.let { snackbar.showSnackbar(it); vm.error = null }
    }
    LaunchedEffect(vm.notice, vm.playerOpen) {
        if (!vm.playerOpen) vm.notice?.let { snackbar.showSnackbar(it); vm.notice = null }
    }
    Box(androidx.compose.ui.Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Translucent: the screens scroll underneath and show through blurred, like the web player.
            Column(Modifier.hazeChild(haze, HazeMaterials.thin(MaterialTheme.colorScheme.background))) {
                FolderBanner(vm.folderStatus) { vm.chooseFolder() }
                AnimatedVisibility(vm.nowPlaying != null, enter = slideInVertically(tween(280, easing = UiEaseOut)) { it } + fadeIn(tween(200, easing = UiEaseOut)), exit = slideOutVertically(tween(180, easing = UiEaseOut)) { it } + fadeOut(tween(140, easing = UiEaseOut))) {
                    MiniPlayer(vm)
                }
                NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                    // Search lives in the page headers (a button at the top), not in the bottom bar.
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        selected = vm.showListen,
                        onClick = { vm.openListening() },
                        icon = { Icon(Icons.Filled.MusicNote, LocalStrings.current.nowButton) },
                        label = { Text(LocalStrings.current.nowButton, maxLines = 1, softWrap = false, fontSize = 10.sp) },
                        colors = qbdlxNavColors(),
                    )
                    Tab.entries.filter { it != Tab.Search }.forEach { t ->
                        if (t == Tab.Settings && (vm.ipodPlugged || vm.ipodProgress != null)) NavigationBarItem(
                            modifier = Modifier.weight(1f),
                            selected = vm.showIpod,
                            onClick = { vm.closeAlbum(); vm.closeArtist(); vm.closePlaylist(); vm.closeDiagnostics(); vm.closeImport(); vm.openIpod() },
                            icon = { Icon(Icons.Filled.Smartphone, LocalStrings.current.ipodEntry) },
                            label = { Text(LocalStrings.current.ipodEntry, maxLines = 1, softWrap = false, fontSize = 10.sp) },
                            colors = qbdlxNavColors(),
                        )
                        NavigationBarItem(
                            modifier = Modifier.weight(1f),
                            selected = vm.tab == t && vm.detail == null && vm.playlistDetail == null && vm.artistPage == null && !vm.showDiagnostics && !vm.showIpod && !vm.showImport && !vm.showListen,
                            onClick = { vm.closeAlbum(); vm.closeArtist(); vm.closePlaylist(); vm.closeDiagnostics(); vm.closeIpod(); vm.closeImport(); vm.tab = t },
                            icon = { Icon(t.icon(), LocalStrings.current.tab(t)) },
                            label = { Text(LocalStrings.current.tab(t), maxLines = 1, softWrap = false, fontSize = 10.sp) },
                            colors = qbdlxNavColors(),
                        )
                    }
                }
            }
        },
    ) { padding ->
        // No padding here: content runs behind the bar, and lists add LocalBottomInset at their end.
        Box(Modifier.fillMaxSize().haze(haze).background(MaterialTheme.colorScheme.background)) {
            CompositionLocalProvider(LocalBottomInset provides padding.calculateBottomPadding()) {
                // Screens fade into each other (and an album or playlist slides up a little) instead of switching at once.
                val screen = when {
                    vm.showDiagnostics -> "diagnostics"
                    vm.showIpod -> "ipod"
                    vm.showImport -> "import"
                    vm.showListen -> "listen"
                    vm.detail != null -> "album"
                    vm.playlistDetail != null -> "playlist"
                    vm.artistPage != null -> "artist"
                    else -> vm.tab.name
                }
                AnimatedContent(
                    targetState = screen,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        // Como Qobuz: una página de detalle (álbum, lista, artista...) entra empujando desde la derecha
                        // y al volver sale hacia la derecha; entre pestañas, un fundido con un leve acercamiento.
                        val details = setOf("album", "playlist", "artist", "diagnostics", "import", "ipod")
                        val push = targetState in details && initialState !in details
                        val pop = initialState in details && targetState !in details
                        when {
                            push -> (slideInHorizontally(tween(300, easing = UiEaseOut)) { it / 4 } + fadeIn(tween(220, easing = UiEaseOut))) togetherWith
                                (slideOutHorizontally(tween(300, easing = UiEaseOut)) { -it / 10 } + fadeOut(tween(160, easing = UiEaseOut)))
                            pop -> (slideInHorizontally(tween(300, easing = UiEaseOut)) { -it / 10 } + fadeIn(tween(220, easing = UiEaseOut))) togetherWith
                                (slideOutHorizontally(tween(260, easing = UiEaseOut)) { it / 4 } + fadeOut(tween(160, easing = UiEaseOut)))
                            else -> (fadeIn(tween(220, delayMillis = 40, easing = UiEaseOut)) + androidx.compose.animation.scaleIn(tween(260, easing = UiEaseOut), initialScale = 0.97f)) togetherWith
                                fadeOut(tween(120, easing = UiEaseOut))
                        }
                    },
                    label = "screen",
                ) { key ->
                    when (key) {
                        "artist" -> ArtistScreen(vm)
                        "diagnostics" -> DiagnosticsScreen(vm)
                        "ipod" -> IpodScreen(vm)
                        "import" -> ImportScreen(vm)
                        "listen" -> ListeningScreen(vm)
                        "album" -> AlbumScreen(vm)
                        "playlist" -> PlaylistScreen(vm)
                        Tab.Home.name -> HomeScreen(vm)
                        Tab.Search.name -> SearchScreen(vm)
                        Tab.Library.name -> LibraryScreen(vm)
                        Tab.Downloads.name -> DownloadsScreen(vm)
                        else -> SettingsScreen(vm)
                    }
                }
            }
            vm.pendingRequest?.let {
                QualityDialog(vm.quality, onPick = { q -> vm.confirmDownload(q) }, onCancel = { vm.pendingRequest = null })
            }
            vm.pendingBulk?.let { r ->
                val st = LocalStrings.current
                AlertDialog(
                    onDismissRequest = { vm.pendingBulk = null },
                    title = { Text(when (r) { is DownloadRequest.Albums -> st.confirmBulkAlbums(r.albums.size); is DownloadRequest.Playlists -> st.confirmBulkPlaylists(r.playlists.size); else -> "" }) },
                    confirmButton = { TextButton({ vm.confirmBulk() }) { Text(st.confirmBulkYes) } },
                    dismissButton = { TextButton({ vm.pendingBulk = null }) { Text(st.cancel) } },
                )
            }
        }
    }
    if (vm.playerOpen) PlayerHost(vm)
    }
}

/** The app's one strong ease-out curve for entrances and exits (no ease-in, no stock curves). */
val UiEaseOut = androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
