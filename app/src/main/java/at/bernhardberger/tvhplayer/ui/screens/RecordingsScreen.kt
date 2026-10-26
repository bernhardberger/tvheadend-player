package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.ui.components.watchingIndicator
import at.bernhardberger.tvhplayer.ui.components.recordingViewerPosition
import at.bernhardberger.tvhplayer.ui.components.progressColor

import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvhplayer.ui.TvTextColors
import at.bernhardberger.tvhplayer.ui.components.CentredDialog
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.common.formatDateRange
import at.bernhardberger.tvhplayer.ui.common.formatFileSize
import at.bernhardberger.tvhplayer.core.summarizeDvrFolder
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
import at.bernhardberger.tvhplayer.ui.components.BrowseContextMenu
import at.bernhardberger.tvhplayer.ui.components.ProgrammeArtwork
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvhplayer.ui.common.programmeEpisodeLine
import at.bernhardberger.tvhplayer.ui.common.recordingSpaceText
import androidx.compose.foundation.layout.width

import at.bernhardberger.tvhplayer.notices.NoticeCenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
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
import at.bernhardberger.tvhplayer.core.DvrProblemBucket
import at.bernhardberger.tvhplayer.core.DvrScheduleSection
import at.bernhardberger.tvhplayer.core.buildDvrArchive
import at.bernhardberger.tvhplayer.core.groupDvrProblems
import at.bernhardberger.tvhplayer.core.groupDvrSchedule
import at.bernhardberger.tvhplayer.core.partitionDvrLibrary
import at.bernhardberger.tvhplayer.core.recordingIsRunning
import at.bernhardberger.tvhplayer.core.watchedCount
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.rememberCloseBehindPlayer
import at.bernhardberger.tvhplayer.ui.components.TopLevelBrowseHeader
import at.bernhardberger.tvhplayer.ui.components.TabContent
import at.bernhardberger.tvhplayer.ui.components.BrowsePreparationPending
import at.bernhardberger.tvhplayer.ui.components.PreparedBrowseData
import at.bernhardberger.tvhplayer.ui.components.rememberPreparedBrowseData
import at.bernhardberger.tvhplayer.ui.components.rememberTabContentMotion
import at.bernhardberger.tvhplayer.ui.screens.recordings.ArchiveDepthContent
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
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingSchedule
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

private data class RecordingLibraryPresentation(
    val library: DvrLibraryPartition,
    val archive: DvrArchiveFolder,
    val schedule: List<DvrScheduleSection>,
    val problems: Map<DvrProblemBucket, List<DvrEntry>>,
)

class RecordingsScreenState {
    val selectedKeys = mutableStateMapOf<String, String>()
    val archiveScrollPositions = mutableStateMapOf<String, Int>()
    val archiveScrollOffsets = mutableMapOf<String, Int>()
    val mode = mutableStateOf(DvrLibraryMode.ARCHIVE)
    val archiveNavigation = DepthNavigationState(DepthStack(listOf(DepthFrame("archive:"))))
    val archivePath: List<String> get() = archiveLevelPath(archiveNavigation.stack.active.levelId)

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
    nowSecProvider: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = contentPadding.calculateStartPadding(layoutDirection)
    val endPadding = contentPadding.calculateEndPadding(layoutDirection)
    val currentSession = observation.currentSession
    val latestObservationProvider by rememberUpdatedState(currentObservation)
    val preparationAuthority = currentSession ?: observation.dvrSnapshotForDisplay
    var retainedPreparation by remember(preparationAuthority) {
        mutableStateOf<PreparedBrowseData<DvrSnapshot?, RecordingLibraryPresentation>?>(null)
    }
    val channels = observation.channelCatalogForDisplay?.channels.orEmpty()
    val channelsById = remember(channels) { channels.associateBy { it.id } }
    val scope = rememberCoroutineScope()
    val screenState = state ?: remember { RecordingsScreenState() }
    val contentFocus = remember { FocusRequester() }
    val modeFocus = remember { FocusRequester() }
    val selectedKeys = screenState.selectedKeys
    val archiveScrollPositions = screenState.archiveScrollPositions
    var mode by screenState.mode
    val archiveNavigation = screenState.archiveNavigation
    val archiveLevel = archiveNavigation.stack.active.levelId
    val drawerFocus = LocalBrowseNavigationFocus.current
    var requestContentFocus by remember { mutableStateOf(true) }
    var contentHasFocus by remember { mutableStateOf(false) }
    var contentFocusOwned by remember { mutableStateOf(false) }
    var loadingFocusParked by remember { mutableStateOf(false) }
    var relocatingKey by remember { mutableStateOf<Key?>(null) }
    var focusRecoveryGeneration by remember { mutableIntStateOf(0) }
    var detailsEntry by remember { mutableStateOf<DvrEntry?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showResumeChoice by remember { mutableStateOf(false) }
    var menuConsumed by remember(detailsEntry?.id) { mutableStateOf(false) }
    var detailsObservation by remember { mutableStateOf<SessionObservation?>(null) }
    var detailsInitialAction by remember {
        mutableStateOf<RecordingDetailsAction?>(null)
    }
    var pendingAction by remember { mutableStateOf<PendingRecordingAction?>(null) }
    val confirmationVisibility = remember(pendingAction) {
        MutableTransitionState(false).apply { targetState = pendingAction != null }
    }
    var pendingMutation by remember { mutableStateOf<DvrMutationAction?>(null) }
    var confirmationKey by remember { mutableStateOf<Key?>(null) }
    var pendingDetailsReturn by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf<FolderMenuTarget?>(null) }
    var folderDelete by remember { mutableStateOf<FolderDeleteTarget?>(null) }

