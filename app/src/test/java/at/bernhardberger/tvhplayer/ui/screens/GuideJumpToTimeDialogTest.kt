package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.GuideWindowBounds
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.guide.JumpToTimeDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideJumpToTimeDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun englishJumpLabelsUseOneHour() = checkLabels("1 hour")

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun germanJumpLabelsUseOneHour() = checkLabels("1 Stunde")

    private fun checkLabels(hour: String) {
        compose.setContent {
            TVHeadendPlayerTheme {
                JumpToTimeDialog(7200, GuideWindowBounds(0, 14400), ZoneId.of("UTC"),
                    nowSecProvider = { 7200 }, onDismiss = {}, onJump = {})
            }
        }
        compose.onNodeWithText("−$hour").assertIsDisplayed()
        compose.onNodeWithText("+$hour").assertIsDisplayed()
        val context = ApplicationProvider.getApplicationContext<Application>()
        compose.onNodeWithText(context.getString(R.string.now)).assertIsDisplayed()
    }
}
