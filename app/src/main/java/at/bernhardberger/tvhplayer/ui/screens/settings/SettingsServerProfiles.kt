package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.compactServerName
import at.bernhardberger.tvhplayer.settings.serverProfileAddress
import at.bernhardberger.tvhplayer.settings.serverProfileDisplayName
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.components.ServerAvatar
import at.bernhardberger.tvhplayer.ui.components.ServerAvatarRowSize
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.components.depth.DepthRow
import at.bernhardberger.tvhplayer.ui.components.serverAvatarLook

internal const val ADD_SERVER = "add-server"
internal const val SERVER_USE = "server-use"
internal const val SERVER_PASSWORD = "server-password"
internal const val SERVER_NAME = "server-name"
internal const val SERVER_REMOVE = "server-remove"
internal const val SERVER_ADDRESS = "server-address"
internal const val SERVER_ACCOUNT = "server-account"

/** A stored server's row on the Connection list and the id of the level it opens. */
internal fun serverProfileRowId(id: ServerProfileId): String = "server-${id.value}"

/** What the Connection list and a server's level can ask Settings to do. */
@Immutable
internal class ServerProfileActions(
    val onUse: (ServerProfileSummary) -> Unit = {},
    val onEnterPassword: (ServerProfileSummary) -> Unit = {},
    val onSetUp: (ServerProfileSummary) -> Unit = {},
    val onName: (ServerProfileSummary) -> Unit = {},
    val onRemove: (ServerProfileSummary) -> Unit = {},
    val onAdd: () -> Unit = {},
)

/** True when servers are stored but none is in use, so nothing connects or plays. */
internal fun ServerProfileListResult?.noServerInUse(): Boolean =
    this is ServerProfileListResult.Available && profiles.isNotEmpty() && activeId == null

/** True once the stored list is read and holds at least one server. */
internal fun ServerProfileListResult?.serversStored(): Boolean =
    this is ServerProfileListResult.Available && profiles.isNotEmpty()

/** The profile in use, if any. */
internal fun ServerProfileListResult?.activeProfile(): ServerProfileSummary? =
    (this as? ServerProfileListResult.Available)?.let { list -> list.profiles.firstOrNull { it.id == list.activeId } }

private fun ServerProfileSummary.passwordNeeded(): Boolean =
    authenticationMode == ServerProfileAuthenticationMode.PASSWORD && !credentialsAvailable

/**
 * The Connection list (frames 09, 12) and one level per stored server (frame 03). The list keeps
 * the stored order. It opens on the server in use, or on the first server with the line that none
 * is in use. The server in use shows its status when it isn't connected. Loading shows no rows; an
 * unreadable list offers adding.
 */
@Composable
internal fun settingsServerLevels(
    profiles: ServerProfileListResult?,
    status: ConnectionOverviewStatus,
    actions: ServerProfileActions,
    showRetry: Boolean = false,
    connecting: Boolean = false,
    onRetry: () -> Unit = {},
    switching: Boolean = false,
): List<DepthLevel> {
    val list = profiles as? ServerProfileListResult.Available
    val stored = list?.profiles.orEmpty()
    val noneInUse = profiles.noServerInUse()
    val add = settingsRow(ADD_SERVER, stringResource(R.string.server_add),
        supporting = stringResource(R.string.server_add_description), icon = R.drawable.ic_add,
        onClick = actions.onAdd)
    val retry = settingsRow(RETRY_CONNECTION, stringResource(R.string.connection_try_again),
        supporting = stringResource(
            if (connecting) R.string.connection_overview_connecting else R.string.connection_retry_description,
        ),
        icon = R.drawable.ic_refresh, onClick = { if (!connecting) onRetry() })
    val rows = buildList {
        stored.forEach { profile -> add(serverProfileRow(profile, profile.id == list?.activeId, status,
            cancelSwitch = if (switching && profile.id == list?.activeId) ({ actions.onUse(profile) }) else null)) }
        if (profiles != null) add(add)
        if (showRetry && list?.activeId != null) add(retry)
    }
    val initial = stored.firstOrNull { it.id == list?.activeId } ?: stored.firstOrNull()
    val connection = settingsLevel(SettingsSection.CONNECTION.name, stringResource(R.string.server_list_title), rows,
        initialItemId = initial?.let { serverProfileRowId(it.id) },
        description = when {
            noneInUse -> stringResource(R.string.server_none_in_use_description)
            profiles == ServerProfileListResult.Unavailable -> stringResource(R.string.server_list_unavailable)
            else -> null
        })
    return listOf(connection) + stored.map { settingsServerLevel(it, it.id == list?.activeId, actions) }
}

