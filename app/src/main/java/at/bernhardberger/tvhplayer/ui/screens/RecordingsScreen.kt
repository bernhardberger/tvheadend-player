package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.ui.components.rule
import at.bernhardberger.tvhplayer.ui.components.watchingIndicator
import at.bernhardberger.tvhplayer.ui.components.recordingViewerPosition
import at.bernhardberger.tvhplayer.ui.components.progressColor

import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.components.ConfirmationChoice
import at.bernhardberger.tvhplayer.ui.components.ConfirmationDialog
import at.bernhardberger.tvhplayer.ui.components.ConfirmationSubject
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.common.formatDateRange
import at.bernhardberger.tvhplayer.ui.common.formatFileSize
import at.bernhardberger.tvhplayer.core.summarizeDvrFolder
import at.bernhardberger.tvhplayer.core.recordingArtwork
import at.bernhardberger.tvhplayer.core.runningRecordings
import at.bernhardberger.tvhplayer.core.folderContextActions
import at.bernhardberger.tvhplayer.core.folderActionTargets
import at.bernhardberger.tvhplayer.ui.components.label
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import at.bernhardberger.tvhplayer.ui.components.browseDetailsViewport
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.player.animateShown

import androidx.compose.ui.graphics.graphicsLayer

import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.core.recordingContextActions
import at.bernhardberger.tvhplayer.core.recordingRuleActions
import at.bernhardberger.tvhplayer.core.recordingRuleAvailable
import at.bernhardberger.tvhplayer.core.recordingRuleKind
import at.bernhardberger.tvhplayer.core.isRuleRecording
import at.bernhardberger.tvhplayer.ui.components.recordingRule
import at.bernhardberger.tvhplayer.core.deleteRemovesEntryOnly
import at.bernhardberger.tvhplayer.ui.components.BrowseContextMenu
import at.bernhardberger.tvhplayer.ui.components.ProgrammeArtwork
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.common.programmeEpisodeLine

import at.bernhardberger.tvhplayer.notices.NoticeCenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import at.bernhardberger.tvhplayer.ui.screens.recordings.ArchiveViewOptionsButton
import at.bernhardberger.tvhplayer.ui.screens.recordings.ArchiveViewOptionsPanel
import at.bernhardberger.tvhplayer.core.recordingNowEntries
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingStorageIndicator
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.OutlinedButtonDefaults
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrProgressResult
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.toDvrMutationFeedback
import at.bernhardberger.tvhplayer.core.recordingWatchStateAction
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.DvrLibraryMode
import at.bernhardberger.tvhplayer.core.DvrLibraryPartition
import at.bernhardberger.tvhplayer.core.DvrArchiveFolder
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.core.DvrArchiveSort
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.core.DvrArchiveNaming
import at.bernhardberger.tvhplayer.core.programmeCategory
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabelRes
import at.bernhardberger.tvhplayer.core.DvrProblemBucket
import at.bernhardberger.tvhplayer.core.DvrScheduleSection
import at.bernhardberger.tvhplayer.core.buildDvrArchive
import at.bernhardberger.tvhplayer.core.groupDvrProblems
import at.bernhardberger.tvhplayer.core.groupDvrSchedule
import at.bernhardberger.tvhplayer.core.partitionDvrLibrary
import at.bernhardberger.tvhplayer.core.recordingElapsedProgress
import at.bernhardberger.tvhplayer.core.recordingIsRunning
import at.bernhardberger.tvhplayer.core.recordingWasRemoved
import at.bernhardberger.tvhplayer.core.watchedCount
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.core.recordingWatchState
import at.bernhardberger.tvhplayer.ui.rememberCloseBehindPlayer
import at.bernhardberger.tvhplayer.ui.components.TopLevelBrowseHeader
import at.bernhardberger.tvhplayer.ui.components.TabContent
import at.bernhardberger.tvhplayer.ui.components.BrowsePreparationPending
import at.bernhardberger.tvhplayer.ui.components.PreparedBrowseData
import at.bernhardberger.tvhplayer.ui.components.rememberPreparedBrowseData
import at.bernhardberger.tvhplayer.ui.components.rememberTabContentMotion
import at.bernhardberger.tvhplayer.ui.screens.recordings.ArchiveDepthContent
import at.bernhardberger.tvhplayer.ui.screens.recordings.archiveLevelId
import at.bernhardberger.tvhplayer.ui.screens.recordings.archiveLevelPath
import at.bernhardberger.tvhplayer.ui.screens.recordings.archiveLevelItems
import at.bernhardberger.tvhplayer.ui.screens.recordings.reconcileArchiveStack
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.ui.components.depth.DepthStack
import at.bernhardberger.tvhplayer.ui.components.depth.DepthFrame
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus
import at.bernhardberger.tvhplayer.ui.TvBrowseLeadingInset
import at.bernhardberger.tvhplayer.ui.TvBrowseTrailingInset
import at.bernhardberger.tvhplayer.ui.screens.recordings.PendingRecordingAction
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingConfirmationDialog
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingDetailsAction
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsPage
import at.bernhardberger.tvhplayer.core.recordingDetailsActions
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingModeTabs
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingProblems
import at.bernhardberger.tvhplayer.ui.screens.recordings.ScheduleDepthContent
import at.bernhardberger.tvhplayer.ui.screens.recordings.OpenRule
import at.bernhardberger.tvhplayer.ui.screens.recordings.RuleSurface
import at.bernhardberger.tvhplayer.ui.screens.recordings.ScheduleRuleActions
import at.bernhardberger.tvhplayer.ui.screens.recordings.currentIn
import at.bernhardberger.tvhplayer.core.AutomaticRecordingListItem
import at.bernhardberger.tvhplayer.ui.screens.recordings.SCHEDULE_ROOT_LEVEL
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingsEmptyState
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingsPreparationSkeleton
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingsSkeleton
import at.bernhardberger.tvhplayer.ui.screens.recordings.recordingsHeaderMinHeight
import at.bernhardberger.tvhplayer.ui.screens.recordings.recordingsLoading
import at.bernhardberger.tvhplayer.ui.screens.recordings.recordingsAppError
import at.bernhardberger.tvhplayer.ui.screens.recordings.listItems
import at.bernhardberger.tvhplayer.ui.screens.recordings.recordingItemKey
import at.bernhardberger.tvhplayer.ui.screens.recordings.recordingTimeRange
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.koin.compose.koinInject
import androidx.compose.ui.platform.LocalConfiguration
import at.bernhardberger.tvhplayer.core.automaticRecordingList
import at.bernhardberger.tvhplayer.ui.screens.recordings.SCHEDULE_NEW_TIMER
import at.bernhardberger.tvhplayer.ui.screens.recordings.scheduleManagementKeys
import at.bernhardberger.tvhplayer.ui.screens.recordings.ScheduleState
import java.text.Collator

private fun recordingOverlayOpen(
    detailsEntry: DvrEntry?,
    pendingAction: PendingRecordingAction?,
    folderMenu: FolderMenuTarget?,
): Boolean = detailsEntry != null || pendingAction != null || folderMenu != null

private data class RecordingLibraryPresentation(
    val library: DvrLibraryPartition,
    val archive: DvrArchiveFolder,
    val archiveEntries: List<DvrEntry>,
    val schedule: List<DvrScheduleSection>,
    val problems: Map<DvrProblemBucket, List<DvrEntry>>,
)

class RecordingsScreenState(initialMode: DvrLibraryMode = DvrLibraryMode.ARCHIVE) {
    val selectedKeys = mutableStateMapOf<String, String>()
    val archiveScrollPositions = mutableStateMapOf<String, Int>()
    val archiveScrollOffsets = mutableMapOf<String, Int>()
    val mode = mutableStateOf(initialMode)
    internal val requestedScheduleEntryId = mutableStateOf<DvrEntryId?>(null)
    val archiveNavigation = DepthNavigationState(DepthStack(listOf(DepthFrame("archive:"))))
    val scheduleNavigation = DepthNavigationState(DepthStack(listOf(
        DepthFrame(SCHEDULE_ROOT_LEVEL, firstVisibleItemId = SCHEDULE_NEW_TIMER),
    )))
    internal val scheduleState = ScheduleState()
    val archivePath: List<String> get() = archiveLevelPath(archiveNavigation.stack.active.levelId)

    /** Home can enter Schedule at an entry; ordinary tab/rail entry keeps its saved position. */
    fun open(mode: DvrLibraryMode, entryId: DvrEntryId? = null) {
        this.mode.value = mode
        requestedScheduleEntryId.value = entryId.takeIf { mode == DvrLibraryMode.SCHEDULE }
        if (mode == DvrLibraryMode.SCHEDULE && entryId != null) {
            selectedKeys["schedule"] = "recording:${recordingItemKey(entryId)}"
            archiveScrollPositions.remove("schedule")
        }
    }

    /** Folder actions run one at a time, also when one still finishes after the screen was left and opened again. */
    internal val folderActionLock = Mutex()
}

private data class RecordingsTabBody(
    val mode: DvrLibraryMode,
    val location: String,
    val empty: Boolean,
    val connection: ConnectionUiState,
    val failure: ConnectionUiState?,
    val observation: SessionObservation,
    val channelsById: Map<ChannelId, Channel>,
    val archive: DvrArchiveFolder,
    val fullArchive: DvrArchiveFolder,
    val schedule: List<DvrScheduleSection>,
    val problems: Map<DvrProblemBucket, List<DvrEntry>>,
    val selectedKey: String?,
    val scrollIndex: Int,
    val scrollOffset: Int,
    val recoveryGeneration: Int,
)

/** A folder's menu: the folder by path, so it always shows what is there now, and the session it opened in. */
private data class FolderMenuTarget(val path: List<String>, val session: CurrentSessionObservation)

/** What a confirmed folder deletion removes, fixed when it was asked so nothing added later goes with it. */
private data class FolderDeleteTarget(
    val action: BrowseContextAction,
    val name: String,
    val recordingIds: List<DvrEntryId>,
    val sizeBytes: Long,
    val keepsRunning: Boolean,
)

