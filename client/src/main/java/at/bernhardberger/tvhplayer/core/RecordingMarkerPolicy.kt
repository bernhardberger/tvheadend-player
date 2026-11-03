package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrCutpoint
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointAction

/** File chapters supplement scene boundaries only when Tvheadend has no commercial/cut intervals. */
fun recordingMarkerPositions(
    cutpoints: List<DvrCutpoint>,
    verifiedDurationMs: Long,
    chapterStartsMs: List<Long> = emptyList(),
): List<Long> {
    if (verifiedDurationMs <= 0L) return emptyList()
    val chapters = if (cutpoints.any {
            it.action == DvrCutpointAction.COMMERCIAL_BREAK || it.action == DvrCutpointAction.CUT
        }) emptyList() else chapterStartsMs
    val markers = (cutpoints.asSequence()
        .flatMap { point ->
            val endpoints = when (point.action) {
                DvrCutpointAction.COMMERCIAL_BREAK, DvrCutpointAction.CUT -> listOf(point.start, point.end)
                else -> emptyList()
            }
            (listOfNotNull(point.sceneBoundary) + endpoints).asSequence()
        }
        .map { it.inWholeMilliseconds } + chapters.asSequence())
        .filter { it > 0L && it < verifiedDurationMs }
        .distinct()
        .sorted()
        .toList()
    // Start is a navigation boundary, not a fabricated chapter. Empty metadata
    // still leaves ordinary seeking unchanged.
    return if (markers.isEmpty()) emptyList() else listOf(0L) + markers
}
