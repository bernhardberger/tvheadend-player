package at.bernhardberger.tvhplayer.stores

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.channelAccentDataStore by preferencesDataStore(name = "tvhplayer_channel_accents")

/**
 * Channel colours as RGB by the SDK's opaque artwork cache key, which is per server profile and
 * picon. The keys are never logged, shown or parsed. Read once by [load]; lookups are synchronous
 * from memory. Without a [dataStore] nothing is persisted.
 */
class ChannelAccentStore(private val dataStore: DataStore<Preferences>? = null) {
    constructor(context: Context) : this(context.channelAccentDataStore)

    private val colours = ConcurrentHashMap<String, Int>()
    private val fileAccess = Mutex()
    private var loaded = dataStore == null
    private val mutableClears = MutableStateFlow(0)

    /** Counts [clear] calls, so a fill-in pass can run again. */
    val clears: StateFlow<Int> = mutableClears.asStateFlow()

    /** Reads the file on the first call and returns at once on later ones. An unreadable file reads as empty. */
    suspend fun load() {
        val source = dataStore ?: return
        fileAccess.withLock {
            if (loaded) return
            source.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first().asMap()
                // A colour put while the file was read is the newer one.
                .forEach { (key, value) -> if (value is Int) colours.putIfAbsent(key.name, value) }
            loaded = true
        }
    }

    operator fun get(key: String): Int? = colours[key]

    /** Keeps [rgb] in memory even when the file cannot be written. */
    suspend fun put(key: String, rgb: Int) {
        colours[key] = rgb
        write { it[intPreferencesKey(key)] = rgb }
    }

    suspend fun clear() {
        fileAccess.withLock {
            colours.clear()
            write { it.clear() }
            // Nothing the file held before is read back.
            loaded = true
        }
        mutableClears.update { it + 1 }
    }

    private suspend fun write(change: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            dataStore?.edit(change)
        } catch (_: IOException) {
        }
    }
}
