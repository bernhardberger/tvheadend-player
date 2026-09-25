package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import kotlin.math.abs

enum class ProgrammeAction {
    WATCH,
    RECORD,
    CANCEL_RECORDING,
    WATCH_FROM_START,
}

fun programmeActions(
    event: EpgEventEntry,
    nowSec: Long,
    recording: DvrEntry?,
    serverTimeshiftCoversEvent: Boolean = false,
    canModifyRecordings: Boolean = true,
): List<ProgrammeAction> {
    val actions = when {
        event.start.epochSeconds <= nowSec && nowSec < event.stop.epochSeconds -> listOf(ProgrammeAction.WATCH)
        event.start.epochSeconds > nowSec -> when (recording?.state) {
            DvrEntryState.SCHEDULED,
            DvrEntryState.RECORDING -> listOf(ProgrammeAction.CANCEL_RECORDING)
            else -> listOf(ProgrammeAction.RECORD)
        }
        serverTimeshiftCoversEvent ||
            recording?.state == DvrEntryState.COMPLETED ||
            recording?.state == DvrEntryState.RECORDING -> listOf(ProgrammeAction.WATCH_FROM_START)
        else -> emptyList()
    }
    if (canModifyRecordings) return actions
    return actions.filter {
        it != ProgrammeAction.RECORD && it != ProgrammeAction.CANCEL_RECORDING
    }
}

fun nearestProgrammeAt(
    events: List<EpgEventEntry>,
    targetStartSec: Long,
): EpgEventEntry? = events.minByOrNull { abs(it.start.epochSeconds - targetStartSec) }
