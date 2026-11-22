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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelActionLabel
import at.bernhardberger.tvhplayer.touch.ui.channelLogoSlot
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.LocalDestinationBackEnabled
import at.bernhardberger.tvhplayer.touch.ui.LocalDestinationReselections
import at.bernhardberger.tvhplayer.touch.ui.ListMessage
import at.bernhardberger.tvhplayer.touch.ui.MessageKind
import at.bernhardberger.tvhplayer.touch.ui.OnDestinationReselected
import at.bernhardberger.tvhplayer.touch.ui.ScrollToStartOnReselect
import at.bernhardberger.tvhplayer.touch.ui.TouchFrame
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Section 7's Guide in the frame's Guide destination: the overview and one channel's day. A tap
 * opens a program in place and never plays; Details opens section 9's page, which Back closes.
 */
@Composable
internal fun GuideScreen(
    state: GuideState,
    actions: GuideActions,
    modifier: Modifier = Modifier,
    view: GuideView = GuideView.Overview,
    channelId: String? = null,
    dayIndex: Int = state.selectedDay,
    initiallyOpen: String? = null,
    destinations: List<Destination> = Destination.entries,
) {
    val layout = guideLayout()
    val listed = state.listed
    val channel = state.channels.firstOrNull { it.id == channelId } ?: state.channels.firstOrNull()
    val (openId, onOpen) = rememberOpenedGuideRow(state, view, channel, dayIndex, initiallyOpen)
    val details = state.details
    val page = details != null
    val browse = rememberSaveableStateHolder()

    TouchFrame(
        modifier = modifier,
        initialDestination = Destination.Guide,
        destinations = destinations,
        showTopBar = { !(it == Destination.Guide && page) },
        showNavigation = { !(it == Destination.Guide && page && layout.compact) },
        actions = { destination -> if (destination == Destination.Guide && listed) TagFilter(state, actions) },
        belowTopBar = { destination ->
            if (destination == Destination.Guide) {
                GuideBelowTopBar(state, view, layout, actions.onView)
            }
        },
    ) { destination ->
        if (destination != Destination.Guide) return@TouchFrame
        if (!listed) {
            GuideStandIn(state.availability, layout, actions)
            return@TouchFrame
        }
        // Registered after the frame's own handler, so the page and an opened program take Back first.
        val closeDetails = { actions.onDetails(null) }
        BackHandler(enabled = LocalDestinationBackEnabled.current && page, onBack = closeDetails)
        BackHandler(enabled = LocalDestinationBackEnabled.current && !page && openId != null) { onOpen(null) }
        if (details != null) {
            OnDestinationReselected(Destination.Guide) { closeDetails() }
            ProgramDetailsPage(details, layout, actions, closeDetails)
            return@TouchFrame
        }
        browse.SaveableStateProvider("GuideBrowse") {
            GuideList(state, layout, actions) {
                GuidePages(state, layout, view, channel, dayIndex, openId, onOpen, actions)
            }
        }
    }
}

@Composable
private fun rememberOpenedGuideRow(
    state: GuideState, view: GuideView, channel: GuideChannel?, dayIndex: Int, initiallyOpen: String?,
): Pair<String?, (String?) -> Unit> {
    val pageChannel = if (view == GuideView.ByChannel) channel?.id else null
    val pageKey = "${view.name}:$pageChannel:${state.dayDates.getOrNull(dayIndex) ?: dayIndex}"
    var openedPage by rememberSaveable { mutableStateOf(pageKey) }
    var openedId by rememberSaveable { mutableStateOf(initiallyOpen) }
    var owner by rememberSaveable { mutableStateOf(state.overviewOwner(initiallyOpen)) }
    val lostRow = state.availability == GuideAvailability.Current && openedId?.let {
        state.lostOpenedRow(view, channel, dayIndex, it, owner)
    } == true
    val openId = openedId.takeIf { openedPage == pageKey && !lostRow }
    LaunchedEffect(pageKey, state.listed, lostRow) {
        if (lostRow || state.listed && openedPage != pageKey) openedId = null
    }
    return openId to { id ->
        openedPage = pageKey
        openedId = id
        owner = state.overviewOwner(id)
    }
}

private fun GuideState.overviewOwner(id: String?): String? =
    id?.let { channels.firstOrNull { row -> row.programs.any { it.id == id } }?.id }

private fun GuideState.lostOpenedRow(
    view: GuideView, channel: GuideChannel?, dayIndex: Int, id: String, owner: String?,
): Boolean = when (view) {
    GuideView.Overview -> {
        val row = channels.firstOrNull { it.id == owner }
        when {
            row?.overviewPending == true -> false
            row != null -> row.programs.none { it.id == id }
            owner != null -> true
            else -> channels.none { it.programs.any { program -> program.id == id } }
        }
    }
    GuideView.ByChannel -> channel == null ||
        dayProgramsFor(channel, dayIndex)?.none { it.id == id } == true
}

