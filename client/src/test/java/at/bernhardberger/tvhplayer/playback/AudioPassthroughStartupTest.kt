@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendRenderersFactory
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AudioPassthroughStartupTest {
    @Test fun liveFirstStartupLoadsPersistedOffBeforeAnyFormatQuery() = exercise(recordingFirst = false)
    @Test fun recordingFirstStartupLoadsPersistedOffBeforeAnyFormatQuery() = exercise(recordingFirst = true)

    // Media3's real playback thread and this timeout must use the same wall-clock domain.
    private fun exercise(recordingFirst: Boolean) = runBlocking {
        val runtimeScope = CoroutineScope(coroutineContext + SupervisorJob())
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        settings.setAudioPassthroughEnabled(false)
        val session = FakeTvheadendSession()
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        val output = TvheadendAudioOutputProvider(context)
        val player = ExoPlayer.Builder(context)
            .setRenderersFactory(createTvheadendRenderersFactory(context, output)).build()
        val runtime = AppPlaybackRuntime(player, session, createTvheadendPlaybackCoordinator(player),
            settings, profiles, runtimeScope, output, PlaybackAudioFocus.None, PlaybackRuntimePolicy.fromPlayerSettings())
        val selection = requireNotNull(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
            )),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        ).currentSession)
        suspend fun requestTarget() {
            // Even an unavailable first target must configure output before preparation can occur.
            if (recordingFirst) runtime.playRecording(RecordingPlaybackSelection(selection, DvrEntryId(1)), RecordingPlaybackStart.START_OVER)
            else runtime.playLive(LivePlaybackSelection(selection, ChannelId(1)))
        }
        try {
            requestTarget()
            val ac3 = AudioOutputProvider.FormatConfig.Builder(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).build()).build()
            assertEquals(AudioOutputProvider.FORMAT_UNSUPPORTED, output.getFormatSupport(ac3).supportLevel)
            assertThrows(IllegalStateException::class.java) { output.configurePassthrough(true) }
            settings.setAudioPassthroughEnabled(true)
            withTimeout(5_000) {
                while (!output.isPassthroughEnabled) delay(10)
            }
            requestTarget()
            assertTrue(output.isPassthroughEnabled)
            assertFalse(runtime.audioPassthroughChangeFailed.value)
            assertFalse(player.playWhenReady)
            assertNull(player.playerError)
        } finally {
            runtime.detach()
            runtimeScope.cancel()
            player.release()
            session.shutdown()
        }
    }
}
