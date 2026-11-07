package at.bernhardberger.tvhplayer.appliance

enum class ApplianceKeyDecision {
    PASS_THROUGH,
    CONSUME,
    LAUNCH_AND_CONSUME,
}

object ApplianceEntryPolicy {
    const val ACTION_APPLIANCE_ENTRY = "at.bernhardberger.tvhplayer.action.APPLIANCE_ENTRY"
    const val EXTRA_APPLIANCE_DESTINATION = "at.bernhardberger.tvhplayer.extra.APPLIANCE_DESTINATION"
    const val DESTINATION_LIVE = "live"
    const val DESTINATION_GUIDE = "guide"
    const val ENTRY_ALIAS_CLASS_NAME = "at.bernhardberger.tvhplayer.ui.ApplianceEntryActivity"
    const val TCL_TV_KEY_CODE = 4001

    fun keyEvent(
        isApplianceEntryKey: Boolean,
        isDown: Boolean,
        repeatCount: Int,
    ): ApplianceKeyDecision = when {
        !isApplianceEntryKey -> ApplianceKeyDecision.PASS_THROUGH
        isDown && repeatCount == 0 -> ApplianceKeyDecision.LAUNCH_AND_CONSUME
        else -> ApplianceKeyDecision.CONSUME
    }
}

class ApplianceEntryLaunchGate(
    private val minimumIntervalMillis: Long,
) {
    private var lastLaunchMillis: Long? = null
    private var lastDestination: String? = null
    private var lastExplicit = false

    fun shouldLaunch(
        nowMillis: Long,
        destination: String = ApplianceEntryPolicy.DESTINATION_LIVE,
        explicit: Boolean = false,
    ): Boolean {
        val previous = lastLaunchMillis
        if (previous != null && nowMillis - previous < minimumIntervalMillis &&
            ((!explicit && lastExplicit) ||
                (destination == lastDestination && !(explicit && !lastExplicit)))) return false

        lastLaunchMillis = nowMillis
        lastDestination = destination
        lastExplicit = explicit
        return true
    }
}
