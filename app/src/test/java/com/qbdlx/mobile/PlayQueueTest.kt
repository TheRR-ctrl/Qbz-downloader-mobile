package com.qbdlx.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

class PlayQueueTest {
    private fun queue(n: Int = 5, start: Int = 0, seed: Long = 1) =
        PlayQueue<String>(Random(seed)).also { it.set((1..n).map { i -> "t$i" }, start) }

    // ---- going forward and back ---------------------------------------------------------------------------------

    @Test fun playsInOrderAndEndsAtTheLastTrack() {
        val q = queue(3)
        assertEquals("t1", q.current)
        assertEquals("t2", q.next(false)); assertEquals("t3", q.next(false))
        assertNull("nothing after the last track", q.next(false))
        assertEquals("the last track stays current", "t3", q.current)
    }

    @Test fun startsWhereTheUserTapped() {
        val q = queue(5, start = 3)
        assertEquals("t4", q.current)
        assertEquals(listOf("t1", "t2", "t3"), q.state().history)
        assertEquals(listOf("t5"), q.state().upcoming)
    }

    @Test fun previousGoesBackAndStaysAtTheStart() {
        val q = queue(3, start = 1)
        assertEquals("t1", q.previous())
        assertEquals("already first: stays", "t1", q.previous())
        assertFalse(q.state().hasPrevious)
    }

    @Test fun anEmptyQueueIsHarmless() {
        val q = PlayQueue<String>()
        q.set(emptyList(), 0)
        assertNull(q.current); assertNull(q.next(true)); assertNull(q.previous()); assertNull(q.jump(0))
        q.toggleShuffle(); q.removeUpcoming(0); q.clearUpcoming()
        assertEquals(0, q.size)
    }

    @Test fun anOutOfRangeStartIsClamped() {
        assertEquals("t3", queue(3, start = 99).current)
        assertEquals("t1", queue(3, start = -4).current)
    }

    // ---- repeat -------------------------------------------------------------------------------------------------

    @Test fun repeatAllWrapsInBothDirections() {
        val q = queue(3).also { it.cycleRepeat() }
        assertEquals(RepeatMode.ALL, q.repeat)
        q.next(false); q.next(false)
        assertEquals("t1", q.next(true))
        assertEquals("t3", q.previous())
    }

    @Test fun repeatOneRepeatsOnlyWhenTheTrackEndsByItself() {
        val q = queue(3).also { it.cycleRepeat(); it.cycleRepeat() }
        assertEquals(RepeatMode.ONE, q.repeat)
        assertEquals("ended on its own: again", "t1", q.next(true))
        assertEquals("the user pressed next: moves on", "t2", q.next(false))
    }

    @Test fun repeatCyclesOffAllOneOff() {
        val q = queue()
        assertEquals(
            listOf(RepeatMode.ALL, RepeatMode.ONE, RepeatMode.OFF),
            List(3) { q.cycleRepeat(); q.repeat },
        )
    }

    // ---- shuffle ------------------------------------------------------------------------------------------------

    @Test fun shuffleKeepsTheCurrentTrackPlayingAndMixesTheRest() {
        val q = queue(20, start = 2)
        q.toggleShuffle()
        assertEquals("the track playing doesn't change", "t3", q.current)
        val upcoming = q.state().upcoming
        assertEquals((4..20).map { "t$it" }.toSet(), upcoming.toSet())
        assertTrue("with 17 tracks the order must differ from the original", upcoming != (4..20).map { "t$it" })
        assertEquals("what already played stays as history", listOf("t1", "t2"), q.state().history)
    }

    @Test fun shuffleOffRestoresTheOriginalOrderAroundTheCurrentTrack() {
        val q = queue(10)
        q.toggleShuffle()
        repeat(4) { q.next(false) }
        val playing = q.current!!
        q.toggleShuffle()
        assertEquals(playing, q.current)
        val all = q.state().history + playing + q.state().upcoming
        assertEquals((1..10).map { "t$it" }, all)
    }

    @Test fun startingAShuffledQueuePlaysTheChosenTrackFirstAndEveryOtherOnce() {
        val q = PlayQueue<String>(Random(7)).also { it.toggleShuffle(); it.set((1..8).map { i -> "t$i" }, 5) }
        assertEquals("t6", q.current)
        assertTrue(q.state().history.isEmpty())
        assertEquals((1..8).map { "t$it" }.filter { it != "t6" }.toSet(), q.state().upcoming.toSet())
        assertEquals(7, q.state().upcoming.size)
    }

