package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingDecision
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingState
import at.bernhardberger.tvhplayer.core.liveInfoRecordingDecision
import at.bernhardberger.tvhplayer.core.programmeRecordingTarget
import at.bernhardberger.tvhplayer.ui.TvPanelDenseAlpha
import at.bernhardberger.tvhplayer.ui.TvScrimModalAlpha
import at.bernhardberger.tvhplayer.ui.TvSpacing24
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvSpacing32
import at.bernhardberger.tvhplayer.ui.TvSpacing56
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.components.ActionsTemplate
import at.bernhardberger.tvhplayer.ui.components.ProgrammeContentDetails

private val LiveInfoPanelMaxWidth = 760.dp
private val LiveInfoPanelMaxHeight = 420.dp

@Composable
internal fun LiveInfoRecordingValidityEffect(
    state: LiveInfoRecordingState,
    currentEvent: EpgEventEntry?,
    actionEligible: Boolean,
    confirmationVisible: Boolean,
    onInvalidated: () -> Unit,
) {
    val latestOnInvalidated by rememberUpdatedState(onInvalidated)
    LaunchedEffect(state, currentEvent, actionEligible, confirmationVisible) {
        if (
            confirmationVisible &&
            state is LiveInfoRecordingState.Confirming &&
            liveInfoRecordingDecision(state, currentEvent, actionEligible) ==
                LiveInfoRecordingDecision.Invalidate
        ) {
            latestOnInvalidated()
        }
    }
}

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
    modifier: Modifier = Modifier,
    piconContent: (@Composable () -> Unit)? = null,
    onRecordFocusRestored: () -> Unit = {},
) {
    val closeFocus = remember { FocusRequester() }
    val recordFocus = remember { FocusRequester() }
    val showingRecordingDialog = confirmationVisible &&
        recordingState !is LiveInfoRecordingState.Idle
    val paneTitle = if (showingRecordingDialog) {
        stringResource(R.string.record_confirm_pane_title)
    } else {
        stringResource(R.string.player_info_pane_title)
    }
    val recordAvailable = event != null && !recordingScheduled && canRecord
    var previousRecordAvailable by remember(event?.id) {
        mutableStateOf(recordAvailable)
    }

    LaunchedEffect(event?.id, showingRecordingDialog) {
        if (showingRecordingDialog || restoreRecordFocus) return@LaunchedEffect
        withFrameNanos { }
        closeFocus.requestFocus()
    }

    LaunchedEffect(
        restoreRecordFocus,
        event?.id,
        showingRecordingDialog,
        recordingScheduled,
        canRecord,
    ) {
        if (!restoreRecordFocus || showingRecordingDialog) return@LaunchedEffect
        withFrameNanos { }
        val target = if (recordAvailable) recordFocus else closeFocus
        if (target.requestFocus()) onRecordFocusRestored()
    }

    LaunchedEffect(recordAvailable, showingRecordingDialog) {
        val recordBecameUnavailable = previousRecordAvailable && !recordAvailable
        previousRecordAvailable = recordAvailable
        if (recordBecameUnavailable && !showingRecordingDialog) {
            withFrameNanos { }
            closeFocus.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = TvScrimModalAlpha))
            .padding(
                horizontal = TvSpacing56,
                vertical = TvSpacing32,
            )
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = LiveInfoPanelMaxWidth)
                .heightIn(max = LiveInfoPanelMaxHeight)
                .testTag("live-info-panel"),
            shape = MaterialTheme.shapes.large,
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface.copy(
                    alpha = TvPanelDenseAlpha
                ),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Box(
                modifier = Modifier
                    .testTag("live-info-overlay")
                    .semantics {
                        this.paneTitle = paneTitle
                        if (showingRecordingDialog) dialog()
                    }
                    .padding(TvSpacing24),
            ) {
                if (showingRecordingDialog) {
                    ProgrammeRecordingConfirmation(
                        state = recordingState,
                        onActivate = onRecordingActivate,
                        onDismiss = onRecordingDismiss,
                    )
                } else if (event == null) {
                    UnavailableProgrammeInfo(
                        channelIdentity = channelIdentity,
                        channelName = channelName,
                        closeFocus = closeFocus,
                        onClose = onClose,
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TvSpacing24),
                        verticalAlignment = Alignment.Top,
                    ) {
                        piconContent?.invoke()
                        ProgrammeContentDetails(
                            event = event,
                            subtitle = buildString {
                                append(channelIdentity)
                                append(" • ")
                                append(formatClock(event.start.epochSeconds))
                                append("–")
                                append(formatClock(event.stop.epochSeconds))
                            },
                            modifier = Modifier.weight(1f),
                            footer = {
                                if (recordingScheduled) {
                                    Text(
                                        text = stringResource(
                                            R.string.recording_already_scheduled
                                        ),
                                        color = TvRecordingColor,
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(
                                        TvSpacing24,
                                        Alignment.End,
                                    ),
                                ) {
                                    if (!recordingScheduled && canRecord) {
                                        Button(
                                            onClick = onRecord,
                                            modifier = Modifier
                                                .testTag("live-info-record")
                                                .focusRequester(recordFocus)
                                                .focusProperties {
                                                    left = FocusRequester.Cancel
                                                    right = closeFocus
                                                },
                                        ) {
                                            Text(stringResource(R.string.record))
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = onClose,
                                        modifier = Modifier
                                            .testTag("live-info-close")
                                            .focusRequester(closeFocus)
                                            .focusProperties {
                                                left = if (!recordingScheduled && canRecord) {
                                                    recordFocus
                                                } else {
                                                    FocusRequester.Cancel
                                                }
                                                right = FocusRequester.Cancel
                                            },
                                    ) {
                                        Text(stringResource(R.string.player_info_close))
                                    }
                                }
                            },
                        )
                    }
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
) {
    ActionsTemplate(
        title = stringResource(R.string.player_info_unavailable_title),
        subtitle = channelIdentity,
        body = {
            Text(
                text = stringResource(R.string.player_info_unavailable_message, channelName),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        actions = {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier
                    .testTag("live-info-close")
                    .focusRequester(closeFocus)
                    .focusProperties {
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
            ) {
                Text(stringResource(R.string.player_info_close))
            }
        },
    )
}

@Composable
internal fun ProgrammeRecordingConfirmation(
    state: LiveInfoRecordingState,
    onActivate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeFocus = remember { FocusRequester() }
    val activateFocus = remember { FocusRequester() }
    val target = when (state) {
        is LiveInfoRecordingState.Confirming -> state.target
        is LiveInfoRecordingState.Dispatching -> state.target
        is LiveInfoRecordingState.Succeeded -> state.target
        is LiveInfoRecordingState.Failed -> state.target
        LiveInfoRecordingState.Idle -> return
    }

    LaunchedEffect(state) {
        when (state) {
            is LiveInfoRecordingState.Failed -> activateFocus.requestFocus()
            is LiveInfoRecordingState.Confirming,
            is LiveInfoRecordingState.Dispatching,
            is LiveInfoRecordingState.Succeeded -> closeFocus.requestFocus()
            LiveInfoRecordingState.Idle -> Unit
        }
    }

    val title = when (state) {
        is LiveInfoRecordingState.Confirming ->
            stringResource(R.string.record_confirm_title, target.title)
        is LiveInfoRecordingState.Dispatching ->
            stringResource(R.string.recording_request_busy_title, target.title)
        is LiveInfoRecordingState.Succeeded ->
            stringResource(R.string.recording_request_success_title)
        is LiveInfoRecordingState.Failed ->
            stringResource(R.string.recording_request_failure_title)
        LiveInfoRecordingState.Idle -> return
    }
    val resultMessage = when (state) {
        is LiveInfoRecordingState.Confirming -> null
        is LiveInfoRecordingState.Dispatching ->
            stringResource(R.string.recording_request_busy_message)
        is LiveInfoRecordingState.Succeeded ->
            stringResource(R.string.recording_request_success_message, target.title)
        is LiveInfoRecordingState.Failed ->
            stringResource(
                R.string.recording_request_failure_message,
                target.title,
                dvrFailureLabel(state.result),
            )
        LiveInfoRecordingState.Idle -> null
    }

    ActionsTemplate(
        title = title,
        subtitle = if (state is LiveInfoRecordingState.Confirming) {
            stringResource(R.string.record_confirm_message)
        } else {
            null
        },
        modifier = modifier,
        body = {
            if (state is LiveInfoRecordingState.Dispatching) {
                CircularProgressIndicator()
            }
            resultMessage?.let {
                Text(
                    text = it,
                    modifier = Modifier
                        .testTag("programme-recording-result")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = if (state is LiveInfoRecordingState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        },
        actions = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .testTag(
                        if (state is LiveInfoRecordingState.Confirming) {
                            "programme-recording-cancel"
                        } else {
                            "programme-recording-close"
                        }
                    )
                    .focusRequester(closeFocus)
                    .focusProperties {
                        up = FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                        down = if (
                            state is LiveInfoRecordingState.Confirming ||
                            state is LiveInfoRecordingState.Failed
                        ) {
                            activateFocus
                        } else {
                            FocusRequester.Cancel
                        }
                    },
            ) {
                Text(
                    stringResource(
                        if (state is LiveInfoRecordingState.Confirming) {
                            R.string.record_confirm_cancel
                        } else {
                            R.string.close
                        }
                    )
                )
            }
            when (state) {
                is LiveInfoRecordingState.Confirming -> Button(
                    onClick = onActivate,
                    modifier = Modifier
                        .testTag("programme-recording-confirm")
                        .focusRequester(activateFocus)
                        .focusProperties {
                            up = closeFocus
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                ) {
                    Text(stringResource(R.string.record))
                }
                is LiveInfoRecordingState.Dispatching -> Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.testTag("programme-recording-confirm"),
                ) {
                    Text(stringResource(R.string.recording_request_busy_action))
                }
                is LiveInfoRecordingState.Failed -> Button(
                    onClick = onActivate,
                    modifier = Modifier
                        .testTag("programme-recording-retry")
                        .focusRequester(activateFocus)
                        .focusProperties {
                            up = closeFocus
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                ) {
                    Text(stringResource(R.string.retry))
                }
                LiveInfoRecordingState.Idle,
                is LiveInfoRecordingState.Succeeded -> Unit
            }
        },
    )
}

@Composable
private fun dvrFailureLabel(result: DvrMutationResult<*>): String = stringResource(
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
