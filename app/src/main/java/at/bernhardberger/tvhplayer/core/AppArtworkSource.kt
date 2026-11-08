package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.android.TvheadendArtwork
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi

/**
 * Coil model for one TVHeadend image-cache entry, captured with the session it belongs to.
 * Without a current session it can only be served from [saved], the SDK's model of the saved copy
 * in the profile of the observation that named the ID; it never yields another profile's bytes.
 * [savedKey] is that copy's opaque cache key, which also names the logo's stored colour; it is
 * never logged.
 *
 * The app image loader maps it to the SDK's authenticated `TvheadendArtwork`; composables do not
 * hold the SDK session themselves.
 */
data class AppArtworkSource(
    val currentSession: CurrentSessionObservation?,
    val id: ArtworkId,
    val saved: TvheadendArtwork? = null,
    val savedKey: String? = null,
) {
    override fun toString(): String = "AppArtworkSource(currentSession=$currentSession, id=$id, saved=$saved)"
}

/** Shared logo model resolution for Compose and non-UI consumers. */
@OptIn(ExperimentalTvheadendApi::class)
internal fun channelLogoModel(
    icon: ChannelIcon,
    current: CurrentSessionObservation?,
    tvheadend: TvheadendSession?,
    observation: SessionObservation?,
): Any = when (icon) {
    is ChannelIcon.RemoteUrl -> icon.url
    is ChannelIcon.ImageCache -> {
        val id = icon.artworkId
        if (current != null) AppArtworkSource(current, id)
        else if (tvheadend == null || observation == null) AppArtworkSource(null, id)
        else AppArtworkSource(null, id, TvheadendArtwork.cached(tvheadend, observation, id),
            tvheadend.artwork.cachedArtwork(observation, id)?.cacheKey)
    }
}
