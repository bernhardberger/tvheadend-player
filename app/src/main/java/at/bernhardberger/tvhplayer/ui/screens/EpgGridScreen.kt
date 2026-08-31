package at.bernhardberger.tvhplayer.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrConfigurationsState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.core.DvrConfigChoice
import at.bernhardberger.tvhplayer.core.EpgColumnDataState
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
import at.bernhardberger.tvhplayer.core.epgColumnDataState
import at.bernhardberger.tvhplayer.core.epgFrontierSettled
import at.bernhardberger.tvhplayer.core.guideEntryFocusTarget
import at.bernhardberger.tvhplayer.core.guideScopeExitFocusTarget
import at.bernhardberger.tvhplayer.core.indexTimelineEventsByChannel
import at.bernhardberger.tvhplayer.core.initialTimelineEpgFocus
import at.bernhardberger.tvhplayer.core.matchesProgrammeCategory
import at.bernhardberger.tvhplayer.core.moveTimelineEpgFocus
import at.bernhardberger.tvhplayer.core.programmeActions
import at.bernhardberger.tvhplayer.core.programmeRecordingTarget
import at.bernhardberger.tvhplayer.core.reconcileTimelineEpgFocus
import at.bernhardberger.tvhplayer.core.timelineEventSpan
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
import at.bernhardberger.tvhplayer.ui.TvPanelDenseAlpha
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvScrimModalAlpha
import at.bernhardberger.tvhplayer.ui.TvTrackAlpha
import at.bernhardberger.tvhplayer.core.programmeHasAired
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabel
import at.bernhardberger.tvhplayer.ui.components.ChannelTagSelector
import at.bernhardberger.tvhplayer.ui.components.ChannelTitle
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.ProgrammeContentDetails
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvhplayer.ui.components.TopLevelBrowseHeader
import at.bernhardberger.tvhplayer.ui.components.UnavailableTagNotice
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
private val CHANNEL_HEADER_WIDTH = 190.dp
private val TIMELINE_ROW_HEIGHT = 76.dp

private enum class GuideHeaderFocus {
    DATE,
    NOW,
    CLEAR_FILTER,
}

