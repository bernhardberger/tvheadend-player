package at.bernhardberger.tvhplayer.touch

import at.bernhardberger.tvhplayer.touch.ui.ConfirmDvrChange
import at.bernhardberger.tvhplayer.touch.ui.DvrControls
import androidx.compose.runtime.remember
import at.bernhardberger.tvhplayer.touch.ui.ArtworkScreenShown
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.touch.channels.ChannelRowPrograms
import at.bernhardberger.tvhplayer.touch.channels.LocalChannelProgressClock
import at.bernhardberger.tvhplayer.touch.channels.ChannelsActions
import at.bernhardberger.tvhplayer.touch.channels.ChannelsScreen
import at.bernhardberger.tvhplayer.touch.channels.ChannelsState
import at.bernhardberger.tvhplayer.touch.channels.ChannelsTexts
import at.bernhardberger.tvhplayer.touch.channels.channelRowPrograms
import at.bernhardberger.tvhplayer.touch.channels.sameProgrammeAuthority
import at.bernhardberger.tvhplayer.touch.channels.channelsState
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
import at.bernhardberger.tvhplayer.touch.ui.LocalFrameSelection
import at.bernhardberger.tvhplayer.touch.guide.GuideDestination
import at.bernhardberger.tvhplayer.touch.guide.GuideViewModel
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsDestination
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsViewModel
import at.bernhardberger.tvhplayer.touch.ui.epgChangeCounts
import at.bernhardberger.tvhplayer.touch.ui.artworkMappingStart
import android.os.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.Flow

/** [SignIn] is the Connections page opened on the editor, for a password that can't be read. */
private enum class Page { Frame, Connections, SignIn }

/**
 * The app's pages: first run while no connection is saved, else the frame with the Connections
 * page over it. First run stays until its save has finished, so the save is not cut short when
 * the saved connection appears.
 */
@Suppress("LongParameterList") // The app's owners and view models, handed in by the activity.
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
    val connect = viewModel { ConnectViewModel(session, owner) }
    val dvr = viewModel { DvrControls(session, notices) }
    SideEffect { connect.discovery = discovery }
    var page by rememberSaveable { mutableStateOf(Page.Frame) }
    // First run follows the settled profile: a removal's passing "no connection" does not open it,
    // and it stays until its own save and naming have finished.
    val firstRun = (profile == ServerProfileReadResult.Missing && !connect.removing) || connect.firstRunSettling
    LaunchedEffect(firstRun) {
        // Whatever was open belonged to the removed connection; nothing stays behind first run.
        if (firstRun) page = Page.Frame else connect.releaseFirstRun()
    }
    FrameNotices(notices, owner, session) {
        when {
            profile == null -> Surface(Modifier.fillMaxSize()) {}
            firstRun -> Box(Modifier.fillMaxSize()) {
                FirstRunFlow(connect.firstRun())
                BottomWindowNotices()
            }
            page != Page.Frame -> {
                val active = (profiles as? ServerProfileListResult.Available)?.let { list ->
                    list.profiles.firstOrNull { it.id == list.activeId }
                }
                ConnectionsPage(
                    session, owner, connect, active?.uiState(connectionStatus(profile, presentation)), active,
                    listed = profiles is ServerProfileListResult.Available,
                    signIn = page == Page.SignIn,
                    onBack = { page = Page.Frame },
                    onSaved = { page = Page.Connections },
                )
            }
            else -> AppDestinations(
                session, dvr, connection, channels, recordings, guide, playingId, onWatch, onPlayRecording,
                channelsDispatcher = channelsDispatcher,
                playingRecordingId = playingRecordingId,
                onOpenConnections = { page = Page.Connections },
                onSignIn = { page = Page.SignIn },
            )
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
    dvr: DvrControls,
    onOpenConnections: () -> Unit,
    onSignIn: () -> Unit,
    mappingDispatcher: CoroutineDispatcher,
) {
    ArtworkScreenShown("Channels")
    val presentation = connection.uiState.collectAsStateWithLifecycle().value
    val scope = channels.scope.collectAsStateWithLifecycle().value
    // The scope is republished for channel changes only; the guide comes with every observation.
    val observation = channels.observation.collectAsStateWithLifecycle().value
    val texts = rememberChannelsTexts()
    val artwork = LocalArtworkModels.current
    val context = LocalContext.current.applicationContext
    val key = ChannelsMappingKey(scope, presentation.connection, presentation.degraded, texts, playingId, artwork)
    val mapped by produceState<ChannelsMappingResult?>(null, key, observation) {
        while (true) {
            // A clock wake and a data update in the same UI turn need only the newest inputs.
            yield()
            val previous = value
            val result = withContext(mappingDispatcher) { key.map(context, observation, previous) }
            value = result
            delay((result.nextWakeSec * MILLIS_PER_SECOND - System.currentTimeMillis()).coerceAtLeast(1L))
        }
    }
    val state = mapped?.takeIf { it.key.scope.profileId == scope.profileId }?.state ?: ChannelsState.Loading
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
                onRecordControl = dvr::channelRecordControl,
            ),
            destinations = AppDestinationList,
        )
    }
}

