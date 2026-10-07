package at.bernhardberger.tvhplayer.ui.screens.recordings

import at.bernhardberger.tvhplayer.ui.TvScrimModalAlpha
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.notifications.label
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsLayout
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsInformation
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsButton
import at.bernhardberger.tvhplayer.ui.components.programmeDetailsFrame
import at.bernhardberger.tvhplayer.ui.player.ProgrammeHero
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import coil3.ImageLoader
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import at.bernhardberger.tvhplayer.ui.player.PlayerChromeTokens

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
    entry: DvrEntry,
    canModifyRecordings: Boolean,
    playbackEligible: Boolean,
    initialAction: RecordingDetailsAction?,
    backEnabled: Boolean,
    onPlay: (RecordingPlaybackStart) -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    channel: Channel?,
) {
    val primaryFocus = remember { FocusRequester() }
    val secondaryFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val deleteFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    var panelFocused by remember(entry.id) { mutableStateOf(false) }
    var focusedAction by remember(entry.id) { mutableStateOf<RecordingDetailsAction?>(null) }
    // Stop and Cancel are mutually exclusive and share one button slot and focus requester.
    val endAction = recordingEndAction(entry.state).takeIf { canModifyRecordings }
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
    // A completed or an in-progress recording resumes where the viewer stopped.
    val resumeSeconds = entry.playPosition?.inWholeSeconds?.takeIf { canPlay && it > 0L }
    val primaryAction = when {
        resumeSeconds != null -> RecordingDetailsAction.RESUME
        canPlay -> RecordingDetailsAction.PLAY
        endAction != null -> endAction
        canDelete -> RecordingDetailsAction.DELETE
        else -> null
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
    }
    fun requester(action: RecordingDetailsAction): FocusRequester = when (action) {
        RecordingDetailsAction.RESUME,
        RecordingDetailsAction.PLAY -> primaryFocus
        RecordingDetailsAction.BEGINNING -> secondaryFocus
        RecordingDetailsAction.STOP,
        RecordingDetailsAction.CANCEL -> cancelFocus
        RecordingDetailsAction.DELETE -> deleteFocus
    }
    LaunchedEffect(entry.id, initialAction) {
        withFrameNanos { }
        ((initialAction?.takeIf { it in availableActions } ?: primaryAction)?.let(::requester) ?: panelFocus).requestFocus()
    }
    val removedFocusTarget = if (focusedAction != null && focusedAction !in availableActions || panelFocused) {
        primaryAction?.let(::requester) ?: panelFocus
    } else null
    LaunchedEffect(availableActions) {
        if (availableActions.isEmpty() || removedFocusTarget != null) {
            withFrameNanos { }
            (removedFocusTarget ?: panelFocus).requestFocus()
        }
    }
    BackHandler(enabled = backEnabled, onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = TvScrimModalAlpha))
        .then(if (availableActions.isEmpty()) Modifier
            .focusRequester(panelFocus)
            .onFocusChanged { panelFocused = it.isFocused }
            .focusProperties {
                up = FocusRequester.Cancel; down = FocusRequester.Cancel
                left = FocusRequester.Cancel; right = FocusRequester.Cancel
            }
            .focusable() else Modifier.focusGroup())
        .testTag("recording-details-panel")) {
        ProgrammeDetailsLayout(modifier = Modifier.programmeDetailsFrame(), reading = {
            ProgrammeDetailsInformation(entry.title, entry.subtitle, buildString {
                append(entry.start?.epochSeconds.recordingDateTime())
                entry.stop?.epochSeconds?.let { append('–').append(formatHm(it)) }
                entry.channelName?.let { append(" • ").append(it) }
                append(" • ").append(dvrStateLabel(entry.state))
            }, if (entry.subscriptionError == null) entry.summary?.takeIf(String::isNotBlank) ?: entry.description else null,
                tile = { modifier ->
                    ProgrammeHero(entry.image, entry.channelId ?: ChannelId(0), channel?.number?.toString().orEmpty(),
                        channel?.icon, imageLoader, currentSession, modifier.clip(PlayerChromeTokens.heroShape))
                }, status = {
                    entry.subscriptionError?.label()?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                })
        }) {
            val ordered = availableActions.toList()
            ordered.forEachIndexed { index, action ->
                key(action) {
                    val label = when (action) {
                        RecordingDetailsAction.RESUME -> stringResource(R.string.recording_resume_from,
                            formatPlaybackDuration(requireNotNull(resumeSeconds).coerceAtMost(Long.MAX_VALUE / 1000) * 1000))
                        RecordingDetailsAction.BEGINNING -> stringResource(R.string.recording_play_from_beginning)
                        RecordingDetailsAction.PLAY -> stringResource(R.string.play)
                        RecordingDetailsAction.STOP -> stringResource(R.string.stop_recording)
                        RecordingDetailsAction.CANCEL -> stringResource(R.string.cancel_recording)
                        RecordingDetailsAction.DELETE -> stringResource(R.string.delete_recording)
                    }
                    val accessibleLabel = if (action == RecordingDetailsAction.RESUME)
                        stringResource(R.string.recording_resume_from, recordingDurationForAccessibility(requireNotNull(resumeSeconds))) else null
                    ProgrammeDetailsButton(title = label, onClick = {
                        when (action) {
                            RecordingDetailsAction.RESUME -> onPlay(RecordingPlaybackStart.RESUME)
                            RecordingDetailsAction.BEGINNING, RecordingDetailsAction.PLAY -> onPlay(RecordingPlaybackStart.START_OVER)
                            RecordingDetailsAction.STOP -> onStop()
                            RecordingDetailsAction.CANCEL -> onCancel()
                            RecordingDetailsAction.DELETE -> onDelete()
                        }
                    }, modifier = Modifier.focusRequester(requester(action))
                        .onFocusChanged { if (it.isFocused) { focusedAction = action; panelFocused = false } }
                        .focusProperties {
                            up = if (index == 0) FocusRequester.Cancel else requester(ordered[index - 1])
                            down = if (index == ordered.lastIndex) FocusRequester.Cancel else requester(ordered[index + 1])
                        }
                        .semantics { accessibleLabel?.let { contentDescription = it } }
                        .testTag("recording-details-${action.name.lowercase(java.util.Locale.ROOT)}"),
                        icon = { Icon(painterResource(when (action) {
                            RecordingDetailsAction.STOP, RecordingDetailsAction.CANCEL -> R.drawable.ic_stop
                            RecordingDetailsAction.DELETE -> R.drawable.ic_delete_outlined
                            else -> R.drawable.ic_play_arrow
                        }), null) })
                }
            }
        }
    }
}

@Composable
internal fun recordingDurationForAccessibility(totalSeconds: Long): String {
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
internal fun RecordingConfirmationDialog(
    action: PendingRecordingAction,
    title: String,
    backEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val safeFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(action) {
        // Wait one frame so Back is ready to draw its focused state.
        withFrameNanos { }
        safeFocus.requestFocus()
    }
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
