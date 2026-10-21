package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.android.TvheadendArtwork
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation

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
