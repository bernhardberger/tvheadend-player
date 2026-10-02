package at.bernhardberger.tvhplayer.core

/** Main-thread process opportunity, consumed even by restoration. */
internal class MainStartupProcessEntry {
    private var claimed = false
    fun claim(restored: Boolean): Boolean {
        val fresh = !claimed && !restored
        claimed = true
        return fresh
    }
}

internal enum class MainStartupBrandDecision { PENDING, ASSEMBLE, SETTLED }

/** Fresh-process admission follows the actual visible wait, not metadata or disk cache. */
internal class MainStartupEntry(fresh: Boolean) {
    var decision = if (fresh) MainStartupBrandDecision.PENDING else MainStartupBrandDecision.SETTLED
        private set
    private var readySeen = false

    /** Connection observation only cancels recovery; Ready is not first picture. */
    fun observe(connection: ConnectionUiState) {
        when (connection) {
            ConnectionUiState.Ready -> readySeen = true
            ConnectionUiState.Connecting, ConnectionUiState.SyncingChannels -> if (readySeen) cancel()
            else -> cancel()
        }
    }

    fun afterGrace() {
        if (decision != MainStartupBrandDecision.PENDING) return
        decision = MainStartupBrandDecision.ASSEMBLE
    }

    fun cancel() { decision = MainStartupBrandDecision.SETTLED }
}
