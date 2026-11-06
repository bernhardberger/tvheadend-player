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
        assertEquals(listOf(0L, 20L, 30L, 90L), stops(
            listOf(marker(0, 20), marker(20, 90)), 100,
            listOf(30L, 20L, 30L, 0L, -1L, 100L, 110L),
        ))
    }

    @Test
    fun anyCommercialOrCutIntervalSuppressesAllUnnamedChapters() {
        for (action in listOf(DvrCutpointAction.COMMERCIAL_BREAK, DvrCutpointAction.CUT)) {
            assertEquals(listOf(0L, 10L, 20L, 90L), stops(
                listOf(DvrCutpoint(10.milliseconds, 20.milliseconds, action), marker(20, 90)),
                100, listOf(11L, 21L, 30L),
            ))
            // Even an out-of-range interval establishes the source precedence.
            assertTrue(stops(listOf(DvrCutpoint(100.milliseconds, 110.milliseconds, action)), 100, listOf(30L)).isEmpty())
        }
    }

    @Test
    fun muteAndUnknownDoNotSuppressChapters() {
        for (action in listOf(DvrCutpointAction.MUTE, DvrCutpointAction.UNKNOWN)) {
            assertEquals(listOf(0L, 30L), stops(
                listOf(DvrCutpoint(10.milliseconds, 20.milliseconds, action)), 100, listOf(30L),
            ))
        }
    }

    @Test
    fun chaptersAloneIncludeStartButNeverInventAnEnd() {
        assertEquals(listOf(0L, 30L), stops(emptyList(), 100, listOf(30L)))
        assertEquals(listOf(0L), stops(emptyList(), 100, listOf(0L)))
        assertEquals(listOf(0L, 4_000L), stops(emptyList(), 10_000, listOf(0L, 4_000L)))
        assertTrue(stops(emptyList(), 100, listOf(-1L, 100L, 110L)).isEmpty())
        assertTrue(stops(emptyList(), 0, listOf(0L, 30L)).isEmpty())
    }

    @Test
    fun sceneMarkersUseTheSdkSceneBoundaryOfActualTvheadendIntervals() {
        val points = listOf(marker(0, 10_000), marker(10_000, 90_000))
        assertEquals(
            points.map { RecordingMark(RecordingMarkKind.CHAPTER, requireNotNull(it.sceneBoundary).inWholeMilliseconds) },
            recordingMarks(points),
        )
        assertEquals(listOf(0L, 10_000L, 90_000L), stops(points, 100_000))
    }

    @Test
    fun stopsAreSortedDistinctAndStrictlyInsideVerifiedContent() {
        val points = listOf(marker(10, 90), marker(0, 20), marker(1, 20), marker(20, 100), marker(0, 110))
        assertEquals(listOf(0L, 20L, 90L), stops(points, 100))
        // Display may extrapolate beyond 100, but cannot admit the 100 or 110 boundary.
        assertEquals(listOf(0L, 20L), stops(points, 90))
    }

    @Test
    fun muteAndUnknownActionsNeverBecomeMarks() {
        val points = listOf(DvrCutpointAction.MUTE, DvrCutpointAction.UNKNOWN)
            .map { DvrCutpoint(0.milliseconds, 20.milliseconds, it) }
        assertTrue(recordingMarks(points).isEmpty())
        assertTrue(stops(points, 100).isEmpty())
    }

    @Test
    fun commercialAndCutIntervalsStopAtBothEndpoints() {
        val points = listOf(
            DvrCutpoint(10.milliseconds, 20.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
            DvrCutpoint(30.milliseconds, 40.milliseconds, DvrCutpointAction.CUT),
        )
        assertEquals(listOf(0L, 10L, 20L, 30L, 40L), stops(points, 100))
    }

    @Test
    fun commercialAndCutEndpointsStayStrictlyInsideVerifiedContent() {
        val points = listOf(
            DvrCutpoint(0.milliseconds, 20.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
            DvrCutpoint(30.milliseconds, 100.milliseconds, DvrCutpointAction.CUT),
            DvrCutpoint(40.milliseconds, 110.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
        )
        assertEquals(listOf(0L, 20L, 30L, 40L), stops(points, 100))
        assertTrue(stops(points, 0).isEmpty())
        assertTrue(stops(points, -1).isEmpty())
        // The break starting at 0 is a stop; its end at the duration is not.
        assertEquals(listOf(0L), stops(points, 20))
    }

    @Test
    fun duplicateMarksAndSceneBoundariesMergeOnlyAtExactPositions() {
        val commercial = DvrCutpoint(10.milliseconds, 20.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK)
        val cut = DvrCutpoint(20.milliseconds, 30.milliseconds, DvrCutpointAction.CUT)
        val points = listOf(commercial, commercial, cut, marker(0, 20), marker(20, 21))
        val stops = recordingMarkStops(recordingMarks(points), 100)
        assertEquals(listOf(0L, 10L, 20L, 21L, 30L), stops.map { it.positionMs })
        // The cut starting where the break ends and a scene boundary lies takes that stop.
        assertEquals(RecordingMarkStop(20, RecordingMark(RecordingMarkKind.CUT, 20, 30)), stops[2])
    }

    @Test
    fun overlappingCommercialRangesMergeWithoutMuteOrUnknown() {
        val points = listOf(
            DvrCutpoint(30.milliseconds, 50.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
            DvrCutpoint(10.milliseconds, 40.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
            DvrCutpoint(15.milliseconds, 35.milliseconds, DvrCutpointAction.MUTE),
            DvrCutpoint(25.milliseconds, 45.milliseconds, DvrCutpointAction.UNKNOWN),
            DvrCutpoint(50.milliseconds, 60.milliseconds, DvrCutpointAction.CUT),
            marker(0, 70),
        )
        assertEquals(listOf(0L, 10L, 50L, 60L, 70L), stops(points, 100))
    }

    @Test
    fun namedChaptersSurviveIntervalsAndSameKindOverlapsMergeButTouchingOnesStaySeparate() {
        val points = listOf(
            DvrCutpoint(10.milliseconds, 30.milliseconds, DvrCutpointAction.CUT),
            DvrCutpoint(20.milliseconds, 40.milliseconds, DvrCutpointAction.CUT),
            DvrCutpoint(40.milliseconds, 50.milliseconds, DvrCutpointAction.CUT),
            DvrCutpoint(15.milliseconds, 35.milliseconds, DvrCutpointAction.COMMERCIAL_BREAK),
        )
        assertEquals(listOf(
            RecordingMark(RecordingMarkKind.CUT, 10, 40),
            RecordingMark(RecordingMarkKind.AD_BREAK, 15, 35),
            RecordingMark(RecordingMarkKind.CHAPTER, 25, name = "Story"),
            RecordingMark(RecordingMarkKind.CUT, 40, 50),
        ), recordingMarks(points, listOf(RecordingChapter(5), RecordingChapter(25, " Story "))))
    }

    @Test
    fun missingMarkersAndUnknownOrEmptyDurationNeverInventStops() {
        assertTrue(stops(emptyList(), 100).isEmpty())
        listOf(-1L, 0L, Long.MIN_VALUE).forEach {
            assertTrue(stops(listOf(marker(0, 20)), it).isEmpty())
        }
        assertTrue(stops(listOf(marker(0, 20)), 20).isEmpty())
    }

    /** The marks row's positions for [points] and unnamed file chapters at [chapterStartsMs]. */
    private fun stops(points: List<DvrCutpoint>, verifiedDurationMs: Long, chapterStartsMs: List<Long> = emptyList()) =
        recordingMarkStops(recordingMarks(points, chapterStartsMs.map { RecordingChapter(it) }), verifiedDurationMs)
            .map { it.positionMs }

    private fun marker(start: Long, end: Long) =
        DvrCutpoint(start.milliseconds, end.milliseconds, DvrCutpointAction.SCENE_MARKER)
}
