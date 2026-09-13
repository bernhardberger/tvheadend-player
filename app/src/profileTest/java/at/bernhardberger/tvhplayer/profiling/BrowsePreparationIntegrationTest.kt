package at.bernhardberger.tvhplayer.profiling

import androidx.activity.compose.setContent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import at.bernhardberger.tvhplayer.ui.components.rememberPreparedBrowseData
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class BrowsePreparationIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<JourneyProfileActivity>()

    @Test fun rightIntoActualGuideWhilePreparingEntersScopeBeforeProgramme() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            compose.waitUntilAtLeastOneExists(hasText("Offline channel 1", substring = true), 15_000)
            // Occupy the real preparation lane through its public composable, then
            // recreate the existing production-screen fixture. No production gate
            // or fake Guide controller is introduced for this test.
            compose.activityRule.scenario.onActivity { activity ->
                activity.setContent {
                    rememberPreparedBrowseData("test lane occupant", Unit) {
                        started.countDown()
                        assertTrue(release.await(20, TimeUnit.SECONDS))
                    }
                }
                activity.intent.putExtra("journey", "guide")
            }
            compose.waitUntil(5_000) { started.count == 0L }
            compose.activityRule.scenario.recreate()
            compose.waitUntilAtLeastOneExists(hasTestTag("browse-preparing"), 15_000)
            compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
            compose.onNodeWithText("Guide").assertIsFocused()
            compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNode(hasTestTag("browse-preparing") and isFocused()).assertIsFocused()
            release.countDown()
            compose.waitUntilAtLeastOneExists(hasText("All channels") and isFocused(), 5_000)
            compose.onNode(hasText("All channels") and isFocused()).assertIsSelected()
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitUntilAtLeastOneExists(
                hasContentDescription("Offline channel 1", substring = true) and isFocused(), 5_000,
            )
        } finally {
            release.countDown()
        }
    }
}
