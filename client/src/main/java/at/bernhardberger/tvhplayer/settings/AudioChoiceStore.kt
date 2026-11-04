package at.bernhardberger.tvhplayer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AudioTrackChoice(
    val language: String?,
    val mimeType: String?,
    val roleFlags: Int,
    val channelCount: Int,
    val sampleRate: Int,
)

/** App-private semantic choices, never Media3 group references or track indices. */
class AudioChoiceStore(private val dataStore: DataStore<Preferences>) {
    @Serializable
    private data class Entry(val profile: String, val channel: Long, val choice: AudioTrackChoice)

    private val key = stringPreferencesKey("explicit_audio_choices_v1")

    suspend fun read(profile: String, channel: ChannelId): AudioTrackChoice? =
        decode(dataStore.data.first()[key]).lastOrNull {
            it.profile == profile && it.channel == channel.value
        }?.choice

    suspend fun write(profile: String, channel: ChannelId, choice: AudioTrackChoice) {
        dataStore.edit { preferences ->
            if (preferences[activeProfileKey] != profile) return@edit
            val entries = decode(preferences[key]).filterNot {
                it.profile == profile && it.channel == channel.value
            } + Entry(profile, channel.value, choice)
            preferences[key] = Json.encodeToString(
                entries.filterNot { it.profile == profile } + entries.filter { it.profile == profile }.takeLast(64),
            )
        }
    }

    suspend fun remove(profile: String, channel: ChannelId) {
        dataStore.edit { preferences ->
            val remaining = decode(preferences[key]).filterNot {
                it.profile == profile && it.channel == channel.value
            }
            preferences[key] = Json.encodeToString(remaining)
        }
    }

    internal suspend fun removeProfile(profile: String) {
        dataStore.edit { it[key] = Json.encodeToString(decode(it[key]).filterNot { entry -> entry.profile == profile }) }
    }

    private fun decode(value: String?): List<Entry> {
        if (value == null) return emptyList()
        return try {
            Json.decodeFromString<List<Entry>>(value)
        } catch (_: SerializationException) {
            emptyList()
        }
    }
}
