package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
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
import coil3.ImageLoader
import coil3.map.Mapper
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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
            RecordingOverlayControls(
                imageLoader = loader, piconPath = ArtworkId(2), title = "Zeit im Bild",
                subtitle = "Eine Reise durch die Berge", channelName = "Documentary",
                positionMs = 1_800_000, durationMs = 3_600_000, growing = true, nowSec = 1_800,
                canSeek = true, controlsVisible = true, optionsOpen = false, paused = false,
                onTogglePlayPause = {}, onSeek = {}, onStopPlayback = {}, onUserInteraction = {},
                onOpenOptions = {}, onOpenInfo = {},
            )
        }
        capture("header-recording")
    }

    /** Controls hand over to the options panel: frames 3, 6 and 9 after opening, then settled. */
    @Test fun controlsToPanelMidTransition() {
        var open by mutableStateOf(false)
        show { loader, session ->
            Box(Modifier.fillMaxSize()) {
                PlayerControlsLayer(visible = true, modalVisible = open) {
                    live(loader, session, behind = false)
                }
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
        show { loader, session ->
            PlayerControlsLayer(visible = true, modalVisible = false) {
                live(loader, session, behind = false, zapped = zapped)
            }
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
            PlayerControlsLayer(
                visible = layers.controlsVisible,
                modalVisible = false,
                entry = layers.controlsEntry,
            ) { live(loader, session, behind = false, zapped = zapped) }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            layers.hideControls()
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
        capture("$prefix-settled", focus = settledFocus, directory = directory, timing = "settled (+1000 ms)")
    }

    @Composable
    private fun live(
        loader: ImageLoader,
        session: CurrentSessionObservation,
        behind: Boolean,
        zapped: Boolean = false,
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
        OverlayControlsTv(
            imageLoader = loader,
            currentSession = session,
            channelId = ChannelId(if (zapped) 2 else 1),
            channelNumber = if (zapped) 2 else 1,
            channelName = if (zapped) "Science and Nature" else "Documentary",
            piconPath = ArtworkId(if (zapped) 2 else 1),
            nowEvent = programme,
            nextEvent = next.takeUnless { zapped },
            nowSec = 1_800,
            channelRecordingNow = behind || zapped,
            controlsVisible = true,
            optionsOpen = false,
            onOpenChannels = {},
            onStopPlayback = {},
            onUserInteraction = {},
            onOpenOptions = {},
            timeshiftState = timeshift,
            timeshiftFeedback = null,
            paused = behind,
            committedTimeshiftState = timeshift,
            programmeWindow = window,
            committedWindow = window,
            onToggleTimeshiftPause = {},
            onSeekTimeshift = {},
            onGoLive = {},
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
