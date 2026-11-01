package com.qbdlx.lyrics

/** One lyric line and the moment it starts. */
class LyricLine(val timeMs: Long, val text: String)

object Lrc {
    private val stamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /** Parses LRC text ("[mm:ss.xx] words", several stamps per line allowed) into lines sorted by time. Tags like [ar:..] are ignored. */
    fun parse(text: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (raw in text.lines()) {
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val words = raw.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val frac = m.groupValues[3]
                val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                out += LyricLine(m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toLong() * 1000 + ms, words)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    /** Index of the line being sung at [posMs], or -1 before the first line. */
    fun indexAt(lines: List<LyricLine>, posMs: Long): Int {
        var lo = 0; var hi = lines.size - 1; var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= posMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }

    /** Plain (unsynced) text as lines; blank lines are dropped. */
    fun plain(text: String): List<LyricLine> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { LyricLine(0, it) }
}
