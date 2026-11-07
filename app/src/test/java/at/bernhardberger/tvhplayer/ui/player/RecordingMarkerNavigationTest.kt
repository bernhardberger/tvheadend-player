package at.bernhardberger.tvhplayer.ui.player

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
