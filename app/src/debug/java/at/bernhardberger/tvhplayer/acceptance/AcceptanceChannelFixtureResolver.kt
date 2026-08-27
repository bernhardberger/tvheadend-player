package at.bernhardberger.tvhplayer.acceptance

import at.bernhardberger.tvhplayer.data.Channel

internal sealed interface AcceptanceChannelFixtureResolution {
    data class Resolved(
        val progressiveChannelId: Int,
        val interlacedChannelId: Int,
    ) : AcceptanceChannelFixtureResolution

    data object Missing : AcceptanceChannelFixtureResolution
    data object Ambiguous : AcceptanceChannelFixtureResolution
    data object SameChannel : AcceptanceChannelFixtureResolution
}

internal fun resolveAcceptanceChannelFixture(
    channels: List<Channel>,
    progressiveSelector: String,
    interlacedSelector: String,
): AcceptanceChannelFixtureResolution {
    val progressive = channels.filter { it.name == progressiveSelector }
    val interlaced = channels.filter { it.name == interlacedSelector }
    return when {
        progressive.isEmpty() || interlaced.isEmpty() -> AcceptanceChannelFixtureResolution.Missing
        progressive.size != 1 || interlaced.size != 1 -> AcceptanceChannelFixtureResolution.Ambiguous
        progressive.single().channelId == interlaced.single().channelId ->
            AcceptanceChannelFixtureResolution.SameChannel
        else -> AcceptanceChannelFixtureResolution.Resolved(
            progressiveChannelId = progressive.single().channelId,
            interlacedChannelId = interlaced.single().channelId,
        )
    }
}
