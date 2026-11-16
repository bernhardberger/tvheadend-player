package at.bernhardberger.tvhplayer.ui.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Player
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlaybackQuickListTest {
    @Test fun audioBackRestoresExactOverrideWithoutRestoringInterruptionMute() {
        val override = TrackSelectionOverride(TrackGroup(Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC).build()), listOf(0))
        val start = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT.buildUpon()
            .addOverride(override).setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
        val current = start.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).build()
        val restored = restoredTrackSelection(current, start, C.TRACK_TYPE_AUDIO)
        assertFalse(C.TRACK_TYPE_AUDIO in restored.disabledTrackTypes)
        assertSame(override.mediaTrackGroup, restored.overrides.values.single().mediaTrackGroup)
        assertTrue(C.TRACK_TYPE_AUDIO in restoredTrackSelection(start, current, C.TRACK_TYPE_AUDIO).disabledTrackTypes)
    }

    @Test fun subtitleBackRestoresTheOpeningOffChoice() {
        val start = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        val current = start.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        assertTrue(C.TRACK_TYPE_TEXT in restoredTrackSelection(current, start, C.TRACK_TYPE_TEXT).disabledTrackTypes)
        assertFalse(C.TRACK_TYPE_TEXT in restoredTrackSelection(start, current, C.TRACK_TYPE_TEXT).disabledTrackTypes)
    }

    @Test fun subtitleBackFromDefaultUsesTheRuntimePathForOffAndTrackPreviews() {
        val start = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
        val track = TrackSelectionOverride(TrackGroup(Format.Builder()
            .setSampleMimeType(MimeTypes.APPLICATION_DVBSUBS).build()), listOf(0))
        val previews = listOf(
            start,
            start.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build(),
            start.buildUpon().addOverride(track).build(),
        )
        previews.forEach { current ->
            var defaultCalls = 0
            val player = java.lang.reflect.Proxy.newProxyInstance(
                Player::class.java.classLoader, arrayOf(Player::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "getTrackSelectionParameters" -> current
                    else -> error("Default must use the runtime, not ${method.name}")
                }
            } as Player
            restoreQuickListStart(player, PlaybackOptionsPage.SUBTITLES, start, {},
                onDefaultSubtitles = { defaultCalls++ })
            assertEquals(if (current == start) 0 else 1, defaultCalls)
        }
    }
}
