package at.bernhardberger.tvhplayer.player

import at.bernhardberger.tvhplayer.core.RECORDING_PERIODIC_INTERVAL_MS
import at.bernhardberger.tvhplayer.core.RecordingCheckpointTrigger
import at.bernhardberger.tvhplayer.core.RecordingCompletionTrigger
import at.bernhardberger.tvhplayer.core.recordingCheckpointSeconds
import at.bernhardberger.tvhplayer.core.recordingCompletionDecision
import at.bernhardberger.tvhplayer.core.recordingPeriodicCheckpointDue
import at.bernhardberger.tvhplayer.core.recordingRemoteUpdateDecision
import at.bernhardberger.tvhplayer.core.RecordingRemoteUpdateDecision
import at.bernhardberger.tvhplayer.htsp.DvrState
import at.bernhardberger.tvhplayer.repositories.RecordingProgressCapability
import at.bernhardberger.tvhplayer.repositories.RecordingProgressUpdateResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RecordingProgressWrite(
    val positionSeconds: Long,
    val setWatched: Boolean,
)

fun interface RecordingProgressWriter {
    suspend fun write(progress: RecordingProgressWrite): RecordingProgressUpdateResult
}

enum class RecordingProgressSyncState {
    Inactive,
    Available,
    Saving,
    Degraded,
    ReadOnly,
    Unsupported,
}

data class RecordingProgressSnapshot(
    val lastServerSeconds: Long?,
    val lastSubmittedSeconds: Long?,
    val lastAcceptedSeconds: Long?,
    val localDirty: Boolean,
    val ending: Boolean,
    val syncState: RecordingProgressSyncState,
)

