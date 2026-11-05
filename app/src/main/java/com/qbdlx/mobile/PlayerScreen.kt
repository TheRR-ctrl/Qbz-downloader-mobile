package com.qbdlx.mobile

import sh.calvin.reorderable.ReorderableItem
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class PlayerPage { Player, Explore, Lyrics, Info, Queue }

private val OnPlayer = Color.White
private val OnPlayerDim = Color.White.copy(alpha = .7f)
private val Chip = Color.White.copy(alpha = .12f)

/** The quality asked for before the real file is known ("24/48" or "24/44.1") written like Qobuz does: "24 Bit / 48 kHz". */
private fun qualityText(q: String): String {
    val m = Regex("""^\s*(\d{1,2})\s*/\s*([\d.]+)\s*(?:kHz)?\s*$""", RegexOption.IGNORE_CASE).find(q) ?: return q.ifBlank { "—" }
    return "${m.groupValues[1]} Bit / ${m.groupValues[2]} kHz"
}

private fun clock(ms: Long): String = ms.coerceAtLeast(0).let { "%02d:%02d".format(java.util.Locale.ROOT, it / 60000, it / 1000 % 60) }

/**
 * The background tone for a cover, read from a small copy of the image off the main thread. The previous tone stays
 * until the next one is ready, so the colour fades from one song to the next instead of flashing. Null = no real
 * colour in the cover (or it could not be read): the normal background is used.
 */
@Composable
private fun rememberCoverTone(url: String?): State<CoverTone?> {
    val context = LocalContext.current
    val tone = remember { mutableStateOf<CoverTone?>(null) }
    LaunchedEffect(url) { tone.value = loadCoverTone(context, url) }
    return tone
}

private suspend fun loadCoverTone(context: android.content.Context, url: String?): CoverTone? {
    if (url == null) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context).data(url).size(THUMB).allowHardware(false).build()
            val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                ?: return@runCatching null
            val small = Bitmap.createScaledBitmap(bitmap, THUMB, THUMB, true)
            val pixels = IntArray(THUMB * THUMB).also { small.getPixels(it, 0, THUMB, 0, 0, THUMB, THUMB) }
            backgroundTone(pixels)
        }.getOrNull()
    }
}

private const val THUMB = 96

/** The full-screen player: the cover, the controls and, behind three buttons, Explore, Info and the queue. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerHost(vm: MainViewModel) {
    val (album, track) = vm.nowPlaying ?: run { vm.playerOpen = false; return }
    var page by rememberSaveable { mutableStateOf(PlayerPage.Player) }
    val scope = rememberCoroutineScope()

    // The screen slides up from the bottom when opened and back down when closed; it can also be dragged down from the top.
    // The position is a fraction of the screen height (1 = below the screen), so it never depends on when the size is measured.
    val offset = remember { Animatable(1f) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) { offset.animateTo(0f, tween(360, easing = FastOutSlowInEasing)) }
    /** Slides the player away, then closes it and runs [after] (e.g. opening an album underneath). */
    val close: (() -> Unit) -> Unit = { after ->
        scope.launch {
            offset.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
            vm.playerOpen = false
            after()
        }
    }
    BackHandler { if (page != PlayerPage.Player) page = PlayerPage.Player else close {} }

    val tone by rememberCoverTone(album.coverUrl)
    val fallback = Panel // the text here is always white, so the fallback must be dark whatever the theme
    val top by animateColorAsState(tone?.let { Color(it.top) } ?: fallback, tween(700), label = "top")
    val bottom by animateColorAsState(tone?.let { Color(it.bottom) } ?: fallback, tween(700), label = "bottom")

    // Messages (errors, "queued") show here: the app's own snackbar is underneath this screen.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(it); vm.error = null } }
    LaunchedEffect(vm.notice) { vm.notice?.let { snackbar.showSnackbar(it); vm.notice = null } }

    CompositionLocalProvider(LocalContentColor provides OnPlayer) {
      Box(
          Modifier.fillMaxSize()
              .onSizeChanged { heightPx = it.height.toFloat() }
              .graphicsLayer { translationY = offset.value * size.height },
      ) {
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom))).statusBarsPadding().navigationBarsPadding(),
        ) {
            // The grab area at the top: tap to close, or drag down.
            Box(
                Modifier.fillMaxWidth().height(40.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta -> scope.launch { offset.snapTo((offset.value + delta / heightPx).coerceAtLeast(0f)) } },
                        onDragStopped = { velocity ->
                            if (offset.value > 0.22f || velocity > 1400f) close {}
                            else scope.launch { offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                        },
                    )
                    .clickable { close {} },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .35f)))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 14 }) togetherWith fadeOut(tween(140)) },
                    label = "page",
                ) { p ->
                    when (p) {
                        PlayerPage.Player -> NowPlayingPage(vm, album, track, close)
                        PlayerPage.Explore -> ExplorePage(vm, album, close)
                        PlayerPage.Lyrics -> LyricsPage(vm, album, track)
                        PlayerPage.Info -> InfoPage(vm, album, track, close)
                        PlayerPage.Queue -> QueuePage(vm)
                    }
                }
            }
            AnimatedVisibility(page != PlayerPage.Player, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                PlayerMini(vm, album, track) { page = PlayerPage.Player }
            }
            PlayerBar(page) { page = if (page == it) PlayerPage.Player else it }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp))
      }
    }
}

