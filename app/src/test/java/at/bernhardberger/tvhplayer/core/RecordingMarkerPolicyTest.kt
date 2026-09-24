package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrCutpoint
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointAction
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingMarkerPolicyTest {
    @Test
    fun sceneMarkersUseTheSdkSceneBoundaryOfActualTvheadendIntervals() {
        val points = listOf(marker(0, 10_000), marker(10_000, 90_000))
        assertEquals(
            listOf(0L) + points.map { requireNotNull(it.sceneBoundary).inWholeMilliseconds },
            recordingMarkerPositions(points, 100_000),
        )
        assertEquals(listOf(0L, 10_000L, 90_000L), recordingMarkerPositions(points, 100_000))
    }

    @Test
    fun positionsAreSortedDistinctAndStrictlyInsideVerifiedContent() {
        val points = listOf(marker(10, 90), marker(0, 20), marker(1, 20), marker(20, 100), marker(0, 110))
        assertEquals(listOf(0L, 20L, 90L), recordingMarkerPositions(points, 100))
        // Display may extrapolate beyond 100, but cannot admit the 100 or 110 boundary.
        assertEquals(listOf(0L, 20L), recordingMarkerPositions(points, 90))
    }

    @Test
    fun otherActionsNeverBecomeNavigationMarkers() {
        val points = DvrCutpointAction.entries.filter { it != DvrCutpointAction.SCENE_MARKER }
            .map { DvrCutpoint(0.milliseconds, 20.milliseconds, it) }
        assertTrue(recordingMarkerPositions(points, 100).isEmpty())
    }

    @Test
    fun missingMarkersAndUnknownOrEmptyDurationNeverInventEndpoints() {
        assertTrue(recordingMarkerPositions(emptyList(), 100).isEmpty())
        listOf(-1L, 0L, Long.MIN_VALUE).forEach {
            assertTrue(recordingMarkerPositions(listOf(marker(0, 20)), it).isEmpty())
        }
        assertTrue(recordingMarkerPositions(listOf(marker(0, 20)), 20).isEmpty())
    }

    private fun marker(start: Long, end: Long) =
        DvrCutpoint(start.milliseconds, end.milliseconds, DvrCutpointAction.SCENE_MARKER)
}
