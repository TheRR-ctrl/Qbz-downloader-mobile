package com.qbdlx.mobile

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The timing rules for fading from one song into the next and for loading the next song before it is needed.
 * Plain Kotlin, so they are unit-tested; PlayerEngine only applies the numbers to the real players.
 */

/** The fade lengths offered in Settings, in milliseconds; 0 is "off". */
val CROSSFADE_OPTIONS_MS = listOf(0, 2000, 4000, 6000, 8000, 10000)

/** Volumes (outgoing, incoming) at [progress] 0..1 of the fade. Equal power: the loudness stays even through the middle. */
fun fadeGains(progress: Double): Pair<Float, Float> {
    val p = progress.coerceIn(0.0, 1.0)
    return cos(p * PI / 2).toFloat() to sin(p * PI / 2).toFloat()
}

/**
 * How long the fade really lasts for a track of [trackMs]: the chosen length, but never more than a third of the
 * track, and none at all (0) when it would be shorter than a second or the track is unknown.
 */
fun effectiveFadeMs(trackMs: Long, fadeMs: Int): Long {
    if (fadeMs <= 0 || trackMs <= 0) return 0
    val ms = min(fadeMs.toLong(), trackMs / 3)
    return if (ms < 1000) 0 else ms
}

/** Whether the fade into the next song should begin now. */
fun shouldStartFade(positionMs: Long, trackMs: Long, fadeMs: Int): Boolean {
    val fade = effectiveFadeMs(trackMs, fadeMs)
    return fade > 0 && positionMs >= trackMs - fade
}

/** How long before the end the next song's address is fetched: early enough to be ready, late enough not to expire. */
fun prefetchLeadMs(fadeMs: Int): Long = max(45_000L, fadeMs + 20_000L)

/** Whether it is time to fetch the next song's address. An unknown length means "not yet". */
fun shouldPrefetch(positionMs: Long, trackMs: Long, fadeMs: Int): Boolean =
    trackMs > 0 && positionMs >= trackMs - prefetchLeadMs(fadeMs)
