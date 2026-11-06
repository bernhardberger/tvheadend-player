package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The password field's keyboard and its "Show password" row, with the D-pad as the only input. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class TvPasswordFieldTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var view: View

    @Test
    fun theKeyboardIsAPasswordKeyboardWithoutSuggestionsShownOrHidden() {
        show(value = SECRET)
        compose.onNodeWithTag(FIELD).requestFocus()
        press(Key.DirectionCenter)
        assertPasswordKeyboard()
        assertFieldText(MASKED)
        back()
        pressUntilRowIsFocused()
        press(Key.DirectionCenter)
        assertFieldText(SECRET)
        compose.onNodeWithTag(FIELD).requestFocus()
        press(Key.DirectionCenter)
        assertPasswordKeyboard()
    }

    @Test
    fun okOnTheRowTogglesTheTextWithoutOpeningTheField() {
        show(value = SECRET)
        compose.onNodeWithTag(FIELD).requestFocus()
        press(Key.DirectionDown)
        compose.onNodeWithText(SHOW_PASSWORD).assertIsFocused().assertIsOff()
        assertFieldText(MASKED)

        press(Key.DirectionCenter)
        compose.onNodeWithText(SHOW_PASSWORD).assertIsFocused().assertIsOn()
        assertFieldText(SECRET)
        compose.onNodeWithTag("editing").assertTextEquals("none")

        press(Key.DirectionCenter)
        compose.onNodeWithText(SHOW_PASSWORD).assertIsFocused().assertIsOff()
        assertFieldText(MASKED)
        compose.onNodeWithTag("editing").assertTextEquals("none")
        assertEquals(SECRET, value)
    }

    @Test
    fun theRowIsOneCheckboxNode() {
        show(value = SECRET)
        val node = compose.onNodeWithText(SHOW_PASSWORD).fetchSemanticsNode()
        assertEquals(Role.Checkbox, node.config[SemanticsProperties.Role])
        compose.onNodeWithText(SHOW_PASSWORD).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
    }

    @Test
    fun theRowIsBetweenTheFieldAndWhatFollows() {
        show(value = SECRET)
        compose.onNodeWithTag(FIELD).requestFocus()
        press(Key.DirectionDown)
        compose.onNodeWithText(SHOW_PASSWORD).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("after").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithText(SHOW_PASSWORD).assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag(FIELD).assertIsFocused()
    }

    @Test
    fun theRowHasNothingToRevealWhileASavedPasswordIsShown() {
        show(value = "", presentationValue = SAVED)
        compose.onNodeWithTag(FIELD).requestFocus()
        assertFieldText(SAVED)
        compose.onNodeWithText(SHOW_PASSWORD).assertIsNotEnabled().assertIsOff()
        // The disabled row keeps its place but the D-pad passes over it.
        press(Key.DirectionDown)
        compose.onNodeWithTag("after").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag(FIELD).assertIsFocused()
    }

    @Test
    fun aReplacementPasswordEnablesTheRow() {
        show(value = "", presentationValue = SAVED)
        compose.onNodeWithTag(FIELD).requestFocus()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("editing").assertTextEquals("password-id")
        compose.runOnIdle { value = "replacement" }
        back()
        compose.onNodeWithText(SHOW_PASSWORD).assertIsOff()
        press(Key.DirectionDown)
        compose.onNodeWithText(SHOW_PASSWORD).assertIsFocused()
        press(Key.DirectionCenter)
        assertFieldText("replacement")
        // Emptied again, the saved password is back: it is not revealed, and nothing to toggle.
        compose.runOnIdle { value = "" }
        assertFieldText(SAVED)
        compose.onNodeWithText(SHOW_PASSWORD).assertIsNotEnabled().assertIsOff()
    }

    private var value by mutableStateOf("")

    private fun pressUntilRowIsFocused() {
        press(Key.DirectionDown)
        compose.onNodeWithText(SHOW_PASSWORD).assertIsFocused()
    }

    private fun assertPasswordKeyboard() {
        val info = EditorInfo()
        assertNotNull(compose.runOnIdle { view.onCreateInputConnection(info) })
        assertEquals(InputType.TYPE_CLASS_TEXT, info.inputType and InputType.TYPE_MASK_CLASS)
        assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD, info.inputType and InputType.TYPE_MASK_VARIATION)
        assertEquals(0, info.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        assertEquals(EditorInfo.IME_ACTION_DONE, info.imeOptions and EditorInfo.IME_MASK_ACTION)
    }

    /** The field's own text, as typed or masked; its merged Text is the label. */
    private fun assertFieldText(expected: String) {
        val text = compose.onNodeWithTag(FIELD).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals(expected, text)
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun show(value: String, presentationValue: String? = null) {
        this.value = value
        compose.setContent {
            view = LocalView.current
            var editingId by remember { mutableStateOf<String?>(null) }
            TVHeadendPlayerTheme {
                Column {
                    TvPasswordField(
                        id = "password-id",
                        editingId = editingId,
                        setEditingId = { editingId = it },
                        value = this@TvPasswordFieldTest.value,
                        onValueChange = { this@TvPasswordFieldTest.value = it },
                        presentationValue = presentationValue,
                        modifier = Modifier.testTag(FIELD),
                    )
                    Text("after", Modifier.testTag("after").focusable())
                    Text(editingId ?: "none", Modifier.testTag("editing"))
                }
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        const val FIELD = "password"
        const val SECRET = "fake password"
        const val MASKED = "•••••••••••••"
        const val SAVED = "Saved password"
        const val SHOW_PASSWORD = "Show password"
    }
}
