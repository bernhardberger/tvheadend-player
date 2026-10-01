package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertContentDescriptionEquals

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    fun upCommitsPreviewAndStaysOnTheSeekbar() {
        val preview = mutableStateOf(false)
        var commits = 0
        val state = AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = -30_000, liveEdgeMs = 0)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ModernLiveFixture(state, previewing = preview.value, onCommitSeek = { commits++ })
            }
        }
        composeRule.onNodeWithTag("player-pause").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.runOnIdle { preview.value = true }
        composeRule.onNodeWithTag("player-go-live").assertDoesNotExist()
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        // Nothing above the seekbar: focus stays and no Go live control appears.
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
        composeRule.onNodeWithTag("player-go-live").assertDoesNotExist()
        assertTrue(preview.value)
        assertEquals(1, commits)
    }

    @Test
    fun timelineGeometrySurvivesScheduleHistoryAndTimingLossAtLargeText() {
        val buffered = AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = -30_000, liveEdgeMs = 0)
        val state = mutableStateOf(AppTimeshiftState())
        val current = mutableStateOf<EpgEvent?>(event(1, 0, 3600, "Programme"))
        val scale = mutableStateOf(1f)
        composeRule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, scale.value),
            ) {
                TVHeadendPlayerTheme {
                    PlayerChrome(
                        mode = PlayerChromeMode.CONTROLS,
                        content = PlayerChromeContent("", liveInfoBarData(1, "Documentary", null, null, false, 0, "")),
                        timeline = PlayerChromeTimeline.Live(
                            state.value,
                            nowSec = 1800,
                            programme = current.value,
                        ),
                        actions = PlayerChromeActions(active = true),
                        imageLoader = rememberFixtureImageLoader(), currentSession = null,
                        onStop = {},
                        onInteraction = {},
                        onOptions = {},
                        onTogglePause = {},
                        onSeek = {},

                        onInfo = {},
                    )
                }
            }
        }
        fun anchors() = listOf("player-timeline-labels", "player-actions")
            .map { composeRule.onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
        fun track() = composeRule.onNodeWithTag("player-timeline-track", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        fun status() = composeRule.onAllNodesWithTag("player-timeline-status", useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }
        for (fontScale in listOf(1f, 1.5f)) {
            composeRule.runOnIdle { scale.value = fontScale }
            val baseline = anchors()
            assertTrue(status().isEmpty())
            val trackTop = track().top
            val trackBottom = track().bottom
            for (epg in listOf(event(1, 0, 3600, "Programme"), null, event(2, 3600, 7200, "Future"))) {
                composeRule.runOnIdle { current.value = epg }
                for (candidate in listOf(buffered, buffered.copy(timingKnown = false), AppTimeshiftState())) {
                    composeRule.runOnIdle { state.value = candidate }
                    assertEquals(baseline, anchors())
                    assertEquals(trackTop, track().top, 1f)
                    assertEquals(trackBottom, track().bottom, 1f)
                    if (candidate.available && !candidate.timingKnown) {
                        composeRule.mainClock.advanceTimeBy(1_600L)
                        val feedback = status().single()
                        assertTrue(feedback.bottom <= track().top)
                        composeRule.onNodeWithText("Playback timing unavailable").assertExists()
                    } else assertTrue(status().isEmpty())
                    val labels = composeRule.onNodeWithTag("player-timeline-labels", useUnmergedTree = true)
                        .fetchSemanticsNode().boundsInRoot
                    assertTrue(labels.top <= track().center.y && labels.bottom >= track().center.y)
                    if (!candidate.timingKnown || !candidate.available) {
                        composeRule.onNodeWithTag("player-seekbar-thumb").assertDoesNotExist()
                        if (candidate.available) {
                            val semantics = composeRule.onNodeWithTag("player-seekbar").fetchSemanticsNode().config
                            assertEquals(false, semantics[androidx.compose.ui.semantics.SemanticsProperties.Focused])
                            assertTrue(!semantics.contains(androidx.compose.ui.semantics.SemanticsActions.CustomActions))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun passiveScheduleProgressIsTruthfulAndNeverSeekableAcrossTuning() {
        val state = mutableStateOf(AppTimeshiftState())
        val current = mutableStateOf<EpgEvent?>(event(1, 0, 3600, "Programme"))
        composeRule.setContent {
            TVHeadendPlayerTheme {
                PlayerChrome(
                    mode = PlayerChromeMode.CONTROLS,
                    content = PlayerChromeContent("", liveInfoBarData(1, "Documentary", null, null, false, 0, "")),
                    timeline = PlayerChromeTimeline.Live(
                        state.value,
                        nowSec = 1800,
                        programme = current.value,
                    ),
                    actions = PlayerChromeActions(active = true),
                    imageLoader = rememberFixtureImageLoader(), currentSession = null,
                    onStop = {},
                    onInteraction = {},
                    onOptions = {},
                    onTogglePause = {},
                    onSeek = { error("Passive progress sought") },

                    onInfo = {},
                )
            }
        }
        val progress = composeRule.onNodeWithTag("player-schedule-progress").fetchSemanticsNode().config
        assertEquals(0.5f, progress[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current)
        assertEquals(listOf("Current broadcast. ${formatClock(0)} - ${formatClock(3600)}"),
            progress[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription])
        assertTrue(!progress.contains(androidx.compose.ui.semantics.SemanticsActions.CustomActions))
        assertTrue(!progress.contains(androidx.compose.ui.semantics.SemanticsActions.SetProgress))
        assertTrue(!progress.contains(androidx.compose.ui.semantics.SemanticsProperties.Focused))
        composeRule.onNodeWithTag("player-seekbar-thumb").assertDoesNotExist()
        // Without a focusable timeline Up from the action row reaches the card, and Down returns to the entry.
        composeRule.onNodeWithTag("player-pause").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("player-identity-card").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        composeRule.runOnIdle {
            state.value = AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = 0, liveEdgeMs = 0)
        }
        composeRule.onNodeWithTag("player-schedule-progress").assertDoesNotExist()
        composeRule.onNodeWithTag("player-seekbar").assertExists()
        val seekbar = composeRule.onNodeWithTag("player-seekbar").fetchSemanticsNode().config
        assertEquals(listOf("Timeshift position Live. Buffer starts 10:00 behind live."),
            seekbar[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription])
        // The controls have no state cell: the Pause button speaks for playback.
        composeRule.onNodeWithTag("player-state").assertDoesNotExist()
        val end = composeRule.onNodeWithTag("player-live-state").assertContentDescriptionEquals("Live").fetchSemanticsNode()
        assertEquals(androidx.compose.ui.semantics.LiveRegionMode.Polite,
            end.config[androidx.compose.ui.semantics.SemanticsProperties.LiveRegion])
        composeRule.runOnIdle { state.value = AppTimeshiftState() }
        for (epg in listOf(null, event(3, 3600, 7200, "Future"), event(4, 0, 1800, "Ended"))) {
            composeRule.runOnIdle { current.value = epg }
            composeRule.onNodeWithTag("player-schedule-progress").assertDoesNotExist()
            composeRule.onNodeWithTag("player-seekbar").assertDoesNotExist()
        }
    }

    @Test
    fun identityCardContentsStayCentredAtNormalAndLargeTextWithFocus() {
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
            for (focus in listOf("player-pause", "player-identity-card", "player-settings")) {
                composeRule.onNodeWithTag(focus).requestFocus()
                val card = composeRule.onNodeWithTag("player-identity-card").fetchSemanticsNode().boundsInRoot
                val mark = composeRule.onNodeWithTag("player-identity-name", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val pause = composeRule.onNodeWithTag("player-pause").fetchSemanticsNode().boundsInRoot
                val settings = composeRule.onNodeWithTag("player-settings").fetchSemanticsNode().boundsInRoot
                with(composeRule.density) {
                    assertEquals(160.dp.toPx(), card.width, 1f)
                    assertEquals(90.dp.toPx(), card.height, 1f)
                }
                assertEquals(card.center.x, mark.center.x, 1f)
                assertEquals(card.center.y, mark.center.y, 1f)
                assertEquals(settings.center.y, pause.center.y, 1f)
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
                        if (hidden.value) QuickStepBanner(state,
                            at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision(-30_000, 0, false),
                            programmeWindow = window.value, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                        else ModernLiveFixture(state, window.value, preview.value, feedback = feedback.value)
                    }
                }
            }
        }
        fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val track = bounds("player-timeline-track")
        val liveState = bounds("player-live-state")
        val endpoints = bounds("player-window-start")
        for (candidate in listOf(estimatedWindow, null, estimatedWindow.copy(targetAvailable = false), estimatedWindow)) {
            composeRule.runOnIdle { window.value = candidate }
            for (seeking in listOf(true, false, true, false)) {
                composeRule.runOnIdle { preview.value = seeking }
                assertEquals(track.top, bounds("player-timeline-track").top, 1f)
                assertEquals(track.bottom, bounds("player-timeline-track").bottom, 1f)
                composeRule.onNodeWithTag("player-state").assertDoesNotExist()
                assertEquals(liveState.top, bounds("player-live-state").top, 1f)
                assertEquals(liveState.bottom, bounds("player-live-state").bottom, 1f)
                if (!seeking) composeRule.onNodeWithTag("player-timeline-status").assertDoesNotExist()
                if (candidate != null) assertEquals(endpoints, bounds("player-window-start"))
                if (seeking) composeRule.onNodeWithTag("timeshift-preview-target", useUnmergedTree = true).assertExists()
            }
        }
        composeRule.runOnIdle { preview.value = true }
        val target = bounds("timeshift-preview-target")
        // The readout is on the bar's own coordinates; with no thumb drawn (the bar is not focused) it
        // rests on the bar row's top. No feedback slot for it.
        composeRule.onNodeWithTag("player-timeline-status").assertDoesNotExist()
        composeRule.onNodeWithTag("player-seekbar-thumb", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(track.top, target.bottom, 1f)
        composeRule.onNodeWithTag("player-window-title").assertDoesNotExist()
        composeRule.runOnIdle { hidden.value = true }
        val drop = with(composeRule.density) { PlayerBannerDrop.toPx() }
        // The Banner keeps the state cell's room before the bar, which the controls hand to it.
        val room = bounds("player-state").width + with(composeRule.density) { 8.dp.toPx() }
        val stepTrack = bounds("player-timeline-track")
        assertEquals(track.left + room, stepTrack.left, 1f)
        assertEquals(track.right, stepTrack.right, 1f)
        assertEquals(track.top + drop, stepTrack.top, 1f)
        assertEquals(target.top + drop, bounds("timeshift-preview-target").top, 1f)
        composeRule.onNodeWithTag("timeshift-preview-programme").assertDoesNotExist()
        composeRule.runOnIdle { hidden.value = false; feedback.value = "Target expired" }
        assertEquals(track, bounds("player-timeline-track"))
        val slot = bounds("player-timeline-status")
        assertTrue("feedback paints above the bar row", slot.bottom <= track.top + 1f)
        composeRule.runOnIdle { feedback.value = null; preview.value = false }
        assertEquals(track, bounds("player-timeline-track"))
        composeRule.onNodeWithTag("player-timeline-status").assertDoesNotExist()
    }

    @Test
    fun losingKnownTimingBufferWhilePauseFocusedKeepsPauseFocused() {
        assertLosingTheBufferKeepsPauseFocused(timingKnown = true)
    }

    @Test
    fun losingUnknownTimingBufferWhilePauseFocusedKeepsPauseFocused() {
        assertLosingTheBufferKeepsPauseFocused(timingKnown = false)
    }

    private fun assertLosingTheBufferKeepsPauseFocused(timingKnown: Boolean) {
        val state = mutableStateOf(AppTimeshiftState(available = true, timingKnown = timingKnown))
        var pauses = 0
        composeRule.setContent { TVHeadendPlayerTheme { ModernLiveFixture(state.value, onPause = { pauses++ }) } }
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        composeRule.runOnIdle { state.value = AppTimeshiftState() }
        composeRule.onNodeWithTag("player-seekbar").assertDoesNotExist()
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        assertEquals(0, pauses)
    }

    @Test
    fun capabilityUpdatesPreserveSurvivingCardAndSettingsFocus() {
        val buffered = AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = -30_000, liveEdgeMs = 0)
        val state = mutableStateOf(buffered)
        composeRule.setContent { TVHeadendPlayerTheme { ModernLiveFixture(state.value) } }
        for (tag in listOf("player-identity-card", "player-settings")) {
            composeRule.onNodeWithTag(tag).requestFocus().assertIsFocused()
            for (updated in listOf(buffered.copy(positionMs = 0), buffered.copy(timingKnown = false), AppTimeshiftState(), buffered)) {
                composeRule.runOnIdle { state.value = updated }
                composeRule.onNodeWithTag(tag).assertIsFocused()
            }
        }
    }

    @Test
    fun noBufferReservesTimelineGeometryWithoutInventingProgressAndStartsOnPause() {
        composeRule.setContent { TVHeadendPlayerTheme { ModernLiveFixture(AppTimeshiftState()) } }
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        composeRule.onNodeWithTag("player-settings").assertExists()
        composeRule.onNodeWithTag("player-info").assertDoesNotExist()
        composeRule.onNodeWithTag("player-seekbar").assertDoesNotExist()
        val track = composeRule.onNodeWithTag("player-timeline-track").fetchSemanticsNode().config
        assertTrue(!track.contains(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo))
        composeRule.onNodeWithTag("player-schedule-progress").assertDoesNotExist()
        composeRule.onNodeWithTag("player-seekbar-thumb").assertDoesNotExist()
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
                            QuickStepBanner(state, at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision(-30_000, 0, false),
                                programmeWindow = window, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                        } else ModernLiveFixture(state, window, previewing = stage.value == 2)
                    }
                }
            }
        }
        fun track() = composeRule.onNodeWithTag("player-timeline-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val resting = track()
        val actions = composeRule.onNodeWithTag("player-actions").fetchSemanticsNode().boundsInRoot
        val labels = composeRule.onNodeWithTag("player-timeline-labels", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(actions.left, labels.left, 1f)
        assertEquals(actions.right, labels.right, 1f)
        assertTrue(resting.left >= labels.left && resting.right <= labels.right)
        composeRule.onNodeWithTag("player-seekbar-thumb").assertDoesNotExist()
        composeRule.onNodeWithTag("player-pause").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
        composeRule.onNodeWithTag("player-seekbar-thumb").assertExists()
        assertEquals(resting, track())
        composeRule.runOnIdle { stage.value = 2 }
        assertEquals(resting, track())
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
        val target = composeRule.onNodeWithTag("timeshift-preview-target", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("player-go-live").assertDoesNotExist()
        assertTrue(target.bottom <= resting.top + 1f)
        val thumb = composeRule.onNodeWithTag("player-seekbar-thumb", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("the readout's bottom is 8dp clear of the thumb's top", thumb.top - with(composeRule.density) { ReadoutThumbGap.toPx() },
            target.bottom, 1.5f)
        composeRule.runOnIdle { stage.value = 3 }
        val stepTrack = track()
        val drop = with(composeRule.density) { PlayerBannerDrop.toPx() }
        val room = composeRule.onNodeWithTag("player-state", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width +
            with(composeRule.density) { 8.dp.toPx() }
        assertEquals("the Banner keeps the state cell's room", resting.left + room, stepTrack.left, 1f)
        assertEquals(resting.right, stepTrack.right, 1f)
        assertEquals(resting.top + drop, stepTrack.top, 1f)
        assertEquals(resting.bottom + drop, stepTrack.bottom, 1f)
        composeRule.onNodeWithTag("player-seekbar-thumb", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("player-actions").assertDoesNotExist()
    }

    @androidx.compose.runtime.Composable
    private fun ModernLiveFixture(
        state: AppTimeshiftState,
        window: ProgrammeWindow? = null,
        previewing: Boolean = false,
        onPause: () -> Unit = {},
        feedback: String? = null,
        onCommitSeek: () -> Unit = {},
    ) {
        PlayerChrome(
            mode = PlayerChromeMode.CONTROLS,
            content = PlayerChromeContent("", liveInfoBarData(1, "Documentary", null, null, false, 0, "")),
            timeline = PlayerChromeTimeline.Live(
                state,
                nowSec = 1800,
                programme = window?.event,
                committedWindow = window,
                programmeWindow = window,
                previewing = previewing,
                feedback = feedback,
            ),
            actions = PlayerChromeActions(active = true),
            imageLoader = rememberFixtureImageLoader(), currentSession = null,
            onStop = {},
            onInteraction = {},
            onOptions = {},
            onTogglePause = onPause,
            onSeek = {},
            onCommitSeek = onCommitSeek,
            onInfo = {},
        )
    }

    @Test
    fun pauseAccessibleNameTracksPlaybackWithoutFloatingCaptionOrMovingFocus() {
        val paused = mutableStateOf(false)
        composeRule.setContent {
            val pauseFocus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
            val settingsFocus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
            androidx.compose.runtime.LaunchedEffect(Unit) { pauseFocus.requestFocus() }
            TVHeadendPlayerTheme {
                PlayerActionRow(pauseFocus, settingsFocus, onTogglePause = { paused.value = !paused.value },
                    onSettings = {}, onStop = {}, onInteraction = {}, paused = paused.value)
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
    fun playbackOptionsFloatInsideEndTopAndBottomInsets() {
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

        val inset = with(composeRule.density) { 24.dp.toPx() }
        assertEquals(root.right - inset, panel.right, 1f)
        assertEquals(root.bottom - inset, panel.bottom, 1f)
        assertEquals(root.top + inset, panel.top, 1f)
        assertEquals(with(composeRule.density) { 320.dp.toPx() }, panel.width, 1f)
    }

    @Test
    fun timelineKeepsOneAxisWhetherFocusedOrNot() {
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                PlayerChrome(
                    mode = PlayerChromeMode.CONTROLS,
                    content = PlayerChromeContent("", liveInfoBarData(1, "ORF 1 HD", null, null, false, 0, "")),
                    timeline = PlayerChromeTimeline.Live(
                        AppTimeshiftState(
                        available = true,
                        bufferStartMs = -3_600_000,
                        positionMs = -4_000,
                        liveEdgeMs = 0,
                    ),
                        nowSec = 5_400,
                        programme = event(1, 3_600, 7_200, "Zeit im Bild"),
                    ),
                    actions = PlayerChromeActions(active = true),
                    imageLoader = rememberFixtureImageLoader(), currentSession = null,
                    onStop = {},
                    onInteraction = {},
                    onOptions = {},
                    onTogglePause = {},
                    onSeek = {},

                    onInfo = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-live-state").assertContentDescriptionEquals("Live")
        composeRule.onNodeWithTag("player-seekbar-thumb").assertDoesNotExist()
        composeRule.onNodeWithText("1:00:00 available").assertDoesNotExist()

        composeRule.onNodeWithTag("player-seekbar").requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player-live-state").assertContentDescriptionEquals("Live")
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
                PlayerChrome(
                    mode = PlayerChromeMode.CONTROLS,
                    content = PlayerChromeContent("", liveInfoBarData(1, "ORF 1 HD", null, null, false, 0, "")),
                    timeline = PlayerChromeTimeline.Live(
                        AppTimeshiftState(
                        available = true,
                        bufferStartMs = -60_000,
                        positionMs = -30_000,
                        liveEdgeMs = 0,
                    ),
                        nowSec = 5_400,
                        programme = null,
                    ),
                    actions = PlayerChromeActions(active = true),
                    imageLoader = rememberFixtureImageLoader(), currentSession = null,
                    onStop = {},
                    onInteraction = {},
                    onOptions = {},
                    onTogglePause = {},
                    onSeek = {},

                    onInfo = {},
                )
            }
        }

        val actionsBefore = composeRule.onNodeWithTag("player-actions")
            .fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("player-pause").assertIsFocused()
        composeRule.onNodeWithTag("player-identity-card").assertContentDescriptionEquals("Info")
        composeRule.onNodeWithTag("player-info").assertDoesNotExist()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-stop").assertIsFocused()
        composeRule.onNodeWithTag("player-action-context-label").assertDoesNotExist()
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

    private fun event(id: Int, start: Long, stop: Long, title: String) = EpgEvent.create(
        id = EventId(id.toLong()),
        channelId = ChannelId(1),
        start = Instant.fromEpochSeconds(start),
        stop = Instant.fromEpochSeconds(stop),
        title = title,
    )
}
