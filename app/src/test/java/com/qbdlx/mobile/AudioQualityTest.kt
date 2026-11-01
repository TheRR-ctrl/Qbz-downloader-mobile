package com.qbdlx.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/** A minimal FLAC start: "fLaC", a STREAMINFO block header, and a STREAMINFO with the given values. */
private fun flacHead(rate: Int, depth: Int, samples: Long, channels: Int = 2): ByteArray {
    val b = ByteArray(42)
    "fLaC".forEachIndexed { i, c -> b[i] = c.code.toByte() }
    b[4] = 0x00; b[7] = 34 // STREAMINFO, length 34
    val bits = (rate.toLong() shl 44) or ((channels - 1).toLong() shl 41) or ((depth - 1).toLong() shl 36) or samples
    for (i in 0 until 8) b[18 + i] = (bits ushr (56 - 8 * i)).toByte()
    return b
}

class AudioQualityTest {
    private val cd = AudioQuality.flac(16, 44100, 200.0)
    private val hi96 = AudioQuality.flac(24, 96000, 200.0)
    private val hi192 = AudioQuality.flac(24, 192000, 200.0)

    // ---- reading the file -------------------------------------------------------------------------------------

    @Test fun readsSampleRateBitDepthAndLengthFromStreamInfo() {
        val q = FlacInfo.parse(flacHead(96000, 24, 96000L * 200))!!
        assertEquals(96000, q.sampleRate); assertEquals(24, q.bitDepth)
        assertEquals(200.0, q.durationSec, 0.001)
        assertFalse(q.mp3)
    }

    @Test fun readsBigValues() {
        val q = FlacInfo.parse(flacHead(192000, 32, 68_719_476_735L))!! // largest 36-bit sample count
        assertEquals(192000, q.sampleRate); assertEquals(32, q.bitDepth)
    }

    @Test fun rejectsAnythingThatIsNotAFlacStart() {
        assertNull(FlacInfo.parse(ByteArray(10)))                                       // too short
        assertNull(FlacInfo.parse(ByteArray(42)))                                       // no "fLaC"
        assertNull(FlacInfo.parse(flacHead(44100, 16, 1).also { it[4] = 0x04 }))        // first block isn't STREAMINFO
        assertNull(FlacInfo.parse(flacHead(0, 16, 1)))                                  // impossible sample rate
        assertNull(FlacInfo.parse(ByteArray(0)))
    }

    @Test fun readUpToReturnsWhatThereIsEvenWhenTheStreamIsShort() {
        assertEquals(5, ByteArrayInputStream(ByteArray(5)).readUpTo(42).size)
        assertEquals(42, ByteArrayInputStream(ByteArray(100)).readUpTo(42).size)
        // a stream that hands out one byte at a time must still fill the buffer
        val slow = object : java.io.InputStream() {
            var n = 0
            override fun read() = if (n < 100) n++ and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int { val v = read(); if (v < 0) return -1; b[off] = v.toByte(); return 1 }
        }
        assertEquals(42, slow.readUpTo(42).size)
    }

    // ---- what Qobuz says ----------------------------------------------------------------------------------------

    @Test fun responseFieldsBecomeAQuality() {
        val q = AudioQuality.fromResponse("audio/flac", 24, 96.0)!!
        assertEquals(24, q.bitDepth); assertEquals(96000, q.sampleRate)
        assertEquals(44100, AudioQuality.fromResponse("audio/flac", 16, 44.1)!!.sampleRate)
        assertEquals(44100, AudioQuality.fromResponse("audio/flac", 16, 44100.0)!!.sampleRate) // already in Hz
        assertTrue(AudioQuality.fromResponse("audio/mpeg", 0, 0.0)!!.mp3)
    }

    @Test fun aResponseWithoutTheFieldsIsUnknownNotGuessed() {
        assertNull(AudioQuality.fromResponse("audio/flac", 0, 0.0))
        assertNull(AudioQuality.fromResponse("audio/flac", 24, 0.0))
        assertNull(AudioQuality.fromResponse("audio/flac", 0, 96.0))
    }

    // ---- comparing ----------------------------------------------------------------------------------------------

    @Test fun moreBitsOrAHigherRateIsBetterAndEqualIsNot() {
        assertTrue(hi96.isBetterThan(cd))
        assertTrue(hi192.isBetterThan(hi96))
        assertTrue(AudioQuality.flac(24, 48000).isBetterThan(AudioQuality.flac(16, 96000))) // bits come first
        assertFalse(cd.isBetterThan(cd))
        assertFalse(hi96.isBetterThan(hi96))
        assertFalse(cd.isBetterThan(hi96))
    }

