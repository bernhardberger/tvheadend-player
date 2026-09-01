package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import at.bernhardberger.tvhplayer.ui.AppDestination
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
                    currentRoute = AppDestination.CHANNELS,
                    showEpgMenu = true,
                    availableDestinations = setOf(AppDestination.UNLOCK),
                    onRootBack = {},
                    onNavigate = {},
                    content = { _, _ -> },
                )
            }
        }

        assertEquals(0, composeRule.onAllNodesWithTag("nav-channels").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("nav-epg").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("nav-recordings").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("nav-settings").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithTag("nav-unlock").fetchSemanticsNodes().size)
    }
}
