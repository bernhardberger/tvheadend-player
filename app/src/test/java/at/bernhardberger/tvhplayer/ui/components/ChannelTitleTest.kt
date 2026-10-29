package at.bernhardberger.tvhplayer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelTitleTest {

    @Test
    fun includesChannelNumberInlineWhenAvailable() {
        assertEquals("12 ORF III", channelTitleText(number = 12, name = "ORF III"))
    }

    @Test
    fun omitsNumberSpacingWhenNumberIsUnavailable() {
        assertEquals("ORF III", channelTitleText(number = null, name = "ORF III"))
    }

    @Test
    fun omitsNameSpacingWhenNameIsBlank() {
        assertEquals("12", channelTitleText(number = 12, name = "  "))
    }

    @Test fun visibilityOnlyChangesNumberPresentationAndKeepsUnnamedChannelsIdentifiable() {
        for (shown in listOf(true, false)) {
            assertEquals(if (shown) "12 ORF III" else "ORF III",
                channelTitleText(12, "ORF III", showNumbers = shown))
            assertEquals(if (shown) 12L else null,
                channelTitleNumber(12, "ORF III", showNumbers = shown))
            for (blank in listOf("", "  ")) {
                assertEquals("12", channelTitleText(12, blank, showNumbers = shown))
                assertEquals(12L, channelTitleNumber(12, blank, showNumbers = shown))
            }
            assertEquals("ORF III", channelTitleText(null, "ORF III", showNumbers = shown))
            assertEquals("", channelTitleText(null, "", showNumbers = shown))
        }
    }
}
