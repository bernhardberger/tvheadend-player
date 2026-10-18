package at.bernhardberger.tvhplayer.ui.player

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.AppVideoPresentation
import at.bernhardberger.tvhplayer.playback.BUSY_RING_QUIET_AFTER_SOUND_MILLIS
import at.bernhardberger.tvhplayer.playback.busyRingQuietInMillis
import kotlinx.coroutines.delay

internal enum class PlayerBusyStatus(val delayMs: Long) { TUNING(500L), BUFFERING(1_000L) }

internal fun tuningStatusEligible(
    state: AppPlaybackState,
    playWhenReady: Boolean,
    target: AppPlaybackTarget?,
    expectedTarget: AppPlaybackTarget,
    screenActive: Boolean,
    foregroundBlocked: Boolean,
    presented: Boolean,
): Boolean = !presented && playWhenReady && target == expectedTarget && screenActive &&
    !foregroundBlocked && (state == AppPlaybackState.Starting || state == AppPlaybackState.Buffering ||
        state == AppPlaybackState.Playing)

internal fun bufferingStatusEligible(
    state: AppPlaybackState,
    playWhenReady: Boolean,
    target: AppPlaybackTarget?,
    expectedTarget: AppPlaybackTarget,
    screenActive: Boolean,
    foregroundBlocked: Boolean,
): Boolean = state == AppPlaybackState.Buffering && playWhenReady &&
    target == expectedTarget && screenActive && !foregroundBlocked

@Composable
internal fun rememberBusyVisible(targetKey: Any?, eligible: Boolean, status: PlayerBusyStatus): State<Boolean> {
    // A new eligibility interval owns new state, so exit/target replacement cannot flash
    // the previous interval's result while its cancelled effect is being disposed.
    val visible = remember(targetKey, eligible, status) { mutableStateOf(false) }
    LaunchedEffect(targetKey, eligible, status) {
        if (eligible) {
            delay(status.delayMs)
            visible.value = true
        }
    }
    return visible
}

/**
 * Whether the ring of a covered live target goes quiet: its sound has played for
 * [BUSY_RING_QUIET_AFTER_SOUND_MILLIS] without the picture showing. Any change of the target's
 * presentation (cover lifted, sound stopped, another target) starts over with a spinning ring.
 */
@Composable
internal fun rememberBusyRingQuiet(presentation: AppVideoPresentation?): State<Boolean> {
    val quiet = remember(presentation) { mutableStateOf(false) }
    LaunchedEffect(presentation) {
        val wait = presentation?.busyRingQuietInMillis(SystemClock.elapsedRealtime()) ?: return@LaunchedEffect
        delay(wait)
        quiet.value = true
    }
    return quiet
}
