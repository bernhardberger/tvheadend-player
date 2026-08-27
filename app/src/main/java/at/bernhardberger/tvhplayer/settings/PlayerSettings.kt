package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import at.bernhardberger.tvheadend.playback.PlaybackPreferences
import at.bernhardberger.tvheadend.playback.PlaybackPreferencesProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class AspectRatioMode { FIT, FORCE_16_9, FORCE_4_3 }

data class PlayerSettings(
    val profile: String = "",
    val legacyProfileName: String? = null,
    val audioLanguage: String?,
    val subtitleLanguage: String?,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val timeshiftEnabled: Boolean = true,
    val refreshRateMatchingEnabled: Boolean = true,
)

class PlayerSettingsStore(private val context: Context) {

    private object Keys {
        val PROFILE_UUID = stringPreferencesKey("profileUuid")
        val LEGACY_PROFILE_NAME = stringPreferencesKey("profile")
        val AUDIO_LANGUAGE = stringPreferencesKey("audioLanguage")
        val SUBTITLE_LANGUAGE = stringPreferencesKey("subtitleLanguage")
        val ASPECT_RATIO = stringPreferencesKey("aspectRatio")
        val TIMESHIFT_ENABLED = booleanPreferencesKey("timeshiftEnabled")
        val REFRESH_RATE_MATCHING_ENABLED = booleanPreferencesKey("refreshRateMatchingEnabled")
    }

    val playerSettings: Flow<PlayerSettings> =
        context.dataStore.data.map(::playerSettingsFromPreferences)

    suspend fun savePlayer(
        profileUuid: String,
        legacyProfileName: String?,
        audioLanguage: String?,
        subtitleLanguage: String?,
        aspectRatio: AspectRatioMode,
        timeshiftEnabled: Boolean,
        refreshRateMatchingEnabled: Boolean,
    ) {
        context.dataStore.edit { p ->
            p.writeProfileSelection(profileUuid, legacyProfileName)
            p[Keys.AUDIO_LANGUAGE] = audioLanguage.orEmpty()
            p[Keys.SUBTITLE_LANGUAGE] = subtitleLanguage.orEmpty()
            p[Keys.ASPECT_RATIO] = aspectRatio.name
            p[Keys.TIMESHIFT_ENABLED] = timeshiftEnabled
            p[Keys.REFRESH_RATE_MATCHING_ENABLED] = refreshRateMatchingEnabled
        }
    }

    suspend fun setProfile(profileUuid: String, legacyProfileName: String) {
        context.dataStore.edit { p ->
            p.writeProfileSelection(profileUuid, legacyProfileName)
        }
    }

    suspend fun setAspectRatio(aspectRatio: AspectRatioMode) {
        context.dataStore.edit { p ->
            p[Keys.ASPECT_RATIO] = aspectRatio.name
        }
    }

    suspend fun setTimeshiftEnabled(enabled: Boolean) {
        context.dataStore.edit { p ->
            p[Keys.TIMESHIFT_ENABLED] = enabled
        }
    }

    suspend fun setRefreshRateMatchingEnabled(enabled: Boolean) {
        context.dataStore.edit { p ->
            p[Keys.REFRESH_RATE_MATCHING_ENABLED] = enabled
        }
    }

    internal companion object {
        fun decodePlayerSettings(p: Preferences): PlayerSettings {
            val ar = p[Keys.ASPECT_RATIO]
            val aspect = runCatching { ar?.let(AspectRatioMode::valueOf) }
                .getOrNull() ?: AspectRatioMode.FIT

            return PlayerSettings(
                profile = p[Keys.PROFILE_UUID] ?: "",
                legacyProfileName = p[Keys.LEGACY_PROFILE_NAME]?.takeIf { it.isNotBlank() },
                audioLanguage = p[Keys.AUDIO_LANGUAGE]?.takeIf { it.isNotBlank() },
                subtitleLanguage = p[Keys.SUBTITLE_LANGUAGE]?.takeIf { it.isNotBlank() },
                aspectRatio = aspect,
                timeshiftEnabled = p[Keys.TIMESHIFT_ENABLED] ?: true,
                refreshRateMatchingEnabled = p[Keys.REFRESH_RATE_MATCHING_ENABLED] ?: true,
            )
        }

        fun writeProfileSelection(
            preferences: MutablePreferences,
            profileUuid: String,
            legacyProfileName: String?,
        ) {
            preferences[Keys.PROFILE_UUID] = profileUuid
            preferences[Keys.LEGACY_PROFILE_NAME] = legacyProfileName.orEmpty()
        }
    }
}

internal fun playerSettingsFromPreferences(preferences: Preferences): PlayerSettings =
    PlayerSettingsStore.decodePlayerSettings(preferences)

internal fun MutablePreferences.writeProfileSelection(
    profileUuid: String,
    legacyProfileName: String?,
) {
    PlayerSettingsStore.writeProfileSelection(this, profileUuid, legacyProfileName)
}

class PlayerSettingsPlaybackPreferencesProvider internal constructor(
    private val loadSettings: suspend () -> PlayerSettings,
) : PlaybackPreferencesProvider {
    constructor(settingsStore: PlayerSettingsStore) : this(
        loadSettings = { settingsStore.playerSettings.first() },
    )

    override suspend fun currentPreferences(): PlaybackPreferences {
        val settings = loadSettings()
        return PlaybackPreferences(
            profile = settings.legacyProfileName.orEmpty(),
            audioLanguage = settings.audioLanguage,
            subtitleLanguage = settings.subtitleLanguage,
            timeshiftEnabled = settings.timeshiftEnabled,
            refreshRateMatchingEnabled = settings.refreshRateMatchingEnabled,
        )
    }
}
