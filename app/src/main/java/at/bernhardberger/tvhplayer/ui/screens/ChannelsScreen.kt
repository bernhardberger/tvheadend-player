package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.activeRecordingChannelIds
import at.bernhardberger.tvhplayer.core.browsingFocusChannelId
import at.bernhardberger.tvhplayer.core.channelNowStatus
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.ConnectionRecoveryAction
import at.bernhardberger.tvhplayer.core.forEmptyChannelPresentation
import at.bernhardberger.tvhplayer.core.primaryRecoveryAction
import at.bernhardberger.tvhplayer.core.shouldPresentEmptyTag
import at.bernhardberger.tvhplayer.core.shouldRequestEmptyChannelsAction
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.core.programmeSummaryText
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.common.programmeMetadata
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.subscriptionFailureMessageResource
import at.bernhardberger.tvhplayer.ui.components.ChannelRow
import at.bernhardberger.tvhplayer.ui.components.ChannelTagSelector
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.TopLevelBrowseHeader
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.ProgressStrip
import at.bernhardberger.tvhplayer.ui.components.UnavailableTagNotice
import at.bernhardberger.tvhplayer.ui.TvPanelBrowseAlpha
import at.bernhardberger.tvhplayer.playback.LivePlaybackSelection
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

internal fun channelsBrowseViewportPadding(
    contentPadding: PaddingValues,
    layoutDirection: LayoutDirection,
): PaddingValues = PaddingValues(
    start = contentPadding.calculateStartPadding(layoutDirection),
    end = 0.dp,
)

internal fun channelsDetailPanePadding(
    contentPadding: PaddingValues,
    layoutDirection: LayoutDirection,
): PaddingValues = PaddingValues(
    end = contentPadding.calculateEndPadding(layoutDirection),
)

internal fun channelLazyItemKey(channelId: ChannelId): Long = channelId.value

internal fun channelLazyItemMatches(key: Any?, channelId: ChannelId): Boolean =
    key == channelLazyItemKey(channelId)

internal fun restoredChannelId(
    visibleChannelIds: List<ChannelId>,
    rememberedChannelId: ChannelId?,
    selectedChannelId: ChannelId?,
): ChannelId? = rememberedChannelId?.takeIf(visibleChannelIds::contains)
    ?: selectedChannelId?.takeIf(visibleChannelIds::contains)
    ?: visibleChannelIds.firstOrNull()

private const val CHANNEL_RESTORE_LAYOUT_FRAMES = 4

private suspend fun LazyListState.awaitVisibleChannel(channelId: ChannelId): Boolean {
    repeat(CHANNEL_RESTORE_LAYOUT_FRAMES) {
        if (layoutInfo.visibleItemsInfo.any { channelLazyItemMatches(it.key, channelId) }) {
            return true
        }
        withFrameNanos { }
    }
    return layoutInfo.visibleItemsInfo.any { channelLazyItemMatches(it.key, channelId) }
}

@Composable
fun ChannelsScreen(
    contentPadding: PaddingValues = PaddingValues(),
    initialFocusEnabled: Boolean = true,
    channelViewModel: ChannelsViewModel = koinViewModel(),
    selection: ChannelSelectionStore = koinInject(),
    imageLoader: ImageLoader = koinInject(),
    playingChannelId: ChannelId?,
    connectionUiState: ConnectionUiState,
    onRetryConnection: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onPlay: (selection: LivePlaybackSelection, channelName: String) -> Unit
) {
    val channelScopeState by channelViewModel.scope.collectAsStateWithLifecycle()
    val observation by channelViewModel.observation.collectAsStateWithLifecycle()
    val tagNotice by channelViewModel.unavailableTagNotice.collectAsStateWithLifecycle()
    val selectedId by selection.selectedId.collectAsStateWithLifecycle()

    ChannelsScreenContent(
        contentPadding = contentPadding,
        initialFocusEnabled = initialFocusEnabled,
        channelScopeState = channelScopeState,
        observation = observation,
        tagNotice = tagNotice,
        selectedId = selectedId,
        imageLoader = imageLoader,
        playingChannelId = playingChannelId,
        connectionUiState = connectionUiState,
        onSelectChannel = selection::setSelected,
        onSelectTag = channelViewModel::selectTag,
        onDismissTagNotice = channelViewModel::dismissUnavailableTagNotice,
        onRetryConnection = onRetryConnection,
        onOpenConnectionSettings = onOpenConnectionSettings,
        onPlay = onPlay,
    )
}

