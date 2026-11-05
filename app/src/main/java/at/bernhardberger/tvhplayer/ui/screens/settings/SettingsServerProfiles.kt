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

private const val DEFAULT_PORT = 9982

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

/** The Connection list's status line: the connection's own status, or that no server is in use. */
internal fun serverListStatus(profiles: ServerProfileListResult?, connectionStatus: String, noneInUse: String): String =
    if (profiles.noServerInUse()) noneInUse else connectionStatus

/** True when servers are stored but none is in use, so nothing connects or plays. */
internal fun ServerProfileListResult?.noServerInUse(): Boolean =
    this is ServerProfileListResult.Available && profiles.isNotEmpty() && activeId == null

/** True once the stored list is read and holds at least one server. */
internal fun ServerProfileListResult?.serversStored(): Boolean =
    this is ServerProfileListResult.Available && profiles.isNotEmpty()

/** The profile in use, if any. */
internal fun ServerProfileListResult?.activeProfile(): ServerProfileSummary? =
    (this as? ServerProfileListResult.Available)?.let { list -> list.profiles.firstOrNull { it.id == list.activeId } }

/** A server's name without its label: the account on the address, or the address alone. */
@Composable
internal fun serverProfileUnnamed(profile: ServerProfileSummary): String {
    val address = serverAddress(profile)
    val username = profile.accountName()
    return if (username == null) address else stringResource(R.string.server_user_on_host, username, address)
}

@Composable
private fun serverAddress(profile: ServerProfileSummary): String =
    if (profile.port == DEFAULT_PORT) profile.host else stringResource(R.string.server_host_port, profile.host, profile.port)

private fun ServerProfileSummary.accountName(): String? =
    username?.takeIf { authenticationMode == ServerProfileAuthenticationMode.PASSWORD && it.isNotBlank() }

private fun ServerProfileSummary.passwordNeeded(): Boolean =
    authenticationMode == ServerProfileAuthenticationMode.PASSWORD && !credentialsAvailable

/**
 * The Connection list (frames 09, 12) and one level per stored server (frame 03). The list keeps
 * the stored order. It opens on the server in use, or on the first server with the line that none
 * is in use. Loading shows only the status; an unreadable list offers adding and the status.
 */
@Composable
internal fun settingsServerLevels(
    profiles: ServerProfileListResult?,
    status: String,
    actions: ServerProfileActions,
    showRetry: Boolean = false,
    connecting: Boolean = false,
    onRetry: () -> Unit = {},
    switching: Boolean = false,
): List<DepthLevel> {
    val list = profiles as? ServerProfileListResult.Available
    val stored = list?.profiles.orEmpty()
    val noneInUse = profiles.noServerInUse()
    val statusRow = settingsRow(CONNECTION_STATUS, stringResource(R.string.connection_overview_status),
        serverListStatus(profiles, status, stringResource(R.string.server_none_in_use)), icon = R.drawable.ic_cloud)
    val add = settingsRow(ADD_SERVER, stringResource(R.string.server_add),
        supporting = stringResource(R.string.server_add_description), icon = R.drawable.ic_add,
        trailingIcon = R.drawable.ic_arrow_forward, onClick = actions.onAdd)
    val retry = settingsRow(RETRY_CONNECTION, stringResource(R.string.connection_try_again),
        supporting = stringResource(if (connecting) R.string.connection_overview_connecting else R.string.connection_retry_description),
        icon = R.drawable.ic_refresh, onClick = { if (!connecting) onRetry() })
    val rows = buildList {
        stored.forEach { profile -> add(serverProfileRow(profile, profile.id == list?.activeId,
            cancelSwitch = if (switching && profile.id == list?.activeId) ({ actions.onUse(profile) }) else null)) }
        if (profiles != null) add(add)
        if (showRetry && list?.activeId != null) add(retry)
        add(statusRow)
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
private fun serverProfileRow(profile: ServerProfileSummary, active: Boolean, cancelSwitch: (() -> Unit)? = null): DepthRow {
    val state = listOfNotNull(
        stringResource(R.string.server_in_use).takeIf { active },
        stringResource(R.string.server_password_needed).takeIf { profile.passwordNeeded() },
    )
    val detail = when {
        profile.label != null -> serverProfileUnnamed(profile)
        profile.accountName() == null -> stringResource(R.string.server_no_sign_in)
        else -> null
    }
    val supporting = (listOfNotNull(detail) + state).joinToString("\n").takeIf { it.isNotEmpty() }
    val look = serverAvatarLook(profile)
    val rowId = serverProfileRowId(profile.id)
    return settingsRow(rowId, profile.label ?: serverProfileUnnamed(profile), supporting,
        child = rowId.takeIf { cancelSwitch == null }, onClick = { cancelSwitch?.invoke() },
        leading = { ServerAvatar(look, ServerAvatarRowSize) },
        supportingContent = supporting?.let { {
            Column {
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = .75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                state.forEach { text -> Text(text, style = MaterialTheme.typography.labelSmall,
                    color = if (profile.passwordNeeded()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        } })
}

/** One server's level: its actions first, then what it connects to, as focusable rows without an action. */
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
            trailingIcon = R.drawable.ic_arrow_forward, onClick = { actions.onUse(profile) }))
        if (profile.passwordNeeded()) add(settingsRow(SERVER_PASSWORD, stringResource(R.string.server_enter_password),
            supporting = stringResource(R.string.server_password_needed),
            trailingIcon = R.drawable.ic_arrow_forward, onClick = { actions.onEnterPassword(profile) }))
        add(settingsRow(EDIT_CONNECTION, stringResource(R.string.setup_connection),
            supporting = stringResource(R.string.setup_connection_description),
            trailingIcon = R.drawable.ic_arrow_forward, onClick = { actions.onSetUp(profile) }))
        add(settingsRow(SERVER_NAME, stringResource(R.string.server_name),
            supporting = profile.label ?: stringResource(R.string.server_name_not_set),
            trailingIcon = R.drawable.ic_arrow_forward, onClick = { actions.onName(profile) }))
        add(settingsRow(SERVER_REMOVE, stringResource(R.string.server_remove),
            supporting = stringResource(R.string.server_remove_description),
            trailingIcon = R.drawable.ic_delete_outlined, onClick = { actions.onRemove(profile) }))
        add(settingsRow(SERVER_ADDRESS, stringResource(R.string.connection_overview_server),
            serverAddress(profile)))
        add(settingsRow(SERVER_ACCOUNT, stringResource(R.string.server_account),
            username ?: stringResource(R.string.server_no_sign_in)))
    }
    return settingsLevel(serverProfileRowId(profile.id), profile.label ?: serverProfileUnnamed(profile), rows)
}

/** "<name> · <status>" for Settings' Connection row, or that no server is in use. */
@Composable
internal fun connectionRootStatus(profiles: ServerProfileListResult?, status: String): String {
    if (profiles.noServerInUse()) return stringResource(R.string.server_none_in_use)
    val active = profiles.activeProfile() ?: return status
    return stringResource(R.string.server_detail_separator, active.label ?: serverProfileUnnamed(active), status)
}

/** The name dialogs and the switch question use: the label, else the account on the address. */
@Composable
internal fun serverProfileTitle(profile: ServerProfileSummary): String = profile.label ?: serverProfileUnnamed(profile)
