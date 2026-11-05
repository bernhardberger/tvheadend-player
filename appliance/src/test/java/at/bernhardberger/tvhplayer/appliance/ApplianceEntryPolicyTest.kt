package at.bernhardberger.tvhplayer.appliance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplianceEntryPolicyTest {
    @Test
    fun destinationContractLiteralsArePinned() {
        assertEquals("at.bernhardberger.tvhplayer.extra.APPLIANCE_DESTINATION", ApplianceEntryPolicy.EXTRA_APPLIANCE_DESTINATION)
        assertEquals("live", ApplianceEntryPolicy.DESTINATION_LIVE)
        assertEquals("guide", ApplianceEntryPolicy.DESTINATION_GUIDE)
    }

    @Test
    fun playerEntryContractLiteralsArePinned() {
        assertEquals("at.bernhardberger.tvhplayer.action.APPLIANCE_ENTRY", ApplianceEntryPolicy.ACTION_APPLIANCE_ENTRY)
        assertEquals("at.bernhardberger.tvhplayer.ui.ApplianceEntryActivity", ApplianceEntryPolicy.ENTRY_ALIAS_CLASS_NAME)
    }

    @Test
    fun initialGuideDown_launchesAndConsumes() {
        assertEquals(
            ApplianceKeyDecision.LAUNCH_AND_CONSUME,
            ApplianceEntryPolicy.keyEvent(
                isApplianceEntryKey = true,
                isDown = true,
                repeatCount = 0,
            ),
        )
    }

    @Test
    fun guideRepeatAndUp_consumeWithoutLaunchingAgain() {
        assertEquals(
            ApplianceKeyDecision.CONSUME,
            ApplianceEntryPolicy.keyEvent(
                isApplianceEntryKey = true,
                isDown = true,
                repeatCount = 1,
            ),
        )
        assertEquals(
            ApplianceKeyDecision.CONSUME,
            ApplianceEntryPolicy.keyEvent(
                isApplianceEntryKey = true,
                isDown = false,
                repeatCount = 0,
            ),
        )
    }

    @Test
    fun everyNonGuideKey_passesThrough() {
        assertEquals(
            ApplianceKeyDecision.PASS_THROUGH,
            ApplianceEntryPolicy.keyEvent(
                isApplianceEntryKey = false,
                isDown = true,
                repeatCount = 0,
            ),
        )
        assertEquals(
            ApplianceKeyDecision.PASS_THROUGH,
            ApplianceEntryPolicy.keyEvent(
                isApplianceEntryKey = false,
                isDown = false,
                repeatCount = 0,
            ),
        )
    }

    @Test
    fun androidGuideAndCapturedTclTvCodes_areApplianceEntryKeys() {
        val functions = CompanionFunctions()
        assertEquals(CompanionFunction.GUIDE, functions.owner(172))
        assertEquals(CompanionFunction.GUIDE, functions.owner(ApplianceEntryPolicy.TCL_GUIDE_KEY_CODE))
        assertEquals(null, functions.owner(4))
    }

    @Test
    fun applianceAccessibilityDoesNotClaimMediaKeys() {
        for (key in listOf(126, 127, 85, 87, 88)) {
            assertEquals(null, CompanionFunctions().owner(key))
        }
    }

    @Test
    fun launchGate_coalescesAdjacentBootAndWakeSignals() {
        val gate = ApplianceEntryLaunchGate(minimumIntervalMillis = 1_500)

        assertTrue(gate.shouldLaunch(nowMillis = 10_000))
        assertFalse(gate.shouldLaunch(nowMillis = 10_500))
        assertTrue(gate.shouldLaunch(nowMillis = 11_500))
    }

    @Test
    fun launchGate_wakeLiveThenGuideAcceptsBothWithinTheWindow() {
        val gate = ApplianceEntryLaunchGate(minimumIntervalMillis = 1_500)
        assertTrue(gate.shouldLaunch(10_000))
        assertTrue(gate.shouldLaunch(10_100, ApplianceEntryPolicy.DESTINATION_GUIDE, explicit = true))
    }

    @Test
    fun launchGate_guideThenLiveAcceptsBothWithinTheWindow() {
        val gate = ApplianceEntryLaunchGate(minimumIntervalMillis = 1_500)
        assertTrue(gate.shouldLaunch(10_000, ApplianceEntryPolicy.DESTINATION_GUIDE, explicit = true))
        assertTrue(gate.shouldLaunch(10_100, ApplianceEntryPolicy.DESTINATION_LIVE, explicit = true))
    }

    @Test
    fun launchGate_sameDestinationButtonBurstIsDeduplicated() {
        val gate = ApplianceEntryLaunchGate(minimumIntervalMillis = 1_500)
        assertTrue(gate.shouldLaunch(10_000, ApplianceEntryPolicy.DESTINATION_GUIDE, explicit = true))
        assertFalse(gate.shouldLaunch(10_100, ApplianceEntryPolicy.DESTINATION_GUIDE, explicit = true))
    }

    @Test
    fun launchGate_explicitLiveIsNotSwallowedByAutomaticLive() {
        val gate = ApplianceEntryLaunchGate(minimumIntervalMillis = 1_500)
        assertTrue(gate.shouldLaunch(10_000))
        assertTrue(gate.shouldLaunch(10_100, explicit = true))
        assertFalse(gate.shouldLaunch(10_200, explicit = true))
        assertFalse(gate.shouldLaunch(10_300))
    }

    @Test
    fun launchGate_automaticLiveCannotOverrideExplicitGuideWithinTheWindow() {
        val gate = ApplianceEntryLaunchGate(minimumIntervalMillis = 1_500)
        assertTrue(gate.shouldLaunch(10_000, ApplianceEntryPolicy.DESTINATION_GUIDE, explicit = true))
        assertFalse(gate.shouldLaunch(10_100))
        assertTrue(gate.shouldLaunch(11_500))
    }
}
