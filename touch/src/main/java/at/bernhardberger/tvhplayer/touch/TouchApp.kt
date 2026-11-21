package at.bernhardberger.tvhplayer.touch

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryState
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.serverProfileDisplayName
import at.bernhardberger.tvhplayer.touch.connect.ActiveConnectionUiState
import at.bernhardberger.tvhplayer.touch.connect.ConnectionsScreen
import at.bernhardberger.tvhplayer.touch.connect.EditConnectionFlow
import at.bernhardberger.tvhplayer.touch.connect.FirstRunFlow
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.TouchFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

private enum class Page { Frame, Connections }

/**
 * The app's pages: first run while no connection is saved, else the frame with the Connections
 * page over it. First run stays until its save has finished, so the save is not cut short when
 * the saved connection appears.
 */
@Composable
internal fun TouchApp(
    session: TvheadendSession,
    owner: AppProfileOwner,
    discovery: () -> Flow<TvheadendDiscoveryState>,
) {
    val profile by owner.serverProfile.collectAsStateWithLifecycle()
    val profiles by owner.profiles.collectAsStateWithLifecycle()
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
        page == Page.Connections -> {
            val active = (profiles as? ServerProfileListResult.Available)?.let { list ->
                list.profiles.firstOrNull { it.id == list.activeId }
            }
            ConnectionsPage(session, owner, active, onBack = { page = Page.Frame }, rename = rename)
        }
        else -> TouchFrame(
            actions = { destination ->
                // Until Home exists, connection settings open from the end of the Channels top bar.
                if (destination == Destination.Channels) {
                    IconButton(onClick = { page = Page.Connections }, modifier = Modifier.padding(end = 4.dp)) {
                        Icon(
                            painterResource(R.drawable.channels_settings),
                            stringResource(R.string.channels_connection_settings),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun ConnectionsPage(
    session: TvheadendSession,
    owner: AppProfileOwner,
    active: ServerProfileSummary?,
    onBack: () -> Unit,
    rename: (String?, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf(false) }
    ConnectionsScreen(
        active = active?.uiState(),
        onBack = onBack,
        onEdit = { editing = true },
        onRemove = { scope.launch { if (active != null) owner.remove(active.id) } },
    )
    if (editing && active != null) {
        EditConnectionFlow(
            session, owner, name = active.label.orEmpty(),
            onClose = { editing = false },
            onSaved = { name ->
                editing = false
                rename(active.label, name)
            },
        )
    }
}

private fun ServerProfileSummary.uiState() = ActiveConnectionUiState(
    name = serverProfileDisplayName(this),
    address = host,
    port = port,
    username = username.takeIf { authenticationMode == ServerProfileAuthenticationMode.PASSWORD }.orEmpty(),
)
