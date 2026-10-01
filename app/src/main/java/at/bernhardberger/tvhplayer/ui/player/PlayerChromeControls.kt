package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.tv.material3.LocalContentColor
import at.bernhardberger.tvhplayer.core.PlayerStateCell
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
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineActionGap

/**
 * [PlayerChrome]'s controls: the header and info bar, whose identity card takes focus and opens Info,
 * over the focusable timeline and the action row.
 * The bar row's state cell and end and the distance behind live are passive. [decoration] hosts them, such as live's
 * quick-zap rail; while it [covered] them they take no focus.
 */
@Composable
internal fun PlayerChromeControls(
    timeline: PlayerChromeTimeline,
    liveBar: LiveBarPresentation?,
    actions: PlayerChromeActions,
    /** Resets the timing-unavailable notice when it changes, such as on a zap. */
    identity: Any?,
    header: @Composable (Modifier) -> Unit,
    /** The info bar with its content alpha and its identity card's place in the focus graph. */
    infoBar: @Composable (contentAlpha: () -> Float, card: Modifier) -> Unit,
    /** What playback is doing: the bar row's state cell. */
    state: PlayerStateCell,
    /** Fraction of [PlayerBannerDrop] the footer still rests below its place, taken over from the Banner. */
    bannerDrop: () -> Float,
    /** The action row's alpha while it fades in under the risen Banner content. */
    actionsAlpha: () -> Float,
    onTogglePause: () -> Unit,
    onSeek: (Long) -> Unit,
    onStop: () -> Unit,
    onRecord: () -> Unit,
    onOptions: () -> Unit,
    onInteraction: () -> Unit,
    onCommitSeek: () -> Unit,
    onPauseUnavailable: (reason: String) -> Unit,
    onActionFocused: (String) -> Unit,
    onFocusRestored: () -> Unit,
    onDownFromActions: () -> Unit,
    covered: Boolean,
    decoration: @Composable (emphasisAlpha: () -> Float, controls: @Composable () -> Unit) -> Unit,
    markerNavigation: RecordingMarkerNavigation,
    onSeekMarker: (Long) -> Unit,
) {
    val live = timeline as? PlayerChromeTimeline.Live
    val recording = timeline as? PlayerChromeTimeline.Recording
    val recordingRange = recording?.let(::recordingChromeRange)
    val previewing = live?.previewing ?: (recording?.targetMs != null)
    val paused = actions.paused
    val livePause = actions.livePause
    val restoreFocus = actions.restoreFocus
    val pauseFocus = remember { FocusRequester() }
    val cardFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val recordFocus = remember { FocusRequester() }
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
    // The action row's entry is Play/Pause: where a reveal lands and where Down from the timeline or the card leads.
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
        else -> null
    }
    LaunchedEffect(actions.active, restoreFocus, seekable, pausable) {
        if (actions.active) {
            val target = when (restoreFocus) {
                null -> null
                PlayerIdentityCardTag -> cardFocus
                "player-record" -> recordFocus.takeIf { actions.record }
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
        // Closing the markers returns focus to the timeline, or with Up moves it on to the card; a
        // later reveal starts on Pause.
        val restorationBaseline = remember { markerNavigation.restoration }
        LaunchedEffect(markerNavigation.restoration) {
            if (markerNavigation.restoration > restorationBaseline && actions.active) {
                when {
                    markerNavigation.closedUpward -> cardFocus.requestFocus()
                    seekable -> timelineFocus.requestFocus()
                    else -> initialFocus.requestFocus()
                }
            }
        }
        LaunchedEffect(recording.markers, seekable, actions.active) {
            if (markerNavigation.selectedMs !in recording.markers || !seekable || !actions.active) markerNavigation.dismiss()
        }
    }
    decoration({ chromeAlpha.value }) {
    PlayerOverlayChrome(
        bannerDrop = bannerDrop,
        modifier = Modifier.onPreviewKeyEvent { event ->
            recording != null && markerNavigation.handle(event, recording.markers, onSeekMarker)
        }.onPreviewKeyEvent { event ->
        if (event.key != relocatingKey) false else {
            if (event.type == KeyEventType.KeyUp) relocatingKey = null
            true
        }
    }, headerContent = header) {
        val timelineModifier = Modifier.testTag("player-seekbar").focusRequester(timelineFocus)
                    .focusProperties { canFocus = !covered }
                    .then(if (timelineFocused && !seekable && !previewing) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
                    .onFocusChanged {
                        timelineFocused = it.isFocused
                        if (it.isFocused) {
                            lastFocusedControl = "player-seekbar"
                            // A quick list returns to the recording timeline.
                            if (recording != null) onActionFocused("player-seekbar")
                        }
                    }
                    .focusProperties { down = initialFocus; up = cardFocus }
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
                                        if (event.key == Key.DirectionDown) initialFocus.requestFocus()
                                        else cardFocus.requestFocus()
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    }
        // The card stands above everything else that takes focus: Down leads to the timeline or,
        // when that takes no focus, to the action row's entry; nothing else is reachable from it.
        val cardBelow = if (timelineFocusable) timelineFocus else initialFocus
        val cardModifier = Modifier.focusRequester(cardFocus)
            .onFocusChanged {
                if (it.isFocused) {
                    lastFocusedControl = PlayerIdentityCardTag
                    onActionFocused(PlayerIdentityCardTag)
                    onInteraction()
                }
            }
            .focusProperties {
                canFocus = !covered
                left = FocusRequester.Cancel; right = FocusRequester.Cancel; up = FocusRequester.Cancel
                down = cardBelow
            }
            .onPreviewKeyEvent { event ->
                if (event.key != Key.DirectionDown) false else {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        relocatingKey = event.key
                        cardBelow.requestFocus()
                    }
                    true
                }
            }
        if (recording != null) {
            // The step's target label paints over the info bar, which fades out meanwhile.
            val infoAlpha by animateFloatAsState(if (previewing && !markerNavigation.open) 0f else 1f,
                tween(PlayerMotion.ShortMs, easing = PlayerMotion.Standard), label = "player-info-bar-seek")
            Box(Modifier.graphicsLayer { alpha = infoAlpha }) {
                infoBar({ 1f }, cardModifier)
            }
            RecordingControlsTimeline(
                timeline = recording,
                range = recordingRange.takeIf { seekable },
                previewing = previewing && !markerNavigation.open,
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
        infoBar({ infoAlpha }, cardModifier)
        val barStatus = if (previewing) live.barStatus(state, window = live.programmeWindow)
            else rememberPlayerBarStatus(state, liveBar?.end).takeIf { live.liveAvailable }
        if (seekable) {
            PlaybackSeekbar(
                range = timeshiftSeekbarRange(timeshiftState).let { range ->
                    if (previewing) range.copy(positionKnown = true) else range
                },
                timeshiftPosition = requireNotNull(timelinePosition),
                stepDeltaMs = live.stepDeltaMs,
                programmeWindow = programmeWindow,
                previewing = previewing,
                collapsed = false,
                barStatus = barStatus,
                timeshiftStartLabel = timeshiftStartClock(live.timeshift, live.nowSec),
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
        Spacer(Modifier.height(TvOverlayTimelineActionGap))
        PlayerActionRow(
            settingsFocus = settingsFocus,
            onSettings = onOptions, onRecord = onRecord.takeIf { actions.record },
            recordFocus = recordFocus,
            onStop = onStop,
            stopFocus = stopFocus,
            onInteraction = onInteraction,
            onActionFocused = { lastFocusedControl = it; onActionFocused(it) },
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
                    up = if (timelineFocusable) timelineFocus else cardFocus
                    down = FocusRequester.Cancel
                }
                .onPreviewKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                if (event.key == Key.DirectionUp) {
                                    relocatingKey = event.key
                                    if (timelineFocusable) timelineFocus.requestFocus() else cardFocus.requestFocus()
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
    }
    }
}
