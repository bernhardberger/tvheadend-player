package at.bernhardberger.tvhplayer.stores

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvhplayer.core.RECENT_CHANNEL_LIMIT
import at.bernhardberger.tvhplayer.core.pushRecentChannelId
import at.bernhardberger.tvhplayer.settings.activeProfileId
import at.bernhardberger.tvhplayer.settings.dataStore
import at.bernhardberger.tvhplayer.settings.profilePreferencePrefix
import at.bernhardberger.tvhplayer.settings.persistedIdToLongOrNull
import at.bernhardberger.tvhplayer.settings.sdkU32IdOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

/** Confirmation can arrive before the channel scope catches up to the playback session. */
fun recentChannelToPersist(
    profileId: ServerProfileId?,
    channelId: ChannelId?,
    sessionsAligned: Boolean,
): Pair<ServerProfileId, ChannelId>? =
    if (sessionsAligned && profileId != null && channelId != null) profileId to channelId else null

class LastPlayedChannelStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dataStore)

    val channelId: Flow<ChannelId?> = dataStore.data.map { preferences ->
        val id = preferences.activeProfileId() ?: return@map null
        preferences[longPreferencesKey(profilePreferencePrefix(id) + "lastChannel")]
            ?.let(::sdkU32IdOrNull)?.let(::ChannelId)
    }

    val recentChannelIds: Flow<List<ChannelId>> = dataStore.data.map { preferences ->
        val id = preferences.activeProfileId() ?: return@map emptyList()
        decode(preferences[stringPreferencesKey(profilePreferencePrefix(id) + "recentChannels")])
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            val id = preferences.activeProfileId() ?: return@edit
            preferences.remove(longPreferencesKey(profilePreferencePrefix(id) + "lastChannel"))
            preferences.remove(stringPreferencesKey(profilePreferencePrefix(id) + "recentChannels"))
        }
    }

    suspend fun setChannelId(channelId: ChannelId) {
        setChannelId(dataStore.data.first().activeProfileId(), channelId)
    }

    suspend fun setChannelId(profileId: ServerProfileId?, channelId: ChannelId) {
        val id = profileId ?: return
        dataStore.edit { preferences ->
            if (preferences.activeProfileId() != id) return@edit
            val recentKey = stringPreferencesKey(profilePreferencePrefix(id) + "recentChannels")
            preferences[longPreferencesKey(profilePreferencePrefix(id) + "lastChannel")] = channelId.value
            preferences[recentKey] = pushRecentChannelId(
                current = decode(preferences[recentKey]), channelId = channelId, limit = RECENT_CHANNEL_LIMIT,
            ).joinToString(",") { it.value.toString() }
        }
    }

    private fun decode(value: String?): List<ChannelId> = value?.split(',')
        ?.mapNotNull { persistedIdToLongOrNull(it.trim())?.let(::ChannelId) }.orEmpty()
}
