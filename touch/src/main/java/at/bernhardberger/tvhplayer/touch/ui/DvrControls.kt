package at.bernhardberger.tvhplayer.touch.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
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
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Instant

/** The recording control a program offers; its icon and label are kept here so a swap is one line. */
internal enum class ProgramRecordControl(@param:DrawableRes val icon: Int, @param:StringRes val label: Int) {
    Record(R.drawable.channels_record, R.string.channels_record),
    Cancel(R.drawable.guide_event_busy, SharedR.string.cancel_recording),
}

/** How a finished recording is removed: with its file, or only its entry when it never had one. */
internal enum class RecordingRemoveControl(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
    @param:StringRes val confirmTitle: Int,
    @param:StringRes val confirmBody: Int,
    @param:StringRes val confirmButton: Int,
) {
    Delete(
        R.drawable.connect_delete_outline, R.string.recordings_delete,
        SharedR.string.delete_recording_confirm_title, SharedR.string.delete_recording_confirm_body,
        SharedR.string.context_delete,
    ),
    RemoveEntry(
        R.drawable.connect_delete_outline, SharedR.string.remove_entry,
        SharedR.string.remove_entry_confirm_title, SharedR.string.remove_entry_confirm_body,
        R.string.recordings_remove,
    ),
}

/**
 * The control for the shared Record key action (`programmeRecordKeyAction`). Stop, Skip and Unskip are not
 * offered on touch yet, so a program that offers only those shows none.
 */
internal fun programRecordControl(action: BrowseContextAction?): ProgramRecordControl? = when (action) {
    BrowseContextAction.RECORD -> ProgramRecordControl.Record
    BrowseContextAction.CANCEL -> ProgramRecordControl.Cancel
    else -> null
}

/** The remove control among the shared `recordingContextActions`; Stop and Cancel are not offered here yet. */
internal fun recordingRemoveControl(actions: List<BrowseContextAction>): RecordingRemoveControl? = when {
    BrowseContextAction.DELETE in actions -> RecordingRemoveControl.Delete
    BrowseContextAction.REMOVE_ENTRY in actions -> RecordingRemoveControl.RemoveEntry
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
)

/**
 * Record, Cancel and Delete for the browse screens, through the shared DVR functions the TV calls. Record acts at
 * once; Cancel and Delete ask first ([ConfirmDvrChange]). Failures are posted to [notices] by the shared functions.
 */
@Stable
@OptIn(ExperimentalTvheadendApi::class)
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

    /** Asks to delete a recording, or to remove an entry without a file (RecordingsScreen's Delete). */
    fun delete(recordingId: String) {
        val current = observation
        val capability = current.currentSession
        val entry = recordingId.toLongOrNull()?.let { current.dvrEntry(DvrEntryId(it)) }
        val control = entry?.let(current::recordingRemoveControl)
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

    @Suppress("LongParameterList") // The change and the dialog's four texts.
    private fun ask(
        mutation: DvrMutationAction,
        source: SessionObservation,
        title: Int,
        body: Int,
        dismiss: Int,
        confirm: Int,
    ) {
        if (currentDvrMutation(mutation, source, source) == null) return
        pending = PendingDvrChange(mutation, source, title, body, dismiss, confirm)
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
    DvrChangeDialog(change.title, change.body, change.dismiss, change.confirm, controls::confirm, controls::dismiss)
}

/** The stock Material 3 dialog that asks before a Cancel or Delete; the confirming button is at the end. */
@Composable
@Suppress("LongParameterList") // The dialog's four texts and its two answers.
internal fun DvrChangeDialog(
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes dismiss: Int,
    @StringRes confirm: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(dismiss)) } },
    )
}
