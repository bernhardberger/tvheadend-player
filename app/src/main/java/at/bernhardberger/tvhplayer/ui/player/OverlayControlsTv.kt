package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.ImageLoader
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import at.bernhardberger.tvhplayer.core.TimeshiftState
import at.bernhardberger.tvhplayer.core.PlaybackOverlayFocusTarget
import at.bernhardberger.tvhplayer.core.canSeekTimeshiftBackward
import at.bernhardberger.tvhplayer.core.canSeekTimeshiftForward
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.initialPlaybackOverlayFocus
import at.bernhardberger.tvhplayer.core.programmeAnchoredAxis
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.ui.TvOverlayActionButtonSize
import at.bernhardberger.tvhplayer.ui.TvOverlayActionGap
import at.bernhardberger.tvhplayer.ui.TvOverlayBottomPadding
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineBlockGap
import at.bernhardberger.tvhplayer.ui.TvOverlayTopPadding
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.components.channelTitleText

@Composable
fun OverlayControlsTv(
    imageLoader: ImageLoader,
    channelNumber: Int?,
    channelName: String,
    piconPath: String?,
    nowEvent: EpgEventEntry?,
    nextEvent: EpgEventEntry?,
    nowSec: Long,
    controlsVisible: Boolean,
    optionsOpen: Boolean,
    onOpenChannels: () -> Unit,
    onOpenInfo: () -> Unit = {},
    onStopPlayback: () -> Unit,
    onUserInteraction: () -> Unit,
    onOpenOptions: () -> Unit,
    timeshiftState: TimeshiftState,
    timeshiftFeedback: String?,
    onToggleTimeshiftPause: () -> Unit,
    onSeekTimeshift: (Long) -> Unit,
    onGoLive: () -> Unit,
    showStop: Boolean = true,
    restoreInfoFocus: Boolean = false,
    onInfoFocusRestored: () -> Unit = {},
    restoreOptionsFocus: Boolean = false,
    onOptionsFocusRestored: () -> Unit = {},
) {
    var lastFocused by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedAction by remember { mutableStateOf<String?>(null) }

    val channelsFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }
    val stopFocus = remember { FocusRequester() }
    val optionsFocus = remember { FocusRequester() }
    val pauseFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val forwardFocus = remember { FocusRequester() }
    val liveFocus = remember { FocusRequester() }
    val seekbarFocus = remember { FocusRequester() }
    val canSeekBack = canSeekTimeshiftBackward(timeshiftState)
    val canSeekForward = canSeekTimeshiftForward(timeshiftState)

    LaunchedEffect(
        controlsVisible,
        timeshiftState.available,
        canSeekBack,
        canSeekForward,
        showStop,
        optionsOpen,
        restoreInfoFocus,
        restoreOptionsFocus,
    ) {
        if (controlsVisible && !optionsOpen) {
            val requesters = buildMap {
                put("channels", channelsFocus)
                if (timeshiftState.available) {
                    put("pause", pauseFocus)
                    if (canSeekBack) put("back", backFocus)
                    if (canSeekForward) {
                        put("forward", forwardFocus)
                        put("live", liveFocus)
                    }
                }
                put("info", infoFocus)
                put("options", optionsFocus)
                if (showStop) put("stop", stopFocus)
            }
            val initialKey = when (initialPlaybackOverlayFocus(timeshiftState.available)) {
                PlaybackOverlayFocusTarget.TIMESHIFT_TOGGLE -> "pause"
                PlaybackOverlayFocusTarget.CHANNELS -> "channels"
                PlaybackOverlayFocusTarget.CONTROLS_CLUSTER ->
                    if (timeshiftState.available) "pause" else "channels"
            }
            val target = when {
                restoreInfoFocus -> infoFocus
                restoreOptionsFocus -> optionsFocus
                else -> lastFocused?.let(requesters::get)
                    ?: requesters[initialKey]
                    ?: requesters.values.first()
            }
            if (target.requestFocus()) {
                if (restoreInfoFocus) onInfoFocusRestored()
                if (restoreOptionsFocus) onOptionsFocusRestored()
            }
        }
    }

    val playbackSec = nowSec + timeshiftState.positionMs / 1_000L
    val progress = remember(nowEvent, playbackSec) {
        nowEvent?.progress(playbackSec) ?: 0f
    }
    val title = remember(nowEvent) { nowEvent?.title.orEmpty() }
    val clock = remember(nowSec) { formatClock(nowSec) }
    val programmeAxis = programmeAnchoredAxis(
        state = timeshiftState,
        nowEpochSec = nowSec,
        programmeStartSec = nowEvent?.start,
        programmeStopSec = nowEvent?.stop,
    )
    val programmeDurationMs = nowEvent?.let { (it.stop - it.start).coerceAtLeast(0L) * 1_000L }
    val programmePositionMs = nowEvent?.let {
        ((playbackSec - it.start) * 1_000L).coerceIn(0L, programmeDurationMs ?: 0L)
    }
    fun focusChanged(key: String, isFocused: Boolean) {
        if (isFocused) {
            lastFocused = key
            focusedAction = key
            onUserInteraction()
        } else if (focusedAction == key) {
            focusedAction = null
        }
    }

    val channelsLabel = stringResource(R.string.nav_channels)
    val seekBackLabel = stringResource(R.string.seek_back_30)
    val seekForwardLabel = stringResource(R.string.seek_forward_30)
    val playLabel = stringResource(R.string.play)
    val pauseLabel = stringResource(R.string.pause)
    val goLiveLabel = stringResource(R.string.timeshift_go_live)
    val infoLabel = stringResource(R.string.player_info)
    val moreLabel = stringResource(R.string.playback_options)
    val stopLabel = stringResource(R.string.stop_playback)
    val contextLabel = if (controlsVisible && !optionsOpen) {
        when (focusedAction) {
            "channels" -> channelsLabel
            "live" -> goLiveLabel
            "options" -> moreLabel
            else -> null
        }
    } else {
        null
    }
    Box(Modifier.fillMaxSize()) {
        PlayerIdentityHeader(
            imageLoader = imageLoader,
            piconPath = piconPath,
            eyebrow = channelTitleText(number = channelNumber, name = channelName),
            title = title.ifEmpty { channelName },
            support = nextEvent?.let {
                stringResource(R.string.player_next_event_at, formatClock(it.start), it.title)
            },
            clock = clock,
            clockSupport = nowEvent?.let {
                stringResource(R.string.player_ends_in, formatClock(it.stop))
            },
            tags = PlayerHeaderTags(
                picon = "player-picon",
                eyebrow = "player-channel-identity",
                title = "player-programme-title",
                support = "player-next-programme",
                clock = "player-clock",
                clockSupport = "player-programme-end",
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(topGradient)
                .padding(
                    start = TvOverlaySidePadding,
                    end = TvOverlaySidePadding,
                    top = TvOverlayTopPadding,
                    bottom = TvOverlayHeaderGradientRunout,
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(bottomGradient)
                .padding(
                    start = TvOverlaySidePadding,
                    end = TvOverlaySidePadding,
                    top = TvOverlayFooterGradientRunout,
                    bottom = TvOverlayBottomPadding,
                ),
        ) {
            if (timeshiftState.available || nowEvent != null) {
                Column(Modifier.testTag("player-timeline")) {
                    if (timeshiftState.available) {
                    PlaybackSeekbar(
                        range = timeshiftSeekbarRange(timeshiftState),
                        onSeekTo = { target ->
                            onUserInteraction()
                            onSeekTimeshift(target - timeshiftState.positionMs)
                        },
                        programmeAxis = programmeAxis,
                        programmePositionMs = programmePositionMs,
                        programmeDurationMs = programmeDurationMs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player-seekbar")
                            .focusRequester(seekbarFocus)
                            .focusProperties {
                                down = pauseFocus
                            },
                    )
                    } else {
                        PlayerTimelineBlock(
                            progress = progress,
                            tone = PlayerTimelineTone.AMBIENT,
                            leadingLabel = formatPlaybackDuration(programmePositionMs ?: 0L),
                            trailingLabel = formatPlaybackDuration(programmeDurationMs ?: 0L),
                            leadingLabelTestTag = "player-programme-progress",
                        )
                    }
                }
            }
            timeshiftFeedback?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(TvOverlayTimelineBlockGap))
            PlayerActionRow(
                contextLabel = contextLabel,
                modifier = Modifier
                    .testTag("player-actions")
                    .onPreviewKeyEvent { event ->
                        if (
                            timeshiftState.available &&
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionUp
                        ) {
                            seekbarFocus.requestFocus()
                            true
                        } else {
                            false
                        }
                    },
                navigation = {
                    Row(
                        modifier = Modifier.testTag("player-navigation-actions"),
                    ) {
                        IconButton(
                            onClick = { onUserInteraction(); onOpenChannels() },
                            modifier = Modifier
                                .size(TvOverlayActionButtonSize)
                                .testTag("player-channels")
                                .focusProperties {
                                    if (timeshiftState.available) {
                                        up = seekbarFocus
                                        right = if (canSeekBack) backFocus else pauseFocus
                                    } else {
                                        right = infoFocus
                                    }
                                }
                                .focusRequester(channelsFocus)
                                .onFocusChanged { focusChanged("channels", it.isFocused) },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, channelsLabel)
                        }
                    }
                },
                transport = if (timeshiftState.available) {
                    {
                        Row(
                            modifier = Modifier.testTag("player-transport-actions"),
                            horizontalArrangement = Arrangement.spacedBy(TvOverlayActionGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (canSeekBack) {
                                IconButton(
                                onClick = {
                                    onUserInteraction()
                                    onSeekTimeshift(
                                        -at.bernhardberger.tvhplayer.core.TIMESHIFT_SEEK_STEP_MS,
                                    )
                                },
                                modifier = Modifier
                                    .size(TvOverlayActionButtonSize)
                                    .focusProperties {
                                        up = seekbarFocus
                                        left = channelsFocus
                                        right = pauseFocus
                                    }
                                    .focusRequester(backFocus)
                                    .onFocusChanged { focusChanged("back", it.isFocused) },
                                ) {
                                    Icon(Icons.Filled.Replay30, seekBackLabel)
                                }
                            }
                            IconButton(
                                onClick = { onUserInteraction(); onToggleTimeshiftPause() },
                                modifier = Modifier
                                    .size(TvOverlayActionButtonSize)
                                    .focusProperties {
                                        up = seekbarFocus
                                        left = if (canSeekBack) backFocus else channelsFocus
                                        right = if (canSeekForward) forwardFocus else infoFocus
                                    }
                                    .focusRequester(pauseFocus)
                                    .onFocusChanged { focusChanged("pause", it.isFocused) },
                            ) {
                                Icon(
                                    if (timeshiftState.paused) {
                                        Icons.Filled.PlayArrow
                                    } else {
                                        Icons.Filled.Pause
                                    },
                                    if (timeshiftState.paused) playLabel else pauseLabel,
                                )
                            }
                            if (canSeekForward) {
                                IconButton(
                                    onClick = {
                                        onUserInteraction()
                                        onSeekTimeshift(
                                            at.bernhardberger.tvhplayer.core.TIMESHIFT_SEEK_STEP_MS,
                                        )
                                    },
                                    modifier = Modifier
                                        .size(TvOverlayActionButtonSize)
                                        .focusProperties {
                                            up = seekbarFocus
                                            left = pauseFocus
                                            right = liveFocus
                                        }
                                        .focusRequester(forwardFocus)
                                        .onFocusChanged { focusChanged("forward", it.isFocused) },
                                ) {
                                    Icon(Icons.Filled.Forward30, seekForwardLabel)
                                }
                                IconButton(
                                    onClick = {
                                        onUserInteraction()
                                        onGoLive()
                                        seekbarFocus.requestFocus()
                                    },
                                    modifier = Modifier
                                        .size(TvOverlayActionButtonSize)
                                        .testTag("player-go-live")
                                        .focusProperties {
                                            up = seekbarFocus
                                            left = forwardFocus
                                            right = infoFocus
                                        }
                                        .focusRequester(liveFocus)
                                        .onFocusChanged { focusChanged("live", it.isFocused) },
                                ) {
                                    Icon(Icons.Outlined.LiveTv, goLiveLabel)
                                }
                            }
                        }
                    }
                } else {
                    null
                },
                utilities = {
                    Row(
                        modifier = Modifier.testTag("player-utility-actions"),
                        horizontalArrangement = Arrangement.spacedBy(TvOverlayActionGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                            IconButton(
                                onClick = { onUserInteraction(); onOpenInfo() },
                                modifier = Modifier
                                    .size(TvOverlayActionButtonSize)
                                    .testTag("live-info-action")
                                    .focusProperties {
                                        if (timeshiftState.available) up = seekbarFocus
                                        left = when {
                                            canSeekForward -> liveFocus
                                            timeshiftState.available -> pauseFocus
                                            else -> channelsFocus
                                        }
                                        right = optionsFocus
                                    }
                                    .focusRequester(infoFocus)
                                    .onFocusChanged { focusChanged("info", it.isFocused) },
                            ) {
                                Icon(Icons.Filled.Info, infoLabel)
                            }
                            IconButton(
                                onClick = { onUserInteraction(); onOpenOptions() },
                                modifier = Modifier
                                    .size(TvOverlayActionButtonSize)
                                    .testTag("live-playback-options")
                                    .focusProperties {
                                        if (timeshiftState.available) up = seekbarFocus
                                        left = infoFocus
                                        if (showStop) right = stopFocus
                                    }
                                    .focusRequester(optionsFocus)
                                    .onFocusChanged { focusChanged("options", it.isFocused) },
                            ) {
                                Icon(Icons.Filled.MoreVert, moreLabel)
                            }
                    }
                },
                terminal = if (showStop) {
                    {
                        Row(modifier = Modifier.testTag("player-terminal-actions")) {
                                IconButton(
                                    onClick = { onUserInteraction(); onStopPlayback() },
                                    modifier = Modifier
                                        .size(TvOverlayActionButtonSize)
                                        .focusProperties {
                                            if (timeshiftState.available) up = seekbarFocus
                                            left = optionsFocus
                                        }
                                        .focusRequester(stopFocus)
                                        .onFocusChanged { focusChanged("stop", it.isFocused) },
                                ) {
                                    Icon(Icons.Filled.Stop, stopLabel)
                                }
                        }
                    }
                } else {
                    null
                },
            )
        }
    }
}
