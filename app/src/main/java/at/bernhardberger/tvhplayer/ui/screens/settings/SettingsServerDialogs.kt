package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.ui.components.CentredDialog
import org.koin.compose.koinInject

internal const val SWITCH_KEEP = "server-switch-keep"
internal const val SWITCH_CONFIRM = "server-switch-confirm"
internal const val REMOVE_KEEP = "server-remove-keep"
internal const val REMOVE_CONFIRM = "server-remove-confirm"
internal const val SWITCH_CANCEL = "server-switch-cancel"
internal const val SWITCH_HOME = "server-switch-home"
internal const val SWITCH_BACK = "server-switch-back"
internal const val SWITCH_TRY_AGAIN = "server-switch-try-again"
internal const val SWITCH_EDIT = "server-switch-edit"

/** What switching servers would stop: nothing, live TV or a recording. */
internal enum class PlaybackInterruption { NONE, LIVE, RECORDING }

/** Only a target that is starting, playing, buffering or recovering is stopped by a switch. */
internal fun playbackInterruption(target: AppPlaybackTarget?, state: AppPlaybackState): PlaybackInterruption = when {
    target == null || state == AppPlaybackState.Idle || state == AppPlaybackState.Finished ||
        state is AppPlaybackState.Failed -> PlaybackInterruption.NONE
    target is AppPlaybackTarget.Recording -> PlaybackInterruption.RECORDING
    else -> PlaybackInterruption.LIVE
}

/** Reads the process playback runtime when asked, so the question reflects the moment of the choice. */
@Composable
internal fun rememberPlaybackInterruption(runtime: AppPlaybackRuntime = koinInject()): () -> PlaybackInterruption =
    remember(runtime) { { playbackInterruption(runtime.activeTarget.value, runtime.state.value) } }

/** The same user stop as browsing with warm playback off: nothing retunes or resumes it. */
@Composable
internal fun rememberPlaybackStop(runtime: AppPlaybackRuntime = koinInject()): suspend () -> Unit =
    remember(runtime) { { runtime.stop() } }

/** Asked only while something plays (frame 04); "Keep watching" comes first and is focused. */
@Composable
internal fun SwitchServerDialog(
    name: String,
    username: String?,
    interruption: PlaybackInterruption,
    onSwitch: () -> Unit,
    onKeep: () -> Unit,
) {
    val recording = interruption == PlaybackInterruption.RECORDING
    CentredDialog(
        title = stringResource(R.string.server_switch_title, name),
        message = when {
            username == null -> stringResource(
                if (recording) R.string.server_switch_recording_anonymous else R.string.server_switch_live_anonymous,
            )
            else -> stringResource(
                if (recording) R.string.server_switch_recording else R.string.server_switch_live, username,
            )
        },
        firstLabel = stringResource(R.string.server_keep_watching),
        secondLabel = stringResource(R.string.server_switch),
        onFirst = onKeep,
        onSecond = onSwitch,
        onDismiss = onKeep,
        firstTag = SWITCH_KEEP,
        secondTag = SWITCH_CONFIRM,
    )
}

/** Frame 11; "Keep" comes first and is focused. Removing the server in use also stops what plays. */
@Composable
internal fun RemoveServerDialog(
    name: String,
    active: Boolean,
    onRemove: () -> Unit,
    onKeep: () -> Unit,
) {
    CentredDialog(
        title = stringResource(R.string.server_remove_title, name),
        message = stringResource(if (active) R.string.server_remove_body_active else R.string.server_remove_body, name),
        firstLabel = stringResource(R.string.keep),
        secondLabel = stringResource(R.string.server_remove),
        onFirst = onKeep,
        onSecond = onRemove,
        onDismiss = onKeep,
        firstTag = REMOVE_KEEP,
        secondTag = REMOVE_CONFIRM,
    )
}
