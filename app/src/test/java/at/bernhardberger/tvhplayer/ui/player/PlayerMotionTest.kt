package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.components.PlaybackOptionsOverlayFrame

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.focusable
import androidx.tv.material3.MaterialTheme
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
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
import androidx.compose.ui.test.onAllNodesWithText
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
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.PlayerForegroundContext
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.PlayerSeekPreviewPhase
import at.bernhardberger.tvhplayer.core.playerChromeLayer
import at.bernhardberger.tvhplayer.core.playerForegroundLayer
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
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
            controls = { open, restore, restored ->
                liveControls(
                    optionsOpen = open,
                    restoreOptionsFocus = restore,
                    onOptionsFocusRestored = restored,
                    onOpenOptions = { settingsPresses++ },
                )
            },
            panel = { optionsRoot() },
        )
    }

    @Test fun closingRecordingInfoHandsFocusAndKeysToTheInfoActionWhileThePanelFades() {
        var infoPresses = 0
        checkPanelCloseHandsFocusBack(
            panelTag = "recording-info-panel",
            actionTag = "player-info",
            activations = { infoPresses },
            controls = { open, restore, restored ->
                RecordingChromeFixture(
                    positionMs = 600_000, panelOpen = open, onInfo = { infoPresses++ },
                    restoreFocus = "player-info".takeIf { restore },
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
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    liveControls(optionsOpen = open, restoreOptionsFocus = restore, onOptionsFocusRestored = { restore = false })
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
            layers = rememberLivePlayerLayerState()
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    liveControls(
                        optionsOpen = false, channelId = channel,
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
            layers = rememberLivePlayerLayerState()
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    // One chrome: the Banner and the controls are two of its modes.
                    liveControls(
                        optionsOpen = false, channelId = ChannelId(1), title = "Programme",
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

    @Test fun typedDigitsStandRightAlignedInAFieldThatNeverShifts() {
        var number by mutableStateOf("1")
        var target by mutableStateOf<ChannelNumberTarget>(ChannelNumberTarget.Pending)
        compose.setContent { TVHeadendPlayerTheme { ChannelNumberOverlay(number, target, Modifier.testTag("number")) } }
        compose.waitForIdle()
        fun width() = compose.onNodeWithTag("number").fetchSemanticsNode().size.width
        fun digits(text: String) = compose.onNodeWithTag("channel-number-digits").assertTextEquals(text).fetchSemanticsNode().boundsInRoot
        val pending = width()
        val end = digits("1").right

        for (next in listOf("12", "123")) {
            change { number = next }
            compose.waitForIdle()
            compose.onNodeWithText(next.dropLast(1)).assertDoesNotExist()
            assertEquals("the field is sized for the whole number at once", pending, width())
            assertEquals("$next grew from the field's end", end, digits(next).right, 0.5f)
        }
        val settled = digits("123")
        change { target = ChannelNumberTarget.Channel("123", "One", null) }
        compose.waitForIdle()
        compose.onNodeWithText("One").assertExists()
        assertTrue("the destination follows the field", width() > pending)
        assertTrue(compose.onNodeWithText("One").fetchSemanticsNode().boundsInRoot.left > settled.right)
        assertEquals(settled, digits("123"))
        change { target = ChannelNumberTarget.None }
        compose.waitForIdle()
        compose.onNodeWithText("No channel").assertExists()
        compose.onNodeWithText("One").assertDoesNotExist()
        assertEquals(settled, digits("123"))
        change { target = ChannelNumberTarget.Pending }
        compose.waitForIdle()
        compose.onNodeWithTag("channel-number-target").assertDoesNotExist()
        assertEquals("pending has no destination compartment", pending, width())
    }

    @Test fun eachDigitPushesTheEarlierOnesStartWardAndADigitMidPushCarriesOn() {
        var number by mutableStateOf("1")
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    ChannelNumberOverlay(number, ChannelNumberTarget.Pending, Modifier.testTag("number"), plate = ChannelNumberPlate.None)
                }
            }
        }
        compose.waitForIdle()
        // Tabular digits: the one digit is one cell wide, at the end of the three-digit field.
        val one = compose.onNodeWithTag("channel-number-digits").fetchSemanticsNode().boundsInRoot
        fun cell(index: Int) = Rect(one.right - (3 - index) * one.width, one.top, one.right - (2 - index) * one.width, one.bottom)
        val lastAtRest = ink(view, cell(2))
        assertTrue(lastAtRest > 0f)
        assertEquals(0f, ink(view, cell(1)))
        compose.mainClock.autoAdvance = false

        change { number = "12" }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("the earlier digit starts from where it stood", 0f, ink(view, cell(1)), lastAtRest * 0.05f)
        val arriving = ink(view, cell(2))
        compose.mainClock.advanceTimeBy(PlayerMotion.ShortMs / 2L)
        val pushing = ink(view, cell(1))
        assertTrue("the earlier digit slides start-ward: $pushing", pushing > 0f)
        assertTrue("the new digit fades in at the end: $arriving", ink(view, cell(2)) > arriving)

        // A digit mid-push: the earlier digits carry on from where they are; none is lost or snaps home.
        change { number = "123" }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("123").assertExists()
        assertEquals("the first digit did not jump to its rest", 0f, ink(view, cell(0)), lastAtRest * 0.05f)
        assertTrue("the first digit did not restart from the end", ink(view, cell(1)) > 0f)
        // The fading-in end digit stays in its cell as the next one pushes it, never end-ward past the field.
        assertEquals("nothing spills past the field's end", 0f, ink(view, cell(3)))
        compose.mainClock.advanceTimeBy(PlayerMotion.ShortMs + 100L)
        for (index in 0..2) assertTrue("cell $index holds its digit", ink(view, cell(index)) > 0f)
    }

    @Test fun aLogoLessMatchShowsItsNumberOnceInTheFieldWithoutMovingAnything() {
        var target by mutableStateOf<ChannelNumberTarget>(ChannelNumberTarget.Pending)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    ChannelNumberOverlay("12", target, Modifier.testTag("number"), plate = ChannelNumberPlate.None)
                }
            }
        }
        compose.waitForIdle()
        val row = compose.onNodeWithTag("number").fetchSemanticsNode().boundsInRoot
        val chip = with(compose.density) {
            Rect(row.left + IdentityInset.toPx(), row.center.y - ChipHeight.toPx() / 2,
                row.left + (IdentityInset + ChipWidth).toPx(), row.center.y + ChipHeight.toPx() / 2)
        }
        assertTrue("an empty tile waits for the match", ink(view, chip) > 0f)
        change { target = ChannelNumberTarget.None }
        compose.waitForIdle()
        assertTrue("no channel keeps the empty tile", ink(view, chip) > 0f)
        val digits = compose.onNodeWithTag("channel-number-digits").fetchSemanticsNode().boundsInRoot
        val name = compose.onNodeWithTag("channel-number-target").fetchSemanticsNode().boundsInRoot

        change { target = ChannelNumberTarget.Channel("12", "Harbor", null) }
        compose.waitForIdle()
        compose.onNodeWithTag("channel-number-picon", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("the chip is left empty, without a tile", 0f, ink(view, chip))
        assertEquals("the number stands once", 1, compose.onAllNodesWithText("12", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(digits, compose.onNodeWithTag("channel-number-digits").fetchSemanticsNode().boundsInRoot)
        assertEquals(name.left, compose.onNodeWithText("Harbor").fetchSemanticsNode().boundsInRoot.left, 0.5f)
        assertEquals(row.height, compose.onNodeWithTag("number").fetchSemanticsNode().boundsInRoot.height, 0.5f)
    }

    @Test fun numberEntryIsReadOutPolitelyOncePerChangeAndNotWhileLeaving() {
        var number by mutableStateOf("1")
        var target by mutableStateOf<ChannelNumberTarget>(ChannelNumberTarget.Pending)
        compose.setContent { TVHeadendPlayerTheme { ChannelNumberOverlay(number, target, Modifier.testTag("number")) } }
        compose.waitForIdle()
        fun spoken() = compose.onNodeWithTag("number").fetchSemanticsNode().config
        assertEquals(LiveRegionMode.Polite, spoken()[SemanticsProperties.LiveRegion])
        assertEquals(listOf("1"), spoken()[SemanticsProperties.ContentDescription])
        compose.mainClock.autoAdvance = false

        change { number = "12"; target = ChannelNumberTarget.Channel("12", "Harbor Sport HD", null) }
        repeat(10) { frame ->
            compose.mainClock.advanceTimeByFrame()
            assertEquals("frame $frame: one announcement while the entry moves", listOf("12, Harbor Sport HD"),
                spoken()[SemanticsProperties.ContentDescription])
        }
        // The parts are read only through the entry, never on their own.
        for (part in listOf("channel-number-digits", "channel-number-target")) {
            assertTrue(part, compose.onNodeWithTag(part, useUnmergedTree = true).fetchSemanticsNode().config
                .contains(SemanticsProperties.HideFromAccessibility))
        }
        change { number = "999"; target = ChannelNumberTarget.None }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(listOf("999, No channel"), spoken()[SemanticsProperties.ContentDescription])

        change { number = ""; target = ChannelNumberTarget.Pending }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("999").assertExists()
        assertFalse("a leaving entry is silent", spoken().contains(SemanticsProperties.LiveRegion))
        assertFalse(spoken().contains(SemanticsProperties.ContentDescription))
    }

    @Test fun plateStylesChangeOnlyColoursNeverTheLayout() {
        var plate by mutableStateOf(ChannelNumberPlate.Dark)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    ChannelNumberOverlay("12", ChannelNumberTarget.Channel("12", "Harbor", null), Modifier.testTag("number"), plate = plate)
                }
            }
        }
        val tags = listOf("number", "channel-number-target")
        val layouts = mutableMapOf<ChannelNumberPlate, List<Rect>>()
        val gapInk = mutableMapOf<ChannelNumberPlate, Float>()
        for (style in ChannelNumberPlate.entries) {
            change { plate = style }
            compose.waitForIdle()
            layouts[style] = tags.map { compose.onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot } +
                compose.onNodeWithTag("channel-number-digits").fetchSemanticsNode().boundsInRoot
            // Between the chip and the field only the plate draws.
            val row = layouts.getValue(style).first()
            val gap = with(compose.density) { row.left + (IdentityInset + ChipWidth).toPx() + 2f }
            gapInk[style] = ink(view, Rect(gap, row.center.y - 4f, gap + 10f, row.center.y + 4f))
        }
        assertEquals(layouts[ChannelNumberPlate.Dark], layouts[ChannelNumberPlate.Light])
        assertEquals(layouts[ChannelNumberPlate.Dark], layouts[ChannelNumberPlate.None])
        assertEquals("no plate draws nothing", 0f, gapInk.getValue(ChannelNumberPlate.None))
        assertTrue("the dark plate draws", gapInk.getValue(ChannelNumberPlate.Dark) > 0f)
        assertTrue("the light plate is lighter", gapInk.getValue(ChannelNumberPlate.Light) > gapInk.getValue(ChannelNumberPlate.Dark))
    }

    @Test fun aDigitWhileThePlateGrowsOrLeavesCarriesOnWithoutRestarting() {
        var number by mutableStateOf("")
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    ChannelNumberOverlay(number, ChannelNumberTarget.Pending, Modifier.testTag("number"))
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        change { number = "1" }
        compose.mainClock.advanceTimeByFrame()
        val row = compose.onNodeWithTag("number").fetchSemanticsNode().boundsInRoot
        val gapX = with(compose.density) { row.left + (IdentityInset + ChipWidth).toPx() + 2f }
        val gap = Rect(gapX, row.center.y - 4f, gapX + 10f, row.center.y + 4f)
        fun plate() = ink(view, gap)

        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationMs / 3L)
        val growing = plate()
        assertTrue("the plate grows in: $growing", growing > 0f)
        change { number = "12" }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("a digit kept the plate growing", plate() >= growing)
        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationMs + 100L)
        val rest = plate()
        assertTrue(rest > growing)

        change { number = "" }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationExitMs / 3L)
        val leaving = plate()
        assertTrue("the plate recedes: $leaving of $rest", leaving < rest)
        change { number = "5" }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("5").assertExists()
        compose.onNodeWithText("12").assertDoesNotExist()
        assertTrue("the plate turned back from where it stood", plate() >= leaving * 0.9f)
        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationMs + 100L)
        assertEquals(rest, plate(), rest * 0.01f)
    }

    @Test fun aCommittedEntrySlidesIntoTheIdentitysAlignmentAndACancelledOneFadesInPlace() {
        var number by mutableStateOf("12")
        var target by mutableStateOf<ChannelNumberTarget>(ChannelNumberTarget.Channel("12", "One", null))
        var settles by mutableStateOf(false)
        compose.setContent {
            TVHeadendPlayerTheme { ChannelNumberOverlay(number, target, Modifier.testTag("number"), settles = settles) }
        }
        compose.waitForIdle()
        fun digits() = compose.onNodeWithTag("channel-number-digits").fetchSemanticsNode().boundsInRoot
        fun name() = compose.onNodeWithText("One").fetchSemanticsNode().boundsInRoot
        val restDigits = digits()
        val restName = name()
        val advance = restDigits.width / 2
        compose.mainClock.autoAdvance = false

        // Back or the timeout without a match: the content fades where it is.
        change { number = "" }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(PlayerMotion.FastMs / 2L)
        assertEquals(restDigits, digits())
        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationExitMs + 100L)
        compose.onNodeWithTag("number").assertDoesNotExist()

        compose.mainClock.autoAdvance = true
        change { number = "12" }
        compose.waitForIdle()
        assertEquals(restDigits, digits())
        compose.mainClock.autoAdvance = false
        // Committed: the number's start moves onto the field's start (one missing digit) with the name.
        change { settles = true; number = "" }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationExitMs / 2L)
        val sliding = digits().left
        assertTrue("the number slides start-ward: $sliding", sliding < restDigits.left - 0.5f && sliding > restDigits.left - advance)
        assertEquals(restName.left - restDigits.left, name().left - digits().left, 0.5f)
        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationExitMs / 2L - 20L)
        assertEquals(restDigits.left - advance, digits().left, 2f)
        compose.mainClock.advanceTimeBy(100L)
        compose.onNodeWithTag("number").assertDoesNotExist()

        // A leading zero: "01" settles as channel 1's own number, its name where the identity's name will be.
        compose.mainClock.autoAdvance = true
        change { settles = false; target = ChannelNumberTarget.Channel("1", "One", null); number = "01" }
        compose.waitForIdle()
        val typed = digits()
        val space = name().left - typed.right
        val fieldStart = typed.right - 3 * advance
        compose.mainClock.autoAdvance = false
        change { settles = true; number = "" }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("channel-number-digits").assertTextEquals("1")
        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationExitMs - 20L)
        assertEquals("the number starts on the field's start", fieldStart, digits().left, 2f)
        assertEquals("the name one space after it", fieldStart + advance + space, name().left, 2f)
    }

    @Test fun numberEntryKeepsTheChromeItInterruptedStillAndNeverRevealsHiddenChrome() {
        var context by mutableStateOf(PlayerForegroundContext(
            confirmationVisible = false, infoVisible = false, optionsPage = null, numberEntryVisible = false,
            channelDrawerVisible = false, recoveryVisible = false, terminalErrorVisible = false,
            seekPreviewPhase = PlayerSeekPreviewPhase.NONE, controlsVisible = false, statsEnabled = false,
        ))
        var bannerVisible by mutableStateOf(false)
        var mode = PlayerChromeMode.HIDDEN
        compose.setContent {
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    // The live screen's derivation of the chrome mode and the entry's digits.
                    val chromeLayer = playerChromeLayer(context)
                    mode = playerChromeMode(
                        controls = chromeLayer == PlayerForegroundLayer.CONTROLS,
                        banner = bannerVisible && chromeLayer == PlayerForegroundLayer.NONE,
                        stepPreview = false,
                    )
                    val number = if (playerForegroundLayer(context) == PlayerForegroundLayer.NUMBER_ENTRY) "1" else ""
                    liveControls(optionsOpen = number.isNotEmpty(), panelOpen = false, mode = mode,
                        numberEntryShown = number.isNotEmpty())
                    PlayerTopIdentitySlot { ChannelNumberOverlay(number, ChannelNumberTarget.Pending, Modifier.testTag("number")) }
                }
            }
        }
        for ((expected, shown) in listOf(
            PlayerChromeMode.CONTROLS to { context = context.copy(controlsVisible = true); bannerVisible = false },
            PlayerChromeMode.BANNER to { context = context.copy(controlsVisible = false); bannerVisible = true },
            PlayerChromeMode.HIDDEN to { context = context.copy(controlsVisible = false); bannerVisible = false },
        )) {
            compose.mainClock.autoAdvance = true
            change(shown)
            compose.waitForIdle()
            assertEquals(expected, mode)
            val chrome = expected != PlayerChromeMode.HIDDEN
            val header = if (chrome) headerTop() else null
            val identity = if (chrome) compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot else null
            compose.mainClock.autoAdvance = false
            // Typed, then cleared: Back and the digit timer both clear the entry.
            for (typed in listOf(true, false)) {
                change { context = context.copy(numberEntryVisible = typed) }
                repeat(12) { frame ->
                    compose.mainClock.advanceTimeByFrame()
                    assertEquals("$expected typed=$typed frame $frame: the entry kept the chrome it found", expected, mode)
                    if (chrome) {
                        assertEquals("$expected typed=$typed frame $frame: the chrome did not travel", header!!, headerTop(), 0.5f)
                        assertEquals("$expected typed=$typed frame $frame: the identity stayed in place", identity,
                            compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
                    } else {
                        compose.onNodeWithTag("player-header", useUnmergedTree = true).assertDoesNotExist()
                    }
                }
                // The plate recedes over its whole exit.
                if (!typed) compose.mainClock.advanceTimeBy(100)
                assertEquals(typed, compose.onAllNodes(hasText("1") and hasAnyAncestor(hasTestTag("number")))
                    .fetchSemanticsNodes().isNotEmpty())
            }
        }
    }

    @Test fun theTopIdentityAndTheNumberEntryCrossInPlace() {
        var number by mutableStateOf("")
        var withEntry by mutableStateOf(false)
        lateinit var view: View
        var fieldWidth = 0
        compose.setContent {
            view = LocalView.current
            val content = PlayerChromeContent("", liveInfoBarData(LocalContext.current.resources, 1, "One", null, null, false, 0, "Programme"))
            TVHeadendPlayerTheme {
                // The entry's three-digit field, in tabular digits.
                val measurer = rememberTextMeasurer()
                val style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum")
                fieldWidth = measurer.measure("000", style, softWrap = false).size.width
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    PlayerTopIdentity(content, numberEntryShown = number.isNotEmpty())
                    if (withEntry) PlayerTopIdentitySlot {
                        ChannelNumberOverlay(number, ChannelNumberTarget.Channel("1", "One", null), Modifier.testTag("number"))
                    }
                }
            }
        }
        compose.waitForIdle()
        fun identity(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bounds = identity(PlayerTopIdentityTag)
        val chip = identity("player-top-identity-number")
        val name = identity("player-top-identity-name")
        // The identity alone, as the entry's plate covers it: it gives way quickly and returns softly.
        val rest = ink(view, name)
        assertTrue("the identity is drawn at rest", rest > 0f)
        compose.mainClock.autoAdvance = false

        change { number = "1" }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(PlayerMotion.FastMs / 2L)
        val crossing = ink(view, name)
        assertTrue("the identity fades as the entry comes in: $crossing of $rest", crossing > 0f && crossing < rest)
        compose.mainClock.advanceTimeBy(200)
        assertEquals("the identity has given way to the entry", 0f, ink(view, name), rest * 0.01f)

        change { number = "" }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(PlayerMotion.ShortMs / 2L)
        val returning = ink(view, name)
        assertTrue("the identity returns as the entry leaves: $returning of $rest", returning > 0f && returning < rest)
        compose.mainClock.advanceTimeBy(200)
        assertEquals("the identity is back", rest, ink(view, name), rest * 0.01f)

        // The entry takes the identity's place: same top edge and height, its chip on the identity's chip,
        // its field starting where the identity's number and name start.
        compose.mainClock.autoAdvance = true
        change { withEntry = true; number = "1" }
        compose.waitForIdle()
        val entry = compose.onNodeWithTag("number").fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.left, entry.left, 0.5f)
        assertEquals(bounds.top, entry.top, 0.5f)
        assertEquals(bounds.height, entry.height, 0.5f)
        val digits = compose.onNodeWithTag("channel-number-digits").fetchSemanticsNode().boundsInRoot
        assertEquals("the field starts with the identity's text", name.left, digits.right - fieldWidth, 0.5f)
        assertEquals(chip.center.y, digits.center.y, 0.5f)
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
        controls: @Composable (open: Boolean, restore: Boolean, restored: () -> Unit) -> Unit,
        panel: @Composable () -> Unit,
    ) {
        var open by mutableStateOf(true)
        var restore by mutableStateOf(false)
        var panelComposed = false
        compose.setContent {
            val context = LocalContext.current
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    // The controls host their own layer, covered while the panel is open.
                    controls(open, restore) { restore = false }
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
        // The header slot fills the chrome, so its entry offset leaves the screen: read it unclipped. Number entry
        // clears the covered chrome's semantics, so read the unmerged tree.
        compose.onNodeWithTag("player-header", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y

    @Composable
    private fun liveControls(
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
        numberEntryShown: Boolean = false,
    ) {
        PlayerChrome(
            mode = mode,
            content = PlayerChromeContent("", liveInfoBarData(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().resources, 1, channelName, null, null, false, nowSec, title)),
            timeline = PlayerChromeTimeline.Live(timeshiftState, nowSec, nowEvent, motionKey = channelId),
            actions = PlayerChromeActions(
                active = !optionsOpen,
                restoreFocus = when {
                    restoreInfoFocus -> "player-info"
                    restoreOptionsFocus -> "player-settings"
                    else -> null
                },
            ),
            onTogglePause = {}, onSeek = {}, onStop = {}, onInteraction = {},
            onInfo = onOpenInfo,
            onOptions = onOpenOptions,
            onFocusRestored = {
                if (restoreInfoFocus) onInfoFocusRestored()
                if (restoreOptionsFocus) onOptionsFocusRestored()
            },
            entry = entry,
            panelOpen = panelOpen,
            numberEntryShown = numberEntryShown,
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
