package com.qbdlx.mobile

import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.foundation.shape.CircleShape
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
        if (vm.genres.isNotEmpty()) item(key = "genres") {
            var sheet by remember { mutableStateOf(false) }
            if (sheet) GenreSheet(vm.genres, vm.selectedGenre, { vm.selectGenre(it); sheet = false }) { sheet = false }
            Row(
                Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .1f))
                    .clickable { sheet = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(vm.genres.firstOrNull { it.first == vm.selectedGenre }?.second ?: "Todos los géneros", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
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
        // Como Qobuz: una barra de búsqueda redonda arriba y, sin búsqueda en curso, las búsquedas recientes.
        androidx.compose.material3.TextField(
            vm.searchQuery, { vm.searchQuery = it }, singleLine = true, placeholder = { Text(s.searchHint) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (vm.searchQuery.isNotEmpty()) IconButton({ vm.searchQuery = "" }) { Icon(Icons.Default.Close, null) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(28.dp),
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(vm.searchQuery) }),
        )
        if (vm.searchQuery.isBlank() && vm.recentSearches.isNotEmpty() && !vm.busy) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.recentSearches, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton({ vm.clearRecentSearches() }) { Icon(Icons.Default.Delete, s.recentSearchesClear) }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp + LocalBottomInset.current)) {
                items(vm.recentSearches, key = { "r$it" }) { q ->
                    Row(Modifier.fillMaxWidth().clickable { vm.searchQuery = q; vm.search(q) }.padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(14.dp))
                        Text(q, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton({ vm.removeRecentSearch(q) }) { Icon(Icons.Default.Close, null) }
                    }
                }
            }
            return@Column
        }
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
                if (vm.ipodPlugged || vm.ipodProgress != null) TextButton({ vm.ipodSendDownloaded(done) }) { Text(s.ipodSendDownloaded, maxLines = 1, softWrap = false) }
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
    val ctx0 = androidx.compose.ui.platform.LocalContext.current
    var sort by remember { mutableStateOf(ctx0.getSharedPreferences("qbdlx", 0).getInt("playlist_sort", 0)) }
    var grid by remember { mutableStateOf(ctx0.getSharedPreferences("qbdlx", 0).getBoolean("playlist_grid", false)) }
    val shown = remember(d, sort) { when (sort) { 1 -> d.tracks.sortedBy { it.second.title.lowercase() }; 2 -> d.tracks.sortedBy { it.second.artist.lowercase() }; else -> d.tracks } }
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
        item {
            val s = LocalStrings.current
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val prefs = ctx.getSharedPreferences("qbdlx", 0)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White).clickable { vm.playList(d.tracks.shuffled(), 0) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Shuffle, s.shuffle, tint = Color.Black)
                }
                listOf(s.sortAdded, s.sortTitle, s.sortArtist).forEachIndexed { i, label ->
                    QChip(sort == i, { sort = i; prefs.edit().putInt("playlist_sort", i).apply() }, label = { Text(label) })
                }
                listOf(false to Icons.Default.ViewList, true to Icons.Default.GridView).forEach { (g, icon) ->
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (grid == g) Color.White else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { grid = g; prefs.edit().putBoolean("playlist_grid", g).apply() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Icon(icon, null, tint = if (grid == g) Color.Black else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        // A playlist can list the same track twice, so key by position.
        if (grid) items(shown.chunked(2)) { row ->
            GridRow(row.map { (album, track) -> GridCell(album.coverUrl, track.title, track.artist) { vm.playList(shown, shown.indexOf(album to track).coerceAtLeast(0)) } })
        } else itemsIndexed(shown) { i, (album, track) -> TrackRow(vm, album, track, "${track.artist} • ${album.title}", onPlay = { vm.playList(shown, i) }) }
    }
}

/** An artist's page, like Qobuz's: most listened, albums, EPs and singles, live, similar artists. */
@Composable
fun ArtistScreen(vm: MainViewModel) {
    val a = vm.artistPage ?: return
    val s = LocalStrings.current
    BackHandler { vm.closeArtist() }
    fun type(al: Album) = al.productType.lowercase()
    fun isLive(al: Album) = Regex("\\b(live|en vivo|en directo|unplugged)\\b", RegexOption.IGNORE_CASE).containsMatchIn(al.title + " " + al.version)
    fun isEp(al: Album) = type(al).let { it.contains("single") || it.contains("ep") } || (al.tracksCount in 1..6 && !type(al).contains("album"))
    val live = a.albums.filter { isLive(it) }
    val eps = a.albums.filter { !isLive(it) && isEp(it) }
    val albums = a.albums.filter { !isLive(it) && !isEp(it) }
    var topAll by remember(a.name) { mutableStateOf(false) }
    var bioOpen by remember(a.name) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp + LocalBottomInset.current)) {
        item {
            DetailHeader(a.image, a.name, listOf(s.artistsTab, "${a.albums.size} ${s.albums.lowercase()}"), null,
                onBack = { vm.closeArtist() }, onDownload = { vm.requestDownload(DownloadRequest.Albums(a.albums)) })
            if (a.bio.isNotBlank()) {
                Text(a.bio, Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clickable { bioOpen = !bioOpen }, fontSize = 13.sp,
                    maxLines = if (bioOpen) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!bioOpen) Text(s.readMore, Modifier.padding(horizontal = 16.dp).clickable { bioOpen = true }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (a.topTracks.isNotEmpty()) {
            item { ArtistSectionHeader(s.topSongs, if (a.topTracks.size > 5) (if (topAll) "▲" else s.seeAll) else null) { topAll = !topAll } }
            val shown = if (topAll) a.topTracks else a.topTracks.take(5)
            itemsIndexed(shown, key = { _, t -> "at" + t.second.id }) { i, (album, track) ->
                TrackRow(vm, album, track.copy(number = i + 1), "${track.artist} • ${album.title}", showCover = true, onPlay = { vm.playList(a.topTracks, i) })
            }
        }
        fun section(title: String, list: List<Album>) {
            if (list.isEmpty()) return
            item(key = "sec$title") {
                ArtistSectionHeader(title, null) {}
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    list.forEach { al ->
                        Column(Modifier.width(140.dp).clickable { vm.openAlbum(al.id) }) {
                            Cover(al.coverUrl, 140)
                            Text(al.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            Text(al.year, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        section(s.albums, albums); section(s.epsSingles, eps); section(s.liveAlbums, live)
        if (a.similar.isNotEmpty()) item {
            ArtistSectionHeader(s.similarArtists, null) {}
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                a.similar.forEach { n -> QChip(false, { vm.openArtist(n) }, label = { Text(n) }) }
            }
        }
    }
}

@Composable
private fun ArtistSectionHeader(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (action != null) TextButton(onAction) { Text(action, fontSize = 12.sp) }
    }
}

private enum class LibraryView { Tracks, Albums, Artists, Playlists, History, Folders }

/** The user's Library, like the web player's: their playlists, favorite albums and favorite tracks. */
@Composable
fun LibraryScreen(vm: MainViewModel) {
    val s = LocalStrings.current
    var view by remember { mutableStateOf(LibraryView.entries.getOrElse(vm.libraryViewIdx) { LibraryView.Tracks }) }
    // Como la biblioteca de Qobuz: buscar dentro de ella y ver álbumes y listas en lista o en cuadrícula.
    var query by remember { mutableStateOf(vm.libraryQuery) }
    LaunchedEffect(query) { vm.libraryQuery = query }
    val ctxLib = androidx.compose.ui.platform.LocalContext.current
    var grid by remember { mutableStateOf(ctxLib.getSharedPreferences("qbdlx", 0).getBoolean("library_grid", false)) }
    fun hit(vararg t: String) = query.isBlank() || t.any { it.contains(query.trim(), ignoreCase = true) }
    LaunchedEffect(Unit) { vm.loadLibrary() }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp + LocalBottomInset.current)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Title(s.library) }
                IconButton({ vm.openSearch() }) { Icon(Icons.Default.Search, s.search) }
                IconButton({ vm.loadLibrary(force = true) }, enabled = !vm.libraryLoading) { Icon(Icons.Default.Refresh, s.refresh) }
            }
            androidx.compose.material3.TextField(
                query, { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text(s.searchLibrary) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Close, null) } },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
            )
            var order by remember {
                mutableStateOf(run {
                    val saved = ctxLib.getSharedPreferences("qbdlx", 0).getString("library_tab_order", "").orEmpty()
                        .split(',').mapNotNull { n -> LibraryView.entries.firstOrNull { it.name == n } }.distinct()
                    saved + LibraryView.entries.filter { it !in saved }
                })
            }
            var reordering by remember { mutableStateOf(false) }
            fun tabLabel(v: LibraryView) = when (v) { LibraryView.Playlists -> s.playlists; LibraryView.Albums -> s.albums; LibraryView.Tracks -> s.tracksTab; LibraryView.Artists -> s.artistsTab; LibraryView.History -> s.history; LibraryView.Folders -> s.localFolders }
            fun move(i: Int, d: Int) {
                val j = i + d; if (j !in order.indices) return
                order = order.toMutableList().also { val x = it[i]; it[i] = it[j]; it[j] = x }
                ctxLib.getSharedPreferences("qbdlx", 0).edit().putString("library_tab_order", order.joinToString(",") { it.name }).apply()
            }
            if (reordering) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { reordering = false },
                    title = { Text(s.reorderTabs) },
                    text = {
                        Column {
                            order.forEachIndexed { i, v ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tabLabel(v), Modifier.weight(1f))
                                    androidx.compose.material3.TextButton({ move(i, -1) }, enabled = i > 0) { Text("▲") }
                                    androidx.compose.material3.TextButton({ move(i, 1) }, enabled = i < order.size - 1) { Text("▼") }
                                }
                            }
                        }
                    },
                    confirmButton = { androidx.compose.material3.TextButton({ reordering = false }) { Text(s.reorderDone) } },
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                order.forEach { v ->
                    val label = when (v) { LibraryView.Playlists -> s.playlists; LibraryView.Albums -> s.albums; LibraryView.Tracks -> s.tracksTab; LibraryView.Artists -> s.artistsTab; LibraryView.History -> s.history; LibraryView.Folders -> s.localFolders }
                    val n = when (v) { LibraryView.Playlists -> vm.myPlaylists?.size; LibraryView.Albums -> vm.favAlbums?.size; LibraryView.Tracks -> vm.favTracks?.size; LibraryView.Artists -> vm.favArtists?.size; else -> null }
                    QChip(view == v, { view = v; vm.libraryViewIdx = v.ordinal }, label = { Text(if (n != null) "$label · $n" else label) })
                }
                QChip(false, { reordering = true }, label = { Text("⇅") })
            }
            if (view != LibraryView.History && view != LibraryView.Artists && view != LibraryView.Folders) {
                var menu by remember { mutableStateOf(false) }
                val labels = listOf(s.sortAdded, s.sortTitle, s.sortArtist, s.sortNewest, s.sortOldest)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (view == LibraryView.Playlists || view == LibraryView.Albums) {
                        Row(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            listOf(false to Icons.Default.ViewList, true to Icons.Default.GridView).forEach { (g, icon) ->
                                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (grid == g) Color.White else Color.Transparent)
                                    .clickable { grid = g; ctxLib.getSharedPreferences("qbdlx", 0).edit().putBoolean("library_grid", g).apply() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)) {
                                    Icon(icon, null, tint = if (grid == g) Color.Black else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                Box {
                    TextButton({ menu = true }) { Text(labels[vm.librarySort.coerceIn(0, labels.lastIndex)] + " ▾", color = MaterialTheme.colorScheme.onSurface) }
                    androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                        labels.forEachIndexed { i, l -> androidx.compose.material3.DropdownMenuItem({ Text(l) }, { vm.updateLibrarySort(i); menu = false }) }
                    }
                }
                if (view == LibraryView.Tracks) {
                    Spacer(Modifier.weight(1f))
                    // Reproducción aleatoria como la de Qobuz: baraja lo que se ve (con el filtro y el orden de ahora).
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White).clickable {
                        val l = vm.favTracks.orEmpty().filter { hit(it.second.displayTitle, it.second.artist, it.first.title) }.shuffled()
                        if (l.isNotEmpty()) vm.playList(l, 0)
                    }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Shuffle, s.shuffle, tint = Color.Black) }
                }
                }
            }
            if (vm.libraryLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        }
        when (view) {
            LibraryView.Playlists -> {
                val list = vm.myPlaylists.orEmpty().filter { hit(it.name, it.owner) }.let { l -> when (vm.librarySort) { 1 -> l.sortedBy { it.name.lowercase() }; 2 -> l.sortedBy { it.owner.lowercase() }; else -> l } }
                if (list.isEmpty() && !vm.libraryLoading) item { EmptyText(s.nothingHere) }
                if (list.isNotEmpty()) item { DownloadAllButton(vm) { vm.requestDownload(DownloadRequest.Playlists(list)) } }
                if (grid) items(list.chunked(2), key = { "pg" + it.first().id }) { row ->
                    GridRow(row.map { pl -> GridCell(pl.coverUrl, pl.name, pl.owner) { vm.openPlaylist(pl.id) } })
                } else items(list, key = { "p" + it.id }) { pl ->
                    // The list entry may carry only one cover: ask for the rest of the mosaic once the row is on screen.
                    LaunchedEffect(pl.id) { vm.ensurePlaylistCovers(pl) }
                    LibraryRow(pl.coverUrl, pl.name, listOf(pl.owner, if (pl.tracksCount > 0) s.tracks(pl.tracksCount) else "").filter { it.isNotBlank() }.joinToString(" • "),
                        onClick = { vm.openPlaylist(pl.id) },
                        covers = vm.playlistCovers[pl.id] ?: pl.covers) { open, close -> CollectionMenu(vm, null, pl, open, close) }
                }
            }
            LibraryView.Albums -> {
                val list = vm.favAlbums.orEmpty().filter { hit(it.title, it.artist) }.let { l -> when (vm.librarySort) { 1 -> l.sortedBy { it.title.lowercase() }; 2 -> l.sortedBy { it.artist.lowercase() }; 3 -> l.sortedByDescending { it.year }; 4 -> l.sortedBy { it.year.ifBlank { "9999" } }; else -> l } }
                if (list.isEmpty() && !vm.libraryLoading) item { EmptyText(s.nothingHere) }
                if (list.isNotEmpty()) item { DownloadAllButton(vm) { vm.requestDownload(DownloadRequest.Albums(list)) } }
                if (grid) items(list.chunked(2), key = { "ag" + it.first().id }) { row ->
                    GridRow(row.map { al -> GridCell(al.coverUrl, al.title, al.artist) { vm.openAlbum(al.id) } })
                } else items(list, key = { "a" + it.id }) { al ->
                    LibraryRow(al.coverUrl, al.title, listOf("Álbum", al.artist).filter { it.isNotBlank() }.joinToString(" • "),
                        onClick = { vm.openAlbum(al.id) },
                        badge = al.quality, hiRes = al.hiRes) { open, close -> CollectionMenu(vm, al, null, open, close) }
                }
            }
            LibraryView.Tracks -> {
                val list = vm.favTracks.orEmpty().filter { hit(it.second.displayTitle, it.second.artist, it.first.title) }.let { l -> when (vm.librarySort) { 1 -> l.sortedBy { it.second.displayTitle.lowercase() }; 2 -> l.sortedBy { it.second.artist.lowercase() }; 3 -> l.sortedByDescending { it.first.year }; 4 -> l.sortedBy { it.first.year.ifBlank { "9999" } }; else -> l } }
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
                val artists = byName.values.filter { hit(it.name) }.sortedWith(compareByDescending<Entry> { it.followed }.thenByDescending { it.tracks + it.albums.size }.thenBy { it.name.lowercase() })
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
            LibraryView.Folders -> item { LocalFoldersBrowser(vm, query) }
            LibraryView.History -> {
                val list = vm.history.filter { hit(it.second.displayTitle, it.second.artist, it.first.title) }
                if (list.isEmpty()) item { EmptyText(s.nothingHere) }
                itemsIndexed(list) { i, (album, track) -> TrackRow(vm, album, track.copy(number = i + 1), "${track.artist} • ${album.title}", showCover = true, onPlay = { vm.playList(list, i) }) }
            }
        }
    }
}

