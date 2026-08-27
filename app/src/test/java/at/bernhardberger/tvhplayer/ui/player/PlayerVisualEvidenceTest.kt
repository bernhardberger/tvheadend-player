package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.HtmlReportWriter
import app.cash.paparazzi.Paparazzi
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingState
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.ProgrammeRecordingTarget
import at.bernhardberger.tvhplayer.core.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.core.TimeshiftState
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrFile
import at.bernhardberger.tvhplayer.htsp.DvrState
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import at.bernhardberger.tvhplayer.player.PlaybackDiagnosticsSnapshot
import at.bernhardberger.tvhplayer.player.PlaybackDiagnosticsSource
import at.bernhardberger.tvhplayer.player.PlaybackFormatDiagnostics
import at.bernhardberger.tvhplayer.player.PlaybackSessionState
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvPanelBrowseAlpha
import at.bernhardberger.tvhplayer.ui.TvPanelDenseAlpha
import at.bernhardberger.tvhplayer.ui.TvPlaybackPadding
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.components.ChannelRow
import at.bernhardberger.tvhplayer.ui.components.ProgrammeContentDetails
import at.bernhardberger.tvhplayer.ui.components.RecordingContentDetails
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvhplayer.ui.components.SettingsPane
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import coil3.ImageLoader
import com.android.resources.Density
import com.android.resources.Keyboard
import com.android.resources.KeyboardState
import com.android.resources.Navigation
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import com.android.resources.TouchScreen
import com.android.resources.UiMode
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Generates ignored, deterministic composable evidence for visual review.
 * Normal verification only initializes Paparazzi and skips the capture body.
 */
