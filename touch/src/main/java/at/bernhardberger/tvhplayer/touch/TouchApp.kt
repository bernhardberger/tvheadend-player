package at.bernhardberger.tvhplayer.touch

import at.bernhardberger.tvhplayer.touch.ui.ConfirmDvrChange
import at.bernhardberger.tvhplayer.touch.ui.DvrControls
import androidx.compose.runtime.remember
import at.bernhardberger.tvhplayer.touch.ui.ArtworkFirstLoad
import at.bernhardberger.tvhplayer.touch.ui.LocalArtworkConnectedAt
import at.bernhardberger.tvhplayer.touch.ui.rememberArtworkConnectedAt
import at.bernhardberger.tvhplayer.touch.ui.traceArtworkMapping
import at.bernhardberger.tvhplayer.touch.ui.LocalArtworkModels
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryState
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.serverProfileDisplayName
import at.bernhardberger.tvhplayer.ui.screens.settings.rememberSavedConnectionRestore
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.SessionGenerationIdentity
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.touch.channels.ChannelRowPrograms
import at.bernhardberger.tvhplayer.touch.channels.LocalChannelProgressClock
import at.bernhardberger.tvhplayer.touch.channels.ChannelsActions
import at.bernhardberger.tvhplayer.touch.channels.ChannelsScreen
import at.bernhardberger.tvhplayer.touch.channels.ChannelsState
import at.bernhardberger.tvhplayer.touch.channels.ChannelsTextsFormat
import at.bernhardberger.tvhplayer.touch.channels.ChannelsTexts
import at.bernhardberger.tvhplayer.touch.channels.MappedChannelRow
import at.bernhardberger.tvhplayer.touch.channels.channelRowPrograms
import at.bernhardberger.tvhplayer.touch.channels.sameProgrammeAuthority
import at.bernhardberger.tvhplayer.touch.channels.channelsState
import at.bernhardberger.tvhplayer.touch.channels.channelsTextFormat
import at.bernhardberger.tvhplayer.touch.channels.rememberChannelsTexts
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import at.bernhardberger.tvhplayer.ui.common.rememberCurrentEpochSeconds
import kotlin.time.Instant
import at.bernhardberger.tvhplayer.touch.connect.ActiveConnectionUiState
import at.bernhardberger.tvhplayer.touch.connect.ConnectViewModel
import at.bernhardberger.tvhplayer.touch.connect.ConnectionStatus
import at.bernhardberger.tvhplayer.touch.connect.ConnectionsScreen
import at.bernhardberger.tvhplayer.touch.connect.EditConnectionFlow
import at.bernhardberger.tvhplayer.touch.connect.FirstRunFlow
import at.bernhardberger.tvhplayer.touch.connect.connectionStatus
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.FrameNotices
import at.bernhardberger.tvhplayer.touch.ui.BottomWindowNotices
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.touch.ui.DestinationFrame
import at.bernhardberger.tvhplayer.touch.ui.LocalDestinationBackEnabled
import at.bernhardberger.tvhplayer.touch.guide.GuideDestination
import at.bernhardberger.tvhplayer.touch.guide.GuideViewModel
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsDestination
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsViewModel
import at.bernhardberger.tvhplayer.touch.ui.epgChangeCounts
import at.bernhardberger.tvhplayer.touch.ui.artworkMappingStart
import at.bernhardberger.tvhplayer.touch.ui.pageMove
import android.os.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

private enum class Page { Frame, Connections }

/**
 * The app's pages: first run while no connection is saved, else the frame with the Connections
 * page over it. First run stays until its save has finished, so the save is not cut short when
 * the saved connection appears.
 */
