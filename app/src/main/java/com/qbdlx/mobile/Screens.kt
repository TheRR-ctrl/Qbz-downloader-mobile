package com.qbdlx.mobile

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private fun mmss(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

@Composable
fun Title(text: String) =
    Text(text, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp))

/** A page title with the search button at its right, where the search tab used to be. */
@Composable
private fun TitleWithSearch(text: String, vm: MainViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Title(text) }
        IconButton({ vm.openSearch() }) { Icon(Icons.Default.Search, LocalStrings.current.search) }
    }
}

@Composable
fun LoginScreen(vm: MainViewModel) {
    val s = LocalStrings.current

    val activity = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(vm.busy) {
        if (!vm.busy) {
            LoginWebViewPool.prewarm(activity)
            vm.prefetchCredentials()
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("QBDLX Mobile", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Button({ vm.startWebLogin() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(s.logInWithQobuz) }
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        // Settings is only reachable once logged in, so offer the language here too.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            listOf(Language.ENGLISH, Language.SPANISH).forEach { l ->
                TextButton({ vm.updateLanguage(l) }) {
                    Text(s.languageName(l), fontWeight = if (vm.strings === l.resolve()) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))

@Composable
private fun AlbumRow(vm: MainViewModel, section: HomeSection) {
    SectionTitle(LocalStrings.current.section(section.key))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(section.albums, key = { it.id }) { album ->
            AlbumCard(album, onClick = { vm.openAlbum(album.id) }, onDownload = { vm.requestDownload(DownloadRequest.WholeAlbum(album)) })
        }
    }
}

/** Mirrors the web player's Discover page: new releases, Qobuz playlists by tag, then the editorial rows. */
@Composable
fun HomeScreen(vm: MainViewModel) {
    val d = vm.discover
    val s = LocalStrings.current
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp + LocalBottomInset.current)) {
        item { TitleWithSearch(s.discover, vm) }
        if (vm.homeLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
        if (d == null) return@LazyColumn
        d.newReleases?.let { item(key = "new") { AlbumRow(vm, it) } }
        if (d.playlists.isNotEmpty()) item(key = "playlists") {
            SectionTitle(s.qobuzPlaylists)
            if (d.playlistTags.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { QChip(vm.selectedTag == null, { vm.selectTag(null) }, label = { Text(s.all) }) }
                    items(d.playlistTags, key = { it.slug }) { tag ->
                        QChip(vm.selectedTag == tag, { vm.selectTag(tag) }, label = { Text(tag.name) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            val shown = if (vm.selectedTag == null) d.playlists else vm.tagPlaylists
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(shown, key = { it.id }) { pl ->
                    PlaylistCard(pl, onClick = { vm.openPlaylist(pl.id) }, onDownload = { vm.requestDownload(DownloadRequest.WholePlaylist(pl)) })
                }
            }
        }
        items(d.sections, key = { it.key }) { AlbumRow(vm, it) }
    }
}

@Composable
fun SearchScreen(vm: MainViewModel) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Title(s.search)
        OutlinedTextField(
            vm.searchQuery, { vm.searchQuery = it }, singleLine = true, label = { Text(s.searchHint) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(vm.searchQuery) }),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QChip(vm.searchTab == 0, { vm.searchTab = 0 }, label = { Text(s.searchAlbumsTab(vm.results.size)) })
            QChip(vm.searchTab == 1, { vm.searchTab = 1 }, label = { Text(s.searchSongsTab(vm.trackResults.size)) })
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        if (vm.searchTab == 1) LazyColumn(contentPadding = PaddingValues(0.dp, 8.dp, 0.dp, 16.dp + LocalBottomInset.current)) {
            itemsIndexed(vm.trackResults) { i, (album, track) ->
                TrackRow(vm, album, track, "${track.artist} • ${album.title}", showCover = true, onPlay = { vm.playList(vm.trackResults, i) })
            }
        } else LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 16.dp + LocalBottomInset.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(vm.results, key = { it.id }) { album ->
                Row(Modifier.fillMaxWidth().clickable { vm.openAlbum(album.id) }, verticalAlignment = Alignment.CenterVertically) {
                    Cover(album.coverUrl, 64)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(album.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(album.artist, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (album.quality.isNotBlank()) QualityBadge(album.quality, album.hiRes) else if (album.hiRes) HiResBadge()
                            Text(album.year, fontSize = 11.sp)
                        }
                    }
                    IconButton({ vm.requestDownload(DownloadRequest.WholeAlbum(album)) }) { Icon(Icons.Default.Download, s.downloadAlbum) }
                }
            }
        }
    }
}

/** "FLAC Hi-Res", "FLAC" or "MP3": the file that was saved (its extension; the requested quality when the name says nothing). */
private fun DownloadTask.fileFormat(): String {
    val ext = savedUri?.let { android.net.Uri.decode(it).substringAfterLast('.', "").lowercase() }.orEmpty()
    val flac = ext == "flac" || (ext !in setOf("mp3", "m4a") && quality != Quality.MP3)
    return when {
        !flac -> ext.uppercase().ifBlank { "MP3" }
        quality == Quality.HI_RES || quality == Quality.HI_RES_MAX -> "FLAC Hi-Res"
        else -> "FLAC"
    }
}

/** One track line, shared by the album and playlist pages. */
@Composable
private fun TrackRow(vm: MainViewModel, album: Album, track: Track, subtitle: String, showCover: Boolean = false, onPlay: () -> Unit = { vm.play(album, track) }) {
    val item = vm.downloads.latestFor(track.id)
    val s = LocalStrings.current
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(enabled = track.streamable, onClick = onPlay).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        // The Library's track list mixes albums, so each row shows its own cover instead of a list number.
        if (showCover) { Cover(album.coverUrl, 56); Spacer(Modifier.width(14.dp)) }
        else Text("${track.number}", Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                color = if (track.streamable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item?.status == TaskStatus.COMPLETED) {
                    Icon(Icons.Default.Check, s.downloaded, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                // Downloaded songs show the format of the file on the device, then the quality Qobuz streams.
                val fmt = if (item?.status == TaskStatus.COMPLETED) item.fileFormat() else null
                // Both: what is on the device, then the best Qobuz offers (so a song saved as MP3 still shows it could be 24/44).
                if (fmt != null) { Spacer(Modifier.width(6.dp)); QualityBadge(fmt, item!!.quality == Quality.HI_RES || item!!.quality == Quality.HI_RES_MAX) }
                if (track.quality.isNotBlank()) { Spacer(Modifier.width(6.dp)); QualityBadge(track.quality, track.hiRes) }
            }
        }
        if (!showCover) Text(mmss(track.durationSec), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, null) }
            TrackMenu(vm, album, track, menu) { menu = false }
        }
    }
}

/** Album/playlist header over its own cover, blurred and faded into the background (as in the repo mockups). */
@Composable
private fun DetailHeader(coverUrl: String?, title: String, lines: List<String>, badge: String?, onBack: () -> Unit, onDownload: () -> Unit, badgeHiRes: Boolean = true, covers: List<String> = emptyList()) {
    val s = LocalStrings.current
    val bg = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth()) {
        AsyncImage(
            coverUrl, null,
            // blur() needs Android 12+; older versions just get the faded cover.
            Modifier.matchParentSize().blur(40.dp).alpha(.55f),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(bg.copy(alpha = .2f), bg))))
        Column(Modifier.statusBarsPadding()) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back) }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (covers.size >= 2) CoverMosaic(covers, 140) else Cover(coverUrl, 140)
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    lines.filter { it.isNotBlank() }.forEach { Text(it, fontSize = 13.sp) }
                    badge?.let { QualityBadge(it, badgeHiRes) }
                    Spacer(Modifier.height(4.dp))
                    Button(onDownload) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(s.download) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun AlbumScreen(vm: MainViewModel) {
    val d = vm.detail ?: return
    BackHandler { vm.closeAlbum() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp + LocalBottomInset.current)) {
        item {
            DetailHeader(
                d.album.coverUrl, d.album.title,
                listOf(d.album.artist, listOf(d.album.year, LocalStrings.current.tracks(d.album.tracksCount)).filter { it.isNotBlank() }.joinToString(" · ")),
                if (d.album.hiRes) d.album.quality.ifBlank { "Hi-Res" } else d.album.quality.ifBlank { null },
                badgeHiRes = d.album.hiRes,
                onBack = { vm.closeAlbum() },
                onDownload = { vm.requestDownload(DownloadRequest.Tracks(d.tracks.filter { it.streamable }.map { d.album to it })) },
            )
        }
        item {
            // Once something of this album is downloaded it can go straight to Drive or the iPod.
            val s = LocalStrings.current
            val context = androidx.compose.ui.platform.LocalContext.current
            val done = vm.downloads.tasks.filter { it.status == TaskStatus.COMPLETED && it.savedUri != null && it.album.id == d.album.id }
            if (done.isNotEmpty()) Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton({
                    val r = shareToDrive(context, done.take(100).mapNotNull { it.savedUri }, s.shareDrive)
                    if (r.missing.isNotEmpty()) vm.downloads.forgetFiles(r.missing)
                    if (r.unreadable.isNotEmpty()) android.widget.Toast.makeText(context, s.shareUnreadable(r.unreadable.size), android.widget.Toast.LENGTH_LONG).show()
                }) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.shareDrive, maxLines = 1, softWrap = false) }
                TextButton({
                    val r = shareToDrive(context, done.take(100).mapNotNull { it.savedUri }, s.shareGeneric, chooser = true)
                    if (r.missing.isNotEmpty()) vm.downloads.forgetFiles(r.missing)
                    if (r.unreadable.isNotEmpty()) android.widget.Toast.makeText(context, s.shareUnreadable(r.unreadable.size), android.widget.Toast.LENGTH_LONG).show()
                }) { Text(s.shareGeneric, maxLines = 1, softWrap = false) }
                TextButton({ vm.ipodSendDownloaded(done) }) { Text(s.ipodSendDownloaded, maxLines = 1, softWrap = false) }
            }
        }
        val albumItems = d.tracks.map { d.album to it }
        itemsIndexed(d.tracks, key = { _, t -> t.id }) { i, track -> TrackRow(vm, d.album, track, track.artist, onPlay = { vm.playList(albumItems, i) }) }
    }
}