    @Test fun repeatAllWrapsAShuffledQueueWithoutLosingOrDuplicatingTracks() {
        val q = queue(6).also { it.toggleShuffle(); it.cycleRepeat() }
        val firstRound = (q.state().history + q.current!! + q.state().upcoming)
        repeat(5) { q.next(false) }
        assertNotNull(q.next(true)) // wraps
        assertEquals(6, q.size)
        assertEquals("the same shuffled order plays again", firstRound.first(), q.current)
        assertEquals(firstRound.toSet(), (q.state().history + q.current!! + q.state().upcoming).toSet())
    }

    // ---- what comes next (for loading it ahead of time) -----------------------------------------------------------

    @Test fun peekNextShowsTheTrackThatNextWouldPlayAndMovesNothing() {
        val q = queue(3)
        assertEquals("t2", q.peekNext()); assertEquals("t2", q.peekNext()); assertEquals("t1", q.current)
        q.next(false); q.next(false)
        assertNull("end of the queue", q.peekNext())
    }

    @Test fun peekNextFollowsRepeatAndShuffle() {
        val q = queue(3).also { it.cycleRepeat() } // ALL
        q.next(false); q.next(false)
        assertEquals("wraps to the first", "t1", q.peekNext())
        q.cycleRepeat() // ONE
        assertEquals("same track again", "t3", q.peekNext())
        val s = queue(10).also { it.toggleShuffle() }
        val promised = s.peekNext()
        assertEquals("what peek said is what plays", promised, s.next(true))
    }

    @Test fun peekNextOnAnEmptyQueueIsNull() { assertNull(PlayQueue<String>().peekNext()) }

    // ---- editing ------------------------------------------------------------------------------------------------

    @Test fun removeUpcomingTakesOutExactlyThatTrack() {
        val q = queue(5)
        q.removeUpcoming(1) // t3
        assertEquals(listOf("t2", "t4", "t5"), q.state().upcoming)
        q.removeUpcoming(10); q.removeUpcoming(-1) // out of range: nothing happens
        assertEquals(3, q.state().upcoming.size)
    }

    @Test fun aRemovedTrackStaysGoneAfterShuffleIsTurnedOff() {
        val q = queue(6)
        q.toggleShuffle()
        val gone = q.state().upcoming[2]
        q.removeUpcoming(2)
        q.toggleShuffle()
        val all = q.state().history + q.current!! + q.state().upcoming
        assertFalse(gone in all); assertEquals(5, all.size)
    }

    @Test fun clearUpcomingKeepsOnlyTheCurrentTrack() {
        val q = queue(5, start = 2).also { it.clearUpcoming() }
        assertEquals("t3", q.current)
        assertTrue(q.state().upcoming.isEmpty()); assertTrue(q.state().history.isEmpty())
    }

    @Test fun jumpPlaysAnyTrackOfTheQueuePage() {
        val q = queue(5)
        assertEquals("t4", q.jump(3))
        assertEquals(listOf("t5"), q.state().upcoming)
        assertEquals("t1", q.jump(0))
        assertNull(q.jump(9))
        assertEquals("a bad jump changes nothing", "t1", q.current)
    }

    @Test fun theSameTrackTwiceIsTwoEntries() {
        val q = PlayQueue<String>().also { it.set(listOf("a", "b", "a"), 0) }
        q.removeUpcoming(1) // the second "a"
        assertEquals(listOf("b"), q.state().upcoming)
        assertEquals("a", q.current)
    }

    // ---- which tracks go into the queue -------------------------------------------------------------------------

    @Test fun tracksThatCannotBePlayedAreLeftOutAndTheStartFollowsTheTappedTrack() {
        val items = listOf("a", "x", "b", "x", "c")
        val (list, start) = playableQueue(items, 2) { it != "x" }
        assertEquals(listOf("a", "b", "c"), list); assertEquals("b", list[start])
        val (l2, s2) = playableQueue(items, 1) { it != "x" } // tapped an unplayable one: next playable
        assertEquals("b", l2[s2])
    }

    @Test fun nothingPlayableGivesAnEmptyQueue() {
        val (list, start) = playableQueue(listOf("x", "x"), 0) { false }
        assertTrue(list.isEmpty()); assertEquals(0, start)
    }

    @Test fun repeatedTracksKeepTheTappedPosition() {
        val a = String(charArrayOf('a')); val a2 = String(charArrayOf('a')) // equal but distinct
        val (list, start) = playableQueue(listOf(a, "b", a2), 2) { true }
        assertEquals(2, start); assertTrue(list[start] === a2)
    }
}

