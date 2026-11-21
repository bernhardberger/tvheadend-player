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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
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
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationAction
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions
import at.bernhardberger.tvhplayer.ui.screens.currentDvrMutation
import at.bernhardberger.tvhplayer.ui.screens.launchMutation
import at.bernhardberger.tvhplayer.ui.screens.programmeRecordKeyAction
import at.bernhardberger.tvhplayer.ui.screens.recordings.StopConfirmationTimes
import at.bernhardberger.tvhplayer.ui.screens.recordings.stopConfirmationSentence
import at.bernhardberger.tvhplayer.ui.screens.recordings.stopConfirmationTimes
import kotlinx.coroutines.launch
import kotlin.time.Clock
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
    val canModify = currentSession != null && dvrState is DvrRepositoryState.Current &&
        (sessionState as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
    return recordingRemoveControl(recordingContextActions(entry, recordingPlaybackAdmission(entry), canModify))
}

/** The exact offer confirmed by the viewer, not just the wire mutation it maps to. */
internal sealed interface DvrConfirmationTarget {
    val recordingId: DvrEntryId

    data class Programme(
        override val recordingId: DvrEntryId,
        val eventId: EventId,
        val control: ProgramRecordControl,
    ) : DvrConfirmationTarget

    data class Recording(
        override val recordingId: DvrEntryId,
        val control: RecordingRemoveControl,
    ) : DvrConfirmationTarget
}

