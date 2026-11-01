package com.qbdlx.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class SongLogTest {
    @Test fun theSavedLastScanReadsBackEqual() {
        val songs = listOf(
            Song("Title", "Artist", "Album", 215, "a.mp3", Source.Tags, 1.0, "Music/Rock/"),
            Song("x", "", "", 99, "b.flac", Source.AcoustId, 0.87, ""),
        )
        assertEquals(songs, SongLog.fromJson(SongLog.toJson(songs)))
        assertEquals(emptyList<Song>(), SongLog.fromJson("not json"))
    }

    @Test fun aSavedSongKeepsItsKeyForResuming() {
        val song = Song("T", "A", "B", 200, "t.mp3", Source.Tags, 1.0, "Music/X/")
        val back = SongLog.fromJson(SongLog.toJson(listOf(song))).single()
        assertEquals(song.key(), back.key())
        assertEquals("Music/X/|t.mp3|200", back.key())
    }
}
