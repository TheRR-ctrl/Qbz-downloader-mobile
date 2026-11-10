package com.qbdlx.mobile

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ostrichbat2k.usbdac.UsbAudioManager

/** Un paso de la ruta de la señal: qué es, cómo va, y si conserva la señal tal cual (verde), la cambia (ámbar) o no se sabe (gris). */
data class SignalStep(val title: String, val detail: String, val state: SignalState)
enum class SignalState { Intacta, Cambiada, Desconocida }

private fun khz(hz: Int) = if (hz % 1000 == 0) "${hz / 1000} kHz" else "%.1f kHz".format(java.util.Locale.ROOT, hz / 1000.0)

private fun pcmName(enc: Int): String = when (enc) {
    android.media.AudioFormat.ENCODING_PCM_16BIT -> "PCM 16 bits"
    android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM 24 bits"
    android.media.AudioFormat.ENCODING_PCM_32BIT -> "PCM 32 bits"
    android.media.AudioFormat.ENCODING_PCM_FLOAT -> "PCM flotante 32 bits"
    else -> "PCM"
}

/** La salida que usa Android ahora para la música, si no es la del controlador USB propio. */
private fun androidOutput(context: Context): AudioDeviceInfo? {
    val am = context.getSystemService(AudioManager::class.java) ?: return null
    val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val prefer = listOf(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET)
    return prefer.firstNotNullOfOrNull { t -> outs.firstOrNull { it.type == t } } ?: outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
}

/** Arma la ruta de la señal de lo que suena: archivo → decodificador → salida → aparato. */
fun signalPath(context: Context, vm: MainViewModel): List<SignalStep> {
    val steps = mutableListOf<SignalStep>()
    val q = vm.nowQuality
    steps += SignalStep("Origen", when {
        q == null -> "Calidad aún desconocida"
        q.mp3 -> "MP3 (con pérdida)"
        else -> "FLAC ${q.bitDepth} bits / ${khz(q.sampleRate)}"
    }, if (q == null) SignalState.Desconocida else if (q.mp3) SignalState.Cambiada else SignalState.Intacta)

    val dec = vm.engine.decodedFormat()
    steps += SignalStep("Decodificador", dec?.let { "${pcmName(it.second)} · ${khz(it.first)}" } ?: "Sin datos",
        when {
            dec == null || q == null -> SignalState.Desconocida
            dec.first != q.sampleRate -> SignalState.Cambiada
            else -> SignalState.Intacta
        })

    val rg = org.ostrichbat2k.usbdac.streaming.UsbTelemetry.gananciaReplay
    if (vm.replayGain && rg != 1f) steps += SignalStep("ReplayGain", "%+.1f dB para igualar el volumen".format(java.util.Locale.ROOT, 20 * kotlin.math.log10(rg.toDouble())), SignalState.Cambiada)
    val vs = org.ostrichbat2k.usbdac.streaming.UsbTelemetry.volumenSoftware
    if (vs < 0.999f) steps += SignalStep("Volumen digital", "${(vs * 100).toInt()} %", SignalState.Cambiada)
    val route = if (UsbDirect.prefs(context).enabled) UsbDirect.managerOrNull(context)?.playbackRoute?.value else null
    when (route) {
        is UsbAudioManager.PlaybackRoute.Direct -> {
            val exacta = q != null && !q.mp3 && route.sampleRateHz == q.sampleRate && (route.bitDepth ?: 0) >= q.bitDepth
            steps += SignalStep("Controlador USB propio",
                "Directo al DAC, sin el mezclador de Android · ${route.bitDepth ?: "?"} bits / ${khz(route.sampleRateHz)}",
                if (exacta) SignalState.Intacta else SignalState.Cambiada)
            steps += SignalStep("DAC", usbDacLabel(context) ?: "DAC USB", if (exacta) SignalState.Intacta else SignalState.Cambiada)
        }
        else -> {
            val dev = androidOutput(context)
            val bt = dev?.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || dev?.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            steps += SignalStep("Mezclador de Android",
                if (bt) "Android mezcla y remuestrea la señal antes de enviarla" else "Android puede remuestrear la señal" + (if (vm.bitPerfect) " (bit-perfect pedido a Android)" else ""),
                if (vm.bitPerfect && !bt) SignalState.Desconocida else SignalState.Cambiada)
            steps += SignalStep(
                if (bt) "Bluetooth" else "Salida",
                when {
                    bt -> "${dev?.productName ?: "Auriculares"} · el códec (aptX, aptX HD, LDAC, AAC o SBC) lo elige Android con el auricular; se ve en Opciones de desarrollador"
                    dev != null -> dev.productName?.toString()?.ifBlank { null } ?: "Altavoz del teléfono"
                    else -> "Desconocida"
                },
                if (bt) SignalState.Cambiada else SignalState.Desconocida)
        }
    }
    return steps
}

