package com.qbdlx.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {
    @Test fun cleansStreamingTitles() {
        assertEquals("That's What I Like (feat. Gucci Mane)", LyricsClient.cleanTitle("That's What I Like (feat. Gucci Mane)  (2017 Remix)"))
        assertEquals("That's What I Like", LyricsClient.cleanTitle("That's What I Like (feat. Gucci Mane)  (2017 Remix)", dropFeat = true))
        assertEquals("Song", LyricsClient.cleanTitle("Song [Hi-Res]"))
        assertEquals("(2017)", LyricsClient.cleanTitle("(2017)"))
    }
    @Test fun parsesTimesAndSorts() {
        val l = LyricsParser.parseLrc("[00:12.50] second\n[00:01.5]first\n[01:02]third\nnot a lyric")
        assertEquals(listOf("first", "second", "third"), l.map { it.text })
        assertEquals(listOf(1500L, 12500L, 62000L), l.map { it.timeMs })
    }
    @Test fun repeatedStampsShareTheWords() {
        val l = LyricsParser.parseLrc("[00:10.00][00:30.00]chorus")
        assertEquals(listOf(10000L, 30000L), l.map { it.timeMs }); assertEquals(listOf("chorus", "chorus"), l.map { it.text })
    }
    @Test fun currentLine() {
        val l = LyricsParser.parseLrc("[00:01.00]a\n[00:05.00]b")
        assertEquals(-1, LyricsParser.currentIndex(l, 500)); assertEquals(0, LyricsParser.currentIndex(l, 1000)); assertEquals(1, LyricsParser.currentIndex(l, 9000))
    }

    @Test fun rejectsAnotherSongWithASimilarName() {
        assertTrue(LyricsClient.sameSong("That's What I Like", "Bruno Mars", "That's What I Like (feat. Gucci Mane) (2017 Remix)", "Bruno Mars"))
        assertFalse(LyricsClient.sameSong("Holdin On", "Someone Else", "Holdin On", "Flume"))
        assertFalse(LyricsClient.sameSong("Other Song", "Flume", "Holdin On", "Flume"))
    }
}
