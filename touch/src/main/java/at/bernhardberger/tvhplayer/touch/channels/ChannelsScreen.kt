package at.bernhardberger.tvhplayer.touch.channels

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.ListFailure
import at.bernhardberger.tvhplayer.touch.ui.ListMessage
import at.bernhardberger.tvhplayer.touch.ui.LocalBrowseMargin
import at.bernhardberger.tvhplayer.touch.ui.MessageKind
import at.bernhardberger.tvhplayer.touch.ui.OfflineBanner
import at.bernhardberger.tvhplayer.touch.ui.OfflineTexts
import at.bernhardberger.tvhplayer.touch.ui.TouchFrame
import kotlinx.coroutines.flow.first

@Composable
internal fun ChannelsScreen(
    state: ChannelsState,
    actions: ChannelsActions,
    modifier: Modifier = Modifier,
    destinations: List<Destination> = Destination.entries,
    lastWatchedId: String? = null,
    openChannelId: String? = null,
    layout: ChannelsLayout = channelsLayout(),
    pane: @Composable (channelId: String, modifier: Modifier) -> Unit = { _, _ -> },
) {
    val list = when (state) {
        is ChannelsState.Ready -> state.list
        is ChannelsState.Offline -> state.list
        else -> null
    }
    TouchFrame(
        modifier = modifier,
        destinations = destinations,
        lastActionIsIconOnly = true,
        actions = { destination ->
            if (destination == Destination.Channels) {
                if (list != null && list.canFilter) {
                    TagFilter(list, actions.onTag, iconOnly = layout != ChannelsLayout.Wide)
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = actions.onOpenConnectionSettings) {
                    Icon(
                        painterResource(R.drawable.channels_settings),
                        stringResource(R.string.channels_connection_settings),
                    )
                }
            }
        },
        belowTopBar = { destination ->
            if (destination == Destination.Channels) {
                ChannelsSubtitle(state, list, layout)
            }
        },
    ) { destination ->
        if (destination == Destination.Channels) {
            ChannelsContent(state, list, layout, actions, lastWatchedId, openChannelId, pane)
        }
    }
}

internal enum class ChannelsLayout {
    Phone, Upright, Wide;

    val tablet: Boolean get() = this != Phone
    val showNext: Boolean get() = this == Wide
    val rowHeight get() = if (tablet) 76.dp else 72.dp
}

