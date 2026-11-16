@file:androidx.media3.common.util.UnstableApi
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class, at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import at.bernhardberger.tvheadend.sdk.core.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
internal class ChannelStreamProfileRuntimeTest : AudioInterruptionFixture() {
    private val connectionProfile = StreamProfileId("11111111111111111111111111111111")
    private val channelProfile = StreamProfileId("22222222222222222222222222222222")
    private val available = listOf(StreamProfile(connectionProfile, "Connection", ""), StreamProfile(channelProfile, "Channel", ""))
    private val profile = "33333333-3333-3333-3333-333333333333"

    @Test fun tunesUseOnlyTheirChannelsValidatedChoiceAndDefaultForgets() = exercise(streamProfiles = available) {
        settle()
        assertTrue("discovered profiles: ${profiles.streamProfiles.value}", profiles.streamProfiles.value is StreamProfilesResult.Available)
        useChoiceProfile(profile)
        profiles.selectStreamProfile(connectionProfile)
        settings.channelStreamProfiles.set(profile, 1, channelProfile.value)
        live(timeshift = false)
        assertEquals(channelProfile.value, subscribedProfiles.last().streamProfileUuid)
        live(timeshift = false, channel = 2)
        assertEquals(connectionProfile.value, subscribedProfiles.last().streamProfileUuid)
        for (unavailable in listOf("44444444444444444444444444444444", "unreadable")) {
            settings.channelStreamProfiles.set(profile, 1, unavailable)
            live(timeshift = false)
            assertEquals(connectionProfile.value, subscribedProfiles.last().streamProfileUuid)
            assertEquals(unavailable, settings.channelStreamProfiles.read(profile, 1))
        }
        settings.channelStreamProfiles.set(profile, 1, "no-longer-listed")
        val count = connection.subscribeCount
        runtime.selectChannelStreamProfile(ChannelId(1), null).join()
        settle()
        assertNull(settings.channelStreamProfiles.read(profile, 1))
        assertEquals("effective Default is already playing", count, connection.subscribeCount)
    }

    @Test fun picksAreWrittenBeforeRetuningAndCannotCrossAQueuedChannelSwitch() = exercise(streamProfiles = available) {
        settle()
        assertTrue("discovered profiles: ${profiles.streamProfiles.value}", profiles.streamProfiles.value is StreamProfilesResult.Available)
        useChoiceProfile(profile)
        live(timeshift = false)
        val before = connection.subscribeCount
        val writing = CompletableDeferred<Unit>()
        val stored = CompletableDeferred<Unit>()
        beforeSettingsWrite = { writing.complete(Unit); stored.await() }
        runtime.selectChannelStreamProfile(ChannelId(1), channelProfile)
        await { writing.isCompleted }
        assertEquals("the retune waits for the committed choice", before, connection.subscribeCount)
        beforeSettingsWrite = {}
        stored.complete(Unit)
        await { connection.subscribeCount > before }
        startSubscription(connection.awaitCollectionRegistered())
        await { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
        assertEquals(channelProfile.value, subscribedProfiles.last().streamProfileUuid)
        assertEquals(channelProfile.value, settings.channelStreamProfiles.read(profile, 1))
        val count = connection.subscribeCount
        runtime.selectChannelStreamProfile(ChannelId(1), channelProfile).join()
        settle()
        assertEquals(count, connection.subscribeCount)

        val release = CompletableDeferred<Unit>()
        val blocker = scope.launch { commands.serialize(onClosed = {}) { release.await() } }
        scheduler.runCurrent()
        val switch = scope.launch {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        scheduler.runCurrent()
        val pick = runtime.selectChannelStreamProfile(ChannelId(1), null)
        release.complete(Unit)
        blocker.join()
        await { connection.subscribeCount > count }
        startSubscription(connection.awaitCollectionRegistered())
        await { switch.isCompleted && pick.isCompleted }
        assertEquals(channelProfile.value, settings.channelStreamProfiles.read(profile, 1))
        assertNull(settings.channelStreamProfiles.read(profile, 2))
        assertEquals(count + 1, connection.subscribeCount)
    }

    @Test fun defaultOnFailedZapBelongsToTheRequestedChannelEvenWhenPreviousTargetIsKept() = exercise(streamProfiles = available) {
        settle()
        useChoiceProfile(profile)
        profiles.selectStreamProfile(connectionProfile)
        settings.channelStreamProfiles.set(profile, 1, connectionProfile.value)
        settings.channelStreamProfiles.set(profile, 2, channelProfile.value)
        live(timeshift = false)
        reportedPlaybackState = androidx.media3.common.Player.STATE_READY
        reportedIsPlaying = true
        markReady()
        assertEquals(AppPlaybackState.Playing, runtime.state.value)
        val before = connection.subscribeCount
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val failed = scope.launch {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        await { failed.isCompleted }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Playing, runtime.state.value)
        runtime.selectChannelStreamProfile(ChannelId(1), null).join()
        assertEquals("the retained target is not the channel the viewer requested", connectionProfile.value,
            settings.channelStreamProfiles.read(profile, 1))
        assertEquals(channelProfile.value, settings.channelStreamProfiles.read(profile, 2))
        assertEquals(before, connection.subscribeCount)
        session.scriptLivePlaybackSuccess(manager)
        val pick = runtime.selectChannelStreamProfile(ChannelId(2), null)
        await { connection.subscribeCount > before || pick.isCompleted }
        assertNull("Default from failed channel B forgets B, not the retained channel A", settings.channelStreamProfiles.read(profile, 2))
        assertEquals(connectionProfile.value, settings.channelStreamProfiles.read(profile, 1))
        await { connection.subscribeCount > before }
        startSubscription(connection.awaitCollectionRegistered())
        await { pick.isCompleted }
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertEquals(2L, subscribedChannels.last())
        assertEquals(connectionProfile.value, subscribedProfiles.last().streamProfileUuid)
        assertEquals(before + 1, connection.subscribeCount)
    }

    @Test fun defaultRecoversAChannelWhoseOwnProfileFailedBeforeInstallation() = exercise(streamProfiles = available) {
        settle()
        useChoiceProfile(profile)
        profiles.selectStreamProfile(connectionProfile)
        settings.channelStreamProfiles.set(profile, 1, channelProfile.value)
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val failed = scope.launch {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1))))
        }
        await { failed.isCompleted && runtime.state.value is AppPlaybackState.Failed }
        assertNull(runtime.activeTarget.value)
        var retried = 0
        beforeLiveBinding = { retried++ }
        runtime.selectChannelStreamProfile(ChannelId(1), channelProfile).join()
        assertEquals("a pick on a failed channel retries even its already-effective profile", 1, retried)
        beforeLiveBinding = {}
        session.scriptLivePlaybackSuccess(manager)
        val pick = runtime.selectChannelStreamProfile(ChannelId(1), null)
        await { connection.subscribeCount == 1 }
        assertTrue("the viewer sees a fresh start instead of the old failure", runtime.state.value is AppPlaybackState.Starting)
        startSubscription(connection.awaitCollectionRegistered())
        await { pick.isCompleted }
        assertEquals(connectionProfile.value, subscribedProfiles.last().streamProfileUuid)
        assertNull(settings.channelStreamProfiles.read(profile, 1))
        assertTrue(player.playWhenReady)
    }
}
