package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.robolectric.util.ReflectionHelpers
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isNotFocusable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.semantics.LiveRegionMode
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CacheStatistics
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.LivePauseAvailability
import at.bernhardberger.tvhplayer.playback.LivePauseState
import at.bernhardberger.tvhplayer.ui.components.drawerControlPaused
import at.bernhardberger.tvhplayer.ui.components.drawerTogglePlayWhenReady
import at.bernhardberger.tvhplayer.settings.AppLanguage
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.ui.common.isRunning
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.LiveViewerPosition
import at.bernhardberger.tvhplayer.ui.components.LocalLivePosition
import at.bernhardberger.tvhplayer.ui.components.LocalRecordingPosition
import at.bernhardberger.tvhplayer.ui.components.LocalWatchingChannel
import at.bernhardberger.tvhplayer.ui.components.LocalWatchingIndicator
import at.bernhardberger.tvhplayer.ui.components.LocalWatchingTarget
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.components.RailNowPlaying
import at.bernhardberger.tvhplayer.ui.components.RailPlaybackStatus
import at.bernhardberger.tvhplayer.ui.components.RecordingViewerPosition
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.railNowPlaying
import at.bernhardberger.tvhplayer.ui.components.railStatusLine
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import at.bernhardberger.tvhplayer.ui.components.depth.DepthFrame
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.ui.components.depth.DepthStack
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.screens.SETTINGS_ROOT
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settingsRootLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsGeneralLevels
import at.bernhardberger.tvhplayer.viewmodels.CacheClearState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Global navigation shell evidence: Material for TV drawer geometry, the
 * non-focusable brand top section, the playing slot that replaces it while
 * something plays behind browse, and the absence of any drawer-owned scrim.
 * Captures render the production composables on the 960x540 logical canvas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationShellEvidenceTest {
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun scopeTabContentDoesNotReclipLeadingOverflow() {
        compose.setContent {
            view = LocalView.current
            Box(Modifier.fillMaxSize().background(Color.Black).padding(start = 80.dp)) {
                at.bernhardberger.tvhplayer.ui.components.TabContent(
                    motion = at.bernhardberger.tvhplayer.ui.components.rememberTabContentMotion("scope"),
                    selectedKey = "scope",
                    state = { Unit },
                    modifier = Modifier.fillMaxSize(),
                ) { _, _ ->
                    Box(Modifier.fillMaxSize().drawBehind {
                        drawRect(Color.Red, topLeft = androidx.compose.ui.geometry.Offset(-80.dp.toPx(), 0f))
                    })
                }
            }
        }
        compose.waitForIdle()
        assertEquals("scope wrapper must allow content beneath the rail", Color.Red.toArgb(), drawShell().getPixel(40, 400))
    }
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Category(VisualCapture::class)
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-mdpi") fun englishNormal() = captureShell("en", 1f)
    @Category(VisualCapture::class)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi") fun germanNormal() = captureShell("de", 1f)
    @Category(VisualCapture::class)
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-mdpi") fun englishEnlarged() = captureShell("en", 1.3f)
    @Category(VisualCapture::class)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi") fun germanEnlarged() = captureShell("de", 1.3f)

    /**
     * 12 + 56 + 12 closed and 12 + 256 + 12 expanded, with Settings C keeping its
     * accepted absolute columns on the re-aligned shell.
     */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun drawerWidthsStayEightyAndTwoEightyAroundSettingsColumns() {
        settingsShell(1f)

        assertEquals(80f, drawerWidth(), .5f)
        assertEquals(130f, bounds("depth-active").left, .5f)
        assertEquals(340f, bounds("depth-active").width, .5f)
        assertEquals(562f, derivedPreviewLeft(bounds("depth-active")), .5f)

        openDrawer()

        assertEquals(280f, drawerWidth(), .5f)
        // The expanded drawer pushes the unchanged viewport: same width, 200dp over.
        assertEquals(330f, bounds("depth-active").left, .5f)
        assertEquals(340f, bounds("depth-active").width, .5f)
        assertEquals(762f, derivedPreviewLeft(bounds("depth-active")), .5f)
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun brandHeaderIsNotAFocusTargetAndEntryFocusesTheCurrentDestination() {
        settingsShell(1f)
        val appName = RuntimeEnvironment.getApplication().resources
            .getString(at.bernhardberger.tvhplayer.R.string.app_name)

        compose.onNodeWithTag("global-drawer-brand").assertExists()
        compose.onNodeWithTag("global-drawer-brand").assert(isNotFocusable())
        compose.onNodeWithTag("global-drawer-brand")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        // Like the item labels, the wordmark is only composed while expanded.
        compose.onNodeWithTag("global-drawer-wordmark").assertDoesNotExist()
        compose.onAllNodesWithContentDescription(appName).assertCountEquals(0)

        openDrawer()
        // Drawer entry still lands on the current destination, not on the mark.
        compose.onNodeWithTag("nav-settings").assertIsFocused()
        compose.onNodeWithTag("global-drawer-wordmark")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onAllNodesWithContentDescription(appName).assertCountEquals(1)

        compose.onNodeWithTag("nav-channels")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        // Up from the first destination cannot reach the brand section.
        compose.onNodeWithTag("nav-channels").assertIsFocused()
        compose.onNodeWithTag("global-drawer-brand").assert(isNotFocusable())
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun playingControlsReserveTheirHeightWhileTheDrawerIsClosed() {
        settingsShell(1f)
        fun rows() = listOf("nav-channels", "nav-epg", "nav-recordings", "nav-settings").map(::bounds)
        val brand = bounds("global-drawer-brand")
        val closed = rows()

        playLive()
        compose.onNodeWithTag("global-drawer-brand").assertDoesNotExist()
        assertEquals(brand.height + 36f, bounds("global-drawer-now-playing").height, .5f)
        val playingRows = rows()
        compose.onNodeWithTag("global-drawer-now-playing-controls").assertDoesNotExist()
        // The plate keeps the mark's axis, the icons' centre at x40.
        assertEquals(40f, bounds("global-drawer-now-playing-plate", unmerged = true).center.x, .5f)
        assertEquals(brand.center.y, bounds("global-drawer-now-playing-plate", unmerged = true).center.y, .5f)

        openDrawer()
        assertEquals(playingRows.map { it.top }, rows().map { it.top })
        compose.onNodeWithTag("global-drawer-now-playing-controls").assertExists()
        compose.runOnIdle { nowPlaying = null }
        compose.waitForIdle()
        val open = rows()
        playLive()
        assertEquals(playingRows.map { it.top }, rows().map { it.top })
        assertEquals(280f, drawerWidth(), .5f)
        compose.runOnIdle { nowPlaying = null }
        compose.waitForIdle()
        compose.onNodeWithTag("global-drawer-now-playing").assertDoesNotExist()
        compose.onNodeWithTag("global-drawer-brand").assertExists()
        assertEquals(open, rows())
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun fullScreenIsTheFirstStopAboveTheDestinationsAndReturnsToThePlayer() {
        settingsShell(1f)
        playLive()
        openDrawer()
        // Entry still lands on the current destination.
        compose.onNodeWithTag("nav-settings").assertIsFocused()

        compose.onNodeWithTag("nav-channels").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
        val visited = navigations.toList()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        compose.onNodeWithTag("global-drawer-now-playing-fullscreen").assertIsFocused()
        // Focus shows where the player is; it neither navigates nor starts anything.
        assertEquals(visited, navigations)
        assertEquals(0, opened)

        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithTag("nav-channels").assertIsFocused()

        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.runOnIdle { watchingIndicator = ChannelPlaybackIndicator.PAUSED }
        compose.waitForIdle()
        compose.onNodeWithTag("global-drawer-now-playing-glyph-paused", useUnmergedTree = true).assertExists()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(1, opened)
        assertEquals(visited, navigations)
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun focusedSlotEndingReturnsFocusToTheDrawerDestination() {
        settingsShell(1f)
        playLive()
        openDrawer()
        compose.onRoot().performKeyInput { repeat(4) { pressKey(Key.DirectionUp) } }
        compose.waitForIdle()
        compose.onNodeWithTag("global-drawer-now-playing-fullscreen").assertIsFocused()

        compose.runOnIdle { nowPlaying = null }
        compose.waitForIdle()
        compose.onNodeWithTag("global-drawer-now-playing").assertDoesNotExist()
        // Up from Settings passed Channels, so Channels is the destination the drawer stands on.
        compose.onNodeWithTag("nav-channels").assertIsFocused()
        assertEquals(280f, drawerWidth(), .5f)
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun controlRowTraversesInvokesActionsAndStopRestoresChannels() {
        settingsShell(1f)
        playLive()
        openDrawer()
        focusControl("fullscreen")
        key(Key.DirectionLeft)
        key(Key.DirectionUp)
        control("fullscreen").assertIsFocused()
        key(Key.DirectionRight)
        control("pause").assertIsFocused()
        key(Key.DirectionCenter)
        compose.onAllNodesWithContentDescription("Play").assertCountEquals(1)
        assertEquals(1, toggled)
        key(Key.DirectionCenter)
        compose.onAllNodesWithContentDescription("Pause").assertCountEquals(1)
        assertEquals(2, toggled)
        key(Key.DirectionRight)
        control("stop").assertIsFocused()
        key(Key.DirectionLeft)
        control("pause").assertIsFocused()
        listOf("fullscreen", "pause", "stop").forEach {
            focusControl(it)
            key(Key.DirectionUp)
            control(it).assertIsFocused()
            key(Key.DirectionDown)
            compose.onNodeWithTag("nav-channels").assertIsFocused()
        }
        focusControl("stop")
        key(Key.DirectionCenter)
        assertEquals(1, stopped)
        compose.onNodeWithTag("global-drawer-now-playing").assertDoesNotExist()
        compose.onNodeWithTag("nav-channels").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun pauseRemovalRestoresChannelsButRecordingsAlwaysOfferPause() {
        settingsShell(1f)
        playLive()
        openDrawer()
        focusControl("pause")
        compose.runOnIdle { pauseAvailable = false }
        compose.waitForIdle()
        control("pause").assertDoesNotExist()
        compose.onNodeWithTag("nav-channels").assertIsFocused()
        key(Key.DirectionUp)
        control("fullscreen").assertIsFocused()
        key(Key.DirectionRight)
        control("stop").assertIsFocused()
        playRecording()
        control("pause").assertExists()
        focusControl("pause")
        key(Key.DirectionCenter)
        assertEquals(1, toggled)
        compose.onAllNodesWithContentDescription("Play").assertCountEquals(1)
        focusControl("fullscreen")
        key(Key.DirectionCenter)
        assertEquals(1, opened)
        focusControl("stop")
        key(Key.DirectionCenter)
        assertEquals(1, stopped)
        compose.onNodeWithTag("nav-channels").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun rightFromStopExitsAndDrawerReentryUsesDestinationNotControls() {
        settingsShell(1f)
        playLive()
        openDrawer()
        focusControl("stop")
        key(Key.DirectionRight)
        assertEquals(80f, drawerWidth(), .5f)
        compose.onNodeWithTag("global-drawer-now-playing-controls").assertDoesNotExist()
        openDrawer()
        compose.onNodeWithTag("nav-settings").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun controlsKeepTheDrawersBackHandler() {
        settingsShell(1f)
        playLive()
        openDrawer()
        focusControl("pause")
        key(Key.Back)
        compose.waitForIdle()
        compose.onNodeWithTag("nav-channels").assertIsFocused()
        assertEquals(0, toggled)
        assertEquals(0, stopped)
        assertEquals(0, opened)
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun pendingPauseWhileStartingShowsPlayAndTheNextPressResumes() {
        settingsShell(1f)
        playLive()
        compose.runOnIdle {
            watchingIndicator = ChannelPlaybackIndicator.TUNING
            transportPause = LivePauseState(LivePauseAvailability.STARTING, selectionPending = true)
        }
        openDrawer()
        focusControl("pause")
        compose.onAllNodesWithContentDescription("Pause").assertCountEquals(1)
        key(Key.DirectionCenter)
        compose.onAllNodesWithContentDescription("Play").assertCountEquals(1)
        compose.onNodeWithTag("global-drawer-now-playing-glyph-tuning", useUnmergedTree = true).assertExists()
        assertTrue(transportPause.pending)
        key(Key.DirectionCenter)
        assertEquals(false, transportPause.pending)
        compose.onAllNodesWithContentDescription("Pause").assertCountEquals(1)
        assertEquals(2, toggled)
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun reducedMotionSettingIsReadAgainOnResumeWithoutRecreatingTheShell() {
        fun durationScale(value: Float) = ReflectionHelpers.callStaticMethod<Unit>(
            android.animation.ValueAnimator::class.java, "setDurationScale",
            ReflectionHelpers.ClassParameter.from(Float::class.javaPrimitiveType, value))
        durationScale(1f)
        animateNowPlaying = null
        try {
            settingsShell(1f)
            openDrawer()
            compose.mainClock.autoAdvance = false
            playLive()
            compose.mainClock.advanceTimeBy(2_000)
            compose.waitForIdle()
            val moving = drawShell()
            compose.mainClock.advanceTimeBy(320)
            compose.waitForIdle()
            assertTrue("playing marker animates before the setting changes", !moving.sameAs(drawShell()))
            compose.runOnIdle {
                shellLifecycle.currentState = Lifecycle.State.CREATED
                durationScale(0f)
                shellLifecycle.currentState = Lifecycle.State.RESUMED
            }
            compose.mainClock.advanceTimeBy(32)
            compose.waitForIdle()
            val still = drawShell()
            compose.mainClock.advanceTimeBy(320)
            compose.waitForIdle()
            assertTrue("resumed shell uses a static marker", still.sameAs(drawShell()))
        } finally {
            durationScale(1f)
            compose.mainClock.autoAdvance = true
        }
    }

    private fun control(name: String) = compose.onNodeWithTag("global-drawer-now-playing-$name")
    private fun focusControl(name: String) {
        control(name).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
    }
    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Category(VisualCapture::class)
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun approvedDrawerControlStates() {
        images.install(FixtureArt.imageLoader(RuntimeEnvironment.getApplication(), mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"), ArtworkId(3) to FixtureArt.picon("northline-news"))))
        settingsShell(1f)
        compose.runOnIdle { warmPlayback = true }
        playLive()
        captureNowPlaying("G-5-closed-playing")
        compose.runOnIdle { watchingIndicator = ChannelPlaybackIndicator.PAUSED }
        captureNowPlaying("closed-paused")
        compose.runOnIdle { watchingIndicator = ChannelPlaybackIndicator.PLAYING }
        openDrawer()
        focusControl("fullscreen")
        captureNowPlaying("G-1-fullscreen-focused")
        focusControl("stop")
        captureNowPlaying("G-2-stop-focused")
        compose.runOnIdle { watchingIndicator = ChannelPlaybackIndicator.PAUSED }
        focusControl("pause")
        captureNowPlaying("G-3-play-focused")
        openDrawer()
        compose.runOnIdle { watchingIndicator = ChannelPlaybackIndicator.PLAYING; pauseAvailable = false }
        captureNowPlaying("G-4-no-timeshift")
        // The bright Northline mark and recording title share the fixture's news scene.
        key(Key.DirectionRight)
        playRecording()
        assertEquals(80f, drawerWidth(), .5f)
        captureNowPlaying("R9-closed-recording-bright-logo")
    }

    @Category(VisualCapture::class)
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun approvedDrawerControlEnlarged() {
        images.install(FixtureArt.imageLoader(RuntimeEnvironment.getApplication(), mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"), ArtworkId(3) to FixtureArt.picon("northline-news"))))
        settingsShell(1.3f)
        compose.runOnIdle { warmPlayback = true }
        playLive()
        openDrawer()
        focusControl("fullscreen")
        assertEquals(100f, bounds("global-drawer-now-playing").height, .5f)
        captureNowPlaying("G-6-fullscreen-focused", fontScale = "1.3")
    }

    /** The open header at full HD with Full screen focused, live and paused. */
    @Category(VisualCapture::class)
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi")
    fun openHeaderFullHd() {
        images.install(FixtureArt.imageLoader(RuntimeEnvironment.getApplication(), mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"), ArtworkId(3) to FixtureArt.picon("northline-news"))))
        settingsShell(1f, density = 2f)
        compose.runOnIdle { warmPlayback = true }
        playLive()
        openDrawer()
        focusControl("fullscreen")
        compose.onNodeWithTag("global-drawer-now-playing-fullscreen").assertIsFocused()
        assertEquals(1920, view.width)
        assertEquals(1080, view.height)
        captureNowPlaying("1080p-open-live-fullscreen-focused")
        compose.runOnIdle { watchingIndicator = ChannelPlaybackIndicator.PAUSED }
        compose.onNodeWithTag("global-drawer-now-playing-fullscreen").assertIsFocused()
        captureNowPlaying("1080p-open-paused-fullscreen-focused")
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun nothingPlayingLeavesNoSlotToFocus() {
        settingsShell(1f)
        compose.runOnIdle { nowPlaying = railNowPlaying(liveTarget, AppPlaybackState.Finished, fixtureObservation, null, false) }
        openDrawer()
        compose.onNodeWithTag("nav-channels").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        compose.onNodeWithTag("nav-channels").assertIsFocused()
        compose.onNodeWithTag("global-drawer-now-playing").assertDoesNotExist()
    }

    /** Each state's mark, strip and announcement on the slot; line two's wording is checked below. */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun slotFollowsThePlaybackState() {
        settingsShell(1f)
        openDrawer()
        fun description() = compose.onNodeWithTag("global-drawer-now-playing-item")
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.single()
        fun marks(mark: String, strip: Boolean) {
            compose.waitForIdle()
            compose.onNodeWithTag("global-drawer-now-playing-glyph-$mark", useUnmergedTree = true).assertExists()
            // The strip's semantics stay out of the announced item, so its ink is the evidence.
            val plate = bounds("global-drawer-now-playing-plate", unmerged = true)
            val ink = Color(drawShell().getPixel(plate.left.toInt() + 6, plate.bottom.toInt() - 1))
            assertEquals("strip ink $ink", strip, channelDistance(ink, viewerColor) < 0.05f)
        }

        playLive()
        marks(mark = "playing", strip = true)
        assertEquals("Now playing: Ridge Light, 1 Ridge Earth HD, Live.", description())

        compose.runOnIdle { livePosition.value = LiveViewerPosition(liveTarget.channelId, fixtureNow - 720, behindLive = true) }
        marks(mark = "playing", strip = true)
        assertEquals("Now playing: Ridge Light, 1 Ridge Earth HD, Behind live.", description())

        compose.runOnIdle { watchingIndicator = ChannelPlaybackIndicator.PAUSED }
        marks(mark = "paused", strip = true)
        assertEquals("Now playing: Ridge Light, 1 Ridge Earth HD, Paused.", description())

        compose.runOnIdle {
            watchingIndicator = ChannelPlaybackIndicator.TUNING
            livePosition.value = null
        }
        marks(mark = "tuning", strip = false)
        assertEquals("Now playing: Ridge Light, 1 Ridge Earth HD, Tuning.", description())

        compose.runOnIdle {
            watchingIndicator = ChannelPlaybackIndicator.PLAYING
            livePosition.value = LiveViewerPosition(liveTarget.channelId, fixtureNow, behindLive = false)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            nowPlaying = railNowPlaying(liveTarget, AppPlaybackState.Recovering(
                PlaybackRecoveryReason.LIVE_ENDED, 0L), fixtureObservation, null, false)
        }
        compose.waitForIdle()
        // A reconnect's runtime then reports nothing playing; the slot keeps where the viewer was.
        compose.runOnIdle {
            watchingIndicator = ChannelPlaybackIndicator.NONE
            livePosition.value = null
        }
        marks(mark = "tuning", strip = true)
        assertEquals("Now playing: Ridge Light, 1 Ridge Earth HD, Reconnecting.", description())

        playLive()
        compose.runOnIdle { guideData = false }
        marks(mark = "playing", strip = false)
        assertEquals("Now playing: 1 Ridge Earth HD, Live.", description())

        playRecording()
        marks(mark = "playing", strip = true)
        assertEquals("Now playing: Northline Tonight, 3 Northline News, Recorded.", description())
        assertEquals(LiveRegionMode.Polite, compose.onNodeWithTag("global-drawer-now-playing-item")
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion))
    }

    /** Without a position sample the viewer is at the clock; a reconnect holds that clock with the title. */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun reconnectKeepsTheProgrammeAcrossItsEndUntilPlaybackResumes() {
        settingsShell(1f)
        fun description() = compose.onNodeWithTag("global-drawer-now-playing-item")
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.single()
        fun stripRow(): List<Int> {
            val plate = bounds("global-drawer-now-playing-plate", unmerged = true)
            val bitmap = drawShell()
            return (plate.left.toInt() + 4 until plate.right.toInt() - 4).map { bitmap.getPixel(it, plate.bottom.toInt() - 1) }
        }
        fun passMinute() {
            clock += 60
            compose.mainClock.advanceTimeBy(61_000)
            compose.waitForIdle()
        }
        playLive()
        compose.runOnIdle { livePosition.value = null }
        compose.waitForIdle()
        compose.runOnIdle {
            nowPlaying = railNowPlaying(liveTarget, AppPlaybackState.Recovering(
                PlaybackRecoveryReason.LIVE_ENDED, 0L), fixtureObservation, null, false)
        }
        compose.waitForIdle()
        compose.runOnIdle { watchingIndicator = ChannelPlaybackIndicator.NONE }
        compose.waitForIdle()
        val strip = stripRow()
        assertEquals("Now playing: Ridge Light, 1 Ridge Earth HD, Reconnecting.", description())

        // The clock passes the programme's end while the reconnect lasts.
        clock = fixtureStart.epochSeconds + 7200 + 540
        passMinute()
        assertEquals("Now playing: Ridge Light, 1 Ridge Earth HD, Reconnecting.", description())
        assertEquals(strip, stripRow())

        compose.runOnIdle {
            nowPlaying = railNowPlaying(liveTarget, AppPlaybackState.Playing, fixtureObservation, null, false)
            watchingIndicator = ChannelPlaybackIndicator.PLAYING
        }
        compose.waitForIdle()
        assertEquals("Now playing: Ridge Night, 1 Ridge Earth HD, Live.", description())
        assertTrue("the strip moves on with the programme", strip != stripRow())
    }

    /** A hold that still describes another target lends this slot none of its pause or tune. */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun heldMarkOfAnotherTargetLeavesTheSlotUnmarked() {
        settingsShell(1f)
        openDrawer()
        playLive()
        compose.runOnIdle {
            heldTarget = AppPlaybackTarget.Recording(fixtureRecording.id)
            watchingIndicator = ChannelPlaybackIndicator.PAUSED
        }
        compose.waitForIdle()
        compose.onNodeWithTag("global-drawer-now-playing-glyph-paused", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("Now playing: Ridge Light, 1 Ridge Earth HD, Live.",
            compose.onNodeWithTag("global-drawer-now-playing-item").fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.ContentDescription)?.single())

        compose.runOnIdle { heldTarget = null }
        compose.waitForIdle()
        compose.onNodeWithTag("global-drawer-now-playing-glyph-paused", useUnmergedTree = true).assertExists()
    }

    /** The mark leads line two, beside the plate rather than over the picon. */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun playbackMarkLeadsLineTwo() {
        settingsShell(1f)
        playLive()
        compose.onNodeWithTag("global-drawer-now-playing-glyph-playing", useUnmergedTree = true).assertDoesNotExist()
        openDrawer()
        val plate = bounds("global-drawer-now-playing-plate", unmerged = true)
        val text = bounds("global-drawer-now-playing-text", unmerged = true)
        val line = bounds("global-drawer-now-playing-line", unmerged = true)
        val glyph = bounds("global-drawer-now-playing-glyph-playing", unmerged = true)
        assertTrue("mark $glyph outside line two $line", line.contains(glyph.topLeft) && line.contains(glyph.center))
        assertEquals(text.left, glyph.left, .5f)
        assertTrue("line two $line not below the title in $text", line.top > text.top + 8)
        assertEquals(plate.right + 12f, glyph.left, .5f)
    }

    /** Opening, the labels come out of the plate's edge; nothing of them crosses the picon. */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun headerTextNeverCrossesThePlateWhileTheDrawerOpens() {
        settingsShell(1f)
        playLive()
        val plate = bounds("global-drawer-now-playing-plate", unmerged = true)
        fun plateRegion(bitmap: Bitmap) = (plate.left.toInt() + 2 until plate.right.toInt() - 2).flatMap { x ->
            (plate.top.toInt() + 2 until plate.bottom.toInt() - 2).map { y -> bitmap.getPixel(x, y) }
        }
        val closedPlate = plateRegion(drawShell())
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("nav-settings").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            val widths = mutableListOf<Float>()
            repeat(90) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                val text = compose.onAllNodesWithTag("global-drawer-now-playing-text", useUnmergedTree = true)
                    .fetchSemanticsNodes().singleOrNull()?.boundsInRoot ?: return@repeat
                if (text.width > 0f) {
                    assertTrue("labels at ${text.left} left of the plate's edge", text.left >= plate.right + 12f - .5f)
                    widths += text.width
                }
                assertEquals("frame $it drew over the plate", closedPlate, plateRegion(drawShell()))
            }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            val settled = bounds("global-drawer-now-playing-text", unmerged = true).width
            assertTrue("no frame caught the labels mid-entry: $widths", widths.any { it < settled - 4f })
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    /** Header is readable, not interactive; each control is one named button, never selected. */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun headerIsPassiveAndControlsAreButtonsOutsideTheDestinations() {
        settingsShell(1f)
        openDrawer()
        fun selectable() = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
            .fetchSemanticsNodes().size
        val destinations = selectable()
        playLive()
        compose.onNodeWithTag("global-drawer-now-playing-item")
            .assert(isNotFocusable())
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        listOf("fullscreen" to "Full screen", "pause" to "Pause", "stop" to "Stop").forEach { (control, label) ->
            compose.onNodeWithTag("global-drawer-now-playing-$control")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            compose.onAllNodesWithContentDescription(label).assertCountEquals(1)
        }
        assertEquals(destinations, selectable())
    }

    /** Collapsed and expanded slot in each main state, over the warm fixture still. */
    @Category(VisualCapture::class)
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun englishNowPlayingStates() {
        images.install(FixtureArt.imageLoader(RuntimeEnvironment.getApplication(), mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"), ArtworkId(3) to FixtureArt.picon("northline-news"))))
        warmPlayback = true
        settingsShell(1f)
        fun states(drawer: String) {
            playLive()
            captureNowPlaying("$drawer-live")
            compose.runOnIdle { livePosition.value = LiveViewerPosition(liveTarget.channelId, fixtureNow - 720, behindLive = true) }
            captureNowPlaying("$drawer-behind-live")
            compose.runOnIdle { watchingIndicator = ChannelPlaybackIndicator.PAUSED }
            captureNowPlaying("$drawer-paused")
            compose.runOnIdle {
                watchingIndicator = ChannelPlaybackIndicator.TUNING
                livePosition.value = null
                nowPlaying = railNowPlaying(liveTarget, AppPlaybackState.Starting, fixtureObservation, null, false)
            }
            captureNowPlaying("$drawer-tuning")
            playLive()
            compose.runOnIdle {
                nowPlaying = railNowPlaying(liveTarget, AppPlaybackState.Recovering(
                    PlaybackRecoveryReason.LIVE_ENDED, 0L), fixtureObservation, null, false)
            }
            captureNowPlaying("$drawer-reconnecting")
            playLive()
            compose.runOnIdle { guideData = false }
            captureNowPlaying("$drawer-no-guide-data")
            playRecording()
            captureNowPlaying("$drawer-recording")
        }
        states("closed")
        openDrawer()
        states("open")
        playLive()
        compose.onRoot().performKeyInput { repeat(4) { pressKey(Key.DirectionUp) } }
        compose.waitForIdle()
        compose.onNodeWithTag("global-drawer-now-playing-fullscreen").assertIsFocused()
        captureNowPlaying("open-live-focused")
        compose.runOnIdle { watchingIndicator = ChannelPlaybackIndicator.PAUSED }
        captureNowPlaying("open-paused-focused")
    }

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun lineTwoLeavesLiveAndPausedToTheMark() {
        val resources = RuntimeEnvironment.getApplication().resources
        val channel = "1 Ridge Earth HD"
        fun line(status: RailPlaybackStatus, minutes: Long? = null, on: String? = channel) =
            railStatusLine(status, minutes, on, resources)
        assertEquals("1 Ridge Earth HD", line(RailPlaybackStatus.LIVE))
        assertEquals("12m behind live • 1 Ridge Earth HD", line(RailPlaybackStatus.BEHIND_LIVE, 12))
        assertEquals("Behind live • 1 Ridge Earth HD", line(RailPlaybackStatus.BEHIND_LIVE))
        assertEquals("1 Ridge Earth HD", line(RailPlaybackStatus.PAUSED))
        assertEquals("Tuning • 1 Ridge Earth HD", line(RailPlaybackStatus.TUNING))
        assertEquals("Reconnecting • 1 Ridge Earth HD", line(RailPlaybackStatus.RECONNECTING))
        assertEquals("Recorded • 1 Ridge Earth HD", line(RailPlaybackStatus.RECORDED))
        // Without guide data the channel is the title and line two is only the state.
        assertEquals("Live", line(RailPlaybackStatus.LIVE, on = null))
        assertEquals("Paused", line(RailPlaybackStatus.PAUSED, on = null))
    }

    /**
     * The shared overlap backing falls off gradually in either drawer state,
     * and leaves the rest of the content untouched.
     */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun sharedDrawerBackingHasSoftFalloffInBothStates() {
        val contentFocus = FocusRequester()
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                Box(Modifier.fillMaxSize().background(Color.Red)) {
                    SideRail(
                        currentRoute = AppDestination.CHANNELS,
                        showEpgMenu = true,
                        onRootBack = {},
                        onNavigate = {},
                    ) { _, drawerActive ->
                        // Focusable browse content, well clear of the sampled band.
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .padding(start = 600.dp, top = 40.dp)
                                .focusRequester(contentFocus)
                                .testTag("browse-field"),
                        ) { Text("Browse") }
                        LaunchedEffect(drawerActive) {
                            if (!drawerActive) contentFocus.requestFocus()
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        assertEquals(80f, drawerWidth(), .5f)
        assertUntouchedField("closed")

        compose.onNodeWithTag("nav-channels")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()

        assertEquals(280f, drawerWidth(), .5f)
        assertUntouchedField("open")
    }

    /**
     * Kit anatomy: the icon column stays at 12 + 16 + 12 = 40dp in both states and
     * the mark shares it, so nothing in the rail moves horizontally when it
     * expands (the library alone would nudge its icon 4dp right). The wordmark
     * shares the item label's origin, and the mark is dimmed like the inactive
     * items while the drawer is closed.
     */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun nothingInTheRailMovesHorizontallyWhenTheDrawerExpands() {
        settingsShell(1f)

        val iconAxis = 12f + 16f + 12f
        assertEquals("collapsed icon axis", iconAxis, itemIconCenter(), 1f)
        assertEquals("collapsed mark axis", iconAxis, symbolCenter(), 1f)
        val dimmed = symbolPeakBlue()

        openDrawer()

        assertEquals("expanded icon axis", iconAxis, itemIconCenter(), 1f)
        assertEquals("expanded mark axis", iconAxis, symbolCenter(), 1f)
        assertEquals("wordmark on the label origin", labelInkLeft(), bounds("global-drawer-wordmark").left, 1f)
        assertTrue("mark brightens when the drawer is active ($dimmed -> ${symbolPeakBlue()})", symbolPeakBlue() > dimmed + 0.3f)
    }

    /**
     * The collapsed-rail dim is the library's own: `NavigationDrawerScope.hasFocus`
     * is `drawerState.currentValue == DrawerValue.Open`, so a closed drawer renders
     * unselected items with `inactiveContentColor` while the selected destination
     * keeps its full colour and container — the rail still reports where you are.
     * The brand mark is not an item, so it follows the same ratio explicitly.
     *
     * The selected container is asserted against the library value on the closed
     * capture only: opening the drawer also focuses that destination, so the open
     * capture samples the focus container and cannot witness a selected-state dim.
     */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun collapsedDrawerDimsUnselectedContentAndKeepsTheSelectedDestinationFull() {
        settingsShell(1f)
        val closed = drawShell()
        val iconClosed = destinationIconPeak(closed)
        val brandClosed = brandPeakBlue(closed)
        val indicatorClosed = selectedIndicator(closed)

        openDrawer()
        val open = drawShell()
        val iconOpen = destinationIconPeak(open)
        val brandOpen = brandPeakBlue(open)

        assertInactiveAlpha("unselected destination icon", iconClosed, iconOpen)
        assertInactiveAlpha("brand mark", brandClosed, brandOpen)

        val expectedIndicator = overlay(
            TvDarkColors.secondaryContainer,
            0.4f,
            TvDarkColors.background,
        )
        assertTrue(
            "closed selected indicator $indicatorClosed should be the library selected " +
                "container (expected $expectedIndicator), undimmed by any app-owned rail alpha",
            channelDistance(indicatorClosed, expectedIndicator) < 0.04f,
        )
    }

    /** Strongest blue channel inside the brand box, as drawn. */
    private fun symbolPeakBlue(): Float = brandPeakBlue(drawShell())

    private fun brandPeakBlue(bitmap: Bitmap): Float {
        val brand = bounds("global-drawer-brand")
        return (0..70).maxOf { x ->
            (brand.top.toInt()..brand.bottom.toInt()).maxOf { y -> Color(bitmap.getPixel(x, y)).blue }
        }
    }

    /**
     * Strongest channel of the unselected Channels icon. Settings is the selected
     * destination in [settingsShell], so this ink is ordinary item content.
     */
    private fun destinationIconPeak(bitmap: Bitmap): Float {
        val row = bounds("nav-channels")
        val background = bitmap.getPixel(2, 300)
        var peak = 0f
        for (x in 12..64) {
            for (y in row.top.toInt()..row.bottom.toInt()) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel == background) continue
                val color = Color(pixel)
                peak = maxOf(peak, color.red, color.green, color.blue)
            }
        }
        check(peak > 0f) { "no destination icon ink in ${row}" }
        return peak
    }

    /** Trailing interior of the selected Settings item — the pill, not the icon. */
    private fun selectedIndicator(bitmap: Bitmap): Color {
        val row = bounds("nav-settings")
        val x = (row.right - 8f).toInt()
        val y = ((row.top + row.bottom) / 2f).toInt()
        return Color(bitmap.getPixel(x, y))
    }

    /** graphicsLayer alpha over an opaque colour on [TvDarkColors.background]. */
    private fun overlay(source: Color, alpha: Float, background: Color) = Color(
        red = source.red * alpha + background.red * (1f - alpha),
        green = source.green * alpha + background.green * (1f - alpha),
        blue = source.blue * alpha + background.blue * (1f - alpha),
    )

    private fun channelDistance(a: Color, b: Color) = maxOf(
        kotlin.math.abs(a.red - b.red),
        kotlin.math.abs(a.green - b.green),
        kotlin.math.abs(a.blue - b.blue),
    )

    /**
     * Content drawn at the library's `inactiveContentColor` over the dark
     * background lands near 0.4 of the active peak, not full opacity.
     */
    private fun assertInactiveAlpha(label: String, inactive: Float, active: Float) {
        val ratio = inactive / active
        assertTrue("$label must brighten when the drawer is active ($inactive -> $active)", active > inactive + 0.08f)
        assertTrue(
            "$label inactive/active peak $ratio ($inactive / $active) should be the library " +
                "inactive content colour, not full opacity or an app-owned rail alpha",
            ratio in 0.33f..0.50f,
        )
    }

    /** Horizontal centre of the first destination's icon ink, as drawn. */
    private fun itemIconCenter(): Float {
        val bitmap = drawShell()
        val row = bounds("nav-channels")
        val rows = row.top.toInt()..row.bottom.toInt()
        val columns = (12..64).filter { x ->
            rows.any { y -> channelDistance(Color(bitmap.getPixel(x, y)), Color(bitmap.getPixel(x, 400))) > 0.03f }
        }
        check(columns.isNotEmpty()) { "no icon ink in rows $rows of ${bitmap.width}x${bitmap.height}; brand=${bounds("global-drawer-brand")}" }
        return (columns.first() + columns.last()) / 2f
    }

    /**
     * Kit vertical anatomy: 12dp edge, 56dp top section, destinations centred
     * between the top and bottom sections, 12dp item gap. Rows must not move
     * when the drawer expands.
     */
    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-mdpi")
    fun destinationsAreCentredBetweenTopAndBottomSectionsInBothStates() {
        settingsShell(1f)

        fun rows() = listOf("nav-channels", "nav-epg", "nav-recordings", "nav-settings").map { bounds(it).top }

        val brand = bounds("global-drawer-brand")
        assertEquals("top section under the 12dp edge", 12f, brand.top, 1f)
        assertEquals("top section height", 56f, brand.height, 1f)
        val collapsed = rows()
        assertEquals("settings above the 12dp edge", 540f - 12f - 48f, collapsed[3], 1f)
        assertEquals("first item row", 190f, collapsed[0], 1f)
        assertEquals("item gap", 60f, collapsed[1] - collapsed[0], 1f)
        val above = collapsed[0] - brand.bottom
        val below = collapsed[3] - (collapsed[2] + 48f)
        assertEquals("block centred between sections", above, below, 1f)

        openDrawer()

        assertEquals("rows stable across states", collapsed, rows())
    }

    /** Horizontal centre of the brand symbol's cyan ink, as drawn. */
    private fun symbolCenter(): Float {
        val bitmap = drawShell()
        val brand = bounds("global-drawer-brand")
        val columns = (0..70).filter { x ->
            (brand.top.toInt()..brand.bottom.toInt()).any { y ->
                val pixel = bitmap.getPixel(x, y)
                Color(pixel).blue > 0.35f && Color(pixel).blue > Color(pixel).red + 0.12f
            }
        }
        check(columns.isNotEmpty()) { "no symbol ink in brand $brand of ${bitmap.width}x${bitmap.height}" }
        return (columns.first() + columns.last()) / 2f
    }

    /** Leading ink column of the first destination's label in the expanded drawer. */
    private fun labelInkLeft(): Float {
        val bitmap = drawShell()
        val row = bounds("nav-channels")
        val rows = row.top.toInt()..row.bottom.toInt()
        return (60..260).first { x ->
            rows.any { y -> channelDistance(Color(bitmap.getPixel(x, y)), Color(bitmap.getPixel(x, 400))) > 0.03f }
        }.toFloat()
    }

    private fun assertUntouchedField(state: String) {
        val bitmap = drawShell()
        val end = drawerWidth().toInt() + 128
        val red = (0..end).map { Color(bitmap.getPixel(it, 400)).red }
        // 60% black at the screen edge while the rail is closed, 95% with the drawer open.
        val edge = if (state == "open") 0.03f..0.07f else 0.38f..0.42f
        assertTrue("$state backing should be translucent", red.first() in edge)
        assertTrue("$state backing must have no hard edge", red.zipWithNext().all { (a, b) -> kotlin.math.abs(b - a) < 0.02f })
        assertEquals("$state content beyond the fade", Color.Red.toArgb(), bitmap.getPixel(end + 8, 400))
    }

    /** EN/DE at 1.0 and 1.3, drawer closed and open, over no playback and warm playback. */
    private fun captureShell(locale: String, fontScale: Float) {
        val warmLabel = "warm-fixture-still"
        settingsShell(fontScale)
        val prefix = "$locale-font$fontScale"

        capture("$prefix-drawer-closed-no-playback", drawerOpen = false)
        compose.runOnIdle { warmPlayback = true }
        compose.waitForIdle()
        capture("$prefix-drawer-closed-$warmLabel", drawerOpen = false)
        openDrawer()
        capture("$prefix-drawer-open-$warmLabel", drawerOpen = true)
        compose.runOnIdle { warmPlayback = false }
        compose.waitForIdle()
        capture("$prefix-drawer-open-no-playback", drawerOpen = true)

        assertEquals(130f + 200f, bounds("depth-active").left, .5f)
    }

    /**
     * Every capture must show the same real state at every font scale: Settings is
     * the selected destination, it owns drawer focus only while the drawer is open,
     * and the shell is at its settled 80/280dp width.
     */
    private fun assertCaptureState(name: String, drawerOpen: Boolean) {
        val item = compose.onNodeWithTag("nav-settings").fetchSemanticsNode().config
        assertEquals("$name selection", true, item.getOrNull(SemanticsProperties.Selected))
        assertEquals("$name drawer focus", drawerOpen, item.getOrNull(SemanticsProperties.Focused))
        assertEquals("$name width", if (drawerOpen) 280f else 80f, drawerWidth(), .5f)
    }

    private var warmPlayback by mutableStateOf(false)

    @get:Rule val images = ImageLoaderRule()
    private val fixtureStart = Instant.parse("2026-09-15T19:00:00Z")
    private val fixtureNow = fixtureStart.epochSeconds + 1800
    private val liveTarget = AppPlaybackTarget.Live(ChannelId(1))
    private val fixtureProgramme = EpgEvent.create(EventId(1), liveTarget.channelId, fixtureStart,
        fixtureStart + 7200.seconds, title = "Ridge Light")
    private val fixtureFollowing = EpgEvent.create(EventId(2), liveTarget.channelId, fixtureStart + 7200.seconds,
        fixtureStart + 10800.seconds, title = "Ridge Night")
    /** The wall clock the slot reads; it only moves when a test moves it. */
    private var clock = fixtureNow
    private val fixtureRecording = DvrEntry.create(DvrEntryId(1), channelId = ChannelId(3), title = "Northline Tonight",
        channelName = "Northline News", start = fixtureStart - 86400.seconds, stop = fixtureStart - 82800.seconds,
        state = DvrEntryState.COMPLETED)
    private val fixtureObservation = FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(
            Channel.create(ChannelId(1), name = "Ridge Earth HD", number = 1, icon = ArtworkId(1)),
            Channel.create(ChannelId(3), name = "Northline News", number = 3, icon = ArtworkId(3)),
        ))),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create(listOf(fixtureRecording))),
    )).observation.value
    private var nowPlaying by mutableStateOf<RailNowPlaying?>(null)
    /** What the watching locals describe when an entry hold keeps an earlier target; else what plays. */
    private var heldTarget by mutableStateOf<AppPlaybackTarget?>(null)
    private var watchingIndicator by mutableStateOf(ChannelPlaybackIndicator.PLAYING)
    private var guideData by mutableStateOf(true)
    private val livePosition = mutableStateOf<LiveViewerPosition?>(null)
    private val recordingPosition = mutableStateOf<RecordingViewerPosition?>(null)
    private val navigations = mutableListOf<AppDestination>()
    private var opened = 0
    private var pauseAvailable by mutableStateOf(true)
    private var toggled = 0
    private var transportPause by mutableStateOf(LivePauseState(LivePauseAvailability.READY))
    private var stopped = 0
    private var browseBack: () -> Unit = {}
    private var viewerColor = Color.Unspecified
    private var animateNowPlaying: Boolean? = false
    private val shellOwner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }
    private val shellLifecycle get() = shellOwner.lifecycle

    /** Live on Ridge Earth at the live edge, as the runtime reports it while browse keeps it warm. */
    private fun playLive() {
        compose.runOnIdle {
            nowPlaying = railNowPlaying(liveTarget, AppPlaybackState.Playing, fixtureObservation, null, false)
            watchingIndicator = ChannelPlaybackIndicator.PLAYING
            livePosition.value = LiveViewerPosition(liveTarget.channelId, fixtureNow, behindLive = false)
            guideData = true
        }
        compose.waitForIdle()
    }

    /** The warm recording a quarter of the way in. */
    private fun playRecording() {
        compose.runOnIdle {
            nowPlaying = railNowPlaying(AppPlaybackTarget.Recording(fixtureRecording.id), AppPlaybackState.Playing,
                fixtureObservation, fixtureRecording.id, false)
            watchingIndicator = ChannelPlaybackIndicator.PLAYING
            livePosition.value = null
            recordingPosition.value = RecordingViewerPosition(fixtureRecording.id, 900_000, 3_600_000)
        }
        compose.waitForIdle()
    }

    /** Settings root on the global shell, over an offline video-like still. */
    private fun settingsShell(fontScale: Float, density: Float = 1f) {
        val still = FixtureArt.art("still-ridge-light").asImageBitmap()
        val recordingStill = FixtureArt.art("still-northline-studio").asImageBitmap()
        lateinit var navigation: DepthNavigationState
        compose.runOnUiThread { shellLifecycle.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides shellOwner,
                LocalDensity provides Density(density, fontScale),
                LocalWatchingTarget provides (heldTarget ?: nowPlaying?.target),
                LocalWatchingChannel provides ((heldTarget ?: nowPlaying?.target) as? AppPlaybackTarget.Live)?.channelId,
                LocalWatchingIndicator provides watchingIndicator,
                LocalLivePosition provides livePosition,
                LocalRecordingPosition provides recordingPosition,
            ) {
                TVHeadendPlayerTheme {
                    BackHandler { browseBack() }
                    val backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                    view = LocalView.current
                    viewerColor = MaterialTheme.colorScheme.tertiary
                    navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.GENERAL.name)
                    // Robolectric's Compose key injection stops at the view; supply the activity's
                    // unhandled-Back fallback after child key handlers have had the event.
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onKeyEvent {
                        if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                            backDispatcher.onBackPressed()
                            true
                        } else false
                    }) {
                        if (warmPlayback) {
                            Image(if (nowPlaying?.target is AppPlaybackTarget.Recording) recordingStill else still,
                                null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            WarmPlaybackScrim()
                        }
                        ProvideArtworkSession(fixtureObservation.currentSession) {
                        SideRail(
                            currentRoute = AppDestination.SETTINGS,
                            showEpgMenu = true,
                            onRootBack = {},
                            onBackHandlerChanged = { browseBack = it },
                            onNavigate = { navigations += it },
                            nowPlaying = nowPlaying,
                            livePauseAvailable = pauseAvailable,
                            nowPlayingPaused = drawerControlPaused(nowPlaying?.target, nowPlaying?.target,
                                transportPause, watchingIndicator != ChannelPlaybackIndicator.PAUSED) == true,
                            animateNowPlaying = animateNowPlaying,
                            programmeAt = { _, at ->
                                listOf(fixtureProgramme, fixtureFollowing).firstOrNull { guideData && it.isRunning(at) }
                            },
                            onOpenNowPlaying = { opened++ },
                            onToggleNowPlayingPause = {
                                toggled++
                                val playing = drawerTogglePlayWhenReady(nowPlaying?.target, nowPlaying?.target,
                                    transportPause, watchingIndicator != ChannelPlaybackIndicator.PAUSED)!!
                                if (transportPause.selectionPending) {
                                    transportPause = transportPause.copy(pending = !playing)
                                } else {
                                    watchingIndicator = if (playing) ChannelPlaybackIndicator.PLAYING else ChannelPlaybackIndicator.PAUSED
                                }
                            },
                            onStopNowPlaying = { stopped++; nowPlaying = null },
                            nowSecProvider = { clock },
                        ) { padding, drawerActive ->
                            SettingsScreenNavigation(
                                navigation,
                                listOf(settingsRootLevel()) + settingsGeneralLevels(
                                    UiSettings(), AppLanguage.SYSTEM, CacheStatistics.EMPTY,
                                    CacheClearState.IDLE, {}, {}, {},
                                ),
                                initialFocusEnabled = !drawerActive,
                                contentPadding = padding,
                            )
                        }
                        }
                    }
                }
            }
        }
        compose.runOnIdle {
            navigation.update(DepthStack(listOf(DepthFrame(SETTINGS_ROOT, SettingsSection.GENERAL.name))))
        }
        compose.waitForIdle()
    }

    private fun openDrawer() {
        compose.onNodeWithTag("nav-settings")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
    }

    private fun drawerWidth() = bounds("global-drawer-surface").width

    private fun bounds(tag: String, unmerged: Boolean = false) =
        compose.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().boundsInRoot

    /**
     * Derived constraint, not a measurement: the preview column clears its own
     * semantics, so its origin is only checked as the measured active column plus
     * the pinned depth tokens. It pins those tokens against the measured column;
     * it cannot observe where the preview is actually placed.
     */
    private fun derivedPreviewLeft(activeColumn: Rect) =
        activeColumn.left + SettingsDepthColumnWidth.value + SettingsDepthColumnGap.value

    private fun drawShell(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun capture(name: String, drawerOpen: Boolean) {
        assertCaptureState(name, drawerOpen)
        val bitmap = drawShell()
        // A second frame of the same state proves no animation was still running.
        compose.waitForIdle()
        assertTrue("$name was captured mid-animation", bitmap.sameAs(drawShell()))
        val directory = File("build/outputs/navigation-shell-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Category(VisualCapture::class)
    @Test @Config(qualifiers = PseudoLocaleEvidence.QUALIFIERS_EN_XA) fun pseudoEnXaDrawer() = capturePseudoDrawer(PseudoLocaleEvidence.EN_XA)
    @Category(VisualCapture::class)
    @Test @Config(qualifiers = PseudoLocaleEvidence.QUALIFIERS_AR_XB) fun pseudoArXbDrawer() = capturePseudoDrawer(PseudoLocaleEvidence.AR_XB)

    /** The drawer with a playing channel, closed and open, on generated pseudo-locale resources. */
    private fun capturePseudoDrawer(tag: String) {
        images.install(FixtureArt.imageLoader(RuntimeEnvironment.getApplication(), mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"), ArtworkId(3) to FixtureArt.picon("northline-news"))))
        settingsShell(1f)
        PseudoLocaleEvidence.assertGenerated(tag, view)
        compose.runOnIdle { warmPlayback = true }
        playLive()
        fun save(name: String, focus: String) {
            compose.waitForIdle()
            val bitmap = drawShell()
            compose.waitForIdle()
            assertTrue("$name was captured mid-animation", bitmap.sameAs(drawShell()))
            PseudoLocaleEvidence.mirror("drawer", "$tag-font1.0-$name", bitmap, view, 1f, "mdpi 1.0", focus,
                "Settings on the SideRail with live playback; drawerWidth=${drawerWidth()}; production SideRail")
            bitmap.recycle()
        }
        save("closed-playing", "none (drawer closed)")
        openDrawer()
        save("open-destination-focus", "nav-settings")
        focusControl("fullscreen")
        save("open-fullscreen-focus", "global-drawer-now-playing-fullscreen")
        focusControl("stop")
        save("open-stop-focus", "global-drawer-now-playing-stop")
    }

    private fun captureNowPlaying(name: String, fontScale: String = "1.0") {
        compose.waitForIdle()
        val bitmap = drawShell()
        compose.waitForIdle()
        assertTrue("$name was captured mid-animation", bitmap.sameAs(drawShell()))
        val directory = File("../build/review/drawer-controls").apply { mkdirs() }
        File(directory, "en-font$fontScale-$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

}
