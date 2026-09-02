package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrConfigurationsState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSearchResult
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.core.DvrConfigChoice
import at.bernhardberger.tvhplayer.core.EpgFocusColumn
import at.bernhardberger.tvhplayer.core.EpgFocusDirection
import at.bernhardberger.tvhplayer.core.EpgFocusTarget
import at.bernhardberger.tvhplayer.core.GuideEntryFocusTarget
import at.bernhardberger.tvhplayer.core.GuideScopeExitFocusTarget
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import at.bernhardberger.tvhplayer.core.ProgrammeRecordingTarget
import at.bernhardberger.tvhplayer.core.browsingFocusChannelId
import at.bernhardberger.tvhplayer.core.chooseDvrConfig
import at.bernhardberger.tvhplayer.core.currentEpgSnapshot
import at.bernhardberger.tvhplayer.core.epgFrontierSettled
import at.bernhardberger.tvhplayer.core.guideEntryFocusTarget
import at.bernhardberger.tvhplayer.core.guideScopeExitFocusTarget
import at.bernhardberger.tvhplayer.core.indexTimelineEventsByChannel
import at.bernhardberger.tvhplayer.core.initialTimelineEpgFocus
import at.bernhardberger.tvhplayer.core.matchesProgrammeCategory
import at.bernhardberger.tvhplayer.core.moveTimelineEpgFocus
import at.bernhardberger.tvhplayer.core.programmeRecordingTarget
import at.bernhardberger.tvhplayer.core.reconcileTimelineEpgFocus
import at.bernhardberger.tvhplayer.core.timelinePageFocusTarget
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.LivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.playback.toAppPresentation
import at.bernhardberger.tvhplayer.stores.GuidePosition
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabel
import at.bernhardberger.tvhplayer.ui.components.ChannelTagSelector
import at.bernhardberger.tvhplayer.ui.components.TopLevelBrowseHeader
import at.bernhardberger.tvhplayer.ui.components.UnavailableTagNotice
import at.bernhardberger.tvhplayer.ui.screens.guide.ConfirmProgrammeActionDialog
import at.bernhardberger.tvhplayer.ui.screens.guide.DvrConfigDialog
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideConnectionRecovery
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideEmptyState
import at.bernhardberger.tvhplayer.ui.screens.guide.GuidePassiveNotice
import at.bernhardberger.tvhplayer.ui.screens.guide.EpgSearchDialog
import at.bernhardberger.tvhplayer.ui.screens.guide.JumpToTimeDialog
import at.bernhardberger.tvhplayer.ui.screens.guide.ProgrammeDetailsPanel
import at.bernhardberger.tvhplayer.ui.screens.guide.TimelineChannelRow
import at.bernhardberger.tvhplayer.ui.screens.guide.TimelineTimeRuler
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import coil3.ImageLoader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlin.time.Instant as KotlinInstant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.math.max

private const val VISIBLE_WINDOW_SEC = 3 * 3600L
private const val FRONTIER_STEP_SEC = 3 * 3600L
private const val CHANNEL_PAGE_SIZE = 6

private enum class GuideHeaderFocus {
    DATE,
    NOW,
    SEARCH,
    CLEAR_FILTER,
}

private data class FrontierRequest(val afterSec: Long, val throughSec: Long)

private data class GuideSearchRequest(
    val observation: SessionObservation,
    val currentSession: CurrentSessionObservation,
    val query: String,
    val tagId: ChannelTagId?,
)

internal fun guideTimelineContentPadding(
    contentPadding: PaddingValues,
    layoutDirection: LayoutDirection,
): PaddingValues = PaddingValues(
    start = contentPadding.calculateStartPadding(layoutDirection),
    top = 2.dp,
    end = 0.dp,
    bottom = contentPadding.calculateBottomPadding() + 2.dp,
)

