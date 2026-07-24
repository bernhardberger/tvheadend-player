package at.bernhardberger.tvhplayer.core

enum class ApplianceKeyDecision {
    PASS_THROUGH,
    CONSUME,
    LAUNCH_AND_CONSUME,
}

object ApplianceEntryPolicy {
    const val TCL_TV_KEY_CODE = 4001

    fun isApplianceEntryKey(keyCode: Int, androidGuideKeyCode: Int): Boolean =
        keyCode == androidGuideKeyCode || keyCode == TCL_TV_KEY_CODE

    fun shouldCreateLaunchRequest(isPlayerVisible: Boolean): Boolean = !isPlayerVisible

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

    fun shouldLaunch(nowMillis: Long): Boolean {
        val previous = lastLaunchMillis
        if (previous != null && nowMillis - previous < minimumIntervalMillis) return false

        lastLaunchMillis = nowMillis
        return true
    }
}
