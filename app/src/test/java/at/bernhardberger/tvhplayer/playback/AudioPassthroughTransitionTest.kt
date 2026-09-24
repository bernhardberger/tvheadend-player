@file:androidx.media3.common.util.UnstableApi
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.exoplayer.Renderer
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import java.lang.reflect.Proxy
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AudioPassthroughTransitionTest {
    @Test fun `both directions reset disabled audio before changing mode and preserve other parameters`() = runTest {
        val fixture = TransitionPlayer()
        for (enabled in listOf(false, true)) {
            val change = async { fixture.output.setPassthroughEnabled(fixture.player, enabled) }
            runCurrent()
            assertEquals(!enabled, fixture.output.isPassthroughEnabled)
            fixture.next() // Track-selection invalidation disables the renderer.
            assertEquals(Renderer.STATE_DISABLED, fixture.rendererState)
            assertEquals(!enabled, fixture.output.isPassthroughEnabled)
            fixture.next() // Playback-thread message resets the disabled renderer and commits.
            runCurrent()
            fixture.drain()
            assertTrue(change.await())
            assertEquals(enabled, fixture.output.isPassthroughEnabled)
            assertFalse(C.TRACK_TYPE_AUDIO in fixture.parameters.disabledTrackTypes)
            assertEquals("de", fixture.parameters.preferredAudioLanguages.single())
            assertEquals("fr", fixture.parameters.preferredTextLanguages.single())
            assertTrue(fixture.parameters.overrides.isEmpty())
        }
        assertEquals(2, fixture.resets)
    }

    @Test fun `old-mode-only explicit audio choice waits for fresh supported capabilities`() = runTest {
        val fixture = TransitionPlayer()
        val selection = SessionAudioSelection()
        selection.useProfile(Any(), fixture.player)
        selection.activate(ChannelId(1), fixture.player)
        fixture.player.trackSelectionParameters = fixture.parameters.buildUpon().addOverride(fixture.override).build()
        assertNotNull(selection.rememberExplicitChoice(fixture.player))
        fixture.drain()
        val change = async { fixture.output.setPassthroughEnabled(fixture.player, false) }
        runCurrent()
        fixture.drain()
        runCurrent()
        assertTrue(change.await())
        // currentTracks still advertises OLD support here; it must not drive immediate restoration.
        assertTrue(fixture.tracks.groups.single().isTrackSupported(0))
        assertTrue(fixture.parameters.overrides.isEmpty())
        fixture.drain()
        fixture.tracks = fixture.audioTracks(C.FORMAT_UNSUPPORTED_TYPE)
        selection.restore(fixture.player) // Fresh onTracksChanged in PCM mode.
        assertTrue(fixture.parameters.overrides.isEmpty())
        fixture.tracks = fixture.audioTracks(C.FORMAT_HANDLED)
        selection.restore(fixture.player) // Remembered choice survives and returns when supported.
        assertEquals(fixture.override, fixture.parameters.overrides.values.single())
    }

    @Test fun `cancellation rejects a late message and removes only the temporary disable`() = runTest {
        val fixture = TransitionPlayer()
        val change = async { fixture.output.setPassthroughEnabled(fixture.player, false) }
        runCurrent()
        change.cancel()
        runCurrent()
        fixture.drain(deliverCancelled = true)
        assertTrue(change.isCancelled)
        assertTrue(fixture.output.isPassthroughEnabled)
        assertFalse(C.TRACK_TYPE_AUDIO in fixture.parameters.disabledTrackTypes)
        assertEquals(0, fixture.resets)
    }

    @Test fun `timeout leaves usable prior mode and a late message cannot commit`() = runTest {
        val fixture = TransitionPlayer()
        val change = async { fixture.output.setPassthroughEnabled(fixture.player, false) }
        runCurrent()
        advanceTimeBy(2_001)
        runCurrent()
        assertFalse(change.await())
        fixture.drain(deliverCancelled = true)
        assertTrue(fixture.output.isPassthroughEnabled)
        assertEquals(Renderer.STATE_STARTED, fixture.rendererState)
        assertFalse(C.TRACK_TYPE_AUDIO in fixture.parameters.disabledTrackTypes)
    }

    @Test fun `cancellation during decoder reset prevents a late mode commit`() = runTest {
        val fixture = TransitionPlayer()
        val change = async { fixture.output.setPassthroughEnabled(fixture.player, false) }
        fixture.onReset = { change.cancel() }
        runCurrent()
        fixture.drain()
        runCurrent()
        fixture.drain()
        assertTrue(fixture.output.isPassthroughEnabled)
        assertFalse(C.TRACK_TYPE_AUDIO in fixture.parameters.disabledTrackTypes)
    }

    @Test fun `an already disabled track stays disabled and newer subtitle choices survive`() = runTest {
        val fixture = TransitionPlayer(disabled = true)
        val change = async { fixture.output.setPassthroughEnabled(fixture.player, false) }
        runCurrent()
        fixture.player.trackSelectionParameters = fixture.parameters.buildUpon().setPreferredTextLanguage("en").build()
        fixture.drain()
        runCurrent()
        assertTrue(change.await())
        assertTrue(C.TRACK_TYPE_AUDIO in fixture.parameters.disabledTrackTypes)
        assertEquals("en", fixture.parameters.preferredTextLanguages.single())
    }

    @Test fun `audio re-enabled by another owner is never reset while running`() = runTest {
        val fixture = TransitionPlayer()
        val change = async { fixture.output.setPassthroughEnabled(fixture.player, false) }
        runCurrent()
        fixture.next()
        fixture.rendererState = Renderer.STATE_STARTED
        fixture.next()
        runCurrent()
        fixture.drain()
        assertFalse(change.await())
        assertEquals(0, fixture.resets)
        assertTrue(fixture.output.isPassthroughEnabled)
    }

    @Test fun `rapid opposite requests serialize complete audio transitions`() = runTest {
        val fixture = TransitionPlayer()
        val off = async { fixture.output.setPassthroughEnabled(fixture.player, false) }
        val on = async { fixture.output.setPassthroughEnabled(fixture.player, true) }
        repeat(4) { runCurrent(); fixture.drain() }
        assertTrue(off.await())
        assertTrue(on.await())
        assertTrue(fixture.output.isPassthroughEnabled)
        assertFalse(C.TRACK_TYPE_AUDIO in fixture.parameters.disabledTrackTypes)
        assertEquals(2, fixture.resets)
    }

    @Test fun `remembered audio choice restores when the same stream arrives with a different format id`() = runTest {
        val fixture = TransitionPlayer()
        fun audio(id: String, language: String) = TrackGroup(Format.Builder().setId(id).setLanguage(language)
            .setSampleMimeType(MimeTypes.AUDIO_AC3).setChannelCount(6).setSampleRate(48_000).build())
        fun tracks(vararg groups: TrackGroup) = Tracks(groups.map {
            Tracks.Group(it, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))
        })
        val selection = SessionAudioSelection()
        selection.useProfile(Any(), fixture.player)
        selection.activate(ChannelId(1), fixture.player)
        val chosen = audio(id = "0", language = "de")
        fixture.tracks = tracks(chosen)
        fixture.player.trackSelectionParameters = fixture.parameters.buildUpon()
            .addOverride(TrackSelectionOverride(chosen, listOf(0))).build()
        assertNotNull(selection.rememberExplicitChoice(fixture.player))

        selection.onMediaItemTransition(fixture.player)
        val shifted = audio(id = "1", language = "de")
        fixture.tracks = tracks(audio(id = "0", language = "en"), shifted)
        selection.activate(ChannelId(1), fixture.player)
        val restored = fixture.parameters.overrides.values.single()
        assertSame(shifted, restored.mediaTrackGroup)
        assertEquals(listOf(0), restored.trackIndices)
    }
}