/** The view switch under the top bar once there is a list; the first load's progress line before. */
@Composable
private fun GuideBelowTopBar(state: GuideState, view: GuideView, layout: GuideLayout, onView: (GuideView) -> Unit) {
    if (state.listed) ViewTabs(view, layout, onView) else GuideLoadingLine(state.availability, layout)
}

@Composable
private fun GuidePages(
    state: GuideState,
    layout: GuideLayout,
    view: GuideView,
    channel: GuideChannel?,
    dayIndex: Int,
    openId: String?,
    onOpen: (String?) -> Unit,
    actions: GuideActions,
) {
    val pager = rememberGuidePager(GuideView.entries, view.ordinal)
    HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = false) { index ->
        GuidePageReselection(enabled = index == view.ordinal) {
            when (GuideView.entries[index]) {
                GuideView.Overview -> Overview(
                    state, layout, openId.takeIf { view == GuideView.Overview }, onOpen, actions, actions.onDetails,
                    actions.onChannel,
                )
                GuideView.ByChannel -> if (channel != null) {
                    ByChannel(
                        state, layout, channel, dayIndex, openId.takeIf { view == GuideView.ByChannel },
                        onOpen, actions,
                    )
                }
            }
        }
    }
}

// Pagers can compose neighbors; only the chosen tab, channel, or day should consume a tap.
@Composable
private fun GuidePageReselection(enabled: Boolean, content: @Composable () -> Unit) {
    val taps = LocalDestinationReselections.current
    CompositionLocalProvider(
        LocalDestinationReselections provides if (enabled) taps else emptyFlow(),
        content = content,
    )
}

/** The listed Guide: the offline line above saved content, and Channels' empty forms without channels. */
@Composable
private fun GuideList(state: GuideState, layout: GuideLayout, actions: GuideActions, content: @Composable () -> Unit) {
    val body: @Composable () -> Unit = {
        when {
            state.channels.isNotEmpty() -> content()
            state.dayLoading -> DayLoading()
            state.selectedTagId != null -> ListMessage(MessageKind.EmptyTag, actions.onOpenConnectionSettings) {
                actions.onTag(null)
            }
            else -> ListMessage(MessageKind.Empty, actions.onOpenConnectionSettings)
        }
    }
    if (state.availability != GuideAvailability.Offline) {
        body()
        return
    }
    Column(Modifier.fillMaxSize()) {
        GuideOfflineBanner(
            layout, actions.onOpenConnectionSettings,
            Modifier.padding(start = margin(layout), end = margin(layout), top = 8.dp),
        )
        Box(Modifier.weight(1f)) { body() }
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
            OverviewRows(state, openId, onOpen, actions, onDetails, padding)
        } else {
            val padding = PaddingValues(horizontal = margin)
            OverviewColumns(state, layout, openId, onOpen, onChannel, actions, onDetails, padding)
        }
    }
}

private fun margin(layout: GuideLayout) = if (layout.compact) 12.dp else 24.dp