class AudioQualityLabelTest {
    @Test fun writesQualityTheWayQobuzDoes() {
        assertEquals("24 Bit / 96 kHz", AudioQuality.flac(24, 96000).label())
        assertEquals("16 Bit / 44.1 kHz", AudioQuality.flac(16, 44100).label())
        assertEquals("24 Bit / 192 kHz", AudioQuality.flac(24, 192000).label())
        assertEquals("24 Bit / 88.2 kHz", AudioQuality.flac(24, 88200).label())
        assertEquals("MP3", AudioQuality.MP3.label())
    }

    @Test fun hiResMeansMoreThanCdQuality() {
        assertTrue(AudioQuality.flac(24, 44100).isHiRes)
        assertTrue(AudioQuality.flac(16, 96000).isHiRes)
        assertFalse(AudioQuality.flac(16, 44100).isHiRes)
        assertFalse(AudioQuality.flac(16, 48000).isHiRes)
        assertFalse(AudioQuality.MP3.isHiRes)
    }
}

class CoverColorTest {
    private fun tone(px: IntArray): Int? = backgroundTone(px)?.top
    private fun solid(argb: Int, n: Int = 400) = IntArray(n) { argb }
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    @Test fun aRedCoverGivesADarkRedBackground() {
        val c = tone(solid(0xFFD02020.toInt()))!!
        assertTrue("red channel dominates", r(c) > g(c) * 2 && r(c) > b(c) * 2)
        assertTrue("dark enough for white text", contrastWithWhite(c) >= MIN_CONTRAST - 0.01)
        assertEquals("opaque", 0xFF, c ushr 24)
    }

    @Test fun otherHuesComeOutAsTheirOwnColour() {
        val green = tone(solid(0xFF20C040.toInt()))!!
        assertTrue(g(green) > r(green) && g(green) > b(green))
        val blue = tone(solid(0xFF2040D0.toInt()))!!
        assertTrue(b(blue) > r(blue) && b(blue) > g(blue))
        val yellow = tone(solid(0xFFE0D020.toInt()))!!
        assertTrue(r(yellow) > b(yellow) && g(yellow) > b(yellow))
    }

    @Test fun whiteTextStaysReadableOnEveryHueAndBrightness() {
        for (hue in 0 until 360 step 7) for (v in listOf(0.5, 0.8, 1.0)) {
            val px = solid(hsvToArgb(hue.toDouble(), 0.9, v))
            val t = backgroundTone(px)!!
            assertTrue("hue $hue v $v: contrast ${contrastWithWhite(t.top)}", contrastWithWhite(t.top) >= MIN_CONTRAST - 0.01)
            assertTrue("bottom is darker still", contrastWithWhite(t.bottom) >= contrastWithWhite(t.top) - 0.01)
        }
    }

    @Test fun theBackgroundFadesToADarkerShadeOfTheSameColour() {
        val t = backgroundTone(solid(0xFF2040D0.toInt()))!!
        assertTrue(maxOf(r(t.bottom), g(t.bottom), b(t.bottom)) < maxOf(r(t.top), g(t.top), b(t.top)))
        assertTrue("same hue family", b(t.bottom) > r(t.bottom) && b(t.bottom) > g(t.bottom))
    }

    @Test fun aVividAccentBeatsALargerGreyishArea() {
        // a pale grey-blue cover with a small but clearly present red logo -> red, not blue
        val px = IntArray(1000) { if (it < 40) 0xFFD01828.toInt() else 0xFF9AA8B8.toInt() }
        val c = tone(px)!!
        assertTrue("red", r(c) > b(c) * 2)
    }

