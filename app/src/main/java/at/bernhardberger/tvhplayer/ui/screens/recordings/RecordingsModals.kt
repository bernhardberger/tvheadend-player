package at.bernhardberger.tvhplayer.ui.screens.recordings

import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.ui.TvScrimModalAlpha
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationFeedback
import at.bernhardberger.tvhplayer.ui.screens.label

internal enum class PendingRecordingAction {
    STOP,
    CANCEL,
    DELETE,
}

internal enum class RecordingDetailsAction {
    RESUME,
    BEGINNING,
    PLAY,
    STOP,
    CANCEL,
    DELETE,
    CLOSE,
}

/**
 * Chooses how an active entry ends: a running recording is stopped (kept as recorded so far),
 * a scheduled one is cancelled. Other states offer neither.
 */
internal fun recordingEndAction(state: DvrEntryState?): RecordingDetailsAction? = when (state) {
    DvrEntryState.RECORDING -> RecordingDetailsAction.STOP
    DvrEntryState.SCHEDULED -> RecordingDetailsAction.CANCEL
    else -> null
}

@Composable
internal fun RecordingDetailsPanel(
    contentPadding: PaddingValues,
    entry: DvrEntry,
    actionResult: DvrMutationFeedback?,
    canModifyRecordings: Boolean,
    playbackEligible: Boolean,
    initialAction: RecordingDetailsAction?,
    backEnabled: Boolean,
    onPlay: (RecordingPlaybackStart) -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val primaryFocus = remember { FocusRequester() }
    val secondaryFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val deleteFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    var focusedAction by remember(entry.id) { mutableStateOf<RecordingDetailsAction?>(null) }
    // Stop and Cancel are mutually exclusive and share one button slot and focus requester.
    val endAction = recordingEndAction(entry.state).takeIf { canModifyRecordings }
    val canEnd = endAction != null
    val canDelete = canModifyRecordings &&
        entry.state in setOf(
            DvrEntryState.COMPLETED,
            DvrEntryState.MISSED,
            DvrEntryState.INVALID,
            DvrEntryState.RECORDING_ERROR,
            DvrEntryState.COMPLETED_ERROR,
            DvrEntryState.FILE_MISSING,
        )
    val canPlay = playbackEligible && entry.state in setOf(
        DvrEntryState.COMPLETED,
        DvrEntryState.RECORDING,
    )
    val resumeSeconds = entry.playPosition?.inWholeSeconds?.takeIf {
        canPlay && entry.state == DvrEntryState.COMPLETED && it > 0L
    }
    val primaryAction = when {
        resumeSeconds != null -> RecordingDetailsAction.RESUME
        canPlay -> RecordingDetailsAction.PLAY
        endAction != null -> endAction
        canDelete -> RecordingDetailsAction.DELETE
        else -> RecordingDetailsAction.CLOSE
    }
    val availableActions = buildSet {
        if (resumeSeconds != null) {
            add(RecordingDetailsAction.RESUME)
            add(RecordingDetailsAction.BEGINNING)
        } else if (canPlay) {
            add(RecordingDetailsAction.PLAY)
        }
        endAction?.let(::add)
        if (canDelete) add(RecordingDetailsAction.DELETE)
        add(RecordingDetailsAction.CLOSE)
    }
    fun requester(action: RecordingDetailsAction): FocusRequester = when (action) {
        RecordingDetailsAction.RESUME,
        RecordingDetailsAction.PLAY -> primaryFocus
        RecordingDetailsAction.BEGINNING -> secondaryFocus
        RecordingDetailsAction.STOP,
        RecordingDetailsAction.CANCEL -> cancelFocus
        RecordingDetailsAction.DELETE -> deleteFocus
        RecordingDetailsAction.CLOSE -> closeFocus
    }
    LaunchedEffect(entry.id, initialAction) {
        withFrameNanos { }
        requester(initialAction?.takeIf { it in availableActions } ?: primaryAction).requestFocus()
    }
    LaunchedEffect(availableActions, focusedAction) {
        val focused = focusedAction ?: return@LaunchedEffect
        if (focused !in availableActions) {
            withFrameNanos { }
            requester(primaryAction).requestFocus()
        }
    }
    RecordingDetailsSurface(
        contentPadding = contentPadding,
        backEnabled = backEnabled,
        onBack = onClose,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recording-details-metadata"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                entry.title.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                modifier = Modifier.semantics { heading() },
            )
            entry.subtitle?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                buildString {
                    append(entry.start?.epochSeconds.recordingDateTime())
                    entry.stop?.epochSeconds?.let { append('–').append(formatHm(it)) }
                    entry.channelName?.let { append(" • ").append(it) }
                    append(" • ").append(dvrStateLabel(entry.state))
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("recording-details-metadata-anchor"),
            )
            val failureReason = entry.subscriptionError?.name
            when {
                actionResult == null && !failureReason.isNullOrBlank() -> Text(
                    failureReason,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                actionResult != null -> Text(
                    actionResult.label(),
                    color = if (actionResult.isFailure) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (
                failureReason.isNullOrBlank() &&
                actionResult == null
            ) {
                val synopsis = entry.summary?.takeIf(String::isNotBlank)
                    ?: entry.description?.takeIf(String::isNotBlank)
                synopsis?.let {
                    Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recording-details-playback-actions"),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (resumeSeconds != null) {
                val accessibleResumeLabel = stringResource(
                    R.string.recording_resume_from,
                    recordingDurationForAccessibility(resumeSeconds),
                )
                Button(
                    onClick = { onPlay(RecordingPlaybackStart.RESUME) },
                    modifier = Modifier
                        .focusRequester(primaryFocus)
                        .onFocusChanged {
                            if (it.isFocused) focusedAction = RecordingDetailsAction.RESUME
                        }
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = secondaryFocus
                            up = FocusRequester.Cancel
                            down = closeFocus
                        }
                        .semantics { contentDescription = accessibleResumeLabel }
                        .testTag("recording-details-resume"),
                ) {
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            R.string.recording_resume_from,
                            formatPlaybackDuration(
                                resumeSeconds.coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
                            ),
                        )
                    )
                }
                Button(
                    onClick = { onPlay(RecordingPlaybackStart.START_OVER) },
                    modifier = Modifier
                        .focusRequester(secondaryFocus)
                        .onFocusChanged {
                            if (it.isFocused) focusedAction = RecordingDetailsAction.BEGINNING
                        }
                        .focusProperties {
                            left = primaryFocus
                            right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = when {
                                canEnd -> cancelFocus
                                canDelete -> deleteFocus
                                else -> closeFocus
                            }
                        }
                        .testTag("recording-details-beginning"),
                ) {
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recording_play_from_beginning))
                }
            } else if (canPlay) {
                Button(
                    onClick = { onPlay(RecordingPlaybackStart.START_OVER) },
                    modifier = Modifier
                        .focusRequester(primaryFocus)
                        .onFocusChanged {
                            if (it.isFocused) focusedAction = RecordingDetailsAction.PLAY
                        }
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = closeFocus
                        }
                        .testTag("recording-details-play"),
                ) {
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.play))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier
                    .focusRequester(closeFocus)
                    .onFocusChanged {
                        if (it.isFocused) focusedAction = RecordingDetailsAction.CLOSE
                    }
                    .focusProperties {
                        left = FocusRequester.Cancel
                        right = when {
                            canEnd -> cancelFocus
                            canDelete -> deleteFocus
                            else -> FocusRequester.Cancel
                        }
                        up = if (canPlay) primaryFocus else FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    }
                    .testTag("recording-details-close"),
            ) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.close))
            }
            if (endAction != null) {
                val stops = endAction == RecordingDetailsAction.STOP
                Button(
                    onClick = if (stops) onStop else onCancel,
                    modifier = Modifier
                        .focusRequester(cancelFocus)
                        .onFocusChanged {
                            if (it.isFocused) focusedAction = endAction
                        }
                        .focusProperties {
                            left = closeFocus
                            right = FocusRequester.Cancel
                            up = if (resumeSeconds != null) secondaryFocus
                                else if (canPlay) primaryFocus
                                else FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        }
                        .testTag(if (stops) "recording-details-stop" else "recording-details-cancel"),
                ) {
                    Icon(painterResource(R.drawable.ic_stop), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (stops) R.string.stop_recording else R.string.cancel_recording))
                }
            }
            if (canDelete) {
                Button(
                    onClick = onDelete,
                    modifier = Modifier
                        .focusRequester(deleteFocus)
                        .onFocusChanged {
                            if (it.isFocused) focusedAction = RecordingDetailsAction.DELETE
                        }
                        .focusProperties {
                            left = closeFocus
                            right = FocusRequester.Cancel
                            up = if (resumeSeconds != null) secondaryFocus
                                else if (canPlay) primaryFocus
                                else FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        }
                        .testTag("recording-details-delete"),
                ) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_recording))
                }
            }
        }
    }
}

