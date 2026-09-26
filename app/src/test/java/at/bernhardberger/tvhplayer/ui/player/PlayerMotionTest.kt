package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingState
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/**
 * Player motion never holds focus, keys or semantics: leaving content gives them up on
 * its first exit frame while it keeps fading, and incoming focus does not wait for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hidingControlsDropsFocusSemanticsAndKeysOnTheFirstExitFrame() {
        var visible by mutableStateOf(true)
        val probe = FocusProbe()
        var composed = false
        compose.setContent {
            TVHeadendPlayerTheme {
                PlayerControlsLayer(visible = visible, modalVisible = false) {
                    DisposableEffect(Unit) { composed = true; onDispose { composed = false } }
                    probe.Target("controls-action")
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("controls-action").assertIsFocused()

        compose.mainClock.autoAdvance = false
        change { visible = false }
        compose.mainClock.advanceTimeByFrame()

        assertTrue("controls still fade out", composed)
        assertFalse("the leaving action keeps focus", probe.focused)
        compose.onNodeWithTag("controls-action").assertDoesNotExist()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(0, probe.activations)

        compose.mainClock.advanceTimeBy(1_000)
        assertFalse(composed)
    }

    @Test fun closingOptionsHandsFocusAndKeysToSettingsWhileThePanelFades() {
        var settingsPresses = 0
        checkPanelCloseHandsFocusBack(
            panelTag = "playback-options-overlay",
            actionTag = "player-settings",
            activations = { settingsPresses },
            controls = { loader, open, restore, restored ->
                liveControls(
                    loader,
                    optionsOpen = open,
                    restoreOptionsFocus = restore,
                    onOptionsFocusRestored = restored,
                    onOpenOptions = { settingsPresses++ },
                )
            },
            panel = { optionsRoot() },
        )
    }

    @Test fun closingLiveInfoHandsFocusAndKeysToInfoWhileThePanelFades() {
        var infoPresses = 0
        checkPanelCloseHandsFocusBack(
            panelTag = "live-info-panel",
            actionTag = "player-info",
            activations = { infoPresses },
            controls = { loader, _, restore, restored ->
                liveControls(
                    loader,
                    optionsOpen = false,
                    restoreInfoFocus = restore,
                    onInfoFocusRestored = restored,
                    onOpenInfo = { infoPresses++ },
                )
            },
            panel = {
                LiveProgrammeInfoOverlay(
                    event = null,
                    channelIdentity = "1 One",
                    channelName = "One",
                    recordingScheduled = false,
                    canRecord = false,
                    recordingState = LiveInfoRecordingState.Idle,
                    confirmationVisible = false,
                    restoreRecordFocus = false,
                    onRecord = {},
                    onRecordingActivate = {},
                    onRecordingDismiss = {},
                    onClose = {},
                )
            },
        )
    }

    @Test fun closingRecordingInfoHandsFocusAndKeysToInfoWhileThePanelFades() {
        var infoPresses = 0
        checkPanelCloseHandsFocusBack(
            panelTag = "recording-info-panel",
            actionTag = "player-info",
            activations = { infoPresses },
            controls = { loader, _, restore, restored ->
                RecordingOverlayControls(
                    imageLoader = loader, piconPath = null, title = "News", subtitle = null,
                    channelName = "One", positionMs = 600_000, durationMs = 3_600_000,
                    growing = false, nowSec = 1_800, canSeek = true, controlsVisible = true,
                    optionsOpen = false, onTogglePlayPause = {}, onSeek = {}, onStopPlayback = {},
                    onUserInteraction = {}, onOpenOptions = {}, onOpenInfo = { infoPresses++ },
                    restoreInfoFocus = restore, onInfoFocusRestored = restored,
                )
            },
            panel = {
                PlaybackOptionsOverlayFrame(paneTitle = "Info", panelTag = "recording-info-panel") {
                    FocusProbe().Target("recording-info-row")
                }
            },
        )
    }

    @Test fun reopeningOptionsWhileTheyFadeFocusesTheirFirstRowAgain() {
        var open by mutableStateOf(true)
        var restore by mutableStateOf(false)
        val pageRequests = mutableListOf<PlaybackOptionsPage>()
        compose.setContent {
            val context = LocalContext.current
            val loader = remember { ImageLoader(context) }
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    PlayerControlsLayer(visible = true, modalVisible = open) {
                        liveControls(loader, optionsOpen = open, restoreOptionsFocus = restore, onOptionsFocusRestored = { restore = false })
                    }
                    PlayerPanelVisibility(Unit.takeIf { open }) {
                        optionsRoot(onPageChange = { pageRequests += it })
                    }
                }
            }
        }
        compose.waitForIdle()
        val firstRow = rootRowTags.single { compose.onNodeWithTag(it).isFocusedNow() }

        compose.mainClock.autoAdvance = false
        change {
            open = false
            restore = true
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag("player-settings").assertIsFocused()

        // Reopen 32 ms into the 150 ms exit.
        change { open = true }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.onAllNodesWithTag("playback-options-overlay").assertCountEquals(1)
        compose.onNodeWithTag(firstRow).assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, pageRequests.size)
    }

    @Test fun reversingPagesWhileTheyMoveFocusesTheReturningPageAgain() {
        var page by mutableStateOf(PlaybackOptionsPage.DISPLAY)
        val pageRequests = mutableListOf<PlaybackOptionsPage>()
        val aspectChanges = mutableListOf<AspectRatioMode>()
        compose.setContent {
            TVHeadendPlayerTheme {
                optionsRoot(
                    page = page,
                    aspectRatio = AspectRatioMode.FORCE_16_9,
                    onPageChange = { pageRequests += it },
                    onAspectRatioChange = { aspectChanges += it },
                )
            }
        }
        compose.waitForIdle()
        compose.onNode(isFocused() and hasText("Fill 16:9")).assertExists()

        compose.mainClock.autoAdvance = false
        change { page = PlaybackOptionsPage.ROOT }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag("playback-options-display").assertIsFocused()

        // Back to the display page 32 ms in, while it is still leaving.
        change { page = PlaybackOptionsPage.DISPLAY }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.onNode(isFocused() and hasText("Fill 16:9")).assertExists()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf(AspectRatioMode.FORCE_16_9), aspectChanges)

        // And forward to the root again, while it is still leaving.
        change { page = PlaybackOptionsPage.ROOT }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag("playback-options-display").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf(PlaybackOptionsPage.DISPLAY), pageRequests)
    }

    @Test fun changingPageLeavesTheOutgoingPageWithoutFocusKeysOrDuplicateTags() {
        var page by mutableStateOf(PlaybackOptionsPage.DISPLAY)
        var aspectChanges = 0
        var statsChanges = 0
        compose.setContent {
            TVHeadendPlayerTheme {
                optionsRoot(
                    page = page,
                    aspectRatio = AspectRatioMode.FORCE_16_9,
                    onAspectRatioChange = { aspectChanges++ },
                    onStatsVisibleChange = { statsChanges++ },
                )
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithTag("playback-options-title").assertCountEquals(1)

        compose.mainClock.autoAdvance = false
        change { page = PlaybackOptionsPage.STATS }
        compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithTag("playback-options-title").assertCountEquals(1)
        compose.onAllNodesWithTag("playback-options-header-back").assertCountEquals(1)
        compose.onNodeWithTag("playback-options-title").assertTextEquals("Stats for nerds")

        // The new page focuses its row while the old one is still fading, and OK reaches it.
        compose.mainClock.advanceTimeByFrame()
        compose.onNode(isFocused() and hasText("Stats for nerds") and hasClickAction()).assertExists()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, statsChanges)
        assertEquals("a key reached the leaving page", 0, aspectChanges)

        compose.mainClock.advanceTimeBy(1_000)
        compose.onAllNodesWithTag("playback-options-title").assertCountEquals(1)
    }

    @Test fun timelineSnapsWhenWhatItMeasuresChangesAndGlidesWithinAProgramme() {
        var progress by mutableStateOf(0.2f)
        var key by mutableStateOf<Any>("channel-1" to 1L)
        compose.setContent {
            TVHeadendPlayerTheme {
                Box(Modifier.width(400.dp)) {
                    PlayerTimelineBlock(progress = progress, tone = PlayerTimelineTone.AMBIENT, motionKey = key)
                }
            }
        }
        compose.waitForIdle()
        val track = fill().fetchSemanticsNode().size.width / 0.2f

        compose.mainClock.autoAdvance = false
        change { progress = 0.8f }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64)
        val gliding = fill().fetchSemanticsNode().size.width / track
        assertTrue("within a programme the fill glides, was $gliding", gliding > 0.21f && gliding < 0.79f)
        compose.mainClock.advanceTimeBy(1_000)

        change {
            progress = 0.3f
            key = "channel-2" to 7L
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0.3f, fill().fetchSemanticsNode().size.width / track, 0.01f)

        change {
            progress = 0.9f
            key = "channel-2" to 8L
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0.9f, fill().fetchSemanticsNode().size.width / track, 0.01f)
    }

    @Test fun channelStepMovesTheIdentityInItsDirectionWithoutDuplicatingTags() {
        var identity by mutableStateOf(PlayerHeaderIdentity("1", 10L))
        var eyebrow by mutableStateOf("1 One")
        var direction by mutableStateOf(0)
        compose.setContent {
            val context = LocalContext.current
            val loader = remember { ImageLoader(context) }
            TVHeadendPlayerTheme {
                PlayerIdentityHeader(
                    imageLoader = loader, piconPath = null, eyebrow = eyebrow, title = "News",
                    support = null, clock = "20:00", clockSupport = null,
                    tags = PlayerHeaderTags(picon = "picon", eyebrow = "eyebrow", title = "title"),
                    identity = identity, zapDirection = direction,
                )
            }
        }
        compose.waitForIdle()
        val settledTop = eyebrowTop()

        fun step(next: PlayerHeaderIdentity, text: String, zap: Int): Float {
            compose.mainClock.autoAdvance = false
            change {
                identity = next
                eyebrow = text
                direction = zap
            }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag("eyebrow").assertCountEquals(1)
            compose.onAllNodesWithTag("picon").assertCountEquals(1)
            compose.onNodeWithTag("eyebrow").assertTextEquals(text)
            val top = eyebrowTop()
            compose.mainClock.advanceTimeBy(1_000)
            assertEquals(settledTop, eyebrowTop(), 0.01f)
            return top
        }

        assertTrue("channel up rises from below", step(PlayerHeaderIdentity("2", 20L), "2 Two", 1) > settledTop)
        assertTrue("channel down drops from above", step(PlayerHeaderIdentity("1", 10L), "1 One", -1) < settledTop)
        assertEquals("a picked channel only fades", settledTop, step(PlayerHeaderIdentity("3", 30L), "3 Three", 0), 0.01f)
        assertEquals("a programme change only fades", settledTop, step(PlayerHeaderIdentity("3", 31L), "3 Three", 1), 0.01f)
    }

    @Test fun channelMotionFollowsTheChannelIdNotItsNumberOrName() {
        var channel by mutableStateOf(ChannelId(1))
        var name by mutableStateOf("News")
        var now by mutableStateOf(1_200L)
        var direction by mutableStateOf(0)
        val programme = EpgEvent.create(
            id = EventId(5),
            channelId = ChannelId(1),
            start = Instant.fromEpochSeconds(0),
            stop = Instant.fromEpochSeconds(3_600),
            title = "Evening news",
        )
        compose.setContent {
            val context = LocalContext.current
            val loader = remember { ImageLoader(context) }
            TVHeadendPlayerTheme {
                liveControls(
                    loader, optionsOpen = false, channelId = channel, channelName = name,
                    nowEvent = programme, nowSec = now, zapDirection = direction,
                )
            }
        }
        compose.waitForIdle()
        val settledTop = identityTop()
        val track = fill().fetchSemanticsNode().size.width * 3f

        // Another channel with the same number and name: the identity steps, the timeline snaps.
        compose.mainClock.autoAdvance = false
        change {
            channel = ChannelId(2)
            direction = 1
            now = 2_400
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        assertTrue("an identical-looking channel did not step", identityTop() > settledTop)
        assertEquals(2f / 3f, fill().fetchSemanticsNode().size.width / track, 0.01f)
        compose.mainClock.advanceTimeBy(1_000)

        // New metadata for the same channel: nothing steps, the timeline glides.
        change {
            name = "News HD"
            now = 600
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        assertEquals("a renamed channel stepped", settledTop, identityTop(), 0.01f)
        compose.mainClock.advanceTimeBy(64)
        val gliding = fill().fetchSemanticsNode().size.width / track
        assertTrue("a renamed channel snapped the timeline, was $gliding", gliding > 0.2f && gliding < 0.65f)
    }

    @Test fun typedDigitsAppearAtOnceInAFixedBox() {
        var number by mutableStateOf("1")
        compose.setContent {
            TVHeadendPlayerTheme { ChannelNumberOverlay(number, Modifier.testTag("number")) }
        }
        compose.waitForIdle()
        val width = compose.onNodeWithTag("number").fetchSemanticsNode().size.width

        compose.mainClock.autoAdvance = false
        for (next in listOf("12", "123")) {
            change { number = next }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText(next).assertExists()
            compose.onNodeWithText(next.dropLast(1)).assertDoesNotExist()
            assertEquals(width, compose.onNodeWithTag("number").fetchSemanticsNode().size.width)
        }
    }

    @Test fun recoveryFocusesRetryAsItStartsToAppearAndLetsGoAtOnce() {
        var visible by mutableStateOf(false)
        var retries = 0
        compose.setContent {
            TVHeadendPlayerTheme {
                TvRecoveryOverlay(
                    visible = visible,
                    message = "Playback stopped",
                    primaryActionLabel = "Retry",
                    onPrimaryAction = { retries++ },
                )
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        change { visible = true }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        // Three frames are well inside the 200 ms entrance.
        compose.onNodeWithTag("tv-recovery-primary").assertIsFocused()

        change { visible = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("tv-recovery-overlay").assertDoesNotExist()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(0, retries)
    }

    @Test fun tuningStatusLeavesSemanticsOnItsFirstExitFrame() {
        var visible by mutableStateOf(true)
        compose.setContent {
            TVHeadendPlayerTheme { CompactTuningStatus(visible = visible, label = "Tuning", Modifier.testTag("tuning")) }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tuning").assertExists()
        compose.mainClock.autoAdvance = false
        change { visible = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("tuning").assertDoesNotExist()
        // The unmerged tree still lists cleared content, so it shows the surface is fading.
        compose.onNodeWithTag("compact-tuning-surface", useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("compact-tuning-surface", useUnmergedTree = true).assertDoesNotExist()
    }

    /** Applies a state change so the next frame sees it even while the clock is paused. */
    private fun change(block: () -> Unit) = compose.runOnIdle {
        block()
        Snapshot.sendApplyNotifications()
    }

    private fun fill(): SemanticsNodeInteraction =
        compose.onNodeWithTag("player-timeline-fill", useUnmergedTree = true)

    private fun eyebrowTop(): Float = compose.onNodeWithTag("eyebrow").fetchSemanticsNode().boundsInRoot.top

    /**
     * Opens a panel over controls, closes it and checks the first frames of the handover:
     * the panel gives up focus at once and is still fading when the invoking action has
     * focus again and takes OK.
     */
    private fun checkPanelCloseHandsFocusBack(
        panelTag: String,
        actionTag: String,
        activations: () -> Int,
        controls: @Composable (loader: ImageLoader, open: Boolean, restore: Boolean, restored: () -> Unit) -> Unit,
        panel: @Composable () -> Unit,
    ) {
        var open by mutableStateOf(true)
        var restore by mutableStateOf(false)
        var panelComposed = false
        compose.setContent {
            val context = LocalContext.current
            val loader = remember { ImageLoader(context) }
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    PlayerControlsLayer(visible = true, modalVisible = open) {
                        controls(loader, open, restore) { restore = false }
                    }
                    PlayerPanelVisibility(Unit.takeIf { open }) {
                        DisposableEffect(Unit) { panelComposed = true; onDispose { panelComposed = false } }
                        panel()
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(panelTag).assertExists()
        compose.onNode(isFocused()).assertExists()

        compose.mainClock.autoAdvance = false
        change {
            open = false
            restore = true
        }
        // Close frame: the panel leaves semantics and focus; the controls compose at alpha 0.
        compose.mainClock.advanceTimeByFrame()
        assertTrue("the panel still fades out", panelComposed)
        compose.onNodeWithTag(panelTag).assertDoesNotExist()
        // The unmerged tree still lists the cleared panel rows, so it shows none kept focus.
        compose.onNode(isFocused(), useUnmergedTree = true).assertDoesNotExist()

        // First frame the controls draw: the invoking action already has focus and takes OK.
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(actionTag).assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, activations())
        assertTrue("focus waited for the panel exit", panelComposed)

        compose.mainClock.advanceTimeBy(1_000)
        assertFalse(panelComposed)
        compose.onNodeWithTag(actionTag).assertIsFocused()
    }

    @Composable
    private fun optionsRoot(
        page: PlaybackOptionsPage = PlaybackOptionsPage.ROOT,
        aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
        onPageChange: (PlaybackOptionsPage) -> Unit = {},
        onAspectRatioChange: (AspectRatioMode) -> Unit = {},
        onStatsVisibleChange: (Boolean) -> Unit = {},
    ) {
        PlaybackOptionsSheetContent(
            page = page,
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
            tracksResolving = false,
            aspectRatio = aspectRatio,
            statsVisible = false,
            onPageChange = onPageChange,
            onAudioTrackSelected = {},
            onSubtitleTrackSelected = {},
            onAspectRatioChange = onAspectRatioChange,
            onStatsVisibleChange = onStatsVisibleChange,
        )
    }

    private val rootRowTags = listOf(
        "playback-options-audio",
        "playback-options-subtitles",
        "playback-options-display",
        "playback-options-stats",
    )

    private fun SemanticsNodeInteraction.isFocusedNow(): Boolean =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true

    private fun identityTop(): Float =
        compose.onNodeWithTag("player-channel-identity").fetchSemanticsNode().boundsInRoot.top

    @Composable
    private fun liveControls(
        loader: ImageLoader,
        optionsOpen: Boolean,
        restoreOptionsFocus: Boolean = false,
        onOptionsFocusRestored: () -> Unit = {},
        restoreInfoFocus: Boolean = false,
        onInfoFocusRestored: () -> Unit = {},
        onOpenOptions: () -> Unit = {},
        onOpenInfo: () -> Unit = {},
        channelId: ChannelId? = null,
        channelName: String = "One",
        nowEvent: EpgEvent? = null,
        nowSec: Long = 1_800,
        zapDirection: Int = 0,
    ) {
        OverlayControlsTv(
            imageLoader = loader,
            channelNumber = 1,
            channelName = channelName,
            piconPath = null,
            nowEvent = nowEvent,
            nextEvent = null,
            nowSec = nowSec,
            controlsVisible = true,
            optionsOpen = optionsOpen,
            onOpenChannels = {},
            onOpenInfo = onOpenInfo,
            onStopPlayback = {},
            onUserInteraction = {},
            onOpenOptions = onOpenOptions,
            timeshiftState = AppTimeshiftState(),
            timeshiftFeedback = null,
            onToggleTimeshiftPause = {},
            onSeekTimeshift = {},
            onGoLive = {},
            restoreInfoFocus = restoreInfoFocus,
            onInfoFocusRestored = onInfoFocusRestored,
            restoreOptionsFocus = restoreOptionsFocus,
            onOptionsFocusRestored = onOptionsFocusRestored,
            channelId = channelId,
            headerZapDirection = zapDirection,
        )
    }

    /** A focusable leaf that records focus and Enter activations. */
    private class FocusProbe {
        var focused by mutableStateOf(false)
        var activations = 0

        @Composable
        fun Target(tag: String, requestInitialFocus: Boolean = true) {
            val requester = remember { FocusRequester() }
            Box(
                Modifier
                    .size(48.dp)
                    .testTag(tag)
                    .focusRequester(requester)
                    .onFocusChanged { focused = it.isFocused }
                    .onKeyEvent {
                        if (it.key == Key.DirectionCenter && it.type == KeyEventType.KeyUp) activations++
                        false
                    }
                    .focusable(),
            )
            if (requestInitialFocus) {
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    requester.requestFocus()
                }
            }
        }
    }
}
