package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
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
import at.bernhardberger.tvhplayer.core.PlaybackTrackContentState
import at.bernhardberger.tvhplayer.core.PlaybackTrackFocusTarget
import at.bernhardberger.tvhplayer.core.playbackOptionsCategories
import at.bernhardberger.tvhplayer.core.playbackTrackContentState
import at.bernhardberger.tvhplayer.core.playbackTrackFocusTarget
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.ui.TvPanelDenseAlpha
import at.bernhardberger.tvhplayer.ui.TvScrimModalAlpha
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing24
import at.bernhardberger.tvhplayer.ui.TvSpacing32
import at.bernhardberger.tvhplayer.ui.TvSpacing4
import at.bernhardberger.tvhplayer.ui.TvSpacing48
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvTextTertiaryAlpha
import kotlinx.coroutines.flow.first

private val PlaybackOptionsWidth = 420.dp
private val PlaybackOptionsMaxHeight = 420.dp
private val PlaybackOptionsBottomAnchor = 108.dp
private val PlaybackOptionsTrackListMaxHeight = 236.dp

internal data class PlaybackOptionTrack(
    val key: String,
    val label: String,
    val supportingLabel: String? = null,
    val selected: Boolean = false,
)

/**
 * Compact playback-dependent overlay with a structured root and detail pages.
 */
@Composable
internal fun PlaybackOptionsSheet(
    page: PlaybackOptionsPage,
    player: Player,
    tracksResolving: Boolean,
    aspectRatio: AspectRatioMode,
    statsVisible: Boolean,
    showSimpleTvExit: Boolean,
    simpleTvActive: Boolean = false,
    onPageChange: (PlaybackOptionsPage) -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onStatsVisibleChange: (Boolean) -> Unit,
    onSimpleTvExit: () -> Unit,
) {
    val unknownLanguage = stringResource(R.string.track_unknown_language)
    val mono = stringResource(R.string.track_mono)
    val stereo = stringResource(R.string.track_stereo)
    val surround51 = stringResource(R.string.track_surround_5_1)
    val surround71 = stringResource(R.string.track_surround_7_1)
    val channelsTemplate = stringResource(R.string.track_channels_count)
    val currentTracks = rememberPlayerTracks(player)
    val audioChoices = remember(
        currentTracks,
        unknownLanguage,
        mono,
        stereo,
        surround51,
        surround71,
        channelsTemplate,
    ) {
        collectTracks(
            tracks = currentTracks,
            trackType = C.TRACK_TYPE_AUDIO,
            unknownLanguageLabel = unknownLanguage,
            monoLabel = mono,
            stereoLabel = stereo,
            surround51Label = surround51,
            surround71Label = surround71,
            channelsLabel = { count -> channelsTemplate.format(count) },
        )
    }
    val subtitleChoices = remember(
        currentTracks,
        unknownLanguage,
        mono,
        stereo,
        surround51,
        surround71,
        channelsTemplate,
    ) {
        collectTracks(
            tracks = currentTracks,
            trackType = C.TRACK_TYPE_TEXT,
            unknownLanguageLabel = unknownLanguage,
            monoLabel = mono,
            stereoLabel = stereo,
            surround51Label = surround51,
            surround71Label = surround71,
            channelsLabel = { count -> channelsTemplate.format(count) },
        )
    }
    val audioTracks = remember(audioChoices) { audioChoices.map(UiTrack::toPlaybackOptionTrack) }
    val subtitleTracks = remember(subtitleChoices) {
        subtitleChoices.map(UiTrack::toPlaybackOptionTrack)
    }

    PlaybackOptionsSheetContent(
        page = page,
        audioTracks = audioTracks,
        subtitleTracks = subtitleTracks,
        tracksResolving = tracksResolving,
        aspectRatio = aspectRatio,
        statsVisible = statsVisible,
        showSimpleTvExit = showSimpleTvExit,
        simpleTvActive = simpleTvActive,
        onPageChange = onPageChange,
        onAudioTrackSelected = { key ->
            audioChoices.firstOrNull { it.stableKey == key }?.let { selectAudioTrack(player, it) }
        },
        onSubtitleTrackSelected = { key ->
            selectTextTrack(
                player = player,
                choice = key?.let { selectedKey ->
                    subtitleChoices.firstOrNull { it.stableKey == selectedKey }
                },
            )
        },
        onAspectRatioChange = onAspectRatioChange,
        onStatsVisibleChange = onStatsVisibleChange,
        onSimpleTvExit = onSimpleTvExit,
    )
}

