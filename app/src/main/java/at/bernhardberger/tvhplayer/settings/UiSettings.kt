package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UiSettings(
    val showEpgMenu: Boolean = true,
    val autoStartPlayback: Boolean = false,
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val recordingProfileId: DvrConfigId? = null,
    val channelsView: ChannelsView = ChannelsView(),
)

fun resolveEpgMenuVisibility(storedValue: Boolean?): Boolean = storedValue ?: true
fun resolvePlaybackAutoStart(storedValue: Boolean?): Boolean = storedValue ?: false

class UiSettingsStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dataStore)
    private object Keys {
        val SHOW_EPG_MENU = booleanPreferencesKey("showEpgMenu")
        val AUTO_START_PLAYBACK = booleanPreferencesKey("autoStartPlayback")
        val CLOCK_FORMAT = stringPreferencesKey("clockFormat")
        val RECORDING_PROFILE = stringPreferencesKey("recordingProfile")
        val CHANNELS_LAYOUT = stringPreferencesKey("channelsLayout")
        val CHANNELS_CARD_ART = booleanPreferencesKey("channelsCardArt")
        val CHANNELS_BACKDROP_ART = booleanPreferencesKey("channelsBackdropArt")
    }

    val settings: Flow<UiSettings> = dataStore.data.map { preferences ->
        UiSettings(
            showEpgMenu = resolveEpgMenuVisibility(preferences[Keys.SHOW_EPG_MENU]),
            autoStartPlayback = resolvePlaybackAutoStart(preferences[Keys.AUTO_START_PLAYBACK]),
            clockFormat = resolveClockFormat(preferences[Keys.CLOCK_FORMAT]),
            recordingProfileId = preferences[Keys.RECORDING_PROFILE]?.takeIf(String::isNotBlank)?.let(::DvrConfigId),
            channelsView = resolveChannelsView(
                layout = preferences[Keys.CHANNELS_LAYOUT],
                cardArt = preferences[Keys.CHANNELS_CARD_ART],
                backdropArt = preferences[Keys.CHANNELS_BACKDROP_ART],
            ),
        )
    }

    suspend fun setClockFormat(format: ClockFormat) {
        dataStore.edit { preferences ->
            preferences[Keys.CLOCK_FORMAT] = format.name
        }
    }

    suspend fun setChannelsView(view: ChannelsView) {
        dataStore.edit { preferences ->
            preferences[Keys.CHANNELS_LAYOUT] = view.layout.name
            preferences[Keys.CHANNELS_CARD_ART] = view.cardArt
            preferences[Keys.CHANNELS_BACKDROP_ART] = view.backdropArt
        }
    }

    suspend fun setShowEpgMenu(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SHOW_EPG_MENU] = show
        }
    }

    suspend fun setAutoStartPlayback(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_START_PLAYBACK] = enabled
        }
    }

    suspend fun setRecordingProfile(id: DvrConfigId) {
        dataStore.edit { it[Keys.RECORDING_PROFILE] = id.value }
    }
}
