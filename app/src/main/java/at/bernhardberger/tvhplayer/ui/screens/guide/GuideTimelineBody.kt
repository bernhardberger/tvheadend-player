package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.EpgCoverage
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.GuideRows
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import at.bernhardberger.tvhplayer.core.AppError
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.EpgFocusTarget
import at.bernhardberger.tvhplayer.core.TimelineEpgEventIndex
import at.bernhardberger.tvhplayer.profiling.profileTrace
import at.bernhardberger.tvhplayer.ui.TvBrowseLeadingInset
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseCullWidthPx
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.components.TabContent
import at.bernhardberger.tvhplayer.ui.components.TabContentMotion
import at.bernhardberger.tvhplayer.ui.components.UnavailableTagNotice
import at.bernhardberger.tvhplayer.ui.components.rememberTabListState
import at.bernhardberger.tvhplayer.ui.components.rememberTabReader
import at.bernhardberger.tvhplayer.ui.components.tabFocus
import at.bernhardberger.tvhplayer.ui.screens.guideTimelineContentPadding
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState

/** One rendered scope visit of the Guide; values inside the tab body come from this frame. */
internal data class GuideTabBody(
    val scope: ChannelScopeState,
    val observation: SessionObservation,
    val connection: ConnectionUiState,
    val index: TimelineEpgEventIndex,
    val ids: List<ChannelId>,
    val numbers: Map<ChannelId, Long?>,
    val windowStart: Long,
    val windowEnd: Long,
    val nowSec: () -> Long,
    val selected: () -> EpgFocusTarget?,
    val tagNotice: Boolean,
    val recovering: Boolean,
    val alert: AppError?,
    val coveragePending: (ChannelId) -> Boolean,
    /** The channel's coverage in the snapshot the rows were indexed from. */
    val coverage: (ChannelId) -> EpgCoverage?,
    val earlierContent: Boolean,
    val laterContent: Boolean,
    val rows: GuideRows,
    val genre: ProgrammeCategory,
    val genreColors: Boolean,
)

/**
 * The Guide's scope body: notices, the empty state, the time ruler and the channel rows.
 * Callbacks that change the screen's state run only for the current visit.
 */
