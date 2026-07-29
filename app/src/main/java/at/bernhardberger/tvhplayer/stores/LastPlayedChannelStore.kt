package at.bernhardberger.tvhplayer.stores

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import at.bernhardberger.tvhplayer.core.RECENT_CHANNEL_LIMIT
import at.bernhardberger.tvhplayer.core.pushRecentChannelId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.applianceDataStore by preferencesDataStore(name = "tvhplayer_appliance")

class LastPlayedChannelStore(private val context: Context) {
    private object Keys {
        val CHANNEL_ID = intPreferencesKey("last_played_channel_id")
        val RECENT_CHANNEL_IDS = stringPreferencesKey("recent_played_channel_ids")
    }

    val channelId: Flow<Int?> = context.applianceDataStore.data.map { preferences ->
        preferences[Keys.CHANNEL_ID]
    }

    val recentChannelIds: Flow<List<Int>> = context.applianceDataStore.data.map { preferences ->
        preferences[Keys.RECENT_CHANNEL_IDS]
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            .orEmpty()
    }

    suspend fun setChannelId(channelId: Int) {
        context.applianceDataStore.edit { preferences ->
            preferences[Keys.CHANNEL_ID] = channelId
            val current = preferences[Keys.RECENT_CHANNEL_IDS]
                ?.split(',')
                ?.mapNotNull { it.trim().toIntOrNull() }
                .orEmpty()
            preferences[Keys.RECENT_CHANNEL_IDS] = pushRecentChannelId(
                current = current,
                channelId = channelId,
                limit = RECENT_CHANNEL_LIMIT,
            ).joinToString(",")
        }
    }
}
