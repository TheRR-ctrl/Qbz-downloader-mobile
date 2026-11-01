package com.qbdlx.mobile

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The extra Qobuz fields behind the tags, including the malformed shapes a real API can return. */
class MetaParsingTest {
    private fun album() = Album("a1", "Out of Time", "R.E.M.", null, 11, "1991", "16/44", false)
    private fun track() = Track(9L, "Losing My Religion", null, 3, 1, "R.E.M.", 269, true, "16/44", false)

    @Test fun readsTheFieldsTheDesktopAppTags() {
        val json = JSONObject(
            """{"genre":{"name":"Pop/Rock"},"label":{"name":"Warner  Records"},"upc":"0093624899927",
               "copyright":"(c) 1991","media_count":2,"product_type":"album","release_date_original":"1991-03-12","version":"Deluxe",
               "artists":[{"name":"R.E.M.","roles":["main-artist"]}]}""",
        )
        val a = album().withTagDetails(json)
        assertEquals("Pop/Rock", a.genre); assertEquals("Warner  Records", a.label); assertEquals("0093624899927", a.upc)
        assertEquals("(c) 1991", a.copyright); assertEquals(2, a.mediaCount); assertEquals("album", a.productType)
        assertEquals("1991-03-12", a.releaseDate); assertEquals("Deluxe", a.version); assertEquals("R.E.M.", a.albumArtist)
        assertTrue(a.hasTagDetails)
    }

    @Test fun nullsAndMissingKeysBecomeEmptyNotTheTextNull() {
        val a = album().withTagDetails(JSONObject("""{"genre":null,"label":{"name":null},"upc":null,"version":null,"copyright":null}"""))
        assertEquals("", a.genre); assertEquals("", a.label); assertEquals("", a.upc); assertEquals("", a.version); assertEquals("", a.copyright)
        assertFalse(a.hasTagDetails)
        val bare = album().withTagDetails(JSONObject("{}"))
        assertEquals("falls back to the listed artist", "R.E.M.", bare.albumArtist)
        assertEquals("", bare.releaseDate)
    }

    @Test fun surprisingShapesNeverThrowAndLeaveTheAlbumUsable() {
        val weird = JSONObject("""{"genre":"Rock","label":["x"],"media_count":"two","artists":"R.E.M.","upc":12345}""")
        val a = album().withTagDetails(weird) // must not throw: this runs on every browse
        assertEquals("Out of Time", a.title)
        assertEquals("", a.genre); assertEquals("", a.label)
        assertEquals(0, a.mediaCount)
        assertEquals("a numeric UPC is still read", "12345", a.upc)
    }

    @Test fun mainArtistsAreJoinedLikeTheDesktopApp() {
        fun artists(vararg names: Pair<String, String>) =
            JSONObject().put("artists", org.json.JSONArray(names.map { (n, r) -> JSONObject().put("name", n).put("roles", org.json.JSONArray(listOf(r))) }))
        assertEquals("A", mainArtists(artists("A" to "main-artist")))
        assertEquals("A & B", mainArtists(artists("A" to "main-artist", "B" to "main-artist")))
        assertEquals("A, B & D", mainArtists(artists("A" to "main-artist", "B" to "main-artist", "C" to "featured-artist", "D" to "main-artist")))
        assertEquals("", mainArtists(artists("C" to "featured-artist")))
        assertEquals("", mainArtists(JSONObject()))
    }

    @Test fun namesGivenAsDisplayObjectsAreUnderstood() {
        val a = album().withTagDetails(JSONObject("""{"genre":{"name":{"display":"Jazz"}},"artists":[{"name":{"display":"Miles"},"roles":["main-artist"]}]}"""))
        assertEquals("Jazz", a.genre); assertEquals("Miles", a.albumArtist)
    }

    @Test fun trackFieldsIncludeTheAlbumsOwnTrackNumber() {
        val t = track().withTagDetails(JSONObject("""{"isrc":"USWB19900001","composer":{"name":"Bill Berry"},"parental_warning":true,"track_number":3}"""))
        assertEquals("USWB19900001", t.isrc); assertEquals("Bill Berry", t.composer); assertTrue(t.explicit); assertEquals(3, t.albumTrackNumber)
        val plain = track().withTagDetails(JSONObject("""{"isrc":null,"composer":null}"""))
        assertEquals("", plain.isrc); assertEquals("", plain.composer); assertFalse(plain.explicit); assertEquals(0, plain.albumTrackNumber)
        assertEquals("the visible title is unchanged", "Losing My Religion", plain.displayTitle)
    }
}