@Composable
fun RecordingsScreen(
    contentPadding: PaddingValues = PaddingValues(),
    initialFocusEnabled: Boolean = true,
    backEnabled: Boolean = true,
    session: TvheadendSession = koinInject(),
    connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
    connectionFailure: ConnectionUiState? = connectionUiState,
    onRetry: () -> Unit = {},
    onOpenConnectionSettings: () -> Unit = {},
    onPlayRecording: (RecordingPlaybackSelection, RecordingPlaybackStart) -> Unit = { _, _ -> },
    onWatchChannel: ((ChannelId, String) -> Unit)? = null,
    onShowGuide: (ChannelId, EpgEvent?) -> Unit = { _, _ -> },
    state: RecordingsScreenState? = null,
    playingRecordingId: DvrEntryId? = null,
    onReturnToRecording: (DvrEntryId, () -> Unit) -> Boolean = { _, _ -> false },
    view: DvrArchiveView = DvrArchiveView(),
    onViewChange: (DvrArchiveView) -> Unit = {},
) {
    val observation by session.observation.collectAsStateWithLifecycle()
    val dvrMutationActions = remember(session.dvrRepository) {
        DvrMutationActions(session.dvrRepository)
    }
    RecordingsScreenContent(
        observation = observation,
        contentPadding = contentPadding,
        initialFocusEnabled = initialFocusEnabled,
        backEnabled = backEnabled,
        connectionUiState = connectionUiState,
        connectionFailure = connectionFailure,
        onRetry = onRetry,
        onOpenConnectionSettings = onOpenConnectionSettings,
        onPlayRecording = onPlayRecording,
        onWatchChannel = onWatchChannel,
        onShowGuide = onShowGuide,
        state = state,
        playingRecordingId = playingRecordingId,
        onReturnToRecording = onReturnToRecording,
        dvrMutationActions = dvrMutationActions,
        currentObservation = { session.observation.value },
        view = view,
        onViewChange = onViewChange,
    )
}

