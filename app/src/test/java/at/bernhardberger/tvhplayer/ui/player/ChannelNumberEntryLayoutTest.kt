package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.playerStateCell
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import java.io.File
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The channel-number entry's geometry: it keeps one height, corner and digit line whatever the
 * destination, every plate lays out alike, and over shown controls it takes the top identity's
 * place exactly. These checks used to run inside the capture-only chrome class; PNGs are written
 * only in capture runs ([CaptureOutput]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
class ChannelNumberEntryLayoutTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule(effectContext = kotlinx.coroutines.test.StandardTestDispatcher())
    private lateinit var brightStill: Bitmap
    private val channels = listOf(
        Channel.create(ChannelId(1), name = "Ridge Earth HD", number = 101, icon = ChannelIcon.ImageCache(LOGO)),
        Channel.create(ChannelId(2), name = "Harbor Sport HD", number = 102, icon = ChannelIcon.ImageCache(SPORT_LOGO)),
    )
    private val session = FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels)),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).captureCurrentSession()

    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature("android.software.leanback", true)
        brightStill = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        Canvas(brightStill).apply {
            drawColor(android.graphics.Color.rgb(232, 240, 246))
            drawRect(0f, 350f, 960f, 540f, Paint().apply { color = android.graphics.Color.rgb(188, 206, 184) })
        }
        images.install(FixtureArt.imageLoader(app, mapOf(
            LOGO to FixtureArt.picon("ridge-earth"), SPORT_LOGO to FixtureArt.picon("harbor-sport"),
            ArtworkId(1) to FixtureArt.art("ridge-light"),
        )))
    }

    @Test fun overControls() = overControlsLayout()
    @Test fun hiddenChrome() = hiddenChromeLayout(maxDigits = 3)
    @Test fun hiddenChromeFourDigits() = hiddenChromeLayout(maxDigits = 4)

    /** Typing over shown controls: the top identity gives way to the entry in its place. */
    private fun overControlsLayout() {
        val locale = "en"
        val fontScale = 1f
        lateinit var view: View
        var entry by mutableStateOf<Pair<String, ChannelNumberTarget>>("" to ChannelNumberTarget.Pending)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    // The screen's root, which takes focus from the controls while the entry is up.
                    val rootFocus = remember { FocusRequester() }
                    PlayerRootFocusEffect(
                        if (entry.first.isEmpty()) PlayerForegroundLayer.CONTROLS else PlayerForegroundLayer.NUMBER_ENTRY,
                        rootFocus,
                    )
                    Box(Modifier.fillMaxSize().focusRequester(rootFocus).focusable()) {
                        Image(brightStill.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        val timeshift = AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = 0, liveEdgeMs = 0, timingKnown = true)
                        val window = ProgrammeWindow(programme, Instant.fromEpochSeconds(NOW), 0.57f, 0.46f, 0.57f, 0.57f, true)
                        ProvideArtworkSession(session) { PlayerChrome(
                            mode = PlayerChromeMode.CONTROLS,
                            content = PlayerChromeContent(
                                clock = formatClock(NOW),
                                info = liveInfoBarData(ApplicationProvider.getApplicationContext<Application>().resources,
                                    101, "Ridge Earth HD", programme, next, nextScheduled = true, nowSec = NOW,
                                    unavailableTitle = "No program information"),
                                state = playerStateCell(false),
                                recordingNow = false,
                                picon = ChannelIcon.ImageCache(LOGO),
                                artwork = programme.image,
                            ),
                            timeline = PlayerChromeTimeline.Live(
                                timeshift = timeshift, nowSec = NOW, programme = programme, committedTimeshift = timeshift,
                                committedWindow = window, programmeWindow = window, motionKey = ChannelId(1),
                            ),
                            actions = PlayerChromeActions(active = entry.first.isEmpty(), paused = false, restoreFocus = null,
                                tracks = PlayerTrackButtons()),
                            onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
                            numberEntryShown = entry.first.isNotEmpty(),
                        ) }
                        ProvideArtworkSession(session) { PlayerTopIdentitySlot {
                            ChannelNumberOverlay(entry.first, entry.second, Modifier.testTag("number-entry"))
                        } }
                    }
                }
            }
        }
        fun capture(name: String) {
            if (!CaptureOutput.enabled) return
            val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            compose.runOnIdle { view.draw(Canvas(bitmap)) }
            CaptureOutput.writePng(File("../captures/number-entry-in-place/controls-$name-$locale-font$fontScale.png"), bitmap)
            bitmap.recycle()
        }
        repeat(6) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
        val identity = compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        capture("0-identity")
        for ((name, next) in listOf(
            "1-pending" to ("1" to ChannelNumberTarget.Pending),
            "12-matched" to ("12" to ChannelNumberTarget.Channel("12", "Harbor Sport HD", ChannelIcon.ImageCache(SPORT_LOGO))),
            "999-unknown" to ("999" to ChannelNumberTarget.None),
        )) {
            compose.runOnIdle { entry = next }
            if (next.first == "1") {
                // Midway through the crossfade: the identity fades out as the entry fades in.
                repeat(4) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
                capture("1-crossfade")
            }
            repeat(6) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
            val placed = compose.onNodeWithTag("number-entry").fetchSemanticsNode().boundsInRoot
            assertEquals("$name: the entry starts where the identity does", identity.left, placed.left, 0.5f)
            assertEquals("$name: the entry stands on the identity's line", identity.top, placed.top, 0.5f)
            assertEquals("$name: the entry is the identity's height", identity.height, placed.height, 0.5f)
            capture(name)
        }
    }

    private fun hiddenChromeLayout(maxDigits: Int) {
        val locale = "en"
        val fontScale = 1f
        lateinit var view: View
        var entry by mutableStateOf<Pair<String, ChannelNumberTarget>>("" to ChannelNumberTarget.Pending)
        var plate by mutableStateOf(ChannelNumberPlateStyle)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize()) {
                        Image(brightStill.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        // Chrome hidden: the entry alone, in the top identity's place.
                        ProvideArtworkSession(session) { PlayerTopIdentitySlot { ChannelNumberOverlay(entry.first, entry.second,
                            Modifier.testTag("number-entry"), maxDigits = maxDigits, plate = plate) } }
                    }
                }
            }
        }
        var initialHeight: Int? = null
        var panelLeft: Float? = null
        var digitsRight: Float? = null
        var digitsTop: Float? = null
        var withLogoWidth: Int? = null
        val entries = listOf(
            "0" to ChannelNumberTarget.Pending,
            "1" to ChannelNumberTarget.Channel("1", "Ridge Earth HD", ChannelIcon.ImageCache(LOGO)),
            "01" to ChannelNumberTarget.Channel("1", "Ridge Earth HD", ChannelIcon.ImageCache(LOGO)),
            "02" to ChannelNumberTarget.Channel("2", "Ridge Earth HD", null),
            "12" to ChannelNumberTarget.Channel("12", "Channel without a logo", null),
            "123" to ChannelNumberTarget.Channel("123", "A very long international channel name HD", null),
            "999" to ChannelNumberTarget.None,
        ) + if (maxDigits == 4) listOf(
            "1234" to ChannelNumberTarget.Channel("1234", "A very long international channel name HD", ChannelIcon.ImageCache(LOGO)),
        ) else emptyList()
        fun capture(name: String) {
            if (!CaptureOutput.enabled) return
            val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            compose.runOnIdle { view.draw(Canvas(bitmap)) }
            CaptureOutput.writePng(File("../captures/number-entry-in-place/hidden-$name-$locale-font$fontScale-digits$maxDigits.png"), bitmap)
            bitmap.recycle()
        }
        // Each number is its own entry, so its plate is sized for it alone.
        fun show(next: Pair<String, ChannelNumberTarget>) {
            compose.runOnIdle { entry = "" to ChannelNumberTarget.Pending }
            repeat(10) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
            compose.runOnIdle { entry = next }
            repeat(6) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
        }
        for (next in entries) {
            show(next)
            val size = compose.onNodeWithTag("number-entry").fetchSemanticsNode().size
            val left = compose.onNodeWithTag("number-entry").fetchSemanticsNode().boundsInRoot.left
            val digits = compose.onNodeWithTag("channel-number-digits").fetchSemanticsNode().boundsInRoot
            if (initialHeight == null) {
                initialHeight = size.height
                panelLeft = left
                digitsRight = digits.right
                digitsTop = digits.top
            }
            assertEquals("entry keeps its height: $next", initialHeight, size.height)
            assertEquals("entry keeps its corner anchor: $next", panelLeft!!, left, 0.5f)
            assertEquals("digits end at the same field end", digitsRight!!, digits.right, 0.5f)
            assertEquals("destination never moves the digits vertically", digitsTop!!, digits.top, 0.5f)
            assertTrue("entry stays inside the grid margin", compose.onNodeWithTag("number-entry").fetchSemanticsNode()
                .boundsInRoot.right <= with(Density(2f)) { (960.dp - TvGridHorizontalMargin).toPx() })
            if (next.first == "01") withLogoWidth = size.width
            if (next.first == "02") {
                assertEquals("an absent logo keeps the chip's place", withLogoWidth, size.width)
                compose.onNodeWithTag("channel-number-picon", useUnmergedTree = true).assertDoesNotExist()
                // A logo-less match leaves the chip empty: the number stands once, in the field.
                assertEquals(1, compose.onAllNodesWithText("02", useUnmergedTree = true).fetchSemanticsNodes().size +
                    compose.onAllNodesWithText("2", useUnmergedTree = true).fetchSemanticsNodes().size)
            }
            if (next.second == ChannelNumberTarget.Pending) {
                compose.onNodeWithTag("channel-number-target").assertDoesNotExist()
            }
            if (next.second is ChannelNumberTarget.Channel) {
                compose.onNodeWithText((next.second as ChannelNumberTarget.Channel).name).assertExists()
                val name = compose.onNodeWithTag("channel-number-target").fetchSemanticsNode().boundsInRoot
                assertTrue("destination sits beside the number", name.left > digits.right)
                // The nested rows can each round their centred placement by half a pixel.
                assertEquals("number and destination share one line", digits.center.y, name.center.y, 1f)
            }
            capture(next.first)
        }
        // The other plates swap only colours: the same entry keeps its layout on each.
        if (maxDigits != 3) return
        val matched = "12" to ChannelNumberTarget.Channel("12", "Harbor Sport HD", ChannelIcon.ImageCache(SPORT_LOGO))
        show(matched)
        val layout = compose.onNodeWithTag("number-entry").fetchSemanticsNode().boundsInRoot
        for (style in ChannelNumberPlate.entries) {
            compose.runOnIdle { plate = style }
            show(matched)
            assertEquals("$style: same layout", layout, compose.onNodeWithTag("number-entry").fetchSemanticsNode().boundsInRoot)
            capture("12-plate-${style.name.lowercase()}")
        }
    }

    private companion object {
        val LOGO = ArtworkId(2)
        val SPORT_LOGO = ArtworkId(4)
        // 2026-09-12 20:15–21:45 UTC; the clock reads 21:07.
        const val START = 1_789_244_100L
        const val NOW = START + 52 * 60
        val programme = EpgEvent.create(EventId(42), ChannelId(1), Instant.fromEpochSeconds(START), Instant.fromEpochSeconds(START + 90 * 60),
            title = "Ridge Light: The Long Way Across the Alps", subtitle = "A new view of people and their stories",
            summary = "A journey through the mountains with their people and stories.", genre = "Drama", image = ArtworkId(1))
        val next = EpgEvent.create(EventId(43), ChannelId(1), Instant.fromEpochSeconds(START + 90 * 60), Instant.fromEpochSeconds(START + 120 * 60),
            title = "Northline Tonight")
    }
}
