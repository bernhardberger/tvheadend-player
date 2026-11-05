package at.bernhardberger.tvhplayer.appliance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface CaptureState {
    data object Idle : CaptureState
    data class Waiting(val function: CompanionFunction, val deadline: Long) : CaptureState
    data class Refused(val function: CompanionFunction, val refusedKeyName: String, val deadline: Long) : CaptureState
    data class TimedOut(val function: CompanionFunction) : CaptureState
    data class Captured(val function: CompanionFunction, val keyCode: Int, val moved: MovedButton?) : CaptureState
    data object Cancelled : CaptureState
}

/** Plain policy. The service/activity adapter supplies monotonic time and persists the captured assignment. */
class ButtonCapture {
    private val mutableState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state = mutableState.asStateFlow()
    private val heldKeys = mutableSetOf<Int>()
    val deadline: Long? get() = when (val current = state.value) {
        is CaptureState.Waiting -> current.deadline
        is CaptureState.Refused -> current.deadline
        else -> null
    }

    fun start(function: CompanionFunction, now: Long) {
        require(function != CompanionFunction.STARTUP)
        heldKeys.clear()
        mutableState.value = CaptureState.Waiting(function, now + TIMEOUT_MILLIS)
    }

    fun cancel() { if (deadline != null) mutableState.value = CaptureState.Cancelled }

    fun dismiss() {
        if (state.value !is CaptureState.Waiting) mutableState.value = CaptureState.Idle
    }

    fun expire(now: Long) {
        val current = state.value
        if (now < (deadline ?: return)) return
        val function = when (current) {
            is CaptureState.Waiting -> current.function
            is CaptureState.Refused -> current.function
            else -> return
        }
        mutableState.value = CaptureState.TimedOut(function)
    }

    fun keyEvent(keyCode: Int, isDown: Boolean, isUp: Boolean, repeatCount: Int,
                 now: Long, functions: CompanionFunctions): Boolean {
        expire(now)
        // Retain the whole cycle even after capture, cancel or timeout changes the screen.
        if (deadline == null) {
            if (keyCode !in heldKeys) return false
            // A fresh DOWN starts a new press, even if the previous UP never reached this filter.
            if (isDown && repeatCount == 0) {
                heldKeys.remove(keyCode)
                return false
            }
            if (isUp) heldKeys.remove(keyCode)
            return true
        }
        if (isUp) heldKeys.remove(keyCode)
        if (isDown) heldKeys.add(keyCode)
        if (!isDown || repeatCount != 0) return true
        val function = when (val current = state.value) {
            is CaptureState.Waiting -> current.function
            is CaptureState.Refused -> current.function
            else -> return true
        }
        if (keyCode == 4) {
            cancel()
            return true
        }
        val refused = when (keyCode) {
            0 -> buttonName(keyCode)
            19, 20, 21, 22 -> "Arrows"
            23, 66, 160 -> "OK"
            3 -> "HOME"
            24, 25, 164 -> "Volume"
            26, 223, 224 -> "Power"
            else -> null
        }
        mutableState.value = if (refused != null) CaptureState.Refused(function, refused, now + TIMEOUT_MILLIS)
        else CaptureState.Captured(function, keyCode,
            functions.assign(function, ButtonBinding.Custom(keyCode)).moved.singleOrNull())
        return true
    }

    companion object { const val TIMEOUT_MILLIS = 10_000L }
}