    /**
     * Covers cut from real player screenshots (see the folder <audioDir>/covers: navy.png, purple.png, red.png),
     * with the background colour Qobuz's own app showed for each. Our hue must be close to theirs.
     */
    @Test fun realCoversGiveTheColourQobuzShowed() {
        val path = System.getProperty("qbz.audioDir")
        org.junit.Assume.assumeTrue("set -Dqbz.audioDir=<folder> to run this test", !path.isNullOrBlank() && File(path, "covers").isDirectory)
        // name -> (hue, saturation, brightness) measured on the app's background
        val expected = mapOf("navy" to Triple(240.0, 0.57, 0.22), "purple" to Triple(307.0, 0.73, 0.43), "red" to Triple(354.0, 0.87, 0.49))
        // Navy is a pale, barely tinted cover, so only "the same blue family" is asked of it; the vivid ones must be close.
        val tolerance = mapOf("navy" to 35.0, "purple" to 15.0, "red" to 15.0)
        for ((name, want) in expected) {
            val raw = File(path!!, "covers/$name.rgb").readBytes() // 96x96 pixels, 3 bytes (R, G, B) each
            val px = IntArray(raw.size / 3) { i -> (0xFF shl 24) or ((raw[3 * i].toInt() and 0xFF) shl 16) or ((raw[3 * i + 1].toInt() and 0xFF) shl 8) or (raw[3 * i + 2].toInt() and 0xFF) }
            val t = backgroundTone(px)
            org.junit.Assert.assertNotNull("$name: no tone", t)
            val c = t!!.top
            val mx = maxOf(r(c), g(c), b(c)) / 255.0; val mn = minOf(r(c), g(c), b(c)) / 255.0
            val hue = run {
                val d = mx - mn; val rr = r(c) / 255.0; val gg = g(c) / 255.0; val bb = b(c) / 255.0
                ((when (mx) { rr -> ((gg - bb) / d) % 6.0; gg -> (bb - rr) / d + 2.0; else -> (rr - gg) / d + 4.0 } * 60.0) + 360.0) % 360.0
            }
            println("COVER $name: ours H=${"%.0f".format(hue)} S=${"%.2f".format((mx - mn) / mx)} V=${"%.2f".format(mx)}  Qobuz H=${want.first} S=${want.second} V=${want.third}")
            var dh = Math.abs(hue - want.first); if (dh > 180) dh = 360 - dh
            assertTrue("$name: hue ${"%.0f".format(hue)} vs ${want.first}", dh <= tolerance.getValue(name))
        }
    }

    @Test fun blackWhiteAndGreyCoversKeepTheNormalBackground() {
        assertNull(tone(solid(0xFF000000.toInt())))
        assertNull(tone(solid(0xFFFFFFFF.toInt())))
        assertNull(tone(solid(0xFF808080.toInt())))
        assertNull(tone(IntArray(400) { if (it % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }))
        assertNull(tone(IntArray(0)))
    }

    @Test fun transparentPixelsAreIgnored() {
        assertNull(tone(solid(0x00D02020)))
    }

    @Test fun theMostPresentColourWinsOverALittleOfAnother() {
        val px = IntArray(400) { if (it < 340) 0xFF2040D0.toInt() else 0xFFD02020.toInt() }
        val c = tone(px)!!
        assertTrue("mostly blue", b(c) > r(c))
    }

    @Test fun aFewStrayColouredPixelsInAGreyCoverDoNotTintTheScreen() {
        val px = IntArray(1000) { if (it < 5) 0xFFD02020.toInt() else 0xFF909090.toInt() }
        assertNull(tone(px))
    }

    @Test fun aHueThatSitsOnTheRedSeamIsNotSplit() {
        // half slightly-orange red, half slightly-magenta red: still one red, not cancelled out
        val px = IntArray(400) { if (it % 2 == 0) 0xFFD02A10.toInt() else 0xFFD0102A.toInt() }
        val c = tone(px)!!
        assertTrue(r(c) > g(c) * 2 && r(c) > b(c) * 2)
    }
}

class CrossfadeTimingTest {
    @Test fun gainsStartAtOneAndZeroAndEndAtZeroAndOne() {
        assertEquals(1f, fadeGains(0.0).first, 1e-6f); assertEquals(0f, fadeGains(0.0).second, 1e-6f)
        assertEquals(0f, fadeGains(1.0).first, 1e-6f); assertEquals(1f, fadeGains(1.0).second, 1e-6f)
    }

    @Test fun theLoudnessStaysEvenThroughTheMiddle() {
        for (i in 0..20) {
            val (out, inn) = fadeGains(i / 20.0)
            assertEquals("power at ${i * 5}%", 1.0, (out * out + inn * inn).toDouble(), 1e-5)
        }
        val (o, n) = fadeGains(0.5)
        assertEquals("a plain linear fade would dip to about 0.5 here", 0.7071, o.toDouble(), 1e-3); assertEquals(0.7071, n.toDouble(), 1e-3)
    }

    @Test fun outOfRangeProgressIsClamped() {
        assertEquals(1f, fadeGains(-3.0).first, 1e-6f); assertEquals(1f, fadeGains(7.0).second, 1e-6f)
    }

