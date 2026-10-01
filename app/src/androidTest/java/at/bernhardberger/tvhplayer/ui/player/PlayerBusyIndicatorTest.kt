package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class PlayerBusyIndicatorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bufferingIsACentredPassiveOverlayNotAPauseButtonState() {
        var busy by mutableStateOf<PlayerBusyStatus?>(null)
        compose.setContent { TVHeadendPlayerTheme {
            Box(Modifier.fillMaxSize().testTag("video")) {
                RecordingChromeFixture()
                PlayerBusyIndicator(busy, Modifier.align(Alignment.Center))
            }
        } }
        val ring = compose.onNodeWithTag("player-busy-indicator", useUnmergedTree = true)
        ring.assertDoesNotExist()
        val before = compose.onNodeWithTag("player-pause").fetchSemanticsNode()
        compose.runOnIdle { busy = PlayerBusyStatus.BUFFERING }
        compose.waitForIdle()
        val indicator = ring.fetchSemanticsNode()
        assertEquals(compose.onNodeWithTag("video").fetchSemanticsNode().boundsInRoot.center, indicator.boundsInRoot.center)
        assertEquals(LiveRegionMode.Polite, indicator.config[SemanticsProperties.LiveRegion])
        assertFalse(indicator.config.contains(SemanticsProperties.Focused))
        compose.onNodeWithTag("player-pause-busy", useUnmergedTree = true).assertDoesNotExist()
        val pause = compose.onNodeWithTag("player-pause").fetchSemanticsNode()
        assertEquals(before.boundsInRoot, pause.boundsInRoot)
        assertEquals(before.config[SemanticsProperties.ContentDescription], pause.config[SemanticsProperties.ContentDescription])
        assertFalse(pause.config.contains(SemanticsProperties.LiveRegion))
        compose.runOnIdle { busy = null }
        compose.waitForIdle()
        ring.assertDoesNotExist()
    }
}
