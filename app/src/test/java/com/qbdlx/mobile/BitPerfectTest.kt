package com.qbdlx.mobile

import android.media.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BitPerfectTest {
    private val st = AudioFormat.CHANNEL_OUT_STEREO
    private val offered = listOf(
        MixerFormat(44100, AudioFormat.ENCODING_PCM_16BIT, st),
        MixerFormat(96000, AudioFormat.ENCODING_PCM_24BIT_PACKED, st),
    )

    @Test fun matchesExactFormat() {
        assertEquals(offered[1], chooseMixer(offered, 96000, AudioFormat.ENCODING_PCM_24BIT_PACKED, st))
    }

    @Test fun rejectsDifferentRateOrEncoding() {
        assertNull(chooseMixer(offered, 48000, AudioFormat.ENCODING_PCM_16BIT, st))
        assertNull(chooseMixer(offered, 44100, AudioFormat.ENCODING_PCM_FLOAT, st))
    }

    @Test fun bitsPerEncoding() {
        assertEquals(16, encodingBits(AudioFormat.ENCODING_PCM_16BIT))
        assertEquals(24, encodingBits(AudioFormat.ENCODING_PCM_24BIT_PACKED))
    }
}
