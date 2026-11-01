package com.qbdlx.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The expected values come from the real libchromaprint (default algorithm) fed with the same 10 seconds. */
class ChromaprintTest {
    private fun pcm(): ShortArray {
        val bytes = javaClass.getResourceAsStream("/s10.wav")!!.readBytes()
        val data = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray((bytes.size - 44) / 2) { data.short }
    }

    private fun expected() = javaClass.getResource("/s10.raw")!!.readText().split(',').map { it.trim().toLong().toInt() }.toIntArray()

    private fun bitErrors(a: IntArray, b: IntArray): Double {
        val n = minOf(a.size, b.size)
        return (0 until n).sumOf { Integer.bitCount(a[it] xor b[it]) } / (32.0 * n)
    }

    @Test fun matchesTheReferenceImplementation() {
        val got = Chromaprint.fingerprint(pcm())
        val want = expected()
        assertEquals(want.size, got.size)
        assertTrue("bit error ${bitErrors(got, want)}", bitErrors(got, want) < 0.005)
    }

    @Test fun compressedFormMatchesTheReference() {
        val want = javaClass.getResource("/s10.b64")!!.readText().trim()
        assertEquals(want, Chromaprint.encode(expected()))
    }

    @Test fun survivesResamplingFromHigherRates() {
        // Up to 44.1 kHz by linear interpolation, then back down with our own resampler.
        val src = pcm()
        val up = ShortArray(src.size * 4) { i -> val p = i / 4; val f = (i % 4) / 4f; val a = src[p].toInt(); val b = src[minOf(p + 1, src.size - 1)].toInt(); (a + (b - a) * f).toInt().toShort() }
        val back = Resampler.toFingerprintRate(up, 44100)
        val got = Chromaprint.fingerprint(back)
        val ber = bitErrors(got, expected())
        assertTrue("bit error $ber", ber < 0.2)
    }

    @Test fun tooShortAudioGivesNothing() {
        assertEquals(0, Chromaprint.fingerprint(ShortArray(3000)).size)
    }
}
