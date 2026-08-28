package at.bernhardberger.tvhplayer.stores

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.core.RECENT_CHANNEL_LIMIT
import at.bernhardberger.tvhplayer.core.pushRecentChannelId
import at.bernhardberger.tvhplayer.settings.intToLongPreferenceMigration
import at.bernhardberger.tvhplayer.settings.persistedIdToLongOrNull
import at.bernhardberger.tvhplayer.settings.sdkU32IdOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val legacyChannelIdKey = intPreferencesKey("last_played_channel_id")
private val channelIdKey = longPreferencesKey("last_played_channel_id_long")
private val Context.applianceDataStore by preferencesDataStore(
    name = "tvhplayer_appliance",
    produceMigrations = { listOf(intToLongPreferenceMigration(legacyChannelIdKey, channelIdKey)) },
)

class LastPlayedChannelStore(private val context: Context) {
    private object Keys {
        val RECENT_CHANNEL_IDS = stringPreferencesKey("recent_played_channel_ids")
    }

    val channelId: Flow<ChannelId?> = context.applianceDataStore.data.map { preferences ->
        preferences[channelIdKey]?.let(::sdkU32IdOrNull)?.let(::ChannelId)
    }

    val recentChannelIds: Flow<List<ChannelId>> = context.applianceDataStore.data.map { preferences ->
        preferences[Keys.RECENT_CHANNEL_IDS]
            ?.split(',')
            ?.mapNotNull { persistedIdToLongOrNull(it.trim())?.let(::ChannelId) }
            .orEmpty()
    }

    suspend fun setChannelId(channelId: ChannelId) {
        context.applianceDataStore.edit { preferences ->
            preferences[channelIdKey] = channelId.value
            val current = preferences[Keys.RECENT_CHANNEL_IDS]
                ?.split(',')
                ?.mapNotNull { persistedIdToLongOrNull(it.trim())?.let(::ChannelId) }
                .orEmpty()
            preferences[Keys.RECENT_CHANNEL_IDS] = pushRecentChannelId(
                current = current,
                channelId = channelId,
                limit = RECENT_CHANNEL_LIMIT,
            ).joinToString(",") { it.value.toString() }
        }
    }
}
