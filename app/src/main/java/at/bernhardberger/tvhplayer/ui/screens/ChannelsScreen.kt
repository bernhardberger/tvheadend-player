package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout

import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.components.channelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.rememberPlaybackIntent

import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import kotlin.math.roundToInt

import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.profiling.profileTrace
import at.bernhardberger.tvhplayer.profiling.profileLayout

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.components.rememberTabGridState
import at.bernhardberger.tvhplayer.ui.components.CompactZapRowOffscreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Button
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusEnterExitScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.core.activeRecordingChannelIds
import at.bernhardberger.tvhplayer.core.channelNowStatus
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.browseAppError
import at.bernhardberger.tvhplayer.core.awaitsGuide
import at.bernhardberger.tvhplayer.core.forEmptyChannelPresentation
import at.bernhardberger.tvhplayer.core.shouldPresentEmptyTag
import at.bernhardberger.tvhplayer.core.shouldRequestEmptyChannelsAction
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.components.ChannelRow
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy
import at.bernhardberger.tvhplayer.ui.components.AppErrorAlert
import at.bernhardberger.tvhplayer.ui.components.TabContent
import at.bernhardberger.tvhplayer.ui.components.tabFocus
import at.bernhardberger.tvhplayer.ui.components.rememberTabListState
import at.bernhardberger.tvhplayer.ui.components.rememberTabReader
import at.bernhardberger.tvhplayer.ui.components.rememberTabContentMotion
import at.bernhardberger.tvhplayer.ui.components.ChannelRowEdgeInset
import at.bernhardberger.tvhplayer.ui.components.ListBottomReserve
import at.bernhardberger.tvhplayer.ui.components.listEdgeFades
import at.bernhardberger.tvhplayer.ui.components.rememberListEdgeBringIntoView
import at.bernhardberger.tvhplayer.ui.components.ChannelRowGap
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseDrawerState
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus
import at.bernhardberger.tvhplayer.ui.components.ProgrammePanePictureGap
import at.bernhardberger.tvhplayer.ui.components.ProgrammePaneWidth
import at.bernhardberger.tvhplayer.ui.components.UnavailableTagNotice
import at.bernhardberger.tvhplayer.ui.TvPanelBrowseAlpha
import at.bernhardberger.tvhplayer.ui.TvBrowseHeaderHeight
import at.bernhardberger.tvhplayer.ui.TvBrowseLeadingInset
import at.bernhardberger.tvhplayer.ui.TvBrowseTrailingInset
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

internal fun channelsBrowseViewportPadding(
    contentPadding: PaddingValues,
    layoutDirection: LayoutDirection,
): PaddingValues = PaddingValues(
    start = contentPadding.calculateStartPadding(layoutDirection) + TvBrowseLeadingInset - ChannelRowEdgeInset,
    end = 0.dp,
)

/**
 * The details column keeps the trailing and bottom safe insets; the list column beside
 * it deliberately runs to the screen bottom beneath its fade.
 */
internal fun channelsDetailPanePadding(
    contentPadding: PaddingValues,
    layoutDirection: LayoutDirection,
): PaddingValues = PaddingValues(
    end = maxOf(contentPadding.calculateEndPadding(layoutDirection), TvBrowseTrailingInset),
    bottom = contentPadding.calculateBottomPadding(),
)

private const val CHANNEL_EMPTY_GROUP_FOCUS_FRAMES = 4

internal fun channelLazyItemKey(channelId: ChannelId): Long = channelId.value

internal fun channelLazyItemMatches(key: Any?, channelId: ChannelId): Boolean =
    key == channelLazyItemKey(channelId)

/**
 * Browse focus requested by Back from the live player: the channel playing when the
 * player closed, and the channel whose row opened it (the previously focused row).
 */
data class PlayerReturnFocus(
    val playingChannelId: ChannelId,
    val originChannelId: ChannelId,
    val programmeId: at.bernhardberger.tvheadend.sdk.core.EventId? = null,
    val programmeStartSec: Long? = null,
)

internal fun restoredChannelId(
    visibleChannelIds: List<ChannelId>,
    rememberedChannelId: ChannelId?,
    selectedChannelId: ChannelId?,
): ChannelId? = rememberedChannelId?.takeIf(visibleChannelIds::contains)
    ?: selectedChannelId?.takeIf(visibleChannelIds::contains)
    ?: visibleChannelIds.firstOrNull()

