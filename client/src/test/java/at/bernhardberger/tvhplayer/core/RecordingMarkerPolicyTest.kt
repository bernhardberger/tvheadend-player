package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrCutpoint
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointAction
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingMarkerPolicyTest {
    @Test
    fun chaptersMergeWithScenesAndAreSortedDistinctAndInsideDuration() {
        assertEquals(listOf(0L, 20L, 30L, 90L), recordingMarkerPositions(
            listOf(marker(0, 20), marker(20, 90)), 100,
            listOf(30L, 20L, 30L, 0L, -1L, 100L, 110L),
        ))
    }

    @Test
    fun anyCommercialOrCutIntervalSuppressesAllChapters() {
        for (action in listOf(DvrCutpointAction.COMMERCIAL_BREAK, DvrCutpointAction.CUT)) {
            assertEquals(listOf(0L, 10L, 20L, 90L), recordingMarkerPositions(
                listOf(DvrCutpoint(10.milliseconds, 20.milliseconds, action), marker(20, 90)),
                100, listOf(11L, 21L, 30L),
            ))
            // Even an out-of-range interval establishes the source precedence.
            assertTrue(recordingMarkerPositions(
                listOf(DvrCutpoint(100.milliseconds, 110.milliseconds, action)), 100, listOf(30L),
            ).isEmpty())
        }
    }

    @Test
    fun muteAndUnknownDoNotSuppressChapters() {
        for (action in listOf(DvrCutpointAction.MUTE, DvrCutpointAction.UNKNOWN)) {
            assertEquals(listOf(0L, 30L), recordingMarkerPositions(
                listOf(DvrCutpoint(10.milliseconds, 20.milliseconds, action)), 100, listOf(30L),
            ))
        }
    }

    @Test
    fun chaptersAloneIncludeStartButNeverInventAnEnd() {
        assertEquals(listOf(0L, 30L), recordingMarkerPositions(emptyList(), 100, listOf(30L)))
        assertTrue(recordingMarkerPositions(emptyList(), 100, listOf(0L)).isEmpty())
        assertEquals(listOf(0L, 4_000L), recordingMarkerPositions(emptyList(), 10_000, listOf(0L, 4_000L)))
        assertTrue(recordingMarkerPositions(emptyList(), 100, listOf(-1L, 100L, 110L)).isEmpty())
        assertTrue(recordingMarkerPositions(emptyList(), 0, listOf(0L, 30L)).isEmpty())
    }

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
    fun muteAndUnknownActionsNeverBecomeNavigationMarkers() {
        val points = listOf(DvrCutpointAction.MUTE, DvrCutpointAction.UNKNOWN)
            .map { DvrCutpoint(0.milliseconds, 20.milliseconds, it) }
        assertTrue(recordingMarkerPositions(points, 100).isEmpty())
    }

    @Test
    fun commercialAndCutIntervalsContributeBothEndpoints() {
        val points = listOf(
            DvrCutpoint(10.milliseconds, 20.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
            DvrCutpoint(30.milliseconds, 40.milliseconds, DvrCutpointAction.CUT),
        )
        assertEquals(listOf(0L, 10L, 20L, 30L, 40L), recordingMarkerPositions(points, 100))
    }

    @Test
    fun commercialAndCutEndpointsStayStrictlyInsideVerifiedContent() {
        val points = listOf(
            DvrCutpoint(0.milliseconds, 20.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
            DvrCutpoint(30.milliseconds, 100.milliseconds, DvrCutpointAction.CUT),
            DvrCutpoint(40.milliseconds, 110.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
        )
        assertEquals(listOf(0L, 20L, 30L, 40L), recordingMarkerPositions(points, 100))
        assertTrue(recordingMarkerPositions(points, 0).isEmpty())
        assertTrue(recordingMarkerPositions(points, -1).isEmpty())
        assertTrue(recordingMarkerPositions(points, 20).isEmpty())
    }

    @Test
    fun duplicateMarksAndSceneBoundariesMergeOnlyAtExactPositions() {
        val commercial = DvrCutpoint(10.milliseconds, 20.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK)
        val points = listOf(
            commercial, commercial,
            DvrCutpoint(20.milliseconds, 30.milliseconds, DvrCutpointAction.CUT),
            marker(0, 20), marker(20, 21),
        )
        assertEquals(listOf(0L, 10L, 20L, 21L, 30L), recordingMarkerPositions(points, 100))
    }

    @Test
    fun overlappingCommercialRangesKeepAllEndpointsWithoutMuteOrUnknown() {
        val points = listOf(
            DvrCutpoint(30.milliseconds, 50.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
            DvrCutpoint(10.milliseconds, 40.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
            DvrCutpoint(15.milliseconds, 35.milliseconds, DvrCutpointAction.MUTE),
            DvrCutpoint(25.milliseconds, 45.milliseconds, DvrCutpointAction.UNKNOWN),
            DvrCutpoint(50.milliseconds, 60.milliseconds, DvrCutpointAction.CUT),
            marker(0, 70),
        )
        assertEquals(listOf(0L, 10L, 30L, 40L, 50L, 60L, 70L), recordingMarkerPositions(points, 100))
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