@Composable
private fun PlayerBar(page: PlayerPage, onPick: (PlayerPage) -> Unit) {
    val s = LocalStrings.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(
            Triple(PlayerPage.Explore, Icons.Outlined.Explore, s.playerExplore),
            Triple(PlayerPage.Lyrics, Icons.Outlined.Lyrics, s.lyricsTitle),
            Triple(PlayerPage.Info, Icons.Outlined.Info, s.playerInfo),
            Triple(PlayerPage.Queue, Icons.Outlined.QueueMusic, s.playerQueue),
        ).forEach { (p, icon, label) ->
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(if (page == p) Chip else Color.Transparent)
                    .clickable { onPick(p) }.padding(horizontal = 20.dp, vertical = 12.dp),
            ) { Icon(icon, label, tint = OnPlayer) }
        }
    }
}

/** The small card above the bar on the other pages; tap it to come back to the big player. */
@Composable
private fun PlayerMini(vm: MainViewModel, album: Album, track: Track, onOpen: () -> Unit) {
    val progress by rememberPlaybackProgress(vm, track)
    Surface(
        shape = RoundedCornerShape(16.dp), color = Chip, contentColor = OnPlayer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).swipeToSkip(vm::next, vm::previous).clickable(onClick = onOpen),
    ) {
        Column {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(album.coverUrl, 44)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.displayTitle, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.basicMarquee())
                    Text(album.artist, fontSize = 12.sp, color = OnPlayerDim, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.basicMarquee())
                }
                IconButton({ vm.togglePlay() }) {
                    Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, LocalStrings.current.playPause, tint = OnPlayer)
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = .2f))) {
                Box(Modifier.fillMaxWidth(progress.first.coerceIn(0f, 1f)).height(2.dp).background(OnPlayer))
            }
        }
    }
}

