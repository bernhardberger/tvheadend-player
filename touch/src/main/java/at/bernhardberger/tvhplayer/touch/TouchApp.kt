package at.bernhardberger.tvhplayer.touch

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import at.bernhardberger.tvhplayer.touch.channels.ChannelsActions
import at.bernhardberger.tvhplayer.touch.channels.ChannelsScreen
import at.bernhardberger.tvhplayer.touch.channels.channelsState
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import kotlin.time.Clock
import at.bernhardberger.tvhplayer.touch.connect.ActiveConnectionUiState
import at.bernhardberger.tvhplayer.touch.connect.ConnectViewModel
import at.bernhardberger.tvhplayer.touch.connect.ConnectionStatus
import at.bernhardberger.tvhplayer.touch.connect.ConnectionsScreen
import at.bernhardberger.tvhplayer.touch.connect.EditConnectionFlow
import at.bernhardberger.tvhplayer.touch.connect.FirstRunFlow
import at.bernhardberger.tvhplayer.touch.connect.connectionStatus
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.LocalFrameSelection
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsDestination
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsViewModel
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
    discovery: () -> Flow<TvheadendDiscoveryState>,
) {
    val profile by owner.serverProfile.collectAsStateWithLifecycle()
    val profiles by owner.profiles.collectAsStateWithLifecycle()
    val presentation by connection.uiState.collectAsStateWithLifecycle()
    val connect = viewModel { ConnectViewModel(session, owner) }
    SideEffect { connect.discovery = discovery }
    var page by rememberSaveable { mutableStateOf(Page.Frame) }
    // First run follows the settled profile: a removal's passing "no connection" does not open it,
    // and it stays until its own save and naming have finished.
    val firstRun = (profile == ServerProfileReadResult.Missing && !connect.removing) || connect.firstRunSettling
    LaunchedEffect(firstRun) {
        // Whatever was open belonged to the removed connection; nothing stays behind first run.
        if (firstRun) page = Page.Frame else connect.releaseFirstRun()
    }
    when {
        profile == null -> Surface(Modifier.fillMaxSize()) {}
        firstRun -> FirstRunFlow(connect.firstRun())
        page != Page.Frame -> {
            val active = (profiles as? ServerProfileListResult.Available)?.let { list ->
                list.profiles.firstOrNull { it.id == list.activeId }
            }
            ConnectionsPage(
                session, owner, connect, active?.uiState(connectionStatus(profile, presentation)), active,
                signIn = page == Page.SignIn,
                onBack = { page = Page.Frame },
                onSaved = { page = Page.Connections },
            )
        }
        else -> AppDestinations(
            connection, channels, recordings,
            onOpenConnections = { page = Page.Connections },
            onSignIn = { page = Page.SignIn },
        )
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
    signIn: Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    ConnectionsScreen(
        active = card,
        onBack = onBack,
        onEdit = { editing = true },
        onRemove = { if (active != null) connect.remove(active.id) },
    )
    val editorShown = (editing || signIn) && active != null
    LaunchedEffect(editorShown) { if (!editorShown) connect.releaseEditor() }
    if (editorShown && active != null) {
        val restoreSaved = rememberSavedConnectionRestore(session, owner)
        val setup = connect.editor(active.label.orEmpty(), requireSignIn = signIn, restoreSaved)
        // The editor closes once the change and its name are saved.
        LaunchedEffect(setup, setup.finished) {
            if (setup.finished) {
                editing = false
                onSaved()
            }
        }
        EditConnectionFlow(
            setup,
            onClose = { if (signIn) onBack() else editing = false },
            focusPassword = signIn,
        )
    }
}

private fun ServerProfileSummary.uiState(status: ConnectionStatus) = ActiveConnectionUiState(
    name = serverProfileDisplayName(this),
    address = host,
    port = port,
    username = username.takeIf { authenticationMode == ServerProfileAuthenticationMode.PASSWORD }.orEmpty(),
    status = status,
)

/**
 * The frame with Channels and Recordings wired: the destinations without data are not offered yet,
 * and the row controls with nothing behind them (Watch, Record, Details, Show in Guide) are left out.
 */
@Composable
private fun AppFrame(
    connection: AppConnectionViewModel,
    channels: ChannelsViewModel,
    onOpenConnections: () -> Unit,
    onSignIn: () -> Unit,
) {
    val presentation by connection.uiState.collectAsStateWithLifecycle()
    val scope by channels.scope.collectAsStateWithLifecycle()
    // The scope is republished for channel changes only; the guide comes with every observation.
    val observation by channels.observation.collectAsStateWithLifecycle()
    val state = channelsState(scope, presentation.connection, presentation.degraded, observation, Clock.System.now())
    ChannelsScreen(
        state = state,
        actions = ChannelsActions(
            onPlay = null,
            onTag = { id -> channels.selectTag(id?.let { ChannelTagId(it.toLong()) }) },
            onRetry = { connection.reconnectNow() },
            // Until Home exists, connection settings open from the end of the Channels top bar.
            onOpenConnectionSettings = onOpenConnections,
            onSignIn = onSignIn,
        ),
        destinations = AppDestinationList,
    )
}

/** Each destination's screen draws the frame, so the selection is kept here and picks the screen. */
@Composable
private fun AppDestinations(
    connection: AppConnectionViewModel,
    channels: ChannelsViewModel,
    recordings: RecordingsViewModel,
    onOpenConnections: () -> Unit,
    onSignIn: () -> Unit,
) {
    val selection = rememberSaveable { mutableStateOf(Destination.Channels) }
    CompositionLocalProvider(LocalFrameSelection provides selection) {
        when (selection.value) {
            Destination.Recordings ->
                RecordingsDestination(recordings, connection, AppDestinationList, onOpenConnections, onSignIn)
            else -> AppFrame(connection, channels, onOpenConnections, onSignIn)
        }
    }
}

private val AppDestinationList = listOf(Destination.Channels, Destination.Recordings)
