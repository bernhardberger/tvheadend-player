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
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.recordingSeekbarRange
import at.bernhardberger.tvhplayer.ui.common.formatHms
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import coil3.ImageLoader

@Composable
internal fun RecordingOverlayControls(
    imageLoader: ImageLoader,
    piconPath: String?,
    title: String,
    subtitle: String?,
    channelName: String?,
    positionMs: Long,
    durationMs: Long,
    nowSec: Long,
    isPlaying: Boolean,
    controlsVisible: Boolean,
    optionsOpen: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onStopPlayback: () -> Unit,
    onUserInteraction: () -> Unit,
    showStop: Boolean,
    onOpenOptions: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    var lastFocused by rememberSaveable { mutableStateOf("playPause") }
    val playPauseFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val forwardFocus = remember { FocusRequester() }
    val stopFocus = remember { FocusRequester() }
    val optionsFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }
    val seekbarFocus = remember { FocusRequester() }
    val focusTargets = mapOf(
        "playPause" to playPauseFocus,
        "back" to backFocus,
        "forward" to forwardFocus,
        "stop" to stopFocus,
        "options" to optionsFocus,
        "info" to infoFocus,
    )

    LaunchedEffect(controlsVisible, showStop, optionsOpen) {
        if (controlsVisible && !optionsOpen) {
            val availableTargets = buildMap {
                put("playPause", playPauseFocus)
                put("back", backFocus)
                put("forward", forwardFocus)
                if (showStop) put("stop", stopFocus)
                put("options", optionsFocus)
                put("info", infoFocus)
            }
            (availableTargets[lastFocused] ?: playPauseFocus).requestFocus()
        }
    }

    fun focused(key: String) {
        if (focusTargets.containsKey(key)) lastFocused = key
        onUserInteraction()
    }

    val knownDuration = durationMs.takeIf { it != C.TIME_UNSET && it > 0L }
    val seekBackLabel = stringResource(R.string.seek_back_30)
    val seekForwardLabel = stringResource(R.string.seek_forward_30)
    val playLabel = stringResource(R.string.play)
    val pauseLabel = stringResource(R.string.pause)
    val moreLabel = stringResource(R.string.playback_options)
    val infoLabel = stringResource(R.string.player_info)
    val stopLabel = stringResource(R.string.stop_playback)
    val clock = remember(nowSec) { at.bernhardberger.tvhplayer.ui.common.formatClock(nowSec) }
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
                    modifier = Modifier.width(160.dp).height(90.dp),
                )
                Spacer(Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    channelName?.takeIf(String::isNotBlank)?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    subtitle?.takeIf(String::isNotBlank)?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Text(
                text = clock,
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(bottomGradient)
                .padding(start = 56.dp, end = 56.dp, top = 80.dp, bottom = 28.dp),
        ) {
            PlaybackSeekbar(
                range = recordingSeekbarRange(
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = knownDuration,
                ),
                onSeekTo = { target ->
                    onUserInteraction()
                    onSeek(target - positionMs)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(seekbarFocus)
                    .focusProperties { down = playPauseFocus },
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (
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
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onUserInteraction(); onSeek(-30_000L) },
                        modifier = Modifier
                            .size(48.dp)
                            .focusProperties {
                                up = seekbarFocus
                                right = playPauseFocus
                            }
                            .focusRequester(backFocus)
                            .onFocusChanged { if (it.isFocused) focused("back") },
                    ) {
                        Icon(Icons.Filled.Replay30, seekBackLabel)
                    }
                    IconButton(
                        onClick = { onUserInteraction(); onTogglePlayPause() },
                        modifier = Modifier
                            .size(48.dp)
                            .focusProperties {
                                up = seekbarFocus
                                left = backFocus
                                right = forwardFocus
                            }
                            .focusRequester(playPauseFocus)
                            .onFocusChanged { if (it.isFocused) focused("playPause") },
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (isPlaying) pauseLabel else playLabel,
                        )
                    }
                    IconButton(
                        onClick = { onUserInteraction(); onSeek(30_000L) },
                        modifier = Modifier
                            .size(48.dp)
                            .focusProperties {
                                up = seekbarFocus
                                left = playPauseFocus
                                right = infoFocus
                            }
                            .focusRequester(forwardFocus)
                            .onFocusChanged { if (it.isFocused) focused("forward") },
                    ) {
                        Icon(Icons.Filled.Forward30, seekForwardLabel)
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { onUserInteraction(); onOpenInfo() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .focusProperties {
                                        up = seekbarFocus
                                        left = forwardFocus
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
                                        up = seekbarFocus
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
                            Row(modifier = Modifier.padding(start = 16.dp)) {
                                IconButton(
                                    onClick = { onUserInteraction(); onStopPlayback() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .focusProperties {
                                            up = seekbarFocus
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

@Composable
internal fun RecordingSeekProgress(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val knownDuration = durationMs.takeIf { it != C.TIME_UNSET && it > 0L }
    val progress = knownDuration?.let { positionMs.toFloat() / it } ?: 0f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bottomGradient)
            .padding(start = 56.dp, end = 56.dp, top = 96.dp, bottom = 32.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = formatHms(positionMs.coerceAtLeast(0L) / 1_000L),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = knownDuration?.let { formatHms(it / 1_000L) }
                    ?: stringResource(R.string.recording_duration_unknown),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.24f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(MaterialTheme.shapes.small),
        )
    }
}
