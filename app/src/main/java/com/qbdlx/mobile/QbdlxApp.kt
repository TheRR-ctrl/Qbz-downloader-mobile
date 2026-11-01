package com.qbdlx.mobile

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Owns the [MainViewModel] for the whole process instead of for one Activity, so the download
 * queue (and the session it signs requests with) survives the Activity being closed while the
 * foreground [DownloadService] keeps the process alive.
 */
class QbdlxApp : Application(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    override fun onCreate() {
        super.onCreate()
        // Keep the last crash, so it can be shared from Settings and the cause found.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                java.io.File(filesDir, CRASH_FILE).writeText("${java.util.Date()} thread ${t.name}\n${android.util.Log.getStackTraceString(e)}")
            }
            previous?.uncaughtException(t, e)
        }
    }

    companion object {
        const val CRASH_FILE = "last-crash.txt"

        /** Set by the ViewModel; read by [DownloadService] to show progress. */
        @Volatile var downloads: DownloadManager? = null

        /** Set by the ViewModel; read by [PlaybackService] for the "now playing" notification. */
        @Volatile var playback: PlaybackControls? = null
    }
}
