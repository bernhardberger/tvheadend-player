package at.bernhardberger.tvhplayer.player.htsp

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal enum class LiveSubscriptionAcceptance {
    OWNED,
    RELEASE_IMMEDIATELY,
}

internal class LiveSubscriptionLifecycle {
    private var retired = false
    private var closeInProgress = false
    private var accepted = false
    private var ownershipAttemptId: Long? = null
    private var openSettlement: CountDownLatch? = null
    private var unsubscribeSettlement: CountDownLatch? = null
    private var ownershipSettled = true
    private var unsubscribeInProgress = false

    @Synchronized
    fun beginOpen(): Boolean {
        if (retired || closeInProgress || openSettlement != null) return false
        openSettlement = CountDownLatch(1)
        return true
    }

    @Synchronized
    fun beginSubscriptionRequest(attemptId: Long): Boolean {
        if (
            retired ||
            closeInProgress ||
            openSettlement == null ||
            ownershipAttemptId != null
        ) return false
        ownershipAttemptId = attemptId
        accepted = false
        ownershipSettled = false
        return true
    }

    @Synchronized
    fun ownershipAttemptId(): Long? = ownershipAttemptId

    @Synchronized
    fun hasAcceptedSubscription(attemptId: Long): Boolean =
        ownershipAttemptId == attemptId && accepted && !unsubscribeInProgress

    @Synchronized
    fun subscriptionAccepted(attemptId: Long): LiveSubscriptionAcceptance {
        check(ownershipAttemptId == attemptId) { "Subscription attempt changed before acceptance" }
        check(!unsubscribeInProgress) { "Subscription unsubscribe is already in progress" }
        accepted = true
        ownershipSettled = false
        return if (retired || closeInProgress) {
            LiveSubscriptionAcceptance.RELEASE_IMMEDIATELY
        } else {
            LiveSubscriptionAcceptance.OWNED
        }
    }

    @Synchronized
    fun commitIfOwned(attemptId: Long, block: () -> Unit): Boolean {
        if (
            retired ||
            closeInProgress ||
            ownershipAttemptId != attemptId ||
            !accepted ||
            unsubscribeInProgress
        ) return false
        block()
        return true
    }

    @Synchronized
    fun terminalFailureRequiresUnsubscribe(): Boolean {
        closeInProgress = true
        return claimUnsubscribe()
    }

    @Synchronized
    fun retireRequiresUnsubscribe(): Boolean {
        retired = true
        return claimUnsubscribe()
    }

    @Synchronized
    fun pendingUnsubscribeRequiresUnsubscribe(): Boolean {
        check(retired || closeInProgress)
        return claimUnsubscribe(allowUnacceptedOpen = true)
    }

    @Synchronized
    fun failedOpenRequiresUnsubscribe(attemptId: Long): Boolean {
        check(ownershipAttemptId == attemptId) { "Subscription attempt changed during open" }
        closeInProgress = true
        return claimUnsubscribe(allowUnacceptedOpen = true)
    }

    @Synchronized
    fun connectionAttemptUnavailable(attemptId: Long): Boolean {
        if (ownershipAttemptId != attemptId) return false
        ownershipAttemptId = null
        accepted = false
        ownershipSettled = true
        unsubscribeInProgress = false
        unsubscribeSettlement?.countDown()
        unsubscribeSettlement = null
        return true
    }

    private fun claimUnsubscribe(allowUnacceptedOpen: Boolean = false): Boolean {
        if (ownershipAttemptId == null || unsubscribeInProgress) return false
        if (!allowUnacceptedOpen && openSettlement != null && !accepted) return false
        unsubscribeInProgress = true
        unsubscribeSettlement = CountDownLatch(1)
        return true
    }

    @Synchronized
    fun openSettled() {
        openSettlement?.countDown()
        openSettlement = null
    }

    @Synchronized
    fun unsubscribeSettled(attemptId: Long, success: Boolean) {
        check(unsubscribeInProgress) { "No subscription unsubscribe is in progress" }
        check(ownershipAttemptId == attemptId) { "Subscription attempt changed during unsubscribe" }
        if (success) {
            ownershipAttemptId = null
            accepted = false
            ownershipSettled = true
        } else {
            ownershipSettled = false
        }
        unsubscribeInProgress = false
        unsubscribeSettlement?.countDown()
        unsubscribeSettlement = null
    }

    fun awaitOwnershipSettlement(timeoutMillis: Long): Boolean {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val opening = synchronized(this) { openSettlement }
        if (opening != null && !opening.awaitRemaining(deadlineNanos)) return false

        while (true) {
            val unsubscribe = synchronized(this) {
                if (!unsubscribeInProgress) {
                    return ownershipAttemptId == null && ownershipSettled
                }
                unsubscribeSettlement
            }
            if (unsubscribe != null && !unsubscribe.awaitRemaining(deadlineNanos)) return false
        }
    }
}

private fun CountDownLatch.awaitRemaining(deadlineNanos: Long): Boolean {
    val remainingNanos = deadlineNanos - System.nanoTime()
    return remainingNanos > 0L && await(remainingNanos, TimeUnit.NANOSECONDS)
}

internal class LiveSubscriptionFactoryOwner<T : Any> {
    private var retired = false
    private val sources = mutableListOf<T>()

    @Synchronized
    fun create(factory: () -> T): T {
        check(!retired) { "Live subscription factory is retired" }
        return factory().also(sources::add)
    }

    @Synchronized
    fun current(): T? = if (retired) null else sources.lastOrNull()

    @Synchronized
    fun retire(): List<T> {
        retired = true
        return sources.toList()
    }

    @Synchronized
    fun releaseSettled(source: T) {
        check(retired) { "Factory is still active" }
        sources.remove(source)
    }
}
