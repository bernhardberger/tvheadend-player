package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationShellPolicyTest {
    @Test
    fun backFromBrowseContentFocusesCurrentDrawerDestination() {
        assertEquals(
            BrowseShellBackAction.FOCUS_CURRENT_DESTINATION,
            browseShellBackAction(
                drawerOpen = false,
                currentRoute = "settings",
                rootRoute = "channels",
            ),
        )
    }

    @Test
    fun pendingRootWorkTakesBackPriorityOverBrowseFocus() {
        assertEquals(
            BrowseShellBackAction.DELEGATE_TO_ROOT,
            browseShellBackAction(
                drawerOpen = false,
                currentRoute = "channels",
                rootRoute = "channels",
                rootBackPriority = true,
            ),
        )
    }

    @Test
    fun backFromNonRootDrawerDestinationFocusesRoot() {
        assertEquals(
            BrowseShellBackAction.FOCUS_ROOT_DESTINATION,
            browseShellBackAction(
                drawerOpen = true,
                currentRoute = "settings",
                rootRoute = "channels",
            ),
        )
    }

    @Test
    fun backFromRootDrawerDestinationDelegatesToRootPolicy() {
        assertEquals(
            BrowseShellBackAction.DELEGATE_TO_ROOT,
            browseShellBackAction(
                drawerOpen = true,
                currentRoute = "channels",
                rootRoute = "channels",
            ),
        )
    }

    @Test
    fun backWaitsWhenDrawerIntentIsRootButRouteFeedbackIsNot() {
        assertEquals(
            BrowseShellBackAction.AWAIT_ROOT_DESTINATION,
            browseShellBackAction(
                drawerOpen = true,
                currentRoute = "epg",
                drawerRoute = "channels",
                rootRoute = "channels",
            ),
        )
    }

    @Test
    fun backUsesLatestNonRootDrawerIntentInsteadOfStaleRootRoute() {
        assertEquals(
            BrowseShellBackAction.FOCUS_ROOT_DESTINATION,
            browseShellBackAction(
                drawerOpen = true,
                currentRoute = "channels",
                drawerRoute = "recordings",
                rootRoute = "channels",
            ),
        )
    }

    @Test
    fun backFromSettingsContentFocusesCurrentCategory() {
        assertEquals(
            SettingsBackAction.FOCUS_CURRENT_CATEGORY,
            settingsBackAction(
                contentPaneFocused = true,
            ),
        )
    }

    @Test
    fun backFromSettingsCategoryDelegatesToGlobalDrawer() {
        assertEquals(
            SettingsBackAction.DELEGATE_TO_GLOBAL_NAVIGATION,
            settingsBackAction(
                contentPaneFocused = false,
            ),
        )
    }

    @Test
    fun showsRailOnBrowseDestinations() {
        assertTrue(
            showGlobalNavigationRail(
                simpleTvActive = false,
                playerVisible = false,
            ),
        )
        assertTrue(
            showGlobalNavigationRail(
                simpleTvActive = false,
                playerVisible = false,
            ),
        )
        assertTrue(
            showGlobalNavigationRail(
                simpleTvActive = false,
                playerVisible = false,
            ),
        )
    }

    @Test
    fun keepsStandardRailOnSettings() {
        assertTrue(
            showGlobalNavigationRail(
                simpleTvActive = false,
                playerVisible = false,
            ),
        )
    }

    @Test
    fun hidesRailOnPlayersAndSimpleTv() {
        assertFalse(
            showGlobalNavigationRail(
                simpleTvActive = false,
                playerVisible = true,
            ),
        )
        assertFalse(
            showGlobalNavigationRail(
                simpleTvActive = false,
                playerVisible = true,
            ),
        )
        assertFalse(showGlobalNavigationRail(simpleTvActive = true, playerVisible = false))
    }
}