@Composable
fun PlaylistScreen(vm: MainViewModel) {
    val d = vm.playlistDetail ?: return
    BackHandler { vm.closePlaylist() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp + LocalBottomInset.current)) {
        item {
            DetailHeader(
                d.playlist.coverUrl, d.playlist.name,
                listOf(d.playlist.owner, LocalStrings.current.tracks(d.tracks.size)), null,
                covers = d.playlist.covers.takeIf { it.size >= 2 } ?: d.tracks.map { it.first.coverUrl }.filterNotNull().distinct().take(4),
                onBack = { vm.closePlaylist() },
                onDownload = { vm.requestDownload(DownloadRequest.Tracks(d.tracks.filter { it.second.streamable })) },
            )
        }
        // A playlist can list the same track twice, so key by position.
        itemsIndexed(d.tracks) { i, (album, track) -> TrackRow(vm, album, track, "${track.artist} • ${album.title}", onPlay = { vm.playList(d.tracks, i) }) }
    }
}

/** An artist's page: their picture, a button to download everything, and their albums (newest first). */
@Composable
fun ArtistScreen(vm: MainViewModel) {
    val a = vm.artistPage ?: return
    BackHandler { vm.closeArtist() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp + LocalBottomInset.current)) {
        item {
            DetailHeader(a.image, a.name, listOf(LocalStrings.current.artistsTab, "${a.albums.size} ${LocalStrings.current.albums.lowercase()}"), null,
                onBack = { vm.closeArtist() }, onDownload = { vm.requestDownload(DownloadRequest.Albums(a.albums)) })
        }
        items(a.albums, key = { "aa" + it.id }) { al ->
            LibraryRow(al.coverUrl, al.title, al.year,
                onClick = { vm.openAlbum(al.id) }, badge = al.quality, hiRes = al.hiRes) { open, close -> CollectionMenu(vm, al, null, open, close) }
        }
    }
}

