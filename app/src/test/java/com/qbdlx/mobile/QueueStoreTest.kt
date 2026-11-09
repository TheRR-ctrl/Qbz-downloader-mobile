package com.qbdlx.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class QueueStoreTest {
    private val album = Album("a1", "Out of Time", "R.E.M.", null, 11, "1991", "16/44", false)
    private fun track(id: Long) = Track(id, "Song $id", null, id.toInt(), 1, "R.E.M.", 200, true, "16/44", false)
    private val items = (1L..3L).map { album to track(it) }

    @Test fun cachedItemsGiveTheSameSavedQueue() {
        val whole = QueueStore.encodeQueue(items, 1, 42_000)
        val fromCache = QueueStore.encodeQueue(QueueStore.encodeItemsText(items), 1, 42_000)
        val a = QueueStore.decodeQueue(whole); val b = QueueStore.decodeQueue(fromCache)
        assertNotNull(a); assertNotNull(b)
        assertEquals(a!!.items.map { it.second.id }, b!!.items.map { it.second.id })
        assertEquals(1, b.position); assertEquals(42_000L, b.positionMs)
        assertEquals(listOf(1L, 2L, 3L), b.items.map { it.second.id })
    }
}