class PlayerVisualEvidenceTest {
    private val evidenceOutput = PlayerEvidenceOutput()
    private val evidenceHandler = playerEvidenceHandler(evidenceOutput)

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = playerDeviceConfig(locale = "en", fontScale = 1f),
        snapshotHandler = evidenceHandler,
        showSystemUi = false,
        useDeviceResolution = true,
    )

    @Test
    fun generateRequiredPlayerEvidence() {
        assumeTrue(System.getenv("GENERATE_PLAYER_VISUAL_EVIDENCE") == "1")

        captureLiveEvidence()
        captureRecordingEvidence()
        captureColorSystemEvidence()
        evidenceOutput.writeManifest()
    }

    private fun captureColorSystemEvidence() {
        capture(
            "color-01-neutral-success",
            "Connection and PIN success feedback",
            "en",
            1f,
            "none",
            provenance = REPRESENTATIVE_PRODUCTION_PRIMITIVES,
        ) {
            ColorSemanticEvidence(ColorEvidenceScenario.SUCCESS)
        }
        capture(
            "color-02-epg-recording",
            "EPG recording status and accepted result",
            "en",
            1f,
            "none",
            provenance = REPRESENTATIVE_PRODUCTION_PRIMITIVES,
        ) {
            ColorSemanticEvidence(ColorEvidenceScenario.EPG_RECORDING)
        }
        capture(
            "color-03-recording-result",
            "Recording episode metadata and accepted action result",
            "en",
            1f,
            "none",
            provenance = REPRESENTATIVE_PRODUCTION_PRIMITIVES,
        ) {
            ColorSemanticEvidence(ColorEvidenceScenario.RECORDING_RESULT)
        }
        capture(
            "color-04-live-recording-result",
            "Live programme recording succeeded",
            "en",
            1f,
            "Close",
        ) {
            EvidenceBackdrop {
                LiveInfo(
                    event = germanEvent,
                    confirmation = LiveInfoRecordingState.Succeeded(germanEvent.recordingTarget()),
                )
            }
        }
        capture(
            "color-05-panel-tiers",
            "Channels, EPG, and Settings panel opacity tiers",
            "en",
            1f,
            "none",
            provenance = REPRESENTATIVE_PRODUCTION_PRIMITIVES,
        ) {
            PanelTierEvidence()
        }
        capture(
            "color-06-playback-options",
            "Playback options panel over deterministic video backdrop",
            "en",
            1f,
            "Audio track",
        ) {
            EvidenceBackdrop { Options(page = PlaybackOptionsPage.ROOT) }
        }
    }

    private fun captureLiveEvidence() {
        capture("live-01-controls-hidden", "Live TV — controls hidden", "en", 1f, "none") {
            EvidenceBackdrop()
        }
        capture("live-02-controls-at-live", "Live TV — at-live controls", "en", 1f, "Pause") {
            LiveControls(timeshiftState = liveEdgeTimeshift)
        }
        capture(
            "live-03-controls-paused-de",
            "Live TV — paused controls, long German identity",
            "de",
            1.3f,
            "Wiedergabe",
        ) {
            LiveControls(
                timeshiftState = liveEdgeTimeshift.copy(paused = true),
                channelName = "Österreichischer Rundfunk Nachrichten International HD",
                title = "Eine außergewöhnlich lange Nachrichtensendung mit ausführlicher Überschrift",
            )
        }
        capture("live-04-controls-behind-live", "Live TV — behind-live controls", "en", 1f, "Pause") {
            LiveControls(timeshiftState = behindLiveTimeshift)
        }
        capture("live-05-hidden-seek-preview", "Live TV — hidden timeshift seek preview", "en", 1f, "none") {
            EvidenceBackdrop {
                TimeshiftSeekPreview(
                    state = behindLiveTimeshift,
                    decision = TimeshiftSeekDecision(
                        targetMs = -1_860_000L,
                        deltaMs = -90_000L,
                        clamped = false,
                    ),
                    nowEpochSec = NOW_SEC,
                    programmeStartSec = NOW_SEC - 1_800L,
                    programmeStopSec = NOW_SEC + 1_800L,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        capture(
            "live-06-channel-drawer-de",
            "Live TV — channel drawer",
            "de",
            1.3f,
            "channel 2",
            provenance = REPRESENTATIVE_PRODUCTION_PRIMITIVES,
        ) {
            EvidenceBackdrop { EvidenceChannelDrawer() }
        }
        capture("live-07-programme-info-de", "Live TV — programme info", "de", 1.3f, "Schließen") {
            EvidenceBackdrop { LiveInfo(event = germanEvent, confirmation = null) }
        }
        capture("live-08-programme-info-unavailable", "Live TV — no EPG info", "en", 1f, "Close") {
            EvidenceBackdrop { LiveInfo(event = null, confirmation = null) }
        }
        capture("live-09-recording-confirmation-de", "Live TV — recording confirmation", "de", 1.3f, "Abbrechen") {
            EvidenceBackdrop {
                LiveInfo(
                    event = germanEvent,
                    confirmation = LiveInfoRecordingState.Confirming(germanEvent.recordingTarget()),
                )
            }
        }
        capture("live-10-options-root", "Live TV — playback options root", "en", 1f, "Audio track") {
            EvidenceBackdrop { Options(page = PlaybackOptionsPage.ROOT) }
        }
        capture("live-11-options-audio-long-de", "Live TV — long audio-track list", "de", 1.3f, "selected track 1") {
            EvidenceBackdrop {
                Options(
                    page = PlaybackOptionsPage.AUDIO,
                    audioTracks = List(18) { index ->
                        PlaybackOptionTrack(
                            key = "audio-$index",
                            label = "Tonspur mit ausführlicher deutscher Bezeichnung ${index + 1}",
                            supportingLabel = "Deutsch (Österreich) · Dolby Digital Plus 5.1",
                            selected = index == 0,
                        )
                    },
                )
            }
        }
        capture("live-12-options-subtitles-empty", "Live TV — subtitles unavailable", "en", 1.3f, "Off") {
            EvidenceBackdrop {
                Options(
                    page = PlaybackOptionsPage.SUBTITLES,
                    subtitleTracks = emptyList(),
                )
            }
        }
        capture("live-13-options-display-de", "Live TV — display modes", "de", 1.3f, "Automatisch") {
            EvidenceBackdrop { Options(page = PlaybackOptionsPage.DISPLAY) }
        }
        capture("live-14-stats-maximum", "Live TV — maximum diagnostics", "en", 1.3f, "none") {
            EvidenceBackdrop {
                PlaybackStatsOverlay(
                    diagnostics = maximumDiagnostics,
                    aspectRatio = AspectRatioMode.FIT,
                    timeshiftState = behindLiveTimeshift,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 36.dp, end = 48.dp),
                )
            }
        }
        capture("live-15-compact-tuning-de", "Live TV — compact tuning feedback", "de", 1.3f, "none") {
            EvidenceBackdrop {
                CompactTuningStatus(
                    visible = true,
                    label = "ORF 1 HD wird eingestellt …",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        capture("live-16-connection-recovery-de", "Live TV — connection recovery", "de", 1.3f, "Wiederholen") {
            Recovery(
                message = "Verbindung zum TVHeadend-Server unterbrochen",
                detail = "Live-TV konnte nicht fortgesetzt werden.",
                primary = "Wiederholen",
            )
        }
        capture("live-17-terminal-error", "Live TV — terminal actionable error", "en", 1.3f, "Retry") {
            Recovery(
                message = "Playback could not continue",
                detail = "The stream ended unexpectedly after repeated recovery attempts.",
                hint = "Check the server connection, then try again.",
                primary = "Retry",
            )
        }
        capture("live-18-simple-tv-recovery-exit", "Live TV — Simple TV recovery and exit", "de", 1.3f, "Wiederholen") {
            Recovery(
                message = "Live-TV ist momentan nicht verfügbar",
                detail = "Der zuletzt verwendete Sender konnte nicht gestartet werden.",
                hint = "Erneut versuchen oder Simple TV beenden.",
                primary = "Wiederholen",
                secondary = "Simple TV beenden",
            )
        }
    }

    private fun captureRecordingEvidence() {
        capture(
            "dvr-01-details-resume-de",
            "DVR — details with resume and start-over",
            "de",
            1.3f,
            "Fortsetzen",
            provenance = REPRESENTATIVE_PRODUCTION_PRIMITIVES,
        ) {
            EvidenceBackdrop { RecordingDetailsEvidence() }
        }
        capture("dvr-02-controls-short", "DVR — controls with short metadata", "en", 1f, "Pause") {
            RecordingControls(title = "Evening News", subtitle = "Episode 12")
        }
        capture("dvr-03-controls-extreme-en", "DVR — extreme English metadata", "en", 1.3f, "Pause") {
            RecordingControls(
                title = "A deliberately extraordinary recording title that must wrap cleanly across two lines",
                subtitle = "An equally descriptive episode subtitle with more context than usual",
                channel = "International Public Broadcasting Channel HD",
            )
        }
        capture("dvr-04-controls-extreme-de", "DVR — extreme German metadata", "de", 1.3f, "Pause") {
            RecordingControls(
                title = "Eine außergewöhnlich lange deutschsprachige Aufnahme mit sehr ausführlicher Überschrift",
                subtitle = "Untertitel mit zusätzlichen Informationen zur aufgezeichneten Folge",
                channel = "Österreichischer Rundfunk Nachrichten International HD",
            )
        }
        capture("dvr-05-controls-paused", "DVR — paused controls", "en", 1f, "Play") {
            RecordingControls(title = "Evening News", subtitle = "Episode 12", isPlaying = false)
        }
        capture("dvr-06-controls-hidden", "DVR — controls hidden", "en", 1f, "none") {
            EvidenceBackdrop()
        }
        capture("dvr-07-hidden-seek-preview", "DVR — hidden seek preview", "en", 1.3f, "none") {
            EvidenceBackdrop {
                RecordingSeekPreview(
                    targetMs = 3_540_000L,
                    originMs = 2_880_000L,
                    durationMs = 7_200_000L,
                    growing = false,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        capture("dvr-08-known-duration", "DVR — known-duration timeline", "en", 1f, "Pause") {
            RecordingControls(title = "Nature Documentary", subtitle = "Mountain Rivers")
        }
        capture("dvr-09-growing-unknown-duration", "DVR — growing recording, unknown duration", "en", 1.3f, "Pause") {
            RecordingControls(
                title = "Live Sports Final",
                subtitle = "Recording is still in progress",
                durationMs = C.TIME_UNSET,
                growing = true,
            )
        }
        capture("dvr-10-unknown-duration-de", "DVR — unavailable duration", "de", 1.3f, "Pause") {
            RecordingControls(
                title = "Archivaufnahme ohne verlässliche Zeitangabe",
                subtitle = "Vom Server wurde keine Dauer gemeldet",
                durationMs = C.TIME_UNSET,
                growing = false,
            )
        }
        capture(
            "dvr-11-info-de",
            "DVR — recording info",
            "de",
            1.3f,
            "Schließen",
            provenance = REPRESENTATIVE_PRODUCTION_PRIMITIVES,
        ) {
            EvidenceBackdrop { RecordingInfoEvidence() }
        }
        capture("dvr-12-options-root-de", "DVR — playback options", "de", 1.3f, "Audiospur") {
            EvidenceBackdrop { Options(page = PlaybackOptionsPage.ROOT) }
        }
        capture("dvr-13-read-failure-retry", "DVR — read failure with retry", "de", 1.3f, "Wiederholen") {
            Recovery(
                message = "Aufnahme konnte nicht weitergelesen werden",
                detail = "Die Verbindung zur Aufnahmedatei wurde unterbrochen.",
                primary = "Wiederholen",
                secondary = "Schließen",
            )
        }
        capture("dvr-14-file-unavailable-close", "DVR — unavailable file, close only", "de", 1.3f, "Schließen") {
            Recovery(
                message = "Aufnahmedatei ist nicht verfügbar",
                detail = "TVHeadend hat für diese Aufnahme keine abspielbare Datei gemeldet.",
                primary = "Schließen",
            )
        }
        capture("dvr-15-stats-maximum", "DVR — maximum diagnostics", "en", 1.3f, "none") {
            EvidenceBackdrop {
                PlaybackStatsOverlay(
                    diagnostics = maximumDiagnostics.copy(
                        source = PlaybackDiagnosticsSource.RECORDING,
                        state = PlaybackSessionState.Playing,
                        isPlaying = true,
                    ),
                    aspectRatio = AspectRatioMode.FIT,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 36.dp, end = 48.dp),
                )
            }
        }
    }

    private fun capture(
        id: String,
        scenario: String,
        locale: String,
        fontScale: Float,
        focusState: String,
        provenance: String = PRODUCTION_COMPOSABLE,
        content: @Composable () -> Unit,
    ) {
        val spec = PlayerEvidenceSpec(
            id = id,
            scenario = scenario,
            locale = locale,
            fontScale = fontScale,
            requestedFocusState = focusState,
            provenance = provenance,
        )
        paparazzi.unsafeUpdateConfig(playerDeviceConfig(locale, fontScale))
        evidenceOutput.expect(spec)
        val view = ComposeView(paparazzi.context).apply {
            setContent { TVHeadendPlayerTheme(content = content) }
        }
        paparazzi.snapshot(view = view, name = "warmup-$id", offsetMillis = 300L)
        paparazzi.snapshot(view = view, name = id, offsetMillis = 300L)
    }
}

private enum class ColorEvidenceScenario {
    SUCCESS,
    EPG_RECORDING,
    RECORDING_RESULT,
}

@Composable
private fun ColorSemanticEvidence(scenario: ColorEvidenceScenario) {
    EvidenceBackdrop {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .width(620.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = TvPanelDenseAlpha),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (scenario) {
                    ColorEvidenceScenario.SUCCESS -> {
                        Text("Connection test succeeded", color = MaterialTheme.colorScheme.onSurface)
                        Text("PIN updated", color = MaterialTheme.colorScheme.onSurface)
                    }
                    ColorEvidenceScenario.EPG_RECORDING -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RecordingStatusIndicator(state = DvrState.SCHEDULED)
                            Text("Recording status: Scheduled", color = TvRecordingColor)
                        }
                        Text("Recording scheduled", color = MaterialTheme.colorScheme.onSurface)
                    }
                    ColorEvidenceScenario.RECORDING_RESULT -> {
                        ProgrammeContentDetails(
                            event = germanEvent,
                            subtitle = "ORF 1 HD • 20:15–21:00",
                        )
                        Text("S02 E04", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Recording cancelled", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelTierEvidence() {
    EvidenceBackdrop {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            EvidencePanel("Channels", TvPanelBrowseAlpha, Modifier.weight(1f))
            EvidencePanel("EPG", TvPanelDenseAlpha, Modifier.weight(1f))
            EvidencePanel("Settings", TvPanelDenseAlpha, Modifier.weight(1f))
        }
    }
}

@Composable
private fun EvidencePanel(label: String, alpha: Float, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 220.dp),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label) }
    }
}

@Composable
private fun EvidenceBackdrop(content: @Composable BoxScope.() -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        DebugVideoBackdrop(visible = true, modifier = Modifier.fillMaxSize())
        content()
    }
}

@Composable
private fun LiveControls(
    timeshiftState: TimeshiftState,
    channelName: String = "ORF 1 HD",
    title: String = "Zeit im Bild",
) {
    EvidenceBackdrop {
        val imageLoader = ImageLoader.Builder(LocalContext.current).build()
        val event = event(title = title)
        OverlayControlsTv(
            imageLoader = imageLoader,
            channelNumber = 1,
            channelName = channelName,
            piconPath = null,
            nowEvent = event,
            nextEvent = event(
                id = 2,
                start = NOW_SEC + 1_800L,
                stop = NOW_SEC + 3_600L,
                title = "Weather and regional outlook",
            ),
            nowSec = NOW_SEC,
            controlsVisible = true,
            optionsOpen = false,
            onOpenChannels = {},
            onOpenInfo = {},
            onStopPlayback = {},
            onUserInteraction = {},
            onOpenOptions = {},
            timeshiftState = timeshiftState,
            timeshiftFeedback = null,
            onToggleTimeshiftPause = {},
            onSeekTimeshift = {},
            onGoLive = {},
        )
    }
}

@Composable
private fun RecordingControls(
    title: String,
    subtitle: String?,
    channel: String = "ORF 1 HD",
    durationMs: Long = 7_200_000L,
    growing: Boolean = false,
    isPlaying: Boolean = true,
) {
    EvidenceBackdrop {
        val imageLoader = ImageLoader.Builder(LocalContext.current).build()
        RecordingOverlayControls(
            imageLoader = imageLoader,
            piconPath = null,
            title = title,
            subtitle = subtitle,
            channelName = channel,
            positionMs = 2_880_000L,
            durationMs = durationMs,
            growing = growing,
            nowSec = NOW_SEC,
            isPlaying = isPlaying,
            controlsVisible = true,
            optionsOpen = false,
            onTogglePlayPause = {},
            onSeek = {},
            onStopPlayback = {},
            onUserInteraction = {},
            showStop = true,
            onOpenOptions = {},
            onOpenInfo = {},
        )
    }
}

@Composable
private fun LiveInfo(
    event: EpgEventEntry?,
    confirmation: LiveInfoRecordingState?,
) {
    LiveProgrammeInfoOverlay(
        event = event,
        channelIdentity = "1 · ORF 1 HD",
        channelName = "ORF 1 HD",
        recordingScheduled = false,
        canRecord = true,
        recordingState = confirmation ?: LiveInfoRecordingState.Idle,
        confirmationVisible = confirmation != null,
        restoreRecordFocus = false,
        onRecord = {},
        onRecordingActivate = {},
        onRecordingDismiss = {},
        onClose = {},
    )
}

@Composable
private fun Options(
    page: PlaybackOptionsPage,
    audioTracks: List<PlaybackOptionTrack> = listOf(
        PlaybackOptionTrack("audio-de", "Deutsch", "Dolby Digital Plus 5.1", selected = true),
        PlaybackOptionTrack("audio-en", "English", "Stereo"),
    ),
    subtitleTracks: List<PlaybackOptionTrack> = listOf(
        PlaybackOptionTrack("sub-off", "Deutsch", selected = true),
        PlaybackOptionTrack("sub-en", "English"),
    ),
) {
    PlaybackOptionsSheetContent(
        page = page,
        audioTracks = audioTracks,
        subtitleTracks = subtitleTracks,
        tracksResolving = false,
        aspectRatio = AspectRatioMode.FIT,
        statsVisible = true,
        showSimpleTvExit = false,
        simpleTvActive = false,
        onPageChange = {},
        onAudioTrackSelected = {},
        onSubtitleTrackSelected = {},
        onAspectRatioChange = {},
        onStatsVisibleChange = {},
        onSimpleTvExit = {},
    )
}

@Composable
private fun Recovery(
    message: String,
    detail: String,
    primary: String,
    hint: String? = null,
    secondary: String? = null,
) {
    EvidenceBackdrop {
        TvRecoveryOverlay(
            visible = true,
            message = message,
            detail = detail,
            hint = hint,
            opaque = false,
            primaryActionLabel = primary,
            onPrimaryAction = {},
            secondaryActionLabel = secondary,
            onSecondaryAction = secondary?.let { {} },
        )
    }
}

@Composable
private fun EvidenceChannelDrawer() {
    val imageLoader = ImageLoader.Builder(LocalContext.current).build()
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(selectedFocus) { selectedFocus.requestFocus() }
    Box(
        modifier = Modifier
            .width(480.dp)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.96f),
                    0.82f to Color.Black.copy(alpha = 0.92f),
                    1f to Color.Transparent,
                )
            )
            .padding(TvPlaybackPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(
                Triple(1, "ORF 1 HD", "Zeit im Bild"),
                Triple(2, "ORF 2 Wien HD", "Bundesland heute und ausführliche Wetteraussichten"),
                Triple(3, "3sat HD", "Kulturzeit"),
                Triple(4, "arte HD", "Dokumentation"),
                Triple(5, "BBC World News", "Global News"),
            ).forEachIndexed { index, item ->
                ChannelRow(
                    number = item.first,
                    name = item.second,
                    programTitle = item.third,
                    progress = 0.18f + index * 0.14f,
                    imageLoader = imageLoader,
                    piconPath = null,
                    focused = index == 1,
                    playingNow = index == 0,
                    recordingNow = index == 2,
                    onFocus = {},
                    onConfirm = {},
                    modifier = if (index == 1) Modifier.focusRequester(selectedFocus) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun RecordingDetailsEvidence() {
    val resumeFocus = remember { FocusRequester() }
    LaunchedEffect(resumeFocus) { resumeFocus.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.64f))
            .padding(48.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            modifier = Modifier
                .width(640.dp)
                .heightIn(max = 432.dp),
            shape = MaterialTheme.shapes.large,
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                RecordingContentDetails(
                    entry = recordingEntry,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {}, modifier = Modifier.focusRequester(resumeFocus)) {
                        Text("Fortsetzen ab 48:00")
                    }
                    Button(onClick = {}) { Text("Von Anfang an") }
                    OutlinedButton(onClick = {}) { Text("Schließen") }
                }
            }
        }
    }
}

