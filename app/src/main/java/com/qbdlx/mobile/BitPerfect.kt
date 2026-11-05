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

class BitPerfectOutput(context: Context) {
    class Result(val state: BitPerfectState, /** The mixer format changed: the track must be created again. */ val changed: Boolean)

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var device: AudioDeviceInfo? = null
    private var applied: MixerFormat? = null

    private fun usb(): List<AudioDeviceInfo> = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }

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
        for (d in devices) {
            val offered = bitPerfectModes(d)
            val wanted = chooseMixer(offered.map { it.first }, rate, encoding, channelMask) ?: continue
            if (applied == wanted && device?.id == d.id) return Result(BitPerfectState(BitPerfectKind.ACTIVE, name(d), rate, bits), false)
            val mode = offered.first { it.first == wanted }.second
            val ok = runCatching { setMixer(d, mode) }.getOrDefault(false)
            if (!ok) return Result(BitPerfectState(BitPerfectKind.REFUSED, name(d), rate, bits), false)
            device = d; applied = wanted
            return Result(BitPerfectState(BitPerfectKind.ACTIVE, name(d), rate, bits), true)
        }
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
