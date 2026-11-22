package at.bernhardberger.tvhplayer.touch.player

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.OrientationEventListener
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlin.math.abs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Only the measured phone layout offers rotation, and never in a system window (split-screen or PiP). */
internal fun PlayerFaceState.withFullScreenOffer(layout: PlayerLayout, inWindow: Boolean): PlayerFaceState {
    val allowed = layout == PlayerLayout.Phone && !inWindow && status.hasTimeline
    if ((PlayerControl.FullScreen in offered) == allowed) return this
    val controls = if (allowed) offered + PlayerControl.FullScreen else offered - PlayerControl.FullScreen
    return when (this) {
        is LivePlayerState -> copy(offered = controls)
        is RecordingPlayerState -> copy(offered = controls)
    }
}

internal enum class FullScreenRequest { Inactive, AwaitingSideways, AwaitingUpright }

internal enum class PhoneOrientation { Unknown, Upright, Sideways }

private const val HALF_TURN = 180
private const val CARDINAL_TOLERANCE = 30

internal fun phoneOrientation(degrees: Int): PhoneOrientation {
    if (degrees !in 0 until 2 * HALF_TURN) return PhoneOrientation.Unknown
    val tilt = degrees % HALF_TURN
    return when {
        minOf(tilt, HALF_TURN - tilt) <= CARDINAL_TOLERANCE -> PhoneOrientation.Upright
        abs(tilt - HALF_TURN / 2) <= CARDINAL_TOLERANCE -> PhoneOrientation.Sideways
        else -> PhoneOrientation.Unknown
    }
}

internal fun nextFullScreenRequest(
    request: FullScreenRequest,
    orientation: PhoneOrientation,
    inWindow: Boolean,
    exitRequested: Boolean = false,
): FullScreenRequest = when {
    inWindow || exitRequested -> FullScreenRequest.Inactive
    request == FullScreenRequest.AwaitingSideways && orientation == PhoneOrientation.Sideways ->
        FullScreenRequest.AwaitingUpright
    request == FullScreenRequest.AwaitingUpright && orientation == PhoneOrientation.Upright ->
        FullScreenRequest.Inactive
    else -> request
}

/** An orientation request is independent of the route's playback and the window's video surface. */
internal class PlayerFullScreen(private val activity: ComponentActivity) : DefaultLifecycleObserver {
    var request = FullScreenRequest.Inactive
        private set
    private var listener: OrientationEventListener? = null

    fun restore(state: Bundle?, inWindow: StateFlow<Boolean>) {
        activity.lifecycle.addObserver(this)
        activity.addOnMultiWindowModeChangedListener { if (it.isInMultiWindowMode) release() }
        val index = state?.getInt(SAVED_REQUEST, -1) ?: -1
        val restored = FullScreenRequest.entries.getOrNull(index) ?: FullScreenRequest.Inactive
        update(nextFullScreenRequest(
            restored, PhoneOrientation.Unknown, activity.isInPictureInPictureMode || activity.isInMultiWindowMode,
        ))
        activity.lifecycleScope.launch {
            inWindow.collect { update(nextFullScreenRequest(request, PhoneOrientation.Unknown, it)) }
        }
    }

    fun save(state: Bundle) {
        state.putInt(SAVED_REQUEST, request.ordinal)
    }

    fun enter(inWindow: Boolean) {
        if (activity.isFinishing) return
        if (inWindow || activity.isInPictureInPictureMode || activity.isInMultiWindowMode) return
        if (request == FullScreenRequest.Inactive) update(FullScreenRequest.AwaitingSideways)
    }

    fun release() = update(nextFullScreenRequest(request, PhoneOrientation.Unknown, false, exitRequested = true))

    fun finishPlayer() {
        release()
        activity.finish()
    }

    override fun onStart(owner: LifecycleOwner) = startListening()

    override fun onStop(owner: LifecycleOwner) = stopListening()

    private fun update(next: FullScreenRequest) {
        val wasActive = request != FullScreenRequest.Inactive
        request = next
        val active = request != FullScreenRequest.Inactive
        if (wasActive == active) return
        activity.requestedOrientation = if (active) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (active && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startListening()
        if (!active) stopListening()
    }

    private fun startListening() {
        if (request == FullScreenRequest.Inactive || listener != null) return
        listener = object : OrientationEventListener(activity) {
            override fun onOrientationChanged(orientation: Int) {
                update(nextFullScreenRequest(request, phoneOrientation(orientation), activity.isInPictureInPictureMode))
            }
        }.also { if (it.canDetectOrientation()) it.enable() }
    }

    private fun stopListening() {
        listener?.disable()
        listener = null
    }

    private companion object {
        const val SAVED_REQUEST = "full_screen_request"
    }
}
