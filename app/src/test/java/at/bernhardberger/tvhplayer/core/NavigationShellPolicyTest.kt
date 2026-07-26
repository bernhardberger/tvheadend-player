package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationShellPolicyTest {
    @Test
    fun showsRailOnBrowseDestinations() {
        assertTrue(
            showGlobalNavigationRail(
                simpleTvActive = false,
                topRoute = "channels",
                playerRoute = "player",
                recordingPlayerRoute = "recording-player",
                settingsRoute = "settings",
            ),
        )
        assertTrue(
            showGlobalNavigationRail(
                simpleTvActive = false,
                topRoute = "epg",
                playerRoute = "player",
                recordingPlayerRoute = "recording-player",
                settingsRoute = "settings",
            ),
        )
        assertTrue(
            showGlobalNavigationRail(
                simpleTvActive = false,
                topRoute = "recordings",
                playerRoute = "player",
                recordingPlayerRoute = "recording-player",
                settingsRoute = "settings",
            ),
        )
    }

    @Test
    fun hidesRailOnSettingsSoCategoryRailReclaimsWidth() {
        assertFalse(
            showGlobalNavigationRail(
                simpleTvActive = false,
                topRoute = "settings",
                playerRoute = "player",
                recordingPlayerRoute = "recording-player",
                settingsRoute = "settings",
            ),
        )
    }

    @Test
    fun hidesRailOnPlayersAndSimpleTv() {
        assertFalse(
            showGlobalNavigationRail(
                simpleTvActive = false,
                topRoute = "player",
                playerRoute = "player",
                recordingPlayerRoute = "recording-player",
                settingsRoute = "settings",
            ),
        )
        assertFalse(
            showGlobalNavigationRail(
                simpleTvActive = false,
                topRoute = "recording-player",
                playerRoute = "player",
                recordingPlayerRoute = "recording-player",
                settingsRoute = "settings",
            ),
        )
        assertFalse(
            showGlobalNavigationRail(
                simpleTvActive = true,
                topRoute = "channels",
                playerRoute = "player",
                recordingPlayerRoute = "recording-player",
                settingsRoute = "settings",
            ),
        )
    }
}
