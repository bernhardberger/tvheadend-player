package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppError
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.WaitingConnectionFailure
import at.bernhardberger.tvhplayer.ui.common.AppErrorText
import at.bernhardberger.tvhplayer.ui.player.rememberLastShown

/** The browse screens' connection failure and the viewer's retry of it. */
internal class BrowseConnectionFailure(val failure: ConnectionUiState?, val retry: () -> Unit)

/**
 * The failure the browse screens show for [connection], held through automatic attempts
 * ([WaitingConnectionFailure]) and shared by every browse screen so navigating keeps it. A new
 * [profileGeneration] starts over and, like the viewer's retry, ignores the old profile's failure
 * until the new profile's connection leaves it.
 */
@Composable
internal fun rememberBrowseConnectionFailure(
    connection: ConnectionUiState,
    profileGeneration: Long,
    onRetry: () -> Unit,
): BrowseConnectionFailure {
    val firstGeneration = remember { profileGeneration }
    var waiting by remember(profileGeneration) {
        mutableStateOf(WaitingConnectionFailure(retrying = profileGeneration != firstGeneration))
    }
    SideEffect { waiting = waiting.observe(connection) }
    return BrowseConnectionFailure(waiting.shown(connection)) {
        waiting = waiting.retry()
        onRetry()
    }
}

/**
 * The full-screen alert of a connection [error] outside the player: startup and the browse screens
 * without saved data. Title, then why and what helps as one paragraph, then the error's own buttons
 * in order. Shown while [error] is not null; its exit keeps what it showed. The focus parameters are
 * [TvRecoveryOverlay]'s. Inside a leaving tab, directional focus cannot enter it.
 */
@Composable
internal fun AppErrorAlert(
    error: AppError?,
    onAction: (AppErrorAction) -> Unit,
    modifier: Modifier = Modifier,
    actionFocus: FocusRequester? = null,
    leadingExit: FocusRequester = FocusRequester.Cancel,
    upExit: FocusRequester = FocusRequester.Cancel,
) {
    val shown = rememberLastShown(error)
    val owner = LocalTabOwner.current
    val context = LocalContext.current
    val text = remember(context) { AppErrorText(context) }
    TvRecoveryOverlay(
        visible = error != null,
        message = shown?.let { text.title(it.condition) }.orEmpty(),
        modifier = modifier
            .focusProperties { onEnter = { if (owner?.isCurrent == false) cancelFocusChange() } }
            .focusGroup(),
        detail = shown?.let { text.paragraph(it.condition) },
        icon = painterResource(R.drawable.ic_error_outlined),
        actions = shown?.actions.orEmpty().map { action -> TvRecoveryAction(text.label(action)) { onAction(action) } },
        actionFocus = actionFocus,
        leadingExit = leadingExit,
        upExit = upExit,
    )
}
