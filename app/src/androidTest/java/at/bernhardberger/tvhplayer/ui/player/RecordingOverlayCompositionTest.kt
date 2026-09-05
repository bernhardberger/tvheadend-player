package at.bernhardberger.tvhplayer.ui.player

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.test.assertIsDisplayed
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
        val title = bounds("recording-title")
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
                    canSeek = true,
                    controlsVisible = true,
                    optionsOpen = false,
                    onTogglePlayPause = {},
                    onSeek = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
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
    fun recordingKeepsTheRecordSlotEmptyAndActionsAboveTheTimeline() {
        setRecordingOverlay("Recording title")

        val info = bounds("player-info")
        val settings = bounds("player-settings")
        val stop = bounds("player-stop")
        assertTrue(info.right < settings.left)
        assertTrue(stop.left - settings.right >= settings.width)
        assertTrue(bounds("recording-actions").bottom <= bounds("recording-duration-status").top)
        composeRule.onNodeWithTag("player-record").assertDoesNotExist()
        composeRule.onNodeWithTag("player-go-live").assertDoesNotExist()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun recordingContextLabelAppearsOnlyForAFocusedNonObviousAction() {
        setRecordingOverlay("Recording title")

        val actionsBefore = bounds("recording-actions")
        composeRule.onNodeWithTag("player-info").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-settings").assertIsFocused()
        composeRule.onNodeWithTag("player-action-context-label").assertExists()
        composeRule.onNodeWithText("Settings", useUnmergedTree = true).assertExists()
        val actionsAfter = bounds("recording-actions")
        val timeline = bounds("recording-duration-status")
        val contextLabel = bounds("player-action-context-label")
        assertEquals(actionsBefore, actionsAfter)
        assertTrue(actionsAfter.bottom <= timeline.top)
        assertTrue(contextLabel.top >= actionsAfter.top)

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("player-info").assertIsFocused()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun germanContextLabelFitsTheGapAndKeepsOnlyTheIconAccessibleAtLargeText() {
        setRecordingOverlay(
            title = "Eine außergewöhnlich lange deutschsprachige Aufnahme",
            german = true,
            fontScale = 1.3f,
        )

        composeRule.onNodeWithTag("player-info").requestFocus()
        composeRule.onRoot().performKeyInput {
            pressKey(Key.DirectionRight)
        }

        val options = composeRule.onNodeWithTag("player-settings")
        val contextLabel = composeRule.onNodeWithTag("player-action-context-label")
        val labelBounds = contextLabel.fetchSemanticsNode().boundsInRoot
        val timeline = bounds("recording-duration-status")
        val actions = bounds("recording-actions")
        options.assertIsFocused().assertContentDescriptionEquals("Einstellungen")
        composeRule.onNodeWithText("Einstellungen", useUnmergedTree = true).assertExists()
        contextLabel.assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility),
        )
        assertTrue(actions.bottom <= timeline.top)
        assertTrue(labelBounds.top >= actions.top)
        assertTrue(labelBounds.left >= actions.left)
        assertTrue(labelBounds.right <= actions.right)
    }

    @Test
    fun returningFromInfoRestoresInfoWithoutBouncingBackToTimeline() {
        var restoreInfo by mutableStateOf(false)
        setRecordingOverlay(
            title = "Recording",
            durationMs = 600_000L,
            restoreInfoFocus = { restoreInfo },
            onInfoFocusRestored = { restoreInfo = false },
        )
        composeRule.onNodeWithTag("recording-seekbar").assertIsFocused()
        composeRule.runOnIdle { restoreInfo = true }
        composeRule.onNodeWithTag("player-info").assertIsFocused()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-info").assertIsFocused()
    }

    @Test
    fun knownDurationWithoutSeekCapabilityIsPassiveAndStartsOnInfo() {
        setRecordingOverlay(title = "Recording", durationMs = 600_000L, canSeek = false)
        composeRule.onNodeWithTag("recording-seekbar").assertDoesNotExist()
        composeRule.onNodeWithTag("recording-duration-status").assertIsDisplayed()
        composeRule.onNodeWithTag("player-info").assertIsFocused()
    }

    private fun setRecordingOverlay(
        title: String,
        german: Boolean = false,
        fontScale: Float = 1f,
        durationMs: Long = C.TIME_UNSET,
        canSeek: Boolean = true,
        restoreInfoFocus: () -> Boolean = { false },
        onInfoFocusRestored: () -> Unit = {},
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
                        durationMs = durationMs,
                        growing = true,
                        nowSec = 5_400L,
                        canSeek = canSeek,
                        controlsVisible = true,
                        optionsOpen = false,
                        onTogglePlayPause = {},
                        onSeek = {},
                        onStopPlayback = {},
                        onUserInteraction = {},
                        onOpenOptions = {},
                        onOpenInfo = {},
                        restoreInfoFocus = restoreInfoFocus(),
                        onInfoFocusRestored = onInfoFocusRestored,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String) =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
}
