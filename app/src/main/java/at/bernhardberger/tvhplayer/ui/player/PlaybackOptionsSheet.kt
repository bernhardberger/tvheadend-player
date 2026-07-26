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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import at.bernhardberger.tvhplayer.core.adjacentPlaybackOptionsPage
import at.bernhardberger.tvhplayer.settings.AspectRatioMode

/**
 * Anchored opaque playback-options popover.
 *
 * Sits above the bottom-end control cluster so focus stays local and controls
 * cannot ghost through the surface. Categories switch laterally.
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
    val categoryFocus = remember { FocusRequester() }
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
    val currentValue = when (page) {
        PlaybackOptionsPage.AUDIO ->
            audioTracks.firstOrNull { it.selected }?.label ?: noneAudio
        PlaybackOptionsPage.SUBTITLES ->
            textTracks.firstOrNull { it.selected }?.label ?: noneSubs
        PlaybackOptionsPage.DISPLAY -> when (aspectRatio) {
            AspectRatioMode.FIT -> stringResource(R.string.display_mode_auto)
            AspectRatioMode.FORCE_16_9 -> stringResource(R.string.display_mode_16_9)
            AspectRatioMode.FORCE_4_3 -> stringResource(R.string.display_mode_4_3)
        }
        PlaybackOptionsPage.STATS -> stringResource(
            if (statsVisible) R.string.stats_on else R.string.stats_off
        )
    }
    val categoryTitle = stringResource(
        when (page) {
            PlaybackOptionsPage.AUDIO -> R.string.audio_track
            PlaybackOptionsPage.SUBTITLES -> R.string.subtitles
            PlaybackOptionsPage.DISPLAY -> R.string.display_mode
            PlaybackOptionsPage.STATS -> R.string.stats_for_nerds
        }
    )

    LaunchedEffect(page) {
        // Prefer content focus; category header remains reachable with Up.
        runCatching { initialFocus.requestFocus() }
            .recoverCatching { categoryFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f)),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 48.dp, bottom = 108.dp)
                .widthIn(min = 360.dp, max = 440.dp)
                .width(420.dp)
                .heightIn(max = 420.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onPageChange(
                                adjacentPlaybackOptionsPage(page, -1, simpleTvActive),
                            )
                            true
                        }
                        Key.DirectionRight -> {
                            onPageChange(
                                adjacentPlaybackOptionsPage(page, 1, simpleTvActive),
                            )
                            true
                        }
                        else -> false
                    }
                },
        ) {
            Surface(
                // Compact anchored popover — not a full-height sheet.
                shape = MaterialTheme.shapes.large,
                colors = SurfaceDefaults.colors(
                    containerColor = Color(0xFF0D1117),
                    contentColor = Color.White,
                ),
            ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CategoryHeader(
                    title = categoryTitle,
                    currentValue = currentValue,
                    focusRequester = categoryFocus,
                    onPrevious = {
                        onPageChange(adjacentPlaybackOptionsPage(page, -1, simpleTvActive))
                    },
                    onNext = {
                        onPageChange(adjacentPlaybackOptionsPage(page, 1, simpleTvActive))
                    },
                )

                when (page) {
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

                if (showSimpleTvExit) {
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
    }
}

@Composable
private fun CategoryHeader(
    title: String,
    currentValue: String,
    focusRequester: FocusRequester,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.playback_options_previous_category),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = currentValue,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.playback_options_next_category),
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
            .heightIn(max = 260.dp),
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
    showSwitch: Boolean = false,
    leadingLock: Boolean = false,
) {
    ListItem(
        selected = if (showSwitch) false else selected,
        onClick = onClick,
        headlineContent = { Text(label) },
        supportingContent = supportingLabel?.let { text ->
            { Text(text, color = Color.White.copy(alpha = 0.72f)) }
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
                selected -> Icon(Icons.Filled.Check, contentDescription = null)
            }
        },
        scale = ListItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    )
}
