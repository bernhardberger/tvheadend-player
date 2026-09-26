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
import at.bernhardberger.tvhplayer.playback.StartupBufferVerdict
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
    /** Live start-up buffer in milliseconds; [STARTUP_BUFFER_AUTOMATIC] learns it per server. */
    val startupBufferMillis: Int = STARTUP_BUFFER_AUTOMATIC,
)

/** Stored [PlayerSettings.startupBufferMillis] value for the learned, per-server buffer. */
const val STARTUP_BUFFER_AUTOMATIC = 0

internal const val STARTUP_BUFFER_LEARNED_MIN_MILLIS = 500
internal const val STARTUP_BUFFER_LEARNED_MAX_MILLIS = 3000

/** Verdicts kept per server for the Automatic level. */
internal const val STARTUP_BUFFER_RECENT_VERDICTS = 5

/**
 * Version of the Automatic learning rules. Learning stored under other rules
 * (or without a version) starts over at the default level.
 */
internal const val STARTUP_BUFFER_LEARNING_POLICY = 2

/** Fixed start-up buffer choices, in milliseconds, offered next to Automatic. */
val STARTUP_BUFFER_FIXED_MILLIS: List<Int> = listOf(500, 1000, 1500, 2000, 3000)

/**
 * Learned Automatic start-up buffer for one server identity. [profile] is the
 * server-scoped identity the level was learned for; a different identity means
 * the level no longer applies. [recentVerdicts] holds the verdicts since the
 * level last moved, oldest first, at most [STARTUP_BUFFER_RECENT_VERDICTS].
 */
data class StartupBufferLearningState(
    val profile: String? = null,
    val levelMillis: Int = STARTUP_BUFFER_LEARNED_MIN_MILLIS,
    val recentVerdicts: List<StartupBufferVerdict> = emptyList(),
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
        val STARTUP_BUFFER_MILLIS = intPreferencesKey("startupBufferMillis")
        val STARTUP_BUFFER_LEARNED_PROFILE = stringPreferencesKey("startupBufferLearnedProfile")
        val STARTUP_BUFFER_LEARNED_MILLIS = intPreferencesKey("startupBufferLearnedMillis")
        val STARTUP_BUFFER_RECENT_VERDICTS = stringPreferencesKey("startupBufferRecentVerdicts")
        val STARTUP_BUFFER_LEARNING_POLICY = intPreferencesKey("startupBufferLearningPolicy")
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

    suspend fun setStartupBufferMillis(millis: Int) {
        require(millis == STARTUP_BUFFER_AUTOMATIC || millis in STARTUP_BUFFER_FIXED_MILLIS)
        dataStore.edit { it[Keys.STARTUP_BUFFER_MILLIS] = millis }
    }

    /**
     * Learned Automatic level. Kept out of [playerSettings] so that learning
     * never re-emits the settings the playback runtime applies.
     */
    val startupBufferLearning: Flow<StartupBufferLearningState> =
        dataStore.data.map(::decodeStartupBufferLearning)

    /** Atomically replaces the learned state with [transform] of the stored one. */
    internal suspend fun updateStartupBufferLearning(
        transform: (StartupBufferLearningState) -> StartupBufferLearningState,
    ): StartupBufferLearningState {
        var result = StartupBufferLearningState()
        dataStore.edit { p ->
            result = transform(decodeStartupBufferLearning(p))
            val profile = result.profile
            if (profile == null) p.remove(Keys.STARTUP_BUFFER_LEARNED_PROFILE)
            else p[Keys.STARTUP_BUFFER_LEARNED_PROFILE] = profile
            p[Keys.STARTUP_BUFFER_LEARNED_MILLIS] = result.levelMillis
            p[Keys.STARTUP_BUFFER_RECENT_VERDICTS] = result.recentVerdicts
                .takeLast(STARTUP_BUFFER_RECENT_VERDICTS)
                .joinToString("") { if (it == StartupBufferVerdict.TROUBLE) "T" else "C" }
            p[Keys.STARTUP_BUFFER_LEARNING_POLICY] = STARTUP_BUFFER_LEARNING_POLICY
        }
        return result
    }

    internal companion object {
        fun decodeStartupBufferLearning(p: Preferences): StartupBufferLearningState {
            val profile = p[Keys.STARTUP_BUFFER_LEARNED_PROFILE]
            // Learning from other rules restarts at the default level.
            if (p[Keys.STARTUP_BUFFER_LEARNING_POLICY] != STARTUP_BUFFER_LEARNING_POLICY) {
                return StartupBufferLearningState(profile = profile)
            }
            val default = StartupBufferLearningState()
            return StartupBufferLearningState(
                profile = profile,
                levelMillis = p[Keys.STARTUP_BUFFER_LEARNED_MILLIS]
                    ?.takeIf { it in STARTUP_BUFFER_LEARNED_MIN_MILLIS..STARTUP_BUFFER_LEARNED_MAX_MILLIS }
                    ?: default.levelMillis,
                recentVerdicts = decodeRecentVerdicts(p[Keys.STARTUP_BUFFER_RECENT_VERDICTS]),
            )
        }

        /** "T" and "C" per verdict, oldest first; anything else is an empty history. */
        private fun decodeRecentVerdicts(value: String?): List<StartupBufferVerdict> {
            if (value == null || value.length > STARTUP_BUFFER_RECENT_VERDICTS) return emptyList()
            return value.map { letter ->
                when (letter) {
                    'T' -> StartupBufferVerdict.TROUBLE
                    'C' -> StartupBufferVerdict.CLEAN
                    else -> return emptyList()
                }
            }
        }

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
                startupBufferMillis = p[Keys.STARTUP_BUFFER_MILLIS]
                    ?.takeIf { it == STARTUP_BUFFER_AUTOMATIC || it in STARTUP_BUFFER_FIXED_MILLIS }
                    ?: STARTUP_BUFFER_AUTOMATIC,
            )
        }

    }
}

internal fun playerSettingsFromPreferences(preferences: Preferences): PlayerSettings =
    PlayerSettingsStore.decodePlayerSettings(preferences)
