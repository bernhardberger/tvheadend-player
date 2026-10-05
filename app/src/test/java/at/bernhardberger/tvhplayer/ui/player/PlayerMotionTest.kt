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
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.test.onNodeWithContentDescription
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

    @Test fun closingRecordingInfoHandsFocusAndKeysToTheCardWhileThePanelFades() {
        var infoPresses = 0
        checkPanelCloseHandsFocusBack(
            panelTag = "recording-info-panel",
            actionTag = "player-identity-card",
            activations = { infoPresses },
            controls = { _, open, restore, restored ->
                RecordingChromeFixture(
                    positionMs = 600_000, panelOpen = open, onInfo = { infoPresses++ },
                    restoreFocus = "player-identity-card".takeIf { restore },
                    onFocusRestored = { if (restore) restored() },
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
                    liveControls(loader, optionsOpen = open, restoreOptionsFocus = restore, onOptionsFocusRestored = { restore = false })
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

    @Test fun aZapKeepsVisibleControlsInPlaceWhileAViewerRevealMovesThemIn() {
        lateinit var layers: LivePlayerLayerState
        var channel by mutableStateOf(ChannelId(1))
        compose.setContent {
            val context = LocalContext.current
            val loader = remember { ImageLoader(context) }
            layers = rememberLivePlayerLayerState()
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    liveControls(
                        loader, optionsOpen = false, channelId = channel,
                        mode = if (layers.chrome.controlsVisible) PlayerChromeMode.CONTROLS else PlayerChromeMode.HIDDEN,
                        entry = layers.chrome.controlsEntry,
                    )
                }
            }
        }
        change { layers.showControls() }
        compose.waitForIdle()
        val settled = chromeTops()
        compose.mainClock.autoAdvance = false
        fun hide() {
            change { layers.chrome.hideControls() }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithTag("player-footer").assertDoesNotExist()
        }
        fun zap(to: Long) = change {
            channel = ChannelId(to)
            layers.onChannelTuneRequested()
        }

        // A zap while the controls are up does not restart their entry.
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

    @Test fun revealingTheControlsFromTheBannerSlidesItsInfoUpInsteadOfFadingInASecondCopy() {
        lateinit var layers: LivePlayerLayerState
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            val context = LocalContext.current
            val loader = remember { ImageLoader(context) }
            layers = rememberLivePlayerLayerState()
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    // One chrome: the Banner and the controls are two of its modes.
                    liveControls(
                        loader, optionsOpen = false, channelId = ChannelId(1), title = "Programme",
                        mode = playerChromeMode(layers.chrome.controlsVisible, layers.chrome.bannerVisible, stepPreview = false),
                        entry = layers.chrome.controlsEntry,
                    )
                }
            }
        }
        compose.waitForIdle()
        fun tops() = compose.onAllNodesWithTag("player-info-bar", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.top }
        compose.onNodeWithTag("player-banner").assertExists()
        val bannerTop = tops().single()
        val infoArea = compose.onNodeWithTag("player-info-bar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bannerInk = ink(view, infoArea)
        compose.mainClock.autoAdvance = false

        change { layers.showControls() }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("the Banner left at once, without a second copy", 1, tops().size)
        assertEquals("the info jumped on reveal", bannerTop, tops().single(), 0.5f)
        assertEquals("the taken-over info was not drawn on the first frame", bannerInk, ink(view, infoArea), bannerInk * 0.05f)
        compose.mainClock.advanceTimeBy(PlayerMotion.PanelMs / 2L)
        val rising = tops().single()
        assertTrue("the info did not rise gradually, was $rising", rising < bannerTop - 0.5f)
        compose.mainClock.advanceTimeBy(1_000)
        val drop = with(compose.density) { PlayerBannerDrop.toPx() }
        assertTrue("the info is not above the rising point", tops().single() < rising)
        assertEquals("the info did not settle one action row higher", bannerTop - drop, tops().single(), 0.5f)
    }

    @Test fun typedDigitsAppearAtOnceInACompactEntry() {
        var number by mutableStateOf("1")
        var target by mutableStateOf<ChannelNumberTarget>(ChannelNumberTarget.Pending)
        compose.setContent {
            val context = LocalContext.current
            val loader = remember { ImageLoader(context) }
            TVHeadendPlayerTheme { ChannelNumberOverlay(number, target, loader, null, Modifier.testTag("number")) }
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
        val digits = compose.onNodeWithText("123").fetchSemanticsNode().boundsInRoot
        // Only a known destination extends the badge; the number stays anchored in its slot.
        change { target = ChannelNumberTarget.Channel("One", null) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("One").assertExists()
        compose.mainClock.advanceTimeBy(200)
        assertTrue(compose.onNodeWithTag("number").fetchSemanticsNode().size.width > width)
        assertEquals(digits, compose.onNodeWithText("123").fetchSemanticsNode().boundsInRoot)
        change { target = ChannelNumberTarget.None }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("No channel").assertExists()
        compose.onNodeWithText("One").assertDoesNotExist()
        assertTrue(compose.onNodeWithTag("number").fetchSemanticsNode().size.width >= width)
        assertEquals(digits, compose.onNodeWithText("123").fetchSemanticsNode().boundsInRoot)
        change { target = ChannelNumberTarget.Pending }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("channel-number-target").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(200)
        assertEquals("pending has no empty destination compartment", width, compose.onNodeWithTag("number").fetchSemanticsNode().size.width)
    }

    @Test fun numberDestinationResizesWithoutMovingTheDigitsAndCanReverseMidway() {
        var target by mutableStateOf<ChannelNumberTarget>(ChannelNumberTarget.Pending)
        compose.setContent {
            val context = LocalContext.current
            val loader = remember { ImageLoader(context) }
            TVHeadendPlayerTheme { ChannelNumberOverlay("123", target, loader, null, Modifier.testTag("number")) }
        }
        compose.waitForIdle()
        fun bounds() = compose.onNodeWithTag("number").fetchSemanticsNode().boundsInRoot
        val compact = bounds()
        val digits = compose.onNodeWithText("123").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false

        change { target = ChannelNumberTarget.Channel("A long channel destination", null) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("A long channel destination").assertExists()
        compose.mainClock.advanceTimeBy(48)
        val growing = bounds()
        compose.mainClock.advanceTimeBy(200)
        val expanded = bounds()
        assertTrue("the badge grows gradually", growing.width > compact.width && growing.width < expanded.width)
        assertEquals(compact.left, growing.left, 0.5f)
        assertEquals(compact.top, growing.top, 0.5f)
        assertEquals(compact.height, growing.height, 0.5f)
        assertEquals(digits, compose.onNodeWithText("123").fetchSemanticsNode().boundsInRoot)

        change { target = ChannelNumberTarget.Pending }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("channel-number-target").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(48)
        val shrinking = bounds()
        assertTrue("the badge shrinks gradually", shrinking.width > compact.width && shrinking.width < expanded.width)

        change { target = ChannelNumberTarget.None }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("No channel").assertExists()
        compose.onNodeWithText("A long channel destination").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(200)
        assertEquals(digits, compose.onNodeWithText("123").fetchSemanticsNode().boundsInRoot)
        assertTrue(bounds().width > compact.width)

        change { target = ChannelNumberTarget.Pending }
        compose.mainClock.advanceTimeBy(200)
        assertEquals(compact, bounds())
    }

    @Test fun numberEntryKeepsItsDestinationWhileLeavingAndReopensWithTheLatestDigits() {
        var number by mutableStateOf("123")
        var target by mutableStateOf<ChannelNumberTarget>(ChannelNumberTarget.Channel("One", null))
        compose.setContent {
            val context = LocalContext.current
            val loader = remember { ImageLoader(context) }
            TVHeadendPlayerTheme { ChannelNumberOverlay(number, target, loader, null, Modifier.testTag("number")) }
        }
        compose.waitForIdle()
        val shown = compose.onNodeWithTag("number").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false

        change { number = ""; target = ChannelNumberTarget.Pending }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("123").assertExists()
        compose.onNodeWithText("One").assertExists()
        assertEquals("the complete badge fades without first collapsing", shown,
            compose.onNodeWithTag("number").fetchSemanticsNode().boundsInRoot)

        change { number = "7"; target = ChannelNumberTarget.Channel("Seven", null) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("7").assertExists()
        compose.onNodeWithText("Seven").assertExists()
        compose.onNodeWithText("123").assertDoesNotExist()
        compose.onNodeWithText("One").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("7").assertExists()

        change { number = ""; target = ChannelNumberTarget.Pending }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("7").assertDoesNotExist()
        compose.onNodeWithText("Seven").assertDoesNotExist()
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

    /** Applies a state change so the next frame sees it even while the clock is paused. */
    private fun change(block: () -> Unit) = compose.runOnIdle {
        block()
        Snapshot.sendApplyNotifications()
    }

    private fun fill(): SemanticsNodeInteraction =
        compose.onNodeWithTag("player-timeline-fill", useUnmergedTree = true)

    /** Header and footer tops of live controls. */
    private fun chromeTops(): Pair<Float, Float> = headerTop() to
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
                    // The controls host their own layer, covered while the panel is open.
                    controls(loader, open, restore) { restore = false }
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

    private fun headerTop(): Float =
        // The header slot fills the chrome, so its entry offset leaves the screen: read it unclipped.
        compose.onNodeWithTag("player-header").fetchSemanticsNode().positionInRoot.y

    @Composable
    private fun liveControls(
        loader: ImageLoader,
        optionsOpen: Boolean,
        panelOpen: Boolean = optionsOpen,
        mode: PlayerChromeMode = PlayerChromeMode.CONTROLS,
        entry: PlayerControlsEntry = PlayerControlsEntry.TRAVEL,
        restoreOptionsFocus: Boolean = false,
        onOptionsFocusRestored: () -> Unit = {},
        restoreInfoFocus: Boolean = false,
        onInfoFocusRestored: () -> Unit = {},
        onOpenOptions: () -> Unit = {},
        onOpenInfo: () -> Unit = {},
        channelId: ChannelId? = null,
        channelName: String = "One",
        title: String = "",
        nowEvent: EpgEvent? = null,
        nowSec: Long = 1_800,
        timeshiftState: AppTimeshiftState = AppTimeshiftState(),
    ) {
        PlayerChrome(
            mode = mode,
            content = PlayerChromeContent("", liveInfoBarData(1, channelName, null, null, false, nowSec, title)),
            timeline = PlayerChromeTimeline.Live(timeshiftState, nowSec, nowEvent, motionKey = channelId),
            actions = PlayerChromeActions(
                active = !optionsOpen,
                restoreFocus = when {
                    restoreInfoFocus -> "player-identity-card"
                    restoreOptionsFocus -> "player-settings"
                    else -> null
                },
            ),
            imageLoader = loader,
            currentSession = null,
            onTogglePause = {}, onSeek = {}, onStop = {}, onInteraction = {},
            onInfo = onOpenInfo,
            onOptions = onOpenOptions,
            onFocusRestored = {
                if (restoreInfoFocus) onInfoFocusRestored()
                if (restoreOptionsFocus) onOptionsFocusRestored()
            },
            entry = entry,
            panelOpen = panelOpen,
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
