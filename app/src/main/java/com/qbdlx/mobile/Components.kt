package com.qbdlx.mobile

import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.valentinilk.shimmer.shimmer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import android.os.Build
import android.view.WindowManager
import coil.compose.AsyncImage

@Composable
fun HiResBadge(text: String = "Hi-Res") = QualityBadge(text, hiRes = true)

/** The quality of a track or album in a small box: gold for Hi-Res, cyan for CD quality (16/44.1) and MP3. */
@Composable
fun QualityBadge(text: String, hiRes: Boolean) {
    val color = if (hiRes) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Text(
        text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier
            .border(BorderStroke(1.dp, color), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
fun Cover(url: String?, size: Int, modifier: Modifier = Modifier) {
    var loading by remember(url) { mutableStateOf(url != null) }
    AsyncImage(
        url, null,
        modifier.size(size.dp).clip(RoundedCornerShape(6.dp))
            .then(if (loading) Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)).shimmer() else Modifier),
        contentScale = ContentScale.Crop,
        onState = { loading = it is coil.compose.AsyncImagePainter.State.Loading },
    )
}

/** A cover with a small download button in its corner, like the web player's card actions. */
@Composable
private fun CoverWithDownload(url: String?, onDownload: () -> Unit) {
    Box {
        Cover(url, 150)
        Box(
            Modifier.align(Alignment.BottomEnd).padding(6.dp).size(32.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = .65f))
                .clickable(onClick = onDownload),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Download, LocalStrings.current.download, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/** Up to four covers in a 2x2 mosaic (a playlist's cover); missing ones repeat the first. */
@Composable
fun CoverMosaic(urls: List<String>, size: Int, modifier: Modifier = Modifier) {
    Column(modifier.size(size.dp).clip(RoundedCornerShape(6.dp))) {
        for (row in 0 until 2) Row(Modifier.weight(1f)) {
            for (col in 0 until 2) {
                val url = urls.getOrNull(row * 2 + col) ?: urls.firstOrNull()
                AsyncImage(url, null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
            }
        }
    }
}

/** A playlist's mosaic with the download button over the bottom-right corner. */
@Composable
private fun CoverGrid(urls: List<String>, onDownload: () -> Unit) {
    Box {
        CoverMosaic(urls, 150)
        Box(
            Modifier.align(Alignment.BottomEnd).padding(6.dp).size(32.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = .65f))
                .clickable(onClick = onDownload),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Download, LocalStrings.current.download, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CardText(title: String, subtitle: String, extra: String) {
    Spacer(Modifier.height(4.dp))
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (extra.isNotBlank()) Text(extra, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f), maxLines = 1)
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, onDownload: () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CoverWithDownload(album.coverUrl, onDownload)
        CardText(album.title, album.artist, "")
        if (album.quality.isNotBlank()) QualityBadge(album.quality, album.hiRes)
    }
}

@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit, onDownload: () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (playlist.covers.size >= 2) CoverGrid(playlist.covers, onDownload)
        else CoverWithDownload(playlist.coverUrl, onDownload)
        CardText(playlist.name, playlist.owner, if (playlist.tracksCount > 0) LocalStrings.current.tracks(playlist.tracksCount) else "")
    }
}

/**
 * The one filter chip used by every menu (Home tags, Library, Downloads, Settings), with the colours spelled out
 * so no chip falls back to Material's grey: selected is cyan on a dark-cyan fill, unselected is outlined.
 */
@Composable
fun QChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    // Qobuz's chips: dark grey pills, and the selected one turns white with dark text.
    FilterChip(
        selected = selected, onClick = onClick, label = label,
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = c.surfaceVariant,
            labelColor = c.onSurface,
            selectedContainerColor = c.secondaryContainer,
            selectedLabelColor = c.onSecondaryContainer,
        ),
        border = null,
    )
}

/** Bottom tabs: the selected one is clearly marked (accent icon and label on a soft accent pill); the rest stay muted. */
@Composable
fun qbdlxNavColors(): NavigationBarItemColors {
    val c = MaterialTheme.colorScheme
    return NavigationBarItemDefaults.colors(
        selectedIconColor = c.primary,
        selectedTextColor = c.primary,
        indicatorColor = c.primary.copy(alpha = 0.18f),
        unselectedIconColor = c.onSurfaceVariant.copy(alpha = 0.75f),
        unselectedTextColor = c.onSurfaceVariant.copy(alpha = 0.75f),
    )
}

/**
 * Warns that the chosen download folder can't be used and that downloads are going to the default one. It sits in
 * the bottom bar, so the lists' bottom padding keeps it from covering anything.
 */
@Composable
fun FolderBanner(status: FolderStatus, onChoose: () -> Unit) {
    if (!status.visible) return
    val s = LocalStrings.current
    Surface(
        shape = RoundedCornerShape(12.dp), color = Panel, contentColor = Color.White, border = BorderStroke(1.dp, Gold),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = Gold)
                Spacer(Modifier.width(10.dp))
                Text(s.folderBanner, fontSize = 13.sp)
            }
            Row(Modifier.align(Alignment.End)) {
                TextButton(onChoose, colors = ButtonDefaults.textButtonColors(contentColor = Cyan)) { Text(s.folderBannerChoose) }
                TextButton({ status.dismiss() }, colors = ButtonDefaults.textButtonColors(contentColor = Cyan)) { Text(s.folderBannerDismiss) }
            }
        }
    }
}

