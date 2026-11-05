package com.qbdlx.lyrics

/** Pure helpers for editing a lyric by hand. */
object LyricEdit {
    /** Added to the source of a lyric the user corrected: automatic searches never offer to replace those. */
    const val EDITED = "editada"

    fun isEdited(source: String?) = source?.contains(EDITED) == true

    /** The source label after an edit: "LRCLIB" becomes "LRCLIB · editada"; an already edited one stays the same. */
    fun editedSource(source: String?): String = if (source.isNullOrBlank()) EDITED else if (isEdited(source)) source else "$source · $EDITED"

    /** Replaces the whole word [from] by [to] in every line (case-insensitive, accents count). Returns the new lines and how many words changed. */
    fun replaceWord(lines: List<LyricLine>, from: String, to: String): Pair<List<LyricLine>, Int> {
        val f = from.trim()
        if (f.isEmpty()) return lines to 0
        val re = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(f) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        var n = 0
        val out = lines.map { l ->
            val hits = re.findAll(l.text).count()
            if (hits == 0) l else { n += hits; LyricLine(l.timeMs, re.replace(l.text) { to.trim() }, l.endMs) }
        }
        return out to n
    }
}
