@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.acceptance

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvheadend.sdk.android.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.android.TvheadendServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvhplayer.core.StreamProfileDiscovery
import at.bernhardberger.tvhplayer.core.resolvePiconModel
import at.bernhardberger.tvhplayer.data.TvheadendDataRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackCommandResult
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class DeviceAcceptanceTest {
    @get:Rule
    val failureCodeReporter = AcceptanceFailureCodeReporter()

    private val koin get() = GlobalContext.get()
    private val runtime get() = koin.get<TvheadendDataRuntime>()
    private val playback get() = koin.get<AppPlaybackRuntime>()
    private val profileStore get() = koin.get<TvheadendServerProfileStore>()
    private val imageLoader get() = koin.get<ImageLoader>()
    private val streamProfileDiscovery get() = koin.get<StreamProfileDiscovery>()
    private var playbackSurface: AcceptancePlaybackSurface? = null

    @After
    fun cleanUp() = runTest(timeout = 30.seconds) {
        try {
            onMain { playback.stop() }
        } finally {
            try {
                playbackSurface?.let { attached ->
                    onMain {
                        playback.player.clearVideoSurface(attached.surface)
                        attached.close()
                    }
                }
            } finally {
                onMain { runtime.session.disconnect() }
            }
        }
    }

    @Test
    fun readMetadataProfilesAndArtwork() = runTest(timeout = 120.seconds) {
        connectReady()
        await(30.seconds) { runtime.metadataReady.first { it } }
        val channels = runtime.channels.value
        assertTrue("ACCEPTANCE_CHANNEL_METADATA_EMPTY", channels.isNotEmpty())

        val profiles = streamProfileDiscovery.discover()
        assertTrue("ACCEPTANCE_PROFILES_UNAVAILABLE", profiles is StreamProfilesResult.Available)
        assertTrue(
            "ACCEPTANCE_PROFILES_EMPTY",
            (profiles as StreamProfilesResult.Available).profiles.isNotEmpty(),
        )

        val artwork = channels.firstNotNullOfOrNull { channel ->
            resolvePiconModel("acceptance", channel.icon)
        }
        assertNotNull("ACCEPTANCE_ARTWORK_SELECTOR_MISSING", artwork)
        val request = ImageRequest.Builder(InstrumentationRegistry.getInstrumentation().targetContext)
            .data(artwork)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
        assertTrue(
            "ACCEPTANCE_ARTWORK_READ_FAILED",
            imageLoader.execute(request) is SuccessResult,
        )
    }

    @Test
    fun progressiveLivePlayback() = runTest(timeout = 120.seconds) {
        verifyLivePlayback(channelId("progressiveChannelId"))
    }

    @Test
    fun interlacedLivePlayback() = runTest(timeout = 120.seconds) {
        verifyLivePlayback(channelId("interlacedChannelId"))
    }

    @Test
    fun channelReplacement() = runTest(timeout = 120.seconds) {
        connectReady()
        val first = channelId("progressiveChannelId")
        val second = channelId("interlacedChannelId")
        requireKnownChannel(first)
        requireKnownChannel(second)
        submitLive(first, "ACCEPTANCE_INITIAL_TUNE_REJECTED")
        awaitPlaying(first)
        submitLive(second, "ACCEPTANCE_REPLACEMENT_REJECTED")
        awaitPlaying(second)
    }

    @Test
    fun timeshiftControls() = runTest(timeout = 120.seconds) {
        val channel = channelId("progressiveChannelId")
        verifyLivePlayback(channel)
        await(30.seconds) { playback.timeshiftState.first { it.available } }
        assertEquals(
            "ACCEPTANCE_TIMESHIFT_PAUSE_REJECTED",
            AppPlaybackCommandResult.SUBMITTED,
            onMain { playback.pauseTimeshift() },
        )
        await(15.seconds) { playback.timeshiftState.first { it.paused } }
        val beforeSeek = playback.timeshiftState.value
        val seek = onMain { playback.seekTimeshift(-10_000L) }
        assertTrue(
            "ACCEPTANCE_TIMESHIFT_SEEK_REJECTED",
            seek is at.bernhardberger.tvhplayer.playback.AppTimeshiftSeekResult.Applied &&
                seek.deltaMs != 0L && seek.targetMs < beforeSeek.positionMs,
        )
        val shifted = await(15.seconds) {
            playback.timeshiftState.first { state ->
                state.positionMs <= beforeSeek.positionMs - 1_000L
            }
        }
        assertEquals(
            "ACCEPTANCE_TIMESHIFT_RESUME_REJECTED",
            AppPlaybackCommandResult.SUBMITTED,
            onMain { playback.resumeTimeshift() },
        )
        await(15.seconds) { playback.timeshiftState.first { state -> !state.paused } }
        val goLive = onMain { playback.goLive() }
        assertTrue(
            "ACCEPTANCE_RETURN_LIVE_REJECTED",
            goLive !is at.bernhardberger.tvhplayer.playback.AppTimeshiftSeekResult.Unavailable,
        )
        await(15.seconds) {
            playback.timeshiftState.first { state ->
                state.positionMs >= shifted.positionMs && state.liveEdgeMs - state.positionMs <= 1_000L
            }
        }
    }

    @Test
    fun reconnectAndTeardown() = runTest(timeout = 120.seconds) {
        connectReady()
        onMain { runtime.session.disconnect() }
        await(15.seconds) { runtime.session.state.first { it is SessionState.Disconnected } }
        connectReady()
        val channel = channelId("progressiveChannelId")
        requireKnownChannel(channel)
        submitLive(channel, "ACCEPTANCE_TEARDOWN_TUNE_REJECTED")
        awaitPlaying(channel)
        val stopResult = onMain { playback.stop() }
        assertEquals(
            "ACCEPTANCE_ACTIVE_TEARDOWN_REJECTED",
            AppPlaybackCommandResult.STOPPED,
            stopResult,
        )
        await(15.seconds) { playback.state.first { it is AppPlaybackState.Idle } }
        assertEquals("ACCEPTANCE_TEARDOWN_TARGET_RETAINED", null, playback.submittedTarget.value)
        assertEquals("ACCEPTANCE_TEARDOWN_LIVE_RETAINED", null, playback.activeLiveServiceId.value)
        assertEquals("ACCEPTANCE_TEARDOWN_PLAYING_RETAINED", null, playback.playingLiveServiceId.value)
        assertTrue("ACCEPTANCE_TEARDOWN_PLAYER_ACTIVE", !onMain { playback.player.isPlaying })
        onMain { runtime.session.disconnect() }
        await(15.seconds) { runtime.session.state.first { it is SessionState.Disconnected } }
    }

    private suspend fun verifyLivePlayback(channel: Int) {
        connectReady()
        requireKnownChannel(channel)
        onMain { playback.setDiagnosticsEnabled(true) }
        submitLive(channel, "ACCEPTANCE_LIVE_TUNE_REJECTED")
        awaitPlaying(channel)
        await(30.seconds) {
            playback.diagnostics.first { diagnostics ->
                diagnostics.isPlaying && diagnostics.video != null && diagnostics.audio != null
            }
        }
        withTimeout(30.seconds) {
            while (onMain {
                    playback.player.videoDecoderCounters?.renderedOutputBufferCount ?: 0
                } <= 0
            ) {
                delay(100)
            }
        }
    }

    private suspend fun connectReady() {
        val stored = profileStore.loadProfile()
        assertTrue("ACCEPTANCE_PROFILE_MISSING", stored is ServerProfileReadResult.Available)
        onMain { runtime.session.connect((stored as ServerProfileReadResult.Available).profile) }
        val terminal = await(30.seconds) {
            runtime.session.state.first { it is SessionState.Ready || it is SessionState.Unavailable }
        }
        assertTrue("ACCEPTANCE_CONNECTION_FAILED", terminal is SessionState.Ready)
    }

    private suspend fun submitLive(channel: Int, rejectionCode: String) {
        if (playbackSurface == null) {
            val attached = onMain { createAcceptancePlaybackSurface() }
            onMain { playback.player.setVideoSurface(attached.surface) }
            playbackSurface = attached
        }
        assertEquals(
            rejectionCode,
            AppPlaybackCommandResult.SUBMITTED,
            onMain { playback.playLive(channel) },
        )
        onMain { playback.play() }
    }

    private suspend fun awaitPlaying(channel: Int) {
        val state = await(45.seconds) {
            playback.state.first { it is AppPlaybackState.Playing || it is AppPlaybackState.Failed }
        }
        assertTrue("ACCEPTANCE_PLAYBACK_FAILED", state is AppPlaybackState.Playing)
        assertEquals("ACCEPTANCE_ACTIVE_CHANNEL_MISMATCH", channel, playback.activeLiveServiceId.value)
    }

    private fun requireKnownChannel(channel: Int) {
        assertTrue(
            "ACCEPTANCE_FIXTURE_CHANNEL_MISSING",
            runtime.channels.value.any { it.channelId == channel },
        )
    }

    private fun channelId(name: String): Int {
        val value = InstrumentationRegistry.getArguments().getString(name)?.toIntOrNull()
        assertTrue("ACCEPTANCE_CHANNEL_ARGUMENT_INVALID", value != null && value > 0)
        return checkNotNull(value)
    }

    private suspend fun <T> onMain(block: suspend () -> T): T =
        withContext(Dispatchers.Main.immediate) { block() }

    private suspend fun <T> await(timeout: Duration, block: suspend () -> T): T =
        withContext(Dispatchers.Default) { withTimeout(timeout) { block() } }
}

class AcceptanceFailureCodeReporter : TestWatcher() {
    override fun failed(failure: Throwable, description: Description) {
        val code = generateSequence(failure) { it.cause }
            .mapNotNull { cause -> FAILURE_CODE.find(cause.message.orEmpty())?.value }
            .firstOrNull()
            ?: "UNCLASSIFIED"
        InstrumentationRegistry.getInstrumentation().sendStatus(
            2,
            Bundle().apply { putString("acceptanceFailureCode", code) },
        )
    }

    private companion object {
        val FAILURE_CODE = Regex("""(?<![A-Z0-9_])ACCEPTANCE_[A-Z0-9_]{1,96}(?![A-Z0-9_])""")
    }
}
