package at.bernhardberger.tvhplayer.core

import java.util.Locale

/** Under one hour "m:ss", one hour or more "h:mm:ss". Never negative. */
fun formatPlaybackDuration(ms: Long): String {
    val totalSec = (ms / 1_000L).coerceAtLeast(0L)
    val hours = totalSec / 3_600L
    val minutes = (totalSec % 3_600L) / 60L
    val seconds = totalSec % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/**
 * A seek step's offset from where stepping started, e.g. "+3:00" / "−1:30" / "+1:02:00", with a real
 * minus sign and Latin digits like every other time on the bar, so one chip never mixes digit systems. Zero is unsigned: it moves nowhere.
 */
fun formatStepOffset(deltaMs: Long): String {
    val totalSec = (if (deltaMs == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(deltaMs)) / 1_000L
    val hours = totalSec / 3_600L
    val minutes = (totalSec % 3_600L) / 60L
    val seconds = totalSec % 60L
    val magnitude = if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
    return when {
        totalSec == 0L -> magnitude
        deltaMs < 0L -> "−$magnitude"
        else -> "+$magnitude"
    }
}
