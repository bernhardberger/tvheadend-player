@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import androidx.media3.common.C
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.common.Timeline
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.trackselection.FixedTrackSelection
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.test.utils.FakeClock
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

/** Drives the released SDK source with real readers/queues, without depending on a device decoder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AudioInterruptionBufferContractTest {
    @Test fun disabledAudioIsDiscardedWhileVideoAdvancesBeyondTheSampleLimit() = exercise(discard = true)

    @Test
    fun withoutDiscardDisabledAudioReportsExhaustionAfterRetirement() = exercise(discard = false)

    private fun exercise(discard: Boolean) = runTest {
        val work = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val driver = PlaybackTestDriver(testScheduler)
        // Period preparation and sample availability are signals from Media3's real loader thread.
        fun await(predicate: () -> Boolean) = driver.await("buffer contract condition", predicate = predicate)
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Application>())
            .setClock(FakeClock(false)).build()
        var source: MediaSource? = null
        // Capture the actual source installed by the public coordinator; drive its period below.
        val capturingPlayer = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader,
            arrayOf(ExoPlayer::class.java)) { _, method, args ->
            if (method.name == "setMediaSource") {
                source = args!![0] as MediaSource
                // Install the real source so SDK ownership checks see its actual timeline uid.
                // Suppress prepare below: this test drives its period, not ExoPlayer's renderer.
                method.invoke(player, *args)
            }
            else if (method.name == "prepare") null
            else method.invoke(player, *(args ?: emptyArray()))
        } as ExoPlayer
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
        }
        val manager = createSubscriptionManager(connection, work).apply { startAdmission() }
        val session = FakeTvheadendSession(currentPlaybackObservation(channels = listOf(Channel.create(ChannelId(1))))).apply { scriptLivePlaybackSuccess(manager) }
        val recoveries = mutableListOf<PlaybackRecoveryReason>()
        val coordinator = createTvheadendPlaybackCoordinator(capturingPlayer, workDispatcher = work, onRecoveryRequired = {
            assertEquals(0, player.mediaItemCount)
            recoveries += it
        }).also { it.launchIn(scope) }
        var period: MediaPeriod? = null
        var timeline: Timeline? = null
        val caller = MediaSource.MediaSourceCaller { _, value -> timeline = value }
        // The body crosses Media3's real loader thread while preparing and reading its period.
        driver.exercise(this, 60_000) body@ { try {
            val result = coordinator.setLiveTarget(session, requireNotNull(session.observation.value.currentSession), ChannelId(1))
            assertTrue(result is LivePlaybackTargetResult.Bound && result.result.isStarted)
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            val liveSource = requireNotNull(source)
            liveSource.prepareSource(caller, PlayerId.UNSET, BandwidthMeter.NO_OP)
            await { timeline != null }
            val allocator = DefaultAllocator(true, 64 * 1024)
            val livePeriod = liveSource.createPeriod(MediaSource.MediaPeriodId(timeline!!.getUidOfPeriod(0)), allocator, 0)
            period = livePeriod
            var prepared = false
            livePeriod.prepare(object : MediaPeriod.Callback {
                override fun onPrepared(mediaPeriod: MediaPeriod) { prepared = true }
                override fun onContinueLoadingRequested(source: MediaPeriod) = Unit
            }, 0)
            connection.awaitCollectionRegistered()
            connection.emit(SubscriptionEvent.Started(listOf(
                subscriptionStream(1, SubscriptionStreamType.MPEG2_VIDEO), subscriptionStream(2, SubscriptionStreamType.MPEG2_AUDIO),
            ), null))
            repeat(8) { emitVideoAudioPair(connection, it) }
            await { livePeriod.maybeThrowPrepareError(); prepared }
            val groups = livePeriod.trackGroups
            assertEquals(2, groups.length)
            val video = (0 until groups.length).map(groups::get).single { it.type == C.TRACK_TYPE_VIDEO }
            assertTrue(C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes)
            // This is ExoPlayer's selection shape when the audio track type is disabled.
            val streams = arrayOfNulls<SampleStream>(2)
            livePeriod.selectTracks(arrayOf(FixedTrackSelection(video, 0), null),
                BooleanArray(2), streams, BooleanArray(2), 0)
            assertNull(streams[1])
            val videoStream = requireNotNull(streams[0])
            val holder = FormatHolder()
            val buffer = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL)
            var consumed = 0
            var lastPosition = 0L
            // 5,120 packets per track exceed the SDK's 4,096-sample cap if disabled audio is retained.
            for (batch in 0 until 80) {
                repeat(64) { emitVideoAudioPair(connection, 8 + batch * 64 + it) }
                val expectedEnd = (8L + batch * 64 + 62) * 40_000
                await {
                    livePeriod.maybeThrowPrepareError()
                    while (true) {
                        buffer.clear()
                        when (videoStream.readData(holder, buffer, 0)) {
                            C.RESULT_FORMAT_READ -> continue
                            C.RESULT_BUFFER_READ -> { consumed++; lastPosition = buffer.timeUs }
                            else -> break
                        }
                    }
                    if (discard) livePeriod.discardBuffer(lastPosition, false)
                    lastPosition >= expectedEnd || recoveries.isNotEmpty()
                }
                if (recoveries.isNotEmpty()) {
                    assertFalse(discard)
                    assertEquals(listOf(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED), recoveries)
                    return@body
                }
                if (discard) assertTrue("Queues must remain bounded while audio is disabled", allocator.totalBytesAllocated < 2 * 1024 * 1024)
            }
            assertTrue(consumed > 4_096)
            assertTrue("Retained disabled audio must exhaust its bounded queue", discard)
            livePeriod.maybeThrowPrepareError()
            videoStream.maybeThrowError()
        } finally {
            period?.let { source?.releasePeriod(it) }
            if (timeline != null) source?.releaseSource(caller)
            scope.cancel()
            manager.closeAndJoin()
            session.shutdown()
            player.release()
        } }
    }

}
