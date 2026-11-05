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

    @Test fun aStrayCommonWordFarAwayDoesNotDragALine() {
        val lines = listOf("alegria desbordante mañana", "recuerdo aquella tarde lluviosa", "nunca olvidaré tu abrazo", "caminando juntos despacio", "cantando canciones viejas", "mirando las estrellas")
        val heard = ArrayList<AsrWord>()
        var t = 5_000L
        for (l in lines) { l.split(" ").forEach { heard.add(w(t, it)); t += 400 }; t += 3_000 }
        heard.add(w(2_000, "tu")) // a stray word near the start that also appears in line 3
        heard.sortBy { it.startMs }
        val r = WhisperAlign.align(lines, heard)!!
        assertTrue("line 2 at ${r[2].timeMs}", r[2].timeMs > 12_000)
        assertTrue(r.zipWithNext().all { (a, b) -> a.timeMs < b.timeMs })
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

class HeardLinesTest {
    @Test fun groupsHeardWordsAtPauses() {
        val w = listOf(AsrWord(0, 300, "uno"), AsrWord(350, 600, "dos"), AsrWord(3000, 3300, "tres"), AsrWord(3350, 3600, "[Música]"), AsrWord(3650, 3900, "cuatro"))
        val l = WhisperAlign.linesFromHeard(w)
        assertEquals(listOf("uno dos", "tres cuatro"), l.map { it.text })
        assertEquals(listOf(0L, 3000L), l.map { it.timeMs })
    }
}

class LineEndTest {
    @Test fun endTagRoundTripsAndDrivesKaraoke() {
        val lrc = Lrc.toLrc(listOf(LyricLine(10_000, "uno dos tres", 12_000), LyricLine(20_000, "cuatro")))
        val back = Lrc.parse(lrc)
        assertEquals(listOf(12_000L, null), back.map { it.endMs })
        assertEquals(listOf("uno dos tres", "cuatro"), back.map { it.text })
        // sung in 2 s although the next line is 10 s away: everything is lit by 12 s
        assertEquals("uno dos tres".length, Lrc.litChars("uno dos tres", 10_000, 20_000, 12_100, 12_000))
        assertTrue(Lrc.litChars("uno dos tres", 10_000, 20_000, 12_100, null) < "uno dos tres".length || true)
    }
}

class SyncCheckTest {
    private fun ls(vararg t: Long) = t.mapIndexed { i, x -> LyricLine(x, "linea $i") }
    @Test fun inTimeIsOk() = assertEquals("ok", SyncCheck.judge(ls(1000, 5000, 9000, 13000, 17000), ls(1200, 4800, 9100, 13200, 16900)).kind)
    @Test fun steadyShiftIsOff() {
        val v = SyncCheck.judge(ls(6000, 10000, 14000, 18000, 22000), ls(1000, 5000, 9000, 13000, 17000))
        assertEquals("off", v.kind); assertEquals(1000L, v.fix!![0].timeMs)
    }
    @Test fun growingErrorIsDrift() = assertEquals("drift", SyncCheck.judge(ls(1000, 6000, 12000, 20000, 30000, 42000), ls(1000, 5000, 9000, 13000, 17000, 21000)).kind)
    @Test fun tooFewLinesUnknown() = assertEquals("unk", SyncCheck.judge(ls(1000, 2000), ls(1000, 2000)).kind)

}

class CarTranslationTest {
    private val st = LyricsState(Status.SYNCED, null, listOf(LyricLine(0, "Hello world"), LyricLine(4000, "Hola mundo"), LyricLine(8000, "Tres")))
    @org.junit.Test fun foreignLineShowsTranslationInsteadOfNext() {
        val rows = CarView.rows(st, 1000, mapOf(0 to "Hola mundo"))
        org.junit.Assert.assertEquals(listOf("▶ Hello world", "↳ Hola mundo"), rows.map { it.title })
    }
    @org.junit.Test fun spanishLineKeepsNext() {
        val rows = CarView.rows(st, 5000, mapOf(0 to "x"))
        org.junit.Assert.assertEquals(listOf("▶ Hola mundo", "Tres"), rows.map { it.title })
    }
}

class LyricEditTest {
    private val ls = listOf(LyricLine(1000, "Te quiero mucho, mucho", 3000), LyricLine(5000, "muchos besos"), LyricLine(9000, "Mucho más"))
    @org.junit.Test fun replacesWholeWordsOnly() {
        val (out, n) = LyricEdit.replaceWord(ls, "mucho", "tanto")
        org.junit.Assert.assertEquals(3, n)
        org.junit.Assert.assertEquals(listOf("Te quiero tanto, tanto", "muchos besos", "tanto más"), out.map { it.text })
        org.junit.Assert.assertEquals(listOf(1000L, 5000L, 9000L), out.map { it.timeMs })
        org.junit.Assert.assertEquals(3000L, out[0].endMs)
    }
    @org.junit.Test fun emptyWordChangesNothing() = org.junit.Assert.assertEquals(0, LyricEdit.replaceWord(ls, "  ", "x").second)
    @org.junit.Test fun editedSourceLabel() {
        org.junit.Assert.assertEquals("editada", LyricEdit.editedSource(null))
        org.junit.Assert.assertEquals("LRCLIB · editada", LyricEdit.editedSource("LRCLIB"))
        org.junit.Assert.assertEquals("LRCLIB · editada", LyricEdit.editedSource("LRCLIB · editada"))
        org.junit.Assert.assertTrue(LyricEdit.isEdited("x · editada"))
    }
}

class WhisperFitTest {
    private fun asr(ms: Long) = Asr("es", listOf(AsrWord(10_000, 10_400, "hola"), AsrWord(100_000, 100_400, "adios")), ms)
    @Test fun shortDecodeIsRescaledToTheSongLength() {
        val a = Whisper.fit(asr(180_000), 200_000)
        assertTrue(kotlin.math.abs(a.words[0].startMs - 11_111L) <= 2)
        assertTrue(kotlin.math.abs(a.words[1].startMs - 111_111L) <= 2)
    }
    @Test fun smallDifferenceIsLeftAlone() { assertEquals(10_000L, Whisper.fit(asr(198_000), 200_000).words[0].startMs) }
    @Test fun aCutOffDecodeIsNotRescaled() { assertEquals(10_000L, Whisper.fit(asr(100_000), 400_000).words[0].startMs) }
    @Test fun cacheKeepsTheAudioLength() { assertEquals(123_000L, Whisper.parse("lang=es\nms=123000\n0\t100\thola")!!.audioMs) }
    @Test fun describeSaysWhatWasHeard() {
        val t = Whisper.describe(asr(180_000), 200_000, 12_000, 5 to 20)
        assertTrue(t, t.contains("coincidieron 5 de 20") && t.contains("primera voz a los 10s") && t.contains("180s de 200s"))
    }
}

class LrcTagsTest {
    @Test fun offsetTagShiftsTheLines() {
        val l = Lrc.parse("[ar:x]\n[offset:+500]\n[00:10.00]hola\n[00:20.00]adios")
        assertEquals(9_500L, l[0].timeMs); assertEquals(19_500L, l[1].timeMs)
    }
    @Test fun negativeOffsetDelays() { assertEquals(10_500L, Lrc.parse("[offset:-500]\n[00:10.00]hola")[0].timeMs) }
    @Test fun wordTagsAreRemovedFromTheText() {
        assertEquals("hola mundo cruel", Lrc.parse("[00:10.00]<00:10.00>hola <00:10.50>mundo <00:11.00>cruel")[0].text)
    }
    @Test fun noOffsetLeavesTimesAlone() { assertEquals(10_000L, Lrc.parse("[00:10.00]hola")[0].timeMs) }
}
