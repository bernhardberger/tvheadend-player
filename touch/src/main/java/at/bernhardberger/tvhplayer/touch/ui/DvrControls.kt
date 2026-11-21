package at.bernhardberger.tvhplayer.touch.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.core.isRuleRecording
import at.bernhardberger.tvhplayer.core.linkedRecordingForEvent
import at.bernhardberger.tvhplayer.core.programmeRecordingTarget
import at.bernhardberger.tvhplayer.core.recordingContextActions
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.ui.common.rememberCurrentEpochSeconds
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationAction
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions
import at.bernhardberger.tvhplayer.ui.screens.currentDvrMutation
import at.bernhardberger.tvhplayer.ui.screens.launchMutation
import at.bernhardberger.tvhplayer.ui.screens.recordChannelProgramme
import at.bernhardberger.tvhplayer.ui.screens.recordings.StopConfirmationTimes
import at.bernhardberger.tvhplayer.ui.screens.recordings.stopConfirmationSentence
import at.bernhardberger.tvhplayer.ui.screens.recordings.stopConfirmationTimes
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Instant

/** The recording control a program offers; its icon and label are kept here so a swap is one line. */
internal enum class ProgramRecordControl(@param:DrawableRes val icon: Int, @param:StringRes val label: Int) {
    Record(R.drawable.channels_record, R.string.channels_record),
    Cancel(R.drawable.dvr_stop, SharedR.string.cancel_recording),
    Stop(R.drawable.dvr_stop, SharedR.string.stop_recording),
}

/** A rule's schedule is shown as a state, not a control, until Skip/Unskip are available. */
internal enum class RuleSchedule(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
    @param:StringRes val markDescription: Int = label,
) {
    Scheduled(R.drawable.dvr_schedule_rule, SharedR.string.recording_state_scheduled, R.string.channels_scheduled),
    Skipped(R.drawable.guide_event_busy, SharedR.string.autorec_editor_skipped),
}

internal fun ruleSchedule(entry: DvrEntry?): RuleSchedule? = when {
    entry == null || entry.state != DvrEntryState.SCHEDULED || !entry.isRuleRecording -> null
    entry.enabled == false -> RuleSchedule.Skipped
    else -> RuleSchedule.Scheduled
}

/** The same linked entry as the shared `isScheduled` mark. */
internal fun SessionObservation.ruleSchedule(event: EpgEvent): RuleSchedule? =
    ruleSchedule(linkedRecordingForEvent(event))

