package at.bernhardberger.tvhplayer.core

object ApplianceEntryPolicy {
    const val ACTION_APPLIANCE_ENTRY = "at.bernhardberger.tvhplayer.action.APPLIANCE_ENTRY"
    const val ENTRY_ALIAS_CLASS_NAME = "at.bernhardberger.tvhplayer.ui.ApplianceEntryActivity"

    fun acceptsEntryIntent(
        action: String?,
        componentPackage: String?,
        componentClassName: String?,
        playerPackage: String,
    ): Boolean = action == ACTION_APPLIANCE_ENTRY &&
        componentPackage == playerPackage && componentClassName == ENTRY_ALIAS_CLASS_NAME

    fun shouldCreateLaunchRequest(isPlayerVisible: Boolean): Boolean = !isPlayerVisible
}
