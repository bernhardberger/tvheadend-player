package at.bernhardberger.tvhplayer.core

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.viewmodels.MainStartupViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Root exit (a user stop) and a relaunch from the launcher in the same process: the
 * startup autoplay request is the viewer's playback intent, so the live player it opens
 * stays open instead of taking the root-exit stop for the latest playback event.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StartupAutoplayIntentTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val player = ExoPlayer.Builder(context).build()
    private val runtime = run {
        val session = FakeTvheadendSession(SessionObservation.create())
        val settings = PlayerSettingsStore(preferences())
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        AppPlaybackRuntime(player, session, createTvheadendPlaybackCoordinator(player), settings, profiles, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None, PlaybackRuntimePolicy.fromPlayerSettings())
    }

    @After fun after() { scope.cancel(); player.release() }

    @Test fun startupAutoplayAfterARootExitStopOpensAPlayerThatStaysOpen() = runBlocking {
        runtime.stop() // Back at the root: stopPlaybackAndClose(playbackRuntime::stop, finish)
        assertNull(runtime.enterPlayerScreen())

        startup(autoStartPlayback = true)

        val entry = requireNotNull(runtime.enterPlayerScreen())
        assertFalse(runtime.isPlaybackIntentStopped(entry))
    }

    @Test fun startupWithoutAutoplayOrAlreadyHandledNotesNoIntent() = runBlocking {
        runtime.stop()
        startup(autoStartPlayback = false)
        startup(autoStartPlayback = true, createStartupRequest = false) // restored activity
        startup(autoStartPlayback = true, server = ServerSettings()) // not configured
        assertNull(runtime.enterPlayerScreen())
    }

    @Test fun theAppWiresTheRuntimeIntoEveryLaunchRequest() {
        var root = File("").absoluteFile
        while (!File(root, "app/src/main").isDirectory) root = root.parentFile
        fun source(path: String) = File(root, "app/src/main/java/at/bernhardberger/tvhplayer/$path").readText()
            .lines().joinToString("") { it.substringBefore("//") }.filterNot(Char::isWhitespace)
        assertEquals(1, source("di/AppModule.kt")
            .split("noteViewingIntent=get<AppPlaybackRuntime>()::notePlaybackIntent").size - 1)
        assertTrue(source("viewmodels/MainStartupViewModel.kt")
            .contains("createRetainedApplianceLaunchRequests(savedStateHandle,noteViewingIntent)"))
    }

    private suspend fun startup(
        autoStartPlayback: Boolean,
        createStartupRequest: Boolean = true,
        server: ServerSettings = ServerSettings(host = "tvh.invalid"),
    ) {
        // A fresh MainStartupViewModel's requests, hooked as the Koin module hooks them.
        val requests = MainStartupViewModel.createRetainedApplianceLaunchRequests(
            SavedStateHandle(),
            runtime::notePlaybackIntent,
        )
        StartupBootstrapCoordinator(
            applianceLaunchRequests = requests,
            loadServerSettings = { server },
            loadUiSettings = { UiSettings(autoStartPlayback = autoStartPlayback) },
            createStartupRequest = createStartupRequest,
        ).bootstrap()
        assertEquals(autoStartPlayback && createStartupRequest && server.host.isNotBlank(),
            requests.state.value is ApplianceLaunchState.Pending)
    }

    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(data.value).also { data.value = it }
    }
}
