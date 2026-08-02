package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import at.bernhardberger.tvhplayer.player.PlaybackPreferences
import at.bernhardberger.tvhplayer.player.PlaybackPreferencesProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class AspectRatioMode { FIT, FORCE_16_9, FORCE_4_3 }

data class PlayerSettings(
    val profile: String = "",
    val audioLanguage: String?,
    val subtitleLanguage: String?,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val timeshiftEnabled: Boolean = true,
    val refreshRateMatchingEnabled: Boolean = true,
)

class PlayerSettingsStore(private val context: Context) {

    private object Keys {
        val PROFILE = stringPreferencesKey("profile")
        val AUDIO_LANGUAGE = stringPreferencesKey("audioLanguage")
        val SUBTITLE_LANGUAGE = stringPreferencesKey("subtitleLanguage")
        val ASPECT_RATIO = stringPreferencesKey("aspectRatio")
        val TIMESHIFT_ENABLED = booleanPreferencesKey("timeshiftEnabled")
        val REFRESH_RATE_MATCHING_ENABLED = booleanPreferencesKey("refreshRateMatchingEnabled")
    }

    val playerSettings: Flow<PlayerSettings> =
        context.dataStore.data.map { p ->
            val ar = p[Keys.ASPECT_RATIO]
            val aspect = runCatching { ar?.let(AspectRatioMode::valueOf) }
                .getOrNull() ?: AspectRatioMode.FIT

            PlayerSettings(
                profile = p[Keys.PROFILE] ?: "",
                audioLanguage = p[Keys.AUDIO_LANGUAGE]?.takeIf { it.isNotBlank() },
                subtitleLanguage = p[Keys.SUBTITLE_LANGUAGE]?.takeIf { it.isNotBlank() },
                aspectRatio = aspect,
                timeshiftEnabled = p[Keys.TIMESHIFT_ENABLED] ?: true,
                refreshRateMatchingEnabled = p[Keys.REFRESH_RATE_MATCHING_ENABLED] ?: true,
            )
        }

    suspend fun savePlayer(
        profile: String,
        audioLanguage: String?,
        subtitleLanguage: String?,
        aspectRatio: AspectRatioMode,
        timeshiftEnabled: Boolean,
        refreshRateMatchingEnabled: Boolean,
    ) {
        context.dataStore.edit { p ->
            p[Keys.PROFILE] = profile
            p[Keys.AUDIO_LANGUAGE] = audioLanguage.orEmpty()
            p[Keys.SUBTITLE_LANGUAGE] = subtitleLanguage.orEmpty()
            p[Keys.ASPECT_RATIO] = aspectRatio.name
            p[Keys.TIMESHIFT_ENABLED] = timeshiftEnabled
            p[Keys.REFRESH_RATE_MATCHING_ENABLED] = refreshRateMatchingEnabled
        }
    }

    suspend fun setProfile(profile: String) {
        context.dataStore.edit { p ->
            p[Keys.PROFILE] = profile
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
}

class PlayerSettingsPlaybackPreferencesProvider(
    private val settingsStore: PlayerSettingsStore,
) : PlaybackPreferencesProvider {
    override suspend fun currentPreferences(): PlaybackPreferences {
        val settings = settingsStore.playerSettings.first()
        return PlaybackPreferences(
            profile = settings.profile,
            audioLanguage = settings.audioLanguage,
            subtitleLanguage = settings.subtitleLanguage,
            timeshiftEnabled = settings.timeshiftEnabled,
            refreshRateMatchingEnabled = settings.refreshRateMatchingEnabled,
        )
    }
}
