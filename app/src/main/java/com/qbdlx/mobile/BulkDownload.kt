package com.qbdlx.mobile

import kotlinx.coroutines.CancellationException

/** How a "download everything" request went: what was queued, what was already there, what could not be read. */
class BulkResult(val sources: Int, val failedSources: Int, val added: Int, val skipped: Int)

/**
 * Resolves each source (an album or a playlist) to its tracks and queues them one source at a time, so the first
 * downloads start while the rest is still being looked up, and one source that fails never stops the others.
 * Tracks Qobuz marks as not streamable are left out; duplicates are the queue's business ([DownloadManager.enqueue]).
 */
suspend fun <T> enqueueBulk(
    sources: List<T>,
    resolve: suspend (T) -> List<Pair<Album, Track>>,
    enqueue: (List<Pair<Album, Track>>) -> EnqueueResult,
): BulkResult {
    var added = 0
    var skipped = 0
    var failed = 0
    for (source in sources) {
        val items = try {
            resolve(source).filter { it.second.streamable }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed++
            continue
        }
        if (items.isEmpty()) continue
        val r = enqueue(items)
        added += r.added
        skipped += r.skipped
    }
    return BulkResult(sources.size, failed, added, skipped)
}
