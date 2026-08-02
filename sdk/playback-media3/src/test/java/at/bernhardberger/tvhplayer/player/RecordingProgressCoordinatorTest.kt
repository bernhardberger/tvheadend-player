package at.bernhardberger.tvhplayer.player

import at.bernhardberger.tvhplayer.core.RecordingCheckpointTrigger
import at.bernhardberger.tvhplayer.htsp.DvrState
import at.bernhardberger.tvhplayer.htsp.RecordingProgressCapability
import at.bernhardberger.tvhplayer.htsp.RecordingProgressUpdateResult
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingProgressCoordinatorTest {
    @Test
    fun periodicWritesAtThirtySecondsAndTenSecondDeltaOnly() = runBlocking {
        val writes = mutableListOf<RecordingProgressWrite>()
        val coordinator = coordinator(writes)

        coordinator.tick(positionMs = 189_000L, playing = true, nowMs = 30_000L)
        coordinator.tick(positionMs = 190_000L, playing = true, nowMs = 30_000L)
        coordinator.tick(positionMs = 220_000L, playing = true, nowMs = 59_999L)
        coordinator.tick(positionMs = 220_000L, playing = true, nowMs = 60_000L)

        assertEquals(
            listOf(
                RecordingProgressWrite(190L, setWatched = false),
                RecordingProgressWrite(220L, setWatched = false),
            ),
            writes,
        )
    }

    @Test
    fun seekPauseAndFinalAllHonorResumeFloor() = runBlocking {
        val writes = mutableListOf<RecordingProgressWrite>()
        val coordinator = RecordingProgressCoordinator(
            initialServerPositionSeconds = 0L,
            capability = RecordingProgressCapability.Full,
            writer = RecordingProgressWriter { write ->
                writes += write
                RecordingProgressUpdateResult.Accepted
            },
        )

        RecordingCheckpointTrigger.entries.forEach { trigger ->
            coordinator.checkpoint(trigger, positionMs = 179_999L, nowMs = 1L)
        }
        coordinator.checkpoint(RecordingCheckpointTrigger.Seek, 180_000L, 2L)
        coordinator.checkpoint(RecordingCheckpointTrigger.Pause, 181_000L, 3L)
        coordinator.finish(
            positionMs = 182_000L,
            durationMs = 3_600_000L,
            dvrState = DvrState.COMPLETED,
            naturalEnd = false,
            terminalError = false,
            nowMs = 4L,
        )

        assertEquals(listOf(180L, 181L, 182L), writes.map { it.positionSeconds })
    }

    @Test
    fun replacementFinalPersistsLatestAboveFloorPlayerPositionWithoutSeekCheckpoint() =
        runBlocking {
            val writes = mutableListOf<RecordingProgressWrite>()
            val coordinator = RecordingProgressCoordinator(
                initialServerPositionSeconds = null,
                capability = RecordingProgressCapability.Full,
                writer = RecordingProgressWriter { write ->
                    writes += write
                    RecordingProgressUpdateResult.Accepted
                },
            )
            coordinator.playbackStarted(positionMs = 0L, nowMs = 0L)

            coordinator.finish(
                positionMs = 240_000L,
                durationMs = 3_600_000L,
                dvrState = DvrState.COMPLETED,
                naturalEnd = false,
                terminalError = false,
                nowMs = 1_000L,
            )

            assertEquals(
                listOf(RecordingProgressWrite(240L, setWatched = false)),
                writes,
            )
        }

    @Test
    fun oneRequestIsInFlightAndNewerPendingReplacesOlder() = runBlocking {
        val writes = CopyOnWriteArrayList<RecordingProgressWrite>()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val coordinator = RecordingProgressCoordinator(
            initialServerPositionSeconds = 180L,
            capability = RecordingProgressCapability.Full,
            writer = RecordingProgressWriter { write ->
                writes += write
                if (writes.size == 1) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                RecordingProgressUpdateResult.Accepted
            },
        )

        val first = launch(Dispatchers.Default) {
            coordinator.checkpoint(RecordingCheckpointTrigger.Seek, 200_000L, 1L)
        }
        firstStarted.await()
        coordinator.checkpoint(RecordingCheckpointTrigger.Seek, 210_000L, 2L)
        coordinator.checkpoint(RecordingCheckpointTrigger.Seek, 220_000L, 3L)
        releaseFirst.complete(Unit)
        first.join()

        assertEquals(listOf(200L, 220L), writes.map { it.positionSeconds })
    }

    @Test
    fun cleanRemoteUpdateBecomesBaselineAndIdleTickDoesNotOverwriteIt() = runBlocking {
        val writes = mutableListOf<RecordingProgressWrite>()
        val coordinator = coordinator(writes)
        coordinator.checkpoint(RecordingCheckpointTrigger.Pause, 200_000L, 1L)

        coordinator.remoteUpdate(900L)
        coordinator.tick(positionMs = 200_000L, playing = false, nowMs = 120_000L)

        assertEquals(listOf(200L), writes.map { it.positionSeconds })
        assertEquals(900L, coordinator.snapshot().lastAcceptedSeconds)
    }

    @Test
    fun cleanRemoteUpdateIsNotOverwrittenByFinalFlush() = runBlocking {
        val writes = mutableListOf<RecordingProgressWrite>()
        val coordinator = coordinator(writes)
        coordinator.checkpoint(RecordingCheckpointTrigger.Pause, 200_000L, 1L)
        coordinator.remoteUpdate(900L)

        coordinator.finish(
            positionMs = 200_000L,
            durationMs = 3_600_000L,
            dvrState = DvrState.COMPLETED,
            naturalEnd = false,
            terminalError = false,
            nowMs = 2L,
        )

        assertEquals(listOf(200L), writes.map { it.positionSeconds })
        assertEquals(900L, coordinator.snapshot().lastAcceptedSeconds)
    }

    @Test
    fun localMovementAfterRemoteUpdateMayWinAtFinalFlush() = runBlocking {
        val writes = mutableListOf<RecordingProgressWrite>()
        val coordinator = coordinator(writes)
        coordinator.checkpoint(RecordingCheckpointTrigger.Pause, 200_000L, 1L)
        coordinator.remoteUpdate(900L)

        coordinator.finish(
            positionMs = 240_000L,
            durationMs = 3_600_000L,
            dvrState = DvrState.COMPLETED,
            naturalEnd = false,
            terminalError = false,
            nowMs = 2L,
        )

        assertEquals(listOf(200L, 240L), writes.map { it.positionSeconds })
    }

    @Test
    fun transientRetryUsesBackoffAndDoesNotSurviveFinish() = runBlocking {
        val writes = mutableListOf<RecordingProgressWrite>()
        var result: RecordingProgressUpdateResult = RecordingProgressUpdateResult.Timeout
        val coordinator = RecordingProgressCoordinator(
            initialServerPositionSeconds = 180L,
            capability = RecordingProgressCapability.Full,
            writer = RecordingProgressWriter { write ->
                writes += write
                result
            },
        )

        coordinator.checkpoint(RecordingCheckpointTrigger.Seek, 200_000L, 0L)
        coordinator.tick(210_000L, playing = true, nowMs = 29_999L)
        assertEquals(1, writes.size)
        result = RecordingProgressUpdateResult.Accepted
        coordinator.tick(210_000L, playing = true, nowMs = 30_000L)
        assertEquals(2, writes.size)

        coordinator.finish(
            positionMs = 220_000L,
            durationMs = 3_600_000L,
            dvrState = DvrState.COMPLETED,
            naturalEnd = false,
            terminalError = false,
            nowMs = 31_000L,
        )
        coordinator.tick(300_000L, playing = true, nowMs = 600_000L)
        assertEquals(3, writes.size)
        assertTrue(coordinator.snapshot().ending)
    }

    @Test
    fun newerRemoteUpdateCancelsAmbiguousTimedOutRetryWithoutLocalMovement() = runBlocking {
        val writes = mutableListOf<RecordingProgressWrite>()
        val coordinator = RecordingProgressCoordinator(
            initialServerPositionSeconds = 180L,
            capability = RecordingProgressCapability.Full,
            writer = RecordingProgressWriter { write ->
                writes += write
                RecordingProgressUpdateResult.Timeout
            },
        )

        coordinator.checkpoint(RecordingCheckpointTrigger.Seek, 200_000L, 0L)
        coordinator.remoteUpdate(900L)
        coordinator.tick(200_000L, playing = false, nowMs = 300_000L)

        assertEquals(listOf(200L), writes.map { it.positionSeconds })
        assertEquals(900L, coordinator.snapshot().lastAcceptedSeconds)
        assertEquals(RecordingProgressSyncState.Available, coordinator.snapshot().syncState)
    }

    @Test
    fun unchangedRemoteSnapshotDoesNotCancelAmbiguousTimedOutRetry() = runBlocking {
        val writes = mutableListOf<RecordingProgressWrite>()
        var result: RecordingProgressUpdateResult = RecordingProgressUpdateResult.Timeout
        val coordinator = RecordingProgressCoordinator(
            initialServerPositionSeconds = 180L,
            capability = RecordingProgressCapability.Full,
            writer = RecordingProgressWriter { write ->
                writes += write
                result
            },
        )

        coordinator.checkpoint(RecordingCheckpointTrigger.Seek, 200_000L, 0L)
        coordinator.remoteUpdate(180L)
        result = RecordingProgressUpdateResult.Accepted
        coordinator.tick(200_000L, playing = false, nowMs = 30_000L)

        assertEquals(listOf(200L, 200L), writes.map { it.positionSeconds })
        assertEquals(200L, coordinator.snapshot().lastAcceptedSeconds)
    }

    @Test
    fun remoteUpdateDuringAmbiguousRequestPreventsRetry() = runBlocking {
        val writes = mutableListOf<RecordingProgressWrite>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val coordinator = RecordingProgressCoordinator(
            initialServerPositionSeconds = 180L,
            capability = RecordingProgressCapability.Full,
            writer = RecordingProgressWriter { write ->
                writes += write
                started.complete(Unit)
                release.await()
                RecordingProgressUpdateResult.Timeout
            },
        )

        val request = launch(Dispatchers.Default) {
            coordinator.checkpoint(RecordingCheckpointTrigger.Seek, 200_000L, 0L)
        }
        started.await()
        coordinator.remoteUpdate(900L)
        release.complete(Unit)
        request.join()
        coordinator.tick(200_000L, playing = false, nowMs = 300_000L)

        assertEquals(listOf(200L), writes.map { it.positionSeconds })
        assertEquals(900L, coordinator.snapshot().lastAcceptedSeconds)
    }

    @Test
    fun naturalEndAndNearEndFinalCompleteAtMostOnce() = runBlocking {
        val writes = mutableListOf<RecordingProgressWrite>()
        val coordinator = coordinator(writes)

        assertTrue(
            coordinator.finish(
                positionMs = 5_900_000L,
                durationMs = 6_000_000L,
                dvrState = DvrState.COMPLETED,
                naturalEnd = true,
                terminalError = false,
                nowMs = 1L,
            ),
        )
        assertFalse(
            coordinator.finish(
                positionMs = 5_900_000L,
                durationMs = 6_000_000L,
                dvrState = DvrState.COMPLETED,
                naturalEnd = false,
                terminalError = false,
                nowMs = 2L,
            ),
        )
        coordinator.checkpoint(RecordingCheckpointTrigger.Final, 5_900_000L, 3L)

        assertEquals(listOf(RecordingProgressWrite(0L, setWatched = true)), writes)
    }

    @Test
    fun terminalErrorNearEndPreservesNonzeroPosition() = runBlocking {
        val writes = mutableListOf<RecordingProgressWrite>()
        val coordinator = coordinator(writes)

        val completed = coordinator.finish(
            positionMs = 5_900_000L,
            durationMs = 6_000_000L,
            dvrState = DvrState.COMPLETED,
            naturalEnd = false,
            terminalError = true,
            nowMs = 1L,
        )

        assertFalse(completed)
        assertEquals(listOf(RecordingProgressWrite(5_900L, setWatched = false)), writes)
    }

    @Test
    fun readOnlyAndUnsupportedSessionsRemainPlayableWithoutWrites() = runBlocking {
        val writes = mutableListOf<RecordingProgressWrite>()
        val readOnly = coordinator(writes, RecordingProgressCapability.ReadOnly)
        val unsupported = coordinator(writes, RecordingProgressCapability.Unsupported)

        readOnly.checkpoint(RecordingCheckpointTrigger.Pause, 300_000L, 1L)
        unsupported.checkpoint(RecordingCheckpointTrigger.Pause, 300_000L, 1L)
        yield()

        assertTrue(writes.isEmpty())
        assertEquals(RecordingProgressSyncState.ReadOnly, readOnly.snapshot().syncState)
        assertEquals(RecordingProgressSyncState.Unsupported, unsupported.snapshot().syncState)
    }

    @Test
    fun growingRecordingProgressRemainsGated() = runBlocking {
        val writes = mutableListOf<RecordingProgressWrite>()
        val coordinator = RecordingProgressCoordinator(
            initialServerPositionSeconds = 600L,
            capability = RecordingProgressCapability.Full,
            enabled = false,
            writer = RecordingProgressWriter { write ->
                writes += write
                RecordingProgressUpdateResult.Accepted
            },
        )

        coordinator.tick(positionMs = 900_000L, playing = true, nowMs = 60_000L)
        coordinator.checkpoint(RecordingCheckpointTrigger.Pause, 900_000L, 61_000L)
        coordinator.finish(
            positionMs = 900_000L,
            durationMs = null,
            dvrState = DvrState.RECORDING,
            naturalEnd = true,
            terminalError = false,
            nowMs = 62_000L,
        )

        assertTrue(writes.isEmpty())
        assertEquals(RecordingProgressSyncState.Inactive, coordinator.snapshot().syncState)
    }

    private fun coordinator(
        writes: MutableList<RecordingProgressWrite>,
        capability: RecordingProgressCapability = RecordingProgressCapability.Full,
    ) = RecordingProgressCoordinator(
        initialServerPositionSeconds = 180L,
        capability = capability,
        writer = RecordingProgressWriter { write ->
            writes += write
            RecordingProgressUpdateResult.Accepted
        },
    )
}
