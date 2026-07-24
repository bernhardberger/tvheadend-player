package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UiSettings(
    val showEpgMenu: Boolean = true,
)

fun resolveEpgMenuVisibility(storedValue: Boolean?): Boolean = storedValue ?: true

class UiSettingsStore(private val context: Context) {
    private object Keys {
        val SHOW_EPG_MENU = booleanPreferencesKey("showEpgMenu")
    }

    val settings: Flow<UiSettings> = context.dataStore.data.map { preferences ->
        UiSettings(
            showEpgMenu = resolveEpgMenuVisibility(preferences[Keys.SHOW_EPG_MENU]),
        )
    }

    suspend fun setShowEpgMenu(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SHOW_EPG_MENU] = show
        }
    }
}
