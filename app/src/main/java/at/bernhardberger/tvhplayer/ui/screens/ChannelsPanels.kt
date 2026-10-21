package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.ui.TvScrimModalAlpha
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvTextTertiaryAlpha
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseContentLayering
import at.bernhardberger.tvhplayer.ui.components.PlaybackOptionsOverlayFrame
import at.bernhardberger.tvhplayer.ui.components.browseDetailsViewport
import at.bernhardberger.tvhplayer.ui.player.OptionsHeader
import at.bernhardberger.tvhplayer.ui.player.PlaybackListBottomFade
import at.bernhardberger.tvhplayer.ui.player.PlaybackListFade
import at.bernhardberger.tvhplayer.ui.player.PlaybackListTopFade
import at.bernhardberger.tvhplayer.ui.player.PlaybackOptionRow
import at.bernhardberger.tvhplayer.ui.player.PlaybackPanelHeaderTop
import at.bernhardberger.tvhplayer.ui.player.PlaybackPanelPadding
import at.bernhardberger.tvhplayer.ui.player.containedFocus
import at.bernhardberger.tvhplayer.ui.player.playbackListFade
import kotlinx.coroutines.flow.first

/** Which Channels side panel is open. */
internal enum class ChannelsPanel { GROUP, VIEW_OPTIONS }

/** A line of a Channels side panel: a quiet section heading, a divider, a choice or a switch. */
internal sealed interface ChannelsPanelEntry {
    data class Section(val title: String) : ChannelsPanelEntry
    /** Sets a lone row apart from the section above it, where a heading would only repeat its label. */
    data object Divider : ChannelsPanelEntry
    /** A row; [checked] makes it a switch. */
    class Choice(
        val key: String,
        val label: String,
        val selected: Boolean,
        val onClick: () -> Unit,
        val icon: Int? = null,
        val checked: Boolean? = null,
    ) : ChannelsPanelEntry
}

/**
 * The group picker: the visible groups as the kit's single-choice list, the current one checked
 * and focused. OK applies and closes.
 */
@Composable
internal fun ChannelsGroupPanel(
    groups: List<ChannelsGroup>,
    currentTagId: ChannelTagId?,
    onSelect: (ChannelTagId?) -> Unit,
    onClose: () -> Unit,
) {
    val allChannels = stringResource(R.string.all_channels)
    val entries = groups.map { group ->
        ChannelsPanelEntry.Choice(
            key = "channels-group-${group.tagId?.value ?: "all"}",
            label = group.name ?: allChannels,
            selected = group.tagId == currentTagId,
            onClick = { onSelect(group.tagId) },
        )
    }
    ChannelsSidePanel(stringResource(R.string.channels_group), "channels-group-panel", onClose) {
        ChannelsPanelList(entries, radio = false)
    }
}

/**
 * View options: the layouts, then the current layout's program art switch where it has one. Each
 * change applies at once; Back closes the panel.
 */
@Composable
internal fun ChannelsViewOptionsPanel(
    view: ChannelsView,
    onViewChange: (ChannelsView) -> Unit,
    onClose: () -> Unit,
) {
    val entries = buildList {
        add(ChannelsPanelEntry.Section(stringResource(R.string.channels_view_layout)))
        ChannelsLayout.entries.forEach { layout ->
            add(ChannelsPanelEntry.Choice(
                key = "channels-view-layout-${layout.name.lowercase()}",
                label = stringResource(layout.label()),
                selected = layout == view.layout,
                onClick = { onViewChange(view.copy(layout = layout)) },
                icon = layout.icon(),
            ))
        }
        add(ChannelsPanelEntry.Divider)
        add(ChannelsPanelEntry.Choice(
            key = "channels-view-program-art",
            label = stringResource(R.string.channels_view_program_art),
            selected = false,
            onClick = { onViewChange(view.toggledProgramArt()) },
            checked = view.showsProgramArt,
        ))
    }
    ChannelsSidePanel(stringResource(R.string.channels_view_options), "channels-view-panel", onClose) {
        ChannelsPanelList(entries, radio = true)
    }
}

private fun ChannelsLayout.label(): Int = when (this) {
    ChannelsLayout.LIST -> R.string.channels_layout_list
    ChannelsLayout.GRID -> R.string.channels_layout_grid
    ChannelsLayout.IMMERSIVE -> R.string.channels_layout_immersive
}

private fun ChannelsLayout.icon(): Int = when (this) {
    ChannelsLayout.LIST -> R.drawable.ic_view_list
    ChannelsLayout.GRID -> R.drawable.ic_grid_view
    ChannelsLayout.IMMERSIVE -> R.drawable.ic_view_carousel
}

