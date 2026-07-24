package at.bernhardberger.tvhplayer.core

enum class PlaybackStatusPresentation {
    NONE,
    COMPACT_TUNING,
    FULL_RECOVERY,
}

fun playbackStatusPresentation(
    connectionAvailable: Boolean,
    playbackStarting: Boolean,
    playbackRecovering: Boolean,
    playbackPlaying: Boolean,
): PlaybackStatusPresentation = when {
    !connectionAvailable || playbackRecovering -> PlaybackStatusPresentation.FULL_RECOVERY
    playbackStarting -> PlaybackStatusPresentation.COMPACT_TUNING
    playbackPlaying -> PlaybackStatusPresentation.NONE
    else -> PlaybackStatusPresentation.NONE
}
