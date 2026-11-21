package at.bernhardberger.tvhplayer.ui.screens.recordings

import android.content.res.Resources
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvhplayer.core.recordingLengthSeconds
import at.bernhardberger.tvhplayer.core.recordingPaddedStart
import at.bernhardberger.tvhplayer.presentation.R
import at.bernhardberger.tvhplayer.ui.common.formatNarrowDuration
import at.bernhardberger.tvhplayer.ui.common.formatRecordedDuration
import at.bernhardberger.tvhplayer.ui.common.formatSpokenDuration

/**
 * A running recording's time recorded so far, and the programme's time left to its listed end, which
 * the stop leaves out; null past that end or without one.
 */
data class StopConfirmationTimes(val recordedSec: Long, val remainingSec: Long?)

fun stopConfirmationTimes(entry: DvrEntry, nowSec: Long): StopConfirmationTimes {
    val recorded = recordingLengthSeconds(entry, nowSec)
        ?: recordingPaddedStart(entry)?.let { nowSec - it.epochSeconds } ?: 0L
    val remaining = entry.stop?.epochSeconds?.minus(nowSec)?.takeIf { it > 0L }
    return StopConfirmationTimes(recorded.coerceAtLeast(0L), remaining)
}

/** The stop sentence, drawn or as read aloud: what is kept, and the listed time left out while there is some. */
fun stopConfirmationSentence(
    entry: DvrEntry, nowSec: Long, resources: Resources, spoken: Boolean,
): String {
    return stopConfirmationSentence(stopConfirmationTimes(entry, nowSec), resources, spoken)
}

fun stopConfirmationSentence(
    times: StopConfirmationTimes, resources: Resources, spoken: Boolean,
): String {
    val recorded = sentenceDuration(times.recordedSec, roundUp = false, resources, spoken)
    return times.remainingSec?.let {
        resources.getString(R.string.stop_recording_confirm_body, recorded,
            sentenceDuration(it, roundUp = true, resources, spoken))
    } ?: resources.getString(R.string.stop_recording_confirm_body_open, recorded)
}

/**
 * Recorded minutes round down, remaining minutes round up. Drawn lengths use narrow units;
 * spoken lengths stay fully spelled out. Recorded lengths under a minute retain seconds.
 */
private fun sentenceDuration(
    seconds: Long, roundUp: Boolean, resources: Resources, spoken: Boolean,
): String {
    val minutes = if (roundUp) (seconds + 59L) / 60L else seconds / 60L
    return when {
        !spoken -> formatNarrowDuration(if (minutes > 0L) minutes * 60L else seconds, resources)
        minutes > 0L -> formatSpokenDuration(minutes * 60L, resources)
        else -> formatRecordedDuration(seconds, resources, spoken = true)
    }
}