@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
@Composable
internal fun RecordingsScreenContent(
    observation: SessionObservation,
    contentPadding: PaddingValues = PaddingValues(),
    initialFocusEnabled: Boolean = true,
    backEnabled: Boolean = true,
    connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
    connectionFailure: ConnectionUiState? = connectionUiState,
    onRetry: () -> Unit = {},
    onOpenConnectionSettings: () -> Unit = {},
    onPlayRecording: (RecordingPlaybackSelection, RecordingPlaybackStart) -> Unit = { _, _ -> },
    onWatchChannel: ((ChannelId, String) -> Unit)? = null,
    onShowGuide: (ChannelId, EpgEvent?) -> Unit = { _, _ -> },
    state: RecordingsScreenState? = null,
    playingRecordingId: DvrEntryId? = null,
    onReturnToRecording: (DvrEntryId, () -> Unit) -> Boolean = { _, _ -> false },
    dvrMutationActions: DvrMutationActions,
    currentObservation: () -> SessionObservation = { observation },
    notices: NoticeCenter = koinInject(),
    nowSecProvider: () -> Long = at.bernhardberger.tvhplayer.ui.common.rememberCurrentEpochSeconds(),
    view: DvrArchiveView = DvrArchiveView(),
    onViewChange: (DvrArchiveView) -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = contentPadding.calculateStartPadding(layoutDirection)
    val endPadding = contentPadding.calculateEndPadding(layoutDirection)
    val currentSession = observation.currentSession
    val locale = LocalConfiguration.current.locales[0]
    val automaticRecordings = remember(observation.dvrSnapshotForDisplay, locale) {
        val collator = Collator.getInstance(locale)
        automaticRecordingList(observation, Comparator { first, second -> collator.compare(first, second) })
    }
    val canModifyRecordings = (observation.sessionState as? SessionState.Ready)
        ?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
    val scheduleManagement = if (automaticRecordings.available) {
        scheduleManagementKeys(canModifyRecordings, automaticRecordings.items.isNotEmpty())
    } else emptyList()
    val latestObservationProvider by rememberUpdatedState(currentObservation)
    val preparationAuthority = currentSession ?: observation.dvrSnapshotForDisplay
    var retainedPreparation by remember(preparationAuthority) {
        mutableStateOf<PreparedBrowseData<DvrSnapshot?, RecordingLibraryPresentation>?>(null)
    }
    @Composable
    fun rememberChannelsById(): Map<ChannelId, Channel> {
        val channels = observation.channelCatalogForDisplay?.channels.orEmpty()
        return remember(channels) { channels.associateBy { it.id } }
    }
    val channelsById = rememberChannelsById()
    val scope = rememberCoroutineScope()
    val screenState = state ?: remember { RecordingsScreenState() }
    DisposableEffect(screenState) {
        onDispose { screenState.requestedScheduleEntryId.value = null }
    }
    val contentFocus = remember { FocusRequester() }
    val modeFocus = remember { FocusRequester() }
    val selectedKeys = screenState.selectedKeys
    val archiveScrollPositions = screenState.archiveScrollPositions
    var mode by screenState.mode
    val archiveNavigation = screenState.archiveNavigation
    val scheduleNavigation = screenState.scheduleNavigation
    val archiveLevel = archiveNavigation.stack.active.levelId
    val drawerFocus = LocalBrowseNavigationFocus.current
    val drawerState = at.bernhardberger.tvhplayer.ui.components.LocalBrowseDrawerState.current
    var requestContentFocus by remember { mutableStateOf(true) }
    var contentHasFocus by remember { mutableStateOf(false) }
    var contentFocusOwned by remember { mutableStateOf(false) }
    // Browse's options button sits in the archive's root headline: focus there is not the list's.
    var archiveHasFocus by remember { mutableStateOf(false) }
    var optionsHasFocus by remember { mutableStateOf(false) }
    var loadingFocusParked by remember { mutableStateOf(false) }
    LaunchedEffect(initialFocusEnabled, drawerState?.currentValue, screenState.requestedScheduleEntryId.value) {
        if ((!initialFocusEnabled || drawerState?.currentValue == androidx.tv.material3.DrawerValue.Open) &&
            screenState.requestedScheduleEntryId.value != null
        ) {
            screenState.requestedScheduleEntryId.value = null
            requestContentFocus = false
            loadingFocusParked = false
        }
    }
    var relocatingKey by remember { mutableStateOf<Key?>(null) }
    var focusRecoveryGeneration by remember { mutableIntStateOf(0) }
    val overlays = remember { RecordingOverlaysState(screenState.scheduleState) }
    var menuConsumed by remember(overlays.detailsEntry?.id) { mutableStateOf(false) }
    val confirmationVisibility = remember(overlays.pendingAction) {
        MutableTransitionState(false).apply { targetState = overlays.pendingAction != null }
    }
    fun openScheduleRule(surface: RuleSurface, item: AutomaticRecordingListItem? = null) {
        val latest = latestObservationProvider()
        val ready = latest.sessionState as? SessionState.Ready
        val currentItem = item?.currentIn(latest)
        if (latest.currentSession == currentSession && ready?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED &&
            latest.dvrState is DvrRepositoryState.Current
        ) latest.currentSession?.let { session ->
            if (item == null || currentItem != null) {
                overlays.scheduleRuleRequest = OpenRule(surface, session, currentItem)
                if (overlays.scheduleRuleRequest != null) contentFocusOwned = false
            }
        }
    }

    fun closeFolderMenu() {
        if (overlays.folderMenu == null) return
        // As for a recording's menu: restore the folder row before the menu leaves.
        overlays.pendingDetailsReturn = !runCatching { contentFocus.requestFocus() }.getOrDefault(false)
        overlays.folderDelete = null
        overlays.folderMenu = null
    }

    fun runFolderAction(action: BrowseContextAction, session: CurrentSessionObservation, recordingIds: List<DvrEntryId>) {
        scope.launch {
            val noticeContext = notices.context()
            // A folder action finishes and reports even when the screen leaves halfway, and a second
            // one waits for the first so both never send the same recording.
            withContext(NonCancellable) {
                screenState.folderActionLock.withLock {
                    dvrMutationActions.runFolderAction(action, session, recordingIds) { latestObservationProvider() }
                }?.let { notices.post(it, noticeContext) }
            }
        }
    }

    // [beforePlay] runs only when the recording plays, before the request; a menu stays up through it.
    // With [runningOpensDetails] (Schedule) a running recording opens its details: no choice, no return to its player.
    fun openRecording(entry: DvrEntry, returnIfPlaying: Boolean = true, beforePlay: () -> Unit = { overlays.showContextMenu = false },
        runningOpensDetails: Boolean = false) {
        val details = runningOpensDetails && recordingIsRunning(entry)
        val returnToPlayer = returnIfPlaying && !details
        if (returnToPlayer && entry.id == playingRecordingId && onReturnToRecording(entry.id, {})) return
        val current = latestObservationProvider()
        val capability = current.currentSession
        val action = if (details) at.bernhardberger.tvhplayer.core.RecordingOpenAction.DETAILS
            else at.bernhardberger.tvhplayer.core.recordingOpenAction(
                capability?.let { current.recordingPlaybackAdmission(entry) })
        val activeChoice = !details && recordingIsRunning(entry) && entry.channelId?.let(current::channel) != null &&
            capability != null && onWatchChannel != null
        if (!activeChoice && capability != null && (action == at.bernhardberger.tvhplayer.core.RecordingOpenAction.START_OVER ||
                action == at.bernhardberger.tvhplayer.core.RecordingOpenAction.RESUME)) {
            beforePlay()
            onPlayRecording(RecordingPlaybackSelection(capability, entry.id),
                if (action == at.bernhardberger.tvhplayer.core.RecordingOpenAction.RESUME) RecordingPlaybackStart.RESUME
                else RecordingPlaybackStart.START_OVER)
            return
        }
        overlays.showContextMenu = false
        overlays.showResumeChoice = activeChoice ||
            action == at.bernhardberger.tvhplayer.core.RecordingOpenAction.RESUME_CHOICE
        contentFocusOwned = false
        overlays.detailsInitialAction = null
        overlays.detailsEntry = entry
        overlays.detailsObservation = current
    }

    var pendingView by rememberPendingArchiveView(view)
    val chosenView = pendingView ?: view
    val chosenGrouping = chosenView.grouping
    // The stop above the list: the View options button at Browse's root, else (or without a list) the selected tab.
    fun focusHeader(): Boolean {
        fun request(stop: FocusRequester) = runCatching { stop.requestFocus() }.getOrDefault(false)
        val atBrowseRoot = mode == DvrLibraryMode.ARCHIVE && !archiveNavigation.stack.canPop
        return (atBrowseRoot && request(overlays.optionsFocus)) || request(modeFocus)
    }
    val naming = rememberArchiveNaming(channelsById)
    // Unwatched, Partially watched and Watched read the position behind the check mark,
    // so they need the observation's saved position.
    fun archiveFor(presentation: RecordingLibraryPresentation, show: DvrArchiveShow): DvrArchiveFolder =
        if (chosenGrouping == DvrArchiveGrouping.FOLDERS && chosenView.sort == DvrArchiveSort.NEWEST && show == DvrArchiveShow.ALL) presentation.archive
        else buildDvrArchive(presentation.archiveEntries, chosenGrouping, naming, chosenView.sort, show) { entry ->
            recordingWatchState(
                entry,
                at.bernhardberger.tvhplayer.core.offeredResumePosition(observation.recordingPlaybackAdmission(entry)),
                0L,
            )
        }
    LaunchedEffect(overlays.requestStop) {
        val stop = overlays.requestStop ?: return@LaunchedEffect
        repeat(8) {
            withFrameNanos { }
            if (runCatching { stop.requestFocus() }.getOrDefault(false)) {
                contentFocusOwned = false
                requestContentFocus = false
                overlays.requestStop = null
                return@LaunchedEffect
            }
        }
        // Nothing to land on (empty or a tab without chips): the list takes it as before.
        overlays.requestStop = null
        requestContentFocus = true
    }
    val location = when (mode) {
        DvrLibraryMode.ARCHIVE -> archiveLevel
        DvrLibraryMode.SCHEDULE -> "schedule"
        DvrLibraryMode.PROBLEMS -> "problems"
    }
    val prepared = rememberPreparedBrowseData(preparationAuthority, observation.dvrSnapshotForDisplay) { snapshot ->
        val library = partitionDvrLibrary(snapshot?.entries.orEmpty())
        currentCoroutineContext().ensureActive()
        val archiveEntries = library.archive + snapshot?.entries.orEmpty().filter(::recordingIsRunning)
        val archive = buildDvrArchive(archiveEntries)
        currentCoroutineContext().ensureActive()
        RecordingLibraryPresentation(
            library = library,
            archive = archive,
            archiveEntries = archiveEntries,
            schedule = groupDvrSchedule(library.schedule, System.currentTimeMillis() / 1000L),
            problems = groupDvrProblems(library.problems),
        )
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        // An observation can dismiss the confirmation between down and up.
        // Finish that key cycle here, not on the refreshed details' action.
        if (event.key == overlays.confirmationKey && overlays.pendingAction == null) {
            if (event.type == KeyEventType.KeyUp) overlays.confirmationKey = null
            true
        } else {
            if (overlays.pendingAction != null && event.type == KeyEventType.KeyDown) {
                overlays.confirmationKey = event.key
            }
            if (event.key == overlays.confirmationKey && event.type == KeyEventType.KeyUp) {
                overlays.confirmationKey = null
            }
            false
        }
    }) content@{
    BrowsePreparationPending(initialFocusEnabled, ready = prepared != null,
        onReadyFocus = {
            // A new session can replace preparation while the viewer owns a mode tab.
            // The temporary anchor must hand back to that tab, not revive a deep link.
            if (!requestContentFocus && !contentFocusOwned && !loadingFocusParked) focusHeader()
            else { requestContentFocus = true; true }
        },
        skeleton = { RecordingsPreparationSkeleton(contentPadding, startPadding) })
    if (prepared == null) return@content
    val observedEntries = prepared.input?.entries.orEmpty()
    val retained = retainedPreparation ?: prepared
    val retainedEntries = retained.input?.entries.orEmpty()
    val observedLibrary = prepared.value.library
    val observedFullArchive = remember(prepared.value, chosenView.grouping, chosenView.sort, naming) {
        archiveFor(prepared.value, DvrArchiveShow.ALL)
    }
    val observedArchive = remember(
        prepared.value, chosenView, naming, observedFullArchive,
        if (chosenView.show != DvrArchiveShow.ALL) observation else null,
    ) {
        if (chosenView.show == DvrArchiveShow.ALL) observedFullArchive else archiveFor(prepared.value, chosenView.show)
    }
    val observedRecordingNow = remember(observedArchive, chosenView) { observedArchive.recordingNowEntries(chosenView) }
    val reconciledArchiveStack = remember(
        observedArchive, observedFullArchive, observedRecordingNow, archiveNavigation.stack,
    ) {
        reconcileArchiveStack(archiveNavigation.stack, observedArchive, observedRecordingNow)
    }
    val observedKeys = when (mode) {
        DvrLibraryMode.ARCHIVE -> requireNotNull(
            archiveLevelItems(
                observedArchive, reconciledArchiveStack.active.levelId, observedRecordingNow,
            ),
        ).map { it.key }
        DvrLibraryMode.SCHEDULE -> scheduleManagement +
            observedLibrary.schedule.map { "recording:${recordingItemKey(it.id)}" }
        DvrLibraryMode.PROBLEMS -> observedLibrary.problems.map { "recording:${recordingItemKey(it.id)}" }
    }
    val removalHandoff = contentFocusOwned && retainedEntries != observedEntries &&
        ((when (mode) {
            DvrLibraryMode.ARCHIVE -> archiveNavigation.stack.active.focusedItemId
            DvrLibraryMode.SCHEDULE -> scheduleNavigation.stack.frames.first().focusedItemId
            DvrLibraryMode.PROBLEMS -> selectedKeys[location]
        })?.let { it !in observedKeys } == true ||
            (mode == DvrLibraryMode.ARCHIVE && reconciledArchiveStack.active.levelId != archiveLevel))
    val entries = if (removalHandoff) retainedEntries else observedEntries
    val presentation = if (removalHandoff) retained.value else prepared.value
    val library = presentation.library
    val fullArchive = remember(presentation, chosenView.grouping, chosenView.sort, naming, observedFullArchive) {
        if (presentation === prepared.value) observedFullArchive else archiveFor(presentation, DvrArchiveShow.ALL)
    }
    val archive = remember(
        presentation, chosenView, naming, fullArchive, observedArchive,
        if (chosenView.show != DvrArchiveShow.ALL) observation else null,
    ) {
        when {
            presentation === prepared.value -> observedArchive
            chosenView.show == DvrArchiveShow.ALL -> fullArchive
            else -> archiveFor(presentation, chosenView.show)
        }
    }
    val recordingNow = remember(archive, chosenView) { archive.recordingNowEntries(chosenView) }
    val archiveItems = remember(archive, archiveLevel, fullArchive, recordingNow) {
        archiveLevelItems(archive, archiveLevel, recordingNow) ?: archive.listItems(recordingNow)
    }
    val scheduleGroups = presentation.schedule
    val problemGroups = presentation.problems
    val itemKeys = when (mode) {
        DvrLibraryMode.ARCHIVE -> archiveItems.map { it.key }
        DvrLibraryMode.SCHEDULE -> scheduleManagement + scheduleGroups.flatMap { it.entries }
            .map { "recording:${recordingItemKey(it.id)}" }
        DvrLibraryMode.PROBLEMS -> DvrProblemBucket.entries
            .flatMap { problemGroups[it].orEmpty() }
            .map { "recording:${recordingItemKey(it.id)}" }
    }
    val alertShown = recordingsAppError(entries.isEmpty(), connectionUiState, connectionFailure) != null

    LaunchedEffect(observedEntries, observedArchive, observedRecordingNow) {
        // Transfer locally before removing the actual focused node. Keeping only the
        // old key would recreate the node and leave the same focus vacancy.
        if (removalHandoff && initialFocusEnabled &&
            drawerState?.currentValue != androidx.tv.material3.DrawerValue.Open
        ) {
            if (focusHeader()) {
                contentFocusOwned = false
                requestContentFocus = observedKeys.isNotEmpty()
            }
        }
        retainedPreparation = prepared
        archiveNavigation.update(
            reconcileArchiveStack(archiveNavigation.stack, observedArchive, observedRecordingNow),
        )
    }

    LaunchedEffect(
        location,
        itemKeys,
        screenState.requestedScheduleEntryId.value,
        prepared.input,
        observation.dvrSnapshotForDisplay,
        selectedKeys[location],
        requestContentFocus,
        initialFocusEnabled,
        alertShown,
        overlays.detailsEntry,
        overlays.pendingAction,
        overlays.folderMenu,
        overlays.optionsOpen,
        overlays.newTimerSession,
        overlays.scheduleRuleRequest,
    ) {
        if (recordingOverlayOpen(overlays.detailsEntry, overlays.pendingAction, overlays.folderMenu) ||
            overlays.optionsOpen
        ) return@LaunchedEffect
        if (overlays.scheduleSurfaceOpen) return@LaunchedEffect
        val requestedEntry = screenState.requestedScheduleEntryId.value
        // Preparation retains the last projection while the new snapshot is calculated.
        // Only the projection built from the displayed snapshot can judge this target.
        val snapshotReady = prepared.input != null && prepared.input == observation.dvrSnapshotForDisplay
        val unavailableScheduleEntry = snapshotReady && mode == DvrLibraryMode.SCHEDULE && requestedEntry != null &&
            observedLibrary.schedule.none { it.id == requestedEntry && it.state == DvrEntryState.SCHEDULED }
        if (snapshotReady && requestedEntry != null) {
            if (mode == DvrLibraryMode.SCHEDULE) {
                val key = "recording:${recordingItemKey(requestedEntry)}".takeUnless { unavailableScheduleEntry }
                    ?: itemKeys.firstOrNull { it.startsWith("recording:") } ?: itemKeys.firstOrNull()
                scheduleNavigation.update(DepthStack(listOf(DepthFrame(
                    SCHEDULE_ROOT_LEVEL, focusedItemId = key,
                    firstVisibleItemId = key,
                    firstVisibleIndex = itemKeys.indexOf(key).coerceAtLeast(0),
                )), visit = scheduleNavigation.stack.visit + 1))
            }
            screenState.requestedScheduleEntryId.value = null
            if (mode == DvrLibraryMode.SCHEDULE && initialFocusEnabled && (loadingFocusParked || contentFocusOwned)) {
                requestContentFocus = true
            }
        }
        if (itemKeys.isEmpty() && !alertShown) {
            if (unavailableScheduleEntry) selectedKeys.remove(location)
            if (!initialFocusEnabled || (!requestContentFocus && !contentFocusOwned)) return@LaunchedEffect
            if (drawerState?.currentValue == androidx.tv.material3.DrawerValue.Open) return@LaunchedEffect
            if (focusHeader()) {
                requestContentFocus = false
                contentFocusOwned = false
                loadingFocusParked = true
            }
            return@LaunchedEffect
        }
        if (requestedEntry != null && !snapshotReady) return@LaunchedEffect
        if (mode == DvrLibraryMode.PROBLEMS && itemKeys.isNotEmpty() && selectedKeys[location] !in itemKeys) {
            if (itemKeys.isEmpty()) selectedKeys.remove(location) else selectedKeys[location] = itemKeys.first()
            archiveScrollPositions[location] = 0
            screenState.archiveScrollOffsets[location] = 0
            focusRecoveryGeneration++
            if (contentFocusOwned) requestContentFocus = true
            return@LaunchedEffect
        }
        if (!initialFocusEnabled) return@LaunchedEffect
        if (!requestContentFocus && !(alertShown && (loadingFocusParked || contentFocusOwned))) return@LaunchedEffect
        repeat(4) {
            withFrameNanos { }
            if (drawerState?.currentValue == androidx.tv.material3.DrawerValue.Open) return@LaunchedEffect
            // The list may already have restored its row (Back from the player). Requesting the
            // focused group again re-enters it at its first row and skips the list's own onEnter.
            val focused = contentHasFocus || runCatching { contentFocus.requestFocus() }.getOrDefault(false)
            if (focused) {
                requestContentFocus = false
                loadingFocusParked = false
                return@LaunchedEffect
            }
        }
    }
    // The end of a menu or details goes back to the list.
    fun restoreAfterOverlay(): Boolean = runCatching { contentFocus.requestFocus() }.getOrDefault(false)
    LaunchedEffect(
        overlays.detailsEntry,
        overlays.pendingAction,
        overlays.pendingDetailsReturn,
        location,
        focusRecoveryGeneration,
        overlays.folderMenu,
        overlays.optionsOpen,
        overlays.newTimerSession,
        overlays.scheduleRuleRequest,
    ) {
        if (!overlays.pendingDetailsReturn) return@LaunchedEffect
        if (recordingOverlayOpen(overlays.detailsEntry, overlays.pendingAction, overlays.folderMenu) ||
            overlays.optionsOpen
        ) return@LaunchedEffect
        if (overlays.scheduleSurfaceOpen) return@LaunchedEffect
        repeat(4) {
            withFrameNanos { }
            val restored = runCatching {
                if (itemKeys.isEmpty() && !alertShown) focusHeader() else restoreAfterOverlay()
            }.getOrDefault(false)
            if (restored) {
                overlays.pendingDetailsReturn = false
                requestContentFocus = false
                return@LaunchedEffect
            }
        }
    }

    val modeMotion = rememberTabContentMotion(mode) { screenState.mode.value }
    val detailsLayering = at.bernhardberger.tvhplayer.ui.components.LocalBrowseContentLayering.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                if (overlays.detailsEntry != null || overlays.folderMenu != null || overlays.optionsOpen)
                    hideFromAccessibility()
                if (overlays.scheduleSurfaceOpen) hideFromAccessibility()
            }
            .then(if (overlays.scheduleSurfaceOpen) Modifier.focusProperties { canFocus = false } else Modifier)
            .graphicsLayer { alpha = 1f - (detailsLayering?.detailsAlpha() ?: if (overlays.detailsEntry != null && !overlays.showContextMenu && !overlays.showResumeChoice) 1f else 0f) }
            .onPreviewKeyEvent { event ->
                if (event.key == relocatingKey) {
                    if (event.type == KeyEventType.KeyUp) relocatingKey = null
                    return@onPreviewKeyEvent true
                }
                val problemsListOwnsFocus = mode == DvrLibraryMode.PROBLEMS && contentHasFocus && drawerFocus != null
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && problemsListOwnsFocus) {
                    // Like Archive's root Left: target the current rail item before natural search
                    // can enter the drawer at Home and its later restoration effect corrects it.
                    if (drawerFocus?.requestFocus() == true) {
                        contentFocusOwned = false
                        requestContentFocus = false
                    }
                    return@onPreviewKeyEvent true
                }
                val firstProblemRow = mode == DvrLibraryMode.PROBLEMS && contentHasFocus &&
                    selectedKeys[location] == itemKeys.firstOrNull()
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionUp &&
                    firstProblemRow
                ) {
                    relocatingKey = event.key
                    if (modeFocus.requestFocus()) {
                        contentFocusOwned = false
                        requestContentFocus = false
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
            // Lists run to the bottom edge and fade there; other content keeps the safe area.
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        RecordingsHeader(
            mode = mode,
            modeFocus = modeFocus,
            problemCount = problemGroups.values.sumOf { it.size },
            diskSpace = observation.dvrDiskSpaceState,
            startPadding = startPadding,
            endPadding = endPadding,
            onFocused = {
                modeMotion.select(it, DvrLibraryMode.entries)
                if (mode != it) {
                    screenState.requestedScheduleEntryId.value = null
                    contentFocusOwned = false
                    requestContentFocus = false
                }
                mode = it
            },
            onClick = {
                screenState.requestedScheduleEntryId.value = null
                modeMotion.select(it, DvrLibraryMode.entries)
                mode = it
                requestContentFocus = true
            },
            onMoveToContent = { requestContentFocus = true },
            onNavigateTabs = {
                // A deliberate header move gives the viewer ownership, not the loading handoff.
                screenState.requestedScheduleEntryId.value = null
                loadingFocusParked = false
                contentFocusOwned = false
            },
        )
        TabContent(
            motion = modeMotion,
            selectedKey = mode,
            state = {
                RecordingsTabBody(
                    mode, location, entries.isEmpty(), connectionUiState, connectionFailure,
                    observation, channelsById, archive, fullArchive,
                    scheduleGroups, problemGroups, selectedKeys[location],
                    archiveScrollPositions[location] ?: 0,
                    screenState.archiveScrollOffsets[location] ?: 0,
                    focusRecoveryGeneration,
                )
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { frame, owner ->
        val mode = frame.mode
        val location = frame.location
        val observation = frame.observation
        val currentSession = observation.currentSession
        val channelsById = frame.channelsById
        val scheduleGroups = frame.schedule
        val problemGroups = frame.problems
        val scheduleReady = mode == DvrLibraryMode.SCHEDULE && automaticRecordings.available &&
            recordingsAppError(true, frame.connection, frame.failure) == null
        // Every tab's content starts at y80, as the channel list's first row does.
        Spacer(Modifier.height(TvSpacing12))

        if (frame.empty && recordingsLoading(frame.connection, frame.failure)) {
            // In the Archive column's place, running to the bottom edge as its list does.
            RecordingsSkeleton(Modifier.padding(start = startPadding + TvBrowseLeadingInset))
        } else if (frame.empty && !scheduleReady) {
            RecordingsEmptyState(
                connectionUiState = frame.connection,
                connectionFailure = frame.failure,
                onAction = { action ->
                    if (owner.isCurrent) {
                        when (action) {
                            AppErrorAction.RETRY -> onRetry()
                            AppErrorAction.CONNECTION_SETTINGS -> onOpenConnectionSettings()
                            AppErrorAction.CLOSE -> Unit
                        }
                    }
                },
                actionFocus = if (owner.isCurrent) contentFocus else remember { FocusRequester() },
                upFocus = modeFocus,
                modifier = Modifier
                    .padding(start = startPadding, end = endPadding, bottom = contentPadding.calculateBottomPadding())
                    .onFocusChanged {
                        if (owner.isCurrent) {
                            contentHasFocus = it.hasFocus
                            contentFocusOwned = it.hasFocus
                        }
                    },
            )
        } else ProvideArtworkSession(currentSession, observation) {
            // A frame shows its pictures with the session of the observation it was built from.
            when (mode) {
                DvrLibraryMode.ARCHIVE -> Box(Modifier.fillMaxSize()) { key(chosenView) { ArchiveDepthContent(
                    root = if (owner.isCurrent) archive else frame.archive,
                    recordingNow = if (owner.isCurrent) recordingNow else {
                        remember(frame.archive, chosenView) { frame.archive.recordingNowEntries(chosenView) }
                    },
                    nowSeconds = nowSecProvider,
                    navigation = archiveNavigation,
                    contentPadding = PaddingValues(
                        start = startPadding + TvBrowseLeadingInset,
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                    isCurrent = owner.isCurrent,
                    initialFocusEnabled = owner.isCurrent && initialFocusEnabled &&
                        overlays.detailsEntry == null && overlays.folderMenu == null && !overlays.optionsOpen &&
                        (contentFocusOwned || requestContentFocus),
                    backEnabled = backEnabled && overlays.detailsEntry == null && overlays.folderMenu == null &&
                        !overlays.optionsOpen && contentFocusOwned,
                    grouping = chosenGrouping,
                    sort = chosenView.sort,
                    fullRoot = if (owner.isCurrent) fullArchive else frame.fullArchive,
                    show = chosenView.show,
                    onFirstRowUp = {
                        if (owner.isCurrent && focusHeader()) contentFocusOwned = false
                    },
                    contentFocus = contentFocus,
                    onDrawerFocus = drawerFocus?.let { focus -> {
                        if (owner.isCurrent && focus.requestFocus()) {
                            contentFocusOwned = false
                            requestContentFocus = false
                        }
                    } },
                    onOpenRecording = {
                        if (owner.isCurrent) {
                            openRecording(it)
                        }
                    },
                    onOpenContextMenu = {
                        if (owner.isCurrent) {
                            contentFocusOwned = false
                            overlays.detailsInitialAction = null
                            overlays.detailsEntry = it
                            overlays.detailsObservation = observation
                            overlays.showContextMenu = true
                        }
                    },
                    // Channel and genre groups are not server folders; they have no folder actions.
                    onOpenFolderMenu = if (chosenGrouping != DvrArchiveGrouping.FOLDERS) null else { folder ->
                        val session = currentSession
                        val canModify = (observation.sessionState as? SessionState.Ready)
                            ?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
                        // Without anything to change there is no menu, as for a row without one.
                        if (owner.isCurrent && session != null &&
                            folderContextActions(folder, observation::recordingPlaybackAdmission, canModify).isNotEmpty()
                        ) {
                            contentFocusOwned = false
                            overlays.folderMenu = FolderMenuTarget(folder.path, session)
                        }
                    },
                    piconForEntry = { it.channelId?.let(channelsById::get)?.icon },
                    admissionForEntry = { observation.recordingPlaybackAdmission(it) },
                    modifier = Modifier.fillMaxSize().onFocusChanged {
                        if (owner.isCurrent) {
                            archiveHasFocus = it.hasFocus
                            contentHasFocus = it.hasFocus && !optionsHasFocus
                            if (contentHasFocus) contentFocusOwned = true
                        }
                    },
                    // The options belong to Browse's root and travel with its column; below it they can't take focus.
                    rootHeadingEnd = { ArchiveViewOptionsButton(
                        view = chosenView,
                        focusable = owner.isCurrent && !archiveNavigation.stack.canPop,
                        focusRequester = if (owner.isCurrent) overlays.optionsFocus else remember { FocusRequester() },
                        upFocus = modeFocus,
                        navigationFocus = drawerFocus,
                        onOpen = {
                            if (owner.isCurrent) {
                                contentFocusOwned = false
                                overlays.optionsOpen = true
                            }
                        },
                        // Leaving the button for the list keeps the archive focused, so its callback stays quiet.
                        onDown = {
                            val moved = runCatching { contentFocus.requestFocus() }.getOrDefault(false)
                            if (moved) contentFocusOwned = true
                        },
                        onFocusChanged = {
                            if (owner.isCurrent) {
                                optionsHasFocus = it
                                contentHasFocus = archiveHasFocus && !it
                                if (it) {
                                    screenState.requestedScheduleEntryId.value = null
                                    loadingFocusParked = false
                                    contentFocusOwned = false
                                    requestContentFocus = false
                                }
                            }
                        },
                    ) },
                ) } }
                DvrLibraryMode.SCHEDULE -> Box(
                    modifier = Modifier
                        // Archive's columns: the list in its first, the focused recording where it shows its preview.
                        .padding(end = endPadding)
                        .fillMaxSize()
                        .onFocusChanged {
                            if (!owner.isCurrent) return@onFocusChanged
                            contentHasFocus = it.hasFocus
                            if (it.hasFocus) contentFocusOwned = true
                        },
                ) {
                    ScheduleDepthContent(
                        groups = scheduleGroups,
                        automatic = automaticRecordings,
                        canModify = canModifyRecordings,
                        canAdd = observation.dvrState is DvrRepositoryState.Current,
                        onNewTimer = {
                            val latest = latestObservationProvider()
                            val ready = latest.sessionState as? SessionState.Ready
                            if (owner.isCurrent && latest.currentSession == currentSession) {
                                if (ready?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED &&
                                    latest.dvrState is DvrRepositoryState.Current
                                ) {
                                    contentFocusOwned = false
                                    overlays.newTimerSession = latest.currentSession
                                }
                            }
                        },
                        channels = channelsById,
                        onNewAutomatic = { if (owner.isCurrent) openScheduleRule(RuleSurface.NEW) },
                        onEditRule = { if (owner.isCurrent) openScheduleRule(RuleSurface.EDIT, it) },
                        onRuleMenu = { if (owner.isCurrent) openScheduleRule(RuleSurface.MENU, it) },
                        navigation = scheduleNavigation,
                        contentPadding = PaddingValues(
                            start = startPadding + TvBrowseLeadingInset,
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
                        isCurrent = owner.isCurrent,
                        initialFocusEnabled = owner.isCurrent && initialFocusEnabled &&
                            overlays.detailsEntry == null && !overlays.scheduleSurfaceOpen &&
                            (contentFocusOwned || requestContentFocus),
                        backEnabled = backEnabled && overlays.detailsEntry == null &&
                            !overlays.scheduleSurfaceOpen && contentFocusOwned,
                        contentFocus = contentFocus,
                        onFirstRowUp = {
                            if (owner.isCurrent && focusHeader()) contentFocusOwned = false
                        },
                        onDrawerFocus = drawerFocus?.let { focus -> {
                            if (owner.isCurrent && focus.requestFocus()) {
                                contentFocusOwned = false
                                requestContentFocus = false
                            }
                        } },
                        onOpenRecording = {
                            if (!owner.isCurrent) return@ScheduleDepthContent
                            openRecording(it, runningOpensDetails = true)
                        },
                        onOpenContextMenu = {
                            if (owner.isCurrent) {
                                contentFocusOwned = false
                                overlays.detailsInitialAction = null
                                overlays.detailsEntry = it
                                overlays.detailsObservation = observation
                                overlays.showContextMenu = true
                            }
                        },
                        piconForEntry = { entry ->
                            entry.channelId?.let(channelsById::get)?.icon
                        },
                        admissionForEntry = { observation.recordingPlaybackAdmission(it) },
                        ruleForEntry = { recordingRule(observation.recordingRuleKind(it)) },
                    )
                }
                DvrLibraryMode.PROBLEMS -> Box(
                    modifier = Modifier
                        .padding(start = startPadding + TvBrowseLeadingInset, end = endPadding)
                        .fillMaxSize()
                        .onFocusChanged {
                            if (!owner.isCurrent) return@onFocusChanged
                            contentHasFocus = it.hasFocus
                            if (it.hasFocus) contentFocusOwned = true
                        },
                ) {
                    key(location, frame.recoveryGeneration) {
                        val generation = frame.recoveryGeneration
                        RecordingProblems(
                            groups = problemGroups,
                            selectedKey = frame.selectedKey,
                            selectedFocus = contentFocus,
                            onFocused = { if (owner.isCurrent) selectedKeys[location] = it },
                            onOpen = {
                                if (!owner.isCurrent) return@RecordingProblems
                                openRecording(it)
                            },
                            onOpenContextMenu = {
                                if (owner.isCurrent) {
                                    contentFocusOwned = false
                                    overlays.detailsInitialAction = null
                                    overlays.detailsEntry = it
                                    overlays.detailsObservation = observation
                                    overlays.showContextMenu = true
                                }
                            },
                            piconForEntry = { entry ->
                                entry.channelId?.let(channelsById::get)?.icon
                            },
                            admissionForEntry = { observation.recordingPlaybackAdmission(it) },
                            paneBottom = contentPadding.calculateBottomPadding(),
                            initialScrollIndex = frame.scrollIndex,
                            onScrollChanged = {
                                if (owner.isCurrent && generation == focusRecoveryGeneration) {
                                    archiveScrollPositions[location] = it
                                }
                            },
                        )
                    }
                }
            }
        }
        }
    }

    @Composable
    fun RecordingDetails() {
    var ruleEditorEntry by remember { mutableStateOf<DvrEntry?>(null) }
    var detailsRuleEditor by remember { mutableStateOf(false) }
    var ruleMutationBusy by remember { mutableStateOf(false) }
    val latestRuleNow by rememberUpdatedState(nowSecProvider)
    // Refresh metadata only within the opening session. Never substitute a colliding ID
    // from a later session, including while a captured confirmation is pending.
    val selectedCapability = overlays.detailsObservation?.currentSession
        ?.takeIf { observation.currentSession === it }
    val observed = overlays.detailsEntry?.let { entry ->
        if (selectedCapability != null && observation.dvrState is DvrRepositoryState.Current) {
            observation.dvrEntry(entry.id)?.takeUnless(::recordingWasRemoved)
        } else entry
    }
    val opened = observed ?: (ruleEditorEntry ?: overlays.detailsEntry.takeIf { detailsRuleEditor })
        .takeIf { selectedCapability != null }
    LaunchedEffect(selectedCapability) { if (selectedCapability == null) ruleEditorEntry = null }
    // The SDK admits the opened entry with the observation it was read from; without its session nothing plays.
    val openedAdmission = opened?.let { entry ->
        selectedCapability?.let {
            (if (observation.dvrState is DvrRepositoryState.Current) observation else overlays.detailsObservation)
                ?.recordingPlaybackAdmission(entry)
        }
    }
    fun markRecording(action: BrowseContextAction) {
        val openingSession = selectedCapability ?: return
        val entryId = opened?.id ?: return
        scope.launch {
            val current = latestObservationProvider()
            if (current.currentSession !== openingSession) return@launch
            val entry = current.dvrEntry(entryId) ?: return@launch
            val canModify = (current.sessionState as? SessionState.Ready)
                ?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
            if (recordingWatchStateAction(entry, current.recordingPlaybackAdmission(entry), canModify) != action) return@launch
            val noticeContext = notices.context()
            when (val result = dvrMutationActions.updateWatchState(openingSession, entryId, action == BrowseContextAction.MARK_WATCHED)) {
                DvrProgressResult.Accepted -> Unit
                is DvrProgressResult.Failed -> {
                    val feedback = result.toDvrMutationFeedback()
                    notices.post(Notice.DvrActionFailed(DvrMutationKind.UPDATE, feedback), noticeContext)
                }
            }
        }
    }
    fun ruleAction(action: BrowseContextAction) {
        val capability = selectedCapability ?: return
        val current = latestObservationProvider()
        val target = opened?.id?.let(current::dvrEntry)
        val ready = !ruleMutationBusy && ruleEditorEntry == null && current.currentSession === capability
        val writable = (current.sessionState as? SessionState.Ready)
            ?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
        if (!ready || !writable || target == null) return
        if (action == BrowseContextAction.EDIT_RULE) {
            val ruleId = target.autorecRuleId?.value ?: target.timerecRuleId?.value
            val kind = current.recordingRuleKind(target)
            val pending = ruleId != null && kind != null && screenState.scheduleState.hasPending(ruleId, kind)
            if (!pending && action in recordingRuleActions(target, writable, current.recordingRuleAvailable(target)))
                ruleEditorEntry = target
        } else if (action in recordingRuleActions(target, true, current.recordingRuleAvailable(target))) {
            ruleMutationBusy = true
            scope.launch {
                try {
                    dvrMutationActions.changeScheduledRecordingEnabled(
                        target.id, capability, latestObservationProvider, action, notices)
                } finally {
                    ruleMutationBusy = false
                }
            }
        }
    }
    LaunchedEffect(overlays.detailsEntry, opened) {
        if (overlays.detailsEntry != null && opened == null) {
            overlays.showResumeChoice = false
            overlays.pendingAction = null
            overlays.pendingMutation = null
            overlays.detailsEntry = null
            overlays.detailsObservation = null
            overlays.detailsInitialAction = null
            overlays.pendingDetailsReturn = true
        }
    }
    if (opened != null) {
        val nowSec = at.bernhardberger.tvhplayer.ui.common.rememberDetailsEpochSeconds(nowSecProvider)
        @Composable
        fun ResumeChoice() {
            val closeBehindPlayer = rememberCloseBehindPlayer()
            val channel = opened.channelId?.let(channelsById::get)
            val closeResume = {
                overlays.showResumeChoice = false
                overlays.detailsEntry = null
                overlays.detailsObservation = null
                overlays.pendingDetailsReturn = true
            }
            val play: (RecordingPlaybackStart) -> Unit = { intent ->
                selectedCapability?.let { capability ->
                    closeBehindPlayer(closeResume)
                    onPlayRecording(RecordingPlaybackSelection(capability, opened.id), intent)
                }
            }
            val watchLive: (() -> Unit)? = if (recordingIsRunning(opened) && selectedCapability != null &&
                channel != null && onWatchChannel != null) ({
                if (latestObservationProvider().currentSession === selectedCapability) {
                    closeBehindPlayer(closeResume)
                    onWatchChannel(channel.id, channel.name.orEmpty())
                }
            }) else null
            val choice = watchLive != null || at.bernhardberger.tvhplayer.core.recordingOpenAction(openedAdmission) ==
                at.bernhardberger.tvhplayer.core.RecordingOpenAction.RESUME_CHOICE
            LaunchedEffect(choice) {
                if (!choice) closeResume()
            }
            if (choice) RecordingPlaybackChoice(opened, openedAdmission, observation, channel, nowSec,
                onPlay = play, onClose = closeResume, onWatchLive = watchLive, backEnabled = backEnabled)
        }
        if (overlays.showResumeChoice) {
            ResumeChoice()
        } else {
        val closeBehindPlayer = rememberCloseBehindPlayer()
        val entryPending = at.bernhardberger.tvhplayer.ui.rememberPlayerEntryPending()
        // The menu and details return to the playing recording the same way.
        val continueWatching = {
            val leaveBehindPlayer = {
                closeBehindPlayer {
                    overlays.showContextMenu = false
                    overlays.detailsEntry = null
                    overlays.detailsObservation = null
                    overlays.detailsInitialAction = null
                    requestContentFocus = true
                }
            }
            // Register only after warm validation, but before the request makes the handoff pending.
            val returned = onReturnToRecording(opened.id, leaveBehindPlayer)
            // A player that went since composition starts again; the menu or details stays until it shows.
            if (!returned) openRecording(opened, returnIfPlaying = false, beforePlay = leaveBehindPlayer)
        }
        val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
        var keepingUntil by remember { mutableStateOf(false) }
        var keepUntilBusy by remember { mutableStateOf(false) }
        val closing = !visibility.targetState
        BackHandler(enabled = backEnabled && closing) { }
        LaunchedEffect(visibility.isIdle, visibility.currentState, closing) {
            if (closing && visibility.isIdle && !visibility.currentState) {
                val restoredBeforeDismissal = restoreAfterOverlay()
                overlays.pendingDetailsReturn = !restoredBeforeDismissal
                overlays.detailsEntry = null
                overlays.detailsObservation = null
                overlays.detailsInitialAction = null
            }
        }
        AnimatedVisibility(visibleState = visibility, modifier = Modifier.browseDetailsViewport(),
            enter = EnterTransition.None, exit = ExitTransition.None) {
        val detailAlpha = animateShown(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard),
            tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard), "recording-details-layer")
        at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame(leaving = closing,
            modifier = Modifier.onPreviewKeyEvent { closing || entryPending() }) {
        // Match ProgrammeDetailsHost: keep both pages drawn while details owns its alpha.
        AnimatedContent(targetState = overlays.showContextMenu, transitionSpec = {
            (if (targetState) fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard)) else EnterTransition.None)
                .togetherWith(if (initialState) fadeOut(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard)) else ExitTransition.None)
                .using(null)
        }, label = "recording-menu-details") { menu ->
        at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame(leaving = closing || menu != overlays.showContextMenu) {
        if (menu) {
        AnimatedVisibility(visible = !closing && !menuConsumed,
            enter = EnterTransition.None, exit = ExitTransition.None) {
        androidx.compose.runtime.CompositionLocalProvider(
            at.bernhardberger.tvhplayer.ui.player.LocalPlayerPanelMotion provides this) {
        at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame(
            leaving = overlays.pendingAction != null || ruleEditorEntry != null) {
        BrowseContextMenu(
            backEnabled = backEnabled && !closing && menu == overlays.showContextMenu &&
                overlays.pendingAction == null && ruleEditorEntry == null && !ruleMutationBusy && !keepUntilBusy,
            title = opened.title.orEmpty(),
            channel = opened.channelId?.let(channelsById::get)?.let {
                at.bernhardberger.tvhplayer.ui.components.channelTitleText(it.number, it.name.orEmpty())
            } ?: opened.channelName.orEmpty(),
            episode = programmeEpisodeLine(opened.episode, opened.subtitle, androidx.compose.ui.platform.LocalContext.current.resources),
            time = recordingTimeRange(opened.start?.epochSeconds, opened.stop?.epochSeconds,
                androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
            summary = opened.summary?.takeIf(String::isNotBlank) ?: opened.description.orEmpty(),
            recordingState = opened.state,
            recordingRule = recordingRule(observation.recordingRuleKind(opened)),
            recordingKind = observation.recordingRuleKind(opened),
            recordingEnabled = opened.enabled,
            resumePositionMs = at.bernhardberger.tvhplayer.core.offeredResumePosition(openedAdmission)?.inWholeMilliseconds,
            keepUntilOptions = if (keepingUntil) at.bernhardberger.tvhplayer.core.recordingKeepUntilOptions(
                opened, nowSec, java.time.ZoneId.systemDefault()) else null,
            keepUntilBusy = keepUntilBusy,
            keepUntilSupport = at.bernhardberger.tvhplayer.ui.common.formatRecordingKeepUntil(opened, nowSec,
                androidx.compose.ui.platform.LocalContext.current.resources,
                androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
            nowSec = nowSec,
            onKeepUntilBack = { keepingUntil = false },
            onKeepUntilChoose = { option ->
                if (!keepUntilBusy) {
                    keepUntilBusy = true
                    scope.launch {
                        try {
                            if (dvrMutationActions.chooseRecordingRemoval(opened.id, selectedCapability,
                                    latestObservationProvider(), option, notices)) visibility.targetState = false
                        } finally { keepUntilBusy = false }
                    }
                }
            },
            playbackIndicator = watchingIndicator(opened.id),
            actions = recordingContextActions(opened, canModifyRecordings = selectedCapability != null &&
                (observation.sessionState as? at.bernhardberger.tvheadend.sdk.core.SessionState.Ready)
                    ?.capabilities?.dvrWrite == at.bernhardberger.tvheadend.sdk.core.CapabilityAccess.ALLOWED,
                admission = openedAdmission,
                ruleAvailable = observation.recordingRuleAvailable(opened),
                canWatchChannel = selectedCapability != null && onWatchChannel != null && opened.channelId?.let(channelsById::get) != null,
                playing = selectedCapability != null && opened.id ==
                    at.bernhardberger.tvhplayer.ui.warmRecordingId(
                        at.bernhardberger.tvhplayer.ui.components.LocalWatchingTarget.current,
                        at.bernhardberger.tvhplayer.ui.components.LocalRecordingPlaybackSelection.current,
                        observation.currentSession)),
            onClose = { visibility.targetState = false },
            onAction = ruleDispatch@{ action ->
                if (action == BrowseContextAction.KEEP_UNTIL) {
                    if (latestObservationProvider().keepUntilEntry(opened.id, selectedCapability) != null)
                        keepingUntil = true
                    else visibility.targetState = false
                    return@ruleDispatch
                }
                if (action in listOf(BrowseContextAction.EDIT_RULE, BrowseContextAction.SKIP,
                        BrowseContextAction.UNSKIP)) {
                    ruleAction(action)
                    return@ruleDispatch
                }
                val playing = selectedCapability?.takeIf { action in listOf(BrowseContextAction.WATCH,
                    BrowseContextAction.PLAY, BrowseContextAction.RESUME, BrowseContextAction.START_OVER,
                    BrowseContextAction.CONTINUE_WATCHING, BrowseContextAction.WATCH_LIVE) }
                // Playback keeps the menu up until the player has replaced browse.
                if (playing == null) {
                    menuConsumed = action != BrowseContextAction.DETAILS
                    overlays.showContextMenu = action in listOf(BrowseContextAction.STOP, BrowseContextAction.CANCEL,
                        BrowseContextAction.DELETE, BrowseContextAction.REMOVE_ENTRY)
                }
                when (action) {
                    BrowseContextAction.WATCH_LIVE -> opened.channelId?.let(channelsById::get)?.let { channel ->
                        if (playing != null && latestObservationProvider().currentSession === playing && onWatchChannel != null) {
                            closeBehindPlayer {
                                overlays.showContextMenu = false
                                overlays.detailsEntry = null
                                overlays.detailsObservation = null
                                overlays.detailsInitialAction = null
                                requestContentFocus = true
                            }
                            onWatchChannel(channel.id, channel.name.orEmpty())
                        }
                    }
                    BrowseContextAction.CONTINUE_WATCHING -> playing?.let { continueWatching() }
                    BrowseContextAction.WATCH, BrowseContextAction.PLAY,
                    BrowseContextAction.RESUME, BrowseContextAction.START_OVER -> playing?.let {
                        closeBehindPlayer {
                            overlays.showContextMenu = false
                            overlays.detailsEntry = null
                            overlays.detailsObservation = null
                            overlays.detailsInitialAction = null
                            requestContentFocus = true
                        }
                        onPlayRecording(RecordingPlaybackSelection(it, opened.id),
                            if (action == BrowseContextAction.RESUME || action == BrowseContextAction.WATCH)
                                RecordingPlaybackStart.RESUME else RecordingPlaybackStart.START_OVER)
                    }
                    BrowseContextAction.STOP -> {
                        overlays.detailsInitialAction = RecordingDetailsAction.STOP
                        overlays.pendingMutation = selectedCapability?.let { DvrMutationAction.Stop(it, opened.id) }
                        overlays.pendingAction = PendingRecordingAction.STOP
                    }
                    BrowseContextAction.CANCEL -> {
                        overlays.detailsInitialAction = RecordingDetailsAction.CANCEL
                        overlays.pendingMutation = selectedCapability?.let { DvrMutationAction.Cancel(it, opened.id) }
                        overlays.pendingAction = PendingRecordingAction.CANCEL
                    }
                    BrowseContextAction.DELETE, BrowseContextAction.REMOVE_ENTRY -> {
                        overlays.detailsInitialAction = RecordingDetailsAction.DELETE
                        overlays.pendingMutation = selectedCapability?.let { DvrMutationAction.Delete(it, opened.id) }
                        overlays.pendingAction = PendingRecordingAction.DELETE
                    }
                    BrowseContextAction.MARK_WATCHED, BrowseContextAction.MARK_UNWATCHED -> {
                        visibility.targetState = false
                        markRecording(action)
                    }
                    else -> Unit
                }
            },
            artwork = {
                val artwork = recordingArtwork(opened)?.takeIf { selectedCapability != null }
                ProvideArtworkSession(selectedCapability, observation) {
                    val viewerFraction = recordingViewerPosition(opened.id)?.fraction
                    val running = recordingIsRunning(opened)
                    ProgrammeArtwork(artwork, opened.channelId?.let(channelsById::get)?.icon,
                        viewerFraction ?: recordingElapsedProgress(opened, nowSec).takeIf { running },
                        Modifier.fillMaxSize().testTag(if (artwork != null)
                            "browse-context-artwork" else "browse-context-picon"),
                        plateModifier = Modifier.testTag("browse-context-plate"),
                        progressModifier = Modifier.testTag("browse-context-progress"), progressColor = progressColor(viewerFraction != null, running))
                }
            },
        )
        }
        }
        }
        } else {
        val menuAlpha = animateShown(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard),
            tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard), "recording-details-from-menu")
        val alpha = remember(detailAlpha, menuAlpha) { derivedStateOf { detailAlpha.value * menuAlpha.value } }
        // Details show their pictures with the session they were opened with.
        RecordingDetailsHost(
            onRuleEditorActive = { detailsRuleEditor = it },
            onShowGuide = onShowGuide,
            recording = opened,
            openingObservation = requireNotNull(overlays.detailsObservation),
            observation = observation,
            currentObservation = currentObservation,
            nowSec = nowSec,
            mutations = dvrMutationActions,
            notices = notices,
            mutationScope = scope,
            initialAction = when (overlays.detailsInitialAction) {
                RecordingDetailsAction.CANCEL,
                RecordingDetailsAction.STOP -> when (opened.state) {
                    DvrEntryState.SCHEDULED -> if (opened.isRuleRecording)
                        if (opened.enabled == false) BrowseContextAction.UNSKIP else BrowseContextAction.SKIP
                        else BrowseContextAction.CANCEL
                    DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR -> BrowseContextAction.STOP
                    else -> null
                }
                RecordingDetailsAction.RESUME -> BrowseContextAction.RESUME
                RecordingDetailsAction.BEGINNING -> BrowseContextAction.START_OVER
                RecordingDetailsAction.PLAY -> BrowseContextAction.PLAY
                RecordingDetailsAction.DELETE ->
                    if (deleteRemovesEntryOnly(opened)) BrowseContextAction.REMOVE_ENTRY else BrowseContextAction.DELETE
                null -> null
            },
            backEnabled = backEnabled && !closing && menu == overlays.showContextMenu && overlays.pendingAction == null,
            visibilityAlpha = alpha,
            onPlayRecording = { selection, start ->
                closeBehindPlayer {
                    overlays.detailsEntry = null
                    overlays.detailsObservation = null
                    overlays.detailsInitialAction = null
                    requestContentFocus = true
                }
                onPlayRecording(selection, start)
            },
            onWatchChannel = onWatchChannel?.let { watch -> { id, name ->
                closeBehindPlayer {
                    overlays.detailsEntry = null
                    overlays.detailsObservation = null
                    requestContentFocus = true
                }
                watch(id, name)
            } },
            onContinueWatching = continueWatching,
            onClose = { visibility.targetState = false },
        )
        }
        }
        }
        }
        }
        }
    }

    if (ruleEditorEntry != null && selectedCapability != null) {
        val editorSession: TvheadendSession = koinInject()
        RecordingRuleEditorHost(requireNotNull(ruleEditorEntry), selectedCapability, observation,
            editorSession, dvrMutationActions, notices,
            now = { kotlin.time.Instant.fromEpochSeconds(latestRuleNow()) },
            onClose = { ruleEditorEntry = null })
    }
    val action = overlays.pendingAction
    val target = opened
    val closeConsumedMenu = {
        if (overlays.showContextMenu && menuConsumed) {
            // Match details dismissal: restore the mounted card before removing the dialog.
            overlays.pendingDetailsReturn = !restoreAfterOverlay()
            overlays.showContextMenu = false
            overlays.detailsEntry = null
            overlays.detailsObservation = null
            overlays.detailsInitialAction = null
        }
    }
    val mutationStateCurrent = overlays.pendingMutation?.recordingStateIsCurrent(observation) == true
    LaunchedEffect(action, selectedCapability, mutationStateCurrent) {
        if (action != null && (selectedCapability == null || !mutationStateCurrent)) {
            overlays.pendingAction = null
            overlays.pendingMutation = null
            // The dismissed dialog must not restore focus to an action whose authority expired.
            if (selectedCapability == null) overlays.detailsInitialAction = null
            closeConsumedMenu()
        }
    }
    if (action != null && target != null && selectedCapability != null && mutationStateCurrent) {
        RecordingConfirmationDialog(
            visibility = confirmationVisibility,
            action = action,
            entry = target,
            channel = target.channelId?.let(channelsById::get),
            nowSec = at.bernhardberger.tvhplayer.ui.common.rememberDetailsEpochSeconds(nowSecProvider),
            currentSession = selectedCapability,
            observation = observation,
            backEnabled = backEnabled,
            onDismiss = {
                overlays.pendingAction = null
                overlays.pendingMutation = null
                closeConsumedMenu()
            },
            onConfirm = {
                overlays.pendingAction = null
                val mutation = overlays.pendingMutation
                overlays.pendingMutation = null
                val mutationObservation = overlays.detailsObservation
                closeConsumedMenu()
                scope.launch {
                    val latestObservation = latestObservationProvider()
                    if (mutation == null ||
                        mutationObservation?.currentSession !== latestObservation.currentSession ||
                        !mutation.recordingStateIsCurrent(latestObservation)
                    ) return@launch
                    val noticeContext = notices.context()
                    val result = dvrMutationActions.execute(mutation)
                    notices.postDvrFailure(mutation, result, noticeContext)
                }
            },
        )
    }
    }
    RecordingDetails()
    @Composable
    fun ScheduleSurfaces() {
        val scheduleChannels = remember(channelsById) { channelsById.values.toList() }
        ScheduleRuleActions(
            overlays.scheduleRuleRequest,
            onChange = {
                overlays.scheduleRuleRequest = it
                if (it == null) overlays.pendingDetailsReturn = true
            },
            published = observation,
            observation = { latestObservationProvider() },
            isCurrent = screenState.mode.value == DvrLibraryMode.SCHEDULE,
            channels = scheduleChannels, actions = dvrMutationActions, notices = notices,
            now = { kotlin.time.Instant.fromEpochSeconds(nowSecProvider()) },
            state = screenState.scheduleState,
        )
        overlays.newTimerSession?.let { openingSession ->
            val editorSession: TvheadendSession = koinInject()
            TimerRecordingEditorHost(
                openingSession, editorSession.observation, dvrMutationActions, notices,
                now = { kotlin.time.Instant.fromEpochSeconds(nowSecProvider()) },
                onClose = {
                    overlays.newTimerSession = null
                    overlays.pendingDetailsReturn = true
                },
            )
        }
    }
    ScheduleSurfaces()
    @Composable
    fun FolderMenu() {
    overlays.folderMenu?.let { menu ->
        // The menu acts with the session it opened in, on the folder as it is now.
        val session = menu.session.takeIf { observation.currentSession === it }
        val folder = archive.folderAt(menu.path)?.takeIf { menu.path.isNotEmpty() }
        val canModify = session != null && (observation.sessionState as? SessionState.Ready)
            ?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
        val actions = folder?.let { folderContextActions(it, observation::recordingPlaybackAdmission, canModify) }.orEmpty()
        LaunchedEffect(actions.isEmpty()) { if (actions.isEmpty()) closeFolderMenu() }
        if (folder == null || session == null || actions.isEmpty()) return@let
        val summary = remember(folder) { summarizeDvrFolder(folder) }
        val watched = remember(folder) {
            folder.watchedCount { at.bernhardberger.tvhplayer.core.offeredResumePosition(observation.recordingPlaybackAdmission(it)) }
        }
        val context = LocalContext.current
        BrowseContextMenu(
            title = folder.name,
            channel = listOfNotNull(
                pluralStringResource(R.plurals.recordings_folder_recording_count, summary.recordingCount, summary.recordingCount),
                watched.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.recordings_folder_watched_count, it, it) },
            ).joinToString(" • "),
            episode = summary.oldestStart?.let { oldest ->
                formatDateRange(oldest, summary.newestStart ?: oldest)
            }.orEmpty(),
            time = summary.totalSizeBytes.takeIf { it > 0 }?.let { formatFileSize(it, context) }.orEmpty(),
            actions = actions,
            backEnabled = backEnabled && overlays.folderDelete == null,
            onClose = ::closeFolderMenu,
            onAction = { action ->
                val targets = folderActionTargets(folder, action, observation::recordingPlaybackAdmission, canModify)
                when (action) {
                    BrowseContextAction.DELETE_WATCHED, BrowseContextAction.DELETE_ALL -> overlays.folderDelete = FolderDeleteTarget(
                        action = action,
                        name = folder.name,
                        recordingIds = targets.map { it.id },
                        sizeBytes = targets.sumOf { entry -> entry.files.orEmpty().sumOf { it.sizeBytes ?: 0L } },
                        keepsRunning = folder.runningRecordings().isNotEmpty(),
                    )
                    else -> {
                        closeFolderMenu()
                        runFolderAction(action, session, targets.map { it.id })
                    }
                }
            },
            artwork = {
                Box(
                    Modifier.fillMaxSize().clip(MaterialTheme.shapes.small)
                        .background(TvSurfaceColors.containerLowest).testTag("browse-context-folder"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(48.dp))
                }
            },
        )
        overlays.folderDelete?.let { delete ->
            val count = delete.recordingIds.size
            val size = delete.sizeBytes.takeIf { it > 0 }?.let { formatFileSize(it, context) }
            val countLabel = pluralStringResource(R.plurals.recordings_folder_recording_count, count, count)
            val detail = size?.let { stringResource(R.string.server_detail_separator, countLabel, it) } ?: countLabel
            val message = buildString {
                append(stringResource(R.string.delete_folder_confirm_body))
                if (delete.keepsRunning) append(' ').append(stringResource(R.string.delete_folder_running_kept))
            }
            ConfirmationDialog(
                question = stringResource(if (delete.action == BrowseContextAction.DELETE_ALL)
                    R.string.delete_folder_all_confirm_question else R.string.delete_folder_watched_confirm_question),
                subject = ConfirmationSubject.Folder(delete.name, detail),
                sentence = message,
                safe = ConfirmationChoice(stringResource(R.string.keep), R.drawable.ic_check, "folder-delete-back"),
                action = ConfirmationChoice(stringResource(delete.action.label(watchChannel = false)),
                    R.drawable.ic_delete_outlined, "folder-delete-confirm"),
                onConfirm = {
                    closeFolderMenu()
                    runFolderAction(delete.action, session, delete.recordingIds)
                },
                onDismiss = ::closeFolderMenu,
                backEnabled = backEnabled,
            )
        }
    }
    }
    FolderMenu()
    if (overlays.optionsOpen) {
        ArchiveViewOptionsPanel(
            current = chosenView,
            onChoose = { choice ->
                if (choice != chosenView) {
                    val stack = archiveNavigation.stack
                    archiveNavigation.update(
                        if (choice.grouping != chosenView.grouping) {
                            DepthStack(listOf(DepthFrame(archiveLevelId(emptyList()))), visit = stack.visit + 1)
                        } else {
                            stack.copy(frames = stack.frames.dropLast(1) + DepthFrame(stack.active.levelId),
                                visit = stack.visit + 1)
                        },
                    )
                    archiveScrollPositions.clear()
                    screenState.archiveScrollOffsets.clear()
                    focusRecoveryGeneration++
                    pendingView = choice
                    onViewChange(choice)
                }
            },
            onClose = {
                // Move focus before disposing the modal, rather than letting disposal choose a list row.
                if (runCatching { overlays.optionsFocus.requestFocus() }.getOrDefault(false)) {
                    contentFocusOwned = false
                    requestContentFocus = false
                } else {
                    overlays.requestStop = overlays.optionsFocus
                }
                overlays.optionsOpen = false
            },
        )
    }
    }
}

