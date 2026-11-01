package com.qbdlx.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector

private enum class TrackDialog { None, Details, Playlists, Remove }

/** The ⋮ menu of a song: queue, navigation, iPod, share, details, playlists and the downloaded file. */
@Composable
fun TrackMenu(vm: MainViewModel, album: Album, track: Track, expanded: Boolean, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val task = vm.downloads.latestFor(track.id)
    val done = task?.status == TaskStatus.COMPLETED
    val open = task?.status?.isOpen == true
    var dialog by remember { mutableStateOf(TrackDialog.None) }

    @Composable
    fun Item(label: String, icon: ImageVector, enabled: Boolean = true, action: () -> Unit) =
        DropdownMenuItem({ Text(label) }, { onDismiss(); action() }, leadingIcon = { Icon(icon, null) }, enabled = enabled)

    DropdownMenu(expanded, onDismiss) {
        Item(s.playNext, Icons.Default.PlayArrow, track.streamable) { vm.playNext(album, track) }
        Item(s.addToQueue, Icons.AutoMirrored.Filled.QueueMusic, track.streamable) { vm.addToQueue(album, track) }
        Item(s.goToAlbum, Icons.Default.Album) { vm.openAlbum(album.id) }
        Item(s.goToArtist, Icons.Default.Person, track.artist.isNotBlank()) { vm.openArtist(track.artist) }
        Item(s.addToPlaylist, Icons.AutoMirrored.Filled.PlaylistAdd) { dialog = TrackDialog.Playlists; vm.loadLibrary() }
        if (done && (vm.ipodPlugged || vm.ipodProgress != null)) Item(s.sendToIpod, Icons.Default.Smartphone) { task?.let { vm.sendToIpod(it) } }
        if (done && task?.savedUri != null) Item(s.shareTrack, Icons.Default.Share) { shareToDrive(context, listOf(task.savedUri!!), track.displayTitle, chooser = true) }
        Item(s.copyLink, Icons.Default.ContentCopy) {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Qobuz", "https://open.qobuz.com/track/${track.id}"))
            vm.notice = s.linkCopied
        }
        Item(s.trackDetails, Icons.Default.Info) { dialog = TrackDialog.Details }
        Item(if (done) s.downloadAgain else s.download, Icons.Default.Download, track.streamable && !open) { vm.requestDownload(DownloadRequest.Tracks(listOf(album to track))) }
        if (done) Item(s.removeDownload, Icons.Default.Delete) { dialog = TrackDialog.Remove }
    }

    when (dialog) {
        TrackDialog.None -> {}
        TrackDialog.Details -> AlertDialog(
            onDismissRequest = { dialog = TrackDialog.None },
            confirmButton = { TextButton({ dialog = TrackDialog.None }) { Text(s.close) } },
            title = { Text(track.displayTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    fun line(k: String, v: String) = if (v.isNotBlank()) listOf(k to v) else emptyList()
                    (line(s.detailArtist, track.artist) + line(s.detailAlbum, album.title) + line(s.detailYear, album.year.take(4)) +
                        line(s.detailQuality, track.quality) + line(s.detailDuration, "%d:%02d".format(track.durationSec / 60, track.durationSec % 60)) +
                        line("ISRC", track.isrc) + line(s.detailFile, if (done) s.downloaded else s.notDownloaded)).forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(k, Modifier.weight(.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, Modifier.weight(.65f))
                        }
                    }
                }
            },
        )
        TrackDialog.Playlists -> AlertDialog(
            onDismissRequest = { dialog = TrackDialog.None },
            confirmButton = { TextButton({ dialog = TrackDialog.None }) { Text(s.cancel) } },
            title = { Text(s.addToPlaylist) },
            text = {
                val lists = vm.myPlaylists.orEmpty()
                if (lists.isEmpty()) Text(s.noPlaylists)
                else LazyColumn {
                    items(lists, key = { it.id }) { pl ->
                        ListItem(headlineContent = { Text(pl.name) }, modifier = Modifier.clickable { dialog = TrackDialog.None; vm.addTrackToPlaylist(pl, track) })
                    }
                }
            },
        )
        TrackDialog.Remove -> AlertDialog(
            onDismissRequest = { dialog = TrackDialog.None },
            title = { Text(s.removeDownload) }, text = { Text(s.removeDownloadAsk) },
            confirmButton = { TextButton({ dialog = TrackDialog.None; task?.let { vm.removeDownload(it) } }) { Text(s.removeDownload) } },
            dismissButton = { TextButton({ dialog = TrackDialog.None }) { Text(s.cancel) } },
        )
    }
}
