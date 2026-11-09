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
import androidx.compose.runtime.Composable
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
        dismissButton = if (bt) ({
            TextButton({
                runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { vm.notice = "Activa las Opciones de desarrollador para ver el códec Bluetooth" }
            }) { Text("Ver códec") }
        }) else null,
        shape = RoundedCornerShape(20.dp),
    )
}
