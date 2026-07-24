package at.bernhardberger.tvhplayer.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.DvrActionFailure
import at.bernhardberger.tvhplayer.core.DvrConfigChoice
import at.bernhardberger.tvhplayer.core.EpgColumnDataState
import at.bernhardberger.tvhplayer.core.EpgFocusColumn
import at.bernhardberger.tvhplayer.core.EpgFocusDirection
import at.bernhardberger.tvhplayer.core.EpgFocusTarget
import at.bernhardberger.tvhplayer.core.DvrActionResult
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.core.browsingFocusChannelId
import at.bernhardberger.tvhplayer.core.chooseDvrConfig
import at.bernhardberger.tvhplayer.core.epgColumnDataState
import at.bernhardberger.tvhplayer.core.moveMagazineEpgFocus
import at.bernhardberger.tvhplayer.core.nearestProgrammeAt
import at.bernhardberger.tvhplayer.core.programmeActions
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
import at.bernhardberger.tvhplayer.ui.TvScreenPadding
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.components.ChannelTagBar
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.UnavailableTagNotice
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import coil3.ImageLoader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.math.max

private const val VISIBLE_WINDOW_SEC = 4 * 3600L
private const val FRONTIER_STEP_SEC = 4 * 3600L
private const val COLUMN_PAGE_SIZE = 4
private val PROGRAMME_DP_PER_MINUTE = 1.8.dp
private val PROGRAMME_CANVAS_HEIGHT = 450.dp
private val CHANNEL_COLUMN_WIDTH = 270.dp

