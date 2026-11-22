// Sizes are the drawings' (round 37).
@file:Suppress("MagicNumber", "TooManyFunctions")

package at.bernhardberger.tvhplayer.touch.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelActionLabel
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.LocalDestinationBackEnabled
import at.bernhardberger.tvhplayer.touch.ui.ListMessage
import at.bernhardberger.tvhplayer.touch.ui.LocalBrowseMargin
import at.bernhardberger.tvhplayer.touch.ui.MessageKind
import at.bernhardberger.tvhplayer.touch.ui.TouchFrame
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Section 7's Overview with a local stack for program Details and the channel page.
 */
@Composable
internal fun GuideScreen(
    state: GuideState,
    actions: GuideActions,
    modifier: Modifier = Modifier,
    dayIndex: Int = state.selectedDay,
    initiallyOpen: String? = null,
    destinations: List<Destination> = Destination.entries,
    pages: List<GuidePageKey> = emptyList(),
    details: Map<GuidePageKey.Details, ProgramDetails> = emptyMap(),
    onCloseAllPages: () -> Unit = {},
    onShowInGuide: ((ProgramDetails) -> Unit)? = null,
    channelPage: @Composable (GuidePageKey.Channel, Boolean, () -> Unit) -> Unit = { _, _, _ -> },
) {
    val layout = guideLayout()
    ProgramPageHost(
        Destination.Guide, destinations, layout, pages, details, actions, { actions.onDetails(null) },
        onCloseAllPages, onShowInGuide, channelPage, modifier,
    ) { top, frame ->
        GuideBrowse(state, actions, layout, dayIndex, initiallyOpen, destinations, top, frame)
    }
}

@Composable
private fun GuideBrowse(
    state: GuideState, actions: GuideActions, layout: GuideLayout,
    dayIndex: Int, initiallyOpen: String?, destinations: List<Destination>, isTop: Boolean, modifier: Modifier,
) {
    val listed = state.listed
    val (openId, onOpen) = rememberOpenedGuideRow(state, dayIndex, initiallyOpen)

    TouchFrame(
        modifier = modifier,
        initialDestination = Destination.Guide,
        destinations = destinations,
        actions = { destination -> if (destination == Destination.Guide && listed) TagFilter(state, actions) },
        belowTopBar = { destination ->
            if (destination == Destination.Guide) {
                if (!listed) GuideLoadingLine(state.availability)
            }
        },
    ) { destination ->
        if (destination != Destination.Guide) return@TouchFrame
        if (!listed) {
            GuideStandIn(state.availability, layout, actions)
            return@TouchFrame
        }
        BackHandler(enabled = LocalDestinationBackEnabled.current && isTop && openId != null) { onOpen(null) }
        GuideList(state, layout, actions) {
            Overview(state, layout, openId, onOpen, actions, actions.onDetails, actions.onChannel)
        }
    }
}

@Composable
private fun rememberOpenedGuideRow(
    state: GuideState, dayIndex: Int, initiallyOpen: String?,
): Pair<String?, (String?) -> Unit> {
    val pageKey = (state.dayDates.getOrNull(dayIndex) ?: dayIndex).toString()
    var openedPage by rememberSaveable { mutableStateOf(pageKey) }
    var openedId by rememberSaveable { mutableStateOf(initiallyOpen) }
    var owner by rememberSaveable { mutableStateOf(state.overviewOwner(initiallyOpen)) }
    val lostRow = state.availability == GuideAvailability.Current && openedId?.let {
        state.lostOpenedRow(it, owner)
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

private fun GuideState.lostOpenedRow(id: String, owner: String?): Boolean {
    val row = channels.firstOrNull { it.id == owner }
    return when {
        row?.overviewPending == true -> false
        row != null -> row.programs.none { it.id == id }
        owner != null -> true
        else -> channels.none { it.programs.any { program -> program.id == id } }
    }
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
    val margin = LocalBrowseMargin.current
    Column(Modifier.fillMaxSize()) {
        GuideOfflineBanner(
            layout, actions.onOpenConnectionSettings,
            Modifier.padding(start = margin, end = margin, top = 8.dp),
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
private fun Overview(
    state: GuideState,
    layout: GuideLayout,
    openId: String?,
    onOpen: (String?) -> Unit,
    actions: GuideActions,
    onDetails: (String) -> Unit,
    onChannel: (String) -> Unit,
) {
    val margin = LocalBrowseMargin.current
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

/** Prefer a new selection until the pager reaches it; a settled swipe must not re-arm the preference. */
@Composable
internal fun PagerState.tabIndex(selected: Int): Int {
    var pending by remember(this, selected) { mutableStateOf(true) }
    if (!pending) return currentPage
    if (!isScrollInProgress && currentPage == selected) SideEffect { pending = false }
    return selected
}

/** Pager position is transient. Only a settled user move writes back to the selected tab. */
@Composable
internal fun rememberGuidePager(keys: List<Any>, selected: Int, onSettle: (Int) -> Unit): PagerState {
    val count by rememberUpdatedState(keys.size)
    val select by rememberUpdatedState(onSettle)
    val pager = remember { PagerState(currentPage = selected) { count } }
    LaunchedEffect(pager, keys, selected) {
        // A gesture can interrupt animation without cancelling the settled-page observer.
        launch {
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) pager.scrollToPage(selected)
            else pager.animateScrollToPage(selected)
        }.join()
        snapshotFlow { pager.settledPage.takeUnless { pager.isScrollInProgress } }
            .filterNotNull().distinctUntilChanged().collect { index ->
                if (index != selected && index in keys.indices) select(index)
            }
    }
    return pager
}

/** Section 12's empty list for a channel without guide data; Watch is the way on. */
@Composable
internal fun NoSchedule(onWatch: (() -> Unit)?) {
    Box(
        Modifier.fillMaxSize().padding(horizontal = LocalBrowseMargin.current, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 400.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painterResource(R.drawable.guide_event_busy), null, Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
