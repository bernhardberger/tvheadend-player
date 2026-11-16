package at.bernhardberger.tvhplayer.ui.screens.guide

import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.core.programmeArtwork

/** Trial Magazine artwork rule; the SDK event and the shared selector own image availability. */
internal fun magazineShowsPicture(event: EpgEvent): Boolean =
    event.stop.epochSeconds - event.start.epochSeconds >= 3600L && programmeArtwork(event) != null
