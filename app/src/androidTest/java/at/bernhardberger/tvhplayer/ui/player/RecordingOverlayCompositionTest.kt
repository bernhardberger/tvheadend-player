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
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                TVHeadendPlayerTheme {
                    // RecordingPlayerScreen owns Back before its children. Exercise that ordering.
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()
                        .onPreviewKeyEvent { event ->
                            if (navigation.handle(event, markers.value) { seeks += it }) true
                            else if (event.key == Key.Back) {
                                if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) ancestorBacks++
                                true
                            } else false
                        }) {
                        RecordingChromeFixture(
                            positionMs = markerPosition.value,
                            durationMs = 120_000,
                            growing = false,
                            canSeek = true,
                            paused = true,
                            onTogglePause = { toggles++ },
                            onSeek = { seeks += it },
                            onOptions = {},
                            onInfo = {},
                            markers = markers.value,
                            markerNavigation = navigation,
                            onSeekMarker = { seeks += it },
                        )
                    }
                }
            }
        }
        fun track() = composeRule.onNodeWithTag("player-timeline-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val ordinaryTrack = track()
        val ordinaryActions = bounds("player-actions")
        captureMarkerState("markers-absent-en-1.5x.png")
        composeRule.runOnIdle { markers.value = listOf(10_000L, 60_000L, 100_000L) }
        assertEquals(ordinaryTrack, track())
        composeRule.onAllNodesWithTag("recording-marker-tick", useUnmergedTree = true).assertCountEquals(3)
        composeRule.onNodeWithTag("player-pause").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
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
        assertEquals(ordinaryTrack, track())
        assertEquals(ordinaryActions, bounds("player-actions"))
        captureMarkerState("markers-open-en-1.5x.png")
        val enter = android.view.KeyEvent.KEYCODE_ENTER
        dispatch(enter, android.view.KeyEvent.ACTION_DOWN)
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
        dispatch(enter, android.view.KeyEvent.ACTION_DOWN, 1)
        dispatch(enter, android.view.KeyEvent.ACTION_UP)
        assertEquals(listOf(60_000L), seeks)
        assertEquals(0, toggles)
        for (close in listOf(android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.KEYCODE_DPAD_DOWN)) {
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            composeRule.onNodeWithTag("recording-marker-target").assertIsFocused()
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            dispatch(close, android.view.KeyEvent.ACTION_DOWN)
            composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
            dispatch(close, android.view.KeyEvent.ACTION_DOWN, 1)
            dispatch(close, android.view.KeyEvent.ACTION_UP)
            composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
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
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
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
        composeRule.onNodeWithTag("recording-marker-target").assertIsFocused()
            .assert(androidx.compose.ui.test.hasText("0:00"))
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
                    RecordingChromeFixture(
                        positionMs = 60_000,
                        durationMs = 120_000,
                        growing = false,
                        canSeek = true,
                        paused = true,
                        onTogglePause = {},
                        onSeek = {},
                        onOptions = {},
                        onInfo = {},
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
                RecordingChromeFixture(
                    positionMs = 30_000,
                    durationMs = 120_000,
                    growing = false,
                    canSeek = true,
                    onTogglePause = {},
                    onSeek = { seeks += it },
                    onOptions = {},
                    onInfo = {},
                    markers = markers.value,
                )
            }
        }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionRight) }
        assertEquals(listOf(30_000L), seeks)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("recording-marker-overlay").assertDoesNotExist()
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
        composeRule.runOnIdle { markers.value = listOf(60_000L) }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("recording-marker-target").assertIsFocused()
        composeRule.runOnIdle { markers.value = emptyList() }
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
        composeRule.onNodeWithTag("recording-marker-overlay").assertDoesNotExist()
        assertEquals(listOf(30_000L), seeks)
    }

    @Test
    fun growingDisplayAdvanceDoesNotGrantDpadOrAccessibilityForwardSeek() {
        val displayEnd = mutableStateOf(65_000L)
        val seeks = mutableListOf<Long>()
        composeRule.setContent {
            TVHeadendPlayerTheme {
                RecordingChromeFixture(
                    positionMs = 60_000,
                    durationMs = 60_000,
                    displayDurationMs = displayEnd.value,
                    growing = true,
                    canSeek = true,
                    onTogglePause = {},
                    onSeek = { seeks += it },
                    onOptions = {},
                    onInfo = {},
                )
            }
        }
        composeRule.onNodeWithTag("player-pause").performKeyInput { pressKey(Key.DirectionUp) }
        val timeline = composeRule.onNodeWithTag("player-seekbar")
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
                        if (hidden.value) RecordingChromeFixture(
                            mode = PlayerChromeMode.BANNER_STEP,
                            positionMs = 60_000,
                            targetMs = 30_000,
                            originMs = 60_000,
                            durationMs = 5_400_000,
                            growing = false,
                            modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                        ) else RecordingChromeFixture(
                            positionMs = 30_000,
                            durationMs = 5_400_000,
                            growing = false,
                            canSeek = true,
                            onTogglePause = {},
                            onSeek = {},
                            onOptions = {},
                            onInfo = {},
                            targetMs = (30_000).toLong().takeIf { previewing.value },
                        )
                    }
                }
            }
        }
        fun track() = composeRule.onNodeWithTag("player-timeline-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val resting = track()
        composeRule.onNodeWithTag("player-pause").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
        assertEquals(resting, track())
        composeRule.runOnIdle { previewing.value = true }
        assertEquals(resting, track())
        composeRule.runOnIdle { hidden.value = true }
        // A quick step plays in the Banner, which rests one action row lower than the controls.
        val stepped = track()
        assertEquals(resting.top + with(composeRule.density) { PlayerBannerDrop.toPx() }, stepped.top, 1f)
        // The Banner keeps the state cell's room before the bar, which the controls hand to it.
        val room = composeRule.onNodeWithTag("player-state", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width +
            with(composeRule.density) { 8.dp.toPx() }
        assertEquals(resting.left + room, stepped.left, 1f)
        assertEquals(resting.right, stepped.right, 1f)
        composeRule.onNodeWithTag("player-actions").assertDoesNotExist()
        composeRule.onNodeWithTag("player-seekbar-thumb", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("player-end-clock", useUnmergedTree = true)
            .assert(androidx.compose.ui.test.hasText("1:30:00", substring = true)).assertIsDisplayed()
    }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recordingOmitsRecordWithoutAnEmptySlotAndKeepsTimelineAboveActions() {
        setRecordingOverlay("Recording title")

        val pause = bounds("player-pause")
        val stop = bounds("player-stop")
        val settings = bounds("player-settings")
        // Play/Pause and Stop stand together at the start, Settings alone at the end.
        assertTrue(pause.right < stop.left)
        assertTrue(stop.left - pause.right < pause.width)
        assertTrue(settings.left - stop.right > stop.width)
        assertEquals(bounds("player-actions").right, settings.right, 1f)
        composeRule.onNodeWithTag("player-info").assertDoesNotExist()
        assertTrue(bounds("recording-duration-status").bottom <= bounds("player-actions").top)
        composeRule.onNodeWithTag("player-channels-cue").assertDoesNotExist()
        composeRule.onNodeWithTag("player-record").assertDoesNotExist()
        composeRule.onNodeWithTag("player-go-live").assertDoesNotExist()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun recordingUtilitiesRemainReachableWithoutFloatingCaptions() {
        setRecordingOverlay("Recording title")

        val actionsBefore = bounds("player-actions")
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-stop").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-settings").assertIsFocused()
        composeRule.onNodeWithTag("player-action-context-label").assertDoesNotExist()
        composeRule.onNodeWithTag("player-settings").assertContentDescriptionEquals("Settings")
        val actionsAfter = bounds("player-actions")
        val timeline = bounds("recording-duration-status")
        assertEquals(actionsBefore, actionsAfter)
        assertTrue(timeline.bottom <= actionsAfter.top)

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("player-stop").assertIsFocused()
        // Info stays reachable: the passive timeline takes no focus, so Up reaches the card.
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("player-identity-card").assertIsFocused().assertContentDescriptionEquals("Info")
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun germanUtilitiesRetainAccessibleNamesWithoutCaptionsAtLargeText() {
        setRecordingOverlay(
            title = "Eine außergewöhnlich lange deutschsprachige Aufnahme",
            german = true,
            fontScale = 1.5f,
        )

        composeRule.onNodeWithTag("player-stop").requestFocus()
        composeRule.onRoot().performKeyInput {
            pressKey(Key.DirectionRight)
        }

        val options = composeRule.onNodeWithTag("player-settings")
        val contextLabel = composeRule.onNodeWithTag("player-action-context-label")
        val timeline = bounds("recording-duration-status")
        val actions = bounds("player-actions")
        options.assertIsFocused().assertContentDescriptionEquals("Einstellungen")
        composeRule.onNodeWithText("Einstellungen", useUnmergedTree = true).assertDoesNotExist()
        contextLabel.assertDoesNotExist()
        assertTrue(timeline.bottom <= actions.top)
    }

    @Test
    fun returningFromInfoRestoresTheCardWithoutBouncingBackToTimeline() {
        var restoreInfo by mutableStateOf(false)
        setRecordingOverlay(
            title = "Recording",
            durationMs = 600_000L,
            restoreInfoFocus = { restoreInfo },
            onInfoFocusRestored = { restoreInfo = false },
        )
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        composeRule.onNodeWithTag("player-seekbar").requestFocus()
        composeRule.runOnIdle { restoreInfo = true }
        composeRule.onNodeWithTag("player-identity-card").assertIsFocused()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-identity-card").assertIsFocused()
    }

    @Test
    fun knownDurationWithoutSeekCapabilityIsPassiveAndStartsOnPause() {
        setRecordingOverlay(title = "Recording", durationMs = 600_000L, canSeek = false)
        composeRule.onNodeWithTag("player-seekbar").assertDoesNotExist()
        composeRule.onNodeWithTag("recording-duration-status").assertIsDisplayed()
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
    }

    @Test
    fun longRecordingElapsedMatchesTotalPrecision() {
        setRecordingOverlay("Recording", durationMs = 5_400_000L)
        composeRule.onNodeWithText("0:00:30").assertIsDisplayed()
        composeRule.onNodeWithTag("player-end-clock", useUnmergedTree = true)
            .assert(androidx.compose.ui.test.hasTextExactly("1:30:00")).assertIsDisplayed()
        // A recording's bar row is position, bar and length: no remaining time and no status.
        composeRule.onNodeWithTag("player-distance", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("player-live-state", useUnmergedTree = true).assertDoesNotExist()
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
                    RecordingChromeFixture(
                        positionMs = 30_000L,
                        durationMs = durationMs,
                        growing = true,
                        canSeek = canSeek,
                        onTogglePause = {},
                        onSeek = {},
                        onOptions = {},
                        onInfo = {},
                        restoreFocus = "player-identity-card".takeIf { restoreInfoFocus() },
                        onFocusRestored = onInfoFocusRestored,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String) =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
}