private enum class LibraryView { Tracks, Albums, Artists, Playlists, History }

/** The user's Library, like the web player's: their playlists, favorite albums and favorite tracks. */
@Composable
fun LibraryScreen(vm: MainViewModel) {
    val s = LocalStrings.current
    var view by remember { mutableStateOf(LibraryView.Tracks) }
    LaunchedEffect(Unit) { vm.loadLibrary() }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp + LocalBottomInset.current)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Title(s.library) }
                IconButton({ vm.openSearch() }) { Icon(Icons.Default.Search, s.search) }
                IconButton({ vm.loadLibrary(force = true) }, enabled = !vm.libraryLoading) { Icon(Icons.Default.Refresh, s.refresh) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryView.entries.forEach { v ->
                    val label = when (v) { LibraryView.Playlists -> s.playlists; LibraryView.Albums -> s.albums; LibraryView.Tracks -> s.tracksTab; LibraryView.Artists -> s.artistsTab; LibraryView.History -> s.history }
                    val n = when (v) { LibraryView.Playlists -> vm.myPlaylists?.size; LibraryView.Albums -> vm.favAlbums?.size; LibraryView.Tracks -> vm.favTracks?.size; LibraryView.Artists -> vm.favArtists?.size; else -> null }
                    QChip(view == v, { view = v }, label = { Text(if (n != null) "$label · $n" else label) })
                }
            }
            if (view != LibraryView.History && view != LibraryView.Artists) {
                var menu by remember { mutableStateOf(false) }
                val labels = listOf(s.sortAdded, s.sortTitle, s.sortArtist, s.sortNewest, s.sortOldest)
                Box(Modifier.padding(horizontal = 8.dp)) {
                    TextButton({ menu = true }) { Text("${s.sortBy}: ${labels[vm.librarySort.coerceIn(0, labels.lastIndex)]}") }
                    androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                        labels.forEachIndexed { i, l -> androidx.compose.material3.DropdownMenuItem({ Text(l) }, { vm.updateLibrarySort(i); menu = false }) }
                    }
                }
            }
            if (vm.libraryLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        }
        when (view) {
            LibraryView.Playlists -> {
                val list = vm.myPlaylists.orEmpty().let { l -> when (vm.librarySort) { 1 -> l.sortedBy { it.name.lowercase() }; 2 -> l.sortedBy { it.owner.lowercase() }; else -> l } }
                if (list.isEmpty() && !vm.libraryLoading) item { EmptyText(s.nothingHere) }
                if (list.isNotEmpty()) item { DownloadAllButton(vm) { vm.requestDownload(DownloadRequest.Playlists(list)) } }
                items(list, key = { "p" + it.id }) { pl ->
                    // The list entry may carry only one cover: ask for the rest of the mosaic once the row is on screen.
                    LaunchedEffect(pl.id) { vm.ensurePlaylistCovers(pl) }
                    LibraryRow(pl.coverUrl, pl.name, listOf(pl.owner, if (pl.tracksCount > 0) s.tracks(pl.tracksCount) else "").filter { it.isNotBlank() }.joinToString(" • "),
                        onClick = { vm.openPlaylist(pl.id) },
                        covers = vm.playlistCovers[pl.id] ?: pl.covers) { open, close -> CollectionMenu(vm, null, pl, open, close) }
                }
            }
            LibraryView.Albums -> {
                val list = vm.favAlbums.orEmpty().let { l -> when (vm.librarySort) { 1 -> l.sortedBy { it.title.lowercase() }; 2 -> l.sortedBy { it.artist.lowercase() }; 3 -> l.sortedByDescending { it.year }; 4 -> l.sortedBy { it.year.ifBlank { "9999" } }; else -> l } }
                if (list.isEmpty() && !vm.libraryLoading) item { EmptyText(s.nothingHere) }
                if (list.isNotEmpty()) item { DownloadAllButton(vm) { vm.requestDownload(DownloadRequest.Albums(list)) } }
                items(list, key = { "a" + it.id }) { al ->
                    LibraryRow(al.coverUrl, al.title, listOf("Álbum", al.artist).filter { it.isNotBlank() }.joinToString(" • "),
                        onClick = { vm.openAlbum(al.id) },
                        badge = al.quality, hiRes = al.hiRes) { open, close -> CollectionMenu(vm, al, null, open, close) }
                }
            }
            LibraryView.Tracks -> {
                val list = vm.favTracks.orEmpty().let { l -> when (vm.librarySort) { 1 -> l.sortedBy { it.second.displayTitle.lowercase() }; 2 -> l.sortedBy { it.second.artist.lowercase() }; 3 -> l.sortedByDescending { it.first.year }; 4 -> l.sortedBy { it.first.year.ifBlank { "9999" } }; else -> l } }
                if (list.isEmpty() && !vm.libraryLoading) item { EmptyText(s.nothingHere) }
                if (list.isNotEmpty()) item { DownloadAllButton(vm) { vm.requestDownload(DownloadRequest.Tracks(list.filter { it.second.streamable })) } }
                itemsIndexed(list) { i, (album, track) -> TrackRow(vm, album, track.copy(number = i + 1), "${track.artist} • ${album.title}", showCover = true, onPlay = { vm.playList(list, i) }) }
            }
            LibraryView.Artists -> {
                // Artists followed in the library first, then those behind favorite albums and tracks.
                class Entry(val name: String, var cover: String?, val albums: MutableSet<String> = mutableSetOf(), var tracks: Int = 0, var followed: Boolean = false)
                val byName = LinkedHashMap<String, Entry>()
                fun entry(name: String, cover: String?) = byName.getOrPut(name.trim().lowercase()) { Entry(name.trim(), cover) }
                vm.favArtists.orEmpty().forEach { f -> entry(f.name, f.cover).also { e -> if (f.cover != null) e.cover = f.cover; e.followed = true } }
                vm.favAlbums.orEmpty().filter { it.artist.isNotBlank() }.forEach { entry(it.artist, it.coverUrl).albums += it.id }
                vm.favTracks.orEmpty().filter { it.second.artist.isNotBlank() }.forEach { (al, tr) -> entry(tr.artist, al.coverUrl).also { e -> e.albums += al.id; e.tracks++ } }
                val artists = byName.values.sortedWith(compareByDescending<Entry> { it.followed }.thenByDescending { it.tracks + it.albums.size }.thenBy { it.name.lowercase() })
                if (artists.isEmpty() && !vm.libraryLoading) item { EmptyText(s.nothingHere) }
                items(artists, key = { "ar" + it.name.lowercase() }) { a ->
                    Row(Modifier.fillMaxWidth().clickable { vm.openArtist(a.name) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(a.cover, 56)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull("${a.albums.size} ${s.albums.lowercase()}", if (a.tracks > 0) s.tracks(a.tracks) else null).joinToString(" · "),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            )
                        }
                    }
                }
            }
            LibraryView.History -> {
                val list = vm.history
                if (list.isEmpty()) item { EmptyText(s.nothingHere) }
                itemsIndexed(list) { i, (album, track) -> TrackRow(vm, album, track.copy(number = i + 1), "${track.artist} • ${album.title}", showCover = true, onPlay = { vm.playList(list, i) }) }
            }
        }
    }
}

