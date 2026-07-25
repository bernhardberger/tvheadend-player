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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.common.formatHms
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import coil3.ImageLoader

@Composable
internal fun RecordingOverlayControls(
    player: Player,
    imageLoader: ImageLoader,
    piconPath: String?,
    title: String,
    subtitle: String?,
    channelName: String?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    controlsVisible: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onStopPlayback: () -> Unit,
    onUserInteraction: () -> Unit,
    showStop: Boolean,
    showUnlock: Boolean,
    onUnlock: () -> Unit,
) {
    var showAudio by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var lastFocused by rememberSaveable { mutableStateOf("playPause") }
    val playPauseFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val forwardFocus = remember { FocusRequester() }
    val stopFocus = remember { FocusRequester() }
    val unlockFocus = remember { FocusRequester() }
    val audioFocus = remember { FocusRequester() }
    val subtitleFocus = remember { FocusRequester() }
    val focusTargets = mapOf(
        "playPause" to playPauseFocus,
        "back" to backFocus,
        "forward" to forwardFocus,
        "stop" to stopFocus,
        "unlock" to unlockFocus,
        "audio" to audioFocus,
        "subtitles" to subtitleFocus,
    )

    LaunchedEffect(controlsVisible, showStop, showUnlock) {
        if (controlsVisible) {
            val availableTargets = buildMap {
                put("playPause", playPauseFocus)
                put("back", backFocus)
                put("forward", forwardFocus)
                if (showStop) put("stop", stopFocus)
                if (showUnlock) put("unlock", unlockFocus)
                put("audio", audioFocus)
                put("subtitles", subtitleFocus)
            }
            (availableTargets[lastFocused] ?: playPauseFocus).requestFocus()
        }
    }

    fun focused(key: String) {
        if (focusTargets.containsKey(key)) lastFocused = key
        onUserInteraction()
    }

    val knownDuration = durationMs.takeIf { it != C.TIME_UNSET && it > 0L }
    val progress = knownDuration?.let { positionMs.toFloat() / it } ?: 0f

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(bottomGradient)
                .padding(start = 56.dp, end = 56.dp, top = 104.dp, bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
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
                    channelName?.takeIf(String::isNotBlank)?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.76f),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.takeIf(String::isNotBlank)?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.76f),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = formatHms(positionMs.coerceAtLeast(0L) / 1_000L),
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = knownDuration?.let { formatHms(it / 1_000L) }
                        ?: stringResource(R.string.recording_duration_unknown),
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.24f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.small),
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onUserInteraction(); onSeek(-30_000L) },
                        modifier = Modifier
                            .size(52.dp)
                            .focusRequester(backFocus)
                            .onFocusChanged { if (it.isFocused) focused("back") },
                    ) {
                        Icon(Icons.Filled.Replay30, stringResource(R.string.seek_back_30))
                    }
                    IconButton(
                        onClick = { onUserInteraction(); onTogglePlayPause() },
                        modifier = Modifier
                            .size(64.dp)
                            .focusRequester(playPauseFocus)
                            .onFocusChanged { if (it.isFocused) focused("playPause") },
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            stringResource(if (isPlaying) R.string.pause else R.string.play),
                        )
                    }
                    IconButton(
                        onClick = { onUserInteraction(); onSeek(30_000L) },
                        modifier = Modifier
                            .size(52.dp)
                            .focusRequester(forwardFocus)
                            .onFocusChanged { if (it.isFocused) focused("forward") },
                    ) {
                        Icon(Icons.Filled.Forward30, stringResource(R.string.seek_forward_30))
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onUserInteraction(); showAudio = true },
                        modifier = Modifier
                            .size(52.dp)
                            .focusRequester(audioFocus)
                            .onFocusChanged { if (it.isFocused) focused("audio") },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.audio_track))
                    }
                    IconButton(
                        onClick = { onUserInteraction(); showSubs = true },
                        modifier = Modifier
                            .size(52.dp)
                            .focusRequester(subtitleFocus)
                            .onFocusChanged { if (it.isFocused) focused("subtitles") },
                    ) {
                        Icon(Icons.Filled.Subtitles, stringResource(R.string.subtitles))
                    }
                }
            }
        }
    }

    if (showAudio) AudioTrackDialog(player = player, onDismiss = { showAudio = false })
    if (showSubs) SubtitleTrackDialog(player = player, onDismiss = { showSubs = false })
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
