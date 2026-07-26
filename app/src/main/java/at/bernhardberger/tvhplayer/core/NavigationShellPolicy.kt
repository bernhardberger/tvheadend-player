package at.bernhardberger.tvhplayer.core

/**
 * Whether the global navigation rail should be composed around browse content.
 *
 * Hidden during Simple TV, fullscreen players, and Settings (Settings keeps only
 * its own category rail so content reclaims the global-rail width).
 */
fun showGlobalNavigationRail(
    simpleTvActive: Boolean,
    topRoute: String?,
    playerRoute: String,
    recordingPlayerRoute: String,
    settingsRoute: String,
): Boolean {
    if (simpleTvActive) return false
    return when (topRoute) {
        playerRoute, recordingPlayerRoute, settingsRoute -> false
        else -> true
    }
}
