package at.bernhardberger.tvhplayer.core

/** A local Back owner consumes the whole press and acts only on its uncanceled release. */
class BackKeyCycle {
    private var tracking = false

    fun reset() { tracking = false }

    fun handle(down: Boolean, repeat: Int, canceled: Boolean, enabled: Boolean = true, onBack: () -> Unit): Boolean {
        if (!enabled) {
            reset()
            return false
        }
        if (down) {
            if (repeat == 0) tracking = true
        } else {
            val act = tracking && !canceled
            reset()
            if (act) onBack()
        }
        return true
    }
}
