package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertTextEquals

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.runtime.mutableStateOf
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.common.formatClock
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class PlayerOverlayCompositionTest {
    @Test
    fun passiveScheduleProgressIsTruthfulAndNeverSeekableAcrossTuning() {
        val state = mutableStateOf(AppTimeshiftState())
        val current = mutableStateOf<EpgEvent?>(event(1, 0, 3600, "Programme"))
        composeRule.setContent {
            TVHeadendPlayerTheme {
                OverlayControlsTv(
                    imageLoader = ImageLoader.Builder(LocalContext.current).build(),
                    channelNumber = 1, channelName = "Documentary", piconPath = null,
                    nowEvent = current.value, nextEvent = event(2, 3600, 7200, "Next programme"), nowSec = 1800,
                    controlsVisible = true, optionsOpen = false,
                    onOpenChannels = {}, onStopPlayback = {}, onUserInteraction = {}, onOpenOptions = {},
                    timeshiftState = state.value, timeshiftFeedback = null,
                    onToggleTimeshiftPause = {}, onSeekTimeshift = { error("Passive progress sought") }, onGoLive = {},
                )
            }
        }
        val progress = composeRule.onNodeWithTag("player-schedule-progress").fetchSemanticsNode().config
        assertEquals(0.5f, progress[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current)
        assertTrue(!progress.contains(androidx.compose.ui.semantics.SemanticsActions.SetProgress))
        assertTrue(!progress.contains(androidx.compose.ui.semantics.SemanticsProperties.Focused))
        composeRule.onNodeWithTag("player-seekbar-thumb").assertDoesNotExist()
        composeRule.onNodeWithTag("player-info").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("player-info").assertIsFocused()
        fun headerBounds() = listOf("player-channel-identity", "player-programme-title", "player-next-programme", "player-clock")
            .map { composeRule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        val tuningHeader = headerBounds()
        composeRule.runOnIdle {
            state.value = AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = 0, liveEdgeMs = 0)
        }
        assertEquals(tuningHeader, headerBounds())
        composeRule.onNodeWithTag("player-schedule-progress").assertDoesNotExist()
        composeRule.onNodeWithTag("player-seekbar").assertExists()
        composeRule.runOnIdle { state.value = AppTimeshiftState() }
        for (epg in listOf(null, event(3, 3600, 7200, "Future"), event(4, 0, 1800, "Ended"))) {
            composeRule.runOnIdle { current.value = epg }
            composeRule.onNodeWithTag("player-schedule-progress").assertDoesNotExist()
            composeRule.onNodeWithTag("player-seekbar").assertDoesNotExist()
        }
    }

    @Test
    fun infoContentsStayVerticallyCenteredAtNormalAndLargeTextWithFocus() {
        val fontScale = mutableStateOf(1f)
        composeRule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale.value),
            ) {
                TVHeadendPlayerTheme { ModernLiveFixture(AppTimeshiftState(available = true)) }
            }
        }
        for (scale in listOf(1f, 1.5f)) {
            composeRule.runOnIdle { fontScale.value = scale }
            for (focus in listOf("player-pause", "player-info", "player-settings")) {
                composeRule.onNodeWithTag(focus).requestFocus()
                val info = composeRule.onNodeWithTag("player-info").fetchSemanticsNode().boundsInRoot
                val icon = composeRule.onNodeWithTag("player-info-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val label = composeRule.onNodeWithText("Info", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val settings = composeRule.onNodeWithTag("player-settings").fetchSemanticsNode().boundsInRoot
                assertEquals(settings.center.y, info.center.y, 1f)
                assertEquals(info.center.y, icon.center.y, 1f)
                assertEquals(info.center.y, label.center.y, 1f)
            }
        }
    }

    @Test
    fun timelineStatusReadoutsAndFeedbackKeepAnchorsAcrossPreviewAndMetadataTransitions() {
        val preview = mutableStateOf(false)
        val hidden = mutableStateOf(false)
        val feedback = mutableStateOf<String?>(null)
        val programme = event(1, 0, 3600, "Preview programme")
        val estimatedWindow = ProgrammeWindow(programme, Instant.fromEpochSeconds(1800), 0.5f, 0.2f, 0.75f, 0.75f, true)
        val window = mutableStateOf<ProgrammeWindow?>(estimatedWindow)
        val state = AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = -30_000, liveEdgeMs = 0)
        composeRule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.5f),
            ) {
                TVHeadendPlayerTheme {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        if (hidden.value) TimeshiftSeekPreview(state,
                            at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision(-30_000, 0, false),
                            programmeWindow = window.value, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                        else ModernLiveFixture(state, window.value, preview.value, feedback = feedback.value)
                    }
                }
            }
        }
        fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val track = bounds("player-timeline-track")
        val goLive = bounds("player-go-live")
        val status = bounds("player-timeline-status")
        val endpoints = bounds("player-window-start")
        for (candidate in listOf(estimatedWindow, null, estimatedWindow.copy(targetAvailable = false), estimatedWindow)) {
            composeRule.runOnIdle { window.value = candidate }
            for (seeking in listOf(true, false, true, false)) {
                composeRule.runOnIdle { preview.value = seeking }
                assertEquals(track, bounds("player-timeline-track"))
                assertEquals(goLive, bounds("player-go-live"))
                assertEquals(status, bounds("player-timeline-status"))
                if (candidate != null) assertEquals(endpoints, bounds("player-window-start"))
                if (seeking) composeRule.onNodeWithTag("timeshift-preview-target", useUnmergedTree = true).assertExists()
            }
        }
        composeRule.runOnIdle { preview.value = true }
        val title = bounds("player-window-title")
        val target = bounds("timeshift-preview-target")
        composeRule.runOnIdle { hidden.value = true }
        assertEquals(track, bounds("player-timeline-track"))
        assertEquals(target, bounds("timeshift-preview-target"))
        assertEquals(title.top, bounds("timeshift-preview-programme").top, 1f)
        composeRule.runOnIdle { hidden.value = false; feedback.value = "Target expired" }
        assertEquals(track, bounds("player-timeline-track"))
        assertEquals(status, bounds("player-timeline-status"))
        composeRule.runOnIdle { feedback.value = null; preview.value = false }
        assertEquals(track, bounds("player-timeline-track"))
        assertEquals(goLive, bounds("player-go-live"))
    }

    @Test
    fun losingKnownTimingBufferWhilePauseFocusedRestoresInfo() {
        assertPauseRemovalRestoresInfo(timingKnown = true)
    }

    @Test
    fun losingUnknownTimingBufferWhilePauseFocusedRestoresInfo() {
        assertPauseRemovalRestoresInfo(timingKnown = false)
    }

    private fun assertPauseRemovalRestoresInfo(timingKnown: Boolean) {
        val state = mutableStateOf(AppTimeshiftState(available = true, timingKnown = timingKnown))
        var pauses = 0
        composeRule.setContent { TVHeadendPlayerTheme { ModernLiveFixture(state.value, onPause = { pauses++ }) } }
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        composeRule.runOnIdle { state.value = AppTimeshiftState() }
        composeRule.onNodeWithTag("player-pause").assertDoesNotExist()
        composeRule.onNodeWithTag("player-info").assertIsFocused()
        assertEquals(0, pauses)
    }

    @Test
    fun capabilityUpdatesPreserveSurvivingInfoAndSettingsFocus() {
        val buffered = AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = -30_000, liveEdgeMs = 0)
        val state = mutableStateOf(buffered)
        composeRule.setContent { TVHeadendPlayerTheme { ModernLiveFixture(state.value) } }
        for (tag in listOf("player-info", "player-settings")) {
            composeRule.onNodeWithTag(tag).requestFocus().assertIsFocused()
            for (updated in listOf(buffered.copy(positionMs = 0), buffered.copy(timingKnown = false), AppTimeshiftState(), buffered)) {
                composeRule.runOnIdle { state.value = updated }
                composeRule.onNodeWithTag(tag).assertIsFocused()
            }
        }
    }

    @Test
    fun passiveGoLiveTransitionRestoresPauseButDoesNotStealSettingsAfterLeavingGoLive() {
        val buffered = AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = -30_000, liveEdgeMs = 0)
        val state = mutableStateOf(buffered)
        var pauses = 0
        composeRule.setContent { TVHeadendPlayerTheme { ModernLiveFixture(state.value, onPause = { pauses++ }) } }
        composeRule.onNodeWithTag("player-settings").requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-go-live").assertIsFocused()
        composeRule.runOnIdle { state.value = buffered.copy(positionMs = 0) }
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        composeRule.runOnIdle { state.value = buffered }
        composeRule.onNodeWithTag("player-settings").requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-go-live").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("player-settings").assertIsFocused()
        composeRule.runOnIdle { state.value = buffered.copy(positionMs = 0) }
        composeRule.onNodeWithTag("player-settings").assertIsFocused()
        assertEquals(0, pauses)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun heldGoLiveEnterConsumesRepeatsAndUpBeforeNextDeliberatePausePress() {
        val state = mutableStateOf(AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = -30_000, liveEdgeMs = 0))
        var pauses = 0
        var goLiveCalls = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ModernLiveFixture(state.value, onPause = { pauses++ }, onGoLive = {
                    goLiveCalls++
                    state.value = state.value.copy(positionMs = 0)
                })
            }
        }
        composeRule.onNodeWithTag("player-settings").requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-go-live").assertIsFocused()
        val downTime = android.os.SystemClock.uptimeMillis()
        fun dispatch(action: Int, repeatCount: Int = 0) {
            assertTrue(composeRule.onRoot().performKeyPress(androidx.compose.ui.input.key.KeyEvent(
                android.view.KeyEvent(downTime, android.os.SystemClock.uptimeMillis(), action,
                    android.view.KeyEvent.KEYCODE_ENTER, repeatCount),
            )))
        }
        dispatch(android.view.KeyEvent.ACTION_DOWN)
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        dispatch(android.view.KeyEvent.ACTION_DOWN, 1)
        dispatch(android.view.KeyEvent.ACTION_DOWN, 2)
        dispatch(android.view.KeyEvent.ACTION_UP)
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        assertEquals(1, goLiveCalls)
        assertEquals(0, pauses)
        composeRule.onNodeWithTag("player-pause").performKeyInput { pressKey(Key.Enter) }
        assertEquals(1, pauses)
    }

    @Test
    fun noBufferHasNoBlankTimelineSlotAndStartsOnInfo() {
        composeRule.setContent { TVHeadendPlayerTheme { ModernLiveFixture(AppTimeshiftState()) } }
        composeRule.onNodeWithTag("player-info").assertIsFocused()
        composeRule.onNodeWithTag("player-pause").assertDoesNotExist()
        composeRule.onNodeWithTag("player-seekbar").assertDoesNotExist()
        composeRule.onNodeWithTag("player-timeline-track").assertDoesNotExist()
        val status = composeRule.onNodeWithTag("player-timeline-status").fetchSemanticsNode().boundsInRoot
        val actions = composeRule.onNodeWithTag("player-actions").fetchSemanticsNode().boundsInRoot
        assertEquals(with(composeRule.density) { 8.dp.toPx() }, actions.top - status.bottom, 1f)
    }

    @Test
    fun goLiveIsReachableFromUtilitiesAndStatusTransitionRestoresPauseWithoutActivation() {
        val state = mutableStateOf(AppTimeshiftState(
            available = true, bufferStartMs = -600_000, positionMs = -30_000, liveEdgeMs = 0,
        ))
        var goLiveCalls = 0
        var pauses = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ModernLiveFixture(state.value, onPause = { pauses++ }, onGoLive = {
                    goLiveCalls++
                    state.value = state.value.copy(positionMs = 0)
                })
            }
        }
        composeRule.onNodeWithTag("player-pause").assertIsFocused().performKeyInput {
            repeat(4) { pressKey(Key.DirectionRight) }
        }
        composeRule.onNodeWithTag("player-settings").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("player-go-live").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("player-go-live").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("player-live-status").assertExists()
        composeRule.onNodeWithTag("player-go-live").assertDoesNotExist()
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        assertEquals(1, goLiveCalls)
        assertEquals(0, pauses)
        composeRule.onNodeWithTag("player-pause").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
        composeRule.runOnIdle { state.value = state.value.copy(positionMs = -30_000) }
        composeRule.onNodeWithTag("player-settings").requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-go-live").assertIsFocused()
        composeRule.runOnIdle { state.value = AppTimeshiftState() }
        composeRule.onNodeWithTag("player-info").assertIsFocused()
        assertEquals(1, goLiveCalls)
        assertEquals(0, pauses)
    }

    @Test
    fun largeTextLiveTrackKeepsAnchorAcrossRestFocusPreviewAndHiddenPreview() {
        val stage = mutableStateOf(0)
        val state = AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = -30_000, liveEdgeMs = 0)
        val programme = event(1, 0, 3600, "A long programme title for a deterministic preview")
        val window = ProgrammeWindow(programme, Instant.fromEpochSeconds(1800), 0.5f, 0.2f, 0.75f, 0.75f, true)
        composeRule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.5f),
            ) {
                TVHeadendPlayerTheme {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        if (stage.value == 3) {
                            TimeshiftSeekPreview(state, at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision(-30_000, 0, false),
                                programmeWindow = window, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                        } else ModernLiveFixture(state, window, previewing = stage.value == 2)
                    }
                }
            }
        }
        fun track() = composeRule.onNodeWithTag("player-timeline-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val resting = track()
        val actions = composeRule.onNodeWithTag("player-actions").fetchSemanticsNode().boundsInRoot
        assertEquals(actions.left, resting.left, 1f)
        assertEquals(actions.right, resting.right, 1f)
        composeRule.onNodeWithTag("player-seekbar-thumb").assertDoesNotExist()
        composeRule.onNodeWithTag("player-pause").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
        composeRule.onNodeWithTag("player-seekbar-thumb").assertExists()
        assertEquals(resting, track())
        composeRule.runOnIdle { stage.value = 2 }
        assertEquals(resting, track())
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
        val target = composeRule.onNodeWithTag("timeshift-preview-target", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val goLive = composeRule.onNodeWithTag("player-go-live").fetchSemanticsNode().boundsInRoot
        assertTrue(goLive.bottom <= target.top)
        assertTrue(target.bottom <= resting.top)
        composeRule.runOnIdle { stage.value = 3 }
        assertEquals(resting, track())
        composeRule.onNodeWithTag("player-seekbar-thumb", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("player-actions").assertDoesNotExist()
    }

    @androidx.compose.runtime.Composable
    private fun ModernLiveFixture(
        state: AppTimeshiftState,
        window: ProgrammeWindow? = null,
        previewing: Boolean = false,
        onPause: () -> Unit = {},
        onGoLive: () -> Unit = {},
        feedback: String? = null,
    ) {
        OverlayControlsTv(
            imageLoader = ImageLoader.Builder(LocalContext.current).build(),
            channelNumber = 1, channelName = "Documentary", piconPath = null,
            nowEvent = window?.event, nextEvent = null, nowSec = 1800,
            controlsVisible = true, optionsOpen = false,
            onOpenChannels = {}, onStopPlayback = {}, onUserInteraction = {}, onOpenOptions = {},
            timeshiftState = state, timeshiftFeedback = feedback,
            onToggleTimeshiftPause = onPause, onSeekTimeshift = {}, onGoLive = onGoLive,
            committedWindow = window, programmeWindow = window, previewing = previewing,
        )
    }

    @Test
    fun pauseAccessibleNameTracksPlaybackWithoutFloatingCaptionOrMovingFocus() {
        val paused = mutableStateOf(false)
        composeRule.setContent {
            val pauseFocus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
            val infoFocus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
            val settingsFocus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
            androidx.compose.runtime.LaunchedEffect(Unit) { pauseFocus.requestFocus() }
            TVHeadendPlayerTheme {
                PlayerActionRow(infoFocus, settingsFocus, {}, {}, {}, {},
                    onTogglePause = { paused.value = !paused.value }, paused = paused.value, pauseFocus = pauseFocus)
            }
        }
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        composeRule.onNodeWithContentDescription("Play").assertExists()
        composeRule.onNodeWithText("Pause", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("player-pause").performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithContentDescription("Pause").assertExists()
        composeRule.onNodeWithText("Play", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("player-action-context-label").assertDoesNotExist()
    }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun debugVideoBackdropExistsOnlyWhenRequested() {
        val visible = mutableStateOf(false)
        composeRule.setContent {
            DebugVideoBackdrop(
                visible = visible.value,
                modifier = Modifier.fillMaxSize(),
            )
        }

        composeRule.onNodeWithTag("debug-video-backdrop").assertDoesNotExist()

        composeRule.runOnIdle { visible.value = true }

        composeRule.onNodeWithTag("debug-video-backdrop").assertExists()
    }

    @Test
    fun playbackOptionsAttachToTheRightEdgeAtFullHeight() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                PlaybackOptionsOverlayFrame {
                    androidx.tv.material3.Text("Playback options")
                }
            }
        }

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val panel = composeRule.onNodeWithTag("playback-options-overlay")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(root.right, panel.right, 1f)
        assertEquals(root.bottom, panel.bottom, 1f)
        assertEquals(root.height, panel.height, 1f)
        assertTrue(panel.width < root.width / 2f)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun liveOverlaySeparatesIdentityTimelineAndControls() {
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                OverlayControlsTv(
                    imageLoader = imageLoader,
                    channelNumber = 1,
                    channelName = "ORF 1 HD",
                    piconPath = null,
                    nowEvent = event(1, 3_600, 7_200, "Zeit im Bild"),
                    nextEvent = event(2, 7_200, 9_000, "Wetter"),
                    nowSec = 5_400,
                    controlsVisible = true,
                    optionsOpen = false,
                    onOpenChannels = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    onOpenOptions = {},
                    timeshiftState = AppTimeshiftState(
                        available = true,
                        bufferStartMs = -3_600_000,
                        positionMs = -30_000,
                        liveEdgeMs = 0,
                    ),
                    timeshiftFeedback = null,
                    onToggleTimeshiftPause = {},
                    onSeekTimeshift = {},
                    onGoLive = {},
                )
            }
        }

        composeRule.waitForIdle()
        val picon = composeRule.onNodeWithTag("player-picon").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("player-programme-title").assertDoesNotExist()
        val channel = composeRule.onNodeWithTag("player-channel-identity")
            .fetchSemanticsNode().boundsInRoot
        val next = composeRule.onNodeWithTag("player-next-programme")
            .fetchSemanticsNode().boundsInRoot
        val clock = composeRule.onNodeWithTag("player-clock").fetchSemanticsNode().boundsInRoot
        val actions = composeRule.onNodeWithTag("player-actions").fetchSemanticsNode().boundsInRoot
        val timeline = composeRule.onNodeWithTag("player-seekbar").fetchSemanticsNode().boundsInRoot
        val goLive = composeRule.onNodeWithTag("player-go-live").fetchSemanticsNode().boundsInRoot
        val icons = listOf("player-pause", "player-stop", "player-info", "player-record", "player-settings")
            .map { composeRule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val sidePaddingPx = with(composeRule.density) { TvOverlaySidePadding.toPx() }

        assertEquals(root.left + sidePaddingPx, picon.left, 1f)
        assertEquals(root.right - sidePaddingPx, clock.right, 1f)
        assertTrue(picon.left < channel.left)
        assertTrue(channel.bottom <= next.top)
        assertTrue(clock.left > channel.left)
        assertTrue(clock.height > 0f)
        assertTrue(kotlin.math.abs(channel.top - clock.top) < with(composeRule.density) { 12.dp.toPx() })
        assertTrue(next.bottom < timeline.top)
        assertTrue(timeline.bottom <= actions.top)
        assertTrue(icons.zipWithNext().all { (left, right) -> left.right < right.left })
        assertTrue(goLive.bottom <= timeline.top)
        assertEquals(actions.right, goLive.right, 1f)
        assertEquals(actions.left, icons.first().left, 1f)
        assertEquals(actions.right, icons.last().right, 1f)
        assertTrue(icons[2].left > root.center.x)
        val infoLabel = composeRule.onNodeWithText("Info", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(icons[2].contains(infoLabel.center))
        composeRule.onNodeWithTag("player-transport-actions").assertDoesNotExist()
        assertEquals(1, composeRule.onAllNodesWithText("Channels").fetchSemanticsNodes().size)
        assertTrue(actions.bottom <= composeRule.onNodeWithTag("player-channels-cue").fetchSemanticsNode().boundsInRoot.top)
        assertEquals(
            0,
            composeRule.onAllNodesWithText(
                "Next ${formatClock(7_200)} - ${formatClock(9_000)}: Wetter",
            ).fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithText("Programme timing unavailable").assertExists()

        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        composeRule.onNodeWithTag("player-info").requestFocus().performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
    }

    @Test
    fun timelineKeepsOneAxisWhetherFocusedOrNot() {
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                OverlayControlsTv(
                    imageLoader = imageLoader,
                    channelNumber = 1,
                    channelName = "ORF 1 HD",
                    piconPath = null,
                    nowEvent = event(1, 3_600, 7_200, "Zeit im Bild"),
                    nextEvent = null,
                    nowSec = 5_400,
                    controlsVisible = true,
                    optionsOpen = false,
                    onOpenChannels = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    onOpenOptions = {},
                    timeshiftState = AppTimeshiftState(
                        available = true,
                        bufferStartMs = -3_600_000,
                        positionMs = -4_000,
                        liveEdgeMs = 0,
                    ),
                    timeshiftFeedback = null,
                    onToggleTimeshiftPause = {},
                    onSeekTimeshift = {},
                    onGoLive = {},
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithText("Live").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("player-seekbar-thumb").assertDoesNotExist()
        composeRule.onNodeWithText("1:00:00 available").assertDoesNotExist()

        composeRule.onNodeWithTag("player-seekbar").requestFocus()
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithText("Live").fetchSemanticsNodes().size)
        assertEquals(
            1,
            composeRule.onAllNodesWithTag("player-seekbar-thumb")
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun liveUtilitiesKeepAccessibleNamesAndStableGeometryWithoutCaptions() {
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                OverlayControlsTv(
                    imageLoader = imageLoader,
                    channelNumber = 1,
                    channelName = "ORF 1 HD",
                    piconPath = null,
                    nowEvent = null,
                    nextEvent = null,
                    nowSec = 5_400,
                    controlsVisible = true,
                    optionsOpen = false,
                    onOpenChannels = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    onOpenOptions = {},
                    timeshiftState = AppTimeshiftState(
                        available = true,
                        bufferStartMs = -60_000,
                        positionMs = -30_000,
                        liveEdgeMs = 0,
                    ),
                    timeshiftFeedback = null,
                    onToggleTimeshiftPause = {},
                    onSeekTimeshift = {},
                    onGoLive = {},
                )
            }
        }

        val actionsBefore = composeRule.onNodeWithTag("player-actions")
            .fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        composeRule.onNodeWithTag("player-info").requestFocus()
        composeRule.onNodeWithTag("player-action-context-label").assertDoesNotExist()
        composeRule.onNodeWithText("Info").assertExists()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-record").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-settings").assertIsFocused()
        composeRule.onNodeWithContentDescription("Settings").assertExists()
        composeRule.onNodeWithText("Settings", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("player-action-context-label").assertDoesNotExist()
        val actionsAfter = composeRule.onNodeWithTag("player-actions")
            .fetchSemanticsNode().boundsInRoot
        val timeline = composeRule.onNodeWithTag("player-seekbar")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(actionsBefore, actionsAfter)
        assertTrue(timeline.bottom <= actionsAfter.top)

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("player-record").assertIsFocused()
    }

    @Test
    fun liveHeaderKeepsItsAnchorsWhenTheTitleWraps() {
        val eventTitle = mutableStateOf("Short title")
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                OverlayControlsTv(
                    imageLoader = imageLoader,
                    channelNumber = 1,
                    channelName = "Channel",
                    piconPath = null,
                    nowEvent = event(1, 3_600, 7_200, eventTitle.value),
                    nextEvent = null,
                    nowSec = 5_400,
                    controlsVisible = true,
                    optionsOpen = false,
                    onOpenChannels = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    onOpenOptions = {},
                    timeshiftState = AppTimeshiftState(),
                    timeshiftFeedback = null,
                    onToggleTimeshiftPause = {},
                    onSeekTimeshift = {},
                    onGoLive = {},
                )
            }
        }

        composeRule.waitForIdle()
        val shortEyebrow = composeRule.onNodeWithTag("player-channel-identity")
            .fetchSemanticsNode().boundsInRoot
        val shortPicon = composeRule.onNodeWithTag("player-picon")
            .fetchSemanticsNode().boundsInRoot

        composeRule.runOnIdle {
            eventTitle.value = "A deliberately long programme title that wraps onto a second " +
                "line without moving the header anchors"
        }
        composeRule.waitForIdle()
        val longEyebrow = composeRule.onNodeWithTag("player-channel-identity")
            .fetchSemanticsNode().boundsInRoot
        val longPicon = composeRule.onNodeWithTag("player-picon")
            .fetchSemanticsNode().boundsInRoot
        val clock = composeRule.onNodeWithTag("player-clock").fetchSemanticsNode().boundsInRoot

        assertEquals(shortEyebrow.top, longEyebrow.top, 1f)
        assertEquals(shortPicon.top, longPicon.top, 1f)
        assertTrue(kotlin.math.abs(longEyebrow.top - clock.top) < with(composeRule.density) { 12.dp.toPx() })
        val title = composeRule.onNodeWithTag("player-programme-title").fetchSemanticsNode().boundsInRoot
        assertEquals(longEyebrow.left, title.left, 1f)
        assertEquals(with(composeRule.density) { 64.dp.toPx() }, longPicon.height, 1f)
    }

    private fun event(id: Int, start: Long, stop: Long, title: String) = EpgEvent.create(
        id = EventId(id.toLong()),
        channelId = ChannelId(1),
        start = Instant.fromEpochSeconds(start),
        stop = Instant.fromEpochSeconds(stop),
        title = title,
    )
}
