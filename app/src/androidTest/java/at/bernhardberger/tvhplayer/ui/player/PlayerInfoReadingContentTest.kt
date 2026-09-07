package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PlayerInfoReadingContentTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun readingFirstScrollsLongContentThenReachesCloseWithoutActivation() {
        var closes = 0
        rule.setContent {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            TVHeadendPlayerTheme {
                PlayerInfoReadingContent("Programme ".repeat(12), "Channel", "Synopsis. ".repeat(300), focus) {
                    OutlinedButton(onClick = { closes++ }) { Text("Close info") }
                }
            }
        }
        rule.onNodeWithTag("player-info-reading").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter); pressKey(Key.DirectionDown) }
        rule.onNodeWithTag("player-info-reading").assertIsFocused()
        repeat(100) { rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
        rule.onNodeWithText("Close info").assertIsFocused()
        rule.runOnIdle { assertEquals(0, closes) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithTag("player-info-reading").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithText("Close info").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        rule.runOnIdle { assertEquals(1, closes) }
    }
}
