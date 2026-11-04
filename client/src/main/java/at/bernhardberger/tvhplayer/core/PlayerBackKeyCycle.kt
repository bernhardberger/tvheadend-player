package at.bernhardberger.tvhplayer.core

enum class PlayerBackKeyResult { NONE, BACK, HOLD }

/** Separates a player-owned hardware Back release from the platform's hold signal. */
class PlayerBackKeyCycle {
    private var tracking = false
    private var held = false

    fun onKey(
        isDown: Boolean,
        repeatCount: Int,
        isLongPress: Boolean,
        canceled: Boolean,
        holdAllowed: Boolean,
    ): PlayerBackKeyResult {
        if (isDown) {
            if (repeatCount == 0 && !isLongPress) {
                tracking = true
                held = false
            } else if (tracking && !held && holdAllowed) {
                held = true
                return PlayerBackKeyResult.HOLD
            }
            return PlayerBackKeyResult.NONE
        }
        val back = tracking && !held && !canceled
        tracking = false
        held = false
        return if (back) PlayerBackKeyResult.BACK else PlayerBackKeyResult.NONE
    }
}
