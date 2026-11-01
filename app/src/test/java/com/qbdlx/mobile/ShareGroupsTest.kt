package com.qbdlx.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareGroupsTest {
    @Test fun splitsWhereTheGapIsLong() {
        val min = 60_000L
        val g = groupByGap(listOf(0L, 2 * min, 5 * min, 40 * min, 43 * min, 200 * min), 10 * min) { it }
        assertEquals(listOf(listOf(200 * min), listOf(43 * min, 40 * min), listOf(5 * min, 2 * min, 0L)), g)
    }
    @Test fun empty() = assertEquals(emptyList<List<Long>>(), groupByGap(emptyList<Long>(), 1) { it })
}
