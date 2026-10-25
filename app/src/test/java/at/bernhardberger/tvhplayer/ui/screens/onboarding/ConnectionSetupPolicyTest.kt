package at.bernhardberger.tvhplayer.ui.screens.onboarding

import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionSetupPolicyTest {
    @Test
    fun backWalksTheStepsAndLeavesTheFlowOnlyFromWelcome() {
        for (choice in ServerChoice.entries) {
            assertNull(SetupStep.WELCOME.previous(choice))
            assertEquals(SetupStep.WELCOME, SetupStep.FIND.previous(choice))
            for (step in listOf(SetupStep.CONNECTING, SetupStep.FAILED, SetupStep.DONE)) {
                assertEquals(SetupStep.SIGN_IN, step.previous(choice))
                assertEquals(SetupStep.SIGN_IN, step.resumable())
            }
        }
    }

    @Test
    fun backReturnsToWhereTheServerWasChosen() {
        assertEquals(SetupStep.WELCOME, SetupStep.ADDRESS.previous(ServerChoice.TYPED))
        assertEquals(SetupStep.ADDRESS, SetupStep.SIGN_IN.previous(ServerChoice.TYPED))
        assertEquals(SetupStep.FIND, SetupStep.ADDRESS.previous(ServerChoice.TYPED_AFTER_SEARCH))
        assertEquals(SetupStep.ADDRESS, SetupStep.SIGN_IN.previous(ServerChoice.TYPED_AFTER_SEARCH))
        assertEquals("A found server skips the address", SetupStep.FIND, SetupStep.SIGN_IN.previous(ServerChoice.FOUND))
        assertEquals(SetupStep.FIND, SetupStep.FIND.resumable())
    }

    @Test
    fun eachSessionFailureGetsItsOwnOutcome() {
        val outcomes = mapOf(
            SessionFailure.SERVER_UNREACHABLE to SetupFailure.UNREACHABLE,
            SessionFailure.NETWORK_UNAVAILABLE to SetupFailure.UNREACHABLE,
            SessionFailure.TRANSPORT_UNAVAILABLE to SetupFailure.UNREACHABLE,
            SessionFailure.AUTHENTICATION_REJECTED to SetupFailure.SIGN_IN_REJECTED,
            SessionFailure.INCOMPATIBLE_SERVER to SetupFailure.NOT_TVHEADEND,
            SessionFailure.SERVER_UPGRADE_REQUIRED to SetupFailure.VERSION,
            SessionFailure.synchronizationFailed(TvheadendFailure.ACCESS_DENIED) to SetupFailure.NO_PERMISSION,
            SessionFailure.synchronizationFailed(TvheadendFailure.TIMEOUT) to SetupFailure.TIMEOUT,
            SessionFailure.synchronizationFailed(TvheadendFailure.MALFORMED_RESPONSE) to SetupFailure.OTHER,
            SessionFailure.UNEXPECTED_FAILURE to SetupFailure.OTHER,
        )
        outcomes.forEach { (failure, outcome) -> assertEquals(failure.kind.toString(), outcome, failure.toSetupFailure()) }
    }

    @Test
    fun theLikelyFixComesFirstAndOnlyAnUnreachableServerCanBeSavedAnyway() {
        val first = SetupFailure.entries.associateWith { it.actions.first() }
        assertEquals(SetupAction.TRY_AGAIN, first[SetupFailure.UNREACHABLE])
        assertEquals(SetupAction.CHANGE_ADDRESS, first[SetupFailure.NOT_TVHEADEND])
        assertEquals(SetupAction.CHANGE_SIGN_IN, first[SetupFailure.SIGN_IN_REJECTED])
        assertEquals(SetupAction.CHANGE_SIGN_IN, first[SetupFailure.NO_PERMISSION])
        assertEquals(SetupAction.TRY_AGAIN, first[SetupFailure.VERSION])
        assertEquals(
            listOf(SetupFailure.UNREACHABLE),
            SetupFailure.entries.filter { SetupAction.CONTINUE_ANYWAY in it.actions },
        )
    }
}
