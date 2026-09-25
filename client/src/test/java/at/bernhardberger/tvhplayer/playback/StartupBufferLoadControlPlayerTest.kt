@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
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
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
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
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.playback.MuxFrameType
import at.bernhardberger.tvheadend.sdk.playback.StreamIndex
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionCondition
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionConfirmation
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionEvent
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionOperationResult
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStream
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType
import at.bernhardberger.tvheadend.sdk.playback.createSubscriptionManager
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvheadend.sdk.testing.ScriptedSubscriptionConnection
import at.bernhardberger.tvheadend.sdk.testing.SubscriptionBinaryFixture
import java.lang.reflect.Proxy
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A real ExoPlayer drives the wrapper: prepare, play and release reach the delegate, a start from
 * the SDK's live source waits for the live threshold, and any other item starts by the delegate.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StartupBufferLoadControlPlayerTest {
    private val calls = Collections.synchronizedList(mutableListOf<String>())
    private val real = DefaultLoadControl()
    private val delegate = Proxy.newProxyInstance(
        LoadControl::class.java.classLoader, arrayOf(LoadControl::class.java),
    ) { _, method, args ->
        calls += method.name
        method.invoke(real, *(args ?: emptyArray()))
    } as LoadControl
    private val control = StartupBufferLoadControl(delegate)
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun aRealPlayerPreparesPlaysAndReleasesThroughTheWrapper() {
        val player = ExoPlayer.Builder(context).setLoadControl(control).build()
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
        for (name in listOf(
            "onPrepared", "onTracksSelected", "getAllocator", "getBackBufferDurationUs",
            "retainBackBufferFromKeyframe", "shouldContinueLoading", "onStopped", "onReleased",
        )) assertTrue("$name reached the delegate: ${calls.toSet()}", name in calls)
    }

    @Test
    fun aNonLiveStartIsDecidedByTheDelegate() {
        val player = ExoPlayer.Builder(context)
            .setRenderersFactory { _, _, _, _, _ -> arrayOf<Renderer>(SampleSink(C.TRACK_TYPE_AUDIO)) }
            .setLoadControl(control)
            .build()
        // A threshold the wrapper would never meet: only the delegate can start this item.
        control.setLiveStartBufferMillis(600_000)
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
            assertTrue("The delegate made the start decision: ${calls.toSet()}", "shouldStartPlayback" in calls)
        } finally {
            player.release()
        }
    }

    @Test
    fun aLiveStartFromTheSdkLiveSourceWaitsForTheLiveThreshold() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val player = ExoPlayer.Builder(context)
            .setRenderersFactory { _, _, _, _, _ -> arrayOf<Renderer>(SampleSink()) }
            .setLoadControl(control)
            .build()
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
        val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(scope) }
        try {
            // Far more than the delegate's 1 s, so only the wrapper can hold the start.
            control.setLiveStartBufferMillis(60_000)
            val result = coordinator.setLiveTarget(session, requireNotNull(session.observation.value.currentSession), ChannelId(1))
            assertTrue(result is LivePlaybackTargetResult.Bound && result.result.isStarted)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
            connection.awaitCollectionRegistered()
            connection.emit(SubscriptionEvent.Started(listOf(
                stream(1, SubscriptionStreamType.MPEG2_VIDEO), stream(2, SubscriptionStreamType.MPEG2_AUDIO),
            ), null, SubscriptionCondition.NO_DETAIL))
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
            assertEquals(StartupBufferLoadControl.LIVE_MEDIA_ID, player.currentMediaItem?.mediaId)

            // Lowering the live threshold below what is buffered lets the same start begin.
            control.setLiveStartBufferMillis(2_000)
            awaitMain { player.playerError != null || player.isPlaying }
            assertNull(player.playerError)
            assertTrue(player.isPlaying)
            assertFalse("The delegate was asked for a live start: ${calls.toSet()}", "shouldStartPlayback" in calls)
        } finally {
            scope.cancel()
            manager.closeAndJoin()
            session.shutdown()
            player.release()
        }
    }

    /** Accepts video samples and is ready whenever the stream has one; decodes nothing. */
    private class SampleSink(private val type: Int = C.TRACK_TYPE_VIDEO) : BaseRenderer(type) {
        override fun getName() = "SampleSink"
        override fun supportsFormat(format: Format): Int = RendererCapabilities.create(
            if (MimeTypes.getTrackType(format.sampleMimeType) == type) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_TYPE,
        )
        override fun isReady() = isSourceReady
        override fun isEnded() = false
        override fun render(positionUs: Long, elapsedRealtimeUs: Long) = Unit
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
        audioType = null, audioVersion = null, channelCount = null, rate = null, rdsUecp = null, codecMetadata = null,
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
