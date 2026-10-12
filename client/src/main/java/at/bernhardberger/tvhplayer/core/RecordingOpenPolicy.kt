package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrProgressPolicy
import kotlin.time.Duration

enum class RecordingOpenAction { START_OVER, RESUME_CHOICE, RESUME, DETAILS }

fun recordingOpenAction(entry: DvrEntry, playbackEligible: Boolean): RecordingOpenAction {
    if (!playbackEligible) return RecordingOpenAction.DETAILS
    val position = entry.playPosition ?: Duration.ZERO
    return when (entry.state) {
        DvrEntryState.RECORDING -> if (position.isPositive()) RecordingOpenAction.RESUME else RecordingOpenAction.START_OVER
        DvrEntryState.COMPLETED -> {
            val duration = if (entry.start != null && entry.stop != null) entry.stop!! - entry.start!! else null
            val finished = duration != null && duration.isPositive() &&
                position >= duration * DvrProgressPolicy().orderlyCompletionFraction
            if (!position.isPositive() || finished) RecordingOpenAction.START_OVER else RecordingOpenAction.RESUME_CHOICE
        }
        else -> RecordingOpenAction.DETAILS
    }
}
