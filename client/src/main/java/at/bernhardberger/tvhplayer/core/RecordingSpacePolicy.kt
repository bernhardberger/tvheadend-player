package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrDiskSpace
import at.bernhardberger.tvheadend.sdk.core.DvrDiskSpaceState

/**
 * The recording storage figures worth showing, or null when there is nothing to show.
 * A stale value stays visible while the server is re-read; no value, or a total that
 * cannot anchor "free of", hides the surface instead of showing a placeholder.
 */
fun DvrDiskSpaceState.displayedDiskSpace(): DvrDiskSpace? =
    when (this) {
        is DvrDiskSpaceState.Current -> diskSpace
        is DvrDiskSpaceState.Stale -> diskSpace
        is DvrDiskSpaceState.Synchronizing -> staleDiskSpace
        DvrDiskSpaceState.Unknown -> null
    }?.takeIf { it.totalBytes > 0 }

/** How much room the recording storage has left, for the colour of its indicator. */
enum class RecordingSpaceLevel { NORMAL, LOW, CRITICAL }

/** What the storage indicator draws: the current figures and how urgent they are. */
data class RecordingSpaceIndicator(val freeBytes: Long, val totalBytes: Long, val level: RecordingSpaceLevel) {
    /** The share of the storage in use, 0 to 1. */
    val usedFraction: Float get() = ((totalBytes - freeBytes).toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat()
}

private const val LOW_FREE_SHARE = 0.15
private const val LOW_FREE_CEILING_BYTES = 500_000_000_000L
private const val CRITICAL_FREE_SHARE = 0.05
private const val CRITICAL_FREE_CEILING_BYTES = 100_000_000_000L

/**
 * The indicator for a current reading, or null when there is none to trust: a stale or still
 * synchronizing value is not shown as if it were fresh. Low is under 15% of the total free but no
 * more than 500 GB, critical under 5% but no more than 100 GB, so a huge store warns before it fills
 * and a small one still warns in proportion.
 */
fun DvrDiskSpaceState.recordingSpaceIndicator(): RecordingSpaceIndicator? {
    val space = (this as? DvrDiskSpaceState.Current)?.diskSpace?.takeIf { it.totalBytes > 0 } ?: return null
    val free = space.freeBytes.coerceAtLeast(0L)
    val level = when {
        free < minOf(space.totalBytes * CRITICAL_FREE_SHARE, CRITICAL_FREE_CEILING_BYTES.toDouble()) ->
            RecordingSpaceLevel.CRITICAL
        free < minOf(space.totalBytes * LOW_FREE_SHARE, LOW_FREE_CEILING_BYTES.toDouble()) ->
            RecordingSpaceLevel.LOW
        else -> RecordingSpaceLevel.NORMAL
    }
    return RecordingSpaceIndicator(free, space.totalBytes, level)
}
