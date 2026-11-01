package com.qbdlx.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyListTest {
    @Test fun readsPlaylistAndAlbumLinks() {
        assertEquals("playlist" to "37i9dQZF1DXcBWIGoYBM5M", SpotifyLists.parseLink("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc"))
        assertEquals("album" to "4aawyAB9vmqN3uQ7FjRGTy", SpotifyLists.parseLink("https://open.spotify.com/intl-es/album/4aawyAB9vmqN3uQ7FjRGTy"))
        assertEquals("playlist" to "37i9dQZF1DXcBWIGoYBM5M", SpotifyLists.parseLink("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"))
        assertNull(SpotifyLists.parseLink("https://example.com/playlist/x"))
    }

    @Test fun readsTheTrackListFromTheEmbedPage() {
        val html = """<html><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"data":{"entity":{"name":"Mix","trackList":[
            {"uri":"spotify:track:7bxaFZ1O3cHkgLKMsdC3xR","title":"Loser","subtitle":"Tame Impala","duration":223069},
            {"uri":"spotify:track:x","title":"","subtitle":"nobody","duration":1}]}}}}}}</script></html>"""
        val l = SpotifyLists.parsePage(html, "playlist")
        assertEquals("Mix", l.name)
        assertEquals(1, l.songs.size)
        assertEquals("Loser", l.songs[0].title); assertEquals("Tame Impala", l.songs[0].artist)
        assertEquals(223, l.songs[0].durationSec); assertEquals(Source.Spotify, l.songs[0].source)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsAPageWithoutData() { SpotifyLists.parsePage("<html></html>", "album") }
}