@Composable
fun EpgGridScreen(
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
    onPlayRecording: (Int) -> Unit = {},
    onPlay: (channelId: Int, serviceId: Int, channelName: String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val channelScope by channelViewModel.scope.collectAsStateWithLifecycle()
    val channels = channelScope.visibleChannels
    val tagNotice by channelViewModel.unavailableTagNotice.collectAsStateWithLifecycle()
    val selectedChannelId by selection.selectedId.collectAsStateWithLifecycle()
    val playingChannelId by playerSession.activeServiceId.collectAsStateWithLifecycle()
    val timeshiftState by playerSession.timeshiftState.collectAsStateWithLifecycle()
    val dvrEntries by dvrRepository.entries.collectAsStateWithLifecycle()
    val dvrConfigs by dvrRepository.configs.collectAsStateWithLifecycle()
    val lazyRowState = rememberLazyListState()
    val selectedFocus = remember { FocusRequester() }
    val dayStripFocus = remember { FocusRequester() }

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
    var pendingAction by remember { mutableStateOf<ProgrammeAction?>(null) }
    var configChoices by remember { mutableStateOf<List<DvrConfig>?>(null) }
    var selectedRecordConfigId by remember { mutableStateOf<String?>(null) }
    var actionResult by remember { mutableStateOf<DvrActionResult?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var frontierAfterSec by remember { mutableStateOf<Long?>(null) }
    var frontierLoading by remember { mutableStateOf(false) }
    var lastPlayedId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        lastPlayedId = lastPlayedStore.channelId.first()
        while (true) {
            nowSec = System.currentTimeMillis() / 1000L
            delay(5_000)
        }
    }

    val selectedIndex = selectedTarget?.channelIndex ?: pendingInitialChannelIndex
    val selectedChannel = channels.getOrNull(selectedIndex)
    val emptyEventsFlow = remember {
        kotlinx.coroutines.flow.MutableStateFlow<List<EpgEventEntry>>(emptyList())
    }
    val selectedEventsFlow = selectedChannel?.let { repository.epgForChannel(it.id) }
        ?: emptyEventsFlow
    val selectedChannelEvents by selectedEventsFlow.collectAsStateWithLifecycle()

    fun requestVisibleWindow(anchorSec: Long, channelIndex: Int) {
        if (channels.isEmpty()) return
        val pageStart = (channelIndex.coerceAtLeast(0) / COLUMN_PAGE_SIZE) * COLUMN_PAGE_SIZE
        val ids = channels
            .subList(pageStart, (pageStart + COLUMN_PAGE_SIZE).coerceAtMost(channels.size))
            .map { it.id }
        repository.requestEpgAtFrontier(ids, anchorSec)
    }

    LaunchedEffect(channels, playingChannelId, lastPlayedId, initialPositionDone) {
        if (initialPositionDone || channels.isEmpty()) return@LaunchedEffect
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
        val events = repository.epgForChannel(channelId).value.sortedBy { it.start }
        val event = restored?.let { position ->
            events.firstOrNull { it.eventId == position.eventId }
                ?: nearestProgrammeAt(events, position.eventStartSec)
        } ?: events.firstOrNull { it.start <= nowSec && nowSec < it.stop }
            ?: events.firstOrNull { it.start >= nowSec }

        selection.setSelected(channelId)
        requestVisibleWindow(windowStartSec, channelIndex)
        if (event != null) {
            selectedTarget = EpgFocusTarget(channelIndex, event.eventId)
            lazyRowState.scrollToItem(
                restored?.firstVisibleColumn?.coerceIn(channels.indices)
                    ?: (channelIndex / COLUMN_PAGE_SIZE) * COLUMN_PAGE_SIZE
            )
        }
        initialPositionDone = true
    }

    LaunchedEffect(selectedChannelEvents, selectedTarget, initialPositionDone) {
        if (!initialPositionDone || selectedTarget != null || pendingInitialChannelIndex < 0) {
            return@LaunchedEffect
        }
        val event = selectedChannelEvents.firstOrNull {
            it.start <= nowSec && nowSec < it.stop
        } ?: selectedChannelEvents.firstOrNull { it.start >= nowSec }
        if (event != null) {
            selectedTarget = EpgFocusTarget(pendingInitialChannelIndex, event.eventId)
        }
    }

    LaunchedEffect(channelScope.activeTagId) {
        initialPositionDone = false
        selectedTarget = null
        pendingInitialChannelIndex = -1
    }

    LaunchedEffect(selectedTarget, windowStartSec, channels) {
        val target = selectedTarget ?: return@LaunchedEffect
        val event = repository.epgForChannel(
            channels.getOrNull(target.channelIndex)?.id ?: return@LaunchedEffect
        ).value.firstOrNull { it.eventId == target.eventId } ?: return@LaunchedEffect
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
                firstVisibleColumn = lazyRowState.firstVisibleItemIndex,
            )
        )
        delay(80)
        runCatching { selectedFocus.requestFocus() }
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

    fun focusColumns(): List<EpgFocusColumn> = channels.map { channel ->
        EpgFocusColumn(channel.id, repository.epgForChannel(channel.id).value)
    }

    fun pageColumns(direction: Int) {
        if (channels.isEmpty()) return
        val current = selectedTarget ?: return
        val targetIndex = (current.channelIndex + direction * COLUMN_PAGE_SIZE)
            .coerceIn(0, channels.lastIndex)
        val currentEvent = repository.epgForChannel(
            channels[current.channelIndex].id
        ).value.firstOrNull { it.eventId == current.eventId } ?: return
        val targetEvent = repository.epgForChannel(channels[targetIndex].id).value
            .maxByOrNull { overlapSeconds(currentEvent, it) }
            ?: return
        selectedTarget = EpgFocusTarget(targetIndex, targetEvent.eventId)
        requestVisibleWindow(windowStartSec, targetIndex)
        coroutineScope.launch {
            lazyRowState.animateScrollToItem(
                (targetIndex / COLUMN_PAGE_SIZE) * COLUMN_PAGE_SIZE
            )
        }
    }

    fun moveFocus(direction: EpgFocusDirection): Boolean {
        val current = selectedTarget ?: return false
        val visibleIndices = lazyRowState.layoutInfo.visibleItemsInfo.map { it.index }
        val visibleRange = if (visibleIndices.isEmpty()) {
            current.channelIndex..current.channelIndex
        } else {
            visibleIndices.min()..visibleIndices.max()
        }
        val move = moveMagazineEpgFocus(
            columns = focusColumns(),
            current = current,
            direction = direction,
            visibleColumnRange = visibleRange,
        )
        when {
            move.focusDayStrip -> {
                dayStripFocus.requestFocus()
                return true
            }
            move.extendTimeFrontier -> {
                val currentEvent = repository.epgForChannel(
                    channels[current.channelIndex].id
                ).value.firstOrNull { it.eventId == current.eventId }
                val after = currentEvent?.stop ?: windowEndSec
                windowStartSec += FRONTIER_STEP_SEC
                frontierAfterSec = after
                frontierLoading = true
                requestVisibleWindow(windowStartSec, current.channelIndex)
                return true
            }
            move.target != current -> {
                selectedTarget = move.target
                if (move.pageColumns) {
                    coroutineScope.launch {
                        val pageStart = (
                            move.target.channelIndex / COLUMN_PAGE_SIZE * COLUMN_PAGE_SIZE
                        )
                        lazyRowState.animateScrollToItem(pageStart)
                        requestVisibleWindow(windowStartSec, move.target.channelIndex)
                    }
                }
                return true
            }
            else -> return true
        }
    }

    BackHandler(enabled = detailsEvent != null || showJumpDialog) {
        detailsEvent = null
        showJumpDialog = false
        coroutineScope.launch {
            delay(80)
            runCatching { selectedFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(TvScreenPadding)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP -> {
                            pageColumns(1)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            pageColumns(-1)
                            true
                        }
                        else -> false
                    }
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.epg_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { showJumpDialog = true }) {
                    Text(stringResource(R.string.epg_jump))
                }
            }
            Spacer(Modifier.height(10.dp))
            UnavailableTagNotice(
                visible = tagNotice,
                onDismiss = channelViewModel::dismissUnavailableTagNotice,
            )
            if (tagNotice) Spacer(Modifier.height(8.dp))
            ChannelTagBar(
                tags = channelScope.tags,
                activeTagId = channelScope.activeTagId,
                onSelectTag = channelViewModel::selectTag,
            )
            Spacer(Modifier.height(10.dp))
            DayStrip(
                activeStartSec = windowStartSec,
                focusRequester = dayStripFocus,
                onSelect = { dayStart ->
                    windowStartSec = dayStart
                    requestVisibleWindow(
                        dayStart,
                        selectedTarget?.channelIndex ?: pendingInitialChannelIndex,
                    )
                    selectedTarget = nearestTargetAt(
                        channels,
                        repository,
                        selectedTarget?.channelIndex ?: 0,
                        dayStart,
                    )
                },
                onJump = { showJumpDialog = true },
                onReturnToProgramme = {
                    if (selectedTarget != null) {
                        selectedFocus.requestFocus()
                        true
                    } else {
                        false
                    }
                },
            )
            Spacer(Modifier.height(10.dp))

            if (channels.isEmpty()) {
                GuideEmptyState(
                    isEmptyTag = channelScope.activeTagId != null,
                    connectionUiState = connectionUiState,
                    onRetry = onRetry,
                )
            } else {
                LazyRow(
                    state = lazyRowState,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusGroup(),
                ) {
                    itemsIndexed(channels, key = { _, channel -> channel.id }) {
                            channelIndex, channel ->
                        MagazineChannelColumn(
                            channel = channel,
                            channelIndex = channelIndex,
                            allChannels = channels,
                            selectedTarget = selectedTarget,
                            selectedFocusRequester = selectedFocus,
                            windowStartSec = windowStartSec,
                            windowEndSec = windowEndSec,
                            nowSec = nowSec,
                            imageLoader = imageLoader,
                            repository = repository,
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
                                detailsEvent = it
                                actionResult = null
                            },
                            onMoveFocus = ::moveFocus,
                            onRetry = onRetry,
                        )
                    }
                }
            }
        }

        detailsEvent?.let { event ->
            val channel = channels.firstOrNull { it.id == event.channelId }
            val recording = dvrEntries.firstOrNull { it.eventId == event.eventId }
            val timeshiftCoversEvent = playingChannelId == event.channelId &&
                timeshiftState.available &&
                event.stop <= nowSec &&
                event.start * 1_000L >= nowSec * 1_000L + timeshiftState.bufferStartMs
            ProgrammeDetailsPanel(
                event = event,
                channel = channel,
                recording = recording,
                nowSec = nowSec,
                serverTimeshiftCoversEvent = timeshiftCoversEvent,
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
                onClose = {
                    detailsEvent = null
                    coroutineScope.launch {
                        delay(80)
                        runCatching { selectedFocus.requestFocus() }
                    }
                },
            )
        }

        val confirmationAction = pendingAction
        val confirmationEvent = detailsEvent
        if (confirmationAction != null && confirmationEvent != null) {
            val recording = dvrEntries.firstOrNull { it.eventId == confirmationEvent.eventId }
            ConfirmProgrammeActionDialog(
                action = confirmationAction,
                onDismiss = { pendingAction = null },
                onConfirm = {
                    pendingAction = null
                    coroutineScope.launch {
                        actionResult = when (confirmationAction) {
                            ProgrammeAction.RECORD ->
                                dvrRepository.scheduleEvent(
                                    eventId = confirmationEvent.eventId,
                                    configId = selectedRecordConfigId,
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
                    )
                },
            )
        }
    }
}

