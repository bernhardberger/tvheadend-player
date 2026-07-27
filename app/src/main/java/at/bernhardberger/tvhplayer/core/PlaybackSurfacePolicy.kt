package at.bernhardberger.tvhplayer.core

fun shouldUseWarmVideoSurface(
    hasActivePlayback: Boolean,
    isPlayerRoute: Boolean,
): Boolean = hasActivePlayback && !isPlayerRoute
