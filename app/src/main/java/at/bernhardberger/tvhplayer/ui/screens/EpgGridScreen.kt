package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.foundation.layout.width

import at.bernhardberger.tvhplayer.notices.NoticeCenter

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

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.tv.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.AppError
import at.bernhardberger.tvhplayer.core.browseAppError
import at.bernhardberger.tvhplayer.core.awaitsGuide
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
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import at.bernhardberger.tvhplayer.core.ProgrammeRecordingTarget
import at.bernhardberger.tvhplayer.core.browsingFocusChannelId
import at.bernhardberger.tvhplayer.core.currentEpgSnapshot
import at.bernhardberger.tvhplayer.core.firstUnsettledGuidePageIndex
import at.bernhardberger.tvhplayer.core.floorGuideWindowToHour
import at.bernhardberger.tvhplayer.core.guideChannelPageCoverageSettled
import at.bernhardberger.tvhplayer.core.guideEntryChannelId
import at.bernhardberger.tvhplayer.core.guideEntryFocusTarget
import at.bernhardberger.tvhplayer.core.guideNowWindowStart
import at.bernhardberger.tvhplayer.core.guideScopeExitFocusTarget
import at.bernhardberger.tvhplayer.core.guideWindowBounds
import at.bernhardberger.tvhplayer.core.guideDisplayEvents
import at.bernhardberger.tvhplayer.core.indexTimelineEventsByChannel
import at.bernhardberger.tvhplayer.core.initialTimelineEpgFocus
import at.bernhardberger.tvhplayer.core.programmeCategory
import at.bernhardberger.tvhplayer.core.moveTimelineEpgFocus
import at.bernhardberger.tvhplayer.core.programmeRecordingTarget
import at.bernhardberger.tvhplayer.core.reconcileTimelineEpgFocus
import at.bernhardberger.tvhplayer.core.resolveGuideFrontierOrigin
import at.bernhardberger.tvhplayer.core.resolveGuideWindowFocus
import at.bernhardberger.tvhplayer.core.shouldWaitForGuideCoverage
import at.bernhardberger.tvhplayer.core.timelineFrontierFocus
import at.bernhardberger.tvhplayer.core.timelinePageFocusTarget
import at.bernhardberger.tvhplayer.core.timelineEventIsReadable
import at.bernhardberger.tvhplayer.core.timelineFocusAnchorRange
import at.bernhardberger.tvhplayer.core.timelineProgrammeAcrossGap
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.stores.GuidePosition
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.ui.common.clockWidthSamples
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabel
import at.bernhardberger.tvhplayer.ui.tvGenreColors
import at.bernhardberger.tvhplayer.core.TimelineEpgEventIndex
import at.bernhardberger.tvhplayer.ui.components.rememberTabContentMotion
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideChannelWidth
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideHeader
import at.bernhardberger.tvhplayer.ui.screens.guide.guideHeaderMinHeight
import at.bernhardberger.tvhplayer.ui.screens.guide.guideHeaderRulerClearance
import at.bernhardberger.tvhplayer.ui.screens.guide.guideHeadlineDrop
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideHeadline
import at.bernhardberger.tvhplayer.ui.screens.guide.GuidePreparationSkeleton
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideHeadlineBackground
import at.bernhardberger.tvhplayer.ui.screens.guide.guideHeadlineProgramme
import at.bernhardberger.tvhplayer.ui.screens.guide.rememberSettledGuideHeadline
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideOverlayHost
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideTabBody
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideTimelineBody
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideTimePanel
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideViewOptionsPanel
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideFilterPanel
import at.bernhardberger.tvhplayer.settings.GuideView
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.TvBrowseLeadingInset
import at.bernhardberger.tvhplayer.ui.TvBrowseTrailingInset
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideChannelGap
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideEdgeFade
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideFocusReserve
import at.bernhardberger.tvhplayer.ui.screens.guide.guideFocusWindowStart
import at.bernhardberger.tvhplayer.ui.screens.guide.guideReadableTimeRange
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideMinReadableOverlap
import at.bernhardberger.tvhplayer.ui.screens.guide.guideMinReadableOverlapSec
import at.bernhardberger.tvhplayer.ui.screens.guide.guideFocusInsets
import at.bernhardberger.tvhplayer.ui.screens.guide.guideTerminalViewportReserve
import at.bernhardberger.tvhplayer.ui.screens.guide.guideVisibleWindowSec
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant as KotlinInstant
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import at.bernhardberger.tvhplayer.ui.components.InsetBringIntoViewSpec

private const val CHANNEL_PAGE_SIZE = 6
private const val GUIDE_COVERAGE_NAVIGATION_TIMEOUT_MS = 10_000L
// Hidden for 1.0, owner decision 2026-10-12.
private const val GuideSearchEntryVisible = false

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
    val anchor: GuideFocusAnchor,
)

/** A null span follows now; a visible span stays fixed until another focus change drops it. */
private data class GuideFocusAnchor(
    val target: EpgFocusTarget,
    val sec: Long,
    val keepWindow: Boolean,
    val spanSec: LongRange? = null,
)

private data class WindowFocusRequest(
    val preferredChannelId: ChannelId,
    val windowStartSec: Long,
    val coverageRequest: GuideCoverageRequestToken,
    val throughSec: Long,
    val programmeId: EventId? = null,
    val programmeStartSec: Long? = null,
    /** Now and Back-to-now: the programme on air is anchored at now, as on entry. */
    val atNow: Boolean = false,
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
    val windowStartSec: Long,
    val windowEndSec: Long,
)

private data class GuideIndexPresentation(
    val index: TimelineEpgEventIndex,
    val eventIds: Set<EventId>,
)

// Bookkeeping only: this is never rendered or accepted as completed positioning.
private val emptyGuideIndex = TimelineEpgEventIndex(emptyMap(), emptySet())

internal fun guideTimelineContentPadding(
    contentPadding: PaddingValues,
    layoutDirection: LayoutDirection,
    terminalReserve: Dp = 0.dp,
): PaddingValues = PaddingValues(
    start = contentPadding.calculateStartPadding(layoutDirection) + TvBrowseLeadingInset,
    top = 8.dp,
    end = terminalReserve,
    bottom = maxOf(contentPadding.calculateBottomPadding(), GuideFocusReserve),
)