/** The "Descargar todo" button at the top of each Library list. */
@Composable
private fun DownloadAllButton(vm: MainViewModel, onClick: () -> Unit) {
    var share by remember { mutableStateOf(false) }
    val hasDone = vm.downloads.tasks.any { it.status == TaskStatus.COMPLETED && it.savedUri != null }
    if (share) ShareDialog(vm, generic = true) { share = false }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick) {
            Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(LocalStrings.current.downloadAll)
        }
        if (hasDone) OutlinedButton({ share = true }) {
            Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(LocalStrings.current.shareGeneric)
        }
    }
}

@Composable
private fun EmptyText(text: String) =
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))

@Composable
private fun LibraryRow(coverUrl: String?, title: String, subtitle: String, onClick: () -> Unit, covers: List<String> = emptyList(), badge: String? = null, hiRes: Boolean = false, menuContent: @Composable (Boolean, () -> Unit) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (covers.size >= 2) CoverMosaic(covers, 64) else Cover(coverUrl, 64)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (!badge.isNullOrBlank()) { Spacer(Modifier.width(6.dp)); QualityBadge(badge, hiRes) }
            }
        }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, null) }
            menuContent(menu) { menu = false }
        }
    }
}

private enum class DlFilter { ACTIVE, DONE, FAILED }

private fun DlFilter.matches(st: TaskStatus) = when (this) {
    DlFilter.ACTIVE -> st.isOpen
    DlFilter.DONE -> st == TaskStatus.COMPLETED
    DlFilter.FAILED -> st == TaskStatus.FAILED || st == TaskStatus.CANCELED
}

