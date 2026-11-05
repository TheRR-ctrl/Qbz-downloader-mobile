package com.qbdlx.lyrics

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class LyricsTest {
    private val lrc = "[ar:Someone]\n[00:12.50] First\n[00:20.00][01:00.5] Chorus\n[00:30.123] Second\n"

    @Test fun parsesStampsTagsAndRepeatedLines() {
        val l = Lrc.parse(lrc)
        assertEquals(listOf(12_500L, 20_000L, 30_123L, 60_500L), l.map { it.timeMs })
        assertEquals(listOf("First", "Chorus", "Second", "Chorus"), l.map { it.text })
    }

    @Test fun findsTheCurrentLine() {
        val l = Lrc.parse(lrc)
        assertEquals(-1, Lrc.indexAt(l, 5_000))
        assertEquals(0, Lrc.indexAt(l, 12_500))
        assertEquals(1, Lrc.indexAt(l, 29_999))
        assertEquals(3, Lrc.indexAt(l, 999_999))
    }

    @Test fun carRowsShowNeighboursAndMarkTheCurrentLine() {
        val st = LyricsState(Status.SYNCED, TrackInfo("T", "A", "B", 200_000, "p"), Lrc.parse(lrc), true)
        val rows = CarView.rows(st, 21_000)
        assertEquals(listOf("T", "▶ Chorus", "Second"), rows.map { it.title })
        assertEquals(1, rows.count { it.current })
    }

    @Test fun beforeTheFirstLineShowsAMusicNote() {
        val st = LyricsState(Status.SYNCED, null, Lrc.parse(lrc), true)
        assertEquals(listOf("♪ …", "First"), CarView.rows(st, 0).take(2).map { it.title })
    }

    @Test fun messagesWhenThereIsNoLyric() {
        assertTrue(CarView.rows(LyricsState(Status.NOT_FOUND), 0)[0].title.contains("No hay letra"))
        assertTrue(CarView.rows(LyricsState(Status.NO_ACCESS), 0)[0].title.contains("acceso"))
    }

    @Test fun searchPrefersSyncedResultsWithMatchingDuration() {
        val r = JSONArray("""[
          {"duration":300,"plainLyrics":"x","syncedLyrics":null},
          {"duration":201,"plainLyrics":"p","syncedLyrics":"[00:01.00] hi"},
          {"duration":200,"plainLyrics":"p2","syncedLyrics":null}]""")
        assertEquals("[00:01.00] hi", LrcLib.pickBest(r, 200)!!.synced)
        assertNull(LrcLib.pickBest(JSONArray("""[{"duration":300,"plainLyrics":"x"}]"""), 200))
        assertEquals("near", LrcLib.pickBest(JSONArray("""[{"duration":207,"plainLyrics":"near"}]"""), 200)!!.plain)
    }

    @Test fun cleansStreamingSuffixes() {
        assertEquals("Song", TrackText.clean("Song (Remastered 2011)"))
        assertEquals("Song", TrackText.clean("Song [Hi-Res]"))
        assertEquals("Song (Part 1)", TrackText.clean("Song (Part 1)"))
    }

    @Test fun rejectsAnotherSongWithASimilarName() {
        val other = JSONArray("""[{"trackName":"Atte El Insuperable","artistName":"Otro Artista","duration":200,"plainLyrics":"wrong"}]""")
        assertNull(LrcLib.pickBest(other, 200, "Atte El Insuperable", "Carin León"))
        val right = JSONArray("""[{"trackName":"Atte El Insuperable (Remastered 2020)","artistName":"Carin León","duration":200,"plainLyrics":"ok"}]""")
        assertEquals("ok", LrcLib.pickBest(right, 200, "Atte El Insuperable", "Carin León")!!.plain)
        assertTrue(LrcLib.sameSong("That's What I Like", "Bruno Mars", "That's What I Like (feat. Gucci Mane) (2017 Remix)", "Bruno Mars"))
        assertFalse(LrcLib.sameSong("Holdin On", "Someone Else", "Holdin On", "Flume"))
    }
}

