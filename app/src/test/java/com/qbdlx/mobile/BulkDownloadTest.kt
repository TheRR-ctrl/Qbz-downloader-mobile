package com.qbdlx.mobile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** "Descargar todo" for albums and playlists: resolve one source at a time, queue as you go, never abort for one failure. */
class BulkDownloadTest {
    private fun album(id: String) = Album(id, "Album $id", "Artist", null, 3, "2020", "16/44", false)
    private fun track(id: Long, streamable: Boolean = true) = Track(id, "Song $id", null, id.toInt(), 1, "Artist", 200, streamable, "16/44", false)
    private fun tracks(albumId: String, vararg ids: Long) = ids.map { album(albumId) to track(it) }

    @Test fun eachSourceIsQueuedBeforeTheNextOneIsLookedUp() = runBlocking {
        val events = ArrayList<String>()
        val result = enqueueBulk(
            sources = listOf("a", "b", "c"),
            resolve = { events += "resolve $it"; tracks(it, 1, 2) },
            enqueue = { events += "enqueue ${it.first().first.id}"; EnqueueResult(it.size, 0) },
        )
        assertEquals("downloads start while the rest is still being looked up",
            listOf("resolve a", "enqueue a", "resolve b", "enqueue b", "resolve c", "enqueue c"), events)
        assertEquals(6, result.added); assertEquals(0, result.skipped); assertEquals(0, result.failedSources); assertEquals(3, result.sources)
    }

    @Test fun oneSourceThatFailsDoesNotStopTheOthers() = runBlocking {
        val queued = ArrayList<String>()
        val result = enqueueBulk(
            sources = listOf("a", "bad", "c", "worse", "e"),
            resolve = { if (it == "bad" || it == "worse") throw IOException("HTTP 503") else tracks(it, 1) },
            enqueue = { queued += it.first().first.id; EnqueueResult(it.size, 0) },
        )
        assertEquals(listOf("a", "c", "e"), queued)
        assertEquals(3, result.added); assertEquals(2, result.failedSources); assertEquals(5, result.sources)
    }

    @Test fun tracksQobuzDoesNotAllowToStreamAreLeftOut() = runBlocking {
        val seen = ArrayList<Int>()
        val result = enqueueBulk(
            sources = listOf("a"),
            resolve = { listOf(album("a") to track(1), album("a") to track(2, streamable = false), album("a") to track(3)) },
            enqueue = { seen += it.size; EnqueueResult(it.size, 0) },
        )
        assertEquals(listOf(2), seen); assertEquals(2, result.added)
    }

    @Test fun aSourceWithNothingToDownloadQueuesNothing() = runBlocking {
        var calls = 0
        val result = enqueueBulk(
            sources = listOf("empty", "allBlocked"),
            resolve = { if (it == "empty") emptyList() else listOf(album(it) to track(1, streamable = false)) },
            enqueue = { calls++; EnqueueResult(it.size, 0) },
        )
        assertEquals("no enqueue call for an empty list", 0, calls)
        assertEquals(0, result.added); assertEquals(0, result.failedSources)
    }

    @Test fun addedAndAlreadyQueuedAreCountedSeparately() = runBlocking {
        val result = enqueueBulk(
            sources = listOf("a", "b"),
            resolve = { tracks(it, 1, 2, 3) },
            enqueue = { if (it.first().first.id == "a") EnqueueResult(3, 0) else EnqueueResult(1, 2) },
        )
        assertEquals(4, result.added); assertEquals(2, result.skipped)
    }

    @Test fun aCancellationIsNeverSwallowedAsAFailedSource() {
        try {
            runBlocking {
                enqueueBulk(listOf("a", "b"), resolve = { throw CancellationException("stop") }, enqueue = { EnqueueResult(it.size, 0) })
            }
            fail("a cancellation must propagate")
        } catch (e: CancellationException) {
            assertTrue(e.message!!.contains("stop"))
        }
    }

    @Test fun anEmptyRequestIsHarmless() = runBlocking {
        val r = enqueueBulk(emptyList<String>(), resolve = { tracks(it, 1) }, enqueue = { EnqueueResult(it.size, 0) })
        assertEquals(0, r.sources); assertEquals(0, r.added)
    }
}
