package com.qbdlx.mobile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** A scriptable stand-in for the network/MediaStore side, so the queue logic runs on a plain JVM. */
private class FakeWorker : TaskWorker {
    val running = AtomicInteger(0)
    val peak = AtomicInteger(0)
    val calls = ConcurrentHashMap<String, AtomicInteger>()
    val discarded = CopyOnWriteArrayList<String>()
    private val gates = ConcurrentHashMap<String, CountDownLatch>()
    private val sameKeyRunning = ConcurrentHashMap<String, AtomicInteger>()
    val sameKeyPeak = AtomicInteger(0)
    /** Behaviours consumed one per call, before the transfer starts; may throw. */
    val scripts = ConcurrentHashMap<String, CopyOnWriteArrayList<() -> Unit>>()
    @Volatile var reportPendingUri = false
    /** Tasks whose file "was already in the folder": the worker finishes without downloading. */
    val skipKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun callsFor(key: String) = calls[key]?.get() ?: 0
    fun hold(vararg keys: String) = keys.forEach { gates[it] = CountDownLatch(1) }
    fun release(key: String) { gates[key]?.countDown() }
    fun releaseAll() = gates.values.forEach { it.countDown() }
    fun script(key: String, vararg steps: () -> Unit) { scripts.getOrPut(key) { CopyOnWriteArrayList() }.addAll(steps) }

    override fun download(task: DownloadTask, handle: CancelHandle, listener: TaskListener) {
        val n = running.incrementAndGet()
        peak.updateAndGet { maxOf(it, n) }
        val k = sameKeyRunning.getOrPut(task.key) { AtomicInteger(0) }.incrementAndGet()
        sameKeyPeak.updateAndGet { maxOf(it, k) }
        try {
            calls.getOrPut(task.key) { AtomicInteger(0) }.incrementAndGet()
            if (task.key in skipKeys) { listener.onSkipped(); listener.onProgress(1f); return }
            if (reportPendingUri) listener.onPendingUri("content://media/${task.key}")
            scripts[task.key]?.let { if (it.isNotEmpty()) it.removeAt(0)() }
            gates[task.key]?.let { latch ->
                // What a cancelled OkHttp call does: the blocked transfer ends with an IOException.
                while (!latch.await(5, TimeUnit.MILLISECONDS)) if (handle.cancelled) throw DownloadCancelledException()
            }
            handle.throwIfCancelled()
            listener.onProgress(1f)
            if (reportPendingUri) listener.onPendingUri(null)
        } finally {
            sameKeyRunning[task.key]!!.decrementAndGet()
            running.decrementAndGet()
        }
    }

    override fun discard(uri: String) { discarded += uri }
}

private class MemoryStore : TaskStore {
    @Volatile var saved: List<DownloadTask> = emptyList()
    override fun load() = saved
    override fun save(tasks: List<DownloadTask>) { saved = tasks.toList() }
}

class DownloadManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var worker: FakeWorker

    @Before fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        worker = FakeWorker()
    }

    @After fun tearDown() { scope.cancel() }

    private fun album() = Album("a1", "Album", "Artist", null, 10, "2020", "16/44", false)
    private fun track(id: Long) = Track(id, "Song $id", null, id.toInt(), 1, "Artist", 200, true, "16/44", false)
    private fun items(vararg ids: Long) = ids.map { album() to track(it) }
    private fun key(id: Long, q: Quality = Quality.LOSSLESS) = "$id:${q.formatId}"

    private fun manager(
        limit: Int = 2,
        store: TaskStore = MemoryStore(),
        secret: () -> String = { "s" },
        backoff: List<Long> = listOf(1L, 1L, 1L),
        clock: () -> Long = System::currentTimeMillis,
        w: TaskWorker = worker,
        restore: Boolean = true,
    ) = DownloadManager(
        worker = w, store = store, scope = scope, secretVersion = secret,
        backoffMs = backoff, clock = clock, sleep = { },
    ).also { if (restore) it.restore(); it.maxConcurrent = limit; it.setEnabled(true) }

    private fun waitUntil(timeoutMs: Long = 10_000, what: String = "condition", cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Timed out waiting for $what")
            Thread.sleep(5)
        }
    }

    /** The task is DOWNLOADING as soon as it is scheduled; this waits until the transfer really began. */
    private fun waitStarted(id: Long, q: Quality = Quality.LOSSLESS) =
        waitUntil(what = "transfer of $id to begin") { worker.callsFor(key(id, q)) >= 1 }

    private fun DownloadManager.task(id: Long, q: Quality = Quality.LOSSLESS) = tasks.first { it.key == key(id, q) }
    private fun DownloadManager.count(s: TaskStatus) = tasks.count { it.status == s }
    private fun DownloadManager.allDone() = tasks.all { it.status == TaskStatus.COMPLETED }

    // ------------------------------------------------------------------ concurrency

    @Test fun neverRunsMoreTransfersThanTheLimit() {
        worker.hold(*(1L..10L).map { key(it) }.toTypedArray())
        val m = manager(limit = 3)
        m.enqueue(items(*(1L..10L).toList().toLongArray()), Quality.LOSSLESS)

        waitUntil(what = "3 running") { m.count(TaskStatus.DOWNLOADING) == 3 }
        assertEquals(7, m.count(TaskStatus.PENDING))
        Thread.sleep(100) // give a wrongly-eager scheduler time to overshoot
        assertEquals(3, m.count(TaskStatus.DOWNLOADING))

        worker.releaseAll()
        waitUntil(what = "all complete") { m.allDone() }
        assertEquals("limit must hold for the whole run", 3, worker.peak.get())
    }

    @Test fun limitOfOneRunsOneAtATime() {
        worker.hold(key(1), key(2), key(3))
        val m = manager(limit = 1)
        m.enqueue(items(1, 2, 3), Quality.LOSSLESS)
        waitUntil { m.task(1).status == TaskStatus.DOWNLOADING }
        assertEquals(TaskStatus.PENDING, m.task(2).status)
        worker.releaseAll()
        waitUntil { m.allDone() }
        assertEquals(1, worker.peak.get())
    }

    @Test fun raisingTheLimitStartsMoreAndLoweringItNeverKillsRunningOnes() {
        worker.hold(*(1L..6L).map { key(it) }.toTypedArray())
        val m = manager(limit = 2)
        m.enqueue(items(1, 2, 3, 4, 5, 6), Quality.LOSSLESS)
        waitUntil { m.count(TaskStatus.DOWNLOADING) == 2 }

        m.maxConcurrent = 5
        waitUntil(what = "5 running") { m.count(TaskStatus.DOWNLOADING) == 5 }

        m.maxConcurrent = 1 // lower: the 5 running keep going, nothing new starts
        Thread.sleep(80)
        assertEquals(5, m.count(TaskStatus.DOWNLOADING))
        assertEquals(1, m.count(TaskStatus.PENDING))

        worker.release(key(1)); worker.release(key(2)); worker.release(key(3)); worker.release(key(4))
        waitUntil { m.count(TaskStatus.COMPLETED) == 4 }
        Thread.sleep(80)
        assertEquals("below the new limit of 1 only after all but one finished", TaskStatus.PENDING, m.task(6).status)
        worker.releaseAll()
        waitUntil { m.allDone() }
    }

    // ------------------------------------------------------------------ duplicates

    @Test fun duplicatesAreNotQueuedTwice() {
        worker.hold(key(1))
        val m = manager()
        val first = m.enqueue(items(1, 1, 2), Quality.LOSSLESS) // duplicate inside one request
        assertEquals(2, first.added); assertEquals(1, first.skipped)

        val again = m.enqueue(items(1, 2), Quality.LOSSLESS) // already queued/running
        assertEquals(0, again.added); assertEquals(2, again.skipped)

        worker.releaseAll(); waitUntil { m.allDone() }
        val done = m.enqueue(items(1), Quality.LOSSLESS) // already downloaded
        assertEquals(0, done.added); assertEquals(1, done.skipped)

        val other = m.enqueue(items(1), Quality.MP3) // a different quality is a different file
        assertEquals(1, other.added)
        assertEquals(3, m.tasks.size)
    }

    @Test fun failedAndCanceledTracksCanBeQueuedAgain() {
        worker.script(key(1), { throw ApiException("Qobuz returned no download URL") })
        val m = manager()
        m.enqueue(items(1), Quality.LOSSLESS)
        waitUntil { m.task(1).status == TaskStatus.FAILED }
        val r = m.enqueue(items(1), Quality.LOSSLESS)
        assertEquals(1, r.added)
        assertEquals("revived in place, not duplicated", 1, m.tasks.size)
        waitUntil { m.allDone() }
    }

    // ------------------------------------------------------------------ pause / resume / cancel / retry / delete

    @Test fun pausingARunningTrackFreesItsSlotAndResumeRestartsIt() {
        worker.hold(key(1), key(2))
        val m = manager(limit = 1)
        m.enqueue(items(1, 2), Quality.LOSSLESS)
        waitUntil { m.task(1).status == TaskStatus.DOWNLOADING }
        waitStarted(1)

        m.pause(m.task(1))
        assertEquals(TaskStatus.PAUSED, m.task(1).status)
        waitUntil(what = "next track takes the slot") { m.task(2).status == TaskStatus.DOWNLOADING }
        assertNull("a pause is not a failure", m.task(1).error)
        assertEquals(TaskStatus.PAUSED, m.task(1).status)

        worker.release(key(2))
        waitUntil { m.task(2).status == TaskStatus.COMPLETED }
        worker.release(key(1)) // open the gate for the restart
        m.resume(m.task(1))
        waitUntil { m.task(1).status == TaskStatus.COMPLETED }
        assertEquals("paused track restarts from the beginning", 2, worker.callsFor(key(1)))
    }

    /**
     * Regression: pausing/cancelling a track in the instant after it starts used to cancel its
     * coroutine before the body ran, so the cleanup never executed and the slot leaked forever
     * (with a limit of 1 the whole queue stalled). It only failed some of the time, hence the loop.
     */
    @Test fun pausingTheInstantATrackStartsNeverLeaksItsSlot() {
        repeat(300) { i ->
            val w = FakeWorker()
            w.hold(key(1), key(2))
            val m = manager(limit = 1, w = w)
            m.enqueue(items(1, 2), Quality.LOSSLESS)
            waitUntil(what = "track 1 started (round $i)") { m.task(1).status == TaskStatus.DOWNLOADING }
            if (i % 2 == 0) m.pause(m.task(1)) else m.cancel(m.task(1))
            waitUntil(5_000, "track 2 takes the freed slot (round $i)") { m.task(2).status == TaskStatus.DOWNLOADING }
            w.releaseAll()
            waitUntil(what = "round $i finishes") { m.task(2).status == TaskStatus.COMPLETED }
        }
    }

    @Test fun pausingAPendingTrackKeepsItFromStarting() {
        worker.hold(key(1), key(2))
        val m = manager(limit = 1)
        m.enqueue(items(1, 2), Quality.LOSSLESS)
        waitUntil { m.task(1).status == TaskStatus.DOWNLOADING }
        m.pause(m.task(2))
        worker.release(key(1))
        waitUntil { m.task(1).status == TaskStatus.COMPLETED }
        Thread.sleep(80)
        assertEquals(TaskStatus.PAUSED, m.task(2).status)
        assertEquals(0, worker.callsFor(key(2)))
        worker.release(key(2)); m.resume(m.task(2))
        waitUntil { m.task(2).status == TaskStatus.COMPLETED }
    }

    @Test fun cancelIsNotAFailureAndIsNeverRetried() {
        worker.hold(key(1))
        val m = manager()
        m.enqueue(items(1), Quality.LOSSLESS)
        waitUntil { m.task(1).status == TaskStatus.DOWNLOADING }
        waitStarted(1)

        m.cancel(m.task(1))
        waitUntil(what = "transfer unwound") { worker.running.get() == 0 }
        Thread.sleep(150) // an auto-retry (backoff is ~1 ms here) would have shown up by now
        assertEquals(TaskStatus.CANCELED, m.task(1).status)
        assertNull(m.task(1).error)
        assertEquals(1, worker.callsFor(key(1)))

        worker.releaseAll()
        m.retry(m.task(1))
        waitUntil { m.task(1).status == TaskStatus.COMPLETED }
    }

    @Test fun retryRerunsAFailedTrack() {
        worker.script(key(1), { throw ApiException("Qobuz returned no download URL (track not streamable on this account?)") })
        val m = manager()
        m.enqueue(items(1), Quality.LOSSLESS)
        waitUntil { m.task(1).status == TaskStatus.FAILED }
        assertTrue(m.task(1).error!!.contains("no download URL"))
        assertEquals("fatal errors are not retried automatically", 1, worker.callsFor(key(1)))

        m.retry(m.task(1))
        waitUntil { m.task(1).status == TaskStatus.COMPLETED }
        assertNull(m.task(1).error)
    }

    @Test fun deleteRemovesTheEntryAndStopsARunningTransfer() {
        worker.hold(key(1), key(2))
        val m = manager(limit = 1)
        m.enqueue(items(1, 2), Quality.LOSSLESS)
        waitUntil { m.task(1).status == TaskStatus.DOWNLOADING }

        m.delete(m.task(1))
        assertTrue(m.tasks.none { it.key == key(1) })
        waitUntil(what = "slot reused") { m.task(2).status == TaskStatus.DOWNLOADING }
        // The deleted transfer may still be unwinding for a moment when the next one starts; once it has, only one runs.
        waitUntil(what = "deleted transfer unwound") { worker.running.get() == 1 }
        Thread.sleep(100)
        assertEquals(1, worker.running.get())
        worker.releaseAll(); waitUntil { m.allDone() }
    }

    @Test fun pauseAllResumeAllAndClearCompleted() {
        worker.hold(key(1), key(2), key(3))
        val m = manager(limit = 2)
        m.enqueue(items(1, 2, 3), Quality.LOSSLESS)
        waitUntil { m.count(TaskStatus.DOWNLOADING) == 2 }

        m.pauseAll()
        assertEquals(3, m.count(TaskStatus.PAUSED))
        waitUntil { worker.running.get() == 0 }

        worker.releaseAll()
        m.resumeAll()
        waitUntil { m.allDone() }
        m.clearCompleted()
        assertTrue(m.tasks.isEmpty())
    }

    @Test fun aQuickPauseThenResumeNeverRunsTheSameTrackTwice() {
        worker.hold(key(1))
        val m = manager(limit = 2)
        m.enqueue(items(1), Quality.LOSSLESS)
        waitUntil { m.task(1).status == TaskStatus.DOWNLOADING }
        repeat(5) { m.pause(m.task(1)); m.resume(m.task(1)) }
        worker.releaseAll()
        waitUntil { m.allDone() }
        assertEquals(1, worker.sameKeyPeak.get())
    }

    // ------------------------------------------------------------------ skip files that already exist

    @Test fun aTrackThatWasAlreadyInTheFolderCompletesWithoutBeingDownloadedAndSaysSo() {
        worker.skipKeys += key(2)
        val m = manager(limit = 2)
        m.enqueue(items(1, 2, 3), Quality.LOSSLESS)
        waitUntil { m.allDone() }

        assertEquals("completed, not failed", TaskStatus.COMPLETED, m.task(2).status)
        assertEquals(TaskNote.EXISTED, m.task(2).note)
        assertEquals(1f, m.task(2).progress, 0f)
        assertNull(m.task(2).error)
        assertEquals("a normal track carries no such note", TaskNote.NONE, m.task(1).note)
        assertEquals(3, m.summary().done)
    }

    @Test fun aSkippedTrackKeepsItsSlotOnlyForAMoment() {
        worker.skipKeys += (1L..6L).map { key(it) }
        val m = manager(limit = 1)
        m.enqueue(items(1, 2, 3, 4, 5, 6), Quality.LOSSLESS)
        waitUntil { m.allDone() }
        assertEquals(6, m.count(TaskStatus.COMPLETED))
        assertTrue((1L..6L).all { m.task(it).note == TaskNote.EXISTED })
    }

    @Test fun aSkippedTrackIsNotQueuedAgainAndRetryingIsNotOffered() {
        worker.skipKeys += key(1)
        val m = manager()
        m.enqueue(items(1), Quality.LOSSLESS)
        waitUntil { m.allDone() }
        assertEquals("it counts as downloaded for the duplicate check", 1, m.enqueue(items(1), Quality.LOSSLESS).skipped)
    }

    // ------------------------------------------------------------------ errors and retries

    @Test fun transientErrorsRetryWithBackoffThenSucceed() {
        worker.script(key(1), { throw IOException("timeout") }, { throw java.net.SocketTimeoutException("slow") })
        val m = manager()
        m.enqueue(items(1), Quality.LOSSLESS)
        waitUntil { m.allDone() }
        assertEquals(3, worker.callsFor(key(1)))
    }

    @Test fun transientErrorsGiveUpAfterTheBackoffListIsUsedUp() {
        worker.script(key(1), *Array(10) { { throw IOException("connection reset") } })
        val m = manager(backoff = listOf(1L, 1L))
        m.enqueue(items(1), Quality.LOSSLESS)
        waitUntil { m.task(1).status == TaskStatus.FAILED }
        assertEquals("first attempt + 2 retries", 3, worker.callsFor(key(1)))
        assertTrue(m.task(1).error!!.contains("connection reset"))
    }

    @Test fun httpServerErrorsRetryButClientErrorsDoNot() {
        worker.script(key(1), { throw IOException("Download failed: HTTP 503") })
        worker.script(key(2), *Array(5) { { throw IOException("Download failed: HTTP 404") } })
        val m = manager(limit = 2)
        m.enqueue(items(1, 2), Quality.LOSSLESS)
        waitUntil { m.task(1).status == TaskStatus.COMPLETED && m.task(2).status == TaskStatus.FAILED }
        assertEquals(2, worker.callsFor(key(1)))
        assertEquals(1, worker.callsFor(key(2)))
    }

    // ------------------------------------------------------------------ credential auto-refresh

    private val badSig = { throw ApiException("Invalid Request Signature parameter (request_sig)") }

    @Test fun threeTracksFailingTogetherCauseExactlyOneRefreshThenAllRetry() {
        val secret = java.util.concurrent.atomic.AtomicReference("old")
        val refreshes = AtomicInteger(0)
        val refreshedEvents = AtomicInteger(0)
        (1L..3L).forEach { worker.script(key(it), badSig) } // first call of each fails; retries succeed
        val m = manager(limit = 3, secret = { secret.get() })
        m.refresher = { refreshes.incrementAndGet(); Thread.sleep(150); secret.set("new") }
        m.onCredentialsRefreshed = { refreshedEvents.incrementAndGet() }
        m.enqueue(items(1, 2, 3), Quality.LOSSLESS)

        waitUntil(what = "all retried and completed") { m.allDone() }
        assertEquals("one shared refresh, not one per track", 1, refreshes.get())
        assertEquals(1, refreshedEvents.get())
        (1L..3L).forEach { assertEquals(2, worker.callsFor(key(it))) }
    }

    @Test fun aFailedRefreshFailsTheTrackWithThatReasonAndDoesNotLoop() {
        val refreshes = AtomicInteger(0)
        (1L..3L).forEach { worker.script(key(it), *Array(5) { badSig }) }
        val m = manager(limit = 3, clock = { 1_000L })
        m.refresher = { refreshes.incrementAndGet(); throw ApiException("Ningún App Secret reconstruido fue aceptado") }
        m.enqueue(items(1, 2, 3), Quality.LOSSLESS)

        waitUntil { m.count(TaskStatus.FAILED) == 3 }
        assertEquals("cooldown stops every queued track re-downloading the 9 MB bundle", 1, refreshes.get())
        assertTrue(m.task(1).error!!.contains("Ningún App Secret"))
        (1L..3L).forEach { assertEquals("no retry after a failed refresh", 1, worker.callsFor(key(it))) }
    }

    @Test fun aTrackIsRetriedAfterARefreshAtMostOnce() {
        val secret = java.util.concurrent.atomic.AtomicReference("old")
        worker.script(key(1), *Array(5) { badSig }) // the secret never actually fixes it
        val m = manager(secret = { secret.get() })
        m.refresher = { secret.set("new" + System.nanoTime()) }
        m.enqueue(items(1), Quality.LOSSLESS)
        waitUntil { m.task(1).status == TaskStatus.FAILED }
        assertEquals(2, worker.callsFor(key(1)))
    }

    @Test fun withoutARefresherABadSignatureJustFails() {
        worker.script(key(1), badSig)
        val m = manager()
        m.enqueue(items(1), Quality.LOSSLESS)
        waitUntil { m.task(1).status == TaskStatus.FAILED }
        assertTrue(m.task(1).error!!.contains("request_sig"))
    }

    @Test fun ifTheSecretAlreadyChangedTheTrackRetriesWithoutAnotherRefresh() {
        val secret = java.util.concurrent.atomic.AtomicReference("old")
        val refreshes = AtomicInteger(0)
        // The track signed with "old"; by the time it fails, someone has already moved to "new".
        worker.script(key(1), { secret.set("new"); throw ApiException("Invalid Request Signature parameter (request_sig)") })
        val m = manager(secret = { secret.get() })
        m.refresher = { refreshes.incrementAndGet() }
        m.enqueue(items(1), Quality.LOSSLESS)
        waitUntil { m.allDone() }
        assertEquals(0, refreshes.get())
    }

    // ------------------------------------------------------------------ session / lifecycle

    @Test fun loggingOutReturnsRunningTracksToPendingAndLoggingInResumesThem() {
        worker.hold(key(1), key(2))
        val m = manager(limit = 2)
        m.enqueue(items(1, 2), Quality.LOSSLESS)
        waitUntil { m.count(TaskStatus.DOWNLOADING) == 2 }

        m.setEnabled(false)
        assertEquals(2, m.count(TaskStatus.PENDING))
        waitUntil { worker.running.get() == 0 }
        Thread.sleep(100)
        assertEquals("not failed, not retried while logged out", 2, m.count(TaskStatus.PENDING))

        worker.releaseAll()
        m.setEnabled(true)
        waitUntil { m.allDone() }
    }

    @Test fun activityCallbackFiresWhenWorkStartsAndEnds() {
        worker.hold(key(1))
        val events = CopyOnWriteArrayList<Boolean>()
        val m = manager()
        m.onActiveChanged = { events += it }
        m.enqueue(items(1), Quality.LOSSLESS)
        waitUntil { events.contains(true) }
        worker.releaseAll()
        waitUntil { events.contains(false) }
        assertEquals(listOf(true, false), events.toList())
    }

    @Test fun globalProgressAveragesTheCurrentBatchAndFailedCountsAsFinished() {
        worker.script(key(3), { throw ApiException("Qobuz returned no download URL") })
        worker.hold(key(2))
        val m = manager(limit = 3)
        m.enqueue(items(1, 2, 3), Quality.LOSSLESS)
        waitUntil { m.task(1).status == TaskStatus.COMPLETED && m.task(3).status == TaskStatus.FAILED }
        val s = m.summary()
        assertEquals(3, s.total); assertEquals(1, s.done); assertEquals(1, s.failed)
        assertEquals(2f / 3f, s.overall, 0.01f)
        worker.releaseAll(); waitUntil { m.task(2).status == TaskStatus.COMPLETED }
        assertEquals(1f, m.summary().overall, 0.001f)
    }

    // ------------------------------------------------------------------ persistence and recovery

    @Test fun theQueueIsRestoredAfterARestartAndInterruptedTracksResume() {
        val file = tmp.newFile("queue.json")
        val store = FileTaskStore(file)
        worker.reportPendingUri = true
        worker.hold(key(1), key(2), key(3), key(4))
        val m1 = manager(limit = 1, store = store)
        m1.enqueue(items(1, 2, 3, 4), Quality.LOSSLESS)
        waitUntil { m1.task(1).status == TaskStatus.DOWNLOADING }
        m1.pause(m1.task(4))                      // user-paused: must stay paused after restart
        m1.cancel(m1.task(3))                     // canceled: must stay canceled
        waitUntil { worker.callsFor(key(1)) == 1 }
        waitUntil(what = "the half-written file is reported") { m1.task(1).pendingUri != null }
        m1.flush()

        // "Restart": a brand-new manager, a brand-new worker, the same file.
        scope.cancel()
        worker.releaseAll() // let the old process's blocked threads end; its save loop is already dead
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val worker2 = FakeWorker()
        val m2 = manager(limit = 2, store = FileTaskStore(file), w = worker2, restore = false).also { it.setEnabled(false) }
        m2.restore()

        assertEquals(4, m2.tasks.size)
        assertEquals("was downloading → pending", TaskStatus.PENDING, m2.task(1).status)
        assertEquals(TaskStatus.PENDING, m2.task(2).status)
        assertEquals(TaskStatus.CANCELED, m2.task(3).status)
        assertEquals(TaskStatus.PAUSED, m2.task(4).status)
        assertEquals("half-written file of the interrupted track is cleaned up", listOf("content://media/${key(1)}"), worker2.discarded.toList())
        assertEquals("nothing runs until the session is back", 0, worker2.callsFor(key(1)))

        m2.setEnabled(true)
        waitUntil(what = "recovered tracks finish") { m2.task(1).status == TaskStatus.COMPLETED && m2.task(2).status == TaskStatus.COMPLETED }
        assertEquals(TaskStatus.PAUSED, m2.task(4).status)
        assertEquals(TaskStatus.CANCELED, m2.task(3).status)
    }

    @Test fun completedAndFailedHistorySurvivesARestart() {
        val file = tmp.newFile("history.json")
        worker.script(key(2), { throw ApiException("Qobuz returned no download URL") })
        val m1 = manager(store = FileTaskStore(file))
        m1.enqueue(items(1, 2), Quality.LOSSLESS)
        waitUntil { m1.task(1).status == TaskStatus.COMPLETED && m1.task(2).status == TaskStatus.FAILED }
        m1.flush()

        val m2 = manager(store = FileTaskStore(file), w = FakeWorker(), restore = false)
        m2.restore()
        assertEquals(TaskStatus.COMPLETED, m2.task(1).status)
        assertEquals(1f, m2.task(1).progress, 0f)
        assertEquals(TaskStatus.FAILED, m2.task(2).status)
        assertNotNull(m2.task(2).error)
    }

    /**
     * Regression: a fresh manager has an empty queue, and its background saver used to write that
     * empty queue over the file before restore() read it, so the saved queue was lost on restart.
     */
    @Test fun aFreshManagerNeverOverwritesTheSavedQueueBeforeRestoringIt() {
        val file = tmp.newFile("saved.json")
        val m1 = manager(limit = 1, store = FileTaskStore(file))
        worker.hold(key(1), key(2))
        m1.enqueue(items(1, 2), Quality.LOSSLESS)
        waitUntil { m1.task(1).status == TaskStatus.DOWNLOADING }
        m1.flush()
        assertEquals(2, FileTaskStore(file).load().size)

        // A new process: configure the manager exactly as the app does, and give the saver time to run.
        scope.cancel()
        worker.releaseAll()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val m2 = manager(limit = 3, store = FileTaskStore(file), w = FakeWorker(), restore = false)
        m2.maxConcurrent = 5
        m2.setEnabled(false)
        Thread.sleep(300)
        assertEquals("the saved queue must survive until it is restored", 2, FileTaskStore(file).load().size)

        m2.restore()
        assertEquals(2, m2.tasks.size)
    }

    @Test fun restoreKeepsTracksQueuedBeforeItFinishedAndPutsRestoredOnesFirst() {
        val file = tmp.newFile("merge.json")
        val m1 = manager(store = FileTaskStore(file))
        m1.enqueue(items(1, 2), Quality.LOSSLESS)
        waitUntil { m1.allDone() }
        m1.flush()

        val m2 = manager(store = FileTaskStore(file), w = FakeWorker(), restore = false)
        m2.enqueue(items(9), Quality.LOSSLESS)   // queued before the saved queue was loaded
        m2.restore()
        assertEquals(listOf(key(1), key(2), key(9)), m2.tasks.map { it.key })
    }

    @Test fun savedQueueNeverContainsTheSecretOrToken() {
        val store = MemoryStore()
        val m = manager(store = store, secret = { "SUPERSECRETVALUE" })
        m.enqueue(items(1), Quality.LOSSLESS)
        waitUntil { m.allDone() }
        m.flush()
        assertFalse(FileTaskStore.serialize(store.saved).contains("SUPERSECRETVALUE"))
    }
}
