package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.tv.material3.LocalContentColor
import at.bernhardberger.tvhplayer.ui.TvTextTertiaryAlpha
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.PlayerStateCell
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.core.programmeTimingDescribesPlayback
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.playback.LivePauseAvailability

/**
 * [PlayerChrome]'s controls: the header and info bar over the focusable timeline and the action row.
 * The header and artwork are passive; details opens from the action row.
 * The bar row's state cell and end and the distance behind live are passive. [decoration] hosts them, such as live's
 * quick-zap rail; while it or number entry [covered] them they take no focus and expose no semantics.
 */
@Composable
internal fun PlayerChromeControls(
    timeline: PlayerChromeTimeline,
    liveBar: LiveBarPresentation?,
    actions: PlayerChromeActions,
    /** Resets the timing-unavailable notice when it changes, such as on a zap. */
    identity: Any?,
    header: @Composable (Modifier) -> Unit,
    /**
     * The info bar with its content alpha, and the timeline row drawn the full width below it (a
     * recording's is drawn after the bar instead, with its markers).
     */
    infoBar: @Composable (contentAlpha: () -> Float, programmeFocus: Modifier, timelineRow: @Composable () -> Unit) -> Unit,
    /** What playback is doing: the bar row's state cell. */
    state: PlayerStateCell,
    /** Fraction of [PlayerBannerDrop] the footer still rests below its place, taken over from the Banner. */
    bannerDrop: () -> Float,
    /** The action row's alpha while it fades in under the risen Banner content. */
    actionsAlpha: () -> Float,
    onTogglePause: () -> Unit,
    onSeek: (Long) -> Unit,
    onStop: () -> Unit,
    onInfo: () -> Unit,
    onOptions: () -> Unit,
    /** Opens the Audio or Subtitles quick list from its action-row button. */
    onTrackList: (PlaybackOptionsPage) -> Unit,
    onInteraction: () -> Unit,
    onCommitSeek: () -> Unit,
    onPauseUnavailable: (reason: String) -> Unit,
    onActionFocused: (String) -> Unit,
    onControlFocused: (String) -> Unit,
    onFocusRestored: () -> Unit,
    onDownFromActions: () -> Unit,
    covered: Boolean,
    decoration: @Composable (emphasisAlpha: () -> Float, controls: @Composable () -> Unit) -> Unit,
    markerNavigation: RecordingMarkerNavigation,
    onSeekMarker: (Long) -> Unit,
    /** Retained trial caller input; the passive top identity does not handle keys. */
    onChannelZap: ((direction: Int, keyTimeMs: Long, repeat: Boolean) -> Unit)? = null,
    downHint: String? = null,
    onSchedule: (() -> Unit)? = null,
    scheduleAvailable: Boolean = true,
    /**
     * The passive top identity, drawn over the header. Legacy focus slots receive no focus target.
     */
    topIdentity: (@Composable (placed: Modifier, focus: Modifier, onRecentDown: () -> Unit) -> Unit)? = null,
) {
    val live = timeline as? PlayerChromeTimeline.Live
    val recording = timeline as? PlayerChromeTimeline.Recording
    val recordingRange = recording?.let(::recordingChromeRange)
    val previewing = live?.previewing ?: (recording?.targetMs != null)
    val paused = actions.paused
    val livePause = actions.livePause
    val restoreFocus = actions.restoreFocus
    val pauseFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }
    val scheduleFocus = remember { FocusRequester() }
    val audioFocus = remember { FocusRequester() }
    val subtitlesFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val timelineFocus = remember { FocusRequester() }
    val stopFocus = remember { FocusRequester() }
    val seekable = when (timeline) {
        is PlayerChromeTimeline.Live -> timeline.timeshift.available && (timeline.timeshift.timingKnown || previewing)
        is PlayerChromeTimeline.Recording -> timeline.canSeek && recordingRange != null
    }
    val timelinePosition = live?.timeshift?.let { timeshiftState ->
        if (previewing) {
            timeshiftPositionPresentation(timeshiftState.positionMs, timeshiftState.liveEdgeMs)
        } else timeshiftPositionPresentation(timeshiftState)
    }
    // Live pauses into timeshift; a recording always can.
    val pausable = live?.timeshift?.available ?: true
    // Live's timeline takes focus with timeshift, before its timing is known; a recording's while seekable.
    val timelineFocusable = if (live != null) pausable else seekable
    val pauseUnavailableReason = when (livePause) {
        LivePauseAvailability.UNAVAILABLE -> stringResource(R.string.pause_unavailable_channel)
        LivePauseAvailability.OFF -> stringResource(R.string.pause_timeshift_off)
        else -> null
    }?.takeUnless { pausable }
    var timingUnavailable by remember(identity) { mutableStateOf(false) }
    LaunchedEffect(identity, pausable, seekable) {
        timingUnavailable = false
        if (live != null && pausable && !seekable) {
            kotlinx.coroutines.delay(1_500L)
            timingUnavailable = true
        }
    }
    // The action row's entry is Play/Pause: where a reveal lands and where Down from the timeline leads.
    val initialFocus = pauseFocus
    var focusInitialized by remember { mutableStateOf(false) }
    var lastFocusedControl by remember { mutableStateOf<String?>(null) }
    var relocatingKey by remember { mutableStateOf<Key?>(null) }
    var timelineFocused by remember { mutableStateOf(false) }
    val chromeAlpha = rememberPlayerChromeAlpha(timelineFocused, previewing)
    val chromeHidden = timelineFocused && previewing
    // Capture ownership before the timeline's focus node goes. Compose may automatically focus a
    // surviving action during apply; that must not erase the disappearing node's fallback.
    val removedFocusTarget = when (lastFocusedControl) {
        "player-seekbar" -> if (timelineFocusable) timelineFocus else initialFocus
        // A track button goes with its choice (zap pending, failure, fewer tracks); its neighbour Info takes over.
        "player-audio" -> if (actions.tracks.audio != null) audioFocus else infoFocus
        "player-subtitles" -> if (actions.tracks.subtitles != null) subtitlesFocus else infoFocus
        else -> null
    }
    val pageActive = LocalPlayerPageActive.current
    val audioShown = actions.tracks.audio != null
    val subtitlesShown = actions.tracks.subtitles != null
    LaunchedEffect(actions.active, restoreFocus, seekable, pausable, pageActive, audioShown, subtitlesShown) {
        if (actions.active && pageActive) {
            val target = when (restoreFocus) {
                null -> null
                "player-info" -> infoFocus
                "player-schedule" -> scheduleFocus
                // A track button gone with its choice hands focus to its neighbour, Info.
                "player-audio" -> if (actions.tracks.audio != null) audioFocus else infoFocus
                "player-subtitles" -> if (actions.tracks.subtitles != null) subtitlesFocus else infoFocus
                "player-settings" -> settingsFocus
                "player-stop" -> stopFocus
                "player-seekbar" -> timelineFocus.takeIf { timelineFocusable }
                else -> initialFocus
            } ?: when {
                // Revealing chrome lands on the action strip; the timeline is one Up away and
                // pausing there by accident on a fresh reveal was a recurring complaint.
                !focusInitialized -> initialFocus
                removedFocusTarget != null -> removedFocusTarget
                else -> null
            }
            if (target != null) androidx.compose.runtime.withFrameNanos { }
            if (target?.requestFocus() == true) {
                focusInitialized = true
                if (restoreFocus != null) onFocusRestored()
            }
        } else {
            focusInitialized = false
        }
    }
    if (recording != null) {
        // Closing the markers returns focus to the timeline; a later reveal starts on Pause.
        val restorationBaseline = remember { markerNavigation.restoration }
        LaunchedEffect(markerNavigation.restoration) {
            if (markerNavigation.restoration > restorationBaseline && actions.active) {
                when {
                    seekable -> timelineFocus.requestFocus()
                    else -> initialFocus.requestFocus()
                }
            }
        }
        LaunchedEffect(recording.markers, seekable, actions.active) {
            if (markerNavigation.selectedMs !in recording.markers || !seekable || !actions.active) markerNavigation.dismiss()
        }
    }
    // Down from the timeline returns to the action last focused.
    var lastAction by remember { mutableStateOf("player-pause") }
    val actionFocus = when (lastAction) {
        "player-stop" -> stopFocus
        "player-settings" -> settingsFocus
        "player-info" -> infoFocus
        "player-schedule" -> scheduleFocus
        "player-audio" -> if (actions.tracks.audio != null) audioFocus else infoFocus
        "player-subtitles" -> if (actions.tracks.subtitles != null) subtitlesFocus else infoFocus
        else -> pauseFocus
    }
    val belowIdentity = if (timelineFocusable) timelineFocus else actionFocus
    val recentDown: () -> Unit = {
        relocatingKey = Key.DirectionDown
        belowIdentity.requestFocus()
    }
    // The decoration follows the action row, which fades in last when taken over from the Banner.
    decoration({ actionsAlpha() * chromeAlpha.value }) {
    PlayerOverlayChrome(
        bannerDrop = bannerDrop,
        // Covered, they also offer an accessibility service nothing to read or act on.
        modifier = Modifier.then(if (covered) Modifier.clearAndSetSemantics { } else Modifier).onPreviewKeyEvent { event ->
            recording != null && markerNavigation.handle(event, recording.markers, onSeekMarker)
        }.onPreviewKeyEvent { event ->
        if (event.key != relocatingKey) false else {
            if (event.type == KeyEventType.KeyUp) relocatingKey = null
            true
        }
    }, headerContent = { placed ->
        header(placed)
        topIdentity?.invoke(placed, Modifier, recentDown)
    }) {
        val timelineModifier = Modifier.testTag("player-seekbar").focusRequester(timelineFocus)
                    .focusProperties { canFocus = !covered }
                    .then(if (timelineFocused && !seekable && !previewing) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
                    .onFocusChanged {
                        timelineFocused = it.isFocused
                        if (it.isFocused) {
                            lastFocusedControl = "player-seekbar"
                            onControlFocused("player-seekbar")
                            // A quick list returns to the recording timeline.
                            if (recording != null) onActionFocused("player-seekbar")
                        }
                    }
                    .focusProperties { down = actionFocus; up = FocusRequester.Cancel }
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            Key.DirectionLeft, Key.DirectionRight -> {
                                if (actions.active && seekable && event.type == KeyEventType.KeyDown) {
                                    onInteraction()
                                    val step = at.bernhardberger.tvhplayer.core.seekStepMs(event.nativeKeyEvent.repeatCount)
                                    val delta = if (event.key == Key.DirectionLeft) -step else step
                                    if (recordingRange != null) {
                                        val target = (recordingRange.positionMs + delta)
                                            .coerceIn(recordingRange.startMs, recordingRange.endMs)
                                        if ((delta < 0 && target < recordingRange.positionMs) ||
                                            (delta > 0 && target > recordingRange.positionMs)) {
                                            onSeek(target - recordingRange.positionMs)
                                        }
                                    } else onSeek(delta)
                                }
                                true
                            }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                    onInteraction(); onTogglePause()
                                }
                                true
                            }
                            Key.DirectionDown, Key.DirectionUp -> {
                                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                    onCommitSeek()
                                    val markers = recording?.markers.orEmpty()
                                    if (event.key == Key.DirectionUp && markers.isNotEmpty()) {
                                        onInteraction()
                                        markerNavigation.show(markers, recording?.let { it.targetMs ?: it.positionMs } ?: 0L,
                                            event.key, recording?.markerRevision ?: 0L)
                                    } else {
                                        relocatingKey = event.key
                                        if (event.key == Key.DirectionDown) actionFocus.requestFocus()
                                        // The passive artwork is no longer a focus stop.
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    }
        if (recording != null) {
            // The step's target label paints over the info bar, which fades out meanwhile.
            val showPreview = previewing && !markerNavigation.open
            val infoAlpha by animateFloatAsState(if (showPreview) 0f else 1f,
                tween(PlayerMotion.ShortMs, easing = PlayerMotion.Standard), label = "player-info-bar-seek")
            Box(Modifier.graphicsLayer { alpha = infoAlpha }) {
                infoBar({ 1f }, Modifier) {}
            }
            RecordingControlsTimeline(
                timeline = recording,
                range = recordingRange.takeIf { seekable },
                previewing = showPreview,
                paused = paused,
                state = state,
                markerNavigation = markerNavigation,
                onSeekMarker = onSeekMarker,
                onOpenMarkers = {
                    onCommitSeek(); onInteraction()
                    markerNavigation.show(recording.markers, recording.targetMs ?: recording.positionMs,
                        revision = recording.markerRevision)
                },
                onSeekTo = { onInteraction(); onSeek(it - (recording.targetMs ?: recording.positionMs)) },
                modifier = timelineModifier,
            )
        } else if (live != null) {
        val timeshiftState = live.timeshift
        val programmeWindow = live.programmeWindow
        val timeshiftFeedback = live.feedback
        val timeshiftFeedbackIsError = live.feedbackIsError
        val previewFeedback = timeshiftFeedback ?: if (previewing) {
            if (programmeWindow?.targetAvailable == false) stringResource(R.string.timeshift_target_expired)
            else programmeWindow?.event?.title
        } else null
        val seekFeedback = when {
            timeshiftFeedbackIsError -> timeshiftFeedback
            previewing && programmeWindow?.targetAvailable == false -> stringResource(R.string.timeshift_target_expired)
            previewing && !timeshiftState.timingKnown -> stringResource(R.string.timeshift_seek_waiting)
            else -> timeshiftFeedback
        }
        // Schedule elapsed time is informational, never a playback coordinate or seek grant.
        val showTimingUnavailable = pausable && timingUnavailable && !(live.liveStart && live.programme != null)
        val scheduleFeedback = if (showTimingUnavailable && !timeshiftFeedbackIsError) {
            stringResource(R.string.player_timing_unavailable)
        } else previewFeedback
        // The info bar rests right above the timeline. A seek label or feedback paints over the
        // bar, so the bar fades out meanwhile instead of anything moving.
        val statusTextShown = if (seekable) previewing || seekFeedback != null else scheduleFeedback != null
        val infoAlpha by animateFloatAsState(if (statusTextShown) 0f else 1f,
            tween(PlayerMotion.ShortMs, easing = PlayerMotion.Standard), label = "player-info-bar-seek")
        val barStatus = if (previewing) live.barStatus(state, window = live.programmeWindow)
            else rememberPlayerBarStatus(state, liveBar?.end).takeIf { live.liveAvailable }
        infoBar({ infoAlpha }, Modifier) {
        if (seekable) {
            PlaybackSeekbar(
                range = timeshiftSeekbarRange(timeshiftState).let { range ->
                    if (previewing) range.copy(positionKnown = true) else range
                },
                timeshiftPosition = requireNotNull(timelinePosition),
                stepDeltaMs = live.stepDeltaMs,
                stepSettled = live.stepSettled,
                onStepAnnounced = live.onStepAnnounced,
                stepId = live.stepId,
                programmeWindow = programmeWindow,
                previewing = previewing,
                collapsed = false,
                barStatus = barStatus,
                timeshiftStartLabel = timeshiftStartClock(live.timeshift, live.nowSec),
                nowSec = live.nowSec.takeIf { timeshiftState.available && timeshiftState.timingKnown },
                feedback = seekFeedback,
                feedbackIsError = timeshiftFeedbackIsError || previewing && programmeWindow?.targetAvailable == false,
                paused = paused,
                onSeekTo = { onInteraction(); onSeek(it - timeshiftState.positionMs) },
                modifier = timelineModifier,
                motionKey = live.motionKey,
                schedule = live.scheduleProgress(),
            )
        } else {
            // Tuning shows the schedule; a later timing gap holds the presented row. Neither
            // supplies a playback coordinate or seek authority.
            val schedule = live.scheduleProgress()
            val event = schedule?.event
            val description = listOfNotNull(
                stringResource(R.string.player_paused).takeIf { paused },
                stringResource(R.string.player_current_broadcast).takeIf { event != null },
                stringResource(R.string.player_timing_unavailable).takeIf { event == null || showTimingUnavailable },
            ).joinToString(". ")
            PlayerPresentedLiveTimeline(
                presentation = requireNotNull(liveBar),
                state = state,
                feedback = scheduleFeedback,
                feedbackIsError = timeshiftFeedbackIsError,
                modifier = (if (pausable) timelineModifier else if (event != null) Modifier.testTag("player-schedule-progress") else Modifier)
                    .then(if (pausable || event != null) Modifier.semantics(mergeDescendants = true) {
                        contentDescription = description
                    } else Modifier)
                    .then(if (pausable) Modifier.focusable() else Modifier),
            )
        }
        }
        }
        Spacer(Modifier.height(PlayerChromeTokens.timelineActionGap))
        Box {
        PlayerActionRow(
            settingsFocus = settingsFocus,
            onSettings = onOptions,
            onStop = onStop,
            stopFocus = stopFocus,
            onInfo = onInfo,
            onSchedule = onSchedule,
            scheduleAvailable = scheduleAvailable,
            scheduleFocus = scheduleFocus,
            infoFocus = infoFocus,
            tracks = actions.tracks,
            onTrackList = onTrackList,
            audioFocus = audioFocus,
            subtitlesFocus = subtitlesFocus,
            onInteraction = onInteraction,
            onActionFocused = {
                lastFocusedControl = it; lastAction = it; onActionFocused(it); onControlFocused(it)
            },
            onTogglePause = when {
                pauseUnavailableReason != null -> { { onPauseUnavailable(pauseUnavailableReason) } }
                // The selected channel has no installed target yet; a toggle would reach the previous one.
                !pausable && livePause == LivePauseAvailability.NONE -> { {} }
                else -> { { onTogglePause() } }
            },
            pauseUnavailableReason = pauseUnavailableReason,
            paused = paused, pauseFocus = pauseFocus,
            modifier = Modifier
                // Taken over from the Banner, the actions fade in under the rising info and timeline,
                // inside the emphasis layer's expanded buffer.
                .playerChromeEmphasis(chromeAlpha, chromeHidden, focusOverflow = 8.dp, revealAlpha = actionsAlpha)
                .testTag("player-actions")
                .focusProperties {
                    canFocus = !covered
                    up = if (timelineFocusable) timelineFocus else FocusRequester.Cancel
                    down = FocusRequester.Cancel
                }
                .onPreviewKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                if (event.key == Key.DirectionUp) {
                                    relocatingKey = event.key
                                    if (timelineFocusable) timelineFocus.requestFocus()
                                } else {
                                    // The screen owns this cross-layer cycle, including its release.
                                    onDownFromActions()
                                }
                            }
                            true
                        }
                        else -> false
                    }
                },
        )
        // What Down opens, centred between the start and end groups.
        if (downHint != null) PlayerDownHint(downHint, Modifier.align(Alignment.Center)
            .pageMotion(0..140, at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy.pageAccelerate,
                entering = false, inheritedFade = 0..220)
            .graphicsLayer { alpha = actionsAlpha() * chromeAlpha.value }.testTag("player-down-hint"))
        }
    }
    }
}

@Composable
internal fun PlayerDownHint(text: String, modifier: Modifier = Modifier, up: Boolean = false, shadow: Shadow? = null) {
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = TvTextTertiaryAlpha)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.tv.material3.Icon(painterResource(R.drawable.ic_keyboard_arrow_right), contentDescription = null, tint = tint,
            modifier = Modifier.size(24.dp).rotate(if (up) -90f else 90f))
        Text(text, style = MaterialTheme.typography.labelLarge.copy(shadow = shadow), color = tint, maxLines = 1)
    }
}
