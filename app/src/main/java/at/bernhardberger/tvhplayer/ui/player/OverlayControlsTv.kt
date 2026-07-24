package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.Icon
import androidx.tv.material3.Button
import androidx.tv.material3.IconButton
import androidx.tv.material3.LocalContentColor
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
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.RoundIconButton

@Composable
fun OverlayControlsTv(
    player: Player,
    imageLoader: ImageLoader,
    channelNumber: Int?,
    channelName: String,
    piconPath: String?,
    nowEvent: EpgEventEntry?,
    nextEvent: EpgEventEntry?,
    nowSec: Long,
    controlsVisible: Boolean,
    onOpenChannels: () -> Unit,
    onStopPlayback: () -> Unit,
    onUserInteraction: () -> Unit,
    aspectRatio: AspectRatioMode,
    onAspectRatioChange: () -> Unit,
    timeshiftState: TimeshiftState,
    timeshiftFeedback: String?,
    onToggleTimeshiftPause: () -> Unit,
    onSeekTimeshift: (Long) -> Unit,
    onGoLive: () -> Unit,
    showStop: Boolean = true,
    showUnlock: Boolean = false,
    onUnlock: () -> Unit = {},
) {
    var showAudio by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var lastFocused by rememberSaveable { mutableStateOf<String?>(null) }

    val channelsFocus = remember { FocusRequester() }
    val stopFocus = remember { FocusRequester() }
    val aspectFocus = remember { FocusRequester() }
    val audioFocus = remember { FocusRequester() }
    val subtitleFocus = remember { FocusRequester() }
    val pauseFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val forwardFocus = remember { FocusRequester() }
    val liveFocus = remember { FocusRequester() }
    val unlockFocus = remember { FocusRequester() }
    val canSeekBack = canSeekTimeshiftBackward(timeshiftState)
    val canSeekForward = canSeekTimeshiftForward(timeshiftState)

    LaunchedEffect(
        controlsVisible,
        timeshiftState.available,
        canSeekBack,
        canSeekForward,
        showStop,
        showUnlock,
    ) {
        if (controlsVisible) {
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
                put("aspect", aspectFocus)
                put("audio", audioFocus)
                put("subtitles", subtitleFocus)
                if (showUnlock) put("unlock", unlockFocus)
                if (showStop) put("stop", stopFocus)
            }
            val initialKey = when (initialPlaybackOverlayFocus(timeshiftState.available)) {
                PlaybackOverlayFocusTarget.TIMESHIFT_TOGGLE -> "pause"
                PlaybackOverlayFocusTarget.CHANNELS -> "channels"
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
                PiconBox(
                    imageLoader = imageLoader,
                    piconPath = piconPath,
                    modifier = Modifier
                        .width(72.dp)
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color.White.copy(alpha = 0.10f))
                        .padding(6.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = listOfNotNull(channelNumber?.toString(), channelName)
                            .joinToString("  "),
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = title.ifEmpty { channelName },
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
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
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = nowEvent?.let { formatClock(it.start) }.orEmpty(),
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = nowEvent?.let { formatClock(it.stop) }.orEmpty(),
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.24f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            )
            timeshiftFeedback?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onUserInteraction(); onOpenChannels() },
                        modifier = Modifier
                            .size(52.dp)
                            .focusRequester(channelsFocus)
                            .onFocusChanged { if (it.isFocused) focused("channels") },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            stringResource(R.string.nav_channels),
                        )
                    }
                    if (showStop) {
                        IconButton(
                            onClick = { onUserInteraction(); onStopPlayback() },
                            modifier = Modifier
                                .size(52.dp)
                                .focusRequester(stopFocus)
                                .onFocusChanged { if (it.isFocused) focused("stop") },
                        ) {
                            Icon(Icons.Filled.Stop, stringResource(R.string.stop_playback))
                        }
                    }
                    if (showUnlock) {
                        OutlinedButton(
                            onClick = { onUserInteraction(); onUnlock() },
                            modifier = Modifier
                                .focusRequester(unlockFocus)
                                .onFocusChanged { if (it.isFocused) focused("unlock") },
                        ) {
                            Text(stringResource(R.string.simple_tv_unlock))
                        }
                    }
                }

                if (timeshiftState.available) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (canSeekBack) {
                            IconButton(
                                onClick = {
                                    onUserInteraction()
                                    onSeekTimeshift(-at.bernhardberger.tvhplayer.core.TIMESHIFT_SEEK_STEP_MS)
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .focusRequester(backFocus)
                                    .onFocusChanged { if (it.isFocused) focused("back") },
                            ) {
                                Icon(Icons.Filled.Replay30, stringResource(R.string.seek_back_30))
                            }
                        }
                        IconButton(
                            onClick = { onUserInteraction(); onToggleTimeshiftPause() },
                            modifier = Modifier
                                .size(64.dp)
                                .focusRequester(pauseFocus)
                                .onFocusChanged { if (it.isFocused) focused("pause") },
                        ) {
                            Icon(
                                if (timeshiftState.paused) {
                                    Icons.Filled.PlayArrow
                                } else {
                                    Icons.Filled.Pause
                                },
                                stringResource(
                                    if (timeshiftState.paused) R.string.play else R.string.pause
                                ),
                            )
                        }
                        if (canSeekForward) {
                            IconButton(
                                onClick = {
                                    onUserInteraction()
                                    onSeekTimeshift(at.bernhardberger.tvhplayer.core.TIMESHIFT_SEEK_STEP_MS)
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .focusRequester(forwardFocus)
                                    .onFocusChanged { if (it.isFocused) focused("forward") },
                            ) {
                                Icon(
                                    Icons.Filled.Forward30,
                                    stringResource(R.string.seek_forward_30),
                                )
                            }
                            OutlinedButton(
                                onClick = { onUserInteraction(); onGoLive() },
                                modifier = Modifier
                                    .focusRequester(liveFocus)
                                    .onFocusChanged { if (it.isFocused) focused("live") },
                            ) {
                                Text(stringResource(R.string.timeshift_go_live))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundIconButton(
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                stringResource(R.string.audio_track),
                            )
                        },
                        onClick = { onUserInteraction(); showAudio = true },
                        focusRequester = audioFocus,
                        onFocused = { focused("audio") },
                    )
                    RoundIconButton(
                        icon = {
                            Icon(Icons.Filled.Subtitles, stringResource(R.string.subtitles))
                        },
                        onClick = { onUserInteraction(); showSubs = true },
                        focusRequester = subtitleFocus,
                        onFocused = { focused("subtitles") },
                    )
                    RoundIconButton(
                        icon = { AspectRatioIcon(aspectRatio) },
                        onClick = { onUserInteraction(); onAspectRatioChange() },
                        focusRequester = aspectFocus,
                        onFocused = { focused("aspect") },
                    )
                }
            }
        }
    }

    if (showAudio) AudioTrackDialog(player = player, onDismiss = { showAudio = false })
    if (showSubs) SubtitleTrackDialog(player = player, onDismiss = { showSubs = false })
}

