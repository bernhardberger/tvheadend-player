@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.os.Looper
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
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.io.IOException

/** Drives the released SDK source with real readers/queues, without depending on a device decoder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AudioInterruptionBufferContractTest {
    @Test fun disabledAudioIsDiscardedWhileVideoAdvancesBeyondTheSampleLimit() = exercise(discard = true)

    @Test(expected = IOException::class)
    fun withoutDiscardDisabledAudioHitsTheReleasedSdkBufferLimit() = exercise(discard = false)

    private fun exercise(discard: Boolean) = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Application>()).build()
        var source: MediaSource? = null
        // Capture the actual source installed by the public coordinator; drive its period below.
        val capturingPlayer = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader,
            arrayOf(ExoPlayer::class.java)) { _, method, args ->
            if (method.name == "setMediaSource") { source = args!![0] as MediaSource; null }
            else method.invoke(player, *(args ?: emptyArray()))
        } as ExoPlayer
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
        }
        val manager = createSubscriptionManager(connection, Dispatchers.Default).apply { startAdmission() }
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1))))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )).apply { scriptLivePlaybackSuccess(manager) }
        val coordinator = createTvheadendPlaybackCoordinator(capturingPlayer).also { it.launchIn(scope) }
        var period: MediaPeriod? = null
        var timeline: Timeline? = null
        val caller = MediaSource.MediaSourceCaller { _, value -> timeline = value }
        try {
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
                stream(1, SubscriptionStreamType.MPEG2_VIDEO), stream(2, SubscriptionStreamType.MPEG2_AUDIO),
            ), null, SubscriptionCondition.NO_DETAIL))
            repeat(8) { emitPair(connection, it) }
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
            repeat(80) { batch ->
                repeat(64) { emitPair(connection, 8 + batch * 64 + it) }
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
                    lastPosition >= expectedEnd
                }
                if (discard) assertTrue("Queues must remain bounded while audio is disabled", allocator.totalBytesAllocated < 2 * 1024 * 1024)
            }
            assertTrue(consumed > 4_096)
            livePeriod.maybeThrowPrepareError()
            videoStream.maybeThrowError()
        } finally {
            period?.let { source?.releasePeriod(it) }
            if (timeline != null) source?.releaseSource(caller)
            scope.cancel()
            manager.closeAndJoin()
            session.shutdown()
            player.release()
        }
    }

    private suspend fun await(predicate: () -> Boolean) = withTimeout(5_000) {
        while (!predicate()) { shadowOf(Looper.getMainLooper()).idle(); delay(1) }
    }

    private fun stream(index: Long, type: SubscriptionStreamType) = SubscriptionStream(
        index = StreamIndex(index), type = type, language = null, compositionId = null, ancillaryId = null,
        width = null, height = null, frameDuration = null, aspectNumerator = null, aspectDenominator = null,
        audioType = null, audioVersion = null, channelCount = null, rate = null, rdsUecp = null, codecMetadata = null,
    )

    private suspend fun emitPair(connection: ScriptedSubscriptionConnection, index: Int) {
        val time = index * 40_000L
        // Synthetic MPEG-2 sequence/picture headers and an MPEG-1 Layer II frame. No decoding needed.
        val video = byteArrayOf(0, 0, 1, 0xb3.toByte(), 0x14, 0, 0xf0.toByte(), 0x13,
            0xff.toByte(), 0xff.toByte(), 0xe0.toByte(), 0x18,
            0, 0, 1, 0xb8.toByte(), 0, 0, 0, 0x40, 0, 0, 1, 0, 0, 8, 0, 0)
        val audio = ByteArray(417).apply { this[0] = 0xff.toByte(); this[1] = 0xfd.toByte(); this[2] = 0x80.toByte(); this[3] = 0xc0.toByte() }
        connection.emit(SubscriptionEvent.Packet(MuxFrameType.I, StreamIndex(1), time, time, 40_000, SubscriptionBinaryFixture(video)))
        connection.emit(SubscriptionEvent.Packet(MuxFrameType.I, StreamIndex(2), time, time, 40_000, SubscriptionBinaryFixture(audio)))
    }
}
