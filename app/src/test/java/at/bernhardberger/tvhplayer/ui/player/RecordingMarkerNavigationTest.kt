package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.core.RecordingMarkStop
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingMarkerNavigationTest {
    private val markers = listOf(10_000L, 30_000L, 60_000L)

    @Test fun entrySelectsTheNearerMarkBeforeThePosition() = selects(14_000L, 10_000L)
    @Test fun entrySelectsTheNearerMarkAfterThePosition() = selects(26_000L, 30_000L)
    @Test fun anEqualDistanceSelectsTheEarlierMark() = selects(20_000L, 10_000L)
    @Test fun aPositionBeforeAllMarksSelectsTheFirst() = selects(0L, 10_000L)
    @Test fun aPositionAfterAllMarksSelectsTheLast() = selects(90_000L, 60_000L)
    @Test fun aPositionExactlyOnAMarkSelectsThatMark() = selects(30_000L, 30_000L)

    @Test fun entryNearStartSkipsTheArtificialStartButLeftStillReachesIt() {
        val stops = listOf(RecordingMarkStop(0L),
            RecordingMarkStop(30_000L, RecordingMark(RecordingMarkKind.CHAPTER, 30_000L)))
        val navigation = RecordingMarkerNavigation()
        navigation.show(recordingMarkerOpeningPositions(stops), 1_000L)
        assertEquals(30_000L, navigation.selectedMs)
        navigation.move(stops.map { it.positionMs }, -1)
        assertEquals(0L, navigation.selectedMs)
        navigation.move(stops.map { it.positionMs }, 1)
        assertEquals(30_000L, navigation.selectedMs)
    }

    @Test fun entryWithOnlyTheArtificialStartOpensThere() {
        val navigation = RecordingMarkerNavigation()
        navigation.show(recordingMarkerOpeningPositions(listOf(RecordingMarkStop(0L))), 1_000L)
        assertEquals(0L, navigation.selectedMs)
    }

    @Test fun entryCanSelectARealMarkAtZero() {
        val stops = listOf(
            RecordingMarkStop(0L, RecordingMark(RecordingMarkKind.CHAPTER, 0L)),
            RecordingMarkStop(30_000L, RecordingMark(RecordingMarkKind.CHAPTER, 30_000L)))
        val navigation = RecordingMarkerNavigation()
        navigation.show(recordingMarkerOpeningPositions(stops), 1_000L)
        assertEquals(0L, navigation.selectedMs)
    }

    @Test fun anEmptyListDoesNotOpenTheMarksRow() {
        val navigation = RecordingMarkerNavigation()
        navigation.show(emptyList(), 0L)
        assertEquals(null, navigation.selectedMs)
    }

    private fun selects(positionMs: Long, expectedMs: Long) {
        val navigation = RecordingMarkerNavigation()
        navigation.show(markers, positionMs)
        assertEquals(expectedMs, navigation.selectedMs)
    }
}