@Composable
private fun RecordingsHeader(
    mode: DvrLibraryMode,
    modeFocus: FocusRequester,
    problemCount: Int,
    diskSpace: at.bernhardberger.tvheadend.sdk.core.DvrDiskSpaceState,
    startPadding: Dp,
    endPadding: Dp,
    onFocused: (DvrLibraryMode) -> Unit,
    onClick: (DvrLibraryMode) -> Unit,
    onMoveToContent: () -> Unit,
    onNavigateTabs: () -> Unit,
) {
    TopLevelBrowseHeader(
        title = stringResource(R.string.recordings_title),
        modifier = Modifier
            .padding(start = startPadding + TvBrowseLeadingInset, end = maxOf(endPadding, TvBrowseTrailingInset))
            .heightIn(min = recordingsHeaderMinHeight())
            .testTag("recordings-header"),
        leading = {
            RecordingModeTabs(
                selected = mode,
                selectedFocus = modeFocus,
                problemCount = problemCount,
                onFocused = onFocused,
                onClick = onClick,
                onMoveToContent = onMoveToContent,
                modifier = Modifier
                    .onPreviewKeyEvent {
                        onNavigateTabs()
                        false
                    }
                    .testTag("recordings-mode-tabs"),
            )
            Spacer(Modifier.weight(1f))
            // Quiet information at the tab line's end on every tab: no focus stop.
            RecordingStorageIndicator(diskSpace)
        },
    )
}

