package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.AutorecRule
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvhplayer.core.automaticRecordingList
import at.bernhardberger.tvhplayer.ui.screens.recordings.ScheduleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleStateTest {
    @Test fun acceptedMutationRunsAfterItsScopeIsCancelledBeforeDispatchAndReleasesTheQueue() = runTest {
        val item = automaticRecordingList(automaticRecordingObservation(autorecRules = listOf(
            AutorecRule.create(AutorecRuleId("ridge"), title = automaticRecordingSeries.pattern.words),
        ))).items.single()
        val state = ScheduleState()
        val firstScope = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
        var firstRuns = 0
        state.launchRuleMutation(firstScope, item) { firstRuns++ }
        firstScope.cancel()
        runCurrent()
        assertEquals("An accepted mutation survives cancellation before its first dispatch", 1, firstRuns)

        val nextScope = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
        var nextRuns = 0
        try {
            state.launchRuleMutation(nextScope, item) { nextRuns++ }
            runCurrent()
            assertEquals("The completed loop releases the recording's queue", 1, nextRuns)
            assertEquals(1, firstRuns)
        } finally {
            nextScope.cancel()
        }
    }
}
