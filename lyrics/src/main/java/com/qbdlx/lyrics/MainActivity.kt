package com.qbdlx.lyrics

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val audioPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startLocalPrefetch() else android.widget.Toast.makeText(this, "Sin permiso de audio no puedo leer la música del teléfono.", android.widget.Toast.LENGTH_LONG).show()
    }

    private fun startLocalPrefetch() {
        val tracks = Prefetch.fromMediaStore(this)
        if (tracks.isEmpty()) android.widget.Toast.makeText(this, "No encontré música en el teléfono.", android.widget.Toast.LENGTH_LONG).show()
        else Prefetch.start(this, tracks)
    }

    private fun fromPhone() {
        val perm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) startLocalPrefetch() else audioPermission.launch(perm)
    }

    /** The list QBDLX Mobile shares arrives as a JSON file. */
    private fun handleShared(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        val text = uri?.let { runCatching { contentResolver.openInputStream(it)?.use { s -> s.readBytes().toString(Charsets.UTF_8) } }.getOrNull() } ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        val tracks = text?.let { Prefetch.parse(it) }
        if (tracks.isNullOrEmpty()) android.widget.Toast.makeText(this, "Ese archivo no es una lista de canciones de QBDLX Mobile.", android.widget.Toast.LENGTH_LONG).show()
        else Prefetch.start(this, tracks)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleShared(intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tracker.start(this)
        LyricsStore.init(this)
        if (savedInstanceState == null) handleShared(intent)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1) // so the lyric notification can show
        val prefs = getSharedPreferences("lyrics", MODE_PRIVATE)
        Tracker.offsetMs = prefs.getLong("offset", 0)
        setContent {
            val accent by Tracker.accent.collectAsState()
            MaterialTheme(colorScheme = schemeFor(accent, isSystemInDarkTheme())) {
                Surface(Modifier.fillMaxSize()) {
                    CoverBackdrop(accent) // blurred cover behind everything
                    var offset by remember { mutableLongStateOf(Tracker.offsetMs) }
                    Screen(offset) { v -> offset = v; Tracker.offsetMs = v; prefs.edit().putLong("offset", v).apply() }
                }
            }
        }
    }

    /** The cover shrunk to a few pixels and stretched back (a blur on every Android version), under a dark veil. Fades between songs. */
    @Composable
    private fun CoverBackdrop(key: Int) {
        if (!isSystemInDarkTheme()) return // the light theme keeps its plain background (dark text would not read over the veil)
        val cover = Tracker.cover
        val img = remember(cover, key) {
            cover?.let { runCatching { android.graphics.Bitmap.createScaledBitmap(it, 64, 64, true).asImageBitmap() }.getOrNull() }
        }
        Crossfade(img, animationSpec = tween(400, easing = UiEaseOut), label = "backdrop") { b ->
            Box(Modifier.fillMaxSize()) {
                // real blur on Android 12+, a smooth 64 px upscale on older versions
                val blur = if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(48.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded) else Modifier
                if (b != null) androidx.compose.foundation.Image(b, null, Modifier.fillMaxSize().then(blur), contentScale = androidx.compose.ui.layout.ContentScale.Crop, alpha = .55f,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(.35f), Color.Black.copy(.75f)))))
            }
        }
    }

    /** Three dots that light up one after another as [progress] goes 0..1, and breathe while [active]. Opacity and scale only. */
    @Composable
    private fun BreathDots(progress: Float, active: Boolean, modifier: Modifier = Modifier) {
        val breathe = androidx.compose.animation.core.rememberInfiniteTransition(label = "breathe").animateFloat(
            1f, 1.12f, androidx.compose.animation.core.infiniteRepeatable(tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "b").value
        val show by animateFloatAsState(if (active) 1f else .35f, tween(if (active) 280 else 200, easing = UiEaseOut), label = "dotsShow")
        Row(modifier.graphicsLayer { alpha = show; val k = if (active) breathe else 1f; scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f) },
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (n in 0..2) {
                val lit by animateFloatAsState(if (progress * 3f >= n + .15f) 1f else .3f, tween(220, easing = UiEaseOut), label = "dot$n")
                Box(Modifier.size(14.dp).graphicsLayer { alpha = lit; scaleX = .8f + .2f * lit; scaleY = .8f + .2f * lit }
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
            }
        }
    }

    @Composable
    private fun Screen(offset: Long, setOffset: (Long) -> Unit) {
        val state by Tracker.state.collectAsState()
        var pos by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) { while (true) { pos = Tracker.positionMs(); delay(200) } }
        val idx = if (state.status == Status.SYNCED) Lrc.indexAt(state.lines, pos) else -1
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        // Dragging the list lets the reader browse without being pulled back; "Resincronizar" returns to the line being sung.
        var browsing by remember { mutableStateOf(false) }
        val dragged by listState.interactionSource.collectIsDraggedAsState()
        LaunchedEffect(dragged) { if (dragged) browsing = true }
        LaunchedEffect(state.track?.key) { browsing = false }
        LaunchedEffect(idx, browsing) { if (!browsing && idx >= 0) listState.animateScrollToItem(idx) } // the list padding puts that item in the first third
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Q Lyrics", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            val pre by Prefetch.state.collectAsState()
            if (pre.running || pre.total > 0) {
                Text(
                    (if (pre.running) "Descargando letras: " else "Descarga terminada: ") + "${pre.done} de ${pre.total} · ${pre.found} guardadas · ${pre.missing} sin letra" + (pre.error?.let { " · $it" } ?: ""),
                    fontSize = 12.sp,
                )
                if (pre.running) {
                    LinearProgressIndicator(progress = { if (pre.total > 0) pre.done / pre.total.toFloat() else 0f }, modifier = Modifier.fillMaxWidth())
                    TextButton({ Prefetch.cancel() }) { Text("Cancelar") }
                }
            }
            var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
            val tools = tab == 1
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Letra") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Ajustes") })
            }
            if (!pre.running && tools) {
                PillTonal({ fromPhone() }, Modifier.fillMaxWidth()) { Text("Bajar letras de la música del teléfono", fontSize = 12.sp) }
                Text("Para tu biblioteca de Qobuz: en QBDLX Mobile → Ajustes → «Enviar lista de canciones a Q Lyrics».", fontSize = 11.sp)
            }
            if (state.status == Status.NO_ACCESS) {
                Text("Para saber qué canción suena, la app necesita «Acceso a notificaciones». Actívalo para Q Lyrics (no lee tus notificaciones, solo la canción en reproducción).")
                Button({ startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Dar acceso") }
            } else {
                state.track?.let { Text("${it.title} — ${it.artist}", fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.basicMarquee()) }
                    ?: Text("Pon una canción en tu app de música.")
                val savedCount by Tracker.saved.collectAsState()
                val isSaved = state.track?.let { (LyricsStore.has(it.storeKey) || LyricsStore.has(it.key)) } == true && savedCount >= 0
                if (isSaved) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Letra guardada en el teléfono: no se vuelve a buscar.", Modifier.weight(1f), fontSize = 12.sp)
                    TextButton({ Tracker.forgetAndSearch() }) { Text("Buscar de nuevo") }
                }
                if (state.status == Status.LOADING) Text("Buscando la letra…")
                if (state.status == Status.NOT_FOUND) Text("No hay letra para esta canción.")
                if (state.status == Status.ERROR) Text("No se pudo cargar la letra (${Tracker.lastError}). Se reintenta sola.")
                if (state.status == Status.PLAIN) Text("Solo hay letra sin sincronizar.", fontSize = 12.sp)
                if (tools) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ajuste de sincronía: ${offset} ms", Modifier.weight(1f), fontSize = 12.sp)
                    PillTonal({ setOffset(offset - 250) }) { Text("−") }
                    PillTonal({ setOffset(offset + 250) }) { Text("+") }
                }
                var floating by remember { mutableStateOf(FloatingService.running) }
                if (tools) PillTonal({
                    if (!Settings.canDrawOverlays(this@MainActivity))
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))) // then tap the button again
                    else { FloatingService.toggle(this@MainActivity); floating = !floating }
                }) { Text(if (floating) "Cerrar ventana flotante" else "Ventana flotante con la letra") }
                if (browsing && state.status == Status.SYNCED) PillButton({ browsing = false }) { Text("Resincronizar") }
                if (!tools) BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // a third of the height as padding above, so the line being sung sits in the first third however many lines fit
                    val pad = if (state.status == Status.SYNCED) (maxHeight / 3 - 24.dp).coerceAtLeast(0.dp) else 12.dp
                    LazyColumn(Modifier.fillMaxSize()
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent() // soft fade at the top and bottom edges
                            val f = size.height * .12f / size.height
                            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(
                                0f to androidx.compose.ui.graphics.Color.Transparent, f to androidx.compose.ui.graphics.Color.Black,
                                1f - f to androidx.compose.ui.graphics.Color.Black, 1f to androidx.compose.ui.graphics.Color.Transparent),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                        }, state = listState, contentPadding = PaddingValues(top = pad, bottom = pad)) {
                        // Intro longer than 5 s: three dots that fill up until the first line.
                        val first = state.lines.firstOrNull()?.timeMs ?: 0L
                        if (state.status == Status.SYNCED && first >= 5000) item(key = "intro") {
                            BreathDots(if (idx < 0) (pos.toFloat() / first).coerceIn(0f, 1f) else 1f, idx < 0, Modifier.padding(vertical = 10.dp))
                        }
                        itemsIndexed(state.lines) { i, l ->
                            val on = i == idx
                            val span = (state.lines.getOrNull(i + 1)?.timeMs ?: (l.timeMs + 4000)) - l.timeMs
                            if (state.status == Status.SYNCED && l.text.isBlank() && span >= 5000) {
                                BreathDots(if (on) ((pos - l.timeMs).toFloat() / span).coerceIn(0f, 1f) else if (i < idx) 1f else 0f, on, Modifier.padding(vertical = 10.dp))
                                return@itemsIndexed
                            }
                            val dist = if (idx >= 0) kotlin.math.abs(i - idx) else 0
                            val ms = if (on) 280 else 200
                            val scale by animateFloatAsState(if (on) 1.2f else 1f, tween(ms, easing = UiEaseOut), label = "scale")
                            val color by animateColorAsState(
                                if (on) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = when (dist) { 1 -> .8f; 2 -> .6f; else -> .45f }),
                                tween(ms, easing = UiEaseOut), label = "color",
                            )
                            Text(l.text.ifBlank { "♪" }, fontSize = 22.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = color,
                                modifier = Modifier.fillMaxWidth(.83f)
                                    .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f) }
                                    .clickable(enabled = state.status == Status.SYNCED) {
                                    // jump the playing app to this line (undo the manual sync offset first)
                                    browsing = false
                                    Tracker.controls()?.seekTo((l.timeMs - Tracker.offsetMs).coerceAtLeast(0))
                                }.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
