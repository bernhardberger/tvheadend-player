package at.bernhardberger.tvhplayer.ui.common

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.EpgEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class EpisodeLabelTest {
    private val resources get() = ApplicationProvider.getApplicationContext<Application>().resources
    private fun episode(season: Long? = null, number: Long? = null, part: Long? = null, onscreen: String? = null) =
        EpgEpisode(null, null, season, 7, number, 24, part, 3, onscreen)

    @Test fun compactNumbersHaveNoPaddingOrCounts() {
        assertEquals("S5 E10", episodeLabel(episode(5, 10), resources))
        assertEquals("S5", episodeLabel(episode(season = 5), resources))
        assertEquals("E10", episodeLabel(episode(number = 10), resources))
        assertEquals("S5 E10 · Part 2", episodeLabel(episode(5, 10, 2), resources))
        assertEquals("Part 2", episodeLabel(episode(part = 2), resources))
        assertEquals("Season 5, Episode 10, Part 2", episodeLabel(episode(5, 10, 2), resources, spoken = true))
    }

    @Test fun onscreenIsOnlyAFallbackAndCanBeOmittedByCaller() {
        assertEquals("Pilot", episodeLabel(episode(onscreen = "Pilot"), resources))
        assertEquals("Pilot", episodeLabel(episode(onscreen = "Pilot"), resources, spoken = true))
        assertEquals("S5 E10", episodeLabel(episode(5, 10, onscreen = "Pilot"), resources))
        assertNull(episodeLabel(episode(onscreen = "Pilot"), resources, onscreenFallback = false))
        assertNull(episodeLabel(episode(onscreen = " "), resources))
        assertNull(episodeLabel(null, resources))
    }

    @Test @Config(qualifiers = "de") fun germanKeepsCompactSEAndLocalizesSpokenAndPart() {
        assertEquals("S5 E10 · Teil 2", episodeLabel(episode(5, 10, 2), resources))
        assertEquals("Staffel 5, Folge 10, Teil 2", episodeLabel(episode(5, 10, 2), resources, spoken = true))
    }

    @Test fun menuEpisodeLineCombinesHeaderMetadataWithoutRepeatingSubtitle() {
        assertEquals("S5 E10 · The ridge", programmeEpisodeLine(episode(5, 10), "The ridge", resources))
        assertEquals("S5 E10", programmeEpisodeLine(episode(5, 10), "S5 E10", resources))
        assertEquals("The ridge", programmeEpisodeLine(null, "The ridge", resources))
        assertEquals("", programmeEpisodeLine(null, " ", resources))
    }
}
