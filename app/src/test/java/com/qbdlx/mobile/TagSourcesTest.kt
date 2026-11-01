package com.qbdlx.mobile

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Cover and album-detail fetching: a couple of retries for fixable failures, never for permanent ones. */
class TagSourcesTest {
    private lateinit var server: MockWebServer
    private val sleeps = CopyOnWriteArrayList<Long>()
    private val logs = CopyOnWriteArrayList<String>()
    @Volatile private var now = 1_000L

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    /** SOI + APP0 + SOF0 (400x300) + filler: enough for the cover sniffer and the size parser. */
    private fun jpeg(): ByteArray {
        val head = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0,
            1, 1, 0, 0, 1, 0, 1, 0, 0,
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 8, 0x01, 0x2C, 0x01, 0x90.toByte(), 3,
        )
        return head + ByteArray(40) { it.toByte() }
    }

    private fun ok() = MockResponse().setBody(Buffer().write(jpeg()))
    private fun url() = server.url("/images/covers/abc_600.jpg").toString()
    private fun sources(loader: (String) -> Album = { throw AssertionError("no album lookup expected") }) = TagSources(
        http = OkHttpClient(), loadAlbum = loader,
        sleep = { sleeps += it }, clock = { now }, log = { logs += it },
    )

    private fun album() = Album("a1", "Title", "Artist", "https://img/c.jpg", 10, "2020", "16/44", false)
    private fun detailed() = album().copy(genre = "Rock", label = "Label", upc = "123", copyright = "(c)", mediaCount = 2, albumArtist = "Artist")

    // ------------------------------------------------------------------ cover

    @Test fun aFixableCoverFailureIsRetriedAndThenSucceeds() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(ok())

        val cover = sources().coverFor(url())

        assertNotNull("third attempt works", cover)
        assertEquals(3, server.requestCount)
        assertEquals("waited 0.5 s then 1.5 s between attempts", 2_000L, sleeps.sum())
        assertEquals("image/jpeg", cover!!.mime)
        assertEquals(400, cover.width); assertEquals(300, cover.height)
        assertTrue("a recovered cover is not an event worth logging", logs.isEmpty())
    }

    @Test fun itGivesUpAfterOneTryPlusTwoRetriesAndSaysSo() {
        repeat(6) { server.enqueue(MockResponse().setResponseCode(503)) }

        assertNull(sources().coverFor(url()))

        assertEquals("1 try + 2 retries, not more", 3, server.requestCount)
        assertEquals(1, logs.size)
        assertTrue(logs[0], logs[0].contains("3 intentos") && logs[0].contains("HTTP 503"))
    }

    @Test fun aDroppedConnectionStillGetsTheCover() {
        // OkHttp may retry a dropped connection by itself before our retry does; either way the cover must arrive.
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(ok())
        assertNotNull(sources().coverFor(url()))
        assertTrue("it asked again after the drop", server.requestCount >= 2)
    }

    @Test fun tooManyRequestsIsRetried() {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(ok())
        assertNotNull(sources().coverFor(url()))
        assertEquals(2, server.requestCount)
    }

    @Test fun permanentErrorsAreNotRetried() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(sources().coverFor(url()))
        assertEquals("a 404 will not fix itself", 1, server.requestCount)
        assertEquals(0L, sleeps.sum())
    }

    @Test fun aBodyThatIsNotAnImageIsNotRetriedAndNeverEmbedded() {
        server.enqueue(MockResponse().setBody("<html><body>Service unavailable</body></html>"))
        assertNull(sources().coverFor(url()))
        assertEquals(1, server.requestCount)
    }

    @Test fun aBlankUrlNeedsNoRequest() {
        assertNull(sources().coverFor(null))
        assertNull(sources().coverFor("  "))
        assertEquals(0, server.requestCount)
    }

    @Test fun aFailureIsRememberedForAMinuteAndThenTriedAgain() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503)) }
        val s = sources()
        assertNull(s.coverFor(url()))
        assertEquals(3, server.requestCount)

        now += 10_000
        assertNull("the next track of the album does not spend seconds retrying again", s.coverFor(url()))
        assertEquals(3, server.requestCount)

        now += 60_000
        server.enqueue(ok())
        assertNotNull("after a minute it is worth another go", s.coverFor(url()))
        assertEquals(4, server.requestCount)
    }

    @Test fun aSuccessIsKept() {
        server.enqueue(ok())
        val s = sources()
        val first = s.coverFor(url())
        now += 3_600_000
        assertSame(first, s.coverFor(url()))
        assertEquals(1, server.requestCount)
    }

    @Test fun tracksOfTheSameAlbumStartingTogetherMakeOneRequest() {
        repeat(8) { server.enqueue(ok().setBodyDelay(300, TimeUnit.MILLISECONDS)) }
        val s = sources()
        val pool = Executors.newFixedThreadPool(8)
        val go = CountDownLatch(1)
        val results = (1..8).map { pool.submit<Cover?> { go.await(); s.coverFor(url()) } }
        go.countDown()
        val covers = results.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals("one fetch, shared", 1, server.requestCount)
        assertTrue(covers.all { it != null })
    }

    @Test fun aCancelDoesNotRetryAndIsNotRememberedAsAFailure() {
        server.enqueue(MockResponse().setResponseCode(503))
        val s = sources()
        assertNull(s.coverFor(url()) { true })
        assertEquals("no retry once the download was cancelled", 1, server.requestCount)
        assertTrue("and nothing worth logging", logs.isEmpty())

        server.enqueue(ok())
        assertNotNull("the next track must not inherit a failure that never happened", s.coverFor(url()))
        assertEquals(2, server.requestCount)
    }

    @Test fun aCancelDuringTheWaitStopsRetrying() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(ok())
        val asked = AtomicInteger(0)
        // Not cancelled for the first checks, cancelled once it is waiting to retry.
        assertNull(sources().coverFor(url()) { asked.incrementAndGet() > 2 })
        assertEquals(1, server.requestCount)
    }

    // ------------------------------------------------------------------ album details

    @Test fun albumDetailsAreRetriedOnTransientErrors() {
        val calls = AtomicInteger(0)
        val result = sources { _ ->
            if (calls.incrementAndGet() <= 2) throw IOException("timeout") else detailed()
        }.detailedAlbum(album())

        assertEquals(3, calls.get())
        assertEquals("Rock", result.genre)
        assertEquals("Label", result.label)
    }

    @Test fun albumDetailsGiveUpAfterThreeAttemptsAndKeepTheAlbumAsItWas() {
        val calls = AtomicInteger(0)
        val original = album()
        val result = sources { _ -> calls.incrementAndGet(); throw IOException("Download failed: HTTP 503") }.detailedAlbum(original)

        assertEquals(3, calls.get())
        assertEquals("fewer tags, never a failure", original, result)
        assertEquals(1, logs.size)
    }

    @Test fun anAlbumThatDoesNotExistIsNotRetried() {
        val calls = AtomicInteger(0)
        sources { _ -> calls.incrementAndGet(); throw ApiException("No album found") }.detailedAlbum(album())
        assertEquals(1, calls.get())
    }

    @Test fun aCompleteAlbumNeverTriggersALookup() {
        val calls = AtomicInteger(0)
        val full = detailed()
        assertSame(full, sources { _ -> calls.incrementAndGet(); detailed() }.detailedAlbum(full))
        assertEquals(0, calls.get())
    }

    @Test fun aPartialAlbumWithJustGenreAndLabelStillGetsTheRestFromTheFullAlbum() {
        val calls = AtomicInteger(0)
        val partial = album().copy(genre = "Rock", label = "Label") // what a playlist entry might carry
        val result = sources { _ -> calls.incrementAndGet(); detailed() }.detailedAlbum(partial)

        assertEquals("still needs the lookup for barcode, discs and copyright", 1, calls.get())
        assertEquals("0 -> 2", 2, result.mediaCount)
        assertEquals("123", result.upc)
        assertEquals("(c)", result.copyright)
        assertEquals("what it already had is kept", "Rock", result.genre)
    }

    @Test fun albumDetailsAreFetchedOncePerAlbumEvenWhenManyTracksAsk() {
        val calls = AtomicInteger(0)
        val s = sources { _ -> calls.incrementAndGet(); Thread.sleep(150); detailed() }
        val pool = Executors.newFixedThreadPool(6)
        val go = CountDownLatch(1)
        val futures = (1..6).map { pool.submit<Album> { go.await(); s.detailedAlbum(album()) } }
        go.countDown()
        val results = futures.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(1, calls.get())
        assertTrue(results.all { it.genre == "Rock" })
    }

    @Test fun fillingNeverOverwritesWhatTheListingAlreadyHad() {
        val partial = album().copy(genre = "Jazz", albumArtist = "Listed Artist")
        val merged = partial.fillFrom(detailed().copy(genre = "Rock", albumArtist = "Other", label = "Label"))
        assertEquals("Jazz", merged.genre)
        assertEquals("Listed Artist", merged.albumArtist)
        assertEquals("filled where it was blank", "Label", merged.label)
        assertEquals("Title", merged.title)
    }
}