@Composable
internal fun ChannelsScreenContent(
    contentPadding: PaddingValues = PaddingValues(),
    initialFocusEnabled: Boolean = true,
    channelScopeState: ChannelScopeState,
    observation: SessionObservation,
    tagNotice: Boolean,
    selectedId: ChannelId?,
    imageLoader: ImageLoader,
    playingChannelId: ChannelId?,
    connectionUiState: ConnectionUiState,
    onSelectChannel: (ChannelId) -> Unit,
    onSelectTag: (ChannelTagId?) -> Unit,
    onDismissTagNotice: () -> Unit,
    onRetryConnection: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onPlay: (selection: LivePlaybackSelection, channelName: String) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = contentPadding.calculateStartPadding(layoutDirection)
    val endPadding = contentPadding.calculateEndPadding(layoutDirection)
    val browseViewportPadding = channelsBrowseViewportPadding(
        contentPadding = contentPadding,
        layoutDirection = layoutDirection,
    )
    val detailPanePadding = channelsDetailPanePadding(
        contentPadding = contentPadding,
        layoutDirection = layoutDirection,
    )
    val channelScope = channelScopeState.scope
    val currentSession = observation.currentSession
    val dvrEntries = observation.dvrSnapshotForDisplay?.entries.orEmpty()
    val recordingChannelIds = remember(dvrEntries) { activeRecordingChannelIds(dvrEntries) }
    val channels = channelScope.visibleChannels
    val orderedChannelIds = remember(channels) { channels.map { it.id } }
    val rowFocusRequesters = remember(orderedChannelIds) {
        orderedChannelIds.associateWith { FocusRequester() }
    }
    val channelNumbers = remember(channels) {
        channels.associate { it.id to it.number?.toInt() }
    }
    var didInitialRestore by remember { mutableStateOf(false) }
    var focusedChannelId by remember { mutableStateOf<ChannelId?>(null) }
    var contentFocusOwned by remember { mutableStateOf(false) }
    val rememberedChannelIds = remember {
        mutableStateMapOf<ChannelTagId?, ChannelId>()
    }
    var restorationGeneration by remember { mutableIntStateOf(0) }
    var restorationJob by remember { mutableStateOf<Job?>(null) }
    var pendingFocusId by remember { mutableStateOf<ChannelId?>(null) }
    var pendingFocusTagId by remember { mutableStateOf<ChannelTagId?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    fun cancelRestoration() {
        restorationGeneration++
        restorationJob?.cancel()
        restorationJob = null
        pendingFocusId = null
        pendingFocusTagId = null
        isRestoring = false
    }

    fun requestChannelFocus(channelId: ChannelId): Boolean {
        val index = orderedChannelIds.indexOf(channelId)
        val requester = rowFocusRequesters[channelId]
        if (index < 0 || requester == null) {
            cancelRestoration()
            return false
        }

        val tagId = channelScope.activeTagId
        val generation = ++restorationGeneration
        restorationJob?.cancel()
        pendingFocusId = channelId
        pendingFocusTagId = tagId
        isRestoring = true
        restorationJob = coroutineScope.launch {
            try {
                listState.scrollToItem(index)
                if (!listState.awaitVisibleChannel(channelId)) return@launch
                withFrameNanos { }
                if (runCatching(requester::requestFocus).getOrDefault(false)) {
                    focusedChannelId = channelId
                    rememberedChannelIds[tagId] = channelId
                    onSelectChannel(channelId)
                }
            } finally {
                if (restorationGeneration == generation) {
                    restorationJob = null
                    pendingFocusId = null
                    pendingFocusTagId = null
                    isRestoring = false
                }
            }
        }
        return true
    }

    fun relinquishContentFocus(clearFocusedChannel: Boolean = false) {
        contentFocusOwned = false
        if (clearFocusedChannel) focusedChannelId = null
        cancelRestoration()
    }

    fun focusBrowseContent(): Boolean {
        val id = restoredChannelId(
            visibleChannelIds = orderedChannelIds,
            rememberedChannelId = rememberedChannelIds[channelScope.activeTagId],
            selectedChannelId = selectedId,
        ) ?: return false
        contentFocusOwned = true
        return requestChannelFocus(id)
    }

    fun pageChannels(direction: Int): Boolean {
        val currentId = pendingFocusId
            ?.takeIf { pendingFocusTagId == channelScope.activeTagId }
            ?: focusedChannelId?.takeIf(orderedChannelIds::contains)
            ?: selectedId
        val currentIndex = orderedChannelIds.indexOf(currentId)
        val visibleCount = listState.layoutInfo.visibleItemsInfo.size
        val targetIndex = ChannelNavigation.pageTargetIndex(
            itemCount = channels.size,
            currentIndex = currentIndex,
            visibleItemCount = visibleCount,
            direction = direction,
        ) ?: return true
        if (targetIndex == currentIndex) return true

        val targetId = channels[targetIndex].id
        contentFocusOwned = true
        onSelectChannel(targetId)
        requestChannelFocus(targetId)
        return true
    }

    val detailChannelId = focusedChannelId?.takeIf(orderedChannelIds::contains)
        ?: browsingFocusChannelId(channels, selectedId)
    val focusedChannel = channels.firstOrNull { it.id == detailChannelId }
    val focusedNow = remember(observation, focusedChannel?.id, nowSec) {
        focusedChannel?.id?.let {
            observation.eventAt(it, kotlin.time.Instant.fromEpochSeconds(nowSec))
        }
    }
    val focusedNext = remember(observation, focusedChannel?.id, nowSec) {
        focusedChannel?.id?.let {
            observation.nextEvent(it, kotlin.time.Instant.fromEpochSeconds(nowSec))
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowSec = System.currentTimeMillis() / 1000L
            delay(5000L)
        }
    }

    LaunchedEffect(channels, selectedId) {
        val focusId = browsingFocusChannelId(channels, selectedId) ?: return@LaunchedEffect
        if (focusId != selectedId) onSelectChannel(focusId)
    }

    LaunchedEffect(channelScope.activeTagId, orderedChannelIds, initialFocusEnabled) {
        val pendingId = pendingFocusId?.takeIf {
            pendingFocusTagId == channelScope.activeTagId && it in orderedChannelIds
        }
        cancelRestoration()
        if (orderedChannelIds.isEmpty()) {
            focusedChannelId = null
            return@LaunchedEffect
        }

        if (!didInitialRestore && initialFocusEnabled) {
            didInitialRestore = true
            contentFocusOwned = true
            val id = restoredChannelId(
                visibleChannelIds = orderedChannelIds,
                rememberedChannelId = rememberedChannelIds[channelScope.activeTagId],
                selectedChannelId = selectedId,
            ) ?: return@LaunchedEffect
            if (selectedId != id) onSelectChannel(id)
            requestChannelFocus(id)
            return@LaunchedEffect
        }

        if (pendingId != null) {
            requestChannelFocus(pendingId)
            return@LaunchedEffect
        }

        if (contentFocusOwned && focusedChannelId !in orderedChannelIds) {
            val id = restoredChannelId(
                visibleChannelIds = orderedChannelIds,
                rememberedChannelId = rememberedChannelIds[channelScope.activeTagId],
                selectedChannelId = selectedId,
            ) ?: return@LaunchedEffect
            requestChannelFocus(id)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
    ) {
        TopLevelBrowseHeader(
            title = stringResource(R.string.channel_list),
            modifier = Modifier.padding(start = startPadding, end = endPadding),
        )
        if (channelScope.tags.size + (if (channelScope.allChannelsVisible) 1 else 0) > 1) {
            Spacer(Modifier.height(TvSpacing8))
            ChannelTagSelector(
                tags = channelScope.tags,
                activeTagId = channelScope.activeTagId,
                onSelectTag = {
                    relinquishContentFocus(clearFocusedChannel = true)
                    onSelectTag(it)
                },
                onMoveToContent = ::focusBrowseContent,
                onTagFocus = ::relinquishContentFocus,
                allChannelsVisible = channelScope.allChannelsVisible,
                modifier = Modifier
                    .padding(browseViewportPadding)
                    .fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(TvSpacing16))

        if (channels.isEmpty()) {
            if (
                shouldPresentEmptyTag(
                    channelCatalogCurrent = channelScopeState.channelCatalogCurrent,
                    connectionState = connectionUiState,
                    hasChannelsOutsideActiveTag = channelScope.allChannels.isNotEmpty(),
                    activeTagSelected = channelScope.activeTagId != null,
                )
            ) {
                EmptyTagState(
                    Modifier
                        .padding(browseViewportPadding)
                        .fillMaxSize(),
                )
            } else {
                EmptyChannelsState(
                    state = connectionUiState.forEmptyChannelPresentation(
                        channelCatalogCurrent = channelScopeState.channelCatalogCurrent,
                    ),
                    initialFocusEnabled = initialFocusEnabled,
                    onRetry = onRetryConnection,
                    onOpenSettings = onOpenConnectionSettings,
                    modifier = Modifier
                        .padding(browseViewportPadding)
                        .fillMaxSize(),
                )
            }
            return@Column
        }

        if (connectionUiState != ConnectionUiState.Ready) {
            InlineConnectionState(
                state = connectionUiState,
                onRetry = onRetryConnection,
                onOpenSettings = onOpenConnectionSettings,
                modifier = Modifier.padding(start = startPadding, end = endPadding),
            )
            Spacer(Modifier.height(12.dp))
        }

        Row(
                Modifier
                    .padding(browseViewportPadding)
                    .fillMaxSize(),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(
                            alpha = TvPanelBrowseAlpha
                        ),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.44f)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        UnavailableTagNotice(
                            visible = tagNotice,
                            onDismiss = onDismissTagNotice,
                        )
                        if (tagNotice) Spacer(Modifier.height(8.dp))

                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .focusGroup()
                                .focusRestorer()
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) {
                                        return@onPreviewKeyEvent false
                                    }
                                    val exitsTowardNavigation = when (layoutDirection) {
                                        LayoutDirection.Ltr -> event.key == Key.DirectionLeft
                                        LayoutDirection.Rtl -> event.key == Key.DirectionRight
                                    }
                                    if (exitsTowardNavigation) relinquishContentFocus()
                                    ChannelNavigation.pageDirectionForKeyCode(
                                        event.nativeKeyEvent.keyCode
                                    )?.let(::pageChannels) ?: false
                                }
                        ) {
                            items(channels, key = { ch -> channelLazyItemKey(ch.id) }) { ch ->
                                val channelId = ch.id
                                val now =
                                    remember(channelId, observation, nowSec) {
                                        observation.eventAt(
                                            channelId,
                                            kotlin.time.Instant.fromEpochSeconds(nowSec),
                                        )
                                    }
                                val prog = remember(now, nowSec) { now?.progress(nowSec) ?: 0f }
                                val status = channelNowStatus(
                                    channelId = channelId,
                                    playingChannelId = playingChannelId,
                                    recordingChannelIds = recordingChannelIds,
                                )

                                ChannelRow(
                                    modifier = Modifier
                                        .focusRequester(rowFocusRequesters.getValue(channelId))
                                        .testTag("channel-row-${channelId.value}"),
                                    number = ChannelNavigation.numberForId(
                                        orderedChannelIds,
                                        channelNumbers,
                                        channelId,
                                    ),
                                    name = ch.name.orEmpty(),
                                    programTitle = now?.title ?: stringResource(R.string.no_epg),
                                    progress = if (now != null) prog else null,
                                    imageLoader = imageLoader,
                                    currentSession = currentSession,
                                    piconPath = ch.icon,
                                    recordingNow = status.recordingNow,
                                    playingNow = status.playingNow,
                                    onFocus = {
                                        focusedChannelId = channelId
                                        rememberedChannelIds[channelScope.activeTagId] = channelId
                                        contentFocusOwned = true
                                        if (isRestoring && channelId != pendingFocusId) {
                                            cancelRestoration()
                                        }
                                        if (!isRestoring) {
                                            onSelectChannel(channelId)
                                        }
                                    },
                                    onConfirm = {
                                        currentSession?.let {
                                            onPlay(
                                                LivePlaybackSelection(it, channelId),
                                                ch.name.orEmpty(),
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(24.dp))

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(
                            alpha = TvPanelBrowseAlpha
                        ),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .weight(0.56f)
                        .padding(detailPanePadding)
                        .fillMaxHeight(),
                ) {
                    EpgDetailPane(
                        channelName = focusedChannel?.name ?: "—",
                        now = focusedNow,
                        nowSec = nowSec,
                        next = focusedNext,
                        imageLoader = imageLoader,
                        currentSession = currentSession,
                        piconPath = focusedChannel?.icon
                    )
                }
            }
    }
}

@Composable
private fun EmptyTagState(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = TvPanelBrowseAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.empty_channel_tag),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.widthIn(max = 680.dp),
            )
        }
    }
}

