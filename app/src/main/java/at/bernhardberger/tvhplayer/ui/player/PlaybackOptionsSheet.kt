package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.settings.AspectRatioMode

/**
 * Compact playback-dependent overlay with a structured root and detail pages.
 */
@Composable
internal fun PlaybackOptionsSheet(
    page: PlaybackOptionsPage,
    player: Player,
    aspectRatio: AspectRatioMode,
    statsVisible: Boolean,
    showSimpleTvExit: Boolean,
    simpleTvActive: Boolean = false,
    onPageChange: (PlaybackOptionsPage) -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onStatsVisibleChange: (Boolean) -> Unit,
    onSimpleTvExit: () -> Unit,
) {
    val initialFocus = remember(page) { FocusRequester() }
    val unknownLanguage = stringResource(R.string.track_unknown_language)
    val mono = stringResource(R.string.track_mono)
    val stereo = stringResource(R.string.track_stereo)
    val surround51 = stringResource(R.string.track_surround_5_1)
    val surround71 = stringResource(R.string.track_surround_7_1)
    val channelsTemplate = stringResource(R.string.track_channels_count)
    val noneAudio = stringResource(R.string.no_audio_tracks)
    val noneSubs = stringResource(R.string.subtitles_off)
    val audioTracks = remember(player.currentTracks, page) {
        collectTracks(
            tracks = player.currentTracks,
            trackType = C.TRACK_TYPE_AUDIO,
            unknownLanguageLabel = unknownLanguage,
            monoLabel = mono,
            stereoLabel = stereo,
            surround51Label = surround51,
            surround71Label = surround71,
            channelsLabel = { count -> channelsTemplate.format(count) },
        )
    }
    val textTracks = remember(player.currentTracks, page) {
        collectTracks(
            tracks = player.currentTracks,
            trackType = C.TRACK_TYPE_TEXT,
            unknownLanguageLabel = unknownLanguage,
            monoLabel = mono,
            stereoLabel = stereo,
            surround51Label = surround51,
            surround71Label = surround71,
            channelsLabel = { count -> channelsTemplate.format(count) },
        )
    }
    val audioValue = audioTracks.firstOrNull { it.selected }?.label ?: noneAudio
    val subtitlesValue = textTracks.firstOrNull { it.selected }?.label ?: noneSubs
    val displayValue = stringResource(
        when (aspectRatio) {
            AspectRatioMode.FIT -> R.string.display_mode_auto
            AspectRatioMode.FORCE_16_9 -> R.string.display_mode_16_9
            AspectRatioMode.FORCE_4_3 -> R.string.display_mode_4_3
        }
    )
    val statsValue = stringResource(if (statsVisible) R.string.stats_on else R.string.stats_off)

    LaunchedEffect(page) {
        runCatching { initialFocus.requestFocus() }
    }

    PlaybackOptionsOverlayFrame {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OptionsHeader(
                title = stringResource(
                    when (page) {
                        PlaybackOptionsPage.ROOT -> R.string.playback_options
                        PlaybackOptionsPage.AUDIO -> R.string.audio_track
                        PlaybackOptionsPage.SUBTITLES -> R.string.subtitles
                        PlaybackOptionsPage.DISPLAY -> R.string.display_mode
                        PlaybackOptionsPage.STATS -> R.string.stats_for_nerds
                    }
                ),
                currentValue = when (page) {
                    PlaybackOptionsPage.ROOT -> null
                    PlaybackOptionsPage.AUDIO -> audioValue
                    PlaybackOptionsPage.SUBTITLES -> subtitlesValue
                    PlaybackOptionsPage.DISPLAY -> displayValue
                    PlaybackOptionsPage.STATS -> statsValue
                },
                onBack = if (page == PlaybackOptionsPage.ROOT) {
                    null
                } else {
                    { onPageChange(PlaybackOptionsPage.ROOT) }
                },
            )

            when (page) {
                PlaybackOptionsPage.ROOT -> PlaybackOptionsRoot(
                    audioValue = audioValue,
                    subtitlesValue = subtitlesValue,
                    displayValue = displayValue,
                    statsVisible = statsVisible,
                    simpleTvActive = simpleTvActive,
                    initialFocus = initialFocus,
                    onPageChange = onPageChange,
                    onStatsVisibleChange = onStatsVisibleChange,
                )
                PlaybackOptionsPage.AUDIO -> TrackOptions(
                    tracks = audioTracks,
                    subtitles = false,
                    initialFocus = initialFocus,
                    onSelect = { track ->
                        selectAudioTrack(player, track)
                    },
                )
                PlaybackOptionsPage.SUBTITLES -> TrackOptions(
                    tracks = textTracks,
                    subtitles = true,
                    initialFocus = initialFocus,
                    onSelectOff = {
                        selectTextTrack(player, null)
                    },
                    onSelect = { track ->
                        selectTextTrack(player, track)
                    },
                )
                PlaybackOptionsPage.DISPLAY -> DisplayOptions(
                    selected = aspectRatio,
                    initialFocus = initialFocus,
                    onSelect = onAspectRatioChange,
                )
                PlaybackOptionsPage.STATS -> {
                    PlaybackOptionRow(
                        label = stringResource(R.string.stats_for_nerds),
                        selected = statsVisible,
                        onClick = { onStatsVisibleChange(!statsVisible) },
                        showSwitch = true,
                        modifier = Modifier.focusRequester(initialFocus),
                    )
                }
            }

            if (showSimpleTvExit && page == PlaybackOptionsPage.ROOT) {
                Text(
                    text = stringResource(R.string.simple_tv_owner_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { heading() },
                )
                PlaybackOptionRow(
                    label = stringResource(R.string.simple_tv_unlock),
                    onClick = onSimpleTvExit,
                    leadingLock = true,
                )
            }
        }
    }
}

