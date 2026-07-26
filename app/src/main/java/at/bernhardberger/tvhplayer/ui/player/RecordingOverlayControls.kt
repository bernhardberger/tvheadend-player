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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
    isPlaying: Boolean,
    controlsVisible: Boolean,
    optionsOpen: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onStopPlayback: () -> Unit,
    onUserInteraction: () -> Unit,
    showStop: Boolean,
    onOpenOptions: () -> Unit,
) {
    var lastFocused by rememberSaveable { mutableStateOf("playPause") }
    val playPauseFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val forwardFocus = remember { FocusRequester() }
    val stopFocus = remember { FocusRequester() }
    val optionsFocus = remember { FocusRequester() }
    val focusTargets = mapOf(
        "playPause" to playPauseFocus,
        "back" to backFocus,
        "forward" to forwardFocus,
        "stop" to stopFocus,
        "options" to optionsFocus,
    )

    LaunchedEffect(controlsVisible, showStop, optionsOpen) {
        if (controlsVisible && !optionsOpen) {
            val availableTargets = buildMap {
                put("playPause", playPauseFocus)
                put("back", backFocus)
                put("forward", forwardFocus)
                if (showStop) put("stop", stopFocus)
                put("options", optionsFocus)
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
    val stopLabel = stringResource(R.string.stop_playback)
    val focusedLabel = when (lastFocused) {
        "back" -> seekBackLabel
        "forward" -> seekForwardLabel
        "playPause" -> if (isPlaying) pauseLabel else playLabel
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
                .padding(start = 56.dp, end = 56.dp, top = 104.dp, bottom = 32.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
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
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.titleSmall,
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

            Spacer(Modifier.height(14.dp))
            PlaybackSeekbar(
                range = recordingSeekbarRange(
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = knownDuration,
                ),
                onSeekTo = { target ->
                    onUserInteraction()
                    onSeek(target - positionMs)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
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
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onUserInteraction(); onSeek(-30_000L) },
                        modifier = Modifier
                            .size(52.dp)
                            .focusRequester(backFocus)
                            .onFocusChanged { if (it.isFocused) focused("back") },
                    ) {
                        Icon(Icons.Filled.Replay30, seekBackLabel)
                    }
                    IconButton(
                        onClick = { onUserInteraction(); onTogglePlayPause() },
                        modifier = Modifier
                            .size(56.dp)
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
                            .size(52.dp)
                            .focusRequester(forwardFocus)
                            .onFocusChanged { if (it.isFocused) focused("forward") },
                    ) {
                        Icon(Icons.Filled.Forward30, seekForwardLabel)
                    }
                    IconButton(
                        onClick = { onUserInteraction(); onOpenOptions() },
                        modifier = Modifier
                            .size(52.dp)
                            .focusRequester(optionsFocus)
                            .onFocusChanged { if (it.isFocused) focused("options") },
                    ) {
                        Icon(Icons.Filled.MoreVert, moreLabel)
                    }
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
