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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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

    /** In the small picture-in-picture window the screen shows only the line being sung. */
    private var inPip by mutableStateOf(false)
    private fun pipOn() = getSharedPreferences("lyrics", MODE_PRIVATE).getBoolean("pip_on_leave", true)

    /** Leaving the app opens the floating bubble; the small picture-in-picture window is only the fallback while the overlay permission is missing. */
    private fun bubbleReady() = Settings.canDrawOverlays(this)

    private fun pipParams(auto: Boolean) = android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(239, 100)).also {
        if (android.os.Build.VERSION.SDK_INT >= 31) it.setAutoEnterEnabled(auto)
    }.build()

    /** The floating bubble when it can be shown; otherwise the picture-in-picture window. */
    private fun enterPip() { if (!FloatingService.show(this, false)) runCatching { enterPictureInPictureMode(pipParams(false)) } }

    override fun onResume() { super.onResume(); FloatingService.appForeground = true; FloatingService.hideIfAuto(this) }
    override fun onPause() { super.onPause(); FloatingService.appForeground = false }

    /** Leaving the app (Home) keeps the lyrics in a small window when the setting is on: automatically on Android 12+, from here before. */
    fun applyPipSetting() { if (android.os.Build.VERSION.SDK_INT >= 31) runCatching { setPictureInPictureParams(pipParams(pipOn() && !bubbleReady())) } }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!pipOn() || Tracker.state.value.track == null) return
        if (bubbleReady()) FloatingService.show(this, true)
        else if (android.os.Build.VERSION.SDK_INT < 31) enterPip()
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig); inPip = isInPictureInPictureMode
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleShared(intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // the screen stays on while the app is open
        Tracker.start(this)
        LyricsStore.init(this)
        if (savedInstanceState == null) handleShared(intent)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1) // so the lyric notification can show
        val prefs = getSharedPreferences("lyrics", MODE_PRIVATE)
        applyPipSetting()
        setContent {
            val accent by Tracker.accent.collectAsState()
            MaterialTheme(colorScheme = schemeFor(accent, isSystemInDarkTheme())) {
                Surface(Modifier.fillMaxSize()) {
                    CoverBackdrop(accent) // blurred cover behind everything
                    var offset by remember { mutableLongStateOf(Tracker.offsetMs) }
                    if (inPip) PipScreen() else Screen(offset) { v -> offset = v; Tracker.offsetMs = v; prefs.edit().putLong("offset", v).apply() }
                }
            }
        }
    }

    @Composable
    private fun PipScreen() {
        val state by Tracker.state.collectAsState()
        var pos by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) { while (true) { pos = Tracker.positionMs(); delay(200) } }
        val i = if (state.status == Status.SYNCED) Lrc.indexAt(state.lines, pos) else -1
        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (state.status == Status.SYNCED) {
                    Text(state.lines.getOrNull(i)?.text?.ifBlank { "♪" } ?: "♪", fontSize = 16.sp, maxLines = 2, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    state.lines.getOrNull(i + 1)?.let { Text(it.text, fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                } else Text(state.track?.title ?: "Q Lyrics", fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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

    /** Fades and unfolds its content the first time it is shown (cards that appear after a check or a search). */
    @Composable
    private fun Appear(content: @Composable () -> Unit) {
        val st = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
        androidx.compose.animation.AnimatedVisibility(st,
            enter = androidx.compose.animation.fadeIn(tween(220, easing = UiEaseOut)) + androidx.compose.animation.expandVertically(tween(260, easing = UiEaseOut)) +
                androidx.compose.animation.slideInVertically(tween(260, easing = UiEaseOut)) { -it / 5 }) { content() }
    }

    /** Asks before another lyric (found by listening to the song) replaces the one the user has. */
    @Composable
    private fun SuggestionBanner(track: TrackInfo?) {
        val sg by Tracker.suggestion.collectAsState()
        val s = sg ?: return
        if (track == null || s.trackKey != track.key) return
        Appear { QCard(Modifier.fillMaxWidth()) {
            Text("¿Cambiar la letra?", fontWeight = FontWeight.Bold)
            Text("Encontré otra letra en ${s.source} que coincide mejor con lo que se oye en la canción que la que tienes. Empieza así:", style = MaterialTheme.typography.bodySmall)
            Text(s.lines.map { it.text }.filter { it.isNotBlank() }.take(2).joinToString("\n"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PillButton({ Tracker.acceptSuggestion() }) { Text("Cambiarla", maxLines = 1, softWrap = false) }
                TextButton({ Tracker.declineSuggestion() }) { Text("Dejar la mía") }
            }
        } }
    }

    /** The result of "Verificar sincronización": says in words whether the lyric is in time and offers the fix. */
    @Composable
    private fun VerificationBanner(track: TrackInfo?) {
        val vf by Tracker.verification.collectAsState()
        val r = vf ?: return
        if (track == null || r.trackKey != track.key) return
        val v = r.verdict
        val secs = "%.1f".format(kotlin.math.abs(v.medianMs) / 1000.0)
        Appear { QCard(Modifier.fillMaxWidth()) {
            Text(when (v.kind) { "ok" -> "✓ Bien sincronizada"; "off" -> "Desfasada"; "drift" -> "Se va desfasando"; else -> "Sin comprobar con la voz" }, fontWeight = FontWeight.Bold)
            Text(when (v.kind) {
                "ok" -> "${v.within} de ${v.total} líneas coinciden con la voz (margen de 2,5 s)."
                "off" -> "Toda la letra va ${if (v.medianMs < 0) "adelantada" else "atrasada"} unos $secs s respecto a la voz." + (if (v.total > 0 && v.within == 0) " (Medido con el sonido de la canción, no con Whisper.)" else "")
                "drift" -> "Solo ${v.within} de ${v.total} líneas coinciden: el error cambia a lo largo de la canción. Puedo usar los tiempos de la voz."
                else -> "Whisper no entiende bien el idioma de esta canción, así que no pudo comparar. Se usan los tiempos de la fuente de la letra, que no tienen por qué estar mal."
            }, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (r.fix != null) PillButton({ Tracker.applyVerificationFix() }) { Text(if (v.kind == "off") (if (v.medianMs < 0) "Retrasar la letra $secs s" else "Adelantar la letra $secs s") else "Usar tiempos de la voz", maxLines = 1, softWrap = false) }
                TextButton({ Tracker.verification.value = null }) { Text("Cerrar") }
            }
        } }
    }

    private class EditRow(val line: LyricLine, text: String) { var text by mutableStateOf(text) }

    /** Full-screen editor: fix the words of any line, delete a line, or change a misheard word everywhere. Times are kept. */
    @Composable
    private fun LyricsPicker(track: TrackInfo, onClose: () -> Unit) {
        var items by remember { mutableStateOf<List<Candidate>?>(null) }
        var why by remember { mutableStateOf("") }
        LaunchedEffect(track.key) {
            val (list, msg) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Candidates.search(track) }
            items = list; why = msg
        }
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Elegir la letra", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${track.title} — ${track.artist}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text("Estas son las letras que se encontraron. La que elijas se queda y no se reemplaza sola.", style = MaterialTheme.typography.bodySmall)
                    val list = items
                    if (list == null) Text("Buscando en LRCLIB y YouTube Music…")
                    else if (list.isEmpty()) Text("No encontré otras letras. $why")
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list.orEmpty().size) { i ->
                            val c = list!![i]
                            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(c.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(c.preview, fontSize = 12.sp, maxLines = 4, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton({ Tracker.useChosen(c.lyrics); onClose() }) { Text("Usar esta") }
                                }
                            }
                        }
                    }
                    TextButton(onClose) { Text("Cerrar") }
                }
            }
        }
    }

    @Composable
    private fun LyricsEditor(state: LyricsState, onClose: () -> Unit) {
        val synced = state.status == Status.SYNCED
        val rows = remember { mutableStateListOf<EditRow>().also { l -> state.lines.forEach { l.add(EditRow(it, it.text)) } } }
        var from by remember { mutableStateOf("") }
        var to by remember { mutableStateOf("") }
        var note by remember { mutableStateOf<String?>(null) }
        fun current() = rows.map { LyricLine(it.line.timeMs, it.text, it.line.endMs) }
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Editar la letra", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(state.track?.let { "${it.title} — ${it.artist}" }.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text("Corrige las palabras de cualquier línea; los tiempos no cambian. Si una palabra salió mal varias veces, cámbiala en toda la letra:", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(from, { from = it }, Modifier.weight(1f), singleLine = true, label = { Text("Palabra") })
                        OutlinedTextField(to, { to = it }, Modifier.weight(1f), singleLine = true, label = { Text("Por") })
                        TextButton({
                            val (out, n) = LyricEdit.replaceWord(current(), from, to)
                            out.forEachIndexed { i, l -> rows[i].text = l.text }
                            note = if (n == 0) "No encontré «${from.trim()}»." else "Cambiadas $n veces."
                        }, enabled = from.isNotBlank()) { Text("Cambiar") }
                    }
                    note?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(rows, key = { _, r -> System.identityHashCode(r) }) { _, r ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                if (synced) Text("%d:%02d".format(r.line.timeMs / 60000, r.line.timeMs / 1000 % 60), Modifier.width(40.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(r.text, { r.text = it }, Modifier.weight(1f), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp), placeholder = { Text("♪ (pausa)") })
                                TextButton({ rows.remove(r) }) { Text("✕") }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PillButton({ Tracker.saveEdited(current()); onClose() }, Modifier.weight(1f), enabled = rows.isNotEmpty()) { Text("Guardar", maxLines = 1, softWrap = false) }
                        TextButton(onClose) { Text("Cancelar") }
                    }
                }
            }
        }
    }

    @Composable
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    private fun Screen(offset: Long, setOffset: (Long) -> Unit) {
        val state by Tracker.state.collectAsState()
        var pos by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) { while (true) { pos = Tracker.positionMs(); delay(200) } }
        // Tap-timing of a lyric that has no times: one tap per line as it starts to be sung.
        var tagging by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf(false) }
        var picking by remember { mutableStateOf(false) }
        LaunchedEffect(state.track?.key) { picking = false }
        if (picking && state.track != null) LyricsPicker(state.track!!) { picking = false }
        LaunchedEffect(state.track?.key) { editing = false }
        if (editing && (state.status == Status.SYNCED || state.status == Status.PLAIN)) LyricsEditor(state) { editing = false }
        val marks = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
        LaunchedEffect(state.track?.key, state.status) { tagging = false; marks.clear() }
        val idx = if (state.status == Status.SYNCED) Lrc.indexAt(state.lines, pos)
            else if (tagging && state.lines.isNotEmpty()) marks.size.coerceAtMost(state.lines.size - 1) else -1
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        // Dragging the list lets the reader browse without being pulled back; "Resincronizar" returns to the line being sung.
        val translations by Tracker.translations.collectAsState()
        var browsing by remember { mutableStateOf(false) }
        val dragged by listState.interactionSource.collectIsDraggedAsState()
        LaunchedEffect(dragged) { if (dragged) browsing = true }
        LaunchedEffect(state.track?.key) { browsing = false }
        val hasIntro = state.status == Status.SYNCED && (state.lines.firstOrNull()?.timeMs ?: 0L) >= 5000 // the dots are item 0, so every line sits one place later
        LaunchedEffect(idx, browsing) { if (!browsing && idx >= 0) listState.animateScrollToItem(idx + if (hasIntro) 1 else 0) } // the list padding puts that item in the first third
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Q Lyrics", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            val pre by Prefetch.state.collectAsState()
            if (pre.running || pre.total > 0) {
                Text(
                    (if (pre.running) "Descargando letras: " else "Descarga terminada: ") + "${pre.done} de ${pre.total} · ${pre.found} nuevas · ${pre.skipped} que ya estaban · ${pre.missing} sin letra" + (pre.error?.let { " · $it" } ?: ""),
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
            if (tools) {
                val sp = getSharedPreferences("lyrics", MODE_PRIVATE)
                val savedCount by Tracker.saved.collectAsState()
                val isSaved = state.track?.let { (LyricsStore.has(it.storeKey) || LyricsStore.has(it.key)) } == true && savedCount >= 0
                var karaoke by remember { mutableStateOf(Tracker.karaoke) }
                var autoW by remember { mutableStateOf(Tracker.autoWhisper) }
                var lead by remember { mutableLongStateOf(Tracker.carLeadMs) }
                var floating by remember { mutableStateOf(FloatingService.running) }
                val whisperOk = remember { Whisper.available(this@MainActivity) }
                var pipAuto by remember { mutableStateOf(pipOn()) }
                val small = MaterialTheme.typography.bodySmall
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    QCard(Modifier.fillMaxWidth()) {
                        Text("Sincronía y karaoke", fontWeight = FontWeight.Bold)
                        if (whisperOk) Row(Modifier.padding(top = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Poner tiempos solo a las letras sin sincronizar, escuchando la voz (Whisper; gasta batería)", Modifier.weight(1f), fontSize = 13.sp)
                            androidx.compose.material3.Switch(autoW, { autoW = it; Tracker.autoWhisper = it; sp.edit().putBoolean("auto_whisper", it).apply() })
                        }
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Sincronía: $offset ms", Modifier.weight(1f))
                            TextButton({ setOffset((offset - 250).coerceIn(-10_000, 10_000)) }) { Text("−", fontSize = 20.sp) }
                            TextButton({ setOffset((offset + 250).coerceIn(-10_000, 10_000)) }) { Text("+", fontSize = 20.sp) }
                        }
                        if (kotlin.math.abs(offset) >= 1500) Row(Modifier.padding(top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("⚠ La sincronía manual está en ${offset / 1000.0} s: toda letra irá desfasada esa cantidad, también los puntos de la introducción. Suele quedar así al mantener pulsada una línea que no era la que sonaba.", Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            TextButton({ setOffset(0) }) { Text("Poner a 0") }
                        }
                        Text("Si la letra va adelantada o atrasada, ajústala aquí (pasos de 250 ms). Más rápido: en la pestaña Letra, mantén pulsada la línea que está sonando.", style = small)
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Relleno tipo karaoke", Modifier.weight(1f))
                            androidx.compose.material3.Switch(karaoke, { karaoke = it; Tracker.karaoke = it; sp.edit().putBoolean("karaoke", it).apply() })
                        }
                        Text("Pinta la línea palabra por palabra mientras se canta (aproximado).", style = small)
                        var trOn by remember { mutableStateOf(Tracker.translate) }
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Traducir al español", Modifier.weight(1f))
                            androidx.compose.material3.Switch(trOn, { trOn = it; Tracker.translate = it; sp.edit().putBoolean("translate", it).apply(); Tracker.translateLines() })
                        }
                        Text("Pone la traducción bajo las líneas que no están en español (en el coche y en la ventana flotante también). Usa internet.", style = small)
                    }
                    QCard(Modifier.fillMaxWidth()) {
                        Text("Verificar sincronía", fontWeight = FontWeight.Bold)
                        val sc by SyncCheck.state.collectAsState(); val pend by SyncCheck.pending.collectAsState(); val toFix by SyncCheck.toFix.collectAsState()
                        var syncAuto by remember { mutableStateOf(SyncCheck.autoOnWifi) }
                        Text("Whisper escucha cada canción que tengas en el teléfono y comprueba que su letra guardada va a tiempo. Si va adelantada o atrasada por igual, la mueve; si se va desfasando, usa los tiempos de Whisper.", style = small)
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Verificar cada letra nueva con wifi", Modifier.weight(1f))
                            androidx.compose.material3.Switch(syncAuto, { syncAuto = it; SyncCheck.setAuto(this@MainActivity, it) })
                        }
                        if (pend > 0) Text("$pend letras nuevas esperando el wifi.", style = small)
                        if (sc.running) {
                            Text("Revisando ${sc.done} de ${sc.total}" + (if (sc.current.isNotBlank()) " · ${sc.current}" else "") + " · ${sc.ok} bien · ${sc.off} desfasadas · ${sc.unknown} sin poder juzgar", style = small)
                            LinearProgressIndicator(progress = { if (sc.total > 0) sc.done / sc.total.toFloat() else 0f }, modifier = Modifier.fillMaxWidth())
                            sc.message?.let { Text(it, style = small) }
                            TextButton({ SyncCheck.cancel() }) { Text("Cancelar") }
                        } else {
                            if (sc.total > 0 || sc.message != null) Text(sc.message ?: "Última revisión: ${sc.total} canciones · ${sc.ok} bien · ${sc.off} desfasadas (${sc.fixed} corregidas) · ${sc.unknown} sin poder juzgar", style = small)
                            if (whisperOk) {
                                PillTonal({ SyncCheck.scan(this@MainActivity, fix = false) }, Modifier.fillMaxWidth()) { Text("Escanear las letras guardadas", fontSize = 12.sp) }
                                if (toFix > 0) PillButton({ val n = SyncCheck.fixAll(this@MainActivity); android.widget.Toast.makeText(this@MainActivity, "$n letras corregidas", android.widget.Toast.LENGTH_SHORT).show() }, Modifier.fillMaxWidth()) { Text("Corregir las $toFix desfasadas", fontSize = 12.sp) }
                            } else Text("Whisper no está en esta versión.", style = small)
                        }
                        Text("Tarda unos minutos por canción y gasta batería: déjalo cargando. Solo se pueden revisar las canciones que estén como archivo en el teléfono.", style = small)
                    }
                    QCard(Modifier.fillMaxWidth()) {
                        Text("Android Auto", fontWeight = FontWeight.Bold)
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Adelanto: $lead ms", Modifier.weight(1f))
                            TextButton({ lead = (lead - 250).coerceIn(0, 4000); Tracker.carLeadMs = lead; sp.edit().putLong("car_lead", lead).apply() }) { Text("−", fontSize = 20.sp) }
                            TextButton({ lead = (lead + 250).coerceIn(0, 4000); Tracker.carLeadMs = lead; sp.edit().putLong("car_lead", lead).apply() }) { Text("+", fontSize = 20.sp) }
                        }
                        Text("Si en el coche la letra llega tarde, sube el adelanto; si llega antes de tiempo, bájalo. La sincronía de arriba también cuenta en el coche.", style = small)
                    }
                    QCard(Modifier.fillMaxWidth()) {
                        Text("Ventana flotante", fontWeight = FontWeight.Bold)
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Abrir la burbuja al salir de la app", Modifier.weight(1f))
                            androidx.compose.material3.Switch(pipAuto, { pipAuto = it; sp.edit().putBoolean("pip_on_leave", it).apply(); applyPipSetting() })
                        }
                        Text(if (Settings.canDrawOverlays(this@MainActivity)) "Al ir a Inicio, la letra queda en una burbuja sobre las demás apps (como Messenger) y se cierra sola al volver a Q Lyrics. También la abres con el botón de la pestaña Letra." else "Para la burbuja hace falta el permiso «Mostrar sobre otras apps»: pulsa el botón de abajo para darlo. Mientras tanto se usa la ventana pequeña (imagen en imagen).", style = small)
                        PillTonal({
                            if (!Settings.canDrawOverlays(this@MainActivity))
                                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))) // then tap the button again
                            else { FloatingService.toggle(this@MainActivity); floating = !floating }
                        }) { Text(if (floating) "Cerrar el botón con barra" else "Botón con barra sobre otras apps", fontSize = 12.sp) }
                        // look of the floating window: saved at once, the window follows by itself
                        var fAlpha by remember { mutableFloatStateOf(sp.getInt("float_alpha", 100).toFloat()) }
                        var fScale by remember { mutableFloatStateOf(sp.getFloat("float_scale", 1f)) }
                        var fWidth by remember { mutableFloatStateOf(sp.getInt("float_w", (resources.displayMetrics.widthPixels * 0.86f).toInt()).toFloat()) }
                        var fBubble by remember { mutableFloatStateOf(sp.getInt("float_bubble_dp", 60).toFloat()) }
                        var fFolded by remember { mutableStateOf(sp.getBoolean("float_min", false)) }
                        var fNext by remember { mutableStateOf(sp.getBoolean("float_next", true)) }
                        var fAuto by remember { mutableStateOf(sp.getBoolean("float_auto", true)) }
                        var fSnap by remember { mutableStateOf(sp.getBoolean("float_snap", false)) }
                        var fBar by remember { mutableStateOf(sp.getBoolean("float_bar", true)) }
                        val maxW = resources.displayMetrics.widthPixels.toFloat(); val minW = 160 * resources.displayMetrics.density
                        @Composable fun slider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
                            Text(label, Modifier.padding(top = 6.dp), fontSize = 13.sp)
                            androidx.compose.material3.Slider(value, onChange, valueRange = range)
                        }
                        Text("Aspecto de la ventana", Modifier.padding(top = 10.dp), fontWeight = FontWeight.SemiBold)
                        slider("Transparencia del fondo: ${100 - fAlpha.toInt()} %", fAlpha, 0f..100f) { fAlpha = it; sp.edit().putInt("float_alpha", it.toInt()).apply() }
                        slider("Tamaño del texto: ${(fScale * 100).toInt()} %", fScale, 0.6f..2.2f) { fScale = it; sp.edit().putFloat("float_scale", it).apply() }
                        slider("Ancho: ${(fWidth / resources.displayMetrics.density).toInt()} dp", fWidth.coerceIn(minW, maxW), minW..maxW) { fWidth = it; sp.edit().putInt("float_w", it.toInt()).apply() }
                        slider("Tamaño de la burbuja: ${fBubble.toInt()} dp", fBubble, 28f..80f) { fBubble = it; sp.edit().putInt("float_bubble_dp", it.toInt()).apply() }
                        @Composable fun toggle(label: String, on: Boolean, key: String, set: (Boolean) -> Unit) = Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f), fontSize = 13.sp)
                            androidx.compose.material3.Switch(on, { set(it); sp.edit().putBoolean(key, it).apply() })
                        }
                        toggle("Plegar a solo la burbuja (tócala para abrir)", fFolded, "float_min") { fFolded = it }
                        toggle("Barra compacta (una línea)", fBar, "float_bar") { fBar = it }
                        toggle("Mostrar la línea siguiente (modo grande)", fNext, "float_next") { fNext = it }
                        toggle("Abrir la burbuja sola al detectar música", fAuto, "float_auto") { fAuto = it }
                        toggle("Pegarse al borde al soltarla", fSnap, "float_snap") { fSnap = it }
                        Text("En la ventana, arrastra ⤡ para cambiar el ancho (izquierda/derecha) y el texto (arriba/abajo).", style = small)
                        TextButton({
                            sp.edit().remove("float_alpha").remove("float_scale").remove("float_w").remove("float_bubble_dp").remove("float_bubble").remove("float_min").remove("float_bar").remove("float_next").remove("float_snap").apply()
                            fAlpha = 100f; fScale = 1f; fWidth = resources.displayMetrics.widthPixels * 0.86f; fBubble = 46f; fFolded = false; fBar = true; fNext = true; fSnap = false
                        }) { Text("Restablecer el aspecto") }
                    }
                    QCard(Modifier.fillMaxWidth()) {
                        Text("Esta canción", fontWeight = FontWeight.Bold)
                        Text(state.track?.let { "${it.title} — ${it.artist}" } ?: "Pon una canción en tu app de música.", Modifier.padding(top = 4.dp))
                        Text(when (state.status) {
                            Status.NO_ACCESS -> "Falta el acceso a notificaciones para saber qué suena."
                            Status.LOADING -> "Buscando la letra…"
                            Status.NOT_FOUND -> "No hay letra para esta canción."
                            Status.ERROR -> "No se pudo cargar la letra (${Tracker.lastError}). Se reintenta sola."
                            Status.PLAIN -> "Solo hay letra sin sincronizar." + (state.source?.let { " (de $it)" } ?: "")
                            Status.SYNCED -> "Letra sincronizada." + (state.source?.let { " (de $it)" } ?: "")
                            else -> ""
                        }, Modifier.padding(top = 4.dp), style = small)
                        if (state.status == Status.NO_ACCESS) PillButton({ startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, Modifier.padding(top = 8.dp)) { Text("Dar acceso") }
                        SuggestionBanner(state.track)
                        if (state.status == Status.SYNCED || state.status == Status.PLAIN) PillTonal({ editing = true }, Modifier.padding(top = 8.dp)) { Text("Editar la letra (palabras)", fontSize = 12.sp) }
                        if (whisperOk && (state.status == Status.SYNCED || state.status == Status.PLAIN)) PillTonal({ Tracker.timeFromWhisper() }, Modifier.padding(top = 8.dp)) { Text("Resincronizar con la voz (Whisper)", fontSize = 12.sp) }
                        if (isSaved) Row(Modifier.padding(top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Letra guardada en el teléfono: no se vuelve a buscar.", Modifier.weight(1f), style = small)
                            TextButton({ Tracker.forgetAndSearch() }) { Text("Buscar de nuevo") }
                        }
                    }
                    QCard(Modifier.fillMaxWidth()) {
                        Text("Letras del teléfono", fontWeight = FontWeight.Bold)
                        Text("Baja de una vez las letras de toda tu música. Para tu biblioteca de Qobuz: en QBDLX Mobile → Ajustes → «Enviar lista de canciones a Q Lyrics».", Modifier.padding(vertical = 6.dp), style = small)
                        if (!pre.running) PillTonal({ fromPhone() }, Modifier.fillMaxWidth()) { Text("Bajar letras de la música del teléfono", fontSize = 12.sp) }
                        else Text("Descargando… mira el avance arriba.", style = small)
                    }
                }
            } else if (state.status == Status.NO_ACCESS) {
                Text("Para saber qué canción suena, la app necesita «Acceso a notificaciones». Actívalo para Q Lyrics (no lee tus notificaciones, solo la canción en reproducción).")
                Button({ startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Dar acceso") }
            } else {
                state.track?.let { Text("${it.title} — ${it.artist}", fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.basicMarquee()) }
                    ?: Text("Pon una canción en tu app de música.")
                if (state.status == Status.LOADING) Text("Buscando la letra…")
                if (state.status == Status.NOT_FOUND) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("No hay letra para esta canción (no se vuelve a buscar en 2 días).", Modifier.weight(1f))
                    TextButton({ Tracker.forgetAndSearch() }) { Text("Buscar de nuevo") }
                }
                if (state.status == Status.ERROR) Text("No se pudo cargar la letra (${Tracker.lastError}). Se reintenta sola.")
                if (state.status == Status.PLAIN) Text(
                    if (tagging) "Pulsa el botón justo cuando empiece a cantarse la línea resaltada." else "Solo hay letra sin sincronizar.", fontSize = 12.sp)
                val timingMsg by Tracker.timing.collectAsState()
                val trMsg by Tracker.translating.collectAsState()
                trMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val whisperOk = remember { Whisper.available(this@MainActivity) }
                LaunchedEffect(state.track?.key) { Tracker.timing.value = null }
                timingMsg?.let { m ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(m, Modifier.weight(1f), fontSize = 12.sp)
                        if (m.endsWith("…") || m.endsWith("minutos.")) TextButton({ Tracker.cancelTiming() }) { Text("Cancelar") }
                    }
                }
                SuggestionBanner(state.track)
                VerificationBanner(state.track)
                if (state.status == Status.NOT_FOUND && whisperOk && state.track != null && timingMsg?.let { it.endsWith("…") || it.endsWith("minutos.") } != true)
                    PillButton({ Tracker.writeWithWhisper() }, Modifier.padding(top = 8.dp)) { Text("Escribir la letra con Whisper", maxLines = 1, softWrap = false) }
                if (!tools && state.status == Status.PLAIN) {
                    fun finish() {
                        // lines left unmarked follow the last mark, three seconds apart
                        val last = marks.lastOrNull() ?: 0L
                        val timed = state.lines.mapIndexed { i, l -> LyricLine(marks.getOrNull(i) ?: (last + 3000L * (i - marks.size + 1)), l.text) }
                        Tracker.saveTimed(timed); tagging = false; marks.clear()
                    }
                    if (!tagging) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PillButton({ Tracker.timeFromAudio() }, enabled = timingMsg == null || !timingMsg!!.endsWith("…")) { Text("Detectar con el audio", maxLines = 1, softWrap = false) }
                        if (whisperOk) PillButton({ Tracker.timeFromWhisper() }, enabled = timingMsg == null || !(timingMsg!!.endsWith("…") || timingMsg!!.endsWith("minutos."))) { Text("Alinear con la voz (Whisper)", maxLines = 1, softWrap = false) }
                        PillTonal({ Tracker.saveTimed(Lrc.estimate(state.lines, state.track?.durationMs ?: 0L)) }) { Text("Estimar tiempos solo", maxLines = 1, softWrap = false) }
                        PillButton({ marks.clear(); tagging = true }) { Text("Marcar mientras suena", maxLines = 1, softWrap = false) }
                    }
                    else Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton({
                            // a person taps a moment after hearing the line: 250 ms earlier
                            marks.add((Tracker.rawPositionMs() - 250).coerceAtLeast(marks.lastOrNull() ?: 0L))
                            if (marks.size >= state.lines.size) finish()
                        }, Modifier.weight(1f)) { Text("Empieza esta línea (${marks.size + 1}/${state.lines.size})") }
                        TextButton({ if (marks.isNotEmpty()) marks.removeAt(marks.size - 1) }) { Text("Deshacer") }
                        TextButton({ if (marks.isNotEmpty()) finish() else tagging = false }) { Text(if (marks.isNotEmpty()) "Guardar" else "Cancelar") }
                    }
                }
                if (!tools && state.track != null) {
                    // Two buttons that matter while listening, and the rest tucked in a menu. Lit (filled) means on or ready; grey means it cannot be pressed right now.
                    var menu by remember { mutableStateOf(false) }
                    val idle = timingMsg == null || !(timingMsg!!.endsWith("…") || timingMsg!!.endsWith("minutos."))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.status == Status.SYNCED) {
                            if (browsing) PillButton({ browsing = false }) { Text("Seguir el audio", maxLines = 1, softWrap = false) }
                            else PillButton({ browsing = true }) { Text("✓ Siguiendo el audio", maxLines = 1, softWrap = false) }
                        }
                        if (state.status == Status.SYNCED && whisperOk) PillOutlined({ Tracker.timeFromWhisper() }, enabled = idle) { Text("Resincronizar con la voz", maxLines = 1, softWrap = false) }
                        Box {
                            PillOutlined({ menu = true }) { Text("Más ▾", maxLines = 1, softWrap = false) }
                            androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                                @Composable fun item(label: String, on: Boolean = true, run: () -> Unit) = androidx.compose.material3.DropdownMenuItem({ Text(label) }, { menu = false; run() }, enabled = on)
                                if (state.status == Status.SYNCED || state.status == Status.PLAIN) item("Editar letra") { editing = true }
                                item("Elegir letra") { picking = true }
                                item("Burbuja flotante") { enterPip() }
                                if (state.status == Status.SYNCED && whisperOk) item("Verificar sincronización", idle) { Tracker.verifySync() }
                                if (state.status == Status.SYNCED) item("Informar mala sincronización") {
                                    val checking = Tracker.reportBadSync()
                                    android.widget.Toast.makeText(this@MainActivity, if (checking) "Informe guardado. Compruebo la letra con la voz…" else "Informe guardado. Elige otra letra de la lista.", android.widget.Toast.LENGTH_LONG).show()
                                    if (!checking) picking = true
                                }
                            }
                        }
                    }
                }
                if (!tools) BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // a third of the height as padding above, so the line being sung sits in the first third however many lines fit
                    val pad = if (state.status == Status.SYNCED || tagging) (maxHeight / 3 - 24.dp).coerceAtLeast(0.dp) else 12.dp
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
                            val scale by animateFloatAsState(if (on) 1.2f else 1f, androidx.compose.animation.core.spring(dampingRatio = .7f, stiffness = 380f), label = "scale")
                            val nudge by animateFloatAsState(if (on) 6f else 0f, tween(ms, easing = UiEaseOut), label = "nudge")
                            val color by animateColorAsState(
                                if (on) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = when (dist) { 1 -> .8f; 2 -> .6f; else -> .45f }),
                                tween(ms, easing = UiEaseOut), label = "color",
                            )
                            val lit = if (on && Tracker.karaoke && state.status == Status.SYNCED) Lrc.litChars(l.text, l.timeMs, state.lines.getOrNull(i + 1)?.timeMs, pos, l.endMs) else 0
                            val shown = if (lit > 0) androidx.compose.ui.text.buildAnnotatedString {
                                append(l.text)
                                addStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurface), 0, lit)
                                addStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary), lit, l.text.length)
                            } else androidx.compose.ui.text.AnnotatedString(l.text.ifBlank { "♪" })
                            androidx.compose.foundation.layout.Column {
                            Text(shown, fontSize = 22.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = color,
                                modifier = Modifier.fillMaxWidth(.83f)
                                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = nudge * density; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f) }
                                    .combinedClickable(enabled = state.status == Status.SYNCED, onClick = {
                                        // jump the playing app to this line (undo the manual sync offset first)
                                        browsing = false
                                        Tracker.controls()?.seekTo((l.timeMs - Tracker.offsetMs).coerceAtLeast(0))
                                    }, onLongClick = {
                                        // "this is the line being sung right now": the offset that makes the clock agree with it
                                        setOffset((l.timeMs - (Tracker.positionMs() - Tracker.offsetMs)).coerceIn(-10_000, 10_000))
                                        browsing = false
                                    }).padding(vertical = 6.dp))
                            val trLine = if (Tracker.translate) translations[i] else null
                            if (trLine != null) Text(trLine, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (on) 1f else .7f), modifier = Modifier.fillMaxWidth(.83f).padding(bottom = 4.dp))
                            // A pause of 4 s or more after a sung line (the line is taken as sung at ~100 ms a character, at least 1.5 s): the same three dots as before the first line.
                            val nextT = state.lines.getOrNull(i + 1)?.timeMs
                            if (state.status == Status.SYNCED && nextT != null && l.text.isNotBlank()) {
                                // with Whisper's end for the line, the pause starts when the voice really stops; otherwise it is estimated from the text
                                val realEnd = l.endMs?.takeIf { it > l.timeMs + 300 && it <= nextT }
                                val gapStart = realEnd ?: (l.timeMs + (l.text.length * 100L).coerceAtLeast(1500L).coerceAtMost(span))
                                val gap = nextT - gapStart
                                if (gap >= (if (realEnd != null) 4000 else 4000)) BreathDots(if (pos >= gapStart) ((pos - gapStart).toFloat() / gap).coerceIn(0f, 1f) else 0f, pos in gapStart until nextT, Modifier.padding(vertical = 10.dp))
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}
