package com.qbdlx.scanner

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerLogicTest {
    @Test fun genericNamesNeedTheSound() {
        listOf("Track 03", "audio_0012", "01", "Pista-7", "Unknown", "").forEach { assertTrue(it, Tags.isGenericTitle(it)) }
        assertFalse(Tags.isGenericTitle("Clocks"))
        assertTrue(Tags.needsFingerprint("Clocks", "<unknown>"))
        assertFalse(Tags.needsFingerprint("Clocks", "Coldplay"))
    }

    @Test fun acoustIdPicksTheBestScoreAndDropsWeakOnes() {
        val ok = """{"status":"ok","results":[
            {"score":0.4,"recordings":[{"title":"Weak","artists":[{"name":"X"}]}]},
            {"score":0.93,"recordings":[{"title":"Clocks","artists":[{"name":"Coldplay"}],"releasegroups":[{"title":"A Rush of Blood to the Head"}]}]}]}"""
        val r = AcoustId.parse(ok)!!
        assertEquals("Clocks", r.title); assertEquals("Coldplay", r.artist); assertEquals("A Rush of Blood to the Head", r.album)
        assertNull(AcoustId.parse("""{"status":"ok","results":[{"score":0.3,"recordings":[{"title":"Weak","artists":[{"name":"X"}]}]}]}"""))
        assertNull(AcoustId.parse("""{"status":"ok","results":[]}"""))
    }

    @Test(expected = AcoustIdException::class) fun wrongKeyIsAnError() {
        AcoustId.parse("""{"status":"error","error":{"code":4,"message":"invalid API key"}}""")
    }

    @Test fun logHoldsEverythingTheImporterNeeds() {
        val json = JSONObject(SongLog.toJson(listOf(Song("Clocks", "Coldplay", "ARBTTH", 307, "a.mp3", Source.AcoustId, 0.934))))
        assertEquals(1, json.getInt("version"))
        val s = json.getJSONArray("songs").getJSONObject(0)
        assertEquals("Clocks", s.getString("title")); assertEquals(307, s.getInt("durationSec")); assertEquals("acoustid", s.getString("source"))
    }
}

class OmittedFoldersTest {
    @Test fun skipsFoldersByPartOfTheirName() {
        val omitted = listOf("WhatsApp", "voice notes")
        assertTrue(OmittedFolders.matches("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/", omitted))
        assertTrue(OmittedFolders.matches("Recordings/Voice Notes/", omitted))
        assertFalse(OmittedFolders.matches("Music/Coldplay/", omitted))
        assertFalse(OmittedFolders.matches("Music/", emptyList()))
    }

    @Test fun showsTheLastFolder() {
        assertEquals("WhatsApp Audio", OmittedFolders.shortName("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/"))
    }

    @Test fun onlyAnInvalidKeyIsRejected() {
        assertEquals(Verdict.Invalid, AcoustId.judgeKey("""{"status":"error","error":{"code":4,"message":"invalid API key"}}""").verdict)
        assertEquals(Verdict.Valid, AcoustId.judgeKey("""{"status":"error","error":{"code":3,"message":"invalid fingerprint"}}""").verdict)
        assertEquals(Verdict.Valid, AcoustId.judgeKey("""{"status":"ok","results":[]}""").verdict)
        assertEquals(Verdict.NoConnection, AcoustId.judgeKey("<html>").verdict)
    }

    @org.junit.Test fun recordingFoldersAreMatchedByWholeWords() {
        org.junit.Assert.assertTrue(OmittedFolders.isRecording("Recordings/Call"))
        org.junit.Assert.assertTrue(OmittedFolders.isRecording("Voice Recorder/2024"))
        org.junit.Assert.assertTrue(OmittedFolders.isRecording("Llamadas"))
        org.junit.Assert.assertFalse(OmittedFolders.isRecording("Music/Blue Note Records"))
        org.junit.Assert.assertFalse(OmittedFolders.isRecording("Music/Call Me Maybe Collection"))
    }
}