@Composable
private fun MagazineChannelColumn(
    channel: ChannelUi,
    channelIndex: Int,
    allChannels: List<ChannelUi>,
    selectedTarget: EpgFocusTarget?,
    selectedFocusRequester: FocusRequester,
    windowStartSec: Long,
    windowEndSec: Long,
    nowSec: Long,
    imageLoader: ImageLoader,
    repository: TvhRepository,
    connectionUiState: ConnectionUiState,
    frontierLoading: Boolean,
    recordingForEvent: (Int) -> DvrEntry?,
    onFocused: (EpgEventEntry) -> Unit,
    onOpenDetails: (EpgEventEntry) -> Unit,
    onMoveFocus: (EpgFocusDirection) -> Boolean,
    onRetry: () -> Unit,
) {
    val events by repository.epgForChannel(channel.id).collectAsStateWithLifecycle()
    val visibleEvents = remember(events, windowStartSec, windowEndSec) {
        events.filter { it.stop > windowStartSec && it.start < windowEndSec }
    }
    val state = epgColumnDataState(
        cachedEvents = events,
        visibleEvents = visibleEvents,
        windowStartSec = windowStartSec,
        windowEndSec = windowEndSec,
        connectionState = connectionUiState,
    )
    val orderedIds = remember(allChannels) { allChannels.map { it.id } }
    val numbers = remember(allChannels) { allChannels.associate { it.id to it.number } }

    Column(
        modifier = Modifier
            .width(CHANNEL_COLUMN_WIDTH)
            .fillMaxHeight(),
    ) {
        MagazineChannelHeader(
            channel = channel,
            number = ChannelNavigation.numberForId(orderedIds, numbers, channel.id),
            imageLoader = imageLoader,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PROGRAMME_CANVAS_HEIGHT)
                .clip(MaterialTheme.shapes.medium),
        ) {
            visibleEvents.forEach { event ->
                val top = PROGRAMME_DP_PER_MINUTE *
                    minutesBetween(windowStartSec, max(event.start, windowStartSec))
                val durationMinutes = minutesBetween(
                    max(event.start, windowStartSec),
                    minOf(event.stop, windowEndSec),
                )
                val height = maxOf(54.dp, PROGRAMME_DP_PER_MINUTE * durationMinutes)
                ProgrammeCell(
                    event = event,
                    channel = channel,
                    recording = recordingForEvent(event.eventId),
                    nowSec = nowSec,
                    selected = selectedTarget?.channelIndex == channelIndex &&
                        selectedTarget.eventId == event.eventId,
                    selectedFocusRequester = selectedFocusRequester,
                    onFocused = { onFocused(event) },
                    onOpenDetails = { onOpenDetails(event) },
                    onMoveFocus = onMoveFocus,
                    modifier = Modifier
                        .offset(y = top)
                        .height(height)
                        .fillMaxWidth(),
                )
            }

            if (state != EpgColumnDataState.READY || frontierLoading) {
                ColumnStateOverlay(
                    state = if (frontierLoading) EpgColumnDataState.LOADING else state,
                    hasUsableEvents = visibleEvents.isNotEmpty(),
                    onRetry = onRetry,
                    modifier = Modifier.align(
                        if (visibleEvents.isNotEmpty()) {
                            Alignment.BottomCenter
                        } else {
                            Alignment.Center
                        }
                    ),
                )
            }
        }
    }
}