@Composable
private fun RecordingInfoEvidence() {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(closeFocus) { closeFocus.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 56.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .heightIn(max = 420.dp),
            shape = MaterialTheme.shapes.large,
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                RecordingContentDetails(
                    entry = recordingEntry,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier
                        .align(Alignment.End)
                        .focusRequester(closeFocus),
                ) {
                    Text("Schließen")
                }
            }
        }
    }
}

private fun event(
    id: Int = 1,
    start: Long = NOW_SEC - 1_800L,
    stop: Long = NOW_SEC + 1_800L,
    title: String = "Zeit im Bild",
) = EpgEventEntry(
    eventId = id,
    channelId = 1,
    start = start,
    stop = stop,
    title = title,
    summary = "The most important stories, context, weather, and a detailed regional outlook.",
    description = "A deterministic programme synopsis long enough to exercise the player information panel without relying on a TVHeadend connection.",
    genre = "News",
    seasonNumber = 4,
    episodeNumber = 12,
)

private fun EpgEventEntry.recordingTarget() = ProgrammeRecordingTarget(
    eventId = eventId,
    channelId = channelId,
    start = start,
    stop = stop,
    title = title,
)

private val germanEvent = EpgEventEntry(
    eventId = 99,
    channelId = 1,
    start = NOW_SEC - 1_800L,
    stop = NOW_SEC + 1_800L,
    title = "Eine außergewöhnlich lange Nachrichtensendung mit ausführlicher Überschrift",
    summary = "Die wichtigsten Meldungen des Tages und ihre Hintergründe.",
    description = "Eine ausführliche, deterministische Programmbeschreibung für die Prüfung von Zeilenlängen, Informationshierarchie und Lesbarkeit aus großer Entfernung.",
    genre = "Nachrichten und Zeitgeschehen",
    seasonNumber = 12,
    episodeNumber = 348,
)

