package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.ui.notifications.AppShellNoticeHost

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvhplayer.ui.components.BrowsePreparationPending
import at.bernhardberger.tvhplayer.ui.components.rememberPreparedBrowseData

import androidx.activity.compose.BackHandler
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.profiling.profileTrace
import at.bernhardberger.tvhplayer.profiling.ProfileCompositionLifetime

import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseVisibleWidthPx
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.tv.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrConfigurationsState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvheadend.sdk.core.EpgSearchResult
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.ConnectionRecoveryAction
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.core.DvrConfigChoice
import at.bernhardberger.tvhplayer.core.EpgFocusColumn
import at.bernhardberger.tvhplayer.core.EpgFocusDirection
import at.bernhardberger.tvhplayer.core.EpgFocusTarget
import at.bernhardberger.tvhplayer.core.GUIDE_VISIBLE_WINDOW_SEC
import at.bernhardberger.tvhplayer.core.GuideCoverageFocusResolution
import at.bernhardberger.tvhplayer.core.GuideDeferredOriginResolution
import at.bernhardberger.tvhplayer.core.GuideEntryFocusTarget
import at.bernhardberger.tvhplayer.core.GuideScopeExitFocusTarget
import at.bernhardberger.tvhplayer.core.GuidePendingNavigationAction
import at.bernhardberger.tvhplayer.core.guidePendingNavigationAction
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import at.bernhardberger.tvhplayer.core.ProgrammeRecordingTarget
import at.bernhardberger.tvhplayer.core.browsingFocusChannelId
import at.bernhardberger.tvhplayer.core.chooseDvrConfig
import at.bernhardberger.tvhplayer.core.currentEpgSnapshot
import at.bernhardberger.tvhplayer.core.firstUnsettledGuidePageIndex
import at.bernhardberger.tvhplayer.core.floorGuideWindowToHour
import at.bernhardberger.tvhplayer.core.guideChannelPageCoverageSettled
import at.bernhardberger.tvhplayer.core.guideEntryFocusTarget
import at.bernhardberger.tvhplayer.core.guideScopeExitFocusTarget
import at.bernhardberger.tvhplayer.core.guideWindowBounds
import at.bernhardberger.tvhplayer.core.guideDisplayEvents
import at.bernhardberger.tvhplayer.core.indexTimelineEventsByChannel
import at.bernhardberger.tvhplayer.core.initialTimelineEpgFocus
import at.bernhardberger.tvhplayer.core.matchesProgrammeCategory
import at.bernhardberger.tvhplayer.core.moveTimelineEpgFocus
import at.bernhardberger.tvhplayer.core.programmeRecordingTarget
import at.bernhardberger.tvhplayer.core.primaryRecoveryAction
import at.bernhardberger.tvhplayer.core.reconcileTimelineEpgFocus
import at.bernhardberger.tvhplayer.core.resolveGuideFrontierOrigin
import at.bernhardberger.tvhplayer.core.resolveGuideWindowFocus
import at.bernhardberger.tvhplayer.core.shouldWaitForGuideCoverage
import at.bernhardberger.tvhplayer.core.timelineFrontierFocus
import at.bernhardberger.tvhplayer.core.timelinePageFocusTarget
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.LivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.stores.GuidePosition
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabel
import at.bernhardberger.tvhplayer.ui.components.ChannelTagSelector
import at.bernhardberger.tvhplayer.ui.components.TabContent
import at.bernhardberger.tvhplayer.ui.components.tabFocus
import at.bernhardberger.tvhplayer.ui.components.rememberTabListState
import at.bernhardberger.tvhplayer.ui.components.rememberTabReader
import at.bernhardberger.tvhplayer.core.TimelineEpgEventIndex
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import at.bernhardberger.tvhplayer.ui.components.rememberTabContentMotion
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
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideChannelWidth
import at.bernhardberger.tvhplayer.ui.TvBrowseLeadingInset
import at.bernhardberger.tvhplayer.ui.TvBrowseTrailingInset
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideChannelGap
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideEdgeFade
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideFocusReserve
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideFocusHalfGrowth
import at.bernhardberger.tvhplayer.ui.screens.guide.guideFocusWindowStart
import at.bernhardberger.tvhplayer.ui.screens.guide.guideNowLine
import at.bernhardberger.tvhplayer.ui.screens.guide.guideTerminalViewportReserve
import at.bernhardberger.tvhplayer.ui.screens.guide.guideViewportFades
import at.bernhardberger.tvhplayer.ui.screens.guide.guideVisibleWindowSec
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import coil3.ImageLoader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant as KotlinInstant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private data class GuideTabBody(
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
    val hasRecovery: Boolean,
    val needsSettings: Boolean,
    val permissionDenied: Boolean,
    val coveragePending: (ChannelId) -> Boolean,
    val earlierContent: Boolean,
    val laterContent: Boolean,
)

private const val CHANNEL_PAGE_SIZE = 6
private const val GUIDE_COVERAGE_NAVIGATION_TIMEOUT_MS = 10_000L

private enum class GuideHeaderFocus {
    DATE,
    NOW,
    SEARCH,
    CLEAR_FILTER,
}

private data class FrontierRequest(
    val channelId: ChannelId,
    val originEventId: EventId,
    val boundarySec: Long,
    val direction: Int,
    val originWindowStartSec: Long,
    val coverageRequest: GuideCoverageRequestToken,
    val throughSec: Long,
)

private data class FrontierOrigin(
    val channelId: ChannelId,
    val eventId: EventId,
    val windowStartSec: Long,
)

private fun FrontierRequest.toOrigin() = FrontierOrigin(
    channelId = channelId,
    eventId = originEventId,
    windowStartSec = originWindowStartSec,
)

private data class ChannelFocusRequest(
    val originChannelId: ChannelId,
    val originEventId: EventId,
    val preferredChannelId: ChannelId,
    val direction: Int,
    val coverageRequest: GuideCoverageRequestToken,
    val throughSec: Long,
)

private data class WindowFocusRequest(
    val preferredChannelId: ChannelId,
    val windowStartSec: Long,
    val coverageRequest: GuideCoverageRequestToken,
    val throughSec: Long,
)

private data class GuideSearchRequest(
    val observation: SessionObservation,
    val currentSession: CurrentSessionObservation,
    val query: String,
    val tagId: ChannelTagId?,
)

private data class GuideIndexInput(
    val snapshot: EpgSnapshot?,
    val events: List<EpgEventEntry>,
    val state: EpgRepositoryState,
    val category: ProgrammeCategory,
    val windowStartSec: Long,
    val windowEndSec: Long,
)

private data class GuideIndexPresentation(
    val index: TimelineEpgEventIndex,
    val eventIds: Set<EventId>,
)

// Bookkeeping only: this is never rendered or accepted as completed positioning.
private val emptyGuideIndex = TimelineEpgEventIndex(emptyMap(), emptySet(), emptySet())

