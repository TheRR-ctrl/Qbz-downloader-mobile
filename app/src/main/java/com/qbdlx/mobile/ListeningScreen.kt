package com.qbdlx.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** "Sonando": the song the Qobuz app is playing, identified, with a round button in the middle that downloads it when pressed. */
@Composable
fun ListeningScreen(vm: MainViewModel) {
    val s = LocalStrings.current
    BackHandler { vm.closeImport() }
    LaunchedEffect(Unit) { while (true) { vm.refreshListening(); delay(2000) } }
    val song = vm.listenSong
    val pick = vm.listenMatch?.takeIf { it.level != MatchLevel.Missing }?.candidates?.firstOrNull()

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp).padding(bottom = LocalBottomInset.current), horizontalAlignment = Alignment.CenterHorizontally) {
        Title(s.nowButton)
        Spacer(Modifier.weight(1f))
        when {
            !vm.listenAccess -> {
                Text(s.nowAccess, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button({ vm.askListenAccess() }) { Text(s.nowAllow) }
            }
            song == null -> Text(s.nowNothing, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                Cover(pick?.first?.coverUrl, 240)
                Spacer(Modifier.height(20.dp))
                Text(pick?.second?.displayTitle ?: song.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(listOf(pick?.second?.artist ?: song.artist, pick?.first?.title ?: song.album).filter { it.isNotBlank() }.joinToString(" • "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                pick?.let { Spacer(Modifier.height(8.dp)); if (it.second.quality.isNotBlank()) QualityBadge(it.second.quality, it.second.hiRes) }
                Spacer(Modifier.height(32.dp))
                if (vm.listenSearching) {
                    CircularProgressIndicator(Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(s.nowIdentifying, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (pick == null) {
                    Text(s.nowNotFound(song.title), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FilledIconButton({ vm.downloadListening() }, Modifier.size(88.dp), shape = CircleShape) {
                        androidx.compose.material3.Icon(Icons.Default.Download, s.download, Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(s.nowTapToDownload, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.weight(1.4f))
    }
}
