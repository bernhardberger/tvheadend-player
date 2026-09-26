package at.bernhardberger.tvhplayer.core

import android.view.KeyEvent
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelNavigationTest {
    private val channels = listOf(cid(10), cid(20), cid(30))
    private val channelNumbers = mapOf(cid(10) to 1L, cid(20) to 2L, cid(30) to 4L)

    @Test
    fun next_returnsFollowingChannel() {
        assertEquals(cid(20), ChannelNavigation.adjacentId(channels, cid(10), 1))
    }

    @Test
    fun previous_returnsPrecedingChannel() {
        assertEquals(cid(20), ChannelNavigation.adjacentId(channels, cid(30), -1))
    }

    @Test
    fun next_wrapsAfterLastChannel() {
        assertEquals(cid(10), ChannelNavigation.adjacentId(channels, cid(30), 1))
    }

    @Test
    fun previous_wrapsBeforeFirstChannel() {
        assertEquals(cid(30), ChannelNavigation.adjacentId(channels, cid(10), -1))
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
        assertEquals(cid(10), ChannelNavigation.adjacentId(channels, cid(99), 1))
        assertEquals(cid(10), ChannelNavigation.adjacentId(channels, cid(99), -1))
    }

    @Test
    fun emptyChannelList_hasNoAdjacentChannel() {
        assertNull(ChannelNavigation.adjacentId(emptyList(), cid(10), 1))
    }

    @Test
    fun channelKeys_mapToNavigationDirection() {
        assertEquals(1, ChannelNavigation.directionForKeyCode(KeyEvent.KEYCODE_CHANNEL_UP))
        assertEquals(-1, ChannelNavigation.directionForKeyCode(KeyEvent.KEYCODE_CHANNEL_DOWN))
    }

    @Test
    fun hdmiCecForwardAndBackward_mapToLiveChannelDirection() {
        assertEquals(1, ChannelNavigation.directionForKeyCode(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(-1, ChannelNavigation.directionForKeyCode(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
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
    fun digits_appendUntilSuppliedLimitIncludingFourDigitChannels() {
        assertEquals("1", ChannelNavigation.appendDigit("", 1, 4))
        assertEquals("10", ChannelNavigation.appendDigit("1", 0, 4))
        assertEquals("100", ChannelNavigation.appendDigit("10", 0, 4))
        assertEquals("1000", ChannelNavigation.appendDigit("100", 0, 4))
    }

    @Test
    fun digitAfterSuppliedLimit_startsNewEntry() {
        assertEquals("4", ChannelNavigation.appendDigit("123", 4, 3))
        assertEquals("5", ChannelNavigation.appendDigit("1234", 5, 4))
        assertEquals("2", ChannelNavigation.appendDigit("1", 2, 1))
    }

    @Test
    fun enteredNumber_selectsTvheadendChannelNumber() {
        assertEquals(cid(10), ChannelNavigation.idForNumber(channels, channelNumbers, "1"))
        assertEquals(cid(20), ChannelNavigation.idForNumber(channels, channelNumbers, "2"))
        assertEquals(cid(30), ChannelNavigation.idForNumber(channels, channelNumbers, "004"))
    }

    @Test
    fun invalidEnteredNumber_hasNoChannel() {
        assertNull(ChannelNavigation.idForNumber(channels, channelNumbers, "0"))
        assertNull(ChannelNavigation.idForNumber(channels, channelNumbers, "3"))
        assertNull(ChannelNavigation.idForNumber(channels, channelNumbers, ""))
    }

    @Test
    fun channelNumber_usesTvheadendChannelNumber() {
        assertEquals(1L, ChannelNavigation.numberForId(channels, channelNumbers, cid(10)))
        assertEquals(4L, ChannelNavigation.numberForId(channels, channelNumbers, cid(30)))
        assertNull(ChannelNavigation.numberForId(channels, channelNumbers, cid(99)))
    }

    @Test
    fun missingTvheadendNumber_isNotAssignedAConflictingPosition() {
        assertNull(ChannelNavigation.numberForId(channels, channelNumbers - cid(20), cid(20)))
        assertNull(ChannelNavigation.idForNumber(channels, channelNumbers - cid(20), "3"))
    }

    @Test
    fun serversWithoutChannelNumbers_fallBackToOneBasedPositions() {
        assertEquals(cid(20), ChannelNavigation.idForNumber(channels, emptyMap(), "2"))
        assertEquals(3L, ChannelNavigation.numberForId(channels, emptyMap(), cid(30)))
    }

    @Test
    fun visibleNumberHidesZeroAndAbsentMajorNumbers() {
        for (number in listOf(null, 0L)) {
            assertNull(Channel.create(id = cid(10), number = number).visibleChannelNumber)
        }
        assertEquals(1000L, Channel.create(id = cid(10), number = 1000, numberMinor = 2).visibleChannelNumber)
    }

    @Test
    fun zeroNumberedChannelDoesNotMatchZeroOrGetAPositionInMixedCatalog() {
        val catalog = listOf(
            Channel.create(id = cid(10), number = 0),
            Channel.create(id = cid(20), number = 1000),
        )
        val ids = catalog.map { it.id }
        val numbers = catalog.associate { it.id to it.visibleChannelNumber }

        assertNull(ChannelNavigation.idForNumber(ids, numbers, "0"))
        assertNull(ChannelNavigation.idForNumber(ids, numbers, "1"))
        assertNull(ChannelNavigation.numberForId(ids, numbers, cid(10)))
        assertEquals(cid(20), ChannelNavigation.idForNumber(ids, numbers, "1000"))
        assertEquals(1000L, ChannelNavigation.numberForId(ids, numbers, cid(20)))
    }

    @Test
    fun zeroAndAbsentNumberedServersUseOneBasedPositions() {
        val catalog = listOf(
            Channel.create(id = cid(10), number = 0),
            Channel.create(id = cid(20), number = null),
            Channel.create(id = cid(30), number = 0),
        )
        val ids = catalog.map { it.id }
        val numbers = catalog.associate { it.id to it.visibleChannelNumber }

        assertNull(ChannelNavigation.idForNumber(ids, numbers, "0"))
        assertNull(ChannelNavigation.idForNumber(ids, numbers, "4"))
        ids.forEachIndexed { index, id ->
            assertEquals(id, ChannelNavigation.idForNumber(ids, numbers, (index + 1).toString()))
            assertEquals(index + 1L, ChannelNavigation.numberForId(ids, numbers, id))
        }
    }

    @Test
    fun digitLimitUsesLargestVisibleNumber() {
        for ((number, digits) in listOf(1L to 1, 9L to 1, 10L to 2, 999L to 3, 1000L to 4)) {
            assertEquals(digits, ChannelNavigation.maxChannelNumberDigits(channels, mapOf(cid(20) to number)))
        }
    }

    @Test
    fun threeDigitPrefixOfFourDigitNumberIsNotACompleteEntry() {
        val ids = listOf(cid(1), cid(2))
        val maxDigits = ChannelNavigation.maxChannelNumberDigits(ids, mapOf(cid(1) to 100L, cid(2) to 1000L))

        assertEquals(false, ChannelNavigation.isCompleteEntry("100", maxDigits))
        assertEquals(true, ChannelNavigation.isCompleteEntry("1000", maxDigits))
    }

    @Test
    fun digitLimitUsesPositionalCountWithAtLeastOneDigit() {
        val ids = (1..12).map(::cid)
        assertEquals(2, ChannelNavigation.maxChannelNumberDigits(ids, ids.associateWith { null }))
        assertEquals(1, ChannelNavigation.maxChannelNumberDigits(emptyList(), emptyMap()))
        assertEquals(1, ChannelNavigation.maxChannelNumberDigits(listOf(cid(1)), emptyMap()))
    }

    @Test
    fun entryBeforeTheListLoadsIsNotCompleteAfterOneDigitAndFollowsTheListOnceLoaded() {
        val unloaded = ChannelNavigation.entryMaxDigits(emptyList(), emptyMap())
        assertEquals(false, ChannelNavigation.isCompleteEntry("1", unloaded))
        assertEquals(ChannelNavigation.UNLOADED_CHANNEL_NUMBER_DIGITS, unloaded)

        assertEquals(1, ChannelNavigation.entryMaxDigits(channels, channelNumbers))
        assertEquals(4, ChannelNavigation.entryMaxDigits(channels, mapOf(cid(20) to 1000L)))
    }

    @Test
    fun entryReadinessWaitsForTheListAndTheSessionAndNamesUnknownNumbers() {
        val playable = setOf(cid(20))

        assertEquals(
            ChannelNumberEntryReadiness.NOT_READY,
            ChannelNavigation.entryReadiness(emptyList(), emptyMap(), "2") { true },
        )
        assertEquals(
            ChannelNumberEntryReadiness.READY,
            ChannelNavigation.entryReadiness(channels, channelNumbers, "2") { it in playable },
        )
        assertEquals(
            ChannelNumberEntryReadiness.NOT_READY,
            ChannelNavigation.entryReadiness(channels, channelNumbers, "4") { it in playable },
        )
        assertEquals(
            ChannelNumberEntryReadiness.UNKNOWN,
            ChannelNavigation.entryReadiness(channels, channelNumbers, "7") { true },
        )
    }

    @Test
    fun numbersOutsideReachableChannelsDoNotDisablePositionalFallbackOrRaiseDigitLimit() {
        val numbers = mapOf(cid(99) to 1000L)
        assertEquals(1, ChannelNavigation.maxChannelNumberDigits(channels, numbers))
        assertEquals(cid(20), ChannelNavigation.idForNumber(channels, numbers, "2"))
        assertEquals(3L, ChannelNavigation.numberForId(channels, numbers, cid(30)))
    }

    @Test
    fun unsignedChannelNumberRemainsVisibleAndReachableWithoutIntOverflow() {
        val channel = Channel.create(id = cid(10), number = 4_294_967_295L)
        val ids = listOf(channel.id)
        val numbers = mapOf(channel.id to channel.visibleChannelNumber)

        assertEquals(4_294_967_295L, channel.visibleChannelNumber)
        assertEquals(10, ChannelNavigation.maxChannelNumberDigits(ids, numbers))
        assertEquals(channel.id, ChannelNavigation.idForNumber(ids, numbers, "4294967295"))
        assertEquals(4_294_967_295L, ChannelNavigation.numberForId(ids, numbers, channel.id))
        assertNull(ChannelNavigation.idForNumber(ids, emptyMap(), "4294967297"))
    }

    private fun cid(value: Int) = ChannelId(Int.MAX_VALUE.toLong() + value)
}
