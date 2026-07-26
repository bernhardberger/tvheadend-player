package at.bernhardberger.tvhplayer.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvhplayer.core.isValidSimpleTvPin
import at.bernhardberger.tvhplayer.core.simpleTvPinHash
import at.bernhardberger.tvhplayer.core.verifySimpleTvPin
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SimpleTvSettingsStore(private val context: Context) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("simpleTv.enabled")
        val TIMESHIFT = booleanPreferencesKey("simpleTv.timeshift")
        val PIN_SALT = stringPreferencesKey("simpleTv.pinSalt")
        val PIN_HASH = stringPreferencesKey("simpleTv.pinHash")
        // Obsolete pre-release keys (epg/recordings/stop/settings/appExit) are
        // intentionally unread; no migration bridge is required.
    }

    val settings: Flow<SimpleTvSettings> = context.dataStore.data.map(::settingsFrom)

    suspend fun setEnabled(value: Boolean) = set(Keys.ENABLED, value)
    suspend fun setTimeshift(value: Boolean) = set(Keys.TIMESHIFT, value)

    suspend fun setPin(pin: String): Boolean {
        if (!isValidSimpleTvPin(pin)) return false
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = simpleTvPinHash(pin, salt)
        context.dataStore.edit {
            it[Keys.PIN_SALT] = Base64.getEncoder().encodeToString(salt)
            it[Keys.PIN_HASH] = Base64.getEncoder().encodeToString(hash)
        }
        return true
    }

    suspend fun clearPin() {
        context.dataStore.edit {
            it.remove(Keys.PIN_SALT)
            it.remove(Keys.PIN_HASH)
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val preferences = context.dataStore.data.first()
        val salt = preferences[Keys.PIN_SALT]?.decodeBase64() ?: return false
        val hash = preferences[Keys.PIN_HASH]?.decodeBase64() ?: return false
        return verifySimpleTvPin(pin, salt, hash)
    }

    private suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    private fun settingsFrom(preferences: Preferences) = SimpleTvSettings(
        enabled = preferences[Keys.ENABLED] ?: false,
        timeshift = preferences[Keys.TIMESHIFT] ?: false,
        pinConfigured = preferences[Keys.PIN_SALT] != null &&
            preferences[Keys.PIN_HASH] != null,
    )

    private fun String.decodeBase64(): ByteArray? =
        runCatching { Base64.getDecoder().decode(this) }.getOrNull()
}
