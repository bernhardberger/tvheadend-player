package at.bernhardberger.tvhplayer.core

enum class BrowseShellBackAction {
    FOCUS_CURRENT_DESTINATION,
    FOCUS_HOME_DESTINATION,
    DELEGATE_TO_ROOT,
}

enum class SettingsBackAction {
    FOCUS_CURRENT_CATEGORY,
    DELEGATE_TO_GLOBAL_NAVIGATION,
}

fun browseShellBackAction(
    drawerOpen: Boolean,
    currentRoute: String?,
    homeRoute: String,
    rootBackPriority: Boolean = false,
): BrowseShellBackAction = when {
    rootBackPriority -> BrowseShellBackAction.DELEGATE_TO_ROOT
    !drawerOpen -> BrowseShellBackAction.FOCUS_CURRENT_DESTINATION
    currentRoute != homeRoute -> BrowseShellBackAction.FOCUS_HOME_DESTINATION
    else -> BrowseShellBackAction.DELEGATE_TO_ROOT
}

fun settingsBackAction(
    contentPaneFocused: Boolean,
): SettingsBackAction = if (contentPaneFocused) {
    SettingsBackAction.FOCUS_CURRENT_CATEGORY
} else {
    SettingsBackAction.DELEGATE_TO_GLOBAL_NAVIGATION
}

/**
 * Whether the global navigation rail should be composed around browse content.
 *
 * Hidden during Simple TV and fullscreen players. Browse destinations, including
 * Settings, retain the standard drawer's collapsed icon rail.
 */
fun showGlobalNavigationRail(
    simpleTvActive: Boolean,
    topRoute: String?,
    playerRoute: String,
    recordingPlayerRoute: String,
): Boolean {
    if (simpleTvActive) return false
    return when (topRoute) {
        playerRoute, recordingPlayerRoute -> false
        else -> true
    }
}
