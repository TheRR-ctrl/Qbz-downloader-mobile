package com.qbdlx.mobile.ipod

import android.content.Context
import android.graphics.BitmapFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Gets a cover (downloaded once and kept in the app's cache, so an album is never fetched twice) and turns it into the four
 * RGB565 frames of the nano 6G. Runs on the caller's thread: the send already runs on Dispatchers.IO.
 */
class IpodCovers(private val context: Context) {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val dir get() = File(context.cacheDir, "ipod-covers").also { it.mkdirs() }

    fun frames(url: String): Map<Int, ByteArray>? {
        val file = File(dir, MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".img")
        if (!file.exists() || file.length() == 0L) {
            val tmp = File(file.path + ".part")
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return null
                val body = r.body ?: return null
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            if (!tmp.renameTo(file)) return null
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) { file.delete(); return null }
        var sample = 1
        while (minOf(opts.outWidth, opts.outHeight) / (sample * 2) >= 480) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return try {
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            Nano6GCoverFrames.build(px, bmp.width, bmp.height)
        } finally { bmp.recycle() }
    }
}
