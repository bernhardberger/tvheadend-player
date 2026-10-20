package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelsGridCardHeightTest {
    private val cardMin = 110.dp

    @Test
    fun aMostlyShownLastRowGivesWayToOneMoreWholeRow() {
        // Three 242dp columns: 16:9 rows of 136 would show the third almost whole and cut off.
        assertEquals(129.33f, channelsGridCardHeight(242.dp, 420.dp, 16.dp, cardMin).value, .01f)
    }

    @Test
    fun aShortLastRowKeepsWidescreenCards() {
        assertEquals(112.5f, channelsGridCardHeight(200.dp, 270.dp, 16.dp, 100.dp).value, .01f)
    }

    @Test
    fun cardsNeverGetFlatterThanTwoToOne() {
        // One more row would need 112dp cards in a 242dp column.
        assertEquals(136.125f, channelsGridCardHeight(242.dp, 240.dp, 16.dp, 100.dp).value, .01f)
    }

    @Test
    fun narrowColumnsPlanRowsWithTheCardsOwnHeight() {
        // 175dp columns: 16:9 would be 98dp, but the card's text needs 110.
        assertEquals(110f, channelsGridCardHeight(175.dp, 420.dp, 16.dp, cardMin).value, .01f)
    }

    @Test
    fun aLargerFontKeepsTheTextHeightRatherThanSqueezingAnotherRow() {
        // At font scale 1.3 the text needs 138dp; three rows would need 129dp cards.
        assertEquals(138f, channelsGridCardHeight(242.dp, 420.dp, 16.dp, 138.dp).value, .01f)
    }
}