@Composable
private fun EmptyChannelsState(
    state: ConnectionUiState,
    initialFocusEnabled: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionFocus = remember { FocusRequester() }
    val recoveryAction = state.primaryRecoveryAction()
    val hasPrimaryAction = recoveryAction != ConnectionRecoveryAction.NONE

    LaunchedEffect(state, initialFocusEnabled, hasPrimaryAction) {
        if (shouldRequestEmptyChannelsAction(initialFocusEnabled, hasPrimaryAction)) {
            withFrameNanos { }
            actionFocus.requestFocus()
        }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = TvPanelBrowseAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            DelayedConnectionProgress(
                visible = state.isConnectionProgress(),
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = connectionMessage(state),
                style = MaterialTheme.typography.titleLarge,
                color = if (state.isError()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .padding(top = if (state.isConnectionProgress()) 20.dp else 0.dp)
                    .widthIn(max = 680.dp),
            )

            when (recoveryAction) {
                ConnectionRecoveryAction.SETTINGS -> {
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .focusRequester(actionFocus),
                    ) {
                        Text(stringResource(R.string.open_connection_settings))
                    }
                }

                ConnectionRecoveryAction.RETRY -> {
                    Row(
                        modifier = Modifier.padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.focusRequester(actionFocus),
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                        OutlinedButton(onClick = onOpenSettings) {
                            Text(stringResource(R.string.open_connection_settings))
                        }
                    }
                }

                ConnectionRecoveryAction.NONE -> Unit
            }
        }
    }
}

