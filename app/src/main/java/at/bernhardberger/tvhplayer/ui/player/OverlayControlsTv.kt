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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.ImageLoader
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import at.bernhardberger.tvhplayer.core.TimeshiftState
import at.bernhardberger.tvhplayer.core.PlaybackOverlayFocusTarget
import at.bernhardberger.tvhplayer.core.canSeekTimeshiftBackward
import at.bernhardberger.tvhplayer.core.canSeekTimeshiftForward
import at.bernhardberger.tvhplayer.core.initialPlaybackOverlayFocus
import at.bernhardberger.tvhplayer.core.shouldShowProgrammeTimeline
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.core.timeshiftEpgBoundaryFractions
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.components.PiconBox

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
) {
    var lastFocused by rememberSaveable { mutableStateOf<String?>(null) }

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
            (lastFocused?.let(requesters::get)
                ?: requesters[initialKey]
                ?: requesters.values.first()).requestFocus()
        }
    }

    val playbackSec = nowSec + timeshiftState.positionMs / 1_000L
    val progress = remember(nowEvent, playbackSec) {
        nowEvent?.progress(playbackSec) ?: 0f
    }
    val title = remember(nowEvent) { nowEvent?.title.orEmpty() }
    val clock = remember(nowSec) { formatClock(nowSec) }
    val showProgrammeTimeline = shouldShowProgrammeTimeline(
        state = timeshiftState,
        hasCurrentProgramme = nowEvent != null,
    )
    val programmeDurationMs = nowEvent?.let { (it.stop - it.start).coerceAtLeast(0L) * 1_000L }
    val programmePositionMs = nowEvent?.let {
        ((playbackSec - it.start) * 1_000L).coerceIn(0L, programmeDurationMs ?: 0L)
    }
    val epgBoundaryFractions = remember(timeshiftState, nowEvent, nextEvent, nowSec) {
        timeshiftEpgBoundaryFractions(
            state = timeshiftState,
            nowEpochSec = nowSec,
            boundaryEpochSec = listOfNotNull(
                nowEvent?.start,
                nowEvent?.stop,
                nextEvent?.stop,
            ),
        )
    }

    fun focused(key: String) {
        lastFocused = key
        onUserInteraction()
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
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(topGradient)
                .padding(start = 56.dp, end = 56.dp, top = 32.dp, bottom = 72.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PiconBox(
                    imageLoader = imageLoader,
                    piconPath = piconPath,
                    modifier = Modifier
                        .width(160.dp)
                        .height(90.dp)
                        .testTag("player-picon"),
                )
                Spacer(Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = listOfNotNull(channelNumber?.toString(), channelName)
                            .joinToString("  "),
                        color = Color.White.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("player-channel-identity"),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = title.ifEmpty { channelName },
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("player-programme-title"),
                    )
                    if (nextEvent != null) {
                        Text(
                            text = stringResource(
                                R.string.player_next_event_at,
                                formatClock(nextEvent.start),
                                nextEvent.title,
                            ),
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("player-next-programme"),
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = clock,
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.testTag("player-clock"),
                )
                nowEvent?.let {
                    Text(
                        text = stringResource(
                            R.string.player_ends_in,
                            formatClock(it.stop),
                        ),
                        color = Color.White.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("player-programme-end"),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(bottomGradient)
                .padding(start = 56.dp, end = 56.dp, top = 80.dp, bottom = 28.dp),
        ) {
            if (timeshiftState.available) {
                Column(Modifier.testTag("player-timeline")) {
                    if (canSeekForward) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onUserInteraction()
                                    onGoLive()
                                    seekbarFocus.requestFocus()
                                },
                                modifier = Modifier
                                    .testTag("player-go-live")
                                    .focusProperties { down = seekbarFocus }
                                    .focusRequester(liveFocus)
                                    .onFocusChanged { if (it.isFocused) focused("live") },
                            ) {
                                Text(goLiveLabel, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                    PlaybackSeekbar(
                        range = timeshiftSeekbarRange(timeshiftState),
                        onSeekTo = { target ->
                            onUserInteraction()
                            onSeekTimeshift(target - timeshiftState.positionMs)
                        },
                        epgBoundaryFractions = epgBoundaryFractions,
                        programmePositionMs = programmePositionMs.takeIf {
                            showProgrammeTimeline
                        },
                        programmeDurationMs = programmeDurationMs.takeIf {
                            showProgrammeTimeline
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player-seekbar")
                            .focusRequester(seekbarFocus)
                            .focusProperties {
                                down = pauseFocus
                                if (canSeekForward) right = liveFocus
                            },
                    )
                }
            } else if (nowEvent != null) {
                Column(Modifier.testTag("player-timeline")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "${formatPlaybackClock(programmePositionMs ?: 0L)} / " +
                                formatPlaybackClock(programmeDurationMs ?: 0L),
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.testTag("player-programme-progress"),
                        )
                    }
                    ProgrammeProgressBar(progress = progress)
                }
            }
            timeshiftFeedback?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.testTag("player-navigation-actions"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { onUserInteraction(); onOpenChannels() },
                            modifier = Modifier
                                .size(48.dp)
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
                                .onFocusChanged { if (it.isFocused) focused("channels") },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, channelsLabel)
                        }
                    }
                }
                Row(
                    modifier = Modifier.testTag("player-transport-actions"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (timeshiftState.available) {
                        if (canSeekBack) {
                            IconButton(
                                onClick = {
                                    onUserInteraction()
                                    onSeekTimeshift(
                                        -at.bernhardberger.tvhplayer.core.TIMESHIFT_SEEK_STEP_MS,
                                    )
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .focusProperties {
                                        up = seekbarFocus
                                        left = channelsFocus
                                        right = pauseFocus
                                    }
                                    .focusRequester(backFocus)
                                    .onFocusChanged { if (it.isFocused) focused("back") },
                            ) {
                                Icon(Icons.Filled.Replay30, seekBackLabel)
                            }
                        }
                        IconButton(
                            onClick = { onUserInteraction(); onToggleTimeshiftPause() },
                            modifier = Modifier
                                .size(48.dp)
                                .focusProperties {
                                    up = seekbarFocus
                                    left = if (canSeekBack) backFocus else channelsFocus
                                    right = if (canSeekForward) forwardFocus else infoFocus
                                }
                                .focusRequester(pauseFocus)
                                .onFocusChanged { if (it.isFocused) focused("pause") },
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
                                    .size(48.dp)
                                    .focusProperties {
                                        up = seekbarFocus
                                        left = pauseFocus
                                        right = infoFocus
                                    }
                                    .focusRequester(forwardFocus)
                                    .onFocusChanged { if (it.isFocused) focused("forward") },
                            ) {
                                Icon(Icons.Filled.Forward30, seekForwardLabel)
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.testTag("player-utility-actions"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { onUserInteraction(); onOpenInfo() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .focusProperties {
                                        if (timeshiftState.available) up = seekbarFocus
                                        left = when {
                                            canSeekForward -> forwardFocus
                                            timeshiftState.available -> pauseFocus
                                            else -> channelsFocus
                                        }
                                        right = optionsFocus
                                    }
                                    .focusRequester(infoFocus)
                                    .onFocusChanged { if (it.isFocused) focused("info") },
                            ) {
                                Icon(Icons.Filled.Info, infoLabel)
                            }
                            IconButton(
                                onClick = { onUserInteraction(); onOpenOptions() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .focusProperties {
                                        if (timeshiftState.available) up = seekbarFocus
                                        left = infoFocus
                                        if (showStop) right = stopFocus
                                    }
                                    .focusRequester(optionsFocus)
                                    .onFocusChanged { if (it.isFocused) focused("options") },
                            ) {
                                Icon(Icons.Filled.MoreVert, moreLabel)
                            }
                        }
                        if (showStop) {
                            Row(
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .testTag("player-terminal-actions"),
                            ) {
                                IconButton(
                                    onClick = { onUserInteraction(); onStopPlayback() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .focusProperties {
                                            if (timeshiftState.available) up = seekbarFocus
                                            left = optionsFocus
                                        }
                                        .focusRequester(stopFocus)
                                        .onFocusChanged { if (it.isFocused) focused("stop") },
                                ) {
                                    Icon(Icons.Filled.Stop, stopLabel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
