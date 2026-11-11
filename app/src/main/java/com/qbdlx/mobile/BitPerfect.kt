package com.qbdlx.mobile

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build

/**
 * Bit-perfect output to a USB DAC (Android 14+): the system mixer is asked to run at exactly the format the player
 * writes, so the samples reach the DAC without being resampled or having their bit depth changed.
 *
 * Android only does this when (1) the app names a mixer format the DAC offers in bit-perfect mode and (2) the
 * AudioTrack is then created with that same format. The player decides its format by itself, so the order is:
 * it creates the track, [BitPerfectOutput.prepare] compares the real format with what the DAC offers, and when the
 * mixer had to change the engine restarts the track once so it is created again under the new mixer.
 */

/** One sample format a USB output offers in bit-perfect mode. */
data class MixerFormat(val sampleRate: Int, val encoding: Int, val channelMask: Int)

/** Bits per sample of an Android PCM encoding (0 = not PCM). Float counts as 32. */
fun encodingBits(encoding: Int): Int = when (encoding) {
    AudioFormat.ENCODING_PCM_8BIT -> 8
    AudioFormat.ENCODING_PCM_16BIT -> 16
    AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
    AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
    else -> 0
}

/** The offered format identical to what the player writes (same rate, same encoding, same channel count), or null. */
fun chooseMixer(offered: List<MixerFormat>, rate: Int, encoding: Int, channelMask: Int): MixerFormat? =
    offered.firstOrNull {
        it.sampleRate == rate && it.encoding == encoding && Integer.bitCount(it.channelMask) == Integer.bitCount(channelMask)
    }

enum class BitPerfectKind {
    /** The switch is off. */ OFF,
    /** Android older than 14: the system cannot do it. */ UNSUPPORTED,
    /** No USB DAC connected. */ NO_USB,
    /** A USB DAC is connected; nothing has played yet. */ WAITING,
    /** The mixer runs at the song's own format. */ ACTIVE,
    /** The DAC offers no bit-perfect mode for this song's format. */ NO_MATCH,
    /** Android refused to set the mixer format. */ REFUSED,
}

data class BitPerfectState(val kind: BitPerfectKind, val device: String = "", val sampleRate: Int = 0, val bits: Int = 0)

/** Last things the output saw, for [diagnoseUsb]. */
object BitPerfectLog {
    @Volatile var lastTrack = "(todavía no se ha creado ninguna pista con el modo activo)"
    @Volatile var lastResult = "(sin intentos)"
}

private fun typeName(t: Int) = when (t) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "BUILTIN_EARPIECE"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
    AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB_ACCESSORY"
    else -> "tipo $t"
}

private fun encName(e: Int) = when (e) {
    AudioFormat.ENCODING_PCM_8BIT -> "PCM8"
    AudioFormat.ENCODING_PCM_16BIT -> "PCM16"
    AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM24"
    AudioFormat.ENCODING_PCM_32BIT -> "PCM32"
    AudioFormat.ENCODING_PCM_FLOAT -> "FLOAT"
    else -> "enc$e"
}

