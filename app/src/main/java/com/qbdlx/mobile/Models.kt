package com.qbdlx.mobile

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val tracksCount: Int,
    val year: String,
    val quality: String,
    val hiRes: Boolean,
    // Tag data (written into the audio file). Optional: Discover cards and playlist entries only
    // carry part of it, so everything here defaults to "unknown" and is left out of the tags.
    val albumArtist: String = "",
    val genre: String = "",
    val label: String = "",
    val upc: String = "",
    val copyright: String = "",
    val mediaCount: Int = 0,
    val productType: String = "",
    val releaseDate: String = "",
    val version: String = "",
    /** Length of the whole album in seconds (0 = unknown). */
    val durationSec: Int = 0,
) {
    /**
     * True when the fields that only the full album supplies are all present, so there is no need to ask Qobuz
     * for it. A playlist entry with just a genre and label still needs the lookup for barcode, discs and copyright.
     */
    val hasTagDetails: Boolean get() = genre.isNotBlank() && upc.isNotBlank() && mediaCount > 0
}

data class Track(
    val id: Long,
    val title: String,
    val version: String?,
    val number: Int,
    val disc: Int,
    val artist: String,
    val durationSec: Int,
    val streamable: Boolean,
    val quality: String,
    val hiRes: Boolean,
    val isrc: String = "",
    val composer: String = "",
    val explicit: Boolean = false,
    /** The track's own number on its album. [number] is rewritten to the playlist position for playlists. */
    val albumTrackNumber: Int = 0,
    /** Qobuz's credits string: "Name, Role, Role - Name, Role". See [parsePerformers]. */
    val performers: String = "",
) {
    val displayTitle: String get() = if (version.isNullOrBlank()) title else "$title ($version)"
}

data class AlbumDetail(val album: Album, val tracks: List<Track>)

/** A row of albums; [key] is the Discover container name, which [Strings.section] turns into a title. */
data class HomeSection(val key: String, val albums: List<Album>)

data class Playlist(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val tracksCount: Int,
    val owner: String,
    /** Up to four cover images, shown as a grid like the web player; [coverUrl] is the first of them. */
    val covers: List<String> = emptyList(),
)

/** A playlist's tracks, each with the album it belongs to (used for the download folder). */
data class PlaylistDetail(val playlist: Playlist, val tracks: List<Pair<Album, Track>>)

data class PlaylistTag(val name: String, val slug: String)

/** What the web player's Discover page shows. */
data class Discover(
    val newReleases: HomeSection?,
    val playlists: List<Playlist>,
    val playlistTags: List<PlaylistTag>,
    val sections: List<HomeSection>,
)

/** Qobuz `format_id` values, as offered by the quality picker. */
enum class Quality(val formatId: Int, val label: String) {
    HI_RES(7, "Hi-Res"),
    LOSSLESS(6, "Lossless"),
    MP3(5, "MP3"),
}