/** (fraction played, position ms, length ms), refreshed a few times a second while the screen is shown. */
@Composable
private fun rememberPlaybackProgress(vm: MainViewModel, track: Track): State<Triple<Float, Long, Long>> {
    val state = remember(track.id) { mutableStateOf(Triple(0f, 0L, track.durationSec * 1000L)) }
    LaunchedEffect(track.id) {
        while (true) {
            val len = vm.engine.duration.takeIf { it > 0 } ?: (track.durationSec * 1000L)
            val pos = vm.engine.position
            state.value = Triple(if (len > 0) pos.toFloat() / len else 0f, pos, len)
            delay(250)
        }
    }
    return state
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingPage(vm: MainViewModel, album: Album, track: Track, close: (() -> Unit) -> Unit) {
    val s = LocalStrings.current
    val q = vm.queueState
    val progress by rememberPlaybackProgress(vm, track)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var menu by remember { mutableStateOf(false) }
    var qualityDialog by remember { mutableStateOf(false) }
    var sleepDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val quality = vm.nowQuality
    val len = progress.third.coerceAtLeast(1)
    val posMs = if (dragging) (dragValue * len).toLong() else progress.second

    if (qualityDialog) QualityDialog(vm.playQuality, { vm.updatePlayQuality(it); qualityDialog = false }, { qualityDialog = false })
    if (sleepDialog) AlertDialog(
        onDismissRequest = { sleepDialog = false },
        title = { Text(s.sleepTimer) },
        text = {
            Column {
                listOf(15, 30, 45, 60, 90).forEach { m ->
                    TextButton({ vm.setSleepTimer(m); sleepDialog = false }, Modifier.fillMaxWidth()) { Text(s.sleepMinutes(m)) }
                }
                if (vm.sleepEndsAt > 0) TextButton({ vm.setSleepTimer(0); sleepDialog = false }, Modifier.fillMaxWidth()) { Text(s.sleepOff) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton({ sleepDialog = false }) { Text(s.cancel) } },
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(6.dp))
        // The cover follows the finger and, when the song changes, the old one slides out and the new one slides in.
        var skipDir by remember { mutableIntStateOf(1) }
        var dragX by remember { mutableFloatStateOf(0f) }
        val shownX by animateFloatAsState(dragX, if (dragX == 0f) spring(stiffness = Spring.StiffnessMedium) else snap(), label = "coverDrag")
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { translationX = shownX }
                .clip(RoundedCornerShape(16.dp))
                .swipeToSkip(vm::next, vm::previous, onDrag = { dragX = it }, onSkip = { skipDir = it }),
        ) {
            // Keyed on the song (not the cover URL) so songs of the same album slide too.
            AnimatedContent(
                targetState = track.id to album.coverUrl,
                transitionSpec = {
                    (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it * skipDir } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it * skipDir } + fadeOut(tween(200)))
                },
                label = "cover",
            ) { (_, url) ->
                AsyncImage(model = url, contentDescription = album.title, modifier = Modifier.fillMaxSize().background(Chip))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            InfoPill(onClick = { qualityDialog = true }) {
                if (quality?.isHiRes == true || (quality == null && track.hiRes)) HiResBadge("Hi-Res") else Icon(Icons.Default.Album, null, Modifier.size(20.dp))
                Text(quality?.label() ?: qualityText(track.quality), fontSize = 14.sp)
            }
            InfoPill {
                Icon(Icons.Default.PhoneAndroid, null, Modifier.size(20.dp))
                Text(Build.MODEL, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Crossfade(targetState = track, animationSpec = tween(350), label = "title", modifier = Modifier.weight(1f)) { t ->
                Column {
                    Text(t.displayTitle, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (t.explicit) {
                            Text("E", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(OnPlayerDim).padding(horizontal = 4.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        // Tapping the artist or the album opens it, as in the Qobuz player.
                        Text(album.artist, color = OnPlayerDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { close { vm.openArtist(album.artist) } })
                        Text(" • ", color = OnPlayerDim)
                        Text(album.title, color = OnPlayerDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(4.dp)).clickable { close { vm.openAlbum(album.id) } })
                    }
                }
            }
            val fav = vm.isFavorite(track.id)
            IconButton({ vm.toggleFavorite(album, track) }) {
                Icon(if (fav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder, null, tint = OnPlayer)
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, s.more, tint = OnPlayer) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(s.download) }, onClick = { menu = false; vm.requestDownload(DownloadRequest.Tracks(listOf(album to track))) })
                    DropdownMenuItem(text = { Text(s.viewAlbum) }, onClick = { menu = false; close { vm.openAlbum(album.id) } })
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Timer, null) },
                        text = { Text(if (vm.sleepEndsAt > 0) s.sleepRemaining(((vm.sleepEndsAt - System.currentTimeMillis()) / 60_000L + 1).toInt()) else s.sleepTimer) },
                        onClick = { menu = false; sleepDialog = true },
                    )
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Tune, null) }, text = { Text(s.equalizer) }, onClick = {
                        menu = false
                        val i = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, vm.engine.audioSessionId)
                            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                        try { context.startActivity(i) } catch (e: ActivityNotFoundException) { vm.notice = s.noEqualizer }
                    })
                }
            }
        }
        Column {
            Slider(
                value = if (dragging) dragValue else progress.first.coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { vm.seekTo((dragValue * len).toLong()); dragging = false },
                colors = SliderDefaults.colors(thumbColor = OnPlayer, activeTrackColor = OnPlayer, inactiveTrackColor = Color.White.copy(alpha = .25f)),
                // Like the Qobuz player: a thin rounded bar with no dot, which only shows a small thumb while it is dragged.
                thumb = { Box(Modifier.size(if (dragging) 14.dp else 1.dp).clip(CircleShape).background(OnPlayer)) },
                track = { st ->
                    SliderDefaults.Track(st, Modifier.height(6.dp), colors = SliderDefaults.colors(activeTrackColor = OnPlayer, inactiveTrackColor = Color.White.copy(alpha = .25f)), thumbTrackGapSize = 0.dp, trackInsideCornerSize = 0.dp, drawStopIndicator = null)
                },
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clock(posMs), fontSize = 12.sp, color = OnPlayerDim)
                Text("-" + clock(len - posMs), fontSize = 12.sp, color = OnPlayerDim)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ToggleIcon(Icons.Default.Shuffle, s.shuffle, q.shuffle) { vm.toggleShuffle() }
            IconButton({ vm.previous() }, Modifier.size(56.dp)) { Icon(Icons.Default.SkipPrevious, s.previous, Modifier.size(38.dp), tint = OnPlayer) }
            IconButton({ vm.togglePlay() }, Modifier.size(76.dp)) {
                Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, s.playPause, Modifier.size(64.dp), tint = OnPlayer)
            }
            IconButton({ vm.next() }, Modifier.size(56.dp)) { Icon(Icons.Default.SkipNext, s.next, Modifier.size(38.dp), tint = OnPlayer) }
            ToggleIcon(if (q.repeat == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, s.repeat, q.repeat != RepeatMode.OFF) { vm.cycleRepeat() }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun InfoPill(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(Chip).let { if (onClick != null) it.clickable(onClick = onClick) else it }.border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Shuffle / repeat: a rounded plate and a dot underneath when switched on, like the Qobuz player. */
@Composable
private fun ToggleIcon(icon: ImageVector, description: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Chip else Color.Transparent).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = OnPlayer)
        }
        Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(if (on) OnPlayer else Color.Transparent))
    }
}

