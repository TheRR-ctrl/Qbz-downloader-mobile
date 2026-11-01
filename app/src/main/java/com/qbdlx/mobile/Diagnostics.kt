package com.qbdlx.mobile

import androidx.compose.runtime.mutableStateListOf

/**
 * In-memory capture of Qobuz `track/getFileUrl` attempts, shown on the "Diagnóstico Qobuz" screen.
 *
 * This only stores strings that [QobuzApi] has already redacted: the App Secret, the auth token
 * and any signed download URL never reach this object in clear. It is a plain singleton so the
 * API client (which has no Context) can append to it, while the UI observes the snapshot list.
 */
object Diagnostics {
    private const val MAX_ENTRIES = 50

    /** One formatted, already-redacted block per attempt, oldest first. Observable by Compose. */
    val entries = mutableStateListOf<String>()

    // Downloads run on several threads at once; the add/trim pair must not interleave.
    @Synchronized
    fun log(entry: String) {
        entries.add(entry)
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
    }

    @Synchronized
    fun clear() = entries.clear()

    /** The whole log as one block of text, oldest first, separated by rules. */
    @Synchronized
    fun dump(): String = entries.joinToString("\n\n" + "-".repeat(44) + "\n\n")
}