private data class FrontierRequest(val afterSec: Long, val throughSec: Long)

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
    val channelScopeState by channelViewModel.scope.collectAsStateWithLifecycle()
    val channelScope = channelScopeState.scope
    val observation by channelViewModel.observation.collectAsStateWithLifecycle()
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
    var pendingAction by remember { mutableStateOf<ProgrammeAction?>(null) }
    var configChoices by remember { mutableStateOf<List<DvrConfiguration>?>(null) }
    var pendingRecordingTarget by remember { mutableStateOf<ProgrammeRecordingTarget?>(null) }
    var pendingMutation by remember { mutableStateOf<DvrMutationAction?>(null) }
    var actionResult by remember { mutableStateOf<DvrMutationFeedback?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var frontierRequest by remember { mutableStateOf<FrontierRequest?>(null) }
    var lastPlayedId by remember { mutableStateOf<ChannelId?>(null) }
    var scopeRowFocused by remember { mutableStateOf(false) }
    var lastHeaderFocus by remember { mutableStateOf(GuideHeaderFocus.DATE) }

    fun focusGuideHeader(): Boolean = runCatching {
        when (lastHeaderFocus) {
            GuideHeaderFocus.DATE -> guideDateFocus.requestFocus()
            GuideHeaderFocus.NOW -> guideNowFocus.requestFocus()
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
        detailsEvent = null
        detailsObservation = null
        restoreDetailsFocus = true
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
                                actionResult = null
                            },
                            onMoveFocus = ::moveFocus,
                        )
                    }
                }
            }
        }

        detailsEvent?.let { event ->
            val selectedObservation = detailsObservation ?: return@let
            val selectedCapability = selectedObservation.currentSession
                ?.takeIf { observation.currentSession === it }
            val eventChannelId = event.channelId
            val channel = eventChannelId?.let(selectedObservation::channel)
            val recording = selectedObservation.dvrEntryForEvent(event.id)
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
                onAction = { action ->
                    when (action) {
                        ProgrammeAction.WATCH -> {
                            if (selectedCapability != null && channel != null) {
                                detailsEvent = null
                                detailsObservation = null
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
        if (confirmationAction != null && confirmationEvent != null) {
            ConfirmProgrammeActionDialog(
                action = confirmationAction,
                programmeTitle = confirmationEvent.title.orEmpty(),
                onDismiss = {
                    pendingAction = null
                    pendingMutation = null
                },
                onConfirm = {
                    pendingAction = null
                    val mutation = pendingMutation
                    pendingMutation = null
                    pendingRecordingTarget = null
                    coroutineScope.launch {
                        actionResult = dvrMutationActions.execute(mutation)
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

@Composable
private fun TimelineTimeRuler(
    windowStartSec: Long,
    windowEndSec: Long,
    nowSecProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    val nowSec = nowSecProvider()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .width(CHANNEL_HEADER_WIDTH)
                .fillMaxHeight(),
            shape = MaterialTheme.shapes.small,
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = TvPanelDenseAlpha),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Box(contentAlignment = Alignment.CenterStart) {
                Text(
                    text = stringResource(R.string.epg_channels_heading),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = TvPanelDenseAlpha)),
        ) {
            repeat(6) { markerIndex ->
                val markerOffset = maxWidth * (markerIndex / 6f)
                Column(
                    modifier = Modifier
                        .offset(x = markerOffset)
                        .fillMaxHeight(),
                ) {
                    Text(
                        text = formatHm(windowStartSec + markerIndex * 30 * 60L),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .width(1.dp)
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTrackAlpha)
                            )
                    )
                }
            }
            if (nowSec in windowStartSec until windowEndSec) {
                val nowFraction = (nowSec - windowStartSec).toFloat() /
                    (windowEndSec - windowStartSec).coerceAtLeast(1L)
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * nowFraction - 5.dp)
                        .width(10.dp)
                        .height(10.dp)
                        .align(Alignment.TopStart)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraSmall,
                        ),
                )
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * nowFraction)
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun TimelineChannelRow(
    channel: Channel,
    channelIndex: Int,
    allChannels: List<Channel>,
    selectedTarget: EpgFocusTarget?,
    eventFocusRequesters: MutableMap<EventId, FocusRequester>,
    windowStartSec: Long,
    windowEndSec: Long,
    nowSecProvider: () -> Long,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    events: List<EpgEventEntry>,
    category: ProgrammeCategory,
    connectionUiState: ConnectionUiState,
    frontierLoading: Boolean,
    recordingForEvent: (EventId) -> DvrEntry?,
    onFocused: (EpgEventEntry) -> Unit,
    onOpenDetails: (EpgEventEntry) -> Unit,
    onMoveFocus: (EpgFocusDirection) -> Boolean,
) {
    val nowSec = nowSecProvider()
    val filteredEvents = remember(events, category) {
        events.filter { it.matchesProgrammeCategory(category) }
    }
    val visibleEvents = remember(filteredEvents, windowStartSec, windowEndSec) {
        filteredEvents.filter {
            it.stop.epochSeconds > windowStartSec && it.start.epochSeconds < windowEndSec
        }
    }
    val state = epgColumnDataState(
        cachedEvents = events,
        visibleEvents = visibleEvents,
        windowStartSec = windowStartSec,
        windowEndSec = windowEndSec,
        connectionState = connectionUiState,
        filterActive = category != ProgrammeCategory.ALL,
        matchingCachedEvents = filteredEvents,
    )
    val orderedIds = remember(allChannels) { allChannels.map { it.id } }
    val numbers = remember(allChannels) {
        allChannels.associate { it.id to it.number?.toInt() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TIMELINE_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimelineChannelHeader(
            channel = channel,
            number = ChannelNavigation.numberForId(orderedIds, numbers, channel.id),
            imageLoader = imageLoader,
            currentSession = currentSession,
            selected = selectedTarget?.channelIndex == channelIndex,
        )
        Spacer(Modifier.width(4.dp))
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = TvPanelDenseAlpha)),
        ) {
            visibleEvents.forEach { event ->
                val span = timelineEventSpan(
                    eventStartSec = event.start.epochSeconds,
                    eventEndSec = event.stop.epochSeconds,
                    windowStartSec = windowStartSec,
                    windowEndSec = windowEndSec,
                ) ?: return@forEach
                val start = maxWidth * span.startFraction
                val width = maxWidth * (span.endFraction - span.startFraction)
                TimelineProgrammeCell(
                    event = event,
                    channel = channel,
                    recording = recordingForEvent(event.id),
                    nowSec = nowSec,
                    selected = selectedTarget?.channelIndex == channelIndex &&
                        selectedTarget.eventId == event.id,
                    focusRequester = eventFocusRequesters.getOrPut(event.id) {
                        FocusRequester()
                    },
                    onFocused = { onFocused(event) },
                    onOpenDetails = { onOpenDetails(event) },
                    onMoveFocus = onMoveFocus,
                    width = width,
                    modifier = Modifier
                        .offset(x = start)
                        .width(width)
                        .fillMaxHeight(),
                )
            }

            if (visibleEvents.isEmpty()) {
                TimelineRowState(
                    state = if (frontierLoading) EpgColumnDataState.LOADING else state,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (nowSec in windowStartSec until windowEndSec) {
                val nowFraction = (nowSec - windowStartSec).toFloat() /
                    (windowEndSec - windowStartSec)
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * nowFraction)
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
internal fun TimelineChannelHeader(
    channel: Channel,
    number: Int?,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation? = null,
    selected: Boolean = false,
) {
    Surface(
            modifier = Modifier
                .width(CHANNEL_HEADER_WIDTH)
                .fillMaxHeight(),
            colors = SurfaceDefaults.colors(
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = TvPanelDenseAlpha)
                },
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            shape = MaterialTheme.shapes.small,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PiconBox(
                    imageLoader = imageLoader,
                    currentSession = currentSession,
                    piconPath = channel.icon,
                    modifier = Modifier
                        .width(44.dp)
                        .height(30.dp),
                )
                Spacer(Modifier.width(TvSpacing8))
                ChannelTitle(
                    number = number,
                    name = channel.name.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
            }
        }
}

@Composable
internal fun TimelineProgrammeCell(
    event: EpgEventEntry,
    channel: Channel,
    recording: DvrEntry?,
    nowSec: Long,
    selected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onOpenDetails: () -> Unit,
    onMoveFocus: (EpgFocusDirection) -> Boolean,
    width: Dp,
    modifier: Modifier,
) {
    val stateText = when {
        event.start.epochSeconds <= nowSec && nowSec < event.stop.epochSeconds ->
            stringResource(R.string.epg_state_now)
        event.start.epochSeconds > nowSec -> stringResource(R.string.epg_state_future)
        else -> stringResource(R.string.epg_state_past)
    }
    val description = stringResource(
        R.string.epg_cell_description,
        channel.name.orEmpty(),
        event.start.epochSeconds.formatDateTime(),
        formatHm(event.stop.epochSeconds),
        event.title.orEmpty(),
        stateText,
    )

    Box(modifier = modifier) {
        ListItem(
            selected = selected,
            onClick = onOpenDetails,
            headlineContent = {
                Text(
                    // Always render a label so no focusable cell is visually blank.
                    text = event.title.orEmpty(),
                    maxLines = if (width >= 140.dp) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            supportingContent = if (width >= 90.dp) {
                {
                    Text(
                        text = "${formatHm(event.start.epochSeconds)}–${formatHm(event.stop.epochSeconds)}",
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                null
            },
            scale = ListItemDefaults.scale(
                focusedScale = 1f,
                focusedSelectedScale = 1f,
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 1.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TvPanelDenseAlpha))
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (keyEvent.key) {
                        Key.DirectionUp -> onMoveFocus(EpgFocusDirection.UP)
                        Key.DirectionDown -> onMoveFocus(EpgFocusDirection.DOWN)
                        Key.DirectionLeft -> onMoveFocus(EpgFocusDirection.LEFT)
                        Key.DirectionRight -> onMoveFocus(EpgFocusDirection.RIGHT)
                        else -> false
                    }
                }
                .semantics { contentDescription = description },
        )
        recording?.takeIf {
            it.state == DvrEntryState.RECORDING || it.state == DvrEntryState.SCHEDULED
        }?.let {
            RecordingStatusIndicator(
                state = checkNotNull(it.state),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(2f)
                    .padding(6.dp),
            )
        }
    }
}

@Composable
private fun TimelineRowState(
    state: EpgColumnDataState,
    modifier: Modifier = Modifier,
) {
    val text = stringResource(
        when (state) {
            EpgColumnDataState.READY -> R.string.epg_state_ready
            EpgColumnDataState.LOADING -> R.string.epg_loading
            EpgColumnDataState.NO_DATA -> R.string.epg_no_data
            EpgColumnDataState.EMPTY_DAY -> R.string.epg_empty_day
            EpgColumnDataState.PARTIAL -> R.string.epg_partial
            EpgColumnDataState.STALE -> R.string.epg_stale
            EpgColumnDataState.PERMISSION_DENIED -> R.string.epg_permission_denied
            EpgColumnDataState.RECONNECTING -> R.string.epg_reconnecting
            EpgColumnDataState.SERVER_FAILURE -> R.string.epg_server_failure
            EpgColumnDataState.FILTER_EMPTY -> R.string.epg_filter_empty
        }
    )
    Row(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JumpToTimeDialog(
    initialSec: Long,
    nowSecProvider: () -> Long,
    onDismiss: () -> Unit,
    onJump: (Long) -> Unit,
) {
    var targetSec by remember(initialSec) { mutableLongStateOf(initialSec) }
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocus() }
    DialogScrim(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.epg_jump_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = targetSec.formatDateTime(),
            style = MaterialTheme.typography.titleLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { targetSec -= 24 * 3600L }) {
                Text(stringResource(R.string.previous_day))
            }
            OutlinedButton(onClick = { targetSec += 24 * 3600L }) {
                Text(stringResource(R.string.next_day))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { targetSec -= 3600L }) {
                Text(stringResource(R.string.previous_hour))
            }
            OutlinedButton(onClick = { targetSec += 3600L }) {
                Text(stringResource(R.string.next_hour))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onJump(floorToHour(nowSecProvider())) },
                modifier = Modifier.focusRequester(initialFocus),
            ) {
                Text(stringResource(R.string.now))
            }
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
            Button(onClick = { onJump(floorToHour(targetSec)) }) {
                Text(stringResource(R.string.epg_jump_action))
            }
        }
    }
}

@Composable
private fun ProgrammeDetailsPanel(
    contentPadding: PaddingValues,
    event: EpgEventEntry,
    channel: Channel?,
    recording: DvrEntry?,
    nowSecProvider: () -> Long,
    serverTimeshiftCoversEvent: (Long) -> Boolean,
    timeshiftAllowed: Boolean,
    recordingsAllowed: Boolean,
    canModifyRecordings: Boolean,
    actionResult: DvrMutationFeedback?,
    onAction: (ProgrammeAction) -> Unit,
    onClose: () -> Unit,
) {
    val nowSec = nowSecProvider()
    val initialFocus = remember { FocusRequester() }
    val actions = programmeActions(
        event,
        nowSec,
        recording,
        serverTimeshiftCoversEvent = serverTimeshiftCoversEvent(nowSec),
        canModifyRecordings = canModifyRecordings,
    ).filter { action ->
        when (action) {
            ProgrammeAction.RECORD,
            ProgrammeAction.CANCEL_RECORDING ->
                recordingsAllowed
            ProgrammeAction.WATCH_FROM_START ->
                if (recording != null) {
                    recordingsAllowed
                } else {
                    timeshiftAllowed
                }
            ProgrammeAction.WATCH -> true
        }
    }
    LaunchedEffect(event.id, actions) { initialFocus.requestFocus() }
    val subtitle = buildString {
        append(channel?.name.orEmpty())
        if (isNotEmpty()) append(" • ")
        append(event.start.epochSeconds.formatDateTime())
        append("–")
        append(formatHm(event.stop.epochSeconds))
        append(" • ")
        append((event.stop.epochSeconds - event.start.epochSeconds).coerceAtLeast(0L) / 60L)
        append(" min")
    }
    DialogScrim(
        onDismissRequest = onClose,
        wide = true,
        contentPadding = contentPadding,
    ) {
        ProgrammeContentDetails(
            event = event,
            subtitle = subtitle,
            footer = {
                recording?.let {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RecordingStatusIndicator(state = it.state ?: DvrEntryState.UNKNOWN)
                        Text(
                            text = stringResource(
                                R.string.recording_status,
                                dvrStateLabel(it.state),
                            ),
                            color = when (it.state) {
                                DvrEntryState.SCHEDULED,
                                DvrEntryState.RECORDING -> TvRecordingColor
                                DvrEntryState.MISSED,
                                DvrEntryState.INVALID,
                                DvrEntryState.RECORDING_ERROR,
                                DvrEntryState.COMPLETED_ERROR,
                                DvrEntryState.FILE_MISSING -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    it.subscriptionError?.name?.let { reason ->
                        Text(
                            text = stringResource(R.string.recording_failure_reason, reason),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                actionResult?.let {
                    Text(
                        text = it.label(),
                        color = if (it.isFailure) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                if (actions.isEmpty()) {
                    Text(
                        text = stringResource(
                            if (programmeHasAired(event, nowSec)) {
                                R.string.epg_already_aired
                            } else {
                                R.string.epg_no_actions
                            }
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                actions.forEachIndexed { index, action ->
                    Button(
                        onClick = { onAction(action) },
                        modifier = if (index == 0) {
                            Modifier.focusRequester(initialFocus)
                        } else {
                            Modifier
                        },
                    ) {
                        Text(programmeActionLabel(action))
                    }
                }
                OutlinedButton(
                    onClick = onClose,
                    modifier = if (actions.isEmpty()) {
                        Modifier.focusRequester(initialFocus)
                    } else {
                        Modifier
                    },
                ) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
private fun DvrConfigDialog(
    configs: List<DvrConfiguration>,
    onDismiss: () -> Unit,
    onSelect: (DvrConfiguration) -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(configs) { initialFocus.requestFocus() }
    DialogScrim(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.recording_config_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.recording_config_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        configs.forEachIndexed { index, config ->
            OutlinedButton(
                onClick = { onSelect(config) },
                modifier = if (index == 0) {
                    Modifier.focusRequester(initialFocus)
                } else {
                    Modifier
                },
            ) {
                Column {
                    Text(config.name)
                    config.comment.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        OutlinedButton(onClick = onDismiss) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
internal fun ConfirmProgrammeActionDialog(
    action: ProgrammeAction,
    programmeTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val safeFocus = remember { FocusRequester() }
    LaunchedEffect(action) { safeFocus.requestFocus() }
    DialogScrim(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(
                if (action == ProgrammeAction.RECORD) {
                    R.string.record_confirm_title
                } else {
                    R.string.cancel_recording_confirm_title
                },
                programmeTitle,
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.focusRequester(safeFocus),
            ) {
                Text(stringResource(R.string.back))
            }
            Button(onClick = onConfirm) {
                Icon(
                    imageVector = if (action == ProgrammeAction.RECORD) {
                        Icons.Filled.FiberManualRecord
                    } else {
                        Icons.Filled.Stop
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(
                        if (action == ProgrammeAction.RECORD) {
                            R.string.record
                        } else {
                            R.string.cancel_recording
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun programmeActionLabel(action: ProgrammeAction): String = stringResource(
    when (action) {
        ProgrammeAction.WATCH -> R.string.watch
        ProgrammeAction.RECORD -> R.string.record
        ProgrammeAction.CANCEL_RECORDING -> R.string.cancel_recording
        ProgrammeAction.WATCH_FROM_START -> R.string.watch_from_start
    }
)

@Composable
private fun dvrStateLabel(state: DvrEntryState?): String = stringResource(
    when (state) {
        DvrEntryState.SCHEDULED -> R.string.recording_state_scheduled
        DvrEntryState.RECORDING -> R.string.recording_state_recording
        DvrEntryState.COMPLETED -> R.string.recording_state_completed
        DvrEntryState.MISSED,
        DvrEntryState.INVALID -> R.string.recording_state_cancelled
        DvrEntryState.RECORDING_ERROR,
        DvrEntryState.COMPLETED_ERROR,
        DvrEntryState.FILE_MISSING -> R.string.recording_state_failed
        DvrEntryState.UNKNOWN,
        null -> R.string.recording_state_unknown
    }
)

private fun SessionObservation.dvrEntries(): List<DvrEntry> = when (val state = dvrState) {
    is DvrRepositoryState.Current -> state.snapshot.entries
    is DvrRepositoryState.Stale -> state.snapshot.entries
    is DvrRepositoryState.Synchronizing -> state.staleSnapshot?.entries.orEmpty()
    DvrRepositoryState.Empty -> emptyList()
}

internal fun SessionObservation.currentDvrConfigurations(): List<DvrConfiguration> =
    when (val state = dvrConfigurationsState) {
        is DvrConfigurationsState.Current -> state.configurations
        is DvrConfigurationsState.Stale,
        is DvrConfigurationsState.Synchronizing,
        DvrConfigurationsState.Denied,
        DvrConfigurationsState.Unknown -> emptyList()
    }

@Composable
private fun DialogScrim(
    onDismissRequest: () -> Unit,
    wide: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = TvScrimModalAlpha))
                .focusGroup()
                .then(if (wide) Modifier.padding(contentPadding) else Modifier),
            contentAlignment = if (wide) Alignment.CenterEnd else Alignment.Center,
        ) {
            Surface(
                modifier = if (wide) {
                    Modifier.width(680.dp).fillMaxHeight()
                } else {
                    Modifier.width(720.dp)
                },
                shape = MaterialTheme.shapes.large,
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun GuidePassiveNotice(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun GuideConnectionRecovery(
    needsSettings: Boolean,
    permissionDenied: Boolean,
    focusRequester: FocusRequester,
    onRetry: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (permissionDenied) {
                        R.string.epg_permission_denied
                    } else if (needsSettings) {
                        R.string.connection_configuration_required
                    } else {
                        R.string.epg_server_failure
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = if (needsSettings) onOpenConnectionSettings else onRetry,
                modifier = Modifier.focusRequester(focusRequester),
            ) {
                Text(
                    stringResource(
                        if (needsSettings) {
                            R.string.connection_settings_short
                        } else {
                            R.string.retry
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun GuideEmptyState(
    isEmptyTag: Boolean,
    connectionUiState: ConnectionUiState,
    channelCatalogCurrent: Boolean,
    onRetry: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    retryFocusRequester: FocusRequester,
) {
    val permissionDenied = connectionUiState is ConnectionUiState.Error &&
        connectionUiState.kind == ConnectionFailureKind.PERMISSION_DENIED
    val message = stringResource(
        guideEmptyMessageRes(
            isEmptyTag = isEmptyTag,
            connectionUiState = connectionUiState,
            channelCatalogCurrent = channelCatalogCurrent,
        ),
    )
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("guide-empty-state"),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = TvPanelDenseAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(message, style = MaterialTheme.typography.titleLarge)
            val failure = (connectionUiState is ConnectionUiState.Error && !permissionDenied) ||
                connectionUiState is ConnectionUiState.SubscriptionError
            val settings = connectionUiState == ConnectionUiState.NeedsConfiguration ||
                connectionUiState == ConnectionUiState.CredentialUnavailable || permissionDenied
            if (failure || settings) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = if (settings) onOpenConnectionSettings else onRetry,
                    modifier = Modifier.focusRequester(retryFocusRequester),
                ) {
                    Text(
                        stringResource(
                            if (settings) {
                                R.string.connection_settings_short
                            } else {
                                R.string.retry
                            },
                        ),
                    )
                }
            }
        }
    }
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

private fun floorToHour(epochSec: Long): Long = epochSec - epochSec.mod(3600L)

private fun Long.formatDateTime(): String = Instant.ofEpochSecond(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm"))
