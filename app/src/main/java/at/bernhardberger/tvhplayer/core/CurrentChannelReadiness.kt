package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.client.ConnectionState
import at.bernhardberger.tvheadend.core.Channel

sealed interface CurrentChannelReadiness {
    data object Waiting : CurrentChannelReadiness
    data class Ready(val channels: List<Channel>) : CurrentChannelReadiness
}

internal fun deriveCurrentChannelReadiness(
    connectionState: ConnectionState,
    metadataReady: Boolean,
    channels: List<Channel>,
): CurrentChannelReadiness {
    if (connectionState !is ConnectionState.Connected || !metadataReady) {
        return CurrentChannelReadiness.Waiting
    }

    return CurrentChannelReadiness.Ready(channels.toList())
}
