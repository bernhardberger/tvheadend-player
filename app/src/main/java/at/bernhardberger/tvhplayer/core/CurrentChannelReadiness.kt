package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.RetainedMetadataAuthority

sealed interface CurrentChannelReadiness {
    data object Waiting : CurrentChannelReadiness
    data class Browsable(val channels: List<Channel>) : CurrentChannelReadiness
    data class Ready(val channels: List<Channel>) : CurrentChannelReadiness
}

internal fun deriveCurrentChannelReadiness(
    connected: Boolean,
    authority: RetainedMetadataAuthority,
    channels: List<Channel>,
): CurrentChannelReadiness {
    if (channels.isNotEmpty() && (
        authority == RetainedMetadataAuthority.SYNCHRONIZING_WITH_RETAINED_DATA ||
            authority == RetainedMetadataAuthority.STALE
        )
    ) {
        return CurrentChannelReadiness.Browsable(channels.toList())
    }
    return if (connected && authority == RetainedMetadataAuthority.CURRENT) {
        CurrentChannelReadiness.Ready(channels.toList())
    } else {
        CurrentChannelReadiness.Waiting
    }
}