class RecordingProgressCoordinator(
    initialServerPositionSeconds: Long?,
    capability: RecordingProgressCapability,
    enabled: Boolean = true,
    private val writer: RecordingProgressWriter,
) {
    private val stateMutex = Mutex()
    private val writeMutex = Mutex()
    private var lastServerSeconds = initialServerPositionSeconds
    private var lastSubmittedSeconds: Long? = null
    private var lastAcceptedSeconds = initialServerPositionSeconds
    private var lastObservedLocalSeconds: Long? = null
    private var lastAttemptMs = 0L
    private var localDirty = false
    private var ending = false
    private var draining = false
    private var pending: RecordingProgressWrite? = null
    private var retryIndex = 0
    private var nextRetryAtMs: Long? = null
    private var remoteRevision = 0L
    private var localChangedSinceSubmission = false
    private var writesDisabled = !enabled || capability != RecordingProgressCapability.Full
    private var syncState = if (!enabled) {
        RecordingProgressSyncState.Inactive
    } else {
        when (capability) {
            RecordingProgressCapability.Full -> RecordingProgressSyncState.Available
            RecordingProgressCapability.ReadOnly -> RecordingProgressSyncState.ReadOnly
            RecordingProgressCapability.Unsupported -> RecordingProgressSyncState.Unsupported
            RecordingProgressCapability.Disconnected -> RecordingProgressSyncState.Degraded
        }
    }

    suspend fun tick(positionMs: Long, playing: Boolean, nowMs: Long) {
        val seconds = recordingCheckpointSeconds(RecordingCheckpointTrigger.Periodic, positionMs)
        val periodicDue = stateMutex.withLock {
            if (ending || writesDisabled) return@withLock false
            if (playing && seconds != null) observeLocalPosition(seconds)
            playing && localDirty && seconds != null && recordingPeriodicCheckpointDue(
                nowMs = nowMs,
                lastAttemptMs = lastAttemptMs,
                positionSeconds = seconds,
                lastAcceptedSeconds = lastAcceptedSeconds,
            )
        }
        if (periodicDue && seconds != null) {
            checkpoint(RecordingCheckpointTrigger.Periodic, positionMs, nowMs)
        } else {
            drainIfDue(nowMs)
        }
    }

    suspend fun playbackStarted(positionMs: Long, nowMs: Long) {
        stateMutex.withLock {
            if (!ending) {
                lastAttemptMs = nowMs
                mediaPositionSeconds(positionMs)?.let { lastObservedLocalSeconds = it }
            }
        }
    }

    suspend fun checkpoint(
        trigger: RecordingCheckpointTrigger,
        positionMs: Long,
        nowMs: Long,
    ) {
        val seconds = recordingCheckpointSeconds(trigger, positionMs) ?: return
        val locallyChanged = stateMutex.withLock {
            if (ending || writesDisabled) return@withLock false
            observeLocalPosition(seconds)
            localDirty
        }
        if (locallyChanged) {
            enqueue(RecordingProgressWrite(seconds, setWatched = false), nowMs)
        }
    }

    suspend fun startOverStarted(nowMs: Long) {
        stateMutex.withLock {
            if (!ending && !writesDisabled) localDirty = true
        }
        enqueue(RecordingProgressWrite(0L, setWatched = false), nowMs)
    }

    suspend fun remoteUpdate(positionSeconds: Long) {
        if (positionSeconds < 0L) return
        stateMutex.withLock {
            if (positionSeconds == lastServerSeconds) return@withLock
            remoteRevision++
            lastServerSeconds = positionSeconds
            val unresolvedSubmission = draining || nextRetryAtMs != null
            val reconciliationDirty = if (unresolvedSubmission) {
                localChangedSinceSubmission
            } else {
                localDirty
            }
            if (
                recordingRemoteUpdateDecision(reconciliationDirty, positionSeconds) ==
                RecordingRemoteUpdateDecision.AdoptRemote
            ) {
                lastAcceptedSeconds = positionSeconds
                pending = null
                localDirty = false
                nextRetryAtMs = null
                retryIndex = 0
                if (!writesDisabled) syncState = RecordingProgressSyncState.Available
            }
        }
    }

    suspend fun finish(
        positionMs: Long,
        durationMs: Long?,
        dvrState: DvrState,
        naturalEnd: Boolean,
        terminalError: Boolean,
        nowMs: Long,
    ): Boolean {
        val completion = recordingCompletionDecision(
            state = dvrState,
            trigger = if (naturalEnd) {
                RecordingCompletionTrigger.NaturalEnd
            } else {
                RecordingCompletionTrigger.Final
            },
            positionMs = positionMs,
            durationMs = durationMs,
            terminalError = terminalError,
        )
        val shouldWrite = stateMutex.withLock {
            if (ending) return false
            mediaPositionSeconds(positionMs)?.let(::observeLocalPosition)
            ending = true
            pending = null
            nextRetryAtMs = null
            !writesDisabled && (completion || localDirty)
        }
        if (!shouldWrite) return completion
        val write = if (completion) {
            RecordingProgressWrite(0L, setWatched = true)
        } else {
            recordingCheckpointSeconds(RecordingCheckpointTrigger.Final, positionMs)
                ?.let { RecordingProgressWrite(it, setWatched = false) }
        }
        if (write != null) writeFinal(write, nowMs)
        return completion
    }

    suspend fun snapshot(): RecordingProgressSnapshot = stateMutex.withLock {
        RecordingProgressSnapshot(
            lastServerSeconds = lastServerSeconds,
            lastSubmittedSeconds = lastSubmittedSeconds,
            lastAcceptedSeconds = lastAcceptedSeconds,
            localDirty = localDirty,
            ending = ending,
            syncState = syncState,
        )
    }

    private suspend fun enqueue(write: RecordingProgressWrite, nowMs: Long) {
        val shouldDrain = stateMutex.withLock {
            if (ending || writesDisabled) return@withLock false
            pending = write
            if (draining || nextRetryAtMs?.let { nowMs < it } == true) {
                false
            } else {
                draining = true
                true
            }
        }
        if (shouldDrain) drain(nowMs)
    }

    private suspend fun drainIfDue(nowMs: Long) {
        val shouldDrain = stateMutex.withLock {
            if (
                ending || writesDisabled || draining || pending == null ||
                nextRetryAtMs?.let { nowMs < it } == true
            ) {
                false
            } else {
                draining = true
                true
            }
        }
        if (shouldDrain) drain(nowMs)
    }

    private suspend fun drain(nowMs: Long) {
        while (true) {
            val attempt = stateMutex.withLock {
                if (ending || writesDisabled) {
                    draining = false
                    return
                }
                pending.also {
                    pending = null
                    if (it == null) draining = false
                    else {
                        lastSubmittedSeconds = it.positionSeconds
                        lastAttemptMs = nowMs
                        localChangedSinceSubmission = false
                        syncState = RecordingProgressSyncState.Saving
                    }
                }?.let { PendingAttempt(it, remoteRevision) }
            } ?: return
            val write = attempt.write
            val result = writeMutex.withLock { writer.write(write) }
            val continueDraining = stateMutex.withLock {
                if (ending) {
                    draining = false
                    return@withLock false
                }
                when (result) {
                    RecordingProgressUpdateResult.Accepted -> {
                        lastAcceptedSeconds = write.positionSeconds
                        lastServerSeconds = write.positionSeconds
                        localDirty = pending != null
                        retryIndex = 0
                        nextRetryAtMs = null
                        syncState = RecordingProgressSyncState.Available
                        (pending != null).also { hasPending ->
                            if (!hasPending) draining = false
                        }
                    }
                    RecordingProgressUpdateResult.Timeout,
                    RecordingProgressUpdateResult.Disconnected -> {
                        val remoteAdvanced = remoteRevision != attempt.remoteRevision
                        if (remoteAdvanced && !localChangedSinceSubmission) {
                            localDirty = false
                            nextRetryAtMs = null
                            retryIndex = 0
                            syncState = RecordingProgressSyncState.Available
                            draining = false
                            false
                        } else {
                            if (pending == null) pending = write
                            nextRetryAtMs = nowMs + RETRY_BACKOFF_MS[
                                retryIndex.coerceAtMost(RETRY_BACKOFF_MS.lastIndex)
                            ]
                            retryIndex = (retryIndex + 1)
                                .coerceAtMost(RETRY_BACKOFF_MS.lastIndex)
                            syncState = RecordingProgressSyncState.Degraded
                            draining = false
                            false
                        }
                    }
                    RecordingProgressUpdateResult.PermissionDenied -> {
                        writesDisabled = true
                        pending = null
                        syncState = RecordingProgressSyncState.ReadOnly
                        draining = false
                        false
                    }
                    RecordingProgressUpdateResult.Unsupported -> {
                        writesDisabled = true
                        pending = null
                        syncState = RecordingProgressSyncState.Unsupported
                        draining = false
                        false
                    }
                    RecordingProgressUpdateResult.Rejected -> {
                        writesDisabled = true
                        pending = null
                        syncState = RecordingProgressSyncState.Degraded
                        draining = false
                        false
                    }
                }
            }
            if (!continueDraining) return
        }
    }

    private suspend fun writeFinal(write: RecordingProgressWrite, nowMs: Long) {
        stateMutex.withLock {
            lastSubmittedSeconds = write.positionSeconds
            lastAttemptMs = nowMs
            syncState = RecordingProgressSyncState.Saving
        }
        val result = writeMutex.withLock { writer.write(write) }
        stateMutex.withLock {
            when (result) {
                RecordingProgressUpdateResult.Accepted -> {
                    lastAcceptedSeconds = write.positionSeconds
                    lastServerSeconds = write.positionSeconds
                    localDirty = false
                    syncState = RecordingProgressSyncState.Available
                }
                RecordingProgressUpdateResult.PermissionDenied ->
                    syncState = RecordingProgressSyncState.ReadOnly
                RecordingProgressUpdateResult.Unsupported ->
                    syncState = RecordingProgressSyncState.Unsupported
                RecordingProgressUpdateResult.Timeout,
                RecordingProgressUpdateResult.Disconnected,
                RecordingProgressUpdateResult.Rejected ->
                    syncState = RecordingProgressSyncState.Degraded
            }
        }
    }

    private fun observeLocalPosition(positionSeconds: Long) {
        val previous = lastObservedLocalSeconds
        if (previous == null) {
            if (positionSeconds != lastAcceptedSeconds) localDirty = true
        } else if (positionSeconds != previous) {
            localDirty = true
            if (draining || nextRetryAtMs != null) localChangedSinceSubmission = true
        }
        lastObservedLocalSeconds = positionSeconds
    }

    private fun mediaPositionSeconds(positionMs: Long): Long? =
        positionMs.takeIf { it >= 0L }?.div(1_000L)

    private companion object {
        val RETRY_BACKOFF_MS = longArrayOf(
            RECORDING_PERIODIC_INTERVAL_MS,
            60_000L,
            120_000L,
            300_000L,
        )
    }

    private data class PendingAttempt(
        val write: RecordingProgressWrite,
        val remoteRevision: Long,
    )
}
