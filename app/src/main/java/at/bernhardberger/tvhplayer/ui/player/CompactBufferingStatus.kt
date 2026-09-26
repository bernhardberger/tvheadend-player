package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import kotlinx.coroutines.delay

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
internal fun rememberBufferingVisible(targetKey: Any?, eligible: Boolean): State<Boolean> {
    // A new eligibility interval owns new state, so exit/target replacement cannot flash
    // the previous interval's result while its cancelled effect is being disposed.
    val visible = remember(targetKey, eligible) { mutableStateOf(false) }
    LaunchedEffect(targetKey, eligible) {
        if (eligible) {
            delay(1_000L)
            visible.value = true
        }
    }
    return visible
}

@Composable
internal fun CompactBufferingStatus(
    state: AppPlaybackState,
    playWhenReady: Boolean,
    target: AppPlaybackTarget?,
    expectedTarget: AppPlaybackTarget,
    generation: Any?,
    screenActive: Boolean,
    foregroundBlocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val eligible = bufferingStatusEligible(
        state, playWhenReady, target, expectedTarget,
        screenActive && lifecycleState.isAtLeast(Lifecycle.State.RESUMED), foregroundBlocked,
    )
    val visible by rememberBufferingVisible(expectedTarget to generation, eligible)
    // Unlike tuning, a stall has no minimum hold: it starts fading and leaves
    // semantics as soon as it is no longer eligible.
    CompactTuningStatus(
        visible = visible && eligible,
        label = stringResource(R.string.player_buffering),
        modifier = modifier.testTag("player-buffering-status"),
    )
}