/**
 * The floating side panel over the dimmed browse shell, the rail included. It holds focus until
 * Back closes it; the release of the key that opened it never reaches a row.
 */
@Composable
private fun ChannelsSidePanel(
    title: String,
    panelTag: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val layering = LocalBrowseContentLayering.current
    val scrimKey = remember { Any() }
    val scrim = remember { mutableFloatStateOf(TvScrimModalAlpha) }
    DisposableEffect(layering) {
        layering?.modalScrims?.set(scrimKey, scrim)
        onDispose { layering?.modalScrims?.remove(scrimKey) }
    }
    var openingReleasePending by remember { mutableStateOf(true) }
    BackHandler { onClose() }
    PlaybackOptionsOverlayFrame(
        paneTitle = title,
        panelTag = panelTag,
        modifier = Modifier
            .browseDetailsViewport()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back) {
                    if (event.type == KeyEventType.KeyUp) onClose()
                    return@onPreviewKeyEvent true
                }
                val activation = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (!activation || !openingReleasePending) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    openingReleasePending = false
                    false
                } else {
                    if (event.type == KeyEventType.KeyUp) openingReleasePending = false
                    true
                }
            },
    ) {
        Column(Modifier.fillMaxSize().padding(top = PlaybackPanelHeaderTop)) {
            OptionsHeader(title = title, currentValue = null, onBack = null)
            content()
        }
    }
}

/**
 * One renderer for every panel list; the checked choice takes focus as the panel opens. [radio]
 * marks choices with radio buttons, otherwise the checked one carries a trailing check.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelsPanelList(entries: List<ChannelsPanelEntry>, radio: Boolean) {
    val choices = entries.filterIsInstance<ChannelsPanelEntry.Choice>()
    val requesterStore = remember { mutableMapOf<String, FocusRequester>() }
    val choiceKeys = choices.map { it.key }
    val requesters = remember(choiceKeys) { choiceKeys.map { requesterStore.getOrPut(it) { FocusRequester() } } }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val target = choices.indexOfFirst { it.selected }.coerceAtLeast(0)
        val lazyIndex = entries.indexOf(choices.getOrNull(target) ?: return@LaunchedEffect)
        // Wait one frame so the row is ready to draw its focused state.
        withFrameNanos { }
        // The list opens at its top; only a checked row below the panel scrolls into view.
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == lazyIndex }) {
            listState.scrollToItem(lazyIndex)
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == lazyIndex } }.first { it }
        }
        runCatching { requesters[target].requestFocus() }
    }
    val density = LocalDensity.current
    val platformBringIntoViewSpec = LocalBringIntoViewSpec.current
    val bringIntoViewSpec = remember(platformBringIntoViewSpec, density) {
        with(density) {
            InsetBringIntoViewSpec(
                delegate = platformBringIntoViewSpec,
                topInsetPx = PlaybackListTopFade.toPx(),
                bottomInsetPx = PlaybackListBottomFade.toPx(),
            )
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(TvSpacing8),
            contentPadding = PaddingValues(
                start = PlaybackPanelPadding,
                end = PlaybackPanelPadding,
                // A section heading brings its own room below the title.
                top = 4.dp + if (entries.firstOrNull() is ChannelsPanelEntry.Choice) ChannelsPanelTitleGap else 0.dp,
                bottom = PlaybackPanelPadding,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .playbackListFade(PlaybackListFade(top = listState.canScrollBackward, bottom = listState.canScrollForward))
                .focusGroup(),
        ) {
            items(entries, key = { entry ->
                when (entry) {
                    is ChannelsPanelEntry.Section -> "section-${entry.title}"
                    ChannelsPanelEntry.Divider -> "divider"
                    is ChannelsPanelEntry.Choice -> entry.key
                }
            }) { entry ->
                when (entry) {
                    is ChannelsPanelEntry.Section -> Text(
                        entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = TvTextTertiaryAlpha),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).semantics { heading() },
                    )
                    ChannelsPanelEntry.Divider -> Box(
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().height(1.dp)
                            .background(MaterialTheme.colorScheme.borderVariant),
                    )
                    is ChannelsPanelEntry.Choice -> {
                        val index = choices.indexOf(entry)
                        PlaybackOptionRow(
                            label = entry.label,
                            selected = entry.selected,
                            radio = radio,
                            onClick = entry.onClick,
                            leadingIcon = entry.icon,
                            checked = entry.checked,
                            modifier = Modifier
                                .containedFocus(requesters[index], requesters, index, lazy = true)
                                .testTag(entry.key),
                        )
                    }
                }
            }
        }
    }
}

/** The kit drawer's room between its title and the first item. */
private val ChannelsPanelTitleGap = 16.dp