// ---- Explore ------------------------------------------------------------------------------------------------------

@Composable
private fun ExploreCard(coverUrl: String?, title: String, subtitle: String, subtitleColor: Color = OnPlayerDim, onClick: () -> Unit, onDownload: (() -> Unit)? = null, cover: @Composable (() -> Unit)? = null) {
    Column(Modifier.width(160.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box {
            if (cover != null) cover() else Cover(coverUrl, 160)
            if (onDownload != null) Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f)).clickable(onClick = onDownload),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Download, LocalStrings.current.download, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, fontSize = 13.sp, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ExploreHeading(text: String) =
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 12.dp))

@Composable
private fun ExploreRow(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) =
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), content = content)

@Composable
private fun ExplorePage(vm: MainViewModel, album: Album, close: (() -> Unit) -> Unit) {
    val s = LocalStrings.current
    LaunchedEffect(album.artist) { vm.loadExplore(album.artist) }
    val label = vm.infoDetail?.takeIf { it.album.id == album.id }?.album?.label?.ifBlank { null } ?: album.label.ifBlank { null }
    LaunchedEffect(album.id) { vm.loadInfo(album.id) }
    LaunchedEffect(label) { if (label != null) vm.loadLabelAlbums(label, album.id) }
    val items = vm.exploreAlbums
    val top = vm.exploreTop
    val labelAlbums = vm.exploreLabelAlbums
    Column(Modifier.fillMaxSize().padding(top = 8.dp).verticalScroll(rememberScrollState())) {
        Text(s.exploreTitle, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        ExploreHeading(s.exploreMoreOf(album.artist))
        when {
            items == null -> Text("…", color = OnPlayerDim, modifier = Modifier.padding(16.dp))
            items.isEmpty() -> Text(s.exploreEmpty, color = OnPlayerDim, modifier = Modifier.padding(16.dp))
            else -> ExploreRow {
                itemsIndexed(items, key = { _, a -> a.id }) { _, a ->
                    ExploreCard(a.coverUrl, a.title, a.artist, onClick = { close { vm.openAlbum(a.id) } }, onDownload = { vm.requestDownload(DownloadRequest.WholeAlbum(a)) })
                }
            }
        }
        if (!top.isNullOrEmpty()) {
            ExploreHeading(s.exploreWith(album.artist))
            ExploreRow {
                item(key = "top") {
                    ExploreCard(null, album.artist, s.exploreTopTracks, subtitleColor = Color(0xFFE6B800), onClick = { vm.playList(top, 0) }, cover = {
                        Box(Modifier.size(160.dp).clip(RoundedCornerShape(10.dp)).background(Chip), contentAlignment = Alignment.Center) {
                            CoverMosaic(top.map { it.first.coverUrl }.filterNotNull().distinct().take(4), 160)
                        }
                    })
                }
                itemsIndexed(top, key = { _, p -> p.second.id }) { i, (a, tr) ->
                    ExploreCard(a.coverUrl, tr.displayTitle, a.title, onClick = { vm.playList(top, i) })
                }
            }
        }
        if (label != null && !labelAlbums.isNullOrEmpty()) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Chip), contentAlignment = Alignment.Center) { Icon(Icons.Default.Album, null, tint = OnPlayer) }
                Column {
                    Text(s.exploreLabelOf, fontSize = 15.sp, color = OnPlayerDim)
                    Text(s.exploreLabelName(label), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            ExploreRow {
                itemsIndexed(labelAlbums, key = { _, a -> a.id }) { _, a ->
                    ExploreCard(a.coverUrl, a.title, a.artist, onClick = { close { vm.openAlbum(a.id) } }, onDownload = { vm.requestDownload(DownloadRequest.WholeAlbum(a)) })
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---- Lyrics -------------------------------------------------------------------------------------------------------

@Composable
private fun LyricsPage(vm: MainViewModel, album: Album, track: Track) {
    val s = LocalStrings.current
    LaunchedEffect(track.id) { vm.loadLyrics(album, track) }
    val progress by rememberPlaybackProgress(vm, track)
    val lyrics = vm.lyrics
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.lyricsTitle, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (lyrics?.synced == true) {
                Text("${s.lyricsSync}: ${vm.lyricsOffsetMs} ms", fontSize = 12.sp, color = OnPlayerDim)
                TextButton({ vm.updateLyricsOffset(vm.lyricsOffsetMs - 250) }) { Text("−", fontSize = 20.sp) }
                TextButton({ vm.updateLyricsOffset(vm.lyricsOffsetMs + 250) }) { Text("+", fontSize = 20.sp) }
            }
        }
        when {
            vm.lyricsLoading -> Text("…", color = OnPlayerDim, modifier = Modifier.padding(16.dp))
            lyrics == null || lyrics.lines.isEmpty() -> Text(s.lyricsNone, color = OnPlayerDim, modifier = Modifier.padding(16.dp))
            else -> {
                val current = if (lyrics.synced) LyricsParser.currentIndex(lyrics.lines, progress.second + vm.lyricsOffsetMs) else -1
                val state = androidx.compose.foundation.lazy.rememberLazyListState()
                // Dragging the list lets the reader browse without being pulled back; "Resincronizar" returns to the line being sung.
                var browsing by remember(track.id) { mutableStateOf(false) }
                val dragged by state.interactionSource.collectIsDraggedAsState()
                LaunchedEffect(dragged) { if (dragged) browsing = true }
                LaunchedEffect(current, browsing) { if (!browsing && current >= 0) state.animateScrollToItem(current) } // the padding puts it in the first third
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                    // a third of the height as padding above: the line being sung sits in the first third however many fit
                    val pad = if (lyrics.synced) (maxHeight / 3 - 24.dp).coerceAtLeast(0.dp) else 12.dp
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = pad, bottom = pad)) {
                        itemsIndexed(lyrics.lines) { i, line ->
                            val on = !lyrics.synced || i == current
                            // Emil's motion rules: animate transform and opacity (never font size, which re-lays out the text every frame),
                            // ease-out, short. Lines fade with their distance from the one being sung, like a scrolling wheel.
                            val dist = if (lyrics.synced && current >= 0) kotlin.math.abs(i - current) else 0
                            val scale by androidx.compose.animation.core.animateFloatAsState(if (on && lyrics.synced) 1.16f else 1f, androidx.compose.animation.core.tween(if (on) 280 else 200, easing = UiEaseOut), label = "scale")
                            val alpha by androidx.compose.animation.core.animateFloatAsState(
                                when { !lyrics.synced || dist == 0 -> 1f; dist == 1 -> .55f; dist == 2 -> .42f; else -> .3f },
                                androidx.compose.animation.core.tween(if (on) 280 else 200, easing = UiEaseOut), label = "alpha",
                            )
                            Text(
                                line.text.ifBlank { "♪" }, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                color = OnPlayer.copy(alpha = alpha),
                                modifier = Modifier.fillMaxWidth(if (lyrics.synced) .85f else 1f).padding(vertical = 8.dp)
                                    .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f) }
                                    .let { if (lyrics.synced) it.clickable { browsing = false; vm.seekTo((line.timeMs - vm.lyricsOffsetMs).coerceAtLeast(0)) } else it },
                            )
                        }
                    }
                }
                if (browsing && lyrics.synced) Button(
                    { browsing = false }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                ) { Text(s.lyricsResync) }
                }
            }
        }
    }
}

