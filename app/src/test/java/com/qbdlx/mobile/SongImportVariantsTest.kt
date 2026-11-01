package com.qbdlx.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongImportVariantsTest {
    @Test fun cleansFileAndVideoNames() {
        assertEquals("aliento a tequila billy manhattan", SongImport.clean("y2mate.com - aliento_a_tequila_billy_manhattan_K7axBjqsrzI"))
        assertEquals("AJR - I'm Ready", SongImport.clean("AJR - I'm Ready (Official Video)"))
        assertEquals("Ghost", SongImport.clean("GhostBCVEVO"))
        assertEquals("Mana la prision", SongImport.clean("Mana la prision (Letra)"))
    }
    @Test fun splitsArtistFromTitle() {
        val v = SongImport.variants(ImportedSong("AJR - I'm Ready (Official Video)", "AJR", "", 0, ""))
        assertTrue(v.any { it.artist == "AJR" && it.title == "I'm Ready" })
    }
}

class SongImportTypoTest {
    @Test fun oneLetterTypoStillMatches() {
        assertTrue(SongImport.similarity("chaggy", "shaggy") >= 0.99)
    }
}

class SongImportMoreVariantsTest {
    private fun v(t: String, a: String = "") = SongImport.variants(ImportedSong(t, a, "", 0, "")).map { it.artist to it.title }
    @Test fun titleAfterTheBar() = assertTrue(v("Letra", "Yuridia | En su lugar").contains("Yuridia" to "En su lugar"))
    @Test fun backOfLongTitle() = assertTrue(v("y2mate.com - Panic At The Disco Emperors New Clothes OFFICIAL VI…").any { it.second == "Emperors New Clothes" })
    @Test fun artistAlone() = assertTrue(v("メギツネ (air vocal ver.)", "BABYMETAL").any { it.second == "BABYMETAL" })
}
