package at.bernhardberger.tvhplayer.core

enum class ApplianceEntryDestination { LIVE, GUIDE, RECORDINGS }

enum class ApplianceGuideEntryAction { WAIT, DROP, OPEN_GUIDE }

object ApplianceEntryPolicy {
    const val ACTION_APPLIANCE_ENTRY = "at.bernhardberger.tvhplayer.action.APPLIANCE_ENTRY"
    const val EXTRA_APPLIANCE_DESTINATION = "at.bernhardberger.tvhplayer.extra.APPLIANCE_DESTINATION"
    const val DESTINATION_LIVE = "live"
    const val DESTINATION_GUIDE = "guide"
    const val DESTINATION_RECORDINGS = "recordings"
    const val ENTRY_ALIAS_CLASS_NAME = "at.bernhardberger.tvhplayer.ui.ApplianceEntryActivity"

    fun acceptsEntryIntent(
        action: String?,
        componentPackage: String?,
        componentClassName: String?,
        playerPackage: String,
    ): Boolean = action == ACTION_APPLIANCE_ENTRY &&
        componentPackage == playerPackage && componentClassName == ENTRY_ALIAS_CLASS_NAME

    fun shouldCreateLaunchRequest(isPlayerVisible: Boolean): Boolean = !isPlayerVisible

    fun destination(value: String?): ApplianceEntryDestination =
        when (value) {
            DESTINATION_GUIDE -> ApplianceEntryDestination.GUIDE
            DESTINATION_RECORDINGS -> ApplianceEntryDestination.RECORDINGS
            else -> ApplianceEntryDestination.LIVE
        }

    fun guideEntryAction(
        connectionState: ConnectionUiState,
        readiness: CurrentChannelReadiness,
        warm: Boolean,
        connectionSettingsVisible: Boolean,
    ): ApplianceGuideEntryAction = when {
        connectionSettingsVisible || connectionState == ConnectionUiState.NeedsConfiguration ||
            connectionState == ConnectionUiState.CredentialUnavailable || connectionState is ConnectionUiState.Error ->
            ApplianceGuideEntryAction.DROP
        warm || connectionState == ConnectionUiState.Ready && readiness is CurrentChannelReadiness.Ready ->
            ApplianceGuideEntryAction.OPEN_GUIDE
        else -> ApplianceGuideEntryAction.WAIT
    }
}
