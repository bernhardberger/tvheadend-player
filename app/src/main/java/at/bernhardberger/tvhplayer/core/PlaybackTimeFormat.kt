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

/** Signed delta for seek previews, e.g. "+0:30" / "−1:15". */
fun formatPlaybackDelta(deltaMs: Long): String {
    val magnitude = when {
        deltaMs >= 0L -> deltaMs
        deltaMs == Long.MIN_VALUE -> Long.MAX_VALUE
        else -> -deltaMs
    }
    val sign = if (deltaMs < 0L) "−" else "+"
    return sign + formatPlaybackDuration(magnitude)
}
