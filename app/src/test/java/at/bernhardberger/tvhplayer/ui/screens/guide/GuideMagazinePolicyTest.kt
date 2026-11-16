package at.bernhardberger.tvhplayer.ui.screens.guide

import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideMagazinePolicyTest {
    @Test fun picturesRequireArtworkAndAtLeastOneHour() {
        for (seconds in listOf(0L, 3599L, 3600L, 7200L)) {
            for (image in listOf(null, ArtworkId(1))) {
                val event = EpgEvent.create(
                    id = EventId(1), channelId = ChannelId(1),
                    start = Instant.fromEpochSeconds(0),
                    stop = Instant.fromEpochSeconds(seconds), image = image,
                )
                assertEquals(
                    "duration=$seconds, image=$image",
                    image != null && seconds >= 3600L,
                    magazineShowsPicture(event),
                )
            }
        }
    }
}
