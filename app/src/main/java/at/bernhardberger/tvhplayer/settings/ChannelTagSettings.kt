package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class ChannelTagSettingsStore(private val context: Context) {
    private val activeTagKey = intPreferencesKey("activeChannelTagId")
    private val _unavailableTagNotice = MutableStateFlow(false)

    val activeTagId: Flow<Int?> = context.dataStore.data.map { it[activeTagKey] }
    val unavailableTagNotice = _unavailableTagNotice.asStateFlow()

    suspend fun selectTag(tagId: Int?) {
        context.dataStore.edit { preferences ->
            if (tagId == null) {
                preferences.remove(activeTagKey)
            } else {
                preferences[activeTagKey] = tagId
            }
        }
        _unavailableTagNotice.value = false
    }

    suspend fun fallbackToAllChannels() {
        context.dataStore.edit { it.remove(activeTagKey) }
        _unavailableTagNotice.value = true
    }

    fun dismissUnavailableTagNotice() {
        _unavailableTagNotice.value = false
    }
}
