package com.qbdlx.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

enum class TaskStatus {
    PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELED;

    /** Still has work left to do (a paused task is waiting for the user, but not finished). */
    val isOpen: Boolean get() = this == PENDING || this == DOWNLOADING || this == PAUSED
}

/** A short, language-neutral hint shown next to a task; the UI turns it into text. */
enum class TaskNote { NONE, RETRYING, REFRESHING, SYSTEM_LIMIT, EXISTED, REPLACED }

class DownloadTask(
    val key: String,
    val album: Album,
    val track: Track,
    val quality: Quality,
    val batchId: Long,
    status: TaskStatus = TaskStatus.PENDING,
    error: String? = null,
) {
    var status by mutableStateOf(status); internal set
    var progress by mutableFloatStateOf(0f); internal set
    var error by mutableStateOf(error); internal set
    var note by mutableStateOf(TaskNote.NONE); internal set
    var noteArg by mutableIntStateOf(0); internal set
    /** Where the finished file is (a MediaStore or folder address), so it can be sent to an iPod later. */
    @Volatile var savedUri: String? = null; internal set
    /** When the file was saved (milliseconds since 1970; 0 = unknown, for downloads made before this was recorded). */
    @Volatile var finishedAt = 0L; internal set

    /** MediaStore row of the file being written, so a crash can't leave a half file behind. */
    @Volatile internal var pendingUri: String? = null
    @Volatile internal var handle: CancelHandle? = null
    @Volatile internal var sigRetried = false
    /** The file was already in the download folder, so nothing was downloaded. */
    @Volatile internal var skippedExisting = false
    /** The file was already there in lower quality and was replaced by this download. */
    @Volatile internal var replacedExisting = false
}

class EnqueueResult(val added: Int, val skipped: Int)

/** Totals for the global progress bar and the notification; failed tasks count as finished. */
class DownloadSummary(
    val total: Int,
    val done: Int,
    val downloading: Int,
    val pending: Int,
    val paused: Int,
    val failed: Int,
    val overall: Float,
    val currentTitles: List<String>,
)

interface TaskListener {
    fun onProgress(progress: Float)
    fun onPendingUri(uri: String?)
    /** The worker found the file already saved and finished without downloading it. */
    fun onSkipped() {}
    /** The finished file is at [uri]. */
    fun onSaved(uri: String) {}
    /** The download replaced a lower-quality copy of the same track. */
    fun onReplaced() {}
}

/** Does the actual transfer. [download] blocks until the file is saved; it must honour [CancelHandle]. */
interface TaskWorker {
    fun download(task: DownloadTask, handle: CancelHandle, listener: TaskListener)

    /** Deletes a half-written file left behind by an interrupted run. */
    fun discard(uri: String)
}

interface TaskStore {
    fun load(): List<DownloadTask>
    fun save(tasks: List<DownloadTask>)
}

/**
 * Persistent download queue with a concurrency limit.
 *
 * - States: pending, downloading, paused, completed, failed, canceled.
 * - Pause / cancel stop the transfer (cancelling the HTTP call) and discard the partial file;
 *   resuming restarts that track from the beginning. A user pause or cancel is never treated
 *   as a failure and never retried.
 * - Transient errors (network, HTTP 5xx/429) back off and retry; an "Invalid Request Signature"
 *   triggers one credential refresh (shared by every task that fails meanwhile) and one retry.
 * - Lowering the limit never kills a running transfer; it only stops new ones from starting.
 *
 * Pure Kotlin apart from Compose state, so the logic is unit-tested without a device.
 */