private val recordingEntry = DvrEntry(
    id = 42,
    eventId = 99,
    channelId = 1,
    start = NOW_SEC - 7_200L,
    stop = NOW_SEC,
    title = "Eine außergewöhnlich lange deutschsprachige Aufnahme mit ausführlicher Überschrift",
    subtitle = "Folge 12 · Die Zukunft des öffentlich-rechtlichen Fernsehens",
    summary = "Eine ausführliche Zusammenfassung der aufgezeichneten Sendung mit genügend Text für eine realistische Zehn-Fuß-Prüfung.",
    description = "Deterministische Beschreibung ohne Netzwerk- oder Serverabhängigkeit.",
    state = DvrState.COMPLETED,
    files = listOf(DvrFile(id = 1, path = "/recordings/evidence.ts", size = 4_294_967_296L)),
    channelName = "Österreichischer Rundfunk Nachrichten International HD",
    playPosition = 2_880L,
)

private val liveEdgeTimeshift = TimeshiftState(
    available = true,
    bufferStartMs = -7_200_000L,
    positionMs = -2_000L,
    liveEdgeMs = 0L,
)

private val behindLiveTimeshift = TimeshiftState(
    available = true,
    bufferStartMs = -7_200_000L,
    positionMs = -1_770_000L,
    liveEdgeMs = 0L,
)

