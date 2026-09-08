@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.ui.player.collectTracks
import at.bernhardberger.tvhplayer.ui.player.selectAudioTrack
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionAudioSelectionTest {
    @Test
    fun roundtripRebindsExplicitChoiceToFreshReorderedGroups() {
        val fixture = Fixture()
        fixture.tune(1)
        val original = fixture.chooseAlternate()
        fixture.tune(2)
        assertTrue(fixture.parameters.overrides.isEmpty())
        fixture.tune(1, tracks = audioTracks(reverse = true))
        val restored = fixture.parameters.overrides.values.single()
        assertNotSame(original, restored.mediaTrackGroup)
        assertSame(fixture.tracks.groups.first().mediaTrackGroup, restored.mediaTrackGroup)
        assertEquals("alternate", restored.mediaTrackGroup.getFormat(restored.trackIndices.single()).id)
    }

    @Test
    fun missingUnsupportedAndAmbiguousChoiceLeaveDefaultAudioEnabled() {
        val fixture = Fixture()
        fixture.tune(1)
        fixture.chooseAlternate()
        for (tracks in listOf(audioTracks(missing = true), audioTracks(unsupported = true), audioTracks(duplicate = true))) {
            fixture.tune(2)
            fixture.tune(1, tracks)
            assertTrue(fixture.parameters.overrides.isEmpty())
            assertFalse(C.TRACK_TYPE_AUDIO in fixture.parameters.disabledTrackTypes)
        }
        fixture.tune(2)
        fixture.tune(1)
        assertEquals("alternate", fixture.parameters.overrides.values.single().mediaTrackGroup.getFormat(0).id)
    }

    @Test
    fun profileReplacementAndDisposalDoNotLeakChannelChoice() {
        val fixture = Fixture()
        fixture.tune(1)
        fixture.chooseAlternate()
        fixture.owner.useProfile(Any(), fixture.player)
        fixture.tune(1)
        assertTrue(fixture.parameters.overrides.isEmpty())
        fixture.chooseAlternate()
        fixture.owner.clear(fixture.player)
        fixture.owner.useProfile(Any(), fixture.player)
        fixture.tune(1)
        assertTrue(fixture.parameters.overrides.isEmpty())
    }

    @Test
    fun retainsOnlyTheLast64ExplicitChannelChoices() {
        val fixture = Fixture()
        for (id in 1L..65L) {
            fixture.tune(id)
            fixture.chooseAlternate()
        }
        fixture.tune(1)
        assertTrue(fixture.parameters.overrides.isEmpty())
        fixture.tune(2)
        assertEquals("alternate", fixture.parameters.overrides.values.single().mediaTrackGroup.getFormat(0).id)
    }

    private class Fixture {
        val owner = SessionAudioSelection()
        var parameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
        var tracks = Tracks.EMPTY
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getTrackSelectionParameters" -> parameters
                "getCurrentTracks" -> tracks
                "setTrackSelectionParameters" -> {
                    parameters = args!![0] as TrackSelectionParameters
                    null
                }
                else -> error("Unexpected Player call: ${method.name}")
            }
        } as Player

        init { owner.useProfile(Any(), player) }

        fun tune(id: Long, tracks: Tracks = audioTracks()) {
            owner.onMediaItemTransition(player)
            this.tracks = tracks
            owner.activate(ChannelId(id), player)
        }

        fun chooseAlternate(): TrackGroup {
            val choice = collectTracks(tracks, C.TRACK_TYPE_AUDIO).single { it.group.getTrackFormat(0).id == "alternate" }
            selectAudioTrack(player, choice)
            owner.rememberExplicitChoice(player)
            return choice.group.mediaTrackGroup
        }
    }
}

internal fun audioTracks(
    reverse: Boolean = false,
    missing: Boolean = false,
    unsupported: Boolean = false,
    duplicate: Boolean = false,
): Tracks {
    fun group(id: String, language: String, supported: Boolean = true) = Tracks.Group(
        TrackGroup(Format.Builder().setId(id).setSampleMimeType("audio/mp4a-latm")
            .setLanguage(language).setChannelCount(2).setSampleRate(48_000).build()),
        false, intArrayOf(if (supported) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_TYPE), booleanArrayOf(false),
    )
    val groups = mutableListOf(group("main", "de"))
    if (!missing) groups += group("alternate", "en", !unsupported)
    if (duplicate) groups += group("alternate", "en")
    return Tracks(if (reverse) groups.reversed() else groups)
}
