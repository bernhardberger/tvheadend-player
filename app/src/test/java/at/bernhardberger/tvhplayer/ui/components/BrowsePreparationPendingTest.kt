package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class BrowsePreparationPendingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theSkeletonStaysWhileTheAnchorKeepsFocusAfterTheScreenIsReady() {
        var ready by mutableStateOf(false)
        var canTakeFocus by mutableStateOf(false)
        val content = FocusRequester()
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                BrowsePreparationPending(
                    PaddingValues(), initialFocusEnabled = true, ready = ready,
                    onReadyFocus = { canTakeFocus && runCatching { content.requestFocus() }.getOrDefault(false) },
                    handoffKey = canTakeFocus,
                    skeleton = { Box(Modifier.fillMaxSize().testTag("skeleton")) },
                )
                if (ready) Box(Modifier.size(10.dp).testTag("content").focusRequester(content).focusable())
            }
        }
        compose.onNodeWithTag("browse-preparing").assertIsFocused()
        compose.onNodeWithTag("skeleton", useUnmergedTree = true).assertIsDisplayed()
        // Ready, but the screen cannot take focus yet: keys still reach the anchor, so it stays visible.
        ready = true
        compose.waitForIdle()
        compose.onNodeWithTag("browse-preparing").assertIsFocused()
        compose.onNodeWithTag("skeleton", useUnmergedTree = true).assertIsDisplayed()
        canTakeFocus = true
        compose.waitForIdle()
        compose.onNodeWithTag("content").assertIsFocused()
        compose.onNodeWithTag("browse-preparing").assertDoesNotExist()
    }
}
