package at.bernhardberger.tvhplayer.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppModuleArtworkClearTest {
    @Test fun profileSaveAndRemovalWorkWithoutClearingGlobalArtwork() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = FakeServerProfileStore()
        val session = FakeTvheadendSession()
        val owner = AppProfileOwner(
            session = session, profileStore = store, playerSettings = PlayerSettingsStore(app),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            readProfileForEditing = { error("Unexpected editing read") },
        )
        backgroundScope.launch { owner.run() }
        runCurrent()
        owner.saveServer("offline.invalid", 9982)
        assertTrue(store.loadActive() is ServerProfileReadResult.Available)
        assertTrue(owner.serverProfile.value is ServerProfileReadResult.Available)
        assertTrue(FakeSessionCall.CONNECT in session.calls)
        owner.clearProfile()
        assertEquals(ServerProfileReadResult.Missing, store.loadActive())
        assertEquals(ServerProfileReadResult.Missing, owner.serverProfile.value)
        assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CLEAR_PROFILE_CACHE), session.calls.takeLast(2))
    }
}