@Composable
internal fun rememberPlayerTracks(player: Player): Tracks {
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        tracks = player.currentTracks
        val listener = object : Player.Listener {
            override fun onTracksChanged(updatedTracks: Tracks) {
                tracks = updatedTracks
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return tracks
}

@Composable
internal fun PlaybackOptionsSheetContent(
    page: PlaybackOptionsPage,
    audioTracks: List<PlaybackOptionTrack>,
    subtitleTracks: List<PlaybackOptionTrack>,
    tracksResolving: Boolean,
    aspectRatio: AspectRatioMode,
    statsVisible: Boolean,
    showSimpleTvExit: Boolean,
    simpleTvActive: Boolean,
    onPageChange: (PlaybackOptionsPage) -> Unit,
    onAudioTrackSelected: (String) -> Unit,
    onSubtitleTrackSelected: (String?) -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onStatsVisibleChange: (Boolean) -> Unit,
    onSimpleTvExit: () -> Unit,
) {
    var lastRootPage by remember { mutableStateOf(PlaybackOptionsPage.AUDIO) }
    val availableRootPages = playbackOptionsCategories(simpleTvActive)
    val rootRestorePage = lastRootPage.takeIf(availableRootPages::contains)
        ?: availableRootPages.first()
    val loadingLabel = stringResource(R.string.playback_tracks_loading)
    val noAudioLabel = stringResource(R.string.no_audio_tracks)
    val subtitlesOffLabel = stringResource(R.string.subtitles_off)
    val audioState = playbackTrackContentState(audioTracks.size, tracksResolving)
    val subtitleState = playbackTrackContentState(subtitleTracks.size, tracksResolving)
    val audioValue = audioTracks.firstOrNull(PlaybackOptionTrack::selected)?.label
        ?: when (audioState) {
            PlaybackTrackContentState.LOADING -> loadingLabel
            PlaybackTrackContentState.AVAILABLE ->
                stringResource(R.string.audio_track_unselected)
            PlaybackTrackContentState.EMPTY -> noAudioLabel
        }
    val subtitlesValue = subtitleTracks.firstOrNull(PlaybackOptionTrack::selected)?.label
        ?: if (subtitleState == PlaybackTrackContentState.LOADING) {
            loadingLabel
        } else {
            subtitlesOffLabel
        }
    val displayValue = stringResource(
        when (aspectRatio) {
            AspectRatioMode.FIT -> R.string.display_mode_auto
            AspectRatioMode.FORCE_16_9 -> R.string.display_mode_16_9
            AspectRatioMode.FORCE_4_3 -> R.string.display_mode_4_3
        }
    )
    val statsValue = stringResource(if (statsVisible) R.string.stats_on else R.string.stats_off)
    val sheetTitle = stringResource(
        when (page) {
            PlaybackOptionsPage.ROOT -> R.string.playback_options
            PlaybackOptionsPage.AUDIO -> R.string.audio_track
            PlaybackOptionsPage.SUBTITLES -> R.string.subtitles
            PlaybackOptionsPage.DISPLAY -> R.string.display_mode
            PlaybackOptionsPage.STATS -> R.string.stats_for_nerds
        }
    )

    LaunchedEffect(page) {
        if (page != PlaybackOptionsPage.ROOT && availableRootPages.contains(page)) {
            lastRootPage = page
        }
    }

    fun openPage(target: PlaybackOptionsPage) {
        if (target != PlaybackOptionsPage.ROOT && availableRootPages.contains(target)) {
            lastRootPage = target
        }
        onPageChange(target)
    }

    PlaybackOptionsOverlayFrame(paneTitle = sheetTitle) {
        Column(
            modifier = Modifier.padding(horizontal = TvSpacing24, vertical = TvSpacing16),
            verticalArrangement = Arrangement.spacedBy(TvSpacing12),
        ) {
            when (page) {
                PlaybackOptionsPage.ROOT -> PlaybackOptionsRoot(
                    audioValue = audioValue,
                    subtitlesValue = subtitlesValue,
                    displayValue = displayValue,
                    statsVisible = statsVisible,
                    initialPage = rootRestorePage,
                    simpleTvActive = simpleTvActive,
                    showSimpleTvExit = showSimpleTvExit,
                    onPageChange = ::openPage,
                    onStatsVisibleChange = onStatsVisibleChange,
                    onSimpleTvExit = onSimpleTvExit,
                )
                PlaybackOptionsPage.AUDIO -> TrackOptionsPage(
                    title = stringResource(R.string.audio_track),
                    currentValue = audioValue,
                    tracks = audioTracks,
                    contentState = audioState,
                    unavailableLabel = noAudioLabel,
                    loadingLabel = loadingLabel,
                    subtitles = false,
                    onBack = { onPageChange(PlaybackOptionsPage.ROOT) },
                    onSelect = onAudioTrackSelected,
                )
                PlaybackOptionsPage.SUBTITLES -> TrackOptionsPage(
                    title = stringResource(R.string.subtitles),
                    currentValue = subtitlesValue,
                    tracks = subtitleTracks,
                    contentState = subtitleState,
                    unavailableLabel = stringResource(R.string.no_subtitles),
                    loadingLabel = loadingLabel,
                    subtitles = true,
                    onBack = { onPageChange(PlaybackOptionsPage.ROOT) },
                    onSelectOff = { onSubtitleTrackSelected(null) },
                    onSelect = onSubtitleTrackSelected,
                )
                PlaybackOptionsPage.DISPLAY -> DisplayOptionsPage(
                    selected = aspectRatio,
                    currentValue = displayValue,
                    onBack = { onPageChange(PlaybackOptionsPage.ROOT) },
                    onSelect = onAspectRatioChange,
                )
                PlaybackOptionsPage.STATS -> StatsOptionsPage(
                    selected = statsVisible,
                    currentValue = statsValue,
                    onBack = { onPageChange(PlaybackOptionsPage.ROOT) },
                    onSelectedChange = onStatsVisibleChange,
                )
            }
        }
    }
}

@Composable
internal fun PlaybackOptionsOverlayFrame(
    modifier: Modifier = Modifier,
    paneTitle: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = TvScrimModalAlpha))
            .focusGroup(),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = TvSpacing32,
                    end = TvSpacing48,
                    bottom = PlaybackOptionsBottomAnchor,
                ),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Surface(
                modifier = Modifier
                    .width(PlaybackOptionsWidth)
                    .heightIn(max = PlaybackOptionsMaxHeight)
                    .testTag("playback-options-overlay")
                    .semantics {
                        dialog()
                        paneTitle?.let { this.paneTitle = it }
                    },
                shape = MaterialTheme.shapes.large,
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(
                        alpha = TvPanelDenseAlpha
                    ),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                content()
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
    initialPage: PlaybackOptionsPage,
    simpleTvActive: Boolean,
    showSimpleTvExit: Boolean,
    onPageChange: (PlaybackOptionsPage) -> Unit,
    onStatsVisibleChange: (Boolean) -> Unit,
    onSimpleTvExit: () -> Unit,
) {
    val audioFocus = remember { FocusRequester() }
    val subtitlesFocus = remember { FocusRequester() }
    val displayFocus = remember { FocusRequester() }
    val statsFocus = remember { FocusRequester() }
    val exitFocus = remember { FocusRequester() }
    val requesters = buildList {
        add(PlaybackOptionsPage.AUDIO to audioFocus)
        add(PlaybackOptionsPage.SUBTITLES to subtitlesFocus)
        if (!simpleTvActive) {
            add(PlaybackOptionsPage.DISPLAY to displayFocus)
            add(PlaybackOptionsPage.STATS to statsFocus)
        }
    }
    val orderedFocus = requesters.map(Pair<PlaybackOptionsPage, FocusRequester>::second) +
        if (showSimpleTvExit) listOf(exitFocus) else emptyList()
    val initialFocus = requesters.firstOrNull { it.first == initialPage }?.second ?: audioFocus

    LaunchedEffect(initialPage, simpleTvActive, showSimpleTvExit) {
        runCatching { initialFocus.requestFocus() }
    }

    OptionsHeader(
        title = stringResource(R.string.playback_options),
        currentValue = null,
        onBack = null,
    )
    Column(
        modifier = Modifier.fillMaxWidth().focusGroup(),
        verticalArrangement = Arrangement.spacedBy(TvSpacing8),
    ) {
        PlaybackOptionRow(
            label = stringResource(R.string.audio_track),
            supportingLabel = audioValue,
            onClick = { onPageChange(PlaybackOptionsPage.AUDIO) },
            showChevron = true,
            modifier = Modifier
                .containedFocus(audioFocus, orderedFocus, index = 0)
                .testTag("playback-options-audio"),
        )
        PlaybackOptionRow(
            label = stringResource(R.string.subtitles),
            supportingLabel = subtitlesValue,
            onClick = { onPageChange(PlaybackOptionsPage.SUBTITLES) },
            showChevron = true,
            modifier = Modifier
                .containedFocus(subtitlesFocus, orderedFocus, index = 1)
                .testTag("playback-options-subtitles"),
        )
        if (!simpleTvActive) {
            PlaybackOptionRow(
                label = stringResource(R.string.display_mode),
                supportingLabel = displayValue,
                onClick = { onPageChange(PlaybackOptionsPage.DISPLAY) },
                showChevron = true,
                modifier = Modifier
                    .containedFocus(displayFocus, orderedFocus, index = 2)
                    .testTag("playback-options-display"),
            )
            PlaybackOptionRow(
                label = stringResource(R.string.stats_for_nerds),
                selected = statsVisible,
                onClick = { onStatsVisibleChange(!statsVisible) },
                showSwitch = true,
                modifier = Modifier
                    .containedFocus(statsFocus, orderedFocus, index = 3)
                    .testTag("playback-options-stats"),
            )
        }
        if (showSimpleTvExit) {
            Text(
                text = stringResource(R.string.simple_tv_owner_section),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = TvTextTertiaryAlpha),
                modifier = Modifier
                    .padding(top = TvSpacing8)
                    .semantics { heading() },
            )
            PlaybackOptionRow(
                label = stringResource(R.string.simple_tv_unlock),
                onClick = onSimpleTvExit,
                leadingLock = true,
                modifier = Modifier
                    .containedFocus(
                        requester = exitFocus,
                        orderedFocus = orderedFocus,
                        index = orderedFocus.lastIndex,
                    )
                    .testTag("playback-options-simple-tv-exit"),
            )
        }
    }
}

