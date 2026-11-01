package com.qbdlx.scanner

/** Decides whether a song's own tags are good enough to search with, or the song has to be recognised by its sound. */
object Tags {
    private val generic = Regex(
        """^(track|pista|audio|voice|voz|recording|grabaci[oó]n|untitled|unknown|desconocid[oa]|sin t[ií]tulo|new recording|nueva grabaci[oó]n|song|cancion|canci[oó]n|music|musica|m[uú]sica)?[\s_\-.#]*\d*$""",
        RegexOption.IGNORE_CASE,
    )

    fun isUnknown(s: String?) = s == null || s.isBlank() || s.trim().lowercase() in setOf("<unknown>", "unknown", "desconocido", "unknown artist", "artista desconocido")

    /** A title that says nothing: empty, only a number, "Track 03", "audio_0012" … */
    fun isGenericTitle(title: String?) = isUnknown(title) || generic.matches(title!!.trim())

    /** True when searching with this title and artist is hopeless, so the sound should be used. */
    fun needsFingerprint(title: String?, artist: String?) = isUnknown(artist) || isGenericTitle(title)
}

/** Folders the scan skips (WhatsApp audios, voice notes …): a folder is skipped when its path contains one of the names. */
object OmittedFolders {
    val defaults = listOf("WhatsApp", "Telegram")

    /** Call and voice recordings are never songs: always left out. */
    val recordings = listOf("call", "record", "voice", "grab", "llamada", "memo", "dictaphone")

    /** The places songs normally live; with "only common folders" on, anything else is left out. */
    val common = listOf("music", "musica", "música", "download", "descarga", "documents", "documentos", "podcast", "audiobook", "qobuz", "qbdlx", "deezer", "spotify", "itunes", "dcim", "movies")

    fun isRecording(relativePath: String?) = relativePath.orEmpty().lowercase().let { p -> recordings.any { p.contains(it) } }

    fun isCommon(relativePath: String?) = relativePath.orEmpty().lowercase().let { p -> p.isBlank() || common.any { p.contains(it) } }

    fun matches(relativePath: String?, omitted: Collection<String>): Boolean {
        val path = relativePath.orEmpty().lowercase()
        return omitted.any { it.isNotBlank() && path.contains(it.trim().lowercase()) }
    }

    /** The last folder of a path, for showing: "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/" gives "WhatsApp Audio". */
    fun shortName(relativePath: String) = relativePath.trim('/').substringAfterLast('/').ifBlank { relativePath }
}