@Composable
private fun ByChannel(
    state: GuideState,
    layout: GuideLayout,
    channel: GuideChannel,
    dayIndex: Int,
    openId: String?,
    onOpen: (String?) -> Unit,
    actions: GuideActions,
) {
    if (layout.compact) {
        val keys = state.channels.map { it.id }
        val pager = rememberGuidePager(keys, keys.indexOf(channel.id)) { index ->
            actions.onChannel(keys[index])
        }
        Column(Modifier.fillMaxSize()) {
            ChannelTabs(state.channels, channel.id, actions.onChannel)
            DayBar(state.days, dayIndex, state.date, actions.onDay)
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { keys[it] }) { index ->
                val page = state.channels[index]
                val programs = state.dayProgramsFor(page, dayIndex)
                    .takeIf { page.id == channel.id || state.retainedDays == null }
                GuidePageReselection(enabled = page.id == channel.id) {
                    Day(
                        page, programs, state.dayLoading, layout,
                        openId.takeIf { page.id == channel.id }, onOpen, actions, Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
        return
    }
    Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 8.dp)) {
        ChannelList(
            state.channels, state.channelsHaveLogos, channel.id, actions.onChannel,
            if (layout == GuideLayout.Wide) Modifier.width(344.dp).fillMaxHeight() else Modifier.weight(1f),
        )
        Spacer(Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            val programs = state.dayProgramsFor(channel, dayIndex)
            val hasDay = programs == null || programs.isNotEmpty() || state.dayLoading
            DayHead(channel, state.date.takeIf { hasDay })
            if (hasDay || state.days.size > 1) {
                SecondaryScrollableTabRow(selectedTabIndex = dayIndex, edgePadding = 0.dp) {
                    state.days.forEachIndexed { index, day ->
                        Tab(selected = index == dayIndex, onClick = { actions.onDay(index) }) {
                            Text(day, Modifier.padding(vertical = 12.dp, horizontal = 4.dp), maxLines = 1)
                        }
                    }
                }
            }
            val keys = state.dayDates.ifEmpty { state.days }
            val pager = rememberGuidePager(keys, dayIndex, actions.onDay)
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { keys[it] }) { index ->
                GuidePageReselection(enabled = index == dayIndex) {
                    Day(
                        channel, state.dayProgramsFor(channel, index), state.dayLoading, layout,
                        openId.takeIf { index == dayIndex }, onOpen, actions, Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

/** Pager position is transient. Only a settled user move writes back to the selected tab. */
@Composable
private fun rememberGuidePager(keys: List<Any>, selected: Int, onSettle: ((Int) -> Unit)? = null): PagerState {
    val count by rememberUpdatedState(keys.size)
    val select by rememberUpdatedState(onSettle)
    val pager = remember { PagerState(currentPage = selected) { count } }
    LaunchedEffect(pager, keys, selected) {
        // A gesture can interrupt animation without cancelling the settled-page observer.
        launch {
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) pager.scrollToPage(selected)
            else pager.animateScrollToPage(selected)
        }.join()
        if (onSettle != null) {
            snapshotFlow { pager.settledPage.takeUnless { pager.isScrollInProgress } }
                .filterNotNull().distinctUntilChanged().collect { index ->
                    if (index != selected && index in keys.indices) select?.invoke(index)
                }
        }
    }
    return pager
}

private fun GuideState.dayProgramsFor(channel: GuideChannel, dayIndex: Int): List<GuideProgram>? = when {
    retainedDays != null -> retainedDays[dayDates.getOrNull(dayIndex)]
    dayIndex != selectedDay -> null
    dayDates.isEmpty() -> channel.dayPrograms
    else -> channel.day
}

@Composable
private fun Day(
    channel: GuideChannel,
    programs: List<GuideProgram>?,
    loading: Boolean,
    layout: GuideLayout,
    openId: String?,
    onOpen: (String?) -> Unit,
    actions: GuideActions,
    modifier: Modifier,
) {
    if (programs.isNullOrEmpty()) {
        val watch = actions.onWatch
        if (programs == null || loading) DayLoading() else NoSchedule(watch?.let { { it(channel.id) } })
        return
    }
    val list = rememberLazyListState()
    list.ScrollToStartOnReselect(Destination.Guide)
    ChannelDay(
        programs, openId, onOpen, layout, actions, actions.onDetails, modifier.fillMaxSize(),
        listState = list,
    )
}

@Composable
private fun DayHead(channel: GuideChannel, date: String?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        val logo = channelLogoSlot(
            channel.logo, channel.logoTint, Modifier.size(64.dp, 36.dp), RoundedCornerShape(8.dp), channel.logoModel,
        )
        logo?.invoke()
        Text(
            channel.name,
            Modifier.weight(1f).padding(start = if (logo != null) 12.dp else 0.dp).semantics { heading() },
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
    showLogoSlots: Boolean,
    selectedId: String,
    onChannel: (String) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (channels.indexOfFirst { it.id == selectedId } - VISIBLE_ABOVE).coerceAtLeast(0),
    )
    listState.ScrollToStartOnReselect(Destination.Guide)
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
                leadingContent = channelLogoSlot(
                    channel.logo, channel.logoTint, Modifier.size(112.dp, 56.dp),
                    model = channel.logoModel, showSlot = showLogoSlots,
                ),
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
            val logo = channelLogoSlot(
                channel.logo, channel.logoTint, Modifier.size(52.dp, 30.dp), RoundedCornerShape(7.dp),
                channel.logoModel,
            )
            val selected = channel.id == selectedId
            val pick = { onChannel(channel.id) }
            val text: @Composable () -> Unit = { Text(channel.name, maxLines = 1) }
            if (logo != null) LeadingIconTab(selected, pick, icon = logo, text = text)
            else Tab(selected, pick, text = text)
        }
    }
}

/** Section 12's empty list for a channel without guide data; Watch is the way on. */
@Composable
internal fun NoSchedule(onWatch: (() -> Unit)?) {
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
            if (onWatch != null) {
                FilledTonalButton(onClick = onWatch, modifier = Modifier.padding(top = 16.dp)) {
                    ChannelActionLabel(R.drawable.channels_play, stringResource(R.string.channels_watch))
                }
            }
        }
    }
}

/** By channel's list: the selected day, which the overview's programs stand in for when not given. */
private val GuideChannel.dayPrograms: List<GuideProgram> get() = day ?: programs

private const val VISIBLE_ABOVE = 2
