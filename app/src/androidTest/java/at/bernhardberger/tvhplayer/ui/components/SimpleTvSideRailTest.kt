package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvhplayer.core.simpleTvProfile
import at.bernhardberger.tvhplayer.ui.Routes
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class SimpleTvSideRailTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lockedDefaultRemovesDisabledRoutesAndKeepsUnlockVisible() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SideRail(
                    currentRoute = Routes.CHANNELS,
                    showEpgMenu = true,
                    simpleTvProfile = simpleTvProfile(
                        SimpleTvSettings(enabled = true),
                        active = true,
                    ),
                    onNavigate = {},
                    content = {},
                )
            }
        }

        assertEquals(0, composeRule.onAllNodesWithText("Channels").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Guide").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Recordings").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Settings").fetchSemanticsNodes().size)
        assertEquals(
            1,
            composeRule.onAllNodesWithText("Exit Simple TV").fetchSemanticsNodes().size,
        )
    }
}