@Composable
fun EpgGridScreen(
    contentPadding: PaddingValues = PaddingValues(),
    initialFocusEnabled: Boolean = true,
    category: ProgrammeCategory = ProgrammeCategory.ALL,
    channelViewModel: ChannelsViewModel = koinViewModel(),
    selection: ChannelSelectionStore = koinInject(),
    session: TvheadendSession = koinInject(),
    playerSession: AppPlaybackRuntime = koinInject(),
    lastPlayedStore: LastPlayedChannelStore = koinInject(),
    guidePositionStore: GuidePositionStore = koinInject(),
    imageLoader: ImageLoader = koinInject(),
    connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
    onRetry: () -> Unit = {},
    onOpenConnectionSettings: () -> Unit = {},
    onClearCategory: () -> Unit = {},
    onPlayRecording: (RecordingPlaybackSelection) -> Unit = {},
    timeshiftAllowed: Boolean = true,
    recordingsAllowed: Boolean = true,
    onPlay: (selection: LivePlaybackSelection, channelName: String) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = contentPadding.calculateStartPadding(layoutDirection)
    val endPadding = contentPadding.calculateEndPadding(layoutDirection)
    val timelineContentPadding = guideTimelineContentPadding(
        contentPadding = contentPadding,
        layoutDirection = layoutDirection,
    )
    val coroutineScope = rememberCoroutineScope()
    val dvrMutationActions = remember(session.dvrRepository) {
        DvrMutationActions(session.dvrRepository)
    }
    val epgSearchActions = remember(session.epgRepository) {
        EpgSearchActions(session.epgRepository)
    }
    val channelScopeState by channelViewModel.scope.collectAsStateWithLifecycle()
    val channelScope = channelScopeState.scope
    val observationState = channelViewModel.observation.collectAsStateWithLifecycle()
    val observation = observationState.value
    val currentSession = observation.currentSession
    val channels = channelScope.visibleChannels
    val tagNotice by channelViewModel.unavailableTagNotice.collectAsStateWithLifecycle()
    val selectedChannelId by selection.selectedId.collectAsStateWithLifecycle()
    val activePlaybackTarget by playerSession.activeTarget.collectAsStateWithLifecycle()
    val playingChannelId = (activePlaybackTarget as? AppPlaybackTarget.Live)?.channelId
    val sdkTimeshiftState by playerSession.timeshiftState.collectAsStateWithLifecycle()
    val timeshiftState = sdkTimeshiftState.toAppPresentation()
    val dvrEntries = observation.dvrEntries()
    val channelListState = rememberLazyListState()
    val eventFocusRequesters = remember { mutableMapOf<EventId, FocusRequester>() }
    val guideDateFocus = remember { FocusRequester() }
    val guideNowFocus = remember { FocusRequester() }
    val guideSearchFocus = remember { FocusRequester() }
    val guideClearFilterFocus = remember { FocusRequester() }
    val guideRetryFocus = remember { FocusRequester() }
    val scopeFocus = remember { FocusRequester() }
    val scopeCount = channelScope.tags.size + if (channelScope.allChannelsVisible) 1 else 0
    val hasScopeTabs = scopeCount > 1
    val permissionDenied = connectionUiState is ConnectionUiState.Error &&
        connectionUiState.kind == ConnectionFailureKind.PERMISSION_DENIED
    val hasGuideFailure = (connectionUiState is ConnectionUiState.Error && !permissionDenied) ||
        connectionUiState is ConnectionUiState.SubscriptionError
    val needsGuideSettings = connectionUiState == ConnectionUiState.NeedsConfiguration ||
        connectionUiState == ConnectionUiState.CredentialUnavailable || permissionDenied
    val hasGuideRecoveryAction = hasGuideFailure || needsGuideSettings
    val guideRecovering = connectionUiState == ConnectionUiState.Connecting ||
        connectionUiState == ConnectionUiState.SyncingChannels ||
        connectionUiState == ConnectionUiState.Reconnecting

    val openedAtSec = remember { System.currentTimeMillis() / 1000L }
    val nowSecProvider = rememberCurrentEpochSeconds()
    val restoredPosition = remember { guidePositionStore.position.value }
    var windowStartSec by remember {
        mutableLongStateOf(restoredPosition?.windowStartSec ?: floorToHour(openedAtSec))
    }
    val windowEndSec = windowStartSec + VISIBLE_WINDOW_SEC
    var selectedTarget by remember { mutableStateOf<EpgFocusTarget?>(null) }
    var pendingInitialChannelIndex by remember { mutableIntStateOf(-1) }
    var initialPositionDone by remember { mutableStateOf(false) }
    var detailsEvent by remember { mutableStateOf<EpgEventEntry?>(null) }
    var detailsObservation by remember { mutableStateOf<SessionObservation?>(null) }
    var restoreDetailsFocus by remember { mutableStateOf(false) }
    var detailsFromSearch by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<ProgrammeAction?>(null) }
    var configChoices by remember { mutableStateOf<List<DvrConfiguration>?>(null) }
    var pendingRecordingTarget by remember { mutableStateOf<ProgrammeRecordingTarget?>(null) }
    var pendingMutation by remember { mutableStateOf<DvrMutationAction?>(null) }
    var actionResult by remember { mutableStateOf<DvrMutationFeedback?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<EpgSearchResult?>(null) }
    var searchObservation by remember { mutableStateOf<SessionObservation?>(null) }
    var searchRequest by remember { mutableStateOf<GuideSearchRequest?>(null) }
    var restoreSearchHeaderFocus by remember { mutableStateOf(false) }
    var restoreSearchResultFocus by remember { mutableStateOf<EventId?>(null) }
    var frontierRequest by remember { mutableStateOf<FrontierRequest?>(null) }
    var lastPlayedId by remember { mutableStateOf<ChannelId?>(null) }
    var scopeRowFocused by remember { mutableStateOf(false) }
    var lastHeaderFocus by remember { mutableStateOf(GuideHeaderFocus.DATE) }

    fun focusGuideHeader(): Boolean = runCatching {
        when (lastHeaderFocus) {
            GuideHeaderFocus.DATE -> guideDateFocus.requestFocus()
            GuideHeaderFocus.NOW -> guideNowFocus.requestFocus()
            GuideHeaderFocus.SEARCH -> guideSearchFocus.requestFocus()
            GuideHeaderFocus.CLEAR_FILTER -> guideClearFilterFocus.requestFocus()
        }
    }.getOrDefault(false)

    fun focusGuideContent(): Boolean {
        val programmeFocus = selectedTarget?.eventId?.let(eventFocusRequesters::get)
        val target = guideEntryFocusTarget(
            hasProgrammeTarget = programmeFocus != null,
            hasRetryAction = hasGuideRecoveryAction,
        )
        return when (target) {
            GuideEntryFocusTarget.PROGRAMME -> {
                runCatching { checkNotNull(programmeFocus).requestFocus() }.getOrDefault(false) ||
                    focusGuideHeader()
            }
            GuideEntryFocusTarget.RETRY -> {
                runCatching { guideRetryFocus.requestFocus() }.getOrDefault(false) ||
                    focusGuideHeader()
            }
            GuideEntryFocusTarget.HEADER -> focusGuideHeader()
        }
    }

    fun leaveGuideScope(): Boolean {
        val programmeFocus = selectedTarget?.eventId?.let(eventFocusRequesters::get)
        return when (
            guideScopeExitFocusTarget(
                hasProgrammeTarget = programmeFocus != null,
                hasRetryAction = hasGuideRecoveryAction,
            )
        ) {
            GuideScopeExitFocusTarget.PROGRAMME -> {
                runCatching { checkNotNull(programmeFocus).requestFocus() }.getOrDefault(false)
                true
            }
            GuideScopeExitFocusTarget.RETRY -> {
                runCatching { guideRetryFocus.requestFocus() }
                true
            }
            GuideScopeExitFocusTarget.STAY_ON_SCOPE -> true
        }
    }

    LaunchedEffect(lastPlayedStore) {
        lastPlayedId = lastPlayedStore.channelId.first()
    }

    LaunchedEffect(searchRequest, epgSearchActions) {
        val request = searchRequest ?: return@LaunchedEffect
        try {
            val result = epgSearchActions.execute(
                currentSession = request.currentSession,
                query = request.query,
                tagId = request.tagId,
            )
            if (searchRequest == request) {
                searchObservation = request.observation
                searchResult = result
            }
        } finally {
            if (searchRequest == request) searchRequest = null
        }
    }

    LaunchedEffect(showSearchDialog, restoreSearchHeaderFocus) {
        if (!showSearchDialog && restoreSearchHeaderFocus) {
            withFrameNanos { }
            runCatching { guideSearchFocus.requestFocus() }
            restoreSearchHeaderFocus = false
        }
    }

    val selectedIndex = selectedTarget?.channelIndex ?: pendingInitialChannelIndex
    val epgState = observation.epgState
    val epgSnapshot = when (val state = epgState) {
        is EpgRepositoryState.Current -> state.snapshot
        is EpgRepositoryState.Stale -> state.snapshot
        is EpgRepositoryState.Synchronizing -> state.staleSnapshot
        EpgRepositoryState.Empty -> null
    }
    val snapshotEvents = epgSnapshot?.events.orEmpty()
    val currentEventIds = remember(snapshotEvents) {
        snapshotEvents.mapTo(mutableSetOf()) { it.id }
    }
    LaunchedEffect(currentEventIds) {
        eventFocusRequesters.keys.retainAll(currentEventIds)
    }
    val eventsByChannel = remember(snapshotEvents) {
        indexTimelineEventsByChannel(snapshotEvents)
    }
    val focusRows = remember(channels, category, eventsByChannel) {
        channels.map { channel ->
            EpgFocusColumn(
                channelId = channel.id,
                events = eventsByChannel[channel.id].orEmpty()
                    .filter { it.matchesProgrammeCategory(category) },
            )
        }
    }
    val selectedChannel = channels.getOrNull(selectedIndex)

    fun requestVisibleWindow(anchorSec: Long, channelIndex: Int) {
        val capability = currentSession ?: return
        if (channels.isEmpty()) return
        val pageStart = (channelIndex.coerceAtLeast(0) / CHANNEL_PAGE_SIZE) * CHANNEL_PAGE_SIZE
        val ids = channels
            .subList(pageStart, (pageStart + CHANNEL_PAGE_SIZE).coerceAtMost(channels.size))
            .map { it.id }
        val through = KotlinInstant.fromEpochSeconds(anchorSec)
        ids.forEach { channelId ->
            coroutineScope.launch {
                session.epgRepository.acquireCoverage(capability, channelId, through)
            }
        }
    }

    LaunchedEffect(
        channels,
        focusRows,
        playingChannelId,
        lastPlayedId,
        initialPositionDone,
        category,
    ) {
        if (initialPositionDone || channels.isEmpty() || focusRows.size != channels.size) {
            return@LaunchedEffect
        }
        val preferredId = playingChannelId ?: lastPlayedId ?: selectedChannelId
        val restored = restoredPosition?.takeIf { position ->
            channels.any { it.id == position.channelId }
        }
        val channelId = browsingFocusChannelId(
            channels,
            restored?.channelId ?: preferredId,
        )
            ?: return@LaunchedEffect
        val channelIndex = channels.indexOfFirst { it.id == channelId }
        pendingInitialChannelIndex = channelIndex
        val target = initialTimelineEpgFocus(
            rows = focusRows,
            preferredChannelIndex = channelIndex,
            preferredEventId = restored?.eventId,
            targetSec = restored?.eventStartSec ?: nowSecProvider(),
        )

        val targetChannelIndex = target?.channelIndex ?: channelIndex
        pendingInitialChannelIndex = targetChannelIndex
        selection.setSelected(channels[targetChannelIndex].id)
        requestVisibleWindow(windowStartSec, targetChannelIndex)
        if (target != null) {
            selectedTarget = target
            channelListState.scrollToItem(
                restored?.firstVisibleColumn?.coerceIn(channels.indices)
                    ?: (targetChannelIndex / CHANNEL_PAGE_SIZE) * CHANNEL_PAGE_SIZE
            )
        }
        initialPositionDone = true
    }

    LaunchedEffect(focusRows, selectedTarget, initialPositionDone) {
        if (!initialPositionDone || focusRows.size != channels.size || channels.isEmpty()) {
            return@LaunchedEffect
        }
        val current = selectedTarget
        val preferredIndex = current?.channelIndex
            ?: pendingInitialChannelIndex.takeIf { it >= 0 }
            ?: 0
        val replacement = reconcileTimelineEpgFocus(
            rows = focusRows,
            current = current,
            preferredChannelIndex = preferredIndex,
            targetSec = nowSecProvider(),
        )
        if (replacement == current) return@LaunchedEffect
        selectedTarget = replacement
        if (replacement != null) {
            pendingInitialChannelIndex = replacement.channelIndex
            selection.setSelected(channels[replacement.channelIndex].id)
        }
    }

    LaunchedEffect(channelScope.activeTagId) {
        initialPositionDone = false
        selectedTarget = null
        pendingInitialChannelIndex = -1
    }

    LaunchedEffect(category) {
        initialPositionDone = false
        selectedTarget = null
        pendingInitialChannelIndex = -1
    }

    LaunchedEffect(
        selectedTarget,
        windowStartSec,
        channels,
        initialFocusEnabled,
        scopeRowFocused,
        initialPositionDone,
        connectionUiState,
    ) {
        val target = selectedTarget
        if (target == null) {
            if (
                initialFocusEnabled &&
                !scopeRowFocused &&
                (initialPositionDone || channels.isEmpty())
            ) {
                withFrameNanos { }
                focusGuideContent()
            }
            return@LaunchedEffect
        }
        val channelId = channels.getOrNull(target.channelIndex)?.id
            ?: return@LaunchedEffect
        val event = focusRows.getOrNull(target.channelIndex)?.events
            ?.firstOrNull { it.id == target.eventId }
            ?: return@LaunchedEffect
        when {
            event.start.epochSeconds < windowStartSec ->
                windowStartSec = floorToHour(event.start.epochSeconds)
            event.stop.epochSeconds > windowEndSec -> windowStartSec = floorToHour(
                max(event.start.epochSeconds - 30 * 60L, 0L)
            )
        }
        selection.setSelected(channelId)
        guidePositionStore.save(
            GuidePosition(
                channelId = channelId,
                eventId = event.id,
                eventStartSec = event.start.epochSeconds,
                windowStartSec = windowStartSec,
                firstVisibleColumn = channelListState.firstVisibleItemIndex,
            )
        )
        val visibleRows = channelListState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visibleRows.isNotEmpty() && target.channelIndex !in visibleRows) {
            channelListState.animateScrollToItem(target.channelIndex)
        }
        if (initialFocusEnabled && !scopeRowFocused) {
            withFrameNanos { }
            eventFocusRequesters[target.eventId]?.let { requester ->
                runCatching { requester.requestFocus() }
            }
        }
    }

    LaunchedEffect(epgState, selectedChannel, category, frontierRequest) {
        val request = frontierRequest ?: return@LaunchedEffect
        val channelId = selectedChannel?.id ?: return@LaunchedEffect
        val through = KotlinInstant.fromEpochSeconds(request.throughSec)
        val snapshot = epgState.currentEpgSnapshot() ?: return@LaunchedEffect
        val event = focusRows.getOrNull(selectedIndex)?.events.orEmpty().asSequence()
            .filter { it.start.epochSeconds >= request.afterSec }
            .minByOrNull { it.start }
        if (event != null) {
            selectedTarget = selectedTarget?.copy(eventId = event.id)
        } else if (!epgFrontierSettled(
                snapshot.coverages.firstOrNull { it.channelId == channelId },
                through,
            )
        ) {
            return@LaunchedEffect
        }
        frontierRequest = null
    }

    fun pageColumns(direction: Int) {
        if (channels.isEmpty()) return
        val current = selectedTarget ?: return
        val visibleCount = channelListState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(2)
        val targetIndex = ChannelNavigation.pageTargetIndex(
            itemCount = channels.size,
            currentIndex = current.channelIndex,
            visibleItemCount = visibleCount,
            direction = direction,
        ) ?: return
        val target = timelinePageFocusTarget(
            rows = focusRows,
            current = current,
            preferredChannelIndex = targetIndex,
            direction = direction,
        ) ?: return
        selectedTarget = target
        requestVisibleWindow(windowStartSec, target.channelIndex)
        coroutineScope.launch {
            channelListState.animateScrollToItem(target.channelIndex)
        }
    }

    fun moveFocus(direction: EpgFocusDirection): Boolean {
        val current = selectedTarget ?: return false
        val visibleIndices = channelListState.layoutInfo.visibleItemsInfo.map { it.index }
        val visibleRange = if (visibleIndices.isEmpty()) {
            current.channelIndex..current.channelIndex
        } else {
            visibleIndices.min()..visibleIndices.max()
        }
        val move = moveTimelineEpgFocus(
            rows = focusRows,
            current = current,
            direction = direction,
            visibleChannelRange = visibleRange,
        )
        when {
            move.focusHeader -> {
                if (hasScopeTabs) {
                    scopeFocus.requestFocus()
                } else {
                    focusGuideHeader()
                }
                return true
            }
            move.extendTimeFrontier -> {
                val currentEvent = focusRows[current.channelIndex].events
                    .firstOrNull { it.id == current.eventId }
                val after = currentEvent?.stop?.epochSeconds ?: windowEndSec
                windowStartSec += FRONTIER_STEP_SEC
                frontierRequest = FrontierRequest(after, windowStartSec)
                requestVisibleWindow(windowStartSec, current.channelIndex)
                return true
            }
            move.target != current -> {
                selectedTarget = move.target
                if (move.pageChannels) {
                    coroutineScope.launch {
                        channelListState.animateScrollToItem(move.target.channelIndex)
                        requestVisibleWindow(windowStartSec, move.target.channelIndex)
                    }
                }
                return true
            }
            else -> return true
        }
    }

    LaunchedEffect(detailsEvent, restoreDetailsFocus) {
        if (detailsEvent == null && restoreDetailsFocus) {
            withFrameNanos { }
            selectedTarget?.eventId?.let(eventFocusRequesters::get)?.let { requester ->
                runCatching { requester.requestFocus() }
            }
            restoreDetailsFocus = false
        }
    }

    fun closeDetails() {
        val searchEventId = detailsEvent?.id.takeIf { detailsFromSearch }
        detailsEvent = null
        detailsObservation = null
        if (showSearchDialog && searchEventId != null) {
            restoreSearchResultFocus = searchEventId
        } else {
            restoreDetailsFocus = true
        }
        detailsFromSearch = false
    }

    fun closeSearch() {
        showSearchDialog = false
        searchRequest = null
        searchResult = null
        searchObservation = null
        searchQuery = ""
        restoreSearchResultFocus = null
        restoreSearchHeaderFocus = true
    }

    val searchGeneration = searchObservation?.currentSession ?: searchRequest?.currentSession
    LaunchedEffect(currentSession, searchGeneration) {
        if (searchGeneration != null && searchGeneration !== currentSession) {
            if (detailsFromSearch) {
                detailsEvent = null
                detailsObservation = null
                detailsFromSearch = false
                pendingAction = null
                pendingMutation = null
                pendingRecordingTarget = null
                configChoices = null
                actionResult = null
            }
            closeSearch()
        }
    }

    val categoryLabel = programmeCategoryLabel(category)
    Box(Modifier.fillMaxSize().testTag("epg-screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    ChannelNavigation.pageDirectionForKeyCode(
                        event.nativeKeyEvent.keyCode
                    )?.let {
                        pageColumns(it)
                        true
                    } ?: false
                },
        ) {
            TopLevelBrowseHeader(
                title = if (category == ProgrammeCategory.ALL) {
                    stringResource(R.string.epg_title)
                } else {
                    stringResource(R.string.epg_filtered_title, categoryLabel)
                },
                modifier = Modifier
                    .padding(
                        start = startPadding,
                        top = contentPadding.calculateTopPadding(),
                        end = endPadding,
                    ),
                actions = {
                    OutlinedButton(
                        onClick = { showJumpDialog = true },
                        modifier = Modifier
                            .focusRequester(guideDateFocus)
                            .onFocusChanged {
                                if (it.isFocused) lastHeaderFocus = GuideHeaderFocus.DATE
                            }
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionDown
                                ) {
                                    if (hasScopeTabs) {
                                        scopeFocus.requestFocus()
                                    } else {
                                        focusGuideContent()
                                    }
                                } else {
                                    false
                                }
                            },
                    ) {
                        Text(windowStartSec.formatDateTime())
                    }
                    OutlinedButton(
                        onClick = {
                            val nowSec = nowSecProvider()
                            windowStartSec = floorToHour(nowSec)
                            requestVisibleWindow(
                                windowStartSec,
                                selectedTarget?.channelIndex ?: pendingInitialChannelIndex,
                            )
                            selectedTarget = initialTimelineEpgFocus(
                                rows = focusRows,
                                preferredChannelIndex = selectedTarget?.channelIndex ?: 0,
                                targetSec = nowSec,
                            )
                        },
                        modifier = Modifier
                            .focusRequester(guideNowFocus)
                            .onFocusChanged {
                                if (it.isFocused) lastHeaderFocus = GuideHeaderFocus.NOW
                            }
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionDown
                                ) {
                                    if (hasScopeTabs) {
                                        scopeFocus.requestFocus()
                                    } else {
                                        focusGuideContent()
                                    }
                                } else {
                                    false
                                }
                            },
                    ) {
                        Text(stringResource(R.string.now))
                    }
                    OutlinedButton(
                        onClick = {
                            searchQuery = ""
                            searchResult = null
                            searchObservation = null
                            searchRequest = null
                            restoreSearchResultFocus = null
                            showSearchDialog = true
                        },
                        modifier = Modifier
                            .focusRequester(guideSearchFocus)
                            .onFocusChanged {
                                if (it.isFocused) lastHeaderFocus = GuideHeaderFocus.SEARCH
                            }
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionDown
                                ) {
                                    if (hasScopeTabs) {
                                        scopeFocus.requestFocus()
                                    } else {
                                        focusGuideContent()
                                    }
                                } else {
                                    false
                                }
                            }
                            .testTag("epg-search-open"),
                    ) {
                        Text(stringResource(R.string.epg_search))
                    }
                    if (category != ProgrammeCategory.ALL) {
                        OutlinedButton(
                            onClick = onClearCategory,
                            modifier = Modifier
                                .focusRequester(guideClearFilterFocus)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        lastHeaderFocus = GuideHeaderFocus.CLEAR_FILTER
                                    }
                                }
                                .onPreviewKeyEvent { event ->
                                    if (
                                        event.type == KeyEventType.KeyDown &&
                                        event.key == Key.DirectionDown
                                    ) {
                                        if (hasScopeTabs) {
                                            scopeFocus.requestFocus()
                                        } else {
                                            focusGuideContent()
                                        }
                                    } else {
                                        false
                                    }
                                },
                        ) {
                            Text(stringResource(R.string.epg_clear_filter))
                        }
                    }
                },
            )
            if (hasScopeTabs) {
                Spacer(Modifier.height(TvSpacing8))
                ChannelTagSelector(
                    tags = channelScope.tags,
                    activeTagId = channelScope.activeTagId,
                    onSelectTag = channelViewModel::selectTag,
                    allChannelsVisible = channelScope.allChannelsVisible,
                    activeFocusRequester = scopeFocus,
                    onMoveToContent = ::leaveGuideScope,
                    modifier = Modifier
                        .padding(start = startPadding)
                        .onFocusChanged { scopeRowFocused = it.hasFocus }
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionUp
                            ) {
                                focusGuideHeader()
                            } else {
                                false
                            }
                        },
                )
            }
            Spacer(Modifier.height(TvSpacing8))
            UnavailableTagNotice(
                visible = tagNotice,
                onDismiss = channelViewModel::dismissUnavailableTagNotice,
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

            if (channels.isNotEmpty() && hasGuideRecoveryAction) {
                GuideConnectionRecovery(
                    needsSettings = needsGuideSettings,
                    permissionDenied = permissionDenied,
                    focusRequester = guideRetryFocus,
                    onRetry = onRetry,
                    onOpenConnectionSettings = onOpenConnectionSettings,
                    modifier = Modifier.padding(start = startPadding, end = endPadding),
                )
                Spacer(Modifier.height(TvSpacing8))
            }

            if (channels.isEmpty()) {
                GuideEmptyState(
                    isEmptyTag = channelScope.activeTagId != null,
                    connectionUiState = connectionUiState,
                    channelCatalogCurrent = channelScopeState.channelCatalogCurrent,
                    onRetry = onRetry,
                    onOpenConnectionSettings = onOpenConnectionSettings,
                    retryFocusRequester = guideRetryFocus,
                )
            } else {
                TimelineTimeRuler(
                    windowStartSec = windowStartSec,
                    windowEndSec = windowStartSec + VISIBLE_WINDOW_SEC,
                    nowSecProvider = nowSecProvider,
                    modifier = Modifier.padding(
                        start = timelineContentPadding.calculateStartPadding(layoutDirection),
                        end = timelineContentPadding.calculateEndPadding(layoutDirection),
                    ),
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    state = channelListState,
                    contentPadding = timelineContentPadding,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .focusGroup()
                        .testTag("epg-programme-viewport"),
                ) {
                    itemsIndexed(channels, key = { _, channel -> channel.id.value }) {
                            channelIndex, channel ->
                        TimelineChannelRow(
                            channel = channel,
                            channelIndex = channelIndex,
                            allChannels = channels,
                            selectedTarget = selectedTarget,
                            eventFocusRequesters = eventFocusRequesters,
                            windowStartSec = windowStartSec,
                            windowEndSec = windowEndSec,
                            nowSecProvider = nowSecProvider,
                            imageLoader = imageLoader,
                            currentSession = currentSession,
                            events = eventsByChannel[channel.id].orEmpty(),
                            category = category,
                            connectionUiState = connectionUiState,
                            frontierLoading = frontierRequest != null &&
                                selectedTarget?.channelIndex == channelIndex,
                            onFocused = { event ->
                                selectedTarget = EpgFocusTarget(
                                    channelIndex,
                                    event.id,
                                )
                            },
                            recordingForEvent = { eventId ->
                                observation.dvrEntryForEvent(eventId)
                            },
                            onOpenDetails = {
                                selectedTarget = EpgFocusTarget(
                                    channelIndex,
                                    it.id,
                                )
                                detailsEvent = it
                                detailsObservation = observation
                                detailsFromSearch = false
                                actionResult = null
                            },
                            onMoveFocus = ::moveFocus,
                        )
                    }
                }
            }
        }

        if (showSearchDialog) {
            EpgSearchDialog(
                contentPadding = contentPadding,
                query = searchQuery,
                result = searchResult,
                searching = searchRequest != null,
                searchEnabled = currentSession != null,
                channelName = { channelId ->
                    val resultObservation = searchObservation?.let {
                        searchResultObservation(it, observation)
                    }
                    channelId?.let { resultObservation?.channel(it)?.name }
                },
                restoreFocusTo = restoreSearchResultFocus,
                onFocusRestored = { restoreSearchResultFocus = null },
                onQueryChange = { query ->
                    searchQuery = query
                    searchResult = null
                    searchObservation = null
                    searchRequest = null
                },
                onSearch = {
                    currentSession?.let { capability ->
                        searchResult = null
                        searchObservation = null
                        searchRequest = GuideSearchRequest(
                            observation = observation,
                            currentSession = capability,
                            query = searchQuery,
                            tagId = channelScope.activeTagId,
                        )
                    }
                },
                onOpenDetails = { event ->
                    searchObservation?.let { searchedObservation ->
                        val resultObservation = searchResultObservation(
                            searchedObservation,
                            observation,
                        )
                        if (resultObservation != null) {
                            detailsEvent = event
                            detailsObservation = resultObservation
                            detailsFromSearch = true
                            restoreSearchResultFocus = null
                            actionResult = null
                        }
                    }
                },
                onDismiss = ::closeSearch,
            )
        }

        detailsEvent?.let { event ->
            val selectedObservation = detailsObservation ?: return@let
            val selectedCapability = selectedObservation.currentSession
                ?.takeIf { observation.currentSession === it }
            val eventChannelId = event.channelId
            val channel = eventChannelId?.let(selectedObservation::channel)
            val recording = selectedObservation.dvrEntryForProgramme(event)
            val timeshiftCoversEvent = { nowSec: Long ->
                playingChannelId == eventChannelId &&
                    timeshiftAllowed &&
                    timeshiftState.available &&
                    event.stop.epochSeconds <= nowSec &&
                    event.start.epochSeconds * 1_000L >=
                    nowSec * 1_000L + timeshiftState.bufferStartMs
            }
            ProgrammeDetailsPanel(
                contentPadding = contentPadding,
                event = event,
                channel = channel,
                recording = recording,
                nowSecProvider = nowSecProvider,
                serverTimeshiftCoversEvent = timeshiftCoversEvent,
                timeshiftAllowed = timeshiftAllowed,
                recordingsAllowed = recordingsAllowed,
                canModifyRecordings = selectedCapability != null,
                actionResult = actionResult,
                onAction = actionHandler@{ action ->
                    if (
                        selectedObservation.currentSession == null ||
                        selectedObservation.currentSession !== observationState.value.currentSession
                    ) {
                        closeDetails()
                        closeSearch()
                        return@actionHandler
                    }
                    when (action) {
                        ProgrammeAction.WATCH -> {
                            if (selectedCapability != null && channel != null) {
                                detailsEvent = null
                                detailsObservation = null
                                detailsFromSearch = false
                                onPlay(
                                    LivePlaybackSelection(selectedCapability, channel.id),
                                    channel.name.orEmpty(),
                                )
                            }
                        }
                        ProgrammeAction.WATCH_FROM_START -> {
                            val nowSec = nowSecProvider()
                            if (recording != null && selectedCapability != null) {
                                onPlayRecording(
                                    RecordingPlaybackSelection(
                                        selectedCapability,
                                        recording.id,
                                    )
                                )
                            } else if (
                                timeshiftCoversEvent(nowSec) &&
                                channel != null &&
                                selectedCapability != null
                            ) {
                                val targetPositionMs =
                                    (event.start.epochSeconds - nowSec) * 1_000L
                                coroutineScope.launch {
                                    playerSession.seekTimeshift(
                                        targetPositionMs - timeshiftState.positionMs
                                    )
                                }
                                detailsEvent = null
                                detailsObservation = null
                                detailsFromSearch = false
                                onPlay(
                                    LivePlaybackSelection(selectedCapability, channel.id),
                                    channel.name.orEmpty(),
                                )
                            }
                        }
                        ProgrammeAction.RECORD -> if (selectedCapability != null) {
                            when (
                                val choice = chooseDvrConfig(
                                    selectedObservation.currentDvrConfigurations()
                                )
                            ) {
                                is DvrConfigChoice.Automatic -> {
                                    pendingMutation = DvrMutationAction.CreateProgramme(
                                        target = event.programmeRecordingTarget(selectedCapability),
                                        configId = choice.configId,
                                    )
                                    pendingAction = action
                                }
                                is DvrConfigChoice.RequiresSelection -> {
                                    pendingRecordingTarget = event.programmeRecordingTarget(
                                        selectedCapability
                                    )
                                    configChoices = choice.configs
                                }
                            }
                        }
                        ProgrammeAction.CANCEL_RECORDING -> if (
                            selectedCapability != null && recording != null
                        ) {
                            pendingMutation = DvrMutationAction.Cancel(
                                selectedCapability,
                                recording.id,
                            )
                            pendingAction = action
                        }
                    }
                },
                onClose = ::closeDetails,
            )
        }

        val confirmationAction = pendingAction
        val confirmationEvent = detailsEvent
        val confirmationMutation = currentDvrMutation(
            pendingMutation,
            detailsObservation,
            currentSession,
        )
        if (
            confirmationAction != null &&
            confirmationEvent != null &&
            confirmationMutation != null
        ) {
            ConfirmProgrammeActionDialog(
                action = confirmationAction,
                programmeTitle = confirmationEvent.title.orEmpty(),
                onDismiss = {
                    pendingAction = null
                    pendingMutation = null
                },
                onConfirm = {
                    pendingAction = null
                    val mutation = currentDvrMutation(
                        pendingMutation,
                        detailsObservation,
                        observationState.value.currentSession,
                    )
                    pendingMutation = null
                    pendingRecordingTarget = null
                    if (mutation != null) {
                        coroutineScope.launch {
                            actionResult = dvrMutationActions.execute(mutation)
                        }
                    }
                },
            )
        }

        configChoices?.let { configs ->
            DvrConfigDialog(
                configs = configs,
                onDismiss = { configChoices = null },
                onSelect = { config ->
                    configChoices = null
                    pendingMutation = pendingRecordingTarget?.let { target ->
                        DvrMutationAction.CreateProgramme(target, config.id)
                    }
                    pendingAction = ProgrammeAction.RECORD
                },
            )
        }

        if (showJumpDialog) {
            JumpToTimeDialog(
                initialSec = windowStartSec,
                nowSecProvider = nowSecProvider,
                onDismiss = { showJumpDialog = false },
                onJump = { target ->
                    showJumpDialog = false
                    windowStartSec = target
                    requestVisibleWindow(
                        target,
                        selectedTarget?.channelIndex ?: pendingInitialChannelIndex,
                    )
                    selectedTarget = initialTimelineEpgFocus(
                        rows = focusRows,
                        preferredChannelIndex = selectedTarget?.channelIndex ?: 0,
                        targetSec = target,
                    )
                },
            )
        }

    }
}