    @Test fun anyFlacBeatsAnMp3AndAnMp3NeverBeatsAnything() {
        assertTrue(cd.isBetterThan(AudioQuality.MP3))
        assertFalse(AudioQuality.MP3.isBetterThan(cd))
        assertFalse(AudioQuality.MP3.isBetterThan(AudioQuality.MP3))
    }

    // ---- the decision before downloading ------------------------------------------------------------------------

    @Test fun verdictBeforeDownloading() {
        assertEquals(Upgrade.REPLACE, upgradeVerdict(cd, hi96))
        assertEquals(Upgrade.REPLACE, upgradeVerdict(AudioQuality.MP3, cd))
        assertEquals("Hi-Res was asked for but Qobuz served CD quality: not an upgrade", Upgrade.KEEP, upgradeVerdict(cd, AudioQuality.flac(16, 44100)))
        assertEquals(Upgrade.KEEP, upgradeVerdict(hi96, cd))
        assertEquals(Upgrade.KEEP, upgradeVerdict(cd, AudioQuality.MP3))
        assertEquals("an unreadable saved file is never touched", Upgrade.KEEP, upgradeVerdict(null, hi96))
        assertEquals("Qobuz didn't say: download, then look inside", Upgrade.UNKNOWN, upgradeVerdict(cd, null))
    }

    // ---- the check once the new file is saved -------------------------------------------------------------------

    @Test fun aBetterFullLengthFileIsAccepted() {
        assertTrue(verifySwap(cd, AudioQuality.flac(24, 96000, 200.0)))
        assertTrue("a few seconds shorter is normal (different masters)", verifySwap(cd, AudioQuality.flac(24, 96000, 190.0)))
    }

    @Test fun aPreviewIsNeverAllowedToReplaceAFullTrack() {
        assertFalse("30 s sample of a 200 s track", verifySwap(cd, AudioQuality.flac(24, 96000, 30.0)))
        assertFalse(verifySwap(cd, AudioQuality.flac(24, 96000, 179.0))) // just under 90%
    }

    @Test fun anMp3HasNoLengthSoQobuzsTrackLengthIsTheReference() {
        assertFalse(verifySwap(AudioQuality.MP3, AudioQuality.flac(16, 44100, 30.0), trackSec = 200))
        assertTrue(verifySwap(AudioQuality.MP3, AudioQuality.flac(16, 44100, 200.0), trackSec = 200))
        assertTrue("no reference at all: nothing to compare, quality decides", verifySwap(AudioQuality.MP3, AudioQuality.flac(16, 44100, 30.0), trackSec = 0))
    }

    @Test fun notBetterOrUnreadableIsRejected() {
        assertFalse(verifySwap(hi96, cd))
        assertFalse(verifySwap(cd, AudioQuality.flac(16, 44100, 200.0)))
        assertFalse(verifySwap(cd, null))
    }

    // ---- real files, read back against ffprobe ------------------------------------------------------------------

    /** Files in <audioDir>/info are named <bits>_<rate>_<seconds>.flac and were made by ffmpeg. */
    @Test fun readsRealFlacFilesLikeFfprobeDoes() {
        val path = System.getProperty("qbz.audioDir")
        assumeTrue("set -Dqbz.audioDir=<folder> to run this test", !path.isNullOrBlank() && File(path, "info").isDirectory)
        val files = File(path!!, "info").listFiles { f -> f.extension == "flac" }!!
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val (bits, rate, secs) = f.nameWithoutExtension.split("_").map { it.toInt() }
            val q = FlacInfo.parse(f.inputStream().use { it.readUpTo(FlacInfo.HEAD_BYTES) })
            assertNotNull("${f.name} could not be read", q)
            assertEquals(f.name, bits, q!!.bitDepth); assertEquals(f.name, rate, q.sampleRate)
            assertEquals(f.name, secs.toDouble(), q.durationSec, 0.01)
        }
        // and the real ordering of those files
        val byName = files.associate { it.nameWithoutExtension to FlacInfo.parse(it.readBytes())!! }
        assertTrue(byName.getValue("24_96000_10").isBetterThan(byName.getValue("16_44100_10")))
        assertFalse("a clipped 3 s Hi-Res can't replace the 10 s CD file", verifySwap(byName.getValue("16_44100_10"), byName.getValue("24_192000_3")))
        assertTrue(verifySwap(byName.getValue("16_44100_10"), byName.getValue("24_96000_10")))
    }
}