/** Carpetas locales (como UAPP): las carpetas elegidas, navegar por ellas y reproducir sus archivos. */
@Composable
private fun LocalFoldersBrowser(vm: MainViewModel, query: String) {
    val s = LocalStrings.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tree by remember { mutableStateOf<String?>(null) }
    val path = remember { androidx.compose.runtime.mutableStateListOf<Pair<String, String>>() } // (docId, nombre)
    var entries by remember { mutableStateOf<List<LocalMusic.Entry>?>(null) }
    LaunchedEffect(tree, path.size) {
        val t = tree ?: return@LaunchedEffect
        entries = null
        entries = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { LocalMusic.list(ctx, android.net.Uri.parse(t), path.lastOrNull()?.first) }.getOrDefault(emptyList())
        }
    }
    androidx.activity.compose.BackHandler(tree != null) { if (path.isNotEmpty()) path.removeAt(path.lastIndex) else tree = null }
    Column(Modifier.fillMaxWidth()) {
        val t = tree
        if (t == null) {
            vm.localRoots.forEach { root ->
                val name = LocalMusic.rootName(android.net.Uri.parse(root))
                Row(Modifier.fillMaxWidth().clickable { tree = root; path.clear() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = QobuzGold, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton({ vm.removeLocalRoot(root) }) { Icon(Icons.Default.Close, s.localFolderRemove) }
                }
            }
            TextButton({ vm.chooseLocalFolder() }, Modifier.padding(horizontal = 8.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(s.localFolderAdd) }
            if (vm.localRoots.isEmpty()) EmptyText(s.localFoldersEmpty)
            return@Column
        }
        val title = path.lastOrNull()?.second ?: LocalMusic.rootName(android.net.Uri.parse(t))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ if (path.isNotEmpty()) path.removeAt(path.lastIndex) else tree = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back) }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val list = entries
        if (list == null) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)); return@Column }
        val shown = list.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        val audio = shown.filter { !it.isDir }
        if (shown.isEmpty()) EmptyText(s.nothingHere)
        if (audio.isNotEmpty()) TextButton({ vm.playLocal(audio, title, 0) }, Modifier.padding(horizontal = 8.dp)) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(s.localPlayAll(audio.size))
        }
        shown.forEach { e ->
            Row(Modifier.fillMaxWidth().clickable {
                if (e.isDir) path.add(e.docId to e.name) else vm.playLocal(audio, title, audio.indexOf(e))
            }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (e.isDir) Icons.Default.Folder else Icons.Default.MusicNote, null,
                    tint = if (e.isDir) QobuzGold else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

private class GridCell(val cover: String?, val title: String, val subtitle: String, val onClick: () -> Unit)

/** Dos portadas por fila, como la vista en cuadrícula de la biblioteca de Qobuz. */
@Composable
private fun GridRow(cells: List<GridCell>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        cells.forEach { c ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = c.onClick)) {
                AsyncImage(c.cover, c.title, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Text(c.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text(c.subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (cells.size == 1) Spacer(Modifier.weight(1f))
    }
}

/** «Descargar toda mi cuenta»: la flecha cae una y otra vez hacia la bandeja mientras busca y encola todo. */
@Composable
private fun DownloadEverythingButton(vm: MainViewModel) {
    val busy = vm.everythingBusy
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "todo")
    val fall by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(if (busy) 900 else 2200, easing = androidx.compose.animation.core.LinearEasing)), label = "caida")
    val color = MaterialTheme.colorScheme.onPrimary
    Button({ if (!busy) vm.requestDownload(DownloadRequest.Everything) }, Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp)) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            // La bandeja de abajo y la flecha que cae hasta ella, desvaneciéndose al llegar.
            Box(Modifier.align(Alignment.BottomCenter).width(22.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Icon(Icons.Default.Download, null, Modifier.size(20.dp).graphicsLayer { translationY = (fall * 8f - 6f) * density; alpha = if (fall < 0.8f) 1f else (1f - fall) / 0.2f }, tint = color)
        }
        Spacer(Modifier.width(12.dp))
        Text(if (busy) "…" else LocalStrings.current.everythingButton, fontWeight = FontWeight.SemiBold)
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
                    if (vm.ipodPlugged || vm.ipodProgress != null) TextButton({ vm.ipodSendDownloaded() }, enabled = items.isNotEmpty()) { Text(s.ipodSendDownloaded) }
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
        SettingsGroup(s.everythingTitle, s.everythingHelp) { DownloadEverythingButton(vm) }
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
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ReplayGain", fontWeight = FontWeight.Medium)
                    Text("Iguala el volumen entre canciones con la ganancia que Qobuz da para cada pista, sin recortar los picos. Mientras esté encendido, la señal deja de ser bit-perfect. Por el mezclador de Android solo puede bajar el volumen; por USB directo también lo sube.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(vm.replayGain, { vm.updateReplayGain(it) })
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Medidor de nivel", fontWeight = FontWeight.Medium)
                    Text("Visualización en el reproductor: nivel izquierdo y derecho (con pico y máximo en dB), barras de espectro o ámbito (la onda). Tocarla en el reproductor cambia de una a otra.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(vm.levelMeter, { vm.updateLevelMeter(it) })
            }
            if (vm.levelMeter) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Nivel", "Barras", "Ámbito").forEachIndexed { i, n -> QChip(vm.meterMode == i, { vm.updateMeterMode(i) }, label = { Text(n) }) }
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
            var avanzado by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().clickable { avanzado = !avanzado }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Avanzado (DAC USB)", fontWeight = FontWeight.Medium)
                    Text("Interruptores de diagnóstico y de prueba. Lo normal es no tocarlos.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (avanzado) "Ocultar" else "Mostrar", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
            androidx.compose.animation.AnimatedVisibility(avanzado) { Column {
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
                var borde by remember { mutableStateOf(dp.getBoolean("diag_evitar_borde_255", true)) }
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
                var decent by remember { mutableStateOf(dp.getBoolean("diag_como_decent", true)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Prueba de arreglo: enviar como decent-player", fontSize = 14.sp)
                        Text("Muchos bloques cortos en camino (120 de 2 ms, ~240 ms de margen), al estilo del reproductor libre decent-player, probado en un Galaxy S26 Ultra. Manda sobre «mitad», «120 paquetes» y «más margen». Aplica desde la siguiente pista.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(decent, { decent = it; dp.edit().putBoolean("diag_como_decent", it).apply(); org.ostrichbat2k.usbdac.streaming.UsbTelemetry.comoDecent = it })
                }
            }
            run {
                val dp = ctx.getSharedPreferences("qbdlx", 0)
                var rendim by remember { mutableStateOf(dp.getBoolean("diag_pedir_rendimiento", true)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Prueba de arreglo: pedir rendimiento al sistema", fontSize = 14.sp)
                        Text("Le dice a Android que el hilo de envío no puede llegar tarde (Performance Hint, Android 12+), para que suba la frecuencia o use núcleos rápidos. Puede gastar algo más de batería. Aplica desde la siguiente pista.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(rendim, { rendim = it; dp.edit().putBoolean("diag_pedir_rendimiento", it).apply(); org.ostrichbat2k.usbdac.streaming.UsbTelemetry.pedirRendimiento = it })
                }
            }
            } }
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
    // Plegable: los ajustes empiezan cerrados y se abre solo lo que se necesita; se recuerda al girar la pantalla.
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        androidx.compose.material3.Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(if (open) "▲" else "▼", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.animation.AnimatedVisibility(open) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        if (help != null) Text(help, Modifier.padding(horizontal = 16.dp, vertical = 2.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        content()
                    }
                }
            }
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
                    PlayPauseIcon(vm, 36.dp, MaterialTheme.colorScheme.onSurface)
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