/** Models the pinned Media3 queue ordering; any source, seek or play-intent call fails this fake. */
private class TransitionPlayer(disabled: Boolean = false) {
    private val operations = ArrayDeque<() -> Unit>()
    private var deliverCancelled = false
    private val context = ApplicationProvider.getApplicationContext<Application>()
    val output = TvheadendAudioOutputProvider(context)
    val override = TrackSelectionOverride(TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).build()), listOf(0))
    fun audioTracks(support: Int) = Tracks(listOf(Tracks.Group(override.mediaTrackGroup, false,
        intArrayOf(support), booleanArrayOf(true))))
    var tracks = audioTracks(C.FORMAT_HANDLED)
    var parameters = TrackSelectionParameters.Builder(context)
        .setPreferredAudioLanguage("de").setPreferredTextLanguage("fr").addOverride(override)
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, disabled).build()
    var rendererState = if (disabled) Renderer.STATE_DISABLED else Renderer.STATE_STARTED
    var resets = 0
    var onReset: () -> Unit = {}
    private val renderer = Proxy.newProxyInstance(Renderer::class.java.classLoader, arrayOf(Renderer::class.java)) { _, method, _ ->
        when (method.name) {
            "getTrackType" -> C.TRACK_TYPE_AUDIO
            "getState" -> rendererState
            "reset" -> { check(rendererState == Renderer.STATE_DISABLED); resets++; onReset(); null }
            else -> error("Unexpected renderer call ${method.name}")
        }
    } as Renderer
    val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { _, method, args ->
        when (method.name) {
            "getApplicationLooper" -> Looper.getMainLooper()
            "getRendererCount" -> 1
            "getRenderer" -> renderer
            "getSecondaryRenderer" -> null
            "getTrackSelectionParameters" -> parameters
            "getCurrentTracks" -> tracks
            "setTrackSelectionParameters" -> {
                parameters = args!![0] as TrackSelectionParameters
                val disabledNow = C.TRACK_TYPE_AUDIO in parameters.disabledTrackTypes
                operations.add { rendererState = if (disabledNow) Renderer.STATE_DISABLED else Renderer.STATE_STARTED }
                null
            }
            "createMessage" -> PlayerMessage({ message ->
                operations.add {
                    if (!message.isCanceled || deliverCancelled) message.target.handleMessage(message.type, message.payload)
                    message.markAsProcessed(true)
                }
            }, args!![0] as PlayerMessage.Target, Timeline.EMPTY, 0, Clock.DEFAULT, Looper.getMainLooper())
            else -> error("Unexpected player call ${method.name}")
        }
    } as ExoPlayer
    fun next() { operations.removeFirst().invoke() }
    fun drain(deliverCancelled: Boolean = false) {
        this.deliverCancelled = deliverCancelled
        while (operations.isNotEmpty()) next()
    }
}
