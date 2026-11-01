package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.requestFocus
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A field opens when OK is released. On Gboard for TV the release of the press that opened a
 * field reached the keyboard, which sent the field's Next action and moved on to the next field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class TvTextFieldOpenKeyTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aTextFieldOpensOnlyWhenOkIsReleased() {
        showFields()
        compose.onNodeWithTag("text").requestFocus()
        assertOkOpensOnRelease("text-id", AndroidKeyEvent.KEYCODE_DPAD_CENTER)
    }

    @Test
    fun aPasswordFieldOpensOnlyWhenOkIsReleased() {
        showFields()
        compose.onNodeWithTag("password").requestFocus()
        assertOkOpensOnRelease("password-id", AndroidKeyEvent.KEYCODE_ENTER)
    }

    @Test
    fun aReleaseFromAPressThatStartedElsewhereOpensNothing() {
        showFields()
        compose.onNodeWithTag("text").requestFocus()
        key(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        assertEditing("none")
        compose.onNodeWithTag("password").requestFocus()
        key(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        assertEditing("none")
    }

    @Test
    fun aHeldOkThatReachesAFieldOnlyAsARepeatOpensNothing() {
        showFields()
        compose.onNodeWithTag("text").requestFocus()
        key(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("password").requestFocus()
        key(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_CENTER, repeat = 1)
        key(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        assertEditing("none")
    }

    @Test
    fun aPressEndedByLeavingTheFieldOpensNothingOnReturn() {
        showFields()
        compose.onNodeWithTag("text").requestFocus()
        key(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("password").requestFocus()
        compose.onNodeWithTag("text").requestFocus()
        key(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        assertEditing("none")
    }

    private fun assertOkOpensOnRelease(id: String, code: Int) {
        key(AndroidKeyEvent.ACTION_DOWN, code)
        assertEditing("none")
        key(AndroidKeyEvent.ACTION_DOWN, code, repeat = 1)
        assertEditing("none")
        key(AndroidKeyEvent.ACTION_UP, code)
        assertEditing(id)
    }

    private fun assertEditing(expected: String) {
        compose.onNodeWithTag("editing").assertTextEquals(expected)
    }

    private fun key(action: Int, code: Int, repeat: Int = 0) {
        compose.onRoot().performKeyPress(KeyEvent(AndroidKeyEvent(0L, 0L, action, code, repeat)))
        compose.waitForIdle()
    }

    private fun showFields() {
        compose.setContent {
            var editingId by remember { mutableStateOf<String?>(null) }
            TVHeadendPlayerTheme {
                Column {
                    TvOutlinedTextField(
                        id = "text-id",
                        editingId = editingId,
                        setEditingId = { editingId = it },
                        value = "",
                        onValueChange = {},
                        label = { Text("Text") },
                        modifier = Modifier.testTag("text"),
                    )
                    TvPasswordField(
                        id = "password-id",
                        editingId = editingId,
                        setEditingId = { editingId = it },
                        value = "",
                        onValueChange = {},
                        modifier = Modifier.testTag("password"),
                    )
                    Text(editingId ?: "none", Modifier.testTag("editing"))
                }
            }
        }
        compose.waitForIdle()
    }
}
