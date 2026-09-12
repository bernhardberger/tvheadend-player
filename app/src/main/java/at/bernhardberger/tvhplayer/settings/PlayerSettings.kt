package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.StreamProfile
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class AspectRatioMode { FIT, FORCE_16_9, FORCE_4_3 }

data class PlayerSettings(
    val audioLanguage: String?,
    val subtitleLanguage: String?,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val timeshiftEnabled: Boolean = true,
    val refreshRateMatchingEnabled: Boolean = true,
)

class PlayerSettingsStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dataStore)

    internal val audioChoices = AudioChoiceStore(dataStore)

    private object Keys {
        val PROFILE_UUID = stringPreferencesKey("profileUuid")
        val AUDIO_LANGUAGE = stringPreferencesKey("audioLanguage")
        val SUBTITLE_LANGUAGE = stringPreferencesKey("subtitleLanguage")
        val ASPECT_RATIO = stringPreferencesKey("aspectRatio")
        val TIMESHIFT_ENABLED = booleanPreferencesKey("timeshiftEnabled")
        val REFRESH_RATE_MATCHING_ENABLED = booleanPreferencesKey("refreshRateMatchingEnabled")
    }

    val playerSettings: Flow<PlayerSettings> =
        dataStore.data.map(::playerSettingsFromPreferences)

    internal suspend fun resolveStreamProfileSelection(
        discoveredProfiles: List<StreamProfile>,
        observationIsCurrent: () -> Boolean,
    ): StreamProfileId? {
        if (!observationIsCurrent()) return null
        val selected = dataStore.data.first()[Keys.PROFILE_UUID]
            ?.takeIf(String::isNotBlank)
            ?.let { value -> runCatching { StreamProfileId(value) }.getOrNull() }
            ?.takeIf { id -> discoveredProfiles.any { it.id == id } }
        return selected.takeIf { observationIsCurrent() }
    }

    internal suspend fun setStreamProfile(profileId: StreamProfileId?) {
        dataStore.edit { preferences ->
            if (profileId == null) {
                preferences.remove(Keys.PROFILE_UUID)
            } else {
                preferences[Keys.PROFILE_UUID] = profileId.value
            }
        }
    }

    suspend fun setAspectRatio(aspectRatio: AspectRatioMode) {
        dataStore.edit { p ->
            p[Keys.ASPECT_RATIO] = aspectRatio.name
        }
    }

    suspend fun setTimeshiftEnabled(enabled: Boolean) {
        dataStore.edit { p ->
            p[Keys.TIMESHIFT_ENABLED] = enabled
        }
    }

    suspend fun setRefreshRateMatchingEnabled(enabled: Boolean) {
        dataStore.edit { p ->
            p[Keys.REFRESH_RATE_MATCHING_ENABLED] = enabled
        }
    }

    internal companion object {
        fun decodePlayerSettings(p: Preferences): PlayerSettings {
            val ar = p[Keys.ASPECT_RATIO]
            val aspect = runCatching { ar?.let(AspectRatioMode::valueOf) }
                .getOrNull() ?: AspectRatioMode.FIT

            return PlayerSettings(
                audioLanguage = p[Keys.AUDIO_LANGUAGE]?.takeIf { it.isNotBlank() },
                subtitleLanguage = p[Keys.SUBTITLE_LANGUAGE]?.takeIf { it.isNotBlank() },
                aspectRatio = aspect,
                timeshiftEnabled = p[Keys.TIMESHIFT_ENABLED] ?: true,
                refreshRateMatchingEnabled = p[Keys.REFRESH_RATE_MATCHING_ENABLED] ?: true,
            )
        }

    }
}

internal fun playerSettingsFromPreferences(preferences: Preferences): PlayerSettings =
    PlayerSettingsStore.decodePlayerSettings(preferences)