internal class PendingDvrChange(
    val mutation: DvrMutationAction,
    val source: SessionObservation,
    val target: DvrConfirmationTarget,
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
    private val nowSec: () -> Long = { Clock.System.now().epochSeconds },
) : ViewModel() {
    private val mutations = DvrMutationActions(session.dvrRepository)
    private val inFlight = mutableStateOf<DvrMutationAction?>(null)
    val commandInFlight: Boolean get() = inFlight.value != null
    var pending by mutableStateOf<PendingDvrChange?>(null)
        private set

    private val observation: SessionObservation get() = session.observation.value

    init {
        viewModelScope.launch {
            session.observation.collect { current ->
                if (pending?.isStillOffered(current) == false) dismiss()
            }
        }
    }

    /** Records a Guide program (EpgGridScreen's Record key). */
    fun record(programId: String) {
        val current = observation
        val event = programId.toLongOrNull()?.let { current.event(EventId(it)) } ?: return
        record(event, current)
    }

    private fun record(
        event: EpgEvent,
        current: SessionObservation,
        stillCurrent: (SessionObservation) -> Boolean = { true },
    ) {
        val capability = current.currentSession ?: return
        launch(
            DvrMutationAction.CreateProgramme(event.programmeRecordingTarget(capability), null),
            current, stillCurrent,
        )
    }

    /** Never substitute the next programme for the one on the expanded row. */
    fun channelRecordControl(channelId: String, eventId: EventId, control: ProgramRecordControl) {
        val id = channelId.toLongOrNull()?.let(::ChannelId) ?: return
        val onAir: (SessionObservation) -> Boolean = { latest ->
            latest.eventAt(id, Instant.fromEpochSeconds(nowSec()))?.id == eventId
        }
        val current = observation
        val event = current.event(eventId)?.takeIf { onAir(current) } ?: return
        when (control) {
            ProgramRecordControl.Record -> record(event, current, onAir)
            ProgramRecordControl.Cancel -> askCancel(event, current)
            ProgramRecordControl.Stop -> askProgrammeStop(event, current)
        }
    }

    /** Asks to cancel a Guide program's scheduled recording (ProgrammeDetailsHost's Cancel). */
    fun cancel(programId: String) {
        val current = observation
        programId.toLongOrNull()?.let { current.event(EventId(it)) }?.let { askCancel(it, current) }
    }

    fun stopProgram(programId: String) {
        val current = observation
        val event = programId.toLongOrNull()?.let { current.event(EventId(it)) } ?: return
        askProgrammeStop(event, current)
    }

    private fun askProgrammeStop(event: EpgEvent, current: SessionObservation) {
        val entry = current.dvrEntryForEvent(event) ?: return
        askStop(entry, current, DvrConfirmationTarget.Programme(entry.id, event.id, ProgramRecordControl.Stop))
    }

    fun stopRecording(recordingId: String) {
        val current = observation
        val entry = recordingId.toLongOrNull()?.let { current.dvrEntry(DvrEntryId(it)) } ?: return
        askStop(entry, current, DvrConfirmationTarget.Recording(entry.id, RecordingRemoveControl.Stop))
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
            DvrConfirmationTarget.Recording(entry.id, control),
            control.confirmTitle, control.confirmBody, SharedR.string.cancel, control.confirmButton,
        )
    }

    fun confirm(change: PendingDvrChange) {
        if (pending !== change) return
        if (!change.isStillOffered(observation)) {
            dismiss()
        } else if (!commandInFlight) {
            dismiss()
            launch(change.mutation, change.source) { change.isStillOffered(it) }
        }
    }

    fun dismiss() {
        pending = null
    }

    private fun askCancel(event: EpgEvent, current: SessionObservation) {
        val capability = current.currentSession ?: return
        val recording = current.dvrEntryForEvent(event) ?: return
        ask(
            DvrMutationAction.Cancel(capability, recording.id), current,
            DvrConfirmationTarget.Programme(recording.id, event.id, ProgramRecordControl.Cancel),
            SharedR.string.cancel_recording_confirm_title, SharedR.string.cancel_recording_confirm_body,
            SharedR.string.keep_recording, SharedR.string.cancel_recording,
        )
    }

    private fun askStop(entry: DvrEntry, current: SessionObservation, target: DvrConfirmationTarget) {
        val capability = current.currentSession ?: return
        val control = RecordingRemoveControl.Stop
        ask(
            DvrMutationAction.Stop(capability, entry.id), current, target,
            control.confirmTitle, control.confirmBody, SharedR.string.keep_recording, control.confirmButton,
            stopConfirmationTimes(entry, nowSec()),
        )
    }

    @Suppress("LongParameterList") // The change, the dialog's four texts, and optional stop durations.
    private fun ask(
        mutation: DvrMutationAction,
        source: SessionObservation,
        target: DvrConfirmationTarget,
        title: Int,
        body: Int,
        dismiss: Int,
        confirm: Int,
        stopTimes: StopConfirmationTimes? = null,
    ) {
        val change = PendingDvrChange(mutation, source, target, title, body, dismiss, confirm, stopTimes)
        if (change.isStillOffered(observation)) pending = change
    }

    private fun PendingDvrChange.isStillOffered(current: SessionObservation): Boolean {
        if (currentDvrMutation(mutation, source, current) == null) return false
        return when (val asked = target) {
            is DvrConfirmationTarget.Recording ->
                current.dvrEntry(asked.recordingId)?.let(current::recordingRemoveControl) == asked.control
            is DvrConfirmationTarget.Programme -> current.event(asked.eventId)?.let { event ->
                current.dvrEntryForEvent(event)?.id == asked.recordingId &&
                    programRecordControl(programmeRecordKeyAction(event, current, nowSec())) == asked.control
            } == true
        }
    }

    private fun launch(
        mutation: DvrMutationAction,
        source: SessionObservation,
        stillCurrent: (SessionObservation) -> Boolean,
    ) {
        mutations.launchMutation(
            mutation, source,
            // The shared launch can be dispatched later. Never pass a live proof for a stale touch action.
            { observation.takeIf(stillCurrent) ?: SessionObservation.create() },
            nowSec, { null }, notices, viewModelScope, inFlight,
        )
    }
}

/** Asks about the change [controls] wait on, if any. */
@Composable
internal fun ConfirmDvrChange(controls: DvrControls) {
    val change = controls.pending ?: return
    DvrChangeDialog(
        change.title, change.body, change.dismiss, change.confirm, { controls.confirm(change) }, controls::dismiss,
        stopTimes = change.stopTimes,
        confirmEnabled = !controls.commandInFlight,
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
    confirmEnabled: Boolean = true,
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
        confirmButton = { TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(dismiss)) } },
    )
}
