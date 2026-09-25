package at.bernhardberger.tvhplayer.ui.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
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
}
