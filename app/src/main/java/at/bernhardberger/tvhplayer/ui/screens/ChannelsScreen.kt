package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.channelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.rememberPlaybackIntent

import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import kotlin.math.roundToInt

import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.profiling.profileTrace
import at.bernhardberger.tvhplayer.profiling.profileLayout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import at.bernhardberger.tvheadend.sdk.core.Channel
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
import at.bernhardberger.tvhplayer.ui.components.BrowseTabContent
import at.bernhardberger.tvhplayer.ui.components.browseTabFocus
import at.bernhardberger.tvhplayer.ui.components.rememberBrowseTabListState
import at.bernhardberger.tvhplayer.ui.components.rememberBrowseTabReader
import at.bernhardberger.tvhplayer.ui.components.rememberBrowseContentMotion
import at.bernhardberger.tvhplayer.ui.components.ChannelRowEdgeInset
import at.bernhardberger.tvhplayer.ui.components.ChannelRowGap
import at.bernhardberger.tvhplayer.ui.components.ChannelTagSelector
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseDrawerState
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus
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

/**
 * The details column keeps the trailing and bottom safe insets; the list column beside
 * it deliberately runs to the screen bottom beneath its fade.
 */
internal fun channelsDetailPanePadding(
    contentPadding: PaddingValues,
    layoutDirection: LayoutDirection,
): PaddingValues = PaddingValues(
    end = contentPadding.calculateEndPadding(layoutDirection),
    bottom = contentPadding.calculateBottomPadding(),
)

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
)

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
    channelViewModel: ChannelsViewModel,
    selection: ChannelSelectionStore = koinInject(),
    imageLoader: ImageLoader = koinInject(),
    playingChannelId: ChannelId?,
    playerReturn: PlayerReturnFocus? = null,
    connectionUiState: ConnectionUiState,
    onRetryConnection: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onPlay: (selection: LivePlaybackSelection, channelName: String) -> Unit
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

    ChannelsScreenContent(
        contentPadding = contentPadding,
        initialFocusEnabled = initialFocusEnabled,
        channelScopeState = channelScopeState,
        observation = observation,
        tagNotice = tagNotice,
        selectedId = { selectedId.value },
        imageLoader = imageLoader,
        playingChannelId = playingChannelId,
        playerReturn = playerReturn,
        playbackChannelId = playbackChannelId,
        playbackIndicator = playbackIndicator,
        connectionUiState = connectionUiState,
        onSelectChannel = selection::setSelected,
        onSelectTag = channelViewModel::selectTag,
        scopeStateProvider = { channelViewModel.scope.value },
        onDismissTagNotice = channelViewModel::dismissUnavailableTagNotice,
        onRetryConnection = onRetryConnection,
        onOpenConnectionSettings = onOpenConnectionSettings,
        onPlay = onPlay,
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
    imageLoader: ImageLoader,
    playingChannelId: ChannelId?,
    playerReturn: PlayerReturnFocus? = null,
    playbackChannelId: ChannelId? = playingChannelId,
    playbackIndicator: ChannelPlaybackIndicator = if (playingChannelId != null) ChannelPlaybackIndicator.PLAYING else ChannelPlaybackIndicator.NONE,
    connectionUiState: ConnectionUiState,
    onSelectChannel: (ChannelId) -> Unit,
    onSelectTag: (ChannelTagId?) -> Unit,
    onDismissTagNotice: () -> Unit,
    onRetryConnection: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onPlay: (selection: LivePlaybackSelection, channelName: String) -> Unit,
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
    val channelScope = channelScopeState.scope
    val scopeMotion = rememberBrowseContentMotion(channelScope.activeTagId) {
        scopeStateProvider().scope.activeTagId
    }
    val hasScopeTabs = channelScope.tags.size + (if (channelScope.allChannelsVisible) 1 else 0) > 1
    val scopeFocus = remember { FocusRequester() }
    var consumeBackRelease by remember { mutableStateOf(false) }
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
    // One-shot per player return; AppRoot clears the request when the destination changes.
    var playerReturnPending by remember(playerReturn) { mutableStateOf(playerReturn != null) }
    var focusedChannelId by remember { mutableStateOf<ChannelId?>(null) }
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
    // the readable band between the list's top reserve and its bottom fade.
    val platformBringIntoViewSpec = LocalBringIntoViewSpec.current
    val density = LocalDensity.current
    val bringIntoViewSpec = remember(platformBringIntoViewSpec, density) {
        with(density) {
            InsetBringIntoViewSpec(
                delegate = platformBringIntoViewSpec,
                topInsetPx = ChannelsListTopReserve.toPx(),
                bottomInsetPx = ChannelsListBottomReserve.toPx(),
            )
        }
    }
    val coroutineScope = rememberCoroutineScope()

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

    fun channelFocusScrollOffset(): Int {
        val layout = listState.layoutInfo
        return bringIntoViewSpec.calculateScrollDistance(
            offset = layout.beforeContentPadding.toFloat(),
            size = (layout.visibleItemsInfo.firstOrNull()?.size ?: 0).coerceAtLeast(0).toFloat(),
            containerSize = layout.viewportSize.height.toFloat(),
        ).roundToInt()
    }

    fun requestChannelFocus(
        channelId: ChannelId,
        preserveVisiblePosition: Boolean = false,
        animatePage: Boolean = false,
    ): Boolean {
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
                if (!preserveVisiblePosition || listState.layoutInfo.visibleItemsInfo.none {
                        channelLazyItemMatches(it.key, channelId)
                    }
                ) {
                    if (animatePage) {
                        // Finish at the same position focus will request, rather than animating
                        // to the top and then visibly scrolling backward to the TV focus pivot.
                        listState.animateScrollToItem(index, channelFocusScrollOffset())
                    } else listState.scrollToItem(index)
                }
                if (!listState.awaitVisibleChannel(channelId)) return@launch
                withFrameNanos { }
                // Widget focus changes precede composition's drawerActive feedback.
                // A queued initial restore must not take focus back in that interval.
                if (drawerState?.currentValue == DrawerValue.Open ||
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

    fun focusBrowseContent(): Boolean {
        didInitialRestore = true // Explicit Down/OK intent supersedes cold-start tag entry.
        scopeEntryRequested = true
        if (!matchesRequestedRows(scopeStateProvider())) return true
        scopeEntryRequested = false
        val id = scopeEntryChannelId() ?: return false
        contentFocusOwned = true
        return requestChannelFocus(id, preserveVisiblePosition = true)
    }

    fun focusScope(): Boolean {
        relinquishContentFocus()
        return scopeFocus.requestFocus()
    }

    BackHandler(enabled = initialFocusEnabled && hasScopeTabs && contentFocusOwned) {
        focusScope()
    }

    fun pageChannels(direction: Int): Boolean {
        val currentId = pendingFocusId
            ?.takeIf { pendingFocusTagId == channelScope.activeTagId }
            ?: focusedChannelId?.takeIf(orderedChannelIds::contains)
            ?: selectedId()
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
        initialFocusEnabled, tagResetGeneration,
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
            rememberedChannelIds.remove(channelScope.activeTagId)
        }
        if (awaitingTagChannels) {
            val entryId = restoredChannelId(orderedChannelIds, null, playingChannelId)
            if (entryId != null) {
                rememberedChannelIds[channelScope.activeTagId] = entryId
                awaitingTagChannels = false
            }
            // Override LazyColumn's retained first-item key on a scope change. Merely
            // changing its items preserves an overlapping channel's old viewport anchor.
            // Preview the same target/pivot Down will use, without requesting row focus.
            listState.requestScrollToItem(
                index = orderedChannelIds.indexOf(entryId).coerceAtLeast(0),
                scrollOffset = channelFocusScrollOffset(),
            )
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

        if (!didInitialRestore && initialFocusEnabled) {
            // Player Back re-enters the list, not the first-entry scope tabs.
            val playerReturnId = playerReturn?.takeIf { playerReturnPending }?.let {
                restoredChannelId(orderedChannelIds, it.playingChannelId, it.originChannelId)
            }
            playerReturnPending = false
            if (hasScopeTabs && playerReturnId == null) {
                if (!focusScope()) {
                    // TabRow subcomposes its focus targets during measurement. A cold
                    // composition can precede their placement; lateral warm entry does not.
                    withFrameNanos { }
                    if (contentEntryEnabled && !didInitialRestore && !contentFocusOwned && matchesRequestedRows(currentScope()) &&
                        drawerState?.currentValue != DrawerValue.Open
                    ) scopeFocus.requestFocus()
                }
                return@LaunchedEffect
            }
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

    Column(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Back) return@onPreviewKeyEvent false
                if (consumeBackRelease) {
                    if (event.type == KeyEventType.KeyUp) consumeBackRelease = false
                    return@onPreviewKeyEvent true
                }
                if (event.type == KeyEventType.KeyDown && hasScopeTabs && contentFocusOwned) {
                    consumeBackRelease = true
                    focusScope()
                    true
                } else false
            }
            // The list column runs to the screen bottom beneath its fade; the details
            // column re-applies the bottom inset itself.
            .padding(top = contentPadding.calculateTopPadding())
    ) {
        // No destination headline: the tag row is the top of the immersive surface, on
        // the same leading axis as the rows. The band keeps the list at one position
        // whether or not a scope row is shown.
        Box(
            Modifier
                .fillMaxWidth()
                .height(ChannelsTagSectionHeight)
                .padding(start = startPadding + ChannelRowEdgeInset),
            contentAlignment = Alignment.TopStart,
        ) {
            if (hasScopeTabs) ChannelTagSelector(
                tags = channelScope.tags,
                activeTagId = channelScope.activeTagId,
                activeFocusRequester = scopeFocus,
                onSelectTag = {
                    scopeMotion.select(it, buildList {
                        if (channelScope.allChannelsVisible) add(null)
                        addAll(channelScope.tags.map { tag -> tag.id })
                    })
                    if (it != channelScope.activeTagId) {
                        // Keep a reset even if A -> B -> A coalesces before composition.
                        // Selecting the already active tab on Back/re-entry is not a reset.
                        awaitingTagChannels = true
                        tagResetGeneration++
                    }
                    relinquishContentFocus(clearFocusedChannel = true)
                    onSelectTag(it)
                },
                onMoveToContent = { focusBrowseContent() },
                onTagFocus = {
                    // This callback precedes the drawer's composition feedback on Right.
                    if (!initialFocusEnabled) {
                        selectedId()?.takeIf { it in orderedChannelIds }?.let {
                            rememberedChannelIds[channelScope.activeTagId] = it
                        }
                    }
                    didInitialRestore = true
                    relinquishContentFocus()
                },
                allChannelsVisible = channelScope.allChannelsVisible,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        BrowseTabContent(
            motion = scopeMotion,
            selectedKey = channelScope.activeTagId,
            state = {
                ChannelsTabBody(
                    channelScopeState, observation, connectionUiState, orderedChannelIds,
                    channelNumbers, playingChannelId, recordingChannelIds, tagNotice,
                    { focusedChannelId }, ::scopeEntryChannelId, { nowSec }, initialFocusEnabled,
                    playbackChannelId, playbackIndicator,
                )
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
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
        val nowSecProvider = rememberBrowseTabReader(frame.nowSec)
        val listState = rememberBrowseTabListState(listState)
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
                        .padding(detailPanePadding)
                        .fillMaxSize(),
                )
            } else {
                EmptyChannelsState(
                    state = connectionUiState.forEmptyChannelPresentation(
                        channelCatalogCurrent = channelScopeState.channelCatalogCurrent,
                    ),
                    initialFocusEnabled = owner.isCurrent && frame.initialFocusEnabled,
                    onRetry = { if (owner.isCurrent) onRetryConnection() },
                    onOpenSettings = { if (owner.isCurrent) onOpenConnectionSettings() },
                    modifier = Modifier
                        .padding(browseViewportPadding)
                        .padding(detailPanePadding)
                        .fillMaxSize(),
                )
            }
            return@body
        }

        if (connectionUiState != ConnectionUiState.Ready || !channelScopeState.channelCatalogCurrent) {
            InlineConnectionState(
                state = if (connectionUiState == ConnectionUiState.Ready) ConnectionUiState.SyncingChannels else connectionUiState,
                onRetry = { if (owner.isCurrent) onRetryConnection() },
                onOpenSettings = { if (owner.isCurrent) onOpenConnectionSettings() },
                modifier = Modifier.padding(start = startPadding, end = endPadding),
            )
            Spacer(Modifier.height(12.dp))
        }

        Row(
                Modifier
                    .padding(browseViewportPadding)
                    .fillMaxSize(),
            ) {
                // Standard list column: 12dp focus-scale reserve, 340dp rows, run-out to the
                // screen bottom under a 48dp fade. Focus stays above the fade (see
                // InsetBringIntoViewSpec); scrolled-out rows may pass beneath it.
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
                                bottom = ChannelsListBottomReserve,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .bottomEdgeFadeMask(ChannelsListFadeHeight)
                                .testTag("channels-list")
                                .focusProperties {
                                    onEnter = {
                                        if (!owner.isCurrent) {
                                            cancelFocusChange()
                                        } else if (hasScopeTabs && (!didInitialRestore || requestedFocusDirection == FocusDirection.Right)) {
                                            scopeFocus.requestFocus()
                                        }
                                    }
                                }
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
                                        .browseTabFocus()
                                        .focusProperties {
                                            if (layoutDirection == LayoutDirection.Ltr) {
                                                left = navigationFocus ?: FocusRequester.Default
                                            } else {
                                                right = navigationFocus ?: FocusRequester.Default
                                            }
                                        }
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
                                    programStartSec = now?.start?.epochSeconds,
                                    recordingNow = status.recordingNow,
                                    playingNow = status.playingNow,
                                    playbackIndicator = frame.playbackIndicator.takeIf {
                                        channelId == frame.playbackChannelId
                                    } ?: ChannelPlaybackIndicator.NONE,
                                    onFocus = {
                                        if (!owner.isCurrent) return@ChannelRow
                                        profileTrace("P44:focus:channel") {
                                            if (BuildConfig.PROFILE_TRACE) {
                                                profileTrace("P48:focus:channel") { }
                                            }
                                            focusedChannelId = channelId
                                            rememberedChannelIds[channelScope.activeTagId] = channelId
                                            contentFocusOwned = true
                                            if (isRestoring && channelId != pendingFocusId) {
                                                cancelRestoration()
                                            }
                                            if (!isRestoring) {
                                                onSelectChannel(channelId)
                                            }
                                        }
                                    },
                                    onConfirm = {
                                        if (!owner.isCurrent) return@ChannelRow
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

                Spacer(Modifier.width(ChannelsListToDetailsGap))

                // Focused-programme details: immersive text anchored to the bottom safe
                // inset, no panel. Non-focusable; Right from a row stays on the row.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .profileLayout("channels:details")
                        .padding(detailPanePadding)
                        .fillMaxHeight()
                        .testTag("channels-details"),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    FocusedChannelDetails(
                        channels = channels,
                        focusedChannelId = frame.focusedId,
                        selectedId = frame.entryId,
                        observation = observation,
                        nowSecProvider = nowSecProvider,
                    )
                }
            }
        }
    }
}

private data class ChannelsTabBody(
    val scope: ChannelScopeState,
    val observation: SessionObservation,
    val connection: ConnectionUiState,
    val ids: List<ChannelId>,
    val numbers: Map<ChannelId, Int?>,
    val playingId: ChannelId?,
    val recordingIds: Set<ChannelId>,
    val tagNotice: Boolean,
    val focusedId: () -> ChannelId?,
    val entryId: () -> ChannelId?,
    val nowSec: () -> Long,
    val initialFocusEnabled: Boolean,
    val playbackChannelId: ChannelId?,
    val playbackIndicator: ChannelPlaybackIndicator,
)

@Composable
private fun EmptyTagState(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        colors = SurfaceDefaults.colors(
            containerColor = TvSurfaceColors.container.copy(alpha = TvPanelBrowseAlpha),
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
    val drawerState = LocalBrowseDrawerState.current
    val contentEntryEnabled by rememberUpdatedState(initialFocusEnabled)

    LaunchedEffect(state, initialFocusEnabled, hasPrimaryAction) {
        if (shouldRequestEmptyChannelsAction(initialFocusEnabled, hasPrimaryAction)) {
            withFrameNanos { }
            if (contentEntryEnabled && drawerState?.currentValue != DrawerValue.Open) {
                actionFocus.requestFocus()
            }
        }
    }

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
                            .browseTabFocus()
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
                            modifier = Modifier.browseTabFocus().focusRequester(actionFocus),
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.browseTabFocus()) {
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
            containerColor = TvSurfaceColors.containerHigh,
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
                text = if (state == ConnectionUiState.SyncingChannels) {
                    stringResource(R.string.connection_refreshing_channels)
                } else {
                    connectionMessage(state)
                },
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
                Button(onClick = onRetry, modifier = Modifier.browseTabFocus()) { Text(stringResource(R.string.retry)) }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.browseTabFocus()) {
                    Text(stringResource(R.string.connection_settings_short))
                }
            } else if (recoveryAction == ConnectionRecoveryAction.SETTINGS) {
                Button(onClick = onOpenSettings, modifier = Modifier.browseTabFocus()) {
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
private fun FocusedChannelDetails(
    channels: List<Channel>,
    focusedChannelId: () -> ChannelId?,
    selectedId: () -> ChannelId?,
    observation: SessionObservation,
    nowSecProvider: () -> Long,
) {
    // These reads belong to the details composition, not the screen or list.
    // Keep local native focus ahead of shared selection during restoration.
    val detailChannelId = rememberBrowseTabReader {
        focusedChannelId()?.takeIf { id -> channels.any { it.id == id } }
            ?: browsingFocusChannelId(channels, selectedId())
    }()
    // The details are informational and never focusable, so the column can dissolve
    // between programmes instead of hard-cutting as the browsed row changes. Each
    // state resolves its own EPG inside the transition, so the outgoing copy keeps
    // showing the channel it was written for. The block is bottom-anchored and
    // unequal in height, so the size snaps and the taller copy is left unclipped
    // rather than animating the text baselines around.
    AnimatedContent(
        targetState = detailChannelId,
        transitionSpec = {
            fadeIn(tween(ChannelsDetailCrossfadeMillis)) togetherWith
                fadeOut(tween(ChannelsDetailCrossfadeMillis)) using
                SizeTransform(clip = false) { _, _ -> snap() }
        },
        contentAlignment = Alignment.BottomStart,
        label = "channelsDetails",
    ) { id ->
        val channel = channels.firstOrNull { it.id == id }
        val nowSec = nowSecProvider()
        val now = remember(observation, channel?.id, nowSec) {
            channel?.id?.let {
                profileTrace("P48:channelsNowLookup") {
                    observation.eventAt(it, kotlin.time.Instant.fromEpochSeconds(nowSec))
                }
            }
        }
        val next = remember(observation, channel?.id, nowSec) {
            channel?.id?.let {
                profileTrace("P48:channelsNextLookup") {
                    observation.nextEvent(it, kotlin.time.Instant.fromEpochSeconds(nowSec))
                }
            }
        }
        EpgDetailPane(
            channelName = channel?.name ?: "—",
            now = now,
            next = next,
            nowSec = nowSec,
        )
    }
}

/** Focused-programme dissolve. Short enough to keep row-to-row browsing responsive. */
internal const val ChannelsDetailCrossfadeMillis = 160

/** Description line height relative to its 14sp body size (accepted C3b: 1.45). */
private const val DetailDescriptionLineHeight = 1.45f

/**
 * Immersive focused-programme text (C3c): channel label, title, timing/meta, 4dp
 * progress, bounded description and the next programme. Every line is bounded so
 * long runtime text grows the block upward from its bottom anchor without overlap.
 */
@Composable
private fun EpgDetailPane(
    channelName: String,
    now: EpgEventEntry?,
    next: EpgEventEntry?,
    nowSec: Long,
) = profileTrace("P1:compose:channelDetails") {
    val progress = remember(now, nowSec) { now?.progress(nowSec) ?: 0f }
    val summaryText = remember(now) { now?.let { programmeSummaryText(it) } }
    val metadata = remember(now) { now?.let { programmeMetadata(it) } }
    val body = MaterialTheme.typography.bodyMedium
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = channelName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("channels-detail-channel"),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = now?.title ?: stringResource(R.string.no_epg),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("channels-detail-title"),
        )

        if (now != null) {
            val start = now.start.epochSeconds
            val end = now.stop.epochSeconds
            val durMin = ((end - start) / 60).coerceAtLeast(0)
            val timing = stringResource(
                R.string.channel_details_timing,
                formatHm(start),
                formatHm(end),
                durMin.toInt(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (metadata != null) "$timing · $metadata" else timing,
                style = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("channels-detail-timing"),
            )
            ProgressStrip(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("channels-detail-progress"),
            )
            if (summaryText != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = summaryText,
                    style = body.copy(lineHeight = body.fontSize * DetailDescriptionLineHeight),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("channels-detail-description"),
                )
            }
        }

        if (next != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.epg_next, formatHm(next.start.epochSeconds)) +
                    next.title?.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("channels-detail-next"),
            )
        }
    }
}