@OptIn(ExperimentalFoundationApi::class, at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
@Composable
fun EpgGridScreen(
    contentPadding: PaddingValues = PaddingValues(),
    initialFocusEnabled: Boolean = true,
    guideActive: Boolean = true,
    view: GuideView = GuideView(),
    genre: ProgrammeCategory = ProgrammeCategory.ALL,
    channelViewModel: ChannelsViewModel,
    selection: ChannelSelectionStore = koinInject(),
    session: TvheadendSession = koinInject(),
    playerSession: AppPlaybackRuntime = koinInject(),
    lastPlayedStore: LastPlayedChannelStore = koinInject(),
    guidePositionStore: GuidePositionStore = koinInject(),
    connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
    connectionFailure: ConnectionUiState? = connectionUiState,
    playerReturn: PlayerReturnFocus? = null,
    browseReturn: Boolean = false,
    onRetry: () -> Unit = {},
    onOpenConnectionSettings: () -> Unit = {},
    onGenreChange: (ProgrammeCategory) -> Unit = {},
    onPlayRecording: (RecordingPlaybackSelection) -> Unit = {},
    notices: NoticeCenter = koinInject(),
    onPlay: (channelId: ChannelId, channelName: String) -> Unit,
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
    // With the headline shown, the headline and grid sit lower so the bottom row shows it is cut.
    // Computed where it is used: a value of this body costs registers in its one generated method.
    @Composable
    fun headlineDrop(): Dp = guideHeadlineDrop(
        viewportHeight = maxHeight,
        contentTop = contentPadding.calculateTopPadding(),
        gridTopPadding = guideTimelineContentPadding(contentPadding, layoutDirection).calculateTopPadding(),
        rows = view.rows,
    )
    val timeLabelMeasurer = rememberTextMeasurer()
    val timeLabelWidth = clockWidthSamples(uses24Hour = AppClock.uses24Hour, digit = '0').maxOf {
        timeLabelMeasurer.measure(it, style = MaterialTheme.typography.labelMedium).size.width
    }
    // Capacity already budgets the outer safe area. Do not subtract terminal padding again.
    val visibleWindowSec = guideVisibleWindowSec(timelineWidthPx, timeLabelWidth, density.density)
    val platformBringIntoView = LocalBringIntoViewSpec.current
    // Only the top edge fades; the bottom cuts, so it needs just the focus reserve.
    val topFocusInset = with(density) { (GuideEdgeFade + GuideFocusReserve).toPx() }
    val bottomFocusInset = with(density) { GuideFocusReserve.toPx() }
    val guideBringIntoView = remember(platformBringIntoView, topFocusInset, bottomFocusInset) {
        InsetBringIntoViewSpec(platformBringIntoView, topFocusInset, bottomFocusInset)
    }
    val coroutineScope = rememberCoroutineScope()
    val detailsMutationInFlight = remember { mutableStateOf<DvrMutationAction?>(null) }
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
    val guideRetryFocus = remember { FocusRequester() }
    val groups = remember(channelScope) { channelsGroups(channelScope) }
    val header = remember { ChannelsHeaderFocus() }
    var openPanel by remember { mutableStateOf<ChannelsPanel?>(null) }
    val guideAlert = guideAppError(
        hasChannels = channelScope.visibleChannels.isNotEmpty(),
        isEmptyTag = channelScope.activeTagId != null,
        connectionUiState = connectionUiState,
        channelCatalogCurrent = channelScopeState.channelCatalogCurrent,
        connectionFailure = connectionFailure,
    )
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
    val requestedProgramme = pendingPlayerReturn?.programmeId?.let(observation::event)?.takeIf {
        it.channelId == playerReturnChannelId && it.start.epochSeconds == pendingPlayerReturn.programmeStartSec &&
            it.start.epochSeconds in windowBounds.earliestStartSec..windowBounds.latestStartSec
    }
    val rememberedChannelId = remember { guidePositionStore.position.value?.channelId }
    // Recording-player Back restores the exact spot; live-player return can reposition it below.
    val restoredPosition = remember { guidePositionStore.position.value.takeIf { playerReturn != null || browseReturn } }
    // Only the positioning effect consumes this; later group changes use the live Guide memory.
    var pendingRestoredPosition by remember { mutableStateOf(restoredPosition) }
    val currentBrowseReturn by rememberUpdatedState(browseReturn)
    val restoredTimelinePosition = remember(restoredPosition, windowBounds) {
        restoredPosition?.takeIf { it.windowStartSec in
            windowBounds.earliestStartSec..windowBounds.latestStartSec
        }
    }
    // Start with the available restored/browse viewport instead of constructing page zero
    // and discarding it in the initial-position effect. That effect still resolves the
    // asynchronous last-played identity and current coverage using its existing policy.
    val initialChannelId = playerReturnChannelId ?: guideEntryChannelId(
        orderedChannelIds, playingChannelId, rememberedChannelId, null, selectedChannelId,
    )
    val initialChannelIndex = channels.indexOfFirst { it.id == initialChannelId }.coerceAtLeast(0)
    val initialViewportIndex = restoredTimelinePosition
        ?.takeIf { position -> playerReturnChannelId == null && channels.any { it.id == position.channelId } }
        ?.firstVisibleColumn?.coerceIn(0, channels.lastIndex.coerceAtLeast(0))
        ?: (initialChannelIndex / CHANNEL_PAGE_SIZE) * CHANNEL_PAGE_SIZE
    val channelListState = rememberLazyListState(initialFirstVisibleItemIndex = initialViewportIndex)
    var windowStartSec by remember {
        mutableLongStateOf(
            restoredTimelinePosition?.windowStartSec?.takeIf { playerReturnChannelId == null }
                ?: guideNowWindowStart(openedAtSec, windowBounds, guideZoneId)
        )
    }
    val windowEndSec = windowStartSec + visibleWindowSec
    var selectedTarget by remember { mutableStateOf<EpgFocusTarget?>(null) }
    var focusAnchor by remember { mutableStateOf<GuideFocusAnchor?>(null) }
    val navigationFocus = LocalBrowseNavigationFocus.current
    var realizedTarget by remember { mutableStateOf<EpgFocusTarget?>(null) }
    var lastFocusedEvent by remember { mutableStateOf<EpgEventEntry?>(null) }
    val gridFocus = remember { FocusRequester() }
    var scopeEntryRequested by remember { mutableStateOf(false) }
    var suppressActivationRelease by remember { mutableStateOf(false) }
    var consumeBackRelease by remember { mutableStateOf(false) }
    var railOnBackRelease by remember { mutableStateOf(false) }
    var pendingInitialChannelIndex by remember { mutableIntStateOf(-1) }
    var initialPositionDone by remember { mutableStateOf(false) }
    var destinationEntryPending by remember { mutableStateOf(playerReturn == null && !browseReturn) }
    var detailsEvent by remember { mutableStateOf<EpgEventEntry?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var detailsObservation by remember { mutableStateOf<SessionObservation?>(null) }
    var restoreDetailsFocus by remember { mutableStateOf(false) }
    var detailsFromSearch by remember { mutableStateOf(false) }
    var showTimePanel by remember { mutableStateOf(false) }
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
    var lastPlayedId by remember { mutableStateOf<ChannelId?>(null) }
    var programmeFocusOwned by remember { mutableStateOf(true) }
    var preparationEntryToScope by remember { mutableStateOf(false) }
    val mayFocusProgramme by rememberUpdatedState(
        initialFocusEnabled && programmeFocusOwned &&
            detailsEvent == null && !showSearchDialog && !showTimePanel && openPanel == null &&
            channelFocusRequest == null
    )

    val displayEvents = remember(observation.epgSnapshotForDisplay) { guideDisplayEvents(observation.epgSnapshotForDisplay) }
    // Filter offers itself once the guide carries a genre code, and stays while one is chosen.
    val genreCodes = remember(displayEvents) { displayEvents.any { programmeCategory(it) != null } }
    val showGenre = genreCodes || genre != ProgrammeCategory.ALL
    val indexInput = GuideIndexInput(
        observation.epgSnapshotForDisplay, displayEvents, observation.epgState, windowStartSec, windowEndSec,
    )
    val preparationAuthority = currentSession ?: observation.epgSnapshotForDisplay
    val indexAuthority = remember(preparationAuthority, windowStartSec) { Any() }
    val preparedIndex = rememberPreparedBrowseData(preparationAuthority, indexInput, requestKey = indexAuthority) { input ->
        val preparationContext = currentCoroutineContext()
        val index = profileTrace("P44:guideIndex") {
            indexTimelineEventsByChannel(
                events = input.events,
                windowStartSec = input.windowStartSec,
                windowEndSec = input.windowEndSec,
                ensureActive = { preparationContext.ensureActive() },
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
        guideDateFocus.requestFocus()
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
            hasRetryAction = guideAlert != null,
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
                hasRetryAction = guideAlert != null,
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
            runCatching { guideDateFocus.requestFocus() }
            restoreSearchHeaderFocus = false
        }
    }

    val epgState = preparedIndex?.input?.state ?: observation.epgState
    val timelineEventIndex = preparedIndex?.value?.index ?: emptyGuideIndex
    // From the same snapshot as the rows' programmes, so a row is never proven empty before they arrive.
    val coverageByChannel = remember(epgState) {
        epgState.currentEpgSnapshot()?.coverages?.associateBy { it.channelId }.orEmpty()
    }
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
            input.events.filter { it.channelId in ids }
        }.orEmpty()
    }
    val earlierContent = edgeEvents.any {
        it.start.epochSeconds < windowStartSec && it.stop.epochSeconds > windowBounds.earliestStartSec
    }
    val laterContent = edgeEvents.any {
        it.stop.epochSeconds > windowEndSec && it.start.epochSeconds < windowBounds.latestStartSec + visibleWindowSec
    }
    val (focusStartInset, focusEndInset) = guideFocusInsets(earlierContent, laterContent, layoutDirection == LayoutDirection.Rtl)
    val terminalReserve = guideTerminalViewportReserve(earlierContent, laterContent, layoutDirection == LayoutDirection.Rtl)
    val focusTrackWidthPx = timelineWidthPx - with(density) { terminalReserve.roundToPx() }
    val readableTimeRange = guideReadableTimeRange(
        windowStartSec, visibleWindowSec, focusTrackWidthPx,
        with(density) { focusStartInset.toPx() }, with(density) { focusEndInset.toPx() },
    )
    val minReadableOverlapSec = guideMinReadableOverlapSec(
        visibleWindowSec, focusTrackWidthPx, with(density) { GuideMinReadableOverlap.toPx() },
    )
    fun keepAnchoredWindowFor(event: EpgEventEntry): Boolean =
        focusAnchor?.let { it.keepWindow && it.target == selectedTarget && it.target.eventId == event.id } == true &&
            timelineEventIsReadable(event, readableTimeRange, minReadableOverlapSec)
    fun focusWindowFor(event: EpgEventEntry): Long = if (keepAnchoredWindowFor(event)) windowStartSec else guideFocusWindowStart(
        event.start.epochSeconds, event.stop.epochSeconds, windowStartSec, visibleWindowSec,
        focusTrackWidthPx, with(density) { focusStartInset.toPx() }, with(density) { focusEndInset.toPx() },
        windowBounds.earliestStartSec, windowBounds.latestStartSec, nowSecProvider(),
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

    fun programmeAcrossGap(channelId: ChannelId, originEventId: EventId, boundarySec: Long, direction: Int) =
        timelineProgrammeAcrossGap(
            events = displayEvents.filter { it.channelId == channelId },
            originEventId = originEventId,
            boundarySec = boundarySec,
            direction = direction,
            rangeStartSec = windowBounds.earliestStartSec,
            rangeEndSec = windowBounds.latestStartSec + visibleWindowSec,
        )

    // Keeps Left's usual placement: the earlier programme ends at the window's right edge.
    fun windowStartShowingEarlier(earlier: EpgEventEntry, targetWindowStartSec: Long): Long =
        windowBounds.constrain(minOf(targetWindowStartSec, earlier.stop.epochSeconds - visibleWindowSec))

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

    LaunchedEffect(initialFocusEnabled) {
        if (!initialFocusEnabled) {
            consumeBackRelease = false
            railOnBackRelease = false
            scopeEntryRequested = false
            cancelPendingProgrammeNavigation()
        }
    }

    // The sidebar scene can retain Guide while Channels is selected. Rail focus alone is not entry.
    LaunchedEffect(guideActive) {
        if (!guideActive) {
            clearAllCoverage()
            initialPositionDone = false
            destinationEntryPending = true
            pendingRestoredPosition = null
        } else if (currentBrowseReturn) {
            // Back can reverse the shell transition before this composition is disposed.
            destinationEntryPending = false
            pendingRestoredPosition = guidePositionStore.position.value
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

    /** New vertical sequences follow now on air, otherwise the focused programme's visible span. */
    fun anchorFor(
        current: EpgFocusTarget,
        previous: GuideFocusAnchor? = focusAnchor?.takeIf { it.target == current },
    ): GuideFocusAnchor {
        previous?.takeIf { it.spanSec != null }?.let {
            return it.copy(target = current, sec = it.sec.coerceIn(readableTimeRange), keepWindow = true)
        }
        val nowSec = nowSecProvider()
        // A gap fallback must not turn an ongoing now sequence into a future span.
        if (previous != null && nowSec in readableTimeRange) {
            return previous.copy(target = current, sec = nowSec, keepWindow = true)
        }
        val span = focusRows.getOrNull(current.channelIndex)?.events?.firstOrNull { it.id == current.eventId }
            ?.let { timelineFocusAnchorRange(it, readableTimeRange, nowSec) }
            ?: (readableTimeRange.first..readableTimeRange.first)
        return GuideFocusAnchor(current, span.first, keepWindow = true,
            spanSec = span.takeUnless { it == nowSec..nowSec })
    }

    fun selectAnchored(target: EpgFocusTarget, anchor: GuideFocusAnchor) {
        selectedTarget = target
        focusAnchor = anchor.copy(target = target)
    }

    /** Left, Right and time jumps start a new anchor, even when they land on the anchored programme again. */
    fun selectUnanchored(target: EpgFocusTarget?) {
        selectedTarget = target
        focusAnchor = null
    }

    /** Entry and Now mark a now-point; the next vertical move rechecks now and its readability. */
    fun selectAtNow(target: EpgFocusTarget?) {
        val nowSec = nowSecProvider()
        val event = target?.let { focusRows.getOrNull(it.channelIndex)?.events?.firstOrNull { e -> e.id == it.eventId } }
        if (target != null && event != null && nowSec in windowStartSec until windowEndSec &&
            nowSec in event.start.epochSeconds until event.stop.epochSeconds
        ) selectAnchored(target, GuideFocusAnchor(target, nowSec.coerceIn(readableTimeRange), keepWindow = false))
        else selectUnanchored(target)
    }

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
            anchor = anchorFor(current),
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
        guideActive,
        playingChannelId,
        lastPlayedId,
        initialPositionDone,
        indexCurrent,
        pendingPlayerReturn,
    ) {
        if (!guideActive || !indexCurrent || initialPositionDone || channels.isEmpty() || focusRows.size != channels.size) {
            return@LaunchedEffect
        }
        val restoredChannelPosition = pendingRestoredPosition?.takeIf { position ->
            !destinationEntryPending && playerReturnChannelId == null && channels.any { it.id == position.channelId }
        }
        val restored = restoredChannelPosition?.takeIf {
            it.windowStartSec in windowBounds.earliestStartSec..windowBounds.latestStartSec
        }
        if (restored != null && windowStartSec != restored.windowStartSec) {
            selectedTarget = null
            windowStartSec = restored.windowStartSec
            return@LaunchedEffect
        }
        if (playerReturnChannelId != null || destinationEntryPending && pendingPlayerReturn == null) {
            val nowSec = nowSecProvider()
            // A programme played from its start shows from its hour; one on air, as live, opens just before now.
            val nowWindow = requestedProgramme?.takeUnless { nowSec in it.start.epochSeconds until it.stop.epochSeconds }
                ?.let { windowBounds.constrain(floorGuideWindowToHour(it.start.epochSeconds, guideZoneId)) }
                ?: guideNowWindowStart(nowSec, windowBounds, guideZoneId)
            // Re-enters once the index for the current-hour window is prepared.
            if (windowStartSec != nowWindow) {
                selectedTarget = null
                windowStartSec = nowWindow
                return@LaunchedEffect
            }
        }
        val preferredId = playerReturnChannelId ?: guideEntryChannelId(
            orderedChannelIds, playingChannelId, guidePositionStore.position.value?.channelId,
            lastPlayedId, selectedChannelId,
        )
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
            preferredEventId = requestedProgramme?.id ?: restored?.eventId,
            targetSec = requestedProgramme?.start?.epochSeconds ?: restored?.eventStartSec ?: nowSecProvider(),
        )

        val waitForEntryProgramme = destinationEntryPending && pendingPlayerReturn == null &&
            focusRows[channelIndex].events.none { nowSecProvider() in it.start.epochSeconds until it.stop.epochSeconds } &&
            !guideChannelPageCoverageSettled(
                channelIds = channelPageIds(channelIndex),
                coverages = preparedIndex?.input?.snapshot?.coverages.orEmpty(),
                requestedThrough = KotlinInstant.fromEpochSeconds(windowEndSec),
            )
        val coverageChannelIndex = if (waitForEntryProgramme) channelIndex else target?.channelIndex ?: channelIndex
        val coverageRequest = requestVisibleWindow(windowStartSec, coverageChannelIndex)
        val targetChannelIndex = if (waitForEntryProgramme && coverageRequest != null) channelIndex
            else target?.channelIndex ?: channelIndex
        pendingInitialChannelIndex = targetChannelIndex
        if (destinationEntryPending && pendingPlayerReturn == null &&
            (target == null || waitForEntryProgramme) && coverageRequest != null
        ) {
            selectedTarget = null
            windowFocusRequest = WindowFocusRequest(
                preferredChannelId = channelId,
                windowStartSec = windowStartSec,
                coverageRequest = coverageRequest,
                throughSec = windowEndSec,
                programmeStartSec = nowSecProvider(),
                atNow = true,
            )
        }
        channelListState.scrollToItem(
            restored?.firstVisibleColumn?.coerceIn(channels.indices)
                ?: (targetChannelIndex / CHANNEL_PAGE_SIZE) * CHANNEL_PAGE_SIZE
        )
        if (target != null && (!waitForEntryProgramme || coverageRequest == null)) {
            selectAtNow(target)
        }
        // A return that waited for rows reclaims entry from the empty-state fallback.
        if (playerReturnChannelId != null) programmeFocusOwned = true
        positionedPlayerReturn = pendingPlayerReturn
        pendingRestoredPosition = null
        destinationEntryPending = false
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
            // A first focus, as rows arrive after entry, takes the programme on air while the window shows now.
            val nowSec = nowSecProvider()
            reconcileTimelineEpgFocus(
                rows = focusRows,
                current = current,
                preferredChannelIndex = preferredIndex,
                targetSec = if (current == null && nowSec in windowStartSec until windowEndSec) nowSec else windowStartSec,
            )
        }
        if (replacement == current) return@LaunchedEffect
        if (current == null) selectAtNow(replacement) else selectUnanchored(replacement)
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
                selectUnanchored(resolution.target)
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

    // A window kept for the programme on air trims its cell; when the clock ends that programme the
    // cell grows back under the fade, so positioning runs again. Changes only then, not every tick.
    val selectedOnAir by remember(focusRows, nowSecProvider) {
        derivedStateOf {
            val target = selectedTarget
            val now = nowSecProvider()
            target != null && focusRows.getOrNull(target.channelIndex)?.events
                ?.firstOrNull { it.id == target.eventId }
                ?.let { now in it.start.epochSeconds until it.stop.epochSeconds } == true
        }
    }

    LaunchedEffect(
        selectedTarget,
        selectedOnAir,
        windowStartSec,
        visibleWindowSec,
        focusTrackWidthPx,
        minReadableOverlapSec,
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
                    it.start.epochSeconds < request.throughSec
            }
        }
        // Later EPG beyond the acquired window stays unknown, so only Left crosses a gap here.
        val earlier = if (request.direction < 0) withContext(Dispatchers.Default) {
            programmeAcrossGap(request.channelId, request.originEventId, request.boundarySec, -1)
        } else null
        if (frontierRequest != request) return@LaunchedEffect
        var targetWindowStartSec = request.throughSec - visibleWindowSec
        val target = snapshot?.let {
            timelineFrontierFocus(
                rows = listOf(EpgFocusColumn(request.channelId, frontierEvents)),
                channelId = request.channelId,
                originEventId = request.originEventId,
                boundarySec = request.boundarySec,
                direction = request.direction,
            )?.copy(channelIndex = channelIndex) ?: earlier?.let { event ->
                targetWindowStartSec = windowStartShowingEarlier(event, targetWindowStartSec)
                EpgFocusTarget(channelIndex, event.id)
            }
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
            windowStartSec = targetWindowStartSec
            selectUnanchored(target)
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
        val origin = EpgFocusTarget(originChannelIndex, request.originEventId)
        val anchor = anchorFor(origin, request.anchor)
        val target = snapshot?.let {
            timelinePageFocusTarget(
                rows = focusRows,
                current = origin,
                preferredChannelIndex = preferredChannelIndex,
                direction = request.direction,
                searchChannelIds = request.coverageRequest.generations.keys,
                anchorSec = anchor.sec,
                anchorSpanSec = anchor.spanSec,
                readableRangeSec = readableTimeRange,
                minReadableOverlapSec = minReadableOverlapSec,
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
            selectAnchored(target, anchor)
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
        preparedIndex,
    ) {
        if (!indexCurrent) return@LaunchedEffect
        val request = windowFocusRequest ?: return@LaunchedEffect
        // Coverage and rows must describe the same preparation, even if newer metadata is queued.
        val snapshot = preparedIndex?.input?.snapshot
        if (projectionBehind && !guideChannelPageCoverageSettled(
                channelIds = request.coverageRequest.generations.keys.toList(),
                coverages = snapshot?.coverages.orEmpty(),
                requestedThrough = KotlinInstant.fromEpochSeconds(request.throughSec),
            )) return@LaunchedEffect
        when (
            val resolution = resolveGuideWindowFocus(
                rows = focusRows,
                preferredChannelId = request.preferredChannelId,
                targetSec = request.programmeStartSec ?: request.windowStartSec,
                requestedChannelIds = request.coverageRequest.generations.keys,
                coverages = snapshot?.coverages.orEmpty(),
                requestedThrough = KotlinInstant.fromEpochSeconds(request.throughSec),
                connectionReady = connectionUiState == ConnectionUiState.Ready,
                hasCurrentSnapshot = preparedIndex?.input?.state?.currentEpgSnapshot() != null,
                // Entry can start the request in an earlier effect of this same composition.
                acquisitionPending = coveragePending(request.coverageRequest),
            )
        ) {
            GuideCoverageFocusResolution.Wait -> return@LaunchedEffect
            GuideCoverageFocusResolution.Release -> Unit
            is GuideCoverageFocusResolution.Select -> {
                pendingInitialChannelIndex = resolution.target.channelIndex
                val target = request.programmeId?.let { id ->
                    initialTimelineEpgFocus(focusRows, resolution.target.channelIndex,
                        request.programmeStartSec ?: request.windowStartSec, id)
                } ?: resolution.target
                if (request.atNow) selectAtNow(target) else selectUnanchored(target)
                channelListState.scrollToItem((resolution.target.channelIndex / CHANNEL_PAGE_SIZE) * CHANNEL_PAGE_SIZE)
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
        if (!indexCurrent) return
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
        val anchor = anchorFor(current)
        val target = timelinePageFocusTarget(
            rows = focusRows,
            current = current,
            preferredChannelIndex = targetIndex,
            direction = direction,
            searchChannelIds = targetPageIds,
            anchorSec = anchor.sec,
            anchorSpanSec = anchor.spanSec,
            readableRangeSec = readableTimeRange,
            minReadableOverlapSec = minReadableOverlapSec,
        )
        if (target == null) return
        selectAnchored(target, anchor)
    }

    // focusSec stands in for the programme's start when it is not loaded yet, so the focus lands on
    // the programme airing then rather than the window's first.
    fun jumpToWindow(
        targetSec: Long,
        channelId: ChannelId? = null,
        programme: EpgEventEntry? = null,
        focusSec: Long? = null,
        atNow: Boolean = false,
    ) {
        val channelIndex = channelId?.let { id -> channels.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            ?: selectedTarget?.channelIndex
            ?: pendingInitialChannelIndex.takeIf { it >= 0 }
            ?: 0
        val preferredChannelId = channels.getOrNull(channelIndex)?.id ?: return
        val boundedTargetSec = windowBounds.constrain(targetSec)
        clearAllCoverage()
        programmeFocusOwned = true
        pendingInitialChannelIndex = channelIndex
        selectUnanchored(null)
        windowStartSec = boundedTargetSec
        val coverageRequest = requestVisibleWindow(boundedTargetSec, channelIndex) ?: return
        windowFocusRequest = WindowFocusRequest(
            preferredChannelId = preferredChannelId,
            windowStartSec = boundedTargetSec,
            coverageRequest = coverageRequest,
            throughSec = boundedTargetSec + visibleWindowSec,
            programmeId = programme?.id,
            programmeStartSec = programme?.start?.epochSeconds ?: focusSec,
            atNow = atNow,
        )
    }

    /** Now in the day and time panel and Back-to-now: the window opens just before now, on the programme on air there. */
    fun jumpToNow(channelId: ChannelId?) {
        val nowSec = nowSecProvider()
        val onNow = channelId?.let { id ->
            displayEvents.firstOrNull {
                it.channelId == id && it.start.epochSeconds <= nowSec && it.stop.epochSeconds > nowSec
            }
        }
        jumpToWindow(guideNowWindowStart(nowSec, windowBounds, guideZoneId), channelId, onNow,
            focusSec = nowSec, atNow = true)
    }

    /**
     * D32: Left with nothing earlier on the row leaves the grid for the navigation rail. Keys
     * are not mirrored, so in RTL, where the rail sits on the right, the edge stays silent.
     */
    fun leaveTowardRail(direction: EpgFocusDirection): Boolean {
        if (direction == EpgFocusDirection.LEFT && layoutDirection == LayoutDirection.Ltr) {
            navigationFocus?.let { runCatching { it.requestFocus() } }
        }
        return true
    }

    /**
     * Back on a programme in a Guide moved away from now: returns to now on the same channel, on
     * the programme airing there. False when now is already within the visible window.
     */
    fun backToNow(): Boolean {
        val nowSec = nowSecProvider()
        val channelId = selectedTarget?.channelIndex?.let(channels::getOrNull)?.id
        if (nowSec in windowStartSec until windowEndSec || channelId == null) return false
        jumpToNow(channelId)
        return true
    }

    /** Back on a programme in a Guide at now: leaves for the navigation rail. */
    fun backToRail() {
        releaseProgrammeFocus()
        navigationFocus?.let { runCatching { it.requestFocus() } } ?: focusGuideHeader()
    }

    val programmeBackEnabled = indexCurrent && initialFocusEnabled && programmeFocusOwned &&
        detailsEvent == null && !showSearchDialog && !showTimePanel && openPanel == null
    BackHandler(enabled = programmeBackEnabled) { if (!backToNow()) backToRail() }

    fun moveFocus(direction: EpgFocusDirection): Boolean {
        if (!indexCurrent) return true
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
        val anchor = anchorFor(current)
        val move = moveTimelineEpgFocus(
            rows = focusRows,
            current = current,
            direction = direction,
            visibleChannelRange = visibleRange,
            anchorSec = anchor.sec,
            anchorSpanSec = anchor.spanSec,
            readableRangeSec = readableTimeRange,
            minReadableOverlapSec = minReadableOverlapSec,
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
                focusGuideHeader()
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
                var targetWindowStartSec = windowBounds.constrain(
                    if (move.timeFrontierDirection > 0) {
                        maxOf(windowStartSec + visibleWindowSec, boundarySec)
                    } else {
                        minOf(windowStartSec - visibleWindowSec, boundarySec - visibleWindowSec)
                    }
                )
                if (targetWindowStartSec == windowStartSec) return leaveTowardRail(direction)
                val originWindowStartSec = windowStartSec
                if (targetWindowStartSec + visibleWindowSec <= nowSecProvider()) {
                    val channelId = channels[current.channelIndex].id
                    val pastEvents = displayEvents.filter { it.channelId == channelId &&
                        it.stop.epochSeconds > targetWindowStartSec &&
                        it.start.epochSeconds < targetWindowStartSec + visibleWindowSec }
                    val target = timelineFrontierFocus(
                        rows = listOf(EpgFocusColumn(channelId, pastEvents)), channelId = channelId,
                        originEventId = current.eventId, boundarySec = boundarySec,
                        direction = move.timeFrontierDirection,
                    )
                    if (target != null) {
                        windowStartSec = targetWindowStartSec
                        selectUnanchored(target.copy(channelIndex = current.channelIndex))
                        return true
                    }
                    // An EPG gap can span the whole adjacent window. Continue to the row's
                    // nearest programme beyond it instead of stopping on this one.
                    val across = programmeAcrossGap(channelId, current.eventId, boundarySec, move.timeFrontierDirection)
                        ?: return leaveTowardRail(direction)
                    targetWindowStartSec = if (move.timeFrontierDirection < 0) {
                        windowStartShowingEarlier(across, targetWindowStartSec)
                    } else {
                        // Unacquired EPG after now may hold a nearer programme, so acquire from now at the latest.
                        windowBounds.constrain(maxOf(targetWindowStartSec, minOf(across.start.epochSeconds, nowSecProvider())))
                    }
                    if (targetWindowStartSec + visibleWindowSec <= nowSecProvider()) {
                        windowStartSec = targetWindowStartSec
                        selectUnanchored(EpgFocusTarget(current.channelIndex, across.id))
                        return true
                    }
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
                if (direction == EpgFocusDirection.UP || direction == EpgFocusDirection.DOWN) {
                    selectAnchored(next, anchor)
                } else {
                    selectUnanchored(next)
                }
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
        showContextMenu = false
        val searchEventId = detailsEvent?.id.takeIf { detailsFromSearch }
        detailsEvent = null
        detailsObservation = null
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
                closeDetails()
            }
            closeSearch()
        }
    }

    val settingsStore: UiSettingsStore = koinInject()
    val settingsScope = rememberCoroutineScope()

    /** Closes the open side panel and returns focus to the header button that opened it. */
    fun closePanel() {
        val opener = when (openPanel) {
            ChannelsPanel.TAGS -> header.tags
            ChannelsPanel.FILTER -> header.filter
            else -> header.view
        }
        runCatching { opener.requestFocus() }
        openPanel = null
    }

    /** Closes the day and time panel, making its [jump] first, and returns focus to the date button. */
    fun closeTimePanel(jump: () -> Unit = {}) {
        showTimePanel = false
        jump()
        releaseProgrammeFocus()
        runCatching { guideDateFocus.requestFocus() }
    }

    val detailsLayering = at.bernhardberger.tvhplayer.ui.components.LocalBrowseContentLayering.current
    Box(Modifier.fillMaxSize().testTag("epg-screen")) content@{
        BrowsePreparationPending(initialFocusEnabled, ready = preparedIndex != null,
            onPendingFocus = {
                // Cold entry starts on the header; a player return enters its programme.
                preparationEntryToScope = (!initialPositionDone && pendingPlayerReturn == null) || !programmeFocusOwned
                realizedTarget = null
                if (preparationEntryToScope) programmeFocusOwned = false
            },
            onReadyFocus = {
                if (!indexCurrent || !channelScopeState.settingsLoaded ||
                    channelViewModel.scope.value != channelScopeState
                ) false else if (preparationEntryToScope) {
                    programmeFocusOwned = false
                    focusGuideHeader()
                } else {
                    selectedTarget?.eventId?.let(eventFocusRequesters::get)?.let {
                        runCatching { it.requestFocus() }.getOrDefault(false)
                    } ?: focusGuideHeader()
                }
            },
            handoffKey = Triple(channelScopeState, selectedTarget, indexCurrent),
            skeleton = {
                GuidePreparationSkeleton(
                    windowStartSec = windowStartSec,
                    windowEndSec = windowEndSec,
                    nowSecProvider = nowSecProvider,
                    headline = if (view.headline && channels.isNotEmpty()) {
                        {
                            GuideHeadline(
                                programme = { null },
                                observation = observation,
                                scopeObservation = channelScopeState.observation,
                                loading = true,
                                nowSec = nowSecProvider,
                                modifier = Modifier.padding(start = startPadding + TvBrowseLeadingInset, top = headlineDrop()),
                            )
                        }
                    } else null,
                    contentPadding = contentPadding,
                    timelineContentPadding = guideTimelineContentPadding(contentPadding, layoutDirection, terminalReserve),
                    startPadding = startPadding,
                    endPadding = endPadding,
                    rows = view.rows,
                )
            },
        )
        if (preparedIndex == null) return@content
        // Keep rendering in separate generated methods. As one body this lambda needed
        // more than 256 dex registers, and D8/R8 emitted code that API 31 rejected
        // (VerifyError: an int parameter read from an unassigned register).
        @Composable
        fun GuideBrowseContent() {
        val browseAlpha = { 1f - (detailsLayering?.detailsAlpha() ?: if (detailsEvent != null && !showContextMenu) 1f else 0f) }
        // Read by the headline and its picture alone, so neither focus steps nor a settle recompose the screen.
        val settledHeadline = if (view.headline) {
            rememberSettledGuideHeadline(
                programme = { guideHeadlineProgramme(selectedTarget, channels, focusRows) },
                repositioning = {
                    guideAlert == null && (
                        !initialPositionDone || windowFocusRequest != null ||
                            channelFocusRequest != null || frontierRequest != null ||
                            // Rows with programmes get a focus from reconciliation; empty ones may be loading.
                            focusRows.any { it.events.isNotEmpty() } ||
                            coverageRequestVersion.let {
                                orderedChannelIds.any { coverageRequests.isPending(it, windowStartSec) }
                            }
                        )
                },
            )
        } else null
        val headlineProgramme = { settledHeadline?.value }
        if (view.headline) {
            GuideHeadlineBackground(
                programme = headlineProgramme,
                channels = channels,
                observation = observation,
                scopeObservation = channelScopeState.observation,
                nowSec = nowSecProvider,
                logoTop = contentPadding.calculateTopPadding() + guideHeaderMinHeight(),
                headlineDrop = headlineDrop(),
                endInset = max(endPadding, TvBrowseTrailingInset),
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = browseAlpha() },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = browseAlpha() }
                .semantics { if (detailsEvent != null || openPanel != null || showTimePanel) hideFromAccessibility() }
                .focusProperties {
                    // As in Channels: entering from the rail returns to the remembered programme.
                    onEnter = {
                        val awayFromNavigation = if (layoutDirection == LayoutDirection.Ltr) {
                            FocusDirection.Right
                        } else {
                            FocusDirection.Left
                        }
                        if (requestedFocusDirection == awayFromNavigation) focusGuideContent()
                    }
                }
                .focusGroup()
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Back) {
                        // A release that never reached this Column (the focused cell can leave when
                        // the window swaps) must not leave the next press dead.
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                            consumeBackRelease = false
                            railOnBackRelease = false
                        }
                        if (consumeBackRelease) {
                            if (event.type == KeyEventType.KeyUp) {
                                consumeBackRelease = false
                                // The rail takes focus on the release, so it cannot receive it.
                                if (railOnBackRelease) {
                                    railOnBackRelease = false
                                    backToRail()
                                }
                            }
                            return@onPreviewKeyEvent true
                        }
                        if (event.type == KeyEventType.KeyDown && programmeBackEnabled) {
                            consumeBackRelease = true
                            railOnBackRelease = !backToNow()
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
            GuideHeader(
                tag = channelScope.activeTagId?.let { id -> groups.firstOrNull { it.tagId == id }?.name },
                genre = genre.takeIf { it != ProgrammeCategory.ALL }?.let { programmeCategoryLabel(it) },
                genreDot = if (view.genreColors) tvGenreColors(genre)?.base else null,
                windowStartSec = windowStartSec,
                windowEndSec = windowEndSec,
                nowSecProvider = nowSecProvider,
                focus = header,
                dateFocus = guideDateFocus,
                showTags = groups.size > 1,
                showFilter = showGenre,
                navigationFocus = navigationFocus,
                contentPadding = contentPadding,
                startPadding = startPadding,
                endPadding = endPadding,
                onDateClick = {
                    releaseProgrammeFocus()
                    showTimePanel = true
                },
                onFocused = { releaseProgrammeFocus() },
                onOpen = { panel ->
                    releaseProgrammeFocus()
                    openPanel = panel
                },
                onMoveToContent = { leaveGuideScope() },
            )
            if (view.headline && channels.isNotEmpty()) {
                GuideHeadline(
                    programme = headlineProgramme,
                    observation = observation,
                    scopeObservation = channelScopeState.observation,
                    loading = connectionUiState.awaitsGuide(observation.epgState),
                    nowSec = nowSecProvider,
                    modifier = Modifier.padding(start = startPadding + TvBrowseLeadingInset, top = headlineDrop()),
                )
            } else {
                Spacer(Modifier.height(guideHeaderRulerClearance()))
            }
            GuideTimelineBody(
                motion = scopeMotion,
                selectedKey = channelScope.activeTagId,
                state = {
                    GuideTabBody(
                        channelScopeState, observation, connectionUiState, timelineEventIndex,
                         orderedChannelIds, channelNumbers,
                         preparedIndex.input.windowStartSec, preparedIndex.input.windowEndSec,
                        nowSecProvider, { selectedTarget }, tagNotice, guideRecovering,
                        guideAlert,
                        { channelId ->
                            coverageRequestVersion.let {
                                coverageRequests.isPending(channelId, preparedIndex.input.windowStartSec)
                            }
                        },
                        { channelId -> coverageByChannel[channelId] },
                        earlierContent, laterContent, view.rows, genre, view.genreColors,
                        clipSelectedToViewport = {
                            selectedTarget?.let { target ->
                                focusRows.getOrNull(target.channelIndex)?.events?.firstOrNull { it.id == target.eventId }
                            }?.let(::keepAnchoredWindowFor) == true
                        },
                    )
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                channelListState = channelListState,
                contentPadding = contentPadding,
                layoutDirection = layoutDirection,
                density = density,
                startPadding = startPadding,
                endPadding = endPadding,
                timelineWidthPx = timelineWidthPx,
                guideBringIntoView = guideBringIntoView,
                gridFocus = gridFocus,
                guideRetryFocus = guideRetryFocus,
                navigationFocus = navigationFocus,
                eventFocusRequesters = eventFocusRequesters,
                onDismissTagNotice = { channelViewModel.dismissUnavailableTagNotice() },
                onRetry = onRetry,
                onOpenConnectionSettings = onOpenConnectionSettings,
                // Runs only for the current visit; the body consumes other visits' keys.
                onViewportKey = viewportKey@{ event ->
                    if (!initialFocusEnabled || !programmeFocusOwned) return@viewportKey false
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
                        return@viewportKey consume
                    }
                    if (event.type != KeyEventType.KeyDown) return@viewportKey false
                    when (event.key) {
                        Key.DirectionLeft -> moveFocus(EpgFocusDirection.LEFT)
                        Key.DirectionRight -> moveFocus(EpgFocusDirection.RIGHT)
                        Key.DirectionUp -> moveFocus(EpgFocusDirection.UP)
                        Key.DirectionDown -> moveFocus(EpgFocusDirection.DOWN)
                        else -> false
                    }
                },
                // The scope and list state are the focused row's frame; windowStartSec is the live window.
                onProgrammeFocused = focused@{ frameScope, channelIndex, channelId, event, frameListState ->
                    if (!initialFocusEnabled || channelViewModel.scope.value != frameScope) return@focused
                    profileTrace("P44:focus:guide") {
                        if (BuildConfig.PROFILE_TRACE) {
                            profileTrace("P48:focus:guide:${channelId.value}:${event.id.value}") { }
                        }
                        val target = EpgFocusTarget(channelIndex, event.id)
                        val settled = realizedTarget == selectedTarget
                        realizedTarget = target
                        if (settled || !programmeFocusOwned || selectedTarget == null) {
                            // Focus that arrives on another cell, or after the selection was cleared, starts a new anchor.
                            if (selectedTarget != target) focusAnchor = null
                            selectedTarget = target
                        }
                        programmeFocusOwned = true
                        if (target == selectedTarget) {
                            lastFocusedEvent = event
                            selection.setSelected(channelId)
                            guidePositionStore.save(GuidePosition(
                                channelId = channelId,
                                eventId = event.id,
                                eventStartSec = event.start.epochSeconds,
                                windowStartSec = windowStartSec,
                                firstVisibleColumn = frameListState.firstVisibleItemIndex,
                            ))
                        }
                    }
                },
                onOpenProgrammeDetails = details@{ channelIndex, event, frameObservation ->
                    if (realizedTarget != selectedTarget || frontierRequest != null ||
                        channelFocusRequest != null || windowFocusRequest != null
                    ) return@details
                    releaseProgrammeFocus()
                    selectedTarget = EpgFocusTarget(
                        channelIndex,
                        event.id,
                    )
                    detailsEvent = event
                    detailsObservation = frameObservation
                    detailsFromSearch = false
                    showContextMenu = false
                },
                onOpenProgrammeContextMenu = menu@{ channelIndex, event, frameObservation ->
                    if (realizedTarget != selectedTarget || frontierRequest != null ||
                        channelFocusRequest != null || windowFocusRequest != null
                    ) return@menu
                    suppressActivationRelease = true
                    releaseProgrammeFocus()
                    selectedTarget = EpgFocusTarget(channelIndex, event.id)
                    detailsEvent = event
                    detailsObservation = frameObservation
                    detailsFromSearch = false
                    showContextMenu = true
                },
            )
        }

        }
        GuideBrowseContent()
        @Composable
        fun GuideOverlays() {
        GuideOverlayHost(
            contentPadding = contentPadding,
            showSearchDialog = showSearchDialog,
            detailsFromSearch = detailsFromSearch,
            searchQuery = searchQuery,
            searchResult = searchResult,
            searching = searchRequest != null,
            searchEnabled = currentSession != null,
            searchChannelName = { channelId ->
                val resultObservation = searchObservation?.let {
                    searchResultObservation(it, observation)
                }
                channelId?.let { resultObservation?.channel(it)?.name }
            },
            restoreSearchResultFocus = restoreSearchResultFocus,
            onSearchFocusRestored = { restoreSearchResultFocus = null },
            onSearchQueryChange = { query ->
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
            onOpenSearchDetails = { event ->
                searchObservation?.let { searchedObservation ->
                    val resultObservation = searchResultObservation(
                        searchedObservation,
                        observation,
                    )
                    if (resultObservation != null) {
                        releaseProgrammeFocus()
                        detailsEvent = event
                        detailsObservation = resultObservation
                        detailsFromSearch = true
                        restoreSearchResultFocus = null
                    }
                }
            },
            onDismissSearch = ::closeSearch,
            detailsEvent = detailsEvent,
            detailsObservation = detailsObservation,
            observation = observation,
            currentObservation = { observationState.value },
            showContextMenu = showContextMenu,
            mutationScope = coroutineScope,
            mutationInFlight = detailsMutationInFlight,
            mutations = dvrMutationActions,
            notices = notices,
            onShowDetails = { showContextMenu = false },
            onPlay = onPlay,
            onPlayRecording = onPlayRecording,
            onCloseDetails = ::closeDetails,
            eventFocusRequesters = eventFocusRequesters,
            onShowGuide = { channelId, programme ->
                showSearchDialog = false
                restoreDetailsFocus = false
                val available = programme?.id?.let(observation::event)?.takeIf {
                    it.channelId == channelId &&
                        it.start.epochSeconds in windowBounds.earliestStartSec..windowBounds.latestStartSec
                }
                val focusSec = available?.start?.epochSeconds ?: nowSecProvider()
                jumpToWindow(floorGuideWindowToHour(focusSec, guideZoneId), channelId, available, focusSec = focusSec)
            },
        )
        if (showTimePanel) {
            GuideTimePanel(
                windowStartSec = windowStartSec,
                windowEndSec = windowEndSec,
                bounds = windowBounds,
                zoneId = guideZoneId,
                nowSecProvider = nowSecProvider,
                // A day, a time and Now each jump and close the panel.
                onJump = { closeTimePanel { jumpToWindow(it) } },
                onNow = { closeTimePanel { jumpToNow(selectedTarget?.channelIndex?.let(channels::getOrNull)?.id) } },
                onClose = { closeTimePanel() },
            )
        }
        when (openPanel) {
            ChannelsPanel.TAGS -> ChannelsTagsPanel(
                groups = groups,
                currentTagId = channelScope.activeTagId,
                onSelect = { tagId ->
                    closePanel()
                    releaseProgrammeFocus()
                    scopeMotion.select(tagId, groups.map { it.tagId })
                    channelViewModel.selectTag(tagId)
                },
                onClose = ::closePanel,
            )
            ChannelsPanel.FILTER -> GuideFilterPanel(
                genre = genre,
                genreColors = view.genreColors,
                onSelect = { category ->
                    closePanel()
                    // Without genre codes, All genres takes Filter away; View options keeps focus.
                    if (category == ProgrammeCategory.ALL && !genreCodes) runCatching { header.view.requestFocus() }
                    onGenreChange(category)
                },
                onClose = ::closePanel,
            )
            ChannelsPanel.VIEW_OPTIONS -> GuideViewOptionsPanel(
                view = view,
                onViewChange = { settingsScope.launch { settingsStore.setGuideView(it) } },
                onClose = ::closePanel,
            )
            null -> Unit
        }
        }
        GuideOverlays()

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

internal fun SessionObservation.currentDvrConfigurations(): List<DvrConfiguration> =
    when (val state = dvrConfigurationsState) {
        is DvrConfigurationsState.Current -> state.configurations
        is DvrConfigurationsState.Stale,
        is DvrConfigurationsState.Synchronizing,
        DvrConfigurationsState.Denied,
        DvrConfigurationsState.Unknown -> emptyList()
    }

/** The Guide's 1xx alert, shown only while it has no channels to draw: the connection's failure, or 108 when Ready has none. */
internal fun guideAppError(
    hasChannels: Boolean,
    isEmptyTag: Boolean,
    connectionUiState: ConnectionUiState,
    channelCatalogCurrent: Boolean,
    connectionFailure: ConnectionUiState? = connectionUiState,
): AppError? = if (hasChannels) {
    null
} else {
    connectionFailure?.browseAppError() ?: connectionUiState
        .browseAppError(noChannels = channelCatalogCurrent && !isEmptyTag)
        .takeIf { connectionUiState == ConnectionUiState.Ready }
}

/** The passive text of an empty Guide without an alert: loading, reconnecting, or an empty tag. */
internal fun guideEmptyMessageRes(
    isEmptyTag: Boolean,
    connectionUiState: ConnectionUiState,
    channelCatalogCurrent: Boolean,
): Int = when {
    connectionUiState == ConnectionUiState.Reconnecting -> R.string.epg_reconnecting
    connectionUiState == ConnectionUiState.Ready && channelCatalogCurrent && isEmptyTag -> R.string.empty_channel_tag
    else -> R.string.epg_loading
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

internal fun Long.formatDateTime(locale: java.util.Locale = java.util.Locale.getDefault()): String =
    at.bernhardberger.tvhplayer.ui.common.formatRecordingDate(this, locale = locale)