@OptIn(ExperimentalFoundationApi::class, at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
@Composable
internal fun GuideTimelineBody(
    motion: TabContentMotion,
    selectedKey: ChannelTagId?,
    state: () -> GuideTabBody,
    modifier: Modifier,
    channelListState: LazyListState,
    contentPadding: PaddingValues,
    layoutDirection: LayoutDirection,
    density: Density,
    startPadding: Dp,
    endPadding: Dp,
    timelineWidthPx: Float,
    horizontalFocusReserve: Dp,
    guideBringIntoView: BringIntoViewSpec,
    gridFocus: FocusRequester,
    guideRetryFocus: FocusRequester,
    navigationFocus: FocusRequester?,
    eventFocusRequesters: MutableMap<EventId, FocusRequester>,
    onDismissTagNotice: () -> Unit,
    onRetry: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onViewportKey: (KeyEvent) -> Boolean,
    onProgrammeFocused: (
        scope: ChannelScopeState,
        channelIndex: Int,
        channelId: ChannelId,
        event: EpgEventEntry,
        listState: LazyListState,
    ) -> Unit,
    onOpenProgrammeDetails: (channelIndex: Int, event: EpgEventEntry, observation: SessionObservation) -> Unit,
    onOpenProgrammeContextMenu: (channelIndex: Int, event: EpgEventEntry, observation: SessionObservation) -> Unit,
) {
    TabContent(
        motion = motion,
        selectedKey = selectedKey,
        state = state,
        modifier = modifier,
    ) { frame, owner ->
    val channelScopeState = frame.scope
    val channelScope = channelScopeState.scope
    val channels = channelScope.visibleChannels
    val observation = frame.observation
    val currentSession = observation.currentSession
    val connectionUiState = frame.connection
    val timelineEventIndex = frame.index
    val eventsByChannel = timelineEventIndex.visibleEventsByChannel
    val orderedChannelIds = frame.ids
    val channelNumbers = frame.numbers
    val renderedWindowStartSec = frame.windowStart
    val windowEndSec = frame.windowEnd
    val nowSecProvider = rememberTabReader(frame.nowSec)
    val rows = frame.rows
    val genre = frame.genre
    val genreColors = frame.genreColors
    val tagNotice = frame.tagNotice
    val guideRecovering = frame.recovering
    val channelListState = rememberTabListState(channelListState)
    val terminalReserve = guideTerminalViewportReserve(
        frame.earlierContent, frame.laterContent, layoutDirection == LayoutDirection.Rtl, horizontalFocusReserve,
    )
    val timelineContentPadding = guideTimelineContentPadding(contentPadding, layoutDirection, terminalReserve)
    // Match layout's rounded padding so the continuous Now line and ruler coincide.
    val terminalReservePx = with(density) { terminalReserve.roundToPx().toFloat() }
    val trackWidthPx = timelineWidthPx - terminalReservePx
    UnavailableTagNotice(
        visible = tagNotice,
        onDismiss = { if (owner.isCurrent) onDismissTagNotice() },
        modifier = Modifier.padding(start = startPadding, end = endPadding),
    )
    if (tagNotice) Spacer(Modifier.height(8.dp))

    if (channels.isNotEmpty() && guideRecovering) {
        GuidePassiveNotice(
            text = stringResource(R.string.epg_stale),
            modifier = Modifier.padding(start = startPadding, end = endPadding),
        )
        Spacer(Modifier.height(TvSpacing8))
    }

    if (channels.isEmpty()) {
        GuideEmptyState(
            alert = frame.alert,
            isEmptyTag = channelScope.activeTagId != null,
            connectionUiState = connectionUiState,
            channelCatalogCurrent = channelScopeState.channelCatalogCurrent,
            onAction = { action ->
                if (owner.isCurrent) {
                    when (action) {
                        AppErrorAction.RETRY -> onRetry()
                        AppErrorAction.CONNECTION_SETTINGS -> onOpenConnectionSettings()
                        AppErrorAction.CLOSE -> Unit
                    }
                }
            },
            actionFocus = if (owner.isCurrent) guideRetryFocus else remember { FocusRequester() },
            leadingExit = navigationFocus ?: FocusRequester.Default,
            upExit = FocusRequester.Default,
        )
    } else {
        TimelineTimeRuler(
            windowStartSec = renderedWindowStartSec,
            windowEndSec = windowEndSec,
            nowSecProvider = nowSecProvider,
            earlierContent = frame.earlierContent,
            laterContent = frame.laterContent,
            modifier = Modifier.padding(
                start = timelineContentPadding.calculateStartPadding(layoutDirection),
                end = timelineContentPadding.calculateEndPadding(layoutDirection),
            ),
            rows = rows,
        )
        val trackLeftPx = if (layoutDirection == LayoutDirection.Ltr) {
            with(density) { (startPadding + TvBrowseLeadingInset + guideChannelWidth(rows) + GuideChannelGap).toPx() }
        } else terminalReservePx
        Box(Modifier.weight(1f).fillMaxWidth()
            .guideViewportFades(trackLeftPx, trackWidthPx, frame.earlierContent, frame.laterContent,
                above = { channelListState.canScrollBackward }, below = { channelListState.canScrollForward })
            .guideNowLine(renderedWindowStartSec, windowEndSec, nowSecProvider, trackLeftPx, trackWidthPx,
                MaterialTheme.colorScheme.primary)
            .testTag("epg-timeline-content")) {
        // A frame shows its pictures with the session of the observation it was built from, and
        // its rows' saved logos with the observation their scope was resolved from.
        ProvideArtworkSession(currentSession, channelScopeState.observation) { CompositionLocalProvider(LocalBringIntoViewSpec provides guideBringIntoView) {
        LazyColumn(
            state = channelListState,
            userScrollEnabled = owner.isCurrent,
            contentPadding = timelineContentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxSize()
                .then(if (owner.isCurrent) Modifier.focusRequester(gridFocus) else Modifier)
                .tabFocus()
                .onPreviewKeyEvent { event ->
                    if (!owner.isCurrent) return@onPreviewKeyEvent true
                    onViewportKey(event)
                }
                .focusable()
                .testTag("epg-programme-viewport"),
        ) {
            itemsIndexed(channels, key = { _, channel -> channel.id.value }) {
                    channelIndex, channel ->
                val selected = rememberTabReader(frame.selected)()
                val coveragePending = rememberTabReader { frame.coveragePending(channel.id) }()
                val coverage = rememberTabReader { frame.coverage(channel.id) }()
                TimelineChannelRow(
                    channel = channel,
                    channelIndex = channelIndex,
                    number = ChannelNavigation.numberForId(
                        orderedChannelIds, channelNumbers, channel.id,
                    ),
                    selectedEventId = selected?.takeIf { it.channelIndex == channelIndex }?.eventId,
                    eventFocusRequesters = eventFocusRequesters,
                    windowStartSec = renderedWindowStartSec,
                    windowEndSec = windowEndSec,
                    nowSecProvider = nowSecProvider,
                    events = eventsByChannel[channel.id].orEmpty(),
                    hasCachedEvents = channel.id in timelineEventIndex.channelsWithEvents,
                    connectionUiState = connectionUiState,
                    coveragePending = coveragePending,
                    coverage = coverage,
                    onFocused = { event ->
                        if (!owner.isCurrent) return@TimelineChannelRow
                        onProgrammeFocused(channelScopeState, channelIndex, channel.id, event, channelListState)
                    },
                    recordingForEvent = { event ->
                        profileTrace("P48:guideRecordingLookup") {
                            observation.dvrEntryForEvent(event)
                        }
                    },
                    onOpenDetails = {
                        if (!owner.isCurrent) return@TimelineChannelRow
                        onOpenProgrammeDetails(channelIndex, it, observation)
                    },
                    onOpenContextMenu = {
                        if (!owner.isCurrent) return@TimelineChannelRow
                        onOpenProgrammeContextMenu(channelIndex, it, observation)
                    },
                    visibleRowWidthPx = LocalBrowseCullWidthPx.current?.minus(
                        with(LocalDensity.current) {
                            timelineContentPadding.calculateStartPadding(layoutDirection).roundToPx()
                        },
                    ),
                    focusStartInset = horizontalFocusReserve + if (frame.earlierContent) GuideEdgeFade else 0.dp,
                    focusEndInset = horizontalFocusReserve + if (frame.laterContent) GuideEdgeFade else 0.dp,
                    rows = rows,
                    genre = genre,
                    genreColors = genreColors,
                )
            }
        }
        } }
        }
    }
    }
}
