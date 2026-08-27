package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppPlaybackRuntimeTest {
    @Test
    fun droppedFramePercentageUsesRenderedPlusDroppedFrames() {
        assertEquals(20f, droppedFramePercentage(renderedFrames = 80, droppedFrames = 20))
        assertNull(droppedFramePercentage(renderedFrames = 0, droppedFrames = 0))
    }

    @Test
    fun everyCanonicalSubscriptionIssueRetainsADistinctAppOutcome() {
        assertEquals(
            SubscriptionIssue.entries.size,
            SubscriptionIssue.entries.map { it.toApp() }.toSet().size,
        )
    }

    @Test
    fun everyReleasedTargetAndTimeshiftOutcomeRetainsADistinctAppOutcome() {
        assertEquals(
            PlaybackTargetResult.entries.size,
            PlaybackTargetResult.entries.map { it.toAppCommandResult() }.toSet().size,
        )
        assertEquals(
            TimeshiftCommandResult.entries.size,
            TimeshiftCommandResult.entries.map { it.toAppCommandResult() }.toSet().size,
        )
    }

    @Test
    fun delayedTuneCannotSubmitAfterANewerStopCommand() = runTest {
        val gate = AppPlaybackCommandGate()
        val tuneEntered = CompletableDeferred<Unit>()
        val releaseTune = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()

        val tune = async {
            gate.run {
                calls += "tune.settings"
                tuneEntered.complete(Unit)
                releaseTune.await()
                calls += "tune.submit"
            }
        }
        tuneEntered.await()
        val stop = async { gate.run { calls += "stop.submit" } }
        yield()
        assertEquals(listOf("tune.settings"), calls)

        releaseTune.complete(Unit)
        tune.await()
        stop.await()
        assertEquals(listOf("tune.settings", "tune.submit", "stop.submit"), calls)
    }
}
