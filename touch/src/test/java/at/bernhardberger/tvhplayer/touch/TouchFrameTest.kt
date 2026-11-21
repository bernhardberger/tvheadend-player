package at.bernhardberger.tvhplayer.touch

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.TouchFrame
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The frame with all three destinations, as drawn; the real app offers only those it has wired. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TouchFrameTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val destination = hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun expandedFrame() {
        show()
        assertChannelsFrame(vertical = true)
        compose.captureTouch("slice0", "tablet-landscape", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = "en-rUS-w800dp-h1280dp-port-xhdpi")
    fun mediumFrame() {
        show()
        assertChannelsFrame(vertical = false)
        compose.captureTouch("slice0", "tablet-upright", TouchCanvas.TABLET_UPRIGHT)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun compactFrameNavigatesAndRestoresSelection() {
        val restoration = StateRestorationTester(compose)
        show(restoration)
        assertChannelsFrame(vertical = false)
        compose.captureTouch("slice0", "phone", TouchCanvas.PHONE)

        compose.onNode(destination and hasText("Guide")).performClick()
        compose.onNode(heading and hasText("Guide")).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(destination and hasText("Guide")).assertIsSelected()
        compose.onNode(heading and hasText("Guide")).assertIsDisplayed()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(destination and hasText("Channels")).assertIsSelected()
        compose.onNode(heading and hasText("Channels")).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun singleDestinationLeavesThePhoneBarOut() {
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) { TouchFrame(destinations = listOf(Destination.Channels)) {} }
        }
        compose.onNode(destination).assertDoesNotExist()
        compose.onNode(heading and hasText("Channels")).assertIsDisplayed()
    }

    private fun show(restoration: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            MaterialTheme(colorScheme = MobileDarkColors) {
                TouchFrame(
                    actions = { current ->
                        if (current == Destination.Channels) {
                            IconButton(onClick = {}, modifier = Modifier.padding(end = 4.dp)) {
                                Icon(
                                    painterResource(R.drawable.channels_settings),
                                    stringResource(R.string.channels_connection_settings),
                                )
                            }
                        }
                    },
                ) {}
            }
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
    }

    private fun assertChannelsFrame(vertical: Boolean) {
        val labels = compose.onAllNodes(destination).fetchSemanticsNodes()
            .sortedBy { if (vertical) it.boundsInRoot.top else it.boundsInRoot.left }
            .map { it.config.getOrNull(SemanticsProperties.Text)?.single()?.text }
        assertEquals(listOf("Channels", "Guide", "Recordings"), labels)
        compose.onNode(destination and hasText("Channels")).assertIsSelected()
        compose.onNode(heading and hasText("Channels")).assertIsDisplayed()
    }
}
