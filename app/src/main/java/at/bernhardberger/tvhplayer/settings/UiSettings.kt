package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UiSettings(
    val showEpgMenu: Boolean = true,
    val autoStartPlayback: Boolean = false,
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
)

fun resolveEpgMenuVisibility(storedValue: Boolean?): Boolean = storedValue ?: true
fun resolvePlaybackAutoStart(storedValue: Boolean?): Boolean = storedValue ?: false

class UiSettingsStore(private val context: Context) {
    private object Keys {
        val SHOW_EPG_MENU = booleanPreferencesKey("showEpgMenu")
        val AUTO_START_PLAYBACK = booleanPreferencesKey("autoStartPlayback")
        val CLOCK_FORMAT = stringPreferencesKey("clockFormat")
    }

    val settings: Flow<UiSettings> = context.dataStore.data.map { preferences ->
        UiSettings(
            showEpgMenu = resolveEpgMenuVisibility(preferences[Keys.SHOW_EPG_MENU]),
            autoStartPlayback = resolvePlaybackAutoStart(preferences[Keys.AUTO_START_PLAYBACK]),
            clockFormat = resolveClockFormat(preferences[Keys.CLOCK_FORMAT]),
        )
    }

    suspend fun setClockFormat(format: ClockFormat) {
        context.dataStore.edit { preferences ->
            preferences[Keys.CLOCK_FORMAT] = format.name
        }
    }

    suspend fun setShowEpgMenu(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SHOW_EPG_MENU] = show
        }
    }

    suspend fun setAutoStartPlayback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.AUTO_START_PLAYBACK] = enabled
        }
    }
}
