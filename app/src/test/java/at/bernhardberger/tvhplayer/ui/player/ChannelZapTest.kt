package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.core.ChannelNumberEntryReadiness
import at.bernhardberger.tvhplayer.core.ChannelNumberEntryReadiness.NOT_READY
import at.bernhardberger.tvhplayer.core.ChannelNumberEntryReadiness.READY
import at.bernhardberger.tvhplayer.core.ChannelNumberEntryReadiness.UNKNOWN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelZapTest {
    /** The screen's CH+/- path: the pacer picks the settle delay, the newest press supersedes. */
    private class Zapper(private val scope: CoroutineScope) {
        private val pacer = ChannelZapPacer()
        private var pending: Job? = null
        val tunes = mutableListOf<Pair<Int, Long>>()

        fun press(channel: Int, atMs: Long) {
            pending = scope.scheduleChannelZap(pending, pacer.settleDelayMs(atMs)) {
                tunes += channel to (scope as TestScope).currentTime
            }
        }
    }

    @Test
    fun singleChannelKeyTunesBeforeThePressReturns() = runTest {
        val zapper = Zapper(this)

        zapper.press(channel = 2, atMs = currentTime)

        assertEquals(listOf(2 to 0L), zapper.tunes)
        advanceUntilIdle()
        assertEquals(listOf(2 to 0L), zapper.tunes)
    }

    @Test
    fun burstTunesItsFirstChannelAtOnceAndOnlyItsLastOnceSettled() = runTest {
        val zapper = Zapper(this)

        for (channel in 2..6) {
            zapper.press(channel, atMs = currentTime)
            advanceTimeBy(100)
        }
        advanceUntilIdle()

        // Last press at 400 ms, settled CHANNEL_ZAP_SETTLE_MS later.
        assertEquals(listOf(2 to 0L, 6 to 400L + CHANNEL_ZAP_SETTLE_MS), zapper.tunes)
    }

    @Test
    fun pressAfterTheWindowTunesAtOnceAgain() = runTest {
        val zapper = Zapper(this)

        zapper.press(2, atMs = 0)
        advanceTimeBy(CHANNEL_ZAP_SETTLE_MS)
        zapper.press(3, atMs = CHANNEL_ZAP_SETTLE_MS)

        assertEquals(listOf(2 to 0L, 3 to CHANNEL_ZAP_SETTLE_MS), zapper.tunes)
    }

    @Test
    fun supersedingWithoutStartCancelsTheSettlingTune() = runTest {
        var tunes = 0
        val settling = scheduleChannelZap(null, 350) { tunes++ }

        assertNull(scheduleChannelZap(settling, null) { tunes++ })
        advanceUntilIdle()

        assertEquals(0, tunes)
    }

    @Test
    fun readyEntryCommitsWhenTheTimerFires() = runTest {
        val commits = mutableListOf<Triple<ChannelNumberEntryReadiness, ChannelNumberEntryReadiness, Long>>()

        launch {
            commitChannelNumberEntry(1_500, MutableStateFlow(READY)) { atTimer, settled ->
                commits += Triple(atTimer, settled, currentTime)
            }
        }
        advanceTimeBy(1_499)
        runCurrent()
        assertEquals(emptyList<Any>(), commits)
        advanceUntilIdle()

        assertEquals(listOf(Triple(READY, READY, 1_500L)), commits)
    }

    @Test
    fun entryUsesTheReadinessCurrentAtTheTimerNotAtTheDigit() = runTest {
        val readiness = MutableStateFlow(NOT_READY)
        val commits = mutableListOf<Pair<ChannelNumberEntryReadiness, Long>>()

        launch {
            commitChannelNumberEntry(1_500, readiness) { _, settled -> commits += settled to currentTime }
        }
        advanceTimeBy(1_000)
        readiness.value = READY
        advanceUntilIdle()

        assertEquals(listOf(READY to 1_500L), commits)
    }

    @Test
    fun entryNotReadyAtTheTimerCommitsOnceItBecomesReady() = runTest {
        val readiness = MutableStateFlow(NOT_READY)
        val commits = mutableListOf<Triple<ChannelNumberEntryReadiness, ChannelNumberEntryReadiness, Long>>()

        launch {
            commitChannelNumberEntry(1_500, readiness) { atTimer, settled ->
                commits += Triple(atTimer, settled, currentTime)
            }
        }
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(emptyList<Any>(), commits)
        readiness.value = READY
        runCurrent()

        assertEquals(listOf(Triple(NOT_READY, READY, 3_000L)), commits)
    }

    @Test
    fun entryThatNeverBecomesReadyGivesUpAtTheBound() = runTest {
        val commits = mutableListOf<Pair<ChannelNumberEntryReadiness, Long>>()

        launch {
            commitChannelNumberEntry(1_500, MutableStateFlow(NOT_READY)) { _, settled ->
                commits += settled to currentTime
            }
        }
        advanceUntilIdle()

        assertEquals(listOf(NOT_READY to CHANNEL_NUMBER_READY_BOUND_MS), commits)
    }

    @Test
    fun unknownNumberCommitsAsUnknownWithoutWaiting() = runTest {
        val commits = mutableListOf<Pair<ChannelNumberEntryReadiness, Long>>()

        launch {
            commitChannelNumberEntry(250, MutableStateFlow(UNKNOWN)) { _, settled -> commits += settled to currentTime }
        }
        advanceUntilIdle()

        assertEquals(listOf(UNKNOWN to 250L), commits)
    }
}
