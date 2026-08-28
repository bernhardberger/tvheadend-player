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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.media3.common.C
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.RecordingTimelinePresentation
import at.bernhardberger.tvhplayer.core.formatPlaybackDelta
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.recordingTimelinePresentation
import at.bernhardberger.tvhplayer.ui.TvOverlayActionButtonSize
import at.bernhardberger.tvhplayer.ui.TvOverlayActionGap
import at.bernhardberger.tvhplayer.ui.TvOverlayBottomPadding
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlayHeaderGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.TvOverlayTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayTextTertiaryAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineBlockGap
import at.bernhardberger.tvhplayer.ui.TvOverlayTopPadding
import at.bernhardberger.tvhplayer.ui.common.formatClock
import coil3.ImageLoader

@Composable
internal fun RecordingOverlayControls(
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation? = null,
    piconPath: String?,
    title: String,
    subtitle: String?,
    channelName: String?,
    positionMs: Long,
    durationMs: Long,
    growing: Boolean,
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
    restoreOptionsFocus: Boolean = false,
    onOptionsFocusRestored: () -> Unit = {},
) {
    var lastFocused by rememberSaveable { mutableStateOf("playPause") }
    var focusedAction by remember { mutableStateOf<String?>(null) }
    val playPauseFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val forwardFocus = remember { FocusRequester() }
    val stopFocus = remember { FocusRequester() }
    val optionsFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }
    val seekbarFocus = remember { FocusRequester() }
    val timelinePresentation = recordingTimelinePresentation(
        positionMs = positionMs,
        durationMs = durationMs.takeIf { it != C.TIME_UNSET },
        growing = growing,
    )
    val seekableTimeline = timelinePresentation is RecordingTimelinePresentation.Seekable
    val latestSeekableTimeline by rememberUpdatedState(seekableTimeline)
    val latestLastFocused by rememberUpdatedState(lastFocused)
    DisposableEffect(seekableTimeline) {
        onDispose {
            if (
                seekableTimeline &&
                !latestSeekableTimeline &&
                latestLastFocused == "seekbar"
            ) {
                playPauseFocus.requestFocus()
            }
        }
    }
    val focusTargets = mapOf(
        "playPause" to playPauseFocus,
        "back" to backFocus,
        "forward" to forwardFocus,
        "stop" to stopFocus,
        "options" to optionsFocus,
        "info" to infoFocus,
        "seekbar" to seekbarFocus,
    )

    LaunchedEffect(
        controlsVisible,
        showStop,
        optionsOpen,
        restoreOptionsFocus,
    ) {
        if (controlsVisible && !optionsOpen) {
            val availableTargets = buildMap {
                put("playPause", playPauseFocus)
                put("back", backFocus)
                put("forward", forwardFocus)
                if (showStop) put("stop", stopFocus)
                put("options", optionsFocus)
                put("info", infoFocus)
                if (seekableTimeline) put("seekbar", seekbarFocus)
            }
            val target = if (restoreOptionsFocus) {
                optionsFocus
            } else {
                availableTargets[lastFocused] ?: playPauseFocus
            }
            val focused = target.requestFocus()
            if (focused && restoreOptionsFocus) {
                onOptionsFocusRestored()
            }
        }
    }

    fun focusChanged(key: String, isFocused: Boolean) {
        if (isFocused) {
            if (focusTargets.containsKey(key)) lastFocused = key
            focusedAction = key
            onUserInteraction()
        } else if (focusedAction == key) {
            focusedAction = null
        }
    }

    val seekBackLabel = stringResource(R.string.seek_back_30)
    val seekForwardLabel = stringResource(R.string.seek_forward_30)
    val playLabel = stringResource(R.string.play)
    val pauseLabel = stringResource(R.string.pause)
    val moreLabel = stringResource(R.string.playback_options)
    val infoLabel = stringResource(R.string.player_info)
    val stopLabel = stringResource(R.string.stop_playback)
    val contextLabel = if (controlsVisible && !optionsOpen && focusedAction == "options") {
        moreLabel
    } else {
        null
    }
    val clock = remember(nowSec) { formatClock(nowSec) }
    Box(Modifier.fillMaxSize()) {
        PlayerIdentityHeader(
            imageLoader = imageLoader,
            currentSession = currentSession,
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
            when (timelinePresentation) {
                is RecordingTimelinePresentation.Seekable -> PlaybackSeekbar(
                    range = timelinePresentation.range,
                    onSeekTo = { target ->
                        onUserInteraction()
                        onSeek(target - positionMs)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recording-seekbar")
                        .focusRequester(seekbarFocus)
                        .onFocusChanged { focusChanged("seekbar", it.isFocused) }
                        .focusProperties { down = playPauseFocus },
                )
                is RecordingTimelinePresentation.StillRecording -> RecordingDurationStatus(
                    elapsedMs = timelinePresentation.elapsedMs,
                    status = stringResource(R.string.recording_still_recording),
                )
                is RecordingTimelinePresentation.DurationUnavailable -> RecordingDurationStatus(
                    elapsedMs = timelinePresentation.elapsedMs,
                    status = stringResource(R.string.recording_duration_unavailable),
                )
            }
            Spacer(Modifier.height(TvOverlayTimelineBlockGap))
            PlayerActionRow(
                contextLabel = contextLabel,
                modifier = Modifier
                    .testTag("recording-actions")
                    .onPreviewKeyEvent { event ->
                        if (
                            seekableTimeline &&
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionUp
                        ) {
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
                                    up = if (seekableTimeline) {
                                        seekbarFocus
                                    } else {
                                        FocusRequester.Cancel
                                    }
                                    right = playPauseFocus
                                }
                                .focusRequester(backFocus)
                                .onFocusChanged { focusChanged("back", it.isFocused) },
                        ) {
                            Icon(Icons.Filled.Replay30, seekBackLabel)
                        }
                        IconButton(
                            onClick = { onUserInteraction(); onTogglePlayPause() },
                            modifier = Modifier
                                .size(TvOverlayActionButtonSize)
                                .testTag("recording-play-pause")
                                .focusProperties {
                                    up = if (seekableTimeline) {
                                        seekbarFocus
                                    } else {
                                        FocusRequester.Cancel
                                    }
                                    left = backFocus
                                    right = forwardFocus
                                }
                                .focusRequester(playPauseFocus)
                                .onFocusChanged { focusChanged("playPause", it.isFocused) },
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
                                    up = if (seekableTimeline) {
                                        seekbarFocus
                                    } else {
                                        FocusRequester.Cancel
                                    }
                                    left = playPauseFocus
                                    right = infoFocus
                                }
                                .focusRequester(forwardFocus)
                                .onFocusChanged { focusChanged("forward", it.isFocused) },
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
                                    up = if (seekableTimeline) {
                                        seekbarFocus
                                    } else {
                                        FocusRequester.Cancel
                                    }
                                    left = forwardFocus
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
                                .testTag("recording-playback-options")
                                .focusProperties {
                                    up = if (seekableTimeline) {
                                        seekbarFocus
                                    } else {
                                        FocusRequester.Cancel
                                    }
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
                        Row(modifier = Modifier.testTag("recording-terminal-actions")) {
                            IconButton(
                                onClick = { onUserInteraction(); onStopPlayback() },
                                modifier = Modifier
                                    .size(TvOverlayActionButtonSize)
                                    .focusProperties {
                                        up = if (seekableTimeline) {
                                            seekbarFocus
                                        } else {
                                            FocusRequester.Cancel
                                        }
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

@Composable
internal fun RecordingSeekPreview(
    targetMs: Long,
    originMs: Long?,
    durationMs: Long,
    growing: Boolean,
    modifier: Modifier = Modifier,
) {
    val presentation = recordingTimelinePresentation(
        positionMs = targetMs,
        durationMs = durationMs.takeIf { it != C.TIME_UNSET },
        growing = growing,
    )
    val target = formatPlaybackDuration(targetMs)
    val delta = formatPlaybackDelta((originMs ?: targetMs).let { targetMs - it })
    val durationStatus = when (presentation) {
        is RecordingTimelinePresentation.Seekable -> stringResource(
            R.string.recording_known_duration,
            formatPlaybackDuration(presentation.range.endMs),
        )
        is RecordingTimelinePresentation.StillRecording ->
            stringResource(R.string.recording_still_recording)
        is RecordingTimelinePresentation.DurationUnavailable ->
            stringResource(R.string.recording_duration_unavailable)
    }
    val description = stringResource(
        R.string.recording_seek_preview_description,
        target,
        delta,
        durationStatus,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bottomGradient)
            .padding(
                start = TvOverlaySidePadding,
                end = TvOverlaySidePadding,
                top = TvOverlayFooterGradientRunout,
                bottom = TvOverlayBottomPadding,
            )
            .testTag("recording-seek-preview")
            .clearAndSetSemantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        when (presentation) {
            is RecordingTimelinePresentation.Seekable -> PlayerTimelineBlock(
                progress = presentation.range.progress,
                tone = PlayerTimelineTone.PREVIEW,
                ghostProgress = originMs?.let { origin ->
                    origin.toFloat().div(presentation.range.endMs).coerceIn(0f, 1f)
                },
                leadingLabel = formatPlaybackDuration(targetMs),
                trailingLabel = originMs?.let { formatPlaybackDelta(targetMs - it) }
                    ?: formatPlaybackDuration(presentation.range.endMs),
            )
            is RecordingTimelinePresentation.StillRecording -> RecordingDurationStatus(
                elapsedMs = presentation.elapsedMs,
                status = stringResource(R.string.recording_still_recording),
                delta = originMs?.let { formatPlaybackDelta(targetMs - it) },
            )
            is RecordingTimelinePresentation.DurationUnavailable -> RecordingDurationStatus(
                elapsedMs = presentation.elapsedMs,
                status = stringResource(R.string.recording_duration_unavailable),
                delta = originMs?.let { formatPlaybackDelta(targetMs - it) },
            )
        }
    }
}

internal data class RecordingDurationStatusEmphasis(
    val elapsedAlpha: Float,
    val statusAlpha: Float,
    val deltaAlpha: Float,
)

internal val recordingDurationStatusEmphasis = RecordingDurationStatusEmphasis(
    elapsedAlpha = TvOverlayTextTertiaryAlpha,
    statusAlpha = TvOverlayTextSecondaryAlpha,
    deltaAlpha = TvOverlayTextTertiaryAlpha,
)

@Composable
private fun RecordingDurationStatus(
    elapsedMs: Long,
    status: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
) {
    val elapsed = formatPlaybackDuration(elapsedMs)
    val description = stringResource(
        R.string.recording_timeline_status_description,
        elapsed,
        status,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recording-duration-status")
            .clearAndSetSemantics {
                contentDescription = description
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = elapsed,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = recordingDurationStatusEmphasis.elapsedAlpha,
                ),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = status,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = recordingDurationStatusEmphasis.statusAlpha,
                ),
            )
        }
        delta?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = recordingDurationStatusEmphasis.deltaAlpha,
                ),
                modifier = Modifier.testTag("recording-seek-preview-delta"),
            )
        }
    }
}
