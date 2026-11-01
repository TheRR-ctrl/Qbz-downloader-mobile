package com.qbdlx.mobile

import org.junit.Assert.*
import org.junit.Test

class CarLyricsTest {
    private val lyrics = Lyrics(listOf(LyricLine(1000, "a"), LyricLine(2000, "b"), LyricLine(3000, "c"), LyricLine(4000, "d"), LyricLine(5000, "e")), true)

    @Test fun showsTheSongThenTheCurrentLineWithNeighbours() {
        val rows = CarLyrics.rows("T", "A", lyrics, 2500)
        assertEquals(listOf("♫  T - A", "a", "▶ b", "c", "d"), rows.map { it.title })
        assertTrue(rows.all { it.id.startsWith("l") })
    }

    @Test fun beforeTheFirstLineShowsANote() {
        assertEquals(listOf("♪", "♪ …", "a", "b"), CarLyrics.rows("T", "A", lyrics, 0).drop(1).map { it.title })
    }

    @Test fun nothingWithoutSyncedLyrics() {
        assertTrue(CarLyrics.rows("T", "A", null, 0).isEmpty())
        assertTrue(CarLyrics.rows("T", "A", Lyrics(listOf(LyricLine(0, "x")), false), 0).isEmpty())
    }
}
