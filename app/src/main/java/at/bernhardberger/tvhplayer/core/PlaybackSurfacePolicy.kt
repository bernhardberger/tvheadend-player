package at.bernhardberger.tvhplayer.core

fun shouldMountPersistentPlayerSurface(
    hasActivePlayback: Boolean,
    isPlayerRoute: Boolean,
): Boolean = hasActivePlayback || isPlayerRoute