class DownloadManager(
    private val worker: TaskWorker,
    private val store: TaskStore,
    private val scope: CoroutineScope,
    private val secretVersion: () -> String = { "" },
    /** False while downloads must wait (e.g. Wi-Fi only and no Wi-Fi). */
    private val allowed: () -> Boolean = { true },
    private val errorMessage: (Throwable) -> String = { it.message ?: it.javaClass.simpleName },
    private val backoffMs: List<Long> = listOf(2_000L, 5_000L, 10_000L),
    private val refreshCooldownMs: Long = 10 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    val tasks = mutableStateListOf<DownloadTask>()

    /** Blocking credential refresh (the app's existing, verified one); null disables auto-refresh. */
    @Volatile var refresher: (() -> Unit)? = null
    @Volatile var onCredentialsRefreshed: (() -> Unit)? = null

    /** Called when the queue goes from idle to having work (or back), e.g. to start the service. */
    @Volatile var onActiveChanged: ((Boolean) -> Unit)? = null

    @Volatile private var limit = 2
    var maxConcurrent: Int
        get() = limit
        set(v) { limit = v.coerceAtLeast(1); afterChange { pumpLocked() } }

    private val lock = Any()
    private val saveLock = Any()
    // Cancellation goes through the CancelHandle only: cancelling the coroutine Job could stop it
    // before its body (and its cleanup) ever runs, leaking the concurrency slot forever.
    private val running = HashMap<String, CancelHandle>()
    private var enabled = false
    private var wasActive = false

    // Nothing is written until restore() has read the saved queue. A fresh manager holds an EMPTY
    // queue, and saving that first would overwrite the file we are about to restore from.
    @Volatile private var loaded = false

    private val refreshMutex = Mutex()
    private var refreshBlockedUntil = 0L
    private var lastRefreshError: Throwable? = null

    private val saveSignal = Channel<Unit>(Channel.CONFLATED)

    init {
        // One writer for the whole queue: bursts of changes collapse into a single write.
        scope.launch { for (u in saveSignal) persistNow() }
    }

    // ---------------------------------------------------------------- queries

    fun latestFor(trackId: Long): DownloadTask? = tasks.lastOrNull { it.track.id == trackId }

    /** True while there is something to download and the session is ready to do it. */
    fun isActive(): Boolean = enabled && tasks.any { it.status == TaskStatus.PENDING || it.status == TaskStatus.DOWNLOADING }

    fun summary(): DownloadSummary {
        val snapshot = tasks.toList()
        val batch = snapshot.maxOfOrNull { it.batchId } ?: 0L
        val inBatch = snapshot.filter { it.batchId == batch && it.status != TaskStatus.CANCELED }
        val sum = inBatch.sumOf {
            when (it.status) {
                TaskStatus.COMPLETED, TaskStatus.FAILED -> 1.0
                else -> it.progress.toDouble()
            }
        }
        return DownloadSummary(
            total = inBatch.size,
            done = inBatch.count { it.status == TaskStatus.COMPLETED },
            downloading = snapshot.count { it.status == TaskStatus.DOWNLOADING },
            pending = snapshot.count { it.status == TaskStatus.PENDING },
            paused = snapshot.count { it.status == TaskStatus.PAUSED },
            failed = snapshot.count { it.status == TaskStatus.FAILED },
            overall = if (inBatch.isEmpty()) 0f else (sum / inBatch.size).toFloat().coerceIn(0f, 1f),
            currentTitles = snapshot.filter { it.status == TaskStatus.DOWNLOADING }.map { it.track.displayTitle },
        )
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Loads the saved queue; call once at startup. Anything that was mid-download goes back to
     * pending. Tasks already queued in memory meanwhile are kept (restored ones go in front).
     */
    fun restore() {
        val saved = store.load()
        val stale = ArrayList<String>()
        synchronized(lock) {
            val have = tasks.map { it.key }.toSet()
            val restored = saved.filter { it.key !in have }
            restored.forEach { t ->
                t.pendingUri?.let { stale += it }
                t.pendingUri = null
                if (t.status == TaskStatus.DOWNLOADING) t.status = TaskStatus.PENDING
                t.progress = if (t.status == TaskStatus.COMPLETED) 1f else 0f
            }
            tasks.addAll(0, restored)
            loaded = true
        }
        stale.forEach { runCatching { worker.discard(it) } }
        afterChange { pumpLocked() }
    }

    /** Off before login / after logout: running transfers go back to pending, nothing new starts. */
    fun setEnabled(value: Boolean) {
        afterChange {
            enabled = value
            if (!value) {
                running.values.forEach { it.cancel() }
                tasks.filter { it.status == TaskStatus.DOWNLOADING }.forEach {
                    it.status = TaskStatus.PENDING; it.progress = 0f; it.note = TaskNote.NONE
                }
            } else {
                pumpLocked()
            }
        }
    }

    // ---------------------------------------------------------------- user actions

    fun enqueue(items: List<Pair<Album, Track>>, quality: Quality): EnqueueResult {
        var added = 0
        var skipped = 0
        afterChange {
            val batch = (tasks.maxOfOrNull { it.batchId } ?: 0L).let { max ->
                if (tasks.any { it.status.isOpen }) max else max + 1
            }
            for ((album, track) in items) {
                val key = "${track.id}:${quality.formatId}"
                val existing = tasks.lastOrNull { it.key == key }
                when {
                    existing == null -> {
                        tasks.add(DownloadTask(key, album, track, quality, batch)); added++
                    }
                    existing.status == TaskStatus.FAILED || existing.status == TaskStatus.CANCELED -> {
                        resetToPending(existing); added++
                    }
                    else -> skipped++ // already queued, running, paused or downloaded
                }
            }
            pumpLocked()
        }
        return EnqueueResult(added, skipped)
    }

    fun pause(task: DownloadTask) = afterChange { pauseLocked(task) }
    fun resume(task: DownloadTask) = afterChange { resumeLocked(task); pumpLocked() }
    fun cancel(task: DownloadTask) = afterChange { cancelLocked(task); pumpLocked() }
    fun retry(task: DownloadTask) = afterChange { retryLocked(task); pumpLocked() }

    /** Removes the entry from the list (and stops it if running). The saved audio file is untouched. */
    fun delete(task: DownloadTask) = afterChange {
        cancelRunning(task)
        tasks.remove(task)
        pumpLocked()
    }

    fun pauseAll() = afterChange { tasks.toList().forEach { pauseLocked(it) } }
    fun resumeAll() = afterChange { tasks.toList().forEach { resumeLocked(it) }; pumpLocked() }
    fun retryFailed() = afterChange {
        tasks.toList().filter { it.status == TaskStatus.FAILED }.forEach { retryLocked(it) }; pumpLocked()
    }
    /** Drops the finished entries whose file was deleted outside the queue (the files themselves are untouched). */
    fun forgetFiles(uris: Set<String>) = afterChange { tasks.removeAll { it.status == TaskStatus.COMPLETED && it.savedUri in uris } }
    fun clearCompleted() = afterChange { tasks.removeAll { it.status == TaskStatus.COMPLETED } }

    /** The system ended the foreground service (e.g. Android 15's data-sync time limit). */
    fun pauseForSystemLimit() = afterChange {
        tasks.toList().forEach {
            if (it.status == TaskStatus.PENDING || it.status == TaskStatus.DOWNLOADING) {
                pauseLocked(it); it.note = TaskNote.SYSTEM_LIMIT
            }
        }
    }

    /** Blocks until the queue file reflects the current state (no-op until restore() has run). */
    fun flush() = persistNow()

    // ---------------------------------------------------------------- state transitions (lock held)

    private fun pauseLocked(task: DownloadTask) {
        when (task.status) {
            TaskStatus.PENDING -> task.status = TaskStatus.PAUSED
            TaskStatus.DOWNLOADING -> {
                task.status = TaskStatus.PAUSED; task.progress = 0f; task.note = TaskNote.NONE
                cancelRunning(task)
            }
            else -> Unit
        }
    }

    private fun resumeLocked(task: DownloadTask) {
        if (task.status == TaskStatus.PAUSED) resetToPending(task)
    }

    private fun cancelLocked(task: DownloadTask) {
        if (!task.status.isOpen) return
        if (task.status == TaskStatus.DOWNLOADING) cancelRunning(task)
        task.status = TaskStatus.CANCELED; task.progress = 0f; task.note = TaskNote.NONE
    }

    private fun retryLocked(task: DownloadTask) {
        if (task.status == TaskStatus.FAILED || task.status == TaskStatus.CANCELED) resetToPending(task)
    }

    private fun resetToPending(task: DownloadTask) {
        task.status = TaskStatus.PENDING; task.progress = 0f; task.error = null
        task.note = TaskNote.NONE; task.sigRetried = false
    }

    private fun cancelRunning(task: DownloadTask) {
        running[task.key]?.cancel()
    }

    /** Call when what `allowed` depends on changes: pauses running transfers or starts waiting ones. */
    fun recheckAllowed() {
        afterChange {
            if (!allowed()) {
                running.values.forEach { it.cancel() }
                tasks.filter { it.status == TaskStatus.DOWNLOADING }.forEach {
                    it.status = TaskStatus.PENDING; it.progress = 0f; it.note = TaskNote.NONE
                }
            } else pumpLocked()
        }
    }

    private fun pumpLocked() {
        if (!enabled || !allowed()) return
        while (running.size < limit) {
            val next = tasks.firstOrNull { it.status == TaskStatus.PENDING && it.key !in running } ?: break
            startLocked(next)
        }
    }

    private fun startLocked(task: DownloadTask) {
        val handle = CancelHandle()
        task.handle = handle
        task.status = TaskStatus.DOWNLOADING; task.progress = 0f; task.error = null; task.note = TaskNote.NONE
        task.skippedExisting = false; task.replacedExisting = false
        val signedWith = secretVersion()
        // Lazy so the entry is in `running` before the job can possibly finish.
        val job = scope.launch(start = CoroutineStart.LAZY) { runTask(task, handle, signedWith) }
        running[task.key] = handle
        job.start()
    }

    /** Runs [block] under the lock, then saves the queue and tells listeners if activity changed. */
    private inline fun afterChange(block: () -> Unit) {
        var changed: Boolean? = null
        synchronized(lock) {
            block()
            val active = isActive()
            if (active != wasActive) { wasActive = active; changed = active }
        }
        saveSignal.trySend(Unit)
        changed?.let { onActiveChanged?.invoke(it) }
    }

    // ---------------------------------------------------------------- the transfer itself

    private suspend fun runTask(task: DownloadTask, handle: CancelHandle, signedWith: String) {
        var attempt = 0
        try {
            while (true) {
                if (handle.cancelled) return // paused/canceled in the instant before it began
                try {
                    worker.download(task, handle, listenerFor(task, handle))
                    afterChange {
                        if (task in tasks) {
                            task.status = TaskStatus.COMPLETED; task.progress = 1f
                            task.error = null; task.note = if (task.skippedExisting) TaskNote.EXISTED else if (task.replacedExisting) TaskNote.REPLACED else TaskNote.NONE
                        }
                    }
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // A pause / cancel / logout surfaces as an IOException from the aborted call; the
                    // controller already set the state, so it must not be classified as a failure.
                    if (handle.cancelled) return
                    when (DownloadErrors.classify(e)) {
                        FailureKind.TRANSIENT -> {
                            if (attempt >= backoffMs.size) { fail(task, handle, e); return }
                            afterChange { task.note = TaskNote.RETRYING; task.noteArg = attempt + 1; task.progress = 0f }
                            backoff(backoffMs[attempt], handle); attempt++
                            if (handle.cancelled) return
                            afterChange { task.note = TaskNote.NONE }
                        }
                        FailureKind.SIGNATURE -> { onSignatureError(task, handle, signedWith, e); return }
                        FailureKind.FATAL -> { fail(task, handle, e); return }
                    }
                }
            }
        } finally {
            afterChange {
                if (running[task.key] === handle) running.remove(task.key)
                if (task.handle === handle) task.handle = null
                pumpLocked()
            }
        }
    }

    /** Waits in short steps so a pause or cancel during the delay takes effect almost immediately. */
    private suspend fun backoff(ms: Long, handle: CancelHandle) {
        var left = ms
        while (left > 0 && !handle.cancelled) {
            val step = minOf(left, 200L)
            sleep(step)
            left -= step
        }
    }

    private fun fail(task: DownloadTask, handle: CancelHandle, e: Throwable) = afterChange {
        if (!handle.cancelled && task in tasks) {
            task.status = TaskStatus.FAILED; task.error = errorMessage(e); task.note = TaskNote.NONE
        }
    }

    /** One refresh-and-retry per task; when the refresh itself fails, the task fails with that reason. */
    private suspend fun onSignatureError(task: DownloadTask, handle: CancelHandle, signedWith: String, e: Throwable) {
        if (task.sigRetried) { fail(task, handle, e); return }
        task.sigRetried = true
        afterChange { task.note = TaskNote.REFRESHING }
        val ok = ensureFreshCredentials(signedWith)
        if (handle.cancelled) return
        if (ok) {
            afterChange {
                if (task in tasks && task.status == TaskStatus.DOWNLOADING) {
                    task.status = TaskStatus.PENDING; task.progress = 0f; task.note = TaskNote.NONE
                }
            }
        } else {
            fail(task, handle, lastRefreshError ?: e)
        }
    }

    /**
     * Single-flight: tasks that fail with a bad signature at the same time wait on one refresh.
     * If the secret already changed since this task signed, it just retries. After a refresh that
     * failed, further attempts are blocked for a while so a long queue doesn't re-download the
     * ~9 MB player bundle once per track.
     */
    private suspend fun ensureFreshCredentials(signedWith: String): Boolean = refreshMutex.withLock {
        if (secretVersion() != signedWith) return@withLock true
        val refresh = refresher ?: return@withLock false
        if (clock() < refreshBlockedUntil) return@withLock false
        try {
            refresh()
            lastRefreshError = null
            onCredentialsRefreshed?.invoke()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastRefreshError = e
            refreshBlockedUntil = clock() + refreshCooldownMs
            false
        }
    }

    private fun listenerFor(task: DownloadTask, handle: CancelHandle) = object : TaskListener {
        private var last = 0f
        override fun onProgress(progress: Float) {
            if (handle.cancelled) return
            // Only publish ~1% steps: the UI and notification don't need more, and it keeps recompositions down.
            if (progress >= 1f || progress - last >= 0.01f) { last = progress; task.progress = progress }
        }
        override fun onPendingUri(uri: String?) {
            task.pendingUri = uri
            saveSignal.trySend(Unit)
        }
        override fun onSkipped() { task.skippedExisting = true }
        override fun onSaved(uri: String) { task.savedUri = uri; task.finishedAt = System.currentTimeMillis() }
        override fun onReplaced() { task.replacedExisting = true }
    }

    // ---------------------------------------------------------------- persistence

    private fun persistNow() {
        synchronized(saveLock) {
            if (!loaded) return // see `loaded`: never overwrite the saved queue before reading it
            runCatching { store.save(tasks.toList()) }
        }
    }
}

/** Lets a transfer be aborted from another thread, including the in-flight HTTP call. */
class CancelHandle {
    @Volatile var cancelled = false
        private set
    @Volatile var call: okhttp3.Call? = null

    fun cancel() {
        cancelled = true
        call?.cancel()
    }

    fun throwIfCancelled() {
        if (cancelled) throw DownloadCancelledException()
    }
}

class DownloadCancelledException : IOException("Download cancelled")

/** The file couldn't be created or written; retrying won't help. */
class StorageException(message: String) : IOException(message)

enum class FailureKind { SIGNATURE, TRANSIENT, FATAL }

object DownloadErrors {
    private val retryableHttp = Regex("""HTTP (5\d\d|429)""")
    private val fatalHttp = Regex("""HTTP 4\d\d""")

    fun classify(e: Throwable): FailureKind {
        val msg = e.message.orEmpty()
        return when {
            "request_sig" in msg || "Invalid Request Signature" in msg -> FailureKind.SIGNATURE
            e is StorageException -> FailureKind.FATAL
            retryableHttp.containsMatchIn(msg) -> FailureKind.TRANSIENT
            fatalHttp.containsMatchIn(msg) -> FailureKind.FATAL
            e is ApiException -> FailureKind.FATAL
            e is IOException -> FailureKind.TRANSIENT // sockets, timeouts, DNS, truncated bodies
            else -> FailureKind.FATAL
        }
    }
}
