package com.qbdlx.mobile

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** The response is not a usable cover (an error page, a non-image, or far too big); retrying won't change that. */
class CoverRejectedException(message: String) : IOException(message)

/**
 * Cover art and full album details for the tags. Both are best effort: a failure only means fewer tags,
 * never a failed download.
 *
 * - **Retries**: a failure that can fix itself (network error, HTTP 5xx or 429) is tried again, up to
 *   [retryDelaysMs].size more times (default: 1 try + 2 retries). Permanent ones (HTTP 404, a body that is
 *   not an image) are not retried.
 * - **One request per album**: ten workers starting the same album together wait on one fetch, then use
 *   its result instead of each making their own.
 * - **Failures are remembered for a minute**, so one unreachable cover doesn't make every following track
 *   of the album spend seconds retrying; it is tried again afterwards.
 */
class TagSources(
    private val http: OkHttpClient,
    private val loadAlbum: (String) -> Album,
    private val retryDelaysMs: List<Long> = listOf(500L, 1_500L),
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    private class Cached<T>(val value: T?, val at: Long)

    /** How a fetch ended. A cancel is neither a success nor a failure, and must never be remembered as one. */
    private sealed interface Attempt<out T> {
        class Ok<T>(val value: T) : Attempt<T>
        data object Failed : Attempt<Nothing>
        data object Cancelled : Attempt<Nothing>
    }

    private val covers = lru<Cached<Cover>>(8)
    private val albums = lru<Cached<Album>>(32)
    private val locks = ConcurrentHashMap<String, Any>()

    /** [album] with its blanks filled from the full album, when it is a partial one (playlist, favourites). */
    fun detailedAlbum(album: Album, cancelled: () -> Boolean = { false }): Album {
        if (album.hasTagDetails) return album
        val full = cached(albums, "album:${album.id}") {
            retrying(cancelled, { e -> log("[etiquetas] sin datos completos del álbum ${album.id} tras ${retryDelaysMs.size + 1} intentos: ${reason(e)}") }) {
                loadAlbum(album.id)
            }
        }
        return full?.let { album.fillFrom(it) } ?: album
    }

    fun coverFor(url: String?, cancelled: () -> Boolean = { false }): Cover? {
        if (url.isNullOrBlank()) return null
        return cached(covers, "cover:$url") {
            retrying(cancelled, { e -> log("[etiquetas] sin carátula (${url.substringAfterLast('/')}) tras ${retryDelaysMs.size + 1} intentos: ${reason(e)}") }) {
                fetchCover(url)
            }
        }
    }

    // ------------------------------------------------------------------ internals

    private fun <T> cached(cache: MutableMap<String, Cached<T>>, key: String, load: () -> Attempt<T>): T? {
        fun hit(): Cached<T>? = synchronized(cache) { cache[key] }?.takeIf { it.value != null || clock() - it.at < FAILURE_MEMORY_MS }
        hit()?.let { return it.value }
        // Single flight per key: everyone asking for the same cover/album waits for the first fetch.
        synchronized(locks.computeIfAbsent(key) { Any() }) {
            hit()?.let { return it.value }
            val result = load()
            val value = (result as? Attempt.Ok)?.value
            // Remember successes and real failures; a cancelled attempt says nothing about the cover/album.
            if (result !is Attempt.Cancelled) synchronized(cache) { cache[key] = Cached(value, clock()) }
            return value
        }
    }

    /** Runs [block], retrying fixable failures; calls [onGiveUp] if it never works. */
    private fun <T> retrying(cancelled: () -> Boolean, onGiveUp: (Throwable) -> Unit, block: () -> T): Attempt<T> {
        var attempt = 0
        while (true) {
            try {
                return Attempt.Ok(block())
            } catch (e: Exception) {
                if (cancelled()) return Attempt.Cancelled
                if (attempt >= retryDelaysMs.size || !isRetryable(e)) {
                    onGiveUp(e)
                    return Attempt.Failed
                }
                pause(retryDelaysMs[attempt], cancelled)
                attempt++
                if (cancelled()) return Attempt.Cancelled
            }
        }
    }

    /** Waits in short steps so a cancel during the delay is noticed quickly. */
    private fun pause(ms: Long, cancelled: () -> Boolean) {
        var left = ms
        while (left > 0 && !cancelled()) {
            val step = minOf(left, 100L)
            sleep(step)
            left -= step
        }
    }

    private fun fetchCover(url: String): Cover {
        val client = http.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Cover failed: HTTP ${r.code}")
            val input = r.body?.byteStream() ?: throw IOException("Cover failed: empty response")
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_COVER_BYTES) throw CoverRejectedException("Cover is larger than $MAX_COVER_BYTES bytes")
                out.write(buf, 0, n)
            }
            return Cover.sniff(out.toByteArray()) ?: throw CoverRejectedException("Response is not a JPEG or PNG image")
        }
    }

    private fun isRetryable(e: Throwable) = e !is CoverRejectedException && DownloadErrors.classify(e) == FailureKind.TRANSIENT

    private fun reason(e: Throwable) = (e.message ?: e.javaClass.simpleName).take(120)

    private fun <V> lru(max: Int): MutableMap<String, V> = object : LinkedHashMap<String, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>) = size > max
    }

    private companion object {
        const val MAX_COVER_BYTES = 8 * 1024 * 1024
        const val FAILURE_MEMORY_MS = 60_000L
    }
}
