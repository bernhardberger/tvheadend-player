package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveInfoRecordingPolicyTest {
    @Test
    fun targetFactoryRetainsTheLegacyJvmEntryPoint() {
        val event = event(id = 42)
        val method = Class.forName(
            "at.bernhardberger.tvhplayer.core.LiveInfoRecordingPolicyKt"
        ).getDeclaredMethod(
            "programmeRecordingTarget",
            EpgEventEntry::class.java,
        )

        assertEquals(ProgrammeRecordingTarget.from(event), method.invoke(null, event))
    }

    @Test
    fun confirmationDispatchesTheCapturedTargetExactlyOnce() {
        val event = event(id = 42)
        val target = event.programmeRecordingTarget()
        val confirming = LiveInfoRecordingState.Confirming(target)

        assertEquals(
            LiveInfoRecordingDecision.Dispatch(target),
            liveInfoRecordingDecision(confirming, event, actionEligible = true),
        )

        val dispatching = LiveInfoRecordingState.Dispatching(target)
        assertEquals(
            LiveInfoRecordingDecision.Ignore,
            liveInfoRecordingDecision(dispatching, event, actionEligible = true),
        )
        assertEquals(
            LiveInfoRecordingDecision.Ignore,
            liveInfoRecordingDecision(
                LiveInfoRecordingState.Succeeded(target),
                event,
                actionEligible = true,
            ),
        )
    }

    @Test
    fun replacementOrDisappearanceInvalidatesBeforeDispatch() {
        val event = event(id = 42)
        val confirming = LiveInfoRecordingState.Confirming(event.programmeRecordingTarget())

        listOf(
            null,
            event(id = 43),
            event(id = 42, channelId = 8),
            event(id = 42, start = event.start + 1L),
            event(id = 42, stop = event.stop + 1L),
            event(id = 42, title = "Replacement"),
        ).forEach { current ->
            assertEquals(
                current.toString(),
                LiveInfoRecordingDecision.Invalidate,
                liveInfoRecordingDecision(confirming, current, actionEligible = true),
            )
        }
        assertEquals(
            LiveInfoRecordingDecision.Invalidate,
            liveInfoRecordingDecision(confirming, event, actionEligible = false),
        )
    }

    @Test
    fun failedRetryRevalidatesBeforeAnotherDispatch() {
        val event = event(id = 42)
        val target = event.programmeRecordingTarget()
        val failed = LiveInfoRecordingState.Failed(
            target = target,
            reason = DvrActionFailure.CONNECTION,
        )

        assertEquals(
            LiveInfoRecordingDecision.Dispatch(target),
            liveInfoRecordingDecision(failed, event, actionEligible = true),
        )
        assertEquals(
            LiveInfoRecordingDecision.Invalidate,
            liveInfoRecordingDecision(failed, event(id = 43), actionEligible = true),
        )
    }

    @Test
    fun completionRemainsBoundToTheDispatchedTarget() {
        val target = event(id = 42, title = "Captured programme").programmeRecordingTarget()
        val dispatching = LiveInfoRecordingState.Dispatching(target)

        assertEquals(
            LiveInfoRecordingState.Succeeded(target),
            liveInfoRecordingCompleted(
                dispatching,
                DvrActionResult.Accepted(entryId = 9),
            ),
        )
        assertEquals(
            LiveInfoRecordingState.Failed(target, DvrActionFailure.CONFLICT),
            liveInfoRecordingCompleted(
                dispatching,
                DvrActionResult.Failed(DvrActionFailure.CONFLICT),
            ),
        )
        assertEquals(
            LiveInfoRecordingCompletion(
                state = LiveInfoRecordingState.Succeeded(target),
                showResult = true,
            ),
            liveInfoRecordingCompletion(
                state = dispatching,
                result = DvrActionResult.Accepted(entryId = 9),
                infoOpen = true,
            ),
        )
        assertEquals(
            LiveInfoRecordingCompletion(
                state = LiveInfoRecordingState.Succeeded(target),
                showResult = false,
            ),
            liveInfoRecordingCompletion(
                state = dispatching,
                result = DvrActionResult.Accepted(entryId = 9),
                infoOpen = false,
            ),
        )
    }

    @Test
    fun dismissalCancelsOnlyAnUndispatchedConfirmation() {
        val target = event(id = 42).programmeRecordingTarget()

        assertEquals(
            LiveInfoRecordingState.Idle,
            liveInfoRecordingDismissed(LiveInfoRecordingState.Confirming(target)),
        )
        assertEquals(
            LiveInfoRecordingState.Dispatching(target),
            liveInfoRecordingDismissed(LiveInfoRecordingState.Dispatching(target)),
        )
        assertEquals(
            LiveInfoRecordingState.Succeeded(target),
            liveInfoRecordingDismissed(LiveInfoRecordingState.Succeeded(target)),
        )
        assertEquals(
            LiveInfoRecordingState.Idle,
            liveInfoRecordingDismissed(
                LiveInfoRecordingState.Failed(target, DvrActionFailure.REJECTED)
            ),
        )
    }

    private fun event(
        id: Int,
        channelId: Int = 7,
        start: Long = 1_000L,
        stop: Long = 2_000L,
        title: String = "Programme $id",
    ) = EpgEventEntry(
        eventId = id,
        channelId = channelId,
        start = start,
        stop = stop,
        title = title,
    )
}
