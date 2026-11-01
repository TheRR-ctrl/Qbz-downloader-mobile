package com.qbdlx.mobile

import org.json.JSONObject

/**
 * Reads the extra fields that end up in the audio tags (genre, label, ISRC, ...). Kept apart from the
 * browsing parsers and wrapped in runCatching: a surprise in this data must never break Home or Search,
 * it only means that tag is left out.
 */

/** JSON null or a missing key is "", unlike org.json's optString, which would return the text "null". */
internal fun JSONObject.text(key: String): String = when (val v = opt(key)) {
    null, JSONObject.NULL -> ""
    is String -> v.trim()
    else -> v.toString().trim()
}

/** Names are usually strings, but some newer responses use `{ "display": ... }`. */
private fun JSONObject.nameText(): String = when (val n = opt("name")) {
    null, JSONObject.NULL -> ""
    is JSONObject -> n.text("display")
    else -> n.toString().trim()
}

private fun JSONObject.nameOfChild(key: String): String = optJSONObject(key)?.nameText().orEmpty()

internal fun Album.withTagDetails(o: JSONObject): Album = runCatching {
    copy(
        albumArtist = mainArtists(o).ifBlank { artist },
        genre = o.nameOfChild("genre"),
        label = o.nameOfChild("label"),
        upc = o.text("upc"),
        copyright = o.text("copyright"),
        mediaCount = o.optInt("media_count"),
        productType = o.text("product_type"),
        releaseDate = o.text("release_date_original"),
        version = o.text("version"),
        durationSec = o.optInt("duration"),
    )
}.getOrDefault(this)

internal fun Track.withTagDetails(o: JSONObject): Track = runCatching {
    copy(
        isrc = o.text("isrc"),
        composer = o.nameOfChild("composer"),
        explicit = o.optBoolean("parental_warning"),
        albumTrackNumber = o.optInt("track_number"),
        performers = o.text("performers"),
    )
}.getOrDefault(this)

/** "A", "A & B" or "A, B & C" from the album's main artists, as the desktop app does; "" if the list is absent. */
internal fun mainArtists(o: JSONObject): String {
    val arr = o.optJSONArray("artists") ?: return ""
    val names = (0 until arr.length()).mapNotNull { i ->
        val a = arr.optJSONObject(i) ?: return@mapNotNull null
        val roles = a.optJSONArray("roles")
        val isMain = roles != null && (0 until roles.length()).any { roles.optString(it) == "main-artist" }
        if (isMain) a.nameText().takeIf { it.isNotBlank() } else null
    }
    return when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " & " + names.last()
    }
}

/**
 * Fills the blanks of [this] album (typically a partial one from a playlist or favourites entry) from
 * the full album, without ever replacing a value that is already there.
 */
internal fun Album.fillFrom(full: Album): Album = copy(
    albumArtist = albumArtist.ifBlank { full.albumArtist },
    genre = genre.ifBlank { full.genre },
    label = label.ifBlank { full.label },
    upc = upc.ifBlank { full.upc },
    copyright = copyright.ifBlank { full.copyright },
    mediaCount = if (mediaCount > 0) mediaCount else full.mediaCount,
    productType = productType.ifBlank { full.productType },
    releaseDate = releaseDate.ifBlank { full.releaseDate },
    version = version.ifBlank { full.version },
    tracksCount = if (tracksCount > 0) tracksCount else full.tracksCount,
    coverUrl = coverUrl ?: full.coverUrl,
)
