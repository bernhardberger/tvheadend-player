package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import android.graphics.Canvas
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
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import at.bernhardberger.tvhplayer.R
import androidx.compose.ui.res.stringResource
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
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession

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
    @get:Rule val images = ImageLoaderRule()
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
        show { session -> live(session, behind = false) }
        capture("header-live")
    }

    @Test fun headerBehindLive() {
        show { session -> live(session, behind = true) }
        capture("header-behind-live")
    }

    @Test fun headerRecording() {
        show { _ ->
            RecordingChromeFixture(growing = true)
        }
        capture("header-recording")
    }

    @Test fun numberEntryTransitions() {
        var number by mutableStateOf("")
        var target by mutableStateOf<ChannelNumberTarget>(ChannelNumberTarget.Pending)
        var settles by mutableStateOf(false)
        show { session ->
            Box(Modifier.fillMaxSize()) {
                ProvideArtworkSession(session) {
                    PlayerTopIdentitySlot { ChannelNumberOverlay(number, target, settles = settles) }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        fun change(digits: String, destination: ChannelNumberTarget, committed: Boolean = false) = compose.runOnIdle {
            number = digits
            target = destination
            settles = committed
            Snapshot.sendApplyNotifications()
        }
        fun frames(name: String, frames: List<Int>) =
            captureFrames(name, "number-entry-motion", frames, focus = "passive; no focus")

        change("1", ChannelNumberTarget.Pending)
        frames("entry", listOf(3, 6, 10))
        change("1", ChannelNumberTarget.Channel("1", "Documentary", ArtworkId(1)))
        frames("destination", listOf(3, 6, 9))
        change("12", ChannelNumberTarget.Channel("12", "Arts and Culture", null))
        frames("digit-push", listOf(1, 3, 6))
        change("", ChannelNumberTarget.Pending, committed = true)
        frames("commit", listOf(3, 6, 10))
        change("12", ChannelNumberTarget.Channel("12", "Arts and Culture", null))
        compose.mainClock.advanceTimeBy(1_000)
        change("", ChannelNumberTarget.Pending)
        frames("cancel", listOf(3, 6, 10))
        // A leading zero settles as the channel's own number.
        change("01", ChannelNumberTarget.Channel("1", "Documentary", ArtworkId(1)))
        compose.mainClock.advanceTimeBy(1_000)
        change("", ChannelNumberTarget.Pending, committed = true)
        frames("commit-leading-zero", listOf(1, 6, 10))
        change("999", ChannelNumberTarget.None)
        frames("no-channel", listOf(3, 5))
        change("", ChannelNumberTarget.Pending)
        frames("no-channel-dismissal", listOf(3, 5))
    }

    /** Controls hand over to the options panel: frames 3, 6 and 9 after opening, then settled. */
    @Test fun controlsToPanelMidTransition() {
        var open by mutableStateOf(false)
        show { session ->
            Box(Modifier.fillMaxSize()) {
                live(session, behind = false, panelOpen = open)
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

    /** A zap with the controls up: the header crossfades in place (frames 3 and 6). */
    @Test fun zapWithControlsVisibleCrossfadesInPlace() {
        var zapped by mutableStateOf(false)
        show { session ->
            live(session, behind = false, zapped = zapped)
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
        show { session ->
            layers = rememberLivePlayerLayerState()
            live(
                session, behind = false, zapped = zapped,
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
        session: CurrentSessionObservation,
        behind: Boolean,
        zapped: Boolean = false,
        mode: PlayerChromeMode = PlayerChromeMode.CONTROLS,
        entry: PlayerControlsEntry = PlayerControlsEntry.TRAVEL,
        panelOpen: Boolean = false,
        /** The channel rail below the controls, as the live player shows it. */
        rail: LivePlayerLayerState? = null,
        downHint: String? = null,
    ) {
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
        ProvideArtworkSession(session) { PlayerChrome(
            mode = mode,
            content = PlayerChromeContent("20:15", liveInfoBarData(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().resources, if (zapped) 2 else 1,
                if (zapped) "Science and Nature" else "Documentary", programme, null, false, 1_800, ""), picon = ArtworkId(1)),
            timeline = PlayerChromeTimeline.Live(
                timeshift = timeshift,
                nowSec = 1_800,
                programme = programme,
                committedWindow = window,
                programmeWindow = window,
                motionKey = ChannelId(if (zapped) 2 else 1),
            ),
            actions = PlayerChromeActions(active = true, paused = behind),
            onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
            entry = entry,
            panelOpen = panelOpen,
            downHint = downHint,
            decorationCoversControls = rail?.channelDrawerOpen == true,
            controlsDecoration = { _, controls ->
                if (rail == null) controls() else {
                    val browser = remember { PlayerChannelBrowserState() }
                    QuickZapPresentation(
                        expanded = rail.channelRailPresented,
                        preview = {
                            ProvideArtworkSession(session) { QuickZapTrayPreview(channels.first(), programme, null, 1_800,
                                Modifier.padding(horizontal = TvGridHorizontalMargin)) }
                        },
                        trayBleeds = true,
                        header = { PlayerChannelBrowserHint(browser, true, stringResource(R.string.all_channels)) },
                        controls = controls,
                        channelContent = {
                            ProvideArtworkSession(session) { PlayerChannelBrowser(
                                state = browser, channels = channels, recentChannels = channels.drop(1),
                                playingChannelId = ChannelId(1), recordingChannelIds = emptySet(),
                                nowEvent = { programme }, nowSec = 1_800,
                                onFocusChannel = {}, onOpenDetails = { _, _, _ -> },
                                onPickChannel = {}, onClose = {}, active = rail.channelDrawerOpen,
                            ) }
                        },
                    )
                }
            },
        ) }
    }

    private fun show(content: @Composable (CurrentSessionObservation) -> Unit) {
        images.install(piconLoader(ApplicationProvider.getApplicationContext<Application>()))
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
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF3A4A5A))) {
                    Image(remember { FixtureArt.art("still-ridge-light").asImageBitmap() }, null,
                        Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    content(session)
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
                    val bitmap = FixtureArt.picon(when (data.id.value) {
                        1 -> "ridge-earth"
                        2 -> "harbor-sport"
                        else -> "northline-news"
                    })
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
        title = "Ridge Light: A journey through the mountains and the valleys of the Alps",
        start = Instant.fromEpochSeconds(0),
        stop = Instant.fromEpochSeconds(3_600),
    )

    private val zappedProgramme = EpgEvent.create(
        id = EventId(3),
        channelId = ChannelId(2),
        title = "Harbor Kickoff",
        start = Instant.fromEpochSeconds(900),
        stop = Instant.fromEpochSeconds(4_500),
    )

    private val next = EpgEvent.create(
        id = EventId(2),
        channelId = ChannelId(1),
        title = "Northline Tonight",
        start = Instant.fromEpochSeconds(3_600),
        stop = Instant.fromEpochSeconds(5_400),
    )

    private val channels = (1L..3L).map {
        Channel.create(id = ChannelId(it), number = it + 100,
            name = listOf("Ridge Earth HD", "Harbor Sport HD", "Northline News")[(it - 1).toInt()], icon = ArtworkId(it.toInt()))
    }
}
