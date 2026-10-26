package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.StepReadout
import kotlinx.coroutines.delay

/** How long stepping must rest before its step is spoken; the seek debounce's window, so one burst is one announcement. */
internal const val STEP_ANNOUNCEMENT_SETTLE_MS = 400L

/**
 * The spoken [step] once it has rested for [STEP_ANNOUNCEMENT_SETTLE_MS], else what was spoken last
 * (null before the first). The visible label follows every step; this follows the last one, so a
 * held key is announced once, with its final value. A null [step] ends the stepping at once.
 */
@Composable
internal fun rememberSettledStep(step: String?): String? {
    var spoken by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(step) {
        if (step == null) spoken = null
        else {
            delay(STEP_ANNOUNCEMENT_SETTLE_MS)
            spoken = step
        }
    }
    return if (step == null) null else spoken
}

/**
 * A live step as spoken: offset, target, the distance behind live when the target is a clock time
 * (a `Live` target or the distance itself already says it) and [bufferStart].
 */
@Composable
internal fun liveStepDescription(
    offsetMs: Long,
    readout: StepReadout,
    hasClock: Boolean,
    behindLiveLabel: String,
    bufferStart: String,
): String {
    val offset = stepOffsetDescription(offsetMs)
    val target = requireNotNull(readout.target)
    return if (hasClock && !readout.atLive) {
        stringResource(R.string.timeshift_seek_preview_description, offset, target, behindLiveLabel, bufferStart)
    } else {
        stringResource(
            R.string.timeshift_seek_preview_short_description, offset, if (readout.atLive) target else behindLiveLabel, bufferStart,
        )
    }
}

/** A seek step's offset as spoken: "3 minutes forward", "1 hour 2 minutes back". */
@Composable
internal fun stepOffsetDescription(deltaMs: Long): String {
    val totalSec = (if (deltaMs == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(deltaMs)) / 1_000L
    if (totalSec == 0L) return stringResource(R.string.seek_offset_none_description)
    val hours = (totalSec / 3_600L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val minutes = ((totalSec % 3_600L) / 60L).toInt()
    val seconds = (totalSec % 60L).toInt()
    val duration = buildList {
        if (hours > 0) add(pluralStringResource(R.plurals.seek_offset_hours, hours, hours))
        if (minutes > 0) add(pluralStringResource(R.plurals.seek_offset_minutes, minutes, minutes))
        // Zero units are left out, so a whole number of minutes reads without "0 seconds".
        if (seconds > 0) add(pluralStringResource(R.plurals.seek_offset_seconds, seconds, seconds))
    }.joinToString(" ")
    return stringResource(
        if (deltaMs < 0L) R.string.seek_offset_back_description else R.string.seek_offset_forward_description,
        duration,
    )
}