/** A heading inside a settings page, in the gold accent. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)

/** The "Select Quality" sheet from the Android mockups. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QualityDialog(
    current: Quality, onPick: (Quality) -> Unit, onCancel: () -> Unit,
    /** When given, the dialog offers one choice for Wi-Fi and another for mobile data: (Wi-Fi, data, on Wi-Fi now, pick for Wi-Fi?). */
    perNetwork: Triple<Quality, Quality, Boolean>? = null, onPickFor: ((Quality, Boolean) -> Unit)? = null,
) {
    var tabWifi by remember { mutableStateOf(perNetwork?.third ?: true) }
    val shown = if (perNetwork == null) current else if (tabWifi) perNetwork.first else perNetwork.second
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onCancel, containerColor = Color(0xFF151515), contentColor = Color.White,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // Como la hoja «Calidad de audio» de Qobuz: icono redondo, nombre y detalle, y una marca dorada en la elegida.
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(LocalStrings.current.selectQuality, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(LocalStrings.current.chooseQuality, fontSize = 13.sp, color = Color.White.copy(alpha = .7f))
            Spacer(Modifier.height(6.dp))
            if (perNetwork != null && onPickFor != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(true to LocalStrings.current.qualityOnWifi, false to LocalStrings.current.qualityOnData).forEach { (w, label) ->
                    val on = tabWifi == w
                    Text(
                        label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.Black else Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Gold else Color.White.copy(alpha = .1f))
                            .clickable { tabWifi = w }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Quality.entries.forEach { q ->
                val hi = q == Quality.HI_RES_MAX || q == Quality.HI_RES
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { if (perNetwork != null && onPickFor != null) onPickFor(q, tabWifi) else onPick(q) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                        Text(if (hi) "Hi-Res" else if (q == Quality.LOSSLESS) "CD" else "MP3", color = if (hi) Gold else Color.White, fontWeight = FontWeight.Bold, fontSize = if (hi) 9.sp else 11.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(q.label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(LocalStrings.current.qualityDescription(q), fontSize = 13.sp, color = Color.White.copy(alpha = .65f))
                    }
                    if (q == shown) Icon(androidx.compose.material.icons.Icons.Default.Check, null, tint = Gold)
                }
            }
        }
    }
}

/**
 * Swipe left for the next song, right for the previous one. [onDrag] reports how far the finger has moved (0 when it
 * lets go) so the view can follow it; [onSkip] is told the direction (1 = next, -1 = previous) just before the change.
 */
fun Modifier.swipeToSkip(onNext: () -> Unit, onPrevious: () -> Unit, onDrag: (Float) -> Unit = {}, onSkip: (Int) -> Unit = {}): Modifier = pointerInput(Unit) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            if (total < -SWIPE_SKIP_PX) { onSkip(1); onNext() } else if (total > SWIPE_SKIP_PX) { onSkip(-1); onPrevious() }
            total = 0f; onDrag(0f)
        },
        onDragCancel = { total = 0f; onDrag(0f) },
        onHorizontalDrag = { _, dx -> total += dx; onDrag(total) },
    )
}

private const val SWIPE_SKIP_PX = 120f

/** Dragging up on the mini player opens the full one. */
fun Modifier.swipeUpToOpen(onOpen: () -> Unit): Modifier = pointerInput(Unit) {
    var total = 0f
    detectVerticalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = { if (total < -SWIPE_SKIP_PX / 2) onOpen(); total = 0f },
        onDragCancel = { total = 0f },
        onVerticalDrag = { _, dy -> total += dy },
    )
}


private val GENRE_COLORS = listOf(
    0xFF6A4C93 to 0xFF3A2A5C, 0xFFB5651D to 0xFF5C3310, 0xFF1F7A8C to 0xFF0E3A44, 0xFF9B2335 to 0xFF4A1019,
    0xFF2E7D32 to 0xFF123814, 0xFF34568B to 0xFF17273F, 0xFF8E6C1A to 0xFF40300A, 0xFF7B3F61 to 0xFF3A1D2E,
)

/** El «Selector de género» de Qobuz: tarjetas de color en dos columnas, con la elegida marcada arriba a la derecha. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GenreSheet(genres: List<Pair<String, String>>, selected: String?, onPick: (String?) -> Unit, onClose: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onClose, containerColor = Color(0xFF151515), contentColor = Color.White,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Selector de género", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    LocalStrings.current.all, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected == null) Color.Black else Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected == null) Gold else Color.White.copy(alpha = .12f))
                        .clickable { onPick(null) }.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                genres.chunked(2).forEachIndexed { fila, par ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        par.forEachIndexed { col, g ->
                            val c = GENRE_COLORS[(fila * 2 + col) % GENRE_COLORS.size]
                            val on = g.first == selected
                            Box(
                                Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(12.dp))
                                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(c.first), Color(c.second))))
                                    .clickable { onPick(g.first) }.padding(12.dp),
                            ) {
                                Text(g.second, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, modifier = Modifier.align(Alignment.BottomStart))
                                Box(
                                    Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape)
                                        .background(if (on) Gold else Color.Transparent).border(BorderStroke(2.dp, if (on) Gold else Color.White.copy(alpha = .8f)), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { if (on) Icon(androidx.compose.material.icons.Icons.Default.Check, null, Modifier.size(14.dp), tint = Color.Black) }
                            }
                        }
                        if (par.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Continuar con todos los géneros", fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).border(BorderStroke(1.dp, Color.White.copy(alpha = .5f)), RoundedCornerShape(50))
                    .clickable { onPick(null) }.padding(vertical = 14.dp),
            )
        }
    }
}


/** Icono de play/pausa que, mientras carga el búfer, lleva un círculo girando alrededor. */
@Composable
fun PlayPauseIcon(vm: MainViewModel, size: androidx.compose.ui.unit.Dp, tint: Color) {
    val s = LocalStrings.current
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, s.playPause, Modifier.size(size * 0.8f), tint = tint)
        androidx.compose.animation.AnimatedVisibility(vm.buffering, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(size), color = tint, strokeWidth = 2.dp)
        }
    }
}
