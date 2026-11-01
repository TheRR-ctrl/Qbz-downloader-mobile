package com.qbdlx.mobile

import androidx.compose.foundation.horizontalScroll
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.DateFormat
import java.util.Date

private enum class ShareWindow(val ms: Long) { Hour(3_600_000L), Today(86_400_000L), Week(7 * 86_400_000L), All(Long.MAX_VALUE) }

private const val GROUP_GAP_MS = 10 * 60_000L
private const val MAX_SHARE = 100
private const val DRIVE_PACKAGE = "com.google.android.apps.docs"

/** Choose finished downloads (grouped by how close in time they were saved) and send them to Google Drive's share screen. */
@Composable
fun ShareDialog(vm: MainViewModel, generic: Boolean = false, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    var window by remember { mutableStateOf(ShareWindow.Today) }
    val selected = remember { mutableStateListOf<String>() }
    var openGroup by remember { mutableStateOf<Int?>(null) }

    val done = vm.downloads.tasks.filter { it.status == TaskStatus.COMPLETED && it.savedUri != null }
    val inWindow = if (window == ShareWindow.All) done else done.filter { it.finishedAt > 0 && now - it.finishedAt <= window.ms }
    // Files saved before the time was recorded (finishedAt = 0) can only be reached under "All", as one group at the end.
    val dated = groupByGap(inWindow.filter { it.finishedAt > 0 }, GROUP_GAP_MS) { it.finishedAt }
    val undated = inWindow.filter { it.finishedAt == 0L }
    val groups = dated + listOfNotNull(undated.takeIf { it.isNotEmpty() })
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val sent = remember { mutableStateListOf<String>() }
    val chosen = inWindow.filter { it.key in selected }
    val pending = chosen.filter { it.key !in sent }

    Dialog(onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (generic) s.shareGeneric else s.shareDrive, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(s.shareHint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ShareWindow.Hour to s.shareWindowHour, ShareWindow.Today to s.shareWindowToday, ShareWindow.Week to s.shareWindowWeek, ShareWindow.All to s.shareWindowAll)
                        .forEach { (w, label) -> QChip(window == w, { window = w; selected.clear(); openGroup = null }, label = { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false) }) }
                }
                if (groups.isEmpty()) Text(s.shareNone, Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    TextButton({ if (chosen.size == inWindow.size) selected.clear() else { selected.clear(); selected.addAll(inWindow.map { it.key }) } }) { Text(s.shareAll) }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        groups.forEachIndexed { gi, group ->
                            item(key = "g$gi") {
                                val all = group.all { it.key in selected }
                                Row(Modifier.fillMaxWidth().clickable { openGroup = if (openGroup == gi) null else gi }, verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(all, { on -> group.forEach { selected.remove(it.key) }; if (on) selected.addAll(group.map { it.key }) })
                                    Column(Modifier.weight(1f)) {
                                        val first = group.last().finishedAt
                                        Text(if (first > 0) fmt.format(Date(first)) else "—", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${s.tracks(group.size)} · " + group.map { it.track.artist }.distinct().take(3).joinToString(", "),
                                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            if (openGroup == gi) items(group, key = { "t" + it.key }) { t ->
                                Row(Modifier.fillMaxWidth().padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(t.key in selected, { on -> if (on) selected.add(t.key) else selected.remove(t.key) })
                                    Text(t.track.displayTitle, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                if (sent.isNotEmpty()) Text(s.shareProgress(chosen.size - pending.size, chosen.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                else if (chosen.size > MAX_SHARE) Text(s.shareTooMany, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss) { Text(s.cancel) }
                    val batch = pending.take(MAX_SHARE)
                    Button({
                        val r = shareToDrive(context, batch.mapNotNull { it.savedUri }, s.shareDrive, chooser = generic)
                        // Only what Drive received counts as sent (a refused batch can be tried again); unreadable files are set aside.
                        sent.addAll(batch.filter { it.savedUri in r.handed || it.savedUri in r.unreadable }.map { it.key })
                        // Entries whose file is gone (deleted elsewhere) only clutter the list: forget them.
                        if (r.missing.isNotEmpty()) vm.downloads.forgetFiles(r.missing)
                        if (r.unreadable.isNotEmpty()) android.widget.Toast.makeText(context, s.shareUnreadable(r.unreadable.size), android.widget.Toast.LENGTH_LONG).show()
                        // The dialog stays open: when Drive is done, come back and send the next batch.
                        if (pending.count { it.key !in sent } == 0) onDismiss()
                    }, enabled = batch.isNotEmpty()) { Text(if (sent.isEmpty()) s.shareButton(pending.size) else s.shareNext(batch.size)) }
                }
            }
        }
    }
}

/** What happened to one batch: the files handed to Drive, and the ones this app can no longer read (moved or replaced by another app). */
internal class ShareResult(val handed: Set<String>, val unreadable: Set<String>, val missing: Set<String> = emptySet())

/** Opens Google Drive's upload screen with the files; without Drive, Android's normal share sheet. */
internal fun shareToDrive(context: android.content.Context, uris: List<String>, title: String, chooser: Boolean = false): ShareResult {
    // One file this app may not hand over (the file was moved or replaced by another app) makes Android refuse the whole batch.
    val missing = HashSet<String>()
    val (readable, unreadable) = uris.partition { u ->
        try { context.contentResolver.openAssetFileDescriptor(Uri.parse(u), "r")?.close() != null }
        catch (e: java.io.FileNotFoundException) { missing += u; false }
        catch (e: Exception) { false }
    }
    if (readable.isEmpty()) return ShareResult(emptySet(), unreadable.toSet(), missing)
    val parsed = readable.map { Uri.parse(it) }
    val send = Intent(if (parsed.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).setType("audio/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (parsed.size == 1) send.putExtra(Intent.EXTRA_STREAM, parsed[0]) else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(parsed))
    send.clipData = ClipData.newRawUri("", parsed[0]).also { clip -> parsed.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
    return try {
        try {
            if (chooser) context.startActivity(Intent.createChooser(send, title)) else context.startActivity(Intent(send).setPackage(DRIVE_PACKAGE))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(send, title))
        }
        ShareResult(readable.toSet(), unreadable.toSet(), missing)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "${e.javaClass.simpleName}: ${e.message}".take(200), android.widget.Toast.LENGTH_LONG).show()
        java.io.File(context.filesDir, QbdlxApp.CRASH_FILE).writeText("share failed (${uris.size} files)\n" + android.util.Log.getStackTraceString(e))
        ShareResult(emptySet(), unreadable.toSet(), missing)
    }
}
