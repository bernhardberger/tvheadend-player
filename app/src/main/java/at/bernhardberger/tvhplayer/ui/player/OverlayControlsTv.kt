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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.ImageLoader
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import at.bernhardberger.tvhplayer.core.TimeshiftState
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatHms
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
    onStopPlayback: () -> Unit,
    onUserInteraction: () -> Unit,
    aspectRatio: AspectRatioMode,
    onAspectRatioChange: () -> Unit,
    timeshiftState: TimeshiftState,
    timeshiftFeedback: String?,
    onToggleTimeshiftPause: () -> Unit,
    onSeekTimeshift: (Long) -> Unit,
    onGoLive: () -> Unit,
) {
    var showAudio by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var lastFocused by rememberSaveable { mutableIntStateOf(0) }

    val stopFocus = remember { FocusRequester() }
    val aspectFocus = remember { FocusRequester() }
    val audioFocus = remember { FocusRequester() }
    val subtitleFocus = remember { FocusRequester() }
    val pauseFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val forwardFocus = remember { FocusRequester() }
    val liveFocus = remember { FocusRequester() }
    val baseFocusRequesters = remember {
        listOf(stopFocus, aspectFocus, audioFocus, subtitleFocus)
    }

    LaunchedEffect(controlsVisible, timeshiftState.available) {
        if (controlsVisible) {
            val requesters = if (timeshiftState.available) {
                baseFocusRequesters + listOf(pauseFocus, backFocus, forwardFocus, liveFocus)
            } else {
                baseFocusRequesters
            }
            requesters.getOrElse(lastFocused) { stopFocus }.requestFocus()
        }
    }

    val progress = remember(nowEvent, nowSec) { nowEvent?.progress(nowSec) ?: 0f }
    val title = remember(nowEvent) { nowEvent?.title.orEmpty() }
    val summary = remember(nowEvent) { nowEvent?.summary?.trim().orEmpty() }
    val timeRange = remember(nowEvent) { nowEvent?.timeRangeText().orEmpty() }
    val clock = remember(nowSec) { formatClock(nowSec) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(bottomGradient)
                .padding(start = 56.dp, end = 56.dp, top = 120.dp, bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PiconBox(
                    imageLoader = imageLoader,
                    piconPath = piconPath,
                    modifier = Modifier
                        .width(80.dp)
                        .height(56.dp)
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
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = clock,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            if (summary.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = summary,
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.72f),
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = timeRange,
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                if (nextEvent != null) {
                    Text(
                        text = stringResource(R.string.player_next_event, nextEvent.title),
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.24f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            )

            if (timeshiftState.available) {
                Spacer(Modifier.height(14.dp))
                val bufferDuration = (
                    timeshiftState.liveEdgeMs - timeshiftState.bufferStartMs
                ).coerceAtLeast(1L)
                val bufferProgress = (
                    timeshiftState.positionMs - timeshiftState.bufferStartMs
                ).toFloat() / bufferDuration.toFloat()
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(
                            R.string.timeshift_buffer_start,
                            formatHms((-timeshiftState.bufferStartMs) / 1_000L),
                        ),
                        color = Color.White.copy(alpha = 0.82f),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (timeshiftState.positionMs >= -1_000L) {
                            stringResource(R.string.timeshift_live)
                        } else {
                            stringResource(
                                R.string.timeshift_behind_live,
                                formatHms((-timeshiftState.positionMs) / 1_000L),
                            )
                        },
                        color = Color.White,
                    )
                }
                LinearProgressIndicator(
                    progress = { bufferProgress.coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.24f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                )
            }
            timeshiftFeedback?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(18.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(
                    icon = {
                        Icon(Icons.Filled.Stop, stringResource(R.string.stop_playback))
                    },
                    onClick = { onUserInteraction(); onStopPlayback() },
                    focusRequester = stopFocus,
                    onFocused = { lastFocused = 0 },
                )
                RoundIconButton(
                    icon = { AspectRatioIcon(aspectRatio) },
                    onClick = { onUserInteraction(); onAspectRatioChange() },
                    focusRequester = aspectFocus,
                    onFocused = { lastFocused = 1 },
                )
                RoundIconButton(
                    icon = {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.audio_track))
                    },
                    onClick = { onUserInteraction(); showAudio = true },
                    focusRequester = audioFocus,
                    onFocused = { lastFocused = 2 },
                )
                RoundIconButton(
                    icon = {
                        Icon(Icons.Filled.Subtitles, stringResource(R.string.subtitles))
                    },
                    onClick = { onUserInteraction(); showSubs = true },
                    focusRequester = subtitleFocus,
                    onFocused = { lastFocused = 3 },
                )
                if (timeshiftState.available) {
                    Button(
                        onClick = { onUserInteraction(); onToggleTimeshiftPause() },
                        modifier = Modifier
                            .focusRequester(pauseFocus)
                            .onFocusChanged { if (it.isFocused) lastFocused = 4 },
                    ) {
                        Text(
                            stringResource(
                                if (timeshiftState.paused) R.string.play else R.string.pause
                            )
                        )
                    }
                    Button(
                        onClick = {
                            onUserInteraction()
                            onSeekTimeshift(-at.bernhardberger.tvhplayer.core.TIMESHIFT_SEEK_STEP_MS)
                        },
                        modifier = Modifier
                            .focusRequester(backFocus)
                            .onFocusChanged { if (it.isFocused) lastFocused = 5 },
                    ) {
                        Text(stringResource(R.string.seek_back_30))
                    }
                    Button(
                        onClick = {
                            onUserInteraction()
                            onSeekTimeshift(at.bernhardberger.tvhplayer.core.TIMESHIFT_SEEK_STEP_MS)
                        },
                        modifier = Modifier
                            .focusRequester(forwardFocus)
                            .onFocusChanged { if (it.isFocused) lastFocused = 6 },
                    ) {
                        Text(stringResource(R.string.seek_forward_30))
                    }
                    Button(
                        onClick = { onUserInteraction(); onGoLive() },
                        modifier = Modifier
                            .focusRequester(liveFocus)
                            .onFocusChanged { if (it.isFocused) lastFocused = 7 },
                    ) {
                        Text(stringResource(R.string.timeshift_go_live))
                    }
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

private fun EpgEventEntry.timeRangeText(): String =
    "${formatClock(start)}-${formatClock(stop)}"