@Composable
private fun TrackOptionsPage(
    title: String,
    currentValue: String,
    tracks: List<PlaybackOptionTrack>,
    contentState: PlaybackTrackContentState,
    unavailableLabel: String,
    loadingLabel: String,
    subtitles: Boolean,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onSelectOff: (() -> Unit)? = null,
) {
    val headerBackFocus = remember { FocusRequester() }
    val offFocus = remember { FocusRequester() }
    val requesterStore = remember { mutableMapOf<String, FocusRequester>() }
    val trackRequesters = tracks.associate { track ->
        track.key to requesterStore.getOrPut(track.key) { FocusRequester() }
    }
    val selectedKey = tracks.firstOrNull(PlaybackOptionTrack::selected)?.key
    val focusTarget = playbackTrackFocusTarget(
        trackKeys = tracks.map(PlaybackOptionTrack::key),
        selectedTrackKey = selectedKey,
        subtitles = subtitles,
    )
    val focusableRequesters = buildList {
        if (subtitles) add(offFocus)
        tracks.forEach { track -> add(requireNotNull(trackRequesters[track.key])) }
    }
    val initialContentFocus = when (focusTarget) {
        PlaybackTrackFocusTarget.HeaderBack -> null
        PlaybackTrackFocusTarget.SubtitlesOff -> offFocus
        is PlaybackTrackFocusTarget.Track -> trackRequesters[focusTarget.key]
    }
    val listState = rememberLazyListState()

    LaunchedEffect(focusTarget, contentState) {
        when (focusTarget) {
            PlaybackTrackFocusTarget.HeaderBack -> runCatching {
                headerBackFocus.requestFocus()
            }
            PlaybackTrackFocusTarget.SubtitlesOff -> {
                listState.scrollToItem(0)
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == 0 } }
                    .first { it }
                runCatching { offFocus.requestFocus() }
            }
            is PlaybackTrackFocusTarget.Track -> {
                listState.scrollToItem(focusTarget.lazyIndex)
                snapshotFlow {
                    listState.layoutInfo.visibleItemsInfo.any { it.index == focusTarget.lazyIndex }
                }.first { it }
                runCatching { trackRequesters[focusTarget.key]?.requestFocus() }
            }
        }
    }

    OptionsHeader(
        title = title,
        currentValue = currentValue,
        onBack = onBack,
        backFocusRequester = headerBackFocus,
        downFocusRequester = initialContentFocus,
    )
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(TvSpacing8),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = PlaybackOptionsTrackListMaxHeight)
            .focusGroup(),
    ) {
        if (subtitles && onSelectOff != null) {
            item(key = "subtitles-off") {
                PlaybackOptionRow(
                    label = stringResource(R.string.subtitles_off),
                    selected = contentState != PlaybackTrackContentState.LOADING &&
                        tracks.none(PlaybackOptionTrack::selected),
                    onClick = onSelectOff,
                    modifier = Modifier
                        .containedFocus(offFocus, focusableRequesters, index = 0, headerBackFocus)
                        .testTag("playback-options-subtitles-off"),
                )
            }
        }
        itemsIndexed(
            items = tracks,
            key = { _, track -> track.key },
        ) { index, track ->
            val focusIndex = index + if (subtitles) 1 else 0
            PlaybackOptionRow(
                label = track.label,
                supportingLabel = track.distinguishingSupportingLabel,
                supportingTestTag = "playback-options-track-support-${track.key}",
                selected = track.selected,
                onClick = { onSelect(track.key) },
                modifier = Modifier
                    .containedFocus(
                        requester = requireNotNull(trackRequesters[track.key]),
                        orderedFocus = focusableRequesters,
                        index = focusIndex,
                        headerFocus = headerBackFocus,
                    )
                    .testTag("playback-options-track-${track.key}"),
            )
        }
        if (contentState != PlaybackTrackContentState.AVAILABLE) {
            item(key = "track-status") {
                Text(
                    text = if (contentState == PlaybackTrackContentState.LOADING) {
                        loadingLabel
                    } else {
                        unavailableLabel
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = TvTextTertiaryAlpha),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TvSpacing16, vertical = TvSpacing12)
                        .testTag(
                            if (contentState == PlaybackTrackContentState.LOADING) {
                                "playback-options-track-loading"
                            } else {
                                "playback-options-track-empty"
                            }
                        ),
                )
            }
        }
    }
}

