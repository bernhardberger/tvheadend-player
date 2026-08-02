package at.bernhardberger.tvhplayer.sdk.consumer

import at.bernhardberger.tvhplayer.htsp.ChannelEpgRuntime
import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrRuntime
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import at.bernhardberger.tvhplayer.htsp.TvheadendClient
import kotlinx.coroutines.flow.StateFlow

/**
 * Compile-only proof that a frontend can consume representative HTSP SDK APIs
 * without declaring the SDK's domain or coroutine implementation dependencies.
 */
class HtspPublicApiContract(
    client: TvheadendClient,
    channels: ChannelEpgRuntime,
    dvr: DvrRuntime,
) {
    val connection = client.connectionState
    val frontendState = client.frontendState
    val channelItems: StateFlow<List<ChannelUi>> = channels.channelsUi
    val channelEpg: StateFlow<List<EpgEventEntry>> = channels.epgForChannel(channelId = 1)
    val recordings: StateFlow<List<DvrEntry>> = dvr.entries
}