@Composable
fun DownloadsScreen(vm: MainViewModel) {
    val s = LocalStrings.current
    val dl = vm.downloads
    var filter by remember { mutableStateOf(DlFilter.ACTIVE) }
    var showShare by remember { mutableStateOf(false) }
    var showShareAny by remember { mutableStateOf(false) }
    if (showShare) ShareDialog(vm) { showShare = false }
    if (showShareAny) ShareDialog(vm, generic = true) { showShareAny = false }
    val items = dl.tasks.filter { filter.matches(it.status) }
    val summary = dl.summary()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Title("QBDLX Mobile")
        if (summary.total > 0) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                LinearProgressIndicator(progress = { summary.overall }, Modifier.fillMaxWidth())
                Text(
                    s.dlSummary(summary.done, summary.total, summary.downloading + summary.pending) + "  ·  ${(summary.overall * 100).toInt()}%",
                    Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QChip(filter == DlFilter.ACTIVE, { filter = DlFilter.ACTIVE }, label = { Text(s.inProgress) })
            QChip(filter == DlFilter.DONE, { filter = DlFilter.DONE }, label = { Text(s.completed) })
            QChip(filter == DlFilter.FAILED, { filter = DlFilter.FAILED }, label = { Text(s.dlTabFailed) })
        }
        // Bulk actions for the list being shown.
        // Four buttons do not fit on a phone: the row scrolls sideways instead of squeezing the last one into a tall pill.
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when (filter) {
                DlFilter.ACTIVE -> {
                    TextButton({ dl.pauseAll() }, enabled = summary.downloading + summary.pending > 0) { Text(s.dlPauseAll) }
                    TextButton({ dl.resumeAll() }, enabled = summary.paused > 0) { Text(s.dlResumeAll) }
                }
                DlFilter.DONE -> {
                    TextButton({ showShare = true }, enabled = items.isNotEmpty()) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.shareDrive)
                    }
                    TextButton({ showShareAny = true }, enabled = items.isNotEmpty()) { Text(s.shareGeneric) }
                    TextButton({ vm.ipodSendDownloaded() }, enabled = items.isNotEmpty()) { Text(s.ipodSendDownloaded) }
                    TextButton({ dl.clearCompleted() }, enabled = items.isNotEmpty()) {
                        Icon(Icons.Default.DeleteSweep, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.dlClearDone, maxLines = 1, softWrap = false)
                    }
                }
                DlFilter.FAILED -> TextButton({ dl.retryFailed() }, enabled = summary.failed > 0) { Text(s.dlRetryFailed) }
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp + LocalBottomInset.current), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { it.key }) { task -> DownloadRow(vm, task) }
            item {
                Text(s.dlDeleteHint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun DownloadRow(vm: MainViewModel, task: DownloadTask) {
    val s = LocalStrings.current
    val dl = vm.downloads
    Row(verticalAlignment = Alignment.CenterVertically) {
        Cover(task.album.coverUrl, 48)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(task.track.displayTitle, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(task.album.artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            when (task.status) {
                TaskStatus.DOWNLOADING -> {
                    LinearProgressIndicator(progress = { task.progress }, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        QualityBadge(task.quality.label, task.quality == Quality.HI_RES || task.quality == Quality.HI_RES_MAX)
                        Text("${(task.progress * 100).toInt()}%", fontSize = 11.sp)
                    }
                }
                TaskStatus.PENDING -> Text(s.queued, fontSize = 11.sp)
                TaskStatus.PAUSED -> Text(s.statusPaused, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TaskStatus.FAILED -> Text(task.error ?: s.statusFailed, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                TaskStatus.CANCELED -> Text(s.statusCanceled, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TaskStatus.COMPLETED -> Text(task.quality.label, fontSize = 11.sp)
            }
            when (task.note) {
                TaskNote.NONE -> Unit
                TaskNote.RETRYING -> Text(s.noteRetrying(task.noteArg, 3), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                TaskNote.REFRESHING -> Text(s.noteRefreshing, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                TaskNote.WAITING_NETWORK -> Text(s.noteWaitingNetwork, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                TaskNote.SYSTEM_LIMIT -> Text(s.noteSystemLimit, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                TaskNote.EXISTED -> Text(s.noteExisted, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                TaskNote.REPLACED -> Text(s.noteReplaced, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }
        when (task.status) {
            TaskStatus.PENDING, TaskStatus.DOWNLOADING -> {
                IconButton({ dl.pause(task) }) { Icon(Icons.Default.Pause, s.dlPause) }
                IconButton({ dl.cancel(task) }) { Icon(Icons.Default.Close, s.dlCancel) }
            }
            TaskStatus.PAUSED -> {
                IconButton({ dl.resume(task) }) { Icon(Icons.Default.PlayArrow, s.dlResume, tint = MaterialTheme.colorScheme.primary) }
                IconButton({ dl.cancel(task) }) { Icon(Icons.Default.Close, s.dlCancel) }
            }
            TaskStatus.FAILED, TaskStatus.CANCELED -> {
                IconButton({ dl.retry(task) }) { Icon(Icons.Default.Refresh, s.retry) }
                IconButton({ dl.delete(task) }) { Icon(Icons.Default.Delete, s.dlDelete) }
            }
            TaskStatus.COMPLETED -> IconButton({ dl.delete(task) }) { Icon(Icons.Default.Delete, s.dlDelete) }
        }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 8.dp + LocalBottomInset.current)) {
        Title(s.settings)

        // ── Descargas ──
        SettingsGroup(s.defaultQuality, s.defaultQualityHelp) {
            Quality.entries.forEach { q ->
                Row(Modifier.fillMaxWidth().clickable { vm.updateQuality(q) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(q == vm.quality && !vm.askQuality, { vm.updateQuality(q) })
                    Text("${q.label} · ${s.qualityDescription(q)}")
                }
            }
            Row(Modifier.fillMaxWidth().clickable { vm.chooseAskAlways() }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(vm.askQuality, { vm.chooseAskAlways() })
                Column {
                    Text(s.askAlways)
                    Text(s.askAlwaysDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SettingsGroup(s.maxConcurrentTitle, s.maxConcurrentHelp) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MAX_CONCURRENT_OPTIONS.forEach { n -> QChip(vm.maxConcurrent == n, { vm.updateMaxConcurrent(n) }, label = { Text("$n") }) }
            }
            ListItem(
                headlineContent = { Text(s.wifiOnlyTitle) }, supportingContent = { Text(s.wifiOnlyDesc) },
                trailingContent = { Switch(vm.wifiOnly, { vm.updateWifiOnly(it) }) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(s.skipExistingTitle) }, supportingContent = { Text(s.skipExistingDesc) },
                trailingContent = { Switch(vm.skipExisting, { vm.updateSkipExisting(it) }) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(s.upgradeExistingTitle) }, supportingContent = { Text(s.upgradeExistingDesc) },
                trailingContent = { Switch(vm.upgradeExisting && vm.skipExisting, { vm.updateUpgradeExisting(it) }, enabled = vm.skipExisting) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        SettingsGroup(s.folderTitle, s.folderHelp) {
            val chosen = vm.customFolder
            Text(
                if (chosen == null) s.folderDefault else s.folderCurrent(chosen.isPrimary, chosen.volume, chosen.path),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontWeight = FontWeight.Medium,
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.chooseFolder() }) { Text(s.folderChoose) }
                if (chosen != null) OutlinedButton({ vm.resetFolder() }) { Text(s.folderResetButton) }
            }
            Text(s.knownTitle, Modifier.padding(horizontal = 16.dp, vertical = 4.dp).padding(top = 8.dp), fontWeight = FontWeight.Medium)
            Text(s.knownHelp, Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            vm.knownName?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 13.sp) }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.chooseKnownFolder() }) { Text(s.knownChoose) }
                if (vm.knownName != null) OutlinedButton({ vm.clearKnownFolder() }) { Text(s.knownRemove) }
            }
        }

        // ── Reproducción ──
        SettingsGroup(s.playbackSection, s.crossfadeDesc) {
            Text(s.crossfadeTitle, Modifier.padding(horizontal = 16.dp, vertical = 2.dp), fontWeight = FontWeight.Medium)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CROSSFADE_OPTIONS_MS.forEach { ms ->
                    QChip(vm.crossfadeMs == ms, { vm.updateCrossfade(ms) }, label = { Text(if (ms == 0) s.crossfadeOff else s.seconds(ms / 1000)) })
                }
            }
            LaunchedEffect(Unit) { vm.refreshBitPerfect() }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.bitPerfectTitle, fontWeight = FontWeight.Medium)
                    Text(s.bitPerfectDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (vm.bitPerfect) Text(s.bitPerfectStatus(vm.bitPerfectState), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                Switch(vm.bitPerfect, { vm.updateBitPerfect(it) })
            }
            var diag by remember { mutableStateOf<String?>(null) }
            val ctx = LocalContext.current
            var usbOn by remember { mutableStateOf(UsbDirect.prefs(ctx).enabled) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.usbDirectTitle, fontWeight = FontWeight.Medium)
                    Text(s.usbDirectDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (usbOn) {
                        val mgr = remember { UsbDirect.managerOrNull(ctx) }
                        if (mgr == null) Text(UsbDirect.failure?.lineSequence()?.firstOrNull().orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        else {
                            val route by mgr.playbackRoute.collectAsState()
                            val devs by mgr.devices.collectAsState()
                            Text(if (devs.isEmpty()) s.usbDirectNoDevice else devs.joinToString { it.displayName } + " · " + route, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Switch(usbOn, { usbOn = it; UsbDirect.prefs(ctx).set(it); if (it) UsbDirect.managerOrNull(ctx)?.triggerActivation(); vm.updateUsbDirect(it) })
            }
            run {
                val dp = ctx.getSharedPreferences("qbdlx", 0)
                var sinRec by remember { mutableStateOf(dp.getBoolean("diag_sin_recuperacion", false)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Diagnóstico: sin recuperación automática", fontSize = 14.sp)
                        Text("Si el DAC se para, no se reactiva ni se recrea la salida: solo se anota el atasco con la línea de tiempo. Úsalo solo para diagnosticar.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(sinRec, { sinRec = it; dp.edit().putBoolean("diag_sin_recuperacion", it).apply(); org.ostrichbat2k.usbdac.streaming.UsbTelemetry.sinRecuperacionAutomatica = it })
                }
            }
            run {
                val dp = ctx.getSharedPreferences("qbdlx", 0)
                var sinFb by remember { mutableStateOf(dp.getBoolean("diag_sin_feedback", true)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Diagnóstico: sin lectura de feedback", fontSize = 14.sp)
                        Text("No abre el hilo que lee el reloj del DAC. Audio y feedback comparten el descriptor USB y en el registro el hilo del feedback recogía bloques de audio. Aplica desde la siguiente pista.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(sinFb, { sinFb = it; dp.edit().putBoolean("diag_sin_feedback", it).apply(); org.ostrichbat2k.usbdac.streaming.UsbTelemetry.sinFeedback = it })
                }
            }
            run {
                val dp = ctx.getSharedPreferences("qbdlx", 0)
                var sinPs by remember { mutableStateOf(dp.getBoolean("diag_sin_pista_silenciosa", false)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Diagnóstico: sin pista silenciosa del sistema", fontSize = 14.sp)
                        Text("No crea la pista muda de 8000 Hz que la app abre en Android para los botones multimedia. Esa pista mantiene el audio del sistema escribiendo al mismo DAC mientras suena por USB directo. Los botones del auricular pueden dejar de funcionar. Aplica desde la siguiente pista.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(sinPs, { sinPs = it; dp.edit().putBoolean("diag_sin_pista_silenciosa", it).apply(); org.ostrichbat2k.usbdac.streaming.UsbTelemetry.sinPistaSilenciosa = it })
                }
            }
            run {
                val dp = ctx.getSharedPreferences("qbdlx", 0)
                var mitad by remember { mutableStateOf(dp.getBoolean("diag_bloques_mitad", false)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Diagnóstico: bloques USB de la mitad de tamaño", fontSize = 14.sp)
                        Text("Envía bloques de 8 ms en vez de 16 ms. Tras cada arranque el DAC se paró siempre a los ~512 bloques (≈8,2 s); esto dice si el límite es de bloques o de tiempo. Aplica desde la siguiente pista.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(mitad, { mitad = it; dp.edit().putBoolean("diag_bloques_mitad", it).apply(); org.ostrichbat2k.usbdac.streaming.UsbTelemetry.bloquesMitad = it })
                }
            }
            run {
                val dp = ctx.getSharedPreferences("qbdlx", 0)
                var p120 by remember { mutableStateOf(dp.getBoolean("diag_paquetes_120", false)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Diagnóstico: bloques USB de 15 ms (120 paquetes)", fontSize = 14.sp)
                        Text("Los cortes caen siempre en el bloque nº 255, 510, 765… Con 120 paquetes por bloque, si la causa es el anillo USB del teléfono, caerán cada 17 bloques. Manda sobre «mitad de tamaño». Aplica desde la siguiente pista.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(p120, { p120 = it; dp.edit().putBoolean("diag_paquetes_120", it).apply(); org.ostrichbat2k.usbdac.streaming.UsbTelemetry.paquetes120 = it })
                }
            }
            run {
                val dp = ctx.getSharedPreferences("qbdlx", 0)
                var reabrir by remember { mutableStateOf(dp.getBoolean("diag_reabrir_255", false)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Prueba de arreglo: reabrir el envío antes del bloque 255", fontSize = 14.sp)
                        Text("Los cortes caen siempre en el bloque nº 255, 510, 765… Cada ~4 s, justo antes, deja terminar los bloques en vuelo y reinicia la conexión de audio del DAC sin perder nada del búfer. Puede oírse un clic breve cada ~4 s: dime si se oye. Necesita «sin lectura de feedback». Aplica desde la siguiente pista.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(reabrir, { reabrir = it; dp.edit().putBoolean("diag_reabrir_255", it).apply(); org.ostrichbat2k.usbdac.streaming.UsbTelemetry.reabrirAntes255 = it })
                }
            }
            run {
                val dp = ctx.getSharedPreferences("qbdlx", 0)
                var borde by remember { mutableStateOf(dp.getBoolean("diag_evitar_borde_255", false)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Prueba de arreglo sin cortes: esquivar el borde de 255", fontSize = 14.sp)
                        Text("El bloque que terminaría justo en el borde del anillo USB del teléfono (cada 255 bloques) lleva un paquete más. No reabre nada: no debería oírse. Si con esto no hay cortes en 10 minutos, queda resuelto. Aplica desde la siguiente pista.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(borde, { borde = it; dp.edit().putBoolean("diag_evitar_borde_255", it).apply(); org.ostrichbat2k.usbdac.streaming.UsbTelemetry.evitarBorde255 = it })
                }
            }
            run {
                val dp = ctx.getSharedPreferences("qbdlx", 0)
                var margen by remember { mutableStateOf(dp.getBoolean("diag_mas_margen", false)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Prueba de arreglo: más margen USB (12 bloques en camino)", fontSize = 14.sp)
                        Text("Los cortes llegan al usar el teléfono: el envío se retrasa más de lo que cubren 4 bloques. Con 12 bloques en camino cabe un retraso 3 veces mayor. Aplica desde la siguiente pista.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(margen, { margen = it; dp.edit().putBoolean("diag_mas_margen", it).apply(); org.ostrichbat2k.usbdac.streaming.UsbTelemetry.masMargen = it })
                }
            }
            run {
                val dp = ctx.getSharedPreferences("qbdlx", 0)
                var decent by remember { mutableStateOf(dp.getBoolean("diag_como_decent", false)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Prueba de arreglo: enviar como decent-player", fontSize = 14.sp)
                        Text("80 bloques de 1 ms en camino (~80 ms de margen), como el reproductor libre decent-player, probado en un Galaxy S26 Ultra. Manda sobre «mitad», «120 paquetes» y «más margen». Aplica desde la siguiente pista.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(decent, { decent = it; dp.edit().putBoolean("diag_como_decent", it).apply(); org.ostrichbat2k.usbdac.streaming.UsbTelemetry.comoDecent = it })
                }
            }
            run {
                val dp = ctx.getSharedPreferences("qbdlx", 0)
                var rendim by remember { mutableStateOf(dp.getBoolean("diag_pedir_rendimiento", false)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Prueba de arreglo: pedir rendimiento al sistema", fontSize = 14.sp)
                        Text("Le dice a Android que el hilo de envío no puede llegar tarde (Performance Hint, Android 12+), para que suba la frecuencia o use núcleos rápidos. Puede gastar algo más de batería. Aplica desde la siguiente pista.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(rendim, { rendim = it; dp.edit().putBoolean("diag_pedir_rendimiento", it).apply(); org.ostrichbat2k.usbdac.streaming.UsbTelemetry.pedirRendimiento = it })
                }
            }
            TextButton(onClick = { diag = diagnoseUsb(ctx) + (if (usbOn) "\n\n== libusbdac ==\n" + (UsbDirect.managerOrNull(ctx)?.diagnosticLog?.value?.joinToString("\n") ?: UsbDirect.failure.orEmpty()) else "") + java.io.File(ctx.filesDir, QbdlxApp.CRASH_FILE).takeIf { it.exists() }?.let { "\n\n== Último cierre inesperado ==\n" + it.readText().take(3000) }.orEmpty() + "\n\n== Sesión anterior (registro por segundo) ==\n" + DiagnosticLog.previousSession(ctx) + "\n\n== Reproducción (últimos eventos) ==\n" + PlaybackLog.read(ctx).ifEmpty { "(nada todavía)" } }, Modifier.padding(horizontal = 8.dp)) { Text(s.usbDiagButton) }
            diag?.let { txt ->
                val clip = LocalClipboardManager.current
                AlertDialog(
                    onDismissRequest = { diag = null },
                    title = { Text(s.usbDiagButton) },
                    text = { Column(Modifier.verticalScroll(rememberScrollState())) { SelectionContainer { Text(txt, fontSize = 12.sp, fontFamily = FontFamily.Monospace) } } },
                    confirmButton = { TextButton({ clip.setText(androidx.compose.ui.text.AnnotatedString(txt)) }) { Text(s.usbDiagCopy) } },
                    dismissButton = { TextButton({ diag = null }) { Text(s.usbDiagClose) } },
                )
            }
        }

        // ── Biblioteca y herramientas ──
        SettingsGroup(s.importEntry) {
            ListItem(
                headlineContent = { Text(s.importEntry) }, leadingContent = { Icon(Icons.Default.LibraryMusic, null) },
                modifier = Modifier.clickable { vm.openImport() }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(s.lyricsListTitle) }, supportingContent = { Text(s.lyricsListDesc) },
                modifier = Modifier.clickable { vm.sendLyricsList() }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(if (vm.duplicatesBusy) s.dupScanning else s.dupScan) },
                modifier = Modifier.clickable(enabled = !vm.duplicatesBusy) { vm.scanDuplicates() }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        vm.duplicates?.let { groups ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { vm.duplicates = null },
                confirmButton = { Button({ vm.deleteSelectedDuplicates() }, enabled = vm.dupSelected.isNotEmpty()) { Text(s.dupDelete(vm.dupSelected.size)) } },
                dismissButton = { TextButton({ vm.duplicates = null }) { Text(s.cancel) } },
                title = { Text(s.dupTitle(groups.size)) },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn {
                        item { Text(s.dupHint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (groups.isEmpty()) item { Text(s.dupNone, Modifier.padding(top = 8.dp)) }
                        groups.forEach { g ->
                            item {
                                Column(Modifier.padding(vertical = 6.dp)) {
                                    g.forEach { f ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (f.uri != null) androidx.compose.material3.Checkbox(f.uri in vm.dupSelected, { vm.dupToggle(f.uri) })
                                            else Spacer(Modifier.width(48.dp))
                                            Text(f.label, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }

        // ── Apariencia ──
        SettingsGroup(s.language) {
            Language.entries.forEach { l ->
                Row(Modifier.fillMaxWidth().clickable { vm.updateLanguage(l) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(l == vm.language, { vm.updateLanguage(l) })
                    Text(s.languageName(l))
                }
            }
            ListItem(
                headlineContent = { Text(s.amoledBlack) }, trailingContent = { Switch(vm.amoled, { vm.updateAmoled(it) }) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        // ── Cuenta ──
        SettingsGroup(s.signedInAs(vm.userName), s.updateCredsHelp) {
            Button({ vm.refreshQobuzCredentials() }, enabled = !vm.busy, modifier = Modifier.padding(horizontal = 16.dp)) {
                if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(s.updateCreds)
            }
            ListItem(
                headlineContent = { Text(s.diagOpen) }, leadingContent = { Icon(Icons.Default.BugReport, null) },
                modifier = Modifier.clickable { vm.openDiagnostics() }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (vm.hasCrashReport) ListItem(
                headlineContent = { Text(s.crashShare) },
                modifier = Modifier.clickable { vm.shareCrashReport() }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            TextButton({ vm.logout() }, Modifier.padding(horizontal = 8.dp)) { Text(s.logOut) }
        }
        Text(s.disclaimer, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }
}

/** One settings block: a small title (and help) above a rounded card that holds the controls. */
@Composable
private fun SettingsGroup(title: String, help: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionHeading(title, Modifier.padding(horizontal = 4.dp))
        if (help != null) Text(help, Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), content = content)
        }
    }
}

@Composable
fun MiniPlayer(vm: MainViewModel) {
    val (album, track) = vm.nowPlaying ?: return
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(track.id) {
        while (true) { pos = vm.engine.position; delay(500) }
    }
    val dur = (vm.engine.duration.takeIf { it > 0 } ?: (track.durationSec * 1000L)).coerceAtLeast(1)
    // A rounded dark card floating over the content, its gold progress line along the bottom edge (as in Qobuz).
    androidx.compose.material3.Surface(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.swipeToSkip(vm::next, vm::previous).swipeUpToOpen { vm.playerOpen = true }.clickable { vm.playerOpen = true }) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(album.coverUrl, 44)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.displayTitle, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(album.artist, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton({ vm.togglePlay() }) {
                    Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, LocalStrings.current.playPause, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            LinearProgressIndicator(
                progress = { (pos.toFloat() / dur).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary, trackColor = androidx.compose.ui.graphics.Color.Transparent,
            )
        }
    }
}

@Composable
fun SigningInScreen() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(LocalStrings.current.signingIn)
    }
}