@Composable
private fun InlineConnectionState(
    state: ConnectionUiState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recoveryAction = state.primaryRecoveryAction()
    Surface(
        shape = MaterialTheme.shapes.medium,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DelayedConnectionProgress(
                visible = state.isConnectionProgress(),
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = connectionMessage(state),
                style = MaterialTheme.typography.bodyLarge,
                color = if (state.isError()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (recoveryAction == ConnectionRecoveryAction.RETRY) {
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                OutlinedButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.connection_settings_short))
                }
            } else if (recoveryAction == ConnectionRecoveryAction.SETTINGS) {
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.connection_settings_short))
                }
            }
        }
    }
}

@Composable
private fun DelayedConnectionProgress(
    visible: Boolean,
    modifier: Modifier,
) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        show = false
        if (visible) {
            delay(400L)
            show = true
        }
    }
    if (show) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )
    }
}

private fun ConnectionUiState.isConnectionProgress(): Boolean =
    this == ConnectionUiState.Connecting ||
        this == ConnectionUiState.SyncingChannels ||
        this == ConnectionUiState.Reconnecting

private fun ConnectionUiState.isError(): Boolean =
    this is ConnectionUiState.Error ||
        this is ConnectionUiState.SubscriptionError ||
        this == ConnectionUiState.CredentialUnavailable

