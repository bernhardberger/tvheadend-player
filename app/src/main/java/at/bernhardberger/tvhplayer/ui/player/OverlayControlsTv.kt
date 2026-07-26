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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.components.RoundIconButton

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
    val eventTimeRange = remember(nowEvent) {
        nowEvent?.let { "${formatClock(it.start)}-${formatClock(it.stop)}" }.orEmpty()
    }
    val clock = remember(nowSec) { formatClock(nowSec) }

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
    val focusedLabel = when (lastFocused) {
        "channels" -> channelsLabel
        "back" -> seekBackLabel
        "forward" -> seekForwardLabel
        "pause" -> if (timeshiftState.paused) playLabel else pauseLabel
        "live" -> goLiveLabel
        "info" -> infoLabel
        "options" -> moreLabel
        "stop" -> stopLabel
        else -> null
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(bottomGradient)
                .padding(start = 56.dp, end = 56.dp, top = 96.dp, bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                // Programme title is primary; channel identity secondary; times/up-next tertiary.
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title.ifEmpty { channelName },
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(channelNumber?.toString(), channelName)
                            .joinToString("  "),
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (eventTimeRange.isNotEmpty()) {
                            Text(
                                text = eventTimeRange,
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        if (nextEvent != null) {
                            Text(
                                text = stringResource(R.string.player_next_event, nextEvent.title),
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Text(
                    text = clock,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.height(14.dp))
            if (timeshiftState.available) {
                PlaybackSeekbar(
                    range = timeshiftSeekbarRange(timeshiftState),
                    onSeekTo = { target ->
                        onUserInteraction()
                        onSeekTimeshift(target - timeshiftState.positionMs)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (nowEvent != null) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = formatClock(nowEvent.start),
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatClock(nowEvent.stop),
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                // Non-seekable programme progress: informational only.
                ProgrammeProgressBar(progress = progress)
            }
            timeshiftFeedback?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(18.dp))
            // Single right-aligned control cluster so Right always moves to an adjacent control.
            // Focused control label is anchored above the cluster (JetStream-style).
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                focusedLabel?.let { label ->
                    Text(
                        text = label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(bottom = 8.dp, end = 8.dp)
                            .background(
                                Color.Black.copy(alpha = 0.55f),
                                shape = MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .widthIn(max = 220.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onUserInteraction(); onOpenChannels() },
                        modifier = Modifier
                            .size(52.dp)
                            .focusRequester(channelsFocus)
                            .onFocusChanged { if (it.isFocused) focused("channels") },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, channelsLabel)
                    }
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
                                    .size(52.dp)
                                    .focusRequester(backFocus)
                                    .onFocusChanged { if (it.isFocused) focused("back") },
                            ) {
                                Icon(Icons.Filled.Replay30, seekBackLabel)
                            }
                        }
                        IconButton(
                            onClick = { onUserInteraction(); onToggleTimeshiftPause() },
                            modifier = Modifier
                                .size(56.dp)
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
                                    .size(52.dp)
                                    .focusRequester(forwardFocus)
                                    .onFocusChanged { if (it.isFocused) focused("forward") },
                            ) {
                                Icon(Icons.Filled.Forward30, seekForwardLabel)
                            }
                            OutlinedButton(
                                onClick = { onUserInteraction(); onGoLive() },
                                modifier = Modifier
                                    .focusRequester(liveFocus)
                                    .onFocusChanged { if (it.isFocused) focused("live") },
                            ) {
                                Text(goLiveLabel)
                            }
                        }
                    }
                    IconButton(
                        onClick = { onUserInteraction(); onOpenInfo() },
                        modifier = Modifier
                            .size(52.dp)
                            .focusRequester(infoFocus)
                            .onFocusChanged { if (it.isFocused) focused("info") },
                    ) {
                        Icon(Icons.Filled.Info, infoLabel)
                    }
                    RoundIconButton(
                        icon = {
                            Icon(Icons.Filled.MoreVert, moreLabel)
                        },
                        onClick = { onUserInteraction(); onOpenOptions() },
                        focusRequester = optionsFocus,
                        onFocused = { focused("options") },
                    )
                    if (showStop) {
                        IconButton(
                            onClick = { onUserInteraction(); onStopPlayback() },
                            modifier = Modifier
                                .size(52.dp)
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
