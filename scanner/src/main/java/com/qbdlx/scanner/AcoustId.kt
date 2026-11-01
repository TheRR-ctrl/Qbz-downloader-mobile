package com.qbdlx.scanner

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A guess from AcoustID's database. */
data class Recognized(val title: String, val artist: String, val album: String, val score: Double)

/** Looks a fingerprint up on acoustid.org. Needs the free application key from https://acoustid.org/new-application . */
object AcoustId {
    const val MIN_SCORE = 0.6

    fun lookup(apiKey: String, durationSec: Int, fingerprint: String): Recognized? {
        // POST: fingerprints are too long for a URL.
        val body = "client=${enc(apiKey)}&meta=recordings+releasegroups&duration=$durationSec&fingerprint=${enc(fingerprint)}"
        val conn = URL("https://api.acoustid.org/v2/lookup").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000; conn.readTimeout = 20_000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream).bufferedReader().readText()
            return parse(text)
        } finally {
            conn.disconnect()
        }
    }

    /** Asks AcoustID whether [apiKey] is accepted (a deliberately empty fingerprint is sent: only the key is judged). */
    fun checkKey(apiKey: String): KeyCheck {
        val params = "client=${enc(apiKey)}&duration=10&fingerprint=${enc("AQAAAA")}"
        return try {
            val post = judgeKey(request("https://api.acoustid.org/v2/lookup", params))
            if (post.verdict != Verdict.Invalid) post
            else judgeKey(request("https://api.acoustid.org/v2/lookup?$params", null)).let { get -> if (get.verdict == Verdict.Invalid) post else get } // second opinion by GET
        } catch (e: java.io.IOException) {
            KeyCheck(Verdict.NoConnection, e.message.orEmpty())
        }
    }

    private fun request(url: String, postBody: String?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000; conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "QBDLX-Scanner/0.2")
            if (postBody != null) {
                conn.requestMethod = "POST"; conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(postBody.toByteArray()) }
            }
            return (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream).bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    /** Error 4 is "invalid API key"; any other answer (even a complaint about the fingerprint) means the key was accepted. [KeyCheck.detail] is what AcoustID said. */
    fun judgeKey(text: String): KeyCheck {
        val json = try { JSONObject(text) } catch (e: Exception) { return KeyCheck(Verdict.NoConnection, text.take(120)) }
        if (json.optString("status") == "ok") return KeyCheck(Verdict.Valid, "ok")
        val error = json.optJSONObject("error")
        val detail = "code ${error?.optInt("code")}: ${error?.optString("message")}"
        val bad = error?.optInt("code") == 4 || error?.optString("message").orEmpty().contains("API key", ignoreCase = true)
        return KeyCheck(if (bad) Verdict.Invalid else Verdict.Valid, detail)
    }

    /** Throws [AcoustIdException] when AcoustID answers with an error (a wrong key, mostly). */
    fun parse(text: String): Recognized? {
        val json = JSONObject(text)
        if (json.optString("status") != "ok") {
            throw AcoustIdException(json.optJSONObject("error")?.optString("message") ?: "AcoustID error")
        }
        val results = json.optJSONArray("results") ?: return null
        var best: Recognized? = null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val score = r.optDouble("score", 0.0)
            val recordings = r.optJSONArray("recordings") ?: continue
            for (j in 0 until recordings.length()) {
                val rec = recordings.getJSONObject(j)
                val title = rec.optString("title")
                val artist = rec.optJSONArray("artists")?.let { a -> (0 until a.length()).joinToString(", ") { a.getJSONObject(it).optString("name") } }.orEmpty()
                if (title.isBlank() || artist.isBlank()) continue
                val album = rec.optJSONArray("releasegroups")?.optJSONObject(0)?.optString("title").orEmpty()
                if (best == null || score > best.score) best = Recognized(title, artist, album, score)
                break
            }
        }
        return best?.takeIf { it.score >= MIN_SCORE }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

class AcoustIdException(message: String) : Exception(message)

enum class Verdict { Valid, Invalid, NoConnection }

/** The verdict on a key and AcoustID's own words, to show when it is not accepted. */
class KeyCheck(val verdict: Verdict, val detail: String)
