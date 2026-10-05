package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgEpisode
import at.bernhardberger.tvheadend.sdk.core.EpgRating
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.playback.LiveSubscriptionDiagnostics
import at.bernhardberger.tvheadend.sdk.playback.LiveSubscriptionSource
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionCondition
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionEvent
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.glanceBadges
import at.bernhardberger.tvhplayer.core.projectedTimeshiftState
import at.bernhardberger.tvhplayer.core.PlayerStateCell
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.core.liveBarEnd
import at.bernhardberger.tvhplayer.core.playerStateCell
import at.bernhardberger.tvhplayer.core.recordingBarEnd
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingState
import at.bernhardberger.tvhplayer.playback.AppPlaybackDiagnostics
import at.bernhardberger.tvhplayer.playback.AppPlaybackFormatDiagnostics
import at.bernhardberger.tvhplayer.playback.AppPlaybackSource
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.screens.guide.ConfirmProgrammeActionDialog
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Instant
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.platform.testTag
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * Player chrome evidence: the production slots (info bar, bar row with its state
 * cell and right group, header clock, Banner, tray preview, Info hero,
 * hidden chip) composed over a bright still
 * at the 960×540 dp canvas. Every string goes through the production formatters and resources. Writes to artifacts/player-chrome (ignored).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(VisualCapture::class)
class PlayerChromeCaptureTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var loader: ImageLoader
    private lateinit var brightStill: Bitmap
    private var defaultZone: TimeZone? = null
    private val channels = listOf(
        Channel.create(ChannelId(1), name = "ORF 1 HD", number = 101, icon = LOGO),
        Channel.create(ChannelId(2), name = "ORF 2 HD", number = 102),
        Channel.create(ChannelId(3), name = "ServusTV HD", number = 103),
        Channel.create(ChannelId(4), name = "3sat HD", number = 104),
        Channel.create(ChannelId(5), name = "arte HD", number = 105),
    )
    private val session = FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels)),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).captureCurrentSession()

    @Before fun setup() {
        defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature("android.software.leanback", true)
        // Every image request terminates here: deterministic art, no server or network access.
        val art = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        Canvas(art).apply {
            drawColor(android.graphics.Color.rgb(44, 66, 80))
            val paint = Paint().apply { color = android.graphics.Color.rgb(125, 158, 164) }
            drawCircle(480f, 95f, 55f, paint)
            paint.color = android.graphics.Color.rgb(38, 87, 75)
            drawRect(0f, 225f, 640f, 360f, paint)
        }
        // Wide transparent logo fixture, distinct from programme artwork (not a downloaded logo).
        val logo = Bitmap.createBitmap(512, 144, Bitmap.Config.ARGB_8888)
        Canvas(logo).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 100f; isFakeBoldText = true }
            drawText("ORF 1 HD", 8f, 108f, paint)
        }
        brightStill = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        Canvas(brightStill).apply {
            drawColor(android.graphics.Color.rgb(232, 240, 246))
            val paint = Paint().apply { color = android.graphics.Color.rgb(255, 247, 218) }
            drawCircle(800f, 70f, 55f, paint)
            paint.color = android.graphics.Color.rgb(188, 206, 184)
            drawRect(0f, 350f, 960f, 540f, paint)
        }
        loader = ImageLoader.Builder(app).components {
            add(Interceptor { chain ->
                val image = if ((chain.request.data as? AppArtworkSource)?.id == LOGO) logo else art
                SuccessResult(image.asImage(), chain.request, DataSource.MEMORY)
            })
        }.build()
    }

    @After fun restoreZone() {
        defaultZone?.let(TimeZone::setDefault)
    }

    @Test fun english() = captures("en", 1f)
    @Test fun numberEntry() = numberEntryCaptures("en", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun numberEntryLargeText() = numberEntryCaptures("de", 1.3f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun numberEntryFourDigitsLargeText() = numberEntryCaptures("de", 1.3f, maxDigits = 4)

    private fun numberEntryCaptures(locale: String, fontScale: Float, maxDigits: Int = 3) {
        lateinit var view: View
        var entry by mutableStateOf<Pair<String, ChannelNumberTarget>>("0" to ChannelNumberTarget.Pending)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize()) {
                        Image(brightStill.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        ChannelNumberOverlay(entry.first, entry.second, loader, session,
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 56.dp, top = 48.dp)
                                .testTag("number-entry"),
                            maxDigits = maxDigits)
                    }
                }
            }
        }
        var initialHeight: Int? = null
        var panelLeft: Float? = null
        var digitsCenterX: Float? = null
        var digitsTop: Float? = null
        var withLogoWidth: Int? = null
        val entries = listOf(
            "0" to ChannelNumberTarget.Pending,
            "1" to ChannelNumberTarget.Channel("ORF 1 HD", LOGO),
            "01" to ChannelNumberTarget.Channel("ORF 1 HD", LOGO),
            "02" to ChannelNumberTarget.Channel("ORF 1 HD", null),
            "12" to ChannelNumberTarget.Channel("Channel without a logo", null),
            "123" to ChannelNumberTarget.Channel("A very long international channel name HD", null),
            "999" to ChannelNumberTarget.None,
        ) + if (maxDigits == 4) listOf(
            "1234" to ChannelNumberTarget.Channel("A very long international channel name HD", LOGO),
        ) else emptyList()
        for (next in entries) {
            compose.runOnIdle { entry = next }
            repeat(6) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
            val size = compose.onNodeWithTag("number-entry").fetchSemanticsNode().size
            val left = compose.onNodeWithTag("number-entry").fetchSemanticsNode().boundsInRoot.left
            val digits = compose.onNodeWithText(next.first).fetchSemanticsNode().boundsInRoot
            if (initialHeight == null) {
                initialHeight = size.height
                panelLeft = left
                digitsCenterX = digits.center.x
                digitsTop = digits.top
            }
            assertEquals("entry keeps its height: $next", initialHeight, size.height)
            assertEquals("entry keeps its corner anchor: $next", panelLeft!!, left, 0.5f)
            assertEquals("digits stay centred in the same slot", digitsCenterX!!, digits.center.x, 0.5f)
            assertEquals("destination never moves the digits vertically", digitsTop!!, digits.top, 0.5f)
            assertTrue("entry remains compact", size.width <= 840)
            if (next.first == "1") withLogoWidth = size.width
            if (next.first == "02") {
                assertTrue("an absent logo reserves no space", size.width < withLogoWidth!!)
                compose.onNodeWithTag("channel-number-picon").assertDoesNotExist()
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
            val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            compose.runOnIdle { view.draw(Canvas(bitmap)) }
            val directory = File("../captures/number-entry-compact").apply { mkdirs() }
            File(directory, "compact-${next.first}-$locale-font$fontScale-digits$maxDigits.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
    @Test fun englishLargeText() = captures("en", 1.3f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun german() = captures("de", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLargeText() = captures("de", 1.3f)

    @Test fun programmeDetailsEnglish() = captures("en", 1f, programmeOnly = true)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun programmeDetailsGerman() = captures("de", 1f, programmeOnly = true)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun programmeDetailsGermanLargeText() = captures("de", 1.3f, programmeOnly = true)

    private enum class Scene {
        CONTROLS_LIVE, CONTROLS_NO_LOGO, CONTROLS_BEHIND, CONTROLS_PAUSED, BANNER_TUNING, BANNER_BEHIND, BANNER_STEP, TRAY_ART, TRAY_TEXT,
        PROGRAMME_INFO, PROGRAMME_INFO_NO_EPG, PROGRAMME_INFO_NO_ART, PROGRAMME_INFO_NO_ART_RECORDING, PROGRAMME_SCHEDULE, PROGRAMME_OPENED,
        PROGRAMME_MORE_INFO, RAIL_SCHEDULE, RAIL_PROGRAMME_OPENED, RAIL_PEEK, PROGRAMME_CONFIRM_RECORD, PROGRAMME_RECORDING_BUSY, PROGRAMME_RECORDING_SCHEDULED,
        // English at font scale 1.0 only (EN_ONLY).
        BANNER_LIVE, CONTROLS_BUFFERING, BANNER_PAUSED, BANNER_NO_EPG_BEHIND, HIDDEN_PAUSED, HIDDEN_BUFFERING, STEP_NEAR_END, STEP_NEAR_END_NO_ICON,
        STEP_REACHING_LIVE, RECORDING_BANNER, RECORDING_STEP, RECORDING_CONTROLS, RECORDING_GROWING, RECORDING_HIDDEN_PAUSED,
        CONTROLS_STEP, RECORDING_CONTROLS_STEP, BANNER_NO_EPG_LIVE, CONTROLS_NO_EPG, BANNER_NO_EPG_NO_TIMESHIFT, CONTROLS_REVEAL_MID,
        BANNER_STEP_OVER_DISTANCE, CONTROLS_STEP_OVER_DISTANCE,
        // Trial channel card: focused with the Recent hint, Recent open, the rail opened in place.
        CHANNEL_CARD, CHANNEL_RECENT, CHANNEL_RAIL,
    }

    /** [Scene.CONTROLS_REVEAL_MID] composes the Banner, then the controls take it over. */
    private var revealControls by mutableStateOf(false)

    private fun captures(locale: String, fontScale: Float, programmeOnly: Boolean = false) {
        lateinit var view: View
        var scene by mutableStateOf(Scene.CONTROLS_LIVE)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize()) {
                        Image(brightStill.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Scene(scene)
                    }
                }
            }
        }
        // Recording and state scenes are captured in English at font scale 1.0 only.
        for (next in Scene.entries.filter {
            if (programmeOnly) it in Scene.PROGRAMME_INFO..Scene.PROGRAMME_RECORDING_SCHEDULED
            else it < Scene.BANNER_LIVE || locale == "en" && fontScale == 1f
        }) {
            compose.runOnIdle { scene = next; revealControls = false }
            compose.waitForIdle()
            // Advance deterministic Compose time past page and panel motion; never reach a live service.
            repeat(6) {
                compose.mainClock.advanceTimeBy(100)
                compose.waitForIdle()
            }
            if (next == Scene.CONTROLS_REVEAL_MID) {
                // The controls take the Banner over: two frames, then the actions fade in after FastMs
                // over ShortMs. The clock stops halfway through that fade, Pause focused.
                compose.runOnIdle { revealControls = true }
                compose.waitForIdle()
                compose.mainClock.advanceTimeBy(32L + PlayerMotion.FastMs + PlayerMotion.ShortMs / 2L)
                compose.waitForIdle()
                compose.onNodeWithTag("player-pause").assertIsFocused()
            }
            if (next == Scene.CHANNEL_CARD) compose.onNodeWithTag(PlayerIdentityCardTag).assertIsFocused()
            if (next == Scene.PROGRAMME_SCHEDULE) compose.onNodeWithTag("details-schedule-42").assertIsFocused()
            if (next == Scene.RAIL_SCHEDULE) compose.onNodeWithTag("details-schedule-52").assertIsFocused()
            if (next == Scene.RAIL_PROGRAMME_OPENED) compose.onNodeWithTag("details-record").assertIsFocused()
            if (next == Scene.PROGRAMME_OPENED || next == Scene.RAIL_PROGRAMME_OPENED || next == Scene.RAIL_SCHEDULE) {
                compose.onNodeWithTag("details-tabs").assertDoesNotExist()
            }
            if (next == Scene.PROGRAMME_CONFIRM_RECORD) {
                compose.onNodeWithText(ApplicationProvider.getApplicationContext<Application>().getString(at.bernhardberger.tvhplayer.R.string.back)).assertIsFocused()
            }
            if (next == Scene.RAIL_PEEK) {
                val label = compose.onNodeWithTag("player-channel-1-identity", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val playing = compose.onNodeWithTag("channel-playing-indicator", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val recording = compose.onNodeWithTag("channel-recording-indicator", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("the channel-row markers trail the label", label.right <= playing.left && playing.right < recording.left)
            }
            if (next == Scene.PROGRAMME_MORE_INFO) compose.onNodeWithTag("player-info-reading").assertIsFocused()
            if (next == Scene.PROGRAMME_SCHEDULE || next == Scene.RAIL_SCHEDULE) {
                val firstFallback = if (next == Scene.RAIL_SCHEDULE) 53 else 43
                listOf(firstFallback, firstFallback + 2).forEach { id ->
                    val row = hasAnyAncestor(hasTestTag("details-schedule-$id"))
                    val mark = compose.onNode(hasTestTag("programme-fallback-mark") and row, useUnmergedTree = true)
                        .fetchSemanticsNode().boundsInRoot
                    val time = compose.onNode(hasTestTag("details-schedule-time") and row, useUnmergedTree = true)
                        .fetchSemanticsNode().boundsInRoot
                    assertTrue("the bitmap picon or number leaves the time slot free", mark.bottom + 7f <= time.top)
                    assertTrue("compact marks fit their 80 × 28dp room", mark.width <= 161f && mark.height <= 57f)
                }
            }
            if (next == Scene.PROGRAMME_INFO_NO_ART_RECORDING) {
                val mark = compose.onNodeWithTag("programme-fallback-mark", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val badge = compose.onNodeWithTag("details-recording-badge", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val progress = compose.onNodeWithTag("details-tile-progress", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("REC belongs in the facts line, not over the picon", badge.top > progress.bottom)
                assertTrue("the picon clears the embedded progress", mark.bottom < progress.top)
            }
            if (next == Scene.PROGRAMME_MORE_INFO) {
                val information = compose.onNodeWithTag("details-information").fetchSemanticsNode().boundsInRoot
                val reader = compose.onNodeWithTag("details-full-description").fetchSemanticsNode().boundsInRoot
                assertEquals("one gutter separates identity and reading", 40f, reader.left - information.right, 1f)
                assertTrue("reading uses the remaining six grid columns", reader.width >= 820f)
            }
            if (next == Scene.PROGRAMME_INFO) {
                compose.onNodeWithTag("live-info-record").assertIsFocused()
            }
            if (next == Scene.PROGRAMME_INFO_NO_EPG) {
                compose.onNodeWithTag("live-info-close").assertIsFocused()
            }
            // No status slot and no Go live: the state cell lives in the bar row.
            compose.onNodeWithTag("player-status-slot", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("player-go-live", useUnmergedTree = true).assertDoesNotExist()
            val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            compose.runOnIdle {
                val canvas = Canvas(bitmap)
                view.draw(canvas)
                if (next == Scene.PROGRAMME_CONFIRM_RECORD) ShadowDialog.getLatestDialog().window!!.decorView.draw(canvas)
            }
            val directory = File("../artifacts/player-chrome").apply { mkdirs() }
            val name = "${next.name.lowercase(Locale.ROOT).replace('_', '-')}-$locale-font$fontScale.png"
            File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            if (next == Scene.PROGRAMME_MORE_INFO) {
                repeat(60) {
                    compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                    compose.mainClock.advanceTimeBy(32)
                    compose.waitForIdle()
                }
                compose.onNodeWithTag("player-info-reading").assertIsFocused()
                val metadata = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
                compose.runOnIdle { view.draw(Canvas(metadata)) }
                File(directory, "programme-more-info-end-$locale-font$fontScale.png").outputStream().use {
                    metadata.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                metadata.recycle()
            }
            if (next == Scene.PROGRAMME_SCHEDULE) {
                val mark = compose.onAllNodesWithTag("programme-fallback-mark", useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
                val badge = compose.onNodeWithTag("player-rec-badge", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("the compact picon stays clear of the shared REC badge", mark.right < badge.left)
                compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
                compose.mainClock.advanceTimeBy(200)
                compose.waitForIdle()
                compose.onNodeWithTag("details-tab-1").assertIsFocused()
                val tabs = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
                compose.runOnIdle { view.draw(Canvas(tabs)) }
                File(directory, "programme-schedule-tabs-$locale-font$fontScale.png").outputStream().use {
                    tabs.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                tabs.recycle()
                compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                compose.mainClock.advanceTimeBy(200)
                compose.waitForIdle()
                val initialViewport = compose.onNodeWithTag("details-schedule").fetchSemanticsNode().boundsInRoot
                if (fontScale == 1f) {
                    val fourth = compose.onNodeWithTag("details-schedule-45").fetchSemanticsNode().boundsInRoot
                    val fifth = compose.onNodeWithTag("details-schedule-46").fetchSemanticsNode().boundsInRoot
                    assertTrue("four full rows fit at normal text size", fourth.bottom < initialViewport.bottom - 64f)
                    assertTrue("part of the fifth row previews below", fifth.top < initialViewport.bottom)
                }
                repeat(schedule.lastIndex) {
                    compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                    repeat(6) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
                }
                compose.onNodeWithTag("details-schedule-46").assertIsFocused()
                val viewport = compose.onNodeWithTag("details-schedule").fetchSemanticsNode().boundsInRoot
                val focused = compose.onNodeWithTag("details-schedule-46").fetchSemanticsNode().boundsInRoot
                assertTrue("focused scale and outline stay outside the fades", viewport.bottom - focused.bottom >= 72f && focused.top - viewport.top >= 72f)
                val scrolled = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
                compose.runOnIdle { view.draw(Canvas(scrolled)) }
                File(directory, "programme-schedule-last-$locale-font$fontScale.png").outputStream().use {
                    scrolled.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                scrolled.recycle()
            }
        }
    }

    @Composable
    private fun androidx.compose.foundation.layout.BoxScope.Scene(scene: Scene) {
        val behind = scene == Scene.CONTROLS_BEHIND || scene == Scene.CONTROLS_PAUSED || scene == Scene.BANNER_BEHIND ||
            scene == Scene.BANNER_PAUSED || scene == Scene.HIDDEN_PAUSED || scene == Scene.CONTROLS_STEP
        val paused = scene == Scene.CONTROLS_PAUSED || scene == Scene.BANNER_PAUSED || scene == Scene.HIDDEN_PAUSED
        val timeshift = AppTimeshiftState(
            available = true,
            bufferStartMs = -600_000,
            positionMs = if (behind) -203_000 else if (scene == Scene.BANNER_NO_EPG_BEHIND) -90_000 else 0,
            liveEdgeMs = 0,
            timingKnown = true,
            paused = paused,
        )
        val state = playerStateCell(paused)
        val noEpg = scene == Scene.BANNER_NO_EPG_BEHIND || scene == Scene.BANNER_NO_EPG_LIVE || scene == Scene.CONTROLS_NO_EPG ||
            scene == Scene.BANNER_NO_EPG_NO_TIMESHIFT
        // Without a guide the axis is the half-hour slot around the position: 21:00–21:30.
        val (slotStart, slotStop) = halfHourSlot(Instant.fromEpochSeconds(NOW))
        fun slotWindow(behindSec: Long): ProgrammeWindow {
            val span = (slotStop - slotStart).inWholeSeconds.toFloat()
            fun fraction(sec: Long) = ((sec - slotStart.epochSeconds) / span).coerceIn(0f, 1f)
            return ProgrammeWindow(null, Instant.fromEpochSeconds(NOW - behindSec), fraction(NOW - behindSec), fraction(NOW - 600),
                fraction(NOW), fraction(NOW), true, slotStart, slotStop)
        }
        val content = PlayerChromeContent(
            clock = formatClock(NOW),
            info = liveInfoBarData(101, "ORF 1 HD", programme.takeUnless { noEpg }, next.takeUnless { noEpg }, nextScheduled = true, nowSec = NOW,
                unavailableTitle = "Programme information unavailable"),
            state = state,
            recordingNow = scene == Scene.CONTROLS_BEHIND || scene == Scene.BANNER_BEHIND,
            badges = playerGlanceBadges(diagnostics(true), TrackGlance(audioDescription = true, subtitles = true, teletext = true)),
            picon = if (scene == Scene.CONTROLS_NO_LOGO) null else LOGO,
        )
        when (scene) {
            Scene.CONTROLS_LIVE, Scene.CONTROLS_NO_LOGO, Scene.CONTROLS_BEHIND, Scene.CONTROLS_PAUSED, Scene.CONTROLS_BUFFERING ->
                Controls(timeshift, behind, paused, content)
            Scene.CHANNEL_CARD, Scene.CHANNEL_RECENT -> Controls(timeshift, false, false, content, focus = PlayerIdentityCardTag,
                recentOpen = scene == Scene.CHANNEL_RECENT)
            Scene.CHANNEL_RAIL, Scene.RAIL_PEEK -> Controls(timeshift, false, false, content, inPlaceRail = true,
                recordingChannels = if (scene == Scene.RAIL_PEEK) setOf(ChannelId(1)) else emptySet(), tray = {
                QuickZapTrayPreview(channels[1], trayProgramme, null, NOW, loader, session,
                    Modifier.padding(horizontal = PlayerChromeTokens.gridMargin))
            })
            Scene.BANNER_TUNING -> Banner(PlayerChromeMode.BANNER, content,
                PlayerChromeTimeline.Live(AppTimeshiftState(), NOW, programme, motionKey = ChannelId(1), tuning = true))
            Scene.BANNER_LIVE -> Banner(PlayerChromeMode.BANNER, content, PlayerChromeTimeline.Live(timeshift, NOW, programme,
                committedWindow = ProgrammeWindow(programme, Instant.fromEpochSeconds(NOW), 0.57f, 0.46f, 0.57f, 0.57f, true),
                motionKey = ChannelId(1)))
            Scene.BANNER_BEHIND, Scene.BANNER_PAUSED -> Banner(PlayerChromeMode.BANNER, content, PlayerChromeTimeline.Live(timeshift, NOW, programme,
                committedWindow = ProgrammeWindow(programme, Instant.fromEpochSeconds(NOW - 203), 0.53f, 0.46f, 0.57f, 0.57f, true),
                motionKey = ChannelId(1)))
            // Without programme data the axis is the half-hour slot with the buffer inside it, the distance above its end.
            Scene.BANNER_NO_EPG_BEHIND, Scene.BANNER_NO_EPG_LIVE -> Banner(PlayerChromeMode.BANNER, content, PlayerChromeTimeline.Live(
                timeshift, NOW, committedWindow = slotWindow(if (scene == Scene.BANNER_NO_EPG_BEHIND) 90 else 0), motionKey = ChannelId(1)))
            Scene.CONTROLS_NO_EPG -> Controls(timeshift, false, false, content, window = slotWindow(0), programme = null)
            // Neither guide nor timeshift: no track, no clocks and, at the live edge, no distance.
            Scene.BANNER_NO_EPG_NO_TIMESHIFT -> Banner(PlayerChromeMode.BANNER, content,
                PlayerChromeTimeline.Live(AppTimeshiftState(), NOW, motionKey = ChannelId(1)))
            // In the focused controls, 3:23 behind live, a step back 30 s: the readout clear above the thumb.
            Scene.CONTROLS_STEP -> Controls(projectedTimeshiftState(timeshift, -233_000), true, false, content, step = -30_000,
                committed = timeshift)
            Scene.CONTROLS_REVEAL_MID -> RevealChrome(content, timeshift)
            // Over an hour behind live at the end of the programme played then: a step forward 30 s reads
            // out at the bar's right end, its opaque chip over the distance, which is wider than the end clock.
            Scene.BANNER_STEP_OVER_DISTANCE -> Banner(PlayerChromeMode.BANNER_STEP, content, stepTimeline(hourBehind, hourBehindStep)
                .copy(committedWindow = endingWindow(0.98f), programmeWindow = endingWindow(0.985f)))
            Scene.CONTROLS_STEP_OVER_DISTANCE -> Controls(projectedTimeshiftState(hourBehind, hourBehindStep.targetMs), true, false, content,
                step = hourBehindStep.deltaMs, committed = hourBehind, window = endingWindow(0.98f), shown = endingWindow(0.985f))
            // A quick step on the hidden player: its preview inside the Banner, the programme still shown.
            Scene.BANNER_STEP -> Banner(PlayerChromeMode.BANNER_STEP, content, stepTimeline(timeshift,
                at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision(-30_000, -30_000, false)))
            Scene.TRAY_ART, Scene.TRAY_TEXT -> Controls(timeshift, false, false, content, tray = {
                // The preview follows the focused card; the drawer lands on the playing channel.
                QuickZapTrayPreview(channels[0], if (scene == Scene.TRAY_ART) programme else programme.copyWithoutImage(),
                    next, NOW, loader, session, Modifier.padding(horizontal = androidx.compose.ui.unit.Dp(64f)))
            })
            Scene.PROGRAMME_INFO, Scene.PROGRAMME_INFO_NO_EPG, Scene.PROGRAMME_INFO_NO_ART, Scene.PROGRAMME_INFO_NO_ART_RECORDING, Scene.PROGRAMME_SCHEDULE,
            Scene.PROGRAMME_OPENED, Scene.PROGRAMME_MORE_INFO, Scene.RAIL_SCHEDULE, Scene.RAIL_PROGRAMME_OPENED,
            Scene.PROGRAMME_CONFIRM_RECORD, Scene.PROGRAMME_RECORDING_BUSY, Scene.PROGRAMME_RECORDING_SCHEDULED -> {
                val rail = scene == Scene.RAIL_SCHEDULE || scene == Scene.RAIL_PROGRAMME_OPENED
                val shown = when {
                    rail -> trayProgramme
                    scene == Scene.PROGRAMME_INFO_NO_ART || scene == Scene.PROGRAMME_INFO_NO_ART_RECORDING || scene == Scene.PROGRAMME_SCHEDULE -> programme.copyWithoutImage()
                    scene == Scene.PROGRAMME_MORE_INFO -> richProgramme
                    else -> programme
                }
                val entries = if (rail) listOf(shown) + schedule.drop(1).mapIndexed { index, event ->
                    EpgEvent.create(EventId(53L + index), ChannelId(2), event.start, event.stop,
                        title = event.title, subtitle = event.subtitle, summary = event.summary, genre = event.genre)
                } else listOf(shown) + schedule.drop(1)
                val channel = if (rail) channels[1] else channels[0]
                val identity = "${channel.number} · ${channel.name}"
                val details = remember(scene) {
                    ProgramDetailsState().apply {
                        if (scene == Scene.PROGRAMME_SCHEDULE) tab = 1
                        if (rail) reset(startOnSchedule = true)
                        if (scene == Scene.PROGRAMME_OPENED) { tab = 1; open(schedule[2]) }
                        if (scene == Scene.RAIL_PROGRAMME_OPENED) open(entries[2])
                        if (scene == Scene.PROGRAMME_MORE_INFO) readMore = true
                        if (scene == Scene.PROGRAMME_RECORDING_SCHEDULED) open(schedule[1])
                    }
                }
                LiveProgrammeInfoOverlay(
                    event = shown.takeUnless { scene == Scene.PROGRAMME_INFO_NO_EPG },
                    channelIdentity = identity,
                    channelName = channel.name.orEmpty(),
                    recordingScheduled = false,
                    canRecord = true,
                    recordingState = LiveInfoRecordingState.Idle,
                    confirmationVisible = false,
                    restoreRecordFocus = false,
                    onRecord = {},
                    onRecordingActivate = {},
                    onRecordingDismiss = {},
                    onClose = {},
                    details = { recordFocus, firstFocus ->
                        ProgramDetails(details, shown, entries, NOW, identity,
                            tile = { event, modifier -> ProgrammeHero(event.image, channel.id, channel.number.toString(), channel.icon, loader, session, modifier,
                                recordingBadge = scene == Scene.PROGRAMME_SCHEDULE && event.id == shown.id) },
                            recordingFor = { event ->
                                val state = when {
                                    (scene == Scene.PROGRAMME_INFO_NO_ART_RECORDING || scene == Scene.PROGRAMME_SCHEDULE) && event.id == shown.id -> at.bernhardberger.tvheadend.sdk.core.DvrEntryState.RECORDING
                                    event.id == entries[3].id || scene == Scene.PROGRAMME_RECORDING_SCHEDULED && event.id == entries[1].id -> at.bernhardberger.tvheadend.sdk.core.DvrEntryState.SCHEDULED
                                    else -> null
                                }
                                state?.let { at.bernhardberger.tvheadend.sdk.core.DvrEntry.create(at.bernhardberger.tvheadend.sdk.core.DvrEntryId(1), eventId = event.id, state = it) }
                            }, canModifyRecordings = true,
                            recordFocus = recordFocus, firstFocus = firstFocus, onAction = {},
                            showWatch = rail, busy = scene == Scene.PROGRAMME_RECORDING_BUSY)
                    },
                )
                if (scene == Scene.PROGRAMME_CONFIRM_RECORD) ConfirmProgrammeActionDialog(
                    action = ProgrammeAction.RECORD, programmeTitle = shown.title.orEmpty(), onDismiss = {}, onConfirm = {},
                )
            }
            // Back while paused hides the Banner: the paused chip stays alone at the state cell's place.
            Scene.HIDDEN_PAUSED -> PlayerHiddenStatusChip(state, liveBarEnd(203_000, null),
                Modifier.align(Alignment.BottomStart).padding(playerHiddenChipPadding()))
            Scene.HIDDEN_BUFFERING -> Unit
            // 0:40 behind live, a quick step forward 30 s previews −0:10 near the bar's end.
            Scene.STEP_NEAR_END -> Banner(PlayerChromeMode.BANNER_STEP, content,
                stepTimeline(nearEnd, nearEndStep))
            Scene.STEP_NEAR_END_NO_ICON -> StepWithoutDirection(content)
            // 0:40 behind live, a quick step forward 45 s reaches the live edge: `▶ Live`.
            Scene.STEP_REACHING_LIVE -> Banner(PlayerChromeMode.BANNER_STEP, content,
                stepTimeline(nearEnd, reachingLiveStep))
            Scene.RECORDING_HIDDEN_PAUSED -> PlayerHiddenStatusChip(PlayerStateCell.PAUSED,
                recordingBarEnd(positionMs = 1_045_000, durationMs = 4_350_000, growing = false),
                Modifier.align(Alignment.BottomStart).padding(playerHiddenChipPadding()))
            Scene.RECORDING_BANNER, Scene.RECORDING_STEP, Scene.RECORDING_CONTROLS, Scene.RECORDING_GROWING, Scene.RECORDING_CONTROLS_STEP -> {
                val growing = scene == Scene.RECORDING_GROWING
                RecordingChromeFixture(
                    mode = when (scene) {
                        Scene.RECORDING_STEP -> PlayerChromeMode.BANNER_STEP
                        Scene.RECORDING_CONTROLS, Scene.RECORDING_CONTROLS_STEP -> PlayerChromeMode.CONTROLS
                        else -> PlayerChromeMode.BANNER
                    },
                    // 0:17:25 of 1:12:30; a quick step back 30 s previews 0:16:55.
                    positionMs = 1_045_000,
                    durationMs = 4_350_000,
                    targetMs = 1_015_000L.takeIf { scene == Scene.RECORDING_STEP || scene == Scene.RECORDING_CONTROLS_STEP },
                    originMs = 1_045_000L.takeIf { scene == Scene.RECORDING_STEP || scene == Scene.RECORDING_CONTROLS_STEP },
                    // Stepping in the full controls: the focused timeline.
                    restoreFocus = "player-seekbar".takeIf { scene == Scene.RECORDING_CONTROLS_STEP },
                    growing = growing,
                    info = fixtureRecordingInfo(programme.title.orEmpty(), growing, subtitle = programme.subtitle,
                        positionMs = 1_045_000, durationMs = 4_350_000, nowSec = NOW),
                    picon = LOGO,
                    markers = listOf(0L, 900_000L, 2_700_000L).takeIf { scene == Scene.RECORDING_CONTROLS }.orEmpty(),
                    imageLoader = loader,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        PlayerBusyIndicator(when (scene) {
            Scene.BANNER_TUNING -> PlayerBusyStatus.TUNING
            Scene.CONTROLS_BUFFERING, Scene.HIDDEN_BUFFERING -> PlayerBusyStatus.BUFFERING
            else -> null
        }, Modifier.align(Alignment.Center))
    }

    /**
     * [Scene.STEP_NEAR_END]'s readout composed without a direction, for comparison only: the Banner
     * step's info bar and preview timeline with the same range and label.
     */
    @Composable
    private fun androidx.compose.foundation.layout.BoxScope.StepWithoutDirection(content: PlayerChromeContent) {
        val range = timeshiftSeekbarRange(projectedTimeshiftState(nearEnd, nearEndStep.targetMs))
        PlayerOverlayChrome(
            headerContent = { PlayerChromeHeader(content.clock, it) },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerInfoBar(content.info, content.badges, recordingNow = content.recordingNow) { PlayerIdentityCard(content, loader, session, it) }
            PlayerTimelineBlock(
                progress = range.displayProgress,
                tone = PlayerTimelineTone.PREVIEW,
                rewindableStartFraction = range.availableStartFraction,
                liveEdgeFraction = 1f,
                progressSemantics = false,
                reserveLabelSpace = true,
                leadingLabel = timeshiftStartClock(nearEnd, NOW),
                status = rememberPlayerBarStatus(content.state, liveBarEnd(40_000, null)),
                previewLabel = timeshiftEndpointLabel(false, -nearEndStep.targetMs),
            )
        }
    }

    @Composable
    private fun androidx.compose.foundation.layout.BoxScope.Banner(
        mode: PlayerChromeMode,
        content: PlayerChromeContent,
        timeline: PlayerChromeTimeline.Live,
    ) {
        PlayerChrome(
            mode = mode,
            content = content,
            timeline = timeline,
            actions = PlayerChromeActions(active = false),
            imageLoader = loader,
            currentSession = session,
            onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    /**
     * A quick step from [committed] as the screen hands it over: previewing the projected target, with
     * the programme window at the committed position and at the target (20:15–21:45, now 21:07).
     */
    private fun stepTimeline(committed: AppTimeshiftState, step: at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision) =
        PlayerChromeTimeline.Live(
            timeshift = projectedTimeshiftState(committed, step.targetMs), nowSec = NOW, programme = programme,
            committedTimeshift = committed,
            committedWindow = programmeWindowAt(committed.positionMs), programmeWindow = programmeWindowAt(step.targetMs),
            previewing = true, step = step, stepDeltaMs = step.deltaMs, motionKey = ChannelId(1),
        )

    /** The programme's window with playback at [fraction] of it, all of it still in the buffer and the live edge past its end. */
    private fun endingWindow(fraction: Float) =
        ProgrammeWindow(programme, Instant.fromEpochSeconds(NOW), fraction, 0.2f, 1f, null, true)

    /** The programme's window at [positionMs] behind the live edge at [NOW], with 10 minutes of history. */
    private fun programmeWindowAt(positionMs: Long): ProgrammeWindow {
        val span = (programme.stop - programme.start).inWholeSeconds.toFloat()
        fun fraction(sec: Long) = ((sec - START) / span).coerceIn(0f, 1f)
        val at = NOW + positionMs / 1_000
        return ProgrammeWindow(programme, Instant.fromEpochSeconds(at), fraction(at), fraction(NOW - 600), fraction(NOW), fraction(NOW), true)
    }

    /** The Banner, taken over by the controls once [revealControls] turns true: one chrome throughout. */
    @Composable
    private fun androidx.compose.foundation.layout.BoxScope.RevealChrome(content: PlayerChromeContent, timeshift: AppTimeshiftState) {
        val window = ProgrammeWindow(programme, Instant.fromEpochSeconds(NOW), 0.57f, 0.46f, 0.57f, 0.57f, true)
        PlayerChrome(
            mode = if (revealControls) PlayerChromeMode.CONTROLS else PlayerChromeMode.BANNER,
            content = content,
            timeline = PlayerChromeTimeline.Live(timeshift, NOW, programme, committedWindow = window, programmeWindow = window,
                motionKey = ChannelId(1)),
            actions = PlayerChromeActions(active = revealControls),
            imageLoader = loader,
            currentSession = session,
            onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
            entry = PlayerControlsEntry.FROM_BANNER,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    @Composable
    private fun Controls(
        timeshift: AppTimeshiftState,
        behind: Boolean,
        paused: Boolean,
        content: PlayerChromeContent,
        tray: (@Composable () -> Unit)? = null,
        window: ProgrammeWindow = ProgrammeWindow(
            PlayerChromeCaptureTest.programme, Instant.fromEpochSeconds(if (behind) NOW - 203 else NOW),
            if (behind) 0.53f else 0.57f, 0.46f, 0.57f, 0.57f, true,
        ),
        programme: EpgEvent? = PlayerChromeCaptureTest.programme,
        /** A step's net movement: the timeline previews [timeshift], its target, with focus. */
        step: Long? = null,
        committed: AppTimeshiftState = timeshift,
        /** The step target's window; by default 3:53 behind live in [window]'s programme. */
        shown: ProgrammeWindow? = null,
        focus: String? = null,
        /** Trial: the channel card, with three recent channels above it. */
        recentOpen: Boolean = false,
        /** Trial: [tray] is the rail opened in place of the channel card. */
        inPlaceRail: Boolean = false,
        recordingChannels: Set<ChannelId> = emptySet(),
    ) {
        var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        Box(Modifier.fillMaxSize()) {
        val shown = shown ?: if (step != null) window.copy(positionFraction = 0.51f, estimatedPosition = Instant.fromEpochSeconds(NOW - 233)) else window
        PlayerChrome(
            mode = PlayerChromeMode.CONTROLS,
            content = content,
            timeline = PlayerChromeTimeline.Live(
                timeshift = timeshift,
                nowSec = NOW,
                programme = programme,
                committedTimeshift = committed,
                committedWindow = window,
                programmeWindow = shown,
                previewing = step != null,
                stepDeltaMs = step,
                motionKey = ChannelId(1),
            ),
            actions = PlayerChromeActions(active = true, paused = paused, restoreFocus = focus ?: "player-seekbar".takeIf { step != null }),
            imageLoader = loader,
            currentSession = session,
            onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
            onChannelStep = { _, _ -> },
            onChannelZap = { _, _ -> },
            onCardClick = {},
            downHint = "Program info",
            recents = listOf(
                RecentChannelPeek(null, "102", "ORF 2 HD", ChannelId(2), now = "Universum: Wildes Österreich"),
                RecentChannelPeek(LOGO, "101", "ORF 1 HD", ChannelId(1), now = "Zeit im Bild"),
                RecentChannelPeek(null, "103", "ServusTV HD Oesterreich", ChannelId(3), now = "Servus Nachrichten 19:20"),
            ),
            onRecentPick = {},
            recentRowOpen = recentOpen,
            channelCardHeld = inPlaceRail,
            onCardPlaced = { cardBounds = it },
            decorationCoversControls = tray != null,
            controlsDecoration = { emphasisAlpha, controls ->
                QuickZapPresentation(
                    expanded = tray != null,
                    channelsAvailable = true,
                    peekAlpha = emphasisAlpha,
                    channelContent = {
                        if (tray != null) ChannelDrawer(
                            channels = channels, selectedId = ChannelId(1), playingChannelId = ChannelId(1),
                            recordingChannelIds = recordingChannels, nowEvent = { if (it == ChannelId(1)) programme else if (it == ChannelId(2)) trayProgramme else null },
                            imageLoader = loader, currentSession = session, active = true, nowSec = NOW,
                            onFocusChannel = {}, onPickChannel = {}, onCloseDrawer = {},
                            entryFocusId = ChannelId(1).takeIf { inPlaceRail },
                            inPlace = inPlaceRail,
                        )
                    },
                    preview = { tray?.invoke() },
                    controls = controls,
                    inPlaceAnchor = if (inPlaceRail) ({ cardBounds }) else null,
                )
            },
        )
        if (inPlaceRail) NowPlayingStrip("101", "ORF 1 HD", LOGO, programme, NOW, loader, session,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 58.dp, top = 29.dp))
        }
    }

    @OptIn(SubscriptionInfrastructureApi::class)
    private fun diagnostics(dvb: Boolean): AppPlaybackDiagnostics {
        var live = LiveSubscriptionDiagnostics.update(null, SubscriptionEvent.Started(null, null, SubscriptionCondition.NO_DETAIL, null,
            LiveSubscriptionSource.create(null, null, null, null, "ORF 1 HD")))
        if (dvb) live = LiveSubscriptionDiagnostics.update(live, SubscriptionEvent.Signal(53739, 12300, null, null, 0, 0, false))
        live = LiveSubscriptionDiagnostics.update(live, SubscriptionEvent.Queue(0, 0, 0, 0, 0, 0))
        return AppPlaybackDiagnostics(source = AppPlaybackSource.LIVE_TV,
            video = AppPlaybackFormatDiagnostics("avc1.640028", resolution = "1920 × 1080", frameRate = 25f, sampleMimeType = "video/avc", width = 1920, height = 1080),
            audio = AppPlaybackFormatDiagnostics("ec-3", channelCount = 6, sampleMimeType = "audio/eac3"), live = live)
    }

    private fun EpgEvent.copyWithoutImage() = EpgEvent.create(id, channelId, start, stop, title = title, summary = summary, genre = genre)

    private companion object {
        val LOGO = ArtworkId.parse("imagecache/2")
        val hourBehind = AppTimeshiftState(available = true, bufferStartMs = -7_200_000, positionMs = -3_730_000, liveEdgeMs = 0, timingKnown = true)
        val hourBehindStep = at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision(targetMs = -3_700_000, deltaMs = 30_000, clamped = false)
        val nearEnd = AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = -40_000, liveEdgeMs = 0, timingKnown = true)
        val nearEndStep = at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision(targetMs = -10_000, deltaMs = 30_000, clamped = false)
        val reachingLiveStep = at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision(targetMs = 0, deltaMs = 40_000, clamped = false)
        // 2026-09-12 20:15–21:45 UTC; the clock reads 21:07, 38 minutes left.
        const val START = 1_789_244_100L
        const val NOW = START + 52 * 60
        val programme = EpgEvent.create(EventId(42), ChannelId(1), Instant.fromEpochSeconds(START), Instant.fromEpochSeconds(START + 90 * 60),
            title = "Die außergewöhnliche Reise durch die österreichischen Alpen",
            subtitle = "Eine neue Perspektive auf Menschen und ihre Geschichten",
            summary = "Eine Reise durch die Bergwelt mit ihren Menschen und Geschichten. Entdecken Sie die Landschaft aus einer neuen Perspektive.",
            genre = "Drama", image = "imagecache/1")
        val next = EpgEvent.create(EventId(43), ChannelId(1), Instant.fromEpochSeconds(START + 90 * 60), Instant.fromEpochSeconds(START + 120 * 60),
            title = "Nachrichten")
        val richProgramme = EpgEvent.create(programme.id, programme.channelId, programme.start, programme.stop,
            title = programme.title, subtitle = programme.subtitle, summary = programme.summary,
            description = "Eine Reise durch die Bergwelt mit ihren Menschen und Geschichten. Entdecken Sie die Landschaft aus einer neuen Perspektive. ".repeat(5),
            genre = "Drama", categories = listOf("Dokumentation", "Natur"), keywords = listOf("Alpen", "Bergwelt"),
            episode = EpgEpisode(null, null, 2, null, 3, 6, null, null, "S2 E3"),
            rating = EpgRating(6, null, null, null, null, 4), copyrightYear = 2025,
            firstAired = Instant.fromEpochSeconds(START - 86400 * 180), isNew = true, image = programme.image)
        val schedule = listOf(programme, next,
            EpgEvent.create(EventId(44), ChannelId(1), Instant.fromEpochSeconds(START + 120 * 60), Instant.fromEpochSeconds(START + 165 * 60),
                title = "Mountain Rescue", subtitle = "Storm Over the Ridge", image = "imagecache/7",
                summary = "A storm traps three hikers below the ridge, and the team has one window to reach them before nightfall. " +
                    "Meanwhile Lena has to decide whether she stays with the team after the season ends.", genre = "Action"),
            EpgEvent.create(EventId(45), ChannelId(1), Instant.fromEpochSeconds(START + 165 * 60), Instant.fromEpochSeconds(START + 255 * 60),
                title = "Universum: Wildes Österreich", genre = "Natur"),
            EpgEvent.create(EventId(46), ChannelId(1), Instant.fromEpochSeconds(START + 255 * 60), Instant.fromEpochSeconds(START + 360 * 60),
                title = "Northern Lights", genre = "Thriller"),
        )
        val trayProgramme = EpgEvent.create(EventId(52), ChannelId(2), Instant.fromEpochSeconds(START), Instant.fromEpochSeconds(START + 90 * 60),
            title = "Universum: Wildes Österreich",
            summary = "Die Tierwelt der Alpen im Lauf eines Jahres, vom Frühling bis zum ersten Schnee.",
            genre = "Natur", image = "imagecache/3")
    }
}
