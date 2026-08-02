package at.bernhardberger.tvhplayer.player.htsp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TimeshiftSeekMuxGateTest {
    @Test
    fun successfulSkipKeepsPostAcknowledgementMuxThatArrivedBeforeControlHandling() = runBlocking {
        val gate = TimeshiftSeekMuxGate<TestMux>(
            sequenceOf = TestMux::sequence,
            maxPendingMux = 4,
        )
        val acknowledgement = gate.beginSeek()
        val committed = mutableListOf<String>()

        assertEquals(
            TimeshiftMuxOffer.QUEUED,
            gate.offer(TestMux(sequence = 10, name = "pre-ack")) { _, _ -> false },
        )
        assertEquals(
            TimeshiftMuxOffer.QUEUED,
            gate.offer(TestMux(sequence = 12, name = "post-ack")) { _, _ -> false },
        )

        assertTrue(
            gate.acknowledge(
                messageSequence = 11,
                succeeded = true,
            ) { clearBufferedFrames, readyMux ->
                if (clearBufferedFrames) committed += "clear"
                committed += readyMux.map(TestMux::name)
                true
            }
        )

        assertTrue(acknowledgement.await())
        assertEquals(listOf("clear", "post-ack"), committed)
        assertEquals(
            TimeshiftMuxOffer.WRITTEN,
            gate.offer(TestMux(sequence = 13, name = "next")) { mux, permit ->
                permit.commit {
                    committed += mux.name
                    true
                }
            },
        )
        assertEquals(listOf("clear", "post-ack", "next"), committed)
    }

    @Test
    fun rejectedSkipReleasesQueuedMuxWithoutClearingBufferedFrames() = runBlocking {
        val gate = TimeshiftSeekMuxGate<TestMux>(
            sequenceOf = TestMux::sequence,
            maxPendingMux = 4,
        )
        val acknowledgement = gate.beginSeek()
        val committed = mutableListOf<String>()
        var cleared = false

        gate.offer(TestMux(sequence = 20, name = "first")) { _, _ -> false }
        gate.offer(TestMux(sequence = 21, name = "second")) { _, _ -> false }

        assertTrue(
            gate.acknowledge(
                messageSequence = 22,
                succeeded = false,
            ) { clearBufferedFrames, readyMux ->
                cleared = clearBufferedFrames
                committed += readyMux.map(TestMux::name)
                true
            }
        )

        assertFalse(acknowledgement.await())
        assertFalse(cleared)
        assertEquals(listOf("first", "second"), committed)
    }

    @Test
    fun latePreAcknowledgementMuxIsDroppedAfterSuccessfulTransition() = runBlocking {
        val gate = TimeshiftSeekMuxGate<TestMux>(
            sequenceOf = TestMux::sequence,
            maxPendingMux = 4,
        )
        val acknowledgement = gate.beginSeek()
        val committed = mutableListOf<String>()

        gate.acknowledge(
            messageSequence = 31,
            succeeded = true,
        ) { clearBufferedFrames, readyMux ->
            if (clearBufferedFrames) committed += "clear"
            committed += readyMux.map(TestMux::name)
            true
        }

        assertTrue(acknowledgement.await())
        assertEquals(
            TimeshiftMuxOffer.DROPPED_STALE,
            gate.offer(TestMux(sequence = 30, name = "late-pre-ack")) { mux, permit ->
                permit.commit {
                    committed += mux.name
                    true
                }
            },
        )
        assertEquals(
            TimeshiftMuxOffer.WRITTEN,
            gate.offer(TestMux(sequence = 32, name = "post-ack")) { mux, permit ->
                permit.commit {
                    committed += mux.name
                    true
                }
            },
        )
        assertEquals(listOf("clear", "post-ack"), committed)
    }

    @Test
    fun timeoutInvalidatesGateInsteadOfMixingQueuedAndFutureMux() {
        val gate = TimeshiftSeekMuxGate<TestMux>(
            sequenceOf = TestMux::sequence,
            maxPendingMux = 4,
        )
        val acknowledgement = gate.beginSeek()
        var writeCalled = false

        gate.offer(TestMux(sequence = 40, name = "queued")) { _, _ -> false }

        assertTrue(gate.cancel(acknowledgement))
        assertEquals(
            TimeshiftMuxOffer.DROPPED_STALE,
            gate.offer(TestMux(sequence = 41, name = "after-timeout")) { _, permit ->
                permit.commit {
                    writeCalled = true
                    true
                }
            },
        )
        assertFalse(writeCalled)
    }

    @Test
    fun pendingMuxOverflowFailsInsteadOfDroppingTheRecoveryPoint() = runBlocking {
        val gate = TimeshiftSeekMuxGate<TestMux>(
            sequenceOf = TestMux::sequence,
            maxPendingMux = 2,
        )
        val acknowledgement = gate.beginSeek()

        assertEquals(
            TimeshiftMuxOffer.QUEUED,
            gate.offer(TestMux(sequence = 50, name = "keyframe")) { _, _ -> false },
        )
        assertEquals(
            TimeshiftMuxOffer.QUEUED,
            gate.offer(TestMux(sequence = 51, name = "next")) { _, _ -> false },
        )
        assertEquals(
            TimeshiftMuxOffer.FAILED,
            gate.offer(TestMux(sequence = 52, name = "overflow")) { _, _ -> false },
        )
        assertFalse(acknowledgement.await())
        assertEquals(
            TimeshiftMuxOffer.DROPPED_STALE,
            gate.offer(TestMux(sequence = 53, name = "after-overflow")) { _, _ -> true },
        )
    }

    @Test
    fun seekInvalidatesBlockedOldWriterWithoutWaitingForRingProgress() = runBlocking {
        val gate = TimeshiftSeekMuxGate<TestMux>(
            sequenceOf = TestMux::sequence,
            maxPendingMux = 4,
        )
        val writerEntered = CountDownLatch(1)
        val releaseWriter = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        var writeCommitted = false
        try {
            val writer = executor.submit<TimeshiftMuxOffer> {
                gate.offer(TestMux(sequence = 60, name = "old")) { _, permit ->
                    writerEntered.countDown()
                    assertTrue(releaseWriter.await(1, TimeUnit.SECONDS))
                    permit.commit {
                        writeCommitted = true
                        true
                    }
                }
            }
            assertTrue(writerEntered.await(1, TimeUnit.SECONDS))

            val acknowledgement = gate.beginSeek()
            assertTrue(
                gate.acknowledge(
                    messageSequence = 61,
                    succeeded = true,
                ) { _, _ -> true }
            )
            assertTrue(acknowledgement.await())

            releaseWriter.countDown()
            assertEquals(TimeshiftMuxOffer.DROPPED_STALE, writer.get(1, TimeUnit.SECONDS))
            assertFalse(writeCommitted)
        } finally {
            releaseWriter.countDown()
            executor.shutdownNow()
        }
    }

    private data class TestMux(
        val sequence: Long,
        val name: String,
    )
}
