package at.bernhardberger.tvhplayer.player

import at.bernhardberger.tvhplayer.core.PlaybackIntent
import at.bernhardberger.tvhplayer.core.PlaybackSubmissionDecision
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class PlaybackIssuanceCoordinatorTest {
    @Test
    fun equivalentRequestJoinsTheSoleIssuanceUntilItCompletes() = runBlocking {
        val coordinator = PlaybackIssuanceCoordinator()
        val first = coordinator.submit(PlaybackIntent.Live(serviceId = 10))
        val duplicate = coordinator.submit(PlaybackIntent.Live(serviceId = 10))

        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 1L), first.decision)
        assertEquals(PlaybackSubmissionDecision.Join(epoch = 1L), duplicate.decision)
        assertFalse(duplicate.completion.isCompleted)

        coordinator.complete(epoch = 1L)

        duplicate.completion.await()
        assertTrue(first.completion.isCompleted)
    }

    @Test
    fun currentCommitAndLaterIntentRegistrationCannotInterleave() = runBlocking {
        val coordinator = PlaybackIssuanceCoordinator()
        val issued = coordinator.submit(PlaybackIntent.Live(serviceId = 20))
        val commitEntered = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val submitStarted = CompletableDeferred<Unit>()
        val events = CopyOnWriteArrayList<String>()

        val commit = launch(Dispatchers.Default) {
            coordinator.commitIfCurrent(epoch = 1L) {
                events += "commit-start"
                commitEntered.complete(Unit)
                runBlocking { releaseCommit.await() }
                events += "commit-end"
            }
        }
        commitEntered.await()

        val later = async(Dispatchers.Default) {
            events += "submit-start"
            submitStarted.complete(Unit)
            coordinator.submit(PlaybackIntent.Live(serviceId = 21)).also {
                events += "submit-end"
            }
        }
        submitStarted.await()
        assertFalse(later.isCompleted)

        releaseCommit.complete(Unit)
        commit.join()
        val laterTicket = later.await()

        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 1L), issued.decision)
        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 2L), laterTicket.decision)
        assertEquals(
            listOf("commit-start", "submit-start", "commit-end", "submit-end"),
            events,
        )
    }

    @Test
    fun teardownRejectionExposesBarrierCompletionWithoutStartingPlayback() = runBlocking {
        val coordinator = PlaybackIssuanceCoordinator()
        coordinator.submit(PlaybackIntent.Live(serviceId = 30))
        val stop = coordinator.submit(PlaybackIntent.Stop)
        val blocked = coordinator.submit(PlaybackIntent.Live(serviceId = 31))

        assertFalse(blocked.completion.isCompleted)
        coordinator.completeTeardown(epoch = 2L)
        blocked.completion.await()
        assertTrue(stop.completion.isCompleted)
        assertEquals(2L, coordinator.currentEpoch())
    }

    @Test
    fun failedReleaseNotifiesJoinersAndCanBeRetriedByANewReleaseOwner() = runBlocking {
        val coordinator = PlaybackIssuanceCoordinator()
        val first = coordinator.submit(PlaybackIntent.Release)
        val joiner = coordinator.submit(PlaybackIntent.Release)

        coordinator.failTeardown(epoch = 1L, error = IOException("unsubscribe failed"))

        assertTrue(runCatching { joiner.completion.await() }.isFailure)
        val retry = coordinator.submit(PlaybackIntent.Release)
        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 2L), retry.decision)
        assertFalse(coordinator.isReleased())

        coordinator.completeTeardown(epoch = 2L)
        retry.completion.await()
        assertTrue(coordinator.isReleased())
        assertTrue(first.completion.isCompleted)
    }

    @Test
    fun failedStopNotifiesJoinersAndAllowsTerminalReleaseEscalation() = runBlocking {
        val coordinator = PlaybackIssuanceCoordinator()
        coordinator.submit(PlaybackIntent.Stop)
        val joiner = coordinator.submit(PlaybackIntent.Stop)

        coordinator.failTeardown(epoch = 1L, error = IOException("unsubscribe failed"))

        assertTrue(runCatching { joiner.completion.await() }.isFailure)
        val release = coordinator.submit(PlaybackIntent.Release)
        assertEquals(PlaybackSubmissionDecision.Issue(epoch = 2L), release.decision)
    }
}
