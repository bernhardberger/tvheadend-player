package at.bernhardberger.tvhplayer.touch

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryState
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.serverProfileDisplayName
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.touch.channels.ChannelsActions
import at.bernhardberger.tvhplayer.touch.channels.ChannelsScreen
import at.bernhardberger.tvhplayer.touch.channels.channelsState
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import kotlin.time.Clock
import at.bernhardberger.tvhplayer.touch.connect.ActiveConnectionUiState
import at.bernhardberger.tvhplayer.touch.connect.ConnectionStatus
import at.bernhardberger.tvhplayer.touch.connect.ConnectionsScreen
import at.bernhardberger.tvhplayer.touch.connect.EditConnectionFlow
import at.bernhardberger.tvhplayer.touch.connect.FirstRunFlow
import at.bernhardberger.tvhplayer.touch.connect.connectionStatus
import at.bernhardberger.tvhplayer.touch.ui.Destination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/** [SignIn] is the Connections page opened on the editor, for a password that can't be read. */
private enum class Page { Frame, Connections, SignIn }

/**
 * The app's pages: first run while no connection is saved, else the frame with the Connections
 * page over it. First run stays until its save has finished, so the save is not cut short when
 * the saved connection appears.
 */
@Composable
internal fun TouchApp(
    session: TvheadendSession,
    owner: AppProfileOwner,
    connection: AppConnectionViewModel,
    channels: ChannelsViewModel,
    discovery: () -> Flow<TvheadendDiscoveryState>,
) {
    val profile by owner.serverProfile.collectAsStateWithLifecycle()
    val profiles by owner.profiles.collectAsStateWithLifecycle()
    val presentation by connection.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var firstRun by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(Page.Frame) }
    LaunchedEffect(profile) {
        if (profile == ServerProfileReadResult.Missing) {
            firstRun = true
            // Whatever was open belonged to the removed connection; nothing stays behind first run.
            page = Page.Frame
        }
    }
    val rename: (String?, String) -> Unit = { previous, name ->
        if (name.trim() != previous.orEmpty().trim()) scope.launch {
            val id = owner.profiles.mapNotNull { (it as? ServerProfileListResult.Available)?.activeId }.first()
            owner.rename(id, name)
        }
    }
    when {
        profile == null -> Surface(Modifier.fillMaxSize()) {}
        profile == ServerProfileReadResult.Missing || firstRun -> FirstRunFlow(session, owner, discovery) { name ->
            firstRun = false
            page = Page.Frame
            rename(null, name)
        }
        page != Page.Frame -> {
            val active = (profiles as? ServerProfileListResult.Available)?.let { list ->
                list.profiles.firstOrNull { it.id == list.activeId }
            }
            ConnectionsPage(
                session, owner, active?.uiState(connectionStatus(profile, presentation)), active,
                signIn = page == Page.SignIn,
                onBack = { page = Page.Frame },
                onSaved = { name ->
                    page = Page.Connections
                    rename(active?.label, name)
                },
            )
        }
        else -> AppFrame(
            connection, channels,
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
    card: ActiveConnectionUiState?,
    active: ServerProfileSummary?,
    signIn: Boolean,
    onBack: () -> Unit,
    onSaved: (name: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf(false) }
    ConnectionsScreen(
        active = card,
        onBack = onBack,
        onEdit = { editing = true },
        onRemove = { scope.launch { if (active != null) owner.remove(active.id) } },
    )
    if ((editing || signIn) && active != null) {
        EditConnectionFlow(
            session, owner, name = active.label.orEmpty(),
            onClose = { if (signIn) onBack() else editing = false },
            onSaved = { name ->
                editing = false
                onSaved(name)
            },
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
 * The frame with only Channels wired: the destinations without data are not offered yet, and the
 * row controls with nothing behind them (Watch, Record, Details, Show in Guide) are left out.
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
        destinations = listOf(Destination.Channels),
    )
}
