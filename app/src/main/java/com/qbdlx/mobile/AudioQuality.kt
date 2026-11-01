package com.qbdlx.mobile

/**
 * What a saved or incoming audio file really is, as opposed to the quality that was asked for: asking Qobuz for Hi-Res
 * on a track that only exists in CD quality gives a CD-quality file. Used to decide whether a new download is an
 * upgrade of the file already saved. Plain Kotlin so it can be unit-tested.
 */
class AudioQuality private constructor(val mp3: Boolean, val bitDepth: Int, val sampleRate: Int, val durationSec: Double) {
    /** True only when this is clearly a better file than [old]: more bits, or the same bits at a higher sample rate. */
    fun isBetterThan(old: AudioQuality): Boolean = when {
        mp3 -> false                       // never an upgrade, whatever the old file is
        old.mp3 -> true                    // any FLAC beats an MP3
        bitDepth != old.bitDepth -> bitDepth > old.bitDepth
        else -> sampleRate > old.sampleRate
    }

    companion object {
        val MP3 = AudioQuality(true, 0, 0, 0.0)
        fun flac(bitDepth: Int, sampleRate: Int, durationSec: Double = 0.0) = AudioQuality(false, bitDepth, sampleRate, durationSec)

        /** From the fields of Qobuz's getFileUrl answer. [samplingRate] is in kHz (44.1, 96) or, defensively, in Hz. */
        fun fromResponse(mimeType: String, bitDepth: Int, samplingRate: Double): AudioQuality? = when {
            mimeType.contains("mpeg") -> MP3
            bitDepth <= 0 || samplingRate <= 0.0 -> null
            else -> flac(bitDepth, (if (samplingRate < 1000) samplingRate * 1000 else samplingRate).let { Math.round(it).toInt() })
        }
    }
}

/** Reads the one metadata block every FLAC file starts with. */
object FlacInfo {
    /** The bytes needed: "fLaC" + block header (4) + the 34-byte STREAMINFO. */
    const val HEAD_BYTES = 42

    fun parse(head: ByteArray, length: Int = head.size): AudioQuality? {
        if (length < HEAD_BYTES) return null
        if (head[0] != 'f'.code.toByte() || head[1] != 'L'.code.toByte() || head[2] != 'a'.code.toByte() || head[3] != 'C'.code.toByte()) return null
        if ((head[4].toInt() and 0x7F) != 0) return null // the first block must be STREAMINFO
        // STREAMINFO bytes 10..17: 20 bits sample rate, 3 bits channels, 5 bits bits-per-sample, 36 bits total samples.
        var bits = 0L
        for (i in 18 until 26) bits = (bits shl 8) or (head[i].toLong() and 0xFF)
        val rate = ((bits ushr 44) and 0xFFFFF).toInt()
        val depth = (((bits ushr 36) and 0x1F) + 1).toInt()
        val samples = bits and 0xFFFFFFFFFL
        if (rate <= 0) return null
        return AudioQuality.flac(depth, rate, samples.toDouble() / rate)
    }
}

enum class Upgrade { REPLACE, KEEP, UNKNOWN }

/**
 * Whether [incoming] should take the place of [existing]. [existing] unreadable -> KEEP (never risk a file we can't
 * judge); [incoming] not known yet -> UNKNOWN (download it, then judge with [verifySwap]).
 */
fun upgradeVerdict(existing: AudioQuality?, incoming: AudioQuality?): Upgrade = when {
    existing == null -> Upgrade.KEEP
    incoming == null -> Upgrade.UNKNOWN
    incoming.isBetterThan(existing) -> Upgrade.REPLACE
    else -> Upgrade.KEEP
}

/**
 * The final check once the new file is fully saved, from what is really inside it: strictly better, and not a
 * clipped file (Qobuz can answer with a ~30 s preview when the account isn't entitled to a tier). The length to
 * compare with is the old file's own, or [trackSec] (from Qobuz's metadata) when the old file is an MP3.
 */
fun verifySwap(existing: AudioQuality, saved: AudioQuality?, trackSec: Int = 0): Boolean {
    if (saved == null || !saved.isBetterThan(existing)) return false
    val reference = if (existing.durationSec > 0) existing.durationSec else trackSec.toDouble()
    if (reference > 0 && saved.durationSec > 0 && saved.durationSec < reference * 0.9) return false
    return true
}
