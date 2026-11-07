package at.bernhardberger.tvhplayer.appliance

import at.bernhardberger.tvhplayer.appliance.update.UpdateResult
import at.bernhardberger.tvhplayer.appliance.update.UpdateScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionSetupTest {
    @Test fun blockedIsEnabledButNotConnectedAndRemainsAnUnresolvedAccessibilityRow() {
        assertEquals(CompanionServiceStatus.OFF, companionServiceStatus(false, false))
        assertEquals(CompanionServiceStatus.OFF, companionServiceStatus(false, true))
        assertEquals(CompanionServiceStatus.ON, companionServiceStatus(true, true))
        assertEquals(CompanionServiceStatus.BLOCKED, companionServiceStatus(true, false))
        val blocked = ready.copy(accessibilityStatus = CompanionServiceStatus.BLOCKED)
        assertEquals(listOf(CompanionRow.ACCESSIBILITY), blocked.unresolved)
        assertEquals(CompanionRow.ACCESSIBILITY, blocked.initialFocus)
        assertEquals(CompanionBody.BLOCKED, blocked.body)
        assertEquals(CompanionOverall.NeedsFix(1, CompanionRow.ACCESSIBILITY), blocked.overall)
    }
    private val ready = CompanionScreenState(
        playerVersion = "0.4.2",
        accessibilityStatus = CompanionServiceStatus.ON,
        installAllowed = true,
        defaultHome = true,
        hasSystemHome = true,
        update = UpdateScreenState(),
    )

    @Test fun everythingInOrderIsAllSetAndFocusesThePlayerRow() {
        assertEquals(CompanionOverall.AllSet, ready.overall)
        assertEquals(CompanionRow.PLAYER, ready.initialFocus)
    }

    @Test fun missingPlayerOutranksEveryOtherState() {
        val state = ready.copy(playerVersion = null, accessibilityStatus = CompanionServiceStatus.OFF,
            update = UpdateScreenState(result = UpdateResult.INSTALLING))
        assertEquals(CompanionOverall.PlayerMissing, state.overall)
        assertEquals(CompanionRow.PLAYER, state.initialFocus)
    }

    @Test fun runningInstallOutranksThingsToFix() {
        val state = ready.copy(accessibilityStatus = CompanionServiceStatus.OFF,
            update = UpdateScreenState(result = UpdateResult.INSTALLING))
        assertEquals(CompanionOverall.Updating, state.overall)
    }

    @Test fun countsOnlyThePlayerAccessibilityAndUnknownApps() {
        val state = ready.copy(accessibilityStatus = CompanionServiceStatus.OFF, installAllowed = false, defaultHome = false)
        assertEquals(CompanionOverall.NeedsFix(2, CompanionRow.ACCESSIBILITY), state.overall)
        assertEquals(CompanionRow.ACCESSIBILITY, state.initialFocus)
    }

    @Test fun anUnsetHomeAppIsNotAThingToFix() {
        val state = ready.copy(defaultHome = false)
        assertEquals(CompanionOverall.AllSet, state.overall)
    }

    @Test fun unknownAppsIsTheFirstFixWhenAccessibilityIsOn() {
        val state = ready.copy(installAllowed = false)
        assertEquals(CompanionOverall.NeedsFix(1, CompanionRow.UNKNOWN_APPS), state.overall)
        assertEquals(CompanionRow.UNKNOWN_APPS, state.initialFocus)
    }

    @Test fun bodyNamesTheSingleOpenItemAndStaysGenericForSeveral() {
        assertEquals(CompanionBody.ALL_SET, ready.body)
        assertEquals(CompanionBody.PLAYER_MISSING, ready.copy(playerVersion = null).body)
        assertEquals(CompanionBody.ACCESSIBILITY_OFF, ready.copy(accessibilityStatus = CompanionServiceStatus.OFF).body)
        assertEquals(CompanionBody.INSTALL_UNKNOWN_OFF, ready.copy(installAllowed = false).body)
        assertEquals(CompanionBody.SEVERAL_OPEN,
            ready.copy(accessibilityStatus = CompanionServiceStatus.OFF, installAllowed = false).body)
        assertEquals(CompanionBody.UPDATING,
            ready.copy(installAllowed = false, update = UpdateScreenState(result = UpdateResult.INSTALLING)).body)
    }

    @Test fun failedResultsMapToTheirRowTitleAndOthersToNone() {
        fun failure(result: UpdateResult) = ready.copy(update = UpdateScreenState(result = result)).updateFailure
        listOf(UpdateResult.NO_NETWORK, UpdateResult.RATE_LIMITED, UpdateResult.RELEASE_PROBLEM).forEach {
            assertEquals(it.name, UpdateFailure.COULD_NOT_CHECK, failure(it))
        }
        listOf(
            UpdateResult.HASH_MISMATCH, UpdateResult.WRONG_PACKAGE, UpdateResult.SIGNATURE_MISMATCH,
            UpdateResult.INSTALL_PERMISSION, UpdateResult.INSTALLER_FAILURE, UpdateResult.STORAGE_FAILURE,
        ).forEach { assertEquals(it.name, UpdateFailure.UPDATE_FAILED, failure(it)) }
        listOf(
            UpdateResult.NEVER, UpdateResult.CHECKING, UpdateResult.UP_TO_DATE, UpdateResult.NOT_NEWER,
            UpdateResult.WAITING_STANDBY, UpdateResult.INSTALLING, UpdateResult.PENDING_CONFIRMATION,
            UpdateResult.SUCCESS,
        ).forEach { assertEquals(it.name, null, failure(it)) }
    }

    @Test fun googleTvHomeNeedsTheDefaultHomeAndASystemHome() {
        assertTrue(ready.showSystemHome)
        assertFalse(ready.copy(defaultHome = false).showSystemHome)
        assertFalse(ready.copy(hasSystemHome = false).showSystemHome)
    }

    @Test fun updateModeFollowsTheRecordedResult() {
        fun mode(result: UpdateResult, latest: String = "") =
            ready.copy(update = UpdateScreenState(result = result, latestVersion = latest)).updateMode
        assertEquals(UpdateRowMode.CHECK, mode(UpdateResult.NEVER))
        assertEquals(UpdateRowMode.CHECK, mode(UpdateResult.UP_TO_DATE))
        assertEquals(UpdateRowMode.CHECK, mode(UpdateResult.NO_NETWORK))
        assertEquals(UpdateRowMode.CHECKING, mode(UpdateResult.CHECKING))
        assertEquals(UpdateRowMode.INSTALLING, mode(UpdateResult.INSTALLING, "0.5.0"))
        assertEquals(UpdateRowMode.AVAILABLE, mode(UpdateResult.WAITING_STANDBY, "0.5.0"))
        assertEquals(UpdateRowMode.AVAILABLE, mode(UpdateResult.PENDING_CONFIRMATION, "0.5.0"))
        assertEquals(UpdateRowMode.CHECK, mode(UpdateResult.WAITING_STANDBY))
    }

    @Test fun anAvailableOrRunningUpdateTakesFocusWhenNothingNeedsFixing() {
        val available = ready.copy(update = UpdateScreenState(result = UpdateResult.WAITING_STANDBY, latestVersion = "0.5.0"))
        assertEquals(CompanionRow.UPDATE, available.initialFocus)
        val running = ready.copy(update = UpdateScreenState(result = UpdateResult.CHECKING))
        assertEquals(CompanionRow.UPDATE, running.initialFocus)
    }
}
