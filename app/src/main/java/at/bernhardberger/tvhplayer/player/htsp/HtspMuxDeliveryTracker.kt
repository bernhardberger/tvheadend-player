package at.bernhardberger.tvhplayer.player.htsp

/** Detects loss from the bounded global HTSP mux event buffer. */
internal class HtspMuxDeliveryTracker(initialSequence: Long) {
    private var lastSequence = initialSequence

    fun accept(sequence: Long): Boolean = synchronized(this) {
        if (sequence <= 0L) return@synchronized true
        val previous = lastSequence
        lastSequence = sequence
        sequence == previous + 1L
    }
}