internal fun guideTimelineContentPadding(
    contentPadding: PaddingValues,
    layoutDirection: LayoutDirection,
    terminalReserve: Dp = 0.dp,
): PaddingValues = PaddingValues(
    start = contentPadding.calculateStartPadding(layoutDirection) + TvBrowseLeadingInset,
    top = 8.dp,
    end = terminalReserve,
    bottom = maxOf(contentPadding.calculateBottomPadding(), GuideEdgeFade + GuideFocusReserve),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpgGridScreen(
    contentPadding: PaddingValues = PaddingValues(),
    initialFocusEnabled: Boolean = true,
    category: ProgrammeCategory = ProgrammeCategory.ALL,
    channelViewModel: ChannelsViewModel,
    selection: ChannelSelectionStore = koinInject(),
    session: TvheadendSession = koinInject(),
    playerSession: AppPlaybackRuntime = koinInject(),
    lastPlayedStore: LastPlayedChannelStore = koinInject(),
    guidePositionStore: GuidePositionStore = koinInject(),
    imageLoader: ImageLoader = koinInject(),
    connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
    playerReturn: PlayerReturnFocus? = null,
    onRetry: () -> Unit = {},
    onOpenConnectionSettings: () -> Unit = {},
    onClearCategory: () -> Unit = {},
    onPlayRecording: (RecordingPlaybackSelection) -> Unit = {},
    notices: NoticeCenter = koinInject(),
    onPlay: (selection: LivePlaybackSelection, channelName: String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    ProfileCompositionLifetime("guide")
    val startPadding = contentPadding.calculateStartPadding(layoutDirection)
    val endPadding = contentPadding.calculateEndPadding(layoutDirection)
    val timelineWidth = (maxWidth - startPadding - TvBrowseLeadingInset - GuideChannelWidth - GuideChannelGap)
        .coerceAtLeast(1.dp)
    val timelineWidthPx = with(density) { timelineWidth.toPx() }
    // TV Material's native ListItem focus scale is 1.05; reserve its largest possible
    // half-growth as well as the normal 8dp breathing room, including long programmes.
    val horizontalFocusReserve = maxOf(GuideFocusReserve, (timelineWidth - 8.dp) * GuideFocusHalfGrowth)
    val timeLabelWidth = rememberTextMeasurer().measure(
        "00:00", style = MaterialTheme.typography.labelMedium,
    ).size.width
    // Capacity already budgets the outer safe area. Do not subtract terminal padding again.
    val visibleWindowSec = guideVisibleWindowSec(timelineWidthPx, timeLabelWidth, density.density)
    val platformBringIntoView = LocalBringIntoViewSpec.current
    val verticalFocusInset = with(density) { (GuideEdgeFade + GuideFocusReserve).toPx() }
    val guideBringIntoView = remember(platformBringIntoView, verticalFocusInset) {
        InsetBringIntoViewSpec(platformBringIntoView, verticalFocusInset, verticalFocusInset)
    }
    val coroutineScope = rememberCoroutineScope()
    val dvrMutationActions = remember(session.dvrRepository) {
        DvrMutationActions(session.dvrRepository)
    }
    val epgSearchActions = remember(session.epgRepository) {
        EpgSearchActions(session.epgRepository)
    }
    // Event callbacks must validate the scope that produced their rows, not a newer
    // collector value that can arrive before this composition is replaced.
    val channelScopeState = channelViewModel.scope.collectAsStateWithLifecycle().value
    val channelScope = channelScopeState.scope
    val scopeMotion = rememberTabContentMotion(channelScope.activeTagId) {
        channelViewModel.scope.value.scope.activeTagId
    }
    val observationState = channelViewModel.observation.collectAsStateWithLifecycle()
    val observation = observationState.value
    val currentSession = observation.currentSession
    val channels = channelScope.visibleChannels
    val orderedChannelIds = remember(channels) { channels.map { it.id } }
    val channelNumbers = remember(channels) {
        channels.associate { it.id to it.visibleChannelNumber }
    }
    val tagNotice by channelViewModel.unavailableTagNotice.collectAsStateWithLifecycle()
    val selectedChannelId by selection.selectedId.collectAsStateWithLifecycle()
    val activePlaybackTarget by playerSession.activeTarget.collectAsStateWithLifecycle()
    val playingChannelId = (activePlaybackTarget as? AppPlaybackTarget.Live)?.channelId
    val dvrEntries = observation.dvrEntries()
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
    val guideRecoveryAction = connectionUiState.primaryRecoveryAction()
    val hasGuideFailure = guideRecoveryAction == ConnectionRecoveryAction.RETRY
    val needsGuideSettings = guideRecoveryAction == ConnectionRecoveryAction.SETTINGS
    val hasGuideRecoveryAction = guideRecoveryAction != ConnectionRecoveryAction.NONE
    val guideRecovering = connectionUiState == ConnectionUiState.Connecting ||
        connectionUiState == ConnectionUiState.SyncingChannels ||
        connectionUiState == ConnectionUiState.Reconnecting

    val guideZoneId = remember { ZoneId.systemDefault() }
    val openedAtSec = remember { System.currentTimeMillis() / 1000L }
    val coverageWindowBounds = remember(openedAtSec, guideZoneId) {
        guideWindowBounds(openedAtSec, guideZoneId)
    }
    val windowBounds = remember(coverageWindowBounds, visibleWindowSec) {
        // Narrower text-driven capacity must not shorten the domain's seven-day horizon.
        coverageWindowBounds.copy(latestStartSec = coverageWindowBounds.latestStartSec + GUIDE_VISIBLE_WINDOW_SEC - visibleWindowSec)
    }
    val nowSecProvider = rememberCurrentEpochSeconds()
    // Player Back re-enters on the playing channel at wall-clock now. Outside the current
    // scope it keeps the prior browse position instead of changing the scope. Each
    // distinct return is positioned once, when the channel rows are available.
    var positionedPlayerReturn by remember { mutableStateOf<PlayerReturnFocus?>(null) }
    val pendingPlayerReturn = playerReturn?.takeIf { it !== positionedPlayerReturn }
    val playerReturnChannelId = pendingPlayerReturn?.playingChannelId
        ?.takeIf { id -> channels.any { it.id == id } }
    val restoredPosition = remember { guidePositionStore.position.value }
    val restoredTimelinePosition = remember(restoredPosition, windowBounds) {
        restoredPosition?.takeIf { it.windowStartSec in
            windowBounds.earliestStartSec..windowBounds.latestStartSec
        }
    }
    // Start with the available restored/browse viewport instead of constructing page zero
    // and discarding it in the initial-position effect. That effect still resolves the
    // asynchronous last-played identity and current coverage using its existing policy.
    val initialChannelId = playerReturnChannelId ?: restoredPosition?.channelId?.takeIf { id ->
        channels.any { it.id == id }
    } ?: playingChannelId ?: selectedChannelId
    val initialChannelIndex = channels.indexOfFirst { it.id == initialChannelId }.coerceAtLeast(0)
    val initialViewportIndex = restoredTimelinePosition
        ?.takeIf { position -> playerReturnChannelId == null && channels.any { it.id == position.channelId } }
        ?.firstVisibleColumn?.coerceIn(0, channels.lastIndex.coerceAtLeast(0))
        ?: (initialChannelIndex / CHANNEL_PAGE_SIZE) * CHANNEL_PAGE_SIZE
    val channelListState = rememberLazyListState(initialFirstVisibleItemIndex = initialViewportIndex)
    var windowStartSec by remember {
        mutableLongStateOf(
            restoredTimelinePosition?.windowStartSec?.takeIf { playerReturnChannelId == null }
                ?: floorGuideWindowToHour(openedAtSec, guideZoneId)
        )
    }
    val windowEndSec = windowStartSec + visibleWindowSec
    var selectedTarget by remember { mutableStateOf<EpgFocusTarget?>(null) }
    var realizedTarget by remember { mutableStateOf<EpgFocusTarget?>(null) }
    var lastFocusedEvent by remember { mutableStateOf<EpgEventEntry?>(null) }
    val gridFocus = remember { FocusRequester() }
    var scopeEntryRequested by remember { mutableStateOf(false) }
    var suppressActivationRelease by remember { mutableStateOf(false) }
    var consumeBackRelease by remember { mutableStateOf(false) }
    var pendingInitialChannelIndex by remember { mutableIntStateOf(-1) }
    var initialPositionDone by remember { mutableStateOf(false) }
    var detailsEvent by remember { mutableStateOf<EpgEventEntry?>(null) }
    var detailsObservation by remember { mutableStateOf<SessionObservation?>(null) }
    var detailsOpening by remember { mutableStateOf<Any?>(null) }
    var restoreDetailsFocus by remember { mutableStateOf(false) }
    var detailsFromSearch by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<ProgrammeAction?>(null) }
    var confirmationKey by remember(detailsOpening) { mutableStateOf<Key?>(null) }
    val confirmationKeyHandler: (KeyEvent) -> Boolean = { event ->
        // Dialog windows do not share preview dispatch. Keep the interrupted
        // confirmation's key cycle here until the refreshed details sees its release.
        if (event.key == confirmationKey && pendingAction == null) {
            if (event.type == KeyEventType.KeyUp) confirmationKey = null
            true
        } else {
            if (pendingAction != null && event.type == KeyEventType.KeyDown) confirmationKey = event.key
            if (event.key == confirmationKey && event.type == KeyEventType.KeyUp) confirmationKey = null
            false
        }
    }
    var configChoices by remember { mutableStateOf<List<DvrConfiguration>?>(null) }
    var pendingRecordingTarget by remember { mutableStateOf<ProgrammeRecordingTarget?>(null) }
    var pendingMutation by remember { mutableStateOf<DvrMutationAction?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<EpgSearchResult?>(null) }
    var searchObservation by remember { mutableStateOf<SessionObservation?>(null) }
    var searchRequest by remember { mutableStateOf<GuideSearchRequest?>(null) }
    var restoreSearchHeaderFocus by remember { mutableStateOf(false) }
    var restoreSearchResultFocus by remember { mutableStateOf<EventId?>(null) }
    var frontierRequest by remember { mutableStateOf<FrontierRequest?>(null) }
    var pendingFrontierOrigin by remember { mutableStateOf<FrontierOrigin?>(null) }
    var channelFocusRequest by remember { mutableStateOf<ChannelFocusRequest?>(null) }
    var channelRecoveryPage by remember { mutableStateOf<Set<ChannelId>?>(null) }
    var windowFocusRequest by remember { mutableStateOf<WindowFocusRequest?>(null) }
    var coverageRequestVersion by remember { mutableLongStateOf(0L) }
    val coverageRequests = remember(coroutineScope) {
        GuideCoverageRequestOwner(
            scope = coroutineScope,
            timeoutMillis = GUIDE_COVERAGE_NAVIGATION_TIMEOUT_MS,
            onPendingChanged = { coverageRequestVersion++ },
        )
    }
    DisposableEffect(coverageRequests) {
        onDispose { coverageRequests.dispose() }
    }
    var coverageSession by remember { mutableStateOf(currentSession) }
    var coverageTagId by remember { mutableStateOf(channelScope.activeTagId) }
    var coverageCategory by remember { mutableStateOf(category) }
    var lastPlayedId by remember { mutableStateOf<ChannelId?>(null) }
    var lastHeaderFocus by remember { mutableStateOf(GuideHeaderFocus.DATE) }
    var programmeFocusOwned by remember { mutableStateOf(true) }
    var preparationEntryToScope by remember { mutableStateOf(false) }
    val mayFocusProgramme by rememberUpdatedState(
        initialFocusEnabled && programmeFocusOwned &&
            detailsEvent == null && !showSearchDialog && !showJumpDialog &&
            pendingAction == null && configChoices == null && channelFocusRequest == null
    )

    val displayEvents = remember(observation.epgSnapshotForDisplay) { guideDisplayEvents(observation.epgSnapshotForDisplay) }
    val indexInput = GuideIndexInput(
        observation.epgSnapshotForDisplay, displayEvents, observation.epgState, category, windowStartSec, windowEndSec,
    )
    val preparationAuthority = currentSession ?: observation.epgSnapshotForDisplay
    val indexAuthority = remember(preparationAuthority, category, windowStartSec) { Any() }
    val preparedIndex = rememberPreparedBrowseData(preparationAuthority, indexInput, requestKey = indexAuthority) { input ->
        val preparationContext = currentCoroutineContext()
        val index = profileTrace("P44:guideIndex") {
            indexTimelineEventsByChannel(
                events = input.events,
                windowStartSec = input.windowStartSec,
                windowEndSec = input.windowEndSec,
                matches = {
                    preparationContext.ensureActive()
                    it.matchesProgrammeCategory(input.category)
                },
            )
        }
        GuideIndexPresentation(index, index.visibleEventsByChannel.values
            .flatMapTo(mutableSetOf()) { events -> events.map { it.id } })
    }
    // A completed publication for this window can be displayed even while newer
    // metadata is queued. Requiring the newest revision would starve entry under churn.
    val indexCurrent = preparedIndex?.requestKey == indexAuthority
    val projectionBehind = !indexCurrent || preparedIndex?.input != indexInput

    fun focusGuideHeader(): Boolean = runCatching {
        programmeFocusOwned = false
        when (lastHeaderFocus) {
            GuideHeaderFocus.DATE -> guideDateFocus.requestFocus()
            GuideHeaderFocus.NOW -> guideNowFocus.requestFocus()
            GuideHeaderFocus.SEARCH -> guideSearchFocus.requestFocus()
            GuideHeaderFocus.CLEAR_FILTER -> guideClearFilterFocus.requestFocus()
        }
    }.getOrDefault(false)

    fun focusGuideContent(): Boolean {
        // Coverage completion may populate a target, but only explicit entry returns ownership.
        if (frontierRequest != null || pendingFrontierOrigin != null ||
            channelFocusRequest != null || windowFocusRequest != null
        ) return true
        programmeFocusOwned = true
        val programmeFocus = selectedTarget?.eventId?.let(eventFocusRequesters::get)
        val target = guideEntryFocusTarget(
            hasProgrammeTarget = selectedTarget != null,
            hasRetryAction = hasGuideRecoveryAction,
        )
        return when (target) {
            GuideEntryFocusTarget.PROGRAMME -> {
                // Offscreen targets are composed and focused by the owning scroll effect.
                runCatching { programmeFocus?.requestFocus() }
                true
            }
            GuideEntryFocusTarget.RETRY -> {
                runCatching { guideRetryFocus.requestFocus() }.getOrDefault(false) ||
                    focusGuideHeader()
            }
            GuideEntryFocusTarget.HEADER -> focusGuideHeader()
        }
    }

    fun leaveGuideScope(): Boolean {
        scopeEntryRequested = true
        if (!indexCurrent || !channelScopeState.settingsLoaded || channelViewModel.scope.value != channelScopeState ||
            coverageTagId != channelScope.activeTagId || !initialPositionDone
        ) return true
        if (frontierRequest != null || pendingFrontierOrigin != null ||
            channelFocusRequest != null || windowFocusRequest != null
        ) return true
        scopeEntryRequested = false
        programmeFocusOwned = true
        val programmeFocus = selectedTarget?.eventId?.let(eventFocusRequesters::get)
        return when (
            guideScopeExitFocusTarget(
                hasProgrammeTarget = selectedTarget != null,
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

    LaunchedEffect(showSearchDialog, restoreSearchHeaderFocus, indexCurrent) {
        if (indexCurrent && !showSearchDialog && restoreSearchHeaderFocus) {
            withFrameNanos { }
            runCatching { guideSearchFocus.requestFocus() }
            restoreSearchHeaderFocus = false
        }
    }

    val epgState = preparedIndex?.input?.state ?: observation.epgState
    val timelineEventIndex = preparedIndex?.value?.index ?: emptyGuideIndex
    val currentEventIds = preparedIndex?.value?.eventIds.orEmpty()
    LaunchedEffect(currentEventIds) {
        eventFocusRequesters.keys.retainAll(currentEventIds)
    }
    val eventsByChannel = timelineEventIndex.visibleEventsByChannel
    val focusRows = remember(channels, timelineEventIndex) {
        channels.map { channel ->
            EpgFocusColumn(
                channelId = channel.id,
                events = eventsByChannel[channel.id].orEmpty(),
            )
        }
    }
    val edgeEvents = remember(preparedIndex, orderedChannelIds) {
        val ids = orderedChannelIds.toSet()
        preparedIndex?.input?.let { input ->
            input.events.filter { it.channelId in ids && it.matchesProgrammeCategory(input.category) }
        }.orEmpty()
    }
    val earlierContent = edgeEvents.any {
        it.start.epochSeconds < windowStartSec && it.stop.epochSeconds > windowBounds.earliestStartSec
    }
    val laterContent = edgeEvents.any {
        it.stop.epochSeconds > windowEndSec && it.start.epochSeconds < windowBounds.latestStartSec + visibleWindowSec
    }
    val focusStartInset = horizontalFocusReserve + if (earlierContent) GuideEdgeFade else 0.dp
    val focusEndInset = horizontalFocusReserve + if (laterContent) GuideEdgeFade else 0.dp
    val terminalReserve = guideTerminalViewportReserve(
        earlierContent, laterContent, layoutDirection == LayoutDirection.Rtl, horizontalFocusReserve,
    )
    val focusTrackWidthPx = timelineWidthPx - with(density) { terminalReserve.roundToPx() }
    fun focusWindowFor(event: EpgEventEntry): Long = guideFocusWindowStart(
        event.start.epochSeconds, event.stop.epochSeconds, windowStartSec, visibleWindowSec,
        focusTrackWidthPx, with(density) { focusStartInset.toPx() }, with(density) { focusEndInset.toPx() },
        windowBounds.earliestStartSec, windowBounds.latestStartSec,
    )
    fun channelPageRange(channelIndex: Int): IntRange {
        if (channels.isEmpty()) return 0 until 0
        val boundedIndex = channelIndex.coerceIn(channels.indices)
        val pageStart = (boundedIndex / CHANNEL_PAGE_SIZE) * CHANNEL_PAGE_SIZE
        return pageStart until (pageStart + CHANNEL_PAGE_SIZE).coerceAtMost(channels.size)
    }

    fun channelPageIds(channelIndex: Int): List<ChannelId> =
        channelPageRange(channelIndex).map { channels[it].id }

    fun requestVisibleWindow(
        anchorSec: Long,
        channelIndex: Int,
    ): GuideCoverageRequestToken? {
        val capability = currentSession ?: return null
        val ids = channelPageIds(channelIndex)
        if (ids.isEmpty()) return null
        val displayAnchorSec = windowBounds.constrain(anchorSec)
        if (displayAnchorSec + visibleWindowSec <= nowSecProvider()) return null
        // Acquisition retains its existing three-hour span and seven-day bound; only
        // presentation capacity shrinks with text. Track pending work by the shown window.
        val boundedAnchorSec = coverageWindowBounds.constrain(displayAnchorSec)
        val through = KotlinInstant.fromEpochSeconds(
            boundedAnchorSec + GUIDE_VISIBLE_WINDOW_SEC
        )
        return coverageRequests.request(
            channelIds = ids,
            windowStartSec = displayAnchorSec,
        ) { channelIds ->
            // The SDK takes its metadata monitor before its first suspension. Keep that
            // potentially contended non-UI call off Main; request generations and their
            // completion remain owned by the composition's main-thread coroutine.
            withContext(Dispatchers.Default) {
                profileTrace("P48:coverageAcquire") { }
                session.epgRepository.acquireCoverageBatch(capability, channelIds, through)
            }
        }
    }

    fun deferFrontierOrigin(request: FrontierRequest) {
        windowStartSec = request.originWindowStartSec
        pendingFrontierOrigin = request.toOrigin()
        pendingInitialChannelIndex = -1
        selectedTarget = null
    }

    fun restoreFrontierOrigin(request: FrontierRequest) {
        if (windowStartSec == request.originWindowStartSec && selectedTarget?.eventId == request.originEventId) return
        deferFrontierOrigin(request)
    }

    fun clearAllCoverage(restoreFrontier: Boolean = false) {
        if (restoreFrontier) frontierRequest?.let(::deferFrontierOrigin)
        else pendingFrontierOrigin = null
        coverageRequests.cancelAll()
        frontierRequest = null
        channelFocusRequest = null
        channelRecoveryPage = null
        windowFocusRequest = null
    }

    fun completeFrontierRequest(request: FrontierRequest) {
        coverageRequests.cancel(request.coverageRequest)
        if (frontierRequest == request) {
            frontierRequest = null
        }
    }

    fun completeChannelFocusRequest(request: ChannelFocusRequest) {
        coverageRequests.cancel(request.coverageRequest)
        if (channelFocusRequest == request) {
            channelFocusRequest = null
        }
    }

    fun restoreChannelFocusOrigin(request: ChannelFocusRequest): Boolean {
        completeChannelFocusRequest(request)
        val resolution = resolveGuideFrontierOrigin(
            rows = focusRows,
            channelId = request.originChannelId,
            eventId = request.originEventId,
            connectionReady = currentSession != null && connectionUiState == ConnectionUiState.Ready,
            hasCurrentSnapshot = epgState.currentEpgSnapshot() != null,
            timedOut = false,
        )
        if (resolution !is GuideDeferredOriginResolution.Restore) {
            channelRecoveryPage = request.coverageRequest.generations.keys
            selectedTarget = null
            focusGuideHeader()
            return false
        }
        selectedTarget = resolution.target
        return true
    }

    fun completeWindowFocusRequest(request: WindowFocusRequest) {
        coverageRequests.cancel(request.coverageRequest)
        if (windowFocusRequest == request) {
            windowFocusRequest = null
        }
    }

    fun cancelPendingProgrammeNavigation() {
        frontierRequest?.let { request ->
            restoreFrontierOrigin(request)
            completeFrontierRequest(request)
        }
        channelFocusRequest?.let { request ->
            // Removing the origin may move Compose focus to a header during apply,
            // before the channel-navigation effect can observe the disappearance.
            if (focusRows.firstOrNull { it.channelId == request.originChannelId }
                    ?.events?.none { it.id == request.originEventId } != false
            ) {
                channelRecoveryPage = request.coverageRequest.generations.keys
                selectedTarget = null
                pendingInitialChannelIndex = channels.indexOfFirst {
                    it.id == request.preferredChannelId
                }.coerceAtLeast(0)
            }
            completeChannelFocusRequest(request)
        }
        // Window jumps have no loaded origin. Retain their coverage-gated selection,
        // but relinquish automatic focus separately until explicit content entry.
    }

    fun releaseProgrammeFocus() {
        suppressActivationRelease = false
        scopeEntryRequested = false
        programmeFocusOwned = false
        cancelPendingProgrammeNavigation()
    }

    fun focusScopeFromProgramme() {
        releaseProgrammeFocus()
        if (hasScopeTabs) scopeFocus.requestFocus() else focusGuideHeader()
    }

    val programmeBackEnabled = indexCurrent && initialFocusEnabled && programmeFocusOwned &&
        detailsEvent == null && !showSearchDialog && !showJumpDialog &&
        pendingAction == null && configChoices == null
    BackHandler(enabled = programmeBackEnabled) { focusScopeFromProgramme() }

    LaunchedEffect(initialFocusEnabled) {
        if (!initialFocusEnabled) {
            scopeEntryRequested = false
            cancelPendingProgrammeNavigation()
        }
    }

    fun coveragePending(token: GuideCoverageRequestToken): Boolean =
        coverageRequestVersion.let {
            coverageRequests.isPending(token)
        }

    fun pageCoverageSettled(channelIndex: Int): Boolean {
        val snapshot = observation.epgState.currentEpgSnapshot() ?: return false
        if (windowEndSec <= nowSecProvider()) return true // Local retained data, not claimed server coverage.
        return guideChannelPageCoverageSettled(
            channelIds = channelPageIds(channelIndex),
            coverages = snapshot.coverages,
            requestedThrough = KotlinInstant.fromEpochSeconds(windowEndSec),
        )
    }

    fun unsettledPageIndex(channelIndex: Int, targetIndex: Int): Int? =
        firstUnsettledGuidePageIndex(
            currentChannelIndex = channelIndex,
            targetChannelIndex = targetIndex,
            channelCount = channels.size,
            pageSize = CHANNEL_PAGE_SIZE,
            coverageSettled = ::pageCoverageSettled,
        )

    fun requestChannelFocus(
        current: EpgFocusTarget,
        preferredChannelIndex: Int,
        direction: Int,
    ) {
        val preferredChannel = channels.getOrNull(preferredChannelIndex) ?: return
        val originChannel = channels.getOrNull(current.channelIndex) ?: return
        val coverageRequest = requestVisibleWindow(windowStartSec, preferredChannelIndex) ?: return
        channelRecoveryPage = null
        channelFocusRequest = ChannelFocusRequest(
            originChannelId = originChannel.id,
            originEventId = current.eventId,
            preferredChannelId = preferredChannel.id,
            direction = direction,
            coverageRequest = coverageRequest,
            throughSec = windowEndSec,
        )
    }

    val frontierAcquisitionPending = frontierRequest?.let { request ->
        coveragePending(request.coverageRequest)
    } == true
    val channelAcquisitionPending = channelFocusRequest?.let { request ->
        coveragePending(request.coverageRequest)
    } == true
    val windowAcquisitionPending = windowFocusRequest?.let { request ->
        coveragePending(request.coverageRequest)
    } == true

    LaunchedEffect(
        channels,
        focusRows,
        playingChannelId,
        lastPlayedId,
        initialPositionDone,
        category,
        indexCurrent,
        pendingPlayerReturn,
    ) {
        if (!indexCurrent || initialPositionDone || channels.isEmpty() || focusRows.size != channels.size) {
            return@LaunchedEffect
        }
        if (playerReturnChannelId != null) {
            val nowWindow = windowBounds.constrain(floorGuideWindowToHour(nowSecProvider(), guideZoneId))
            // Re-enters once the index for the current-hour window is prepared.
            if (windowStartSec != nowWindow) {
                windowStartSec = nowWindow
                return@LaunchedEffect
            }
        }
        val preferredId = playerReturnChannelId ?: playingChannelId ?: lastPlayedId ?: selectedChannelId
        val restoredChannelPosition = restoredPosition?.takeIf { position ->
            playerReturnChannelId == null && channels.any { it.id == position.channelId }
        }
        val restored = restoredTimelinePosition?.takeIf { position ->
            playerReturnChannelId == null && channels.any { it.id == position.channelId }
        }
        val channelId = browsingFocusChannelId(
            channels,
            restoredChannelPosition?.channelId ?: preferredId,
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
        requestVisibleWindow(windowStartSec, targetChannelIndex)
        if (target != null) {
            selectedTarget = target
            channelListState.scrollToItem(
                restored?.firstVisibleColumn?.coerceIn(channels.indices)
                    ?: (targetChannelIndex / CHANNEL_PAGE_SIZE) * CHANNEL_PAGE_SIZE
            )
        }
        // A return that waited for rows reclaims entry from the empty-state fallback.
        if (playerReturnChannelId != null) programmeFocusOwned = true
        positionedPlayerReturn = pendingPlayerReturn
        initialPositionDone = true
    }

    LaunchedEffect(
        focusRows, selectedTarget, pendingFrontierOrigin, initialPositionDone,
        channelFocusRequest, channelRecoveryPage, epgState, connectionUiState,
        indexCurrent,
    ) {
        if (!indexCurrent || !initialPositionDone || focusRows.size != channels.size || channels.isEmpty()) {
            return@LaunchedEffect
        }
        if (
            frontierRequest != null ||
            pendingFrontierOrigin != null ||
            channelFocusRequest != null ||
            windowFocusRequest != null
        ) {
            return@LaunchedEffect
        }
        val current = selectedTarget
        // Owned focus in retained history returns to the header if that metadata vanishes.
        // A focus-safe fractional window can also show live cells, unlike a wholly past
        // page; those incidental neighbours must not silently take the removed target.
        if (current != null && lastFocusedEvent?.let {
                it.id == current.eventId && it.stop.epochSeconds <= nowSecProvider()
            } == true && focusRows.getOrNull(current.channelIndex)?.events?.none { it.id == current.eventId } != false
        ) {
            selectedTarget = null
            if (mayFocusProgramme) focusGuideHeader()
            return@LaunchedEffect
        }
        val preferredIndex = current?.channelIndex
            ?: pendingInitialChannelIndex.takeIf { it >= 0 }
            ?: 0
        // A lost channel-navigation origin must not reopen unrestricted cache search.
        // Retain the interrupted page's coverage boundary even after cancelling its job.
        val recoveryPage = channelRecoveryPage
        val replacement = if (recoveryPage != null) {
            val snapshot = epgState.currentEpgSnapshot()
                .takeIf { connectionUiState == ConnectionUiState.Ready }
            (resolveGuideWindowFocus(
                rows = focusRows,
                preferredChannelId = channels[preferredIndex.coerceIn(channels.indices)].id,
                targetSec = windowStartSec,
                requestedChannelIds = recoveryPage,
                coverages = snapshot?.coverages.orEmpty(),
                requestedThrough = KotlinInstant.fromEpochSeconds(windowEndSec),
                connectionReady = connectionUiState == ConnectionUiState.Ready,
                hasCurrentSnapshot = snapshot != null,
                acquisitionPending = false,
            ) as? GuideCoverageFocusResolution.Select)?.target
        } else {
            reconcileTimelineEpgFocus(
                rows = focusRows,
                current = current,
                preferredChannelIndex = preferredIndex,
                targetSec = windowStartSec,
            )
        }
        if (replacement == current) return@LaunchedEffect
        selectedTarget = replacement
        if (replacement != null) {
            channelRecoveryPage = null
            pendingInitialChannelIndex = replacement.channelIndex
        }
    }

    LaunchedEffect(channelScope.activeTagId) {
        if (coverageTagId == channelScope.activeTagId) return@LaunchedEffect
        coverageTagId = channelScope.activeTagId
        clearAllCoverage()
        initialPositionDone = false
        selectedTarget = null
        pendingInitialChannelIndex = -1
        frontierRequest = null
        channelFocusRequest = null
        windowFocusRequest = null
    }

    // A later player return on a retained composition re-runs the initial position.
    LaunchedEffect(pendingPlayerReturn, channels) {
        val request = pendingPlayerReturn ?: return@LaunchedEffect
        if (!initialPositionDone || channels.isEmpty()) return@LaunchedEffect
        if (playerReturnChannelId == null) {
            positionedPlayerReturn = request
            return@LaunchedEffect
        }
        initialPositionDone = false
        selectedTarget = null
        pendingInitialChannelIndex = -1
        frontierRequest = null
        channelFocusRequest = null
        windowFocusRequest = null
    }

    LaunchedEffect(category) {
        if (coverageCategory == category) return@LaunchedEffect
        coverageCategory = category
        clearAllCoverage()
        initialPositionDone = false
        selectedTarget = null
        pendingInitialChannelIndex = -1
        frontierRequest = null
        channelFocusRequest = null
        windowFocusRequest = null
    }

    LaunchedEffect(currentSession) {
        if (coverageSession !== currentSession) {
            coverageSession = currentSession
            clearAllCoverage(restoreFrontier = true)
        }
    }

    LaunchedEffect(currentSession, pendingFrontierOrigin, channels) {
        val origin = pendingFrontierOrigin ?: return@LaunchedEffect
        if (currentSession == null) return@LaunchedEffect
        val channelIndex = channels.indexOfFirst { it.id == origin.channelId }
        if (channelIndex >= 0) {
            requestVisibleWindow(origin.windowStartSec, channelIndex)
        }
    }

    LaunchedEffect(
        currentSession,
        connectionUiState,
        epgState,
        focusRows,
        channels,
        pendingFrontierOrigin,
        indexCurrent,
    ) {
        if (!indexCurrent) return@LaunchedEffect
        val origin = pendingFrontierOrigin ?: return@LaunchedEffect
        when (
            val resolution = resolveGuideFrontierOrigin(
                rows = focusRows,
                channelId = origin.channelId,
                eventId = origin.eventId,
                connectionReady = currentSession != null &&
                    connectionUiState == ConnectionUiState.Ready,
                hasCurrentSnapshot = epgState.currentEpgSnapshot() != null,
                timedOut = false,
            )
        ) {
            GuideDeferredOriginResolution.Wait -> return@LaunchedEffect
            GuideDeferredOriginResolution.Release -> pendingFrontierOrigin = null
            is GuideDeferredOriginResolution.Restore -> {
                windowStartSec = origin.windowStartSec
                pendingInitialChannelIndex = resolution.target.channelIndex
                selectedTarget = resolution.target
                pendingFrontierOrigin = null
            }
        }
    }

    LaunchedEffect(pendingFrontierOrigin) {
        val origin = pendingFrontierOrigin ?: return@LaunchedEffect
        delay(GUIDE_COVERAGE_NAVIGATION_TIMEOUT_MS)
        if (
            pendingFrontierOrigin == origin &&
            resolveGuideFrontierOrigin(
                rows = focusRows,
                channelId = origin.channelId,
                eventId = origin.eventId,
                connectionReady = currentSession != null &&
                    connectionUiState == ConnectionUiState.Ready,
                hasCurrentSnapshot = epgState.currentEpgSnapshot() != null,
                timedOut = true,
            ) == GuideDeferredOriginResolution.Release
        ) {
            pendingFrontierOrigin = null
        }
    }

    LaunchedEffect(connectionUiState) {
        if (connectionUiState != ConnectionUiState.Ready) {
            coverageRequests.cancelAll()
        }
    }

    LaunchedEffect(scopeEntryRequested, channelScopeState, coverageTagId, initialPositionDone,
        selectedTarget, frontierRequest, pendingFrontierOrigin, channelFocusRequest, windowFocusRequest, indexCurrent) {
        if (scopeEntryRequested && initialFocusEnabled) leaveGuideScope()
    }

    // The viewport is a native focus target only used as a bridge while its focused
    // cell is being replaced. Programme cells retain their own TV/accessibility focus.
    fun retainGridFocus(): Boolean = runCatching { gridFocus.requestFocus() }.getOrDefault(false).also {
        if (it) realizedTarget = null
    }

    LaunchedEffect(
        selectedTarget,
        windowStartSec,
        visibleWindowSec,
        focusTrackWidthPx,
        earlierContent,
        laterContent,
        channels,
        initialFocusEnabled,
        mayFocusProgramme,
        initialPositionDone,
        connectionUiState,
        indexCurrent,
    ) {
        if (!indexCurrent) return@LaunchedEffect
        val target = selectedTarget
        if (target == null) {
            if (
                mayFocusProgramme &&
                frontierRequest == null && pendingFrontierOrigin == null &&
                channelFocusRequest == null &&
                windowFocusRequest == null &&
                (initialPositionDone || channels.isEmpty())
            ) {
                withFrameNanos { }
                if (mayFocusProgramme) focusGuideContent()
            }
            return@LaunchedEffect
        }
        if (!mayFocusProgramme || channelFocusRequest != null) return@LaunchedEffect
        val event = focusRows.getOrNull(target.channelIndex)?.events
            ?.firstOrNull { it.id == target.eventId }
            ?: return@LaunchedEffect
        val targetWindow = focusWindowFor(event)
        if (targetWindow != windowStartSec) {
            if (retainGridFocus()) windowStartSec = targetWindow
            return@LaunchedEffect
        }
        val visibleRows = channelListState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visibleRows.isNotEmpty() && target.channelIndex !in visibleRows) {
            channelListState.animateScrollToItem(target.channelIndex)
        }
        if (mayFocusProgramme && realizedTarget != target) {
            // Already attached cells need no artificial one-frame input delay.
            if (eventFocusRequesters[target.eventId]?.let {
                    runCatching { it.requestFocus() }.getOrDefault(false)
                } == true
            ) return@LaunchedEffect
            withFrameNanos { }
            if (!mayFocusProgramme || channelFocusRequest != null || selectedTarget != target) {
                return@LaunchedEffect
            }
            val focused = eventFocusRequesters[target.eventId]?.let { requester ->
                runCatching { requester.requestFocus() }.getOrDefault(false)
            } == true
            if (!focused) focusGuideHeader()
        }
    }

    LaunchedEffect(
        epgState,
        focusRows,
        channels,
        category,
        connectionUiState,
        frontierRequest,
        frontierAcquisitionPending,
        indexCurrent,
    ) {
        if (!indexCurrent) return@LaunchedEffect
        val request = frontierRequest ?: return@LaunchedEffect
        val channelIndex = channels.indexOfFirst { it.id == request.channelId }
        if (channelIndex < 0) {
            restoreFrontierOrigin(request)
            completeFrontierRequest(request)
            return@LaunchedEffect
        }
        val through = KotlinInstant.fromEpochSeconds(request.throughSec)
        val snapshot = epgState.currentEpgSnapshot()
            .takeIf { connectionUiState == ConnectionUiState.Ready }
        val frontierEvents = withContext(Dispatchers.Default) {
            displayEvents.filter {
                it.channelId == request.channelId &&
                    it.stop.epochSeconds > request.throughSec - visibleWindowSec &&
                    it.start.epochSeconds < request.throughSec &&
                    it.matchesProgrammeCategory(category)
            }
        }
        if (frontierRequest != request) return@LaunchedEffect
        val target = snapshot?.let {
            timelineFrontierFocus(
                rows = listOf(EpgFocusColumn(request.channelId, frontierEvents)),
                channelId = request.channelId,
                originEventId = request.originEventId,
                boundarySec = request.boundarySec,
                direction = request.direction,
            )?.copy(channelIndex = channelIndex)
        }
        val coverageSettled = snapshot?.let { current ->
            guideChannelPageCoverageSettled(
                channelIds = request.coverageRequest.generations.keys.toList(),
                coverages = current.coverages,
                requestedThrough = through,
            )
        } == true
        if (!coverageSettled && projectionBehind) return@LaunchedEffect
        if (coverageSettled && target != null) {
            if (mayFocusProgramme && !retainGridFocus()) return@LaunchedEffect
            pendingInitialChannelIndex = target.channelIndex
            windowStartSec = request.throughSec - visibleWindowSec
            selectedTarget = target
        } else if (
            shouldWaitForGuideCoverage(
                connectionReady = connectionUiState == ConnectionUiState.Ready,
                hasCurrentSnapshot = snapshot != null,
                acquisitionPending = frontierAcquisitionPending,
                coverageSettled = coverageSettled,
            )
        ) {
            return@LaunchedEffect
        } else {
            restoreFrontierOrigin(request)
        }
        completeFrontierRequest(request)
    }

    LaunchedEffect(frontierRequest) {
        val request = frontierRequest ?: return@LaunchedEffect
        delay(GUIDE_COVERAGE_NAVIGATION_TIMEOUT_MS)
        if (frontierRequest != request) return@LaunchedEffect
        restoreFrontierOrigin(request)
        completeFrontierRequest(request)
    }

    LaunchedEffect(
        epgState,
        focusRows,
        channels,
        connectionUiState,
        channelFocusRequest,
        channelAcquisitionPending,
        indexCurrent,
    ) {
        if (!indexCurrent) return@LaunchedEffect
        val request = channelFocusRequest ?: return@LaunchedEffect
        val originChannelIndex = channels.indexOfFirst { it.id == request.originChannelId }
        val preferredChannelIndex = channels.indexOfFirst { it.id == request.preferredChannelId }
        val originPresent = focusRows.firstOrNull { it.channelId == request.originChannelId }
            ?.events?.any { it.id == request.originEventId } == true
        if (originChannelIndex < 0 || preferredChannelIndex < 0 || !originPresent) {
            channelRecoveryPage = request.coverageRequest.generations.keys
            selectedTarget = null
            pendingInitialChannelIndex = preferredChannelIndex.coerceAtLeast(0)
            focusGuideHeader()
            completeChannelFocusRequest(request)
            return@LaunchedEffect
        }
        val snapshot = epgState.currentEpgSnapshot()
            .takeIf { connectionUiState == ConnectionUiState.Ready }
        val target = snapshot?.let {
            timelinePageFocusTarget(
                rows = focusRows,
                current = EpgFocusTarget(originChannelIndex, request.originEventId),
                preferredChannelIndex = preferredChannelIndex,
                direction = request.direction,
                searchChannelIds = request.coverageRequest.generations.keys,
            )
        }
        val through = KotlinInstant.fromEpochSeconds(request.throughSec)
        val coverageSettled = snapshot?.let { current ->
            guideChannelPageCoverageSettled(
                channelIds = request.coverageRequest.generations.keys.toList(),
                coverages = current.coverages,
                requestedThrough = through,
            )
        } == true
        if (!coverageSettled && projectionBehind) return@LaunchedEffect
        if (coverageSettled && target != null) {
            pendingInitialChannelIndex = target.channelIndex
            selectedTarget = target
        } else if (
            shouldWaitForGuideCoverage(
                connectionReady = connectionUiState == ConnectionUiState.Ready,
                hasCurrentSnapshot = snapshot != null,
                acquisitionPending = channelAcquisitionPending,
                coverageSettled = coverageSettled,
            )
        ) {
            return@LaunchedEffect
        }
        completeChannelFocusRequest(request)
    }

    LaunchedEffect(channelFocusRequest) {
        val request = channelFocusRequest ?: return@LaunchedEffect
        delay(GUIDE_COVERAGE_NAVIGATION_TIMEOUT_MS)
        if (channelFocusRequest == request) completeChannelFocusRequest(request)
    }

    LaunchedEffect(
        epgState,
        focusRows,
        connectionUiState,
        windowFocusRequest,
        windowAcquisitionPending,
        indexCurrent,
    ) {
        if (!indexCurrent) return@LaunchedEffect
        val request = windowFocusRequest ?: return@LaunchedEffect
        val snapshot = epgState.currentEpgSnapshot()
        if (projectionBehind && !guideChannelPageCoverageSettled(
                channelIds = request.coverageRequest.generations.keys.toList(),
                coverages = snapshot?.coverages.orEmpty(),
                requestedThrough = KotlinInstant.fromEpochSeconds(request.throughSec),
            )) return@LaunchedEffect
        when (
            val resolution = resolveGuideWindowFocus(
                rows = focusRows,
                preferredChannelId = request.preferredChannelId,
                targetSec = request.windowStartSec,
                requestedChannelIds = request.coverageRequest.generations.keys,
                coverages = snapshot?.coverages.orEmpty(),
                requestedThrough = KotlinInstant.fromEpochSeconds(request.throughSec),
                connectionReady = connectionUiState == ConnectionUiState.Ready,
                hasCurrentSnapshot = snapshot != null,
                acquisitionPending = windowAcquisitionPending,
            )
        ) {
            GuideCoverageFocusResolution.Wait -> return@LaunchedEffect
            GuideCoverageFocusResolution.Release -> Unit
            is GuideCoverageFocusResolution.Select -> {
                pendingInitialChannelIndex = resolution.target.channelIndex
                selectedTarget = resolution.target
            }
        }
        completeWindowFocusRequest(request)
    }

    LaunchedEffect(windowFocusRequest) {
        val request = windowFocusRequest ?: return@LaunchedEffect
        delay(GUIDE_COVERAGE_NAVIGATION_TIMEOUT_MS)
        if (windowFocusRequest == request) completeWindowFocusRequest(request)
    }

    fun pageColumns(direction: Int) {
        if (channels.isEmpty()) return
        channelFocusRequest?.takeIf { it.direction == -direction }?.let {
            if (!restoreChannelFocusOrigin(it)) return
        }
        if (
            frontierRequest != null ||
            pendingFrontierOrigin != null ||
            channelFocusRequest != null ||
            windowFocusRequest != null
        ) {
            return
        }
        programmeFocusOwned = true
        val current = selectedTarget ?: return
        val visibleCount = channelListState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(2)
        val targetIndex = ChannelNavigation.pageTargetIndex(
            itemCount = channels.size,
            currentIndex = current.channelIndex,
            visibleItemCount = visibleCount,
            direction = direction,
        ) ?: return
        val unsettledIndex = unsettledPageIndex(current.channelIndex, targetIndex)
        if (unsettledIndex != null) {
            requestChannelFocus(current, unsettledIndex, direction)
            return
        }
        val targetPageIds = channelPageIds(targetIndex).toSet()
        val target = timelinePageFocusTarget(
            rows = focusRows,
            current = current,
            preferredChannelIndex = targetIndex,
            direction = direction,
            searchChannelIds = targetPageIds,
        )
        if (target == null) return
        selectedTarget = target
    }

    fun jumpToWindow(targetSec: Long) {
        val channelIndex = selectedTarget?.channelIndex
            ?: pendingInitialChannelIndex.takeIf { it >= 0 }
            ?: 0
        val preferredChannelId = channels.getOrNull(channelIndex)?.id ?: return
        val boundedTargetSec = windowBounds.constrain(targetSec)
        clearAllCoverage()
        programmeFocusOwned = true
        pendingInitialChannelIndex = channelIndex
        selectedTarget = null
        windowStartSec = boundedTargetSec
        val coverageRequest = requestVisibleWindow(boundedTargetSec, channelIndex) ?: return
        windowFocusRequest = WindowFocusRequest(
            preferredChannelId = preferredChannelId,
            windowStartSec = boundedTargetSec,
            coverageRequest = coverageRequest,
            throughSec = boundedTargetSec + visibleWindowSec,
        )
    }

    fun moveFocus(direction: EpgFocusDirection): Boolean {
        if (channelViewModel.scope.value != channelScopeState) return true
        when (guidePendingNavigationAction(
            direction = direction,
            frontierDirection = frontierRequest?.direction,
            channelDirection = channelFocusRequest?.direction,
            windowPending = windowFocusRequest != null,
            originPending = pendingFrontierOrigin != null,
        )) {
            GuidePendingNavigationAction.WAIT -> return true
            GuidePendingNavigationAction.RESTORE_ORIGIN -> {
                frontierRequest?.let { request ->
                    restoreFrontierOrigin(request)
                    completeFrontierRequest(request)
                }
                return true
            }
            GuidePendingNavigationAction.HEADER -> {
                cancelPendingProgrammeNavigation()
                // Keep window coverage gating alive, without its permission to move focus.
                focusGuideHeader()
                return true
            }
            GuidePendingNavigationAction.MOVE -> {
                channelFocusRequest?.let {
                    if (!restoreChannelFocusOrigin(it)) return true
                }
            }
        }
        programmeFocusOwned = true
        val current = selectedTarget ?: return true
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
                val previousChannelIndex = if (current.channelIndex > 0) {
                    unsettledPageIndex(current.channelIndex, 0)
                } else {
                    null
                }
                if (previousChannelIndex != null) {
                    requestChannelFocus(current, previousChannelIndex, -1)
                    return true
                }
                if (hasScopeTabs) {
                    scopeFocus.requestFocus()
                } else {
                    focusGuideHeader()
                }
                return true
            }
            move.timeFrontierDirection != 0 -> {
                val currentEvent = focusRows[current.channelIndex].events
                    .firstOrNull { it.id == current.eventId }
                    ?: return true
                val boundarySec = if (move.timeFrontierDirection > 0) {
                    currentEvent.stop.epochSeconds
                } else {
                    currentEvent.start.epochSeconds
                }
                // A display-sized step can still land wholly inside a long programme.
                // Reach its edge so the adjacent programme is eligible in the next window.
                val targetWindowStartSec = windowBounds.constrain(
                    if (move.timeFrontierDirection > 0) {
                        maxOf(windowStartSec + visibleWindowSec, boundarySec)
                    } else {
                        minOf(windowStartSec - visibleWindowSec, boundarySec - visibleWindowSec)
                    }
                )
                if (targetWindowStartSec == windowStartSec) return true
                val originWindowStartSec = windowStartSec
                if (targetWindowStartSec + visibleWindowSec <= nowSecProvider()) {
                    val channelId = channels[current.channelIndex].id
                    val pastEvents = displayEvents.filter { it.channelId == channelId &&
                        it.stop.epochSeconds > targetWindowStartSec &&
                        it.start.epochSeconds < targetWindowStartSec + visibleWindowSec &&
                        it.matchesProgrammeCategory(category) }
                    val target = timelineFrontierFocus(
                        rows = listOf(EpgFocusColumn(channelId, pastEvents)), channelId = channelId,
                        originEventId = current.eventId, boundarySec = boundarySec,
                        direction = move.timeFrontierDirection,
                    ) ?: return true
                    windowStartSec = targetWindowStartSec
                    selectedTarget = target.copy(channelIndex = current.channelIndex)
                    return true
                }
                val coverageRequest = requestVisibleWindow(
                    targetWindowStartSec,
                    current.channelIndex,
                ) ?: return true
                frontierRequest = FrontierRequest(
                    channelId = channels[current.channelIndex].id,
                    originEventId = current.eventId,
                    boundarySec = boundarySec,
                    direction = move.timeFrontierDirection,
                    originWindowStartSec = originWindowStartSec,
                    coverageRequest = coverageRequest,
                    throughSec = targetWindowStartSec + visibleWindowSec,
                )
                return true
            }
            move.target != current -> {
                val step = if (direction == EpgFocusDirection.UP) -1 else 1
                val unsettledIndex = unsettledPageIndex(
                    current.channelIndex,
                    move.target.channelIndex,
                )
                if (unsettledIndex != null) {
                    requestChannelFocus(current, unsettledIndex, step)
                    return true
                }
                val next = move.target
                selectedTarget = next
                val event = focusRows.getOrNull(next.channelIndex)?.events
                    ?.firstOrNull { it.id == next.eventId }
                if (mayFocusProgramme && event != null &&
                    focusWindowFor(event) == windowStartSec &&
                    channelListState.layoutInfo.visibleItemsInfo.any { it.index == next.channelIndex }
                ) {
                    // Attached, visible cells can accept native focus in this key dispatch.
                    // Window/coverage/virtualized targets retain the effect-driven handoff.
                    eventFocusRequesters[next.eventId]?.let { runCatching { it.requestFocus() } }
                }
                return true
            }
            else -> {
                if (direction == EpgFocusDirection.UP || direction == EpgFocusDirection.DOWN) {
                    val step = if (direction == EpgFocusDirection.UP) -1 else 1
                    val boundaryIndex = if (step < 0) 0 else channels.lastIndex
                    val preferredChannelIndex = unsettledPageIndex(
                        current.channelIndex,
                        boundaryIndex,
                    )
                    if (preferredChannelIndex != null) {
                        requestChannelFocus(current, preferredChannelIndex, step)
                    }
                }
                return true
            }
        }
    }

    LaunchedEffect(detailsEvent, restoreDetailsFocus, initialFocusEnabled, indexCurrent) {
        if (!indexCurrent) return@LaunchedEffect
        if (detailsEvent == null && restoreDetailsFocus) {
            withFrameNanos { }
            if (mayFocusProgramme) {
                selectedTarget?.eventId?.let(eventFocusRequesters::get)?.let { requester ->
                    runCatching { requester.requestFocus() }
                }
            }
            restoreDetailsFocus = false
        }
    }

    fun closeDetails() {
        val searchEventId = detailsEvent?.id.takeIf { detailsFromSearch }
        detailsEvent = null
        detailsObservation = null
        detailsOpening = null
        if (showSearchDialog && searchEventId != null) {
            restoreSearchResultFocus = searchEventId
        } else {
            programmeFocusOwned = true
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
            }
            closeSearch()
        }
    }

    val categoryLabel = programmeCategoryLabel(category)
    Box(Modifier.fillMaxSize().testTag("epg-screen")) content@{
        BrowsePreparationPending(contentPadding, initialFocusEnabled, ready = preparedIndex != null,
            onPendingFocus = {
                // Cold entry starts on the scope; a player return enters its programme.
                preparationEntryToScope = (!initialPositionDone && pendingPlayerReturn == null) || !programmeFocusOwned
                realizedTarget = null
                if (preparationEntryToScope) programmeFocusOwned = false
            },
            onReadyFocus = {
                if (!indexCurrent || !channelScopeState.settingsLoaded ||
                    channelViewModel.scope.value != channelScopeState
                ) false else if (preparationEntryToScope) {
                    programmeFocusOwned = false
                    if (hasScopeTabs) runCatching { scopeFocus.requestFocus() }.getOrDefault(false)
                    else focusGuideHeader()
                } else {
                    selectedTarget?.eventId?.let(eventFocusRequesters::get)?.let {
                        runCatching { it.requestFocus() }.getOrDefault(false)
                    } ?: focusGuideHeader()
                }
            },
            handoffKey = Triple(channelScopeState, selectedTarget, indexCurrent),
        )
        if (preparedIndex == null) return@content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties {
                    onEnter = {
                        if (requestedFocusDirection == FocusDirection.Right) {
                            if (hasScopeTabs) scopeFocus.requestFocus() else guideDateFocus.requestFocus()
                        }
                    }
                }
                .focusGroup()
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Back) {
                        if (consumeBackRelease) {
                            if (event.type == KeyEventType.KeyUp) consumeBackRelease = false
                            return@onPreviewKeyEvent true
                        }
                        if (event.type == KeyEventType.KeyDown && programmeBackEnabled) {
                            consumeBackRelease = true
                            focusScopeFromProgramme()
                            return@onPreviewKeyEvent true
                        }
                    }
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
                        start = startPadding + TvBrowseLeadingInset,
                        top = contentPadding.calculateTopPadding(),
                        end = maxOf(endPadding, TvBrowseTrailingInset),
                    )
                    .heightIn(min = with(density) {
                        maxOf(40.dp, MaterialTheme.typography.headlineMedium.lineHeight.toDp(),
                            MaterialTheme.typography.labelLarge.lineHeight.toDp() + 16.dp)
                    }),
                actions = {
                    OutlinedButton(
                        onClick = {
                            releaseProgrammeFocus()
                            showJumpDialog = true
                        },
                        modifier = Modifier
                            .focusRequester(guideDateFocus)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    releaseProgrammeFocus()
                                    lastHeaderFocus = GuideHeaderFocus.DATE
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
                        Text(windowStartSec.formatDateTime())
                    }
                    OutlinedButton(
                        onClick = {
                            val nowSec = nowSecProvider()
                            jumpToWindow(floorGuideWindowToHour(nowSec, guideZoneId))
                        },
                        modifier = Modifier
                            .focusRequester(guideNowFocus)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    releaseProgrammeFocus()
                                    lastHeaderFocus = GuideHeaderFocus.NOW
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
                        Text(stringResource(R.string.now))
                    }
                    OutlinedButton(
                        onClick = {
                            releaseProgrammeFocus()
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
                                if (it.isFocused) {
                                    releaseProgrammeFocus()
                                    lastHeaderFocus = GuideHeaderFocus.SEARCH
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
                                        releaseProgrammeFocus()
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
                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(bottom = 8.dp)) {
                ChannelTagSelector(
                    tags = channelScope.tags,
                    activeTagId = channelScope.activeTagId,
                    onSelectTag = {
                        releaseProgrammeFocus()
                        scopeMotion.select(it, buildList {
                            if (channelScope.allChannelsVisible) add(null)
                            addAll(channelScope.tags.map { tag -> tag.id })
                        })
                        channelViewModel.selectTag(it)
                    },
                    allChannelsVisible = channelScope.allChannelsVisible,
                    activeFocusRequester = scopeFocus,
                    onMoveToContent = { leaveGuideScope() },
                    modifier = Modifier
                        .padding(start = startPadding + TvBrowseLeadingInset)
                        .onFocusChanged {
                            if (it.hasFocus) {
                                profileTrace("P48:focus:guideScope") { }
                                releaseProgrammeFocus()
                            }
                        }
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
            }
            Spacer(Modifier.height(4.dp))
            TabContent(
                motion = scopeMotion,
                selectedKey = channelScope.activeTagId,
                state = {
                    GuideTabBody(
                        channelScopeState, observation, connectionUiState, timelineEventIndex,
                         orderedChannelIds, channelNumbers,
                         preparedIndex.input.windowStartSec, preparedIndex.input.windowEndSec,
                        nowSecProvider, { selectedTarget }, tagNotice, guideRecovering,
                        hasGuideRecoveryAction, needsGuideSettings, permissionDenied,
                        { channelId ->
                            coverageRequestVersion.let {
                                coverageRequests.isPending(channelId, preparedIndex.input.windowStartSec)
                            }
                        },
                        earlierContent, laterContent,
                    )
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
            val tagNotice = frame.tagNotice
            val guideRecovering = frame.recovering
            val hasGuideRecoveryAction = frame.hasRecovery
            val needsGuideSettings = frame.needsSettings
            val permissionDenied = frame.permissionDenied
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
                onDismiss = { if (owner.isCurrent) channelViewModel.dismissUnavailableTagNotice() },
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
                    focusRequester = if (owner.isCurrent) guideRetryFocus else remember { FocusRequester() },
                    onRetry = { if (owner.isCurrent) onRetry() },
                    onOpenConnectionSettings = { if (owner.isCurrent) onOpenConnectionSettings() },
                    modifier = Modifier.padding(start = startPadding, end = endPadding),
                )
                Spacer(Modifier.height(TvSpacing8))
            }

            if (channels.isEmpty()) {
                GuideEmptyState(
                    isEmptyTag = channelScope.activeTagId != null,
                    connectionUiState = connectionUiState,
                    channelCatalogCurrent = channelScopeState.channelCatalogCurrent,
                    onRetry = { if (owner.isCurrent) onRetry() },
                    onOpenConnectionSettings = { if (owner.isCurrent) onOpenConnectionSettings() },
                    retryFocusRequester = if (owner.isCurrent) guideRetryFocus else remember { FocusRequester() },
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
                )
                val trackLeftPx = if (layoutDirection == LayoutDirection.Ltr) {
                    with(density) { (startPadding + TvBrowseLeadingInset + GuideChannelWidth + GuideChannelGap).toPx() }
                } else terminalReservePx
                Box(Modifier.weight(1f).fillMaxWidth()
                    .guideViewportFades(trackLeftPx, trackWidthPx, frame.earlierContent, frame.laterContent,
                        above = { channelListState.canScrollBackward }, below = { channelListState.canScrollForward })
                    .guideNowLine(renderedWindowStartSec, windowEndSec, nowSecProvider, trackLeftPx, trackWidthPx,
                        MaterialTheme.colorScheme.primary)
                    .testTag("epg-timeline-content")) {
                CompositionLocalProvider(LocalBringIntoViewSpec provides guideBringIntoView) {
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
                            if (!initialFocusEnabled || !programmeFocusOwned) return@onPreviewKeyEvent false
                            if (preparedIndex.input.category != category && event.key in listOf(
                                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                                )) return@onPreviewKeyEvent true
                            val unsettled = !indexCurrent || realizedTarget != selectedTarget || frontierRequest != null ||
                                channelFocusRequest != null || windowFocusRequest != null
                            if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                                if (event.type == KeyEventType.KeyDown) {
                                    // A new press ends an interrupted sequence; an internal
                                    // focus handoff or a held-key repeat does not.
                                    if (event.nativeKeyEvent.repeatCount == 0) suppressActivationRelease = false
                                    if (unsettled) suppressActivationRelease = true
                                }
                                val consume = unsettled || suppressActivationRelease
                                if (event.type == KeyEventType.KeyUp) suppressActivationRelease = false
                                return@onPreviewKeyEvent consume
                            }
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionLeft -> moveFocus(EpgFocusDirection.LEFT)
                                Key.DirectionRight -> moveFocus(EpgFocusDirection.RIGHT)
                                Key.DirectionUp -> moveFocus(EpgFocusDirection.UP)
                                Key.DirectionDown -> moveFocus(EpgFocusDirection.DOWN)
                                else -> false
                            }
                        }
                        .focusable()
                        .testTag("epg-programme-viewport"),
                ) {
                    itemsIndexed(channels, key = { _, channel -> channel.id.value }) {
                            channelIndex, channel ->
                        val selected = rememberTabReader(frame.selected)()
                        val coveragePending = rememberTabReader { frame.coveragePending(channel.id) }()
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
                            imageLoader = imageLoader,
                            currentSession = currentSession,
                            events = eventsByChannel[channel.id].orEmpty(),
                            hasCachedEvents = channel.id in timelineEventIndex.channelsWithEvents,
                            hasMatchingCachedEvents =
                                channel.id in timelineEventIndex.channelsWithMatchingEvents,
                            connectionUiState = connectionUiState,
                            coveragePending = coveragePending,
                            onFocused = { event ->
                                if (!owner.isCurrent) return@TimelineChannelRow
                                if (!initialFocusEnabled || channelViewModel.scope.value != channelScopeState) return@TimelineChannelRow
                                profileTrace("P44:focus:guide") {
                                    if (BuildConfig.PROFILE_TRACE) {
                                        profileTrace("P48:focus:guide:${channel.id.value}:${event.id.value}") { }
                                    }
                                    val target = EpgFocusTarget(channelIndex, event.id)
                                    val settled = realizedTarget == selectedTarget
                                    realizedTarget = target
                                    if (settled || !programmeFocusOwned || selectedTarget == null) selectedTarget = target
                                    programmeFocusOwned = true
                                    if (target == selectedTarget) {
                                        lastFocusedEvent = event
                                        selection.setSelected(channel.id)
                                        guidePositionStore.save(GuidePosition(
                                            channelId = channel.id,
                                            eventId = event.id,
                                            eventStartSec = event.start.epochSeconds,
                                            windowStartSec = windowStartSec,
                                            firstVisibleColumn = channelListState.firstVisibleItemIndex,
                                        ))
                                    }
                                }
                            },
                            recordingForEvent = { eventId ->
                                profileTrace("P48:guideRecordingLookup") {
                                    observation.dvrEntryForEvent(eventId)
                                }
                            },
                            onOpenDetails = {
                                if (!owner.isCurrent) return@TimelineChannelRow
                                if (realizedTarget != selectedTarget || frontierRequest != null ||
                                    channelFocusRequest != null || windowFocusRequest != null
                                ) return@TimelineChannelRow
                                releaseProgrammeFocus()
                                selectedTarget = EpgFocusTarget(
                                    channelIndex,
                                    it.id,
                                )
                                detailsEvent = it
                                detailsOpening = Any()
                                detailsObservation = observation
                                detailsFromSearch = false
                            },
                            visibleRowWidthPx = LocalBrowseVisibleWidthPx.current?.minus(
                                with(LocalDensity.current) {
                                    timelineContentPadding.calculateStartPadding(layoutDirection).roundToPx()
                                },
                            ),
                            focusStartInset = horizontalFocusReserve + if (frame.earlierContent) GuideEdgeFade else 0.dp,
                            focusEndInset = horizontalFocusReserve + if (frame.laterContent) GuideEdgeFade else 0.dp,
                        )
                    }
                }
                }
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
                            releaseProgrammeFocus()
                            detailsEvent = event
                            detailsOpening = Any()
                            detailsObservation = resultObservation
                            detailsFromSearch = true
                            restoreSearchResultFocus = null
                        }
                    }
                },
                onDismiss = ::closeSearch,
            )
        }

        detailsEvent?.let { openingEvent ->
            val openingObservation = detailsObservation ?: return@let
            LaunchedEffect(openingObservation.currentSession, currentSession) {
                if (openingObservation.currentSession == null || openingObservation.currentSession !== currentSession) {
                    pendingAction = null
                    pendingMutation = null
                    pendingRecordingTarget = null
                    configChoices = null
                    closeDetails()
                    closeSearch()
                }
            }
            val selectedObservation = searchResultObservation(openingObservation, observation)
                ?: return@let
            val detailEvents = remember(selectedObservation.epgSnapshotForDisplay) {
                guideDisplayEvents(selectedObservation.epgSnapshotForDisplay)
            }
            val event = detailEvents
                ?.firstOrNull { it.id == openingEvent.id } ?: openingEvent
            val selectedCapability = selectedObservation.currentSession
                ?.takeIf { observation.currentSession === it }
            val liveEvent = selectedObservation.event(event.id)?.takeIf { it == event }
            val eventChannelId = event.channelId
            val channel = eventChannelId?.let(selectedObservation::channel)
            val recording = selectedObservation.dvrEntryForProgramme(event)
            if (pendingAction == null) ProgrammeDetailsPanel(
                imageLoader = imageLoader,
                currentSession = selectedObservation.currentSession,
                onPreviewKeyEvent = confirmationKeyHandler,
                event = event,
                channel = channel,
                recording = recording,
                nowSecProvider = nowSecProvider,
                canModifyRecordings = selectedCapability != null && (liveEvent != null || recording != null),
                liveProgrammeActions = liveEvent != null,
                notices = { AppShellNoticeHost(queue = notices) },
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
                            if (liveEvent != null && selectedCapability != null && channel != null) {
                                detailsEvent = null
                                detailsObservation = null
                                detailsOpening = null
                                detailsFromSearch = false
                                onPlay(
                                    LivePlaybackSelection(selectedCapability, channel.id),
                                    channel.name.orEmpty(),
                                )
                            }
                        }
                        ProgrammeAction.WATCH_FROM_START -> {
                            if (recording != null && selectedCapability != null) {
                                onPlayRecording(
                                    RecordingPlaybackSelection(
                                        selectedCapability,
                                        recording.id,
                                    )
                                )
                            }
                        }
                        ProgrammeAction.RECORD -> if (selectedCapability != null && liveEvent != null) {
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
                        ProgrammeAction.STOP_RECORDING -> if (
                            selectedCapability != null && recording != null
                        ) {
                            pendingMutation = DvrMutationAction.Stop(
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
            observation,
        )
        val recordingTargetCurrent = currentGuideRecordingTarget(pendingRecordingTarget, observation) != null
        LaunchedEffect(confirmationAction, confirmationMutation, configChoices, currentSession, recordingTargetCurrent) {
            if (
                confirmationAction != null && confirmationMutation == null ||
                configChoices != null && (!recordingTargetCurrent || currentSession == null ||
                    detailsObservation?.currentSession !== currentSession)
            ) {
                pendingAction = null
                pendingMutation = null
                pendingRecordingTarget = null
                configChoices = null
            }
        }
        if (
            confirmationAction != null &&
            confirmationEvent != null &&
            confirmationMutation != null
        ) {
            ConfirmProgrammeActionDialog(
                onPreviewKeyEvent = confirmationKeyHandler,
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
                        observationState.value,
                    )
                    pendingMutation = null
                    pendingRecordingTarget = null
                    if (mutation != null) {
                        val openingObservation = detailsObservation
                        coroutineScope.launch {
                            val currentMutation = currentDvrMutation(mutation, openingObservation, observationState.value)
                                ?: return@launch
                            val noticeContext = notices.context()
                            val feedback = dvrMutationActions.execute(currentMutation)
                            notices.postDvrFailure(currentMutation, feedback, noticeContext)
                        }
                    }
                },
            )
        }

        configChoices?.takeIf { recordingTargetCurrent }?.let { configs ->
            DvrConfigDialog(
                configs = configs,
                onDismiss = { configChoices = null },
                onSelect = { config ->
                    configChoices = null
                    pendingMutation = currentGuideRecordingTarget(pendingRecordingTarget, observationState.value)?.let { target ->
                        DvrMutationAction.CreateProgramme(target, config.id)
                    }
                    pendingAction = ProgrammeAction.RECORD.takeIf { pendingMutation != null }
                },
            )
        }

        if (showJumpDialog) {
            JumpToTimeDialog(
                initialSec = windowStartSec,
                bounds = windowBounds,
                zoneId = guideZoneId,
                nowSecProvider = nowSecProvider,
                onDismiss = { showJumpDialog = false },
                onJump = { target ->
                    showJumpDialog = false
                    jumpToWindow(target)
                },
            )
        }

    }
    }
}

private fun SessionObservation.dvrEntries(): List<DvrEntry> =
    dvrSnapshotForDisplay?.entries.orEmpty()

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
    currentObservation: SessionObservation,
): DvrMutationAction? = mutation?.takeIf {
    currentObservation.currentSession != null && sourceObservation?.currentSession === currentObservation.currentSession &&
        it.recordingStateIsCurrent(currentObservation) &&
        (it !is DvrMutationAction.CreateProgramme || currentGuideRecordingTarget(it.target, currentObservation) != null)
}

internal fun currentGuideRecordingTarget(target: ProgrammeRecordingTarget?, observation: SessionObservation): ProgrammeRecordingTarget? =
    target?.takeIf { selected ->
        observation.currentSession === selected.currentSession &&
            observation.event(selected.eventId)?.programmeRecordingTarget(selected.currentSession) == selected
    }

internal fun SessionObservation.dvrEntryForProgramme(event: EpgEventEntry): DvrEntry? =
    dvrSnapshotForDisplay?.entries?.singleOrNull { entry ->
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

internal fun Long.formatDateTime(): String = Instant.ofEpochSecond(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm"))