@Composable
fun ChannelsScreen(
    contentPadding: PaddingValues = PaddingValues(),
    initialFocusEnabled: Boolean = true,
    channelViewModel: ChannelsViewModel,
    selection: ChannelSelectionStore = koinInject(),
    playingChannelId: ChannelId?,
    playerReturn: PlayerReturnFocus? = null,
    view: ChannelsView = ChannelsView(),
    connectionUiState: ConnectionUiState,
    connectionFailure: ConnectionUiState? = connectionUiState,
    onRetryConnection: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onPlay: (channelId: ChannelId, channelName: String) -> Unit,
    onPlayRecording: ((at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection) -> Unit)? = null,
    onShowGuide: (ChannelId, at.bernhardberger.tvheadend.sdk.core.EpgEvent?) -> Unit = { _, _ -> },
) {
    at.bernhardberger.tvhplayer.profiling.ProfileCompositionLifetime("channels")
    val channelScopeState = channelViewModel.scope.collectAsStateWithLifecycle().value
    val observation by channelViewModel.observation.collectAsStateWithLifecycle()
    val tagNotice by channelViewModel.unavailableTagNotice.collectAsStateWithLifecycle()
    val selectedId = selection.selectedId.collectAsStateWithLifecycle()
    val playbackRuntime: AppPlaybackRuntime = koinInject()
    val playbackState by playbackRuntime.state.collectAsStateWithLifecycle()
    val playbackTarget by playbackRuntime.activeTarget.collectAsStateWithLifecycle()
    val playWhenReady by rememberPlaybackIntent(playbackRuntime.player)
    val playbackChannelId = (playbackTarget as? AppPlaybackTarget.Live)?.channelId
    val playbackIndicator = playbackChannelId?.let {
        channelPlaybackIndicator(it, playbackTarget, playbackState, playWhenReady)
    } ?: ChannelPlaybackIndicator.NONE
    // The view is the screen's own, remembered for the whole app rather than per channel group.
    val settingsStore: UiSettingsStore = koinInject()
    val settingsScope = rememberCoroutineScope()

    ChannelsScreenContent(
        contentPadding = contentPadding,
        initialFocusEnabled = initialFocusEnabled,
        channelScopeState = channelScopeState,
        observation = observation,
        tagNotice = tagNotice,
        selectedId = { selectedId.value },
        playingChannelId = playingChannelId,
        playerReturn = playerReturn,
        view = view,
        onViewChange = { settingsScope.launch { settingsStore.setChannelsView(it) } },
        playbackChannelId = playbackChannelId,
        playbackIndicator = playbackIndicator,
        connectionUiState = connectionUiState,
        connectionFailure = connectionFailure,
        onSelectChannel = selection::setSelected,
        onSelectTag = channelViewModel::selectTag,
        scopeStateProvider = { channelViewModel.scope.value },
        onDismissTagNotice = channelViewModel::dismissUnavailableTagNotice,
        onRetryConnection = onRetryConnection,
        onOpenConnectionSettings = onOpenConnectionSettings,
        onPlay = onPlay,
        onPlayRecording = onPlayRecording,
        onShowGuide = onShowGuide,
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ChannelsScreenContent(
    contentPadding: PaddingValues = PaddingValues(),
    initialFocusEnabled: Boolean = true,
    channelScopeState: ChannelScopeState,
    scopeStateProvider: () -> ChannelScopeState = { channelScopeState },
    observation: SessionObservation,
    tagNotice: Boolean,
    selectedId: () -> ChannelId?,
    playingChannelId: ChannelId?,
    playerReturn: PlayerReturnFocus? = null,
    view: ChannelsView = ChannelsView(),
    onViewChange: (ChannelsView) -> Unit = {},
    playbackChannelId: ChannelId? = playingChannelId,
    playbackIndicator: ChannelPlaybackIndicator = if (playingChannelId != null) ChannelPlaybackIndicator.PLAYING else ChannelPlaybackIndicator.NONE,
    connectionUiState: ConnectionUiState,
    connectionFailure: ConnectionUiState? = connectionUiState,
    onSelectChannel: (ChannelId) -> Unit,
    onSelectTag: (ChannelTagId?) -> Unit,
    onDismissTagNotice: () -> Unit,
    onRetryConnection: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onPlay: (channelId: ChannelId, channelName: String) -> Unit,
    onPlayRecording: ((at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection) -> Unit)? = null,
    onShowGuide: (ChannelId, at.bernhardberger.tvheadend.sdk.core.EpgEvent?) -> Unit = { _, _ -> },
) = profileTrace("P1:compose:channels") {
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
    val layout = view.layout
    val channelScope = channelScopeState.scope
    val scopeMotion = rememberTabContentMotion(channelScope.activeTagId) {
        scopeStateProvider().scope.activeTagId
    }
    val groups = remember(channelScope) { channelsGroups(channelScope) }
    val header = remember { ChannelsHeaderFocus() }
    val headerEntryFocus = header.entry(hasGroupChoice = groups.size > 1)
    var openPanel by remember { mutableStateOf<ChannelsPanel?>(null) }
    val dvrEntries = observation.dvrSnapshotForDisplay?.entries.orEmpty()
    val recordingChannelIds = remember(dvrEntries) { activeRecordingChannelIds(dvrEntries) }
    val channels = channelScope.visibleChannels
    val orderedChannelIds = remember(channels) { channels.map { it.id } }
    val rowFocusRequesters = remember(orderedChannelIds) {
        orderedChannelIds.associateWith { FocusRequester() }
    }
    val channelNumbers = remember(channels) {
        channels.associate { it.id to it.visibleChannelNumber }
    }
    var didInitialRestore by remember { mutableStateOf(false) }
    // An alert replaced by channels while focused hands focus to them, whatever took it meanwhile:
    // its exit can outlast the first restore, which the fallback focus then cancels.
    var restoreAfterAlert by remember { mutableStateOf(false) }
    var alertsGoneWithFocus by remember { mutableIntStateOf(0) }
    // One-shot per player return; AppRoot clears the request when the destination changes.
    var playerReturnPending by remember(playerReturn) { mutableStateOf(playerReturn != null) }
    var focusedChannelId by remember { mutableStateOf<ChannelId?>(null) }
    var contextChannelId by remember { mutableStateOf<ChannelId?>(null) }
    var showProgrammeDetails by remember { mutableStateOf(false) }
    var contentFocusOwned by remember { mutableStateOf(false) }
    var scopeEntryRequested by remember { mutableStateOf(false) }
    val rememberedChannelIds = remember {
        mutableStateMapOf<ChannelTagId?, ChannelId>()
    }
    var positionedTagId by remember { mutableStateOf(channelScope.activeTagId) }
    var awaitingTagChannels by remember { mutableStateOf(false) }
    var tagResetGeneration by remember { mutableIntStateOf(0) }
    var restorationGeneration by remember { mutableIntStateOf(0) }
    var restorationJob by remember { mutableStateOf<Job?>(null) }
    var pendingFocusId by remember { mutableStateOf<ChannelId?>(null) }
    var pendingFocusTagId by remember { mutableStateOf<ChannelTagId?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }

    val listState = rememberLazyListState()
    // Native focus scrolling and the explicit preview anchor share one viewport policy:
    // the readable band between the fades, with the original reserve at the top boundary.
    val bringIntoViewSpec = rememberListEdgeBringIntoView(listState, ChannelsListTopReserve)
    val density = LocalDensity.current
    val gridState = rememberLazyGridState()
    val rowState = rememberLazyListState()
    val rowOffscreenPx = with(density) { CompactZapRowOffscreen.roundToPx() }
    val coroutineScope = rememberCoroutineScope()
    val detailsMutationInFlight = remember { mutableStateOf<DvrMutationAction?>(null) }

    fun cancelRestoration() {
        restorationGeneration++
        restorationJob?.cancel()
        restorationJob = null
        pendingFocusId = null
        pendingFocusTagId = null
        isRestoring = false
    }

    val contentEntryEnabled by rememberUpdatedState(initialFocusEnabled)
    val currentScope by rememberUpdatedState(scopeStateProvider)
    val latestRenderedScope by rememberUpdatedState(channelScopeState)

    fun matchesRequestedRows(state: ChannelScopeState): Boolean =
        state.settingsLoaded && state.scope.activeTagId == channelScope.activeTagId &&
            state.scope.visibleChannels.map { it.id } == orderedChannelIds
    val drawerState = LocalBrowseDrawerState.current
    val navigationFocus = LocalBrowseNavigationFocus.current

    fun scopeEntryChannelId(): ChannelId? = restoredChannelId(
        visibleChannelIds = orderedChannelIds,
        rememberedChannelId = rememberedChannelIds[channelScope.activeTagId]
            .takeIf { positionedTagId == channelScope.activeTagId && !awaitingTagChannels },
        selectedChannelId = if (positionedTagId != channelScope.activeTagId || awaitingTagChannels) {
            playingChannelId
        } else selectedId(),
    )

    fun channelFocusScrollOffset(index: Int): Int {
        val layout = listState.layoutInfo
        return bringIntoViewSpec.calculateScrollDistance(
            offset = layout.beforeContentPadding.toFloat(),
            size = (layout.visibleItemsInfo.firstOrNull()?.size ?: 0).coerceAtLeast(0).toFloat(),
            containerSize = layout.viewportSize.height.toFloat(),
            scrollBackAvailablePx = if (index == 0) 0f else Float.POSITIVE_INFINITY,
        ).roundToInt()
    }

    val viewport = remember(layout, listState, gridState, rowState, bringIntoViewSpec, rowOffscreenPx) {
        when (layout) {
            ChannelsLayout.LIST -> ChannelsListViewport(listState, ::channelFocusScrollOffset)
            ChannelsLayout.GRID -> ChannelsGridViewport(gridState, ChannelsGridColumns)
            ChannelsLayout.IMMERSIVE -> ChannelsRowViewport(rowState) { rowOffscreenPx }
        }
    }
    val currentViewport by rememberUpdatedState(viewport)
    val positionedViewport = remember { mutableStateOf<ChannelsLayoutViewport?>(null) }

    fun requestChannelFocus(
        channelId: ChannelId,
        preserveVisiblePosition: Boolean = false,
        animatePage: Boolean = false,
    ): Boolean {
        if (contextChannelId != null || openPanel != null) return false
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
                if (drawerState?.currentValue == DrawerValue.Open) return@launch
                // Re-entry must not snap an already visible row to the top before
                // focus's bring-into-view restores it. Explicit paging keeps its policy.
                if (!preserveVisiblePosition || !currentViewport.isVisible(channelId)) {
                    // Finish at the same position focus will request, rather than animating
                    // to the top and then visibly scrolling backward to the TV focus pivot.
                    currentViewport.scrollToChannel(index, animate = animatePage)
                }
                if (!currentViewport.awaitVisibleChannel(channelId)) return@launch
                withFrameNanos { }
                // Widget focus changes precede composition's drawerActive feedback.
                // A queued initial restore must not take focus back in that interval.
                if (drawerState?.currentValue == DrawerValue.Open ||
                    contextChannelId != null || openPanel != null ||
                    !contentEntryEnabled || !matchesRequestedRows(currentScope()) ||
                    !matchesRequestedRows(latestRenderedScope) ||
                    restorationGeneration != generation
                ) return@launch
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
        scopeEntryRequested = false
        contentFocusOwned = false
        if (clearFocusedChannel) focusedChannelId = null
        cancelRestoration()
    }

    /** The rail re-enters the channel focus was left on, as Down from the header does. */
    fun FocusEnterExitScope.enterFromNavigation() {
        val awayFromNavigation = if (layoutDirection == LayoutDirection.Ltr) FocusDirection.Right else FocusDirection.Left
        if (requestedFocusDirection != awayFromNavigation) return
        val requester = scopeEntryChannelId()?.let(rowFocusRequesters::get) ?: return
        runCatching { requester.requestFocus() }
    }

    fun focusHeader(): Boolean {
        relinquishContentFocus()
        return runCatching { headerEntryFocus.requestFocus() }.getOrDefault(false)
    }

    /** An empty group offers All channels when it is visible, otherwise the group button. */
    fun focusEmptyGroup(): Boolean {
        coroutineScope.launch {
            // The empty message arrives with the group's content, a few layout passes later.
            repeat(CHANNEL_EMPTY_GROUP_FOCUS_FRAMES) {
                withFrameNanos { }
                if (contextChannelId != null || openPanel != null) return@launch
                if (runCatching { header.showAll.requestFocus() }.getOrDefault(false)) return@launch
            }
            focusHeader()
        }
        return true
    }

    fun focusBrowseContent(): Boolean {
        didInitialRestore = true // Explicit Down/OK intent supersedes cold-start tag entry.
        scopeEntryRequested = true
        if (!matchesRequestedRows(scopeStateProvider())) return true
        scopeEntryRequested = false
        val id = scopeEntryChannelId() ?: return focusEmptyGroup()
        contentFocusOwned = true
        return requestChannelFocus(id, preserveVisiblePosition = true)
    }

    /**
     * Shows [tagId]'s channels. The group picker enters its remembered channel; otherwise content
     * focus is kept, so the scope publication moves it to the group's remembered channel.
     */
    fun selectGroup(tagId: ChannelTagId?, enterContent: Boolean) {
        scopeMotion.select(tagId, groups.map { it.tagId })
        if (tagId != channelScope.activeTagId) {
            // Keep a reset even if A -> B -> A coalesces before composition.
            awaitingTagChannels = true
            tagResetGeneration++
        }
        if (enterContent) relinquishContentFocus(clearFocusedChannel = true) else cancelRestoration()
        onSelectTag(tagId)
        // Entered once the group's channels are published, not against the scope still showing.
        if (enterContent) {
            didInitialRestore = true
            scopeEntryRequested = true
        }
    }

    fun showPanel(panel: ChannelsPanel) {
        relinquishContentFocus()
        openPanel = panel
    }

    /** Back and OK leave a panel with focus on the button that opened it. */
    fun closePanel() {
        val opener = if (openPanel == ChannelsPanel.GROUP) header.group else header.view
        runCatching { opener.requestFocus() }
        openPanel = null
    }

    /** Every layout's channel item reports native focus here. */
    fun onChannelFocused(channelId: ChannelId, tagId: ChannelTagId?) = profileTrace("P44:focus:channel") {
        if (BuildConfig.PROFILE_TRACE) {
            profileTrace("P48:focus:channel") { }
        }
        focusedChannelId = channelId
        rememberedChannelIds[tagId] = channelId
        contentFocusOwned = true
        if (isRestoring && channelId != pendingFocusId) {
            cancelRestoration()
        }
        if (!isRestoring) {
            onSelectChannel(channelId)
        }
    }

    fun openChannelContextMenu(channelId: ChannelId) {
        cancelRestoration()
        contextChannelId = channelId
    }

    fun pageChannels(direction: Int): Boolean {
        val currentId = pendingFocusId
            ?.takeIf { pendingFocusTagId == channelScope.activeTagId }
            ?: focusedChannelId?.takeIf(orderedChannelIds::contains)
            ?: selectedId()
        val currentIndex = orderedChannelIds.indexOf(currentId)
        val targetIndex = ChannelNavigation.pageTargetIndex(
            itemCount = channels.size,
            currentIndex = currentIndex,
            visibleItemCount = viewport.pageSize() + 1,
            direction = direction,
        ) ?: return true
        if (targetIndex == currentIndex) return true

        val targetId = channels[targetIndex].id
        contentFocusOwned = true
        requestChannelFocus(targetId, animatePage = true)
        return true
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowSec = System.currentTimeMillis() / 1000L
            delay(5000L)
        }
    }

    LaunchedEffect(
        channelScope.activeTagId, orderedChannelIds, channelScopeState.settingsLoaded,
        initialFocusEnabled, tagResetGeneration, viewport, alertsGoneWithFocus,
    ) {
        val scopeChanged = positionedTagId != channelScope.activeTagId || awaitingTagChannels
        if (scopeChanged && !matchesRequestedRows(currentScope())) return@LaunchedEffect
        val pendingId = pendingFocusId?.takeIf {
            pendingFocusTagId == channelScope.activeTagId && it in orderedChannelIds
        }
        cancelRestoration()
        if (scopeChanged) {
            positionedTagId = channelScope.activeTagId
            awaitingTagChannels = true
            focusedChannelId = null
        }
        if (awaitingTagChannels) {
            // Each group keeps the channel it was left on.
            val entryId = restoredChannelId(orderedChannelIds, rememberedChannelIds[channelScope.activeTagId], playingChannelId)
            if (entryId != null) {
                rememberedChannelIds[channelScope.activeTagId] = entryId
                awaitingTagChannels = false
            }
            // Override LazyColumn's retained first-item key on a scope change. Merely
            // changing its items preserves an overlapping channel's old viewport anchor.
            // Preview the same target/pivot Down will use, without requesting row focus.
            val entryIndex = orderedChannelIds.indexOf(entryId).coerceAtLeast(0)
            viewport.requestScrollToChannel(entryIndex)
        }
        if (positionedViewport.value !== viewport) {
            positionedViewport.value = viewport
            // A changed view shows the channel focus was left on, behind the panel that changed it.
            scopeEntryChannelId()?.let { viewport.requestScrollToChannel(orderedChannelIds.indexOf(it)) }
        }
        if (!initialFocusEnabled && restoreAfterAlert && orderedChannelIds.isNotEmpty()) {
            // Focus fell from the replaced alert to the rail; the header takes it back, the rail
            // closes and the channels take it from there.
            runCatching { headerEntryFocus.requestFocus() }
        }
        if (!initialFocusEnabled) {
            scopeEntryRequested = false
            contentFocusOwned = false
            didInitialRestore = false
            return@LaunchedEffect
        }
        if (orderedChannelIds.isEmpty()) {
            focusedChannelId = null
            return@LaunchedEffect
        }

        if ((!didInitialRestore || restoreAfterAlert) && initialFocusEnabled) {
            // Player Back re-enters the channel that played; first entry the remembered one.
            val playerReturnId = playerReturn?.takeIf { playerReturnPending || restoreAfterAlert }?.let {
                restoredChannelId(orderedChannelIds, it.playingChannelId, it.originChannelId)
            }
            playerReturnPending = false
            restoreAfterAlert = false
            didInitialRestore = true
            contentFocusOwned = true
            // A retained destination can receive a newer shared selection from Guide.
            // Re-enter that identity instead of a stale pre-visit Channels focus.
            val id = playerReturnId ?: selectedId()?.takeIf { it in orderedChannelIds } ?: restoredChannelId(
                visibleChannelIds = orderedChannelIds,
                rememberedChannelId = rememberedChannelIds[channelScope.activeTagId],
                selectedChannelId = selectedId(),
            ) ?: return@LaunchedEffect
            requestChannelFocus(id, preserveVisiblePosition = true)
            return@LaunchedEffect
        }

        if (pendingId != null) {
            requestChannelFocus(pendingId, preserveVisiblePosition = scopeChanged)
            return@LaunchedEffect
        }

        if (contentFocusOwned && focusedChannelId !in orderedChannelIds) {
            val id = restoredChannelId(
                visibleChannelIds = orderedChannelIds,
                rememberedChannelId = rememberedChannelIds[channelScope.activeTagId],
                selectedChannelId = selectedId(),
            ) ?: return@LaunchedEffect
            requestChannelFocus(id)
        }
    }

    LaunchedEffect(scopeEntryRequested, channelScopeState, initialFocusEnabled) {
        if (scopeEntryRequested && initialFocusEnabled) focusBrowseContent()
    }

    val detailsLayering = at.bernhardberger.tvhplayer.ui.components.LocalBrowseContentLayering.current
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - (detailsLayering?.detailsAlpha() ?: if (showProgrammeDetails) 1f else 0f) },
    ) {
        // Behind the header from the screen's top: it follows focus across groups, while the
        // group's content fades on its own. The grid and list sit on the plain page background.
        if (channels.isNotEmpty() && layout == ChannelsLayout.IMMERSIVE) {
            ProvideArtworkSession(observation.currentSession, channelScopeState.observation) {
                ChannelsImmersiveBackground(
                    programArt = view.showsProgramArt,
                    // The shown channel follows focus after a delay; it may still be the previous group's.
                    channels = channelScope.allChannels,
                    channelId = { focusedChannelId?.takeIf(orderedChannelIds::contains) ?: scopeEntryChannelId() },
                    observation = observation,
                    nowSec = { nowSec },
                    logoTop = contentPadding.calculateTopPadding() + TvBrowseHeaderHeight,
                    endInset = detailPanePadding.calculateEndPadding(layoutDirection),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .semantics { if (contextChannelId != null || openPanel != null) hideFromAccessibility() }
                // The list column runs to the screen bottom beneath its fade; the details
                // column re-applies the bottom inset itself.
                .padding(top = contentPadding.calculateTopPadding())
                // The immersive row sits farther from the rail than the header buttons, so the
                // rail's Right is redirected here too, before it lands on the header.
                .focusProperties { onEnter = { enterFromNavigation() } }
                .focusGroup()
        ) {
            ChannelsHeader(
                // The destination is the header's pane title; the group button shows the active group.
                title = stringResource(R.string.nav_channels),
                groupName = channelScope.activeTagId?.let { id -> groups.firstOrNull { it.tagId == id }?.name },
                focus = header,
                showGroup = groups.size > 1,
                navigationFocus = navigationFocus,
                onFocused = {
                    didInitialRestore = true
                    relinquishContentFocus()
                    // The grid brings its header back with its first row.
                    if (layout == ChannelsLayout.GRID) coroutineScope.launch { gridState.animateScrollToItem(0) }
                },
                onOpen = ::showPanel,
                onMoveToContent = ::focusBrowseContent,
                // The list's rows lead to the header with the end edge, so the start edge leads back
                // to a row; an empty group has none and leaves it to the rail.
                onReturnToContent = if (layout == ChannelsLayout.LIST) {
                    { scopeEntryChannelId() != null && focusBrowseContent() }
                } else null,
                // The grid's header scrolls away with its first row, over the rows reaching up behind it.
                // The immersive header takes no row: it overlays the body from the top.
                modifier = Modifier.zIndex(1f).then(
                    if (layout == ChannelsLayout.IMMERSIVE) Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, 0) { placeable.place(0, 0) }
                    } else if (layout == ChannelsLayout.GRID) Modifier.graphicsLayer {
                        val scrolled = if (gridState.firstVisibleItemIndex == 0) gridState.firstVisibleItemScrollOffset else Int.MAX_VALUE
                        translationY = -minOf(scrolled.toFloat(), size.height + ChannelsHeaderGap.toPx() + contentPadding.calculateTopPadding().toPx())
                    } else Modifier,
                ).padding(
                    start = startPadding + TvBrowseLeadingInset,
                    end = maxOf(endPadding, TvBrowseTrailingInset),
                ),
            )
            if (layout != ChannelsLayout.IMMERSIVE) Spacer(Modifier.height(ChannelsHeaderGap))
            Box(Modifier.weight(1f).fillMaxWidth()) {
            TabContent(
                motion = scopeMotion,
                selectedKey = channelScope.activeTagId,
                state = {
                    ChannelsTabBody(
                        channelScopeState, observation, connectionUiState, connectionFailure, orderedChannelIds,
                        channelNumbers, playingChannelId, recordingChannelIds, tagNotice,
                        { focusedChannelId }, ::scopeEntryChannelId, { nowSec }, initialFocusEnabled,
                        playbackChannelId, playbackIndicator,
                    )
                },
                transform = BrowseMotionPolicy::fadeThroughTransform,
                modifier = Modifier.fillMaxSize(),
            ) body@{ frame, owner ->
            val channelScopeState = frame.scope
            val channelScope = channelScopeState.scope
            val channels = channelScope.visibleChannels
            val observation = frame.observation
            val currentSession = observation.currentSession
            val connectionUiState = frame.connection
            val orderedChannelIds = frame.ids
            val channelNumbers = frame.numbers
            val playingChannelId = frame.playingId
            val recordingChannelIds = frame.recordingIds
            val tagNotice = frame.tagNotice
            val nowSecProvider = rememberTabReader(frame.nowSec)
            val listState = rememberTabListState(listState)
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
                        onShowAll = {
                            if (owner.isCurrent) {
                                // The action leaves with the empty group: hold focus in the header meanwhile.
                                runCatching { headerEntryFocus.requestFocus() }
                                selectGroup(null, enterContent = true)
                            }
                        }.takeIf { channelScope.allChannelsVisible },
                        actionFocus = header.showAll,
                        headerFocus = headerEntryFocus,
                        modifier = Modifier
                            .padding(browseViewportPadding)
                            .padding(detailPanePadding)
                            .fillMaxSize(),
                    )
                } else {
                    EmptyChannelsState(
                        connectionFailure = frame.failure,
                        state = connectionUiState.forEmptyChannelPresentation(
                            channelCatalogCurrent = channelScopeState.channelCatalogCurrent,
                        ),
                        initialFocusEnabled = owner.isCurrent && frame.initialFocusEnabled,
                        onAction = { action ->
                            if (owner.isCurrent) {
                                when (action) {
                                    AppErrorAction.RETRY -> onRetryConnection()
                                    AppErrorAction.CONNECTION_SETTINGS -> onOpenConnectionSettings()
                                    AppErrorAction.CLOSE -> Unit
                                }
                            }
                        },
                        onAlertLeftWithFocus = {
                            if (owner.isCurrent) {
                                // Focus waits in the header while the connection works; the
                                // channels then take it as on first entry.
                                runCatching { headerEntryFocus.requestFocus() }
                                didInitialRestore = false
                            }
                        },
                        onAlertGoneWithFocus = {
                            if (owner.isCurrent) {
                                restoreAfterAlert = true
                                alertsGoneWithFocus++
                            }
                        },
                        modifier = Modifier
                            .padding(browseViewportPadding)
                            .padding(detailPanePadding)
                            .fillMaxSize(),
                    )
                }
                return@body
            }
            // A leaving group's content never takes focus.
            val contentEntry = Modifier.focusProperties {
                onEnter = { if (!owner.isCurrent) cancelFocusChange() else enterFromNavigation() }
            }

            // A frame shows its pictures with the session of the observation it was built from, and
            // its cards' saved logos with the observation their scope was resolved from.
            ProvideArtworkSession(currentSession, channelScopeState.observation) {
            if (layout == ChannelsLayout.IMMERSIVE) {
                Column(Modifier.fillMaxSize()) {
                    UnavailableTagNotice(
                        visible = tagNotice,
                        onDismiss = { if (owner.isCurrent) onDismissTagNotice() },
                        // The header overlays the body's top, so the notice starts below it.
                        modifier = Modifier.padding(
                            start = startPadding + TvBrowseLeadingInset,
                            top = TvBrowseHeaderHeight + ChannelsHeaderGap,
                            end = detailPanePadding.calculateEndPadding(layoutDirection),
                        ),
                    )
                    ChannelsImmersiveBody(
                        channels = channels,
                        cards = ChannelsImmersiveCards(
                            frame = frame,
                            nowSec = nowSecProvider,
                            keyline = startPadding + TvBrowseLeadingInset,
                            programmePicture = view.showsProgramArt,
                            focusRequesters = rowFocusRequesters,
                            navigationFocus = navigationFocus,
                            headerFocus = headerEntryFocus,
                            onFocusChannel = { if (owner.isCurrent) onChannelFocused(it, channelScope.activeTagId) },
                            onPlay = { if (owner.isCurrent) onPlay(it.id, it.name.orEmpty()) },
                            onOpenContextMenu = { if (owner.isCurrent) openChannelContextMenu(it) },
                            onExitTowardNavigation = { relinquishContentFocus() },
                            onPage = ::pageChannels,
                        ),
                        state = rememberTabListState(rowState),
                        current = owner.isCurrent,
                        bottomInset = contentPadding.calculateBottomPadding(),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        rowModifier = contentEntry,
                    )
                }
            } else if (layout == ChannelsLayout.GRID) {
                // Cards keep the list's leading keyline; the grid's edge inset is focus reserve.
                Column(
                    Modifier
                        .padding(
                            start = startPadding + TvBrowseLeadingInset - ChannelsGridEdgeInset,
                            end = detailPanePadding.calculateEndPadding(layoutDirection) - ChannelsGridEdgeInset,
                        )
                        .fillMaxSize(),
                ) {
                    UnavailableTagNotice(
                        visible = tagNotice,
                        onDismiss = { if (owner.isCurrent) onDismissTagNotice() },
                        modifier = Modifier.zIndex(1f).padding(horizontal = ChannelsGridEdgeInset),
                    )
                    if (tagNotice) Spacer(Modifier.height(8.dp))
                    ChannelsGrid(
                        channels = channels,
                        frame = frame,
                        nowSec = nowSecProvider,
                        state = rememberTabGridState(gridState),
                        programmePicture = view.showsProgramArt,
                        isCurrent = owner.isCurrent,
                        focusRequesters = rowFocusRequesters,
                        navigationFocus = navigationFocus,
                        headerFocus = headerEntryFocus,
                        onFocusChannel = { if (owner.isCurrent) onChannelFocused(it, channelScope.activeTagId) },
                        onPlay = { if (owner.isCurrent) onPlay(it.id, it.name.orEmpty()) },
                        onOpenContextMenu = { if (owner.isCurrent) openChannelContextMenu(it) },
                        onExitTowardNavigation = { relinquishContentFocus() },
                        onPage = ::pageChannels,
                        modifier = Modifier.weight(1f).fillMaxWidth().then(contentEntry),
                        topOverflow = contentPadding.calculateTopPadding() + TvBrowseHeaderHeight + ChannelsHeaderGap,
                    )
                }
            } else Row(
                    Modifier
                        .padding(browseViewportPadding)
                        .fillMaxSize(),
                ) {
                    // Standard list column: 12dp focus-scale reserve, 340dp rows, run-out to the
                    // screen bottom under a 48dp fade, with a matching top fade after scrolling.
                    // Focus stays between the fades; scrolled-out rows may pass beneath them.
                    Column(
                        Modifier
                            .width(ChannelsListColumnWidth)
                            .fillMaxHeight()
                            .testTag("channels-list-column"),
                    ) {
                        UnavailableTagNotice(
                            visible = tagNotice,
                            onDismiss = { if (owner.isCurrent) onDismissTagNotice() },
                            modifier = Modifier.padding(horizontal = ChannelRowEdgeInset),
                        )
                        if (tagNotice) Spacer(Modifier.height(8.dp))

                        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
                            LazyColumn(
                                state = listState,
                                userScrollEnabled = owner.isCurrent,
                                verticalArrangement = Arrangement.spacedBy(ChannelRowGap),
                                contentPadding = PaddingValues(
                                    start = ChannelRowEdgeInset,
                                    end = ChannelRowEdgeInset,
                                    top = ChannelsListTopReserve,
                                    bottom = ListBottomReserve,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .listEdgeFades(listState)
                                    .testTag("channels-list")
                                    .then(contentEntry)
                                    .focusGroup()
                                    .focusRestorer()
                                    .onPreviewKeyEvent { event ->
                                        if (!owner.isCurrent) return@onPreviewKeyEvent true
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
                                    // A focused lazy item can stay pinned for the publication that removes it.
                                    if (owner.isCurrent && channelId !in rowFocusRequesters) return@items
                                    val nowSec = nowSecProvider()
                                    val now =
                                        remember(channelId, observation, nowSec) {
                                            profileTrace("P44:channelProgramme") {
                                                observation.eventAt(
                                                    channelId,
                                                    kotlin.time.Instant.fromEpochSeconds(nowSec),
                                                )
                                            }
                                        }
                                    val prog = remember(now, nowSec) { now?.progress(nowSec) ?: 0f }
                                    val status = channelNowStatus(
                                        channelId = channelId,
                                        playingChannelId = playingChannelId,
                                        recordingChannelIds = recordingChannelIds,
                                    )

                                    ChannelRow(
                                        modifier = Modifier
                                            .then(if (owner.isCurrent) Modifier.focusRequester(rowFocusRequesters.getValue(channelId)) else Modifier)
                                            .tabFocus()
                                            .focusProperties {
                                                // The passive details hold no target, so the end edge leads to the header buttons.
                                                if (layoutDirection == LayoutDirection.Ltr) {
                                                    left = navigationFocus ?: FocusRequester.Default
                                                    right = headerEntryFocus
                                                } else {
                                                    right = navigationFocus ?: FocusRequester.Default
                                                    left = headerEntryFocus
                                                }
                                                if (channelId == orderedChannelIds.firstOrNull()) up = headerEntryFocus
                                            }
                                            .testTag("channel-row-${channelId.value}"),
                                        number = ChannelNavigation.numberForId(
                                            orderedChannelIds,
                                            channelNumbers,
                                            channelId,
                                        ),
                                        name = ch.name.orEmpty(),
                                        programTitle = now?.title ?: stringResource(R.string.no_epg),
                                        programmeLoading = now == null && frame.guideLoading,
                                        progress = if (now != null) prog else null,
                                        piconPath = ch.icon,
                                        programStartSec = now?.start?.epochSeconds,
                                        recordingNow = status.recordingNow,
                                        playingNow = status.playingNow,
                                        playbackIndicator = frame.playbackIndicator.takeIf {
                                            channelId == frame.playbackChannelId
                                        } ?: ChannelPlaybackIndicator.NONE,
                                        onFocus = {
                                            if (owner.isCurrent) onChannelFocused(channelId, channelScope.activeTagId)
                                        },
                                        onConfirm = {
                                            if (!owner.isCurrent) return@ChannelRow
                                            // Saved rows play too: the player waits for the session.
                                            onPlay(channelId, ch.name.orEmpty())
                                        },
                                        onOpenContextMenu = {
                                            if (owner.isCurrent) openChannelContextMenu(channelId)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(ChannelsListToDetailsGap))

                    // Passive preview: the focused channel's picture, the same in every state, and the
                    // immersive headline hanging from it. Right from a row leads to the header.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .profileLayout("channels:details")
                            .padding(detailPanePadding)
                            .fillMaxHeight()
                            .testTag("channels-details"),
                    ) {
                        // Level with the first row.
                        ChannelsListArt(channels, frame, nowSecProvider, view.showsProgramArt,
                            Modifier.padding(top = ChannelsListTopReserve))
                        ChannelsImmersiveHero(channels, frame, nowSecProvider,
                            Modifier.padding(top = ProgrammePanePictureGap).weight(1f).widthIn(max = ProgrammePaneWidth), besideList = true)
                    }
                }
            }
            }
            }
        }
    }
    contextChannelId?.let { channelId ->
        ChannelContextMenu(
            mutationScope = coroutineScope,
            mutationInFlight = detailsMutationInFlight,
            onPlayRecording = onPlayRecording,
            returnFocusRequester = rowFocusRequesters[channelId],
            showDetails = showProgrammeDetails,
            onShowDetails = { showProgrammeDetails = true },
            channelId = channelId,
            observation = observation,
            onPlay = onPlay,
            onShowGuide = onShowGuide,
            onClose = {
                showProgrammeDetails = false
                contextChannelId = null
                if (!requestChannelFocus(channelId, preserveVisiblePosition = true)) {
                    // The menu's channel left the catalog; land where a removed focused row would.
                    val fallbackId = restoredChannelId(
                        visibleChannelIds = orderedChannelIds,
                        rememberedChannelId = rememberedChannelIds[channelScope.activeTagId],
                        selectedChannelId = selectedId(),
                    )
                    if (fallbackId == null || !requestChannelFocus(fallbackId)) focusHeader()
                }
            },
        )
    }
    when (openPanel) {
        ChannelsPanel.GROUP -> ChannelsGroupPanel(
            groups = groups,
            currentTagId = channelScope.activeTagId,
            onSelect = { tagId ->
                closePanel()
                selectGroup(tagId, enterContent = true)
            },
            onClose = ::closePanel,
        )
        ChannelsPanel.VIEW_OPTIONS -> ChannelsViewOptionsPanel(view, onViewChange, onClose = ::closePanel)
        null -> Unit
    }
}

