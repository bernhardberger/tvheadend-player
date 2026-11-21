// Sizes are the drawings' (round 37).
@file:Suppress("MagicNumber", "TooManyFunctions")

package at.bernhardberger.tvhplayer.touch.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelActionLabel
import at.bernhardberger.tvhplayer.touch.ui.ChannelLogoTile
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.TouchFrame

/**
 * Section 7's Guide in the frame's Guide destination: the overview and one channel's day. A tap
 * opens a program in place and never plays; Details opens section 9's page, which Back closes.
 */
@Composable
internal fun GuideScreen(
    state: GuideState,
    actions: GuideActions,
    modifier: Modifier = Modifier,
    initialView: GuideView = GuideView.Overview,
    initialChannelId: String? = null,
    initiallyOpen: String? = null,
    initialDetails: String? = null,
) {
    val layout = guideLayout()
    var view by rememberSaveable { mutableStateOf(initialView) }
    var channelId by rememberSaveable { mutableStateOf(initialChannelId) }
    var openId by rememberSaveable { mutableStateOf(initiallyOpen) }
    var detailsId by rememberSaveable { mutableStateOf(initialDetails) }
    val details = detailsId?.let(state.details::get)
    val page = details != null
    val openDetails: (String) -> Unit = {
        detailsId = it
        actions.onDetails(it)
    }

    TouchFrame(
        modifier = modifier,
        initialDestination = Destination.Guide,
        showTopBar = { !(it == Destination.Guide && page) },
        showNavigation = { !(it == Destination.Guide && page && layout.compact) },
        actions = { destination -> if (destination == Destination.Guide) TagFilter(state, actions) },
        belowTopBar = { destination ->
            if (destination == Destination.Guide) {
                ViewTabs(view, layout) {
                    view = it
                    openId = null
                }
            }
        },
    ) { destination ->
        if (destination != Destination.Guide) return@TouchFrame
        // Registered after the frame's own handler, so the page and an opened program take Back first.
        BackHandler(enabled = page) { detailsId = null }
        BackHandler(enabled = !page && openId != null) { openId = null }
        if (details != null) {
            ProgramDetailsPage(details, layout, actions) { detailsId = null }
            return@TouchFrame
        }
        val onOpen: (String?) -> Unit = { openId = it }
        when (view) {
            GuideView.Overview -> Overview(state, layout, openId, onOpen, actions, openDetails) {
                channelId = it
                openId = null
                view = GuideView.ByChannel
            }
            GuideView.ByChannel -> ByChannel(
                state, layout, channelId ?: state.channels.firstOrNull()?.id, openId, onOpen, actions, openDetails,
            ) {
                channelId = it
                openId = null
            }
        }
    }
}

/** The tag filter as on Channels: "All channels" first, then the tags. */
@Composable
private fun TagFilter(state: GuideState, actions: GuideActions) {
    val options = listOf(stringResource(R.string.channels_all)) + state.tags.map { it.label }
    val selected = state.tags.indexOfFirst { it.id == state.selectedTagId } + 1
    MenuButton(
        R.drawable.channels_tag, options, selected,
        onSelect = { actions.onTag(if (it == 0) null else state.tags[it - 1].id) },
        modifier = Modifier.padding(end = 12.dp),
    )
}

@Composable
private fun ViewTabs(view: GuideView, layout: GuideLayout, onView: (GuideView) -> Unit) {
    val tabs: @Composable () -> Unit = {
        GuideView.entries.forEach {
            Tab(
                selected = it == view, onClick = { onView(it) },
                text = { Text(stringResource(it.label), maxLines = 1) },
            )
        }
    }
    if (layout.compact) {
        PrimaryTabRow(selectedTabIndex = view.ordinal) { tabs() }
    } else {
        PrimaryScrollableTabRow(
            selectedTabIndex = view.ordinal, edgePadding = if (layout == GuideLayout.Wide) 8.dp else 24.dp,
        ) { tabs() }
    }
}

@Composable
private fun Overview(
    state: GuideState,
    layout: GuideLayout,
    openId: String?,
    onOpen: (String?) -> Unit,
    actions: GuideActions,
    onDetails: (String) -> Unit,
    onChannel: (String) -> Unit,
) {
    val margin = margin(layout)
    Column(Modifier.fillMaxSize()) {
        OverviewBar(
            state, actions, layout.compact,
            Modifier.padding(start = margin, end = margin, top = 12.dp, bottom = 12.dp),
        )
        if (layout.compact) {
            val padding = PaddingValues(start = margin, end = margin, bottom = 16.dp)
            OverviewRows(state.channels, openId, onOpen, actions, onDetails, padding)
        } else {
            val padding = PaddingValues(horizontal = margin)
            OverviewColumns(state.channels, layout, openId, onOpen, onChannel, actions, onDetails, padding)
        }
    }
}