private fun SessionObservation.dvrEntries(): List<DvrEntry> = when (val state = dvrState) {
    is DvrRepositoryState.Current -> state.snapshot.entries
    is DvrRepositoryState.Stale -> state.snapshot.entries
    is DvrRepositoryState.Synchronizing -> state.staleSnapshot?.entries.orEmpty()
    DvrRepositoryState.Empty -> emptyList()
}

internal fun searchResultObservation(
    searchedObservation: SessionObservation,
    currentObservation: SessionObservation,
): SessionObservation? = if (
    searchedObservation.currentSession != null &&
    searchedObservation.currentSession === currentObservation.currentSession
) {
    currentObservation
} else {
    null
}

internal fun currentDvrMutation(
    mutation: DvrMutationAction?,
    sourceObservation: SessionObservation?,
    currentSession: CurrentSessionObservation?,
): DvrMutationAction? = mutation?.takeIf {
    currentSession != null && sourceObservation?.currentSession === currentSession
}

internal fun SessionObservation.dvrEntryForProgramme(event: EpgEventEntry): DvrEntry? =
    dvrEntries().singleOrNull { entry ->
        entry.eventId == event.id || event.dvrEntryId == entry.id
    }

internal fun SessionObservation.currentDvrConfigurations(): List<DvrConfiguration> =
    when (val state = dvrConfigurationsState) {
        is DvrConfigurationsState.Current -> state.configurations
        is DvrConfigurationsState.Stale,
        is DvrConfigurationsState.Synchronizing,
        DvrConfigurationsState.Denied,
        DvrConfigurationsState.Unknown -> emptyList()
    }

