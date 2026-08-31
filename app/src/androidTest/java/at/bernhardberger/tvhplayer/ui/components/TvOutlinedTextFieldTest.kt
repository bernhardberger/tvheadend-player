package at.bernhardberger.tvhplayer.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun field_entersEditing_onlyAfterOkPressed() {
        composeTestRule.setContent {
            var editingId by remember { mutableStateOf<String?>(null) }
            var value by remember { mutableStateOf("") }
            var parentBackCount by remember { mutableStateOf(0) }
            TVHeadendPlayerTheme {
                BackHandler { parentBackCount += 1 }
                Column {
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
        dispatchBack()
        composeTestRule.onNodeWithTag("state").assertTextEquals("editing=none")
        composeTestRule.onNodeWithTag("parent-back-state").assertTextEquals("parentBack=0")

        dispatchBack()
        composeTestRule.onNodeWithTag("parent-back-state").assertTextEquals("parentBack=1")
    }

    @Test
    fun passwordValueIsMaskedByDefault() {
        composeTestRule.setContent {
            var editingId by remember { mutableStateOf<String?>(null) }
            var password by remember { mutableStateOf(FAKE_PASSWORD) }
            TVHeadendPlayerTheme {
                TvPasswordField(
                    id = "password",
                    editingId = editingId,
                    setEditingId = { editingId = it },
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.testTag("password"),
                )
            }
        }

        composeTestRule.onNodeWithTag("password")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString("\u2022".repeat(FAKE_PASSWORD.length)),
                )
            )
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    }

    @Test
    fun passwordEditingConsumesBackBeforeTheParentOwner() {
        composeTestRule.setContent {
            var editingId by remember { mutableStateOf<String?>(null) }
            var parentBackCount by remember { mutableStateOf(0) }
            TVHeadendPlayerTheme {
                BackHandler { parentBackCount += 1 }
                Column {
                    TvPasswordField(
                        id = "password",
                        editingId = editingId,
                        setEditingId = { editingId = it },
                        value = FAKE_PASSWORD,
                        onValueChange = {},
                        modifier = Modifier.testTag("password"),
                    )
                    Text(
                        text = "editing=${editingId ?: "none"}",
                        modifier = Modifier.testTag("password-editing-state"),
                    )
                    Text(
                        text = "parentBack=$parentBackCount",
                        modifier = Modifier.testTag("password-parent-back-state"),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("password").requestFocus().performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.onNodeWithTag("password-editing-state")
            .assertTextEquals("editing=password")

        dispatchBack()
        composeTestRule.onNodeWithTag("password-editing-state").assertTextEquals("editing=none")
        composeTestRule.onNodeWithTag("password-parent-back-state")
            .assertTextEquals("parentBack=0")
    }

    private fun dispatchBack() {
        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    private companion object {
        const val FAKE_PASSWORD = "fake password"
    }
}
