package at.bernhardberger.tvhplayer.player

import at.bernhardberger.tvhplayer.core.PlaybackIntent
import at.bernhardberger.tvhplayer.core.PlaybackRejectionReason
import at.bernhardberger.tvhplayer.core.PlaybackSubmissionDecision
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class PlaybackCommandOrderingTest {
    @Test
    fun laterTuneInvalidatesEarlierSuspendedCommitBeforeEnteringTheGate() = runBlocking {
        val coordinator = PlaybackIssuanceCoordinator()
        val gate = PlayerCommandGate()
        val first = coordinator.submit(PlaybackIntent.Live(serviceId = 10))
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val committed = CopyOnWriteArrayList<Int>()

        val firstJob = launch(Dispatchers.Default) {
            try {
                gate.run {
                    firstEntered.complete(Unit)
                    releaseFirst.await()
                    coordinator.commitIfCurrent(epoch = 1L) { committed += 10 }
                }
            } finally {
                coordinator.complete(epoch = 1L)
            }
        }
        firstEntered.await()

        val second = coordinator.submit(PlaybackIntent.Live(serviceId = 11))
        val secondJob = launch(Dispatchers.Default) {
            try {
                gate.run {
                    coordinator.commitIfCurrent(epoch = 2L) { committed += 11 }
                }
            } finally {
                coordinator.complete(epoch = 2L)
            }
        }

        releaseFirst.complete(Unit)
        joinAll(firstJob, secondJob)

        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 1L), first.decision)
        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 2L), second.decision)
        assertEquals(listOf(11), committed)
    }

    @Test
    fun concurrentEquivalentStartJoinsTheOnlyCommand() = runBlocking {
        val coordinator = PlaybackIssuanceCoordinator()
        val gate = PlayerCommandGate()
        val first = coordinator.submit(PlaybackIntent.Live(serviceId = 20))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var commits = 0

        val owner = launch(Dispatchers.Default) {
            try {
                gate.run {
                    entered.complete(Unit)
                    release.await()
                    coordinator.commitIfCurrent(epoch = 1L) { commits++ }
                }
            } finally {
                coordinator.complete(epoch = 1L)
            }
        }
        entered.await()
        val duplicate = coordinator.submit(PlaybackIntent.Live(serviceId = 20))
        val joined = async(Dispatchers.Default) { duplicate.completion.await() }

        assertFalse(joined.isCompleted)
        release.complete(Unit)
        owner.join()
        joined.await()

        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 1L), first.decision)
        assertEquals(PlaybackSubmissionDecision.Join(epoch = 1L), duplicate.decision)
        assertEquals(1, commits)
    }

    @Test
    fun queuedStopInvalidatesStartAndRejectsPlaybackUntilTeardownCompletes() = runBlocking {
        val coordinator = PlaybackIssuanceCoordinator()
        val gate = PlayerCommandGate()
        coordinator.submit(PlaybackIntent.Live(serviceId = 30))
        val startEntered = CompletableDeferred<Unit>()
        val releaseStart = CompletableDeferred<Unit>()
        var startCommitted = false
        var stopCompleted = false

        val start = launch(Dispatchers.Default) {
            try {
                gate.run {
                    startEntered.complete(Unit)
                    releaseStart.await()
                    coordinator.commitIfCurrent(epoch = 1L) { startCommitted = true }
                }
            } finally {
                coordinator.complete(epoch = 1L)
            }
        }
        startEntered.await()

        val stop = coordinator.submit(PlaybackIntent.Stop)
        val blocked = coordinator.submit(PlaybackIntent.Live(serviceId = 31))
        val teardown = launch(Dispatchers.Default) {
            try {
                gate.run { stopCompleted = true }
            } finally {
                coordinator.completeTeardown(epoch = 2L)
            }
        }

        releaseStart.complete(Unit)
        joinAll(start, teardown)
        blocked.completion.await()

        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 2L), stop.decision)
        assertEquals(
            PlaybackSubmissionDecision.Reject(PlaybackRejectionReason.TEARDOWN_IN_PROGRESS),
            blocked.decision,
        )
        assertFalse(startCommitted)
        assertTrue(stopCompleted)
        assertEquals(2L, coordinator.currentEpoch())
    }
}
