package at.bernhardberger.tvhplayer.appliance

internal fun shouldOpenPlayerForHome(
    isMainAction: Boolean,
    isHomeIntent: Boolean,
    hasSavedState: Boolean,
): Boolean = isMainAction && isHomeIntent && !hasSavedState

internal fun shouldConsumeBack(isDefaultHome: Boolean): Boolean = isDefaultHome
