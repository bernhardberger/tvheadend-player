package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCardPolicyTest {
    // Fixed parts of the given height, with 36 dp title lines and 24 dp episode lines.
    private fun height(fixed: Float) = { title: Int, episode: Int ->
        fixed + 36f * title + (if (episode > 0) 4f + 24f * episode else 0f)
    }

    @Test fun headingThatFitsKeepsTheFullBandAndEveryLine() {
        assertEquals(PlaybackCardFit(160f, 1, 1), fitPlaybackCard(476f, 112f..160f, 1, 1, height(200f)))
    }

    @Test fun bandGivesWayBeforeAnyText() {
        val fit = fitPlaybackCard(476f, 112f..160f, 2, 2, height(200f))
        assertEquals(PlaybackCardFit(476f - 200f - 72f - 52f, 2, 2), fit)
    }

    @Test fun episodeLosesLinesBeforeTheTitleAndTheBandGrowsBack() {
        // With both episode lines the band would drop below its floor; one line is enough.
        assertEquals(PlaybackCardFit(476f - 225f - 108f - 28f, 3, 1),
            fitPlaybackCard(476f, 112f..160f, 3, 2, height(225f)))
        assertEquals(PlaybackCardFit(476f - 250f - 108f, 3, 0),
            fitPlaybackCard(476f, 112f..160f, 3, 2, height(250f)))
    }

    @Test fun titleGivesWayLastAndKeepsOneLine() {
        assertEquals(PlaybackCardFit(112f, 2, 0), fitPlaybackCard(476f, 112f..160f, 3, 2, height(292f)))
        assertEquals("What never gives way may still overflow", PlaybackCardFit(112f, 1, 0),
            fitPlaybackCard(476f, 112f..160f, 3, 2, height(400f)))
    }

    @Test fun cardWithoutBandOnlyTrimsText() {
        assertEquals(PlaybackCardFit(0f, 3, 1), fitPlaybackCard(476f, 0f..0f, 3, 2, height(340f)))
    }

    @Test fun greyArtLeavesTheCardNeutralAndColouredArtKeepsItsHue() {
        assertEquals(null, playbackCardTint(0x131313))
        val tint = playbackCardTint(detailsTintRgb(0x2F6BFF))!!
        listOf(tint.surface, tint.halo, tint.accent).forEach {
            assertEquals(rgbToHsv(0x2F6BFF).first, rgbToHsv(it).first, 8f)
        }
    }
}
