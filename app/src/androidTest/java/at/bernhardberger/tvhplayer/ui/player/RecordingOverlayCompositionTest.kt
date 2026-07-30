package at.bernhardberger.tvhplayer.ui.player

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.media3.common.C
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import coil3.ImageLoader
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecordingOverlayCompositionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recordingHeaderUsesSameSlotOrderAsLiveOverlay() {
        setRecordingOverlay("Recording title")

        val channel = bounds("recording-channel-identity")
        val title = bounds("recording-programme-title")
        val subtitle = bounds("recording-subtitle")
        val clock = bounds("recording-clock")
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val sidePaddingPx = with(composeRule.density) { TvOverlaySidePadding.toPx() }

        assertEquals(root.left + sidePaddingPx, bounds("recording-picon").left, 1f)
        assertEquals(root.right - sidePaddingPx, clock.right, 1f)
        assertTrue(channel.bottom <= title.top)
        assertTrue(title.bottom <= subtitle.top)
        assertEquals(channel.top, clock.top, 1f)
    }

    @Test
    fun recordingHeaderKeepsItsAnchorsWhenTheTitleWraps() {
        val title = mutableStateOf("Short title")
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                RecordingOverlayControls(
                    imageLoader = imageLoader,
                    piconPath = null,
                    title = title.value,
                    subtitle = null,
                    channelName = "Channel",
                    positionMs = 30_000L,
                    durationMs = 60_000L,
                    growing = false,
                    nowSec = 5_400L,
                    isPlaying = true,
                    controlsVisible = true,
                    optionsOpen = false,
                    onTogglePlayPause = {},
                    onSeek = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    showStop = true,
                    onOpenOptions = {},
                    onOpenInfo = {},
                )
            }
        }
        composeRule.waitForIdle()
        val shortEyebrow = bounds("recording-channel-identity")
        val shortPicon = bounds("recording-picon")

        composeRule.runOnIdle {
            title.value = "A deliberately long recording title that wraps onto a second line " +
                "without moving the header anchors"
        }
        composeRule.waitForIdle()
        val longEyebrow = bounds("recording-channel-identity")
        val longPicon = bounds("recording-picon")
        val clock = bounds("recording-clock")

        assertEquals(shortEyebrow.top, longEyebrow.top, 1f)
        assertEquals(shortPicon.top, longPicon.top, 1f)
        assertEquals(longEyebrow.top, clock.top, 1f)
    }

    @Test
    fun recordingActionsFormOneClusterWithSeparatedStop() {
        setRecordingOverlay("Recording title")

        val transport = bounds("recording-transport-actions")
        val utilities = bounds("recording-utility-actions")
        val terminal = bounds("recording-terminal-actions")

        assertTrue(transport.right < utilities.left)
        assertTrue(utilities.right + 8f < terminal.left)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun recordingContextLabelAppearsOnlyForAFocusedNonObviousAction() {
        setRecordingOverlay("Recording title")

        val actionsBefore = bounds("recording-actions")
        composeRule.onNodeWithTag("player-action-context-label").assertDoesNotExist()
        composeRule.onNodeWithTag("recording-play-pause").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithContentDescription("+30 seconds").assertIsFocused()
        composeRule.onNodeWithTag("player-action-context-label").assertDoesNotExist()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithContentDescription("Info").assertIsFocused()
        composeRule.onNodeWithTag("player-action-context-label").assertDoesNotExist()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("recording-playback-options").assertIsFocused()
        composeRule.onNodeWithTag("player-action-context-label").assertExists()
        composeRule.onNodeWithText("Playback options", useUnmergedTree = true).assertExists()
        val actionsAfter = bounds("recording-actions")
        val timeline = bounds("recording-duration-status")
        val contextLabel = bounds("player-action-context-label")
        assertEquals(actionsBefore, actionsAfter)
        assertTrue(timeline.bottom <= contextLabel.top)
        assertTrue(contextLabel.bottom <= actionsAfter.top)

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("player-action-context-label").assertDoesNotExist()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun germanContextLabelFitsTheGapAndKeepsOnlyTheIconAccessibleAtLargeText() {
        setRecordingOverlay(
            title = "Eine außergewöhnlich lange deutschsprachige Aufnahme",
            german = true,
            fontScale = 1.3f,
        )

        composeRule.onNodeWithTag("recording-play-pause").requestFocus()
        composeRule.onRoot().performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionRight)
        }

        val options = composeRule.onNodeWithTag("recording-playback-options")
        val contextLabel = composeRule.onNodeWithTag("player-action-context-label")
        val labelBounds = contextLabel.fetchSemanticsNode().boundsInRoot
        val timeline = bounds("recording-duration-status")
        val actions = bounds("recording-actions")
        options.assertIsFocused().assertContentDescriptionEquals("Wiedergabeoptionen")
        composeRule.onNodeWithText("Wiedergabeoptionen", useUnmergedTree = true).assertExists()
        contextLabel.assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility),
        )
        assertTrue(timeline.bottom <= labelBounds.top)
        assertTrue(labelBounds.bottom <= actions.top)
        assertTrue(labelBounds.left >= actions.left)
        assertTrue(labelBounds.right <= actions.right)
    }

    private fun setRecordingOverlay(
        title: String,
        german: Boolean = false,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            val configuredConfiguration = remember(configuration, german) {
                Configuration(configuration).apply {
                    if (german) setLocale(Locale.GERMAN)
                }
            }
            val configuredContext = remember(context, configuredConfiguration) {
                context.createConfigurationContext(configuredConfiguration)
            }
            CompositionLocalProvider(
                LocalContext provides configuredContext,
                LocalConfiguration provides configuredConfiguration,
                LocalResources provides configuredContext.resources,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                val imageLoader = ImageLoader.Builder(LocalContext.current).build()
                TVHeadendPlayerTheme {
                    RecordingOverlayControls(
                        imageLoader = imageLoader,
                        piconPath = null,
                        title = title,
                        subtitle = "Episode subtitle",
                        channelName = "Channel",
                        positionMs = 30_000L,
                        durationMs = C.TIME_UNSET,
                        growing = true,
                        nowSec = 5_400L,
                        isPlaying = true,
                        controlsVisible = true,
                        optionsOpen = false,
                        onTogglePlayPause = {},
                        onSeek = {},
                        onStopPlayback = {},
                        onUserInteraction = {},
                        showStop = true,
                        onOpenOptions = {},
                        onOpenInfo = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String) =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
}
