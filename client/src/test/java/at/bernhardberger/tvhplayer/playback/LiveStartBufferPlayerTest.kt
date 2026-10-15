@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackOptions
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendLiveStartBuffer
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendLoadControl
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.media3.isTvheadendLive
import at.bernhardberger.tvheadend.sdk.playback.MuxFrameType
import at.bernhardberger.tvheadend.sdk.playback.SkipOutcome
import at.bernhardberger.tvheadend.sdk.playback.StreamIndex
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionConfirmation
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionEvent
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionOperationResult
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStream
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType
import at.bernhardberger.tvheadend.sdk.playback.createSubscriptionManager
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvheadend.sdk.testing.ScriptedSubscriptionConnection
import at.bernhardberger.tvheadend.sdk.testing.SubscriptionBinaryFixture
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A real ExoPlayer uses the SDK handle: live starts obey the app's mutable threshold,
 * while other items retain the SDK's ordinary buffering policy.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveStartBufferPlayerTest {
    private val liveStartBuffer = TvheadendLiveStartBuffer(500.milliseconds)
    private val control = createTvheadendLoadControl(liveStartBuffer)
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun aRealPlayerPreparesPlaysAndReleasesThroughTheSdkHandle() {
        val player = control.configure(ExoPlayer.Builder(context)).build()
        var error: PlaybackException? = null
        player.addListener(object : Player.Listener {
            override fun onPlayerError(value: PlaybackException) { error = value }
        })
        player.setMediaSource(SilenceMediaSource(10_000_000L))
        player.prepare()
        player.play()
        idleUntil { error != null || player.isPlaying }
        assertNull(error)
        assertTrue(player.isPlaying)
        assertEquals(Player.STATE_READY, player.playbackState)
        player.stop()
        player.release()
        shadowOf(Looper.getMainLooper()).idleFor(TICK)
        assertNull(error)
    }

    @Test
    fun aNonLiveStartIsDecidedByTheDelegate() {
        val player = control.configure(ExoPlayer.Builder(context))
            .setRenderersFactory { _, _, _, _, _ -> arrayOf<Renderer>(SampleSink(C.TRACK_TYPE_AUDIO)) }
            .build()
        // A live threshold this file cannot meet: non-live policy must start this item.
        liveStartBuffer.duration = 600_000.milliseconds
        try {
            // A slowly delivered file keeps loading after the delegate's start buffer, so Media3 asks.
            val wav = pcmWav(seconds = 60)
            val source = ProgressiveMediaSource.Factory({ SlowDataSource(wav) })
                .createMediaSource(MediaItem.Builder().setMediaId("recording").setUri("file:///recording.wav").build())
            player.setMediaSource(source)
            player.prepare()
            player.play()
            idleUntil { player.playerError != null || player.isPlaying }
            assertNull(player.playerError)
            assertTrue(player.isPlaying)
            assertTrue(player.isLoading)
        } finally {
            player.release()
        }
    }

    @Test
    fun aLiveStartFromTheSdkLiveSourceWaitsForTheLiveThreshold() = liveBufferExercise(skip = false)

    @Test
    fun aTimeshiftSkipUsesTheLiveThresholdOnceThenOrdinaryRebufferUsesOnePointFiveSeconds() = liveBufferExercise(skip = true)

    private fun liveBufferExercise(skip: Boolean) = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val player = control.configure(ExoPlayer.Builder(context))
            .setRenderersFactory { _, _, _, _, _ -> arrayOf<Renderer>(SampleSink(consume = skip)) }
            .build()
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
        }
        val manager = createSubscriptionManager(connection, Dispatchers.Default).apply { startAdmission() }
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1))))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )).apply { scriptLivePlaybackSuccess(manager) }
        val coordinator = createTvheadendPlaybackCoordinator(player, control).also { it.launchIn(scope) }
        try {
            // Far more than ordinary startup buffering, so the live threshold holds the start.
            liveStartBuffer.duration = 60_000.milliseconds
            val result = coordinator.setLiveTarget(session, requireNotNull(session.observation.value.currentSession), ChannelId(1),
                LivePlaybackOptions(timeshiftPeriod = 120.seconds))
            assertTrue(result is LivePlaybackTargetResult.Bound && result.result.isStarted)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
            connection.awaitCollectionRegistered()
            connection.emit(SubscriptionEvent.Started(listOf(
                stream(1, SubscriptionStreamType.MPEG2_VIDEO), stream(2, SubscriptionStreamType.MPEG2_AUDIO),
            ), null))
            repeat(8) { emitPair(connection, it) }
            // The period exposes its tracks from the first packets; the player selects them before buffering.
            awaitMain { player.playerError != null || !player.currentTracks.isEmpty }
            repeat(100) {
                emitPair(connection, 8 + it)
                shadowOf(Looper.getMainLooper()).idleFor(TICK)
                delay(2)
            }
            awaitMain { player.playerError != null || player.totalBufferedDuration >= 3_000 }
            assertNull(player.playerError)
            repeat(20) { shadowOf(Looper.getMainLooper()).idleFor(TICK); delay(10) }
            assertEquals(Player.STATE_BUFFERING, player.playbackState)
            assertTrue(requireNotNull(player.currentMediaItem).isTvheadendLive())

            // Lowering the live threshold below what is buffered lets the same start begin.
            liveStartBuffer.duration = 2_000.milliseconds
            awaitMain { player.playerError != null || player.isPlaying }
            assertNull(player.playerError)
            assertTrue(player.isPlaying)
            if (skip) {
                connection.emit(SubscriptionEvent.Timeshift(1_000_000, 0, 0, 120_000_000, 100))
                liveStartBuffer.duration = 60_000.milliseconds
                val returning = async { coordinator.returnToLive() }
                awaitMain { connection.seekTargets.isNotEmpty() }
                connection.emit(SubscriptionEvent.Skipped(true, SkipOutcome.ACCEPTED, 0, null))
                awaitMain { returning.isCompleted }
                assertEquals(TimeshiftCommandResult.ACCEPTED, returning.await())
                awaitMain { player.playbackState == Player.STATE_BUFFERING }
                repeat(100) { emitPair(connection, it); shadowOf(Looper.getMainLooper()).idleFor(TICK); delay(2) }
                awaitMain { player.totalBufferedDuration >= 3_000 }
                assertEquals(Player.STATE_BUFFERING, player.playbackState)
                liveStartBuffer.duration = 2_000.milliseconds
                awaitMain { player.isPlaying }
                // The one-shot mark has been consumed. Withhold packets until an ordinary stall.
                liveStartBuffer.duration = 60_000.milliseconds
                awaitMain { player.playbackState == Player.STATE_BUFFERING }
                repeat(20) { emitPair(connection, 100 + it) }
                repeat(20) { shadowOf(Looper.getMainLooper()).idleFor(TICK); delay(5) }
                assertTrue(player.totalBufferedDuration < 1_500)
                assertEquals(Player.STATE_BUFFERING, player.playbackState)
                repeat(35) { emitPair(connection, 120 + it) }
                awaitMain { player.isPlaying }
                assertTrue(player.totalBufferedDuration < 3_000)
                assertNull(player.playerError)
            }
        } finally {
            scope.cancel()
            manager.closeAndJoin()
            session.shutdown()
            player.release()
        }
    }

    /** Accepts video samples and is ready whenever the stream has one; decodes nothing. */
    private class SampleSink(private val type: Int = C.TRACK_TYPE_VIDEO, private val consume: Boolean = false) : BaseRenderer(type) {
        private val holder = FormatHolder()
        private val buffer = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED)
        private var heldTimeUs: Long? = null
        override fun getName() = "SampleSink"
        override fun supportsFormat(format: Format): Int = RendererCapabilities.create(
            if (MimeTypes.getTrackType(format.sampleMimeType) == type) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_TYPE,
        )
        override fun isReady() = heldTimeUs != null || isSourceReady
        override fun isEnded() = false
        override fun onPositionReset(positionUs: Long, joining: Boolean, sampleStreamIsResetToKeyFrame: Boolean) {
            heldTimeUs = null
        }
        override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
            if (!consume) return
            while (true) {
                val timeUs = heldTimeUs ?: run {
                    buffer.clear()
                    when (readSource(holder, buffer, SampleStream.FLAG_OMIT_SAMPLE_DATA)) {
                        C.RESULT_FORMAT_READ -> return@run null
                        C.RESULT_BUFFER_READ -> if (buffer.isEndOfStream) return else buffer.timeUs
                        else -> return
                    }
                } ?: continue
                if (timeUs > positionUs) {
                    heldTimeUs = timeUs
                    return
                }
                heldTimeUs = null
            }
        }
    }

    private companion object {
        // Media3 schedules its playback loop on Robolectric's clock; idling alone never advances it.
        val TICK: java.time.Duration = java.time.Duration.ofMillis(10)
    }

    /** Serves [data] a little at a time, like a file still arriving from a server. */
    private class SlowDataSource(data: ByteArray) : BaseDataSource(false) {
        private val bytes = ByteArrayDataSource(data)
        override fun open(dataSpec: DataSpec): Long = bytes.open(dataSpec)
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            Thread.sleep(2)
            return bytes.read(buffer, offset, minOf(length, 1_600))
        }
        override fun getUri(): Uri? = bytes.uri
        override fun close() = bytes.close()
    }

    /** Mono 16-bit 8 kHz silence. */
    private fun pcmWav(seconds: Int): ByteArray {
        val size = seconds * 16_000
        return java.nio.ByteBuffer.allocate(44 + size).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(8_000); putInt(16_000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(size)
        }.array()
    }

    private fun idleUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(TICK)
            Thread.sleep(5)
        }
    }

    // Media3's real playback thread and this timeout share the wall clock.
    private suspend fun awaitMain(condition: () -> Boolean) = withTimeout(10_000) {
        while (!condition()) { shadowOf(Looper.getMainLooper()).idleFor(TICK); delay(5) }
    }

    private fun stream(index: Long, type: SubscriptionStreamType) = SubscriptionStream(
        index = StreamIndex(index), type = type, language = null, compositionId = null, ancillaryId = null,
        width = null, height = null, frameDuration = null, aspectNumerator = null, aspectDenominator = null,
        audioType = null, audioVersion = null, channelCount = null, rate = null, rdsUecp = null,
    )

    private suspend fun emitPair(connection: ScriptedSubscriptionConnection, index: Int) {
        val time = index * 40_000L
        // Same synthetic MPEG-2 video and MPEG-1 Layer II audio as AudioInterruptionBufferContractTest.
        val video = byteArrayOf(0, 0, 1, 0xb3.toByte(), 0x14, 0, 0xf0.toByte(), 0x13,
            0xff.toByte(), 0xff.toByte(), 0xe0.toByte(), 0x18,
            0, 0, 1, 0xb8.toByte(), 0, 0, 0, 0x40, 0, 0, 1, 0, 0, 8, 0, 0)
        val audio = ByteArray(417).apply { this[0] = 0xff.toByte(); this[1] = 0xfd.toByte(); this[2] = 0x80.toByte(); this[3] = 0xc0.toByte() }
        connection.emit(SubscriptionEvent.Packet(MuxFrameType.I, StreamIndex(1), time, time, 40_000, SubscriptionBinaryFixture(video)))
        connection.emit(SubscriptionEvent.Packet(MuxFrameType.I, StreamIndex(2), time, time, 40_000, SubscriptionBinaryFixture(audio)))
    }
}
