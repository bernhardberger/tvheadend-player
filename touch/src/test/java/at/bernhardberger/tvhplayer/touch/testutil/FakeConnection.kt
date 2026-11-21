package at.bernhardberger.tvhplayer.touch.testutil

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The app's session and profile owner on the SDK's fakes, as the composition root builds them.
 * Everything runs on the main looper, so Robolectric idling settles it; [close] after each test.
 */
@OptIn(TvheadendTestingApi::class)
internal class FakeConnection {
    val session = FakeTvheadendSession()
    val store = FakeServerProfileStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val owner = AppProfileOwner(
        session, store, PlayerSettingsStore(MemoryPreferences()), Dispatchers.Main,
        { ServerProfileEditReadResult.Missing },
    )

    /** Starts the owner, which reads the saved connection as the app does at start. */
    fun start() = apply { scope.launch { owner.run() } }

    fun close() = scope.cancel()
}

private class MemoryPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(data.value).also { data.value = it }
}