@Composable
private fun ChannelsContent(
    state: ChannelsState,
    list: ChannelList?,
    layout: ChannelsLayout,
    actions: ChannelsActions,
    lastWatchedId: String?,
    openChannelId: String?,
    pane: @Composable (String, Modifier) -> Unit,
) {
    val margin = LocalBrowseMargin.current
    val scroll = rememberLazyListState()
    var savedTag by rememberSaveable { mutableStateOf(list?.selectedTagId) }
    var revealedId by rememberSaveable { mutableStateOf<String?>(null) }
    val paneChannel = list?.takeIf { layout == ChannelsLayout.Wide }?.rows?.let { rows ->
        rows.firstOrNull { it.id == openChannelId }
            ?: rows.firstOrNull { it.playback != null }
            ?: rows.firstOrNull { it.id == lastWatchedId }
            ?: rows.firstOrNull()
    }
    SideEffect {
        if (list != null && savedTag != list.selectedTagId) {
            scroll.requestScrollToItem(0)
            savedTag = list.selectedTagId
            revealedId = null
        }
    }
    LaunchedEffect(paneChannel?.id, openChannelId, list?.selectedTagId) {
        if (list == null || paneChannel == null) return@LaunchedEffect
        if (paneChannel.id == openChannelId || paneChannel.id == revealedId) return@LaunchedEffect
        // Let the initial list lay out before deciding whether the automatically chosen row is visible.
        val layoutInfo = snapshotFlow { scroll.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
        if (layoutInfo.visibleItemsInfo.none { it.key == paneChannel.id }) {
            scroll.requestScrollToItem(list.rows.indexOf(paneChannel))
        }
        revealedId = paneChannel.id
    }
    if (list != null) {
        BrowseChannels(list, layout, actions, state as? ChannelsState.Offline, scroll, paneChannel, pane)
    } else {
        ChannelsLoadContent(state, layout, actions, margin)
    }
}

@Composable
private fun ChannelsLoadContent(state: ChannelsState, layout: ChannelsLayout, actions: ChannelsActions, margin: Dp) {
    when (state) {
        ChannelsState.Loading -> ChannelsSkeleton(layout, margin)
        ChannelsState.Unreachable -> ChannelsMessage(MessageKind.Unreachable, actions)
        ChannelsState.SignInFailed -> ChannelsMessage(MessageKind.SignInFailed, actions)
        ChannelsState.SignInNeeded -> ChannelsMessage(MessageKind.SignInNeeded, actions)
        is ChannelsState.Failed -> ListFailure(state.error, actions.onRetry, actions.onOpenConnectionSettings)
        is ChannelsState.Offline, is ChannelsState.Ready -> Unit
    }
}

@Composable
private fun BrowseChannels(
    list: ChannelList,
    layout: ChannelsLayout,
    actions: ChannelsActions,
    offline: ChannelsState.Offline?,
    scroll: LazyListState,
    paneChannel: ChannelRow?,
    pane: @Composable (String, Modifier) -> Unit,
) {
    val margin = LocalBrowseMargin.current
    Column(Modifier.fillMaxSize().padding(horizontal = margin)) {
        if (offline != null) {
            OfflineBanner(
                !layout.tablet, ChannelsOfflineTexts, actions.onOpenConnectionSettings,
                modifier = Modifier.padding(bottom = 8.dp), retryIn = offline.retryIn,
            )
        }
        when {
            list.rows.isEmpty() -> {
                val kind = if (list.selectedTagId == null) MessageKind.Empty else MessageKind.EmptyTag
                ChannelsMessage(kind, actions, horizontalMargin = if (layout.tablet) margin else 0.dp)
            }
            paneChannel == null -> BrowseChannelRows(list, actions, scroll, offline != null)
            else -> ChannelsPanes(
                listPane = { modifier ->
                    BrowseChannelRows(list, actions, scroll, offline != null, modifier, paneChannel.id)
                },
                detailPane = { modifier -> pane(paneChannel.id, modifier) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun ChannelsPanes(
    listPane: @Composable (Modifier) -> Unit,
    detailPane: @Composable (Modifier) -> Unit,
) {
    ListDetailPaneScaffold(
        directive = ChannelsPaneDirective,
        value = ChannelsPaneValue,
        modifier = Modifier.fillMaxSize(),
        listPane = { listPane(Modifier.preferredWidth(480.dp)) },
        detailPane = {
            Surface(
                Modifier.preferredWidth(240.dp).fillMaxSize().padding(bottom = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                detailPane(Modifier.fillMaxSize())
            }
        },
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val ChannelsPaneDirective = PaneScaffoldDirective.Default.copy(
    maxHorizontalPartitions = 2, horizontalPartitionSpacerSize = 24.dp,
)
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val ChannelsPaneValue = ThreePaneScaffoldValue(
    primary = PaneAdaptedValue.Expanded,
    secondary = PaneAdaptedValue.Expanded,
    tertiary = PaneAdaptedValue.Hidden,
)

@Composable
private fun ChannelsSubtitle(state: ChannelsState, list: ChannelList?, layout: ChannelsLayout) {
    if (state == ChannelsState.Loading) {
        ChannelsLoadingLine(layout, LocalBrowseMargin.current)
    } else if (list != null && layout != ChannelsLayout.Wide) {
        ChannelsCount(list)
    }
}

@Composable
private fun ChannelsCount(list: ChannelList) {
    val count = pluralStringResource(R.plurals.channels_count, list.rows.size, list.rows.size)
    val tag = list.selectedTagLabel
    Text(
        if (tag == null) count else stringResource(R.string.channels_count_with_tag, count, tag),
        Modifier.padding(horizontal = LocalBrowseMargin.current).padding(top = 4.dp, bottom = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The shared message form with Channels' actions; sign-in needed opens the sign-in editor when there is one. */
@Composable
private fun ChannelsMessage(
    kind: MessageKind,
    actions: ChannelsActions,
    horizontalMargin: Dp = LocalBrowseMargin.current,
) {
    val settings = if (kind == MessageKind.SignInNeeded) {
        actions.onSignIn ?: actions.onOpenConnectionSettings
    } else {
        actions.onOpenConnectionSettings
    }
    ListMessage(kind, settings, actions.onRetry, horizontalMargin) { actions.onTag(null) }
}

private val ChannelsOfflineTexts = OfflineTexts(R.string.channels_offline, R.string.channels_saved)

private val ChannelList.canFilter: Boolean get() = rows.isNotEmpty() || selectedTagId != null
private val ChannelList.selectedTagLabel: String? get() = tags.find { it.id == selectedTagId }?.label

@Composable
internal fun TagFilter(
    list: ChannelList,
    onTag: (String?) -> Unit,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val all = stringResource(R.string.channels_all)
    val label = list.selectedTagLabel ?: all
    Box(modifier) {
        if (iconOnly) {
            IconButton(onClick = { open = true }) {
                Icon(painterResource(R.drawable.channels_filter), stringResource(R.string.channels_filter, label))
            }
        } else {
            FilledTonalButton(onClick = { open = true }) {
                Icon(painterResource(R.drawable.channels_tag), null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(label, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(painterResource(R.drawable.channels_expand), null, Modifier.testTag("channel-tag-chevron"))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TagOption(all, list.selectedTagId == null) {
                open = false
                onTag(null)
            }
            list.tags.forEach { tag ->
                TagOption(tag.label, tag.id == list.selectedTagId) {
                    open = false
                    onTag(tag.id)
                }
            }
        }
    }
}

@Composable
private fun TagOption(label: String, current: Boolean, choose: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = choose,
        modifier = Modifier.fillMaxWidth().semantics { selected = current },
        trailingIcon = if (current) {
            { Icon(painterResource(R.drawable.channels_check), contentDescription = null) }
        } else {
            null
        },
    )
}

@Composable
internal fun ChannelLine(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
