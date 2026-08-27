package at.bernhardberger.tvhplayer.viewmodels

import at.bernhardberger.tvhplayer.settings.ServerConnectionConfiguration

internal fun connectionRequiresReplacement(
    previous: ServerConnectionConfiguration?,
    candidate: ServerConnectionConfiguration,
): Boolean = previous != candidate
