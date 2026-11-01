package com.qbdlx.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Send finished MP3 downloads to an iPod nano 6G/7G and browse what is on it (experimental; see ipod/IpodDevice.kt). */
@Composable
fun IpodScreen(vm: MainViewModel) {
    val s = LocalStrings.current
    BackHandler { vm.closeIpod() }
    val insp = vm.ipodInspection
    val ready = vm.ipodUri != null && insp?.ready == true
    val pending = vm.ipodPending
    val shown = vm.ipodShown
    val selected = vm.ipodSelected
    val candidates = vm.ipodCandidates
    val busy = vm.ipodProgress != null
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    if (vm.confirmIpodDeleteSelected) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.confirmIpodDeleteSelected = false },
            title = { Text(s.ipodDeleteSelectedTitle) },
            text = { Text(s.ipodDeleteSelectedWarning(vm.ipodSelectedPids.size) + s.ipodDeleteListsNote(vm.ipodSelectedPlaylists.size)) },
            confirmButton = { TextButton({ vm.ipodDeleteSelected() }) { Text(s.importDeleteConfirm, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ vm.confirmIpodDeleteSelected = false }) { Text(s.cancel) } },
        )
    }
    if (vm.ipodPlaylistPicker) {
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadLibrary() }
        var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.ipodPlaylistPicker = false },
            title = { Text(s.ipodPlaylistTitle) },
            text = {
                val pls = vm.myPlaylists
                Column {
                Text(s.ipodPlaylistLegend, fontSize = 12.sp, color = muted)
                when {
                    pls == null -> Text("…")
                    pls.isEmpty() -> Text(s.ipodPlaylistNone)
                    else -> LazyColumn { items(pls.size) { i ->
                        val p = pls[i]
                        val on = p.id in picked
                        Row(Modifier.fillMaxWidth().clickable { picked = if (on) picked - p.id else picked + p.id }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(on, { picked = if (on) picked - p.id else picked + p.id })
                            val onDevice = vm.ipodPlaylists.any { it.name.trim().equals(p.name.trim(), ignoreCase = true) && it.trackPids.isNotEmpty() }
                            androidx.compose.foundation.layout.Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (onDevice) androidx.compose.ui.graphics.Color(0xFF2E7D32) else androidx.compose.ui.graphics.Color(0xFFC62828)))
                            Text("${p.name} · ${p.tracksCount}", Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                        }
                    } }
                }
                }
            },
            confirmButton = { TextButton({ vm.ipodSendPlaylists((vm.myPlaylists ?: emptyList()).filter { it.id in picked }) }, enabled = picked.isNotEmpty()) { Text(s.ipodPlaylistSendN(picked.size)) } },
            dismissButton = { TextButton({ vm.ipodPlaylistPicker = false }) { Text(s.cancel) } },
        )
    }
    if (vm.confirmIpodClear) {
        var typed by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.confirmIpodClear = false },
            title = { Text(s.ipodClearTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.ipodClearWarning(vm.ipodTracks.size))
                    androidx.compose.material3.OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text(s.ipodClearType) })
                }
            },
            confirmButton = { TextButton({ vm.ipodClearAll() }, enabled = typed.trim().uppercase() == "BORRAR") { Text(s.importDeleteConfirm) } },
            dismissButton = { TextButton({ vm.confirmIpodClear = false }) { Text(s.cancel) } },
        )
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
    androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth()) {
    // Tab switch: content fades in (ease-out, 180 ms) from a visible start, never from nothing.
    val tabFade = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(vm.ipodTab) {
        tabFade.snapTo(0.4f)
        tabFade.animateTo(1f, androidx.compose.animation.core.tween(180, easing = androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
    }
    LazyColumn(
        Modifier.fillMaxSize().graphicsLayer { alpha = tabFade.value },
        state = listState,
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp + LocalBottomInset.current),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ vm.closeIpod() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back) }
                Text(s.ipodTitle, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (vm.ipodChecking) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                OutlinedButton({ vm.chooseIpod() }, enabled = !busy) { Text(if (vm.ipodUri == null) s.ipodChoose else s.ipodChange) }
            }
        }
        if (vm.ipodJobLeft > 0 && !busy) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.ipodResumeBanner(vm.ipodJobLeft), fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ vm.ipodResume() }) { Text(s.ipodResumeButton) }
                        OutlinedButton({ vm.ipodDiscardJob() }) { Text(s.ipodDiscard) }
                    }
                }
            }
        }
        if (vm.ipodUri == null) item { Text(s.ipodHelp, fontSize = 13.sp, color = muted) }
        item {
            when {
                vm.ipodUri == null -> Text(s.ipodNone)
                insp == null -> Unit
                !insp.ready -> Text(s.ipodProblem(insp.problem!!, insp.detail), color = MaterialTheme.colorScheme.error)
                else -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(s.ipodSummary(vm.ipodSongCount.coerceAtLeast(0), pending.size, candidates.size - pending.size), fontWeight = FontWeight.Medium)
                        vm.ipodSpace?.let { (free, total) ->
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            val fmt = { b: Long -> android.text.format.Formatter.formatFileSize(ctx, b) }
                            if (total > 0) {
                                androidx.compose.material3.LinearProgressIndicator(progress = { (1f - free.toFloat() / total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                                Text(s.ipodSpace(fmt(free), fmt(total)), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        if (vm.ipodUri != null) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.ipodRestore() }, Modifier.weight(1f), enabled = !busy) { Text(s.ipodRestore, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                if (ready) OutlinedButton({ vm.ipodShareReport() }, Modifier.weight(1f)) { Text(s.ipodReport, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
        // the progress bar shows in both tabs
        vm.ipodProgress?.let { (done, total) ->
            item {
                Column(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(progress = { if (total > 0) done.toFloat() / total else 0f }, Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (vm.ipodStopping) s.stoppingNow else s.ipodSending(done, total), fontSize = 12.sp, modifier = Modifier.weight(1f).padding(top = 4.dp))
                        TextButton({ vm.ipodStop() }, enabled = !vm.ipodStopping) { Text(s.stopButton) }
                    }
                }
            }
        }
        // progress of every iPod job (lyrics, fix covers, delete): always visible at the top, never only under a button
        if (vm.ipodLyricsStatus != null || vm.ipodTask != null) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val lp = vm.ipodLyricsProgress
                    val label = vm.ipodTask ?: vm.ipodLyricsStatus.orEmpty()
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (vm.ipodTask == null && lp != null && lp.second > 0) {
                        LinearProgressIndicator(progress = { lp.first.toFloat() / lp.second }, Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (vm.ipodStopping) s.stoppingNow else "${lp.first * 100 / lp.second}%", fontSize = 12.sp, color = muted, modifier = Modifier.weight(1f))
                            TextButton({ vm.ipodStop() }, enabled = !vm.ipodStopping) { Text(s.stopButton) }
                        }
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(s.ipodTaskIndeterminateHint, fontSize = 12.sp, color = muted)
                    }
                }
            }
        }
        if (ready) {
            item {
                TabRow(vm.ipodTab) {
                    Tab(vm.ipodTab == 0, { vm.ipodTab = 0 }, text = { Text("${s.ipodTabSend} (${pending.size})") })
                    Tab(vm.ipodTab == 1, { vm.ipodTab = 1 }, text = { Text("${s.ipodTabOn} (${vm.ipodTracks.size})") })
                    Tab(vm.ipodTab == 2, { vm.ipodTab = 2 }, text = { Text("${s.ipodTabPlaylists} (${vm.ipodPlaylists.size})") })
                }
            }
        }
        if (ready && vm.ipodTab == 1) {
            item {
                OutlinedButton({ vm.confirmIpodClear = true }, Modifier.fillMaxWidth(), enabled = vm.ipodTracks.isNotEmpty() && !busy && !vm.ipodClearing) {
                    if (vm.ipodClearing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text(s.importWorking)
                    } else Text(s.ipodClearButton(vm.ipodTracks.size), color = MaterialTheme.colorScheme.error)
                }
            }
            item { Button({ vm.importFromIpod() }, Modifier.fillMaxWidth(), enabled = vm.ipodTracks.isNotEmpty()) { Text(s.ipodLookup) } }
            item { OutlinedButton({ vm.ipodFixAlbumCovers() }, Modifier.fillMaxWidth(), enabled = vm.ipodTracks.isNotEmpty() && !busy && vm.ipodTask == null && vm.ipodLyricsStatus == null) { Text(s.ipodFixAlbumsButton) } }
            item {
                val shown = vm.ipodTracks.filter { vm.ipodQuery.trim().isEmpty() || "${it.title} ${it.artist} ${it.album}".lowercase().contains(vm.ipodQuery.trim().lowercase()) }
                OutlinedButton({ vm.ipodAddLyricsToDevice(shown) }, Modifier.fillMaxWidth(), enabled = shown.any { !it.hasLyrics } && !busy && vm.ipodLyricsStatus == null && vm.ipodTask == null) {
                    Text(s.ipodAddLyricsButton(shown.count { !it.hasLyrics }))
                }
            }
            item {
                OutlinedTextField(
                    vm.ipodQuery, { vm.ipodQuery = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(s.ipodSearch) },
                )
            }
            val q = vm.ipodQuery.trim().lowercase()
            val onIpod = if (q.isEmpty()) vm.ipodSortedTracks else vm.ipodSortedTracks.filter { "${it.title} ${it.artist} ${it.album}".lowercase().contains(q) }
            item {
                Text("${s.ipodTabOn} · ${s.ipodSearch}", fontSize = 12.sp, color = muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(s.ipodSelectedCount(vm.ipodSelectedPids.size), fontSize = 12.sp, color = muted)
                    TextButton({ vm.ipodSelectVisible(onIpod.map { it.pid }, vm.ipodSelectedPids.size < onIpod.size) }, enabled = onIpod.isNotEmpty()) { Text(s.ipodSelectAllVisible) }
                }
                OutlinedButton({ vm.confirmIpodDeleteSelected = true }, Modifier.fillMaxWidth(), enabled = vm.ipodSelectedPids.isNotEmpty() && !busy && !vm.ipodClearing) {
                    if (vm.ipodClearing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("${s.ipodDeleteSelected} (${vm.ipodSelectedPids.size})", color = MaterialTheme.colorScheme.error)
                }
            }
            val libMode = vm.ipodLibMode && q.isEmpty()
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(!vm.ipodLibMode, { vm.ipodLibMode = false }, label = { Text(s.ipodViewList) })
                    androidx.compose.material3.FilterChip(vm.ipodLibMode, { vm.ipodLibMode = true; vm.ipodRebuildLibrary(); vm.ipodLibArtist = null; vm.ipodLibAlbum = null }, label = { Text(s.ipodViewLibrary) })
                }
            }
            if (libMode) {
                val artist = vm.ipodLibArtist; val album = vm.ipodLibAlbum
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (artist != null) TextButton({ if (album != null) vm.ipodLibAlbum = null else vm.ipodLibArtist = null }) { Text("← " + (if (album != null) artist.ifBlank { "—" } else s.ipodViewArtists)) }
                        Text(listOfNotNull(artist?.ifBlank { "—" }, album?.ifBlank { "—" }).joinToString(" › ").ifEmpty { s.ipodViewArtists }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val selecting = vm.ipodSelectedPids.isNotEmpty()
                data class Cell(val key: String, val title: String, val sub: String, val cover: String?, val pids: List<Long>, val open: () -> Unit)
                val cells: List<Cell> = if (artist == null) {
                    vm.ipodLibrary.map { a -> Cell("la${a.name}", a.name.ifBlank { "—" }, s.ipodLibArtistCount(a.albums.size, a.songCount), a.cover, a.pids) { vm.ipodLibArtist = a.name } }
                } else {
                    (vm.ipodLibrary.firstOrNull { it.name == artist }?.albums ?: emptyList()).map { al ->
                        Cell("lb$artist${al.name}", al.name.ifBlank { "—" }, s.ipodPlaylistSongs(al.tracks.size, 0), al.cover, al.pids) { vm.ipodLibAlbum = al.name }
                    }
                }
                if (album == null) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.FilterChip(!vm.ipodGrid, { vm.ipodGrid = false }, label = { Text(s.ipodViewRows) })
                            androidx.compose.material3.FilterChip(vm.ipodGrid, { vm.ipodGrid = true }, label = { Text(s.ipodViewGrid) })
                        }
                    }
                    val onTap = { c: Cell -> if (selecting) vm.ipodSelectVisible(c.pids, !c.pids.all { it in vm.ipodSelectedPids }) else c.open() }
                    if (vm.ipodGrid) {
                        cells.chunked(3).forEach { rowCells ->
                            item(key = "g" + rowCells.first().key) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowCells.forEach { c -> Box(Modifier.weight(1f)) { LibCell(c.title, c.sub, c.cover, c.pids.all { it in vm.ipodSelectedPids }, true, { onTap(c) }) { vm.ipodSelectVisible(c.pids, !c.pids.all { it in vm.ipodSelectedPids }) } } }
                                    repeat(3 - rowCells.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    } else cells.forEach { c ->
                        item(key = c.key) { LibCell(c.title, c.sub, c.cover, c.pids.all { it in vm.ipodSelectedPids }, false, { onTap(c) }) { vm.ipodSelectVisible(c.pids, !c.pids.all { it in vm.ipodSelectedPids }) } }
                    }
                } else {
                    onIpod.filter { it.artist == artist && it.album == album }.forEach { t ->
                        item(key = "lt${t.pid}") {
                            Row(Modifier.fillMaxWidth().clickable { vm.ipodToggleTrack(t.pid) }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(vm.ipodSelectedPids.contains(t.pid), { vm.ipodToggleTrack(t.pid) })
                                Text((if (t.trackNumber > 0) "${t.trackNumber}.  " else "") + t.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
            var lastGroup = ""
            if (!libMode) onIpod.forEach { t ->
                val group = "${t.artist}\u0000${t.album}"
                if (group != lastGroup) {
                    lastGroup = group
                    item(key = "g$group") {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(t.album.ifBlank { "—" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist.ifBlank { "—" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                item(key = "t${t.pid}") {
                    Row(Modifier.fillMaxWidth().clickable { vm.ipodToggleTrack(t.pid) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(vm.ipodSelectedPids.contains(t.pid), { vm.ipodToggleTrack(t.pid) })
                        Text(
                            (if (t.trackNumber > 0) "${t.trackNumber}.  " else "") + t.title,
                            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                    }
                }
            }
        }
        if (ready && vm.ipodTab == 2) {
            val byPid = vm.ipodTracks.associateBy { it.pid }
            item {
                OutlinedButton({ vm.confirmIpodDeleteSelected = true }, Modifier.fillMaxWidth(), enabled = (vm.ipodSelectedPids.isNotEmpty() || vm.ipodSelectedPlaylists.isNotEmpty()) && !busy && !vm.ipodClearing) {
                    Text("${s.ipodDeleteSelected} (${vm.ipodSelectedPids.size})" + if (vm.ipodSelectedPlaylists.isNotEmpty()) " + ${vm.ipodSelectedPlaylists.size} ✓" else "", color = MaterialTheme.colorScheme.error)
                }
            }
            if (vm.ipodPlaylists.isEmpty()) item { Text(s.ipodPlaylistsEmpty, color = muted) }
            vm.ipodPlaylists.forEach { pl ->
                val songs = pl.trackPids.mapNotNull { byPid[it] }
                val open = vm.ipodOpenPlaylist == pl.pid
                item(key = "pl${pl.pid}") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { vm.ipodOpenPlaylist = if (open) null else pl.pid }.padding(vertical = 8.dp)) {
                            Text(pl.name.ifBlank { "—" }, fontWeight = FontWeight.SemiBold)
                            Text(s.ipodPlaylistSongs(songs.size, pl.trackPids.size - songs.size), fontSize = 12.sp, color = muted)
                            Text(s.ipodDeleteListCheck, fontSize = 11.sp, color = muted)
                        }
                        Checkbox(pl.pid in vm.ipodSelectedPlaylists, { vm.ipodTogglePlaylist(pl) })
                    }
                }
                if (open) {
                    item(key = "pls${pl.pid}") {
                        val all = songs.isNotEmpty() && songs.all { it.pid in vm.ipodSelectedPids }
                        TextButton({ vm.ipodSelectVisible(songs.map { it.pid }, !all) }, enabled = songs.isNotEmpty()) { Text(s.ipodSelectPlaylist) }
                    }
                    songs.forEach { t ->
                        item(key = "pl${pl.pid}t${t.pid}") {
                            Row(Modifier.fillMaxWidth().clickable { vm.ipodToggleTrack(t.pid) }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(vm.ipodSelectedPids.contains(t.pid), { vm.ipodToggleTrack(t.pid) })
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(t.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(t.artist, fontSize = 12.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (ready && vm.ipodTab == 0) {
            item {
                Row(Modifier.fillMaxWidth().clickable { vm.ipodReplace = !vm.ipodReplace }, verticalAlignment = Alignment.CenterVertically) {
                    Switch(vm.ipodReplace, { vm.ipodReplace = it })
                    Text(s.ipodReplaceLabel, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                }
                Row(Modifier.fillMaxWidth().clickable { vm.ipodLyrics = !vm.ipodLyrics }, verticalAlignment = Alignment.CenterVertically) {
                    Switch(vm.ipodLyrics, { vm.ipodLyrics = it })
                    Text(s.ipodLyricsLabel, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                }
                OutlinedButton({ vm.ipodPlaylistPicker = true }, Modifier.fillMaxWidth(), enabled = !busy) { Text(s.ipodPlaylistButton) }
            }
            if (candidates.isEmpty()) item { Text(s.ipodNoMp3, color = muted) }
            else if (pending.isEmpty()) item { Text(s.ipodAlready(candidates.size), color = muted) }
            if (pending.isNotEmpty()) item {
                Button({ vm.ipodSend() }, enabled = selected.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text(s.ipodSendButton(selected.size))
                }
            }
            if (pending.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(vm.ipodArtist == null, { vm.ipodFilterArtist(null) }, { Text(s.ipodArtistAll) }) }
                        items(pending.map { it.track.artist }.filter { it.isNotBlank() }.distinct().sorted()) { a ->
                            FilterChip(vm.ipodArtist == a, { vm.ipodFilterArtist(a) }, { Text(a) })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(listOf(0, 1, 7, 30)) { i, d -> FilterChip(vm.ipodDays == d, { vm.ipodFilterDays(d) }, { Text(s.ipodDateLabels[i]) }) }
                    }
                    if (!vm.ipodSendLib) Text(s.ipodGroupTitle, fontSize = 12.sp, color = muted)
                    if (!vm.ipodSendLib) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(MainViewModel.IpodGroup.values().toList()) { i, g -> FilterChip(vm.ipodGroup == g, { vm.ipodSetGroup(g) }, { Text(s.ipodGroupLabels[i]) }) }
                    }
                    Row {
                        TextButton({ vm.ipodTickShown(true) }) { Text(s.ipodTickAll) }
                        TextButton({ vm.ipodTickShown(false) }) { Text(s.ipodTickNone) }
                    }
                }
            }
            val libView = vm.ipodSendLib
            val sArtist = vm.ipodSendArtist; val sAlbum = vm.ipodSendAlbum
            if (pending.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(libView, { vm.ipodSendLib = true; vm.ipodSendArtist = null; vm.ipodSendAlbum = null }, { Text(s.ipodViewLibrary) })
                        FilterChip(!libView, { vm.ipodSendLib = false }, { Text(s.ipodViewList) })
                        if (libView && sAlbum == null) {
                            Spacer(Modifier.width(8.dp))
                            FilterChip(!vm.ipodGrid, { vm.ipodGrid = false }, { Text(s.ipodViewRows) })
                            FilterChip(vm.ipodGrid, { vm.ipodGrid = true }, { Text(s.ipodViewGrid) })
                        }
                    }
                }
                if (libView) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (sArtist != null) TextButton({ if (sAlbum != null) vm.ipodSendAlbum = null else vm.ipodSendArtist = null }) { Text("← " + (if (sAlbum != null) sArtist.ifBlank { "—" } else s.ipodViewArtists)) }
                        Text(listOfNotNull(sArtist?.ifBlank { "—" }, sAlbum?.ifBlank { "—" }).joinToString(" › ").ifEmpty { s.ipodViewArtists }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (libView && pending.isNotEmpty() && (sArtist == null || sAlbum == null)) {
                data class SCell(val key: String, val title: String, val sub: String, val cover: String?, val songs: List<DownloadTask>, val open: () -> Unit)
                val cells: List<SCell> = if (sArtist == null) {
                    shown.groupBy { it.track.artist }.toList().sortedBy { it.first.lowercase() }.map { (name, ts) ->
                        SCell("sa$name", name.ifBlank { "—" }, s.ipodLibArtistCount(ts.map { it.album.title }.distinct().size, ts.size), ts.firstNotNullOfOrNull { it.album.coverUrl }?.replace("_600", "_230"), ts) { vm.ipodSendArtist = name }
                    }
                } else {
                    shown.filter { it.track.artist == sArtist }.groupBy { it.album.title }.toList().sortedBy { it.first.lowercase() }.map { (name, ts) ->
                        SCell("sb$sArtist$name", name.ifBlank { "—" }, s.ipodPlaylistSongs(ts.size, 0), ts.firstNotNullOfOrNull { it.album.coverUrl }?.replace("_600", "_230"), ts) { vm.ipodSendAlbum = name }
                    }
                }
                val toggleCell = { c: SCell -> vm.ipodTickTasks(c.songs, !c.songs.all { vm.ipodIsTicked(it) }) }
                if (vm.ipodGrid) {
                    cells.chunked(3).forEach { rowCells ->
                        item(key = "g" + rowCells.first().key) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowCells.forEach { c -> Box(Modifier.weight(1f)) { LibCell(c.title, c.sub, c.cover, c.songs.all { vm.ipodIsTicked(it) }, true, { c.open() }) { toggleCell(c) } } }
                                repeat(3 - rowCells.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                } else cells.forEach { c ->
                    item(key = c.key) { LibCell(c.title, c.sub, c.cover, c.songs.all { vm.ipodIsTicked(it) }, false, { c.open() }) { toggleCell(c) } }
                }
            }
            var lastHeading: String? = null
            val listed = if (!libView) shown else if (sArtist != null && sAlbum != null) shown.filter { it.track.artist == sArtist && it.album.title == sAlbum } else emptyList()
            listed.forEach { t ->
                val heading = if (libView) null else vm.ipodGroupOf(t)
                if (heading != null && heading != lastHeading) {
                    lastHeading = heading
                    val groupSongs = shown.filter { vm.ipodGroupOf(it) == heading }
                    val allTicked = groupSongs.all { vm.ipodIsTicked(it) }
                    item(key = "h$heading") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${heading.ifBlank { s.ipodUnknownGroup }} (${groupSongs.size})", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            TextButton({ vm.ipodTickGroup(heading, !allTicked) }) { Text(if (allTicked) s.ipodTickNone else s.ipodTickAll, fontSize = 12.sp) }
                        }
                    }
                }
                item(key = t.key) {
                    Row(Modifier.fillMaxWidth().pressable { vm.ipodToggle(t) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(vm.ipodIsTicked(t), { vm.ipodToggle(t) })
                        coil.compose.AsyncImage(
                            model = t.album.coverUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.padding(end = 10.dp).size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(t.track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text("${t.track.artist} · ${t.album.title}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = muted)
                        }
                    }
                }
            }
        }
        item { Text(s.ipodSafeEject, fontSize = 12.sp, color = muted) }
    }
    ListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
    if (listState.firstVisibleItemIndex > 2) androidx.compose.material3.SmallFloatingActionButton(
        { scope.launch { listState.animateScrollToItem(0) } },
        Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp + LocalBottomInset.current),
    ) { Icon(Icons.Filled.KeyboardArrowUp, s.toTop) }
    }
    }
}

/** A thin draggable scroll bar for a lazy list: shows where you are and lets you drag to anywhere in it. */
@Composable
fun ListScrollbar(state: androidx.compose.foundation.lazy.LazyListState, modifier: Modifier = Modifier) {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    if (total <= 1 || info.visibleItemsInfo.size >= total) return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val color = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxHeight().width(22.dp).pointerInput(total) {
            detectVerticalDragGestures { change, _ ->
                val fraction = (change.position.y / size.height).coerceIn(0f, 1f)
                scope.launch { state.scrollToItem((fraction * (total - 1)).toInt()) }
            }
        },
    ) {
        val visible = info.visibleItemsInfo.size.toFloat()
        val thumb = (maxHeight * (visible / total)).coerceAtLeast(36.dp)
        val travel = maxHeight - thumb
        val offset = travel * (state.firstVisibleItemIndex.toFloat() / (total - visible).coerceAtLeast(1f)).coerceIn(0f, 1f)
        androidx.compose.foundation.layout.Box(
            Modifier.align(Alignment.TopEnd).padding(end = 3.dp).offset(y = offset).width(5.dp).height(thumb)
                .background(color.copy(alpha = 0.7f), RoundedCornerShape(3.dp)),
        )
    }
}

/** A cell of the iPod library view (artist or album): cover, name and a small line; tap opens (or toggles while selecting), long press selects. */
/** Tap target with the same press feedback as the library cells: scale 0.97, 160 ms, strong ease-out. */
@Composable
private fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val k by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.97f else 1f, androidx.compose.animation.core.tween(160, easing = androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f)), label = "press")
    return this.graphicsLayer { scaleX = k; scaleY = k }.clickable(interactionSource = source, indication = null, onClick = onClick)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LibCell(title: String, sub: String, cover: String?, selected: Boolean, grid: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // Emil Kowalski: press feedback = scale 0.97, ease-out, <200ms; state changes fade, never snap.
    val ease = androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.97f else 1f, androidx.compose.animation.core.tween(160, easing = ease), label = "press")
    val bg by androidx.compose.animation.animateColorAsState(if (selected) cs.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, androidx.compose.animation.core.tween(150, easing = ease), label = "bg")
    val badge by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f, androidx.compose.animation.core.tween(180, easing = ease), label = "badge")
    val art: @Composable (Modifier) -> Unit = { m ->
        Box(m.clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
            if (cover != null) coil.compose.AsyncImage(cover, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.Low)
            else Text("♪", fontSize = 22.sp, color = cs.onSurfaceVariant)
            if (badge > 0f) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp)
                .graphicsLayer { val k = 0.8f + 0.2f * badge; scaleX = k; scaleY = k; alpha = badge }
                .clip(androidx.compose.foundation.shape.CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                Text("✓", color = cs.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    val base = Modifier.fillMaxWidth().graphicsLayer { scaleX = press; scaleY = press }.clip(RoundedCornerShape(12.dp)).background(bg)
        .combinedClickable(interactionSource = source, indication = null, onClick = onClick, onLongClick = onLong)
    if (grid) Column(base.padding(4.dp)) {
        art(Modifier.fillMaxWidth().aspectRatio(1f))
        Text(title, Modifier.padding(top = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(sub, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else Row(base.padding(vertical = 6.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        art(Modifier.size(52.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
