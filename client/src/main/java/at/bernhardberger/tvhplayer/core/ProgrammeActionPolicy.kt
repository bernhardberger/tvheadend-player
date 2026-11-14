package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import kotlin.math.abs
import at.bernhardberger.tvhplayer.notices.RecordUnavailableReason

enum class ProgrammeAction {
    WATCH,
    RECORD,
    RECORD_SERIES,
    RECORD_TITLE,
    CANCEL_RECORDING,
    STOP_RECORDING,
    WATCH_FROM_START,
}

fun programmeActions(
    event: EpgEventEntry,
    nowSec: Long,
    recording: DvrEntry?,
    serverTimeshiftCoversEvent: Boolean = false,
    canModifyRecordings: Boolean = true,
    /** The SDK's admission of [recording] from the observation it was read from; null offers no playback. */
    recordingAdmission: RecordingPlaybackAdmission? = null,
): List<ProgrammeAction> {
    val actions = when {
        event.start.epochSeconds <= nowSec && nowSec < event.stop.epochSeconds -> buildList {
            add(ProgrammeAction.WATCH)
            if (recording != null && recordingIsRunning(recording) && recordingPlaybackOffered(recordingAdmission)) {
                add(ProgrammeAction.WATCH_FROM_START)
            }
            add(when (recording?.state) {
                // Stopping keeps the partial recording; cancelling would abort it on the server.
                DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR -> ProgrammeAction.STOP_RECORDING
                DvrEntryState.SCHEDULED -> ProgrammeAction.CANCEL_RECORDING
                else -> ProgrammeAction.RECORD
            })
        }
        event.start.epochSeconds > nowSec -> when (recording?.state) {
            DvrEntryState.SCHEDULED -> listOf(ProgrammeAction.CANCEL_RECORDING)
            // Stopping keeps the partial recording; cancelling would abort it on the server.
            DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR -> listOf(ProgrammeAction.STOP_RECORDING)
            else -> listOf(ProgrammeAction.RECORD)
        }
        serverTimeshiftCoversEvent ||
            recording != null && recordingPlaybackOffered(recordingAdmission) -> listOf(ProgrammeAction.WATCH_FROM_START)
        else -> emptyList()
    }
    if (canModifyRecordings) {
        val oneOff = recording?.autorecRuleId == null && recording?.timerecRuleId == null
        val automatic = if (oneOff && (ProgrammeAction.RECORD in actions ||
                recording?.state in listOf(DvrEntryState.SCHEDULED, DvrEntryState.RECORDING,
                    DvrEntryState.RECORDING_ERROR))) {
            if (event.seriesLinkUri.isNullOrBlank()) ProgrammeAction.RECORD_TITLE else ProgrammeAction.RECORD_SERIES
        } else null
        return if (automatic == null) actions else actions + automatic
    }
    return actions.filter {
        it != ProgrammeAction.RECORD &&
            it != ProgrammeAction.CANCEL_RECORDING &&
            it != ProgrammeAction.STOP_RECORDING
    }
}

fun nearestProgrammeAt(
    events: List<EpgEventEntry>,
    targetStartSec: Long,
): EpgEventEntry? = events.minByOrNull { abs(it.start.epochSeconds - targetStartSec) }

fun recordUnavailableReason(
    event: EpgEventEntry?,
    nowSec: Long,
    canModifyRecordings: Boolean?,
): RecordUnavailableReason? = when {
    canModifyRecordings == null -> null
    event == null -> RecordUnavailableReason.NO_PROGRAMME
    event.stop.epochSeconds <= nowSec -> RecordUnavailableReason.ENDED
    !canModifyRecordings -> RecordUnavailableReason.PERMISSION_DENIED
    else -> null
}
