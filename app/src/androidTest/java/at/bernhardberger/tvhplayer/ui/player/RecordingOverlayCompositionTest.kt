package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
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
    @OptIn(ExperimentalTestApi::class)
    fun markersConsumeOpeningAndCommitCyclesWithoutMovingTimelineOrTogglingPause() {
        val markers = mutableStateOf<List<Long>>(emptyList())
        val markerPosition = mutableStateOf(30_000L)
        val navigation = RecordingMarkerNavigation()
        val seeks = mutableListOf<Long>()
        var toggles = 0
        var ancestorBacks = 0
        var autoHides = 0
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                TVHeadendPlayerTheme {
                    PlayerControlsAutoHideEffect(
                        eligible = !navigation.open, interactionToken = 0, timeoutMillis = 5_000L,
                        onHide = { autoHides++ },
                    )
                    // RecordingPlayerScreen owns Back before its children. Exercise that ordering.
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()
                        .onPreviewKeyEvent { event ->
                            if (navigation.handle(event, markers.value) { seeks += it }) true
                            else if (event.key == Key.Back) {
                                if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) ancestorBacks++
                                true
                            } else false
                        }) {
                        RecordingOverlayControls(
                            imageLoader = ImageLoader.Builder(LocalContext.current).build(),
                            piconPath = null, title = "Recording with scene markers", subtitle = null, channelName = "Channel",
                            positionMs = 30_000, durationMs = 120_000, growing = false, nowSec = 1800,
                            canSeek = true, controlsVisible = true, optionsOpen = false, paused = true,
                            onTogglePlayPause = { toggles++ }, onSeek = { seeks += it }, onStopPlayback = {},
                            onUserInteraction = {}, onOpenOptions = {}, onOpenInfo = {},
                            markers = markers.value, markerNavigation = navigation, onSeekMarker = { seeks += it },
                            markerPositionMs = markerPosition.value,
                        )
                    }
                }
            }
        }
        fun track() = composeRule.onNodeWithTag("player-timeline-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val ordinaryTrack = track()
        val ordinaryActions = bounds("recording-actions")
        captureMarkerState("markers-absent-en-1.5x.png")
        composeRule.runOnIdle { markers.value = listOf(10_000L, 60_000L, 100_000L) }
        assertEquals(ordinaryTrack, track())
        composeRule.onAllNodesWithTag("recording-marker-tick", useUnmergedTree = true).assertCountEquals(3)
        composeRule.onNodeWithTag("player-pause").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("recording-seekbar").assertIsFocused()
        captureMarkerState("markers-ticks-en-1.5x.png")
        fun dispatch(code: Int, action: Int, repeat: Int = 0) {
            val time = android.os.SystemClock.uptimeMillis()
            assertTrue(composeRule.onRoot().performKeyPress(androidx.compose.ui.input.key.KeyEvent(
                android.view.KeyEvent(time, time, action, code, repeat),
            )))
        }
        val up = android.view.KeyEvent.KEYCODE_DPAD_UP
        dispatch(up, android.view.KeyEvent.ACTION_DOWN)
        composeRule.onNodeWithTag("recording-marker-target").assertIsFocused()
        dispatch(up, android.view.KeyEvent.ACTION_DOWN, 1)
        dispatch(up, android.view.KeyEvent.ACTION_UP)
        composeRule.onNodeWithTag("recording-marker-target").assertIsFocused()
        composeRule.onNodeWithText("1:00").assertIsDisplayed()
        val selectedLabel = bounds("recording-marker-target")
        assertTrue(kotlin.math.abs(selectedLabel.center.x - ordinaryTrack.center.x) < 2f)
        assertTrue(selectedLabel.bottom < ordinaryTrack.top)
        val previewFill = bounds("player-timeline-fill")
        assertTrue(kotlin.math.abs(previewFill.width - ordinaryTrack.width / 4f) < 2f)
        val indicator = bounds("recording-selected-marker")
        assertTrue(kotlin.math.abs(indicator.center.x - ordinaryTrack.center.x) < 2f)
        composeRule.onNodeWithTag("player-seekbar-thumb", useUnmergedTree = true).assertDoesNotExist()
        assertTrue(seeks.isEmpty())
        val markerActions = composeRule.onNodeWithTag("recording-marker-target")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Previous marker", "Next marker", "Close"), markerActions.map { it.label })
        composeRule.runOnIdle { markerActions[0].action(); markerActions[1].action() }
        assertTrue(seeks.isEmpty())
        composeRule.mainClock.advanceTimeBy(6_000L)
        assertEquals(0, autoHides)
        assertEquals(ordinaryTrack, track())
        assertEquals(ordinaryActions, bounds("recording-actions"))
        captureMarkerState("markers-open-en-1.5x.png")
        val enter = android.view.KeyEvent.KEYCODE_ENTER
        dispatch(enter, android.view.KeyEvent.ACTION_DOWN)
        composeRule.onNodeWithTag("recording-seekbar").assertIsFocused()
        dispatch(enter, android.view.KeyEvent.ACTION_DOWN, 1)
        dispatch(enter, android.view.KeyEvent.ACTION_UP)
        assertEquals(listOf(60_000L), seeks)
        assertEquals(0, toggles)
        for (close in listOf(android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.KEYCODE_DPAD_DOWN)) {
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            composeRule.onNodeWithTag("recording-marker-target").assertIsFocused()
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            dispatch(close, android.view.KeyEvent.ACTION_DOWN)
            composeRule.onNodeWithTag("recording-seekbar").assertIsFocused()
            dispatch(close, android.view.KeyEvent.ACTION_DOWN, 1)
            dispatch(close, android.view.KeyEvent.ACTION_UP)
            composeRule.onNodeWithTag("recording-seekbar").assertIsFocused()
            composeRule.onNodeWithTag("recording-marker-overlay").assertDoesNotExist()
        }
        assertEquals(listOf(60_000L), seeks)
        assertEquals(0, ancestorBacks)
        assertEquals(ordinaryTrack, track())
        assertTrue(kotlin.math.abs(bounds("player-timeline-fill").width - ordinaryTrack.width / 4f) < 2f)
        composeRule.onNodeWithTag("recording-selected-marker", useUnmergedTree = true).assertDoesNotExist()
        // Preselection uses the pending scrub target, and a fresh press repairs a lost release.
        composeRule.runOnIdle { markerPosition.value = 95_000L }
        dispatch(up, android.view.KeyEvent.ACTION_DOWN)
        composeRule.onNodeWithText("1:40").assertIsDisplayed()
        composeRule.runOnIdle { navigation.dismiss() }
        composeRule.onNodeWithTag("recording-seekbar").assertIsFocused()
        dispatch(up, android.view.KeyEvent.ACTION_DOWN)
        composeRule.onNodeWithText("1:40").assertIsDisplayed()
        dispatch(up, android.view.KeyEvent.ACTION_UP)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        assertEquals(listOf(60_000L), seeks)
        composeRule.onRoot().performKeyInput { pressKey(Key.Back) }
        assertEquals(1, ancestorBacks)
        composeRule.runOnIdle { markers.value = listOf(0L, 119_999L); markerPosition.value = 0L }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithText("0:00").assertIsDisplayed()
        assertTrue(bounds("recording-marker-target").left >= ordinaryTrack.left)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        assertTrue(bounds("recording-marker-target").right <= ordinaryTrack.right)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionLeft); pressKey(Key.Enter) }
        assertEquals(listOf(60_000L, 0L), seeks)
    }

    @Test
    fun markerEntryPrefersStrictlyNextThenFallsBackToLast() {
        val navigation = RecordingMarkerNavigation()
        val markers = listOf(0L, 10_000L, 60_000L)
        for ((position, expected) in listOf(0L to 10_000L, 11_000L to 60_000L,
            10_000L to 60_000L, 60_000L to 60_000L, 90_000L to 60_000L)) {
            navigation.show(markers, position)
            assertEquals(expected, navigation.selectedMs)
            navigation.dismiss()
        }
        navigation.show(emptyList(), 0L)
        assertEquals(null, navigation.selectedMs)
    }

    private fun captureMarkerState(name: String) {
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("markerCapture") != "true") return
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.io.File(context.getExternalFilesDir(null), "recording-marker-captures")
        assertTrue(directory.isDirectory || directory.mkdirs())
        java.io.File(directory, name).outputStream().use {
            assertTrue(composeRule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test
    fun germanMarkerOverlayRemainsReadableAtLargeText() {
        composeRule.setContent {
            val context = LocalContext.current
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.GERMAN) }
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides context.createConfigurationContext(configuration),
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, 1.5f),
            ) {
                TVHeadendPlayerTheme {
                    RecordingOverlayControls(
                        imageLoader = ImageLoader.Builder(LocalContext.current).build(),
                        piconPath = null, title = "Aufnahme mit echten Szenenmarken", subtitle = null, channelName = "Sender",
                        positionMs = 60_000, durationMs = 120_000, growing = false, nowSec = 1800,
                        canSeek = true, controlsVisible = true, optionsOpen = false, paused = true,
                        onTogglePlayPause = {}, onSeek = {}, onStopPlayback = {},
                        onUserInteraction = {}, onOpenOptions = {}, onOpenInfo = {},
                        markers = listOf(10_000L, 60_000L, 100_000L),
                    )
                }
            }
        }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("recording-marker-target").assertIsFocused()
        composeRule.onNodeWithText("1:40").assertIsDisplayed()
        val overlay = bounds("recording-marker-target")
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(overlay.left >= root.left && overlay.right <= root.right && overlay.top >= root.top)
        captureMarkerState("markers-open-de-1.5x.png")
    }

    @Test
    fun absentOrRemovedMarkersKeepOrdinarySeekingAndRestoreSafeFocus() {
        val markers = mutableStateOf<List<Long>>(emptyList())
        val seeks = mutableListOf<Long>()
        composeRule.setContent {
            TVHeadendPlayerTheme {
                RecordingOverlayControls(
                    imageLoader = ImageLoader.Builder(LocalContext.current).build(),
                    piconPath = null, title = "Recording", subtitle = null, channelName = null,
                    positionMs = 30_000, durationMs = 120_000, growing = false, nowSec = 0,
                    canSeek = true, controlsVisible = true, optionsOpen = false,
                    onTogglePlayPause = {}, onSeek = { seeks += it }, onStopPlayback = {},
                    onUserInteraction = {}, onOpenOptions = {}, onOpenInfo = {}, markers = markers.value,
                )
            }
        }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionRight) }
        assertEquals(listOf(30_000L), seeks)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("recording-marker-overlay").assertDoesNotExist()
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        composeRule.runOnIdle { markers.value = listOf(60_000L) }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("recording-marker-target").assertIsFocused()
        composeRule.runOnIdle { markers.value = emptyList() }
        composeRule.onNodeWithTag("recording-seekbar").assertIsFocused()
        composeRule.onNodeWithTag("recording-marker-overlay").assertDoesNotExist()
        assertEquals(listOf(30_000L), seeks)
    }

    @Test
    fun growingDisplayAdvanceDoesNotGrantDpadOrAccessibilityForwardSeek() {
        val displayEnd = mutableStateOf(65_000L)
        val seeks = mutableListOf<Long>()
        composeRule.setContent {
            TVHeadendPlayerTheme {
                RecordingOverlayControls(
                    imageLoader = ImageLoader.Builder(LocalContext.current).build(),
                    piconPath = null, title = "Growing recording", subtitle = null, channelName = null,
                    positionMs = 60_000, durationMs = 60_000, displayDurationMs = displayEnd.value,
                    growing = true, nowSec = 0, canSeek = true, controlsVisible = true, optionsOpen = false,
                    onTogglePlayPause = {}, onSeek = { seeks += it }, onStopPlayback = {},
                    onUserInteraction = {}, onOpenOptions = {}, onOpenInfo = {},
                )
            }
        }
        composeRule.onNodeWithTag("player-pause").performKeyInput { pressKey(Key.DirectionUp) }
        val timeline = composeRule.onNodeWithTag("recording-seekbar")
        timeline.assertIsFocused()
        timeline.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.runOnIdle { displayEnd.value = 66_000L }
        timeline.assertIsFocused()
        timeline.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.runOnIdle { assertTrue(seeks.isEmpty()) }
        val actions = timeline.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(1, actions.size) // Backward only; display-only history is not seekable.
        timeline.performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.runOnIdle { assertEquals(listOf(-30_000L), seeks) }
    }

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
