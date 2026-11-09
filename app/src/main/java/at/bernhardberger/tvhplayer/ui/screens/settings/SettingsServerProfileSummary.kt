package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvhplayer.R

private const val DEFAULT_PORT = 9982

/** A server's name without its label: the account on the address, or the address alone. */
@Composable
internal fun serverProfileUnnamed(profile: ServerProfileSummary): String {
    val address = serverAddress(profile)
    val username = profile.accountName()
    return if (username == null) address else stringResource(R.string.server_user_on_host, username, address)
}

@Composable
internal fun serverAddress(profile: ServerProfileSummary): String =
    if (profile.port == DEFAULT_PORT) profile.host
    else stringResource(R.string.server_host_port, profile.host, profile.port)

internal fun ServerProfileSummary.accountName(): String? =
    username?.takeIf { authenticationMode == ServerProfileAuthenticationMode.PASSWORD && it.isNotBlank() }

/** "<name> · <status>" for Settings' Connection row, or that no server is in use. */
@Composable
internal fun connectionRootStatus(profiles: ServerProfileListResult?, status: String): String {
    if (profiles.noServerInUse()) return stringResource(R.string.server_none_in_use)
    val active = profiles.activeProfile()
    return if (active == null) status
    else stringResource(R.string.server_detail_separator, active.label ?: serverProfileUnnamed(active), status)
}

/**
 * The connection that per-connection Settings values (channel groups, stream profile) belong to, or
 * null while at most one is stored, when nothing could be mistaken for another connection's.
 */
@Composable
internal fun connectionScopeName(profiles: ServerProfileListResult?): String? =
    (profiles as? ServerProfileListResult.Available)?.takeIf { it.profiles.size > 1 }
        ?.let { profiles.activeProfile() }?.let { serverProfileTitle(it) }

/** The name dialogs and the switch question use: the label, else the account on the address. */
@Composable
internal fun serverProfileTitle(profile: ServerProfileSummary): String = profile.label ?: serverProfileUnnamed(profile)