private data class ChannelsMappingKey(
    val scope: ChannelScopeState,
    val connection: ConnectionUiState,
    val degraded: Boolean,
    val texts: ChannelsTexts,
    val playingId: String?,
    val artwork: ArtworkModels?,
)

private class ChannelsMappingResult(
    val key: ChannelsMappingKey,
    val observation: SessionObservation,
    val programs: List<ChannelRowPrograms>,
    val state: ChannelsState,
    val atSec: Long,
    val nextBoundarySec: Long,
) {
    val nextWakeSec: Long get() = minOf(nextBoundarySec, nextChannelMinute(atSec))
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
): ChannelsMappingResult {
    val job = kotlin.coroutines.coroutineContext
    val now = Instant.fromEpochSeconds(System.currentTimeMillis() / MILLIS_PER_SECOND)
    val nowSec = now.epochSeconds
    val started = context.artworkMappingStart()
    val programs = scope.scope.visibleChannels.map {
        job.ensureActive()
        observation.channelRowPrograms(it.id, now)
    }
    val boundary = programs.minOfOrNull { it.nextBoundary(now) } ?: Long.MAX_VALUE
    val projectionMs = started?.let { SystemClock.elapsedRealtime() - it }
    val reasons = mappingReasons(previous, observation, nowSec)
    val sameRows = previous != null && reasons.all { it == "epg" } && previous.programs == programs
    val state = if (sameRows) previous.state else {
        val counts = if ("epg" in reasons) context.epgChangeCounts(previous?.observation, observation) else ""
        traceArtworkMapping(context, "Channels", reasons.joinToString("+") + counts, projectionMs) {
            val mapped = channelsState(
                scope, connection, degraded, observation, now, texts, playingId, artwork, programs, job::ensureActive,
            )
            job.ensureActive()
            mapped
        }
    }
    return ChannelsMappingResult(this, observation, programs, state, nowSec, boundary)
}

private fun ChannelsMappingKey.mappingReasons(
    previous: ChannelsMappingResult?,
    observation: SessionObservation,
    nowSec: Long,
): List<String> {
    if (previous == null) return listOf("initial")
    return buildList {
        if (this@mappingReasons != previous.key) add("input")
        if (nowSec >= previous.nextBoundarySec) add("boundary")
        if (nowSec >= nextChannelMinute(previous.atSec) || nowSec < previous.atSec) add("minute")
        if (observation.epgSnapshotForDisplay?.events !== previous.observation.epgSnapshotForDisplay?.events) add("epg")
        if (observation.dvrSnapshotForDisplay !== previous.observation.dvrSnapshotForDisplay ||
            !observation.sameProgrammeAuthority(previous.observation)
        ) add("dvr")
    }
}

/** Each destination's screen draws the frame, so the selection is kept here and picks the screen. */
@Suppress("LongParameterList") // One view model per destination.
@Composable
private fun AppDestinations(
    session: TvheadendSession,
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
    val selection = rememberSaveable { mutableStateOf(Destination.Channels) }
    val artwork = remember(session) { ArtworkModels(session) }
    ConfirmDvrChange(dvr)
    CompositionLocalProvider(LocalFrameSelection provides selection, LocalArtworkModels provides artwork) {
        when (selection.value) {
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
                AppFrame(connection, channels, playingId, onWatch, dvr, onOpenConnections, onSignIn, channelsDispatcher)
        }
    }
}

private val AppDestinationList = Destination.entries