internal data class ChannelsTabBody(
    val scope: ChannelScopeState,
    val observation: SessionObservation,
    val connection: ConnectionUiState,
    val failure: ConnectionUiState?,
    val ids: List<ChannelId>,
    val numbers: Map<ChannelId, Long?>,
    val playingId: ChannelId?,
    val recordingIds: Set<ChannelId>,
    val tagNotice: Boolean,
    val focusedId: () -> ChannelId?,
    val entryId: () -> ChannelId?,
    val nowSec: () -> Long,
    val initialFocusEnabled: Boolean,
    val playbackChannelId: ChannelId?,
    val playbackIndicator: ChannelPlaybackIndicator,
) {
    /** A missing programme is still on its way rather than absent. */
    val guideLoading: Boolean get() = connection.awaitsGuide(observation.epgState)
}

/** An empty group says so; with All channels visible it offers to show them instead. */
@Composable
private fun EmptyTagState(
    onShowAll: (() -> Unit)?,
    actionFocus: FocusRequester,
    headerFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        colors = SurfaceDefaults.colors(
            containerColor = TvSurfaceColors.container.copy(alpha = TvPanelBrowseAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp).testTag("channels-empty-group"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.channels_empty_group),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.widthIn(max = 680.dp),
            )
            if (onShowAll != null) Button(
                onClick = onShowAll,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .focusRequester(actionFocus)
                    .focusProperties { up = headerFocus }
                    .tabFocus()
                    .testTag("channels-show-all"),
            ) {
                Text(stringResource(R.string.channels_show_all))
            }
        }
    }
}

