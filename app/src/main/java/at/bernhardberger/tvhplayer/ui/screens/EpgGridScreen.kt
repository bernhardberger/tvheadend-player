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
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.ConnectionFailureKind
import at.bernhardberger.tvhplayer.core.DvrActionFailure
import at.bernhardberger.tvhplayer.core.DvrConfigChoice
import at.bernhardberger.tvhplayer.core.EpgColumnDataState
import at.bernhardberger.tvhplayer.core.EpgFocusColumn
import at.bernhardberger.tvhplayer.core.EpgFocusDirection
import at.bernhardberger.tvhplayer.core.EpgFocusTarget
import at.bernhardberger.tvhplayer.core.GuideEntryFocusTarget
import at.bernhardberger.tvhplayer.core.GuideScopeExitFocusTarget
import at.bernhardberger.tvhplayer.core.DvrActionResult
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import at.bernhardberger.tvhplayer.core.browsingFocusChannelId
import at.bernhardberger.tvhplayer.core.chooseDvrConfig
import at.bernhardberger.tvhplayer.core.epgColumnDataState
import at.bernhardberger.tvhplayer.core.guideEntryFocusTarget
import at.bernhardberger.tvhplayer.core.guideScopeExitFocusTarget
import at.bernhardberger.tvhplayer.core.initialTimelineEpgFocus
import at.bernhardberger.tvhplayer.core.matchesProgrammeCategory
import at.bernhardberger.tvhplayer.core.moveTimelineEpgFocus
import at.bernhardberger.tvhplayer.core.programmeActions
import at.bernhardberger.tvhplayer.core.reconcileTimelineEpgFocus
import at.bernhardberger.tvhplayer.core.timelineEventSpan
import at.bernhardberger.tvhplayer.core.timelinePageFocusTarget
import at.bernhardberger.tvhplayer.core.SimpleTvCapability
import at.bernhardberger.tvhplayer.core.SimpleTvProfile
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrConfig
import at.bernhardberger.tvhplayer.htsp.DvrState
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import at.bernhardberger.tvhplayer.player.PlayerSession
import at.bernhardberger.tvhplayer.repositories.TvhRepository
import at.bernhardberger.tvhplayer.repositories.DvrRepository
import at.bernhardberger.tvhplayer.stores.GuidePosition
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.TvEpgPanelAlpha
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
    repository: TvhRepository = koinInject(),
    dvrRepository: DvrRepository = koinInject(),
    playerSession: PlayerSession = koinInject(),
    lastPlayedStore: LastPlayedChannelStore = koinInject(),
    guidePositionStore: GuidePositionStore = koinInject(),
    imageLoader: ImageLoader = koinInject(),
    connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
    onRetry: () -> Unit = {},
    onOpenConnectionSettings: () -> Unit = {},
    onClearCategory: () -> Unit = {},
    onPlayRecording: (Int) -> Unit = {},
    simpleTvProfile: SimpleTvProfile = SimpleTvProfile(SimpleTvSettings(), false),
    onPlay: (channelId: Int, serviceId: Int, channelName: String) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = contentPadding.calculateStartPadding(layoutDirection)
    val endPadding = contentPadding.calculateEndPadding(layoutDirection)
    val timelineContentPadding = guideTimelineContentPadding(
        contentPadding = contentPadding,
        layoutDirection = layoutDirection,
    )
    val coroutineScope = rememberCoroutineScope()
    val channelScope by channelViewModel.scope.collectAsStateWithLifecycle()
    val channels = channelScope.visibleChannels
    val tagNotice by channelViewModel.unavailableTagNotice.collectAsStateWithLifecycle()
    val selectedChannelId by selection.selectedId.collectAsStateWithLifecycle()
    val playingChannelId by playerSession.activeServiceId.collectAsStateWithLifecycle()
    val timeshiftState by playerSession.timeshiftState.collectAsStateWithLifecycle()
    val dvrEntries by dvrRepository.entries.collectAsStateWithLifecycle()
    val dvrConfigs by dvrRepository.configs.collectAsStateWithLifecycle()
    val canModifyRecordings by dvrRepository.canModifyRecordings.collectAsStateWithLifecycle()
    val channelListState = rememberLazyListState()
    val eventFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
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
    var nowSec by remember { mutableLongStateOf(openedAtSec) }
    val restoredPosition = remember { guidePositionStore.position.value }
    var windowStartSec by remember {
        mutableLongStateOf(restoredPosition?.windowStartSec ?: floorToHour(openedAtSec))
    }
    val windowEndSec = windowStartSec + VISIBLE_WINDOW_SEC
    var selectedTarget by remember { mutableStateOf<EpgFocusTarget?>(null) }
    var pendingInitialChannelIndex by remember { mutableIntStateOf(-1) }
    var initialPositionDone by remember { mutableStateOf(false) }
    var detailsEvent by remember { mutableStateOf<EpgEventEntry?>(null) }
    var restoreDetailsFocus by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<ProgrammeAction?>(null) }
    var configChoices by remember { mutableStateOf<List<DvrConfig>?>(null) }
    var selectedRecordConfigId by remember { mutableStateOf<String?>(null) }
    var actionResult by remember { mutableStateOf<DvrActionResult?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var frontierAfterSec by remember { mutableStateOf<Long?>(null) }
    var frontierLoading by remember { mutableStateOf(false) }
    var lastPlayedId by remember { mutableStateOf<Int?>(null) }
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

    LaunchedEffect(Unit) {
        lastPlayedId = lastPlayedStore.channelId.first()
        while (true) {
            nowSec = System.currentTimeMillis() / 1000L
            delay(5_000)
        }
    }

    val selectedIndex = selectedTarget?.channelIndex ?: pendingInitialChannelIndex
    val channelIds = remember(channels) { channels.map { it.id } }
    val focusRowsFlow = remember(channelIds, category, repository) {
        if (channelIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(channelIds.map(repository::epgForChannel)) { eventsByChannel ->
                channelIds.mapIndexed { index, channelId ->
                    EpgFocusColumn(
                        channelId = channelId,
                        events = eventsByChannel[index].filter {
                            it.matchesProgrammeCategory(category)
                        },
                    )
                }
            }
        }
    }
    val focusRows by focusRowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedChannel = channels.getOrNull(selectedIndex)
    val emptyEventsFlow = remember {
        kotlinx.coroutines.flow.MutableStateFlow<List<EpgEventEntry>>(emptyList())
    }
    val selectedEventsFlow = selectedChannel?.let { repository.epgForChannel(it.id) }
        ?: emptyEventsFlow
    val unfilteredSelectedChannelEvents by selectedEventsFlow.collectAsStateWithLifecycle()
    val selectedChannelEvents = remember(unfilteredSelectedChannelEvents, category) {
        unfilteredSelectedChannelEvents.filter { it.matchesProgrammeCategory(category) }
    }

    fun filteredEvents(channelId: Int): List<EpgEventEntry> = repository
        .epgForChannel(channelId)
        .value
        .filter { it.matchesProgrammeCategory(category) }

    fun requestVisibleWindow(anchorSec: Long, channelIndex: Int) {
        if (channels.isEmpty()) return
        val pageStart = (channelIndex.coerceAtLeast(0) / CHANNEL_PAGE_SIZE) * CHANNEL_PAGE_SIZE
        val ids = channels
            .subList(pageStart, (pageStart + CHANNEL_PAGE_SIZE).coerceAtMost(channels.size))
            .map { it.id }
        repository.requestEpgAtFrontier(ids, anchorSec)
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
            targetSec = restored?.eventStartSec ?: nowSec,
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

    LaunchedEffect(focusRows, selectedTarget, initialPositionDone, nowSec) {
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
            targetSec = nowSec,
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
        val event = filteredEvents(
            channels.getOrNull(target.channelIndex)?.id ?: return@LaunchedEffect
        ).firstOrNull { it.eventId == target.eventId } ?: return@LaunchedEffect
        when {
            event.start < windowStartSec -> windowStartSec = floorToHour(event.start)
            event.stop > windowEndSec -> windowStartSec = floorToHour(
                max(event.start - 30 * 60L, 0L)
            )
        }
        selection.setSelected(event.channelId)
        guidePositionStore.save(
            GuidePosition(
                channelId = event.channelId,
                eventId = event.eventId,
                eventStartSec = event.start,
                windowStartSec = windowStartSec,
                firstVisibleColumn = channelListState.firstVisibleItemIndex,
            )
        )
        val visibleRows = channelListState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visibleRows.isNotEmpty() && target.channelIndex !in visibleRows) {
            channelListState.animateScrollToItem(target.channelIndex)
        }
        if (initialFocusEnabled && !scopeRowFocused) {
            delay(80)
            eventFocusRequesters[target.eventId]?.let { requester ->
                runCatching { requester.requestFocus() }
            }
        }
    }

    LaunchedEffect(selectedChannelEvents, frontierAfterSec, frontierLoading) {
        if (!frontierLoading) return@LaunchedEffect
        val after = frontierAfterSec ?: return@LaunchedEffect
        val event = selectedChannelEvents
            .filter { it.start >= after }
            .minByOrNull { it.start }
            ?: return@LaunchedEffect
        selectedTarget = selectedTarget?.copy(eventId = event.eventId)
        frontierLoading = false
        frontierAfterSec = null
    }

    fun focusColumns(): List<EpgFocusColumn> = focusRows

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
            rows = focusColumns(),
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
                val currentEvent = filteredEvents(channels[current.channelIndex].id)
                    .firstOrNull { it.eventId == current.eventId }
                val after = currentEvent?.stop ?: windowEndSec
                windowStartSec += FRONTIER_STEP_SEC
                frontierAfterSec = after
                frontierLoading = true
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
                            windowStartSec = floorToHour(nowSec)
                            requestVisibleWindow(
                                windowStartSec,
                                selectedTarget?.channelIndex ?: pendingInitialChannelIndex,
                            )
                            selectedTarget = nearestTargetAt(
                                channels,
                                repository,
                                selectedTarget?.channelIndex ?: 0,
                                nowSec,
                                category,
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
                    onRetry = onRetry,
                    onOpenConnectionSettings = onOpenConnectionSettings,
                    retryFocusRequester = guideRetryFocus,
                )
            } else {
                TimelineTimeRuler(
                    windowStartSec = windowStartSec,
                    windowEndSec = windowStartSec + VISIBLE_WINDOW_SEC,
                    nowSec = nowSec,
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
                    itemsIndexed(channels, key = { _, channel -> channel.id }) {
                            channelIndex, channel ->
                        TimelineChannelRow(
                            channel = channel,
                            channelIndex = channelIndex,
                            allChannels = channels,
                            selectedTarget = selectedTarget,
                            eventFocusRequesters = eventFocusRequesters,
                            windowStartSec = windowStartSec,
                            windowEndSec = windowEndSec,
                            nowSec = nowSec,
                            imageLoader = imageLoader,
                            repository = repository,
                            category = category,
                            connectionUiState = connectionUiState,
                            frontierLoading = frontierLoading &&
                                selectedTarget?.channelIndex == channelIndex,
                            onFocused = { event ->
                                selectedTarget = EpgFocusTarget(channelIndex, event.eventId)
                            },
                            recordingForEvent = { eventId ->
                                dvrEntries.firstOrNull { it.eventId == eventId }
                            },
                            onOpenDetails = {
                                selectedTarget = EpgFocusTarget(channelIndex, it.eventId)
                                detailsEvent = it
                                actionResult = null
                            },
                            onMoveFocus = ::moveFocus,
                        )
                    }
                }
            }
        }

        detailsEvent?.let { event ->
            val channel = channels.firstOrNull { it.id == event.channelId }
            val recording = dvrEntries.firstOrNull { it.eventId == event.eventId }
            val timeshiftCoversEvent = playingChannelId == event.channelId &&
                simpleTvProfile.allows(SimpleTvCapability.TIMESHIFT) &&
                timeshiftState.available &&
                event.stop <= nowSec &&
                event.start * 1_000L >= nowSec * 1_000L + timeshiftState.bufferStartMs
            ProgrammeDetailsPanel(
                contentPadding = contentPadding,
                event = event,
                channel = channel,
                recording = recording,
                nowSec = nowSec,
                serverTimeshiftCoversEvent = timeshiftCoversEvent,
                simpleTvProfile = simpleTvProfile,
                canModifyRecordings = canModifyRecordings,
                actionResult = actionResult,
                onAction = { action ->
                    when (action) {
                        ProgrammeAction.WATCH -> {
                            detailsEvent = null
                            channel?.let { onPlay(it.id, it.id, it.name) }
                        }
                        ProgrammeAction.WATCH_FROM_START -> {
                            if (recording != null) {
                                onPlayRecording(recording.id)
                            } else if (timeshiftCoversEvent && channel != null) {
                                val targetPositionMs = (event.start - nowSec) * 1_000L
                                coroutineScope.launch {
                                    playerSession.seekTimeshift(
                                        targetPositionMs - timeshiftState.positionMs
                                    )
                                }
                                detailsEvent = null
                                onPlay(channel.id, channel.id, channel.name)
                            }
                        }
                        ProgrammeAction.RECORD -> when (val choice = chooseDvrConfig(dvrConfigs)) {
                            is DvrConfigChoice.Automatic -> {
                                selectedRecordConfigId = choice.configId
                                pendingAction = action
                            }
                            is DvrConfigChoice.RequiresSelection -> {
                                configChoices = choice.configs
                            }
                        }
                        ProgrammeAction.CANCEL_RECORDING -> pendingAction = action
                    }
                },
                onClose = ::closeDetails,
            )
        }

        val confirmationAction = pendingAction
        val confirmationEvent = detailsEvent
        if (confirmationAction != null && confirmationEvent != null) {
            val recording = dvrEntries.firstOrNull { it.eventId == confirmationEvent.eventId }
            ConfirmProgrammeActionDialog(
                action = confirmationAction,
                programmeTitle = confirmationEvent.title,
                onDismiss = { pendingAction = null },
                onConfirm = {
                    pendingAction = null
                    coroutineScope.launch {
                        actionResult = when (confirmationAction) {
                            ProgrammeAction.RECORD ->
                                dvrRepository.scheduleEvent(
                                    eventId = confirmationEvent.eventId,
                                    configName = selectedRecordConfigId,
                                )
                            ProgrammeAction.CANCEL_RECORDING -> recording?.let {
                                dvrRepository.cancelEntry(it.id)
                            } ?: DvrActionResult.Failed(DvrActionFailure.REJECTED)
                            else -> null
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
                    selectedRecordConfigId = config.id
                    pendingAction = ProgrammeAction.RECORD
                },
            )
        }

        if (showJumpDialog) {
            JumpToTimeDialog(
                initialSec = windowStartSec,
                nowSec = nowSec,
                onDismiss = { showJumpDialog = false },
                onJump = { target ->
                    showJumpDialog = false
                    windowStartSec = target
                    requestVisibleWindow(
                        target,
                        selectedTarget?.channelIndex ?: pendingInitialChannelIndex,
                    )
                    selectedTarget = nearestTargetAt(
                        channels,
                        repository,
                        selectedTarget?.channelIndex ?: 0,
                        target,
                        category,
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
    nowSec: Long,
    modifier: Modifier = Modifier,
) {
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
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
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
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),
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
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
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
    channel: ChannelUi,
    channelIndex: Int,
    allChannels: List<ChannelUi>,
    selectedTarget: EpgFocusTarget?,
    eventFocusRequesters: MutableMap<Int, FocusRequester>,
    windowStartSec: Long,
    windowEndSec: Long,
    nowSec: Long,
    imageLoader: ImageLoader,
    repository: TvhRepository,
    category: ProgrammeCategory,
    connectionUiState: ConnectionUiState,
    frontierLoading: Boolean,
    recordingForEvent: (Int) -> DvrEntry?,
    onFocused: (EpgEventEntry) -> Unit,
    onOpenDetails: (EpgEventEntry) -> Unit,
    onMoveFocus: (EpgFocusDirection) -> Boolean,
) {
    val events by repository.epgForChannel(channel.id).collectAsStateWithLifecycle()
    val filteredEvents = remember(events, category) {
        events.filter { it.matchesProgrammeCategory(category) }
    }
    val visibleEvents = remember(filteredEvents, windowStartSec, windowEndSec) {
        filteredEvents.filter { it.stop > windowStartSec && it.start < windowEndSec }
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
    val numbers = remember(allChannels) { allChannels.associate { it.id to it.number } }

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
            selected = selectedTarget?.channelIndex == channelIndex,
        )
        Spacer(Modifier.width(4.dp))
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = TvEpgPanelAlpha)),
        ) {
            visibleEvents.forEach { event ->
                val span = timelineEventSpan(
                    eventStartSec = event.start,
                    eventEndSec = event.stop,
                    windowStartSec = windowStartSec,
                    windowEndSec = windowEndSec,
                ) ?: return@forEach
                val start = maxWidth * span.startFraction
                val width = maxWidth * (span.endFraction - span.startFraction)
                TimelineProgrammeCell(
                    event = event,
                    channel = channel,
                    recording = recordingForEvent(event.eventId),
                    nowSec = nowSec,
                    selected = selectedTarget?.channelIndex == channelIndex &&
                        selectedTarget.eventId == event.eventId,
                    focusRequester = eventFocusRequesters.getOrPut(event.eventId) {
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
    channel: ChannelUi,
    number: Int?,
    imageLoader: ImageLoader,
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
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
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
                    piconPath = channel.icon,
                    modifier = Modifier
                        .width(44.dp)
                        .height(30.dp),
                )
                Spacer(Modifier.width(TvSpacing8))
                ChannelTitle(
                    number = number,
                    name = channel.name,
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
    channel: ChannelUi,
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
        event.start <= nowSec && nowSec < event.stop -> stringResource(R.string.epg_state_now)
        event.start > nowSec -> stringResource(R.string.epg_state_future)
        else -> stringResource(R.string.epg_state_past)
    }
    val description = stringResource(
        R.string.epg_cell_description,
        channel.name,
        event.start.formatDateTime(),
        formatHm(event.stop),
        event.title,
        stateText,
    )

    Box(modifier = modifier) {
        ListItem(
            selected = selected,
            onClick = onOpenDetails,
            headlineContent = {
                Text(
                    // Always render a label so no focusable cell is visually blank.
                    text = event.title,
                    maxLines = if (width >= 140.dp) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            supportingContent = if (width >= 90.dp) {
                {
                    Text(
                        text = "${formatHm(event.start)}–${formatHm(event.stop)}",
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
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f))
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
            it.state == DvrState.RECORDING || it.state == DvrState.SCHEDULED
        }?.let {
            RecordingStatusIndicator(
                state = it.state,
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
    nowSec: Long,
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
                onClick = { onJump(floorToHour(nowSec)) },
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
    channel: ChannelUi?,
    recording: DvrEntry?,
    nowSec: Long,
    serverTimeshiftCoversEvent: Boolean,
    simpleTvProfile: SimpleTvProfile,
    canModifyRecordings: Boolean,
    actionResult: DvrActionResult?,
    onAction: (ProgrammeAction) -> Unit,
    onClose: () -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    val actions = programmeActions(
        event,
        nowSec,
        recording,
        serverTimeshiftCoversEvent = serverTimeshiftCoversEvent,
        canModifyRecordings = canModifyRecordings,
    ).filter { action ->
        when (action) {
            ProgrammeAction.RECORD,
            ProgrammeAction.CANCEL_RECORDING ->
                simpleTvProfile.allows(SimpleTvCapability.RECORDINGS)
            ProgrammeAction.WATCH_FROM_START ->
                if (recording != null) {
                    simpleTvProfile.allows(SimpleTvCapability.RECORDINGS)
                } else {
                    simpleTvProfile.allows(SimpleTvCapability.TIMESHIFT)
                }
            ProgrammeAction.WATCH -> true
        }
    }
    LaunchedEffect(event.eventId, actions) { initialFocus.requestFocus() }
    val subtitle = buildString {
        append(channel?.name.orEmpty())
        if (isNotEmpty()) append(" • ")
        append(event.start.formatDateTime())
        append("–")
        append(formatHm(event.stop))
        append(" • ")
        append((event.stop - event.start).coerceAtLeast(0L) / 60L)
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
                        RecordingStatusIndicator(state = it.state)
                        Text(
                            text = stringResource(
                                R.string.recording_status,
                                dvrStateLabel(it.state),
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    it.failureReason?.takeIf(String::isNotBlank)?.let { reason ->
                        Text(
                            text = stringResource(R.string.recording_failure_reason, reason),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                actionResult?.let {
                    Text(
                        text = dvrActionResultLabel(it),
                        color = if (it is DvrActionResult.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
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
    configs: List<DvrConfig>,
    onDismiss: () -> Unit,
    onSelect: (DvrConfig) -> Unit,
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
                    config.comment?.takeIf(String::isNotBlank)?.let {
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
private fun dvrStateLabel(state: DvrState): String = stringResource(
    when (state) {
        DvrState.SCHEDULED -> R.string.recording_state_scheduled
        DvrState.RECORDING -> R.string.recording_state_recording
        DvrState.COMPLETED -> R.string.recording_state_completed
        DvrState.FAILED -> R.string.recording_state_failed
        DvrState.CANCELLED -> R.string.recording_state_cancelled
        DvrState.UNKNOWN -> R.string.recording_state_unknown
    }
)

@Composable
private fun dvrActionResultLabel(result: DvrActionResult): String = stringResource(
    when (result) {
        is DvrActionResult.Accepted -> R.string.recording_action_accepted
        is DvrActionResult.Failed -> when (result.reason) {
            DvrActionFailure.PERMISSION_DENIED -> R.string.recording_action_permission
            DvrActionFailure.CONNECTION_LIMIT -> R.string.recording_action_conn_limit
            DvrActionFailure.CONFLICT -> R.string.recording_action_conflict
            DvrActionFailure.REJECTED -> R.string.recording_action_rejected
            DvrActionFailure.CONNECTION -> R.string.recording_action_connection
        }
    }
)

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
                .background(Color.Black.copy(alpha = 0.72f))
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
    onRetry: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    retryFocusRequester: FocusRequester,
) {
    val permissionDenied = connectionUiState is ConnectionUiState.Error &&
        connectionUiState.kind == ConnectionFailureKind.PERMISSION_DENIED
    val message = if (isEmptyTag) {
        stringResource(R.string.empty_channel_tag)
    } else {
        stringResource(
            when (connectionUiState) {
                ConnectionUiState.Connecting,
                ConnectionUiState.SyncingChannels -> R.string.epg_loading
                ConnectionUiState.Reconnecting -> R.string.epg_reconnecting
                is ConnectionUiState.Error -> if (permissionDenied) {
                    R.string.epg_permission_denied
                } else {
                    R.string.epg_server_failure
                }
                is ConnectionUiState.SubscriptionError -> R.string.epg_server_failure
                ConnectionUiState.NeedsConfiguration -> R.string.connection_configuration_required
                ConnectionUiState.CredentialUnavailable -> R.string.credential_unavailable
                ConnectionUiState.Ready -> R.string.no_channels_available
            }
        )
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = TvEpgPanelAlpha),
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

private fun nearestTargetAt(
    channels: List<ChannelUi>,
    repository: TvhRepository,
    preferredChannelIndex: Int,
    targetSec: Long,
    category: ProgrammeCategory,
): EpgFocusTarget? {
    return initialTimelineEpgFocus(
        rows = channels.map { channel ->
            EpgFocusColumn(
                channel.id,
                repository.epgForChannel(channel.id).value.filter {
                    it.matchesProgrammeCategory(category)
                },
            )
        },
        preferredChannelIndex = preferredChannelIndex,
        targetSec = targetSec,
    )
}

private fun overlapSeconds(left: EpgEventEntry, right: EpgEventEntry): Long =
    max(0L, minOf(left.stop, right.stop) - max(left.start, right.start))

private fun floorToHour(epochSec: Long): Long = epochSec - epochSec.mod(3600L)

private fun Long.formatDateTime(): String = Instant.ofEpochSecond(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm"))
