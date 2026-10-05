package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingState

@Composable
internal fun LiveProgrammeInfoOverlay(
    event: EpgEventEntry?,
    channelIdentity: String,
    channelName: String,
    recordingScheduled: Boolean,
    canRecord: Boolean,
    recordingState: LiveInfoRecordingState,
    confirmationVisible: Boolean,
    restoreRecordFocus: Boolean,
    onRecord: () -> Unit,
    onRecordingActivate: () -> Unit,
    onRecordingDismiss: () -> Unit,
    onClose: () -> Unit,
    /** Trial: the program details, given the Record and first-action focus targets. */
    details: @Composable (recordFocus: FocusRequester, firstFocus: FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
    onRecordFocusRestored: () -> Unit = {},
    onWatch: (() -> Unit)? = null,
    onUp: () -> Unit = onClose,
) {
    val closeFocus = remember { FocusRequester() }
    val recordFocus = remember { FocusRequester() }
    val paneTitle = stringResource(R.string.player_info_pane_title)
    val recordAvailable = event != null && !recordingScheduled && canRecord
    // ProgramDetails owns its tab/content focus. Only the unavailable view has a Close action.
    val pageActive = LocalPlayerPageActive.current
    LaunchedEffect(event?.id, pageActive) {
        if (!pageActive || event != null || restoreRecordFocus) return@LaunchedEffect
        withFrameNanos { }
        closeFocus.requestFocus()
    }

    LaunchedEffect(
        restoreRecordFocus,
        event?.id,
        recordingScheduled,
        canRecord,
        pageActive,
    ) {
        if (!pageActive || !restoreRecordFocus) return@LaunchedEffect
        withFrameNanos { }
        val target = if (recordAvailable) recordFocus else closeFocus
        if (target.requestFocus()) onRecordFocusRestored()
    }

    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
        // Trial: a full-screen sheet replaces the side panel.
        ProgrammeInfoSheetFrame(
            paneTitle = paneTitle,
            panelTag = "live-info-panel",
            bottomPadding = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .testTag("live-info-overlay")
                    .semantics {
                        this.paneTitle = paneTitle
                    },
            ) {
                if (event == null) {
                    UnavailableProgrammeInfo(
                        channelIdentity = channelIdentity,
                        channelName = channelName,
                        closeFocus = closeFocus,
                        onClose = onClose,
                        onWatch = onWatch,
                        onUp = onUp,
                    )
                } else {
                    details(recordFocus, closeFocus)
                }
            }
        }
    }
}

@Composable
private fun UnavailableProgrammeInfo(
    channelIdentity: String,
    channelName: String,
    closeFocus: FocusRequester,
    onClose: () -> Unit,
    onWatch: (() -> Unit)?,
    onUp: () -> Unit,
) {
    val readingFocus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().pageMotion(150..480, dy = 160.dp)) {
        Spacer(Modifier.height(24.dp))
        PlayerInfoReadingContent(
            title = stringResource(R.string.player_info_unavailable_title),
            subtitle = channelIdentity,
            body = stringResource(R.string.player_info_unavailable_message, channelName),
            readingFocus = readingFocus,
            modifier = Modifier.padding(bottom = 40.dp).onKeyEvent {
                if (it.key != Key.DirectionUp) false
                else {
                    if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) onUp()
                    true
                }
            },
            footer = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = onWatch ?: onClose,
                        modifier = Modifier
                            .testTag(if (onWatch != null) "details-watch" else "live-info-close")
                            .focusRequester(closeFocus)
                            .onPreviewKeyEvent {
                                if (it.key != Key.DirectionUp) false
                                else {
                                    if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) readingFocus.requestFocus()
                                    true
                                }
                            }
                            .focusProperties {
                                up = readingFocus
                                down = FocusRequester.Cancel
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            },
                    ) {
                        Text(stringResource(if (onWatch != null) R.string.details_watch else R.string.player_info_close))
                    }
                }
            },
        )
    }
}

@Composable
internal fun dvrFailureLabel(result: DvrMutationResult<*>): String = stringResource(
    when (result) {
        DvrMutationResult.AccessDenied -> R.string.recording_action_permission
        DvrMutationResult.ConnectionLimit -> R.string.recording_action_conn_limit
        DvrMutationResult.ServerRejected,
        DvrMutationResult.NotSupported -> R.string.recording_action_rejected
        DvrMutationResult.NotReady,
        DvrMutationResult.ObservationExpired,
        DvrMutationResult.Timeout,
        DvrMutationResult.TransportUnavailable -> R.string.recording_action_connection
        is DvrMutationResult.Confirmed,
        is DvrMutationResult.AcceptedButUnconfirmed -> R.string.recording_action_rejected
    }
)