@Composable
private fun AspectRatioIcon(aspectRatio: AspectRatioMode) {
    val color = LocalContentColor.current
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(28.dp)) {
            val strokeWidth = 2.5f
            val cap = StrokeCap.Round
            val inset = size.width * 0.28f
            drawLine(color, Offset(0f, inset), Offset(0f, 0f), strokeWidth, cap)
            drawLine(color, Offset(0f, 0f), Offset(inset, 0f), strokeWidth, cap)
            drawLine(color, Offset(size.width - inset, 0f), Offset(size.width, 0f), strokeWidth, cap)
            drawLine(color, Offset(size.width, 0f), Offset(size.width, inset), strokeWidth, cap)
            drawLine(color, Offset(0f, size.height - inset), Offset(0f, size.height), strokeWidth, cap)
            drawLine(color, Offset(0f, size.height), Offset(inset, size.height), strokeWidth, cap)
            drawLine(color, Offset(size.width - inset, size.height), Offset(size.width, size.height), strokeWidth, cap)
            drawLine(color, Offset(size.width, size.height - inset), Offset(size.width, size.height), strokeWidth, cap)
        }
        Text(
            text = when (aspectRatio) {
                AspectRatioMode.FIT -> "AUTO"
                AspectRatioMode.FORCE_16_9 -> "16:9"
                AspectRatioMode.FORCE_4_3 -> "4:3"
            },
            color = color,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