@Composable
internal fun PlaybackOptionsOverlayFrame(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f)),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Surface(
            modifier = Modifier
                .padding(end = 48.dp, bottom = 108.dp)
                .width(420.dp)
                .heightIn(max = 420.dp)
                .testTag("playback-options-overlay"),
            shape = MaterialTheme.shapes.large,
            colors = SurfaceDefaults.colors(
                containerColor = Color(0xFF0D1117),
                contentColor = Color.White,
            ),
        ) {
            content()
        }
    }
}

@Composable
private fun OptionsHeader(
    title: String,
    currentValue: String?,
    onBack: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.back),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            currentValue?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PlaybackOptionsRoot(
    audioValue: String,
    subtitlesValue: String,
    displayValue: String,
    statsVisible: Boolean,
    simpleTvActive: Boolean,
    initialFocus: FocusRequester,
    onPageChange: (PlaybackOptionsPage) -> Unit,
    onStatsVisibleChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PlaybackOptionRow(
            label = stringResource(R.string.audio_track),
            supportingLabel = audioValue,
            onClick = { onPageChange(PlaybackOptionsPage.AUDIO) },
            showChevron = true,
            modifier = Modifier.focusRequester(initialFocus),
        )
        PlaybackOptionRow(
            label = stringResource(R.string.subtitles),
            supportingLabel = subtitlesValue,
            onClick = { onPageChange(PlaybackOptionsPage.SUBTITLES) },
            showChevron = true,
        )
        if (!simpleTvActive) {
            PlaybackOptionRow(
                label = stringResource(R.string.display_mode),
                supportingLabel = displayValue,
                onClick = { onPageChange(PlaybackOptionsPage.DISPLAY) },
                showChevron = true,
            )
            PlaybackOptionRow(
                label = stringResource(R.string.stats_for_nerds),
                selected = statsVisible,
                onClick = { onStatsVisibleChange(!statsVisible) },
                showSwitch = true,
            )
        }
    }
}

@Composable
private fun TrackOptions(
    tracks: List<UiTrack>,
    subtitles: Boolean,
    initialFocus: FocusRequester,
    onSelect: (UiTrack) -> Unit,
    onSelectOff: (() -> Unit)? = null,
) {
    val selectedIndex = tracks.indexOfFirst(UiTrack::selected).takeIf { it >= 0 } ?: 0
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 236.dp),
    ) {
        if (subtitles && onSelectOff != null) {
            item {
                PlaybackOptionRow(
                    label = stringResource(R.string.subtitles_off),
                    selected = tracks.none(UiTrack::selected),
                    onClick = onSelectOff,
                    modifier = if (tracks.none(UiTrack::selected)) {
                        Modifier.focusRequester(initialFocus)
                    } else {
                        Modifier
                    },
                )
            }
        }
        itemsIndexed(tracks) { index, track ->
            PlaybackOptionRow(
                label = track.label,
                supportingLabel = track.secondaryLabel,
                selected = track.selected,
                onClick = { onSelect(track) },
                modifier = if (track.selected || (!subtitles && index == selectedIndex)) {
                    Modifier.focusRequester(initialFocus)
                } else {
                    Modifier
                },
            )
        }
        if (tracks.isEmpty()) {
            item {
                PlaybackOptionRow(
                    label = stringResource(
                        if (subtitles) R.string.no_subtitles else R.string.no_audio_tracks
                    ),
                    onClick = { },
                    modifier = Modifier.focusRequester(initialFocus),
                )
            }
        }
    }
}

@Composable
private fun DisplayOptions(
    selected: AspectRatioMode,
    initialFocus: FocusRequester,
    onSelect: (AspectRatioMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PlaybackOptionRow(
            label = stringResource(R.string.display_mode_auto),
            selected = selected == AspectRatioMode.FIT,
            onClick = { onSelect(AspectRatioMode.FIT) },
            modifier = if (selected == AspectRatioMode.FIT) {
                Modifier.focusRequester(initialFocus)
            } else {
                Modifier
            },
        )
        PlaybackOptionRow(
            label = stringResource(R.string.display_mode_16_9),
            selected = selected == AspectRatioMode.FORCE_16_9,
            onClick = { onSelect(AspectRatioMode.FORCE_16_9) },
            modifier = if (selected == AspectRatioMode.FORCE_16_9) {
                Modifier.focusRequester(initialFocus)
            } else {
                Modifier
            },
        )
        PlaybackOptionRow(
            label = stringResource(R.string.display_mode_4_3),
            selected = selected == AspectRatioMode.FORCE_4_3,
            onClick = { onSelect(AspectRatioMode.FORCE_4_3) },
            modifier = if (selected == AspectRatioMode.FORCE_4_3) {
                Modifier.focusRequester(initialFocus)
            } else {
                Modifier
            },
        )
    }
}

@Composable
private fun PlaybackOptionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingLabel: String? = null,
    selected: Boolean = false,
    showChevron: Boolean = false,
    showSwitch: Boolean = false,
    leadingLock: Boolean = false,
) {
    ListItem(
        selected = if (showSwitch) false else selected,
        onClick = onClick,
        headlineContent = { Text(label) },
        supportingContent = supportingLabel?.let { text ->
            { Text(text) }
        },
        leadingContent = if (leadingLock) {
            {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        } else {
            null
        },
        trailingContent = {
            when {
                showSwitch -> Switch(checked = selected, onCheckedChange = null)
                showChevron -> Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
                selected -> Icon(Icons.Filled.Check, contentDescription = null)
            }
        },
        scale = ListItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    )
}