/** Passive "Scheduled" or "Skipped" text with the same mark as the row. */
@Composable
internal fun RuleScheduleState(schedule: RuleSchedule, modifier: Modifier = Modifier) {
    Row(modifier.heightIn(min = ButtonDefaults.MinHeight), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(schedule.icon), null, Modifier.size(ButtonDefaults.IconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(schedule.label),
            Modifier.padding(start = ButtonDefaults.IconSpacing),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The recording's lifecycle action: stop, delete with its file, or remove an entry without a file. */
internal enum class RecordingRemoveControl(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
    @param:StringRes val confirmTitle: Int,
    @param:StringRes val confirmBody: Int,
    @param:StringRes val confirmButton: Int,
) {
    Delete(
        R.drawable.connect_delete_outline, SharedR.string.context_delete,
        SharedR.string.delete_recording_confirm_title, SharedR.string.delete_recording_confirm_body,
        SharedR.string.context_delete,
    ),
    RemoveEntry(
        R.drawable.connect_delete_outline, SharedR.string.remove_entry,
        SharedR.string.remove_entry_confirm_title, SharedR.string.remove_entry_confirm_body,
        SharedR.string.remove_entry,
    ),
    Stop(
        R.drawable.dvr_stop, SharedR.string.stop_recording,
        SharedR.string.stop_recording_confirm_title, SharedR.string.stop_recording_confirm_body,
        SharedR.string.stop_recording,
    ),
}

/**
 * The control for the shared Record key action (`programmeRecordKeyAction`). Skip and Unskip are not
 * offered on touch yet, so a program that offers only those shows none.
 */
internal fun programRecordControl(action: BrowseContextAction?): ProgramRecordControl? = when (action) {
    BrowseContextAction.RECORD -> ProgramRecordControl.Record
    BrowseContextAction.CANCEL -> ProgramRecordControl.Cancel
    BrowseContextAction.STOP -> ProgramRecordControl.Stop
    else -> null
}

/** The action among the shared `recordingContextActions`; Cancel is not offered in Recordings yet. */
internal fun recordingRemoveControl(actions: List<BrowseContextAction>): RecordingRemoveControl? = when {
    BrowseContextAction.DELETE in actions -> RecordingRemoveControl.Delete
    BrowseContextAction.REMOVE_ENTRY in actions -> RecordingRemoveControl.RemoveEntry
    BrowseContextAction.STOP in actions -> RecordingRemoveControl.Stop
    else -> null
}

/** What [entry] offers to remove it, by the shared rule of the TV's Recordings screen. */
@OptIn(ExperimentalTvheadendApi::class)
internal fun SessionObservation.recordingRemoveControl(entry: DvrEntry): RecordingRemoveControl? {
    val canModify = currentSession != null &&
        (sessionState as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
    return recordingRemoveControl(recordingContextActions(entry, recordingPlaybackAdmission(entry), canModify))
}

/** A change waiting for the viewer's answer, checked against [source] again when confirmed. */
internal class PendingDvrChange(
    val mutation: DvrMutationAction,
    val source: SessionObservation,
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
    @param:StringRes val dismiss: Int,
    @param:StringRes val confirm: Int,
    val stopTimes: StopConfirmationTimes? = null,
)

/**
 * DVR actions for the browse screens, through the shared functions the TV calls. Record acts at once; the
 * rest ask first ([ConfirmDvrChange]). Failures are posted to [notices] by the shared functions.
 */
@Stable
@OptIn(ExperimentalTvheadendApi::class)
@Suppress("TooManyFunctions") // Keep the browse callbacks and their shared confirmation state in one holder.
internal class DvrControls(
    private val session: TvheadendSession,
    private val notices: NoticeCenter,
    private val scope: CoroutineScope,
    private val nowSec: () -> Long,
) {
    private val mutations = DvrMutationActions(session.dvrRepository)
    private val inFlight = mutableStateOf<DvrMutationAction?>(null)
    var pending by mutableStateOf<PendingDvrChange?>(null)
        private set

    private val observation: SessionObservation get() = session.observation.value

    /** Records a Guide program (EpgGridScreen's Record key). */
    fun record(programId: String) {
        val current = observation
        val event = programId.toLongOrNull()?.let { current.event(EventId(it)) } ?: return
        val capability = current.currentSession ?: return
        launch(DvrMutationAction.CreateProgramme(event.programmeRecordingTarget(capability), null), current)
    }

    /** Records what is on now on a channel (ChannelsScreen's Record key); a scheduled one asks to cancel. */
    fun recordChannel(channelId: String) {
        val id = channelId.toLongOrNull()?.let(::ChannelId) ?: return
        val action = mutations.recordChannelProgramme(
            id, { observation }, nowSec, { null }, notices, scope, inFlight,
        )
        if (action == BrowseContextAction.CANCEL) cancelChannel(channelId)
    }

    /** Asks to cancel a Guide program's scheduled recording (ProgrammeDetailsHost's Cancel). */
    fun cancel(programId: String) {
        val current = observation
        programId.toLongOrNull()?.let { current.event(EventId(it)) }?.let { askCancel(it, current) }
    }

    /** Asks to cancel the scheduled recording of what is on now on a channel. */
    fun cancelChannel(channelId: String) {
        val current = observation
        val id = channelId.toLongOrNull()?.let(::ChannelId) ?: return
        current.eventAt(id, Instant.fromEpochSeconds(nowSec()))?.let { askCancel(it, current) }
    }

    fun stopProgram(programId: String) {
        val current = observation
        val event = programId.toLongOrNull()?.let { current.event(EventId(it)) } ?: return
        current.dvrEntryForEvent(event)?.let { askStop(it, current) }
    }

    fun stopChannel(channelId: String) {
        val current = observation
        val id = channelId.toLongOrNull()?.let(::ChannelId) ?: return
        val event = current.eventAt(id, Instant.fromEpochSeconds(nowSec())) ?: return
        current.dvrEntryForEvent(event)?.let { askStop(it, current) }
    }

    fun stopRecording(recordingId: String) {
        val current = observation
        recordingId.toLongOrNull()?.let { current.dvrEntry(DvrEntryId(it)) }?.let { askStop(it, current) }
    }

    /** Asks to delete a recording, or to remove an entry without a file (RecordingsScreen's Delete). */
    fun delete(recordingId: String) {
        val current = observation
        val capability = current.currentSession
        val entry = recordingId.toLongOrNull()?.let { current.dvrEntry(DvrEntryId(it)) }
        val control = entry?.let(current::recordingRemoveControl)?.takeUnless { it == RecordingRemoveControl.Stop }
        if (capability == null || entry == null || control == null) return
        ask(
            DvrMutationAction.Delete(capability, entry.id), current,
            control.confirmTitle, control.confirmBody, SharedR.string.cancel, control.confirmButton,
        )
    }

    fun confirm() {
        val change = pending ?: return
        pending = null
        launch(change.mutation, change.source)
    }

    fun dismiss() {
        pending = null
    }

    private fun askCancel(event: EpgEvent, current: SessionObservation) {
        val capability = current.currentSession ?: return
        val recording = current.dvrEntryForEvent(event) ?: return
        ask(
            DvrMutationAction.Cancel(capability, recording.id), current,
            SharedR.string.cancel_recording_confirm_title, SharedR.string.cancel_recording_confirm_body,
            SharedR.string.keep_recording, SharedR.string.cancel_recording,
        )
    }

    private fun askStop(entry: DvrEntry, current: SessionObservation) {
        val capability = current.currentSession ?: return
        val control = RecordingRemoveControl.Stop
        ask(
            DvrMutationAction.Stop(capability, entry.id), current,
            control.confirmTitle, control.confirmBody, SharedR.string.keep_recording, control.confirmButton,
            stopConfirmationTimes(entry, nowSec()),
        )
    }

    @Suppress("LongParameterList") // The change, the dialog's four texts, and optional stop durations.
    private fun ask(
        mutation: DvrMutationAction,
        source: SessionObservation,
        title: Int,
        body: Int,
        dismiss: Int,
        confirm: Int,
        stopTimes: StopConfirmationTimes? = null,
    ) {
        if (currentDvrMutation(mutation, source, source) == null) return
        pending = PendingDvrChange(mutation, source, title, body, dismiss, confirm, stopTimes)
    }

    private fun launch(mutation: DvrMutationAction, source: SessionObservation) {
        mutations.launchMutation(mutation, source, { observation }, nowSec, { null }, notices, scope, inFlight)
    }
}

@Composable
internal fun rememberDvrControls(session: TvheadendSession, notices: NoticeCenter): DvrControls {
    val scope = rememberCoroutineScope()
    val nowSec = rememberCurrentEpochSeconds()
    return remember(session, notices, scope) { DvrControls(session, notices, scope, nowSec) }
}

/** Asks about the change [controls] wait on, if any. */
@Composable
internal fun ConfirmDvrChange(controls: DvrControls) {
    val change = controls.pending ?: return
    DvrChangeDialog(
        change.title, change.body, change.dismiss, change.confirm, controls::confirm, controls::dismiss,
        stopTimes = change.stopTimes,
    )
}

/** The stock Material 3 confirmation dialog; the confirming button is at the end. */
@Composable
internal fun DvrChangeDialog(
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes dismiss: Int,
    @StringRes confirm: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    stopTimes: StopConfirmationTimes? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            if (stopTimes == null) {
                Text(stringResource(body))
            } else {
                val resources = LocalResources.current
                val spoken = stopConfirmationSentence(stopTimes, resources, spoken = true)
                Text(
                    stopConfirmationSentence(stopTimes, resources, spoken = false),
                    Modifier.clearAndSetSemantics { text = AnnotatedString(spoken) },
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(dismiss)) } },
    )
}
