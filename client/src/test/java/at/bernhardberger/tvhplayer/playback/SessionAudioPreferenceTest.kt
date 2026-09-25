package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.*
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.settings.AudioTrackChoice
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionAudioPreferenceTest {
    private val remembered = AudioTrackChoice("de", MimeTypes.AUDIO_AC3, 0, 6, 48000)

    @Test fun uniqueExactWinsOverFallback() {
        val f = Fixture(remembered.copy(channelCount = 2), remembered)
        f.activate()
        assertEquals(1, f.selectedIndex())
    }

    @Test fun layoutAndRateChangesRestoreUniqueSemanticFallbackWithoutEnablingAudio() {
        val f = Fixture(remembered.copy(channelCount = 2, sampleRate = 44100))
        f.parameters = f.parameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
        f.activate()
        assertEquals(0, f.selectedIndex())
        assertTrue(C.TRACK_TYPE_AUDIO in f.parameters.disabledTrackTypes)
    }

    @Test fun ambiguousExactAndFallbackNeverChoose() {
        for (choices in listOf(arrayOf(remembered, remembered), arrayOf(remembered.copy(channelCount = 2), remembered.copy(sampleRate = 44100)))) {
            val f = Fixture(*choices)
            f.activate()
            assertTrue(f.parameters.overrides.isEmpty())
        }
    }

    @Test fun differentRoleLanguageOrMimeDoesNotRestore() {
        val f = Fixture(remembered.copy(roleFlags = C.ROLE_FLAG_DESCRIBES_VIDEO), remembered.copy(language = "en"), remembered.copy(mimeType = MimeTypes.AUDIO_AAC))
        f.activate()
        assertTrue(f.parameters.overrides.isEmpty())
    }

    @Test fun automaticForgetsChannelAcrossRetuneButPreservesTextAndDisabledFlags() {
        val f = Fixture(remembered)
        f.activate()
        val text = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build())
        f.parameters = f.parameters.buildUpon().addOverride(TrackSelectionOverride(text, listOf(0)))
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
        assertEquals(ChannelId(1), f.owner.useAutomatic(f.player))
        assertNull(f.owner.rememberExplicitChoice(f.player))
        f.owner.onMediaItemTransition(f.player)
        f.owner.activate(ChannelId(1), f.player)
        assertEquals(listOf(text), f.parameters.overrides.keys.toList())
        assertTrue(C.TRACK_TYPE_AUDIO in f.parameters.disabledTrackTypes)
    }

    @Test fun recordingAutomaticOnlyClearsOverrideAndKeepsRememberedLiveChoice() {
        val f = Fixture(remembered)
        f.activate()
        f.owner.onMediaItemTransition(f.player)
        f.parameters = f.parameters.buildUpon().addOverride(TrackSelectionOverride(f.tracks.groups[0].mediaTrackGroup, listOf(0))).build()
        assertNull(f.owner.useAutomatic(f.player))
        assertTrue(f.parameters.overrides.isEmpty())
        f.owner.activate(ChannelId(1), f.player)
        assertEquals(0, f.selectedIndex())
    }

    private inner class Fixture(vararg choices: AudioTrackChoice) {
        val owner = SessionAudioSelection()
        var parameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
        val tracks = Tracks(choices.mapIndexed { index, choice -> Tracks.Group(
            TrackGroup(index.toString(), Format.Builder().setLanguage(choice.language).setSampleMimeType(choice.mimeType)
                .setRoleFlags(choice.roleFlags).setChannelCount(choice.channelCount).setSampleRate(choice.sampleRate).build()),
            false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false),
        ) })
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getTrackSelectionParameters" -> parameters
                "getCurrentTracks" -> tracks
                "setTrackSelectionParameters" -> { parameters = args!![0] as TrackSelectionParameters; null }
                else -> error("Unexpected Player call: ${method.name}")
            }
        } as Player
        init { owner.useProfile(Any(), player); owner.load(ChannelId(1), remembered) }
        fun activate() = owner.activate(ChannelId(1), player)
        fun selectedIndex() = parameters.overrides.values.single().mediaTrackGroup.id.toInt()
    }
}