@Composable
private fun serverProfileRow(
    profile: ServerProfileSummary,
    active: Boolean,
    status: ConnectionOverviewStatus,
    cancelSwitch: (() -> Unit)? = null,
): DepthRow {
    // The server in use wears the avatar badge while it is connected, else it also says what is
    // wrong or under way with it.
    val problem = profile.passwordNeeded() || (active && status.state == ConnectionOverviewState.OFFLINE)
    val state = listOfNotNull(
        status.title.takeIf { active && status.state != ConnectionOverviewState.CONNECTED },
        stringResource(R.string.server_password_needed).takeIf { profile.passwordNeeded() },
    )
    // The title is the label, else the account, else the address; the line under it is what the
    // title leaves out: the account on the address of a labeled server, else the address.
    val address = compactServerName(profile.host, profile.port, null)
    val title = profile.label ?: profile.accountName() ?: address
    val detail = when {
        profile.label != null -> serverProfileAddress(profile)
        profile.accountName() != null -> address
        else -> stringResource(R.string.server_no_sign_in)
    }
    val look = serverAvatarLook(profile)
    val rowId = serverProfileRowId(profile.id)
    return settingsRow(rowId, title, detail, titleMaxLines = 1,
        child = rowId.takeIf { cancelSwitch == null }, onClick = { cancelSwitch?.invoke() },
        leading = { focused ->
            // Focus inverts the row, so the badge's ring takes the focused surface.
            val surface = MaterialTheme.colorScheme.run { if (focused) inverseSurface else background }
            ServerAvatar(look, ServerAvatarRowSize, inUse = active, badgeRing = surface)
        },
        supportingContent = { focused -> ServerRowDetail(detail, state, problem, focused) })
}

/** The lines under a server's title: its address, then the state. Each stays on one line. */
@Composable
private fun ServerRowDetail(detail: String, state: List<String>, problem: Boolean, focused: Boolean) {
    Column {
        Text(detail, style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = .75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        // The accent marks the state on a resting row. Focus inverts the row, so there it takes the
        // focused content color the shared Settings rows give their supporting text.
        val accent = when {
            focused -> LocalContentColor.current
            problem -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }
        state.forEach { text -> Text(text, style = MaterialTheme.typography.labelSmall, color = accent,
            maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/**
 * One server's level: its actions first, then what it connects to, as focusable rows without an
 * action. "Use this connection" switches to this one and is absent on the connection already in use.
 */
@Composable
internal fun settingsServerLevel(
    profile: ServerProfileSummary,
    active: Boolean,
    actions: ServerProfileActions,
): DepthLevel {
    val username = profile.accountName()
    val rows = buildList {
        // An unreadable password can't connect: Enter password is how that server gets used.
        if (!active && !profile.passwordNeeded()) add(settingsRow(SERVER_USE, stringResource(R.string.server_use),
            supporting = if (username == null) stringResource(R.string.server_use_anonymous)
                else stringResource(R.string.server_use_sign_in, username),
            onClick = { actions.onUse(profile) }))
        if (profile.passwordNeeded()) add(settingsRow(SERVER_PASSWORD, stringResource(R.string.server_enter_password),
            supporting = stringResource(R.string.server_password_needed),
            onClick = { actions.onEnterPassword(profile) }))
        add(settingsRow(EDIT_CONNECTION, stringResource(R.string.setup_connection),
            supporting = stringResource(R.string.setup_connection_description),
            onClick = { actions.onSetUp(profile) }))
        add(settingsRow(SERVER_NAME, stringResource(R.string.server_name),
            supporting = profile.label ?: stringResource(R.string.server_name_not_set),
            trailingIcon = R.drawable.ic_edit, onClick = { actions.onName(profile) }))
        add(settingsRow(SERVER_REMOVE, stringResource(R.string.server_remove),
            supporting = stringResource(R.string.server_remove_description),
            trailingIcon = R.drawable.ic_delete_outlined, onClick = { actions.onRemove(profile) }))
        add(settingsRow(SERVER_ADDRESS, stringResource(R.string.connection_overview_server),
            serverAddress(profile)))
        add(settingsRow(SERVER_ACCOUNT, stringResource(R.string.server_account),
            username ?: stringResource(R.string.server_no_sign_in)))
    }
    return settingsLevel(serverProfileRowId(profile.id), serverProfileDisplayName(profile), rows)
}
