package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrState
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
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
): List<ProgrammeAction> = when {
    event.start <= nowSec && nowSec < event.stop -> listOf(ProgrammeAction.WATCH)
    event.start > nowSec -> when (recording?.state) {
        DvrState.SCHEDULED, DvrState.RECORDING -> listOf(ProgrammeAction.CANCEL_RECORDING)
        else -> listOf(ProgrammeAction.RECORD)
    }
    serverTimeshiftCoversEvent ||
        recording?.state == DvrState.COMPLETED ||
        recording?.state == DvrState.RECORDING -> listOf(ProgrammeAction.WATCH_FROM_START)
    else -> emptyList()
}

fun nearestProgrammeAt(
    events: List<EpgEventEntry>,
    targetStartSec: Long,
): EpgEventEntry? = events.minByOrNull { abs(it.start - targetStartSec) }
