package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
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
        composeRule.setContent {
            TVHeadendPlayerTheme {
                Box(Modifier.size(width = 960.dp, height = 540.dp)) {
                    CompactTuningStatus(
                        visible = true,
                        label = "Tuning an exceptionally long television channel name…",
                        modifier = Modifier.testTag("compact-tuning-host"),
                    )
                }
            }
        }

        val node = composeRule.onNodeWithTag("compact-tuning-host")
            .assertIsDisplayed()
            .assertHasNoClickAction()
            .fetchSemanticsNode()
        val maxHeightPx = with(composeRule.density) { 48.dp.toPx() }
        val maxWidthPx = with(composeRule.density) { 848.dp.toPx() }
        assertTrue(node.boundsInRoot.height <= maxHeightPx)
        assertTrue(node.boundsInRoot.width <= maxWidthPx)
    }
}
