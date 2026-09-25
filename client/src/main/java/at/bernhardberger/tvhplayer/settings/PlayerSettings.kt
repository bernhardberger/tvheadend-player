package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.StreamProfile
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class AspectRatioMode { FIT, FORCE_16_9, FORCE_4_3 }
enum class AudioFormatPreference { AUTOMATIC, PREFER_DOLBY, PREFER_STEREO }

/** Store language identities, not localized display names or duplicate preference slots. */
fun normalizeAudioLanguages(languages: List<String>): List<String> = languages
    .mapNotNull(::normalizePreferenceLanguage).distinct().take(3)

fun normalizePreferenceLanguage(value: String): String? {
    val language = java.util.Locale.forLanguageTag(value.trim().replace('_', '-')).language
        .lowercase(java.util.Locale.ROOT)
    if (language.isBlank() || language == "und") return null
    return java.util.Locale.getISOLanguages().firstOrNull { code ->
        code == language || runCatching { java.util.Locale.forLanguageTag(code).isO3Language == language }.getOrDefault(false)
    } ?: when (language) {
        "ger" -> "de"
        "fre" -> "fr"
        "dut" -> "nl"
        "cze" -> "cs"
        "slo" -> "sk"
        "rum" -> "ro"
        "gre" -> "el"
        "chi" -> "zh"
        "alb" -> "sq"
        "arm" -> "hy"
        "baq" -> "eu"
        "bur" -> "my"
        "geo" -> "ka"
        "ice" -> "is"
        "mac" -> "mk"
        "mao" -> "mi"
        "may" -> "ms"
        "per" -> "fa"
        "tib" -> "bo"
        "wel" -> "cy"
        else -> language
    }
}

fun audioLanguagesWithSlot(languages: List<String>, slot: Int, language: String?): List<String> {
    require(slot in 0..2)
    val current = normalizeAudioLanguages(languages).toMutableList()
    val selected = language?.let(::normalizePreferenceLanguage)
    // Moving an existing preference keeps the other languages in their relative order.
    if (selected !in current && slot < current.size) current.removeAt(slot)
    if (selected != null) {
        current.remove(selected)
        current.add(slot.coerceAtMost(current.size), selected)
    }
    return current.take(3)
}

data class PlayerSettings(
    val audioLanguages: List<String> = emptyList(),
    val subtitleLanguage: String? = null,
    val audioFormat: AudioFormatPreference = AudioFormatPreference.AUTOMATIC,
    val audioDescription: Boolean = false,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val timeshiftEnabled: Boolean = true,
    val refreshRateMatchingEnabled: Boolean = true,
    val audioPassthroughEnabled: Boolean = true,
    val keepChannelMinutes: Int = 20,
)

class PlayerSettingsStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dataStore)

    internal val audioChoices = AudioChoiceStore(dataStore)

    private object Keys {
        val PROFILE_UUID = stringPreferencesKey("profileUuid")
        val AUDIO_LANGUAGES = stringPreferencesKey("audioLanguages")
        val AUDIO_FORMAT = stringPreferencesKey("audioFormat")
        val AUDIO_DESCRIPTION = booleanPreferencesKey("audioDescription")
        val SUBTITLE_LANGUAGE = stringPreferencesKey("subtitleLanguage")
        val ASPECT_RATIO = stringPreferencesKey("aspectRatio")
        val TIMESHIFT_ENABLED = booleanPreferencesKey("timeshiftEnabled")
        val REFRESH_RATE_MATCHING_ENABLED = booleanPreferencesKey("refreshRateMatchingEnabled")
        val AUDIO_PASSTHROUGH_ENABLED = booleanPreferencesKey("audioPassthroughEnabled")
        val KEEP_CHANNEL_MINUTES = intPreferencesKey("keepChannelMinutes")
    }

    val playerSettings: Flow<PlayerSettings> =
        dataStore.data.map(::playerSettingsFromPreferences)

    suspend fun setAudioLanguages(languages: List<String>) {
        dataStore.edit { it[Keys.AUDIO_LANGUAGES] = normalizeAudioLanguages(languages).joinToString(",") }
    }

    suspend fun setAudioLanguageSlot(slot: Int, language: String?) {
        dataStore.edit {
            it[Keys.AUDIO_LANGUAGES] = audioLanguagesWithSlot(
                decodePlayerSettings(it).audioLanguages, slot, language,
            ).joinToString(",")
        }
    }

    suspend fun setAudioFormat(format: AudioFormatPreference) {
        dataStore.edit { it[Keys.AUDIO_FORMAT] = format.name }
    }

    suspend fun setAudioDescription(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_DESCRIPTION] = enabled }
    }

    suspend fun setSubtitleLanguage(language: String?) {
        dataStore.edit {
            val normalized = language?.let(::normalizePreferenceLanguage)
            if (normalized == null) it.remove(Keys.SUBTITLE_LANGUAGE)
            else it[Keys.SUBTITLE_LANGUAGE] = normalized
        }
    }

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

    suspend fun setAudioPassthroughEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_PASSTHROUGH_ENABLED] = enabled }
    }

    suspend fun setKeepChannelMinutes(minutes: Int) {
        require(minutes in listOf(0, 10, 20, 30))
        dataStore.edit { it[Keys.KEEP_CHANNEL_MINUTES] = minutes }
    }

    internal companion object {
        fun decodePlayerSettings(p: Preferences): PlayerSettings {
            val ar = p[Keys.ASPECT_RATIO]
            val aspect = runCatching { ar?.let(AspectRatioMode::valueOf) }
                .getOrNull() ?: AspectRatioMode.FIT

            return PlayerSettings(
                audioLanguages = normalizeAudioLanguages(p[Keys.AUDIO_LANGUAGES]?.split(',').orEmpty()),
                audioFormat = runCatching { p[Keys.AUDIO_FORMAT]?.let(AudioFormatPreference::valueOf) }
                    .getOrNull() ?: AudioFormatPreference.AUTOMATIC,
                audioDescription = p[Keys.AUDIO_DESCRIPTION] ?: false,
                subtitleLanguage = p[Keys.SUBTITLE_LANGUAGE]?.let(::normalizePreferenceLanguage),
                aspectRatio = aspect,
                timeshiftEnabled = p[Keys.TIMESHIFT_ENABLED] ?: true,
                refreshRateMatchingEnabled = p[Keys.REFRESH_RATE_MATCHING_ENABLED] ?: true,
                audioPassthroughEnabled = p[Keys.AUDIO_PASSTHROUGH_ENABLED] ?: true,
                keepChannelMinutes = p[Keys.KEEP_CHANNEL_MINUTES]?.takeIf { it in listOf(0, 10, 20, 30) } ?: 20,
            )
        }

    }
}

internal fun playerSettingsFromPreferences(preferences: Preferences): PlayerSettings =
    PlayerSettingsStore.decodePlayerSettings(preferences)
