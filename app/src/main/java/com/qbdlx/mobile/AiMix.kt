package com.qbdlx.mobile

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** One song offered to the AI: [n] is the short number the AI answers with, never a Qobuz or iPod id. */
class MixSong(val n: Int, val title: String, val artist: String, val album: String)

/** A mix the code accepted: a name and songs (as [MixSong.n]) in order, all of them real. */
class Mix(val name: String, val ns: List<Int>)

/**
 * "Genius-like" mixes made by AI, following the video-scout-pipeline pattern: the AI only PROPOSES, the code decides.
 * Everything the AI says is checked here (numbers that exist, no repeats, size limits); with no key, no network or a bad answer
 * the caller falls back to [localMixes], so the feature never depends on the AI.
 */
object AiMix {
    const val MAX_SONGS_SENT = 700
    const val MAX_MIXES = 8
    const val MIN_MIX = 5
    const val MAX_MIX = 50
    /** Mix lengths offered in the dialog (Apple's Genius used 25 to 100; the phone keeps them short). */
    val SIZES = listOf(15, 25, 50)
    const val DEFAULT_SIZE = 25

    /** Known good models, best first. Google retires models: `gemini-2.5-flash*` is now limited to keys that already used it. */
    val PREFERRED = listOf("gemini-3.5-flash-lite", "gemini-3.6-flash", "gemini-3.8-flash", "gemini-2.5-flash-lite", "gemini-2.5-flash")
    private val NOT_TEXT = listOf("tts", "image", "live", "audio", "embedding", "robotics", "omni", "transcribe", "computer", "native", "thinking", "preview", "exp")
    @Volatile private var cachedModels: List<String>? = null

    /** Picks, from what Google says it offers, the text models to try: the preferred ones first, then any other stable "flash". */
    fun rankModels(names: List<String>): List<String> {
        val ok = names.map { it.removePrefix("models/") }.filter { n -> n.startsWith("gemini-") && "flash" in n && NOT_TEXT.none { it in n } }
        val pref = PREFERRED.filter { it in ok }
        val rest = ok.filter { it !in PREFERRED }.sortedDescending()
        return (pref + rest).distinct().take(4)
    }

