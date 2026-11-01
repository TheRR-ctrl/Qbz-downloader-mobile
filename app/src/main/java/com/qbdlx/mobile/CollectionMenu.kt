package com.qbdlx.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** The ⋮ menu of an album (pass [album]) or a playlist (pass [playlist]): play, queue, artist, download, iPod, link, details. */
@Composable
fun CollectionMenu(vm: MainViewModel, album: Album?, playlist: Playlist?, expanded: Boolean, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var details by remember { mutableStateOf(false) }
    val link = if (album != null) "https://open.qobuz.com/album/${album.id}" else "https://open.qobuz.com/playlist/${playlist?.id}"

    @Composable
    fun Item(label: String, icon: ImageVector, action: () -> Unit) =
        DropdownMenuItem({ Text(label) }, { onDismiss(); action() }, leadingIcon = { Icon(icon, null) })

    DropdownMenu(expanded, onDismiss) {
        Item(s.playAll, Icons.Default.PlayArrow) { vm.playCollection(album, playlist, 0) }
        Item(s.playNext, Icons.Default.SkipNext) { vm.playCollection(album, playlist, 1) }
        Item(s.addToQueue, Icons.AutoMirrored.Filled.QueueMusic) { vm.playCollection(album, playlist, 2) }
        if (album != null && album.artist.isNotBlank()) Item(s.goToArtist, Icons.Default.Person) { vm.openArtist(album.artist) }
        Item(s.download, Icons.Default.Download) {
            vm.requestDownload(if (album != null) DownloadRequest.WholeAlbum(album) else DownloadRequest.WholePlaylist(playlist!!))
        }
        if (album != null) Item(s.sendToIpod, Icons.Default.Smartphone) { vm.sendAlbumToIpod(album) }
        Item(s.shareTrack, Icons.Default.Share) {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), null))
        }
        Item(s.copyLink, Icons.Default.ContentCopy) {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Qobuz", link))
            vm.notice = s.linkCopied
        }
        Item(s.details, Icons.Default.Info) { details = true }
    }

    if (details) AlertDialog(
        onDismissRequest = { details = false },
        confirmButton = { TextButton({ details = false }) { Text(s.close) } },
        title = { Text(album?.title ?: playlist?.name.orEmpty()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val lines = if (album != null) listOf(s.detailArtist to album.artist, s.detailYear to album.year.take(4), s.detailTracks to album.tracksCount.takeIf { it > 0 }?.toString().orEmpty(), s.detailQuality to album.quality)
                else listOf(s.detailOwner to playlist!!.owner, s.detailTracks to playlist.tracksCount.takeIf { it > 0 }?.toString().orEmpty())
                lines.filter { it.second.isNotBlank() }.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(k, Modifier.weight(.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, Modifier.weight(.65f))
                    }
                }
            }
        },
    )
}