/** A plain-text report of everything Android says about the outputs, to find out why bit-perfect does or doesn't work. */
@android.annotation.SuppressLint("NewApi")
fun diagnoseUsb(context: Context): String {
    val am = context.getSystemService(AudioManager::class.java)
    val sb = StringBuilder()
    sb.appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${phoneName(context)} (${Build.MODEL})${usbDacLabel(context)?.let { " · DAC $it" } ?: ""}")
    runCatching { sb.appendLine("App ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}") }
    if (Build.VERSION.SDK_INT < 34) sb.appendLine("API < 34: Android no tiene salida bit-perfect.")
    val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    sb.appendLine("Salidas (${outs.size}):")
    for (d in outs) {
        val isUsb = d.type == AudioDeviceInfo.TYPE_USB_DEVICE || d.type == AudioDeviceInfo.TYPE_USB_HEADSET
        sb.appendLine("- ${typeName(d.type)} «${d.productName}» id=${d.id}${if (isUsb) "  ← USB" else ""}")
        if (isUsb || d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
            sb.appendLine("    frecuencias: ${d.sampleRates.joinToString().ifEmpty { "(cualquiera)" }}")
            sb.appendLine("    codificaciones: ${d.encodings.joinToString { encName(it) }.ifEmpty { "(ninguna)" }}")
            sb.appendLine("    canales: ${d.channelCounts.joinToString().ifEmpty { "(?)" }}")
        }
        if (isUsb && Build.VERSION.SDK_INT >= 34) {
            val sup = runCatching { am.getSupportedMixerAttributes(d) }
            sup.onFailure { sb.appendLine("    mezclador: error ${it.javaClass.simpleName}: ${it.message}") }
            sup.getOrNull()?.let { list ->
                if (list.isEmpty()) sb.appendLine("    mezclador: el DAC NO ofrece ningún modo (ni bit-perfect ni por defecto)")
                for (m in list) {
                    val beh = if (m.mixerBehavior == android.media.AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT) "BIT-PERFECT" else "por defecto"
                    sb.appendLine("    mezclador $beh: ${m.format.sampleRate} Hz · ${encName(m.format.encoding)} · ${Integer.bitCount(m.format.channelMask)} canales")
                }
            }
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            val cur = runCatching { am.getPreferredMixerAttributes(attrs, d) }
            sb.appendLine("    mezclador fijado ahora: " + (cur.getOrNull()?.let { "${it.format.sampleRate} Hz ${encName(it.format.encoding)} (${if (it.mixerBehavior == android.media.AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT) "bit-perfect" else "defecto"})" } ?: (cur.exceptionOrNull()?.let { "error ${it.message}" } ?: "ninguno")))
        }
    }
    // What the USB bus itself sees, independent of Android's audio layer.
    runCatching {
        val usbList = context.getSystemService(android.hardware.usb.UsbManager::class.java).deviceList.values
        sb.appendLine("\nDispositivos USB conectados (${usbList.size}):")
        for (u in usbList) {
            val audio = (0 until u.interfaceCount).any { u.getInterface(it).interfaceClass == 1 }
            sb.appendLine("- ${u.productName ?: "?"} (${u.manufacturerName ?: "?"}) id=%04x:%04x%s".format(u.vendorId, u.productId, if (audio) "  ← clase audio" else ""))
        }
        if (usbList.isEmpty()) sb.appendLine("  ninguno: el teléfono no detecta nada por USB (cable/adaptador sin datos, o el DAC no está en modo USB)")
    }
    if (outs.none { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET })
        sb.appendLine("\n⚠ Android no ve ningún DAC USB como salida de audio (revisa cable, adaptador y el modo USB del BTR5).")
    if (Build.VERSION.SDK_INT >= 33) {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        runCatching { am.getDirectProfilesForAttributes(attrs) }.getOrNull()?.let { l ->
            sb.appendLine("\nSalida directa (offload/direct) disponible: ${l.size} perfil(es)")
            for (f in l) sb.appendLine("  ${encName(f.format)} · ${f.sampleRates.joinToString()} Hz · máscaras de canal ${f.channelMasks.size}")
        }
    }
    sb.appendLine("\nÚltima pista creada: ${BitPerfectLog.lastTrack}")
    sb.appendLine("Último intento: ${BitPerfectLog.lastResult}")
    return sb.toString()
}

class BitPerfectOutput(context: Context) {
    class Result(val state: BitPerfectState, /** The mixer format changed: the track must be created again. */ val changed: Boolean)

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var device: AudioDeviceInfo? = null
    private var applied: MixerFormat? = null

