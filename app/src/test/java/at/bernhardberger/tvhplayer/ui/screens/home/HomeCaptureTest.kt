package at.bernhardberger.tvhplayer.ui.screens.home

import androidx.compose.ui.test.assertIsDisplayed

import androidx.compose.ui.test.onNodeWithText

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performKeyInput
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.ContentCategory
import at.bernhardberger.tvheadend.sdk.core.ContentGenre
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgCoverage
import at.bernhardberger.tvheadend.sdk.core.EpgEpisode
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRating
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.HomeInput
import at.bernhardberger.tvhplayer.core.HomePlaying
import at.bernhardberger.tvhplayer.core.HomeSectionKind
import androidx.compose.animation.core.CubicBezierEasing
import at.bernhardberger.tvhplayer.core.homeInitialFocus
import at.bernhardberger.tvhplayer.core.homeSections
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.LocalWarmPlayback
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.WarmPlaybackScrim
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.LiveViewerPosition
import at.bernhardberger.tvhplayer.ui.components.LocalLivePosition
import at.bernhardberger.tvhplayer.ui.components.LocalWatchingChannel
import at.bernhardberger.tvhplayer.ui.components.LocalWatchingIndicator
import at.bernhardberger.tvhplayer.ui.components.LocalWatchingTarget
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.components.RowTravelEasing
import at.bernhardberger.tvhplayer.ui.components.RowTravelMs
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.railNowPlaying
import at.bernhardberger.tvhplayer.ui.screens.ChannelsLayoutFixture
import kotlin.time.Duration.Companion.minutes
import kotlin.math.roundToInt
import kotlin.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone

