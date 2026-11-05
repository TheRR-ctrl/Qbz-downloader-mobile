package com.qbdlx.mobile

import android.content.Context

/**
 * The folder inside Music where downloads go when no folder was chosen. New installs use the app's name; installs that already
 * existed keep "QobuzDownloaderX" (their songs are there), decided once and remembered.
 */
object DefaultFolder {
    private const val LEGACY = "QobuzDownloaderX"
    private const val APP = "QBDLX Mobile"
    @Volatile var name: String = LEGACY; private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("qbdlx", 0)
        name = prefs.getString("default_dir", null) ?: run {
            // Any saved setting means the app was used before this folder name existed.
            val chosen = if (prefs.all.isEmpty()) APP else LEGACY
            prefs.edit().putString("default_dir", chosen).apply()
            chosen
        }
    }
}
