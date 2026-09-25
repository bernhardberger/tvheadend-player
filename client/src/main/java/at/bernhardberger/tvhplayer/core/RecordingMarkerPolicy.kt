package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrCutpoint

/** Chapter positions from the SDK's scene boundaries inside the verified recording duration. */
fun recordingMarkerPositions(cutpoints: List<DvrCutpoint>, verifiedDurationMs: Long): List<Long> {
    if (verifiedDurationMs <= 0L) return emptyList()
    val markers = cutpoints.asSequence()
        .mapNotNull { it.sceneBoundary?.inWholeMilliseconds }
        .filter { it > 0L && it < verifiedDurationMs }
        .distinct()
        .sorted()
        .toList()
    // Start is a navigation boundary, not a fabricated chapter. Empty metadata
    // still leaves ordinary seeking unchanged.
    return if (markers.isEmpty()) emptyList() else listOf(0L) + markers
}