    /** Asks Google which models this key can use (so a retired model never breaks the feature); falls back to [PREFERRED] if that fails. */
    fun models(http: OkHttpClient, key: String): List<String> {
        cachedModels?.let { return it }
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000").header("x-goog-api-key", key.trim()).build()
        val found = try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) emptyList() else {
                    val arr = JSONObject(r.body?.string().orEmpty()).optJSONArray("models") ?: JSONArray()
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { o ->
                        val m = o.optJSONArray("supportedGenerationMethods")
                        m != null && (0 until m.length()).any { m.optString(it) == "generateContent" }
                    }.map { it.optString("name") }
                }
            }
        } catch (_: Exception) { emptyList() }
        val ranked = rankModels(found)
        if (ranked.isEmpty()) return PREFERRED
        cachedModels = ranked
        return ranked
    }

    /** Compact list for the prompt: only titles, artists and albums leave the phone (no audio, no covers, no account data). */
    fun catalog(songs: List<MixSong>): String =
        songs.joinToString("\n") { "${it.n}|${clean(it.title)}|${clean(it.artist)}|${clean(it.album)}" }

    private fun clean(s: String) = s.replace('|', '/').replace('\n', ' ').trim().take(80)

    fun systemPrompt(size: Int = DEFAULT_SIZE) = """
Eres un curador musical. Recibes el catálogo de canciones de una persona, una por línea con el formato número|título|artista|álbum, y un pedido.
Crea mixes (playlists) SOLO con canciones del catálogo, citándolas por su número. No inventes canciones ni números.
Cada mix: un nombre corto en español y unas $size canciones (nunca menos de $MIN_MIX ni más de $MAX_MIX) ordenadas para escucharse de corrido (energía, ánimo, artistas que se mezclen bien).
Si hay varios pedidos, haz un mix por cada uno, en ese orden. Si el pedido nombra una canción o artista, haz mixes de música parecida a eso usando lo que haya en el catálogo.
Si se da una canción de partida, cada mix debe empezar con ella y el resto debe parecerse a ella (género, época, ánimo, artistas afines).
Responde solo JSON.
""".trim()

    fun schema(): JSONObject = JSONObject("""
{"type":"OBJECT","properties":{"mixes":{"type":"ARRAY","items":{"type":"OBJECT","properties":{
"name":{"type":"STRING"},"ids":{"type":"ARRAY","items":{"type":"INTEGER"}}},"required":["name","ids"]}}},"required":["mixes"]}
""".trimIndent())

    /**
     * Turns the AI's JSON into accepted mixes. Drops unknown numbers and repeats, cuts to [MAX_MIX], drops mixes under [MIN_MIX],
     * keeps at most [wanted] mixes and de-duplicates names. Anything that is not valid JSON gives an empty list.
     */
    fun validate(json: String, valid: Set<Int>, wanted: Int = MAX_MIXES, seed: Int? = null, maxLen: Int = MAX_MIX): List<Mix> {
        val arr = try { JSONObject(stripFence(json)).optJSONArray("mixes") } catch (_: Exception) { null } ?: return emptyList()
        val out = ArrayList<Mix>()
        val names = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = TextClean.visible(o.optString("name")).trim().take(40)
            if (name.isBlank() || !names.add(name.lowercase())) continue
            val ids = o.optJSONArray("ids") ?: continue
            val seen = LinkedHashSet<Int>()
            for (k in 0 until ids.length()) { val n = ids.optInt(k, -1); if (n in valid) seen.add(n) }
            if (seed != null && seed in valid) { seen.remove(seed); val l = LinkedHashSet<Int>(); l.add(seed); l.addAll(seen); seen.clear(); seen.addAll(l) }
            val list = seen.toList().take(maxLen.coerceIn(MIN_MIX, MAX_MIX))
            if (list.size >= MIN_MIX) out.add(Mix(name, list))
            if (out.size >= wanted) break
        }
        return out
    }

    private fun stripFence(s: String): String = s.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    /** Without AI: one mix per artist with enough songs, plus a shuffled "Sorpresa". Deterministic for a given [seed]. */
    fun localMixes(songs: List<MixSong>, wanted: Int = MAX_MIXES, seed: Long = 0L, size: Int = MAX_MIX, first: MixSong? = null): List<Mix> {
        val out = ArrayList<Mix>()
        if (first != null) {
            val same = songs.filter { it.n != first.n && it.artist.isNotBlank() && it.artist.trim() == first.artist.trim() }
            val others = songs.filter { it.n != first.n && it !in same }.shuffled(java.util.Random(seed))
            val ns = (listOf(first) + same + others).map { it.n }.take(size.coerceIn(MIN_MIX, MAX_MIX))
            if (ns.size >= MIN_MIX) return listOf(Mix("Parecidas a ${first.title.take(30)}", ns))
        }
        songs.groupBy { it.artist.trim() }.filterKeys { it.isNotBlank() }.entries
            .filter { it.value.size >= MIN_MIX }.sortedByDescending { it.value.size }.take(wanted - 1)
            .forEach { (a, l) -> out.add(Mix(a, l.take(size).map { it.n })) }
        if (songs.size >= MIN_MIX) out.add(Mix("Sorpresa", songs.shuffled(java.util.Random(seed)).take(size).map { it.n }))
        return out.take(wanted)
    }

    class AiError(val kind: Kind, msg: String) : Exception(msg) { enum class Kind { NoKey, BadKey, Quota, Network, Blocked, Bad } }

    /** Checks a key against Google before saving it (like the pipeline's "probar y guardar"): lists one model, costs no quota. Null = works. */
    fun testKey(http: OkHttpClient, key: String): AiError? {
        if (key.isBlank()) return AiError(AiError.Kind.NoKey, "no key")
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1").header("x-goog-api-key", key.trim()).build()
        return try {
            http.newCall(req).execute().use { r ->
                when { r.isSuccessful -> null; r.code == 429 -> null; r.code in listOf(400, 401, 403) -> AiError(AiError.Kind.BadKey, "HTTP ${r.code}"); else -> AiError(AiError.Kind.Bad, "HTTP ${r.code}") }
            }
        } catch (e: java.io.IOException) { AiError(AiError.Kind.Network, e.message ?: "network") }
    }

    /** Plain REST call, like the pipeline's gemini.py: the key goes in a header, never in the URL. Throws [AiError]; returns the JSON text. */
    fun ask(http: OkHttpClient, key: String, requests: List<String>, catalog: String, size: Int = DEFAULT_SIZE, seedLine: String? = null): String {
        val pedidos = if (requests.isEmpty()) "Hazme mixes variados con lo mejor de mi catálogo."
        else "Pedidos (un mix por cada uno, en este orden):\n" + requests.joinToString("\n") { "- ${it.take(200)}" }
        val partida = seedLine?.let { "\nCanción de partida (va primera en cada mix): $it" } ?: ""
        return generate(http, key, systemPrompt(size), schema(), "$pedidos\nTamaño: unas $size canciones por mix.$partida\n\nCatálogo:\n$catalog")
    }

    const val MAX_IDEAS = 5

    fun ideasPrompt() = """
Eres un curador musical. Recibes una muestra del catálogo de canciones de una persona (número|título|artista|álbum).
Propón $MAX_IDEAS ideas de mixes que se puedan armar con esa música, cada una como un pedido corto en español (3 a 8 palabras), por ejemplo "Para conducir de noche" o "Lo más movido de <artista>".
Que sean distintas entre sí y que encajen con lo que hay en el catálogo. Responde solo JSON.
""".trim()

    fun ideasSchema(): JSONObject = JSONObject("""
{"type":"OBJECT","properties":{"ideas":{"type":"ARRAY","items":{"type":"STRING"}}},"required":["ideas"]}
""".trimIndent())

    /** Turns the AI's JSON into short, clean, distinct ideas (at most [MAX_IDEAS]). Bad JSON gives an empty list. */
    fun validateIdeas(json: String): List<String> {
        val arr = try { JSONObject(stripFence(json)).optJSONArray("ideas") } catch (_: Exception) { null } ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val t = TextClean.visible(arr.optString(i)).replace('\n', ' ').trim().trim('"', '\'', '.').take(60)
            if (t.length >= 3 && seen.add(t.lowercase())) out.add(t)
            if (out.size >= MAX_IDEAS) break
        }
        return out
    }

    /** Without AI: ideas taken from the library itself (its most repeated artists), plus a surprise. */
    fun localIdeas(songs: List<MixSong>): List<String> {
        val top = songs.groupBy { it.artist.trim() }.filterKeys { it.isNotBlank() }.entries
            .filter { it.value.size >= MIN_MIX }.sortedByDescending { it.value.size }.take(MAX_IDEAS - 1).map { "Lo mejor de ${it.key}" }
        return (top + "Sorpresa con todo mi catálogo").take(MAX_IDEAS)
    }

    fun askIdeas(http: OkHttpClient, key: String, catalog: String): String =
        generate(http, key, ideasPrompt(), ideasSchema(), "Catálogo:\n$catalog")

    private fun generate(http: OkHttpClient, key: String, system: String, schema: JSONObject, userText: String): String {
        if (key.isBlank()) throw AiError(AiError.Kind.NoKey, "no key")
        var last: AiError = AiError(AiError.Kind.Bad, "no model answered")
        for (model in models(http, key)) {
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userText)))))
                .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("responseSchema", schema).put("temperature", 0.8))
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .header("x-goog-api-key", key.trim())
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            try {
                http.newCall(req).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    when {
                        r.isSuccessful -> {
                            val j = JSONObject(text)
                            j.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }
                                ?.let { throw AiError(AiError.Kind.Blocked, it) }
                            val t = j.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                            if (t.isNullOrBlank()) last = AiError(AiError.Kind.Bad, "empty answer") else return t
                        }
                        // A wrong key will not get better with another model: stop. Quota or overload: try the next one.
                        r.code == 400 && text.contains("API_KEY_INVALID") || r.code == 401 -> throw AiError(AiError.Kind.BadKey, "HTTP ${r.code}")
                        // 403/404 on one model usually means "this model is not open to your key / was retired": try the next one.
                        r.code == 403 -> last = AiError(AiError.Kind.BadKey, "HTTP 403 $model")
                        r.code == 404 -> { cachedModels = null; last = AiError(AiError.Kind.Bad, "HTTP 404 $model") }
                        r.code == 429 || r.code >= 500 -> last = AiError(AiError.Kind.Quota, "HTTP ${r.code}")
                        else -> last = AiError(AiError.Kind.Bad, "HTTP ${r.code} $model")
                    }
                }
            } catch (e: AiError) { if (e.kind == AiError.Kind.BadKey || e.kind == AiError.Kind.Blocked) throw e; last = e }
            catch (e: java.io.IOException) { last = AiError(AiError.Kind.Network, e.message ?: "network") }
            catch (e: org.json.JSONException) { last = AiError(AiError.Kind.Bad, "bad json") }
        }
        throw last
    }
}

/** Removes the invisible characters AIs sometimes slip into text (zero-width, direction marks, soft hyphens), like texto_ia.py. */
object TextClean {
    private val invisible = Regex("[­​‌‍‎‏‪-‮⁠-⁤⁦-⁯﻿]")
    fun visible(s: String): String = invisible.replace(s.replace(' ', ' '), "").filter { it == '\n' || !it.isISOControl() }
}
