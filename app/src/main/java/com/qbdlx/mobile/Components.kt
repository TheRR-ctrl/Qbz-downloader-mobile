package com.qbdlx.mobile

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
@Composable
fun QualityDialog(current: Quality, onPick: (Quality) -> Unit, onCancel: () -> Unit) {
    Dialog(onCancel) {
        // Blur the screen behind the dialog where the device supports cross-window blur (Android 12+).
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = 48 }
                window.setDimAmount(.35f)
            }
        }
        // Fully opaque on purpose: the lists behind it used to show through and mix with the text.
        Surface(shape = RoundedCornerShape(24.dp), color = Panel, contentColor = Color.White, border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(LocalStrings.current.selectQuality, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(LocalStrings.current.chooseQuality, fontSize = 13.sp, color = Color.White.copy(alpha = .7f))
                Quality.entries.forEach { q ->
                    val accent = when (q) { Quality.HI_RES -> Gold; Quality.LOSSLESS -> Cyan; Quality.MP3 -> Cyan.copy(alpha = .55f) }
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(BorderStroke(if (q == current) 2.dp else 1.dp, accent), RoundedCornerShape(12.dp))
                            .clickable { onPick(q) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (q == Quality.MP3) "♪" else "HQ", color = accent, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).padding(top = 8.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(q.label, fontWeight = FontWeight.SemiBold)
                            Text(LocalStrings.current.qualityDescription(q), fontSize = 12.sp, color = Color.White.copy(alpha = .7f))
                        }
                    }
                }
                TextButton(onCancel, Modifier.align(Alignment.CenterHorizontally), colors = ButtonDefaults.textButtonColors(contentColor = Cyan)) { Text(LocalStrings.current.cancel) }
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