@Composable
private fun DisplayOptionsPage(
    selected: AspectRatioMode,
    currentValue: String,
    onBack: () -> Unit,
    onSelect: (AspectRatioMode) -> Unit,
) {
    val headerBackFocus = remember { FocusRequester() }
    val fitFocus = remember { FocusRequester() }
    val wideFocus = remember { FocusRequester() }
    val standardFocus = remember { FocusRequester() }
    val requesters = listOf(fitFocus, wideFocus, standardFocus)
    val initialFocus = when (selected) {
        AspectRatioMode.FIT -> fitFocus
        AspectRatioMode.FORCE_16_9 -> wideFocus
        AspectRatioMode.FORCE_4_3 -> standardFocus
    }

    LaunchedEffect(selected) { runCatching { initialFocus.requestFocus() } }
    OptionsHeader(
        title = stringResource(R.string.display_mode),
        currentValue = currentValue,
        onBack = onBack,
        backFocusRequester = headerBackFocus,
        downFocusRequester = initialFocus,
    )
    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing8)) {
        PlaybackOptionRow(
            label = stringResource(R.string.display_mode_auto),
            selected = selected == AspectRatioMode.FIT,
            onClick = { onSelect(AspectRatioMode.FIT) },
            modifier = Modifier.containedFocus(fitFocus, requesters, 0, headerBackFocus),
        )
        PlaybackOptionRow(
            label = stringResource(R.string.display_mode_16_9),
            selected = selected == AspectRatioMode.FORCE_16_9,
            onClick = { onSelect(AspectRatioMode.FORCE_16_9) },
            modifier = Modifier.containedFocus(wideFocus, requesters, 1, headerBackFocus),
        )
        PlaybackOptionRow(
            label = stringResource(R.string.display_mode_4_3),
            selected = selected == AspectRatioMode.FORCE_4_3,
            onClick = { onSelect(AspectRatioMode.FORCE_4_3) },
            modifier = Modifier.containedFocus(standardFocus, requesters, 2, headerBackFocus),
        )
    }
}

