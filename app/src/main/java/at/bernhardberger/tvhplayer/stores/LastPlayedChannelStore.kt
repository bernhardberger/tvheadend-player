package at.bernhardberger.tvhplayer.stores

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.applianceDataStore by preferencesDataStore(name = "tvhplayer_appliance")

class LastPlayedChannelStore(private val context: Context) {
    private object Keys {
        val CHANNEL_ID = intPreferencesKey("last_played_channel_id")
    }

    val channelId: Flow<Int?> = context.applianceDataStore.data.map { preferences ->
        preferences[Keys.CHANNEL_ID]
    }

    suspend fun setChannelId(channelId: Int) {
        context.applianceDataStore.edit { preferences ->
            preferences[Keys.CHANNEL_ID] = channelId
        }
    }
}
