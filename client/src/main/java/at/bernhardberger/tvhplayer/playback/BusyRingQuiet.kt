package at.bernhardberger.tvhplayer.playback

/** How long a covered picture may stay still after the sound began before the busy ring goes quiet. */
const val BUSY_RING_QUIET_AFTER_SOUND_MILLIS = 300L

/**
 * Milliseconds from [nowMillis] until the busy ring should go quiet: zero when it should be quiet
 * already, null when it must keep spinning (no sound under the cover, or the picture is shown).
 */
fun AppVideoPresentation.busyRingQuietInMillis(nowMillis: Long): Long? =
    if (visible) null else soundSinceMillis?.let { (it + BUSY_RING_QUIET_AFTER_SOUND_MILLIS - nowMillis).coerceAtLeast(0L) }
