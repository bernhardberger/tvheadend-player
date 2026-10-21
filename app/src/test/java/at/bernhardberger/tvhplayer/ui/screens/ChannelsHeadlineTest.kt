package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

class ChannelsHeadlineTest {
    @Test fun synopsisSkipsRepeatedSubtitleAndNormalizesSuppliedDescription() {
        assertEquals("A real synopsis\non two lines", channelsProgrammeSynopsis(event(
            subtitle = "Episode one", summary = " Episode one ", description = "A real synopsis\\non two lines",
        )))
        assertNull(channelsProgrammeSynopsis(event(subtitle = "Episode one", summary = "Episode one", description = " ")))
        assertNull(channelsProgrammeSynopsis(event(summary = "Across the Blue Planet", description = null)))
    }

    private fun event(
        title: String = "Across the Blue Planet",
        subtitle: String? = "Worlds of water",
        summary: String?,
        description: String?,
    ): EpgEvent = EpgEvent.create(
        EventId(1), ChannelId(1), Instant.fromEpochSeconds(0), Instant.fromEpochSeconds(3600),
        title = title, subtitle = subtitle, summary = summary, description = description,
    )
}
