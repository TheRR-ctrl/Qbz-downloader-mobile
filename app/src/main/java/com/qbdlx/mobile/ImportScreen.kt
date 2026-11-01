package com.qbdlx.mobile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Songs from a QBDLX Scanner list, searched on Qobuz: tick the right ones and download them. */
@Composable
fun ImportScreen(vm: MainViewModel) {
    val s = LocalStrings.current
    BackHandler { vm.closeImport() }
            if (vm.confirmIpodDelete) androidx.compose.material3.AlertDialog(
                onDismissRequest = { vm.confirmIpodDelete = false },
                title = { Text(s.importDeleteTitle) },
                text = { Text(s.importDeleteWarning(vm.importIpodCopies.size)) },
                confirmButton = { TextButton({ vm.importDeleteFromIpod() }) { Text(s.importDeleteConfirm) } },
                dismissButton = { TextButton({ vm.confirmIpodDelete = false }) { Text(s.cancel) } },
            )
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importList(uri) }
    val items = vm.importItems
    val progress = vm.importProgress

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp + LocalBottomInset.current),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ vm.closeImport() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back) }
                Text(s.importTitle, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Text(s.importHelp, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.padding(4.dp))
            OutlinedButton({ picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth()) { Text(s.importPick) }
            TextButton({ vm.importForgetAll() }) { Text(s.importForget) }
        }
        if (progress != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator({ progress.first / progress.second.coerceAtLeast(1).toFloat() }, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.importSearching(progress.first, progress.second), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton({ vm.importStop() }) { Text(s.stopButton) }
                }
            }
        }
        if (items != null) {
            val ticked = vm.importTicked.sumOf { i -> items.getOrNull(i)?.picks?.size ?: 0 }
            item {
                Button({ vm.importDownload() }, Modifier.fillMaxWidth(), enabled = ticked > 0) { Text(s.importDownload(ticked)) }
                if (vm.importIsIpod) Button({ vm.importPlaylistThenReplace() }, Modifier.fillMaxWidth(), enabled = ticked > 0 && vm.importBusy == null) { BusyText(vm.importBusy == "plreplace", s.importPlaylistReplace(ticked), s.importWorking) }
                if (vm.importIsIpod) OutlinedButton({ vm.importReplaceOnIpod() }, Modifier.fillMaxWidth(), enabled = ticked > 0 && vm.importBusy == null) { BusyText(vm.importBusy == "replace", s.importReplaceOnIpod(ticked), s.importWorking) }
                if (vm.importIsIpod) OutlinedButton({ vm.confirmIpodDelete = true }, Modifier.fillMaxWidth(), enabled = ticked > 0 && vm.importBusy == null) {
                    BusyText(vm.importBusy == "delete", s.importDeleteFromIpod(vm.importIpodCopies.size), s.importWorking)
                }
                OutlinedButton({ vm.importToPlaylist() }, Modifier.fillMaxWidth(), enabled = ticked > 0 && vm.importBusy == null) { BusyText(vm.importBusy == "playlist", s.importToPlaylist(ticked), s.importWorking) }
                OutlinedButton({ vm.importToFavorites() }, Modifier.fillMaxWidth(), enabled = ticked > 0 && vm.importBusy == null) { BusyText(vm.importBusy == "fav", s.importFavorites(ticked), s.importWorking) }
            }
            for (level in listOf(MatchLevel.Sure, MatchLevel.Check, MatchLevel.Missing)) {
                val group = items.withIndex().filter { it.value.level == level }
                if (group.isEmpty()) continue
                val folded = level in vm.importCollapsed
                item(key = "h$level") {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable { vm.importToggleSection(level) }.padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${when (level) { MatchLevel.Sure -> s.importSure; MatchLevel.Check -> s.importCheck; MatchLevel.Missing -> s.importMissing }} (${group.size})",
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                            )
                            Icon(if (folded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp, null)
                        }
                        if (level == MatchLevel.Check && !folded) OutlinedButton({ vm.importChooseBest() }, Modifier.fillMaxWidth()) { Text(s.importPickBest) }
                        if (level == MatchLevel.Missing && !folded) OutlinedButton({ vm.importRetryMissing() }, Modifier.fillMaxWidth(), enabled = vm.importProgress == null) { Text(s.importRetry) }
                    }
                }
                if (folded) continue
                for ((i, it) in group) item(key = "i$i") { ImportRow(vm, i, it) }
            }
        }
    }
}

@Composable
private fun ImportRow(vm: MainViewModel, index: Int, item: ImportItem) {
    val wanted = listOf(item.song.title, item.song.artist).filter { it.isNotBlank() }.joinToString(" — ")
    when (item.level) {
        MatchLevel.Sure -> {
            val (album, track) = item.candidates[item.chosen ?: 0]
            Row(Modifier.fillMaxWidth().clickable { vm.importToggle(index) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(index in vm.importTicked, { vm.importToggle(index) })
                Cover(album.coverUrl, 44)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.displayTitle, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${track.artist} · ${album.title}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        MatchLevel.Check, MatchLevel.Missing -> Column {
            Text(wanted, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.candidates.forEachIndexed { c, (album, track) ->
                Row(Modifier.fillMaxWidth().clickable { vm.importChoose(index, c) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox((item.chosen == c || c in item.also) && index in vm.importTicked, { vm.importChoose(index, c) })
                    Column(Modifier.weight(1f)) {
                        Text(track.displayTitle, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${track.artist} · ${album.title} · ${track.durationSec / 60}:${"%02d".format(track.durationSec % 60)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            var editing by remember { mutableStateOf(false) }
            TextButton({ editing = !editing }) { Text(vm.strings.importFix) }
            if (editing) {
                var artist by remember { mutableStateOf(item.song.artist) }
                var title by remember { mutableStateOf(item.song.title) }
                OutlinedTextField(artist, { artist = it }, Modifier.fillMaxWidth(), label = { Text(vm.strings.importArtistField) }, singleLine = true)
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(vm.strings.importTitleField) }, singleLine = true)
                Button({ vm.importResearch(index, artist, title); editing = false }, Modifier.fillMaxWidth()) { Text(vm.strings.importSearchAgain) }
            }
        }
    }
}

/** The button label, or a small spinner with [working] while the task it started runs. */
@androidx.compose.runtime.Composable
private fun BusyText(busy: Boolean, text: String, working: String) {
    if (busy) {
        androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(working)
    } else Text(text)
}
