package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.tv.material3.Icon
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

private enum class PlaybackOptionsRootItem {
    AUDIO,
    SUBTITLES,
    DISPLAY,
    STATS,
    EXIT_SIMPLE_TV,
}

@Composable
internal fun PlaybackOptionsSheet(
    page: PlaybackOptionsPage,
    player: Player,
    aspectRatio: AspectRatioMode,
    statsVisible: Boolean,
    showSimpleTvExit: Boolean,
    onPageChange: (PlaybackOptionsPage) -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onStatsVisibleChange: (Boolean) -> Unit,
    onSimpleTvExit: () -> Unit,
) {
    val initialFocus = remember(page) { FocusRequester() }
    var lastRootItem by rememberSaveable { mutableStateOf(PlaybackOptionsRootItem.AUDIO) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f)),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(420.dp),
            colors = SurfaceDefaults.colors(
                // Fully opaque so player controls cannot ghost through the sheet.
                containerColor = Color(0xFF0D1117),
                contentColor = Color.White,
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(
                        when (page) {
                            PlaybackOptionsPage.ROOT -> R.string.playback_options
                            PlaybackOptionsPage.AUDIO -> R.string.audio_track
                            PlaybackOptionsPage.SUBTITLES -> R.string.subtitles
                            PlaybackOptionsPage.DISPLAY -> R.string.display_mode
                        }
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                )

                when (page) {
                    PlaybackOptionsPage.ROOT -> PlaybackOptionsRoot(
                        statsVisible = statsVisible,
                        showSimpleTvExit = showSimpleTvExit,
                        initialFocus = initialFocus,
                        initialItem = lastRootItem,
                        onFocused = { lastRootItem = it },
                        onPageChange = onPageChange,
                        onStatsVisibleChange = onStatsVisibleChange,
                        onSimpleTvExit = onSimpleTvExit,
                    )

                    PlaybackOptionsPage.AUDIO -> TrackOptions(
                        player = player,
                        trackType = C.TRACK_TYPE_AUDIO,
                        initialFocus = initialFocus,
                        onSelected = { onPageChange(PlaybackOptionsPage.ROOT) },
                    )

                    PlaybackOptionsPage.SUBTITLES -> TrackOptions(
                        player = player,
                        trackType = C.TRACK_TYPE_TEXT,
                        initialFocus = initialFocus,
                        onSelected = { onPageChange(PlaybackOptionsPage.ROOT) },
                    )

                    PlaybackOptionsPage.DISPLAY -> DisplayOptions(
                        selected = aspectRatio,
                        initialFocus = initialFocus,
                        onSelect = onAspectRatioChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackOptionsRoot(
    statsVisible: Boolean,
    showSimpleTvExit: Boolean,
    initialFocus: FocusRequester,
    initialItem: PlaybackOptionsRootItem,
    onFocused: (PlaybackOptionsRootItem) -> Unit,
    onPageChange: (PlaybackOptionsPage) -> Unit,
    onStatsVisibleChange: (Boolean) -> Unit,
    onSimpleTvExit: () -> Unit,
) {
    LaunchedEffect(initialFocus) { initialFocus.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlaybackOptionRow(
                label = stringResource(R.string.audio_track),
                onClick = { onPageChange(PlaybackOptionsPage.AUDIO) },
                modifier = Modifier
                    .then(
                        if (initialItem == PlaybackOptionsRootItem.AUDIO) {
                            Modifier.focusRequester(initialFocus)
                        } else {
                            Modifier
                        }
                    )
                    .onFocusChanged {
                        if (it.isFocused) onFocused(PlaybackOptionsRootItem.AUDIO)
                    },
                showChevron = true,
        )
        PlaybackOptionRow(
                label = stringResource(R.string.subtitles),
                onClick = { onPageChange(PlaybackOptionsPage.SUBTITLES) },
                modifier = Modifier
                    .then(
                        if (initialItem == PlaybackOptionsRootItem.SUBTITLES) {
                            Modifier.focusRequester(initialFocus)
                        } else {
                            Modifier
                        }
                    )
                    .onFocusChanged {
                        if (it.isFocused) onFocused(PlaybackOptionsRootItem.SUBTITLES)
                    },
                showChevron = true,
        )
        PlaybackOptionRow(
                label = stringResource(R.string.display_mode),
                onClick = { onPageChange(PlaybackOptionsPage.DISPLAY) },
                modifier = Modifier
                    .then(
                        if (initialItem == PlaybackOptionsRootItem.DISPLAY) {
                            Modifier.focusRequester(initialFocus)
                        } else {
                            Modifier
                        }
                    )
                    .onFocusChanged {
                        if (it.isFocused) onFocused(PlaybackOptionsRootItem.DISPLAY)
                    },
                showChevron = true,
        )
        PlaybackOptionRow(
                label = stringResource(R.string.stats_for_nerds),
                selected = statsVisible,
                onClick = { onStatsVisibleChange(!statsVisible) },
                modifier = Modifier
                    .then(
                        if (initialItem == PlaybackOptionsRootItem.STATS) {
                            Modifier.focusRequester(initialFocus)
                        } else {
                            Modifier
                        }
                    )
                    .onFocusChanged {
                        if (it.isFocused) onFocused(PlaybackOptionsRootItem.STATS)
                    },
                showSwitch = true,
        )
        if (showSimpleTvExit) {
            Text(
                text = stringResource(R.string.simple_tv_owner_section),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            PlaybackOptionRow(
                label = stringResource(R.string.simple_tv_unlock),
                onClick = onSimpleTvExit,
                modifier = Modifier
                    .then(
                        if (initialItem == PlaybackOptionsRootItem.EXIT_SIMPLE_TV) {
                            Modifier.focusRequester(initialFocus)
                        } else {
                            Modifier
                        }
                    )
                    .onFocusChanged {
                        if (it.isFocused) onFocused(PlaybackOptionsRootItem.EXIT_SIMPLE_TV)
                    },
            )
        }
    }
}

@Composable
private fun TrackOptions(
    player: Player,
    trackType: Int,
    initialFocus: FocusRequester,
    onSelected: () -> Unit,
) {
    val unknownLanguage = stringResource(R.string.track_unknown_language)
    val mono = stringResource(R.string.track_mono)
    val stereo = stringResource(R.string.track_stereo)
    val surround51 = stringResource(R.string.track_surround_5_1)
    val surround71 = stringResource(R.string.track_surround_7_1)
    val channelsTemplate = stringResource(R.string.track_channels_count)
    val tracks = collectTracks(
        tracks = player.currentTracks,
        trackType = trackType,
        unknownLanguageLabel = unknownLanguage,
        monoLabel = mono,
        stereoLabel = stereo,
        surround51Label = surround51,
        surround71Label = surround71,
        channelsLabel = { count -> channelsTemplate.format(count) },
    )
    val subtitles = trackType == C.TRACK_TYPE_TEXT
    val selectedIndex = tracks.indexOfFirst(UiTrack::selected).takeIf { it >= 0 } ?: 0
    LaunchedEffect(initialFocus, tracks) {
        // Always request a focusable target, including the empty-state row.
        initialFocus.requestFocus()
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (subtitles) {
            item {
                PlaybackOptionRow(
                    label = stringResource(R.string.subtitles_off),
                    selected = tracks.none(UiTrack::selected),
                    onClick = {
                        selectTextTrack(player, null)
                        onSelected()
                    },
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
                onClick = {
                    if (subtitles) selectTextTrack(player, track) else selectAudioTrack(player, track)
                    onSelected()
                },
                modifier = if (
                    track.selected || (!subtitles && index == selectedIndex)
                ) {
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
    LaunchedEffect(initialFocus) { initialFocus.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
) {
    ListItem(
        selected = if (showSwitch) false else selected,
        onClick = onClick,
        headlineContent = { Text(label) },
        supportingContent = supportingLabel?.let { text ->
            { Text(text, color = Color.White.copy(alpha = 0.72f)) }
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