@Composable
private fun recordingDurationForAccessibility(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3_600L
    val minutes = safeSeconds % 3_600L / 60L
    val seconds = safeSeconds % 60L
    return listOfNotNull(
        hours.takeIf { it > 0L }?.let {
            pluralStringResource(R.plurals.recording_duration_hours, it.toInt(), it)
        },
        minutes.takeIf { it > 0L }?.let {
            pluralStringResource(R.plurals.recording_duration_minutes, it.toInt(), it)
        },
        seconds.takeIf { it > 0L || hours == 0L && minutes == 0L }?.let {
            pluralStringResource(R.plurals.recording_duration_seconds, it.toInt(), it)
        },
    ).joinToString(", ")
}

@Composable
private fun RecordingDetailsSurface(
    contentPadding: PaddingValues,
    backEnabled: Boolean,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = backEnabled, onBack = onBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.64f))
            .focusGroup()
            .padding(contentPadding)
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            modifier = Modifier
                .width(560.dp)
                .heightIn(max = 432.dp)
                .testTag("recording-details-panel"),
            colors = SurfaceDefaults.colors(
                containerColor = TvSurfaceColors.containerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun RecordingConfirmationDialog(
    action: PendingRecordingAction,
    title: String,
    backEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val safeFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(action) { safeFocus.requestFocus() }
    RecordingDialogSurface(
        backEnabled = backEnabled,
        onBack = onDismiss,
    ) {
        Text(
            text = stringResource(
                when (action) {
                    PendingRecordingAction.STOP -> R.string.stop_recording_confirm_title
                    PendingRecordingAction.CANCEL -> R.string.cancel_recording_confirm_title
                    PendingRecordingAction.DELETE -> R.string.delete_recording_confirm_title
                },
                title,
            ),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(
                when (action) {
                    PendingRecordingAction.STOP -> R.string.stop_recording_confirm_message
                    PendingRecordingAction.CANCEL -> R.string.cancel_recording_confirm_message
                    PendingRecordingAction.DELETE -> R.string.delete_recording_confirm_message
                }
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .focusRequester(safeFocus)
                    .focusProperties {
                        left = FocusRequester.Cancel
                        right = confirmFocus
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    }
                    .testTag("recording-confirmation-back"),
            ) {
                Text(stringResource(R.string.back))
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .focusRequester(confirmFocus)
                    .focusProperties {
                        left = safeFocus
                        right = FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    }
                    .testTag("recording-confirmation-confirm"),
            ) {
                Text(
                    stringResource(
                        when (action) {
                            PendingRecordingAction.STOP -> R.string.stop_recording
                            PendingRecordingAction.CANCEL -> R.string.cancel_recording
                            PendingRecordingAction.DELETE -> R.string.delete_recording
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun RecordingDialogSurface(
    backEnabled: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(enabled = backEnabled, onBack = onBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = TvScrimModalAlpha))
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.width(720.dp),
            colors = SurfaceDefaults.colors(
                containerColor = TvSurfaceColors.containerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
            }
        }
    }
}