private val maximumDiagnostics = PlaybackDiagnosticsSnapshot(
    source = PlaybackDiagnosticsSource.LIVE_TV,
    state = PlaybackSessionState.Recovering(retryDelayMillis = 1_500L),
    positionMs = 7_200_000L,
    durationMs = 14_400_000L,
    bufferedMs = 75_000L,
    video = PlaybackFormatDiagnostics(
        codec = "video/hevc (Main 10 profile)",
        resolution = "3840x2160",
        frameRate = 59.94f,
    ),
    audio = PlaybackFormatDiagnostics(
        codec = "audio/eac3-joc",
        language = "Deutsch (Österreich)",
        channelCount = 8,
        sampleRateHz = 192_000,
    ),
)

private data class PlayerEvidenceSpec(
    val id: String,
    val scenario: String,
    val locale: String,
    val fontScale: Float,
    val requestedFocusState: String,
    val provenance: String,
)

private class PlayerEvidenceOutput {
    private val specs = mutableListOf<PlayerEvidenceSpec>()
    private val enabled = System.getenv("GENERATE_PLAYER_VISUAL_EVIDENCE") == "1"
    private val repositoryRoot = generateSequence(
        File(requireNotNull(System.getProperty("user.dir"))),
    ) { it.parentFile }
        .first { File(it, ".git").exists() }
    private val revision = System.getenv("PLAYER_VISUAL_REVISION") ?: "unknown-revision"
    private val dirty = System.getenv("PLAYER_VISUAL_DIRTY") == "1"
    private val phase = System.getenv("PLAYER_VISUAL_PHASE") ?: "baseline"
    val outputDirectory = if (enabled) {
        File(
            repositoryRoot,
            "captures/offline/$revision${if (dirty) "-dirty" else ""}/$phase",
        )
    } else {
        File(repositoryRoot, "app/build/tmp/player-visual-evidence-skipped")
    }