@Composable
fun SignalPathDialog(vm: MainViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val steps = signalPath(ctx, vm)
    val intacta = steps.all { it.state == SignalState.Intacta }
    val bt = steps.any { it.title == "Bluetooth" }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Ruta de la señal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (intacta) "Bit-perfect: la canción llega al DAC tal cual." else "La señal se convierte en algún paso (en ámbar).",
                    fontWeight = FontWeight.SemiBold,
                    color = if (intacta) Color(0xFF4CD07D) else Color(0xFFFFB74D),
                )
                steps.forEachIndexed { i, st ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(
                            when (st.state) { SignalState.Intacta -> Color(0xFF4CD07D); SignalState.Cambiada -> Color(0xFFFFB74D); SignalState.Desconocida -> Color(0xFF8A8A8A) }))
                        Column(Modifier.fillMaxWidth()) {
                            Text("${i + 1}. ${st.title}", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(st.detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Cerrar") } },
        dismissButton = if (vm.downloadedQualityOfCurrent().let { it != null && it != Quality.HI_RES_MAX }) ({
            TextButton({ vm.upgradeCurrentDownload(); onClose() }) { Text("Mejorar calidad") }
        }) else if (bt) ({
            TextButton({
                runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { vm.notice = "Activa las Opciones de desarrollador para ver el códec Bluetooth" }
            }) { Text("Ver códec") }
        }) else null,
        shape = RoundedCornerShape(20.dp),
    )
}

/** «Hasta 32-Bit - 384 kHz» para la salida USB, sacado del DAC y de lo que Android anuncia de él. */
fun usbCapacity(context: Context): String? {
    val am = context.getSystemService(AudioManager::class.java) ?: return null
    val usb = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
        it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
    } ?: return null
    val bits = UsbDirect.managerOrNull(context)?.controlState?.value?.formats?.maxOfOrNull { it.bitResolution }?.takeIf { it > 0 }
        ?: if (usb.encodings.any { it == android.media.AudioFormat.ENCODING_PCM_32BIT || it == android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED }) 24 else 16
    val rate = usb.sampleRates.maxOrNull() ?: return "Hasta $bits-Bit"
    return "Hasta $bits-Bit - ${khz(rate)}"
}

/** La hoja de «Selecciona tu salida de audio», como la de Qobuz: el aparato, su capacidad, el volumen y la ruta de la señal. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OutputSheet(vm: MainViewModel, onSignalPath: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val am = remember(ctx) { ctx.getSystemService(AudioManager::class.java) }
    val manager = if (UsbDirect.prefs(ctx).enabled) UsbDirect.managerOrNull(ctx) else null
    val route = manager?.playbackRoute?.collectAsState()?.value
    val control = manager?.controlState?.collectAsState()?.value
    val direct = route is UsbAudioManager.PlaybackRoute.Direct
    val dev = androidOutput(ctx)
    val dac = usbDacLabel(ctx)?.substringBefore(" · ")
    val via = when {
        direct -> "USB directo / ${dac ?: "DAC USB"}"
        dev?.type == AudioDeviceInfo.TYPE_USB_HEADSET || dev?.type == AudioDeviceInfo.TYPE_USB_DEVICE -> "USB / ${dev.productName}"
        dev?.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || dev?.type == AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth / ${dev.productName}"
        else -> "Altavoz del teléfono"
    }
    val capacity = remember(dac, route) { if (dac != null) usbCapacity(ctx) else null }
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = Color(0xFF151515)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Dispositivo seleccionado", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF2B2416)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                androidx.compose.material3.Icon(Icons.Default.PhoneAndroid, null, tint = QobuzGold, modifier = Modifier.size(30.dp))
                Column {
                    Text(remember { phoneName(ctx) }, color = QobuzGold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(via, fontSize = 14.sp)
                    if (capacity != null) Text(capacity, fontSize = 14.sp)
                }
            }
            androidx.compose.material3.OutlinedButton(onClick = onSignalPath, modifier = Modifier.fillMaxWidth()) { Text("Ruta de la señal") }
            // Volumen: el del DAC si lo expone; si no, digital (deja de ser bit-perfect bajo el 100 %); con el mezclador de Android, el del sistema.
            val range = control?.volumeRange
            when {
                direct && range != null && control.volume != null -> {
                    var db by remember(control.volume) { mutableFloatStateOf(control.volume!!.toFloat()) }
                    VolumeRow("Volumen del DAC", db, range.first.toFloat()..range.second.toFloat(), { db = it }, { manager.setVolume(db.toInt()) })
                }
                direct -> {
                    var v by remember { mutableFloatStateOf(org.ostrichbat2k.usbdac.streaming.UsbTelemetry.volumenSoftware) }
                    VolumeRow(if (v >= 0.999f) "Volumen digital · 100 % (bit-perfect)" else "Volumen digital · ${(v * 100).toInt()} % (no bit-perfect)", v, 0f..1f, {
                        v = it; org.ostrichbat2k.usbdac.streaming.UsbTelemetry.volumenSoftware = it
                    }, { ctx.getSharedPreferences("qbdlx", 0).edit().putFloat("vol_software", v).apply() })
                    Text("Este DAC no deja cambiar su volumen por USB: usa sus botones para no perder bit-perfect.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                am != null -> {
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                    var v by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
                    VolumeRow("Volumen", v, 0f..max, { v = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) }, {})
                }
            }
        }
    }
}

@Composable
private fun VolumeRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Column {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.Icon(Icons.Default.VolumeUp, null)
            androidx.compose.material3.Slider(value, onChange, Modifier.weight(1f), valueRange = range, onValueChangeFinished = onDone,
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = QobuzGold))
        }
    }
}
