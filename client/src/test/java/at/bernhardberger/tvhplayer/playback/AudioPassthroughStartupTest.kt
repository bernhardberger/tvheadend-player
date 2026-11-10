@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.test.utils.FakeClock
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
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
import at.bernhardberger.tvheadend.sdk.testing.ScriptedSubscriptionConnection
import at.bernhardberger.tvheadend.sdk.playback.createSubscriptionManager
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionOperationResult
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionConfirmation
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionEvent
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStream
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType
import at.bernhardberger.tvheadend.sdk.playback.StreamIndex
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AudioPassthroughStartupTest {
    @Test fun liveFirstStartupLoadsPersistedOffBeforeAnyFormatQuery() = exercise(recordingFirst = false, admitted = true)
    @Test fun recordingFirstStartupLoadsPersistedOffBeforeAnyFormatQuery() = exercise(recordingFirst = true, admitted = true)
    @Test fun liveFirstStartupWithHeadphonesConfiguresPcmWithoutChangingStoredSetting() =
        exercise(recordingFirst = false, headphones = true)
    @Test fun recordingFirstStartupWithHeadphonesConfiguresPcmWithoutChangingStoredSetting() =
        exercise(recordingFirst = true, headphones = true)

    private fun exercise(recordingFirst: Boolean, headphones: Boolean = false, admitted: Boolean = false) = runTest {
        val work = StandardTestDispatcher(testScheduler)
        val runtimeScope = CoroutineScope(coroutineContext + SupervisorJob())
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        settings.setAudioPassthroughEnabled(headphones)
        val outputs = object : PlaybackAudioOutputs {
            override val personalOutputConnected = MutableStateFlow(headphones)
            override fun close() = Unit
        }
        val observation = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
            )),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(
                if (admitted) listOf(Channel.create(ChannelId(1))) else emptyList())),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(
                if (admitted) listOf(DvrEntry.create(DvrEntryId(1), state = DvrEntryState.COMPLETED)) else emptyList())),
        )
        val session = if (admitted) FakeTvheadendSession(observation) else FakeTvheadendSession()
        val connection = ScriptedSubscriptionConnection()
        val manager = createSubscriptionManager(connection, work).apply { startAdmission() }
        if (admitted) {
            session.scriptLivePlaybackSuccess(manager)
            session.scriptRecordingPlaybackSuccess()
        }
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, work,
            readProfileForEditing = { ServerProfileEditReadResult.Missing }).also { owner -> runtimeScope.launch { owner.run() } }
        val output = TvheadendAudioOutputProvider(context)
        val player = ExoPlayer.Builder(context)
            .setClock(FakeClock(false))
            .setRenderersFactory(createTvheadendRenderersFactory(context, output)).build()
        val ac3 = AudioOutputProvider.FormatConfig.Builder(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).build()).build()
        var preparations = 0
        val coordinator = createTvheadendPlaybackCoordinator(object : ExoPlayer by player {
            override fun prepare() {
                if (preparations++ == 0) {
                    assertFalse("PCM must be configured before first preparation", output.isPassthroughEnabled)
                    assertEquals(AudioOutputProvider.FORMAT_UNSUPPORTED, output.getFormatSupport(ac3).supportLevel)
                }
                player.prepare()
            }
        }, workDispatcher = work).also { it.launchIn(runtimeScope) }
        val runtime = AppPlaybackRuntime(player, session, coordinator,
            settings, profiles, runtimeScope, output, PlaybackAudioFocus.None, PlaybackRuntimePolicy.fromPlayerSettings(),
            audioOutputs = outputs,
            noticeCenter = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) { at.bernhardberger.tvhplayer.notices.NoticeContext(0, null) })
        val selection = requireNotNull(observation.currentSession)
        val driver = PlaybackTestDriver(testScheduler)
        suspend fun requestTarget() {
            val subscriptions = connection.subscribeCount
            if (admitted && !recordingFirst) connection.scriptSubscribe(
                SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
            val request = runtimeScope.async {
                if (recordingFirst) runtime.playRecording(RecordingPlaybackSelection(selection, DvrEntryId(1)), RecordingPlaybackStart.START_OVER)
                else runtime.playLive(LivePlaybackSelection(selection, ChannelId(1)))
            }
            try {
                driver.settleLive(player)
                if (admitted && !recordingFirst) {
                    driver.awaitCurrent("live subscription opened") {
                        connection.subscribeCount == subscriptions + 1
                    }
                    connection.awaitCollectionRegistered()
                    connection.emit(SubscriptionEvent.Started(listOf(
                        SubscriptionStream(index = StreamIndex(1), type = SubscriptionStreamType.H264,
                            language = null, compositionId = null, ancillaryId = null, width = 320, height = 240,
                            frameDuration = null, aspectNumerator = null, aspectDenominator = null, audioType = null,
                            audioVersion = null, channelCount = null, rate = null, rdsUecp = null),
                    ), null))
                    driver.settleLive(player)
                }
                driver.awaitCurrent("target installed") { request.isCompleted }
                val result = request.await()
                if (admitted) assertTrue("Target must reach preparation", result?.isStarted == true)
            } finally {
                request.cancel()
            }
        }
        try {
            requestTarget()
            if (admitted) assertTrue("First target must have prepared", preparations > 0)
            assertFalse("First target must start in PCM mode", output.isPassthroughEnabled)
            assertEquals(headphones, settings.playerSettings.first().audioPassthroughEnabled)
            assertEquals(AudioOutputProvider.FORMAT_UNSUPPORTED, output.getFormatSupport(ac3).supportLevel)
            assertThrows(IllegalStateException::class.java) { output.configurePassthrough(true) }
            outputs.personalOutputConnected.value = false
            settings.setAudioPassthroughEnabled(true)
            // Output reconfiguration acknowledges work on Media3's real playback thread.
            driver.settleLive(player)
            driver.awaitCurrent("passthrough output enabled") { output.isPassthroughEnabled }
            requestTarget()
            assertTrue(output.isPassthroughEnabled)
            assertFalse(runtime.audioPassthroughChangeFailed.value)
            assertEquals(admitted, player.playWhenReady)
            assertNull(player.playerError)
        } finally {
            runtime.detach()
            runtimeScope.cancel()
            player.release()
            session.shutdown()
            manager.closeAndJoin()
        }
    }
}
