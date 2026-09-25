@file:OptIn(at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.os.Looper
import androidx.media3.common.Player
import at.bernhardberger.tvheadend.sdk.playback.LiveSubscriptionPriority
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackRuntimeTest.Companion.exercise
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaybackRuntimePolicyTest {
    @Test fun fromPlayerSettingsMapsKeepMinutesAndTimeshiftSetting() {
        val policy = PlaybackRuntimePolicy.fromPlayerSettings()
        for (minutes in listOf(0, 10, 20, 30)) {
            assertEquals(minutes.minutes, policy.keepTunedLimit(PlayerSettings(keepChannelMinutes = minutes)))
        }
        assertEquals(Duration.ZERO, policy.keepTunedLimit(PlayerSettings(keepChannelMinutes = -5)))
        assertEquals(2.hours, policy.liveTimeshiftPeriod(PlayerSettings(timeshiftEnabled = true)))
        assertEquals(Duration.ZERO, policy.liveTimeshiftPeriod(PlayerSettings(timeshiftEnabled = false)))
        assertEquals(PlaybackTrace.None, policy.trace)
        assertTrue(!policy.seekDiagnostics)
    }

    @Test fun fixedKeepPolicyKeepsTimeshiftChannelEvenWhenSettingsAreOff() = exercise(
        PlaybackRuntimePolicy(keepTunedLimit = { 10.minutes }, liveTimeshiftPeriod = { 2.hours }),
    ) {
        settings.setKeepChannelMinutes(0)
        live()
        runtime.onAppBackgrounded()
        await { connection.priorityChanges == listOf(LiveSubscriptionPriority.YIELD) }
        settle()
        assertNotNull(runtime.activeTarget.value)
    }

    @Test fun zeroKeepPolicyReleasesEvenWhenSettingsKeepTwentyMinutes() = exercise(
        PlaybackRuntimePolicy(keepTunedLimit = { Duration.ZERO }, liveTimeshiftPeriod = { 2.hours }),
    ) {
        settings.setKeepChannelMinutes(20)
        live()
        runtime.onAppBackgrounded()
        await { runtime.activeTarget.value == null }
        assertTrue(connection.priorityChanges.isEmpty())
    }

    @Test fun zeroTimeshiftPolicyRequestsNoTimeshiftAndReleasesInBackground() = exercise(
        PlaybackRuntimePolicy(keepTunedLimit = { 20.minutes }, liveTimeshiftPeriod = { Duration.ZERO }),
    ) {
        live(timeshift = true) // Settings enable timeshift; the host policy overrides the request.
        assertEquals(0L, connection.requestedTimeshiftSeconds)
        runtime.onAppBackgrounded()
        await { runtime.activeTarget.value == null }
        assertTrue(connection.priorityChanges.isEmpty())
    }

    @Test fun enabledTraceSeesTuneBoundAndReadyWithActiveEpoch() {
        val trace = RecordingTrace(enabled = true)
        exercise(PlaybackRuntimePolicy.fromPlayerSettings(trace = trace)) {
            live()
            val epoch = runtime.videoPresentation.value.epoch
            playerListeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_READY) }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, trace.admitted)
            assertEquals(listOf(epoch), trace.bound)
            assertTrue(trace.ready.isNotEmpty())
            assertTrue(trace.ready.all { it == epoch })
        }
    }

    @Test fun enabledTraceSeesFirstVideoFrameOncePerActiveEpoch() {
        val trace = RecordingTrace(enabled = true)
        exercise(PlaybackRuntimePolicy.fromPlayerSettings(trace = trace)) {
            live()
            val epoch = runtime.videoPresentation.value.epoch
            renderFirstFrame()
            assertTrue(runtime.videoPresentation.value.visible)
            assertEquals(listOf(epoch), trace.frames)
            renderFirstFrame()
            assertEquals(listOf(epoch), trace.frames)
        }
    }

    @Test fun enabledTraceSeesCoverShownAtInstallAndLiftedAfterTheFirstFrame() {
        val trace = RecordingTrace(enabled = true)
        exercise(PlaybackRuntimePolicy.fromPlayerSettings(trace = trace)) {
            live()
            val epoch = runtime.videoPresentation.value.epoch
            assertEquals(listOf("cover"), trace.events)
            renderFirstFrame()
            assertEquals(listOf("cover", "frame:$epoch", "lifted:$epoch"), trace.events)
        }
    }

    @Test fun disabledTraceIsNeverCalled() {
        val trace = RecordingTrace(enabled = false)
        exercise(PlaybackRuntimePolicy.fromPlayerSettings(trace = trace)) {
            live()
            playerListeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_READY) }
            renderFirstFrame()
            settle()
            assertTrue(runtime.videoPresentation.value.visible)
            assertEquals(0, trace.calls)
        }
    }

    private fun BackgroundPlaybackRuntimeTest.Fixture.renderFirstFrame() {
        playerListeners.toList().forEach { it.onRenderedFirstFrame() }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private class RecordingTrace(override val enabled: Boolean) : PlaybackTrace {
        var admitted = 0
        val bound = mutableListOf<Long>()
        val ready = mutableListOf<Long?>()
        val frames = mutableListOf<Long>()
        val events = mutableListOf<String>()
        var calls = 0

        override fun tuneAdmitted() { calls++; admitted++ }
        override fun tuneBound(epoch: Long) { calls++; bound += epoch }
        override fun ready(epoch: Long?) { calls++; ready += epoch }
        override fun firstVideoFrame(epoch: Long, format: androidx.media3.common.Format?, adapterName: String?) {
            calls++
            frames += epoch
            events += "frame:$epoch"
        }
        override fun videoCoverShown() { calls++; events += "cover" }
        override fun videoCoverLifted(epoch: Long) { calls++; events += "lifted:$epoch" }
    }
}
