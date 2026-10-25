package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.core.EpgEvent

internal fun detailsSubtitle(event: EpgEvent, resources: android.content.res.Resources, spoken: Boolean = false): String? =
    event.subtitle?.takeIf(String::isNotBlank)
        ?: at.bernhardberger.tvhplayer.ui.common.episodeLabel(event.episode, resources, spoken = spoken)

/** [rangeIndex] marks the time range, whose separator may break; other dashes stay joined. */
internal fun programmeFactsSegments(segments: List<String>, rangeIndex: Int = -1): String =
    segments.withIndex().joinToString(" • ") { (index, segment) ->
        segment.replace(' ', '\u00a0').let { if (index == rangeIndex) it else it.replace("–", "\u2060–\u2060") }
    }
