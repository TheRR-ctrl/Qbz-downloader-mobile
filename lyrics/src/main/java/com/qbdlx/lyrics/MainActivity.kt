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

    private fun pipParams(auto: Boolean) = android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(239, 100)).also {
        if (android.os.Build.VERSION.SDK_INT >= 31) it.setAutoEnterEnabled(auto)
    }.build()

    private fun enterPip() { runCatching { enterPictureInPictureMode(pipParams(false)) } }

    /** Leaving the app (Home) keeps the lyrics in a small window when the setting is on: automatically on Android 12+, from here before. */
    fun applyPipSetting() { if (android.os.Build.VERSION.SDK_INT >= 31) runCatching { setPictureInPictureParams(pipParams(pipOn())) } }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT < 31 && pipOn() && Tracker.state.value.track != null) enterPip()
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig); inPip = isInPictureInPictureMode
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

    @Composable
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    private fun Screen(offset: Long, setOffset: (Long) -> Unit) {
        val state by Tracker.state.collectAsState()
        var pos by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) { while (true) { pos = Tracker.positionMs(); delay(200) } }
        // Tap-timing of a lyric that has no times: one tap per line as it starts to be sung.
        var tagging by remember { mutableStateOf(false) }
        val marks = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
        LaunchedEffect(state.track?.key, state.status) { tagging = false; marks.clear() }
        val idx = if (state.status == Status.SYNCED) Lrc.indexAt(state.lines, pos)
            else if (tagging && state.lines.isNotEmpty()) marks.size.coerceAtMost(state.lines.size - 1) else -1
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
                        Text("Si la letra va adelantada o atrasada, ajústala aquí (pasos de 250 ms). Más rápido: en la pestaña Letra, mantén pulsada la línea que está sonando.", style = small)
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Relleno tipo karaoke", Modifier.weight(1f))
                            androidx.compose.material3.Switch(karaoke, { karaoke = it; Tracker.karaoke = it; sp.edit().putBoolean("karaoke", it).apply() })
                        }
                        Text("Pinta la línea palabra por palabra mientras se canta (aproximado).", style = small)
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
                            Text("Abrirla sola al salir de la app", Modifier.weight(1f))
                            androidx.compose.material3.Switch(pipAuto, { pipAuto = it; sp.edit().putBoolean("pip_on_leave", it).apply(); applyPipSetting() })
                        }
                        Text("Al ir a Inicio, la letra queda en una ventanita (imagen en imagen). También la abres con el botón de la pestaña Letra.", style = small)
                        PillTonal({
                            if (!Settings.canDrawOverlays(this@MainActivity))
                                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))) // then tap the button again
                            else { FloatingService.toggle(this@MainActivity); floating = !floating }
                        }) { Text(if (floating) "Cerrar el botón con barra" else "Botón con barra sobre otras apps", fontSize = 12.sp) }
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
                if (state.status == Status.NOT_FOUND) Text("No hay letra para esta canción.")
                if (state.status == Status.ERROR) Text("No se pudo cargar la letra (${Tracker.lastError}). Se reintenta sola.")
                if (state.status == Status.PLAIN) Text(
                    if (tagging) "Pulsa el botón justo cuando empiece a cantarse la línea resaltada." else "Solo hay letra sin sincronizar.", fontSize = 12.sp)
                val timingMsg by Tracker.timing.collectAsState()
                val whisperOk = remember { Whisper.available(this@MainActivity) }
                LaunchedEffect(state.track?.key) { Tracker.timing.value = null }
                timingMsg?.let { m ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(m, Modifier.weight(1f), fontSize = 12.sp)
                        if (m.endsWith("…") || m.endsWith("minutos.")) TextButton({ Tracker.cancelTiming() }) { Text("Cancelar") }
                    }
                }
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
                if (!tools && state.track != null) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Follow the audio again after browsing; while following it shows the state instead.
                    if (state.status == Status.SYNCED && whisperOk) PillTonal({ Tracker.timeFromWhisper() }, enabled = timingMsg == null || !(timingMsg!!.endsWith("…") || timingMsg!!.endsWith("minutos."))) { Text("Resincronizar con la voz", maxLines = 1, softWrap = false) }
                    if (state.status == Status.SYNCED) { if (browsing) PillButton({ browsing = false }) { Text("Seguir audio", maxLines = 1, softWrap = false) } else PillTonal({ browsing = true }) { Text("Siguiendo el audio ✓", maxLines = 1, softWrap = false) } }
                    PillTonal({ enterPip() }) { Text("Ventana flotante", maxLines = 1, softWrap = false) }
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
                            val scale by animateFloatAsState(if (on) 1.2f else 1f, tween(ms, easing = UiEaseOut), label = "scale")
                            val color by animateColorAsState(
                                if (on) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = when (dist) { 1 -> .8f; 2 -> .6f; else -> .45f }),
                                tween(ms, easing = UiEaseOut), label = "color",
                            )
                            val lit = if (on && Tracker.karaoke && state.status == Status.SYNCED) Lrc.litChars(l.text, l.timeMs, state.lines.getOrNull(i + 1)?.timeMs, pos) else 0
                            val shown = if (lit > 0) androidx.compose.ui.text.buildAnnotatedString {
                                append(l.text)
                                addStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary), 0, lit)
                                addStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurface), lit, l.text.length)
                            } else androidx.compose.ui.text.AnnotatedString(l.text.ifBlank { "♪" })
                            Text(shown, fontSize = 22.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = color,
                                modifier = Modifier.fillMaxWidth(.83f)
                                    .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f) }
                                    .combinedClickable(enabled = state.status == Status.SYNCED, onClick = {
                                        // jump the playing app to this line (undo the manual sync offset first)
                                        browsing = false
                                        Tracker.controls()?.seekTo((l.timeMs - Tracker.offsetMs).coerceAtLeast(0))
                                    }, onLongClick = {
                                        // "this is the line being sung right now": the offset that makes the clock agree with it
                                        setOffset((l.timeMs - (Tracker.positionMs() - Tracker.offsetMs)).coerceIn(-10_000, 10_000))
                                        browsing = false
                                    }).padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
