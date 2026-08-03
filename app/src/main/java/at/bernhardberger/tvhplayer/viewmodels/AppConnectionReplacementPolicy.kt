package at.bernhardberger.tvhplayer.viewmodels

import at.bernhardberger.tvheadend.client.TvheadendConnection

internal fun connectionRequiresReplacement(
    previous: TvheadendConnection?,
    candidate: TvheadendConnection,
): Boolean = previous == null ||
    previous.host != candidate.host ||
    previous.port != candidate.port ||
    previous.username != candidate.username ||
    previous.password != candidate.password
