package at.bernhardberger.tvhplayer.core

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelNavigationTest {

    private val channels = listOf(10, 20, 30)
    private val channelNumbers = mapOf(10 to 1, 20 to 2, 30 to 4)

    @Test
    fun next_returnsFollowingChannel() {
        assertEquals(20, ChannelNavigation.adjacentId(channels, 10, 1))
    }

    @Test
    fun previous_returnsPrecedingChannel() {
        assertEquals(20, ChannelNavigation.adjacentId(channels, 30, -1))
    }

    @Test
    fun next_wrapsAfterLastChannel() {
        assertEquals(10, ChannelNavigation.adjacentId(channels, 30, 1))
    }

    @Test
    fun previous_wrapsBeforeFirstChannel() {
        assertEquals(30, ChannelNavigation.adjacentId(channels, 10, -1))
    }

    @Test
    fun pageTarget_advancesByOneViewportWithOverlap() {
        assertEquals(
            14,
            ChannelNavigation.pageTargetIndex(
                itemCount = 30,
                currentIndex = 10,
                visibleItemCount = 5,
                direction = 1,
            ),
        )
        assertEquals(
            6,
            ChannelNavigation.pageTargetIndex(
                itemCount = 30,
                currentIndex = 10,
                visibleItemCount = 5,
                direction = -1,
            ),
        )
    }

    @Test
    fun pageTarget_isBoundedAndDoesNotWrap() {
        assertEquals(29, ChannelNavigation.pageTargetIndex(30, 28, 5, 1))
        assertEquals(0, ChannelNavigation.pageTargetIndex(30, 1, 5, -1))
    }

    @Test
    fun pageTarget_movesAtLeastOneItem() {
        assertEquals(11, ChannelNavigation.pageTargetIndex(30, 10, 1, 1))
    }

    @Test
    fun pageTarget_requiresItemsAndAValidCurrentIndex() {
        assertNull(ChannelNavigation.pageTargetIndex(0, 0, 5, 1))
        assertNull(ChannelNavigation.pageTargetIndex(3, -1, 5, 1))
        assertNull(ChannelNavigation.pageTargetIndex(3, 3, 5, -1))
    }

    @Test
    fun staleCurrentChannel_fallsBackToFirstCurrentChannel() {
        assertEquals(10, ChannelNavigation.adjacentId(channels, 99, 1))
        assertEquals(10, ChannelNavigation.adjacentId(channels, 99, -1))
    }

    @Test
    fun emptyChannelList_hasNoAdjacentChannel() {
        assertNull(ChannelNavigation.adjacentId(emptyList(), 10, 1))
    }

    @Test
    fun channelKeys_mapToNavigationDirection() {
        assertEquals(1, ChannelNavigation.directionForKeyCode(KeyEvent.KEYCODE_CHANNEL_UP))
        assertEquals(-1, ChannelNavigation.directionForKeyCode(KeyEvent.KEYCODE_CHANNEL_DOWN))
    }

    @Test
    fun channelKeys_mapToConventionalPageDirection() {
        assertEquals(-1, ChannelNavigation.pageDirectionForKeyCode(KeyEvent.KEYCODE_CHANNEL_UP))
        assertEquals(1, ChannelNavigation.pageDirectionForKeyCode(KeyEvent.KEYCODE_CHANNEL_DOWN))
        assertNull(ChannelNavigation.pageDirectionForKeyCode(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test
    fun unrelatedKey_hasNoNavigationDirection() {
        assertNull(ChannelNavigation.directionForKeyCode(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test
    fun numberKeys_mapToDigits() {
        assertEquals(0, ChannelNavigation.digitForKeyCode(KeyEvent.KEYCODE_0))
        assertEquals(5, ChannelNavigation.digitForKeyCode(KeyEvent.KEYCODE_5))
        assertEquals(9, ChannelNavigation.digitForKeyCode(KeyEvent.KEYCODE_9))
        assertEquals(0, ChannelNavigation.digitForKeyCode(KeyEvent.KEYCODE_NUMPAD_0))
        assertEquals(5, ChannelNavigation.digitForKeyCode(KeyEvent.KEYCODE_NUMPAD_5))
        assertEquals(9, ChannelNavigation.digitForKeyCode(KeyEvent.KEYCODE_NUMPAD_9))
    }

    @Test
    fun unrelatedKey_hasNoDigit() {
        assertNull(ChannelNavigation.digitForKeyCode(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test
    fun digits_appendUntilThreeDigitLimit() {
        assertEquals("1", ChannelNavigation.appendDigit("", 1))
        assertEquals("12", ChannelNavigation.appendDigit("1", 2))
        assertEquals("123", ChannelNavigation.appendDigit("12", 3))
    }

    @Test
    fun digitAfterThreeDigits_startsNewEntry() {
        assertEquals("4", ChannelNavigation.appendDigit("123", 4))
    }

    @Test
    fun enteredNumber_selectsTvheadendChannelNumber() {
        assertEquals(10, ChannelNavigation.idForNumber(channels, channelNumbers, "1"))
        assertEquals(20, ChannelNavigation.idForNumber(channels, channelNumbers, "2"))
        assertEquals(30, ChannelNavigation.idForNumber(channels, channelNumbers, "004"))
    }

    @Test
    fun invalidEnteredNumber_hasNoChannel() {
        assertNull(ChannelNavigation.idForNumber(channels, channelNumbers, "0"))
        assertNull(ChannelNavigation.idForNumber(channels, channelNumbers, "3"))
        assertNull(ChannelNavigation.idForNumber(channels, channelNumbers, ""))
    }

    @Test
    fun channelNumber_usesTvheadendChannelNumber() {
        assertEquals(1, ChannelNavigation.numberForId(channels, channelNumbers, 10))
        assertEquals(4, ChannelNavigation.numberForId(channels, channelNumbers, 30))
        assertNull(ChannelNavigation.numberForId(channels, channelNumbers, 99))
    }

    @Test
    fun missingTvheadendNumber_isNotAssignedAConflictingPosition() {
        assertNull(ChannelNavigation.numberForId(channels, channelNumbers - 20, 20))
        assertNull(ChannelNavigation.idForNumber(channels, channelNumbers - 20, "3"))
    }

    @Test
    fun serversWithoutChannelNumbers_fallBackToOneBasedPositions() {
        assertEquals(20, ChannelNavigation.idForNumber(channels, emptyMap(), "2"))
        assertEquals(3, ChannelNavigation.numberForId(channels, emptyMap(), 30))
    }
}