@Composable
internal fun MagazineChannelHeader(
    channel: ChannelUi,
    number: Int?,
    imageLoader: ImageLoader,
) {
    Surface(
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = number?.toString().orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(34.dp),
                )
                PiconBox(
                    imageLoader = imageLoader,
                    piconPath = channel.icon,
                    modifier = Modifier
                        .width(54.dp)
                        .height(38.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = channel.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
}

@Composable
private fun ProgrammeCell(
    event: EpgEventEntry,
    channel: ChannelUi,
    recording: DvrEntry?,
    nowSec: Long,
    selected: Boolean,
    selectedFocusRequester: FocusRequester,
    onFocused: () -> Unit,
    onOpenDetails: () -> Unit,
    onMoveFocus: (EpgFocusDirection) -> Boolean,
    modifier: Modifier,
) {
    val stateText = when {
        event.start <= nowSec && nowSec < event.stop -> stringResource(R.string.epg_state_now)
        event.start > nowSec -> stringResource(R.string.epg_state_future)
        else -> stringResource(R.string.epg_state_past)
    }
    val recordingText = recording?.state?.let { dvrStateLabel(it) }
    val description = stringResource(
        R.string.epg_cell_description,
        channel.name,
        event.start.formatDateTime(),
        formatHm(event.stop),
        event.title,
        stateText,
    )

    ListItem(
        selected = selected,
        onClick = onOpenDetails,
        headlineContent = {
            Text(
                text = event.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = buildString {
                    append("${formatHm(event.start)}–${formatHm(event.stop)}")
                    recordingText?.let {
                        append(" • ")
                        append(it)
                    }
                },
                maxLines = 1,
            )
        },
        scale = ListItemDefaults.scale(
            focusedScale = 1f,
            focusedSelectedScale = 1f,
        ),
        modifier = modifier
            .alpha(if (event.stop <= nowSec) 0.62f else 1f)
            .padding(2.dp)
            .then(if (selected) Modifier.focusRequester(selectedFocusRequester) else Modifier)
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
}

@Composable
private fun ColumnStateOverlay(
    state: EpgColumnDataState,
    hasUsableEvents: Boolean,
    onRetry: () -> Unit,
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
        }
    )
    Surface(
        modifier = modifier
            .widthIn(max = 248.dp),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (hasUsableEvents) 0.92f else 1f
            ),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = text, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (
                state == EpgColumnDataState.SERVER_FAILURE ||
                state == EpgColumnDataState.PERMISSION_DENIED
            ) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun DayStrip(
    activeStartSec: Long,
    focusRequester: FocusRequester,
    onSelect: (Long) -> Unit,
    onJump: () -> Unit,
    onReturnToProgramme: () -> Boolean,
) {
    val zone = ZoneId.systemDefault()
    val today = Instant.now().atZone(zone).toLocalDate()
    val activeDate = Instant.ofEpochSecond(activeStartSec).atZone(zone).toLocalDate()
    val activeInStrip = activeDate in today.minusDays(1)..today.plusDays(6)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown &&
                    onReturnToProgramme()
            },
    ) {
        items(8) { itemIndex ->
            val offset = itemIndex - 1
            val date = today.plusDays(offset.toLong())
            val label = when (offset) {
                -1 -> stringResource(R.string.yesterday)
                0 -> stringResource(R.string.today)
                1 -> stringResource(R.string.tomorrow)
                else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
            }
            val modifier = if (date == activeDate || (!activeInStrip && itemIndex == 0)) {
                Modifier.focusRequester(focusRequester)
            } else {
                Modifier
            }
            if (date == activeDate) {
                Button(
                    onClick = { onSelect(date.atStartOfDay(zone).toEpochSecond()) },
                    modifier = modifier,
                ) { Text(label) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(date.atStartOfDay(zone).toEpochSecond()) },
                    modifier = modifier,
                ) { Text(label) }
            }
        }
        item {
            OutlinedButton(onClick = onJump) {
                Text(stringResource(R.string.epg_jump))
            }
        }
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
    BackHandler(onBack = onDismiss)
    DialogScrim {
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
    event: EpgEventEntry,
    channel: ChannelUi?,
    recording: DvrEntry?,
    nowSec: Long,
    serverTimeshiftCoversEvent: Boolean,
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
    )
    LaunchedEffect(event.eventId, actions) { initialFocus.requestFocus() }
    BackHandler(onBack = onClose)
    DialogScrim {
        Text(
            text = event.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${channel?.name.orEmpty()} • ${event.start.formatDateTime()}–${
                formatHm(event.stop)
            }",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(
                R.string.epg_time_duration,
                formatHm(event.start),
                formatHm(event.stop),
                ((event.stop - event.start).coerceAtLeast(0L) / 60L),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        programmeMetadata(event)?.let {
            Text(it, style = MaterialTheme.typography.titleSmall)
        }
        event.summary?.takeIf { it.isNotBlank() }?.let {
            Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        event.description
            ?.takeIf { it.isNotBlank() && it != event.summary }
            ?.let { Text(it, maxLines = 5, overflow = TextOverflow.Ellipsis) }
        recording?.let {
            Text(
                text = stringResource(R.string.recording_status, dvrStateLabel(it.state)),
                color = MaterialTheme.colorScheme.primary,
            )
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
        Text(
            text = if (actions.isEmpty()) {
                stringResource(R.string.epg_no_actions)
            } else {
                stringResource(R.string.epg_details_action_hint)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        }
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
    BackHandler(onBack = onDismiss)
    DialogScrim {
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
private fun ConfirmProgrammeActionDialog(
    action: ProgrammeAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(action) { confirmFocus.requestFocus() }
    BackHandler(onBack = onDismiss)
    DialogScrim {
        Text(
            text = stringResource(
                if (action == ProgrammeAction.RECORD) {
                    R.string.record_confirm_title
                } else {
                    R.string.cancel_recording_confirm_title
                }
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(
                if (action == ProgrammeAction.RECORD) {
                    R.string.record_confirm_message
                } else {
                    R.string.cancel_recording_confirm_message
                }
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onConfirm,
                modifier = Modifier.focusRequester(confirmFocus),
            ) {
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
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.back))
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
            DvrActionFailure.CONFLICT -> R.string.recording_action_conflict
            DvrActionFailure.REJECTED -> R.string.recording_action_rejected
            DvrActionFailure.CONNECTION -> R.string.recording_action_connection
        }
    }
)

private fun programmeMetadata(event: EpgEventEntry): String? = buildList {
    event.genre?.takeIf(String::isNotBlank)?.let(::add)
    if (event.seasonNumber != null || event.episodeNumber != null) {
        add(
            buildString {
                event.seasonNumber?.let { append("S$it") }
                event.episodeNumber?.let {
                    if (isNotEmpty()) append(" ")
                    append("E$it")
                    event.episodeCount?.let { count -> append("/$count") }
                }
            }
        )
    }
    if (event.partNumber != null) {
        add(
            buildString {
                append("Part ${event.partNumber}")
                event.partCount?.let { append("/$it") }
            }
        )
    }
}.takeIf { it.isNotEmpty() }?.joinToString(" • ")

@Composable
private fun DialogScrim(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.width(720.dp),
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

@Composable
private fun GuideEmptyState(
    isEmptyTag: Boolean,
    connectionUiState: ConnectionUiState,
    onRetry: () -> Unit,
) {
    val message = if (isEmptyTag) {
        stringResource(R.string.empty_channel_tag)
    } else {
        stringResource(
            when (connectionUiState) {
                ConnectionUiState.Connecting,
                ConnectionUiState.SyncingChannels -> R.string.epg_loading
                ConnectionUiState.Reconnecting -> R.string.epg_reconnecting
                is ConnectionUiState.Error -> R.string.epg_server_failure
                else -> R.string.no_channels_available
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
            if (connectionUiState is ConnectionUiState.Error) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

private fun nearestTargetAt(
    channels: List<ChannelUi>,
    repository: TvhRepository,
    preferredChannelIndex: Int,
    targetSec: Long,
): EpgFocusTarget? {
    if (channels.isEmpty()) return null
    val channelIndex = preferredChannelIndex.coerceIn(channels.indices)
    val event = repository.epgForChannel(channels[channelIndex].id).value
        .minByOrNull { event ->
            when {
                event.start <= targetSec && targetSec < event.stop -> 0L
                else -> kotlin.math.abs(event.start - targetSec)
            }
        } ?: return null
    return EpgFocusTarget(channelIndex, event.eventId)
}

private fun overlapSeconds(left: EpgEventEntry, right: EpgEventEntry): Long =
    max(0L, minOf(left.stop, right.stop) - max(left.start, right.start))

private fun minutesBetween(startSec: Long, stopSec: Long): Float =
    (stopSec - startSec).coerceAtLeast(0L) / 60f

private fun floorToHour(epochSec: Long): Long = epochSec - epochSec.mod(3600L)

private fun Long.formatDateTime(): String = Instant.ofEpochSecond(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm"))
