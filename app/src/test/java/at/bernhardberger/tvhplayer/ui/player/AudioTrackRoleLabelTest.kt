package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AudioTrackRoleLabelTest {
    @Test
    fun describedAndClearDialogueTracksAreNamedAfterTheirRole() {
        val tracks = Tracks(
            listOf(
                audioGroup("main", C.ROLE_FLAG_MAIN, selected = true),
                audioGroup("described", C.ROLE_FLAG_DESCRIBES_VIDEO),
                audioGroup("clear", C.ROLE_FLAG_ENHANCED_DIALOG_INTELLIGIBILITY),
            ),
        )

        val collected = collectTracks(
            tracks = tracks,
            trackType = C.TRACK_TYPE_AUDIO,
            audioDescriptionLabel = "Audiodeskription",
            clearDialogueLabel = "Klare Sprache",
        )
        val labels = collected.map { it.label }

        assertEquals(3, labels.size)
        assertFalse(labels[0].contains(" · Audiodeskription"))
        assertFalse(labels[0].contains(" · Klare Sprache"))
        assertTrue(labels[1].endsWith(" · Audiodeskription"))
        assertTrue(labels[2].endsWith(" · Klare Sprache"))
        val german = collected[0].headline
        assertEquals(listOf(german, "Audiodeskription", "Klare Sprache"), collected.map { it.headline })
        assertEquals(listOf(null, german, german), collected.map { it.overline })
        assertEquals(List(3) { "Stereo · MPEG-1 Layer II" }, collected.map { it.secondaryLabel })
    }

    @Test
    fun accessibilityRolesWinOverCommentaryAndAlternate() {
        val tracks = Tracks(
            listOf(
                audioGroup("described", C.ROLE_FLAG_DESCRIBES_VIDEO or C.ROLE_FLAG_COMMENTARY),
                audioGroup("clear", C.ROLE_FLAG_ENHANCED_DIALOG_INTELLIGIBILITY or C.ROLE_FLAG_ALTERNATE),
            ),
        )

        val labels = collectTracks(
            tracks = tracks,
            trackType = C.TRACK_TYPE_AUDIO,
            audioDescriptionLabel = "Audiodeskription",
            clearDialogueLabel = "Klare Sprache",
        ).map { it.label }

        assertTrue(labels[0].endsWith(" · Audiodeskription"))
        assertTrue(labels[1].endsWith(" · Klare Sprache"))
    }

    private fun audioGroup(id: String, roleFlags: Int, selected: Boolean = false): Tracks.Group =
        Tracks.Group(
            TrackGroup(
                id,
                Format.Builder()
                    .setId(id)
                    .setSampleMimeType(MimeTypes.AUDIO_MPEG_L2)
                    .setLanguage("de")
                    .setChannelCount(2)
                    .setRoleFlags(roleFlags)
                    .build(),
            ),
            false,
            intArrayOf(C.FORMAT_HANDLED),
            booleanArrayOf(selected),
        )
}
