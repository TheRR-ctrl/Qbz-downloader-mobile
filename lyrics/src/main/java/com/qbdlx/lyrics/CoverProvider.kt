package com.qbdlx.lyrics

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Serves the song's cover to Android Auto by address instead of sending the image on every list refresh: the car keeps
 * what it already downloaded for an address, so the icon does not blink when the lyric line changes.
 */
class CoverProvider : ContentProvider() {
    override fun onCreate() = true
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = File(context!!.cacheDir, "car-cover.png")
        if (!f.exists()) throw java.io.FileNotFoundException()
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String = "image/png"
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0

    companion object {
        const val AUTHORITY = "com.qbdlx.lyrics.cover"
        /** Writes [bmp] where the provider reads it and returns the address, which changes with [tag] (one per song). */
        fun publish(context: android.content.Context, bmp: android.graphics.Bitmap, tag: String): Uri? = runCatching {
            File(context.cacheDir, "car-cover.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            Uri.parse("content://$AUTHORITY/c/${Uri.encode(tag)}")
        }.getOrNull()
    }
}
