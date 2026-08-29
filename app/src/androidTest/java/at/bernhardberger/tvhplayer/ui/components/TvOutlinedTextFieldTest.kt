package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Rule
import org.junit.Test

/**
 * Verifies the fix for issue #9: while navigating settings with the D-pad the field
 * must NOT enter editing (which is what triggers the soft keyboard). Editing only
 * starts after the user confirms the field with OK / center.
 */
@OptIn(ExperimentalTestApi::class)
class TvOutlinedTextFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun field_entersEditing_onlyAfterOkPressed() {
        composeTestRule.setContent {
            var editingId by remember { mutableStateOf<String?>(null) }
            var value by remember { mutableStateOf("") }
            var parentBackCount by remember { mutableStateOf(0) }
            TVHeadendPlayerTheme {
                Column(
                    Modifier.onKeyEvent { event ->
                        if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                            parentBackCount += 1
                            true
                        } else {
                            false
                        }
                    }
                ) {
                    TvOutlinedTextField(
                        id = "host",
                        editingId = editingId,
                        setEditingId = { editingId = it },
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("Host") },
                        modifier = Modifier.testTag("field")
                    )
                    Text(
                        text = "editing=${editingId ?: "none"}",
                        modifier = Modifier.testTag("state")
                    )
                    Text(
                        text = "parentBack=$parentBackCount",
                        modifier = Modifier.testTag("parent-back-state")
                    )
                }
            }
        }

        // Focusing the field (D-pad navigation) must not start editing.
        composeTestRule.onNodeWithTag("state").assertTextEquals("editing=none")
        composeTestRule.onNodeWithTag("field").requestFocus()
        composeTestRule.onNodeWithTag("state").assertTextEquals("editing=none")

        // Pressing OK / center enters editing (only now would the keyboard show).
        composeTestRule.onNodeWithTag("field").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.onNodeWithTag("state").assertTextEquals("editing=host")

        // Back leaves editing again.
        composeTestRule.onNodeWithTag("field").performKeyInput {
            pressKey(Key.Back)
        }
        composeTestRule.onNodeWithTag("state").assertTextEquals("editing=none")
        composeTestRule.onNodeWithTag("parent-back-state").assertTextEquals("parentBack=0")
    }

    @Test
    fun configuredCredentialMarkersDisappearForEditingWithoutChangingValues() {
        composeTestRule.setContent {
            var editingId by remember { mutableStateOf<String?>(null) }
            var username by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            TVHeadendPlayerTheme {
                Column {
                    TvOutlinedTextField(
                        id = "username",
                        editingId = editingId,
                        setEditingId = { editingId = it },
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.testTag("username"),
                        presentationValue = "Configured",
                    )
                    TvPasswordField(
                        id = "password",
                        editingId = editingId,
                        setEditingId = { editingId = it },
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.testTag("password"),
                        presentationValue = "Configured",
                    )
                    Text(
                        text = "values=${username.length}:${password.length}",
                        modifier = Modifier.testTag("credential-state"),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("username").assertTextEquals("Configured")
        composeTestRule.onNodeWithTag("password")
            .assertTextEquals("Configured")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Password))

        composeTestRule.onNodeWithTag("username").requestFocus().performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.onNodeWithTag("username").assertTextEquals("")
        composeTestRule.onNodeWithTag("credential-state").assertTextEquals("values=0:0")

        composeTestRule.onNodeWithTag("username").performKeyInput { pressKey(Key.Back) }
        composeTestRule.onNodeWithTag("password").requestFocus().performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.onNodeWithTag("password").assertTextEquals("")
        composeTestRule.onNodeWithTag("password")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        composeTestRule.onNodeWithTag("credential-state").assertTextEquals("values=0:0")
    }
}
