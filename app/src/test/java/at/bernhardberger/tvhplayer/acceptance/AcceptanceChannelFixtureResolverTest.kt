package at.bernhardberger.tvhplayer.acceptance

import at.bernhardberger.tvhplayer.data.Channel
import org.junit.Assert.assertEquals
import org.junit.Test

class AcceptanceChannelFixtureResolverTest {
    @Test
    fun `resolves two unique exact selectors to current channel ids`() {
        val channels = listOf(
            channel(id = 101, name = "Progressive fixture"),
            channel(id = 202, name = "Interlaced fixture"),
        )

        assertEquals(
            AcceptanceChannelFixtureResolution.Resolved(
                progressiveChannelId = 101,
                interlacedChannelId = 202,
            ),
            resolveAcceptanceChannelFixture(
                channels = channels,
                progressiveSelector = "Progressive fixture",
                interlacedSelector = "Interlaced fixture",
            ),
        )
    }

    @Test
    fun `fails closed when either selector is missing or ambiguous`() {
        val channels = listOf(
            channel(id = 101, name = "Progressive fixture"),
            channel(id = 102, name = "Progressive fixture"),
            channel(id = 202, name = "Interlaced fixture"),
        )

        assertEquals(
            AcceptanceChannelFixtureResolution.Ambiguous,
            resolveAcceptanceChannelFixture(
                channels = channels,
                progressiveSelector = "Progressive fixture",
                interlacedSelector = "Interlaced fixture",
            ),
        )
        assertEquals(
            AcceptanceChannelFixtureResolution.Missing,
            resolveAcceptanceChannelFixture(
                channels = channels,
                progressiveSelector = "Missing fixture",
                interlacedSelector = "Interlaced fixture",
            ),
        )
    }

    @Test
    fun `fails closed when selectors resolve to the same channel`() {
        val channels = listOf(channel(id = 101, name = "Shared fixture"))

        assertEquals(
            AcceptanceChannelFixtureResolution.SameChannel,
            resolveAcceptanceChannelFixture(
                channels = channels,
                progressiveSelector = "Shared fixture",
                interlacedSelector = "Shared fixture",
            ),
        )
    }

    private fun channel(id: Int, name: String) = Channel(
        channelId = id,
        name = name,
        number = null,
        icon = null,
    )
}
