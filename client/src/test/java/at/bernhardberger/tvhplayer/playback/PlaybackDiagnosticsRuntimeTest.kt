@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackRuntimeTest.Companion.exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaybackDiagnosticsRuntimeTest {
    @Test fun anOutgoingOwnerCannotDisableAnIncomingOwnersDiagnostics() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            forcedVideoFormat = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264)
                .setWidth(1920).setHeight(1080).build()
            live(timeshift = false)
            val outgoing = Any()
            val incoming = Any()
            runtime.setDiagnosticsClaim(outgoing, true)
            runtime.setDiagnosticsClaim(incoming, true)
            runtime.setDiagnosticsClaim(incoming, true)
            runtime.setDiagnosticsClaim(outgoing, false)
            playerReady()
            assertEquals("the incoming player still owns sampling", 1920, runtime.diagnostics.value.video?.width)
            runtime.setDiagnosticsClaim(outgoing, false)
            assertEquals("withdrawing an absent owner does not affect another", 1920, runtime.diagnostics.value.video?.width)
            runtime.setDiagnosticsClaim(incoming, false)
            assertNull(runtime.diagnostics.value.video)
        }

    @Test fun playerCallbacksRespectDiagnosticsOptInAndOptOut() =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            forcedVideoFormat = Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H264)
                .setWidth(1920)
                .setHeight(1080)
                .build()
            live(timeshift = false)
            // Read immediately after the player callback, before the periodic publisher can
            // hide an unwanted full diagnostics snapshot behind its disabled-state snapshot.
            playerReady()
            assertNull(runtime.diagnostics.value.video)
            assertNull(runtime.diagnostics.value.live)
            assertEquals(runtime.state.value, runtime.diagnostics.value.state)

            runtime.setDiagnosticsClaim(this, true)
            playerReady()
            assertEquals(1920, runtime.diagnostics.value.video?.width)

            runtime.setDiagnosticsClaim(this, false)
            playerReady()
            assertNull(runtime.diagnostics.value.video)
            assertNull(runtime.diagnostics.value.live)
            assertEquals(runtime.state.value, runtime.diagnostics.value.state)
        }
}
