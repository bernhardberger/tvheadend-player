package at.bernhardberger.tvhplayer.appliance

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CompanionServiceStatus { OFF, ON, BLOCKED }

fun companionServiceStatus(enabled: Boolean, connected: Boolean): CompanionServiceStatus = when {
    !enabled -> CompanionServiceStatus.OFF
    connected -> CompanionServiceStatus.ON
    else -> CompanionServiceStatus.BLOCKED
}

/** Process-local bridge: capture is available only on the very key-filter path that will later route it. */
object CompanionServiceState {
    private val mutableConnected = MutableStateFlow(false)
    val connected = mutableConnected.asStateFlow()
    private val capture = ButtonCapture()
    val captureState = capture.state
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { capture.expire(SystemClock.elapsedRealtime()) }

    fun setConnected(connected: Boolean) {
        mutableConnected.value = connected
        if (!connected) cancelCapture()
    }

    fun startCapture(function: CompanionFunction): Boolean {
        if (!connected.value) return false
        capture.start(function, SystemClock.elapsedRealtime())
        scheduleTimeout()
        return true
    }

    fun cancelCapture() {
        capture.cancel()
        handler.removeCallbacks(timeout)
    }

    fun captureKey(context: Context, event: KeyEvent): Boolean {
        val store = FunctionStore(context)
        val before = captureState.value
        val consumed = capture.keyEvent(event.keyCode, event.action == KeyEvent.ACTION_DOWN,
            event.action == KeyEvent.ACTION_UP, event.repeatCount, SystemClock.elapsedRealtime(), store.functions)
        val after = captureState.value
        if (after !== before && after is CaptureState.Captured) {
            store.assign(after.function, ButtonBinding.Custom(after.keyCode))
        }
        scheduleTimeout()
        return consumed
    }

    private fun scheduleTimeout() {
        handler.removeCallbacks(timeout)
        capture.deadline?.let { handler.postDelayed(timeout, (it - SystemClock.elapsedRealtime()).coerceAtLeast(0)) }
    }
}