/**
 * S5 Home page captures: the production [HomeContent] page driven by the real
 * [homeSections] policy over the fictional fixture pack, at 20:15. Each capture focuses the same
 * card the approved mockup captured, so the PNGs compare state by state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = Application::class,
    qualifiers = "en-w960dp-h540dp-land-xhdpi",
    instrumentedPackages = [
        "at.bernhardberger.tvhplayer.ui.screens",
        "at.bernhardberger.tvhplayer.ui.screens.home",
        "at.bernhardberger.tvhplayer.ui.common",
    ],
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTvheadendApi::class, TvheadendTestingApi::class)
class HomeCaptureTest {
    @get:Rule
    val images = ImageLoaderRule()

    @get:Rule
    val compose = createComposeRule()

    private lateinit var view: View
    private val out = File(System.getProperty("home.capture.out") ?: "build/outputs/home")
        .apply { mkdirs() }
    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @Category(VisualCapture::class)
    @Test
    fun browse() = atEvening {
        show(Kind.BROWSE)
        capture("1-default", "home-recent-0")
        key(Key.DirectionDown)
        capture("2-on-now", "home-onnow-0")
        key(Key.DirectionDown)
        capture("3-genres", "home-genre-0")
        key(Key.DirectionDown)
        capture("4-continue", "home-shelf-0")
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        capture("4b-upcoming", "home-upcoming-0")
    }

    /** The recent-channels row's tiles and its "All channels" end card, the card unfocused and then focused. */
    @Category(VisualCapture::class)
    @Test
    fun recentChannelsEndCard() = atEvening {
        show(Kind.BROWSE)
        settle("home-recent-0")
        repeat(7) { key(Key.DirectionRight) }
        capture("recent-end-card-unfocused", "home-recent-7")
        key(Key.DirectionRight)
        capture("recent-end-card-focused", "home-all-channels")
    }

    /** Store listing candidates: the same page as the mockup states, under the store's locale and output folder. */
    @Category(VisualCapture::class)
    @Test @Config(qualifiers = "en-rUS-w960dp-h540dp-land-xhdpi")
    fun storeHome() = atEvening {
        val presentation = File("build/outputs/presentation").apply { mkdirs() }
        show(Kind.BROWSE)
        capture("../presentation/store-home-recent", "home-recent-0")
        key(Key.DirectionDown)
        capture("../presentation/store-home-onnow", "home-onnow-0")
        assertTrue(presentation.isDirectory)
    }

    @Category(VisualCapture::class)
    @Test @Config(qualifiers = "en-rUS-w960dp-h540dp-land-xhdpi")
    fun storeHomeWarm() = atEvening {
        File("build/outputs/presentation").mkdirs()
        show(Kind.WARM)
        capture("../presentation/store-home-warm", "home-activity-0")
    }

    @Category(VisualCapture::class)
    @Test
    fun warm() = atEvening {
        show(Kind.WARM)
        capture("5-warm", "home-activity-0")
    }

    @Category(VisualCapture::class)
    @Test fun rowRailOverlap() = atEvening {
        show(Kind.BROWSE)
        settle("home-recent-0")
        key(Key.DirectionDown)
        settle("home-onnow-0")
        key(Key.DirectionRight)
        capture("row-rail-overlap", "home-onnow-1")
        assertTrue("focused card clears mask", bounds("home-onnow-1").left >= 130.dp)
    }

    @Test fun theBackdropsFadeReachesTheScreenEdgeUnderTheRail() = atEvening {
        show(Kind.BROWSE)
        settle("home-recent-0")
        // A layer no wider than the content would leave a band under the rail while it fades.
        assertEquals(0f, bounds("home-immersive-background").left.value, 0.5f)
    }

    @Test fun aCardLeavingUnderTheRailIsMutedAsAWhole() = atEvening {
        show(Kind.BROWSE)
        settle("home-recent-0")
        key(Key.DirectionDown)
        settle("home-onnow-0")
        val scale = context.resources.displayMetrics.density
        // The brightest of the card's visible middle line, clear of the focus ring and corners.
        fun cardBrightness(): Float {
            val card = bounds("home-onnow-0")
            val bitmap = draw()
            val y = (((card.top.value + card.bottom.value) / 2) * scale).toInt()
            val from = ((card.right.value - 90f).coerceAtLeast(0f) * scale).toInt()
            val brightest = (from until ((card.right.value - 8f) * scale).toInt()).maxOf {
                val pixel = androidx.compose.ui.graphics.Color(bitmap.getPixel(it, y))
                maxOf(pixel.red, pixel.green, pixel.blue)
            }
            bitmap.recycle()
            return brightest
        }
        val atTheKeyline = cardBrightness()
        key(Key.DirectionRight)
        settle("home-onnow-1")
        assertTrue("the card has left the keyline for the rail", bounds("home-onnow-0").right < 130.dp)
        val muted = cardBrightness()
        assertTrue("the leaving card is muted: $muted vs $atTheKeyline", muted < atTheKeyline * 0.35f)
        assertTrue("and still there, right to the screen edge: $muted", muted > atTheKeyline * 0.05f)
        // Fainter still past the keyline in a row without focus, whose own dimming counts towards it.
        key(Key.DirectionUp)
        settle("home-recent-0")
        val inADimmedRow = cardBrightness()
        assertTrue("a dimmed row's card is fainter: $inADimmedRow vs $muted", inADimmedRow < muted)
    }

    @Test fun infoBottomStaysAnchoredDuringTextHeightChange() = atEvening {
        show(Kind.BROWSE, infoLineCounts = true)
        settle("home-recent-0")
        key(Key.DirectionDown)
        settle("home-onnow-0")
        val bottom = bounds("channels-immersive-next").bottom.value
        compose.mainClock.autoAdvance = false
        listOf(Key.DirectionRight, Key.DirectionLeft).forEach { direction ->
            key(direction)
            repeat(16) {
                compose.mainClock.advanceTimeBy(16)
                compose.waitForIdle()
                val footers = compose.onAllNodes(hasTestTag("channels-immersive-next"), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                assertTrue("the info footer remains present", footers.isNotEmpty())
                footers.forEach { node ->
                    assertEquals("outgoing and incoming footer stay bottom anchored", bottom,
                        node.boundsInRoot.bottom / context.resources.displayMetrics.density, 1f)
                }
            }
            compose.mainClock.advanceTimeBy(600)
        }
    }

    @Category(VisualCapture::class)
    @Test fun warmWithoutArtwork() = atEvening {
        show(Kind.WARM, noArtwork = true)
        capture("17-warm-no-art", "home-activity-0")
    }

    @Category(VisualCapture::class)
    @Test fun warmWithoutArtworkLargeFont() = atEvening {
        show(Kind.WARM, noArtwork = true, fontScale = 1.3f)
        capture("18-warm-no-art-font-130", "home-activity-0")
    }

    @Category(VisualCapture::class)
    @Test
    fun firstRun() = atEvening {
        show(Kind.FIRST_RUN)
        capture("6-first-run", "home-onnow-0")
        assertTop("home-onnow-0", HomeImmersiveSlot.value)
    }

    @Category(VisualCapture::class)
    @Test fun optionsPanel() = atEvening {
        show(Kind.BROWSE, screen = true)
        settle("home-recent-0")
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        key(Key.Enter)
        capture("11-options", "home-view-CURRENT_ACTIVITY")
    }

    @Category(VisualCapture::class)
    @Test fun loadingFirstMetadata() = atEvening {
        show(Kind.LOADING, screen = true)
        compose.onNodeWithTag("home-loading").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1_700)
        compose.onNodeWithTag("home-loading").assertExists()
        compose.onNodeWithText("Loading channels…").assertExists()
        compose.onNodeWithTag("home-recovery").assertDoesNotExist()
        capture("12-loading", "home-view-button")
    }

    @Category(VisualCapture::class)
    @Test fun channelsButNoHistoryRecordingsOrEpg() = atEvening {
        show(Kind.EMPTY, screen = true)
        compose.onNodeWithText("Browse channels").assertExists()
        capture("13-empty", "home-recovery")
    }

    @Category(VisualCapture::class)
    @Test fun offlineRecovery() = atEvening {
        show(Kind.OFFLINE, screen = true)
        compose.onNodeWithTag("connection-problem-badge", useUnmergedTree = true).assertExists()
        capture("14-offline", "tv-recovery-primary")
    }

    @Category(VisualCapture::class)
    @Test fun defaultLargeFont() = atEvening {
        show(Kind.BROWSE, fontScale = 1.3f)
        capture("15-default-font-130", "home-recent-0")
        assertFocusedHeadingTop("home-heading-RECENT_CHANNELS")
    }

    @Category(VisualCapture::class)
    @Test fun warmLargeFont() = atEvening {
        show(Kind.WARM, fontScale = 1.3f)
        capture("16-warm-font-130", "home-activity-0")
    }

    @Category(VisualCapture::class)
    @Test fun wholeCardsFillAndNextCardPeeksInMargin() = atEvening {
        show(Kind.BROWSE, longOnNow = true)
        settle("home-recent-0")
        val recent = bounds("home-recent-0")
        assertEquals("six whole recent tiles end at grid end", 902f,
            recent.left.value + (recent.right - recent.left).value * 6 + 16f * 5, 2f)
        assertTrue("seventh recent tile peeks in right margin", bounds("home-recent-6").left >= 902.dp)
        key(Key.DirectionDown)
        settle("home-onnow-0")
        val card = bounds("home-onnow-0")
        assertEquals("four whole programmes end at grid end", 902f,
            card.left.value + (card.right - card.left).value * 4 + 20f * 3, 2f)
        assertTrue("fifth programme peeks in right margin", bounds("home-onnow-4").left >= 902.dp)
        assertTrue("fifth programme is visible", bounds("home-onnow-4").left < 960.dp)
        assertTrue("header is above viewport", bounds("home-header").bottom <= 0.dp)
        compose.onNodeWithTag("home-up-hint", useUnmergedTree = true).assertExists()
        assertEquals("On now heading keeps quiet size", 18f / 28f,
            layerScope("home-heading-ON_NOW").scaleX, 0.02f)
    }

    /** One, two and three current-activity cards, each card half the content width. */
    @Category(VisualCapture::class)
    @Test
    fun activityOne() = activity(1)

    @Category(VisualCapture::class)
    @Test
    fun activityTwo() = activity(2)

    @Category(VisualCapture::class)
    @Test
    fun activityThree() = activity(3)

    /** Production choreography sampled at 50 ms, including the interrupted/retargetable page. */
    @Category(VisualCapture::class)
    @Test
    fun immersiveMotionStripsAndSlots() = atEvening {
        show(Kind.BROWSE)
        settle("home-recent-0")
        compose.mainClock.autoAdvance = false
        strip("down-into", Key.DirectionDown, "home-onnow-0", expanded = true)
        assertTop("home-onnow-0", HomeImmersiveSlot.value)
        assertImmersiveFits()
        val keyline = bounds("home-onnow-0").left.value
        strip("right", Key.DirectionRight, "home-onnow-1", expanded = true)
        assertEquals("focused On now card stays on the start keyline", keyline, bounds("home-onnow-1").left.value, 2f)
        key(Key.DirectionLeft)
        compose.mainClock.advanceTimeBy(600)
        awaitFocus("home-onnow-0")
        assertEquals(keyline, bounds("home-onnow-0").left.value, 2f)
        strip("down-out", Key.DirectionDown, "home-genre-0", expanded = false)
        assertFocusedHeadingTop("home-heading-GENRES")
        val collapsed = bounds("home-onnow-0")
        assertTrue("collapsed On now pictures peek above the genre heading: $collapsed", collapsed.top < 76.dp && collapsed.bottom > 0.dp)
        strip("up-into", Key.DirectionUp, "home-onnow-0", expanded = true)
        assertTop("home-onnow-0", HomeImmersiveSlot.value)
        strip("up-out", Key.DirectionUp, "home-recent-0", expanded = false)
        assertFocusedHeadingTop("home-heading-RECENT_CHANNELS")
        key(Key.DirectionUp)
        compose.mainClock.advanceTimeBy(600)
        awaitFocus("home-tags-button")
        assertTrue("header scrolls back with the page", bounds("home-header").top >= 0.dp)
        key(Key.DirectionDown)
        compose.mainClock.advanceTimeBy(600)
        awaitFocus("home-recent-0")
    }

    @Category(VisualCapture::class)
    @Test
    fun warmImmersiveKeepsSlotWithoutArtwork() = atEvening {
        show(Kind.WARM)
        settle("home-activity-0")
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        settle("home-onnow-0")
        assertTop("home-onnow-0", HomeImmersiveSlot.value)
        compose.onNodeWithTag("channels-immersive-background").assertDoesNotExist()
        assertEquals(1f, layerAlpha("home-on-now-block"), 0.02f)
    }

    @Category(VisualCapture::class)
    @Test
    fun lastRowNeverMovesDownOnEntry() = atEvening {
        show(Kind.BROWSE)
        settle("home-recent-0")
        repeat(4) { key(Key.DirectionDown) }
        settle("home-shelf-0")
        var previous = bounds("home-upcoming-0").top.value
        compose.mainClock.autoAdvance = false
        key(Key.DirectionDown)
        repeat(14) {
            compose.mainClock.advanceTimeBy(50, ignoreFrameDuration = true)
            val y = bounds("home-upcoming-0").top.value
            assertTrue("last row never moves down: $previous -> $y", y <= previous + 0.5f)
            previous = y
        }
        awaitFocus("home-upcoming-0")
        assertTrue("last card fits the canvas", bounds("home-upcoming-0").bottom <= 540.dp)
        capture("20-upcoming", "home-upcoming-0")
        repeat(10) {
            if (compose.onAllNodes(hasTestTag("home-all-scheduled") and isFocused()).fetchSemanticsNodes().isEmpty()) {
                key(Key.DirectionRight)
                compose.mainClock.advanceTimeBy(400)
            }
        }
        capture("20b-upcoming-end", "home-all-scheduled")
    }

    @Category(VisualCapture::class)
    @Test
    fun warmDownInRowTravelsOnce() = atEvening {
        show(Kind.WARM)
        settle("home-activity-0")
        key(Key.DirectionDown)
        settle("home-recent-0")
        compose.mainClock.autoAdvance = false
        strip("warm-down-into", Key.DirectionDown, "home-onnow-0", expanded = true)
    }

    @Category(VisualCapture::class)
    @Test
    fun warmUpOutRowTravelsOnce() = atEvening {
        show(Kind.WARM)
        settle("home-activity-0")
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        settle("home-onnow-0")
        compose.mainClock.autoAdvance = false
        strip("up-out", Key.DirectionUp, "home-recent-0", expanded = false)
    }

    @Category(VisualCapture::class)
    @Test
    fun rapidVerticalMovesRetargetAndReturnToHeader() = atEvening {
        show(Kind.BROWSE)
        settle("home-recent-0")
        key(Key.DirectionDown)
        settle("home-onnow-0")
        compose.mainClock.autoAdvance = false
        listOf(
            Key.DirectionDown to "home-genre-0",
            Key.DirectionDown to "home-shelf-0",
            Key.DirectionUp to "home-genre-0",
            Key.DirectionUp to "home-onnow-0",
            Key.DirectionUp to "home-recent-0",
            Key.DirectionUp to "home-tags-button",
        ).forEach { (direction, tag) ->
            key(direction)
            compose.mainClock.advanceTimeBy(50, ignoreFrameDuration = true)
            awaitFocus(tag)
            assertOpacityEnvelope()
        }
        compose.mainClock.advanceTimeBy(700)
        awaitFocus("home-tags-button")
        assertSettledOpacity(false)
        assertEquals(1f, layerAlpha("home-section-RECENT_CHANNELS"), 0.02f)
    }

    @Category(VisualCapture::class)
    @Test
    fun verticalEntryRestoresScrolledCards() = atEvening {
        show(Kind.BROWSE, longOnNow = true)
        settle("home-recent-0")
        key(Key.DirectionDown)
        settle("home-onnow-0")
        repeat(2) { key(Key.DirectionRight) }
        settle("home-onnow-2")
        key(Key.DirectionDown)
        settle("home-genre-0")
        key(Key.DirectionUp)
        settle("home-onnow-2")
        repeat(11) { key(Key.DirectionRight) }
        settle("home-onnow-13")
        compose.onNodeWithTag("home-onnow-0").assertDoesNotExist()
        key(Key.DirectionDown)
        settle("home-genre-0")
        key(Key.DirectionUp)
        settle("home-onnow-13")
        assertTop("home-onnow-13", HomeImmersiveSlot.value)
    }

    @Category(VisualCapture::class)
    @Test
    fun firstRunHeaderRoundTripAnimatesWithoutDisplacingBlock() = atEvening {
        show(Kind.FIRST_RUN)
        settle("home-onnow-0")
        key(Key.DirectionUp)
        settle("home-tags-button")
        compose.mainClock.autoAdvance = false
        strip("first-run-header-down", Key.DirectionDown, "home-onnow-0", expanded = true)
        assertTop("home-onnow-0", HomeImmersiveSlot.value)
        assertEquals(0f, layerScope("home-on-now-block").translationY, 0.1f)
    }

    @Category(VisualCapture::class)
    @Test
    fun rawReferenceClocksAndInterruptedOrdinaryMoves() {
        val focused = mutableStateOf<HomeSectionKind?>(HomeSectionKind.RECENT_CHANNELS)
        lateinit var motion: HomePageMotion
        compose.setContent {
            val current = rememberHomePageMotion(focused.value) { if (it == HomeSectionKind.ON_NOW) 200f else 0f }
            SideEffect { motion = current }
            Box(Modifier.fillMaxSize().testTag("motion-${focused.value}"))
        }
        compose.waitForIdle()
        assertEquals(HomeSectionKind.RECENT_CHANNELS, motion.to)
        compose.mainClock.autoAdvance = false
        fun focus(kind: HomeSectionKind?) {
            compose.runOnIdle { focused.value = kind }
            compose.waitForIdle()
            compose.mainClock.advanceTimeUntil { motion.to == kind }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        focus(HomeSectionKind.ON_NOW)
        repeat(29) { frame ->
            if (frame > 0) compose.mainClock.advanceTimeByFrame()
            val elapsed = frame * 16f
            assertEquals("raw block at $elapsed", ((elapsed - 100f) / 270f).coerceIn(0f, 1f), motion.block.value, 0.01f)
            assertEquals("raw background at $elapsed", ((elapsed - 100f) / 370f).coerceIn(0f, 1f), motion.background.value, 0.01f)
            assertEquals("entry page progress at $elapsed", RowTravelEasing.transform((elapsed / RowTravelMs).coerceIn(0f, 1f)), motion.progress.value, 0.01f)
        }
        compose.mainClock.advanceTimeBy(100)
        // Fully expanded -> Down out -> ordinary Down: the exit must keep fading, not freeze.
        focus(HomeSectionKind.GENRES)
        compose.mainClock.advanceTimeBy(80)
        focus(HomeSectionKind.CONTINUE_WATCHING)
        var lastBackground = motion.background.value
        repeat(4) {
            compose.mainClock.advanceTimeBy(50)
            assertTrue("interrupted background keeps fading", motion.background.value < lastBackground || lastBackground == 0f)
            lastBackground = motion.background.value
        }
        assertEquals(0f, motion.background.value, 0.02f)
        assertEquals(1f, motion.above.value, 0.02f)
        assertEquals(0f, motion.aboveShift.value, 0.1f)
        assertEquals(1f, motion.heading.value, 0.02f)
        assertEquals(0f, motion.block.value, 0.02f)
        assertEquals(0f, motion.blockShift.value, 0.1f)
        // Up out leaves the row above dimmed briefly; an ordinary move must restore it smoothly.
        focus(HomeSectionKind.ON_NOW)
        assertTrue("Up-in starts above rest", motion.blockShift.value < 0f)
        assertTrue("Up-in does not grow upward from its bottom", !motion.revealFromBottom)
        val startShift = motion.blockShift.value
        compose.mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
        assertTrue("rows start before info block", motion.progress.value > 0f)
        assertEquals("block waits for rows", startShift, motion.blockShift.value, 0.1f)
        assertEquals(0f, motion.block.value, 0.01f)
        var previousShift = startShift
        repeat(7) {
            compose.mainClock.advanceTimeBy(50, ignoreFrameDuration = true)
            assertTrue("Up-in block moves down toward rest", motion.blockShift.value >= previousShift)
            previousShift = motion.blockShift.value
        }
        assertEquals("Up-in settles by 450 ms", 0f, motion.blockShift.value, 0.1f)
        assertEquals(1f, motion.block.value, 0.02f)
        compose.mainClock.advanceTimeBy(600)
        focus(HomeSectionKind.RECENT_CHANNELS)
        compose.mainClock.advanceTimeBy(80)
        focus(null)
        val aboveBefore = motion.above.value
        compose.mainClock.advanceTimeBy(100)
        assertTrue(motion.above.value > aboveBefore)
        assertTrue(motion.above.value < 1f)
        compose.mainClock.advanceTimeBy(150)
        assertEquals(1f, motion.above.value, 0.02f)
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertFocusedHeadingTop(tag: String) {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val fullHeight = layouts.single().size.height
        val growth = fullHeight - (fullHeight * (18f / 28f)).roundToInt()
        assertEquals("$tag full-size drawing starts at ordinary slot", 76f,
            bounds(tag).top.value - growth / 2f, 2f)
    }

    private fun assertImmersiveFits() {
        val block = bounds("home-on-now-block")
        val row = bounds("home-onnow-0")
        assertTrue("whole info block clears safe top: $block", block.top >= 24.dp)
        assertTrue("programme row stays inside canvas: $row", row.bottom <= 540.dp)
        assertTrue("eyebrow remains inside block", bounds("channels-headline-channel").top >= 24.dp)
        compose.onNodeWithTag("channels-immersive-next", useUnmergedTree = true).assertExists()
    }

    @Category(VisualCapture::class)
    @Test
    fun immersiveLargeFontKeepsInfoAndRowInsideCanvas() = atEvening {
        show(Kind.BROWSE, fontScale = 1.3f)
        settle("home-recent-0")
        key(Key.DirectionDown)
        capture("19-on-now-font-130", "home-onnow-0")
        assertImmersiveFits()
    }

    private fun assertTop(tag: String, expected: Float) {
        assertEquals("$tag top slot", expected, bounds(tag).top.value, 8f)
    }

    /** Read the actual drawing modifier, not parallel test-only animation state or pixel guesses. */
    @Suppress("UNCHECKED_CAST")
    private fun layerScope(tag: String): GraphicsLayerScope = compose.runOnIdle {
        val modifiers = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.getModifierInfo()
        val block = modifiers.asSequence().mapNotNull { it.modifier as? InspectableValue }
            .flatMap { it.inspectableElements }
            .first { it.name == "block" }.value as GraphicsLayerScope.() -> Unit
        GraphicsLayerScope().apply(block)
    }

    private fun layerAlpha(tag: String) = layerScope(tag).alpha

    private fun assertOpacityEnvelope() {
        val background = layerAlpha("home-immersive-background")
        val block = layerAlpha("home-on-now-block")
        assertTrue("block=$block must not outpace background=$background", block <= background + 0.02f)
    }

    private fun assertSettledOpacity(expanded: Boolean) {
        val expected = if (expanded) 1f else 0f
        assertEquals(expected, layerAlpha("home-immersive-background"), 0.02f)
        assertEquals(expected, layerAlpha("home-on-now-block"), 0.02f)
    }

    private fun strip(name: String, key: Key, tag: String, expanded: Boolean) {
        key(key)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        awaitFocus(tag)
        val strip = Bitmap.createBitmap(320 * 12, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(strip)
        var previousBlockTop: Float? = null
        var blockBottomGap: Float? = null
        val rowTops = mutableListOf<Float>()
        val aboveTop = bounds("home-header").top.value
        repeat(12) { frame ->
            if (frame > 0) compose.mainClock.advanceTimeBy(50, ignoreFrameDuration = true)
            compose.waitForIdle()
            assertOpacityEnvelope()
            if (name == "up-out" || name.endsWith("down-into")) rowTops += bounds("home-onnow-0").top.value
            val elapsed = frame * 50f
            val delay = when (name) {
                "down-into", "first-run-header-down" -> 100f
                "up-into" -> 150f
                else -> null
            }
            if (delay != null) {
                val rawBlock = ((elapsed - delay) / if (name == "up-into") 300f else 270f).coerceIn(0f, 1f)
                val rawBackground = if (name == "up-into") ((elapsed - 50f) / 320f).coerceIn(0f, 1f)
                    else ((elapsed - delay) / 370f).coerceIn(0f, 1f)
                assertEquals("drawn block follows reference clocks at $elapsed", minOf(rawBlock, rawBackground), layerAlpha("home-on-now-block"), 0.08f)
            }
            if (name == "down-out") assertEquals(0f, layerAlpha("home-on-now-block"), 0.02f)
            if (name == "down-into" || name == "first-run-header-down") {
                if (frame == 2) {
                    assertTrue("header leaves upward, not a crossfade in place", bounds("home-header").top.value < aboveTop - 24f)
                    assertTrue("header clears the top edge", bounds("home-header").bottom <= 0.dp)
                    if (name == "down-into") assertTrue("recent row clears the top edge: ${bounds("home-section-RECENT_CHANNELS")}, shift=${layerScope("home-section-RECENT_CHANNELS").translationY}",
                        bounds("home-section-RECENT_CHANNELS").bottom <= 0.dp)
                }
                if (frame in 3..5) {
                    val block = bounds("home-on-now-block")
                    val heading = bounds("home-heading-ON_NOW")
                    val gap = (heading.top - block.bottom).value
                    blockBottomGap?.let { assertEquals("block bottom stays anchored above heading", it, gap, 1f) }
                    blockBottomGap = gap
                    val top = (block.top - heading.top).value
                    previousBlockTop?.let { assertTrue("block extends upward from its bottom edge", top < it) }
                    previousBlockTop = top
                }
            }
            val bitmap = draw()
            canvas.drawBitmap(bitmap, null, android.graphics.Rect(frame * 320, 0, (frame + 1) * 320, 180), null)
            bitmap.recycle()
        }
        assertSettledOpacity(expanded)
        if (name == "up-out") assertTrue("Up-out row travels monotonically: $rowTops",
            rowTops.zipWithNext().all { (a, b) -> b >= a - 0.5f })
        if (name.endsWith("down-into")) assertTrue("Down-in row travels monotonically: $rowTops",
            rowTops.zipWithNext().all { (a, b) -> b <= a + 0.5f })
        CaptureOutput.writePng(File(out, "strips/$name.png"), strip)
        strip.recycle()
    }

    private fun activity(items: Int) = atEvening {
        show(Kind.WARM, items)
        capture("act-half-$items", "home-activity-0")
    }

    @Category(VisualCapture::class)
    @Test fun activityStatesKeepOneRecordingMark() = atEvening {
        val scene = scene(Kind.WARM, 2, false, false)
        val channel = scene.input.channels.first { it.id == ChannelId(3) }
        val event = scene.input.currentEvents.getValue(channel.id)
        val running = running(901, channel.id.value, 300, event.title, 103, scene.nowSec, 20, 40)
        val finished = scene.input.dvrEntries.first { it.id == DvrEntryId(201) }
        installFixtures()
        compose.setContent { TVHeadendPlayerTheme {
            view = LocalView.current
            ProvideArtworkSession(scene.observation.currentSession, scene.observation) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                        // The viewer wins: this one's strip is orange, the recording-only card's red.
                        CompositionLocalProvider(at.bernhardberger.tvhplayer.ui.components.LocalWatchingChannel provides channel.id,
                            at.bernhardberger.tvhplayer.ui.components.LocalWatchingIndicator provides at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator.PLAYING) {
                            at.bernhardberger.tvhplayer.ui.components.HomeActivityCard(channel, event, running, true, scene.nowSec,
                                268.dp, "Live and recording", {}, Modifier.testTag("activity-live-recording"))
                        }
                        at.bernhardberger.tvhplayer.ui.components.HomeActivityCard(channel, null, running, false, scene.nowSec,
                            268.dp, "Recording only", {}, Modifier.testTag("activity-recording-only"))
                        at.bernhardberger.tvhplayer.ui.components.HomeActivityCard(scene.input.channels.first { it.id == ChannelId(6) }, null,
                            finished, true, scene.nowSec, 268.dp, "Finished recording playback", {},
                            Modifier.testTag("activity-finished"), playingRecording = true)
                    }
                }
            }
        } }
        compose.onNodeWithTag("activity-live-recording").requestFocus()
        compose.onNodeWithTag("channel-recording-indicator", useUnmergedTree = true).assertDoesNotExist()
        listOf("activity-live-recording", "activity-recording-only").forEach { tag ->
            compose.onAllNodes(hasTestTag("recording-badge") and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertCountEquals(1)
        }
        listOf("activity-live-recording", "activity-finished").forEach { tag ->
            compose.onNode(hasTestTag("channel-playing-indicator") and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertExists()
        }
        compose.onNode(hasTestTag("recording-badge") and hasAnyAncestor(hasTestTag("activity-finished")), useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodes(hasContentDescription("Recording") and hasAnyAncestor(hasTestTag("activity-finished")), useUnmergedTree = true).assertCountEquals(1)
        capture("21-activity-recording-marks", "activity-live-recording")
    }

    private enum class Kind { BROWSE, WARM, FIRST_RUN, LOADING, EMPTY, OFFLINE }

    private data class Scene(
        val observation: SessionObservation,
        val input: HomeInput,
        val playing: Channel?,
        val nowSec: Long,
    )

    @Test @Category(VisualCapture::class) fun updateHintHeaderCaptures() = atEvening {
        show(Kind.BROWSE, updateHint = true)
        capture("update-hint-unfocused-en-font1.0", "home-recent-0")
        key(Key.DirectionUp)
        key(Key.DirectionLeft)
        capture("update-hint-focused-en-font1.0", "home-update-hint")
    }

    @Test fun updateHintWithLongTagFitsHeader() = atEvening {
        show(Kind.BROWSE, updateHint = true, headerTag = "International documentary channels")
        capture("update-hint-long-tag-en-font1.0", "home-recent-0")
        compose.onNodeWithTag("home-update-hint").assertIsDisplayed()
        compose.onNodeWithTag("home-tags-button").assertIsDisplayed()
        compose.onNodeWithTag("home-view-button").assertIsDisplayed()
    }

    private fun show(kind: Kind, activityItems: Int = 2, longOnNow: Boolean = false, screen: Boolean = false, fontScale: Float = 1f, noArtwork: Boolean = false, infoLineCounts: Boolean = false, updateHint: Boolean = false, headerTag: String? = null) {
        isDebugInspectorInfoEnabled = true
        val base = scene(kind, activityItems, longOnNow, noArtwork, infoLineCounts)
        val scene = if (kind in setOf(Kind.LOADING, Kind.EMPTY, Kind.OFFLINE)) base.copy(
            input = base.input.copy(channels = if (kind == Kind.EMPTY) base.input.channels else emptyList(),
                currentEvents = emptyMap(), upcomingEvents = emptyList(), recentChannelIds = emptyList(), dvrEntries = emptyList(), playing = null),
            observation = if (kind == Kind.EMPTY) SessionObservation.create(
                channelState = ChannelRepositoryState.Current(ChannelCatalog.create(base.input.channels)),
                epgState = EpgRepositoryState.Current(EpgSnapshot.create(emptyList())),
                dvrState = DvrRepositoryState.Current(DvrSnapshot.create(emptyList())),
            ) else SessionObservation.create(), playing = null,
        ) else base
        val sections = homeSections(scene.input)
        installFixtures()
        val playing = scene.playing
        val nowPlaying = playing?.let {
            railNowPlaying(
                AppPlaybackTarget.Live(it.id),
                AppPlaybackState.Playing,
                scene.observation,
                warmRecordingId = null,
                stopWhileBrowsing = false,
            )
        }
        val livePosition = mutableStateOf(
            playing?.let { LiveViewerPosition(it.id, scene.nowSec, behindLive = false) },
        )
        val video = if (kind == Kind.WARM) FixtureArt.art("still-northline-studio") else null
        compose.setContent {
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    androidx.compose.ui.platform.LocalDensity.current.density, fontScale),
                LocalWarmPlayback provides (kind == Kind.WARM),
                LocalWatchingTarget provides nowPlaying?.target,
                LocalWatchingChannel provides playing?.id,
                LocalWatchingIndicator provides
                    if (playing != null) ChannelPlaybackIndicator.PLAYING else ChannelPlaybackIndicator.NONE,
                LocalLivePosition provides livePosition.takeIf { playing != null },
            ) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        video?.let {
                            Image(
                                it.asImageBitmap(),
                                null,
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            WarmPlaybackScrim()
                        }
                        ProvideArtworkSession(scene.observation.currentSession) {
                            SideRail(
                                currentRoute = AppDestination.HOME,
                                onRootBack = {},
                                onNavigate = {},
                                rootRoute = AppDestination.HOME,
                                availableDestinations = setOf(
                                    AppDestination.HOME,
                                    AppDestination.CHANNELS,
                                    AppDestination.GUIDE,
                                    AppDestination.RECORDINGS,
                                    AppDestination.SETTINGS,
                                ),
                                nowPlaying = nowPlaying,
                                livePauseAvailable = true,
                                programmeAt = { id, at ->
                                    scene.observation.eventAt(id, Instant.fromEpochSeconds(at))
                                },
                                animateNowPlaying = false,
                                connectionDegraded = kind == Kind.OFFLINE,
                                nowSecProvider = { scene.nowSec },
                            ) { padding, _ ->
                                if (screen) HomeScreenContent(scene.input, scene.observation, padding, true,
                                    onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                                    onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { _, _ -> },
                                    groups = listOf(
                                        at.bernhardberger.tvhplayer.ui.screens.ChannelsGroup(null, null, scene.input.channels),
                                        at.bernhardberger.tvhplayer.ui.screens.ChannelsGroup(ChannelTagId(8), "Favorites", scene.input.channels),
                                    ),
                                    connectionUiState = if (kind == Kind.OFFLINE) at.bernhardberger.tvhplayer.core.ConnectionUiState.Error(
                                        at.bernhardberger.tvhplayer.data.ConnectionFailureKind.UNREACHABLE,
                                        at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition.EXPLICIT_RETRY,
                                    ) else at.bernhardberger.tvhplayer.core.ConnectionUiState.Ready,
                                ) else HomeContent(
                                    updateHintShown = updateHint,
                                    tag = headerTag,
                                    sections = sections,
                                    contentPadding = padding,
                                    initialFocus = homeInitialFocus(sections),
                                    onFocused = {},
                                    modifier = Modifier.fillMaxSize(),
                                    observation = scene.observation,
                                    input = scene.input,
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun installFixtures() {
        images.install(
            FixtureArt.imageLoader(
                context,
                buildMap {
                    ChannelsLayoutFixture.PICONS.forEachIndexed { index, name ->
                        if (index + 1 != 14) put(ArtworkId(index + 1), FixtureArt.picon(name))
                    }
                    ChannelsLayoutFixture.ART.forEachIndexed { index, name ->
                        put(ArtworkId(101 + index), FixtureArt.art(name))
                    }
                    listOf("glass-harbor", "lantern-road", "river-court", "field-desk", "little-orbit")
                        .forEachIndexed { index, name ->
                            put(ArtworkId(201 + index), FixtureArt.art(name))
                        }
                },
            ),
        )
    }

    private fun scene(kind: Kind, activityItems: Int, longOnNow: Boolean, noArtwork: Boolean, infoLineCounts: Boolean = false): Scene {
        val now = 1_791_144_900L
        val channels = ChannelsLayoutFixture.LINE_UP.mapIndexed { index, (name, _) ->
            Channel.create(
                id = ChannelId(index + 1L),
                name = if (index == 1) "Harbor Sport" else name,
                number = index + 1L,
                icon = ChannelIcon.ImageCache(ArtworkId(index + 1)),
                tagIds = listOf(ChannelTagId(1)),
            )
        }
        val titles = listOf(
            "Ridge Light", "Harbor Kickoff", "Northline Tonight", "Paper Kite Tales",
            "Ion Wake", "The Kettle Hour", "Harbor Lights Live", "Stories by Lamplight",
        )
        val nextTitles = listOf(
            "Tide Watch", "River Court", "Field Desk", "Little Orbit",
            "Lantern Road", "Glass Harbor", "Harbor Lights Live", "Night Readings",
        )
        val synopses = listOf(
            "Seasonal mountain light, forests and weather moving over a ridge.",
            "Match coverage from a floodlit stadium as Harbor FC meet Amber Town.",
            "Evening studio news from a regional desk with two anchors.",
            "2D animation about a paper kite over painted hills.",
            "Sci-fi anime: a courier rides a magnetic rail through Ion Harbor.",
            "A warm kitchen program about broths, bread and weeknight pans.",
            "A small-hall concert with a quartet.",
            "Short stories read aloud, one each evening.",
        )
        val genreCodes = listOf(
            ContentCategory.MOVIE_DRAMA, ContentCategory.SPORTS, ContentCategory.NEWS_CURRENT_AFFAIRS,
            ContentCategory.CHILDREN_YOUTH, ContentCategory.EDUCATION_SCIENCE_FACTUAL,
            ContentCategory.MUSIC_BALLET_DANCE, ContentCategory.SHOW_GAME_SHOW, null,
        )
        val currentEvents = titles.indices.map { index ->
            val channel = channels[index]
            val switch = if (index == 0) now + 45 * 60 else now + (25 + index * 5) * 60
            EpgEvent.create(
                id = EventId(channel.id.value * 100),
                channelId = channel.id,
                start = Instant.fromEpochSeconds(now - (if (index == 0) 15 else 10 + index * 7) * 60),
                stop = Instant.fromEpochSeconds(switch),
                title = titles[index],
                subtitle = "Winter on the ridge".takeIf { index == 0 },
                genre = "Documentary".takeIf { index == 0 },
                episode = EpgEpisode(null, null, 2, 5, 3, 10, null, null, "S2 E3").takeIf { index == 0 },
                copyrightYear = 2024L.takeIf { index == 0 },
                rating = EpgRating(12, "XMLTV:FSK:12", null, null, null, null).takeIf { index == 0 },
                summary = when {
                    infoLineCounts && index == 0 -> "Mountain light."
                    infoLineCounts && index == 1 -> "Match coverage.\nHarbor FC meet Amber Town.\nA floodlit stadium."
                    else -> synopses[index]
                },
                image = ArtworkId(101 + index).takeIf { !noArtwork && index != 5 && index < 7 },
                contentGenre = genreCodes[index]?.let { ContentGenre.fromContentType((it.code shl 4).toLong()) },
                nextEventId = EventId(channel.id.value * 100 + 1),
            )
        } + if (longOnNow) channels.drop(titles.size).map { channel ->
            EpgEvent.create(
                id = EventId(channel.id.value * 100),
                channelId = channel.id,
                start = Instant.fromEpochSeconds(now - 600),
                stop = Instant.fromEpochSeconds(now + 1800),
                title = "Evening coverage",
            )
        } else emptyList()
        val upcomingEvents = titles.indices.map { index ->
            val channel = channels[index]
            val switch = if (index == 0) now + 45 * 60 else now + (25 + index * 5) * 60
            EpgEvent.create(
                id = EventId(channel.id.value * 100 + 1),
                channelId = channel.id,
                start = Instant.fromEpochSeconds(switch),
                stop = Instant.fromEpochSeconds(switch + 3600),
                title = nextTitles[index],
            )
        }
        val observation = SessionObservation.create(
            sessionState = SessionState.Ready(
                ServerCapabilities.create(
                    streaming = CapabilityAccess.ALLOWED,
                    dvrWrite = CapabilityAccess.ALLOWED,
                ),
            ),
            channelState = ChannelRepositoryState.Current(
                ChannelCatalog.create(
                    channels,
                    listOf(
                        ChannelTag.create(
                            ChannelTagId(1),
                            name = "Favorites",
                            channelIds = channels.map { it.id },
                        ),
                    ),
                ),
            ),
            epgState = EpgRepositoryState.Current(
                EpgSnapshot.create(
                    events = currentEvents + upcomingEvents,
                    coverages = channels.map {
                        EpgCoverage.create(
                            channelId = it.id,
                            coveredFrom = Instant.fromEpochSeconds(now - 86400),
                            coveredTo = Instant.fromEpochSeconds(now + 7 * 86400),
                        )
                    },
                ),
            ),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = dvrEntries(kind, activityItems, now))),
        )
        val firstRun = kind == Kind.FIRST_RUN
        val warm = kind == Kind.WARM
        val recentIds = if (warm) {
            listOf(2, 4, 1, 7, 5, 12, 14, 16)
        } else {
            listOf(3, 2, 4, 1, 7, 5, 12, 14)
        }
        val input = HomeInput(
            channels = channels,
            currentEvents = currentEvents.mapNotNull { event -> event.channelId?.let { it to event } }.toMap(),
            upcomingEvents = upcomingEvents,
            recentChannelIds = if (firstRun) emptyList() else recentIds.map { ChannelId(it.toLong()) },
            dvrEntries = dvrEntries(kind, activityItems, now),
            admission = { entry ->
                resumeMinutes[entry.id]
                    ?.let {
                        TvheadendTestResultFactory.completedRecordingAdmission(
                            it.minutes,
                            RecordingProgressCapability.SUPPORTED,
                        )
                    }
            },
            playing = channels.firstOrNull { it.id == ChannelId(3) }?.let {
                HomePlaying.LiveChannel(it.id)
            }.takeIf { warm },
            nowSec = now,
        )
        return Scene(
            observation = observation,
            input = input,
            playing = channels.firstOrNull { it.id == ChannelId(3) }?.takeIf { warm },
            nowSec = now,
        )
    }

    private val resumeMinutes = mapOf(
        DvrEntryId(201) to 85L,
        DvrEntryId(202) to 12L,
        DvrEntryId(203) to 40L,
    )

    private fun dvrEntries(kind: Kind, activityItems: Int, now: Long): List<DvrEntry> {
        val warm = kind == Kind.WARM
        val firstRun = kind == Kind.FIRST_RUN
        return buildList {
            if (warm && activityItems >= 2) {
                add(running(1, 2, 200, "Harbor Kickoff", 102, now, startMin = 20, stopMin = 40))
            }
            if (warm && activityItems >= 3) {
                add(running(3, 7, 700, "Harbor Lights Live", 106, now, startMin = 25, stopMin = 30))
            }
            if (!firstRun) {
                add(
                    DvrEntry.create(
                        DvrEntryId(201),
                        channelId = ChannelId(6),
                        channelName = "Harbor Lights Live",
                        state = DvrEntryState.COMPLETED,
                        title = "Glass Harbor",
                        start = Instant.fromEpochSeconds(now - 26 * 3600),
                        stop = Instant.fromEpochSeconds(now - 24 * 3600),
                        image = ArtworkId(201),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(202),
                        channelId = ChannelId(5),
                        channelName = "The Kettle Hour",
                        state = DvrEntryState.COMPLETED,
                        title = "Lantern Road",
                        start = Instant.fromEpochSeconds(now - 50 * 3600),
                        stop = Instant.fromEpochSeconds(now - 49 * 3600),
                        image = ArtworkId(202),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(203),
                        channelId = ChannelId(2),
                        channelName = "Harbor Sport",
                        state = DvrEntryState.COMPLETED,
                        title = "River Court",
                        start = Instant.fromEpochSeconds(now - 74 * 3600),
                        stop = Instant.fromEpochSeconds(now - 73 * 3600),
                        image = ArtworkId(203),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(204),
                        channelId = ChannelId(3),
                        channelName = "Northline Tonight",
                        state = DvrEntryState.COMPLETED,
                        title = "Field Desk",
                        start = Instant.fromEpochSeconds(now - 135 * 60),
                        stop = Instant.fromEpochSeconds(now - 90 * 60),
                        image = ArtworkId(204),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(205),
                        channelId = ChannelId(4),
                        channelName = "Paper Kite Tales",
                        state = DvrEntryState.COMPLETED,
                        title = "Little Orbit",
                        start = Instant.fromEpochSeconds(now - 165 * 60),
                        stop = Instant.fromEpochSeconds(now - 135 * 60),
                        image = ArtworkId(205),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(2),
                        channelId = ChannelId(1),
                        channelName = "Ridge Light",
                        eventId = EventId(101),
                        state = DvrEntryState.SCHEDULED,
                        title = "Tide Watch",
                        start = Instant.fromEpochSeconds(now + 45 * 60),
                        stop = Instant.fromEpochSeconds(now + 105 * 60),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(4),
                        channelId = ChannelId(4),
                        channelName = "Paper Kite Tales",
                        state = DvrEntryState.SCHEDULED,
                        title = "Little Orbit",
                        start = Instant.fromEpochSeconds(now + 60 * 60),
                        stop = Instant.fromEpochSeconds(now + 120 * 60),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(5),
                        channelId = ChannelId(5),
                        channelName = "The Kettle Hour",
                        state = DvrEntryState.SCHEDULED,
                        title = "Lantern Road",
                        start = Instant.fromEpochSeconds(now + 75 * 60),
                        stop = Instant.fromEpochSeconds(now + 135 * 60),
                    ),
                )
                add(
                    DvrEntry.create(
                        DvrEntryId(6),
                        channelId = ChannelId(6),
                        channelName = "Harbor Lights Live",
                        state = DvrEntryState.SCHEDULED,
                        title = "Glass Harbor",
                        start = Instant.fromEpochSeconds(now + 90 * 60),
                        stop = Instant.fromEpochSeconds(now + 150 * 60),
                    ),
                )
            }
        }
    }

    private fun running(
        id: Long,
        channelId: Long,
        eventId: Long,
        title: String?,
        image: Long,
        now: Long,
        startMin: Long,
        stopMin: Long,
    ) = DvrEntry.create(
        DvrEntryId(id),
        channelId = ChannelId(channelId),
        channelName = title,
        eventId = EventId(eventId),
        state = DvrEntryState.RECORDING,
        title = title,
        start = Instant.fromEpochSeconds(now - startMin * 60),
        stop = Instant.fromEpochSeconds(now + stopMin * 60),
        image = ArtworkId(image.toInt()),
    )

    private fun atEvening(block: () -> Unit) {
        val zone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertTrue(android.os.SystemClock.setCurrentTimeMillis(1_791_144_900_000L))
            block()
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { keyDown(key); keyUp(key) }
        compose.waitForIdle()
    }

    private fun awaitFocus(tag: String) {
        val target: SemanticsMatcher = hasTestTag(tag)
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodes(target and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            val focused = compose.onAllNodes(isFocused(), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .map { it.config.toString() }
            val bitmap = draw()
            CaptureOutput.writePng(File(out, "debug-$tag.png"), bitmap)
            throw AssertionError("focus not on $tag; focused=$focused", e)
        }
    }

    private fun settle(tag: String) {
        awaitFocus(tag)
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun capture(name: String, tag: String) {
        settle(tag)
        val bitmap = draw()
        CaptureOutput.writePng(File(out, "$name.png"), bitmap)
        bitmap.recycle()
    }

    private fun draw(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val root = view.rootView
            bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
        }
        return bitmap
    }
}
