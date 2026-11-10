package com.qbdlx.lyrics

/** One lyric line and the moment it starts. */
/** [marks]: when each word starts (offset of its first character in [text], ms), from enhanced LRC "<mm:ss.xx>word"; null when only the line is timed. */
class LyricLine(val timeMs: Long, val text: String, val endMs: Long? = null, val marks: List<Pair<Int, Long>>? = null) {
    fun shifted(d: Long) = LyricLine((timeMs + d).coerceAtLeast(0), text, endMs?.let { (it + d).coerceAtLeast(0) }, marks?.map { it.first to (it.second + d).coerceAtLeast(0) })
}

object Lrc {
    private val endTag = Regex("""\s*<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>\s*$""")
    private val offsetTag = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    /** Word-by-word marks of enhanced LRC ("<00:12.30>word"): taken out of the text and kept as each word's start. */
    private val wordTag = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private fun ms(m: MatchResult): Long { val f = m.groupValues[3]
        return m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toLong() * 1000 + when (f.length) { 0 -> 0L; 1 -> f.toLong() * 100; 2 -> f.toLong() * 10; else -> f.take(3).toLong() } }

    /** Text without the word tags, and where each tagged word starts. */
    internal fun wordMarks(raw: String): Pair<String, List<Pair<Int, Long>>> {
        val sb = StringBuilder(); val marks = ArrayList<Pair<Int, Long>>(); var last = 0; var pending: Long? = null
        for (m in wordTag.findAll(raw)) {
            val piece = raw.substring(last, m.range.first)
            if (pending != null) { val lead = piece.length - piece.trimStart().length; if (piece.isNotBlank()) marks.add((sb.length + lead) to pending); pending = null }
            sb.append(piece); last = m.range.last + 1; pending = ms(m)
        }
        val rest = raw.substring(last)
        if (pending != null && rest.isNotBlank()) marks.add((sb.length + rest.length - rest.trimStart().length) to pending)
        sb.append(rest)
        // the spaces are collapsed afterwards: the offsets are moved with them
        val text = StringBuilder(); val map = IntArray(sb.length + 1); var prevSpace = true
        for (i in sb.indices) { map[i] = text.length; val c = sb[i]; if (c.isWhitespace()) { if (!prevSpace) text.append(' '); prevSpace = true } else { text.append(c); prevSpace = false } }
        map[sb.length] = text.length
        val t = text.toString().trimEnd()
        return t to marks.map { map[it.first].coerceAtMost(t.length) to it.second }.distinctBy { it.first }.sortedBy { it.first }
    }
    private val stamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /** Parses LRC text ("[mm:ss.xx] words", several stamps per line allowed) into lines sorted by time. Tags like [ar:..] are ignored. */
    fun parse(text: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        // the LRC "[offset:+500]" tag: positive means the lyric should show that much earlier
        val shift = text.lines().firstNotNullOfOrNull { offsetTag.find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L
        for (raw in text.lines()) {
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            var words = raw.substring(stamps.last().range.last + 1).trim()
            // an end-of-line tag "<mm:ss.xx>" (written by this app from Whisper's word times): when the line stops being sung
            var endMs: Long? = null
            endTag.find(words)?.let { e ->
                val f = e.groupValues[3]
                val ms = when (f.length) { 0 -> 0L; 1 -> f.toLong() * 100; 2 -> f.toLong() * 10; else -> f.take(3).toLong() }
                endMs = e.groupValues[1].toLong() * 60_000 + e.groupValues[2].toLong() * 1000 + ms
                words = words.substring(0, e.range.first).trim()
            }
            val (clean, marks) = wordMarks(words); words = clean.trim()
            for (m in stamps) {
                val frac = m.groupValues[3]
                val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                out += LyricLine((m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toLong() * 1000 + ms - shift).coerceAtLeast(0), words, endMs?.let { (it - shift).coerceAtLeast(0) },
                    marks.takeIf { it.size >= 2 && stamps.size == 1 }?.map { it.first to (it.second - shift).coerceAtLeast(0) })
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

    /**
     * Karaoke approximation: how many characters of [text] are "lit" at [posMs]. LRCLIB only times whole lines, so the line's
     * singing time is spread over its characters; it is capped (about 100 ms a character) so a long instrumental gap after the line does not slow the fill.
     * Lit means up to the end of the last word that has started.
     */
    fun litChars(text: String, startMs: Long, nextMs: Long?, posMs: Long, endMs: Long? = null, marks: List<Pair<Int, Long>>? = null): Int {
        if (text.isBlank() || posMs < startMs) return 0
        if (marks != null) { // the lyric says when each word starts: lit up to the end of the last one that started
            var lit = 0
            for ((off, t) in marks) { if (t > posMs) break; lit = text.indexOf(' ', off).let { if (it < 0) text.length else it } }
            return lit
        }
        val span = (nextMs ?: (startMs + 4000)) - startMs
        // with the end of the line known (Whisper heard its last word), the fill follows the real singing time
        val vocal = if (endMs != null && endMs > startMs) minOf(span.toDouble(), (endMs - startMs).toDouble()).coerceAtLeast(600.0)
            else minOf(span.toDouble(), text.length * 100.0 + 500.0).coerceAtLeast(600.0)
        val target = ((posMs - startMs) / vocal).coerceIn(0.0, 1.0) * text.length
        var lit = 0
        for (w in Regex("""\S+""").findAll(text)) { if (w.range.first <= target) lit = w.range.last + 1 else break }
        return lit
    }

    /** Plain (unsynced) text as lines; blank lines are dropped. */
    /**
     * Rough automatic timing for unsynced lyrics: the singing is assumed to run from a short intro to a short outro,
     * and each line gets time in proportion to its length. Only an estimate; the user fixes it with the sync offset or by tapping.
     */
    fun estimate(lines: List<LyricLine>, durationMs: Long): List<LyricLine> {
        if (lines.isEmpty() || durationMs <= 0) return lines
        val start = minOf(15_000L, durationMs / 12); val end = durationMs - minOf(8_000L, durationMs / 20)
        val weights = lines.map { it.text.length.coerceAtLeast(8).toDouble() }
        val total = weights.sum(); var acc = 0.0
        return lines.mapIndexed { i, l -> LyricLine(start + ((end - start) * acc / total).toLong(), l.text).also { acc += weights[i] } }
    }

    /** LRC text ("[mm:ss.xx]line") for lines that already carry their time. */
    fun toLrc(lines: List<LyricLine>): String = lines.joinToString("\n") { l ->
        val cs = l.timeMs / 10
        val end = l.endMs?.let { val e = it / 10; "<%02d:%02d.%02d>".format(e / 6000, e / 100 % 60, e % 100) } ?: ""
        fun tag(t: Long) = (t / 10).let { "<%02d:%02d.%02d>".format(it / 6000, it / 100 % 60, it % 100) }
        val body = l.marks?.let { mk -> val sb = StringBuilder(l.text); mk.sortedByDescending { it.first }.forEach { (o, t) -> sb.insert(o.coerceIn(0, sb.length), tag(t)) }; sb.toString() } ?: l.text
        "[%02d:%02d.%02d]%s%s".format(cs / 6000, cs / 100 % 60, cs % 100, body, end)
    }

    fun plain(text: String): List<LyricLine> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { LyricLine(0, it) }
}
