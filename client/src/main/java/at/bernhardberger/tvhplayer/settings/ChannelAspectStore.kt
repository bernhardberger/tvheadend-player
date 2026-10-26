package at.bernhardberger.tvhplayer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The remembered display modes of the current server's channels. A channel without an entry,
 * and any lookup before the server identity exists, uses [AspectRatioMode.FIT] (Auto).
 */
data class ChannelAspects internal constructor(private val modes: Map<Long, AspectRatioMode>) {
    fun modeFor(channel: ChannelId?): AspectRatioMode =
        channel?.let { modes[it.value] } ?: AspectRatioMode.FIT

    companion object {
        val None = ChannelAspects(emptyMap())
    }
}

/**
 * Display mode per channel, scoped by the per-server identity that [AudioChoiceStore] keeps (it is
 * replaced whenever the server profile changes, so channel ids of another server never match).
 * Auto is the absence of an entry. Recordings use their channel's entry.
 */
class ChannelAspectStore(private val dataStore: DataStore<Preferences>) {
    @Serializable
    private data class Entry(val profile: String, val channel: Long, val mode: AspectRatioMode)

    private val key = stringPreferencesKey("channel_aspect_modes_v1")

    /** Re-emits only when the current server's modes change. */
    val choices: Flow<ChannelAspects> = dataStore.data
        .map { preferences ->
            val profile = AudioChoiceStore.profileIdentityIn(preferences) ?: return@map ChannelAspects.None
            ChannelAspects(
                decode(preferences[key]).filter { it.profile == profile }.associate { it.channel to it.mode },
            )
        }
        .distinctUntilChanged()

    /** Remembers [mode] for [channel] of the current server; Auto forgets it. No-op without a server identity. */
    suspend fun set(channel: ChannelId, mode: AspectRatioMode) {
        dataStore.edit { preferences ->
            val profile = AudioChoiceStore.profileIdentityIn(preferences) ?: return@edit
            val others = decode(preferences[key]).filterNot { it.profile == profile && it.channel == channel.value }
            val entries = if (mode == AspectRatioMode.FIT) others else others + Entry(profile, channel.value, mode)
            preferences[key] = Json.encodeToString(entries.takeLast(MAX_ENTRIES))
        }
    }

    private fun decode(value: String?): List<Entry> {
        if (value == null) return emptyList()
        return try {
            Json.decodeFromString<List<Entry>>(value)
        } catch (_: SerializationException) {
            emptyList()
        }
    }

    private companion object {
        const val MAX_ENTRIES = 256
    }
}
