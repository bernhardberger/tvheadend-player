package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R as AppR
import at.bernhardberger.tvhplayer.presentation.R
import at.bernhardberger.tvhplayer.core.deleteRemovesEntryOnly
import at.bernhardberger.tvhplayer.core.recordingArtwork
import at.bernhardberger.tvhplayer.core.recordingElapsedProgress
import at.bernhardberger.tvhplayer.core.recordingIsRunning
import at.bernhardberger.tvhplayer.ui.common.formatRecordingSchedule
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.components.ConfirmationChoice
import at.bernhardberger.tvhplayer.ui.components.ConfirmationDialog
import at.bernhardberger.tvhplayer.ui.components.ConfirmationSubject
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal enum class PendingRecordingAction { STOP, CANCEL, DELETE }

internal enum class RecordingDetailsAction { RESUME, BEGINNING, PLAY, STOP, CANCEL, DELETE }

/**
 * Stop, cancel, delete or remove [entry]. Its pictures come from [currentSession], the session the
 * entry was read from, and [observation]'s saved logos.
 */
@Composable
internal fun RecordingConfirmationDialog(
    action: PendingRecordingAction,
    entry: DvrEntry,
    channel: Channel?,
    nowSec: Long,
    currentSession: CurrentSessionObservation?,
    observation: SessionObservation?,
    backEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    visibility: MutableTransitionState<Boolean> = remember { MutableTransitionState(false).apply { targetState = true } },
) {
    val removeEntry = action == PendingRecordingAction.DELETE && deleteRemovesEntryOnly(entry)
    ProvideArtworkSession(currentSession, observation) {
        ConfirmationDialog(
            visibility = visibility,
            question = stringResource(when (action) {
                PendingRecordingAction.STOP -> R.string.stop_recording_confirm_title
                PendingRecordingAction.CANCEL -> R.string.cancel_recording_confirm_title
                PendingRecordingAction.DELETE ->
                    if (removeEntry) R.string.remove_entry_confirm_title else R.string.delete_recording_confirm_title
            }),
            subject = recordingConfirmationSubject(entry, channel, nowSec),
            sentence = recordingConfirmationSentence(action, removeEntry, entry, nowSec),
            safe = ConfirmationChoice(
                stringResource(if (action == PendingRecordingAction.DELETE) R.string.keep else R.string.keep_recording),
                AppR.drawable.ic_check, "recording-confirmation-back"),
            action = ConfirmationChoice(stringResource(recordingConfirmationActionLabel(action, removeEntry)),
                recordingConfirmationActionIcon(action), "recording-confirmation-confirm"),
            onDismiss = onDismiss, onConfirm = onConfirm, backEnabled = backEnabled,
            spokenSentence = if (action == PendingRecordingAction.STOP) {
                stopConfirmationSentence(entry, nowSec, LocalContext.current.resources, spoken = true)
            } else null,
        )
    }
}

@Composable
private fun recordingConfirmationSubject(entry: DvrEntry, channel: Channel?, nowSec: Long) =
    ConfirmationSubject.Programme(
        name = entry.title.orEmpty(),
        image = recordingArtwork(entry),
        channelIcon = channel?.icon,
        channelNumber = channel?.number,
        channelName = channel?.name ?: entry.channelName.orEmpty(),
        airTime = recordingConfirmationAirTime(entry.start?.epochSeconds, entry.stop?.epochSeconds, nowSec),
        progress = if (recordingIsRunning(entry)) recordingElapsedProgress(entry, nowSec)
            ?: if (entry.start?.epochSeconds?.let { nowSec < it } == true) 0f else 1f else null,
    )

/** "Today 20:15–22:05", "Tomorrow …", or the date with the times. */
@Composable
internal fun recordingConfirmationAirTime(start: Long?, stop: Long?, nowSec: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    if (start == null) return ""
    return if (stop == null) formatRecordingSchedule(start, locale = locale, nowSec = nowSec) else {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
        when (ChronoUnit.DAYS.between(today, Instant.ofEpochSecond(start).atZone(zone).toLocalDate())) {
            0L -> stringResource(R.string.details_today, formatTimeInterval(start, stop, locale = locale))
            1L -> stringResource(R.string.details_tomorrow, formatTimeInterval(start, stop, locale = locale))
            else -> formatTimeInterval(start, stop, withDate = true, locale = locale, nowSec = nowSec)
        }
    }
}

@Composable
private fun recordingConfirmationSentence(
    action: PendingRecordingAction, removeEntry: Boolean, entry: DvrEntry, nowSec: Long,
): String = when (action) {
    PendingRecordingAction.STOP ->
        stopConfirmationSentence(entry, nowSec, LocalContext.current.resources, spoken = false)
    PendingRecordingAction.CANCEL -> stringResource(R.string.cancel_recording_confirm_body)
    PendingRecordingAction.DELETE -> stringResource(
        if (removeEntry) R.string.remove_entry_confirm_body else R.string.delete_recording_confirm_body)
}

private fun recordingConfirmationActionLabel(action: PendingRecordingAction, removeEntry: Boolean): Int =
    when (action) {
        PendingRecordingAction.STOP -> R.string.stop_recording
        PendingRecordingAction.CANCEL -> R.string.cancel_recording
        PendingRecordingAction.DELETE -> if (removeEntry) R.string.remove_entry else R.string.delete_recording
    }

private fun recordingConfirmationActionIcon(action: PendingRecordingAction): Int = when (action) {
    PendingRecordingAction.STOP -> AppR.drawable.ic_stop
    PendingRecordingAction.CANCEL -> AppR.drawable.ic_close
    PendingRecordingAction.DELETE -> AppR.drawable.ic_delete_outlined
}
