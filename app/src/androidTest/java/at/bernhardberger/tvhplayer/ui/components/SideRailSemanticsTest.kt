package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvhplayer.core.simpleTvProfile
import at.bernhardberger.tvhplayer.ui.Routes
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Rule
import org.junit.Test

class SideRailSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collapsedRailIconsExposeDestinationNames() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SideRail(
                    currentRoute = Routes.CHANNELS,
                    showEpgMenu = true,
                    simpleTvProfile = simpleTvProfile(
                        SimpleTvSettings(),
                        active = false,
                    ),
                    onNavigate = {},
                    content = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Home").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Channels").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Guide").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Recordings").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Settings").assertCountEquals(1)
    }

    @Test
    fun closedRailReservesItsWidthBeforeBrowseContent() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SideRail(
                    currentRoute = Routes.HOME,
                    showEpgMenu = true,
                    onNavigate = {},
                    content = { Text("Browse content", Modifier.testTag("content")) },
                )
            }
        }

        composeRule.onNodeWithTag("content").assertLeftPositionInRootIsEqualTo(80.dp)
    }
}
