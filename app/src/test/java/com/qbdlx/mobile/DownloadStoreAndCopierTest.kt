package com.qbdlx.mobile

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class FileTaskStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun task(id: Long, status: TaskStatus, version: String? = null, cover: String? = null) = DownloadTask(
        key = "$id:6",
        album = Album("alb$id", "Álbum \"raro\" / ñ", "Artista", cover, 12, "2021", "24/96", true),
        track = Track(id, "Canción $id", version, 3, 2, "Artista", 245, true, "24/96", true),
        quality = Quality.LOSSLESS,
        batchId = 7,
        status = status,
        error = if (status == TaskStatus.FAILED) "HTTP 403" else null,
    )

    @Test fun roundTripKeepsEveryFieldIncludingNulls() {
        val file = File(tmp.root, "q.json")
        val store = FileTaskStore(file)
        val a = task(1, TaskStatus.PAUSED, version = "Live", cover = "https://img/x.jpg").also { it.pendingUri = "content://media/9" }
        val b = task(2, TaskStatus.FAILED)
        store.save(listOf(a, b))

        val back = FileTaskStore(file).load()
        assertEquals(2, back.size)
        with(back[0]) {
            assertEquals("1:6", key); assertEquals(TaskStatus.PAUSED, status); assertEquals(7L, batchId)
            assertEquals("Live", track.version); assertEquals("https://img/x.jpg", album.coverUrl)
            assertEquals("Álbum \"raro\" / ñ", album.title); assertEquals(2, track.disc); assertEquals(245, track.durationSec)
            assertTrue(track.hiRes); assertEquals("content://media/9", pendingUri); assertNull(error)
        }
        with(back[1]) {
            assertNull(track.version); assertNull(album.coverUrl); assertEquals("HTTP 403", error); assertNull(pendingUri)
        }
    }

    @Test fun theTagDataSurvivesARestart() {
        val file = File(tmp.root, "q.json")
        val full = DownloadTask(
            key = "5:6", quality = Quality.LOSSLESS, batchId = 1,
            album = Album("a", "Álbum", "Artista", null, 11, "1991", "24/96", true, albumArtist = "Artista & Co", genre = "Rock",
                label = "Warner", upc = "0093624899927", copyright = "(c) 1991", mediaCount = 2, productType = "album",
                releaseDate = "1991-03-12", version = "Deluxe"),
            track = Track(5, "Canción", null, 41, 1, "Artista", 245, true, "24/96", true,
                isrc = "USWB19900001", composer = "Bill Berry", explicit = true, albumTrackNumber = 3),
        )
        FileTaskStore(file).save(listOf(full))
        val back = FileTaskStore(file).load().single()
        with(back.album) {
            assertEquals("Artista & Co", albumArtist); assertEquals("Rock", genre); assertEquals("Warner", label); assertEquals("0093624899927", upc)
            assertEquals("(c) 1991", copyright); assertEquals(2, mediaCount); assertEquals("album", productType)
            assertEquals("1991-03-12", releaseDate); assertEquals("Deluxe", version)
        }
        with(back.track) {
            assertEquals("USWB19900001", isrc); assertEquals("Bill Berry", composer); assertTrue(explicit)
            assertEquals("the playlist position and the album's own number are kept apart", 41, number)
            assertEquals(3, albumTrackNumber)
        }
        assertEquals("a restored queue tags the same as a fresh one", TrackTags.from(full.album, full.track).trackNumber, TrackTags.from(back.album, back.track).trackNumber)
    }

    @Test fun aQueueSavedByAnOlderVersionStillLoads() {
        // Exactly what 0.4.0 wrote: no tag fields at all.
        val legacy = """{"v":1,"tasks":[{"key":"7:6","status":"PENDING","quality":6,"batch":1,
            "album":{"id":"a","title":"T","artist":"A","tracksCount":10,"year":"2020","quality":"16/44","hiRes":false},
            "track":{"id":7,"title":"S","number":2,"disc":1,"artist":"A","durationSec":200,"streamable":true,"quality":"16/44","hiRes":false}}]}"""
        val t = FileTaskStore.parse(legacy).single()
        assertEquals("T", t.album.title); assertEquals("", t.album.genre); assertEquals(0, t.album.mediaCount)
        assertEquals("", t.track.isrc); assertFalse(t.track.explicit); assertEquals(0, t.track.albumTrackNumber)
        assertEquals("tags fall back to the track number it had", 2, TrackTags.from(t.album, t.track).trackNumber)
    }

    @Test fun saveLeavesNoTempFileAndOverwritesAtomically() {
        val file = File(tmp.root, "q.json")
        val store = FileTaskStore(file)
        store.save(listOf(task(1, TaskStatus.PENDING)))
        store.save(listOf(task(1, TaskStatus.COMPLETED), task(2, TaskStatus.PENDING)))
        assertEquals(2, store.load().size)
        assertTrue("temporary files are cleaned up", tmp.root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun saveCreatesMissingParentDirectories() {
        val file = File(tmp.root, "nested/queue/q.json")
        FileTaskStore(file).save(listOf(task(9, TaskStatus.PENDING)))
        assertEquals("9:6", FileTaskStore(file).load().single().key)
    }

    @Test fun failedReplacementNeverDeletesThePreviousDestination() {
        // A non-empty directory cannot be replaced by a file. The failed save must leave it intact.
        val destination = File(tmp.root, "q.json")
        assertTrue(destination.mkdir())
        val sentinel = File(destination, "keep-me.txt").also { it.writeText("previous data") }
        try {
            FileTaskStore(destination).save(listOf(task(1, TaskStatus.PENDING)))
            fail("saving over a non-empty directory must fail")
        } catch (_: IOException) {
            assertEquals("previous data", sentinel.readText())
        }
        assertTrue("temporary files are cleaned up", tmp.root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun missingOrCorruptFileMeansAnEmptyQueueNotACrash() {
        assertTrue(FileTaskStore(File(tmp.root, "nope.json")).load().isEmpty())
        val bad = tmp.newFile("bad.json").also { it.writeText("{ not json at all") }
        assertTrue(FileTaskStore(bad).load().isEmpty())
    }

    @Test fun anUnreadableFileIsBackedUpInsteadOfBeingSilentlyOverwritten() {
        val bad = tmp.newFile("q.json").also { it.writeText("{ half-written garbage") }
        assertTrue(FileTaskStore(bad).load().isEmpty())
        assertEquals("{ half-written garbage", File(tmp.root, "q.json.corrupt").readText())
    }

    @Test fun anEmptyFileIsJustAnEmptyQueueWithNoBackup() {
        val empty = tmp.newFile("e.json")
        assertTrue(FileTaskStore(empty).load().isEmpty())
        assertFalse(File(tmp.root, "e.json.corrupt").exists())
    }

    @Test fun aSingleUnreadableTaskIsSkippedWithoutLosingTheRest() {
        val good = FileTaskStore.serialize(listOf(task(1, TaskStatus.PENDING), task(2, TaskStatus.PENDING), task(3, TaskStatus.PENDING)))
        val json = org.json.JSONObject(good)
        json.getJSONArray("tasks").put(1, org.json.JSONObject().put("key", "broken")) // replaces the middle one; fields missing
        val back = FileTaskStore.parse(json.toString())
        assertEquals(listOf("1:6", "3:6"), back.map { it.key })
    }

    @Test fun serializedQueueHoldsOnlyTrackMetadata() {
        val text = FileTaskStore.serialize(listOf(task(1, TaskStatus.PENDING)))
        listOf("secret", "token", "password", "auth").forEach { assertFalse("leaks '$it'", text.lowercase().contains(it)) }
    }
}

class DownloadErrorsTest {
    @Test fun classifiesTheFailuresTheQueueMustTreatDifferently() {
        assertEquals(FailureKind.SIGNATURE, DownloadErrors.classify(ApiException("Invalid Request Signature parameter (request_sig)")))
        assertEquals(FailureKind.TRANSIENT, DownloadErrors.classify(IOException("Download failed: HTTP 503")))
        assertEquals(FailureKind.TRANSIENT, DownloadErrors.classify(IOException("Download failed: HTTP 429")))
        assertEquals(FailureKind.TRANSIENT, DownloadErrors.classify(java.net.UnknownHostException("www.qobuz.com")))
        assertEquals(FailureKind.TRANSIENT, DownloadErrors.classify(IOException("unexpected end of stream")))
        assertEquals(FailureKind.FATAL, DownloadErrors.classify(IOException("Download failed: HTTP 404")))
        assertEquals(FailureKind.FATAL, DownloadErrors.classify(ApiException("Qobuz returned no download URL")))
        assertEquals(FailureKind.FATAL, DownloadErrors.classify(StorageException("Could not create file in Music folder")))
        assertEquals(FailureKind.FATAL, DownloadErrors.classify(IllegalStateException("bug")))
        // A full disk can't be fixed by retrying (each retry would download the whole file again).
        assertEquals(FailureKind.FATAL, DownloadErrors.classify(IOException("write failed: ENOSPC (No space left on device)")))
    }
}

/** Runs the real HTTP copy loop against a local server, with no Android involved. */
class StreamCopierTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private fun body(size: Int) = Buffer().write(ByteArray(size) { (it % 251).toByte() })

    @Test fun copiesTheWholeBodyAndReportsProgressUpToOne() {
        server.enqueue(MockResponse().setBody(body(300_000)))
        val out = ByteArrayOutputStream()
        val progress = ArrayList<Float>()
        StreamCopier.copy(http, server.url("/f").toString(), CancelHandle(), { out }, { progress += it })
        assertEquals(300_000, out.size())
        assertEquals(1f, progress.last(), 0.0001f)
        assertTrue("progress never goes backwards", progress.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun httpErrorsSurfaceTheStatusAndNeverOpenTheOutputFile() {
        server.enqueue(MockResponse().setResponseCode(503))
        var opened = false
        try {
            StreamCopier.copy(http, server.url("/f").toString(), CancelHandle(), { opened = true; ByteArrayOutputStream() }, { })
            fail("expected an IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("HTTP 503"))
            assertEquals(FailureKind.TRANSIENT, DownloadErrors.classify(e))
        }
        assertFalse("no file may be created for a failed request", opened)
    }

    @Test fun cancellingMidTransferStopsTheInFlightCallPromptly() {
        // 400 KB trickled at ~20 KB/s would take ~20 s if it were not cancelled.
        server.enqueue(MockResponse().setBody(body(400_000)).throttleBody(20_000, 1, TimeUnit.SECONDS))
        val handle = CancelHandle()
        val out = ByteArrayOutputStream()
        val started = System.nanoTime()
        var failure: Throwable? = null
        val t = Thread {
            try {
                StreamCopier.copy(http, server.url("/f").toString(), handle, { out }, { p -> if (p > 0.02f) handle.cancel() })
            } catch (e: Throwable) { failure = e }
        }
        t.start(); t.join(8_000)

        assertFalse("copy must have ended", t.isAlive)
        assertTrue("took ${(System.nanoTime() - started) / 1_000_000} ms", System.nanoTime() - started < 6_000_000_000L)
        assertTrue(failure is IOException)
        assertTrue(handle.cancelled)
        assertTrue("stopped early, partial data only", out.size() < 400_000)
    }

    @Test fun truncatedBodiesAreAnErrorNotASilentShortFile() {
        server.enqueue(MockResponse().setBody(body(100_000)).setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        try {
            StreamCopier.copy(http, server.url("/f").toString(), CancelHandle(), { ByteArrayOutputStream() }, { })
            fail("a truncated download must not look successful")
        } catch (e: IOException) {
            assertEquals(FailureKind.TRANSIENT, DownloadErrors.classify(e))
        }
    }
}
