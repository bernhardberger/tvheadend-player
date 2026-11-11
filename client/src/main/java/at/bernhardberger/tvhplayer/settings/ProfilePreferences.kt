package at.bernhardberger.tvhplayer.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.DEFAULT_HTSP_PORT
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary

internal val activeProfileKey = stringPreferencesKey("activeServerProfileId")
internal fun profilePreferencePrefix(id: ServerProfileId): String = "server:${id.value}:"
internal fun Preferences.activeProfileId(): ServerProfileId? = this[activeProfileKey]?.let(ServerProfileId::from)

/**
 * One line that names a server without its label: `user@host`, or `host` without a sign-in, with
 * `:port` only when the port isn't the default. An IPv6 literal host is bracketed.
 */
fun compactServerName(host: String, port: Int, username: String?): String {
    val name = if (':' in host && !host.startsWith('[')) "[$host]" else host
    val address = if (port == DEFAULT_HTSP_PORT) name else "$name:$port"
    return username?.takeIf { it.isNotBlank() }?.let { "$it@$address" } ?: address
}

/** [compactServerName] of a stored server; the account counts only for a password sign-in. */
fun serverProfileAddress(profile: ServerProfileSummary): String = compactServerName(
    profile.host, profile.port,
    profile.username.takeIf { profile.authenticationMode == ServerProfileAuthenticationMode.PASSWORD },
)

/** The name Settings and notices give a connection: its label, else [serverProfileAddress]. */
fun serverProfileDisplayName(profile: ServerProfileSummary): String =
    profile.label?.takeIf { it.isNotBlank() } ?: serverProfileAddress(profile)

fun existingServerProfile(
    profiles: List<ServerProfileSummary>,
    host: String,
    port: Int,
    username: String?,
): ServerProfileId? = profiles.firstOrNull {
    it.host == host.trim() && it.port == port && it.username == username?.trim() &&
        (it.authenticationMode == ServerProfileAuthenticationMode.ANONYMOUS) == (username == null)
}?.id
