package at.bernhardberger.tvhplayer.touch.player

import android.os.SystemClock
import android.view.Window
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.GrowingTimelineDisplayEnd
import at.bernhardberger.tvhplayer.core.RecordingTimelinePresentation
import at.bernhardberger.tvhplayer.core.SeekbarRange
import at.bernhardberger.tvhplayer.core.recordingTimelinePresentation
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.touch.recordings.RecordingClock
import at.bernhardberger.tvhplayer.touch.recordings.dayText
import at.bernhardberger.tvhplayer.touch.recordings.recordingDay
import at.bernhardberger.tvhplayer.touch.recordings.recordingsTexts
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import at.bernhardberger.tvhplayer.touch.ui.LocalFrameNotices
import at.bernhardberger.tvhplayer.touch.ui.joinParts
import at.bernhardberger.tvhplayer.touch.ui.rememberStandalonePicture
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.programmeCreditFacts
import at.bernhardberger.tvhplayer.ui.common.rememberCurrentEpochSeconds
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import at.bernhardberger.tvhplayer.presentation.R as SharedR

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalTvheadendApi::class)
@Composable
internal fun RecordingPlayback(
    route: RecordingRoute,
    runtime: AppPlaybackRuntime,
    session: TvheadendSession,
    artwork: ArtworkModels,
    pictureWindow: PlayerWindow,
    window: Window,
    back: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }
    val now = Instant.fromEpochSeconds(rememberCurrentEpochSeconds()())
    val observation by session.observation.collectAsStateWithLifecycle()
    val texts = remember(context, locale, observation.dvrConfigurationsState) {
        recordingsTexts(context, locale, observation)
    }
    val currentTarget by runtime.activeTarget.collectAsStateWithLifecycle()
    val playback by runtime.state.collectAsStateWithLifecycle()
    val selection by runtime.recordingSelection.collectAsStateWithLifecycle()
    val admission by runtime.recordingAdmission.collectAsStateWithLifecycle()
    val failure by runtime.recordingPlaybackFailure.collectAsStateWithLifecycle()
    val video by runtime.videoPresentation.collectAsStateWithLifecycle()
    val inWindow by pictureWindow.inWindow.collectAsStateWithLifecycle()
    val windowAvailable by pictureWindow.available.collectAsStateWithLifecycle()
    val playWhenReady by rememberPlayWhenReady(runtime.player)
    var shown by remember(route.id) { mutableStateOf(true) }
    var brightness by remember { mutableFloatStateOf(brightnessSlider(systemBrightness(context))) }
    val target = AppPlaybackTarget.Recording(route.id)
    val owned = selection?.takeIf { it.recordingId == route.id && it.currentSession === observation.currentSession }
    val ownedAdmission = admission.takeIf { owned != null }
    val installed = currentTarget == target && owned != null
    val ownedPlayback = playback.takeIf {
        installed || owned != null && (it as? AppPlaybackState.Failed)?.target == target
    }
        ?: AppPlaybackState.Starting
    val growing = recordingIsCurrentlyGrowing(observation, owned, ownedAdmission)
    val samples = remember(video.epoch, route.id) { RecordingSamples() }
    SampleRecording(runtime, samples, installed, growing, shown && playWhenReady && !inWindow)
    // Timeline geometry is recomputed only when duration changes; position is read by the face's timeline.
    val timeline = recordingTimelinePresentation(0L, samples.durationMs, growing, samples.displayMs)
    val clock = RecordingClock(now.toJavaInstant(), zone, locale) { formatClock(it, locale) }
    val info = observation.dvrEntry(route.id)?.let { entry ->
        val event = entry.eventId?.let(observation::event)
        recordingPlayerInfo(entry, ownedAdmission, clock, texts).copy(
            credits = programmeCreditFacts(event, LocalResources.current),
        )
    }
    val recordedOn = info?.item?.start?.let {
        joinParts(listOf(dayText(recordingDay(it, clock)), clock.time(it.atZone(zone))))
    }.orEmpty()
    val face = recordingPlayerState(
        target, observation, ownedPlayback, ownedAdmission, timeline, samples.position,
        marks = emptyList(), markerRevision = null, skipPrompt = null, controlsSkip = null,
        adBreakSetting = AdBreakMode.OFF, adBreakPick = null, playWhenReady = playWhenReady, now = now,
        recordedDate = { recordedOn },
        finishClock = { formatClock(it.toJavaInstant().atZone(zone), locale) },
        episodeLine = texts.episode, noChannel = texts.noChannel,
        unknownTitle = stringResource(SharedR.string.recordings_title),
        tracks = PlayerTracks(), options = PlaybackOptions(), brightness = brightness,
        allowPictureInPicture = windowAvailable, artwork = artwork, failure = failure.takeIf { owned != null },
        info = info,
    )
    RecordingPlaybackFace(
        face.copy(growing = growing, offered = recordingRuntimeControls(face.offered)), owned, runtime, session,
        pictureWindow, installed && video.visible, inWindow, timeline, back, { shown = it },
        onBrightness = { brightness = it; applyBrightness(window, it) },
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun RecordingPlaybackFace(
    face: RecordingPlayerState,
    owned: RecordingPlaybackSelection?,
    runtime: AppPlaybackRuntime,
    session: TvheadendSession,
    pictureWindow: PlayerWindow,
    videoVisible: Boolean,
    inWindow: Boolean,
    timeline: RecordingTimelinePresentation,
    back: () -> Unit,
    onControlsShown: (Boolean) -> Unit,
    onBrightness: (Float) -> Unit,
) {
    val picture = rememberStandalonePicture(face.artwork, face.artworkModel)
    SideEffect { pictureWindow.mode = AspectRatioMode.FIT }
    fun ownsPlayback(): Boolean =
        owned != null && runtime.activeTarget.value == AppPlaybackTarget.Recording(owned.recordingId) &&
            runtime.recordingSelection.value == owned &&
            session.observation.value.currentSession === owned.currentSession
    PlayerScreen(
        face.copy(artwork = picture),
        onAction = { action ->
            when (action) {
                PlayerAction.Back -> back()
                PlayerAction.PictureInPicture -> pictureWindow.enter()
                else -> if (ownsPlayback()) runtime.recordingAction(timeline, action)
            }
        },
        onSeek = { if (ownsPlayback()) recordingDragMove(timeline.at(0L), it)
            ?.let { move -> runtime.seekTo(move.positionMs) } },
        onBrightness = onBrightness,
        onChoice = {},
        video = { TouchVideoSurface(runtime.player, videoVisible, it, pictureWindow::attach) },
        onControlsShown = onControlsShown, inWindow = inWindow, hideAfterMs = rememberControlsTimeout(),
        progressing = rememberPlayerProgressing(runtime.player).value, notices = LocalFrameNotices.current,
    )
}

@OptIn(UnstableApi::class)
private fun AppPlaybackRuntime.recordingAction(timeline: RecordingTimelinePresentation, action: PlayerAction) {
    if (action == PlayerAction.TogglePlay) {
        if (player.playWhenReady) pause() else play()
    } else recordingSkipMove(timeline.at(player.currentPosition), action)?.let { seekTo(it.positionMs) }
}

private fun RecordingTimelinePresentation.at(positionMs: Long): SeekbarRange? =
    (this as? RecordingTimelinePresentation.Seekable)?.range?.let {
        it.copy(positionMs = positionMs.coerceIn(it.startMs, it.endMs))
    }

internal fun recordingRuntimeControls(offered: Set<PlayerControl>): Set<PlayerControl> = offered.intersect(
    setOf(PlayerControl.FullScreen, PlayerControl.Info, PlayerControl.Timeshift, PlayerControl.PictureInPicture),
)

@kotlin.OptIn(ExperimentalTvheadendApi::class)
// Matches the TV's currentRecordingIsGrowing: the installed admission alone can outlive recording.
internal fun recordingIsCurrentlyGrowing(
    observation: SessionObservation,
    selection: RecordingPlaybackSelection?,
    admission: RecordingPlaybackAdmission?,
): Boolean = admission is RecordingPlaybackAdmission.Growing && selection != null &&
    observation.currentSession === selection.currentSession &&
    observation.dvrEntry(selection.recordingId)?.let(observation::recordingPlaybackAdmission) is
    RecordingPlaybackAdmission.Growing

private class RecordingSamples {
    private val positionMs = mutableLongStateOf(0L)
    val position: State<Long> get() = positionMs
    var durationMs by mutableStateOf<Long?>(null)
        private set
    var displayMs by mutableStateOf<Long?>(null)
        private set
    private val display = GrowingTimelineDisplayEnd()

    fun update(position: Long, duration: Long, growing: Boolean) {
        positionMs.longValue = position.coerceAtLeast(0L)
        durationMs = duration.takeIf { it > 0 }
        displayMs = display.update(duration.coerceAtLeast(0L), SystemClock.elapsedRealtime(), growing)
            .takeIf { it > 0 }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SampleRecording(
    runtime: AppPlaybackRuntime,
    samples: RecordingSamples,
    installed: Boolean,
    growing: Boolean,
    fast: Boolean,
) {
    val player = runtime.player
    LaunchedEffect(samples, installed, growing, fast) {
        while (installed) {
            samples.update(player.currentPosition, player.duration, growing)
            delay(if (fast) ACTIVE_SAMPLE_MS else IDLE_SAMPLE_MS)
        }
    }
}

private const val ACTIVE_SAMPLE_MS = 250L
private const val IDLE_SAMPLE_MS = 1_000L
