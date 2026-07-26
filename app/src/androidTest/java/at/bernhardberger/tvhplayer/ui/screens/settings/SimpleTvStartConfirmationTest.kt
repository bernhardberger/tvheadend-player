package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SimpleTvStartConfirmationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmationIsVisibleSafeFocusedAndActionable() {
        var confirmed = false

        composeRule.setContent {
            TVHeadendPlayerTheme {
                SimpleTvStartConfirmation(
                    onCancel = {},
                    onConfirm = { confirmed = true },
                )
            }
        }

        composeRule.onNodeWithText("Start Simple TV now?").assertIsDisplayed()
        composeRule.onNodeWithText("Back").assertIsFocused()
        composeRule.onNodeWithText("Start Simple TV now").performClick()
        assertTrue(confirmed)
    }
}
