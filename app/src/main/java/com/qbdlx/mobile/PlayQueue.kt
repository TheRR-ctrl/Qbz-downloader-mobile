package com.qbdlx.mobile

import java.util.Random

enum class RepeatMode { OFF, ALL, ONE }

/** What the player screens show: the track playing, what played before, and what comes next. */
class QueueState<T>(
    val current: T?, val history: List<T>, val upcoming: List<T>,
    val shuffle: Boolean, val repeat: RepeatMode,
) {
    val hasPrevious get() = history.isNotEmpty()
    val hasNext get() = upcoming.isNotEmpty()
}

/**
 * The list of tracks being played and where we are in it. Plain Kotlin, no Android, so every rule (shuffle,
 * repeat, going back, removing a track) is unit-tested. The same track may appear twice (a playlist can list it
 * twice), so entries are tracked by identity, never by equality.
 */
class PlayQueue<T>(private val random: Random = Random()) {
    private class Entry<T>(val value: T)

    private var original: List<Entry<T>> = emptyList() // the order the list was given in (restored when shuffle goes off)
    private var order: MutableList<Entry<T>> = ArrayList() // the order being played
    private var pos = -1

    var shuffle = false; private set
    var repeat = RepeatMode.OFF; private set

    val current: T? get() = order.getOrNull(pos)?.value
    val size get() = order.size

    fun state() = QueueState(
        current,
        if (pos > 0) order.subList(0, pos).map { it.value } else emptyList(),
        if (pos >= 0 && pos + 1 < order.size) order.subList(pos + 1, order.size).map { it.value } else emptyList(),
        shuffle, repeat,
    )

    /** Starts a new queue at [start]. With shuffle on, the chosen track plays first and everything else follows in random order. */
    fun set(items: List<T>, start: Int) {
        original = items.map { Entry(it) }
        order = original.toCollection(ArrayList())
        pos = if (order.isEmpty()) -1 else start.coerceIn(0, order.size - 1)
        if (shuffle && pos >= 0) {
            val first = order[pos]
            val rest = order.filter { it !== first }.toMutableList().also { it.shuffle(random) }
            order = ArrayList<Entry<T>>().apply { add(first); addAll(rest) }
            pos = 0
        }
    }

    /**
     * Moves to the next track and returns it, or null when the queue is over (the current track stays current).
     * [auto] is true when the track ended by itself, which is the only time "repeat one" repeats it.
     */
    fun next(auto: Boolean): T? {
        if (pos < 0) return null
        if (auto && repeat == RepeatMode.ONE) return current
        if (pos + 1 < order.size) { pos++; return current }
        if (repeat != RepeatMode.OFF && order.isNotEmpty()) { pos = 0; return current } // same order again, so [peekNext] is always right
        return null
    }

    /** The track [next] would play when the current one ends by itself, without moving; null when the queue is over. */
    fun peekNext(): T? = when {
        pos < 0 -> null
        repeat == RepeatMode.ONE -> current
        pos + 1 < order.size -> order[pos + 1].value
        repeat == RepeatMode.ALL && order.isNotEmpty() -> order[0].value
        else -> null
    }

    /** Moves back one track and returns it. At the very start it stays (or wraps to the end when repeating the whole queue). */
    fun previous(): T? {
        if (pos < 0) return null
        if (pos > 0) pos-- else if (repeat == RepeatMode.ALL) pos = order.size - 1
        return current
    }

    /** Plays the track at [index] of history + current + upcoming (as shown on the queue page). */
    fun jump(index: Int): T? {
        if (index !in order.indices) return null
        pos = index
        return current
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        if (pos < 0) return
        if (shuffle) {
            val played = order.subList(0, pos + 1).toMutableList()
            val rest = order.subList(pos + 1, order.size).toMutableList().also { it.shuffle(random) }
            order = played.apply { addAll(rest) }
        } else {
            val cur = order[pos]
            val still = order.toSet()
            order = original.filter { it in still }.toCollection(ArrayList())
            pos = order.indexOfFirst { it === cur }
        }
    }

    fun cycleRepeat() {
        repeat = when (repeat) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF }
    }

    /** Removes the [k]-th upcoming track (0 = the very next one). */
    fun removeUpcoming(k: Int) {
        val i = pos + 1 + k
        if (k < 0 || i !in order.indices) return
        val gone = order.removeAt(i)
        original = original.filter { it !== gone }
    }

    /** Moves the [from]-th upcoming track to the [to]-th place (0 = the very next one). */
    fun moveUpcoming(from: Int, to: Int) {
        val a = pos + 1 + from; val b = pos + 1 + to
        if (pos < 0 || from < 0 || to < 0 || a !in order.indices || b !in order.indices || a == b) return
        order.add(b, order.removeAt(a))
        if (!shuffle) original = order.toList()
    }

    /** Puts [item] right after the track that is playing; starts the queue with it when nothing is playing. */
    fun insertNext(item: T) {
        val e = Entry(item)
        if (pos < 0) { original = listOf(e); order = arrayListOf(e); pos = 0; return }
        order.add(pos + 1, e)
        original = if (shuffle) original + e else order.toList()
    }

    /** Puts [item] at the very end of the queue. */
    fun append(item: T) {
        val e = Entry(item)
        order.add(e); original = original + e
        if (pos < 0) pos = 0
    }

    /** Keeps only the track that is playing. */
    fun clearUpcoming() {
        if (pos < 0) return
        val cur = order[pos]
        order = arrayListOf(cur); original = listOf(cur); pos = 0
    }

    fun clear() { original = emptyList(); order = ArrayList(); pos = -1 }
}

/** The tracks that can actually be played, and where the one tapped ended up once the others are left out. */
fun <T> playableQueue(items: List<T>, tapped: Int, playable: (T) -> Boolean): Pair<List<T>, Int> {
    val keep = items.filter(playable)
    if (keep.isEmpty()) return keep to 0
    val start = items.drop(tapped.coerceIn(0, items.size)).firstOrNull(playable)?.let { first -> keep.indexOfFirst { it === first } }
    return keep to (start ?: 0)
}

/** "24 Bit / 96 kHz" the way Qobuz's own player writes it; "MP3" for MP3; null when it isn't known. */
fun AudioQuality.label(): String = when {
    mp3 -> "MP3"
    else -> "$bitDepth Bit / ${khz(sampleRate)} kHz"
}

/** Hi-Res means more than CD quality (more than 16 bits, or a rate above 48 kHz). */
val AudioQuality.isHiRes: Boolean get() = !mp3 && (bitDepth > 16 || sampleRate > 48000)

private fun khz(hz: Int): String {
    val k = hz / 1000.0
    return if (hz % 1000 == 0) "%d".format(java.util.Locale.ROOT, hz / 1000) else "%.1f".format(java.util.Locale.ROOT, k)
}
