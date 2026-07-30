package at.bernhardberger.tvhplayer.player.htsp

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal fun interface TimeshiftMuxWritePermit {
    fun commit(write: () -> Boolean): Boolean
}

internal enum class TimeshiftMuxOffer {
    QUEUED,
    WRITTEN,
    DROPPED_STALE,
    FAILED,
}

/**
 * Preserves reader order while HTSP control and mux messages are collected on
 * separate coroutines during a timeshift seek.
 */
internal class TimeshiftSeekMuxGate<M>(
    private val sequenceOf: (M) -> Long,
    private val maxPendingMux: Int,
) {
    private enum class Phase {
        IDLE,
        PENDING,
        REPLAYING,
        INVALIDATED,
    }

    private val lock = ReentrantLock()
    private var phase = Phase.IDLE
    private var pendingAcknowledgement: CompletableDeferred<Boolean>? = null
    private val pendingMux = ArrayDeque<M>()
    private var writerGeneration = 0L
    private var dropThroughSequence = Long.MIN_VALUE

    fun beginSeek(): CompletableDeferred<Boolean> = lock.withLock {
        check(phase == Phase.IDLE) { "A timeshift seek cannot start in phase $phase" }
        writerGeneration++
        phase = Phase.PENDING
        CompletableDeferred<Boolean>().also {
            pendingAcknowledgement = it
        }
    }

    fun offer(
        mux: M,
        write: (M, TimeshiftMuxWritePermit) -> Boolean,
    ): TimeshiftMuxOffer {
        var failedAcknowledgement: CompletableDeferred<Boolean>? = null
        var offerGeneration = 0L
        val immediate = lock.withLock<TimeshiftMuxOffer?> {
            when (phase) {
                Phase.PENDING,
                Phase.REPLAYING -> {
                    if (pendingMux.size >= maxPendingMux) {
                        writerGeneration++
                        phase = Phase.INVALIDATED
                        failedAcknowledgement = pendingAcknowledgement
                        pendingAcknowledgement = null
                        pendingMux.clear()
                        TimeshiftMuxOffer.FAILED
                    } else {
                        pendingMux.addLast(mux)
                        TimeshiftMuxOffer.QUEUED
                    }
                }
                Phase.INVALIDATED -> TimeshiftMuxOffer.DROPPED_STALE
                Phase.IDLE -> {
                    if (sequenceOf(mux) <= dropThroughSequence) {
                        TimeshiftMuxOffer.DROPPED_STALE
                    } else {
                        offerGeneration = writerGeneration
                        null
                    }
                }
            }
        }
        failedAcknowledgement?.complete(false)
        if (immediate != null) return immediate

        val permit = TimeshiftMuxWritePermit { commit ->
            lock.withLock {
                if (phase == Phase.IDLE && writerGeneration == offerGeneration) {
                    commit()
                } else {
                    false
                }
            }
        }
        val written = write(mux, permit)
        if (written) return TimeshiftMuxOffer.WRITTEN
        return lock.withLock {
            if (phase != Phase.IDLE || writerGeneration != offerGeneration) {
                TimeshiftMuxOffer.DROPPED_STALE
            } else {
                TimeshiftMuxOffer.FAILED
            }
        }
    }

    fun acknowledge(
        messageSequence: Long,
        succeeded: Boolean,
        commit: (clearBufferedFrames: Boolean, readyMux: List<M>) -> Boolean,
    ): Boolean {
        val acknowledgement: CompletableDeferred<Boolean>
        lock.withLock {
            acknowledgement = pendingAcknowledgement ?: return false
            if (phase != Phase.PENDING) return false
            phase = Phase.REPLAYING
            if (succeeded) {
                dropThroughSequence = maxOf(dropThroughSequence, messageSequence)
            }
        }

        var firstBatch = true
        var committed = true
        var stillOwner = true
        while (committed && stillOwner) {
            var finished = false
            val readyMux = lock.withLock {
                if (
                    phase != Phase.REPLAYING ||
                    pendingAcknowledgement !== acknowledgement
                ) {
                    stillOwner = false
                    emptyList()
                } else {
                    val ready = if (succeeded) {
                        pendingMux.filter { sequenceOf(it) > dropThroughSequence }
                    } else {
                        pendingMux.toList()
                    }
                    pendingMux.clear()
                    if (!firstBatch && ready.isEmpty()) {
                        phase = Phase.IDLE
                        pendingAcknowledgement = null
                        finished = true
                    }
                    ready
                }
            }
            if (!stillOwner || finished) break
            committed = commit(firstBatch && succeeded, readyMux)
            firstBatch = false
        }

        if (!committed) {
            lock.withLock {
                if (pendingAcknowledgement === acknowledgement) {
                    writerGeneration++
                    phase = Phase.INVALIDATED
                    pendingAcknowledgement = null
                    pendingMux.clear()
                }
            }
        }
        if (stillOwner) acknowledgement.complete(succeeded && committed)
        return stillOwner && committed
    }

    fun cancel(acknowledgement: CompletableDeferred<Boolean>): Boolean {
        val cancelled = lock.withLock {
            if (pendingAcknowledgement !== acknowledgement) {
                false
            } else {
                writerGeneration++
                phase = Phase.INVALIDATED
                pendingAcknowledgement = null
                pendingMux.clear()
                true
            }
        }
        if (cancelled) acknowledgement.complete(false)
        return cancelled
    }

    fun cancelCurrent() {
        val acknowledgement = lock.withLock {
            pendingAcknowledgement.also {
                writerGeneration++
                phase = Phase.INVALIDATED
                pendingAcknowledgement = null
                pendingMux.clear()
            }
        }
        acknowledgement?.complete(false)
    }

}
