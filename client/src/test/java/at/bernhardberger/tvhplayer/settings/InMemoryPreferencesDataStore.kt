package at.bernhardberger.tvhplayer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
    private val beforeUpdate: suspend () -> Unit = {},
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (Preferences) -> Preferences,
    ): Preferences = mutex.withLock {
        beforeUpdate()
        transform(state.value).also { state.value = it }
    }
}
