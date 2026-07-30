package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.htsp.ConnectionState

sealed interface CurrentChannelReadiness {
    data object Waiting : CurrentChannelReadiness
    data class Ready(val channels: List<ChannelUi>) : CurrentChannelReadiness
}

internal fun deriveCurrentChannelReadiness(
    connectionState: ConnectionState,
    metadataReady: Boolean,
    channels: List<ChannelUi>,
): CurrentChannelReadiness {
    if (connectionState !is ConnectionState.Connected || !metadataReady) {
        return CurrentChannelReadiness.Waiting
    }

    return CurrentChannelReadiness.Ready(channels.toList())
}
