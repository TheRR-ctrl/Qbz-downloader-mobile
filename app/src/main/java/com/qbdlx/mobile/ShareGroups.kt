package com.qbdlx.mobile

/**
 * Splits [items] into groups of things that happened close together: sorted newest first, a new group starts
 * whenever the gap to the previous item is longer than [gapMs]. Used to offer "the songs I downloaded in one go".
 */
fun <T> groupByGap(items: List<T>, gapMs: Long, time: (T) -> Long): List<List<T>> {
    val sorted = items.sortedByDescending(time)
    val groups = ArrayList<MutableList<T>>()
    var last = 0L
    for (item in sorted) {
        val t = time(item)
        if (groups.isEmpty() || last - t > gapMs) groups += ArrayList<T>()
        groups.last() += item
        last = t
    }
    return groups
}