class PrefetchTest {
    @org.junit.Test fun sameSongSameKeyWhateverTheApp() {
        assertEquals(Prefetch.storeKey("Rasputin", "Boney M."), Prefetch.storeKey("RASPUTIN", "boney m"))
        assertEquals(Prefetch.storeKey("Song", "A, B"), Prefetch.storeKey("Song", "A"))
        assertEquals(Prefetch.storeKey("Song", "Daddy Yankee feat. X"), Prefetch.storeKey("Song", "Daddy Yankee"))
        assertNotEquals(Prefetch.storeKey("One", "A"), Prefetch.storeKey("Two", "A"))
        val t = TrackInfo("Rasputin", "Boney M.", "x", 1000, "app.one"); val u = TrackInfo("Rasputin", "Boney M.", "y", 2000, "app.two")
        assertEquals(t.storeKey, u.storeKey); assertNotEquals(t.key, u.key)
    }

    @org.junit.Test fun theSharedListRoundTrips() {
        val list = listOf(PrefetchTrack("Título ñ", "Artista", "Álbum", 200), PrefetchTrack("B", "", "", 0))
        val back = Prefetch.parse(Prefetch.encode(list))!!
        assertEquals(listOf("Título ñ", "B"), back.map { it.title }); assertEquals(200, back[0].durationSec); assertEquals("Álbum", back[0].album)
        assertNull(Prefetch.parse("not json")); assertNull(Prefetch.parse("{\"other\":1}"))
    }

    @Test fun karaokeLightsWordsAsTheLineIsSung() {
        val t = "hello big world"
        assertEquals(0, Lrc.litChars(t, 1000, 5000, 900))
        assertEquals(t.length, Lrc.litChars(t, 1000, 5000, 5000))
        val mid = Lrc.litChars(t, 1000, 5000, 1000 + 900)
        assertTrue(mid in 5..t.length)
        assertEquals(0, Lrc.litChars("", 0, 1000, 500))
    }

    @Test fun estimatedTimesGrowAndStayInsideTheSong() {
        val ls = Lrc.estimate(listOf("a long first line here", "short", "another fairly long line").map { LyricLine(0, it) }, 180_000)
        assertTrue(ls[0].timeMs < ls[1].timeMs && ls[1].timeMs < ls[2].timeMs && ls[2].timeMs < 180_000)
        assertEquals(ls.size, Lrc.parse(Lrc.toLrc(ls)).size)
    }

    @Test fun audioTimingSnapsLinesToTheQuietGaps() {
        // 40 ms frames: phrases of 2, 6, 3 and 5 s separated by 0.6 s of near silence, starting at 2 s
        val spans = listOf(2.0, 6.0, 3.0, 5.0)
        val env = ArrayList<Float>(); repeat(50) { env.add(0.0005f) }
        val starts = ArrayList<Long>()
        for (s in spans) { starts.add(env.size * 40L); repeat((s * 25).toInt()) { env.add(0.2f) }; repeat(15) { env.add(0.0005f) } }
        repeat(100) { env.add(0.0005f) }
        val out = AudioTiming.align(env.toFloatArray(), listOf("same length line", "same length line", "same length line", "same length line"))
        for (k in 0..3) assertTrue("line $k at ${out[k].timeMs} vs ${starts[k] + 0L}", kotlin.math.abs(out[k].timeMs - (starts[k] + 0)) < 900)
    }
}

class WhisperAlignTest {
    private fun w(t: Long, s: String) = AsrWord(t, t + 300, s)

    @Test fun whisperWordsTimeTheLyricLinesDespiteMistakes() {
        val lines = listOf("Caminando por la calle", "buscando tu mirada", "y no te encuentro", "en ninguna parte")
        val heard = listOf(
            w(10_000, "[Música]"), w(12_000, "Caminando"), w(12_400, "por"), w(12_700, "la"), w(13_000, "calle,"),
            w(16_000, "buscando"), w(16_400, "tu"), w(16_800, "mirara"), // misheard
            w(20_000, "y"), w(20_300, "no"), w(20_600, "te"), w(20_900, "encuentro"),
            w(24_000, "en"), w(24_300, "ninguna"), w(24_700, "parte"),
        )
        val r = WhisperAlign.align(lines, heard)!!
        val expect = listOf(12_000L, 16_000L, 20_000L, 24_000L)
        r.forEachIndexed { i, l -> assertTrue("line $i at ${l.timeMs}", kotlin.math.abs(l.timeMs - expect[i]) <= 400) }
    }

