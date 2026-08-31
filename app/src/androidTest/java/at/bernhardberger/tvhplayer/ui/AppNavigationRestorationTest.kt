package at.bernhardberger.tvhplayer.ui

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppNavigationRestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun processRecreationRestoresTopLevelNestedSettingsAndTransientKeys() {
        val restorationTester = StateRestorationTester(composeRule)
        lateinit var backStack: MutableList<AppNavKey>
        restorationTester.setContent {
            backStack = rememberAppNavBackStack(ChannelsKey)
        }
        composeRule.runOnIdle {
            backStack.navigateTopLevel(FilteredGuideKey(ProgrammeCategory.NEWS))
            backStack.navigateTopLevel(SettingsKey(SettingsSection.PLAYER))
            backStack.pushTransient(LivePlayerKey(channelId = 42, channelName = "News / HD"))
        }
        val expected = backStack.toList()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle { assertEquals(expected, backStack.toList()) }
    }
}