@Composable
private fun connectionMessage(state: ConnectionUiState): String = stringResource(
    when (state) {
        ConnectionUiState.NeedsConfiguration -> R.string.connection_configuration_required
        ConnectionUiState.Connecting -> R.string.connection_connecting
        ConnectionUiState.SyncingChannels -> R.string.connection_loading_channels
        ConnectionUiState.Ready -> R.string.no_channels_available
        ConnectionUiState.Reconnecting -> R.string.status_disconnected_reconnecting
        ConnectionUiState.CredentialUnavailable -> R.string.credential_unavailable
        is ConnectionUiState.Error -> when (state.kind) {
            ConnectionFailureKind.AUTHENTICATION -> R.string.status_connection_failed_authentication
            ConnectionFailureKind.DNS -> R.string.status_connection_failed_dns
            ConnectionFailureKind.UNREACHABLE -> R.string.status_connection_failed_unreachable
            ConnectionFailureKind.TIMEOUT -> R.string.status_connection_failed_timeout
            ConnectionFailureKind.INCOMPATIBLE_SERVER ->
                R.string.status_connection_failed_incompatible
            ConnectionFailureKind.PERMISSION_DENIED ->
                R.string.status_connection_failed_permission
            ConnectionFailureKind.ZERO_CHANNELS -> R.string.status_connection_failed_zero_channels
            ConnectionFailureKind.OTHER -> R.string.status_connection_failed_other
        }
        is ConnectionUiState.SubscriptionError ->
            subscriptionFailureMessageResource(state.kind)
    }
)

