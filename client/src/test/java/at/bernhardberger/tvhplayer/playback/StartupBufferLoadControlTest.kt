@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.DefaultAllocator
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Media id of the SDK's live media item, which `isTvheadendLive()` recognises. */
internal const val SDK_LIVE_MEDIA_ID = "tvheadend-live"

class StartupBufferLoadControlTest {
    /** Stands in for the SDK load control: records every call and always refuses to start. */
    private val calls = mutableListOf<String>()
    private val allocator = DefaultAllocator(true, 1024)
    private val delegate = Proxy.newProxyInstance(
        LoadControl::class.java.classLoader, arrayOf(LoadControl::class.java),
    ) { _, method, _ ->
        calls += method.name
        when (method.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Long.TYPE -> 0L
            Allocator::class.java -> allocator
            else -> null
        }
    } as LoadControl
    private var nowMs = 10_000L
    private val control = StartupBufferLoadControl(delegate) { nowMs }

    private val startQueries get() = calls.count { it == "shouldStartPlayback" }

    /** One media item in its own timeline, as the player sees it after setMediaSource. */
    private class Target(item: MediaItem, dynamic: Boolean = false) {
        val timeline: Timeline = SinglePeriodTimeline(C.TIME_UNSET, false, dynamic, dynamic, null, item)
        val period = MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0))
    }

    private fun live() = Target(MediaItem.Builder().setMediaId(SDK_LIVE_MEDIA_ID).build(), dynamic = true)

    /** The SDK's recording items carry their tvheadend-recording URI as id and may be dynamic while growing. */
    private fun recording(growing: Boolean = false) = Target(
        MediaItem.Builder().setMediaId("tvheadend-recording://dvr/1").build(),
        dynamic = growing,
    )

    private fun period(uid: Any = Any()) = MediaSource.MediaPeriodId(uid)

    private fun parameters(
        bufferedMs: Long,
        target: Target,
        rebuffering: Boolean = false,
        targetLiveOffsetMs: Long? = null,
        lastRebufferMs: Long = if (rebuffering) nowMs else C.TIME_UNSET,
    ) = parameters(bufferedMs, target.timeline, target.period, rebuffering, targetLiveOffsetMs, lastRebufferMs)

    private fun parameters(
        bufferedMs: Long,
        timeline: Timeline,
        period: MediaSource.MediaPeriodId,
        rebuffering: Boolean = false,
        targetLiveOffsetMs: Long? = null,
        lastRebufferMs: Long = C.TIME_UNSET,
    ) = LoadControl.Parameters(
        PlayerId.UNSET, timeline, period, 0L,
        bufferedMs * 1000, 1f, true, rebuffering,
        targetLiveOffsetMs?.let { it * 1000 } ?: C.TIME_UNSET, lastRebufferMs,
    )

    private fun select(target: Target) {
        control.onTracksSelected(parameters(0, target), TrackGroupArray.EMPTY, arrayOf<ExoTrackSelection?>())
    }

    @Test
    fun everyNonDeprecatedMethodIsDeclaredNotInherited() {
        // Kotlin class delegation does not forward Java default methods; their
        // defaults throw (onPrepared, onTracksSelected) or refuse (shouldContinuePreloading).
        val missing = LoadControl::class.java.methods
            .filter { !Modifier.isStatic(it.modifiers) && !it.isAnnotationPresent(java.lang.Deprecated::class.java) }
            .filter { method ->
                runCatching {
                    StartupBufferLoadControl::class.java.getDeclaredMethod(method.name, *method.parameterTypes)
                }.isFailure
            }
            .map { it.name }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun everyCallExceptTheStartDecisionReachesTheDelegate() {
        val period = period()
        val parameters = parameters(0, Timeline.EMPTY, period)
        control.onPrepared(PlayerId.UNSET)
        control.onTracksSelected(parameters, TrackGroupArray.EMPTY, arrayOf<ExoTrackSelection?>())
        control.getAllocator(PlayerId.UNSET)
        control.getBackBufferDurationUs(PlayerId.UNSET)
        control.retainBackBufferFromKeyframe(PlayerId.UNSET)
        control.shouldContinueLoading(parameters)
        control.shouldContinuePreloading(PlayerId.UNSET, Timeline.EMPTY, period, 0L)
        control.onStopped(PlayerId.UNSET)
        control.onReleased(PlayerId.UNSET)
        assertEquals(
            listOf(
                "onPrepared", "onTracksSelected", "getAllocator", "getBackBufferDurationUs",
                "retainBackBufferFromKeyframe", "shouldContinueLoading", "shouldContinuePreloading",
                "onStopped", "onReleased",
            ),
            calls,
        )
    }

    @Test
    fun beforeAnySettingArrivesALiveStartWaitsForTheAutomaticStartingLevel() {
        val live = live()
        assertFalse(control.shouldStartPlayback(parameters(499, live)))
        assertTrue(control.shouldStartPlayback(parameters(500, live)))
        assertEquals(0, startQueries)
    }

    @Test
    fun liveStartWaitsForTheConfiguredBuffer() {
        val live = live()
        control.setLiveStartBufferMillis(1500)
        assertFalse(control.shouldStartPlayback(parameters(1499, live)))
        assertTrue(control.shouldStartPlayback(parameters(1500, live)))
        assertEquals(0, startQueries)
    }

    @Test
    fun aChangedThresholdAppliesToTheNextQuery() {
        val live = live()
        control.setLiveStartBufferMillis(3000)
        assertFalse(control.shouldStartPlayback(parameters(1000, live)))
        control.setLiveStartBufferMillis(500)
        assertTrue(control.shouldStartPlayback(parameters(1000, live)))
    }

    @Test
    fun liveTargetOffsetStillCapsTheThreshold() {
        control.setLiveStartBufferMillis(3000)
        assertTrue(control.shouldStartPlayback(parameters(1000, live(), targetLiveOffsetMs = 2000)))
    }

    @Test
    fun recordingStartsDelegateAlsoWhileTheyGrow() {
        control.setLiveStartBufferMillis(500)
        assertFalse(control.shouldStartPlayback(parameters(1000, recording())))
        assertFalse(control.shouldStartPlayback(parameters(1000, recording(growing = true))))
        assertEquals(2, startQueries)
    }

    @Test
    fun liveRebufferDelegates() {
        control.setLiveStartBufferMillis(500)
        assertFalse(control.shouldStartPlayback(parameters(1000, live(), rebuffering = true)))
        assertEquals(1, startQueries)
    }

    @Test
    fun anEmptyTimelineAndAPeriodOutsideTheTimelineDelegate() {
        control.setLiveStartBufferMillis(500)
        assertFalse(control.shouldStartPlayback(parameters(1000, Timeline.EMPTY, period())))
        assertFalse(control.shouldStartPlayback(parameters(1000, live().timeline, period())))
        assertEquals(2, startQueries)
    }

    @Test
    fun aChangedSdkLiveIdMakesEveryStartDelegate() {
        control.setLiveStartBufferMillis(500)
        val renamed = Target(MediaItem.Builder().setMediaId("tvheadend-live-2").build(), dynamic = true)
        val unnamed = Target(MediaItem.Builder().build(), dynamic = true)
        assertFalse(control.shouldStartPlayback(parameters(1000, renamed)))
        assertFalse(control.shouldStartPlayback(parameters(1000, unnamed)))
        assertEquals(2, startQueries)
    }

    @Test
    fun aLiveRestartInANewPeriodIsALiveStart() {
        control.setLiveStartBufferMillis(500)
        select(live())
        // The SDK restarts live with a new period uid for the same live item.
        val restart = live()
        assertTrue(control.shouldStartPlayback(parameters(1000, restart)))
        assertEquals(0, startQueries)
    }

    @Test
    fun aRecordingFirstSelectedDuringAFailedLiveInstallStillDelegates() {
        control.setLiveStartBufferMillis(500)
        // A live replacement begins installing and selects its tracks, then fails.
        // The recording that stays makes its first track selection only now. Any
        // flag set for the live install would still read live at that moment;
        // the kind must come from the recording's own item.
        val recording = recording()
        select(live())
        select(recording)
        assertFalse(control.shouldStartPlayback(parameters(1000, recording)))
        assertEquals(1, startQueries)
    }

    @Test
    fun theSkipRebufferIsALiveStartOnce() {
        val live = live()
        control.setLiveStartBufferMillis(1500)
        control.liveSeekStarting()
        nowMs += 200
        val rebufferAt = nowMs
        // Media3 reports the skip's buffering as a rebuffer that began after the request.
        assertFalse(control.shouldStartPlayback(parameters(1000, live, rebuffering = true, lastRebufferMs = rebufferAt)))
        control.liveSeekFinished()
        assertTrue(control.shouldStartPlayback(parameters(1500, live, rebuffering = true, lastRebufferMs = rebufferAt)))
        assertEquals(0, startQueries)
        assertFalse(control.isLiveSeekStartPending)
        // A later rebuffer is the delegate's again.
        nowMs += 1_000
        assertFalse(control.shouldStartPlayback(parameters(1500, live, rebuffering = true)))
        assertEquals(1, startQueries)
    }

    @Test
    fun aSkipRebufferInsideTheGraceUsesTheStartThresholdOnce() {
        val live = live()
        control.setLiveStartBufferMillis(500)
        control.liveSeekStarting()
        nowMs += 100
        control.liveSeekFinished()
        nowMs += StartupBufferLoadControl.SKIP_REBUFFER_GRACE_MS
        assertTrue(control.shouldStartPlayback(parameters(1000, live, rebuffering = true)))
        assertFalse(control.shouldStartPlayback(parameters(1000, live, rebuffering = true)))
        assertEquals(1, startQueries)
    }

    @Test
    fun unconfirmedSkipWithoutRestartDoesNotOverrideLaterRebuffer() {
        val live = live()
        control.setLiveStartBufferMillis(500)
        // An UNCONFIRMED skip counts as accepted, but the stream never restarted.
        control.liveSeekStarting()
        control.liveSeekFinished()
        nowMs += StartupBufferLoadControl.SKIP_REBUFFER_GRACE_MS + 1
        // A genuine network rebuffer much later waits for the SDK's rebuffer buffer.
        assertFalse(control.shouldStartPlayback(parameters(1000, live, rebuffering = true)))
        nowMs += 60_000
        assertFalse(control.shouldStartPlayback(parameters(1000, live, rebuffering = true)))
        assertEquals(2, startQueries)
    }

    @Test
    fun aRebufferThatBeganBeforeTheSkipIsNotTakenOver() {
        val live = live()
        control.setLiveStartBufferMillis(500)
        val rebufferAt = nowMs
        nowMs += 1
        control.liveSeekStarting()
        assertFalse(control.shouldStartPlayback(parameters(1000, live, rebuffering = true, lastRebufferMs = rebufferAt)))
        control.liveSeekFinished()
        assertFalse(control.shouldStartPlayback(parameters(1000, live, rebuffering = true, lastRebufferMs = rebufferAt)))
        // A rebuffer without a known start time is never the skip's either.
        assertFalse(control.shouldStartPlayback(parameters(1000, live, rebuffering = true, lastRebufferMs = C.TIME_UNSET)))
        assertEquals(3, startQueries)
        assertTrue(control.isLiveSeekStartPending)
    }

    @Test
    fun aSkipPressedWhileBufferingTakesOverTheRebufferInProgress() {
        val live = live()
        control.setLiveStartBufferMillis(2000)
        // The first skip's restart is still buffering when the second skip is pressed;
        // Media3 keeps the first rebuffer's start time.
        control.liveSeekStarting()
        nowMs += 100
        val rebufferAt = nowMs
        control.liveSeekFinished()
        nowMs += 300
        control.liveSeekStarting(alreadyBuffering = true)
        assertFalse(control.shouldStartPlayback(parameters(1500, live, rebuffering = true, lastRebufferMs = rebufferAt)))
        nowMs += 200
        control.liveSeekFinished()
        assertTrue(control.shouldStartPlayback(parameters(2000, live, rebuffering = true, lastRebufferMs = rebufferAt)))
        // The same holds for a skip pressed during a genuine stall.
        val stallAt = nowMs
        nowMs += 10_000
        control.liveSeekStarting(alreadyBuffering = true)
        control.liveSeekFinished()
        assertTrue(control.shouldStartPlayback(parameters(2000, live, rebuffering = true, lastRebufferMs = stallAt)))
        assertEquals(0, startQueries)
        assertFalse(control.isLiveSeekStartPending)
    }

    @Test
    fun finishingSkipConcurrentlyWithStartConsumesTheMarkExactlyOnce() {
        control.setLiveStartBufferMillis(500)
        val live = live()
        var answered = false
        // The skip's answer arrives on the runtime thread while the playback
        // thread is deciding the skip's start, after it has read the mark.
        val timeline = object : ForwardingTimeline(live.timeline) {
            override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                if (!answered) {
                    answered = true
                    control.liveSeekFinished()
                }
                return super.getWindow(windowIndex, window, defaultPositionProjectionUs)
            }
        }
        control.liveSeekStarting()
        nowMs += 100
        assertTrue(control.shouldStartPlayback(parameters(1000, timeline, live.period, rebuffering = true, lastRebufferMs = nowMs)))
        assertTrue(answered)
        assertFalse(control.isLiveSeekStartPending)
        // A genuine rebuffer inside the grace is the delegate's.
        nowMs += 500
        assertFalse(control.shouldStartPlayback(parameters(1000, live, rebuffering = true)))
        assertEquals(1, startQueries)
    }

    @Test
    fun aClearedSkipLeavesRebuffersToTheDelegate() {
        control.setLiveStartBufferMillis(500)
        control.liveSeekStarting()
        control.clearLiveSeekStart()
        assertFalse(control.isLiveSeekStartPending)
        assertFalse(control.shouldStartPlayback(parameters(1000, live(), rebuffering = true)))
        assertEquals(1, startQueries)
    }

    @Test
    fun aSkipMarkNeverAppliesToARecording() {
        control.setLiveStartBufferMillis(500)
        control.liveSeekStarting()
        nowMs += 10
        assertFalse(control.shouldStartPlayback(parameters(1000, recording(growing = true), rebuffering = true)))
        assertEquals(1, startQueries)
        assertTrue(control.isLiveSeekStartPending)
    }
}
