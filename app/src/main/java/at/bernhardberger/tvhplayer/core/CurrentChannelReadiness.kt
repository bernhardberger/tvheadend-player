package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.core.Channel

sealed interface CurrentChannelReadiness {
    data object Waiting : CurrentChannelReadiness
    data class Ready(val channels: List<Channel>) : CurrentChannelReadiness
}

internal fun deriveCurrentChannelReadiness(
    connected: Boolean,
    metadataReady: Boolean,
    channels: List<Channel>,
): CurrentChannelReadiness {
    if (!connected || !metadataReady) {
        return CurrentChannelReadiness.Waiting
    }

    return CurrentChannelReadiness.Ready(channels.toList())
}
