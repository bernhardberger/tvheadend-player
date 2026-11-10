package at.bernhardberger.tvhplayer.stores

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvhplayer.settings.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LastPlayedChannelStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun recentRecordingHistoryIsBoundedDeduplicatedAndCleared() {
        val store = LastPlayedChannelStore(context)
        val first = ServerProfileId.generate()
        runBlocking {
            useConnection(first)
            store.clear()
            (1L..51L).forEach { store.pushRecentRecordingId(DvrEntryId(it)) }
            assertEquals((51L downTo 2L).map(::DvrEntryId), store.recentRecordingIds.first())
            store.pushRecentRecordingId(DvrEntryId(10))
            val ids = store.recentRecordingIds.first()
            assertEquals(DvrEntryId(10), ids.first())
            assertEquals(50, ids.size)
            assertEquals(1, ids.count { it == DvrEntryId(10) })
            // Each connection keeps its own history.
            useConnection(ServerProfileId.generate())
            assertEquals(emptyList<DvrEntryId>(), store.recentRecordingIds.first())
            useConnection(first)
            assertEquals(DvrEntryId(10), store.recentRecordingIds.first().first())
            store.clear()
            assertEquals(emptyList<DvrEntryId>(), store.recentRecordingIds.first())
        }
    }

    private suspend fun useConnection(id: ServerProfileId) {
        context.dataStore.edit { it[stringPreferencesKey("activeServerProfileId")] = id.value }
    }
}