@Suppress("LongParameterList", "LongMethod") // The activity's owners and the two page entries are wired here.
@Composable
internal fun TouchApp(
    session: TvheadendSession,
    owner: AppProfileOwner,
    connection: AppConnectionViewModel,
    channels: ChannelsViewModel,
    recordings: RecordingsViewModel,
    guide: GuideViewModel,
    notices: NoticeCenter,
    playingId: String? = null,
    playingRecordingId: DvrEntryId? = null,
    onWatch: ((String) -> Unit)? = null,
    onPlayRecording: ((String, Boolean) -> Unit)? = null,
    channelsDispatcher: CoroutineDispatcher = ChannelsMappingDispatcher,
    discovery: () -> Flow<TvheadendDiscoveryState>,
) {
    val profile by owner.serverProfile.collectAsStateWithLifecycle()
    val profiles by owner.profiles.collectAsStateWithLifecycle()
    val presentation by connection.uiState.collectAsStateWithLifecycle()
    val connectedAt = rememberArtworkConnectedAt(session)
    val connect = viewModel { ConnectViewModel(session, owner) }
    val dvr = viewModel { DvrControls(session, notices) }
    val channelsCache = viewModel { ChannelsMappingCache(session) }
    SideEffect { connect.discovery = discovery }
    var backStack by rememberSaveable { mutableStateOf(listOf(Page.Frame)) }
    // Sign-in is an editor in the Connections entry, not a second page to animate to.
    var signIn by rememberSaveable { mutableStateOf(false) }
    val closeConnections = {
        signIn = false
        backStack = listOf(Page.Frame)
    }
    val openConnections: (Boolean) -> Unit = { openOnPassword ->
        if (backStack.last() == Page.Frame) {
            signIn = openOnPassword
            backStack = backStack + Page.Connections
        }
    }
    // First run follows the settled profile: a removal's passing "no connection" does not open it,
    // and it stays until its own save and naming have finished.
    val firstRun = (profile == ServerProfileReadResult.Missing && !connect.removing) || connect.firstRunSettling
    LaunchedEffect(firstRun) {
        // Whatever was open belonged to the removed connection; nothing stays behind first run.
        if (firstRun) closeConnections() else connect.releaseFirstRun()
    }
    FrameNotices(notices, owner, session) {
        when {
            profile == null -> Surface(Modifier.fillMaxSize()) {}
            firstRun -> Box(Modifier.fillMaxSize()) {
                FirstRunFlow(connect.firstRun())
                BottomWindowNotices()
            }
            else -> {
                val forward = pageMove(forward = true)
                val backward = pageMove(forward = false)
                // NavDisplay can retain a scene across recompositions. Its entry must read current
                // inputs (notably the playing IDs), even when that scene's key has not changed.
                val frame by rememberUpdatedState<@Composable () -> Unit> {
                    CompositionLocalProvider(
                        LocalArtworkConnectedAt provides connectedAt,
                        LocalDestinationBackEnabled provides (backStack.last() == Page.Frame),
                    ) {
                        AppDestinations(
                            channelsCache, dvr, connection, channels, recordings, guide,
                            playingId, onWatch, onPlayRecording,
                            channelsDispatcher = channelsDispatcher,
                            playingRecordingId = playingRecordingId,
                            onOpenConnections = { openConnections(false) },
                            onSignIn = { openConnections(true) },
                        )
                    }
                }
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = closeConnections,
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                    transitionSpec = { forward },
                    popTransitionSpec = { backward },
                    entryProvider = { page ->
                        NavEntry(page) {
                            when (page) {
                                Page.Frame -> frame()
                                Page.Connections -> {
                                    val active = (profiles as? ServerProfileListResult.Available)?.let { list ->
                                        list.profiles.firstOrNull { it.id == list.activeId }
                                    }
                                    ConnectionsPage(
                                        session, owner, connect,
                                        active?.uiState(connectionStatus(profile, presentation)), active,
                                        listed = profiles is ServerProfileListResult.Available,
                                        signIn = signIn,
                                        onBack = closeConnections,
                                        onSaved = { signIn = false },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * The Connections page. With [signIn] the editor of the connection in use is open at once on
 * Password, and its Cancel leaves the page.
 */
@Composable
private fun ConnectionsPage(
    session: TvheadendSession,
    owner: AppProfileOwner,
    connect: ConnectViewModel,
    card: ActiveConnectionUiState?,
    active: ServerProfileSummary?,
    /** False while the owner can't list connections, as after a write it could not read back. */
    listed: Boolean,
    signIn: Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) = Box(Modifier.fillMaxSize()) {
    var editing by rememberSaveable { mutableStateOf(false) }
    ConnectionsScreen(
        active = card,
        onBack = onBack,
        onEdit = { if (!connect.removing) editing = true },
        onRemove = { if (active != null) connect.remove(active.id) },
    )
    // An open editor stays on its connection while the list can't be read; only an accepted close,
    // a finished save or the connection's removal takes it away.
    val kept = connect.openEditor.takeIf { active == null && !listed }
    val editorShown = (editing || signIn) && (active != null || kept != null) && !connect.removing
    LaunchedEffect(editorShown) { if (!editorShown) connect.releaseEditor() }
    val editorLoaded = if (editorShown) {
        val restoreSaved = rememberSavedConnectionRestore(session, owner)
        val setup = if (active != null) {
            connect.editor(active.id, active.label.orEmpty(), active.requiresSignIn, restoreSaved)
        } else checkNotNull(kept)
        // The editor closes once the change and its name are saved.
        LaunchedEffect(setup, setup.finished) {
            if (setup.finished) {
                connect.releaseEditor()
                editing = false
                onSaved()
            }
        }
        EditConnectionFlow(
            setup,
            onClose = {
                connect.releaseEditor()
                if (signIn) onBack() else editing = false
            },
            focusPassword = signIn,
        )
        setup.loaded
    } else false
    // The editor is a separate window. Once it is open, only that window consumes notices.
    if (!editorLoaded) BottomWindowNotices()
}

// An unreadable saved sign-in keeps a password connection one, whichever way the editor opened.
private val ServerProfileSummary.requiresSignIn: Boolean
    get() = authenticationMode == ServerProfileAuthenticationMode.PASSWORD && !credentialsAvailable

private fun ServerProfileSummary.uiState(status: ConnectionStatus) = ActiveConnectionUiState(
    name = serverProfileDisplayName(this),
    address = host,
    port = port,
    username = username.takeIf { authenticationMode == ServerProfileAuthenticationMode.PASSWORD }.orEmpty(),
    status = status,
)

/**
 * The frame with Channels and Recordings wired: the destinations without data are not offered yet,
 * and the row controls with nothing behind them (Details, Show in Guide) are left out.
 */
@Composable
@Suppress("LongParameterList")
private fun AppFrame(
    connection: AppConnectionViewModel,
    channels: ChannelsViewModel,
    playingId: String?,
    onWatch: ((String) -> Unit)?,
    onOpenConnections: () -> Unit,
    onSignIn: () -> Unit,
    mappingDispatcher: CoroutineDispatcher,
    cache: ChannelsMappingCache,
) {
    val scope = channels.scope.collectAsStateWithLifecycle().value
    val observedSession = channels.observation.collectAsStateWithLifecycle()
    val generation by remember(observedSession) {
        derivedStateOf(structuralEqualityPolicy()) { observedSession.value.currentSession?.generationIdentity }
    }
    val format = channelsTextFormat()
    val texts = rememberChannelsTexts(format)
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, channels, connection, playingId, format, texts, cache, mappingDispatcher) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // The scope changes with the catalog or tag, not with every guide update.
            combine(channels.scope, connection.uiState, channels.observation) { scope, presentation, observation ->
                val key = ChannelsMappingKey(
                    scope, presentation.connection, presentation.degraded, format, playingId, cache.artwork,
                )
                key to observation
            }.collectLatest { (key, observation) ->
                // The catalog scope and the session can arrive on separate flow emissions.
                if (key.scope.observation?.currentSession?.generationIdentity !==
                    observation.currentSession?.generationIdentity
                ) return@collectLatest
                while (true) {
                    // A clock wake and a data update in the same UI turn need only the newest inputs.
                    yield()
                    val previous = cache.mapped
                    val now = Instant.fromEpochSeconds(System.currentTimeMillis() / MILLIS_PER_SECOND)
                    val reasons = key.mappingReasons(previous, observation, now.epochSeconds)
                    val result = if (reasons.isEmpty() && previous != null) previous else {
                        withContext(mappingDispatcher) { key.map(context, observation, previous, now, reasons, texts) }
                    }
                    // The source may already have changed before combine can cancel this pass.
                    if (!key.inputsAreCurrent(channels, connection, observation)) return@collectLatest
                    cache.mapped = result
                    delay((result.nextWakeSec * MILLIS_PER_SECOND - System.currentTimeMillis()).coerceAtLeast(1L))
                }
            }
        }
    }
    val state = cache.mapped?.takeIf { it.matchesDisplay(scope, generation, format) }?.state ?: ChannelsState.Loading
    ArtworkFirstLoad("Channels", if (state == ChannelsState.Loading) when {
        !scope.settingsLoaded -> "settings"
        scope.scope.visibleChannels.isEmpty() -> "channels"
        else -> "mapping"
    } else null)
    CompositionLocalProvider(LocalChannelProgressClock provides rememberCurrentEpochSeconds()) {
        ChannelsScreen(
            state = state,
            actions = ChannelsActions(
                onPlay = onWatch,
                onTag = { id -> channels.selectTag(id?.let { ChannelTagId(it.toLong()) }) },
                onRetry = { connection.reconnectNow() },
                // Until Home exists, connection settings open from the end of the Channels top bar.
                onOpenConnectionSettings = onOpenConnections,
                onSignIn = onSignIn,
                onOpenChannel = null, // The channel page will provide this action.
            ),
            destinations = AppDestinationList,
        )
    }
}

/** Keep the last rows across browse composition and configuration changes, not process death. */
private class ChannelsMappingCache(session: TvheadendSession) : ViewModel() {
    val artwork = ArtworkModels(session)
    var mapped by mutableStateOf<ChannelsMappingResult?>(null)
}

private data class ChannelsMappingKey(
    val scope: ChannelScopeState,
    val connection: ConnectionUiState,
    val degraded: Boolean,
    val format: ChannelsTextsFormat,
    val playingId: String?,
    val artwork: ArtworkModels?,
) {
    fun inputsAreCurrent(
        channels: ChannelsViewModel,
        connectionViewModel: AppConnectionViewModel,
        observation: SessionObservation,
    ): Boolean {
        if (scope !== channels.scope.value || observation !== channels.observation.value) return false
        val current = connectionViewModel.uiState.value
        return connection == current.connection && degraded == current.degraded
    }
}

private class ChannelsMappingResult(
    val key: ChannelsMappingKey,
    val observation: SessionObservation,
    val programs: List<ChannelRowPrograms>,
    val state: ChannelsState,
    val rows: Map<ChannelId, MappedChannelRow>,
    val atSec: Long,
    val nextBoundarySec: Long,
) {
    val nextWakeSec: Long get() = minOf(nextBoundarySec, nextChannelMinute(atSec))

    fun matchesDisplay(
        scope: ChannelScopeState,
        generation: SessionGenerationIdentity?,
        format: ChannelsTextsFormat,
    ): Boolean {
        val sameScope = key.scope.profileId == scope.profileId && key.scope.scope.activeTagId == scope.scope.activeTagId
        return sameScope && observation.currentSession?.generationIdentity === generation && key.format == format
    }
}

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private val ChannelsMappingDispatcher = Dispatchers.Default.limitedParallelism(1)

private fun nextChannelMinute(second: Long): Long =
    second + (SECONDS_PER_MINUTE - Math.floorMod(second, SECONDS_PER_MINUTE))

private suspend fun ChannelsMappingKey.map(
    context: android.content.Context,
    observation: SessionObservation,
    previous: ChannelsMappingResult?,
    now: Instant,
    reasons: List<String>,
    texts: ChannelsTexts,
): ChannelsMappingResult {
    val job = kotlin.coroutines.coroutineContext
    job.ensureActive()
    val nowSec = now.epochSeconds
    val started = context.artworkMappingStart()
    val programs = scope.scope.visibleChannels.map {
        job.ensureActive()
        observation.channelRowPrograms(it.id, now, includeDetails = false)
    }
    val boundary = programs.minOfOrNull { it.nextBoundary(now) } ?: Long.MAX_VALUE
    val projectionMs = started?.let { SystemClock.elapsedRealtime() - it }
    val sameRows = previous != null && reasons.all { it == "epg" } && previous.programs == programs &&
        observation.currentSession != null &&
        observation.channelCatalogForDisplay === previous.observation.channelCatalogForDisplay
    val rows = mutableMapOf<ChannelId, MappedChannelRow>()
    val state = if (sameRows) previous.state else {
        val previousRows = previous?.takeIf {
            it.key.scope.profileId == scope.profileId && it.observation.currentSession === observation.currentSession &&
                it.key.format == format && it.key.artwork === artwork && nowSec >= it.atSec
        }?.rows.orEmpty()
        val counts = if ("epg" in reasons) context.epgChangeCounts(previous?.observation, observation) else ""
        traceArtworkMapping(context, "Channels", reasons.joinToString("+") + counts, projectionMs) {
            val mapped = channelsState(
                scope, connection, degraded, observation, now, texts, playingId, artwork, programs, job::ensureActive,
                previousRows = previousRows, mappedRows = rows,
                includeDetails = false,
            )
            job.ensureActive()
            mapped
        }
    }
    return ChannelsMappingResult(this, observation, programs, state, if (sameRows) previous.rows else rows,
        nowSec, boundary)
}

private fun ChannelsMappingKey.mappingReasons(
    previous: ChannelsMappingResult?,
    observation: SessionObservation,
    nowSec: Long,
): List<String> {
    if (previous == null) return listOf("initial")
    return buildList {
        if (this@mappingReasons != previous.key ||
            observation.channelCatalogForDisplay !== previous.observation.channelCatalogForDisplay
        ) add("input")
        if (nowSec >= previous.nextBoundarySec) add("boundary")
        if (nowSec >= nextChannelMinute(previous.atSec) || nowSec < previous.atSec) add("minute")
        if (observation.epgSnapshotForDisplay?.events !== previous.observation.epgSnapshotForDisplay?.events) add("epg")
        if (observation.dvrSnapshotForDisplay !== previous.observation.dvrSnapshotForDisplay ||
            !observation.sameProgrammeAuthority(previous.observation)
        ) add("dvr")
    }
}

/** Only the selected destination and its header change inside the shared browse frame. */
@Suppress("LongParameterList") // One view model per destination.
@Composable
private fun AppDestinations(
    channelsCache: ChannelsMappingCache,
    dvr: DvrControls,
    connection: AppConnectionViewModel,
    channels: ChannelsViewModel,
    recordings: RecordingsViewModel,
    guide: GuideViewModel,
    playingId: String?,
    onWatch: ((String) -> Unit)?,
    onPlayRecording: ((String, Boolean) -> Unit)?,
    channelsDispatcher: CoroutineDispatcher,
    playingRecordingId: DvrEntryId?,
    onOpenConnections: () -> Unit,
    onSignIn: () -> Unit,
) {
    ConfirmDvrChange(dvr)
    CompositionLocalProvider(LocalArtworkModels provides channelsCache.artwork) {
        DestinationFrame { destination ->
            when (destination) {
                Destination.Recordings ->
                    RecordingsDestination(
                        recordings, connection, AppDestinationList, dvr, onOpenConnections, onSignIn, onPlayRecording,
                        playingRecordingId,
                    )
                Destination.Guide ->
                    GuideDestination(
                        guide, channels, connection, AppDestinationList, playingId, onWatch, dvr,
                        onOpenConnections, onSignIn,
                    )
                Destination.Channels ->
                    AppFrame(
                        connection, channels, playingId, onWatch, onOpenConnections, onSignIn,
                        channelsDispatcher, channelsCache,
                    )
            }
        }
    }
}

private val AppDestinationList = Destination.entries
