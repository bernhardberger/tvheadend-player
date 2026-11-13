package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class GuidedStepActionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disabledActionKeepsNativeDirectionalFocusWithoutActivation() {
        var activations = 0
        compose.setContent {
            TVHeadendPlayerTheme {
                val first = remember { FocusRequester() }
                LaunchedEffect(first) { first.requestFocus() }
                Column {
                    GuidedStepAction("First", {}, Modifier.focusRequester(first).testTag("first"))
                    GuidedStepAction("Unavailable", { activations++ }, Modifier.testTag("disabled"), enabled = false)
                    GuidedStepAction("Last", {}, Modifier.testTag("last"))
                }
            }
        }
        compose.onNodeWithTag("first").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("disabled").assertIsFocused().assertIsNotEnabled()
        compose.pressKey(Key.DirectionCenter)
        assertEquals(0, activations)
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("last").assertIsFocused()
    }
}
