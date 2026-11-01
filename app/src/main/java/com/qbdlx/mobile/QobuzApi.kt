package com.qbdlx.mobile

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

class ApiException(message: String) : IOException(message)

/** One app-secret candidate reconstructed from the web player bundle, tagged with its timezone. */
class SecretCandidate(val timezone: String, val secret: String)

/** App ID plus the secret candidates read from one and the same web player bundle. */
class WebCredentials(val appId: String, val bundleVersion: String, val candidates: List<SecretCandidate>)

/** Blocking client for the Qobuz API; call from a background dispatcher. */
class QobuzApi(val http: OkHttpClient = OkHttpClient()) {
    var appId: String = ""
    var appSecret: String = ""
    var authToken: String? = null

    /** Where the current [appSecret] came from, shown (not the secret itself) in the diagnostics. */
    var secretSource: String = "—"

    /** A single HTTP response, kept raw so [getFileUrl] can record the code and body for diagnostics. */
    private class Raw(
        val code: Int,
        val body: String,
        val json: JSONObject?,
        val successful: Boolean,
        val serverDate: String?,
    )

    private fun getRaw(path: String, params: Map<String, String>): Raw {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("www.qobuz.com")
            .addPathSegments("api.json/0.2/$path")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val request = Request.Builder().url(url)
            .header("X-App-Id", appId)
            .apply { authToken?.let { header("X-User-Auth-Token", it) } }
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            return Raw(response.code, body, json, response.isSuccessful, response.header("Date"))
        }
    }

    private fun post(path: String, params: Map<String, String>): JSONObject {
        val request = Request.Builder().url("https://www.qobuz.com/api.json/0.2/$path")
            .header("X-App-Id", appId)
            .apply { authToken?.let { header("X-User-Auth-Token", it) } }
            .post(okhttp3.FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) } }.build())
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (!response.isSuccessful) throw ApiException(json?.optString("message")?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}")
            return json ?: throw ApiException("Invalid response from Qobuz")
        }
    }

    /** Adds tracks to the user's favourite tracks ("My tracks"). */
    fun addFavoriteTracks(trackIds: List<Long>) {
        trackIds.distinct().chunked(50).forEach { post("favorite/create", mapOf("track_ids" to it.joinToString(","), "type" to "track")) }
    }

    /** Removes tracks from the user's favourite tracks. */
    fun removeFavoriteTracks(trackIds: List<Long>) {
        trackIds.distinct().chunked(50).forEach { post("favorite/delete", mapOf("track_ids" to it.joinToString(","), "type" to "track")) }
    }

    fun addTrackToPlaylist(playlistId: String, trackId: Long) {
        post("playlist/addTracks", mapOf("playlist_id" to playlistId, "track_ids" to "$trackId", "no_duplicate" to "true"))
    }

    /** Creates a private playlist in the user's Qobuz library holding [trackIds]; returns the playlist id. */
    fun createPlaylist(name: String, trackIds: List<Long>): Long {
        val id = post("playlist/create", mapOf("name" to name, "is_public" to "false", "is_collaborative" to "false")).getLong("id")
        trackIds.distinct().chunked(50).forEach { post("playlist/addTracks", mapOf("playlist_id" to "$id", "track_ids" to it.joinToString(","), "no_duplicate" to "true")) }
        return id
    }

    private fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject {
        val r = getRaw(path, params)
        if (!r.successful) {
            throw ApiException(r.json?.optString("message")?.takeIf { it.isNotBlank() } ?: "HTTP ${r.code}")
        }
        return r.json ?: throw ApiException("Invalid response from Qobuz")
    }

    /** Returns the display name of the logged-in user. */
    fun loginWithToken(userId: String, token: String): String {
        authToken = token
        val json = get("user/get", mapOf("user_id" to userId, "user_auth_token" to token, "app_id" to appId))
        return json.optString("display_name")
    }

    /**
     * The app id/secret the official web player uses, read at runtime from its public bundle
     * (nothing is embedded in this app). Tokens issued by the web player are tied to this app id.
     */
    fun fetchWebCredentials(): Pair<String, String> {
        val page = http.newCall(Request.Builder().url("https://play.qobuz.com/login").build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("play.qobuz.com returned HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
        val bundlePath = Regex("""/resources/[0-9.]+-[a-z][0-9]+/bundle\.js""").find(page)?.value
            ?: throw ApiException("Could not find the web player bundle; enter App ID/Secret manually")
        val pattern = Regex("""production:\{api:\{appId:"(\d+)",appSecret:"([0-9a-f]+)"""")

        // The bundle is ~9 MB but the credentials sit about 1.5 MB in, so stop reading once found.
        http.newCall(Request.Builder().url("https://play.qobuz.com$bundlePath").build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("play.qobuz.com returned HTTP ${r.code}")
            val reader = r.body?.charStream() ?: throw ApiException("Empty response from play.qobuz.com")
            val text = StringBuilder()
            val buf = CharArray(64 * 1024)
            while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                val from = maxOf(0, text.length - 200) // a match may straddle two chunks
                text.append(buf, 0, n)
                pattern.find(text, from)?.let { return it.groupValues[1] to it.groupValues[2] }
            }
        }
        throw ApiException("Could not read the web player credentials; enter App ID/Secret manually")
    }

    /**
     * Forces a fresh read of the official web player bundle (no cache) and returns the App ID
     * together with the App Secret candidates reconstructed from that same bundle, using the
     * seed/timezone method the desktop app's Qo(penAPI).dll uses (AppIdRegex / BerlinSeedRegex /
     * BerlinInfoExtrasRegex). The plaintext `appSecret` inline in `production.api` is a decoy that
     * Qobuz rejects for signing, so it is deliberately not used here. Candidates are Berlin-first.
     */
    fun fetchFreshWebCredentials(): WebCredentials {
        val page = http.newCall(Request.Builder().url("https://play.qobuz.com/login").build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("play.qobuz.com returned HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
        val bundlePath = Regex("""/resources/[0-9.]+-[a-z][0-9]+/bundle\.js""").find(page)?.value
            ?: throw ApiException("No se encontró el bundle del reproductor web de Qobuz")
        val version = Regex("""/resources/([0-9.]+-[a-z][0-9]+)/""").find(bundlePath)?.groupValues?.get(1) ?: "?"

        // One-off full read: the seeds sit ~2.4 MB in, past where the login path's early-stop quits.
        val bundle = http.newCall(Request.Builder().url("https://play.qobuz.com$bundlePath").build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("play.qobuz.com returned HTTP ${r.code}")
            r.body?.string() ?: throw ApiException("Respuesta vacía de play.qobuz.com")
        }

        val appId = extractAppId(bundle) ?: throw ApiException("No se pudo leer el App ID del bundle")
        val candidates = extractSecretCandidates(bundle)
        if (candidates.isEmpty()) throw ApiException("No se pudo reconstruir ningún App Secret del bundle (¿cambió el formato?)")
        return WebCredentials(appId, version, candidates)
    }

    fun searchAlbums(query: String, limit: Int = 30): List<Album> {
        val json = get("catalog/search", mapOf("query" to query, "limit" to "$limit", "offset" to "0"))
        val items = json.optJSONObject("albums")?.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { parseAlbum(items.getJSONObject(it)) }
    }

    /** The artist Qobuz finds for [name]: the one whose name matches exactly (ignoring case), else the first result. Null when none. */
    fun findArtist(name: String): Pair<Long, String>? {
        val items = get("catalog/search", mapOf("query" to name, "limit" to "10", "offset" to "0")).optJSONObject("artists")?.optJSONArray("items") ?: return null
        val all = (0 until items.length()).map { items.getJSONObject(it) }
        val pick = all.firstOrNull { nameOf(it).trim().equals(name.trim(), ignoreCase = true) } ?: all.firstOrNull() ?: return null
        return pick.optLong("id") to nameOf(pick).ifBlank { name }
    }

    /** An artist's page: picture and every album (newest first). */
    fun artistPage(id: Long, name: String): ArtistPage {
        val albums = ArrayList<Album>(); var image: String? = null; var offset = 0
        while (offset < 1000) {
            val json = get("artist/get", mapOf("artist_id" to "$id", "extra" to "albums", "limit" to "100", "offset" to "$offset"))
            if (image == null) image = json.optJSONObject("image")?.let { img -> listOf("large", "medium", "small", "portrait").map { img.optString(it) }.firstOrNull { it.startsWith("http") } }
            val items = json.optJSONObject("albums")?.optJSONArray("items") ?: break
            for (i in 0 until items.length()) runCatching { parseAlbum(items.getJSONObject(i)) }.getOrNull()?.let { albums += it }
            if (items.length() < 100) break
            offset += 100
        }
        val list = albums.distinctBy { it.id }.sortedByDescending { it.releaseDate.ifBlank { it.year } }
        return ArtistPage(name, image ?: list.firstOrNull()?.coverUrl, list)
    }

    /** Tracks matching [query] (as (album, track) pairs, like a playlist's), for the player's Explore page. */
    fun searchTracks(query: String, limit: Int = 30): List<Pair<Album, Track>> {
        val json = get("catalog/search", mapOf("query" to query, "limit" to "$limit", "offset" to "0"))
        return json.optJSONObject("tracks")?.optJSONArray("items").mapObjects { t ->
            val album = t.optJSONObject("album")?.let { runCatching { parseAlbum(it) }.getOrNull() } ?: return@mapObjects null
            album to parseTrack(t, 0, album.artist)
        }.distinctBy { it.second.id }
    }

    /** The sections of the web player's Discover page (`discover/index`), skipping any that are missing. */
    fun discover(): Discover {
        val containers = get("discover/index").optJSONObject("containers")
            ?: throw ApiException("Unexpected Discover response from Qobuz")
        fun items(key: String) = containers.optJSONObject(key)?.optJSONObject("data")?.optJSONArray("items")
        fun releases(key: String) =
            HomeSection(key, items(key).mapObjects(::parseRelease).distinctBy { it.id }).takeIf { it.albums.isNotEmpty() }

        return Discover(
            newReleases = releases("new_releases"),
            playlists = items("playlists").mapObjects(::parsePlaylist).distinctBy { it.id },
            playlistTags = items("playlists_tags").mapObjects { t ->
                PlaylistTag(t.optString("name"), t.optString("slug")).takeIf { it.slug.isNotBlank() }
            }.distinctBy { it.slug },
            sections = listOfNotNull(
                releases("album_of_the_week"),
                releases("qobuzissims"),
                releases("ideal_discography"),
                releases("most_streamed"),
                releases("press_awards"),
            ),
        )
    }

    /** Qobuz playlists for one Discover tag (the chips under "Qobuz Playlists"). */
    fun discoverPlaylists(tagSlug: String, limit: Int = 30): List<Playlist> {
        val json = get("discover/playlists", mapOf("tags" to tagSlug, "limit" to "$limit", "offset" to "0"))
        val items = json.optJSONArray("items")
            ?: json.optJSONObject("data")?.optJSONArray("items")
            ?: json.optJSONObject("playlists")?.optJSONArray("items")
        return items.mapObjects(::parsePlaylist).distinctBy { it.id }
    }

    fun getPlaylist(playlistId: String): PlaylistDetail {
        val json = get("playlist/get", mapOf("playlist_id" to playlistId, "extra" to "tracks", "limit" to "500", "offset" to "0"))
        val playlist = parsePlaylist(json) ?: throw ApiException("Unexpected playlist response from Qobuz")
        val items = json.optJSONObject("tracks")?.optJSONArray("items")?.let { first ->
            // a playlist can have more than 500 songs: ask for the next pages too
            val total = json.optJSONObject("tracks")?.optInt("total", first.length()) ?: first.length()
            var offset = first.length()
            while (first.length() == 500 && offset < total && offset < 20_000) {
                val more = get("playlist/get", mapOf("playlist_id" to playlistId, "extra" to "tracks", "limit" to "500", "offset" to "$offset"))
                    .optJSONObject("tracks")?.optJSONArray("items") ?: break
                if (more.length() == 0) break
                for (i in 0 until more.length()) first.put(more.get(i))
                offset += more.length()
                if (more.length() < 500) break
            }
            first
        }
        val tracks = items.mapObjects { t ->
            val album = t.optJSONObject("album")?.let { runCatching { parseAlbum(it) }.getOrNull() } ?: return@mapObjects null
            album to parseTrack(t, 0, album.artist)
        }.mapIndexed { i, (album, track) -> album to track.copy(number = i + 1) }
        return PlaylistDetail(playlist, tracks)
    }

    /** The album covers of a playlist's first tracks (up to four, distinct), for the mosaic when the list gave only one. */
    fun playlistCovers(playlistId: String): List<String> {
        val json = get("playlist/get", mapOf("playlist_id" to playlistId, "extra" to "tracks", "limit" to "12", "offset" to "0"))
        val items = json.optJSONObject("tracks")?.optJSONArray("items")
        return items.mapObjects { t -> t.optJSONObject("album")?.let { coverOf(it) } }.distinct().take(4)
    }

    /** Qobuz returns at most 500 items per request: this asks page after page (offset 0, 500, 1000…) and joins the items. */
    private fun allPages(endpoint: String, params: Map<String, String>, section: String, known: ((JSONObject) -> Boolean)? = null): org.json.JSONArray {
        val all = org.json.JSONArray()
        var offset = 0
        while (offset < 20_000) {
            val sec = get(endpoint, params + mapOf("limit" to "500", "offset" to "$offset")).optJSONObject(section)
            val items = sec?.optJSONArray("items") ?: break
            for (i in 0 until items.length()) all.put(items.get(i))
            // Incremental refresh: a whole page of things we already have means everything after it is old too.
            if (known != null && items.length() > 0 && (0 until items.length()).all { known(items.getJSONObject(it)) }) break
            val total = sec.optInt("total", 0)
            offset += 500
            if (items.length() < 500 || (total in 1..offset)) break
        }
        return all
    }

    /** The user's own and subscribed playlists (the web player's Library > Playlists). */
    fun userPlaylists(): List<Playlist> =
        allPages("playlist/getUserPlaylists", emptyMap(), "playlists").mapObjects(::parsePlaylist).distinctBy { it.id }

    /** [knownIds]: albums already held; the search stops once a whole page of them is seen (only the newest are fetched). */
    fun favoriteAlbums(knownIds: Set<String>? = null): List<Album> =
        allPages("favorite/getUserFavorites", mapOf("type" to "albums"), "albums", knownIds?.let { k -> { o -> o.opt("id")?.toString() in k } }).mapObjects(::parseAlbum).distinctBy { it.id }

    /** The artists the user follows in Qobuz: name and picture. */
    fun favoriteArtists(): List<FavArtist> =
        allPages("favorite/getUserFavorites", mapOf("type" to "artists"), "artists").mapObjects { a ->
                val name = nameOf(a).trim()
                if (name.isBlank()) null
                else FavArtist(name, a.optJSONObject("image")?.let { img ->
                    listOf("large", "medium", "small", "portrait", "thumbnail").map { img.optString(it) }.firstOrNull { it.startsWith("http") }
                })
            }.distinctBy { it.name.lowercase() }

    fun favoriteTracks(knownIds: Set<Long>? = null): List<Pair<Album, Track>> =
        allPages("favorite/getUserFavorites", mapOf("type" to "tracks"), "tracks", knownIds?.let { k -> { o -> o.optLong("id") in k } }).mapObjects { t ->
                val album = t.optJSONObject("album")?.let(::parseAlbum) ?: return@mapObjects null
                album to parseTrack(t, 0, album.artist)
            }.distinctBy { it.second.id }

    /** Featured lists such as `new-releases`, `album-of-the-week`, `re-release-of-the-week`. */
    fun getFeatured(type: String, limit: Int = 12): List<Album> {
        val json = get("album/getFeatured", mapOf("type" to type, "limit" to "$limit", "offset" to "0"))
        val items = json.optJSONObject("albums")?.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { parseAlbum(items.getJSONObject(it)) }
    }

    fun getAlbum(albumId: String): AlbumDetail {
        val json = get("album/get", mapOf("album_id" to albumId))
        val album = parseAlbum(json)
        val items = json.optJSONObject("tracks")?.optJSONArray("items")
        val tracks = if (items == null) emptyList() else (0 until items.length()).map {
            parseTrack(items.getJSONObject(it), it + 1, album.artist)
        }
        return AlbumDetail(album, tracks)
    }

    private fun parseTrack(t: JSONObject, defaultNumber: Int, fallbackArtist: String) = Track(
        id = t.getLong("id"),
        title = t.optString("title"),
        version = t.optString("version").takeIf { v -> v.isNotBlank() && v != "null" },
        number = t.optInt("track_number", defaultNumber),
        disc = t.optInt("media_number", 1),
        artist = t.optJSONObject("performer")?.let(::nameOf)?.takeIf { it.isNotBlank() } ?: fallbackArtist,
        durationSec = t.optInt("duration"),
        streamable = t.optBoolean("streamable", true),
        quality = qualityLabel(t, "/"),
        hiRes = isHiRes(t),
    ).withTagDetails(t)

    /** [quality] is what Qobuz says the file is (null when it doesn't say); only used to judge upgrades. */
    class FileUrl(val url: String, val mimeType: String, val quality: AudioQuality? = null)

    fun getFileUrl(trackId: Long, formatId: Int): FileUrl {
        val ts = (System.currentTimeMillis() / 1000).toString()
        // Signature algorithm UNCHANGED — the base string is only extracted into a val so the
        // diagnostics can show it (with the secret redacted). The md5 input is byte-identical.
        val sigBase = "trackgetFileUrlformat_id${formatId}intentstreamtrack_id$trackId$ts$appSecret"
        val sig = md5(sigBase)
        val params = mapOf(
            "track_id" to "$trackId",
            "format_id" to "$formatId",
            "intent" to "stream",
            "request_ts" to ts,
            "request_sig" to sig,
        )

        val r = try {
            getRaw("track/getFileUrl", params)
        } catch (e: Exception) {
            Diagnostics.log(diagEntry(trackId, formatId, ts, sig, sigBase, null, null, null, redact(e.message ?: e.javaClass.simpleName)))
            throw e
        }

        Diagnostics.log(diagEntry(trackId, formatId, ts, sig, sigBase, r.code, r.json, r.serverDate, redact(r.body)))

        if (!r.successful) {
            throw ApiException(r.json?.optString("message")?.takeIf { it.isNotBlank() } ?: "HTTP ${r.code}")
        }
        val json = r.json ?: throw ApiException("Invalid response from Qobuz")
        val url = json.optString("url").takeIf { it.isNotBlank() }
            ?: throw ApiException("Qobuz returned no download URL (track not streamable on this account?)")
        val mime = json.optString("mime_type", "audio/flac")
        return FileUrl(url, mime, AudioQuality.fromResponse(mime, json.optInt("bit_depth"), json.optDouble("sampling_rate", 0.0)))
    }

    /** Builds one human-readable, fully redacted diagnostic block for a getFileUrl attempt. */
    private fun diagEntry(
        trackId: Long,
        formatId: Int,
        ts: String,
        sig: String,
        sigBase: String,
        code: Int?,
        json: JSONObject?,
        serverDate: String?,
        bodyOrError: String?,
    ): String {
        val signedUrl = json?.optString("url").orEmpty()
        // A success body contains a signed CDN URL (a temporary credential); keep only its host.
        // Qobuz escapes the slashes in the raw body ("https:\/\/…"), so a string replace on the
        // body would miss it — rebuild the body from the parsed JSON instead when a URL is present.
        val safeBody = when {
            bodyOrError == null -> null
            json != null && signedUrl.isNotBlank() -> redact(
                runCatching {
                    JSONObject(json.toString()).put("url", "<URL_FIRMADA host=${hostOf(signedUrl)}>").toString()
                }.getOrDefault("<respuesta OK con URL firmada oculta>")
            )
            else -> bodyOrError
        }
        return buildString {
            appendLine("[${timestamp()}] track/getFileUrl")
            appendLine("  HTTP: ${code?.toString() ?: "sin respuesta (fallo de red)"}")
            if (json != null) {
                appendLine("  status: ${json.optString("status", "—")}")
                appendLine("  code: ${json.opt("code") ?: "—"}")
                json.optString("message").takeIf { it.isNotBlank() }?.let { appendLine("  message: ${redact(it)}") }
            }
            appendLine("  track_id: $trackId")
            appendLine("  format_id (pedido): $formatId")
            appendLine("  request_ts (reloj del teléfono): $ts")
            serverDate?.let { appendLine("  servidor Date: $it   (desfase de reloj ≈ ${skewSeconds(it, ts)} s)") }
            appendLine("  request_sig: $sig  (md5)")
            appendLine("  firma base (secret oculto): ${redact(sigBase)}")
            appendLine("  app_id: ${appId.ifBlank { "—" }}")
            appendLine("  app_secret: ${secretInfo()}")
            appendLine("  origen del secret: $secretSource")
            // Transport (user point 5): request_sig hashes none of these, so header-vs-query doesn't
            // affect the signature. Kept unchanged; the desktop sends app_id/token as query params.
            appendLine("  query enviada: track_id, format_id, intent=stream, request_ts, request_sig")
            appendLine("  cabeceras enviadas: X-App-Id, X-User-Auth-Token (app_id/token no entran en la firma)")
            if (signedUrl.isNotBlank()) {
                appendLine("  OK · url host: ${hostOf(signedUrl)}")
                appendLine("  format_id (recibido): ${json?.opt("format_id") ?: "—"}")
                appendLine("  muestra/sample: ${json?.opt("sample") ?: "—"}  (true = solo 30 s de preview)")
                appendLine("  bit_depth: ${json?.opt("bit_depth") ?: "—"}  ·  sampling_rate: ${json?.opt("sampling_rate") ?: "—"}")
                appendLine("  restrictions: ${json?.opt("restrictions") ?: "—"}")
            }
            append("  respuesta cruda: ${safeBody ?: "—"}")
        }
    }

    /** Short SHA-256 prefix: lets two secrets be compared without ever revealing either one. */
    private fun fingerprint(s: String): String = fingerprintOf(s)

    private fun secretInfo(): String =
        if (appSecret.isBlank()) "AUSENTE (vacío)"
        else "presente (huella=${fingerprint(appSecret)} longitud=${appSecret.length})"

    /** Removes the secret and the auth token from any text before it is stored or shown. */
    private fun redact(s: String): String {
        var out = s
        if (appSecret.isNotBlank()) out = out.replace(appSecret, "<APP_SECRET huella=${fingerprint(appSecret)} longitud=${appSecret.length}>")
        authToken?.takeIf { it.isNotBlank() }?.let { out = out.replace(it, "<AUTH_TOKEN oculto>") }
        return out
    }

    private fun hostOf(url: String): String = runCatching { java.net.URI(url).host }.getOrNull() ?: "?"

    private fun timestamp(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())

    /** Local request_ts minus the server's Date header, in seconds; a large value means a skewed clock. */
    private fun skewSeconds(serverDate: String, ts: String): String = runCatching {
        val fmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
        val serverSec = fmt.parse(serverDate)!!.time / 1000
        (ts.toLong() - serverSec).toString()
    }.getOrDefault("?")

    private fun qualityLabel(o: JSONObject, sep: String): String {
        val bits = o.optInt("maximum_bit_depth")
        val rate = o.optDouble("maximum_sampling_rate")
        if (bits <= 0 || rate.isNaN()) return ""
        return if (sep == "/") "$bits/${rate.toInt()}" else "${bits}bit / ${rate.toInt()}kHz"
    }

    private fun isHiRes(o: JSONObject) = o.optInt("maximum_bit_depth") > 16 || o.optDouble("maximum_sampling_rate", 0.0) > 48.0

    private fun parseAlbum(a: JSONObject): Album = Album(
        id = a.getString("id"),
        title = a.optString("title"),
        artist = a.optJSONObject("artist")?.let(::nameOf).orEmpty(),
        coverUrl = coverOf(a),
        tracksCount = a.optInt("tracks_count"),
        year = a.optString("release_date_original").take(4),
        quality = qualityLabel(a, " "),
        hiRes = isHiRes(a),
    ).withTagDetails(a)

    /**
     * A "release" as Discover returns it, which differs from `album/get`: `track_count`,
     * `audio_info`, `artists[]` and `dates`. Each field falls back to the album shape.
     */
    private fun parseRelease(r: JSONObject): Album? {
        val id = r.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        val audio = r.optJSONObject("audio_info") ?: r
        val artist = r.optJSONArray("artists")?.optJSONObject(0)?.let(::nameOf)?.takeIf { it.isNotBlank() }
            ?: r.optJSONObject("artist")?.let(::nameOf).orEmpty()
        val year = r.optJSONObject("dates")?.optString("original").orEmpty().ifBlank { r.optString("release_date_original") }
        return Album(
            id = id,
            title = r.optString("title"),
            artist = artist,
            coverUrl = coverOf(r),
            tracksCount = r.optInt("track_count", r.optInt("tracks_count")),
            year = year.take(4),
            quality = qualityLabel(audio, " "),
            hiRes = isHiRes(audio),
        )
    }

    private fun parsePlaylist(p: JSONObject): Playlist? {
        val id = p.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        val image = p.optJSONObject("image")
        val cover = listOfNotNull(
            image?.optString("rectangle"),
            image?.optJSONArray("covers")?.optString(0),
            p.optJSONArray("image_rectangle")?.optString(0),
            p.optJSONArray("images300")?.optString(0),
        ).firstOrNull { it.isNotBlank() }
        // Qobuz lists the covers of a playlist's albums in several arrays (different sizes); the first non-empty one will do.
        val covers = listOf(p.optJSONArray("images300"), p.optJSONArray("images150"), p.optJSONArray("images"), image?.optJSONArray("covers"))
            .map { arr -> (0 until (arr?.length() ?: 0)).map { arr!!.optString(it) }.filter { it.isNotBlank() }.distinct() }
            .firstOrNull { it.isNotEmpty() }.orEmpty().take(4)
        return Playlist(
            id = id,
            name = p.optString("name"),
            coverUrl = cover,
            tracksCount = p.optInt("tracks_count"),
            owner = p.optJSONObject("owner")?.optString("name").orEmpty(),
            covers = covers.ifEmpty { listOfNotNull(cover) },
        )
    }

    private fun coverOf(o: JSONObject): String? = o.optJSONObject("image")?.let { img ->
        listOf("large", "small", "thumbnail").map { img.optString(it) }.firstOrNull { it.isNotBlank() }
    }

    /** Names are usually strings, but some newer responses use `{ "display": ... }`. */
    private fun nameOf(o: JSONObject): String = when (val n = o.opt("name")) {
        is JSONObject -> n.optString("display")
        null, JSONObject.NULL -> ""
        else -> n.toString()
    }

    private fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T?): List<T> =
        if (this == null) emptyList()
        else (0 until length()).mapNotNull { i -> optJSONObject(i)?.let { runCatching { f(it) }.getOrNull() } }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        /** The App ID from the bundle's `production.api` block (only the App ID lives there). */
        fun extractAppId(bundle: String): String? =
            Regex("""production:\{api:\{appId:"(\d+)"""").find(bundle)?.groupValues?.get(1)

        /**
         * Reconstructs the App Secret candidates from the web player bundle, one per timezone seed,
         * as `base64decode((seed + info + extras) minus the last 44 chars)`. Pure function over the
         * bundle text so it can be unit-tested. Berlin first (the variant the desktop app uses).
         */
        fun extractSecretCandidates(bundle: String): List<SecretCandidate> {
            val seedRe = Regex("""[a-z]\.initialSeed\("([\w=]+)",window\.utimezone\.([a-z]+)\)""")
            val infoRe = Regex("""name:"([A-Za-z]+/[A-Za-z]+)",info:"([\w=]+)",extras:"([\w=]+)"""")
            val infoByTz = infoRe.findAll(bundle).associate { m ->
                m.groupValues[1].substringAfterLast('/').lowercase() to (m.groupValues[2] to m.groupValues[3])
            }
            val out = ArrayList<SecretCandidate>()
            for (m in seedRe.findAll(bundle)) {
                val seed = m.groupValues[1]
                val tz = m.groupValues[2].lowercase()
                val (info, extras) = infoByTz[tz] ?: continue
                val concat = seed + info + extras
                if (concat.length <= 44) continue
                val secret = runCatching {
                    String(java.util.Base64.getDecoder().decode(concat.substring(0, concat.length - 44)), Charsets.UTF_8)
                }.getOrNull() ?: continue
                if (secret.matches(Regex("[0-9a-f]{32}"))) out.add(SecretCandidate(tz, secret))
            }
            // Berlin first (desktop parity), then the rest in bundle order; de-dup by timezone.
            return out.distinctBy { it.timezone }.sortedByDescending { it.timezone == "berlin" }
        }

        /** Short SHA-256 prefix of a secret, for diagnostics — never the secret itself. */
        fun fingerprintOf(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(8)
    }
}

/** An artist followed in the Qobuz library. */
data class ArtistPage(val name: String, val image: String?, val albums: List<Album>)
data class FavArtist(val name: String, val cover: String?)
