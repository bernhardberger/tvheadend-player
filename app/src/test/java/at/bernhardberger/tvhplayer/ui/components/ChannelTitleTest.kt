package at.bernhardberger.tvhplayer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelTitleTest {

    @Test
    fun includesChannelNumberInlineWhenAvailable() {
        assertEquals("12  ORF III", channelTitleText(number = 12, name = "ORF III"))
    }

    @Test
    fun omitsNumberSpacingWhenNumberIsUnavailable() {
        assertEquals("ORF III", channelTitleText(number = null, name = "ORF III"))
    }
}
