package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation

/**
 * Coil model for one TVHeadend image-cache entry, captured with the session it belongs to.
 *
 * The app image loader maps it to the SDK's authenticated `TvheadendArtwork`; composables do not
 * hold the SDK session themselves.
 */
data class AppArtworkSource(
    val currentSession: CurrentSessionObservation,
    val id: ArtworkId,
)
