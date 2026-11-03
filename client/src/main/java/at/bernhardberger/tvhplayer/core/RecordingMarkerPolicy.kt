package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrCutpoint
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointAction

/** Navigation positions from scene boundaries and commercial/cut endpoints inside the verified duration. */
fun recordingMarkerPositions(cutpoints: List<DvrCutpoint>, verifiedDurationMs: Long): List<Long> {
    if (verifiedDurationMs <= 0L) return emptyList()
    val markers = cutpoints.asSequence()
        .flatMap { point ->
            val endpoints = when (point.action) {
                DvrCutpointAction.COMMERCIAL_BREAK, DvrCutpointAction.CUT -> listOf(point.start, point.end)
                else -> emptyList()
            }
            (listOfNotNull(point.sceneBoundary) + endpoints).asSequence()
        }
        .map { it.inWholeMilliseconds }
        .filter { it > 0L && it < verifiedDurationMs }
        .distinct()
        .sorted()
        .toList()
    // Start is a navigation boundary, not a fabricated chapter. Empty metadata
    // still leaves ordinary seeking unchanged.
    return if (markers.isEmpty()) emptyList() else listOf(0L) + markers
}