@Composable
private fun StatsOptionsPage(
    selected: Boolean,
    currentValue: String,
    onBack: () -> Unit,
    onSelectedChange: (Boolean) -> Unit,
) {
    val headerBackFocus = remember { FocusRequester() }
    val statsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { statsFocus.requestFocus() } }
    OptionsHeader(
        title = stringResource(R.string.stats_for_nerds),
        currentValue = currentValue,
        onBack = onBack,
        backFocusRequester = headerBackFocus,
        downFocusRequester = statsFocus,
    )
    PlaybackOptionRow(
        label = stringResource(R.string.stats_for_nerds),
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        showSwitch = true,
        modifier = Modifier.containedFocus(
            requester = statsFocus,
            orderedFocus = listOf(statsFocus),
            index = 0,
            headerFocus = headerBackFocus,
        ),
    )
}

@Composable
private fun OptionsHeader(
    title: String,
    currentValue: String?,
    onBack: (() -> Unit)?,
    backFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing12),
    ) {
        if (onBack != null && backFocusRequester != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .focusRequester(backFocusRequester)
                    .focusProperties {
                        up = FocusRequester.Cancel
                        down = downFocusRequester ?: FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
                    .testTag("playback-options-header-back"),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.back),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .testTag("playback-options-title")
                    .semantics { heading() },
            )
            currentValue?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = TvTextTertiaryAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlaybackOptionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingLabel: String? = null,
    supportingTestTag: String? = null,
    selected: Boolean = false,
    showChevron: Boolean = false,
    showSwitch: Boolean = false,
    leadingLock: Boolean = false,
) {
    ListItem(
        selected = if (showSwitch) false else selected,
        onClick = onClick,
        headlineContent = {
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = supportingLabel?.let { text ->
            {
                Text(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = supportingTestTag?.let(Modifier::testTag) ?: Modifier,
                )
            }
        },
        leadingContent = if (leadingLock) {
            {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.padding(end = TvSpacing4),
                )
            }
        } else {
            null
        },
        trailingContent = {
            when {
                showSwitch -> Switch(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier.clearAndSetSemantics { },
                )
                showChevron -> Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
                selected -> Icon(Icons.Filled.Check, contentDescription = null)
            }
        },
        scale = ListItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (showSwitch) {
                    Modifier.semantics {
                        role = Role.Switch
                        toggleableState = ToggleableState(selected)
                    }
                } else {
                    Modifier
                }
            ),
    )
}