/** Details, context menus and their confirmations share the screen's composition lifetime. */
private class RecordingOverlaysState(private val scheduleState: ScheduleState) {
    var optionsOpen by mutableStateOf(false)
    val optionsFocus = FocusRequester()
    // The stop the next frames move focus to: the View options button after its panel closes.
    var requestStop by mutableStateOf<FocusRequester?>(null)
    var detailsEntry by mutableStateOf<DvrEntry?>(null)
    var showContextMenu by mutableStateOf(false)
    var showResumeChoice by mutableStateOf(false)
    var detailsObservation by mutableStateOf<SessionObservation?>(null)
    var detailsInitialAction by mutableStateOf<RecordingDetailsAction?>(null)
    var pendingAction by mutableStateOf<PendingRecordingAction?>(null)
    var pendingMutation by mutableStateOf<DvrMutationAction?>(null)
    var confirmationKey by mutableStateOf<Key?>(null)
    var pendingDetailsReturn by mutableStateOf(false)
    var folderMenu by mutableStateOf<FolderMenuTarget?>(null)
    var folderDelete by mutableStateOf<FolderDeleteTarget?>(null)
    var newTimerSession by mutableStateOf<CurrentSessionObservation?>(null)
    private var scheduleRequest by mutableStateOf<OpenRule?>(null)
    var scheduleRuleRequest: OpenRule?
        get() = scheduleRequest
        set(value) {
            if (value?.surface != RuleSurface.EDIT ||
                value.item?.let { scheduleState.hasPending(it.id, it.kind) } != true) {
                scheduleRequest = value
            }
        }
    val scheduleSurfaceOpen: Boolean get() = newTimerSession != null || scheduleRuleRequest != null
}

/** Ignore older settings echoes until the latest optimistic selection is acknowledged. */
@Composable
private fun rememberPendingArchiveView(view: DvrArchiveView): MutableState<DvrArchiveView?> {
    val pending = remember { mutableStateOf<DvrArchiveView?>(null) }
    LaunchedEffect(view, pending.value) {
        if (pending.value == view) pending.value = null
    }
    return pending
}

@Composable
private fun rememberArchiveNaming(channelsById: Map<ChannelId, Channel>): DvrArchiveNaming {
    val noChannelLabel = stringResource(R.string.recordings_group_no_channel)
    val noGenreLabel = stringResource(R.string.recordings_group_no_genre)
    val resources = androidx.compose.ui.platform.LocalResources.current
    return remember(channelsById, resources, noChannelLabel, noGenreLabel) {
        DvrArchiveNaming(
            channelName = { entry -> entry.channelId?.let(channelsById::get)?.name ?: entry.channelName },
            genreName = { entry ->
                programmeCategory(entry.contentGenre)?.let { resources.getString(programmeCategoryLabelRes(it)) }
            },
            noChannel = noChannelLabel,
            noGenre = noGenreLabel,
        )
    }
}
