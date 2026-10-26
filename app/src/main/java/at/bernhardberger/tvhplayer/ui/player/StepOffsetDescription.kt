package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.StepReadout

/**
 * The spoken step: [text] once the step has settled, else what was spoken last (null before the
 * first). The visible label follows every step; this follows the settled one, so a held key is
 * announced once, with its final value. [key] identifies the step (its offset and target as
 * spoken) and is held from the moment [settled] turns true: the server's resolved answer
 * refines the offset after the dispatch by a few hundred milliseconds, which is the same step.
 * [change] is what may announce a settled step again, its feedback. [text] is read when the
 * step settles or [change] changes, so a part that keeps changing, a growing buffer's length,
 * is filled in then and does not announce the same step again. A null [key] ends the stepping
 * at once.
 *
 * [settled] is the seek state's own: the step has been dispatched, by the debounce running out or
 * by the key being released. It is not a timer of this composition, which a quick completion
 * removing the preview could cut short. [onPublished] reports back, a moment after the spoken
 * value is in the semantics, that the announcement has been made, so the seek state keeps a
 * preview that a successful completion has already finished until then.
 */
@Composable
internal fun rememberSettledStep(
    key: String?,
    settled: Boolean,
    text: String,
    change: String = "",
    onPublished: () -> Unit = {},
): String? {
    val latest by rememberUpdatedState(text)
    val publish by rememberUpdatedState(onPublished)
    val held = remember { SpokenBeforeStep("") }
    if (!settled || key == null || held.key == null) held.key = key
    val stepKey = held.key
    var announcement by remember { mutableStateOf<Announcement?>(null) }
    LaunchedEffect(stepKey, settled, change) {
        when {
            stepKey == null -> announcement = null
            // The report is the one of this step, not of a later one that starts within the hold.
            settled -> announcement = Announcement(latest, publish)
        }
    }
    LaunchedEffect(announcement) {
        announcement?.let {
            // The accessibility bridge reads the semantics on its own cadence; the node has to be
            // there for it.
            delay(STEP_ANNOUNCEMENT_HOLD_MS)
            it.onPublished()
        }
    }
    return if (key == null) null else announcement?.text
}

/** One announcement; a repeat of the same words is still a new one. */
private class Announcement(val text: String, val onPublished: () -> Unit)

/**
 * How long a finished step's preview waits, once its announcement is in the semantics, for the
 * accessibility bridge to pick it up before the seek state may retire the preview.
 */
internal const val STEP_ANNOUNCEMENT_HOLD_MS = 200L

/**
 * The value a focused bar speaks: [plain] outside a step; during one, the [stepped] value of the
 * last settled step ([rememberSettledStep]), and before the first settles the [plain] value from
 * just before the step began. Nothing moves with the step mid-burst, programme metadata included.
 */
@Composable
internal fun rememberSteppedSpoken(
    plain: String,
    stepKey: String?,
    settled: Boolean,
    stepped: String,
    onPublished: () -> Unit = {},
): String {
    val settledStep = rememberSettledStep(stepKey, settled, stepped, onPublished = onPublished)
    val before = remember { SpokenBeforeStep(plain) }
    SideEffect { if (stepKey == null) before.value = plain }
    return when {
        stepKey == null -> plain
        else -> settledStep ?: before.value
    }
}

private class SpokenBeforeStep(var value: String, var key: String? = null)

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
