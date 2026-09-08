package at.bernhardberger.tvhplayer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class AudioTrackChoice(
    val id: String?,
    val language: String?,
    val mimeType: String?,
    val roleFlags: Int,
    val channelCount: Int,
    val sampleRate: Int,
)

/** App-private semantic choices, never Media3 group references or track indices. */
internal class AudioChoiceStore(private val dataStore: DataStore<Preferences>) {
    @Serializable
    private data class Entry(val profile: String, val channel: Long, val choice: AudioTrackChoice)

    private val key = stringPreferencesKey("explicit_audio_choices_v1")
    private val profileKey = stringPreferencesKey("explicit_audio_profile_v1")

    suspend fun profileIdentity(replace: Boolean = false): String {
        val preferences = dataStore.edit {
            if (replace || it[profileKey] == null) it[profileKey] = UUID.randomUUID().toString()
        }
        return checkNotNull(preferences[profileKey])
    }

    suspend fun read(profile: String, channel: ChannelId): AudioTrackChoice? =
        decode(dataStore.data.first()[key]).lastOrNull {
            it.profile == profile && it.channel == channel.value
        }?.choice

    suspend fun write(profile: String, channel: ChannelId, choice: AudioTrackChoice) {
        dataStore.edit { preferences ->
            val entries = decode(preferences[key]).filterNot {
                it.profile == profile && it.channel == channel.value
            } + Entry(profile, channel.value, choice)
            preferences[key] = Json.encodeToString(entries.takeLast(64))
        }
    }

    private fun decode(value: String?): List<Entry> {
        if (value == null) return emptyList()
        return try {
            Json.decodeFromString<List<Entry>>(value).takeLast(64)
        } catch (_: SerializationException) {
            emptyList()
        }
    }
}
