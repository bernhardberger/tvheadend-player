package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The confirmation card's width rule (a fixed column that grows only while its copy wraps badly) and its choices. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class ConfirmationDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fittingCopyKeepsTheBaseColumn() {
        assertEquals(352, confirmationColumnWidth(buttonRow = 300, base = 352, max = 520, step = 4) { false })
    }

    @Test fun aWiderButtonRowSetsTheColumn() {
        assertEquals(380, confirmationColumnWidth(buttonRow = 380, base = 352, max = 520, step = 4) { false })
    }

    @Test fun growsInStepsUntilTheCopyFits() {
        val width = confirmationColumnWidth(buttonRow = 0, base = 352, max = 520, step = 4) {
            assertTrue("No probe exceeds the maximum", it <= 520)
            it < 361
        }
        assertEquals(364, width)
    }

    @Test fun stopsAtTheMaximum() {
        assertEquals(520, confirmationColumnWidth(buttonRow = 0, base = 352, max = 520, step = 5) { true })
    }

    @Test fun aButtonRowWiderThanTheMaximumIsKept() {
        assertEquals(560, confirmationColumnWidth(buttonRow = 560, base = 352, max = 520, step = 4) { true })
    }

    @Test fun aLastLineOfOneWordIsAnOrphan() {
        assertTrue(endsOnOneWord("recordings?"))
        assertTrue(endsOnOneWord(" recorded. "))
        assertFalse(endsOnOneWord("won't be recorded."))
    }

    @Test fun theFirstChoiceWinsWhileTheCardFadesOut() {
        val outcomes = mutableListOf<String>()
        compose.setContent {
            TVHeadendPlayerTheme {
                ConfirmationDialog(
                    question = "Question?", subject = null, sentence = "Sentence.",
                    safe = ConfirmationChoice("Safe", R.drawable.ic_check, "safe"),
                    action = ConfirmationChoice("Action", R.drawable.ic_delete_outlined, "action"),
                    cancel = ConfirmationChoice("Cancel", R.drawable.ic_close, "cancel"),
                    onDismiss = { outcomes += "dismiss" },
                    onConfirm = { outcomes += "action" },
                    onSafeConfirm = { outcomes += "safe" },
                )
            }
        }
        compose.onNodeWithTag("safe").assertIsFocused()
        // The shell already swallows keys while the card fades out; an accessibility click still reaches the buttons
        // and must neither switch to the destructive choice nor undo the first.
        compose.mainClock.autoAdvance = false
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("action").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("cancel").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(listOf("safe"), outcomes)
    }
}
