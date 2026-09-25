package at.bernhardberger.tvhplayer.core

import android.view.KeyEvent
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.hasChannelNumber

val Channel.visibleChannelNumber: Long?
    get() = number?.takeIf { hasChannelNumber }

object ChannelNavigation {
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

    fun maxChannelNumberDigits(
        orderedIds: List<ChannelId>,
        channelNumbers: Map<ChannelId, Long?>,
    ): Int = (orderedIds.mapNotNull { channelNumbers[it] }.maxOrNull() ?: orderedIds.size.toLong())
        .toString().length.coerceAtLeast(1)

    fun isCompleteEntry(entered: String, maxDigits: Int): Boolean = entered.length >= maxDigits

    fun appendDigit(current: String, digit: Int, maxDigits: Int): String {
        require(digit in 0..9)
        require(maxDigits >= 1)
        return if (current.length >= maxDigits) {
            digit.toString()
        } else {
            current + digit
        }
    }

    fun idForNumber(
        orderedIds: List<ChannelId>,
        channelNumbers: Map<ChannelId, Long?>,
        enteredNumber: String,
    ): ChannelId? {
        val number = enteredNumber.toLongOrNull() ?: return null
        if (number <= 0) return null
        orderedIds.firstOrNull { channelNumbers[it] == number }?.let { return it }

        return if (number <= orderedIds.size && orderedIds.none { channelNumbers[it] != null }) {
            orderedIds.getOrNull((number - 1).toInt())
        } else null
    }

    fun numberForId(
        orderedIds: List<ChannelId>,
        channelNumbers: Map<ChannelId, Long?>,
        channelId: ChannelId,
    ): Long? {
        val index = orderedIds.indexOf(channelId)
        if (index < 0) return null

        return channelNumbers[channelId]
            ?: if (orderedIds.none { channelNumbers[it] != null }) index + 1L else null
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
