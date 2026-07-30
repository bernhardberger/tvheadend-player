package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompactTuningStatusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longTuningStatusStaysOneRowCompactAndPassiveAt960By540() {
        val visible = mutableStateOf(false)
        val sentinelFocus = FocusRequester()
        composeRule.setContent {
            TVHeadendPlayerTheme {
                Box(Modifier.size(width = 960.dp, height = 540.dp)) {
                    Box(
                        modifier = Modifier
                            .focusRequester(sentinelFocus)
                            .focusable()
                            .testTag("compact-tuning-focus-sentinel"),
                    )
                    CompactTuningStatus(
                        visible = visible.value,
                        label = "Tuning an exceptionally long television channel name…",
                        modifier = Modifier.testTag("compact-tuning-host"),
                    )
                    LaunchedEffect(sentinelFocus) { sentinelFocus.requestFocus() }
                }
            }
        }

        composeRule.onNodeWithTag("compact-tuning-focus-sentinel").assertIsFocused()
        composeRule.runOnIdle { visible.value = true }
        composeRule.waitForIdle()

        val node = composeRule.onNodeWithTag("compact-tuning-host")
            .assertIsDisplayed()
            .assertHasNoClickAction()
            .fetchSemanticsNode()
        val maxHeightPx = with(composeRule.density) { 48.dp.toPx() }
        val maxWidthPx = with(composeRule.density) { 848.dp.toPx() }
        assertTrue(node.boundsInRoot.height <= maxHeightPx)
        assertTrue(node.boundsInRoot.width <= maxWidthPx)
        composeRule.onNodeWithTag("compact-tuning-surface", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        composeRule.onNodeWithTag("compact-tuning-live-region", useUnmergedTree = true)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                )
            )
        composeRule.onNodeWithTag("compact-tuning-focus-sentinel").assertIsFocused()
    }
}