private fun Modifier.containedFocus(
    requester: FocusRequester,
    orderedFocus: List<FocusRequester>,
    index: Int,
    headerFocus: FocusRequester? = null,
): Modifier = focusRequester(requester).focusProperties {
    up = orderedFocus.getOrNull(index - 1) ?: headerFocus ?: FocusRequester.Cancel
    down = orderedFocus.getOrNull(index + 1) ?: FocusRequester.Cancel
    left = FocusRequester.Cancel
    right = FocusRequester.Cancel
}

@get:UnstableApi
private val UiTrack.stableKey: String
    get() {
        val format = group.getTrackFormat(trackIndexInGroup)
        val formatIdentity = format.id?.takeIf(String::isNotBlank)
            ?: "index-$trackIndexInGroup"
        return listOf(
            group.type.toString(),
            group.mediaTrackGroup.id,
            formatIdentity,
        ).joinToString(":")
    }

private fun UiTrack.toPlaybackOptionTrack(): PlaybackOptionTrack = PlaybackOptionTrack(
    key = stableKey,
    label = label,
    supportingLabel = secondaryLabel,
    selected = selected,
)

private val PlaybackOptionTrack.distinguishingSupportingLabel: String?
    get() {
        val distinguishingSuffix = label.takeIf { it.length > 40 }?.takeLast(32)
        return listOfNotNull(distinguishingSuffix, supportingLabel)
            .distinct()
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString(" • ")
    }
