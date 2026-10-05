package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
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
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingState
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import coil3.ImageLoader
import coil3.map.Mapper
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.TimeZone
import kotlin.time.Instant

/**
 * Design-review captures of the player header, the controls-to-panel handover and zap
 * transitions: 960×540 px, density 1.0 (mdpi),
 * locale en, font scale 1.0, textured backdrop, controls focus as composed.
 * Production composables with offline fake state; written to `captures/ui-motion/` and
 * `captures/ui-motion-zap/`
 * (ignored). Readability over motion and overscan remain physical-TV gates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerMotionCaptureTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private var defaultZone: TimeZone? = null

    @Before fun pinClock() {
        defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreClock() {
        defaultZone?.let(TimeZone::setDefault)
    }

    @Test fun headerLive() {
        show { loader, session -> live(loader, session, behind = false) }
        capture("header-live")
    }

    @Test fun headerBehindLive() {
        show { loader, session -> live(loader, session, behind = true) }
        capture("header-behind-live")
    }

    @Test fun headerRecording() {
        show { loader, _ ->
            RecordingChromeFixture(growing = true, imageLoader = loader)
        }
        capture("header-recording")
    }

    @Test fun numberEntryTransitions() {
        var number by mutableStateOf("")
        var target by mutableStateOf<ChannelNumberTarget>(ChannelNumberTarget.Pending)
        show { loader, session ->
            Box(Modifier.fillMaxSize()) {
                ChannelNumberOverlay(number, target, loader, session,
                    Modifier.align(Alignment.TopStart).padding(start = 56.dp, top = 48.dp))
            }
        }
        compose.mainClock.autoAdvance = false
        fun change(digits: String, destination: ChannelNumberTarget) = compose.runOnIdle {
            number = digits
            target = destination
            Snapshot.sendApplyNotifications()
        }
        fun frames(name: String, frames: List<Int>) =
            captureFrames(name, "number-entry-motion", frames, focus = "passive; no focus")

        change("1", ChannelNumberTarget.Pending)
        frames("entry", listOf(3, 5))
        change("1", ChannelNumberTarget.Channel("Documentary", ArtworkId(1)))
        frames("destination", listOf(3, 6, 9))
        change("12", ChannelNumberTarget.Channel("Arts and Culture", null))
        frames("digits-update", listOf(1, 3, 6))
        change("", ChannelNumberTarget.Pending)
        frames("commit-or-cancel", listOf(3, 5))
        change("999", ChannelNumberTarget.None)
        frames("no-channel", listOf(3, 5))
        change("", ChannelNumberTarget.Pending)
        frames("error-dismissal", listOf(3, 5))
    }

    /** Controls hand over to the options panel: frames 3, 6 and 9 after opening, then settled. */
    @Test fun controlsToPanelMidTransition() {
        var open by mutableStateOf(false)
        show { loader, session ->
            Box(Modifier.fillMaxSize()) {
                live(loader, session, behind = false, panelOpen = open)
                PlayerPanelVisibility(Unit.takeIf { open }) {
                    PlaybackOptionsSheetContent(
                        page = PlaybackOptionsPage.ROOT,
                        audioTracks = emptyList(),
                        subtitleTracks = emptyList(),
                        tracksResolving = false,
                        aspectRatio = AspectRatioMode.FIT,
                        statsVisible = false,
                        onPageChange = {},
                        onAudioTrackSelected = {},
                        onSubtitleTrackSelected = {},
                        onAspectRatioChange = {},
                        onStatsVisibleChange = {},
                    )
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            open = true
            Snapshot.sendApplyNotifications()
        }
        captureFrames(
            "controls-to-panel", "ui-motion", listOf(3, 6, 9),
            focus = "panel entering; focus requested on its first frame",
            settledFocus = "panel root row focused",
        )
    }

    /** Per-element page choreography, with a stationary clock and viewport scrims. */
    @Category(VisualCapture::class)
    @Test fun controlsAndDetailsPageScroll() = pageScroll(rail = false)

    @Category(VisualCapture::class)
    @Test fun railAndSchedulePageScroll() = pageScroll(rail = true)

    @Category(VisualCapture::class)
    @Test fun detailsScheduleScrolled() = pageScroll(rail = false, scrollSchedule = true)

    @Category(VisualCapture::class)
    @Test fun railScheduleScrolled() = pageScroll(rail = true, scrollSchedule = true)

    private fun pageScroll(rail: Boolean, scrollSchedule: Boolean = false) {
        lateinit var layers: LivePlayerLayerState
        show { loader, session ->
            layers = rememberLivePlayerLayerState()
            val details = remember { ProgramDetailsState().apply { reset(startOnSchedule = rail) } }
            LaunchedEffect(Unit) { layers.showControls() }
            PlayerPage(details = Unit.takeIf { layers.infoOpen },
                railExpanded = layers.channelRailPresented,
                nowPlaying = { NowPlayingStrip("1", "Documentary", ArtworkId(1), programme, 1_800, loader, session) },
                railHeader = if (rail) ({ ProgrammeScheduleHeader("1 · Documentary") }) else null,
                player = {
                    live(loader, session, behind = false,
                        rail = layers.takeIf { rail }, downHint = "Program info".takeUnless { rail })
                }) {
                LiveProgrammeInfoOverlay(
                    event = programme, channelIdentity = "1 · Documentary", channelName = "Documentary",
                    recordingScheduled = false, canRecord = true, recordingState = LiveInfoRecordingState.Idle,
                    confirmationVisible = false, restoreRecordFocus = false,
                    onRecord = {}, onRecordingActivate = {}, onRecordingDismiss = {},
                    onClose = {},
                    details = { recordFocus, firstFocus ->
                        ProgramDetails(
                            state = details, current = programme,
                            schedule = if (scrollSchedule) listOf(programme) + (1..11).map {
                                EpgEvent.create(EventId(100L + it), ChannelId(1), Instant.fromEpochSeconds(3600L * it),
                                    Instant.fromEpochSeconds(3600L * (it + 1)), title = "Programme $it", summary = "Schedule programme")
                            } else listOf(programme, next),
                            nowSec = 1_800, channelIdentity = "1 · Documentary",
                            tile = { event, modifier ->
                                ProgrammeHero(event.image, ChannelId(1), "1", ArtworkId(1), loader, session, modifier)
                            },
                            recordingFor = { null }, canModifyRecordings = true,
                            recordFocus = recordFocus, firstFocus = firstFocus, onAction = {},
                        )
                    },
                )
            }
        }
        compose.mainClock.autoAdvance = false
        val pair = if (rail) "rail-schedule" else "controls-details"
        if (rail) {
            compose.runOnIdle { layers.openChannelDrawer(); Snapshot.sendApplyNotifications() }
            repeat(2) { compose.mainClock.advanceTimeByFrame() }
            compose.mainClock.advanceTimeBy(16, ignoreFrameDuration = true)
            capture("rail-opening-16ms", directory = "ui-page-scroll",
                focus = "rail entering from the card", timing = "16 ms after reveal start")
            compose.mainClock.advanceTimeBy(1_000)
        }
        capture("$pair-rest", directory = "ui-page-scroll")
        compose.runOnIdle { layers.openInfo(); Snapshot.sendApplyNotifications() }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.advanceTimeBy(120, ignoreFrameDuration = true)
        capture("$pair-down-120ms", directory = "ui-page-scroll", focus = "inert during page motion", timing = "120 ms after transition start")
        compose.mainClock.advanceTimeBy(130, ignoreFrameDuration = true)
        capture("$pair-down-250ms", directory = "ui-page-scroll", focus = "inert during page motion", timing = "250 ms after transition start")
        compose.mainClock.advanceTimeBy(1_000)
        capture("$pair-down-settled", directory = "ui-page-scroll", focus = if (rail) "schedule first row" else "details initial action")
        if (scrollSchedule) {
            fun key(key: Key) {
                compose.onRoot().performKeyInput { pressKey(key) }
                compose.runOnIdle { Snapshot.sendApplyNotifications() }
                compose.mainClock.advanceTimeBy(1_000)
                compose.waitForIdle()
            }
            if (!rail) {
                key(Key.DirectionUp); key(Key.DirectionRight); key(Key.DirectionDown)
                capture("details-schedule-rest", directory = "ui-page-scroll", focus = "schedule Now row")
            }
            compose.onNodeWithTag("details-schedule-1").assertIsFocused()
            repeat(3) { key(Key.DirectionDown) }
            compose.onNodeWithTag("details-schedule-103").assertIsFocused()
            capture("${if (rail) "rail" else "details-tab"}-schedule-down-three", directory = "ui-schedule-scroll",
                focus = "fourth programme focused after three Down keys")
            return
        }
        compose.runOnIdle { layers.closeInfo(returnToRail = rail); Snapshot.sendApplyNotifications() }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.advanceTimeBy(200, ignoreFrameDuration = true)
        capture("$pair-back-200ms", directory = "ui-page-scroll", focus = "inert during page motion", timing = "200 ms after transition start")
    }

    /** A zap with the controls up: the header crossfades in place (frames 3 and 6). */
    @Test fun zapWithControlsVisibleCrossfadesInPlace() {
        var zapped by mutableStateOf(false)
        show { loader, session ->
            live(loader, session, behind = false, zapped = zapped)
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            zapped = true
            Snapshot.sendApplyNotifications()
        }
        captureFrames("zap-visible", "ui-motion-zap", listOf(3, 6), focus = "initial controls focus (kept across the zap)")
    }

    /** Hidden controls revealed by a zap fade in at their resting place (frames 3 and 6). */
    @Test fun zapRevealsHiddenControlsInPlace() {
        lateinit var layers: LivePlayerLayerState
        var zapped by mutableStateOf(false)
        show { loader, session ->
            layers = rememberLivePlayerLayerState()
            live(
                loader, session, behind = false, zapped = zapped,
                mode = if (layers.chrome.controlsVisible) PlayerChromeMode.CONTROLS else PlayerChromeMode.HIDDEN,
                entry = layers.chrome.controlsEntry,
            )
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            layers.chrome.hideControls()
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            zapped = true
            layers.onChannelTuneRequested()
            Snapshot.sendApplyNotifications()
        }
        captureFrames(
            "zap-reveal", "ui-motion-zap", listOf(3, 6),
            focus = "controls entering; focus requested after their first frame",
        )
    }

    /**
     * Captures the given frames after the last state change, named by the test clock's
     * elapsed time since that change (whole 16 ms frames), then the settled state.
     */
    private fun captureFrames(
        prefix: String,
        directory: String,
        frames: List<Int>,
        focus: String,
        settledFocus: String = focus,
        captureSettled: Boolean = true,
    ) {
        val changedAt = compose.mainClock.currentTime
        var drawn = 0
        for (frame in frames) {
            while (drawn < frame) {
                compose.mainClock.advanceTimeByFrame()
                drawn++
            }
            val elapsed = compose.mainClock.currentTime - changedAt
            capture(
                "$prefix-${elapsed}ms", focus = focus, directory = directory,
                timing = "$elapsed ms on the test clock after the state change ($frame frames of 16 ms); " +
                    "the transition starts on the first or second of these frames",
            )
        }
        compose.mainClock.advanceTimeBy(1_000)
        if (captureSettled) capture("$prefix-settled", focus = settledFocus, directory = directory, timing = "settled (+1000 ms)")
    }

    @Composable
    private fun live(
        loader: ImageLoader,
        session: CurrentSessionObservation,
        behind: Boolean,
        zapped: Boolean = false,
        mode: PlayerChromeMode = PlayerChromeMode.CONTROLS,
        entry: PlayerControlsEntry = PlayerControlsEntry.TRAVEL,
        panelOpen: Boolean = false,
        /** The in-place channel rail over the controls, as the live player shows it. */
        rail: LivePlayerLayerState? = null,
        downHint: String? = null,
    ) {
        var railAnchor by remember { mutableStateOf<Rect?>(null) }
        val timeshift = AppTimeshiftState(
            available = true,
            bufferStartMs = -600_000,
            positionMs = if (behind) -30_000 else 0,
            liveEdgeMs = 0,
            timingKnown = true,
        )
        val programme = if (zapped) zappedProgramme else programme
        val window = ProgrammeWindow(
            programme, Instant.fromEpochSeconds(if (behind) 1_770 else 1_800),
            if (behind) 0.49f else 0.5f, 0.33f, 0.5f, 0.5f, true,
        )
        PlayerChrome(
            mode = mode,
            content = PlayerChromeContent("20:15", liveInfoBarData(if (zapped) 2 else 1,
                if (zapped) "Science and Nature" else "Documentary", programme, null, false, 1_800, "")),
            timeline = PlayerChromeTimeline.Live(
                timeshift = timeshift,
                nowSec = 1_800,
                programme = programme,
                committedWindow = window,
                programmeWindow = window,
                motionKey = ChannelId(if (zapped) 2 else 1),
            ),
            actions = PlayerChromeActions(active = true, paused = behind),
            imageLoader = loader,
            currentSession = session,
            onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
            entry = entry,
            panelOpen = panelOpen,
            downHint = downHint,
            decorationCoversControls = rail?.channelDrawerOpen == true,
            onCardPlaced = { railAnchor = it },
            controlsDecoration = { _, controls ->
                if (rail == null) controls() else QuickZapPresentation(
                    expanded = rail.channelRailPresented,
                    channelsAvailable = true,
                    inPlaceAnchor = { railAnchor },
                    preview = { QuickZapTrayPreview(
                        channel = channels.first(), event = programme, next = next,
                        nowSec = 1_800, imageLoader = loader, currentSession = session,
                        modifier = Modifier.padding(horizontal = PlayerChromeTokens.gridMargin),
                    ) },
                    controls = controls,
                    channelContent = {
                        ChannelDrawer(
                            channels = channels, selectedId = ChannelId(1), playingChannelId = ChannelId(1),
                            recordingChannelIds = emptySet(), nowEvent = { programme }, imageLoader = loader,
                            currentSession = session, active = rail.channelDrawerOpen,
                            nowSec = 1_800, onFocusChannel = {}, onPickChannel = {}, onCloseDrawer = {},
                            onOpenSchedule = { _, _ -> }, entryFocusId = ChannelId(1), inPlace = true,
                        )
                    },
                )
            },
        )
    }

    private fun show(content: @Composable (ImageLoader, CurrentSessionObservation) -> Unit) {
        compose.setContent {
            view = LocalView.current
            val context = LocalContext.current
            val session = remember {
                FakeSessionObservation(SessionObservation.create(
                    sessionState = SessionState.Ready(ServerCapabilities.create(
                        streaming = CapabilityAccess.ALLOWED,
                        dvrWrite = CapabilityAccess.ALLOWED,
                    )),
                    channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels)),
                    epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
                    dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
                )).captureCurrentSession()
            }
            val loader = remember { piconLoader(context) }
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF3A4A5A))) {
                    DebugVideoBackdrop(visible = true, modifier = Modifier.fillMaxSize())
                    content(loader, session)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(
        name: String,
        focus: String = "initial controls focus",
        directory: String = "ui-motion",
        timing: String = "settled",
    ) {
        compose.waitForIdle()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("../captures/$directory").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            File(directory, "$name.txt").writeText(
                "file=$name.png\ncanvas=${bitmap.width}x${bitmap.height}\ndensity=1.0\nlocale=en\n" +
                    "fontScale=1.0\nfixture=PlayerMotionCaptureTest; production composables, offline fake state\n" +
                    "focus=$focus\ntiming=$timing\n",
            )
            bitmap.recycle()
        }
    }

    private fun piconLoader(context: android.content.Context) = ImageLoader.Builder(context)
        .components {
            add(object : Mapper<AppArtworkSource, ByteArray> {
                override fun map(data: AppArtworkSource, options: Options): ByteArray {
                    val bitmap = Bitmap.createBitmap(200, 90, Bitmap.Config.ARGB_8888)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF79D1FF.toInt()
                        textSize = 64f
                        isFakeBoldText = true
                    }
                    Canvas(bitmap).drawText("TV ${data.id.value}", 12f, 68f, paint)
                    return ByteArrayOutputStream().also {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }.toByteArray()
                }
            })
        }
        .coroutineContext(Dispatchers.Main.immediate)
        .fetcherCoroutineContext(Dispatchers.Main.immediate)
        .decoderCoroutineContext(Dispatchers.Main.immediate)
        .diskCache(null)
        .build()

    private val programme = EpgEvent.create(
        id = EventId(1),
        channelId = ChannelId(1),
        title = "A journey through the mountains and the valleys of the Alps",
        start = Instant.fromEpochSeconds(0),
        stop = Instant.fromEpochSeconds(3_600),
    )

    private val zappedProgramme = EpgEvent.create(
        id = EventId(3),
        channelId = ChannelId(2),
        title = "Night sky",
        start = Instant.fromEpochSeconds(900),
        stop = Instant.fromEpochSeconds(4_500),
    )

    private val next = EpgEvent.create(
        id = EventId(2),
        channelId = ChannelId(1),
        title = "Zeit im Bild",
        start = Instant.fromEpochSeconds(3_600),
        stop = Instant.fromEpochSeconds(5_400),
    )

    private val channels = (1L..3L).map {
        Channel.create(id = ChannelId(it), number = it, name = "Channel $it", icon = ArtworkId(it.toInt()))
    }
}
