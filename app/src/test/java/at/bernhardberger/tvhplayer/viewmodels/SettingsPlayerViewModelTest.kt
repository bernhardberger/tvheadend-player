package at.bernhardberger.tvhplayer.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPlayerViewModelTest {
    @Test
    fun autoplayChangesPersistAndUpdatePlayerUiState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Application>()
        val player = ExoPlayer.Builder(context).build()
        val models = ViewModelStore()
        try {
            val session = FakeTvheadendSession()
            val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
            val uiSettings = UiSettingsStore(context)
            uiSettings.setAutoStartPlayback(false)
            val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, dispatcher,
                readProfileForEditing = { ServerProfileEditReadResult.Missing })
            val runtime = AppPlaybackRuntime(
                player, session, createTvheadendPlaybackCoordinator(player), settings, profiles,
                backgroundScope, TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None,
                PlaybackRuntimePolicy.fromPlayerSettings(),
            )
            val model = SettingsPlayerViewModel(settings, runtime, session, profiles, uiSettings)
            models.put("player", model)

            for (enabled in listOf(true, false)) {
                model.onAutoStartPlaybackChanged(enabled)
                model.ui.first { it.autoStartPlayback == enabled }
                assertEquals(enabled, uiSettings.settings.first().autoStartPlayback)
                assertEquals(enabled, model.ui.value.autoStartPlayback)
            }
        } finally {
            models.clear()
            player.release()
            Dispatchers.resetMain()
        }
    }
}