/**
 * Channels with none to show: the passive connecting text while the connection works, else the
 * connection's alert inside the content area, 108 when a Ready session's catalog is empty. Its
 * first button takes focus as the content would; Left leads to the rail. An alert leaving with focus
 * calls [onAlertLeftWithFocus] while its buttons still hold it, before the passive text replaces it;
 * one replaced by arriving channels while focused calls [onAlertGoneWithFocus] as it leaves.
 */
@Composable
private fun EmptyChannelsState(
    state: ConnectionUiState,
    connectionFailure: ConnectionUiState?,
    initialFocusEnabled: Boolean,
    onAction: (AppErrorAction) -> Unit,
    onAlertLeftWithFocus: () -> Unit,
    onAlertGoneWithFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = connectionFailure?.browseAppError() ?:
        state.browseAppError(noChannels = true).takeIf { state == ConnectionUiState.Ready }
    val actionFocus = remember { FocusRequester() }
    val drawerState = LocalBrowseDrawerState.current
    val contentEntryEnabled by rememberUpdatedState(initialFocusEnabled)
    var alertFocused by remember { mutableStateOf(false) }

    LaunchedEffect(error, initialFocusEnabled) {
        if (error != null && shouldRequestEmptyChannelsAction(initialFocusEnabled, hasPrimaryAction = true)) {
            withFrameNanos { }
            if (contentEntryEnabled && drawerState?.currentValue != DrawerValue.Open) {
                runCatching { actionFocus.requestFocus() }
            }
        }
    }
    // The alert's exit keeps its buttons a moment; focus leaves them before they go.
    LaunchedEffect(error == null) {
        if (error == null && alertFocused) onAlertLeftWithFocus()
    }
    // Channels arriving straight away replace the alert while it holds focus.
    val latestAlertGone by rememberUpdatedState(onAlertGoneWithFocus)
    val latestAlertFocused by rememberUpdatedState(alertFocused)
    DisposableEffect(Unit) {
        onDispose { if (latestAlertFocused) latestAlertGone() }
    }
    Box(modifier) {
        if (error == null) ConnectingChannelsState(state, Modifier.fillMaxSize())
        AppErrorAlert(
            error = error,
            onAction = onAction,
            modifier = Modifier.fillMaxSize().onFocusChanged { alertFocused = it.hasFocus },
            actionFocus = actionFocus,
            leadingExit = LocalBrowseNavigationFocus.current ?: FocusRequester.Default,
            upExit = FocusRequester.Default,
        )
    }
}

@Composable
private fun ConnectingChannelsState(state: ConnectionUiState, modifier: Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        colors = SurfaceDefaults.colors(
            containerColor = TvSurfaceColors.container.copy(alpha = TvPanelBrowseAlpha),
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
            DelayedConnectionProgress(visible = true, modifier = Modifier.size(40.dp))
            Text(
                text = connectingMessage(state),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .widthIn(max = 680.dp),
            )
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

/** The passive text while [state] connects, loads or reconnects; failures are alerts. */
@Composable
private fun connectingMessage(state: ConnectionUiState): String = stringResource(
    when (state) {
        ConnectionUiState.SyncingChannels -> R.string.connection_loading_channels
        ConnectionUiState.Reconnecting -> R.string.status_disconnected_reconnecting
        else -> R.string.connection_connecting
    }
)

/** Between the header and the content below it. */
private val ChannelsHeaderGap = 8.dp

/** Focused-programme dissolve. Short enough to keep row-to-row browsing responsive. */
internal const val ChannelsDetailCrossfadeMillis = 160
