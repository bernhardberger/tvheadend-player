package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.alpha
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.Alignment
import androidx.tv.material3.Icon
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
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.core.programmeTimingDescribesPlayback
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import coil3.ImageLoader

@Composable
fun OverlayControlsTv(
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation? = null,
    channelNumber: Int?,
    channelName: String,
    piconPath: String?,
    nowEvent: EpgEvent?,
    nextEvent: EpgEvent?,
    nowSec: Long,
    controlsVisible: Boolean,
    optionsOpen: Boolean,
    onOpenChannels: () -> Unit,
    onOpenInfo: () -> Unit = {},
    onOpenRecord: () -> Unit = onOpenInfo,
    onStopPlayback: () -> Unit,
    onUserInteraction: () -> Unit,
    onOpenOptions: () -> Unit,
    timeshiftState: AppTimeshiftState,
    timeshiftFeedback: String?,
    onToggleTimeshiftPause: () -> Unit,
    onSeekTimeshift: (Long) -> Unit,
    onGoLive: () -> Unit,
    restoreInfoFocus: Boolean = false,
    onInfoFocusRestored: () -> Unit = {},
    restoreRecordActionFocus: Boolean = false,
    onRecordActionFocusRestored: () -> Unit = {},
    restoreOptionsFocus: Boolean = false,
    onOptionsFocusRestored: () -> Unit = {},
    onCommitSeek: () -> Unit = {},
    liveAvailable: Boolean = true,
    channelRecordingNow: Boolean = false,
    nextScheduled: Boolean = false,
    paused: Boolean = false,
    committedTimeshiftState: AppTimeshiftState = timeshiftState,
    committedWindow: ProgrammeWindow? = null,
    programmeWindow: ProgrammeWindow? = null,
    previewing: Boolean = false,
    channelsAvailable: Boolean = true,
    restoreChannelAction: String? = null,
    onChannelActionRestored: () -> Unit = {},
    onActionFocused: (String) -> Unit = {},
    timeshiftFeedbackIsError: Boolean = timeshiftFeedback != null,
    channelRailOpen: Boolean = false,
    channelRailContent: @Composable () -> Unit = {},
) {
    val pauseFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val recordFocus = remember { FocusRequester() }
    val timelineFocus = remember { FocusRequester() }
    val goLiveFocus = remember { FocusRequester() }
    val seekable = timeshiftState.available && (timeshiftState.timingKnown || previewing)
    val timelinePosition = if (previewing) {
        timeshiftPositionPresentation(timeshiftState.positionMs, timeshiftState.liveEdgeMs)
    } else timeshiftPositionPresentation(timeshiftState)
    val pausable = timeshiftState.available
    var timingUnavailable by remember(channelNumber, channelName) { mutableStateOf(false) }
    LaunchedEffect(channelNumber, channelName, pausable, seekable) {
        timingUnavailable = false
        if (pausable && !seekable) {
            kotlinx.coroutines.delay(1_500L)
            timingUnavailable = true
        }
    }
    val initialFocus = if (pausable) pauseFocus else infoFocus
    val programmeTimeKnown = committedWindow != null || programmeTimingDescribesPlayback(committedTimeshiftState)
    val programmeTitle = nowEvent?.takeIf { programmeTimeKnown }?.title.orEmpty()
    var focusInitialized by remember { mutableStateOf(false) }
    var lastFocusedControl by remember { mutableStateOf<String?>(null) }
    var relocatingKey by remember { mutableStateOf<Key?>(null) }
    var timelineFocused by remember { mutableStateOf(false) }
    var restoreGoLiveAfterPreview by remember { mutableStateOf(false) }
    val atLive = when {
        !liveAvailable -> null
        !timeshiftState.available -> true
        seekable -> timelinePosition.atLiveEdge
        else -> null
    }
    LaunchedEffect(previewing, restoreGoLiveAfterPreview, controlsVisible) {
        if (restoreGoLiveAfterPreview && !previewing) {
            if (controlsVisible && atLive == false) goLiveFocus.requestFocus()
            restoreGoLiveAfterPreview = false
        }
    }
    // Capture ownership before removing focus nodes. Compose may automatically focus a
    // surviving action during apply; that must not erase the disappearing node's fallback.
    val removedFocusTarget = when (lastFocusedControl) {
        "player-go-live" -> initialFocus.takeIf { atLive != false }
        "player-pause" -> infoFocus.takeIf { !pausable }
        "player-seekbar" -> if (pausable) timelineFocus else initialFocus
        else -> null
    }
    LaunchedEffect(controlsVisible, optionsOpen, restoreInfoFocus, restoreRecordActionFocus, restoreOptionsFocus, restoreChannelAction, seekable, pausable, atLive) {
        if (controlsVisible && !optionsOpen) {
            val target = when {
                restoreInfoFocus -> infoFocus
                restoreRecordActionFocus -> recordFocus
                restoreOptionsFocus -> settingsFocus
                restoreChannelAction != null -> when (restoreChannelAction) {
                    "player-info" -> infoFocus
                    "player-record" -> recordFocus
                    "player-settings" -> settingsFocus
                    else -> initialFocus
                }
                // Revealing chrome lands on the action strip; the timeline is one Up away and
                // pausing there by accident on a fresh reveal was a recurring complaint.
                !focusInitialized -> initialFocus
                removedFocusTarget != null -> removedFocusTarget
                else -> null
            }
            if (target != null) androidx.compose.runtime.withFrameNanos { }
            if (target?.requestFocus() == true) {
                focusInitialized = true
                if (restoreInfoFocus) onInfoFocusRestored()
                if (restoreRecordActionFocus) onRecordActionFocusRestored()
                if (restoreOptionsFocus) onOptionsFocusRestored()
                if (restoreChannelAction != null) onChannelActionRestored()
            }
        } else {
            focusInitialized = false
        }
    }
    PlayerOverlayChrome(modifier = Modifier.onPreviewKeyEvent { event ->
        if (event.key != relocatingKey) false else {
            if (event.type == KeyEventType.KeyUp) relocatingKey = null
            true
        }
    }, headerContent = { modifier ->
        PlayerIdentityHeader(
            imageLoader = imageLoader, currentSession = currentSession, piconPath = piconPath,
            eyebrow = channelTitleText(channelNumber, channelName) +
                if (channelRecordingNow) " / " + stringResource(R.string.player_shelf_recording) else "",
            title = programmeTitle,
            support = if (!programmeTimeKnown) stringResource(R.string.player_programme_timing_unavailable)
            else if (programmeTitle.isBlank()) stringResource(R.string.player_info_unavailable_title) else nextEvent?.let {
                stringResource(R.string.player_next_event_with_range,
                    "${formatClock(it.start.epochSeconds)} - ${formatClock(it.stop.epochSeconds)}", it.title.orEmpty()) +
                    if (nextScheduled) " / " + stringResource(R.string.recording_state_scheduled) else ""
            },
            clock = formatClock(nowSec), clockSupport = null,
            tags = PlayerHeaderTags(picon = "player-picon", eyebrow = "player-channel-identity",
                title = "player-programme-title", support = "player-next-programme", clock = "player-clock"),
            modifier = modifier.alpha(if (previewing || timelineFocused) 0.45f else 1f),
        )
    }) {
        val previewFeedback = timeshiftFeedback ?: if (previewing) {
            if (programmeWindow?.targetAvailable == false) stringResource(R.string.timeshift_target_expired)
            else programmeWindow?.event?.title
        } else null
        val statusAction: @Composable () -> Unit = {
                if (atLive == true) {
                    Text(stringResource(R.string.timeshift_live),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("player-live-status"))
                } else if (atLive == false) {
                    Button(
                        onClick = { onUserInteraction(); onGoLive() },
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .height(at.bernhardberger.tvhplayer.ui.TvOverlayStatusRowHeight)
                            .testTag("player-go-live")
                            .focusRequester(goLiveFocus)
                            .focusProperties {
                                left = settingsFocus
                                right = FocusRequester.Cancel
                                down = if (pausable) timelineFocus else initialFocus
                                up = FocusRequester.Cancel
                            }
                            .onFocusChanged {
                                if (it.isFocused) { lastFocusedControl = "player-go-live"; onUserInteraction() }
                            }
                             .onPreviewKeyEvent { event ->
                                 when (event.key) {
                                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                            relocatingKey = event.key
                                            onUserInteraction()
                                            onGoLive()
                                        }
                                        true
                                    }
                                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                            relocatingKey = event.key
                                            when (event.key) {
                                                  Key.DirectionDown -> if (pausable) timelineFocus.requestFocus() else initialFocus.requestFocus()
                                                 Key.DirectionLeft -> settingsFocus.requestFocus()
                                                else -> Unit
                                            }
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            },
                    ) { Text(stringResource(R.string.timeshift_go_live), maxLines = 1) }
                }
        }
        val timelineModifier = Modifier.testTag("player-seekbar").focusRequester(timelineFocus)
                    .focusProperties { canFocus = !channelRailOpen }
                    .then(if (timelineFocused && !seekable && !previewing) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
                    .onFocusChanged {
                        timelineFocused = it.isFocused
                        if (it.isFocused) lastFocusedControl = "player-seekbar"
                    }
                    .focusProperties { down = initialFocus; up = if (atLive == false) goLiveFocus else FocusRequester.Cancel }
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            Key.DirectionLeft, Key.DirectionRight -> {
                                if (controlsVisible && seekable && event.type == KeyEventType.KeyDown) {
                                    onUserInteraction()
                                    val step = at.bernhardberger.tvhplayer.core.seekStepMs(event.nativeKeyEvent.repeatCount)
                                    onSeekTimeshift(if (event.key == Key.DirectionLeft) -step else step)
                                }
                                true
                            }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                    onUserInteraction(); onToggleTimeshiftPause()
                                }
                                true
                            }
                            Key.DirectionDown, Key.DirectionUp -> {
                                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                    onCommitSeek()
                                    relocatingKey = event.key
                                     if (event.key == Key.DirectionDown) initialFocus.requestFocus()
                                      else if (atLive == false) {
                                          if (previewing) restoreGoLiveAfterPreview = true
                                          else goLiveFocus.requestFocus()
                                      }
                                }
                                true
                            }
                            else -> false
                        }
                    }
        if (seekable) {
            PlaybackSeekbar(
                range = timeshiftSeekbarRange(timeshiftState).let { range ->
                    if (previewing) range.copy(positionKnown = true) else range
                },
                timeshiftPosition = timelinePosition,
                programmeWindow = programmeWindow,
                previewing = previewing,
                collapsed = channelRailOpen,
                reserveStatusSpace = true,
                statusAction = statusAction,
                feedback = when {
                    timeshiftFeedbackIsError -> timeshiftFeedback
                    previewing && programmeWindow?.targetAvailable == false -> stringResource(R.string.timeshift_target_expired)
                    previewing && !timeshiftState.timingKnown -> stringResource(R.string.timeshift_seek_waiting)
                    else -> timeshiftFeedback
                },
                feedbackIsError = timeshiftFeedbackIsError || previewing && programmeWindow?.targetAvailable == false,
                paused = paused,
                onSeekTo = { onUserInteraction(); onSeekTimeshift(it - timeshiftState.positionMs) },
                modifier = timelineModifier,
            )
        } else {
            // Schedule elapsed time is informational, never a playback coordinate or seek grant.
            val showTimingUnavailable = pausable && timingUnavailable
            val event = nowEvent?.takeIf {
                it.start.epochSeconds <= nowSec && nowSec < it.stop.epochSeconds
            }
            val description = listOfNotNull(
                stringResource(R.string.player_paused).takeIf { paused },
                stringResource(R.string.player_current_broadcast).takeIf { event != null },
                stringResource(R.string.player_timing_unavailable).takeIf { event == null || showTimingUnavailable },
            ).joinToString(". ")
            PlayerTimelineBlock(
                progress = event?.let { ((nowSec - it.start.epochSeconds).toDouble() /
                    (it.stop.epochSeconds - it.start.epochSeconds)).toFloat() },
                collapsed = channelRailOpen,
                tone = PlayerTimelineTone.AMBIENT,
                fillColor = MaterialTheme.colorScheme.primary,
                showTrack = true,
                leadingLabel = event?.let { formatClock(it.start.epochSeconds) },
                trailingLabel = event?.let { formatClock(it.stop.epochSeconds) },
                reserveLabelSpace = true,
                reserveStatusSpace = true,
                statusAction = statusAction,
                feedback = if (showTimingUnavailable && !timeshiftFeedbackIsError) {
                    stringResource(R.string.player_timing_unavailable)
                } else previewFeedback,
                feedbackIsError = timeshiftFeedbackIsError,
                timelineModifier = (if (pausable) timelineModifier else if (event != null) Modifier.testTag("player-schedule-progress") else Modifier)
                    .then(if (pausable || event != null) Modifier.semantics(mergeDescendants = true) {
                        contentDescription = description + event?.let {
                            ". ${formatClock(it.start.epochSeconds)} - ${formatClock(it.stop.epochSeconds)}"
                        }.orEmpty()
                    } else Modifier)
                    .then(if (pausable) Modifier.focusable() else Modifier),
            )
        }
        Spacer(Modifier.height(8.dp))
        PlayerActionRow(
            infoFocus = infoFocus, settingsFocus = settingsFocus,
            onInfo = onOpenInfo, onSettings = onOpenOptions, onRecord = onOpenRecord,
            recordFocus = recordFocus,
            onStop = onStopPlayback,
            onInteraction = onUserInteraction,
            onActionFocused = { lastFocusedControl = it; onActionFocused(it) },
            goLiveFocus = goLiveFocus.takeIf { atLive == false },
            onTogglePause = { onToggleTimeshiftPause() }.takeIf { pausable },
            paused = paused, pauseFocus = pauseFocus,
            modifier = Modifier
                .alpha(if (previewing || timelineFocused) 0.55f else 1f)
                .testTag("player-actions")
                .focusProperties {
                    canFocus = !channelRailOpen
                    up = if (pausable) timelineFocus else FocusRequester.Cancel
                    down = FocusRequester.Cancel
                }
                .onPreviewKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                if (event.key == Key.DirectionUp) {
                                    relocatingKey = event.key
                                     if (pausable) timelineFocus.requestFocus()
                                } else if (channelsAvailable) {
                                    // The screen owns this cross-layer cycle, including its release.
                                    onOpenChannels()
                                }
                            }
                            true
                        }
                        else -> false
                    }
                },
        )
        androidx.compose.animation.AnimatedVisibility(
            visible = channelRailOpen,
            enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(LIVE_PLAYER_LAYER_TRANSITION_MS), expandFrom = Alignment.Bottom),
            exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(LIVE_PLAYER_LAYER_TRANSITION_MS), shrinkTowards = Alignment.Bottom),
        ) { channelRailContent() }
    }
}
