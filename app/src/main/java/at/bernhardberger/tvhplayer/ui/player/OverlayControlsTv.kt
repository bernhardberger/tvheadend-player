package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.ui.TvOverlayActionButtonSize
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
import at.bernhardberger.tvhplayer.ui.components.ProgressStrip
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
) {
    val pauseFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val recordFocus = remember { FocusRequester() }
    val timelineFocus = remember { FocusRequester() }
    val goLiveFocus = remember { FocusRequester() }
    val seekable = timeshiftState.available && timeshiftState.timingKnown
    val pausable = timeshiftState.available
    val initialFocus = if (pausable) pauseFocus else infoFocus
    val programmeTimeKnown = committedWindow != null || programmeTimingDescribesPlayback(committedTimeshiftState)
    val programmeTitle = nowEvent?.takeIf { programmeTimeKnown }?.title.orEmpty()
    var focusInitialized by remember { mutableStateOf(false) }
    var lastFocusedControl by remember { mutableStateOf<String?>(null) }
    var relocatingKey by remember { mutableStateOf<Key?>(null) }
    var timelineFocused by remember { mutableStateOf(false) }
    val atLive = when {
        !liveAvailable -> null
        !timeshiftState.available -> true
        timeshiftState.timingKnown -> timeshiftPositionPresentation(timeshiftState).atLiveEdge
        else -> null
    }
    // Capture ownership before removing focus nodes. Compose may automatically focus a
    // surviving action during apply; that must not erase the disappearing node's fallback.
    val removedFocusTarget = when (lastFocusedControl) {
        "player-go-live" -> initialFocus.takeIf { atLive != false }
        "player-pause" -> infoFocus.takeIf { !pausable }
        "player-seekbar" -> initialFocus.takeIf { !seekable }
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
        if (timeshiftState.available || atLive != null || previewFeedback != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(TvOverlayActionButtonSize)
                    .testTag("player-timeline-status"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (previewFeedback != null) {
                    val errorFeedback = timeshiftFeedback != null || programmeWindow?.targetAvailable == false
                    Text(previewFeedback,
                        modifier = Modifier.weight(1f).padding(end = 24.dp)
                            .then(if (errorFeedback) Modifier.background(MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.shapes.small).padding(horizontal = 8.dp) else Modifier)
                            .testTag("player-window-title"),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (errorFeedback) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else Spacer(Modifier.weight(1f))
                if (atLive == true) {
                    Text(stringResource(R.string.timeshift_live),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("player-live-status"))
                } else if (atLive == false) {
                    Button(
                        onClick = { onUserInteraction(); onGoLive() },
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                        modifier = Modifier
                            .testTag("player-go-live")
                            .focusRequester(goLiveFocus)
                            .focusProperties {
                                left = settingsFocus
                                right = FocusRequester.Cancel
                                down = if (seekable) timelineFocus else initialFocus
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
                                                 Key.DirectionDown -> if (seekable) timelineFocus.requestFocus() else initialFocus.requestFocus()
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
        }
        if (timeshiftState.available) {
            // Keep status and preview targets anchored even before a seek starts.
            Spacer(Modifier.height(with(LocalDensity.current) { MaterialTheme.typography.titleMedium.lineHeight.toDp() }))
        }
        if (timeshiftState.available) {
            PlaybackSeekbar(
                range = timeshiftSeekbarRange(timeshiftState),
                timeshiftPosition = timeshiftPositionPresentation(timeshiftState),
                programmeWindow = programmeWindow,
                previewing = previewing,
                showFeedback = false,
                feedback = timeshiftFeedback ?: if (previewing && programmeWindow?.targetAvailable == false) {
                    stringResource(R.string.timeshift_target_expired)
                } else null,
                paused = paused,
                onSeekTo = { onUserInteraction(); onSeekTimeshift(it - timeshiftState.positionMs) },
                modifier = Modifier.testTag("player-seekbar").focusRequester(timelineFocus)
                    .onFocusChanged {
                        timelineFocused = it.isFocused
                        if (it.isFocused) lastFocusedControl = "player-seekbar"
                    }
                    .focusProperties { down = initialFocus; up = if (atLive == false) goLiveFocus else FocusRequester.Cancel }
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
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
                                     else if (atLive == false) goLiveFocus.requestFocus()
                                }
                                true
                            }
                            else -> false
                        }
                    },
            )
        }
        if (!timeshiftState.available) {
            // Schedule elapsed time is informational, never a playback coordinate or seek grant.
            nowEvent?.takeIf {
                it.start.epochSeconds <= nowSec && nowSec < it.stop.epochSeconds
            }?.let { event ->
                val start = event.start.epochSeconds
                val end = event.stop.epochSeconds
                val description = stringResource(R.string.player_current_broadcast)
                ProgressStrip(
                    progress = ((nowSec - start).toDouble() / (end - start)).toFloat(),
                    modifier = Modifier.fillMaxWidth().testTag("player-schedule-progress")
                        .semantics { contentDescription = description },
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatClock(start), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(formatClock(end), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
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
                .alpha(if (timelineFocused) 0.55f else 1f)
                .testTag("player-actions")
                .focusProperties {
                    up = if (seekable) timelineFocus else FocusRequester.Cancel
                    down = FocusRequester.Cancel
                }
                .onPreviewKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                if (event.key == Key.DirectionUp) {
                                    relocatingKey = event.key
                                    if (seekable) timelineFocus.requestFocus()
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
        if (channelsAvailable) {
            Row(Modifier.fillMaxWidth().height(playerChannelsCueHeight).testTag("player-channels-cue"),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nav_channels), color = androidx.tv.material3.MaterialTheme.colorScheme.onSurface,
                    style = androidx.tv.material3.MaterialTheme.typography.labelLarge)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = androidx.tv.material3.MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
