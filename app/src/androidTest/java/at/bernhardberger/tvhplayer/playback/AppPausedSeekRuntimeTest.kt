@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.EGLSurfaceTexture
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendRenderersFactory
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStoreCall
import at.bernhardberger.tvheadend.sdk.testing.ScriptedSubscriptionConnection
import at.bernhardberger.tvheadend.sdk.testing.SubscriptionBinaryFixture
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.testing.testSessionObservation
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.security.MessageDigest
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real application runtime and decoded surface output; no injected position or seek token. */
@RunWith(AndroidJUnit4::class)
class AppPausedSeekRuntimeTest {
    @Test
    fun selectedSeeksThroughAppRuntimePreservePausePlayingAndReferenceContinuation() = runBlocking {
        withTimeout(90.seconds) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val connection = ScriptedSubscriptionConnection()
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
            val manager = createSubscriptionManager(connection, Dispatchers.Default).apply { startAdmission() }
            val session = FakeTvheadendSession(testSessionObservation(channels = listOf(Channel.create(ChannelId(1)))))
                .apply { scriptLivePlaybackSuccess(manager) }
            val settings = PlayerSettingsStore(object : DataStore<Preferences> {
                override val data = MutableStateFlow(emptyPreferences())
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                    transform(data.value).also { data.value = it }
            })
            val profileStore = FakeServerProfileStore()
            val profiles = AppProfileOwner(
                session = session, profileStore = profileStore, playerSettings = settings,
                ioDispatcher = Dispatchers.IO,
                readProfileForEditing = { ServerProfileEditReadResult.Missing },
            )
            val frames = AtomicInteger()
            val images = AtomicInteger()
            val failure = AtomicReference<String>()
            val sourceFailure = AtomicReference<Throwable>()
            val metadata = CopyOnWriteArrayList<Pair<Long, Int>>()
            val thread = HandlerThread("app-paused-seek-surface").apply { start() }
            val handler = Handler(thread.looper)
            val texture = EGLSurfaceTexture(handler) { images.incrementAndGet() }
            val ready = CountDownLatch(1)
            handler.post { texture.init(EGLSurfaceTexture.SECURE_MODE_NONE); ready.countDown() }
            assertTrue("Surface initialized", ready.await(5, TimeUnit.SECONDS))
            val surface = Surface(texture.surfaceTexture)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val profileJob = scope.launch { profiles.run() }
            val player = withContext(Dispatchers.Main) {
                ExoPlayer.Builder(context, createTvheadendRenderersFactory(context))
                    .setLoadControl(createPlaybackLoadControl())
                    .build().apply {
                        volume = 0f
                        setVideoSurface(surface)
                        setVideoFrameMetadataListener { time, _, _, _ -> metadata.add(time to images.get()) }
                        addListener(object : Player.Listener {
                            override fun onRenderedFirstFrame() { frames.incrementAndGet() }
                            override fun onPlayerError(error: PlaybackException) {
                                sourceFailure.set(error.cause)
                                failure.set(error.errorCodeName)
                            }
                        })
                    }
            }
            val coordinator = withContext(Dispatchers.Main) { createTvheadendPlaybackCoordinator(player) }
            val lifetime = coordinator.launchIn(scope)
            val runtime = withContext(Dispatchers.Main) { AppPlaybackRuntime(player, session, coordinator, settings, profiles, scope) }
            suspend fun await(message: String, predicate: suspend () -> Boolean) {
                withTimeout(15.seconds) {
                    while (!predicate()) {
                        assertNull("$message playback error", failure.get())
                        delay(10)
                    }
                }
            }
            suspend fun position() = withContext(Dispatchers.Main) { player.currentPosition }
            try {
                profiles.serverProfile.filterNotNull().first()
                assertEquals(at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult.Missing, profiles.serverProfile.value)
                val (streams, packets) = capturedMedia()
                val origins = packets.groupBy { it.streamIndex }.mapValues { (_, values) ->
                    values.minOf { checkNotNull(it.presentationTimeUs) }
                }
                val install = async(Dispatchers.Main) {
                    withTimeout(10.seconds) {
                        runtime.playLive(checkNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1))))
                    }
                }
                val registration = withTimeout(10.seconds) { connection.awaitCollectionRegistered() }
                suspend fun deliverMedia(edge: Long? = null, stopWhenPaused: Boolean = false) {
                    val delivery = packets.map { packet ->
                        val origin = origins.getValue(packet.streamIndex)
                        if (edge == null) packet else SubscriptionEvent.Packet(
                            frameType = packet.frameType, streamIndex = packet.streamIndex,
                            durationUs = packet.durationUs, payload = packet.payload,
                            presentationTimeUs = edge * 1_000_000 + checkNotNull(packet.presentationTimeUs) - origin,
                            decodingTimeUs = packet.decodingTimeUs?.let { edge * 1_000_000 + it - origin },
                        )
                    }.sortedBy { checkNotNull(it.decodingTimeUs) }
                    val firstDts = checkNotNull(delivery.first().decodingTimeUs)
                    val started = TimeSource.Monotonic.markNow()
                    delivery.forEach { packet ->
                        val remaining = (checkNotNull(packet.decodingTimeUs) - firstDts).microseconds - started.elapsedNow()
                        if (remaining.isPositive()) delay(remaining)
                        if (stopWhenPaused && connection.speeds.lastOrNull() == 0) return
                        connection.emit(registration, packet)
                    }
                }
                connection.emit(registration, SubscriptionEvent.Started(streams, null, SubscriptionCondition.NO_DETAIL))
                deliverMedia()
                assertTrue("App installs the public binding", install.await()?.isStarted == true)
                await("Initial playing surface") { frames.get() > 0 && images.get() > 0 && position() > 0 }
                connection.emit(registration, SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
                await("App timeline") { runtime.sampleTimeshiftPresentation().timeline != null }
                assertEquals(TimeshiftCommandResult.ACCEPTED, withContext(Dispatchers.Main) { runtime.pauseTimeshift() })
                connection.emit(registration, SubscriptionEvent.Speed(0))
                await("App observes paused intent") { withContext(Dispatchers.Main) { !player.playWhenReady } }
                val expectedSpeeds = mutableListOf(0)
                for (edge in listOf(0L, 60L, 30L, 90L, 120L)) {
                    val timeline = checkNotNull(runtime.sampleTimeshiftPresentation().timeline)
                    val selection = checkNotNull(timeline.resolveSelection(checkNotNull(timeline.select(60.seconds)),
                        delta = (edge - 60).seconds))
                    val beforeFrames = frames.get()
                    val beforeImages = images.get()
                    val beforeCommands = connection.seekTargets.size
                    val seek = async(Dispatchers.Main) { runtime.seekTimeshift(selection) }
                    await("Seek admission") { connection.seekTargets.size > beforeCommands }
                    connection.emit(registration, SubscriptionEvent.Skipped(true, SkipOutcome.ACCEPTED, null, null))
                    delay(250)
                    assertEquals("Acknowledgement is not a frame", beforeFrames, frames.get())
                    assertFalse("Acknowledgement is not buffered playback", seek.isCompleted)
                    await("Paused transport refill") { connection.speeds.last() == 100 }
                    connection.emit(registration, SubscriptionEvent.Speed(100))
                    withContext(Dispatchers.Main) { assertFalse("Refill must not start playback", player.playWhenReady) }
                    deliverMedia(edge, stopWhenPaused = true)
                    val result = seek.await() as TimeshiftContentSeekResult.Completed
                    assertEquals(TimeshiftCommandResult.ACCEPTED, result.command)
                    assertNotNull(result.seek)
                    assertEquals(edge.seconds, result.selection!!.target.position)
                    expectedSpeeds += listOf(100, 0)
                    connection.emit(registration, SubscriptionEvent.Speed(0))
                    await("Paused surface frame") { frames.get() > beforeFrames && images.get() > beforeImages }
                    await("App correlated position") {
                        val sample = runtime.sampleTimeshiftPresentation()
                        sample.timingKnown && sample.playbackSeek === result.seek
                    }
                    val settled = position()
                    delay(300)
                    assertEquals("Paused clock stays stopped", settled, position())
                    withContext(Dispatchers.Main) { assertFalse("No forced resume", player.playWhenReady) }
                    assertEquals(expectedSpeeds, connection.speeds)
                    val previewTime = metadata.last().first
                    val continuationStart = metadata.size
                    assertEquals(TimeshiftCommandResult.ACCEPTED, withContext(Dispatchers.Main) { runtime.resumeTimeshift() })
                    expectedSpeeds += 100
                    connection.emit(registration, SubscriptionEvent.Speed(100))
                    await("App observes explicit resume") { withContext(Dispatchers.Main) { player.playWhenReady } }
                    await("Dependent continuation reaches surface") {
                        position() > settled + 100 && metadata.drop(continuationStart).any { (time, priorImages) ->
                            time > previewTime + 80_000 && time < previewTime + 600_000 && images.get() > priorImages
                        }
                    }
                    assertEquals(TimeshiftCommandResult.ACCEPTED, withContext(Dispatchers.Main) { runtime.pauseTimeshift() })
                    expectedSpeeds += 0
                    connection.emit(registration, SubscriptionEvent.Speed(0))
                    await("Pause after continuation") { withContext(Dispatchers.Main) { !player.playWhenReady } }
                }
                assertEquals(TimeshiftCommandResult.ACCEPTED, withContext(Dispatchers.Main) { runtime.resumeTimeshift() })
                expectedSpeeds += 100
                connection.emit(registration, SubscriptionEvent.Speed(100))
                await("Playing intent before seek") { withContext(Dispatchers.Main) { player.playWhenReady } }
                for (edge in listOf(30L, 90L, 0L, 120L)) {
                    val timeline = checkNotNull(runtime.sampleTimeshiftPresentation().timeline)
                    val selection = checkNotNull(timeline.resolveSelection(checkNotNull(timeline.select(60.seconds)),
                        delta = (edge - 60).seconds))
                    val commands = connection.seekTargets.size
                    val seek = async(Dispatchers.Main) { runtime.seekTimeshift(selection) }
                    await("Playing seek admitted") { connection.seekTargets.size > commands }
                    connection.emit(registration, SubscriptionEvent.Skipped(true, SkipOutcome.ACCEPTED, null, null))
                    val result = seek.await() as TimeshiftContentSeekResult.Completed
                    delay(250)
                    assertFalse("Playing acknowledgement is not settlement", runtime.sampleTimeshiftPresentation().timingKnown)
                    val beforeImages = images.get()
                    deliverMedia(edge)
                    await("Playing app settlement with surface output") {
                        val sample = runtime.sampleTimeshiftPresentation()
                        sample.timingKnown && sample.playbackSeek === result.seek && images.get() > beforeImages
                    }
                    withContext(Dispatchers.Main) { assertTrue("Seek preserves playing intent", player.playWhenReady) }
                }
                val retiredTimeline = checkNotNull(runtime.sampleTimeshiftPresentation().timeline)
                assertEquals("Seeking never re-tunes the channel", 1, connection.subscribeCount)
                assertNull("No playback error during paused and playing continuation", failure.get())
                val commands = connection.seekTargets.size
                val cancelledSeek = async(Dispatchers.Main) { runtime.seekTimeshift(checkNotNull(retiredTimeline.select(0.seconds))) }
                await("Cancellable seek admitted") { connection.seekTargets.size > commands }
                assertNull("No playback error before caller cancellation", failure.get())
                cancelledSeek.cancelAndJoin()
                assertTrue("Caller cancellation propagates", cancelledSeek.isCancelled)
                delay(250)
                assertNull("No playback error after caller cancellation before late acknowledgement", failure.get())
                // Cancelling the caller does not cancel a claimed coordinator command. A position
                // request queues behind it; do not withhold its acknowledgement by awaiting that request.
                val cancelledSample = async(Dispatchers.Main) { runtime.sampleTimeshiftPresentation() }
                delay(50)
                assertFalse("Pending seek cannot publish a position sample", cancelledSample.isCompleted)
                val cancelledFrames = frames.get()
                connection.emit(registration, SubscriptionEvent.Skipped(true, SkipOutcome.ACCEPTED, null, null))
                assertFalse("Cancelled seek has no position settlement", withTimeout(2.seconds) { cancelledSample.await() }.timingKnown)
                delay(250)
                assertFalse("Late acknowledgement cannot settle cancelled seek", runtime.sampleTimeshiftPresentation().timingKnown)
                assertEquals("Late acknowledgement is not a frame", cancelledFrames, frames.get())
                assertNull("No playback error after late acknowledgement before Stop", failure.get())
                withContext(Dispatchers.Main) { runtime.stop() }
                assertNull(runtime.activeTarget.value)
                assertFalse(runtime.sampleTimeshiftPresentation().timingKnown)
                assertEquals(TimeshiftContentSeekResult.Replaced,
                    withContext(Dispatchers.Main) { runtime.seekTimeshift(checkNotNull(retiredTimeline.select(0.seconds))) })
                assertNull("No playback error after Stop; cause type=${sourceFailure.get()?.javaClass?.simpleName}; " +
                    "knownPeriodTerminal=${sourceFailure.get()?.message == "Live subscription preparation failed"}", failure.get())
                assertEquals("Only explicit actions and bounded paused refill", expectedSpeeds, connection.speeds)
                assertEquals("Only in-memory profile reads", listOf(FakeServerProfileStoreCall.LOAD_PROFILE), profileStore.calls)
            } finally { withContext(NonCancellable) {
                withContext(Dispatchers.Main) { runtime.detach() }
                lifetime.shutdown(2.seconds)
                lifetime.join()
                manager.closeAndJoin()
                profileJob.cancelAndJoin()
                session.shutdown()
                withContext(Dispatchers.Main) { player.release() }
                scope.cancel()
                surface.release()
                handler.post { texture.release(); thread.quitSafely() }
                thread.join(5_000)
            } }
        }
    }

    private fun capturedMedia(): Pair<List<SubscriptionStream>, List<SubscriptionEvent.Packet>> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this)
            .joinToString("") { "%02x".format(it) }
        val manifest = assets.open("recorded-mux/manifest.json").use { it.readBytes() }
        check(manifest.sha256() == "d7a68fefddd38ac1c2999671c529100708e1b8c493b9228950dd2142d71e4f78")
        val channels = JSONObject(String(manifest, Charsets.UTF_8)).getJSONArray("channels")
        val records = (0 until channels.length()).map { channels.getJSONObject(it) }
            .single { it.getInt("channelOrdinal") == 16 }.getJSONArray("packets")
        val streams = listOf(
            SubscriptionStream(index = StreamIndex(1), type = SubscriptionStreamType.H264,
                language = null, compositionId = null, ancillaryId = null, width = null, height = null,
                frameDuration = null, aspectNumerator = null, aspectDenominator = null, audioType = null,
                audioVersion = null, channelCount = null, rate = null, rdsUecp = null, codecMetadata = null),
            SubscriptionStream(index = StreamIndex(2), type = SubscriptionStreamType.MPEG2_AUDIO,
                language = "ger", compositionId = null, ancillaryId = null, width = null, height = null,
                frameDuration = null, aspectNumerator = null, aspectDenominator = null, audioType = null,
                audioVersion = null, channelCount = null, rate = 3, rdsUecp = null, codecMetadata = null),
        )
        // Local fixture metadata only, never HTSP parsing. Preserve the recorded packet order.
        val packets = (0 until records.length()).map { index ->
            val record = records.getJSONObject(index)
            val file = record.getString("file")
            check(file.matches(Regex("channel-016-stream-0[12]-packet-0(0[1-9]|1[0-6])\\.bin")))
            val bytes = assets.open("recorded-mux/$file").use { it.readBytes() }
            check(bytes.size == record.getInt("size") && bytes.sha256() == record.getString("sha256"))
            SubscriptionEvent.Packet(
                frameType = when (record.getInt("frameType")) { 73 -> MuxFrameType.I; 80 -> MuxFrameType.P; 66 -> MuxFrameType.B; else -> MuxFrameType.UNKNOWN },
                streamIndex = StreamIndex(record.getLong("streamOrdinal")),
                decodingTimeUs = record.getLong("decodingTimeUs"),
                presentationTimeUs = record.getLong("presentationTimeUs"),
                durationUs = record.getLong("durationUs"), payload = SubscriptionBinaryFixture(bytes),
            )
        }
        // Both tracks must span the production startup/rebuffer thresholds. Repeat complete
        // captured GOP/audio cycles, preserving their internal PTS/DTS relationships. These
        // repeated images test delivery/lifecycle, not whether the selected picture is correct.
        val continued = packets.groupBy { it.streamIndex }.values.flatMap { track ->
            val cycleUs = track.maxOf { checkNotNull(it.presentationTimeUs) + checkNotNull(it.durationUs) } -
                track.minOf { checkNotNull(it.presentationTimeUs) }
            check(cycleUs > 0)
            val cycles = (4_000_000L + cycleUs - 1) / cycleUs
            (0L until cycles).flatMap { cycle -> track.map { packet ->
                SubscriptionEvent.Packet(packet.frameType, packet.streamIndex,
                    checkNotNull(packet.decodingTimeUs) + cycle * cycleUs,
                    checkNotNull(packet.presentationTimeUs) + cycle * cycleUs, packet.durationUs, packet.payload)
            } }
        }
        return streams to continued
    }
}