    private fun usb(): List<AudioDeviceInfo> = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY }

    private fun name(d: AudioDeviceInfo) = d.productName?.toString().orEmpty()

    /** What can be said without playing anything: is it possible, and is a DAC connected? */
    fun probe(): BitPerfectState {
        if (Build.VERSION.SDK_INT < 34) return BitPerfectState(BitPerfectKind.UNSUPPORTED)
        val d = usb().firstOrNull() ?: return BitPerfectState(BitPerfectKind.NO_USB)
        return BitPerfectState(BitPerfectKind.WAITING, name(d))
    }

    /** Sets the mixer to the format the player is writing, if a connected USB DAC offers it in bit-perfect mode. */
    fun prepare(rate: Int, encoding: Int, channelMask: Int): Result {
        if (Build.VERSION.SDK_INT < 34) return Result(BitPerfectState(BitPerfectKind.UNSUPPORTED), false)
        val devices = usb()
        if (devices.isEmpty()) { clear(); return Result(BitPerfectState(BitPerfectKind.NO_USB), false) }
        val bits = encodingBits(encoding)
        BitPerfectLog.lastTrack = "$rate Hz · ${encName(encoding)} · ${Integer.bitCount(channelMask)} canales"
        for (d in devices) {
            val offered = bitPerfectModes(d)
            val wanted = chooseMixer(offered.map { it.first }, rate, encoding, channelMask) ?: continue
            if (applied == wanted && device?.id == d.id) return Result(BitPerfectState(BitPerfectKind.ACTIVE, name(d), rate, bits), false)
            val mode = offered.first { it.first == wanted }.second
            val set = runCatching { setMixer(d, mode) }
            val ok = set.getOrDefault(false)
            BitPerfectLog.lastResult = "setPreferredMixerAttributes en ${name(d)} → " + (if (ok) "aceptado" else set.exceptionOrNull()?.let { "excepción ${it.javaClass.simpleName}: ${it.message}" } ?: "rechazado (devolvió false)")
            if (!ok) return Result(BitPerfectState(BitPerfectKind.REFUSED, name(d), rate, bits), false)
            device = d; applied = wanted
            return Result(BitPerfectState(BitPerfectKind.ACTIVE, name(d), rate, bits), true)
        }
        BitPerfectLog.lastResult = "el DAC no ofrece bit-perfect idéntico a la pista"
        return Result(BitPerfectState(BitPerfectKind.NO_MATCH, name(devices.first()), rate, bits), false)
    }

    /** Gives the mixer back to the system. */
    fun clear() {
        val d = device
        device = null; applied = null
        if (d != null && Build.VERSION.SDK_INT >= 34) runCatching { audioManager.clearPreferredMixerAttributes(attrs, d) }
    }

    @android.annotation.SuppressLint("NewApi")
    private fun bitPerfectModes(d: AudioDeviceInfo): List<Pair<MixerFormat, android.media.AudioMixerAttributes>> =
        runCatching {
            audioManager.getSupportedMixerAttributes(d)
                .filter { it.mixerBehavior == android.media.AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
                .map { MixerFormat(it.format.sampleRate, it.format.encoding, it.format.channelMask) to it }
        }.getOrDefault(emptyList())

    @android.annotation.SuppressLint("NewApi")
    private fun setMixer(d: AudioDeviceInfo, mode: android.media.AudioMixerAttributes): Boolean =
        audioManager.setPreferredMixerAttributes(attrs, d, mode)
}

/** The name the user gave the phone in Settings (falls back to the model). */
fun phoneName(context: Context): String =
    runCatching {
        android.provider.Settings.Global.getString(context.contentResolver, "device_name")
            ?: android.provider.Settings.Secure.getString(context.contentResolver, "bluetooth_name")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: Build.MODEL

/** "FiiO BTR5 · 2972:0047" for the connected USB audio DAC, or null when there is none. */
fun usbDacLabel(context: Context): String? = runCatching {
    val usb = context.getSystemService(android.hardware.usb.UsbManager::class.java).deviceList.values.firstOrNull { u ->
        (0 until u.interfaceCount).any { u.getInterface(it).interfaceClass == 1 }
    } ?: return@runCatching null
    "%s · %04x:%04x".format(usb.productName ?: "USB DAC", usb.vendorId, usb.productId)
}.getOrNull()
