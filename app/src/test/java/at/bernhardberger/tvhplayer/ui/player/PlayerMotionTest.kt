package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.di.PlayerStatsLevel

import at.bernhardberger.tvhplayer.testutil.drawnBrightness
import at.bernhardberger.tvhplayer.testutil.changeState
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.testutil.settleFrames
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvhplayer.ui.components.PlaybackOptionsOverlayFrame

import android.app.Application
import android.view.View
import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.focusable
import androidx.tv.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
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
import androidx.compose.ui.unit.LayoutDirection
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
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

/**
 * Player motion never holds focus, keys or semantics: leaving content gives them up on
 * its first exit frame while it keeps fading, and incoming focus does not wait for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class PlayerMotionTest {
    private val motionScale = object : MotionDurationScale { override var scaleFactor = 1f }
    @get:Rule val compose = createComposeRule(effectContext = motionScale)

    @Test fun liveIdentityTravelsToTheStripAndCanTurnBack() = identityHandoff(LayoutDirection.Ltr)

    @Test fun theIdentityLandsOnTheMirroredStrip() = identityHandoff(LayoutDirection.Rtl)

    private fun identityHandoff(direction: LayoutDirection) {
        var expansion by mutableStateOf(0f)
        var expanded by mutableStateOf(false)
        var recording by mutableStateOf(false)
        var identityFirstBaseline = 0f
        var stripFirstBaseline = 0f
        val icon = ChannelIcon.ImageCache(ArtworkId(1))
        val content = PlayerChromeContent(PlayerChromeClockSec,
            PlayerInfoBarData("1", "Ridge Earth", "", ""), picon = icon)
        compose.setContent {
            TVHeadendPlayerTheme { CompositionLocalProvider(LocalLayoutDirection provides direction) {
                val rail = rememberQuickZapRail(expanded, detached = true)
                LaunchedEffect(expansion) { rail.expansionAnimation.snapTo(expansion) }
                val measurer = rememberTextMeasurer()
                identityFirstBaseline = measurer.measure("1 Ridge Earth",
                    MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium)).firstBaseline
                stripFirstBaseline = measurer.measure("1 Ridge Earth", MaterialTheme.typography.labelLarge).firstBaseline
                PlayerPage(details = null as Unit?, rail = rail, chromeIdentityVisible = true,
                    nowPlaying = { NowPlayingStrip(ChannelId(1), "1", "Ridge Earth", icon, null, PlayerChromeClockSec) },
                    player = {
                        PlayerTopIdentity(
                            if (recording) content.copy(info = content.info.copy(
                                recording = RecordingIdentity("Recorded yesterday", "Yesterday"))) else content,
                            Modifier.fillMaxSize(),
                        )
                    }) { }
            } }
        }

        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        fun logo() = bounds("player-top-identity-logo")
        fun title() = bounds("player-top-identity-name")
        fun Rect.start() = if (direction == LayoutDirection.Rtl) right else left
        fun crosses(from: Float, to: Float, at: Float) = at in minOf(from, to)..maxOf(from, to)
        val restLogo = logo()
        val restTitle = title()
        fun identityBaseline() = title().let { it.top + identityFirstBaseline * (it.height / restTitle.height) }
        val restBaseline = identityBaseline()
        compose.changeState { expanded = true }
        val strip = bounds("player-now-playing")
        fun stripTitleBounds() =
            compose.onNodeWithText("1 Ridge Earth", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.changeState { expansion = 1f }
        val stripTitle = stripTitleBounds()
        val stripBaseline = stripTitle.top + stripFirstBaseline
        val stripLogoCenter = strip.start() + with(compose.density) {
            NowPlayingPlateWidth.toPx() / (if (direction == LayoutDirection.Rtl) -2 else 2)
        }
        if (direction == LayoutDirection.Rtl) assertTrue(restLogo.center.x > compose.onRoot()
            .fetchSemanticsNode().boundsInRoot.center.x)

        compose.changeState { expansion = 1f / 3f }
        val middleLogo = logo()
        val middleTitle = title()
        assertTrue("the logo centre travels toward the plate", crosses(stripLogoCenter, restLogo.center.x, middleLogo.center.x))
        assertTrue("text independently travels toward the strip", crosses(stripTitle.start(), restTitle.start(), middleTitle.start()))
        val middleBaseline = identityBaseline()
        assertTrue("the text's baseline rises toward the actual first line", middleBaseline in stripBaseline..<restBaseline)
        val movingStripTitle = stripTitleBounds()
        assertEquals("both names share the moving start", middleTitle.start(), movingStripTitle.start(), 0.5f)
        assertEquals("both names share the moving baseline", middleBaseline,
            movingStripTitle.top + stripFirstBaseline * movingStripTitle.height / stripTitle.height, 1f)
        assertEquals(restLogo.center.y, middleLogo.center.y, 0.5f)

        compose.changeState { expansion = 0.25f }
        assertTrue("reversing sends the same logo back toward rest", crosses(middleLogo.center.x, restLogo.center.x, logo().center.x))
        assertTrue("text reverses independently", crosses(middleTitle.start(), restTitle.start(), title().start()))
        assertTrue("the baseline reverses from its current position", identityBaseline() in middleBaseline..<restBaseline)

        compose.changeState { expansion = 1f }
        assertEquals(stripLogoCenter, logo().center.x, 0.5f)
        assertEquals(stripTitle.start(), title().start(), 0.5f)
        assertEquals(stripBaseline, identityBaseline(), 1f)
        assertEquals(strip, bounds("player-now-playing"))
        if (direction == LayoutDirection.Ltr) {
            compose.changeState { recording = true }
            compose.onNodeWithContentDescription("Recorded yesterday, Ridge Earth").assertExists()
        }
    }

    @Test fun theRailKeepsItsGeometryWhenChromeLeavesAndTheRailReopens() {
        var expanded by mutableStateOf(false)
        var chrome by mutableStateOf(true)
        lateinit var rail: QuickZapRailState
        compose.setContent {
            TVHeadendPlayerTheme {
                rail = rememberQuickZapRail(expanded, detached = true)
                PlayerPage<Unit>(null, rail = rail, chromeIdentityVisible = chrome,
                    nowPlaying = { NowPlayingStrip(ChannelId(1), "1", "Ridge Earth", null, null, PlayerChromeClockSec) },
                    player = {
                        QuickZapControls(rail, null) {
                            PlayerTopIdentity(PlayerChromeContent(PlayerChromeClockSec,
                                PlayerInfoBarData("1", "Ridge Earth", "", "")), Modifier.fillMaxSize())
                        }
                        QuickZapRail(rail, channelContent = {}, preview = {})
                    }, programme = {})
            }
        }
        fun stripTextStart() = compose.onNodeWithText("1 Ridge Earth", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.autoAdvance = false
        compose.changeState { expanded = true }
        compose.mainClock.advanceTimeByFrame()
        assertFalse(compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true)
            .fetchSemanticsNode().config.contains(SemanticsProperties.ContentDescription))
        compose.onNodeWithText("1 Ridge Earth").assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val standing = stripTextStart()
        compose.mainClock.autoAdvance = false
        compose.changeState { expanded = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Channel 1 Ridge Earth").assertExists()
        compose.onNodeWithText("1 Ridge Earth").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(PlayerMotion.MediumMs * 2L / 3)
        val closing = stripTextStart()
        assertTrue("the strip has left its endpoint", closing < standing)
        compose.changeState { chrome = false }
        compose.mainClock.advanceTimeByFrame()
        val hidden = stripTextStart()
        assertTrue("hiding chrome cannot snap the closing strip forward", hidden <= closing + 0.5f)
        compose.changeState { expanded = true }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("reopening changes direction without jumping", hidden, stripTextStart(), 10f)
    }

    @Test fun theDirectRailHasOneChannelNameAndNumberEntryTakesItsPlace() {
        var expanded by mutableStateOf(false)
        var entering by mutableStateOf(false)
        var sample by mutableStateOf(1f)
        lateinit var view: View
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                val rail = rememberQuickZapRail(expanded, detached = true)
                LaunchedEffect(sample) { if (sample < 1f || expanded) rail.expansionAnimation.snapTo(sample) }
                PlayerPage<Unit>(null, rail = rail, numberEntryShown = entering,
                    nowPlaying = { NowPlayingStrip(ChannelId(1), "1", "Ridge Earth", null, null, PlayerChromeClockSec) },
                    player = {
                        val scrim = LocalPlayerPageScrim.current
                        androidx.compose.runtime.SideEffect { scrim?.alpha = { 1f } }
                        QuickZapRail(rail, channelContent = {}, preview = {})
                    }, programme = {})
            }
        }
        compose.changeState { expanded = true }
        val strip = compose.onNodeWithTag("player-now-playing")
        val bounds = strip.fetchSemanticsNode().boundsInRoot
        fun titleBounds() = compose.onNodeWithText("1 Ridge Earth", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val standing = titleBounds()
        compose.changeState { sample = 1f / 3f }
        assertEquals("a direct visit never travels", standing, titleBounds())
        compose.changeState { sample = 1f }
        val spoken = strip.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }.filter(String::isNotBlank)
        val visible = compose.drawnBrightness(view, bounds)
        compose.mainClock.autoAdvance = false
        compose.changeState { entering = true }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("1 Ridge Earth").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(PlayerMotion.FastMs / 2L)
        assertEquals("one channel name; entry must not pop the strip",
            listOf("1 Ridge Earth") to true, spoken to (compose.drawnBrightness(view, bounds) in 1f..<visible))
        compose.mainClock.advanceTimeBy(PlayerMotion.FastMs.toLong())
        assertEquals(0f, compose.drawnBrightness(view, bounds), 0f)
    }

    @Test fun hidingControlsDropsFocusSemanticsAndKeysOnTheFirstExitFrame() {
        var visible by mutableStateOf(true)
        val probe = FocusProbe()
        var mounts = 0
        var disposals = 0
        var frameFocused = false
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            TVHeadendPlayerTheme {
                PlayerControlsLayer(visible = visible, modalVisible = false,
                    modifier = Modifier.onFocusChanged { frameFocused = it.hasFocus }) {
                    DisposableEffect(Unit) { mounts++; onDispose { disposals++ } }
                    probe.Target("controls-action")
                }
            }
        }
        compose.waitForIdle()
        val action = compose.onNodeWithTag("controls-action").assertIsFocused()
        val area = action.fetchSemanticsNode().boundsInRoot
        val shown = compose.drawnBrightness(view, area)
        assertTrue(shown > 0f)

        compose.mainClock.autoAdvance = false
        compose.changeState { visible = false }
        compose.mainClock.advanceTimeByFrame()

        assertEquals("controls still fade out", 0, disposals)
        assertTrue("the exiting controls still draw", compose.drawnBrightness(view, area) > 0f)
        assertFalse("the leaving action keeps focus", probe.focused)
        compose.onNodeWithTag("controls-action").assertDoesNotExist()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(0, probe.activations)

        compose.settleFrames(1, PlayerMotion.MediumMs + 64L)
        assertEquals("hidden controls draw nothing", 0f, compose.drawnBrightness(view, area), 0f)
        assertFalse("the idle frame must release its focus sink", frameFocused)
        assertEquals("hidden controls must retain their composition", 0, disposals)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        assertFalse("focus entered the hidden controls", probe.focused)
        assertEquals(0, probe.activations)
        compose.onNodeWithTag("controls-action").assertDoesNotExist()

        compose.changeState { visible = true }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("controls-action").assertExists()
        assertEquals("the controls were mounted again on reveal", 1, mounts)
    }

    @Test fun theRevealOnTheSettledExitFrameStartsTransparent() {
        var closing by mutableStateOf(false)
        var motion by mutableStateOf<PlayerControlsMotion?>(null)
        var didReopen by mutableStateOf(false)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            // Feed back the completed exit before any idle composition can reset the entry.
            val reopened = didReopen || motion?.transition?.currentState == EnterExitState.PostExit
            androidx.compose.runtime.SideEffect { if (reopened) didReopen = true }
            PlayerControlsLayer(visible = !closing || reopened, modalVisible = false) {
                val current = requireNotNull(LocalPlayerControlsMotion.current)
                androidx.compose.runtime.SideEffect { motion = current }
                Box(Modifier.size(48.dp).background(Color.White))
            }
        }
        compose.waitForIdle()
        val area = Rect(0f, 0f, 48f, 48f)
        assertTrue(compose.drawnBrightness(view, area) > 0f)
        compose.mainClock.autoAdvance = false
        compose.changeState { closing = true }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.advanceTimeBy(PlayerMotion.MediumMs - 32L)
        val firstVisitFrames = buildList {
            repeat(7) {
                compose.mainClock.advanceTimeByFrame()
                if (didReopen && size < 2) {
                    add(compose.drawnBrightness(view, area))
                }
            }
        }
        assertEquals("sample the settled-exit reveal", 2, firstVisitFrames.size)
        assertEquals("a fresh entry cannot show the previous visit's ink", listOf(0f, 0f), firstVisitFrames)
    }

    @Test fun theInitialHiddenControlsDoNotBuildUntilNeeded() {
        var visible by mutableStateOf(false)
        var mounts = 0
        compose.setContent {
            PlayerControlsLayer(visible = visible, modalVisible = false) {
                DisposableEffect(Unit) { mounts++; onDispose {} }
            }
        }
        compose.waitForIdle()
        assertEquals("opening the player does not build hidden controls", 0, mounts)
        compose.changeState { visible = true }
        compose.waitForIdle()
        assertEquals(1, mounts)
    }

    @Test fun theInitialHiddenControlsWaitForOneUninterruptedSettledTune() {
        var token by mutableStateOf<Int?>(1)
        var mounts = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlayerControlsLayer(visible = false, modalVisible = false, settledTuneToken = { token }) {
                DisposableEffect(Unit) { mounts++; onDispose {} }
            }
        }
        compose.waitForIdle()

        compose.mainClock.advanceTimeBy(900, ignoreFrameDuration = true)
        assertEquals("the first tune has not settled long enough", 0, mounts)
        compose.changeState { token = 2 }
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        assertEquals("a different tune cannot finish the first tune's wait", 0, mounts)

        compose.changeState { token = null }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals("losing eligibility cancels prebuild", 0, mounts)

        compose.changeState { token = 3 }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(900, ignoreFrameDuration = true)
        assertEquals("the eligible tune still has to wait the full second", 0, mounts)
        compose.mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
        // The wait has elapsed; let Compose apply the resulting mount.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals("one uninterrupted second builds the hidden controls", 1, mounts)
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
        compose.changeState {
            open = false
            restore = true
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag("player-settings").assertIsFocused()

        // Reopen 32 ms into the 150 ms exit.
        compose.changeState { open = true }
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
        compose.changeState { page = PlaybackOptionsPage.ROOT }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag("playback-options-display").assertIsFocused()

        // Back to the display page 32 ms in, while it is still leaving.
        compose.changeState { page = PlaybackOptionsPage.DISPLAY }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.onNode(isFocused() and hasText("Fill 16:9")).assertExists()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf(AspectRatioMode.FORCE_16_9), aspectChanges)

        // And forward to the root again, while it is still leaving.
        compose.changeState { page = PlaybackOptionsPage.ROOT }
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
                    onStatsLevelChange = { statsChanges++ },
                )
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithTag("playback-options-title").assertCountEquals(1)

        compose.mainClock.autoAdvance = false
        compose.changeState { page = PlaybackOptionsPage.STATS }
        compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithTag("playback-options-title").assertCountEquals(1)
        compose.onAllNodesWithTag("playback-options-header-back").assertCountEquals(1)
        compose.onNodeWithTag("playback-options-title").assertTextEquals("Stats for nerds")

        // The new page focuses its row while the old one is still fading, and OK reaches it.
        compose.mainClock.advanceTimeByFrame()
        compose.onNode(isFocused() and hasText("Off") and hasClickAction()).assertExists()
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
        compose.changeState { progress = 0.8f }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64)
        val gliding = fill().fetchSemanticsNode().size.width / track
        assertTrue("within a programme the fill glides, was $gliding", gliding > 0.21f && gliding < 0.79f)
        compose.mainClock.advanceTimeBy(1_000)

        compose.changeState {
            progress = 0.3f
            key = "channel-2" to 7L
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0.3f, fill().fetchSemanticsNode().size.width / track, 0.01f)

        compose.changeState {
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
            layers = rememberLivePlayerLayerState()
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    liveControls(
                        optionsOpen = false, channelId = channel,
                        mode = if (layers.core.chrome.controlsVisible) PlayerChromeMode.CONTROLS else PlayerChromeMode.HIDDEN,
                        entry = layers.core.chrome.controlsEntry,
                    )
                }
            }
        }
        compose.changeState { layers.showControls() }
        compose.waitForIdle()
        val settled = chromeTops()
        compose.mainClock.autoAdvance = false
        fun hide() {
            compose.changeState { layers.core.chrome.hideControls() }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithTag("player-footer").assertDoesNotExist()
        }
        fun zap(to: Long) = compose.changeState {
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
        compose.changeState { layers.showControls() }
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
            layers = rememberLivePlayerLayerState()
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    // One chrome: the Banner and the controls are two of its modes.
                    liveControls(
                        optionsOpen = false, channelId = ChannelId(1), title = "Programme",
                        mode = playerChromeMode(layers.core.chrome.controlsVisible, layers.core.chrome.bannerVisible, stepPreview = false),
                        entry = layers.core.chrome.controlsEntry,
                    )
                }
            }
        }
        compose.waitForIdle()
        fun tops() = compose.onAllNodesWithTag("player-info-bar", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.top }
        compose.onNodeWithTag("player-banner").assertExists()
        val bannerTop = tops().single()
        val infoArea = compose.onNodeWithTag("player-info-bar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bannerInk = compose.drawnBrightness(view, infoArea)
        compose.mainClock.autoAdvance = false

        compose.changeState { layers.showControls() }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("the Banner left at once, without a second copy", 1, tops().size)
        assertEquals("the info jumped on reveal", bannerTop, tops().single(), 0.5f)
        assertEquals("the taken-over info was not drawn on the first frame", bannerInk, compose.drawnBrightness(view, infoArea), bannerInk * 0.05f)
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
            compose.changeState { number = next }
            compose.waitForIdle()
            compose.onNodeWithText(next.dropLast(1)).assertDoesNotExist()
            assertEquals("the field is sized for the whole number at once", pending, width())
            assertEquals("$next grew from the field's end", end, digits(next).right, 0.5f)
        }
        val settled = digits("123")
        compose.changeState { target = ChannelNumberTarget.Channel("123", "One", null) }
        compose.waitForIdle()
        compose.onNodeWithText("One").assertExists()
        assertTrue("the destination follows the field", width() > pending)
        assertTrue(compose.onNodeWithText("One").fetchSemanticsNode().boundsInRoot.left > settled.right)
        assertEquals(settled, digits("123"))
        compose.changeState { target = ChannelNumberTarget.None }
        compose.waitForIdle()
        compose.onNodeWithText("No channel").assertExists()
        compose.onNodeWithText("One").assertDoesNotExist()
        assertEquals(settled, digits("123"))
        compose.changeState { target = ChannelNumberTarget.Pending }
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
        val lastAtRest = compose.drawnBrightness(view, cell(2))
        assertTrue(lastAtRest > 0f)
        assertEquals(0f, compose.drawnBrightness(view, cell(1)))
        compose.mainClock.autoAdvance = false

        compose.changeState { number = "12" }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("the earlier digit starts from where it stood", 0f, compose.drawnBrightness(view, cell(1)), lastAtRest * 0.05f)
        val arriving = compose.drawnBrightness(view, cell(2))
        compose.mainClock.advanceTimeBy(PlayerMotion.ShortMs / 2L)
        val pushing = compose.drawnBrightness(view, cell(1))
        assertTrue("the earlier digit slides start-ward: $pushing", pushing > 0f)
        assertTrue("the new digit fades in at the end: $arriving", compose.drawnBrightness(view, cell(2)) > arriving)

        // A digit mid-push: the earlier digits carry on from where they are; none is lost or snaps home.
        compose.changeState { number = "123" }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("123").assertExists()
        assertEquals("the first digit did not jump to its rest", 0f, compose.drawnBrightness(view, cell(0)), lastAtRest * 0.05f)
        assertTrue("the first digit did not restart from the end", compose.drawnBrightness(view, cell(1)) > 0f)
        // The fading-in end digit stays in its cell as the next one pushes it, never end-ward past the field.
        assertEquals("nothing spills past the field's end", 0f, compose.drawnBrightness(view, cell(3)))
        compose.mainClock.advanceTimeBy(PlayerMotion.ShortMs + 100L)
        for (index in 0..2) assertTrue("cell $index holds its digit", compose.drawnBrightness(view, cell(index)) > 0f)
    }

    @Test fun reducedMotionSnapsAppendedDigitsToRestOnTheNextFrame() {
        checkReducedMotionDigitAppend(interrupt = false)
    }

    @Test fun reducedMotionSnapsAnInterruptedDigitAppendToRestOnTheNextFrame() {
        checkReducedMotionDigitAppend(interrupt = true)
    }

    private fun checkReducedMotionDigitAppend(interrupt: Boolean) {
        var number by mutableStateOf("1")
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    ChannelNumberOverlay(number, ChannelNumberTarget.Pending, plate = ChannelNumberPlate.None)
                }
            }
        }
        compose.waitForIdle()
        val one = compose.onNodeWithTag("channel-number-digits").fetchSemanticsNode().boundsInRoot
        fun cell(index: Int) = Rect(one.right - (3 - index) * one.width, one.top, one.right - (2 - index) * one.width, one.bottom)
        val firstAtRest = compose.drawnBrightness(view, cell(2))
        assertTrue(firstAtRest > 0f)
        compose.mainClock.autoAdvance = false

        if (interrupt) {
            compose.changeState { number = "12" }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(48L)
            val sliding = compose.drawnBrightness(view, cell(1))
            assertTrue("the append to interrupt is still sliding: $sliding of $firstAtRest", sliding in 1f..(firstAtRest * 0.95f))
        }
        compose.changeState {
            motionScale.scaleFactor = 0f
            number = if (interrupt) "123" else "12"
        }
        compose.mainClock.advanceTimeByFrame()
        val occupied = (3 - number.length)..2
        val nextFrame = occupied.map { compose.drawnBrightness(view, cell(it)) }
        assertEquals("the earlier digit snaps straight to its rest cell", firstAtRest, nextFrame.first(), firstAtRest * 0.01f)
        assertTrue("every digit is drawn at rest on the next frame: $nextFrame", nextFrame.all { it > 0f })
        compose.mainClock.advanceTimeBy(PlayerMotion.ShortMs + 100L)
        occupied.forEachIndexed { index, position ->
            assertEquals("digit $index did not move after the next frame", nextFrame[index], compose.drawnBrightness(view, cell(position)), firstAtRest * 0.01f)
        }
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
        assertTrue("an empty tile waits for the match", compose.drawnBrightness(view, chip) > 0f)
        compose.changeState { target = ChannelNumberTarget.None }
        compose.waitForIdle()
        assertTrue("no channel keeps the empty tile", compose.drawnBrightness(view, chip) > 0f)
        val digits = compose.onNodeWithTag("channel-number-digits").fetchSemanticsNode().boundsInRoot
        val name = compose.onNodeWithTag("channel-number-target").fetchSemanticsNode().boundsInRoot

        compose.changeState { target = ChannelNumberTarget.Channel("12", "Harbor", null) }
        compose.waitForIdle()
        compose.onNodeWithTag("channel-number-picon", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("the chip is left empty, without a tile", 0f, compose.drawnBrightness(view, chip))
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

        compose.changeState { number = "12"; target = ChannelNumberTarget.Channel("12", "Harbor Sport HD", null) }
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
        compose.changeState { number = "999"; target = ChannelNumberTarget.None }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(listOf("999, No channel"), spoken()[SemanticsProperties.ContentDescription])

        compose.changeState { number = ""; target = ChannelNumberTarget.Pending }
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
            compose.changeState { plate = style }
            compose.waitForIdle()
            layouts[style] = tags.map { compose.onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot } +
                compose.onNodeWithTag("channel-number-digits").fetchSemanticsNode().boundsInRoot
            // Between the chip and the field only the plate draws.
            val row = layouts.getValue(style).first()
            val gap = with(compose.density) { row.left + (IdentityInset + ChipWidth).toPx() + 2f }
            gapInk[style] = compose.drawnBrightness(view, Rect(gap, row.center.y - 4f, gap + 10f, row.center.y + 4f))
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
        compose.changeState { number = "1" }
        compose.mainClock.advanceTimeByFrame()
        val row = compose.onNodeWithTag("number").fetchSemanticsNode().boundsInRoot
        val gapX = with(compose.density) { row.left + (IdentityInset + ChipWidth).toPx() + 2f }
        val gap = Rect(gapX, row.center.y - 4f, gapX + 10f, row.center.y + 4f)
        fun plate() = compose.drawnBrightness(view, gap)

        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationMs / 3L)
        val growing = plate()
        assertTrue("the plate grows in: $growing", growing > 0f)
        compose.changeState { number = "12" }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("a digit kept the plate growing", plate() >= growing)
        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationMs + 100L)
        val rest = plate()
        assertTrue(rest > growing)

        compose.changeState { number = "" }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationExitMs / 3L)
        val leaving = plate()
        assertTrue("the plate recedes: $leaving of $rest", leaving < rest)
        compose.changeState { number = "5" }
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
        compose.changeState { number = "" }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(PlayerMotion.FastMs / 2L)
        assertEquals(restDigits, digits())
        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationExitMs + 100L)
        compose.onNodeWithTag("number").assertDoesNotExist()

        compose.mainClock.autoAdvance = true
        compose.changeState { number = "12" }
        compose.waitForIdle()
        assertEquals(restDigits, digits())
        compose.mainClock.autoAdvance = false
        // Committed without a logo: the number and name move to the keyline together.
        compose.changeState { settles = true; number = "" }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationExitMs / 2L)
        val sliding = digits().left
        val keyline = restDigits.left - advance - ChipWidth.value - IdentityTextGap.value
        assertTrue("the number slides start-ward: $sliding", sliding < restDigits.left - 0.5f && sliding > keyline)
        assertEquals(restName.left - restDigits.left, name().left - digits().left, 0.5f)
        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationExitMs / 2L - 20L)
        assertEquals(keyline, digits().left, 2f)
        compose.mainClock.advanceTimeBy(100L)
        compose.onNodeWithTag("number").assertDoesNotExist()

        // A leading zero: "01" settles as channel 1's own number, its name where the identity's name will be.
        compose.mainClock.autoAdvance = true
        compose.changeState { settles = false; target = ChannelNumberTarget.Channel("1", "One", null); number = "01" }
        compose.waitForIdle()
        val typed = digits()
        val space = name().left - typed.right
        val fieldStart = typed.right - 3 * advance - ChipWidth.value - IdentityTextGap.value
        compose.mainClock.autoAdvance = false
        compose.changeState { settles = true; number = "" }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("channel-number-digits").assertTextEquals("1")
        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationExitMs - 20L)
        assertEquals("the number starts on the keyline", fieldStart, digits().left, 2f)
        assertEquals("the name one space after it", fieldStart + advance + space, name().left, 2f)
    }

    @Test fun aCommittedNumberMovesToTheMirroredIdentityWithOrWithoutALogo() {
        var number by mutableStateOf("12")
        var picon by mutableStateOf<ChannelIcon?>(null)
        compose.setContent {
            TVHeadendPlayerTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                PlayerTopIdentity(PlayerChromeContent(PlayerChromeClockSec,
                    PlayerInfoBarData("12", "One", "", ""), picon = picon), Modifier.fillMaxSize())
                PlayerTopIdentitySlot {
                    ChannelNumberOverlay(number, ChannelNumberTarget.Channel("12", "One", picon), settles = number.isEmpty())
                }
            } }
        }
        fun right(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.right
        for (icon in listOf(null, ChannelIcon.ImageCache(ArtworkId(1)))) {
            compose.mainClock.autoAdvance = true
            compose.changeState { picon = icon; number = "12" }
            val identity = right("player-top-identity-name")
            assertTrue(identity > compose.onRoot().fetchSemanticsNode().boundsInRoot.center.x)
            compose.mainClock.autoAdvance = false
            compose.changeState { number = "" }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(PlayerMotion.DestinationExitMs - 20L)
            assertEquals(identity, right("channel-number-digits"), 2f)
        }
    }

    @Test fun numberEntryKeepsTheChromeItInterruptedStillAndNeverRevealsHiddenChrome() {
        var context by mutableStateOf(PlayerForegroundContext(
            confirmationVisible = false, infoVisible = false, optionsPage = null, numberEntryVisible = false,
            channelDrawerVisible = false, recoveryVisible = false, terminalErrorVisible = false,
            seekPreviewPhase = PlayerSeekPreviewPhase.NONE, controlsVisible = false,
        ))
        var bannerVisible by mutableStateOf(false)
        var mode = PlayerChromeMode.HIDDEN
        lateinit var view: View
        var chromeFooter = Rect.Zero
        var chromeClock = Rect.Zero
        compose.setContent {
            view = LocalView.current
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
            compose.changeState(shown)
            compose.waitForIdle()
            assertEquals(expected, mode)
            val chrome = expected != PlayerChromeMode.HIDDEN
            if (expected == PlayerChromeMode.CONTROLS) {
                chromeFooter = compose.onNodeWithTag("player-footer", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                // At the right of the header, outside the channel-number overlay.
                chromeClock = compose.onNodeWithTag("player-top-cluster", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                assertTrue("sample an area painted by the controls", compose.drawnBrightness(view, chromeFooter) > 0f)
                assertTrue("sample an area painted by the clock", compose.drawnBrightness(view, chromeClock) > 0f)
            }
            val header = if (chrome) headerTop() else null
            val identity = if (chrome) compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot else null
            compose.mainClock.autoAdvance = false
            // Typed, then cleared: Back and the digit timer both clear the entry.
            for (typed in listOf(true, false)) {
                compose.changeState { context = context.copy(numberEntryVisible = typed) }
                repeat(12) { frame ->
                    compose.mainClock.advanceTimeByFrame()
                    assertEquals("$expected typed=$typed frame $frame: the entry kept the chrome it found", expected, mode)
                    if (chrome) {
                        assertEquals("$expected typed=$typed frame $frame: the chrome did not travel", header!!, headerTop(), 0.5f)
                        assertEquals("$expected typed=$typed frame $frame: the identity stayed in place", identity,
                            compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
                    } else {
                        compose.onNodeWithTag("player-header").assertDoesNotExist()
                        assertEquals("digits cannot paint the hidden clock on frame $frame", 0f,
                            compose.drawnBrightness(view, chromeClock), 0f)
                        assertEquals("digits cannot paint the hidden footer on frame $frame", 0f,
                            compose.drawnBrightness(view, chromeFooter), 0f)
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
        var settles by mutableStateOf(false)
        lateinit var view: View
        var fieldWidth = 0
        compose.setContent {
            view = LocalView.current
            // With a logo: without one the identity has no chip for the entry's chip to stand on.
            val content = PlayerChromeContent(0L, liveInfoBarData(LocalContext.current.resources, 1, "One", null, null, 0, "Programme"),
                picon = ChannelIcon.parse("imagecache/2"))
            TVHeadendPlayerTheme {
                // The entry's three-digit field, in tabular digits.
                val measurer = rememberTextMeasurer()
                val style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum")
                fieldWidth = measurer.measure("000", style, softWrap = false).size.width
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    PlayerTopIdentity(content, numberEntryShown = number.isNotEmpty())
                    if (withEntry) PlayerTopIdentitySlot {
                        ChannelNumberOverlay(number, ChannelNumberTarget.Channel("1", "One", content.picon),
                            Modifier.testTag("number"), settles = settles)
                    }
                }
            }
        }
        compose.waitForIdle()
        fun identity(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bounds = identity(PlayerTopIdentityTag)
        val chip = identity("player-top-identity-logo")
        val name = identity("player-top-identity-name")
        // The identity alone, as the entry's plate covers it: it gives way quickly and returns softly.
        val rest = compose.drawnBrightness(view, name)
        assertTrue("the identity is drawn at rest", rest > 0f)
        compose.mainClock.autoAdvance = false

        compose.changeState { number = "1" }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(PlayerMotion.FastMs / 2L)
        val crossing = compose.drawnBrightness(view, name)
        assertTrue("the identity fades as the entry comes in: $crossing of $rest", crossing > 0f && crossing < rest)
        compose.mainClock.advanceTimeBy(200)
        assertEquals("the identity has given way to the entry", 0f, compose.drawnBrightness(view, name), rest * 0.01f)

        compose.changeState { number = "" }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(PlayerMotion.ShortMs / 2L)
        val returning = compose.drawnBrightness(view, name)
        assertTrue("the identity returns as the entry leaves: $returning of $rest", returning > 0f && returning < rest)
        compose.mainClock.advanceTimeBy(200)
        assertEquals("the identity is back", rest, compose.drawnBrightness(view, name), rest * 0.01f)

        // The entry takes the identity's place: same top edge and height, its chip on the identity's chip,
        // its field starting where the identity's number and name start.
        compose.mainClock.autoAdvance = true
        compose.changeState { withEntry = true; number = "1" }
        compose.waitForIdle()
        val entry = compose.onNodeWithTag("number").fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.left, entry.left, 0.5f)
        assertEquals(bounds.top, entry.top, 0.5f)
        assertEquals(bounds.height, entry.height, 0.5f)
        val digits = compose.onNodeWithTag("channel-number-digits").fetchSemanticsNode().boundsInRoot
        assertEquals("the field starts with the identity's text", name.left, digits.right - fieldWidth, 0.5f)
        assertEquals(chip.center.y, digits.center.y, 0.5f)

        compose.mainClock.autoAdvance = false
        compose.changeState { settles = true; number = "" }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationExitMs - 20L)
        val settlingDigits = compose.onNodeWithTag("channel-number-digits").fetchSemanticsNode().boundsInRoot
        assertEquals("a committed digit lands on the identity's text start", name.left, settlingDigits.left, 1f)
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
        compose.changeState { visible = true }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        // Three frames are well inside the 200 ms entrance.
        compose.onNodeWithTag("tv-recovery-primary").assertIsFocused()

        compose.changeState { visible = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("tv-recovery-overlay").assertDoesNotExist()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(0, retries)
    }

    private fun fill(): SemanticsNodeInteraction =
        compose.onNodeWithTag("player-timeline-fill", useUnmergedTree = true)

    /** Header and footer tops of live controls. */
    private fun chromeTops(): Pair<Float, Float> = headerTop() to
        compose.onNodeWithTag("player-footer").fetchSemanticsNode().boundsInRoot.top


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
        compose.changeState {
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
        onStatsLevelChange: (PlayerStatsLevel) -> Unit = {},
    ) {
        PlaybackOptionsSheetContent(
            page = page,
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
            tracksResolving = false,
            aspectRatio = aspectRatio,
            statsLevel = PlayerStatsLevel.OFF,
            onPageChange = onPageChange,
            onAudioTrackSelected = {},
            onSubtitleTrackSelected = {},
            onAspectRatioChange = onAspectRatioChange,
            onStatsLevelChange = onStatsLevelChange,
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
        onOpenOptions: () -> Unit = {},
        channelId: ChannelId? = null,
        title: String = "",
        numberEntryShown: Boolean = false,
    ) {
        PlayerChrome(
            mode = mode,
            content = PlayerChromeContent(0L, liveInfoBarData(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().resources, 1, "One", null, null, 1_800, title)),
            timeline = PlayerChromeTimeline.Live(AppTimeshiftState(), 1_800, null, motionKey = channelId),
            actions = PlayerChromeActions(
                active = !optionsOpen,
                restoreFocus = when {
                    restoreOptionsFocus -> "player-settings"
                    else -> null
                },
            ),
            onTogglePause = {}, onSeek = {}, onStop = {}, onInteraction = {},
            onInfo = {},
            onOptions = onOpenOptions,
            onFocusRestored = {
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
                    .background(Color.White)
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
