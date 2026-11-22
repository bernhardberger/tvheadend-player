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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.GrowingTimelineDisplayEnd
import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingTimelinePresentation
import at.bernhardberger.tvhplayer.core.SeekbarRange
import at.bernhardberger.tvhplayer.core.recordingTimelinePresentation
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.playback.RecordingSkipAction
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.touch.recordings.RecordingClock
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsTexts
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
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    settings: PlayerSettingsStore,
    artwork: ArtworkModels,
    pictureWindow: PlayerWindow,
    window: Window,
    navigate: (LeaveTrigger) -> Unit,
    fullScreen: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }
    val now = Instant.fromEpochSeconds(rememberCurrentEpochSeconds()())
    val observation by session.observation.collectAsStateWithLifecycle()
    val texts = remember(context, locale, observation.dvrConfigurationsState) {
        recordingsTexts(context, locale, observation)
    }
    val active by runtime.activeTarget.collectAsStateWithLifecycle()
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
    val installed = ownsRecordingPlayback(target, active, selection, observation)
    val ownedPlayback = playback.takeIf {
        installed || owned != null && (it as? AppPlaybackState.Failed)?.target == target
    } ?: AppPlaybackState.Starting
    val growing = recordingIsCurrentlyGrowing(observation, owned, ownedAdmission)
    val samples = remember(video.epoch, route.id) { RecordingSamples() }
    SampleRecording(runtime, samples, installed, growing, shown && playWhenReady && !inWindow)
    // Timeline geometry is recomputed only when duration changes; position is read by the face's timeline.
    val timeline = recordingTimelinePresentation(0L, samples.durationMs, growing, samples.displayMs)
    val clock = RecordingClock(now.toJavaInstant(), zone, locale) { formatClock(it, locale) }
    val info = recordingPlaybackInfo(observation, route.id, ownedAdmission, clock, texts)
    val recordedOn = info?.item?.start?.let {
        joinParts(listOf(dayText(recordingDay(it, clock)), clock.time(it.atZone(zone))))
    }.orEmpty()
    val markers = recordingMarkerPresentation(runtime, installed)
    val adBreaks = recordingAdBreakChoice(settings, route.id)
    val menus = rememberPlayerOptions(runtime, settings, target, installed) { observation.dvrEntry(it)?.channelId }
    val face = recordingPlayerState(
        target, observation, ownedPlayback, ownedAdmission, timeline, samples.position,
        marks = markers.marks, markerRevision = markers.revision,
        skipPrompt = markers.prompt, controlsSkip = markers.controlsSkip,
        adBreakSetting = adBreaks.default, adBreakPick = adBreaks.selected,
        playWhenReady = playWhenReady, now = now,
        recordedDate = { recordedOn }, finishClock = { formatClock(it.toJavaInstant().atZone(zone), locale) },
        episodeLine = texts.episode, noChannel = texts.noChannel,
        unknownTitle = stringResource(SharedR.string.recordings_title),
        tracks = menus.tracks, options = menus.options, menuControls = menus.offered, brightness = brightness,
        allowPictureInPicture = windowAvailable, artwork = artwork, failure = failure.takeIf { owned != null },
        info = info,
    )
    RecordingPlaybackFace(
        face.copy(growing = growing), menus, owned, runtime, session,
        pictureWindow, installed && video.visible, inWindow, timeline, navigate, fullScreen, { shown = it },
        onBrightness = { brightness = it; applyBrightness(window, it) },
        onAdBreakMode = { if (face.hasAdBreakOptions) adBreaks.set(it) },
    )
}

@Composable
private fun recordingPlaybackInfo(
    observation: SessionObservation,
    id: DvrEntryId,
    admission: RecordingPlaybackAdmission?,
    clock: RecordingClock,
    texts: RecordingsTexts,
): RecordingPlayerInfo? = observation.dvrEntry(id)?.let { entry ->
    recordingPlayerInfo(entry, admission, clock, texts).copy(
        credits = programmeCreditFacts(entry.eventId?.let(observation::event), LocalResources.current),
    )
}

private data class RecordingAdBreakChoice(
    val default: AdBreakMode,
    val selected: AdBreakMode?,
    val set: (AdBreakMode?) -> Unit,
)

private data class RecordingMarkerPresentation(
    val marks: List<RecordingMark>,
    val revision: Long?,
    val prompt: RecordingSkipAction?,
    val controlsSkip: RecordingSkipAction?,
)

