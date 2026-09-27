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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
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
import at.bernhardberger.tvhplayer.core.playerStatus
import at.bernhardberger.tvhplayer.core.recordingNowStatus
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingState
import at.bernhardberger.tvhplayer.playback.AppPlaybackDiagnostics
import at.bernhardberger.tvhplayer.playback.AppPlaybackFormatDiagnostics
import at.bernhardberger.tvhplayer.playback.AppPlaybackSource
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvOverlayBottomPadding
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.common.formatClock
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Instant
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue
import androidx.compose.ui.platform.testTag
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Stage-2 evidence of the New player design: the production slots (info bar, top cluster, Banner,
 * tray preview, Info hero and Stream & signal page, standalone chip) composed over a bright still
 * at the 960×540 dp canvas, plus the Current controls as a regression reference. Every string goes
 * through the production formatters and resources. Writes to artifacts/ui-trial/stage2 (ignored).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerTrialStage2CaptureTest {
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

    @Test fun streamSignalSummaryUsesTheBadgesShortLabels() {
        var summary = ""
        compose.setContent { TVHeadendPlayerTheme {
            summary = glanceSummary(glanceBadges(1080, "video/avc", audioDescription = true, liveFrontend = true, relativeSnrPercent = 82.0))
        } }
        compose.waitForIdle()
        assertEquals("1080 · H.264 · AD · SNR 82 %", summary)
    }

    @Test fun english() = captures("en", 1f)
    @Test fun englishLargeText() = captures("en", 1.3f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun german() = captures("de", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLargeText() = captures("de", 1.3f)

    private enum class Scene {
        CONTROLS_LIVE, CONTROLS_NO_LOGO, CONTROLS_BEHIND, CONTROLS_PAUSED, BANNER_TUNING, TRAY_ART, TRAY_TEXT,
        INFO, DETAILS_DVB, DETAILS_IPTV, BUFFERING_CHIP, CURRENT_CONTROLS_LIVE,
    }

    private fun captures(locale: String, fontScale: Float) {
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
        for (next in Scene.entries) {
            compose.runOnIdle { scene = next }
            compose.waitForIdle()
            // Advance deterministic Compose time past page and panel motion; never reach a live service.
            repeat(6) {
                compose.mainClock.advanceTimeBy(100)
                compose.waitForIdle()
            }
            if (next == Scene.CONTROLS_BEHIND || next == Scene.CONTROLS_PAUSED) {
                // The go-live action paints into the status band above the track; the info bar keeps clear of it.
                val bar = compose.onNodeWithTag("trial-info-bar", useUnmergedTree = true).getUnclippedBoundsInRoot()
                val goLive = compose.onNodeWithTag("player-go-live", useUnmergedTree = true).getUnclippedBoundsInRoot()
                assertTrue("$next info bar ${bar.bottom} overlaps go live ${goLive.top}", bar.bottom <= goLive.top)
            }
            val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            compose.runOnIdle { view.draw(Canvas(bitmap)) }
            val directory = File("../artifacts/ui-trial/stage2").apply { mkdirs() }
            val name = "${next.name.lowercase(Locale.ROOT).replace('_', '-')}-$locale-font$fontScale.png"
            File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Composable
    private fun androidx.compose.foundation.layout.BoxScope.Scene(scene: Scene) {
        val behind = scene == Scene.CONTROLS_BEHIND || scene == Scene.CONTROLS_PAUSED
        val paused = scene == Scene.CONTROLS_PAUSED
        val timeshift = AppTimeshiftState(
            available = true,
            bufferStartMs = -600_000,
            positionMs = if (behind) -203_000 else 0,
            liveEdgeMs = 0,
            timingKnown = true,
            paused = paused,
        )
        val status = when (scene) {
            Scene.BANNER_TUNING -> liveTrialStatus(timeshift, paused = false, tuning = true, buffering = false, presented = false, unavailable = false)
            else -> liveTrialStatus(timeshift, paused, tuning = false, buffering = false, presented = true, unavailable = false)
        }
        val header: @Composable (Modifier) -> Unit = {
            PlayerTrialHeader(formatClock(NOW), status, it, recording = recordingNowStatus(scene == Scene.CONTROLS_BEHIND))
        }
        val infoBar: @Composable () -> Unit = {
            PlayerInfoBar(
                liveInfoBarData(101, "ORF 1 HD", programme, next, nextScheduled = true, nowSec = NOW, unavailableTitle = ""),
                playerTrialBadges(diagnostics(true), TrackGlance(audioDescription = true, subtitles = true, teletext = true)),
                if (scene == Scene.CONTROLS_NO_LOGO) null else LOGO, loader, session,
                Modifier.padding(bottom = at.bernhardberger.tvhplayer.ui.TvOverlayStatusRowHeight).testTag("trial-info-bar"),
            )
        }
        when (scene) {
            Scene.CONTROLS_LIVE, Scene.CONTROLS_NO_LOGO, Scene.CONTROLS_BEHIND, Scene.CONTROLS_PAUSED, Scene.CURRENT_CONTROLS_LIVE ->
                Controls(timeshift, behind, paused, scene == Scene.CONTROLS_BEHIND,
                    newHeader = header.takeUnless { scene == Scene.CURRENT_CONTROLS_LIVE },
                    newInfoBar = infoBar.takeUnless { scene == Scene.CURRENT_CONTROLS_LIVE })
            Scene.BANNER_TUNING -> PlayerBanner(true, header, infoBar, programme, NOW, ChannelId(1), Modifier.align(Alignment.BottomCenter))
            Scene.TRAY_ART, Scene.TRAY_TEXT -> Controls(timeshift, false, false, false, header, infoBar, tray = {
                // The preview follows the focused card; the drawer lands on the playing channel.
                QuickZapTrayPreview(channels[0], if (scene == Scene.TRAY_ART) programme else programme.copyWithoutImage(),
                    next, NOW, loader, session, Modifier.padding(horizontal = androidx.compose.ui.unit.Dp(64f)))
            })
            Scene.INFO, Scene.DETAILS_DVB, Scene.DETAILS_IPTV -> LiveProgrammeInfoOverlay(
                event = programme,
                channelIdentity = "101 • ORF 1 HD",
                channelName = "ORF 1 HD",
                recordingScheduled = false,
                canRecord = true,
                recordingState = LiveInfoRecordingState.Idle,
                confirmationVisible = false,
                restoreRecordFocus = false,
                onRecord = {},
                onRecordingActivate = {},
                onRecordingDismiss = {},
                onClose = {},
                hero = {
                    ProgrammeHero(programme.image, ChannelId(1), "101", LOGO, loader, session,
                        Modifier.size(PlayerTrialTokens.heroWidth, PlayerTrialTokens.heroHeight))
                },
                streamSignalSummary = glanceSummary(playerTrialBadges(diagnostics(scene != Scene.DETAILS_IPTV),
                    TrackGlance(audioDescription = true, subtitles = true, teletext = true))),
                streamSignalDetailsOpen = scene != Scene.INFO,
                streamSignalDetails = { LiveStreamSignalPage(diagnostics(scene != Scene.DETAILS_IPTV)) },
            )
            Scene.BUFFERING_CHIP -> PlayerStandaloneStatusChip(
                playerStatus(buffering = true),
                Modifier.align(Alignment.BottomEnd).padding(end = TvOverlaySidePadding, bottom = TvOverlayBottomPadding),
            )
        }
    }

    @Composable
    private fun Controls(
        timeshift: AppTimeshiftState,
        behind: Boolean,
        paused: Boolean,
        recording: Boolean,
        newHeader: (@Composable (Modifier) -> Unit)?,
        newInfoBar: (@Composable () -> Unit)?,
        tray: (@Composable () -> Unit)? = null,
    ) {
        val window = ProgrammeWindow(
            programme, Instant.fromEpochSeconds(if (behind) NOW - 203 else NOW),
            if (behind) 0.53f else 0.57f, 0.46f, 0.57f, 0.57f, true,
        )
        OverlayControlsTv(
            imageLoader = loader,
            currentSession = session,
            channelId = ChannelId(1),
            channelNumber = 101,
            channelName = "ORF 1 HD",
            piconPath = LOGO,
            nowEvent = programme,
            nextEvent = next,
            nowSec = NOW,
            channelRecordingNow = recording,
            nextScheduled = true,
            controlsVisible = true,
            optionsOpen = false,
            onOpenChannels = {},
            onStopPlayback = {},
            onUserInteraction = {},
            onOpenOptions = {},
            timeshiftState = timeshift,
            timeshiftFeedback = null,
            paused = paused,
            committedTimeshiftState = timeshift,
            programmeWindow = window,
            committedWindow = window,
            onToggleTimeshiftPause = {},
            onSeekTimeshift = {},
            onGoLive = {},
            channelRailOpen = tray != null,
            channelRailContent = {
                if (tray != null) ChannelDrawer(
                    channels = channels, selectedId = ChannelId(1), playingChannelId = ChannelId(1),
                    recordingChannelIds = emptySet(), nowEvent = { if (it == ChannelId(1)) programme else if (it == ChannelId(2)) trayProgramme else null },
                    imageLoader = loader, currentSession = session, active = true, nowSec = NOW,
                    onFocusChannel = {}, onPickChannel = {}, onCloseDrawer = {},
                )
            },
            newHeader = newHeader,
            newInfoBar = newInfoBar,
            newTrayPreview = tray,
        )
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
        val trayProgramme = EpgEvent.create(EventId(52), ChannelId(2), Instant.fromEpochSeconds(START), Instant.fromEpochSeconds(START + 90 * 60),
            title = "Universum: Wildes Österreich",
            summary = "Die Tierwelt der Alpen im Lauf eines Jahres, vom Frühling bis zum ersten Schnee.",
            genre = "Natur", image = "imagecache/3")
    }
}
