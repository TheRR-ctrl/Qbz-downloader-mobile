package com.qbdlx.mobile

/** One person in a track's credits and what they did, e.g. "Armando Avila" -> ["Director", "Producer"]. */
class Credit(val name: String, val roles: List<String>) {
    /** Up to two letters for the round avatar: first letters of the first two words. */
    val initials: String get() = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
}

/**
 * Reads Qobuz's `performers` text: people separated by " - ", each as "Name, Role, Role". The same name may appear
 * more than once (it is merged), roles are not repeated, and anything blank is ignored.
 */
fun parsePerformers(text: String): List<Credit> {
    val out = LinkedHashMap<String, MutableList<String>>()
    for (chunk in text.split(" - ")) {
        val parts = chunk.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val name = parts.firstOrNull() ?: continue
        val roles = out.getOrPut(name) { ArrayList() }
        for (r in parts.drop(1)) if (r !in roles) roles += r
    }
    return out.map { (n, r) -> Credit(n, r) }
}

/** "1h 01m" for an hour or more, "45m" under an hour; "" when unknown. */
fun albumDuration(seconds: Int): String = when {
    seconds <= 0 -> ""
    seconds >= 3600 -> "%dh %02dm".format(java.util.Locale.ROOT, seconds / 3600, seconds % 3600 / 60)
    else -> "%dm".format(java.util.Locale.ROOT, maxOf(1, seconds / 60))
}
