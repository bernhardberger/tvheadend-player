package at.bernhardberger.tvhplayer.ui.screens.settings

import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.ServerProfileWriteResult
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ConnectionFormFeedback
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSetupEditorTest {
    @Test fun savedCredentialsWithFailedActivationRestoreThePersistedConnectionAndFailSubmission() = runTest {
        val session = FakeTvheadendSession()
        val form = ConnectionFormState()
        val stored = FakeServerProfileStore()
        val previous = (stored.addAnonymous("previous.invalid", activate = true) as ServerProfileWriteResult.Applied).id
        val target = (stored.addAnonymous("target.invalid") as ServerProfileWriteResult.Applied).id
        val store = object : ServerProfileStore by stored {
            override suspend fun setActive(id: ServerProfileId) = ServerProfileWriteResult.Unavailable
        }
        val owner = AppProfileOwner(session, store, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }
        runCurrent()
        val summary = (stored.profiles() as ServerProfileListResult.Available)
            .profiles.first { it.id == target }
        var restored = false
        val editor = ProfileSetupEditor(owner, summary,
            activate = true, restoreActive = {
                assertTrue(owner.connectionChanges.isLocked)
                restored = true
            }, onAlreadyExists = {})
        form.updateHost("target.invalid")
        form.updateUsername("sample")
        form.updatePassword("sample")
        val result = form.submit(editor)
        assertEquals(ConnectionFormFeedback.SAVE_FAILED, result)
        assertTrue(restored)
        assertEquals(previous, (stored.profiles() as ServerProfileListResult.Available).activeId)
    }
}
