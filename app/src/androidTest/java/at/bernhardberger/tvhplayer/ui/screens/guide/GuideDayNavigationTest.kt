package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    private val zone = ZoneId.of("UTC")
    private val openedAtSec = LocalDateTime.of(2026, 2, 10, 12, 0)
        .atZone(zone)
        .toEpochSecond()
    private val bounds = guideWindowBounds(openedAtSec, zone)

    @Test
    fun earliestWindowFocusesTodayAndJumpsToTheNextCalendarDay() {
        var jumpedTo: Long? = null
        composeRule.setContent {
            TVHeadendPlayerTheme {
                GuideTimePanel(
                    windowStartSec = bounds.earliestStartSec,
                    bounds = bounds,
                    zoneId = zone,
                    nowSecProvider = { openedAtSec },
                    onJump = { jumpedTo = it },
                    onNow = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("guide-time-day-0").assertIsFocused().assertIsSelected()
        composeRule.onNodeWithTag("guide-time-day-1")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals(bounds.earliestStartSec + 24 * 3600L, jumpedTo)
        }
    }

    @Test
    fun latestWindowEndsTheDaysAndTimesAtTheBounds() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                GuideTimePanel(
                    windowStartSec = bounds.latestStartSec,
                    bounds = bounds,
                    zoneId = zone,
                    nowSecProvider = { openedAtSec },
                    onJump = {},
                    onNow = {},
                    onClose = {},
                )
            }
        }

        // Opens on the final day, scrolled to it; the day after the horizon is not offered.
        composeRule.onNodeWithTag("guide-time-day-7").assertIsFocused().assertIsSelected()
        composeRule.onAllNodesWithTag("guide-time-day-8").assertCountEquals(0)
        // The final window starts at 09:00, so the times from Midday on are past the bound.
        composeRule.onNodeWithTag("guide-time-preset-12").assertIsNotEnabled()
    }
}