// ---- Info ---------------------------------------------------------------------------------------------------------

@Composable
private fun InfoPage(vm: MainViewModel, album: Album, track: Track, close: (() -> Unit) -> Unit) {
    val s = LocalStrings.current
    LaunchedEffect(album.id) { vm.loadInfo(album.id) }
    val detail = vm.infoDetail?.takeIf { it.album.id == album.id }
    val full = detail?.album ?: album
    val fullTrack = detail?.tracks?.firstOrNull { it.id == track.id } ?: track
    val credits = parsePerformers(fullTrack.performers)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(s.creditsTitle, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
        if (credits.isEmpty()) {
            Text(if (fullTrack.composer.isNotBlank()) "${fullTrack.composer} — Composer" else s.noCredits, color = OnPlayerDim)
        } else credits.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = .3f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(c.initials, fontWeight = FontWeight.Medium, color = OnPlayerDim)
                }
                Column {
                    Text(c.name, fontWeight = FontWeight.SemiBold)
                    if (c.roles.isNotEmpty()) Text(c.roles.joinToString(", "), fontSize = 13.sp, color = OnPlayerDim)
                }
            }
        }
        if (full.copyright.isNotBlank()) Text(full.copyright, fontSize = 13.sp, color = OnPlayerDim, modifier = Modifier.padding(top = 6.dp))

        Text(s.aboutRelease, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = Chip, contentColor = OnPlayer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Cover(full.coverUrl, 72)
                    Column {
                        Text(full.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(full.artist, color = OnPlayerDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                InfoRow(s.infoGenre, full.genre)
                InfoRow(s.infoLabel, full.label)
                InfoRow(s.infoReleaseDate, full.releaseDate.ifBlank { full.year })
                InfoRow(s.infoTracks, if (full.tracksCount > 0) "${full.tracksCount}" else "")
                InfoRow(s.infoDuration, albumDuration(full.durationSec))
                InfoRow(s.infoQuality, vm.nowQuality?.label() ?: full.quality)
            }
        }
        TextButton({ close { vm.openAlbum(album.id) } }) { Text(s.viewAlbum, color = OnPlayer) }
        Spacer(Modifier.height(8.dp))
    }
}