private fun margin(layout: GuideLayout) = if (layout.compact) 12.dp else 24.dp

@Composable
private fun ByChannel(
    state: GuideState,
    layout: GuideLayout,
    channelId: String?,
    openId: String?,
    onOpen: (String?) -> Unit,
    actions: GuideActions,
    onDetails: (String) -> Unit,
    onChannel: (String) -> Unit,
) {
    val channel = state.channels.find { it.id == channelId } ?: return
    if (layout.compact) {
        Column(Modifier.fillMaxSize()) {
            ChannelTabs(state.channels, channel.id, onChannel)
            DayBar(state.days, state.selectedDay, state.date, actions.onDay)
            Day(channel, layout, openId, onOpen, actions, onDetails, Modifier.padding(horizontal = 12.dp))
        }
        return
    }
    Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 8.dp)) {
        ChannelList(
            state.channels, channel.id, onChannel,
            if (layout == GuideLayout.Wide) Modifier.width(344.dp).fillMaxHeight() else Modifier.weight(1f),
        )
        Spacer(Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            DayHead(channel, state.date.takeIf { channel.programs.isNotEmpty() })
            if (channel.programs.isNotEmpty()) {
                SecondaryScrollableTabRow(selectedTabIndex = state.selectedDay, edgePadding = 0.dp) {
                    state.days.forEachIndexed { index, day ->
                        Tab(selected = index == state.selectedDay, onClick = { actions.onDay(index) }) {
                            Text(day, Modifier.padding(vertical = 12.dp, horizontal = 4.dp), maxLines = 1)
                        }
                    }
                }
            }
            Day(channel, layout, openId, onOpen, actions, onDetails, Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun Day(
    channel: GuideChannel,
    layout: GuideLayout,
    openId: String?,
    onOpen: (String?) -> Unit,
    actions: GuideActions,
    onDetails: (String) -> Unit,
    modifier: Modifier,
) {
    if (channel.programs.isEmpty()) {
        NoSchedule { actions.onWatch(channel.id) }
        return
    }
    ChannelDay(
        channel.programs, openId, onOpen, layout, actions, onDetails, modifier.fillMaxSize(),
        listState = rememberLazyListState(),
    )
}

@Composable
private fun DayHead(channel: GuideChannel, date: String?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        ChannelLogoTile(channel.logo, channel.logoTint, Modifier.size(64.dp, 36.dp), RoundedCornerShape(8.dp))
        Text(
            channel.name, Modifier.weight(1f).padding(start = 12.dp).semantics { heading() },
            style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        date?.let {
            Text(
                it, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
    }
}

/** The tablet's channels beside the day; the picked one is selected. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelList(
    channels: List<GuideChannel>,
    selectedId: String,
    onChannel: (String) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (channels.indexOfFirst { it.id == selectedId } - VISIBLE_ABOVE).coerceAtLeast(0),
    )
    LazyColumn(
        modifier, state = listState, contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
            ListItem(
                selected = channel.id == selectedId,
                onClick = { onChannel(channel.id) },
                shapes = ListItemDefaults.segmentedShapes(index, channels.size),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                leadingContent = { ChannelLogoTile(channel.logo, channel.logoTint, Modifier.size(112.dp, 56.dp)) },
            ) {
                Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The phone's channels as scrollable tabs with their logos. */
@Composable
private fun ChannelTabs(channels: List<GuideChannel>, selectedId: String, onChannel: (String) -> Unit) {
    PrimaryScrollableTabRow(
        selectedTabIndex = channels.indexOfFirst { it.id == selectedId }.coerceAtLeast(0), edgePadding = 12.dp,
    ) {
        channels.forEach { channel ->
            LeadingIconTab(
                selected = channel.id == selectedId,
                onClick = { onChannel(channel.id) },
                icon = {
                    ChannelLogoTile(
                        channel.logo, channel.logoTint, Modifier.size(52.dp, 30.dp), RoundedCornerShape(7.dp),
                    )
                },
                text = { Text(channel.name, maxLines = 1) },
            )
        }
    }
}

/** Section 12's empty list for a channel without guide data; Watch is the way on. */
@Composable
private fun NoSchedule(onWatch: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 400.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painterResource(R.drawable.guide_event_busy), null, Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Text(
                stringResource(R.string.guide_no_schedule), Modifier.padding(top = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.guide_no_schedule_reason), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            FilledTonalButton(onClick = onWatch, modifier = Modifier.padding(top = 16.dp)) {
                ChannelActionLabel(R.drawable.channels_play, stringResource(R.string.channels_watch))
            }
        }
    }
}

private const val VISIBLE_ABOVE = 2
