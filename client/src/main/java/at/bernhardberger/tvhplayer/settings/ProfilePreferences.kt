package at.bernhardberger.tvhplayer.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary

internal val activeProfileKey = stringPreferencesKey("activeServerProfileId")
internal fun profilePreferencePrefix(id: ServerProfileId): String = "server:${id.value}:"
internal fun Preferences.activeProfileId(): ServerProfileId? = this[activeProfileKey]?.let(ServerProfileId::from)

/** All lists retain SDK insertion order, including the hidden profile currently in use. */
fun switcherProfiles(
    profiles: List<ServerProfileSummary>,
    activeId: ServerProfileId?,
    hiddenIds: Set<ServerProfileId>,
): List<ServerProfileSummary> = profiles.filter { it.id == activeId || it.id !in hiddenIds }

fun showServerSwitcher(profiles: List<ServerProfileSummary>): Boolean = profiles.size >= 2

fun shouldShowStartupPicker(askAtStartup: Boolean, switcherProfiles: List<ServerProfileSummary>): Boolean =
    askAtStartup && switcherProfiles.size >= 2

fun serverProfileDisplayName(profile: ServerProfileSummary): String {
    val name = profile.label ?: profile.username?.let { "$it on ${profile.host}" } ?: profile.host
    return if (profile.port == 9982) name else "$name · ${profile.port}"
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
