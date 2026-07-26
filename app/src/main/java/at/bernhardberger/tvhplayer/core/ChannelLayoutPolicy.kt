package at.bernhardberger.tvhplayer.core

enum class ChannelBrowseLayout {
    LIST_WITH_DETAILS,
    LARGE_CARDS,
}

fun resolveChannelBrowseLayout(storedValue: String?): ChannelBrowseLayout =
    when (storedValue) {
        ChannelBrowseLayout.LARGE_CARDS.name -> ChannelBrowseLayout.LARGE_CARDS
        else -> ChannelBrowseLayout.LIST_WITH_DETAILS
    }

/** Initials fallback when a channel has no picon. */
fun channelInitials(name: String): String {
    val parts = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    if (parts.isEmpty()) return "?"
    return parts
        .take(2)
        .mapNotNull { part -> part.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() }
        .joinToString("")
        .ifBlank { name.take(1).uppercase() }
}
