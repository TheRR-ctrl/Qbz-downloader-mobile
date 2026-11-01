package com.qbdlx.mobile.ipod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test

class IpodHelpersTest {
    @Test fun theSameSongIsRecognisedWhateverTheCaseOrSpaces() {
        assertEquals(trackKey("Song", "Artist", "Album"), trackKey("  song ", "ARTIST", "album  "))
    }

    @Test fun differentSongsOrAlbumsAreDifferentKeys() {
        assertNotEquals(trackKey("Song", "Artist", "Album"), trackKey("Song", "Artist", "Other Album"))
        assertNotEquals(trackKey("A", "B C", "D"), trackKey("A B", "C", "D")) // the parts can't run into each other
    }

    @Test fun argumentsBecomeSqlLiterals() {
        assertEquals("SELECT 1 FROM t WHERE a=5 AND b='x' AND c=NULL", AndroidSql.inline("SELECT 1 FROM t WHERE a=? AND b=? AND c=?", listOf(5L, "x", null)))
    }

    @Test fun quotesInTextAreEscaped() {
        assertEquals("WHERE n='O''Brien''s'", AndroidSql.inline("WHERE n=?", listOf("O'Brien's")))
        assertEquals("WHERE n=''' OR 1=1 --'", AndroidSql.inline("WHERE n=?", listOf("' OR 1=1 --")))
    }

    @Test fun aMismatchedNumberOfArgumentsIsRefused() {
        try { AndroidSql.inline("a=? b=?", listOf(1L)); fail() } catch (e: IllegalArgumentException) { }
        try { AndroidSql.inline("a=?", listOf(1L, 2L)); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun aStatementWithoutPlaceholdersIsUnchanged() {
        assertEquals("PRAGMA quick_check", AndroidSql.inline("PRAGMA quick_check", emptyList()))
    }
}
