@file:androidx.media3.common.util.UnstableApi
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import at.bernhardberger.tvhplayer.playback.StartupBufferVerdict.CLEAN
import at.bernhardberger.tvhplayer.playback.StartupBufferVerdict.TROUBLE
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.STARTUP_BUFFER_AUTOMATIC
import at.bernhardberger.tvhplayer.settings.StartupBufferLearningState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the controller through the player callbacks ExoPlayer would send for
 * one live start, with a stand-in player that reports play intent and state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StartupBufferControllerTest {
    private class Harness(scope: TestScope, initial: StartupBufferLearningState = StartupBufferLearningState()) {
        var storageFails = false
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore(beforeUpdate = {
            if (storageFails) throw java.io.IOException("storage full")
        }))
        val identity = MutableStateFlow<String?>("server-a")
        val loadControl = StartupBufferLoadControl(DefaultLoadControl())
        val controller = StartupBufferController(loadControl, settings, identity, scope.backgroundScope)
        var playWhenReady = true
        var state = Player.STATE_IDLE
        var trackParameters: TrackSelectionParameters = TrackSelectionParameters.DEFAULT
        private val player = java.lang.reflect.Proxy.newProxyInstance(
            ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getPlayWhenReady" -> playWhenReady
                "getPlaybackState" -> state
                "isPlaying" -> state == Player.STATE_READY && playWhenReady
                "getTrackSelectionParameters" -> trackParameters
                "addListener", "addAnalyticsListener" -> Unit
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "StartupBufferTestPlayer"
                else -> error("Unexpected player call: ${method.name}")
            }
        } as ExoPlayer

        init {
            controller.attach(player)
            if (initial != StartupBufferLearningState()) {
                kotlinx.coroutines.runBlocking { settings.updateStartupBufferLearning { initial } }
            }
        }

        suspend fun learned() = settings.startupBufferLearning.first()

        /** A committed live install followed by the first frames playing. */
        fun liveStart() {
            controller.targetInstalling(live = true)
            // The new target buffers from scratch.
            state = Player.STATE_BUFFERING
            controller.onPlaybackStateChanged(state)
            controller.targetInstallFinished(committed = true, activeIsLive = true)
            controller.liveStartApplied()
            ready()
        }

        fun ready() {
            state = Player.STATE_READY
            controller.onPlaybackStateChanged(state)
            if (playWhenReady) controller.onIsPlayingChanged(true)
        }

        /** READY to BUFFERING while playback is wanted: callbacks in ExoPlayer's order. */
        fun buffering() {
            val wasPlaying = state == Player.STATE_READY && playWhenReady
            state = Player.STATE_BUFFERING
            controller.onPlaybackStateChanged(state)
            if (wasPlaying) controller.onIsPlayingChanged(false)
        }

        fun pause() {
            playWhenReady = false
            controller.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            if (state == Player.STATE_READY) controller.onIsPlayingChanged(false)
        }

        fun resume() {
            playWhenReady = true
            controller.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            if (state == Player.STATE_READY) controller.onIsPlayingChanged(true)
        }

        /**
         * A timeshift seek or return to live as the SDK performs it: a server
         * skip without a player seek. The period resets its queues, the player
         * drops from READY to BUFFERING with playback still wanted, and the
         * skip surfaces as an internal discontinuity.
         */
        fun serverSkip(accepted: Boolean = true) {
            controller.timeshiftSeeking()
            if (!accepted) {
                controller.timeshiftSeekFinished(accepted = false)
                return
            }
            buffering()
            val position = Player.PositionInfo(null, 0, null, null, 0, 0, 0, -1, -1)
            controller.onPositionDiscontinuity(position, position, Player.DISCONTINUITY_REASON_INTERNAL)
            controller.timeshiftSeekFinished(accepted = true)
        }

        fun disableAudio(disabled: Boolean) {
            trackParameters = trackParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, disabled).build()
            controller.onTrackSelectionParametersChanged(trackParameters)
        }

        fun underrun() {
            controller.onAudioUnderrun(
                AnalyticsListener.EventTime(0, Timeline.EMPTY, 0, null, 0, Timeline.EMPTY, 0, null, 0, 0),
                0, 0, 0,
            )
        }
    }

    private fun TestScope.settle() {
        runCurrent()
    }

    @Test
    fun aSecondRebufferWithinFiveWindowsRaisesTheLevel() = runTest {
        val harness = Harness(this)
        settle()
        harness.liveStart()
        advanceTimeBy(10_000)
        harness.buffering()
        settle()
        assertEquals(StartupBufferLearningState("server-a", 500, listOf(TROUBLE)), harness.learned())
        assertEquals(StartupBufferInEffect(500, automatic = true), harness.controller.inEffect.value)
        harness.ready()
        harness.liveStart()
        advanceTimeBy(10_000)
        harness.buffering()
        settle()
        assertEquals(StartupBufferLearningState("server-a", 1000), harness.learned())
        assertEquals(StartupBufferInEffect(1000, automatic = true), harness.controller.inEffect.value)
    }

    @Test
    fun threeAudioUnderrunsAreTrouble() = runTest {
        val harness = Harness(this)
        settle()
        harness.liveStart()
        harness.underrun()
        harness.underrun()
        settle()
        assertEquals(StartupBufferLearningState(), harness.learned())
        harness.underrun()
        settle()
        assertEquals(StartupBufferLearningState("server-a", 500, listOf(TROUBLE)), harness.learned())
    }

    @Test
    fun seekAndPauseBufferingGiveNoVerdict() = runTest {
        val harness = Harness(this)
        settle()
        harness.liveStart()
        advanceTimeBy(5_000)
        harness.serverSkip()
        settle()
        assertEquals(StartupBufferLearningState(), harness.learned())
        assertTrue(harness.loadControl.isLiveSeekStartPending)

        // Pausing cuts the window: no trouble, and the 30 s never complete.
        harness.pause()
        harness.buffering()
        advanceTimeBy(60_000)
        settle()
        assertEquals(StartupBufferLearningState(), harness.learned())
    }

    @Test
    fun theStartAfterATimeshiftSeekIsWatchedToo() = runTest {
        val harness = Harness(this)
        settle()
        harness.liveStart()
        advanceTimeBy(5_000)
        harness.serverSkip()
        advanceTimeBy(60_000)
        settle()
        // The window restarts with the start after the skip, not before.
        assertEquals(StartupBufferLearningState(), harness.learned())
        // Still buffering after the grace: the skip's restart keeps its live threshold.
        assertTrue(harness.loadControl.isLiveSeekStartPending)
        harness.ready()
        advanceTimeBy(30_001)
        settle()
        assertEquals(StartupBufferLearningState("server-a", 500, listOf(CLEAN)), harness.learned())
    }

    @Test
    fun theStartAfterASkipThatRebuffersIsTrouble() = runTest {
        val harness = Harness(this)
        settle()
        harness.liveStart()
        advanceTimeBy(5_000)
        harness.serverSkip()
        harness.ready()
        advanceTimeBy(1_000)
        harness.buffering()
        settle()
        assertEquals(StartupBufferLearningState("server-a", 500, listOf(TROUBLE)), harness.learned())
    }

    @Test
    fun aSkipAbsorbedWithoutBufferingStartsTheWindowAfterTheGrace() = runTest {
        val harness = Harness(this)
        settle()
        harness.liveStart()
        advanceTimeBy(5_000)
        // The server skipped, but playback never left READY.
        harness.controller.timeshiftSeeking()
        harness.controller.timeshiftSeekFinished(accepted = true)
        advanceTimeBy(StartupBufferLoadControl.SKIP_REBUFFER_GRACE_MS - 1)
        runCurrent()
        assertTrue(harness.loadControl.isLiveSeekStartPending)
        advanceTimeBy(2)
        runCurrent()
        assertFalse(harness.loadControl.isLiveSeekStartPending)
        // The start after the skip is watched, so a later rebuffer is trouble.
        advanceTimeBy(1_000)
        harness.buffering()
        settle()
        assertEquals(StartupBufferLearningState("server-a", 500, listOf(TROUBLE)), harness.learned())
    }

    @Test
    fun aSkipPressedWhileBufferingOwnsTheRebufferInProgress() = runTest {
        val harness = Harness(this)
        settle()
        harness.liveStart()
        advanceTimeBy(5_000)
        // A stall began before the skip was pressed; Media3 keeps its start time.
        harness.buffering()
        val stalledAtMs = android.os.SystemClock.elapsedRealtime() - 1_000
        harness.controller.timeshiftSeeking()
        harness.controller.timeshiftSeekFinished(accepted = true)
        val item = MediaItem.Builder().setMediaId(SDK_LIVE_MEDIA_ID).build()
        val timeline = SinglePeriodTimeline(C.TIME_UNSET, false, true, true, null, item)
        val restart = LoadControl.Parameters(
            PlayerId.UNSET, timeline, MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0)), 0L,
            1_500_000L, 1f, true, true, C.TIME_UNSET, stalledAtMs,
        )
        // Automatic's 0.5 s start threshold decides, not the delegate's 5 s rebuffer threshold.
        harness.loadControl.onPrepared(PlayerId.UNSET)
        assertTrue(harness.loadControl.shouldStartPlayback(restart))
        assertFalse(harness.loadControl.isLiveSeekStartPending)
    }

    @Test
    fun aSkipAbsorbedWithoutBufferingIsWatchedForTheFullWindow() = runTest {
        val harness = Harness(this)
        settle()
        harness.liveStart()
        advanceTimeBy(5_000)
        harness.controller.timeshiftSeeking()
        harness.controller.timeshiftSeekFinished(accepted = true)
        advanceTimeBy(StartupBufferLoadControl.SKIP_REBUFFER_GRACE_MS + 30_001)
        settle()
        assertEquals(StartupBufferLearningState("server-a", 500, listOf(CLEAN)), harness.learned())
    }

    @Test
    fun aStartWhileAudioIsDisabledArmsNothing() = runTest {
        val harness = Harness(this)
        settle()
        harness.disableAudio(true)
        harness.liveStart()
        harness.underrun()
        harness.underrun()
        harness.underrun()
        harness.buffering()
        harness.ready()
        advanceTimeBy(30_001)
        settle()
        assertEquals(StartupBufferLearningState(), harness.learned())

        // A skip while audio is disabled arms nothing either.
        harness.serverSkip()
        harness.ready()
        advanceTimeBy(1_000)
        harness.buffering()
        harness.ready()
        advanceTimeBy(60_000)
        settle()
        assertEquals(StartupBufferLearningState(), harness.learned())
    }

    @Test
    fun aRejectedSkipCutsTheWindowAndLeavesNoLiveStartPending() = runTest {
        val harness = Harness(this)
        settle()
        harness.liveStart()
        advanceTimeBy(5_000)
        harness.serverSkip(accepted = false)
        advanceTimeBy(60_000)
        harness.buffering()
        harness.ready()
        advanceTimeBy(60_000)
        settle()
        assertEquals(StartupBufferLearningState(), harness.learned())
        assertFalse(harness.loadControl.isLiveSeekStartPending)
    }

    @Test
    fun aPausedStartArmsNothingAndALaterResumeOpensNoWindow() = runTest {
        val harness = Harness(this)
        settle()
        harness.playWhenReady = false
        harness.liveStart()
        harness.resume()
        advanceTimeBy(30_001)
        harness.buffering()
        settle()
        assertEquals(StartupBufferLearningState(), harness.learned())
    }

    @Test
    fun pausingAnArmedStartCutsIt() = runTest {
        val harness = Harness(this)
        settle()
        harness.controller.targetInstalling(live = true)
        harness.controller.targetInstallFinished(committed = true, activeIsLive = true)
        harness.controller.liveStartApplied()
        harness.pause()
        harness.buffering()
        harness.resume()
        harness.ready()
        advanceTimeBy(30_001)
        settle()
        assertEquals(StartupBufferLearningState(), harness.learned())
    }

    @Test
    fun aStartAlreadyPlayingWhenItsIntentIsAppliedIsWatched() = runTest {
        val harness = Harness(this)
        settle()
        harness.controller.targetInstalling(live = true)
        harness.ready()
        harness.controller.targetInstallFinished(committed = true, activeIsLive = true)
        harness.controller.liveStartApplied()
        advanceTimeBy(30_001)
        settle()
        assertEquals(StartupBufferLearningState("server-a", 500, listOf(CLEAN)), harness.learned())
    }

    @Test
    fun mutingOrUnmutingAudioCutsTheWindow() = runTest {
        val harness = Harness(this)
        settle()
        harness.liveStart()
        harness.disableAudio(true)
        advanceTimeBy(30_001)
        settle()
        assertEquals(StartupBufferLearningState(), harness.learned())

        harness.liveStart()
        harness.disableAudio(false)
        harness.buffering()
        settle()
        assertEquals(StartupBufferLearningState(), harness.learned())

        // Unrelated parameter changes keep the window.
        harness.liveStart()
        harness.trackParameters = harness.trackParameters.buildUpon().setMaxVideoBitrate(1_000_000).build()
        harness.controller.onTrackSelectionParametersChanged(harness.trackParameters)
        advanceTimeBy(30_001)
        settle()
        assertEquals(StartupBufferLearningState("server-a", 500, listOf(CLEAN)), harness.learned())
    }

    @Test
    fun aStorageFailureKeepsTheLevelAndPlaybackRunning() = runTest {
        // One more trouble would raise the level.
        val initial = StartupBufferLearningState("server-a", 500, listOf(TROUBLE))
        val harness = Harness(this, initial)
        settle()
        harness.storageFails = true
        harness.liveStart()
        harness.buffering()
        settle()
        assertEquals(initial, harness.learned())
        assertEquals(StartupBufferInEffect(500, automatic = true), harness.controller.inEffect.value)

        // Later windows still learn once storage recovers.
        harness.storageFails = false
        harness.ready()
        harness.liveStart()
        harness.buffering()
        settle()
        assertEquals(StartupBufferLearningState("server-a", 1000), harness.learned())
    }

    @Test
    fun switchingFromFixedToAutomaticMidWindowLearnsNothing() = runTest {
        val harness = Harness(this)
        settle()
        harness.settings.setStartupBufferMillis(1000)
        settle()
        harness.liveStart()
        harness.settings.setStartupBufferMillis(STARTUP_BUFFER_AUTOMATIC)
        settle()
        harness.buffering()
        advanceTimeBy(30_001)
        settle()
        assertEquals(StartupBufferLearningState(), harness.learned())
    }

    @Test
    fun switchingAutomaticToFixedAndBackMidWindowLearnsNothing() = runTest {
        val harness = Harness(this)
        settle()
        harness.liveStart()
        harness.settings.setStartupBufferMillis(1000)
        settle()
        harness.settings.setStartupBufferMillis(STARTUP_BUFFER_AUTOMATIC)
        settle()
        harness.buffering()
        advanceTimeBy(30_001)
        settle()
        assertEquals(StartupBufferLearningState(), harness.learned())
    }

    @Test
    fun aServerChangeMidWindowLearnsNothing() = runTest {
        val harness = Harness(this)
        settle()
        harness.liveStart()
        harness.identity.value = "server-b"
        settle()
        harness.buffering()
        settle()
        assertEquals(StartupBufferLearningState(), harness.learned())
    }

    @Test
    fun aWindowMeasuredAtAnotherLevelDoesNotMoveIt() = runTest {
        val harness = Harness(this)
        settle()
        harness.liveStart()
        // The learned level moved on since this window started at 0.5 s.
        harness.settings.updateStartupBufferLearning { StartupBufferLearningState("server-a", 2000) }
        settle()
        harness.buffering()
        settle()
        assertEquals(StartupBufferLearningState("server-a", 2000), harness.learned())
    }

    @Test
    fun fiveCleanWindowsLowerTheLevel() = runTest {
        val harness = Harness(this, StartupBufferLearningState("server-a", 2000))
        settle()
        assertEquals(StartupBufferInEffect(2000, automatic = true), harness.controller.inEffect.value)
        repeat(4) {
            harness.liveStart()
            advanceTimeBy(30_001)
            settle()
        }
        assertEquals(StartupBufferLearningState("server-a", 2000, List(4) { CLEAN }), harness.learned())
        harness.liveStart()
        advanceTimeBy(30_001)
        settle()
        assertEquals(StartupBufferLearningState("server-a", 1500), harness.learned())
        assertEquals(StartupBufferInEffect(1500, automatic = true), harness.controller.inEffect.value)
    }

    @Test
    fun recordingStartsAndFixedLevelsDoNotLearn() = runTest {
        val harness = Harness(this)
        settle()
        harness.controller.targetInstalling(live = false)
        harness.controller.targetInstallFinished(committed = true, activeIsLive = false)
        harness.buffering()
        harness.ready()
        harness.buffering()
        settle()
        assertEquals(StartupBufferLearningState(), harness.learned())

        harness.settings.setStartupBufferMillis(500)
        settle()
        assertEquals(StartupBufferInEffect(500, automatic = false), harness.controller.inEffect.value)
        harness.liveStart()
        harness.buffering()
        settle()
        assertEquals(StartupBufferLearningState(), harness.learned())
    }

    @Test
    fun anotherServerStartsFromHalfASecond() = runTest {
        val harness = Harness(this, StartupBufferLearningState("server-a", 2500, listOf(TROUBLE)))
        settle()
        assertEquals(2500, harness.controller.inEffect.value.millis)
        harness.identity.value = "server-b"
        settle()
        assertEquals(StartupBufferInEffect(500, automatic = true), harness.controller.inEffect.value)
        harness.liveStart()
        harness.buffering()
        settle()
        assertEquals(StartupBufferLearningState("server-b", 500, listOf(TROUBLE)), harness.learned())
    }
}
