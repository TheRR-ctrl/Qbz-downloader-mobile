package com.qbdlx.mobile

/** One row of the Android Auto list. [id] starts with "l" for lyric rows so a tap on them is ignored. */
class CarRow(val id: String, val title: String, val subtitle: String = "")

/** The lyric rows Android Auto shows while a song plays: the song, then the line being sung between its neighbours. */
object CarLyrics {
    /** Empty when there is nothing synced to show (plain lyrics have no times to follow). */
    fun rows(title: String, artist: String, lyrics: Lyrics?, posMs: Long): List<CarRow> {
        if (lyrics == null || !lyrics.synced || lyrics.lines.isEmpty()) return emptyList()
        val lines = lyrics.lines
        val idx = LyricsParser.currentIndex(lines, posMs)
        fun at(i: Int, id: String) = CarRow(id, lines.getOrNull(i)?.text?.ifBlank { "♪" } ?: "♪")
        // Always four rows (previous, current, two next): the line being sung is the second; missing neighbours show "♪".
        val body = listOf(at(idx - 1, "l-prev"), CarRow("l-now", if (idx < 0) "♪ …" else "▶ " + lines[idx].text.ifBlank { "♪" }), at(idx + 1, "l-next"), at(idx + 2, "l-next2"))
        return listOf(CarRow("ltrack", title, artist)) + body
    }
}
