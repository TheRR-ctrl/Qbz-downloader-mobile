package com.qbdlx.lyrics

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/** Lines the lyrics are sung at, found by matching what Whisper heard against the lyric text. */
object WhisperAlign {
    /** Share of the lyric's words that must be found in what was heard; below it the result is not trusted. */
    const val MIN_MATCHED = 0.25

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).filter { it.isLetterOrDigit() }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (min(a.length, b.length) < 4) return 0.0
        val d = Array(2) { IntArray(b.length + 1) { it } }
        for (i in 1..a.length) {
            val c = i and 1; val p = c xor 1; d[c][0] = i
            for (j in 1..b.length) d[c][j] = minOf(d[p][j] + 1, d[c][j - 1] + 1, d[p][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        return 1.0 - d[a.length and 1][b.length].toDouble() / max(a.length, b.length)
    }

    /** Start of each of [lines] in ms, or null when too little of the lyric was recognised. */
    fun align(lines: List<String>, words: List<AsrWord>): List<LyricLine>? {
        // lyric words with their line; heard words without Whisper's sound tags ("[Música]", "(aplausos)", ♪)
        val lw = ArrayList<Pair<Int, String>>()
        lines.forEachIndexed { i, l -> l.split(Regex("\\s+")).map(::norm).filter { it.isNotEmpty() }.forEach { lw.add(i to it) } }
        val hw = words.filter { w -> w.text.firstOrNull().let { it != '[' && it != '(' && it != '♪' } }.map { it to norm(it.text) }.filter { it.second.isNotEmpty() }
        if (lw.isEmpty() || hw.isEmpty()) return null
        val n = lw.size; val m = hw.size
        // global alignment: equal +3, similar +1, different -2, skipping a word -1
        val s = Array(n + 1) { IntArray(m + 1) }
        for (i in 1..n) s[i][0] = -i
        for (j in 1..m) s[0][j] = -j
        val sim = Array(n) { i -> DoubleArray(m) { j -> similarity(lw[i].second, hw[j].second) } }
        fun sc(i: Int, j: Int) = if (sim[i][j] == 1.0) 3 else if (sim[i][j] >= 0.65) 1 else -2
        for (i in 1..n) for (j in 1..m) s[i][j] = maxOf(s[i - 1][j - 1] + sc(i - 1, j - 1), s[i - 1][j] - 1, s[i][j - 1] - 1)
        // walk back: for every lyric word, the heard word it matched (or -1)
        val match = IntArray(n) { -1 }
        var i = n; var j = m
        while (i > 0 && j > 0) {
            when {
                s[i][j] == s[i - 1][j - 1] + sc(i - 1, j - 1) -> { if (sim[i - 1][j - 1] >= 0.65) match[i - 1] = j - 1; i--; j-- }
                s[i][j] == s[i - 1][j] - 1 -> i--
                else -> j--
            }
        }
        if (match.count { it >= 0 } < n * MIN_MATCHED) return null
        val avgWord = 350L
        // a line starts at its first recognised word (minus the words of the line before it)
        val start = LongArray(lines.size) { -1L }
        val seen = IntArray(lines.size)
        for (k in 0 until n) {
            val line = lw[k].first
            if (match[k] >= 0 && start[line] < 0) start[line] = max(0L, hw[match[k]].first.startMs - seen[line] * avgWord)
            seen[line]++
        }
        // lines with nothing recognised: spread between the neighbours that were
        val known = start.indices.filter { start[it] >= 0 }
        val span = if (known.size >= 2) (start[known.last()] - start[known.first()]) / (known.last() - known.first()).coerceAtLeast(1) else 4000L
        for (l in lines.indices) if (start[l] < 0) {
            val prev = known.lastOrNull { it < l }; val next = known.firstOrNull { it > l }
            start[l] = when {
                prev != null && next != null -> start[prev] + (start[next] - start[prev]) * (l - prev) / (next - prev)
                prev != null -> start[prev] + span * (l - prev)
                next != null -> max(0L, start[next] - span * (next - l))
                else -> 0L
            }
        }
        for (l in 1 until lines.size) if (start[l] < start[l - 1] + 400) start[l] = start[l - 1] + 400
        return lines.mapIndexed { idx, t -> LyricLine(start[idx], t) }
    }
}
