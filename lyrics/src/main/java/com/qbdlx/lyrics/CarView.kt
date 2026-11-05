package com.qbdlx.lyrics

enum class Status { NO_ACCESS, NO_MEDIA, LOADING, SYNCED, PLAIN, NOT_FOUND, ERROR }

class TrackInfo(val title: String, val artist: String, val album: String, val durationMs: Long, val pkg: String) {
    val key get() = "$pkg|$title|$artist|$album|${durationMs / 1000}"
    /** Key of the saved lyrics: the song itself, not the app playing it, so lyrics fetched ahead (Prefetch) are found whichever app plays it. */
    val storeKey get() = Prefetch.storeKey(title, artist)
}

class LyricsState(
    val status: Status = Status.NO_ACCESS,
    val track: TrackInfo? = null,
    val lines: List<LyricLine> = emptyList(),
    val playing: Boolean = false,
    val source: String? = null,
)

/** What one row of the car's list shows. [current] marks the line being sung. */
class Row(val id: String, val title: String, val subtitle: String = "", val current: Boolean = false, val cover: Boolean = false)

object CarView {
    /** The rows for the car screen: the song, then the current lyric line with its neighbours (or a message). */
    fun rows(state: LyricsState, posMs: Long): List<Row> {
        val head = state.track?.let { listOf(Row("track", it.title, it.artist, cover = true)) }.orEmpty()
        return head + when (state.status) {
            Status.NO_ACCESS -> listOf(Row("msg", "Falta el acceso a notificaciones", "Ábrelo en el teléfono: Q Lyrics"))
            Status.NO_MEDIA -> listOf(Row("msg", "Pon una canción para ver su letra"))
            Status.LOADING -> listOf(Row("msg", "Buscando la letra…"))
            Status.NOT_FOUND -> listOf(Row("msg", "No hay letra para esta canción"))
            Status.ERROR -> listOf(Row("msg", "No se pudo cargar la letra", "Revisa la conexión"))
            Status.SYNCED -> window(state.lines, Lrc.indexAt(state.lines, posMs))
            Status.PLAIN -> {
                val d = state.track?.durationMs ?: 0
                val idx = if (d > 0 && state.lines.isNotEmpty()) ((posMs.toDouble() / d) * state.lines.size).toInt().coerceIn(0, state.lines.size - 1) else 0
                window(state.lines, idx)
            }
        }
    }

    /** "Title - Artist(s)" on one row, so it takes less room than a title with a subtitle. */
    fun header(title: String, artist: String) = if (artist.isBlank()) title else "$title - $artist"

    /** Two lyric rows under the song's own row: the one being sung and the next (the previous one was dropped so everything fits the car screen); missing ones show "♪". */
    private fun window(lines: List<LyricLine>, idx: Int): List<Row> {
        if (lines.isEmpty()) return listOf(Row("msg", "♪"))
        fun at(i: Int, id: String) = Row(id, lines.getOrNull(i)?.text?.ifBlank { "♪" } ?: "♪")
        val mid = if (idx < 0) "♪ …" else "▶ " + lines[idx].text.ifBlank { "♪" }
        return listOf(Row("l-now", mid, current = true), at(idx + 1, "l-next"))
    }
}
