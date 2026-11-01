package com.qbdlx.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongImportTest {
    private fun album(artist: String) = Album("a", "Album", artist, null, 10, "2002", "", false)
    private fun track(id: Long, title: String, artist: String, sec: Int, version: String? = null) =
        Track(id, title, version, 1, 1, artist, sec, true, "", false)
    private fun hit(id: Long, title: String, artist: String, sec: Int, version: String? = null) = album(artist) to track(id, title, artist, sec, version)

    @Test fun parsesTheScannersListAndDropsEmptyTitlesAndDuplicates() {
        val songs = SongImport.parse(
            """{"app":"qbdlx-scanner","version":1,"songs":[
              {"title":"Clocks","artist":"Coldplay","album":"X","durationSec":307,"file":"a.mp3"},
              {"title":" clocks ","artist":"COLDPLAY","durationSec":307},
              {"title":"","artist":"Nobody"}]}""",
        )
        assertEquals(1, songs.size)
        assertEquals("Clocks", songs[0].title)
    }

    @Test(expected = IllegalArgumentException::class) fun refusesOtherFiles() { SongImport.parse("""{"songs":[]}""") }

    @Test fun exactTitleArtistAndLengthIsSure() {
        val song = ImportedSong("Clocks", "Coldplay", "", 307, "")
        val item = SongImport.rank(song, listOf(hit(1, "Clocks (Live)", "Coldplay", 360), hit(2, "Clocks", "Coldplay", 308)))
        assertEquals(MatchLevel.Sure, item.level)
        assertEquals(2L, item.candidates[item.chosen!!].second.id)
    }

    @Test fun accentsAndBracketsDoNotMatter() {
        val song = ImportedSong("Corazón partío", "Alejandro Sanz", "", 0, "")
        assertEquals(MatchLevel.Sure, SongImport.rank(song, listOf(hit(1, "Corazon Partio (Remastered 2011)", "Alejandro Sanz", 250))).level)
    }

    @Test fun differentLengthOrArtistNeedsChecking() {
        val song = ImportedSong("Clocks", "Coldplay", "", 307, "")
        assertEquals(MatchLevel.Check, SongImport.rank(song, listOf(hit(1, "Clocks", "Coldplay", 400))).level)
        val other = SongImport.rank(song, listOf(hit(1, "Clocks", "Some Cover Band", 307)))
        assertEquals(MatchLevel.Check, other.level)
        assertNull(other.chosen)
    }

    @Test fun nothingSimilarIsMissing() {
        val item = SongImport.rank(ImportedSong("Clocks", "Coldplay", "", 307, ""), listOf(hit(1, "Yellow", "Coldplay", 267)))
        assertEquals(MatchLevel.Missing, item.level)
        assertEquals(1, item.candidates.size) // kept so it can be picked by hand
        assertNull(item.chosen)
        assertEquals(MatchLevel.Missing, SongImport.rank(ImportedSong("Clocks", "", "", 0, ""), emptyList()).level)
    }
}
