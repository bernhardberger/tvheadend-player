package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.progress
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
) {
    val infoFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val recordFocus = remember { FocusRequester() }
    val timelineFocus = remember { FocusRequester() }
    val seekable = timeshiftState.available && timeshiftState.timingKnown
    val programmeTimeKnown = !timeshiftState.available
    var focusInitialized by remember { mutableStateOf(false) }
    var previousSeekable by remember { mutableStateOf(seekable) }
    var lastFocusWasTimeline by remember { mutableStateOf(false) }
    var relocatingKey by remember { mutableStateOf<Key?>(null) }
    LaunchedEffect(controlsVisible, optionsOpen, restoreInfoFocus, restoreRecordActionFocus, restoreOptionsFocus, seekable) {
        if (controlsVisible && !optionsOpen) {
            val target = when {
                restoreInfoFocus -> infoFocus
                restoreRecordActionFocus -> recordFocus
                restoreOptionsFocus -> settingsFocus
                !focusInitialized -> if (seekable) timelineFocus else infoFocus
                previousSeekable && !seekable && lastFocusWasTimeline -> infoFocus
                else -> null
            }
            previousSeekable = seekable
            if (target != null) androidx.compose.runtime.withFrameNanos { }
            if (target?.requestFocus() == true) {
                focusInitialized = true
                if (restoreInfoFocus) onInfoFocusRestored()
                if (restoreRecordActionFocus) onRecordActionFocusRestored()
                if (restoreOptionsFocus) onOptionsFocusRestored()
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
            title = nowEvent?.takeIf { programmeTimeKnown }?.title.orEmpty().ifEmpty { channelName },
            support = if (!programmeTimeKnown) stringResource(R.string.player_programme_timing_unavailable) else nextEvent?.let {
                stringResource(R.string.player_next_event_with_range,
                    "${formatClock(it.start.epochSeconds)} - ${formatClock(it.stop.epochSeconds)}", it.title.orEmpty()) +
                    if (nextScheduled) " / " + stringResource(R.string.recording_state_scheduled) else ""
            },
            clock = formatClock(nowSec), clockSupport = null,
            programmeStart = nowEvent?.takeIf { programmeTimeKnown }?.let { formatClock(it.start.epochSeconds) },
            programmeEnd = nowEvent?.takeIf { programmeTimeKnown }?.let { formatClock(it.stop.epochSeconds) },
            programmeProgress = nowEvent?.takeUnless { timeshiftState.available }?.progress(nowSec),
            tags = PlayerHeaderTags(picon = "player-picon", eyebrow = "player-channel-identity",
                title = "player-programme-title", support = "player-next-programme", clock = "player-clock"),
            modifier = modifier,
        )
    }) {
        PlayerActionRow(
            infoFocus = infoFocus, settingsFocus = settingsFocus,
            onInfo = onOpenInfo, onSettings = onOpenOptions, onRecord = onOpenRecord,
            recordFocus = recordFocus,
            onStop = onStopPlayback, onInteraction = { lastFocusWasTimeline = false; onUserInteraction() },
            atLive = when {
                !liveAvailable -> null
                !timeshiftState.available -> true
                timeshiftState.timingKnown -> timeshiftPositionPresentation(timeshiftState).atLiveEdge
                else -> null
            },
            onGoLive = onGoLive,
            modifier = Modifier.testTag("player-actions").focusProperties {
                if (seekable) down = timelineFocus
            }.onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionDown) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        relocatingKey = event.key
                        if (seekable) timelineFocus.requestFocus() else onOpenChannels()
                    }
                    true
                } else false
            },
        )
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.Column(
            Modifier.height(100.dp),
        ) {
        if (timeshiftState.available) {
            PlaybackSeekbar(
                range = timeshiftSeekbarRange(timeshiftState),
                paused = paused,
                onSeekTo = { onUserInteraction(); onSeekTimeshift(it - timeshiftState.positionMs) },
                modifier = Modifier.testTag("player-seekbar").focusRequester(timelineFocus)
                    .onFocusChanged { if (it.isFocused) lastFocusWasTimeline = true }
                    .focusProperties { up = infoFocus }
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                    onUserInteraction(); onToggleTimeshiftPause()
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                    onCommitSeek(); onOpenChannels()
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                    onCommitSeek()
                                    relocatingKey = event.key
                                    infoFocus.requestFocus()
                                }
                                true
                            }
                            else -> false
                        }
                    },
            )
        }
        timeshiftFeedback?.let { Text(it, color = androidx.tv.material3.MaterialTheme.colorScheme.onSurface) }
        }
    }
}
