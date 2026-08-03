package at.bernhardberger.tvhplayer.viewmodels

internal fun profileDiscoveryFailureState(
    @Suppress("UNUSED_PARAMETER") failure: Throwable,
): ProfilesUiState = ProfilesUiState.Error
