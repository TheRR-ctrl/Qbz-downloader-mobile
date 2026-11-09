package com.qbdlx.mobile

import android.content.Context
import java.io.File

/** The last playback events (state changes and errors), kept in a small file so "why does it not play" can be read from the diagnostic report. */
object PlaybackLog {
    private const val FILE = "playback-log.txt"
    private const val MAX = 80

    @Synchronized fun add(context: Context, line: String) {
        runCatching {
            val f = File(context.filesDir, FILE)
            val old = if (f.exists()) f.readLines() else emptyList()
            val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            f.writeText((old + "$t $line").takeLast(MAX).joinToString("\n"))
        }
    }

    fun read(context: Context): String = runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()
}