/** One line of the release card; a value Qobuz didn't give is left out rather than shown empty. */
@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.45f))
        Text(value, color = OnPlayerDim, modifier = Modifier.weight(0.55f))
    }
}

// ---- Queue --------------------------------------------------------------------------------------------------------

@Composable
private fun QueuePage(vm: MainViewModel) {
    val s = LocalStrings.current
    val q = vm.queueState
    var showHistory by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val offset = q.history.size // index of the current track on the queue page

    val lazyState = androidx.compose.foundation.lazy.rememberLazyListState()
    // The first upcoming song sits after: title, [history], "current" heading, the current song and the "next" heading.
    val firstUp = 1 + (if (showHistory && q.history.isNotEmpty()) 1 + q.history.size else 0) + 1 + (if (q.current != null) 1 else 0) + 1
    val reorder = sh.calvin.reorderable.rememberReorderableLazyListState(lazyState) { from, to -> vm.moveUpcoming(from.index - firstUp, to.index - firstUp) }
    LazyColumn(Modifier.fillMaxSize(), state = lazyState, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.queueTitle, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(if (showHistory) Color.White.copy(alpha = .22f) else Chip)
                        .clickable { showHistory = !showHistory }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) { Icon(Icons.Default.History, null, Modifier.size(18.dp)); Text(s.queueHistory, fontSize = 14.sp) }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, s.more, tint = OnPlayer) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(s.queueClear) }, onClick = { menu = false; vm.clearUpcoming() })
                    }
                }
            }
        }
        if (showHistory && q.history.isNotEmpty()) {
            item { QueueHeading(s.queueHistory) }
            itemsIndexed(q.history) { i, (a, t) -> QueueRow(a, t, dim = true, onClick = { vm.jumpTo(i) }, onRemove = null) }
        }
        item { QueueHeading(s.queueCurrent) }
        q.current?.let { (a, t) -> item { QueueRow(a, t, dim = false, onClick = { vm.jumpTo(offset) }, onRemove = null) } }
        item { QueueHeading(s.queueNext) }
        if (q.upcoming.isEmpty()) item { Text(s.queueEmpty, color = OnPlayerDim, modifier = Modifier.padding(vertical = 8.dp)) }
        itemsIndexed(q.upcoming, key = { _, it -> System.identityHashCode(it) }) { k, it ->
            val (a, t) = it
            ReorderableItem(reorder, key = System.identityHashCode(it)) { dragging ->
                val lift by androidx.compose.animation.core.animateFloatAsState(if (dragging) 1.03f else 1f, androidx.compose.animation.core.tween(160), label = "lift")
                Row(Modifier.graphicsLayer { scaleX = lift; scaleY = lift }.background(if (dragging) Chip else Color.Transparent), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { QueueRow(a, t, dim = false, onClick = { vm.jumpTo(offset + 1 + k) }, onRemove = { vm.removeUpcoming(k) }) }
                    Icon(Icons.Default.DragHandle, null, tint = OnPlayerDim, modifier = Modifier.padding(end = 4.dp).size(24.dp).draggableHandle())
                }
            }
        }
    }
}

@Composable
private fun QueueHeading(text: String) = Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))

@Composable
private fun QueueRow(album: Album, track: Track, dim: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(album.coverUrl, 52)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = if (dim) OnPlayerDim else OnPlayer)
            Text(track.artist.ifBlank { album.artist }, fontSize = 13.sp, color = OnPlayerDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) IconButton(onRemove) { Icon(Icons.Default.Close, LocalStrings.current.removeFromQueue, tint = OnPlayerDim) }
    }
}
