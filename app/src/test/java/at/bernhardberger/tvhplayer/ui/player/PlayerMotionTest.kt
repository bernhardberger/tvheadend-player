package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.focusable
import androidx.tv.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.semantics.SemanticsNode
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

    @Test fun channelChangeCrossfadesTheIdentityInPlaceWithoutDuplicatingTags() {
        var identity by mutableStateOf(PlayerHeaderIdentity("1", 10L))
        var eyebrow by mutableStateOf("1 One")
        var status by mutableStateOf(recording)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            val context = LocalContext.current
            val loader = remember { ImageLoader(context) }
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    PlayerIdentityHeader(
                        imageLoader = loader, piconPath = null, eyebrow = eyebrow, title = "News",
                        support = null, clock = "20:00", clockSupport = null,
                        tags = PlayerHeaderTags(picon = "picon", eyebrow = "eyebrow", title = "title"),
                        status = status,
                        identity = identity,
                    )
                }
            }
        }
        compose.waitForIdle()
        val settled = headerTops()
        val oneRight = compose.onNodeWithTag("eyebrow").fetchSemanticsNode().boundsInRoot.right
        // Where only the longer incoming name draws.
        fun newOnly() = compose.onNodeWithTag("eyebrow").fetchSemanticsNode().boundsInRoot
            .let { it.copy(left = oneRight + 8f) }

        fun drawn(tag: String) =
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

        /**
         * Steps the identity and samples 12 frames: nothing moves, one of each tag stays, the
         * shown status is exactly the incoming one's and a fading copy exactly the outgoing one's.
         * Returns whether text and status crossfaded, i.e. their outgoing content was still drawn.
         */
        fun step(next: PlayerHeaderIdentity, text: String, nextStatus: PlayerHeaderStatus): Pair<Boolean, Boolean> {
            val previousStatus = status
            compose.mainClock.autoAdvance = false
            change {
                identity = next
                eyebrow = text
                status = nextStatus
            }
            val crossfading = (1..12).map {
                compose.mainClock.advanceTimeByFrame()
                assertEquals("the header moved on frame $it", settled, headerTops())
                compose.onAllNodesWithTag("eyebrow").assertCountEquals(1)
                compose.onAllNodesWithTag("picon").assertCountEquals(1)
                compose.onAllNodesWithTag(STATUS_TAG).assertCountEquals(1)
                compose.onNodeWithTag("eyebrow").assertTextEquals(text)
                compose.onNodeWithTag(STATUS_TAG).assertContentDescriptionEquals(statusDescription(nextStatus))
                assertStatusCopies("frame $it", incoming = nextStatus, outgoing = previousStatus)
                // Outgoing content is still drawn, without semantics, while it fades.
                (drawn("eyebrow") == 2) to (drawn(STATUS_TAG) == 2)
            }
            compose.mainClock.advanceTimeBy(1_000)
            assertEquals(settled, headerTops())
            assertEquals(1, drawn("eyebrow"))
            assertEquals(1, drawn(STATUS_TAG))
            assertEquals(StatusCopies(statusTags(nextStatus)), statusCopies())
            return crossfading.any { it.first } to crossfading.any { it.second }
        }

        // A channel change crossfades picon, text and status in place: the longer incoming
        // name is drawn partly transparent where the outgoing one never was.
        compose.mainClock.autoAdvance = false
        change {
            identity = PlayerHeaderIdentity("2", 20L)
            eyebrow = "2 Two Longer Name"
            status = live
        }
        val fadeInk = (1..6).map {
            compose.mainClock.advanceTimeByFrame()
            assertEquals("the header moved on frame $it", settled, headerTops())
            assertStatusCopies("frame $it", incoming = live, outgoing = recording)
            ink(view, newOnly())
        }
        compose.mainClock.advanceTimeBy(1_000)
        val fullInk = ink(view, newOnly())
        assertTrue("the incoming name did not fade in: $fadeInk of $fullInk",
            fadeInk.any { it > 0.05f * fullInk && it < 0.95f * fullInk })
        assertEquals(settled, headerTops())

        // Back to the recording channel: REC arrives with the incoming status only.
        assertEquals("a channel step crossfades text and status", true to true,
            step(PlayerHeaderIdentity("1", 10L), "1 One", recording))
        assertEquals("a programme change crossfades the text only", true to false,
            step(PlayerHeaderIdentity("1", 11L), "1 One", recording.copy(paused = true)))
    }

    @Test fun outgoingStatusKeepsItsOwnChannelsTagsWhileItFades() {
        var channel by mutableStateOf(ChannelId(1))
        var recordingNow by mutableStateOf(true)
        compose.setContent {
            val context = LocalContext.current
            val loader = remember { ImageLoader(context) }
            TVHeadendPlayerTheme {
                liveControls(
                    loader, optionsOpen = false, channelId = channel,
                    timeshiftState = atLive, channelRecordingNow = recordingNow,
                )
            }
        }
        compose.waitForIdle()
        assertEquals(StatusCopies(incoming = setOf("Live", "REC")), statusCopies())

        /**
         * Zaps and samples 8 frames: the shown status is always exactly the incoming channel's,
         * and while both copies are drawn the fading one is exactly the outgoing channel's.
         */
        fun zap(to: ChannelId, records: Boolean, incoming: Set<String>, outgoing: Set<String>) {
            compose.mainClock.autoAdvance = false
            change {
                channel = to
                recordingNow = records
            }
            val overlap = (1..8).mapNotNull {
                compose.mainClock.advanceTimeByFrame()
                val copies = statusCopies()
                assertEquals("incoming status on frame $it", incoming, copies.incoming)
                copies.takeIf { copies.outgoing.isNotEmpty() }
            }
            assertTrue("the status did not crossfade", overlap.isNotEmpty())
            overlap.forEachIndexed { frame, copies ->
                assertEquals("status copies on overlap frame ${frame + 1}", StatusCopies(incoming, outgoing), copies)
            }
            compose.mainClock.advanceTimeBy(1_000)
            assertEquals(StatusCopies(incoming), statusCopies())
        }

        // From a channel that records to one that does not, and back: REC stays with the
        // channel that records, on the fading copy and on the incoming one.
        zap(ChannelId(2), records = false, incoming = setOf("Live"), outgoing = setOf("Live", "REC"))
        zap(ChannelId(3), records = true, incoming = setOf("Live", "REC"), outgoing = setOf("Live"))
    }

    @Test fun channelMotionFollowsTheChannelIdNotItsNumberOrName() {
        var channel by mutableStateOf(ChannelId(1))
        var name by mutableStateOf("News")
        var now by mutableStateOf(1_200L)
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
                    nowEvent = programme, nowSec = now,
                )
            }
        }
        compose.waitForIdle()
        val settledTop = identityTop()
        val track = fill().fetchSemanticsNode().size.width * 3f
        fun identities() =
            compose.onAllNodesWithTag("player-channel-identity", useUnmergedTree = true).fetchSemanticsNodes().size

        // Another channel with the same number and name: the identity crossfades in place,
        // the timeline snaps.
        compose.mainClock.autoAdvance = false
        change {
            channel = ChannelId(2)
            now = 2_400
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        assertEquals("an identical-looking channel did not crossfade", 2, identities())
        assertEquals("the identity moved on a zap", settledTop, identityTop(), 0.01f)
        assertEquals(2f / 3f, fill().fetchSemanticsNode().size.width / track, 0.01f)
        compose.mainClock.advanceTimeBy(1_000)

        // New metadata for the same channel: the identity updates in place, the timeline glides.
        change {
            name = "News HD"
            now = 600
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        assertEquals("a renamed channel crossfaded", 1, identities())
        assertEquals("a renamed channel moved", settledTop, identityTop(), 0.01f)
        compose.mainClock.advanceTimeBy(64)
        val gliding = fill().fetchSemanticsNode().size.width / track
        assertTrue("a renamed channel snapped the timeline, was $gliding", gliding > 0.2f && gliding < 0.65f)
    }

    @Test fun controlsRevealedByAZapFadeInPlaceWhileAViewerRevealMovesThemIn() {
        lateinit var layers: LivePlayerLayerState
        var channel by mutableStateOf(ChannelId(1))
        compose.setContent {
            val context = LocalContext.current
            val loader = remember { ImageLoader(context) }
            layers = rememberLivePlayerLayerState()
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    PlayerControlsLayer(
                        visible = layers.controlsVisible,
                        modalVisible = false,
                        entry = layers.controlsEntry,
                    ) { liveControls(loader, optionsOpen = false, channelId = channel) }
                }
            }
        }
        compose.waitForIdle()
        val settled = chromeTops()
        compose.mainClock.autoAdvance = false
        fun hide() {
            change { layers.hideControls() }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithTag("player-footer").assertDoesNotExist()
        }
        fun zap(to: Long) = change {
            channel = ChannelId(to)
            layers.onChannelTuneRequested()
        }

        // Hidden controls revealed by a zap fade in at their resting place.
        hide()
        zap(2)
        var drawn = 0
        repeat(14) {
            compose.mainClock.advanceTimeByFrame()
            if (compose.onAllNodesWithTag("player-footer").fetchSemanticsNodes().isNotEmpty()) {
                drawn++
                assertEquals("zap-revealed chrome moved on frame $it", settled, chromeTops())
            }
        }
        assertTrue("the zap did not reveal the controls", drawn >= 12)

        // A zap while the controls are up does not restart their entry.
        compose.mainClock.advanceTimeBy(1_000)
        zap(3)
        repeat(14) {
            compose.mainClock.advanceTimeByFrame()
            assertEquals("a zap restarted the visible controls on frame $it", settled, chromeTops())
        }

        // A viewer's reveal still moves header down and footer up into place, and a zap
        // during that entry neither restarts nor cancels it.
        hide()
        change { layers.showControls() }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        val (headerTop, footerTop) = chromeTops()
        assertTrue("the header did not come down", headerTop < settled.first)
        assertTrue("the footer did not come up", footerTop > settled.second)
        zap(4)
        compose.mainClock.advanceTimeByFrame()
        val (_, nextFooterTop) = chromeTops()
        assertTrue("a zap cancelled the entry", nextFooterTop > settled.second)
        assertTrue("a zap restarted the entry", nextFooterTop <= footerTop)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(settled, chromeTops())
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

    /** Tops of picon, eyebrow and status: what a zap must keep still. */
    /** Status tags drawn under the clock, split into the shown copy and a fading one. */
    private data class StatusCopies(val incoming: Set<String>, val outgoing: Set<String> = emptySet())

    /** Every frame shows exactly [incoming]'s tags; a fading copy, if drawn, exactly [outgoing]'s. */
    private fun assertStatusCopies(frame: String, incoming: PlayerHeaderStatus, outgoing: PlayerHeaderStatus) {
        val copies = statusCopies()
        assertEquals("incoming status on $frame", statusTags(incoming), copies.incoming)
        if (copies.outgoing.isNotEmpty()) {
            assertEquals("outgoing status on $frame", statusTags(outgoing), copies.outgoing)
        }
    }

    private fun statusCopies(): StatusCopies {
        val tags = listOf(STATUS_TAG, "player-recording-now").flatMap { tag ->
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        }
        // A fading copy sits under a cleared ancestor; each tag clears its own semantics.
        val (outgoing, incoming) = tags.partition { node ->
            generateSequence(node.parent) { it.parent }.any { it.config.isClearingSemantics }
        }
        fun labels(nodes: List<SemanticsNode>) = nodes.map {
            it.config[SemanticsProperties.ContentDescription].single().substringBefore(". ")
                .replace("Recording now", "REC")
        }.toSet()
        return StatusCopies(labels(incoming), labels(outgoing))
    }

    private fun headerTops(): List<Float> = listOf("picon", "eyebrow", STATUS_TAG).map {
        compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.top
    }

    /** Header identity and footer tops of live controls. */
    private fun chromeTops(): Pair<Float, Float> = identityTop() to
        compose.onNodeWithTag("player-footer").fetchSemanticsNode().boundsInRoot.top

    /** Summed brightness of [view] as drawn now inside [area] (mdpi: dp are pixels). */
    private fun ink(view: View, area: Rect): Float = compose.runOnIdle {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        var sum = 0f
        for (x in area.left.toInt() until area.right.toInt().coerceAtMost(bitmap.width)) {
            for (y in area.top.toInt() until area.bottom.toInt().coerceAtMost(bitmap.height)) {
                val pixel = bitmap.getPixel(x, y)
                sum += android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) +
                    android.graphics.Color.blue(pixel)
            }
        }
        bitmap.recycle()
        sum
    }

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
        timeshiftState: AppTimeshiftState = AppTimeshiftState(),
        channelRecordingNow: Boolean = false,
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
            timeshiftState = timeshiftState,
            channelRecordingNow = channelRecordingNow,
            timeshiftFeedback = null,
            onToggleTimeshiftPause = {},
            onSeekTimeshift = {},
            onGoLive = {},
            restoreInfoFocus = restoreInfoFocus,
            onInfoFocusRestored = onInfoFocusRestored,
            restoreOptionsFocus = restoreOptionsFocus,
            onOptionsFocusRestored = onOptionsFocusRestored,
            channelId = channelId,
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

    private companion object {
        const val STATUS_TAG = "player-clock-status"
        val atLive = AppTimeshiftState(available = true, timingKnown = true, bufferStartMs = -600_000)
        val recording = PlayerHeaderStatus(paused = false, timeshift = atLive, recordingNow = true)
        val live = PlayerHeaderStatus(paused = false, timeshift = atLive)

        /** Header fixtures sit at the live edge, so the status tag reads Live. */
        fun statusDescription(status: PlayerHeaderStatus) =
            "Live. " + if (status.paused) "Paused" else "Playing"

        fun statusTags(status: PlayerHeaderStatus) =
            if (status.recordingNow) setOf("Live", "REC") else setOf("Live")
    }
}
