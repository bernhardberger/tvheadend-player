package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.guideWindowBounds
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuideDayNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("UTC")
    private val openedAtSec = LocalDateTime.of(2026, 2, 10, 12, 0)
        .atZone(zone)
        .toEpochSecond()
    private val bounds = guideWindowBounds(openedAtSec, zone)

    @Test
    fun earliestWindowFocusesNowAndCommitsTheNextCalendarDay() {
        var jumpedTo: Long? = null
        composeRule.setContent {
            TVHeadendPlayerTheme {
                JumpToTimeDialog(
                    initialSec = bounds.earliestStartSec,
                    bounds = bounds,
                    zoneId = zone,
                    nowSecProvider = { openedAtSec },
                    onDismiss = {},
                    onJump = { jumpedTo = it },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.now)).assertIsFocused()
        composeRule.onNodeWithText(context.getString(R.string.previous_day)).assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.next_day))
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.epg_jump_action))
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals(openedAtSec + 24 * 3600L, jumpedTo)
        }
    }

    @Test
    fun latestWindowDisablesForwardDayAndHourNavigation() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                JumpToTimeDialog(
                    initialSec = bounds.latestStartSec,
                    bounds = bounds,
                    zoneId = zone,
                    nowSecProvider = { openedAtSec },
                    onDismiss = {},
                    onJump = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.next_day)).assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.next_hour)).assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.previous_day)).assertIsEnabled()
    }
}