    @Test fun outgoingFallsAndIncomingRisesMonotonically() {
        var lastOut = 2f; var lastIn = -1f
        for (i in 0..50) { val (o, n) = fadeGains(i / 50.0); assertTrue(o <= lastOut && n >= lastIn); lastOut = o; lastIn = n }
    }

    @Test fun theFadeIsTheChosenLengthForNormalSongs() {
        assertEquals(6000, effectiveFadeMs(240_000, 6000))
        assertEquals(10_000, effectiveFadeMs(180_000, 10_000))
    }

    @Test fun theFadeNeverTakesMoreThanAThirdOfASongAndOffMeansOff() {
        assertEquals("a 12 s track can't fade for 10 s", 4000, effectiveFadeMs(12_000, 10_000))
        assertEquals(0, effectiveFadeMs(240_000, 0))
        assertEquals(0, effectiveFadeMs(0, 6000))
        assertEquals("a 2.4 s clip would fade for under a second: skip it", 0, effectiveFadeMs(2400, 6000))
        assertEquals(0, effectiveFadeMs(240_000, -5))
    }

    @Test fun theFadeStartsFadeLengthBeforeTheEnd() {
        assertFalse(shouldStartFade(233_000, 240_000, 6000))
        assertTrue(shouldStartFade(234_000, 240_000, 6000))
        assertTrue(shouldStartFade(239_500, 240_000, 6000))
        assertFalse("off", shouldStartFade(239_500, 240_000, 0))
        assertFalse("unknown length", shouldStartFade(10_000, -1, 6000))
    }

    @Test fun theNextSongIsFetchedSomeTimeBeforeTheEndButNotTooEarly() {
        assertEquals(45_000, prefetchLeadMs(0)); assertEquals(45_000, prefetchLeadMs(6000))
        assertEquals("a long fade needs more lead", 50_000, prefetchLeadMs(30_000))
        assertFalse(shouldPrefetch(100_000, 240_000, 0))
        assertTrue(shouldPrefetch(195_000, 240_000, 0))
        assertTrue("a short track: right away", shouldPrefetch(0, 30_000, 0))
        assertFalse("unknown length: wait", shouldPrefetch(0, -1, 0))
    }

    @Test fun theNextSongIsAlwaysFetchedBeforeTheFadeBegins() {
        for (fade in CROSSFADE_OPTIONS_MS) {
            val track = 200_000L
            val fadeStart = track - effectiveFadeMs(track, fade)
            assertTrue("fade $fade", track - prefetchLeadMs(fade) < fadeStart || effectiveFadeMs(track, fade) == 0L)
        }
    }
}

class CreditsTest {
    @Test fun readsNamesAndTheirRoles() {
        val c = parsePerformers("José José, Associated Performer, Main Artist - Armando Avila, Producer")
        assertEquals(listOf("José José", "Armando Avila"), c.map { it.name })
        assertEquals(listOf("Associated Performer", "Main Artist"), c[0].roles)
        assertEquals(listOf("Producer"), c[1].roles)
    }

    @Test fun theSamePersonTwiceIsOneEntryWithoutRepeatedRoles() {
        val c = parsePerformers("A B, Composer - C D, Mixer - A B, Lyricist, Composer")
        assertEquals(listOf("A B", "C D"), c.map { it.name })
        assertEquals(listOf("Composer", "Lyricist"), c[0].roles)
    }

    @Test fun aNameWithoutRolesAndMessyInputAreHandled() {
        assertEquals(listOf("Solo"), parsePerformers("Solo").map { it.name })
        assertTrue(parsePerformers("Solo")[0].roles.isEmpty())
        assertTrue(parsePerformers("").isEmpty()); assertTrue(parsePerformers(" - , ,  - ").isEmpty())
        assertEquals(listOf("Ana"), parsePerformers("  Ana ,  Vocals  ").map { it.name })
    }

    @Test fun initialsAreTheFirstTwoWords() {
        assertEquals("JJ", Credit("José José", emptyList()).initials)
        assertEquals("RP", Credit("Rafael Perez Botija", emptyList()).initials)
        assertEquals("S", Credit("solo", emptyList()).initials)
        assertEquals("", Credit("", emptyList()).initials)
    }

    @Test fun albumLengthReadsLikeQobuzs() {
        assertEquals("1h 01m", albumDuration(3660)); assertEquals("1h 00m", albumDuration(3600))
        assertEquals("45m", albumDuration(2700)); assertEquals("1m", albumDuration(20)); assertEquals("", albumDuration(0))
    }
}
