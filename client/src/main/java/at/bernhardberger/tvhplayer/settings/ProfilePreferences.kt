package at.bernhardberger.tvhplayer.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary

internal val activeProfileKey = stringPreferencesKey("activeServerProfileId")
internal fun profilePreferencePrefix(id: ServerProfileId): String = "server:${id.value}:"
internal fun Preferences.activeProfileId(): ServerProfileId? = this[activeProfileKey]?.let(ServerProfileId::from)

/** The name Settings > Connection shows: the label, else "<user> on <host>[ · port N]", else the address. */
fun serverProfileDisplayName(profile: ServerProfileSummary): String {
    profile.label?.let { return it }
    val address = if (profile.port == 9982) profile.host else "${profile.host} · port ${profile.port}"
    val username = profile.username.takeIf { profile.authenticationMode == ServerProfileAuthenticationMode.PASSWORD }
    return username?.let { "$it on $address" } ?: address
}

fun existingServerProfile(
    profiles: List<ServerProfileSummary>,
    host: String,
    port: Int,
    username: String?,
): ServerProfileId? = profiles.firstOrNull {
    it.host == host.trim() && it.port == port && it.username == username?.trim() &&
        (it.authenticationMode == ServerProfileAuthenticationMode.ANONYMOUS) == (username == null)
}?.id
