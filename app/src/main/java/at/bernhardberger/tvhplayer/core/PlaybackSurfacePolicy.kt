package at.bernhardberger.tvhplayer.core

fun shouldMountPersistentPlayerSurface(
    hasActivePlayback: Boolean,
    isPlayerRoute: Boolean,
): Boolean = hasActivePlayback || isPlayerRoute

fun shouldKeepPlaybackScreenOn(
    isForeground: Boolean,
    isVideoVisible: Boolean,
    isPlaying: Boolean,
    hasSelectedVideo: Boolean,
    hasError: Boolean,
): Boolean = isForeground && isVideoVisible && isPlaying && hasSelectedVideo && !hasError
