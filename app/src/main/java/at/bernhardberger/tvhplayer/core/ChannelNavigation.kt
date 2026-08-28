package at.bernhardberger.tvhplayer.core

import android.view.KeyEvent
import at.bernhardberger.tvheadend.sdk.core.ChannelId

object ChannelNavigation {
    private const val MAX_CHANNEL_NUMBER_DIGITS = 3

    fun directionForKeyCode(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_CHANNEL_UP,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        -> 1
        KeyEvent.KEYCODE_CHANNEL_DOWN,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        -> -1
        else -> null
    }

    fun pageDirectionForKeyCode(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_CHANNEL_UP -> -1
        KeyEvent.KEYCODE_CHANNEL_DOWN -> 1
        else -> null
    }

    fun digitForKeyCode(keyCode: Int): Int? = when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
            keyCode - KeyEvent.KEYCODE_NUMPAD_0
        else -> null
    }

    fun appendDigit(current: String, digit: Int): String {
        require(digit in 0..9)
        return if (current.length >= MAX_CHANNEL_NUMBER_DIGITS) {
            digit.toString()
        } else {
            current + digit
        }
    }

    fun idForNumber(
        orderedIds: List<ChannelId>,
        channelNumbers: Map<ChannelId, Int?>,
        enteredNumber: String,
    ): ChannelId? {
        val number = enteredNumber.toIntOrNull() ?: return null
        orderedIds.firstOrNull { channelNumbers[it] == number }?.let { return it }

        return if (channelNumbers.values.none { it != null }) {
            orderedIds.getOrNull(number - 1)
        } else null
    }

    fun numberForId(
        orderedIds: List<ChannelId>,
        channelNumbers: Map<ChannelId, Int?>,
        channelId: ChannelId,
    ): Int? {
        val index = orderedIds.indexOf(channelId)
        if (index < 0) return null

        return channelNumbers[channelId]
            ?: if (channelNumbers.values.none { it != null }) index + 1 else null
    }

    fun adjacentId(
        orderedIds: List<ChannelId>,
        currentId: ChannelId,
        direction: Int,
    ): ChannelId? {
        if (orderedIds.isEmpty()) return null

        val currentIndex = orderedIds.indexOf(currentId)
        if (currentIndex < 0) return orderedIds.first()

        val offset = if (direction < 0) -1 else 1
        return orderedIds[Math.floorMod(currentIndex + offset, orderedIds.size)]
    }

    fun pageTargetIndex(
        itemCount: Int,
        currentIndex: Int,
        visibleItemCount: Int,
        direction: Int,
    ): Int? {
        if (itemCount <= 0 || currentIndex !in 0 until itemCount) return null

        val pageSize = (visibleItemCount - 1).coerceAtLeast(1)
        val offset = if (direction < 0) -pageSize else pageSize
        return (currentIndex + offset).coerceIn(0, itemCount - 1)
    }
}