    fun expect(spec: PlayerEvidenceSpec) {
        check(specs.none { it.id == spec.id }) { "Duplicate evidence id: ${spec.id}" }
        specs += spec
    }

    fun writeManifest() {
        outputDirectory.mkdirs()
        specs.forEach { spec ->
            val source = File(
                outputDirectory,
                "paparazzi/images/at.bernhardberger.tvhplayer.ui.player_" +
                    "PlayerVisualEvidenceTest_generateRequiredPlayerEvidence_${spec.id}.png",
            )
            check(source.isFile) { "Missing Paparazzi image for ${spec.id}: $source" }
            source.copyTo(File(outputDirectory, "${spec.id}.png"), overwrite = true)
        }
        File(outputDirectory, "manifest.json").writeText(buildManifest())
    }

    private fun buildManifest(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"evidenceType\": \"deterministic-offline-composable-evidence\",")
        appendLine("  \"sourceRevision\": \"${revision.json()}\",")
        appendLine("  \"dirty\": $dirty,")
        appendLine("  \"phase\": \"${phase.json()}\",")
        appendLine("  \"canvasPx\": \"960x540\",")
        appendLine("  \"density\": \"1.0 (mdpi, 160 dpi)\",")
        appendLine("  \"uiMode\": \"television-night\",")
        appendLine("  \"captures\": [")
        specs.sortedBy { it.id }.forEachIndexed { index, spec ->
            appendLine("    {")
            appendLine("      \"id\": \"${spec.id.json()}\",")
            appendLine("      \"scenario\": \"${spec.scenario.json()}\",")
            appendLine("      \"file\": \"${spec.id.json()}.png\",")
            appendLine("      \"locale\": \"${spec.locale.json()}\",")
            appendLine("      \"fontScale\": ${spec.fontScale},")
            appendLine("      \"requestedFocusState\": \"${spec.requestedFocusState.json()}\",")
            appendLine(
                "      \"focusVerification\": \"" +
                    if (spec.requestedFocusState == "none") {
                        "not-applicable"
                    } else {
                        "requested-not-semantically-verified"
                    } +
                    "\","
            )
            appendLine("      \"provenance\": \"${spec.provenance.json()}\"")
            append("    }")
            appendLine(if (index == specs.lastIndex) "" else ",")
        }
        appendLine("  ]")
        appendLine("}")
    }
}

