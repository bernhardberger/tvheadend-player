package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuideCoverageRequestOwnerTest {
    @Test
    fun stalledAcquisitionTimesOutBeforeRetryStarts() = runTest {
        var starts = 0
        var cancellations = 0
        val owner = GuideCoverageRequestOwner(
            scope = backgroundScope,
            timeoutMillis = 1_000L,
        )
        val channelId = ChannelId(7)
        val acquire: suspend (List<ChannelId>) -> Unit = {
            starts++
            try {
                awaitCancellation()
            } finally {
                cancellations++
            }
        }

        val first = owner.request(listOf(channelId), windowStartSec = 100L, acquire)
        runCurrent()
        val duplicate = owner.request(listOf(channelId), windowStartSec = 100L, acquire)
        runCurrent()

        assertEquals(first, duplicate)
        assertEquals(1, starts)
        assertTrue(owner.isPending(first))

        advanceTimeBy(1_000L)
        runCurrent()

        assertFalse(owner.isPending(first))
        assertEquals(1, cancellations)

        val retry = owner.request(listOf(channelId), windowStartSec = 100L, acquire)
        runCurrent()

        assertEquals(2, starts)
        assertTrue(owner.isPending(retry))
        owner.dispose()
        runCurrent()
        assertEquals(2, cancellations)
        assertFalse(owner.isPending(retry))
    }

    @Test
    fun replacementAndFailureReleaseOnlyTheirOwnedGeneration() = runTest {
        val channelId = ChannelId(9)
        var cancellations = 0
        val owner = GuideCoverageRequestOwner(
            scope = backgroundScope,
            timeoutMillis = 10_000L,
        )
        val first = owner.request(listOf(channelId), windowStartSec = 100L) {
            try {
                awaitCancellation()
            } finally {
                cancellations++
            }
        }
        runCurrent()

        val replacement = owner.request(listOf(channelId), windowStartSec = 200L) {
            throw IllegalStateException("expected acquisition failure")
        }
        runCurrent()

        assertEquals(1, cancellations)
        assertFalse(owner.isPending(first))
        assertFalse(owner.isPending(replacement))
    }

    @Test
    fun scopeCleanupCancelsEveryOwnedChannelRequest() = runTest {
        val owner = GuideCoverageRequestOwner(
            scope = backgroundScope,
            timeoutMillis = 10_000L,
        )
        var cancellations = 0
        val token = owner.request(
            channelIds = listOf(ChannelId(1), ChannelId(2)),
            windowStartSec = 100L,
        ) {
            try {
                awaitCancellation()
            } finally {
                cancellations++
            }
        }
        runCurrent()

        owner.cancelAll()
        runCurrent()

        assertEquals(1, cancellations)
        assertFalse(owner.isPending(token))
    }

    @Test
    fun pageIsAcquiredOnceInVisibleChannelOrderAndSettlesTogether() = runTest {
        val owner = GuideCoverageRequestOwner(
            scope = backgroundScope,
            timeoutMillis = 10_000L,
        )
        val first = ChannelId(3)
        val second = ChannelId(1)
        val acquisitions = mutableListOf<List<ChannelId>>()

        val token = owner.request(
            channelIds = listOf(first, second, first),
            windowStartSec = 100L,
        ) { channelIds ->
            acquisitions += channelIds
        }
        runCurrent()

        assertEquals(listOf(listOf(first, second)), acquisitions)
        assertFalse(owner.isPending(token))
        assertFalse(owner.isPending(first, windowStartSec = 100L))
        assertFalse(owner.isPending(second, windowStartSec = 100L))
    }
}
