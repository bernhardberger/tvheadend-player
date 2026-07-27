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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.media3.common.C
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.formatPlaybackDelta
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.recordingSeekbarRange
import at.bernhardberger.tvhplayer.ui.TvOverlayActionButtonSize
import at.bernhardberger.tvhplayer.ui.TvOverlayActionGap
import at.bernhardberger.tvhplayer.ui.TvOverlayBottomPadding
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineBlockGap
import at.bernhardberger.tvhplayer.ui.TvOverlayTopPadding
import at.bernhardberger.tvhplayer.ui.common.formatClock
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
    val clock = remember(nowSec) { formatClock(nowSec) }
    Box(Modifier.fillMaxSize()) {
        PlayerIdentityHeader(
            imageLoader = imageLoader,
            piconPath = piconPath,
            eyebrow = channelName?.takeIf(String::isNotBlank),
            title = title,
            support = subtitle?.takeIf(String::isNotBlank),
            clock = clock,
            clockSupport = null,
            tags = PlayerHeaderTags(
                picon = "recording-picon",
                eyebrow = "recording-channel-identity",
                title = "recording-programme-title",
                support = "recording-subtitle",
                clock = "recording-clock",
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
            Spacer(Modifier.height(TvOverlayTimelineBlockGap))
            PlayerActionRow(
                modifier = Modifier
                    .testTag("recording-actions")
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                            seekbarFocus.requestFocus()
                            true
                        } else {
                            false
                        }
                    },
                transport = {
                    Row(
                        modifier = Modifier.testTag("recording-transport-actions"),
                        horizontalArrangement = Arrangement.spacedBy(TvOverlayActionGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { onUserInteraction(); onSeek(-30_000L) },
                            modifier = Modifier
                                .size(TvOverlayActionButtonSize)
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
                                .size(TvOverlayActionButtonSize)
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
                                .size(TvOverlayActionButtonSize)
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
                },
                utilities = {
                    Row(
                        modifier = Modifier.testTag("recording-utility-actions"),
                        horizontalArrangement = Arrangement.spacedBy(TvOverlayActionGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { onUserInteraction(); onOpenInfo() },
                            modifier = Modifier
                                .size(TvOverlayActionButtonSize)
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
                                .size(TvOverlayActionButtonSize)
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
                },
                terminal = if (showStop) {
                    {
                        Row(modifier = Modifier.testTag("recording-terminal-actions")) {
                            IconButton(
                                onClick = { onUserInteraction(); onStopPlayback() },
                                modifier = Modifier
                                    .size(TvOverlayActionButtonSize)
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
                } else {
                    null
                },
            )
        }
    }
}

@Composable
internal fun RecordingSeekPreview(
    targetMs: Long,
    originMs: Long?,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val knownDuration = durationMs.takeIf { it != C.TIME_UNSET && it > 0L }
    val progress = knownDuration?.let { targetMs.toFloat() / it } ?: 0f
    val originProgress = knownDuration?.let { duration ->
        originMs?.toFloat()?.div(duration)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bottomGradient)
            .padding(
                start = TvOverlaySidePadding,
                end = TvOverlaySidePadding,
                top = TvOverlayFooterGradientRunout,
                bottom = TvOverlayBottomPadding,
            ),
    ) {
        PlayerTimelineBlock(
            progress = progress.coerceIn(0f, 1f),
            tone = PlayerTimelineTone.PREVIEW,
            ghostProgress = originProgress?.coerceIn(0f, 1f),
            leadingLabel = formatPlaybackDuration(targetMs),
            trailingLabel = originMs?.let { formatPlaybackDelta(targetMs - it) }
                ?: knownDuration?.let(::formatPlaybackDuration)
                ?: stringResource(R.string.recording_duration_unknown),
        )
    }
}