private fun playerEvidenceHandler(output: PlayerEvidenceOutput): HtmlReportWriter {
    System.setProperty("paparazzi.test.record", "true")
    return HtmlReportWriter(
        runName = "player-visual-evidence",
        rootDirectory = File(output.outputDirectory, "paparazzi-report"),
        maxPercentDifference = 0.0,
        snapshotRootDirectory = File(output.outputDirectory, "paparazzi"),
    )
}

private fun String.json(): String = buildString {
    this@json.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
}

private fun playerDeviceConfig(locale: String, fontScale: Float) = DeviceConfig(
    screenHeight = 540,
    screenWidth = 960,
    xdpi = 160,
    ydpi = 160,
    orientation = ScreenOrientation.LANDSCAPE,
    uiMode = UiMode.TELEVISION,
    nightMode = NightMode.NIGHT,
    density = Density.MEDIUM,
    fontScale = fontScale,
    locale = locale,
    ratio = ScreenRatio.NOTLONG,
    size = ScreenSize.LARGE,
    keyboard = Keyboard.NOKEY,
    touchScreen = TouchScreen.NOTOUCH,
    keyboardState = KeyboardState.HIDDEN,
    softButtons = false,
    navigation = Navigation.DPAD,
)

private const val NOW_SEC = 1_774_569_600L
private const val PRODUCTION_COMPOSABLE = "production-composable"
private const val REPRESENTATIVE_PRODUCTION_PRIMITIVES = "representative-production-primitives"
