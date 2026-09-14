package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrCutpoint
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointAction

/** TVHeadend scene-marker intervals end at the boundary, not at their start. */
fun recordingMarkerPositions(cutpoints: List<DvrCutpoint>, verifiedDurationMs: Long): List<Long> {
    if (verifiedDurationMs <= 0L) return emptyList()
    val markers = cutpoints.asSequence()
        .filter { it.action == DvrCutpointAction.SCENE_MARKER }
        .map { it.end.inWholeMilliseconds }
        .filter { it > 0L && it < verifiedDurationMs }
        .distinct()
        .sorted()
        .toList()
    // Start is a navigation boundary, not a fabricated chapter. Empty metadata
    // still leaves ordinary seeking unchanged.
    return if (markers.isEmpty()) emptyList() else listOf(0L) + markers
}