    @Test fun aLineWithNothingHeardIsPlacedBetweenItsNeighbours() {
        val lines = listOf("uno dos tres cuatro", "palabras que nadie oyó", "cinco seis siete ocho")
        val heard = listOf(w(1_000, "uno"), w(1_400, "dos"), w(1_800, "tres"), w(2_200, "cuatro"), w(9_000, "cinco"), w(9_400, "seis"), w(9_800, "siete"), w(10_200, "ocho"))
        val r = WhisperAlign.align(lines, heard)!!
        assertTrue(r[1].timeMs in 3_000..8_000)
        assertTrue(r[0].timeMs < r[1].timeMs && r[1].timeMs < r[2].timeMs)
    }

    @Test fun tooLittleRecognisedIsNotTrusted() {
        val lines = listOf("una letra larga que nada tiene que ver", "con lo que se escucha de verdad aquí")
        assertNull(WhisperAlign.align(lines, listOf(w(0, "la"), w(500, "la"), w(900, "la"))))
    }

    @Test fun parsesTheNativeAnswer() {
        val asr = Whisper.parse("lang=es\n1200\t1500\t Hola\n1600\t1900\t mundo\n")!!
        assertEquals("es", asr.lang)
        assertEquals(listOf("Hola", "mundo"), asr.words.map { it.text })
        assertEquals(1200L, asr.words[0].startMs)
    }
}

class WhisperScriptTest {
    @Test fun differentAlphabetsFallBackToWordProportion() {
        val lines = List(6) { "हाँ चलदा ना चलदा $it" }
        val heard = (0 until 36).map { AsrWord(it * 1000L, it * 1000L + 400, "ਹਾਂ") } // same song, written in Gurmukhi
        val r = WhisperAlign.align(lines, heard)!!
        assertTrue(WhisperAlign.approximate)
        assertTrue(r.zipWithNext().all { (a, b) -> a.timeMs < b.timeMs })
        assertTrue(r[3].timeMs in 12_000..22_000)
    }
}

class YtMusicTest {
    private fun item(vid: String, title: String, vararg details: String): String {
        val runs = details.joinToString(",") { """{"text":"$it"}""" }
        return """{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"$vid"},"flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[$runs]}}}]}}"""
    }
    private fun search(vararg items: String) = org.json.JSONObject("""{"contents":{"tabs":[{"sections":[${items.joinToString(",")}]}]}}""")

    @Test fun picksTheSameSongNotAnotherVersion() {
        val r = search(item("a", "POKITO (Remix)", "Song", "Otro Artista", "3:00"), item("b", "POKITO", "Song", "Daddy Yankee", "Diljit Dosanjh", "2:49"))
        assertEquals("b", YtMusic.pickSong(r, "POKITO", "Daddy Yankee", 169))
    }
    @Test fun rejectsDifferentDuration() {
        val r = search(item("b", "POKITO", "Song", "Daddy Yankee", "4:49"))
        assertNull(YtMusic.pickSong(r, "POKITO", "Daddy Yankee", 169))
    }
    @Test fun findsLyricsPage() {
        val j = org.json.JSONObject("""{"tabs":[{"endpoint":{"browseEndpoint":{"browseId":"MPREb_x"}}},{"endpoint":{"browseEndpoint":{"browseId":"MPLYt_abc"}}}]}""")
        assertEquals("MPLYt_abc", YtMusic.lyricsBrowseId(j))
        assertNull(YtMusic.lyricsBrowseId(org.json.JSONObject("""{"tabs":[]}""")))
    }
    @Test fun parsesTimedAndPlain() {
        val timed = org.json.JSONObject("""{"timedLyricsData":[{"lyricLine":"uno","cueRange":{"startTimeMilliseconds":"9200","endTimeMilliseconds":"10600"}},{"lyricLine":"dos","cueRange":{"startTimeMilliseconds":"10700","endTimeMilliseconds":"12000"}}]}""")
        assertEquals(listOf(9200L, 10700L), YtMusic.parseTimed(timed)!!.map { it.timeMs })
        assertNull(YtMusic.parseTimed(org.json.JSONObject("""{"timedLyricsData":[{"lyricLine":"uno"},{"lyricLine":"dos"}]}""")))
        val plain = org.json.JSONObject("""{"musicDescriptionShelfRenderer":{"description":{"runs":[{"text":"primera linea de la letra\nsegunda linea"}]}}}""")
        assertEquals("primera linea de la letra\nsegunda linea", YtMusic.parsePlain(plain))
    }
}
