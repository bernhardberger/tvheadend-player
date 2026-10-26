package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R

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
