package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.RecordingTimelinePresentation
import at.bernhardberger.tvhplayer.core.formatPlaybackDelta
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.recordingTimelinePresentation
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineActionGap
import at.bernhardberger.tvhplayer.ui.TvOverlayTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayTextTertiaryAlpha
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
    canSeek: Boolean,
    controlsVisible: Boolean,
    optionsOpen: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onStopPlayback: () -> Unit,
    onUserInteraction: () -> Unit,
    onOpenOptions: () -> Unit,
    onOpenInfo: () -> Unit,
    restoreOptionsFocus: Boolean = false,
    onOptionsFocusRestored: () -> Unit = {},
    restoreInfoFocus: Boolean = false,
    onInfoFocusRestored: () -> Unit = {},
    onCommitSeek: () -> Unit = {},
    paused: Boolean = false,
    playbackPresented: Boolean = true,
    previewing: Boolean = false,
    displayDurationMs: Long = durationMs,
    markers: List<Long> = emptyList(),
    markerPositionMs: Long = positionMs,
    markerNavigation: RecordingMarkerNavigation = remember { RecordingMarkerNavigation() },
    markerRevision: Long = 0L,
    onSeekMarker: (Long) -> Unit = {},
) {
    val pauseFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val timelineFocus = remember { FocusRequester() }
    val presentation = recordingTimelinePresentation(positionMs, durationMs.takeIf { it != C.TIME_UNSET }, growing, displayDurationMs)
    val seekable = canSeek && presentation is RecordingTimelinePresentation.Seekable
    var focusInitialized by remember { mutableStateOf(false) }
    var previousSeekable by remember { mutableStateOf(seekable) }
    var lastFocusWasTimeline by remember { mutableStateOf(false) }
    var relocatingKey by remember { mutableStateOf<Key?>(null) }
    var timelineFocused by remember { mutableStateOf(false) }
    val chromeAlpha = rememberPlayerChromeAlpha(timelineFocused, previewing)
    val chromeHidden = timelineFocused && previewing
    LaunchedEffect(markerNavigation.restoration) {
        if (markerNavigation.restoration > 0 && controlsVisible && !optionsOpen) {
            if (seekable) timelineFocus.requestFocus() else pauseFocus.requestFocus()
        }
    }
    LaunchedEffect(markers, seekable, controlsVisible, optionsOpen) {
        if (markerNavigation.selectedMs !in markers || !seekable || !controlsVisible || optionsOpen) {
            markerNavigation.dismiss()
        }
    }
    LaunchedEffect(controlsVisible, optionsOpen, restoreOptionsFocus, restoreInfoFocus, seekable) {
        if (controlsVisible && !optionsOpen) {
            val target = when {
                restoreInfoFocus -> infoFocus
                restoreOptionsFocus -> settingsFocus
                !focusInitialized -> pauseFocus
                previousSeekable && !seekable && lastFocusWasTimeline -> pauseFocus
                else -> null
            }
            previousSeekable = seekable
            if (target != null) androidx.compose.runtime.withFrameNanos { }
            if (target?.requestFocus() == true) {
                focusInitialized = true
                if (restoreOptionsFocus) onOptionsFocusRestored()
                if (restoreInfoFocus) onInfoFocusRestored()
            }
        } else {
            focusInitialized = false
        }
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        markerNavigation.handle(event, markers, onSeekMarker)
    }) {
    PlayerOverlayChrome(modifier = Modifier.onPreviewKeyEvent { event ->
        if (event.key != relocatingKey) false else {
            if (event.type == KeyEventType.KeyUp) relocatingKey = null
            true
        }
    }, headerContent = { modifier ->
        PlayerIdentityHeader(
            imageLoader = imageLoader, currentSession = currentSession, piconPath = piconPath,
            eyebrow = channelName, title = title, support = subtitle,
            clock = formatClock(nowSec), clockSupport = null,
            clockStatus = { PlayerStatusTags(paused, recordingPlayback = true, growing = growing, playbackPresented = playbackPresented) },
            modifier = modifier,
            tags = PlayerHeaderTags(picon = "recording-picon", eyebrow = "recording-channel-identity",
                title = "recording-title", support = "recording-subtitle", clock = "recording-clock"),
        )
    }) {
        Column {
            when (presentation) {
                is RecordingTimelinePresentation.Seekable -> if (canSeek) PlaybackSeekbar(
                    range = presentation.range,
                    paused = paused,
                    previewing = previewing && !markerNavigation.open,
                    recordingMarkers = markers,
                    recordingMarkerPreviewMs = markerNavigation.selectedMs?.takeIf { it in markers },
                    onOpenRecordingMarkers = {
                        onCommitSeek(); onUserInteraction()
                        markerNavigation.show(markers, markerPositionMs, revision = markerRevision)
                    },
                    trackOverlay = {
                        RecordingMarkerOverlay(
                            navigation = markerNavigation, markers = markers, onSeek = onSeekMarker,
                            displayDurationMs = displayDurationMs,
                            modifier = Modifier.matchParentSize(),
                        )
                    },
                    onSeekTo = { onUserInteraction(); onSeek(it - positionMs) },
                    modifier = Modifier
                        .testTag("recording-seekbar")
                        .focusRequester(timelineFocus)
                        .onFocusChanged {
                            timelineFocused = it.isFocused
                            if (it.isFocused) lastFocusWasTimeline = true
                        }
                        .focusProperties { down = pauseFocus; up = FocusRequester.Cancel }
                        .onPreviewKeyEvent { event ->
                            when (event.key) {
                                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                        onUserInteraction(); onTogglePlayPause()
                                    }
                                    true
                                }
                                Key.DirectionUp, Key.DirectionDown -> {
                                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                        onCommitSeek()
                                        if (event.key == Key.DirectionUp && markers.isNotEmpty()) {
                                            onUserInteraction()
                                            markerNavigation.show(markers, markerPositionMs, event.key, markerRevision)
                                        } else {
                                            relocatingKey = event.key
                                            pauseFocus.requestFocus()
                                        }
                                    }
                                    true
                                }
                                else -> false
                            }
                        },
                ) else RecordingDurationStatus(
                    presentation.range.positionMs,
                    stringResource(R.string.recording_known_duration, formatPlaybackDuration(presentation.range.endMs)),
                )
                is RecordingTimelinePresentation.StillRecording -> RecordingDurationStatus(
                    presentation.elapsedMs, stringResource(R.string.recording_still_recording),
                )
                is RecordingTimelinePresentation.DurationUnavailable -> RecordingDurationStatus(
                    presentation.elapsedMs, stringResource(R.string.recording_duration_unavailable),
                )
            }
        }
        Spacer(Modifier.height(TvOverlayTimelineActionGap))
        PlayerActionRow(
            infoFocus = infoFocus, settingsFocus = settingsFocus,
            onInfo = onOpenInfo, onSettings = onOpenOptions, onStop = onStopPlayback,
            onInteraction = { lastFocusWasTimeline = false; onUserInteraction() },
            onTogglePause = onTogglePlayPause, paused = paused, pauseFocus = pauseFocus,
            modifier = Modifier
                .playerChromeEmphasis(chromeAlpha, chromeHidden, focusOverflow = 8.dp)
                .testTag("recording-actions")
                .focusProperties {
                    up = if (seekable) timelineFocus else FocusRequester.Cancel
                    down = FocusRequester.Cancel
                }
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.DirectionUp || event.key == Key.DirectionDown) {
                        if (seekable && event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                            relocatingKey = event.key
                            timelineFocus.requestFocus()
                        }
                        true
                    } else false
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
    displayDurationMs: Long = durationMs,
) {
    val presentation = recordingTimelinePresentation(
        positionMs = targetMs,
        durationMs = durationMs.takeIf { it != C.TIME_UNSET },
        growing = growing,
        displayDurationMs = displayDurationMs,
    )
    val elapsed = formatPlaybackDuration(targetMs)
    val target = if (durationMs >= 3_600_000L && targetMs < 3_600_000L) "0:${elapsed.padStart(5, '0')}" else elapsed
    val delta = formatPlaybackDelta((originMs ?: targetMs).let { targetMs - it })
    val durationStatus = when (presentation) {
        is RecordingTimelinePresentation.Seekable -> stringResource(
            R.string.recording_known_duration, formatPlaybackDuration(presentation.range.endMs),
        )
        is RecordingTimelinePresentation.StillRecording -> stringResource(R.string.recording_still_recording)
        is RecordingTimelinePresentation.DurationUnavailable -> stringResource(R.string.recording_duration_unavailable)
    }
    val description = stringResource(R.string.recording_seek_preview_description, target, delta, durationStatus)
    Column(
        modifier = modifier.fillMaxWidth().background(bottomGradient)
            .padding(start = TvOverlaySidePadding, end = TvOverlaySidePadding,
                top = TvOverlayFooterGradientRunout, bottom = playerSeekPreviewBottomPadding(false))
            .testTag("recording-seek-preview")
            .clearAndSetSemantics { contentDescription = description; liveRegion = LiveRegionMode.Polite },
    ) {
        when (presentation) {
            is RecordingTimelinePresentation.Seekable -> PlayerTimelineBlock(
                progress = presentation.range.displayProgress,
                tone = PlayerTimelineTone.PREVIEW,
                ghostProgress = originMs?.let { (it.toFloat() / presentation.range.displayEndMs).coerceIn(0f, 1f) },
                leadingLabel = target,
                trailingLabel = formatPlaybackDuration(presentation.range.displayEndMs),
                previewLabel = target,
            )
            is RecordingTimelinePresentation.StillRecording -> RecordingDurationStatus(
                elapsedMs = presentation.elapsedMs, status = stringResource(R.string.recording_still_recording),
                delta = originMs?.let { formatPlaybackDelta(targetMs - it) },
            )
            is RecordingTimelinePresentation.DurationUnavailable -> RecordingDurationStatus(
                elapsedMs = presentation.elapsedMs, status = stringResource(R.string.recording_duration_unavailable),
                delta = originMs?.let { formatPlaybackDelta(targetMs - it) },
            )
        }
    }
}

internal data class RecordingDurationStatusEmphasis(val elapsedAlpha: Float, val statusAlpha: Float, val deltaAlpha: Float)

internal val recordingDurationStatusEmphasis = RecordingDurationStatusEmphasis(
    elapsedAlpha = TvOverlayTextTertiaryAlpha,
    statusAlpha = TvOverlayTextSecondaryAlpha,
    deltaAlpha = TvOverlayTextTertiaryAlpha,
)

@Composable
private fun RecordingDurationStatus(elapsedMs: Long, status: String, modifier: Modifier = Modifier, delta: String? = null) {
    val elapsed = formatPlaybackDuration(elapsedMs)
    val description = stringResource(R.string.recording_timeline_status_description, elapsed, status)
    Column(modifier.fillMaxWidth().testTag("recording-duration-status").clearAndSetSemantics { contentDescription = description }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(elapsed, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = recordingDurationStatusEmphasis.elapsedAlpha))
            Spacer(Modifier.weight(1f))
            Text(status, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = recordingDurationStatusEmphasis.statusAlpha))
        }
        delta?.let {
            Text(it, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = recordingDurationStatusEmphasis.deltaAlpha),
                modifier = Modifier.testTag("recording-seek-preview-delta"))
        }
    }
}
