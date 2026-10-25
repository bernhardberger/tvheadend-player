package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgrammeHeadlineTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultGuideTimingStaysOnOneLine() {
        val timing = "Wednesday, October 28, 2026 • 10:00 AM – 12:00 PM"
        compose.setContent {
            MaterialTheme {
                ProgrammeHeadline(eyebrow = {}, title = "Programme", tagPrefix = "guide-headline",
                    synopsisLines = 0, timing = timing, modifier = Modifier.width(160.dp))
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(timing).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
    }
}