internal fun guideEmptyMessageRes(
    isEmptyTag: Boolean,
    connectionUiState: ConnectionUiState,
    channelCatalogCurrent: Boolean,
): Int = when (connectionUiState) {
    ConnectionUiState.Connecting,
    ConnectionUiState.SyncingChannels -> R.string.epg_loading
    ConnectionUiState.Reconnecting -> R.string.epg_reconnecting
    is ConnectionUiState.Error -> if (connectionUiState.kind == ConnectionFailureKind.PERMISSION_DENIED) {
        R.string.epg_permission_denied
    } else {
        R.string.epg_server_failure
    }
    is ConnectionUiState.SubscriptionError -> R.string.epg_server_failure
    ConnectionUiState.NeedsConfiguration -> R.string.connection_configuration_required
    ConnectionUiState.CredentialUnavailable -> R.string.credential_unavailable
    ConnectionUiState.Ready -> when {
        !channelCatalogCurrent -> R.string.epg_loading
        isEmptyTag -> R.string.empty_channel_tag
        else -> R.string.no_channels_available
    }
}

@Composable
private fun rememberCurrentEpochSeconds(): () -> Long {
    val nowSec = remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(nowSec) {
        while (true) {
            delay(5_000)
            nowSec.longValue = System.currentTimeMillis() / 1000L
        }
    }
    return remember(nowSec) { { nowSec.longValue } }
}

internal fun floorToHour(epochSec: Long): Long = epochSec - epochSec.mod(3600L)

internal fun Long.formatDateTime(): String = Instant.ofEpochSecond(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm"))
