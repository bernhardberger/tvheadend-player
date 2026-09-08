package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    @Test
    fun recordingTrackKeepsAnchorAcrossFocusedAndHiddenPreviewAtLargeText() {
        val hidden = mutableStateOf(false)
        val previewing = mutableStateOf(false)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                TVHeadendPlayerTheme {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                        if (hidden.value) RecordingSeekPreview(
                            targetMs = 30_000, originMs = 60_000, durationMs = 5_400_000, growing = false,
                            modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                        ) else RecordingOverlayControls(
                            imageLoader = ImageLoader.Builder(LocalContext.current).build(),
                            piconPath = null, title = "A long recording title", subtitle = null, channelName = "Channel",
                            positionMs = 30_000, durationMs = 5_400_000, growing = false, nowSec = 1800,
                            canSeek = true, controlsVisible = true, optionsOpen = false,
                            onTogglePlayPause = {}, onSeek = {}, onStopPlayback = {}, onUserInteraction = {},
                            onOpenOptions = {}, onOpenInfo = {}, previewing = previewing.value,
                        )
                    }
                }
            }
        }
        fun track() = composeRule.onNodeWithTag("player-timeline-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val resting = track()
        composeRule.onNodeWithTag("player-pause").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("recording-seekbar").assertIsFocused()
        assertEquals(resting, track())
        composeRule.runOnIdle { previewing.value = true }
        assertEquals(resting, track())
        composeRule.runOnIdle { hidden.value = true }
        assertEquals(resting, track())
        composeRule.onNodeWithTag("recording-actions").assertDoesNotExist()
        composeRule.onNodeWithTag("player-seekbar-thumb", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("1:30:00", useUnmergedTree = true).assertIsDisplayed()
    }

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
        assertTrue(kotlin.math.abs(channel.top - clock.top) < with(composeRule.density) { 12.dp.toPx() })
        assertEquals(channel.left, title.left, 1f)
        assertEquals(with(composeRule.density) { 64.dp.toPx() }, bounds("recording-picon").height, 1f)
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
        assertTrue(kotlin.math.abs(longEyebrow.top - clock.top) < with(composeRule.density) { 12.dp.toPx() })
    }

    @Test
    fun recordingOmitsRecordWithoutAnEmptySlotAndKeepsTimelineAboveActions() {
        setRecordingOverlay("Recording title")

        val info = bounds("player-info")
        val settings = bounds("player-settings")
        val stop = bounds("player-stop")
        assertTrue(info.right < settings.left)
        assertTrue(settings.left - info.right < info.width)
        assertTrue(stop.right < info.left)
        assertTrue(bounds("recording-duration-status").bottom <= bounds("recording-actions").top)
        composeRule.onNodeWithTag("player-channels-cue").assertDoesNotExist()
        composeRule.onNodeWithTag("player-record").assertDoesNotExist()
        composeRule.onNodeWithTag("player-go-live").assertDoesNotExist()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun recordingUtilitiesRemainReachableWithoutFloatingCaptions() {
        setRecordingOverlay("Recording title")

        val actionsBefore = bounds("recording-actions")
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-stop").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-info").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-settings").assertIsFocused()
        composeRule.onNodeWithTag("player-action-context-label").assertDoesNotExist()
        composeRule.onNodeWithTag("player-settings").assertContentDescriptionEquals("Settings")
        val actionsAfter = bounds("recording-actions")
        val timeline = bounds("recording-duration-status")
        assertEquals(actionsBefore, actionsAfter)
        assertTrue(timeline.bottom <= actionsAfter.top)

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("player-info").assertIsFocused()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun germanUtilitiesRetainAccessibleNamesWithoutCaptionsAtLargeText() {
        setRecordingOverlay(
            title = "Eine außergewöhnlich lange deutschsprachige Aufnahme",
            german = true,
            fontScale = 1.5f,
        )

        composeRule.onNodeWithTag("player-info").requestFocus()
        composeRule.onRoot().performKeyInput {
            pressKey(Key.DirectionRight)
        }

        val options = composeRule.onNodeWithTag("player-settings")
        val contextLabel = composeRule.onNodeWithTag("player-action-context-label")
        val timeline = bounds("recording-duration-status")
        val actions = bounds("recording-actions")
        options.assertIsFocused().assertContentDescriptionEquals("Einstellungen")
        composeRule.onNodeWithText("Einstellungen", useUnmergedTree = true).assertDoesNotExist()
        contextLabel.assertDoesNotExist()
        assertTrue(timeline.bottom <= actions.top)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        composeRule.onNodeWithTag("recording-title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2, layouts.single().lineCount)
        assertTrue(layouts.single().getLineBottom(1) <= layouts.single().size.height)
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
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        composeRule.onNodeWithTag("recording-seekbar").requestFocus()
        composeRule.runOnIdle { restoreInfo = true }
        composeRule.onNodeWithTag("player-info").assertIsFocused()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-info").assertIsFocused()
    }

    @Test
    fun knownDurationWithoutSeekCapabilityIsPassiveAndStartsOnPause() {
        setRecordingOverlay(title = "Recording", durationMs = 600_000L, canSeek = false)
        composeRule.onNodeWithTag("recording-seekbar").assertDoesNotExist()
        composeRule.onNodeWithTag("recording-duration-status").assertIsDisplayed()
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
    }

    @Test
    fun longRecordingElapsedMatchesTotalPrecision() {
        setRecordingOverlay("Recording", durationMs = 5_400_000L)
        composeRule.onNodeWithText("0:00:30").assertIsDisplayed()
        composeRule.onNodeWithText("1:30:00").assertIsDisplayed()
        composeRule.onNodeWithText("0:30").assertDoesNotExist()
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
