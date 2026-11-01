package com.qbdlx.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

private val es = Locale.getDefault().language == "es"
private fun t(spanish: String, english: String) = if (es) spanish else english

class MainActivity : ComponentActivity() {
    private val vm: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { ScannerScreen(vm) }
            }
        }
    }
}

private fun audioPermission() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
private fun ScannerScreen(vm: ScanViewModel) {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.scan() else vm.message = "permission"
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.addTree(uri) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun start() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) // only so the scan's progress notification can show
        }
        if (ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED) vm.scan() else ask.launch(audioPermission())
    }
    val songs = vm.songs
    val progress = vm.progress

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("QBDLX Scanner", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                t(
                    "Busca las canciones de tu teléfono y crea una lista para importar en QBDLX Mobile. Si una canción no tiene nombre útil, la reconoce por su sonido.",
                    "Finds the songs on your phone and makes a list to import into QBDLX Mobile. A song without a useful name is recognised by its sound.",
                ),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(t("Lista de Spotify", "Spotify list"), fontWeight = FontWeight.SemiBold)
            Text(
                t("Pega el enlace de una playlist o álbum público de Spotify; se comparte en el mismo formato que el escaneo y QBDLX Mobile la busca en Qobuz.",
                    "Paste the link of a public Spotify playlist or album; it is shared in the same format as the scan and QBDLX Mobile looks it up on Qobuz."),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = vm.spotifyLink, onValueChange = { vm.spotifyLink = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("open.spotify.com/playlist/…") },
            )
            Button(vm::readSpotify, Modifier.fillMaxWidth(), enabled = vm.spotifyLink.isNotBlank() && !vm.spotifyBusy) {
                Text(if (vm.spotifyBusy) t("Leyendo…", "Reading…") else t("Leer lista", "Read list"))
            }
            vm.spotifyError?.let {
                Text(
                    when (it) {
                        "link" -> t("Ese texto no es un enlace de playlist o álbum de Spotify.", "That text is not a Spotify playlist or album link.")
                        "private" -> t("No pude leer la lista: debe ser pública y el enlace correcto.", "Could not read the list: it must be public and the link correct.")
                        else -> t("Sin conexión con Spotify. Revisa internet e intenta de nuevo.", "Could not reach Spotify. Check the internet and try again.")
                    },
                    color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                )
            }
            vm.spotify?.let { l ->
                Text(
                    t("${l.name}: ${l.songs.size} canciones", "${l.name}: ${l.songs.size} songs") +
                        if (l.songs.size >= 100) t(" (Spotify solo muestra las primeras 100)", " (Spotify only shows the first 100)") else "",
                    fontWeight = FontWeight.Medium,
                )
                Button({ vm.shareSpotifyIntent()?.let(context::startActivity) }, Modifier.fillMaxWidth()) { Text(t("Compartir lista", "Share list")) }
            }
            Spacer(Modifier.height(8.dp))
        }
        item {
            OutlinedTextField(
                value = vm.keyDraft, onValueChange = vm::editKey, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(t("Clave de AcoustID (gratis)", "AcoustID key (free)")) },
            )
            OutlinedButton(
                { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://acoustid.org/new-application"))) },
                Modifier.fillMaxWidth(),
            ) { Text(t("Obtener clave gratis (acoustid.org)", "Get a free key (acoustid.org)")) }
            Button(vm::saveKey, Modifier.fillMaxWidth(), enabled = vm.keyDraft.isNotBlank() && !vm.checkingKey) {
                Text(if (vm.checkingKey) t("Probando…", "Testing…") else t("Guardar y probar clave", "Save and test key"))
            }
            vm.keyStatus?.let { st ->
                val (msg, bad) = when (st) {
                    "valid" -> t("✓ Clave guardada y aceptada por AcoustID.", "✓ Key saved and accepted by AcoustID.") to false
                    "invalid" -> t("✗ AcoustID no acepta esta clave. Debe ser la «Application API key» que aparece al registrar la aplicación (no la «User API key» de tu perfil). Cópiala completa, sin espacios.", "✗ AcoustID does not accept this key. It must be the “Application API key” shown when you register the application (not the “User API key” of your profile). Copy all of it, without spaces.") to true
                    "saved-offline" -> t("Clave guardada, pero no pude probarla (sin conexión).", "Key saved, but it could not be tested (no connection).") to false
                    "empty" -> t("Escribe una clave primero.", "Type a key first.") to true
                    else -> t("Clave guardada.", "Key saved.") to false
                }
                Text(msg, fontSize = 13.sp, color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                if (vm.keyDetail.isNotBlank() && st != "saved") Text(t("AcoustID respondió: ", "AcoustID answered: ") + vm.keyDetail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                t("Se abre la página en el navegador: crea una cuenta, registra una aplicación con cualquier nombre y copia la clave aquí.",
                    "Opens the page in your browser: sign in, register an application with any name and paste the key here."),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Solo carpetas comunes", "Only common folders"), fontWeight = FontWeight.SemiBold)
                    Text(t("Música, Descargas, Documentos, Podcasts… Las grabaciones de llamadas y de voz siempre se omiten.", "Music, Downloads, Documents, Podcasts… Call and voice recordings are always skipped."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(vm.onlyCommon, vm::updateOnlyCommon)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Reconocer todas por sonido", "Recognise all by sound"), fontWeight = FontWeight.SemiBold)
                    Text(t("Más lento. Si está apagado, solo las que no tienen nombre útil.", "Slower. When off, only songs without a useful name."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(vm.allBySound, vm::updateAllBySound)
            }
        }
        item {
            Text(t("Carpetas omitidas", "Skipped folders"), fontWeight = FontWeight.SemiBold)
            Text(
                t("Las canciones de estas carpetas no se escanean (audios de WhatsApp, notas de voz…). Basta con parte del nombre de la carpeta.",
                    "Songs in these folders are not scanned (WhatsApp audio, voice notes…). Part of the folder name is enough."),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            vm.omitted.forEach { name ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton({ vm.omitRemove(name) }) { Text(t("Quitar", "Remove")) }
                }
            }
            var newFolder by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newFolder, { newFolder = it }, Modifier.weight(1f), singleLine = true, label = { Text(t("Añadir carpeta", "Add folder")) })
                TextButton({ vm.omitAdd(newFolder); newFolder = "" }, enabled = newFolder.isNotBlank()) { Text(t("Añadir", "Add")) }
            }
        }
        item {
            Text(t("Tarjeta SD u otras carpetas", "SD card or other folders"), fontWeight = FontWeight.SemiBold)
            Text(
                t("La música de la tarjeta SD suele aparecer sola. Si no sale, elige aquí la tarjeta (o una carpeta) y se leerá directamente.",
                    "Music on the SD card normally shows up by itself. If it does not, pick the card (or a folder) here and it is read directly."),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            vm.trees.forEach { u ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Uri.decode(u.substringAfterLast("/tree/")), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton({ vm.removeTree(u) }) { Text(t("Quitar", "Remove")) }
                }
            }
            OutlinedButton({ pickTree.launch(null) }, Modifier.fillMaxWidth()) { Text(t("Elegir tarjeta SD o carpeta", "Pick SD card or folder")) }
        }
        item {
            if (progress != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (progress.total > 0) LinearProgressIndicator({ progress.done / progress.total.toFloat() }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("${progress.done}/${progress.total}  ${progress.current}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.basicMarquee())
                    OutlinedButton(vm::cancel) { Text(t("Cancelar", "Cancel")) }
                }
            } else {
                if (vm.resumable) {
                    Text(t("Un escaneo quedó a medias: ${songs?.size ?: 0} canciones ya están guardadas.", "A scan was left unfinished: ${songs?.size ?: 0} songs are already saved."), fontSize = 13.sp)
                    Button(::start, Modifier.fillMaxWidth()) { Text(t("Continuar escaneo", "Continue scan")) }
                    TextButton(vm::startOver) { Text(t("Empezar de cero", "Start over")) }
                } else Button(::start, Modifier.fillMaxWidth()) { Text(if (songs == null) t("Escanear canciones", "Scan songs") else t("Escanear de nuevo", "Scan again")) }
            }
        }
        vm.message?.let { m ->
            item {
                val text = when {
                    m == "permission" -> t("Sin permiso para leer la música. Actívalo en Ajustes > Apps > QBDLX Scanner > Permisos.", "No permission to read music. Turn it on in Settings > Apps > QBDLX Scanner > Permissions.")
                    m.startsWith("acoustid:") -> t("AcoustID rechazó la clave (${m.removePrefix("acoustid:")}). Las canciones sin nombre quedan sin reconocer.", "AcoustID rejected the key (${m.removePrefix("acoustid:")}). Songs without a name stay unrecognised.")
                    else -> m.removePrefix("error:")
                }
                Text(text, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                if (m == "permission") TextButton({ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text(t("Abrir ajustes", "Open settings")) }
            }
        }
        if (songs != null) {
            item {
                val found = songs.count { it.source != Source.Unknown }
                val bySound = songs.count { it.source == Source.AcoustId }
                Text(
                    t("${songs.size} canciones · $found con nombre ($bySound reconocidas por sonido) · ${songs.size - found} sin identificar", "${songs.size} songs · $found named ($bySound recognised by sound) · ${songs.size - found} unidentified"),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Button({ vm.shareIntent()?.let(context::startActivity) }, Modifier.fillMaxWidth(), enabled = songs.isNotEmpty()) { Text(t("Compartir lista", "Share list")) }
            }
            // No key: the same file name can exist in several folders, and a repeated key crashes a LazyColumn.
            items(songs) { s ->
                Column {
                    Text(s.title.ifBlank { s.file }, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(s.artist, s.album).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { t("sin identificar", "unidentified") } +
                            when (s.source) { Source.AcoustId -> "  ·  🎧 ${(s.confidence * 100).toInt()}%"; Source.Unknown -> "  ·  ?"; else -> "" },
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (s.folder.isNotBlank()) {
                        val short = OmittedFolders.shortName(s.folder)
                        TextButton({ vm.omitAdd(short) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text(t("Omitir carpeta «$short»", "Skip folder “$short”"), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
