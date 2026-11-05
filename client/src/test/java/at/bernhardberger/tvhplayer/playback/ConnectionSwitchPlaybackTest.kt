@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.createSubscriptionManager
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ConnectionSwitchPlaybackTest {
    @Test fun switchStopLetsAReconnectRetuneFinishGatedDiscovery() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeServerProfileStore()
        store.addAnonymous("first.invalid", activate = true)
        val target = (store.addAnonymous("second.invalid") as ServerProfileWriteResult.Applied).id
        val manager = createSubscriptionManager(ScriptedSubscriptionConnection(), dispatcher).apply { startAdmission() }
        fun ready() = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.DENIED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1))))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )
        val fake = FakeTvheadendSession(ready()).apply { scriptLivePlaybackSuccess(manager) }
        val discovery = CompletableDeferred<Unit>()
        var gateDiscovery = false
        var discovering = false
        val session = object : TvheadendSession by fake {
            override suspend fun getStreamProfiles(currentSession: CurrentSessionObservation): StreamProfilesResult {
                if (gateDiscovery) { discovering = true; discovery.await() }
                return StreamProfilesResult.Available.create(profiles = emptyList(), originatingSession = currentSession)
            }
            override suspend fun disconnect() { fake.disconnect(); fake.publish(SessionObservation.create()) }
            override suspend fun connect(profile: ServerProfile): SessionCommandResult {
                val result = fake.connect(profile)
                fake.publish(ready())
                return result
            }
        }
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        val owner = AppProfileOwner(session, store, settings, dispatcher, { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { owner.run() }; runCurrent()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(backgroundScope) }
        val runtime = AppPlaybackRuntime(player, session, coordinator, settings, owner, backgroundScope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None, PlaybackRuntimePolicy.fromPlayerSettings())
        try {
            val start = async { runtime.playLive(checkNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))) }
            runCurrent()
            assertEquals(PlaybackTargetResult.STARTED, start.await())
            gateDiscovery = true
            fake.replaceGeneration(ready()); runCurrent()
            assertTrue(discovering)
            val retune = async { runtime.retuneAfterReconnect() }; runCurrent()
            assertFalse(retune.isCompleted)
            var stopping = false
            val attempt = async { owner.switchActive(target) { stopping = true; runtime.stop() } }
            runCurrent()
            assertTrue(stopping)
            assertFalse(attempt.isCompleted)
            discovery.complete(Unit); runCurrent()
            assertTrue("Retune must release the serializer after discovery", retune.isCompleted)
            assertTrue("Stop and switch must not deadlock behind discovery", attempt.isCompleted)
            assertEquals(ServerProfileWriteResult.Applied(target), attempt.await())
            assertEquals(target, (owner.profiles.value as ServerProfileListResult.Available).activeId)
            assertFalse(owner.connectionChanges.isLocked)
        } finally {
            discovery.complete(Unit)
            runtime.detach()
            player.release()
            session.shutdown()
        }
    }
}
