package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry

/** Summary with description fallback for compact detail panes. */
fun programmeSummaryText(event: EpgEventEntry): String? {
    val summary = event.summary?.takeIf { it.isNotBlank() }
    if (summary != null) return summary
    return event.description?.takeIf { it.isNotBlank() }
}

/**
 * Full readable body for Content Details: summary, then description when it adds
 * information beyond the summary.
 */
fun programmeDetailsBody(event: EpgEventEntry): String? {
    val summary = event.summary?.takeIf { it.isNotBlank() }
    val description = event.description?.takeIf { it.isNotBlank() }
    return when {
        summary != null && description != null && description != summary ->
            "$summary\n\n$description"
        summary != null -> summary
        else -> description
    }
}

fun programmeHasAired(event: EpgEventEntry, nowSec: Long): Boolean =
    event.stop <= nowSec
