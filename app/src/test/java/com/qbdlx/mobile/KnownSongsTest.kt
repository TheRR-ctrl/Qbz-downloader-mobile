package com.qbdlx.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownSongsTest {
    private val known = KnownSongs(listOf(
        KnownSong(KnownSongs.stemOf("03 - Clocks.flac")!!, "coldplay a rush of blood to the head"),
        KnownSong(KnownSongs.stemOf("Yuridia - En su lugar.mp3")!!, "mis descargas"),
    ))
    @Test fun stem() { assertEquals("clocks", KnownSongs.stemOf("03 - Clocks.flac")); assertEquals(null, KnownSongs.stemOf("cover.jpg")) }
    @Test fun matchesByTitleAndArtistInPath() = assertTrue(known.has("Clocks", "Coldplay"))
    @Test fun matchesArtistInFileName() = assertTrue(known.has("En su lugar", "Yuridia"))
    @Test fun otherArtistIsNotAMatch() = assertFalse(known.has("Clocks", "Radiohead"))
    @Test fun unknownTitle() = assertFalse(known.has("Yellow", "Coldplay"))
}

class DuplicatesTest {
    private fun song(file: String, path: String, label: String) = KnownSong(KnownSongs.stemOf(file)!!, path, label)
    @Test fun findsTheSameSongInTwoPlaces() {
        val d = Duplicates.find(listOf(song("03 - Clocks.flac", "coldplay", "A"), song("Clocks.mp3", "mis cosas", "B"), song("Yellow.flac", "coldplay", "C")))
        assertEquals(listOf(listOf("A", "B")), d.map { g -> g.map { it.label } })
    }
    @Test fun artistPrefixInTheNameStillMatches() {
        assertEquals(1, Duplicates.find(listOf(song("01 - En su lugar.mp3", "yuridia", "A"), song("Yuridia - En su lugar.mp3", "", "B"))).size)
    }
    @Test fun nothingRepeated() = assertTrue(Duplicates.find(listOf(song("A song.mp3", "", "A"), song("Other thing.mp3", "", "B"))).isEmpty())
}

class DefaultDeletionsTest {
    private fun s(label: String, uri: String?) = KnownSong("x", "", label, uri)
    @Test fun keepsTheReadOnlyCopyAndDeletesAllOthers() = assertEquals(2, Duplicates.defaultDeletions(listOf(s("a", "u1"), s("b", "u2"), s("c", null))).size)
    @Test fun keepsOneWhenAllAreDeletable() {
        val d = Duplicates.defaultDeletions(listOf(s("a.mp3", "u1"), s("b.flac", "u2")))
        assertEquals(listOf("a.mp3"), d.map { it.label })
    }
}

class ImportCacheTest {
    @Test fun roundTrip() {
        val album = Album("a1", "Album", "Drake", "http://c", 10, "2015", "16/44", false, genre = "Hip-Hop")
        val track = Track(5L, "Fake Love", null, 3, 1, "Drake", 210, true, "16/44", false, "ISRC1", albumTrackNumber = 3)
        val song = ImportedSong("Fake Love", "Drake", "", 0, "")
        val json = ImportCache.toJson(mapOf(ImportCache.key(song) to ImportItem(song, listOf(album to track), MatchLevel.Check, 0, setOf())))
        val back = ImportCache.fromJson(json)[ImportCache.key(song)]!!.toItem(song)
        assertEquals(MatchLevel.Check, back.level)
        assertEquals(5L, back.picks.single().second.id)
        assertEquals("Hip-Hop", back.candidates[0].first.genre)
    }
    @Test fun failedSearchIsNotRemembered() {
        val song = ImportedSong("X", "Y", "", 0, "")
        assertEquals(0, ImportCache.fromJson(ImportCache.toJson(mapOf("k" to ImportItem(song, emptyList(), MatchLevel.Missing, null, emptySet(), searchFailed = true)))).size)
    }
    @Test fun damagedFileIsEmpty() = assertEquals(0, ImportCache.fromJson("{oops").size)
}

class IpodJobTest {
    @Test fun roundTrip() {
        val item = com.qbdlx.mobile.ipod.SendItem("k", "content://x/1", "Title", "Artist", "Album", "Pop", 200000L, 3, 2020, 320, listOf(5L, 6L))
        val back = com.qbdlx.mobile.ipod.IpodJobStore.decode(com.qbdlx.mobile.ipod.IpodJobStore.encode(listOf(item))).single()
        assertEquals("Title", back.title); assertEquals(listOf(5L, 6L), back.replacePids); assertEquals(200000L, back.durationMs)
    }
    @Test fun damagedFileIsEmpty() = assertEquals(0, com.qbdlx.mobile.ipod.IpodJobStore.decode("[oops").size)
}