    fun closeFolderMenu() {
        if (folderMenu == null) return
        // As for a recording's menu: restore the folder row before the menu leaves.
        pendingDetailsReturn = !runCatching { contentFocus.requestFocus() }.getOrDefault(false)
        folderDelete = null
        folderMenu = null
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

    fun openRecording(entry: DvrEntry, returnIfPlaying: Boolean = true) {
        if (returnIfPlaying && entry.id == playingRecordingId && onReturnToRecording(entry.id, {})) return
        val current = latestObservationProvider()
        val capability = current.currentSession
        val action = at.bernhardberger.tvhplayer.core.recordingOpenAction(
            capability?.let { current.recordingPlaybackAdmission(entry) })
        showContextMenu = false
        if (capability != null && (action == at.bernhardberger.tvhplayer.core.RecordingOpenAction.START_OVER ||
                action == at.bernhardberger.tvhplayer.core.RecordingOpenAction.RESUME)) {
            onPlayRecording(RecordingPlaybackSelection(capability, entry.id),
                if (action == at.bernhardberger.tvhplayer.core.RecordingOpenAction.RESUME) RecordingPlaybackStart.RESUME
                else RecordingPlaybackStart.START_OVER)
            return
        }
        showResumeChoice = action == at.bernhardberger.tvhplayer.core.RecordingOpenAction.RESUME_CHOICE
        contentFocusOwned = false
        detailsInitialAction = null
        detailsEntry = entry
        detailsObservation = current
    }

    val location = when (mode) {
        DvrLibraryMode.ARCHIVE -> archiveLevel
        DvrLibraryMode.SCHEDULE -> "schedule"
        DvrLibraryMode.PROBLEMS -> "problems"
    }
    val prepared = rememberPreparedBrowseData(preparationAuthority, observation.dvrSnapshotForDisplay) { snapshot ->
        val library = partitionDvrLibrary(snapshot?.entries.orEmpty())
        currentCoroutineContext().ensureActive()
        val archive = buildDvrArchive(library.archive + snapshot?.entries.orEmpty().filter(::recordingIsRunning))
        currentCoroutineContext().ensureActive()
        RecordingLibraryPresentation(
            library = library,
            archive = archive,
            schedule = groupDvrSchedule(library.schedule, System.currentTimeMillis() / 1000L),
            problems = groupDvrProblems(library.problems),
        )
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        // An observation can dismiss the confirmation between down and up.
        // Finish that key cycle here, not on the refreshed details' action.
        if (event.key == confirmationKey && pendingAction == null) {
            if (event.type == KeyEventType.KeyUp) confirmationKey = null
            true
        } else {
            if (pendingAction != null && event.type == KeyEventType.KeyDown) confirmationKey = event.key
            if (event.key == confirmationKey && event.type == KeyEventType.KeyUp) confirmationKey = null
            false
        }
    }) content@{
    BrowsePreparationPending(initialFocusEnabled, ready = prepared != null,
        onReadyFocus = { requestContentFocus = true; true },
        skeleton = { RecordingsPreparationSkeleton(contentPadding, startPadding) })
    if (prepared == null) return@content
    val observedEntries = prepared.input?.entries.orEmpty()
    val retained = retainedPreparation ?: prepared
    val retainedEntries = retained.input?.entries.orEmpty()
    val observedLibrary = prepared.value.library
    val observedArchive = prepared.value.archive
    val reconciledArchiveStack = remember(observedArchive, archiveNavigation.stack) {
        reconcileArchiveStack(archiveNavigation.stack, observedArchive)
    }
    val observedKeys = when (mode) {
        DvrLibraryMode.ARCHIVE -> requireNotNull(archiveLevelItems(observedArchive, reconciledArchiveStack.active.levelId)).map { it.key }
        DvrLibraryMode.SCHEDULE -> observedLibrary.schedule.map { "recording:${recordingItemKey(it.id)}" }
        DvrLibraryMode.PROBLEMS -> observedLibrary.problems.map { "recording:${recordingItemKey(it.id)}" }
    }
    val removalHandoff = contentFocusOwned && retainedEntries != observedEntries &&
        ((if (mode == DvrLibraryMode.ARCHIVE) archiveNavigation.stack.active.focusedItemId
            else selectedKeys[location])?.let { it !in observedKeys } == true ||
            (mode == DvrLibraryMode.ARCHIVE && reconciledArchiveStack.active.levelId != archiveLevel))
    val entries = if (removalHandoff) retainedEntries else observedEntries
    val presentation = if (removalHandoff) retained.value else prepared.value
    val library = presentation.library
    val archive = presentation.archive
    val archiveItems = remember(archive, archiveLevel) { archiveLevelItems(archive, archiveLevel) ?: archive.listItems() }
    val scheduleGroups = presentation.schedule
    val problemGroups = presentation.problems
    val itemKeys = when (mode) {
        DvrLibraryMode.ARCHIVE -> archiveItems.map { it.key }
        DvrLibraryMode.SCHEDULE -> scheduleGroups.flatMap { it.entries }
            .map { "recording:${recordingItemKey(it.id)}" }
        DvrLibraryMode.PROBLEMS -> DvrProblemBucket.entries
            .flatMap { problemGroups[it].orEmpty() }
            .map { "recording:${recordingItemKey(it.id)}" }
    }
    val alertShown = recordingsAppError(entries.isEmpty(), connectionUiState, connectionFailure) != null

    val drawerState = at.bernhardberger.tvhplayer.ui.components.LocalBrowseDrawerState.current
    LaunchedEffect(observedEntries) {
        // Transfer locally before removing the actual focused node. Keeping only the
        // old key would recreate the node and leave the same focus vacancy.
        if (removalHandoff && initialFocusEnabled &&
            drawerState?.currentValue != androidx.tv.material3.DrawerValue.Open
        ) {
            if (modeFocus.requestFocus()) {
                contentFocusOwned = false
                requestContentFocus = observedKeys.isNotEmpty()
            }
        }
        retainedPreparation = prepared
        archiveNavigation.update(reconcileArchiveStack(archiveNavigation.stack, observedArchive))
    }

    LaunchedEffect(
        location,
        itemKeys,
        selectedKeys[location],
        requestContentFocus,
        initialFocusEnabled,
        alertShown,
        detailsEntry,
        pendingAction,
        folderMenu,
    ) {
        if (detailsEntry != null || pendingAction != null || folderMenu != null) return@LaunchedEffect
        if (itemKeys.isEmpty() && !alertShown) {
            if (!initialFocusEnabled || (!requestContentFocus && !contentFocusOwned)) return@LaunchedEffect
            if (drawerState?.currentValue == androidx.tv.material3.DrawerValue.Open) return@LaunchedEffect
            if (modeFocus.requestFocus()) {
                requestContentFocus = false
                contentFocusOwned = false
                loadingFocusParked = true
            }
            return@LaunchedEffect
        }
        if (mode != DvrLibraryMode.ARCHIVE && itemKeys.isNotEmpty() && selectedKeys[location] !in itemKeys) {
            selectedKeys[location] = itemKeys.first()
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
    LaunchedEffect(
        detailsEntry,
        pendingAction,
        pendingDetailsReturn,
        location,
        focusRecoveryGeneration,
        folderMenu,
    ) {
        if (!pendingDetailsReturn) return@LaunchedEffect
        if (detailsEntry != null || pendingAction != null || folderMenu != null) return@LaunchedEffect
        repeat(4) {
            withFrameNanos { }
            val restored = runCatching {
                if (itemKeys.isEmpty() && !alertShown) modeFocus.requestFocus()
                else contentFocus.requestFocus()
            }.getOrDefault(false)
            if (restored) {
                pendingDetailsReturn = false
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
            .semantics { if (detailsEntry != null || folderMenu != null) hideFromAccessibility() }
            .graphicsLayer { alpha = 1f - (detailsLayering?.detailsAlpha() ?: if (detailsEntry != null && !showContextMenu && !showResumeChoice) 1f else 0f) }
            .onPreviewKeyEvent { event ->
                if (event.key == relocatingKey) {
                    if (event.type == KeyEventType.KeyUp) relocatingKey = null
                    return@onPreviewKeyEvent true
                }
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionUp &&
                    mode != DvrLibraryMode.ARCHIVE &&
                    contentHasFocus &&
                    selectedKeys[location] == itemKeys.firstOrNull()
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
        val spaceValue = recordingSpaceText(observation.dvrDiskSpaceState)
        val spaceDescription = spaceValue?.let { stringResource(R.string.recording_space_line_description, it) }
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
                    onFocused = {
                        modeMotion.select(it, DvrLibraryMode.entries)
                        if (mode != it) {
                            contentFocusOwned = false
                            requestContentFocus = false
                        }
                        mode = it
                    },
                    onClick = {
                        modeMotion.select(it, DvrLibraryMode.entries)
                        mode = it
                        requestContentFocus = true
                    },
                    onMoveToContent = {
                        requestContentFocus = true
                    },
                    modifier = Modifier
                        .onFocusChanged { if (!it.hasFocus) loadingFocusParked = false }
                        .onPreviewKeyEvent {
                            // A deliberate header move gives the viewer ownership, not the loading handoff.
                            loadingFocusParked = false
                            contentFocusOwned = false
                            false
                        }
                        .testTag("recordings-mode-tabs"),
                )
            },
            // Quiet information at the row's end: no focus stop. The tabs measure first, so the
            // text only gets the width they leave; it ellipsizes there and hides when too narrow.
            actions = spaceValue?.let { value ->
                {
                    BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        if (maxWidth >= RecordingSpaceMinWidth) {
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TvTextColors.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .testTag("recordings-space")
                                    .semantics { contentDescription = spaceDescription.orEmpty() },
                            )
                        }
                    }
                }
            },
        )
        TabContent(
            motion = modeMotion,
            selectedKey = mode,
            state = {
                RecordingsTabBody(
                    mode, location, entries.isEmpty(), connectionUiState, connectionFailure,
                    observation, channelsById, archive,
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
        // Every tab's content starts at y80, as the channel list's first row does.
        Spacer(Modifier.height(TvSpacing12))

        if (frame.empty && recordingsLoading(frame.connection, frame.failure)) {
            // In the Archive column's place, running to the bottom edge as its list does.
            RecordingsSkeleton(Modifier.padding(start = startPadding + TvBrowseLeadingInset))
        } else if (frame.empty) {
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
                DvrLibraryMode.ARCHIVE -> ArchiveDepthContent(
                    root = frame.archive,
                    navigation = archiveNavigation,
                    contentPadding = PaddingValues(
                        start = startPadding + TvBrowseLeadingInset,
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                    isCurrent = owner.isCurrent,
                    initialFocusEnabled = owner.isCurrent && initialFocusEnabled &&
                        detailsEntry == null && folderMenu == null && (contentFocusOwned || requestContentFocus),
                    backEnabled = backEnabled && detailsEntry == null && folderMenu == null && contentFocusOwned,
                    contentFocus = contentFocus,
                    onModeFocus = {
                        if (owner.isCurrent && modeFocus.requestFocus()) {
                            contentFocusOwned = false
                            requestContentFocus = false
                        }
                    },
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
                            detailsInitialAction = null
                            detailsEntry = it
                            detailsObservation = observation
                            showContextMenu = true
                        }
                    },
                    onOpenFolderMenu = { folder ->
                        val session = currentSession
                        val canModify = (observation.sessionState as? SessionState.Ready)
                            ?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
                        // Without anything to change there is no menu, as for a row without one.
                        if (owner.isCurrent && session != null &&
                            folderContextActions(folder, observation::recordingPlaybackAdmission, canModify).isNotEmpty()
                        ) {
                            contentFocusOwned = false
                            folderMenu = FolderMenuTarget(folder.path, session)
                        }
                    },
                    piconForEntry = { it.channelId?.let(channelsById::get)?.icon },
                    admissionForEntry = { observation.recordingPlaybackAdmission(it) },
                    modifier = Modifier.fillMaxSize().onFocusChanged {
                        if (owner.isCurrent) {
                            contentHasFocus = it.hasFocus
                            if (it.hasFocus) contentFocusOwned = true
                        }
                    },
                )
                DvrLibraryMode.SCHEDULE -> Box(
                    modifier = Modifier
                        // Archive's columns: the list in its first, the focused recording where it shows its preview.
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
                        RecordingSchedule(
                            groups = scheduleGroups,
                            selectedKey = frame.selectedKey,
                            selectedFocus = contentFocus,
                            onFocused = { if (owner.isCurrent) selectedKeys[location] = it },
                            onOpen = {
                                if (!owner.isCurrent) return@RecordingSchedule
                                openRecording(it)
                            },
                            onOpenContextMenu = {
                                if (owner.isCurrent) {
                                    contentFocusOwned = false
                                    detailsInitialAction = null
                                    detailsEntry = it
                                    detailsObservation = observation
                                    showContextMenu = true
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
                                    detailsInitialAction = null
                                    detailsEntry = it
                                    detailsObservation = observation
                                    showContextMenu = true
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

    // Refresh metadata only within the opening session. Never substitute a colliding ID
    // from a later session, including while a captured confirmation is pending.
    val selectedCapability = detailsObservation?.currentSession
        ?.takeIf { observation.currentSession === it }
    val opened = detailsEntry?.let { entry ->
        if (selectedCapability != null && observation.dvrState is DvrRepositoryState.Current) {
            observation.dvrEntry(entry.id)
        } else entry
    }
    // The SDK admits the opened entry with the observation it was read from; without its session nothing plays.
    val openedAdmission = opened?.let { entry ->
        selectedCapability?.let {
            (if (observation.dvrState is DvrRepositoryState.Current) observation else detailsObservation)
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
    LaunchedEffect(detailsEntry, opened) {
        if (detailsEntry != null && opened == null) {
            showResumeChoice = false
            pendingAction = null
            pendingMutation = null
            detailsEntry = null
            detailsObservation = null
            detailsInitialAction = null
            pendingDetailsReturn = true
        }
    }
    if (opened != null) {
        val nowSec = at.bernhardberger.tvhplayer.ui.common.rememberDetailsEpochSeconds(nowSecProvider)
        if (showResumeChoice) {
            val closeBehindPlayer = rememberCloseBehindPlayer()
            val resources = androidx.compose.ui.platform.LocalContext.current.resources
            val channel = opened.channelId?.let(channelsById::get)
            val closeResume = {
                showResumeChoice = false
                detailsEntry = null
                detailsObservation = null
                pendingDetailsReturn = true
            }
            val play: (RecordingPlaybackStart) -> Unit = { intent ->
                selectedCapability?.let { capability ->
                    closeBehindPlayer(closeResume)
                    onPlayRecording(RecordingPlaybackSelection(capability, opened.id), intent)
                }
            }
            val choice = at.bernhardberger.tvhplayer.core.recordingOpenAction(openedAdmission) ==
                at.bernhardberger.tvhplayer.core.RecordingOpenAction.RESUME_CHOICE
            LaunchedEffect(choice) {
                if (!choice) closeResume()
            }
            val resumeFocus = remember { FocusRequester() }
            val startOverFocus = remember { FocusRequester() }
            var startOverFocused by remember(opened.id) { mutableStateOf(false) }
            val durationMs = at.bernhardberger.tvhplayer.core.recordingDetailsFacts(opened, openedAdmission, nowSec)
                .firstOrNull { it.kind == at.bernhardberger.tvhplayer.core.DetailsFactKind.LENGTH }?.seconds?.times(1000L)
            val positionMs = at.bernhardberger.tvhplayer.core.offeredResumePosition(openedAdmission)?.inWholeMilliseconds ?: 0L
            val resumeEnd = at.bernhardberger.tvhplayer.core.recordingEndsAtSec(nowSec, positionMs, durationMs)
                ?.let { stringResource(R.string.recording_ends_at, at.bernhardberger.tvhplayer.ui.common.formatClock(it)) }.orEmpty()
            val startOverEnd = at.bernhardberger.tvhplayer.core.recordingEndsAtSec(nowSec, 0L, durationMs)
                ?.let { stringResource(R.string.recording_ends_at, at.bernhardberger.tvhplayer.ui.common.formatClock(it)) }.orEmpty()
            val spokenResume = stringResource(R.string.recording_resume_from,
                at.bernhardberger.tvhplayer.ui.common.formatDurationMinutes(positionMs / 60_000L, resources, spoken = true))
            CentredDialog(
                title = opened.title.orEmpty(),
                secondaryText = programmeEpisodeLine(opened.episode, opened.subtitle, resources),
                text = if (startOverFocused) startOverEnd else resumeEnd,
                eyebrow = { ProvideArtworkSession(selectedCapability, observation) {
                    at.bernhardberger.tvhplayer.ui.components.ProgrammeEyebrow(channel?.icon, listOfNotNull(
                    (channel?.let { at.bernhardberger.tvhplayer.ui.components.channelTitleText(it.number, it.name.orEmpty()) }
                        ?: opened.channelName)?.takeIf(String::isNotBlank),
                    opened.start?.epochSeconds?.formatDateTime(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
                    ).joinToString(" • "),
                        Modifier.fillMaxWidth(), Modifier.testTag("recording-resume-picon"))
                } },
                onDismiss = closeResume,
                backEnabled = backEnabled,
            ) {
                LaunchedEffect(Unit) { withFrameNanos { }; resumeFocus.requestFocus() }
                Button(onClick = { play(RecordingPlaybackStart.RESUME) },
                    modifier = Modifier.height(40.dp).focusRequester(resumeFocus)
                        .onFocusChanged { if (it.isFocused) startOverFocused = false }
                        .semantics { contentDescription = spokenResume; if (resumeEnd.isNotBlank()) stateDescription = resumeEnd }
                        .focusProperties { left = FocusRequester.Cancel; right = startOverFocus }
                        .testTag("recording-resume"),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(painterResource(R.drawable.ic_play_arrow), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.recording_resume_from,
                        at.bernhardberger.tvhplayer.core.formatPlaybackDuration(positionMs)),
                        maxLines = 1, softWrap = false)
                }
                OutlinedButton(onClick = { play(RecordingPlaybackStart.START_OVER) },
                    modifier = Modifier.height(40.dp).focusRequester(startOverFocus)
                        .onFocusChanged { if (it.isFocused) startOverFocused = true }
                        .semantics { if (startOverEnd.isNotBlank()) stateDescription = startOverEnd }
                        .focusProperties { left = resumeFocus; right = FocusRequester.Cancel }
                        .testTag("recording-start-over"),
                    contentPadding = OutlinedButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(painterResource(R.drawable.ic_replay), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.context_start_over), maxLines = 1, softWrap = false)
                }
            }
        } else {
        val closeBehindPlayer = rememberCloseBehindPlayer()
        val entryPending = at.bernhardberger.tvhplayer.ui.rememberPlayerEntryPending()
        // The menu and details return to the playing recording the same way.
        val continueWatching = {
            val returned = onReturnToRecording(opened.id) {
                // Register only after warm validation, but before the request makes the handoff pending.
                closeBehindPlayer {
                    showContextMenu = false
                    detailsEntry = null
                    detailsObservation = null
                    detailsInitialAction = null
                    requestContentFocus = true
                }
            }
            if (!returned) openRecording(opened, returnIfPlaying = false)
        }
        val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
        val closing = !visibility.targetState
        BackHandler(enabled = backEnabled && closing) { }
        LaunchedEffect(visibility.isIdle, visibility.currentState, closing) {
            if (closing && visibility.isIdle && !visibility.currentState) {
                val restoredBeforeDismissal = runCatching { contentFocus.requestFocus() }.getOrDefault(false)
                pendingDetailsReturn = !restoredBeforeDismissal
                detailsEntry = null
                detailsObservation = null
                detailsInitialAction = null
            }
        }
        AnimatedVisibility(visibleState = visibility, modifier = Modifier.browseDetailsViewport(),
            enter = EnterTransition.None, exit = ExitTransition.None) {
        val detailAlpha = animateShown(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard),
            tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard), "recording-details-layer")
        at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame(leaving = closing,
            modifier = Modifier.onPreviewKeyEvent { closing || entryPending() }) {
        // Match ProgrammeDetailsHost: keep both pages drawn while details owns its alpha.
        AnimatedContent(targetState = showContextMenu, transitionSpec = {
            (if (targetState) fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard)) else EnterTransition.None)
                .togetherWith(if (initialState) fadeOut(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard)) else ExitTransition.None)
                .using(null)
        }, label = "recording-menu-details") { menu ->
        at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame(leaving = closing || menu != showContextMenu) {
        if (menu) {
        AnimatedVisibility(visible = !closing && !menuConsumed,
            enter = EnterTransition.None, exit = ExitTransition.None) {
        androidx.compose.runtime.CompositionLocalProvider(
            at.bernhardberger.tvhplayer.ui.player.LocalPlayerPanelMotion provides this) {
        at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame(leaving = pendingAction != null) {
        BrowseContextMenu(
            backEnabled = backEnabled && !closing && menu == showContextMenu && pendingAction == null,
            title = opened.title.orEmpty(),
            channel = opened.channelId?.let(channelsById::get)?.let {
                at.bernhardberger.tvhplayer.ui.components.channelTitleText(it.number, it.name.orEmpty())
            } ?: opened.channelName.orEmpty(),
            episode = programmeEpisodeLine(opened.episode, opened.subtitle, androidx.compose.ui.platform.LocalContext.current.resources),
            time = recordingTimeRange(opened.start?.epochSeconds, opened.stop?.epochSeconds,
                androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
            summary = opened.summary?.takeIf(String::isNotBlank) ?: opened.description.orEmpty(),
            recordingState = opened.state,
            playbackIndicator = watchingIndicator(opened.id),
            actions = recordingContextActions(opened, canModifyRecordings = selectedCapability != null &&
                (observation.sessionState as? at.bernhardberger.tvheadend.sdk.core.SessionState.Ready)
                    ?.capabilities?.dvrWrite == at.bernhardberger.tvheadend.sdk.core.CapabilityAccess.ALLOWED,
                admission = openedAdmission, playing = selectedCapability != null && opened.id ==
                    at.bernhardberger.tvhplayer.ui.warmRecordingId(
                        at.bernhardberger.tvhplayer.ui.components.LocalWatchingTarget.current,
                        at.bernhardberger.tvhplayer.ui.components.LocalRecordingPlaybackSelection.current,
                        observation.currentSession)),
            onClose = { visibility.targetState = false },
            onAction = { action ->
                val playing = selectedCapability?.takeIf { action in listOf(BrowseContextAction.WATCH,
                    BrowseContextAction.PLAY, BrowseContextAction.RESUME, BrowseContextAction.START_OVER,
                    BrowseContextAction.CONTINUE_WATCHING) }
                // Playback keeps the menu up until the player has replaced browse.
                if (playing == null) {
                    menuConsumed = action != BrowseContextAction.DETAILS
                    showContextMenu = action in listOf(BrowseContextAction.STOP, BrowseContextAction.CANCEL, BrowseContextAction.DELETE)
                }
                when (action) {
                    BrowseContextAction.CONTINUE_WATCHING -> playing?.let { continueWatching() }
                    BrowseContextAction.WATCH, BrowseContextAction.PLAY,
                    BrowseContextAction.RESUME, BrowseContextAction.START_OVER -> playing?.let {
                        closeBehindPlayer {
                            showContextMenu = false
                            detailsEntry = null
                            detailsObservation = null
                            detailsInitialAction = null
                            requestContentFocus = true
                        }
                        onPlayRecording(RecordingPlaybackSelection(it, opened.id),
                            if (action == BrowseContextAction.RESUME || action == BrowseContextAction.WATCH)
                                RecordingPlaybackStart.RESUME else RecordingPlaybackStart.START_OVER)
                    }
                    BrowseContextAction.STOP -> {
                        detailsInitialAction = RecordingDetailsAction.STOP
                        pendingMutation = selectedCapability?.let { DvrMutationAction.Stop(it, opened.id) }
                        pendingAction = PendingRecordingAction.STOP
                    }
                    BrowseContextAction.CANCEL -> {
                        detailsInitialAction = RecordingDetailsAction.CANCEL
                        pendingMutation = selectedCapability?.let { DvrMutationAction.Cancel(it, opened.id) }
                        pendingAction = PendingRecordingAction.CANCEL
                    }
                    BrowseContextAction.DELETE -> {
                        detailsInitialAction = RecordingDetailsAction.DELETE
                        pendingMutation = selectedCapability?.let { DvrMutationAction.Delete(it, opened.id) }
                        pendingAction = PendingRecordingAction.DELETE
                    }
                    BrowseContextAction.MARK_WATCHED, BrowseContextAction.MARK_UNWATCHED -> {
                        visibility.targetState = false
                        markRecording(action)
                    }
                    else -> Unit
                }
            },
            artwork = {
                val artwork = (opened.image ?: opened.fanartImage)?.takeIf { selectedCapability != null }
                ProvideArtworkSession(selectedCapability, observation) {
                    ProgrammeArtwork(artwork, opened.channelId?.let(channelsById::get)?.icon, recordingViewerPosition(opened.id)?.fraction,
                        Modifier.fillMaxSize().testTag(if (artwork != null)
                            "browse-context-artwork" else "browse-context-picon"),
                        plateModifier = Modifier.testTag("browse-context-plate"),
                        progressModifier = Modifier.testTag("browse-context-progress"), progressColor = progressColor(true))
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
            onShowGuide = onShowGuide,
            recording = opened,
            openingObservation = requireNotNull(detailsObservation),
            observation = observation,
            currentObservation = currentObservation,
            nowSec = nowSec,
            mutations = dvrMutationActions,
            notices = notices,
            mutationScope = scope,
            initialAction = when (detailsInitialAction) {
                RecordingDetailsAction.CANCEL,
                RecordingDetailsAction.STOP -> when (opened.state) {
                    DvrEntryState.SCHEDULED -> BrowseContextAction.CANCEL
                    DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR -> BrowseContextAction.STOP
                    else -> null
                }
                RecordingDetailsAction.RESUME -> BrowseContextAction.RESUME
                RecordingDetailsAction.BEGINNING -> BrowseContextAction.START_OVER
                RecordingDetailsAction.PLAY -> BrowseContextAction.PLAY
                RecordingDetailsAction.DELETE -> BrowseContextAction.DELETE
                null -> null
            },
            backEnabled = backEnabled && !closing && menu == showContextMenu && pendingAction == null,
            visibilityAlpha = alpha,
            onPlayRecording = { selection, start ->
                closeBehindPlayer {
                    detailsEntry = null
                    detailsObservation = null
                    detailsInitialAction = null
                    requestContentFocus = true
                }
                onPlayRecording(selection, start)
            },
            onWatchChannel = onWatchChannel?.let { watch -> { id, name ->
                closeBehindPlayer { detailsEntry = null; detailsObservation = null; requestContentFocus = true }
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

    val action = pendingAction
    val target = opened
    val closeConsumedMenu = {
        if (showContextMenu && menuConsumed) {
            // Match details dismissal: restore the mounted card before removing the dialog.
            pendingDetailsReturn = !runCatching { contentFocus.requestFocus() }.getOrDefault(false)
            showContextMenu = false
            detailsEntry = null
            detailsObservation = null
            detailsInitialAction = null
        }
    }
    val mutationStateCurrent = pendingMutation?.recordingStateIsCurrent(observation) == true
    LaunchedEffect(action, selectedCapability, mutationStateCurrent) {
        if (action != null && (selectedCapability == null || !mutationStateCurrent)) {
            pendingAction = null
            pendingMutation = null
            // The dismissed dialog must not restore focus to an action whose authority expired.
            if (selectedCapability == null) detailsInitialAction = null
            closeConsumedMenu()
        }
    }
    if (action != null && target != null && selectedCapability != null && mutationStateCurrent) {
        RecordingConfirmationDialog(
            visibility = confirmationVisibility,
            action = action,
            title = target.title.orEmpty(),
            channelName = target.channelId?.let(channelsById::get)?.name ?: target.channelName.orEmpty(),
            dateAndTime = at.bernhardberger.tvhplayer.ui.common.formatRecordingSchedule(target.start?.epochSeconds,
                dateOnly = action == PendingRecordingAction.DELETE,
                locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
            backEnabled = backEnabled,
            onDismiss = {
                pendingAction = null
                pendingMutation = null
                closeConsumedMenu()
            },
            onConfirm = {
                pendingAction = null
                val mutation = pendingMutation
                pendingMutation = null
                val mutationObservation = detailsObservation
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
    folderMenu?.let { menu ->
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
            backEnabled = backEnabled && folderDelete == null,
            onClose = ::closeFolderMenu,
            onAction = { action ->
                val targets = folderActionTargets(folder, action, observation::recordingPlaybackAdmission, canModify)
                when (action) {
                    BrowseContextAction.DELETE_WATCHED, BrowseContextAction.DELETE_ALL -> folderDelete = FolderDeleteTarget(
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
        folderDelete?.let { delete ->
            val count = delete.recordingIds.size
            val size = delete.sizeBytes.takeIf { it > 0 }?.let { formatFileSize(it, context) }
            val message = buildString {
                append(if (size != null) pluralStringResource(R.plurals.delete_folder_confirm_message, count, count, size)
                    else pluralStringResource(R.plurals.delete_folder_confirm_message_no_size, count, count))
                if (delete.keepsRunning) append(' ').append(stringResource(R.string.delete_folder_running_kept))
            }
            CentredDialog(
                title = stringResource(if (delete.action == BrowseContextAction.DELETE_ALL)
                    R.string.delete_folder_all_confirm_title else R.string.delete_folder_watched_confirm_title, delete.name),
                message = message,
                firstLabel = stringResource(R.string.keep),
                secondLabel = stringResource(delete.action.label(watchChannel = false)),
                onFirst = ::closeFolderMenu,
                onSecond = {
                    closeFolderMenu()
                    runFolderAction(delete.action, session, delete.recordingIds)
                },
                onDismiss = ::closeFolderMenu,
                backEnabled = backEnabled,
                firstTag = "folder-delete-back", secondTag = "folder-delete-confirm",
            )
        }
    }
    }
}

/** Below this width the disk-space text would show little more than an ellipsis, so it hides. */
private val RecordingSpaceMinWidth = 96.dp