@Composable
private fun recordingMarkerPresentation(runtime: AppPlaybackRuntime, installed: Boolean): RecordingMarkerPresentation {
    if (!installed) return RecordingMarkerPresentation(emptyList(), null, null, null)
    val marks by runtime.recordingMarks.collectAsStateWithLifecycle()
    val revision by runtime.recordingMarkerRevision.collectAsStateWithLifecycle()
    val prompt by runtime.recordingSkipPrompt.collectAsStateWithLifecycle()
    val controlsSkip by runtime.recordingControlsSkip.collectAsStateWithLifecycle()
    return RecordingMarkerPresentation(marks, revision, prompt, controlsSkip)
}

@Composable
private fun recordingAdBreakChoice(settings: PlayerSettingsStore, id: DvrEntryId): RecordingAdBreakChoice {
    val choices by settings.recordingAdBreaks.choices.collectAsStateWithLifecycle(null)
    val defaultMode by remember(settings) { settings.playerSettings.map { it.adBreakMode } }
        .collectAsStateWithLifecycle(AdBreakMode.ASK)
    val scope = rememberCoroutineScope()
    val rendered = choices
    return RecordingAdBreakChoice(defaultMode, rendered?.get(id.value)) { mode ->
        if (rendered != null) scope.launch { setRecordingAdBreakMode(settings, rendered.profile, id.value, mode) }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun RecordingPlaybackFace(
    face: RecordingPlayerState,
    menus: PlayerOptions,
    owned: RecordingPlaybackSelection?,
    runtime: AppPlaybackRuntime,
    session: TvheadendSession,
    pictureWindow: PlayerWindow,
    videoVisible: Boolean,
    inWindow: Boolean,
    timeline: RecordingTimelinePresentation,
    navigate: (LeaveTrigger) -> Unit,
    fullScreen: () -> Unit,
    onControlsShown: (Boolean) -> Unit,
    onBrightness: (Float) -> Unit,
    onAdBreakMode: (AdBreakMode?) -> Unit,
) {
    val picture = rememberStandalonePicture(face.artwork, face.artworkModel)
    SideEffect { pictureWindow.mode = menus.aspect }
    fun ownsPlayback(): Boolean =
        owned != null && runtime.activeTarget.value == AppPlaybackTarget.Recording(owned.recordingId) &&
            runtime.recordingSelection.value == owned &&
            session.observation.value.currentSession === owned.currentSession
    PlayerScreen(
        face.copy(artwork = picture),
        onAction = { action ->
            when (action) {
                PlayerAction.Back -> navigate(LeaveTrigger.Back)
                PlayerAction.PictureInPicture -> navigate(LeaveTrigger.WindowButton)
                PlayerAction.FullScreen -> fullScreen()
                else -> if (ownsPlayback()) runtime.recordingAction(timeline, action)
            }
        },
        onSeek = { if (ownsPlayback()) recordingDragMove(timeline.at(0L), it)
            ?.let { move -> runtime.seekTo(move.positionMs) } },
        onBrightness = onBrightness,
        onChoice = { if (ownsPlayback() && !menus.choose(it)) runtime.recordingChoice(it, onAdBreakMode) },
        video = { TouchVideoSurface(runtime.player, videoVisible, it, pictureWindow::attach, menus.aspect) },
        onControlsShown = onControlsShown, inWindow = inWindow, hideAfterMs = rememberControlsTimeout(),
        progressing = rememberPlayerProgressing(runtime.player).value, notices = LocalFrameNotices.current,
    )
}

private fun AppPlaybackRuntime.recordingChoice(choice: PlayerChoice, onAdBreakMode: (AdBreakMode?) -> Unit) {
    when (choice) {
        is RecordingMove.Marker -> seekRecordingMarker(choice.positionMs, choice.expectedRevision, choice.programMarks)
        is RecordingSkipChoice.Accept -> skipRecordingAdBreak(choice.action, choice.fromControls)
        is RecordingSkipChoice.Dismiss -> dismissRecordingSkipPrompt(choice.action)
        is PlayerChoice.AdBreaks -> onAdBreakMode(choice.mode)
        else -> Unit
    }
}

private suspend fun setRecordingAdBreakMode(
    settings: PlayerSettingsStore,
    profile: String?,
    recordingId: Long,
    mode: AdBreakMode?,
) {
    try {
        settings.recordingAdBreaks.set(profile, recordingId, mode)
    } catch (_: IOException) {
        // Leave the selection as read from the store.
    }
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
