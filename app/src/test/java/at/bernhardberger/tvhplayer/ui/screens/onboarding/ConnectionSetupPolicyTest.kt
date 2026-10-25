package at.bernhardberger.tvhplayer.ui.screens.onboarding

import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionSetupPolicyTest {
    @Test
    fun bothModesUseThreeScreensAndBackLeavesOnlyFromServer() {
        assertEquals(SetupStep.WELCOME, SetupMode.FIRST_RUN.firstStep)
        assertEquals(SetupStep.ADDRESS, SetupMode.EDIT.firstStep)
        assertNull(SetupStep.WELCOME.stage)
        assertNull(SetupStep.WELCOME.previous())
        assertEquals(SetupStep.WELCOME, SetupStep.ADDRESS.previous())
        assertNull(SetupStep.ADDRESS.previous(SetupMode.EDIT))
        assertEquals(SetupStep.ADDRESS, SetupStep.SIGN_IN.previous())
        assertEquals(SetupStage.SERVER, SetupStep.ADDRESS.stage)
        assertEquals(SetupStage.SIGN_IN, SetupStep.SIGN_IN.stage)
        for (step in listOf(SetupStep.CONNECTING, SetupStep.FAILED, SetupStep.DONE)) {
            assertEquals(SetupStep.SIGN_IN, step.previous())
            assertEquals(SetupStep.SIGN_IN, step.resumable())
            assertEquals(SetupStage.CONNECT, step.stage)
        }
    }

    @Test
    fun eachSessionFailureGetsItsOwnOutcome() {
        val outcomes = mapOf(
            SessionFailure.SERVER_UNREACHABLE to SetupFailure.UNREACHABLE,
            SessionFailure.NETWORK_UNAVAILABLE to SetupFailure.NETWORK,
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
        assertEquals("No network has nothing to change", listOf(SetupAction.TRY_AGAIN), SetupFailure.NETWORK.actions)
        assertEquals(
            listOf(SetupFailure.UNREACHABLE),
            SetupFailure.entries.filter { SetupAction.CONTINUE_ANYWAY in it.actions },
        )
    }

    @Test
    fun editingSavesAnUnreachableServerAnywayInsteadOfContinuing() {
        for (failure in SetupFailure.entries) {
            val actions = failure.actionsFor(SetupMode.EDIT)
            assertEquals(failure.actions.size, actions.size)
            assertFalse(SetupAction.CONTINUE_ANYWAY in actions)
            assertEquals(failure.actions, failure.actionsFor(SetupMode.FIRST_RUN))
        }
        assertEquals(
            listOf(SetupAction.TRY_AGAIN, SetupAction.CHANGE_ADDRESS, SetupAction.SAVE_ANYWAY),
            SetupFailure.UNREACHABLE.actionsFor(SetupMode.EDIT),
        )
    }
}