@Composable
private fun EpgDetailPane(
    channelName: String,
    now: EpgEventEntry?,
    next: EpgEventEntry?,
    nowSec: Long,
    imageLoader: ImageLoader,
    currentSession: at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation?,
    piconPath: String? = null,
) {
    val progress = remember(now, nowSec) { now?.progress(nowSec) ?: 0f }
    val summaryText = remember(now) { now?.let { programmeSummaryText(it) } }
    val metadata = remember(now) { now?.let { programmeMetadata(it) } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("channels-detail-channel"),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = now?.title ?: stringResource(R.string.no_epg),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .height(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                PiconBox(
                    imageLoader = imageLoader,
                    currentSession = currentSession,
                    piconPath = piconPath,
                    modifier = Modifier.width(92.dp).height(64.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (now != null) {
            val start = now.start.epochSeconds
            val end = now.stop.epochSeconds
            val durMin = ((end - start) / 60).coerceAtLeast(0)
            Text(
                text = stringResource(
                    R.string.epg_time_duration,
                    formatHm(start),
                    formatHm(end),
                    durMin.toInt(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            ProgressStrip(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
            )
            if (metadata != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summaryText != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (next != null) {
            Text(
                text = stringResource(R.string.epg_next, formatHm(next.start.epochSeconds)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = next.title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
