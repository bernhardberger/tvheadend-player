package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.settings.WarmPlaybackMode

fun shouldMountPersistentPlayerSurface(
    hasActivePlayback: Boolean,
    isPlayerRoute: Boolean,
): Boolean = hasActivePlayback || isPlayerRoute

fun shouldShowWarmPlaybackScrim(
    hasActivePlayback: Boolean,
    isPlayerRoute: Boolean,
    mode: WarmPlaybackMode? = WarmPlaybackMode.ON,
): Boolean = hasActivePlayback && !isPlayerRoute && mode == WarmPlaybackMode.ON

fun shouldCoverWarmPlayback(isPlayerRoute: Boolean, mode: WarmPlaybackMode?): Boolean =
    !isPlayerRoute && mode != WarmPlaybackMode.ON

fun shouldStopPlaybackWhileBrowsing(isPlayerRoute: Boolean, mode: WarmPlaybackMode?): Boolean =
    !isPlayerRoute && mode == WarmPlaybackMode.OFF

fun shouldKeepPlaybackScreenOn(
    isForeground: Boolean,
    isVideoVisible: Boolean,
    isPlaying: Boolean,
    hasSelectedVideo: Boolean,
    hasError: Boolean,
): Boolean = isForeground && isVideoVisible && isPlaying && hasSelectedVideo && !hasError
