package com.qbdlx.mobile

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DiagnosticsScreen(vm: MainViewModel) {
    val s = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    BackHandler { vm.closeDiagnostics() }

    // Device/app header: version lets the user confirm the new build is installed; the rest helps triage.
    val header = remember {
        val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        "QBDLX Mobile ${pkg?.versionName ?: "?"} (build ${pkg?.longVersionCode ?: "?"})\n" +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"
    }

    val entries = Diagnostics.entries

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = LocalBottomInset.current)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp)) {
            IconButton({ vm.closeDiagnostics() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back) }
            Text(s.diagTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            s.diagIntro,
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.refreshQobuzCredentials() }, enabled = !vm.busy) {
                if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(s.updateCreds)
            }
            OutlinedButton({ vm.runDiagnosticTest() }, enabled = !vm.busy) { Text(s.diagRunTest) }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val text = header + "\n\n" + Diagnostics.dump().ifBlank { s.diagEmpty }
                clipboard.setText(AnnotatedString(text))
                vm.notice = s.diagCopied
            }) {
                Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.diagCopy)
            }
            OutlinedButton(onClick = { Diagnostics.clear(); vm.notice = s.diagCleared }) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.diagClear)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(s.diagEmpty, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SelectionContainer {
                        Text(header, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Newest first so the latest attempt is on top.
                items(entries.size) { i ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f), shape = RoundedCornerShape(10.dp)) {
                        SelectionContainer {
                            Text(entries[entries.size - 1 - i], Modifier.padding(12.dp), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}
