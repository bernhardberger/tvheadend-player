package at.bernhardberger.tvhplayer.playback

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AutomaticAudioRuntimeTest {
    @Test fun queuedAutomaticChoiceSurvivesSheetCancellationAndPublishesOverrideState() = runTest {
        val work = StandardTestDispatcher(testScheduler)
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        val session = FakeTvheadendSession()
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings,
            work,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        val player = ExoPlayer.Builder(context).setClock(FakeClock(false)).build()
        val runtime = AppPlaybackRuntime(player, session, createTvheadendPlaybackCoordinator(player, workDispatcher = work),
            settings, profiles, backgroundScope, TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None,
            PlaybackRuntimePolicy.fromPlayerSettings(),
            noticeCenter = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) { at.bernhardberger.tvhplayer.notices.NoticeContext(0, null) })
        val sheetScope = CoroutineScope(coroutineContext + Job())
        // Hold the existing command serializer without adding a production test seam.
        val commands = AppPlaybackRuntime::class.java.getDeclaredField("targetCommands").let {
            it.isAccessible = true
            it.get(runtime) as PlaybackTargetCommandSerialization
        }
        val releaseCommand = CompletableDeferred<Unit>()
        try {
            runCurrent()
            assertTrue(runtime.audioAutomatic.value)
            val group = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build())
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .addOverride(TrackSelectionOverride(group, listOf(0)))
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            assertFalse(runtime.audioAutomatic.value)
            val blocker = launch { commands.serialize(onClosed = {}) { releaseCommand.await() } }
            runCurrent()
            lateinit var choice: Job
            sheetScope.launch { choice = runtime.useAutomaticAudio() }
            runCurrent()
            assertFalse(choice.isCompleted)
            sheetScope.cancel()
            releaseCommand.complete(Unit)
            blocker.join()
            PlaybackTestDriver(testScheduler).awaitCurrent("queued automatic choice completed") { choice.isCompleted }
            choice.join()
            assertFalse(choice.isCancelled)
            assertTrue(runtime.audioAutomatic.value)
            assertTrue(player.trackSelectionParameters.overrides.isEmpty())
            assertTrue(player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO))
        } finally {
            releaseCommand.complete(Unit)
            sheetScope.cancel()
            runtime.detach()
            player.release()
            session.shutdown()
        }
    }
}
