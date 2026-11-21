package at.bernhardberger.tvhplayer.touch.channels

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.TouchFrame

@Composable
internal fun ChannelsScreen(
    state: ChannelsState,
    actions: ChannelsActions,
    modifier: Modifier = Modifier,
    initiallyOpenChannelId: String? = null,
) {
    val window = currentWindowAdaptiveInfoV2().windowSizeClass
    val layout = when {
        window.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> ChannelsLayout.Wide
        window.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> ChannelsLayout.Upright
        else -> ChannelsLayout.Phone
    }
    val list = when (state) {
        is ChannelsState.Ready -> state.list
        is ChannelsState.Offline -> state.list
        else -> null
    }
    TouchFrame(
        modifier = modifier,
        actions = { destination ->
            if (destination == Destination.Channels) {
                if (layout.tablet && list != null && list.canFilter) {
                    TagFilter(list, actions.onTag, Modifier.padding(end = 8.dp))
                }
                IconButton(onClick = actions.onOpenConnectionSettings, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(
                        painterResource(R.drawable.channels_settings),
                        stringResource(R.string.channels_connection_settings),
                    )
                }
            }
        },
        belowTopBar = { destination ->
            if (destination == Destination.Channels && state == ChannelsState.Loading) {
                ChannelsLoadingLine(layout)
            }
        },
    ) { destination ->
        if (destination == Destination.Channels) {
            ChannelsContent(state, layout, actions, initiallyOpenChannelId)
        }
    }
}

internal enum class ChannelsLayout {
    Phone, Upright, Wide;

    val tablet: Boolean get() = this != Phone
    val showNext: Boolean get() = this == Wide
    val margin get() = if (tablet) 24.dp else 12.dp
    val rowHeight get() = if (tablet) 76.dp else 72.dp
}

@Composable
private fun ChannelsContent(
    state: ChannelsState,
    layout: ChannelsLayout,
    actions: ChannelsActions,
    initiallyOpenChannelId: String?,
) {
    when (state) {
        ChannelsState.Loading -> ChannelsSkeleton(layout)
        ChannelsState.Unreachable -> ChannelsMessage(MessageKind.Unreachable, actions)
        ChannelsState.SignInFailed -> ChannelsMessage(MessageKind.SignInFailed, actions)
        is ChannelsState.Offline -> {
            Column(Modifier.fillMaxSize().padding(horizontal = layout.margin)) {
                OfflineBanner(layout, state.retryIn, actions.onOpenConnectionSettings)
                if (!layout.tablet && state.list.canFilter) TagFilter(state.list, actions.onTag)
                ChannelRows(state.list, layout, actions, offline = true)
            }
        }
        is ChannelsState.Ready -> {
            Column(Modifier.fillMaxSize().padding(horizontal = layout.margin)) {
                val list = state.list
                if (!layout.tablet && list.canFilter) TagFilter(list, actions.onTag)
                if (list.rows.isEmpty()) {
                    val kind = if (list.selectedTagId == null) MessageKind.Empty else MessageKind.EmptyTag
                    ChannelsMessage(kind, actions)
                } else {
                    ChannelRows(list, layout, actions, initiallyOpenChannelId = initiallyOpenChannelId)
                }
            }
        }
    }
}

private val ChannelList.canFilter: Boolean get() = rows.isNotEmpty() || selectedTagId != null

@Composable
private fun TagFilter(list: ChannelList, onTag: (String?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val all = stringResource(R.string.channels_all)
    val label = list.tags.find { it.id == list.selectedTagId }?.label ?: all
    Box(modifier) {
        FilledTonalButton(
            onClick = { open = true },
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            ChannelActionLabel(R.drawable.channels_tag, label)
            Icon(painterResource(R.drawable.channels_expand), contentDescription = null)
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
