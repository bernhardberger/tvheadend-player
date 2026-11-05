package at.bernhardberger.tvhplayer.appliance

import org.junit.Assert.*
import org.junit.Test

class CompanionFunctionsTest {
    @Test fun assigningItsSingleDefaultKeyNormalizesToDefaultButDoesNotExpandGuide() {
        val functions = CompanionFunctions(liveTv = ButtonBinding.NotSet)
            .assign(CompanionFunction.GUIDE, ButtonBinding.Custom(170)).functions
        val restored = functions.assign(CompanionFunction.LIVE_TV, ButtonBinding.Custom(170))
        assertEquals(ButtonBinding.Default, restored.functions.liveTv)
        assertEquals(ButtonBinding.NotSet, restored.functions.guide)
        assertEquals(listOf(MovedButton(CompanionFunction.GUIDE, 170)), restored.moved)
        for (key in listOf(172, 4001)) {
            val guide = restored.functions.assign(CompanionFunction.GUIDE, ButtonBinding.Custom(key)).functions
            assertEquals(ButtonBinding.Custom(key), guide.guide)
            assertEquals(setOf(key), guide.keys(CompanionFunction.GUIDE))
        }
    }

    @Test fun defaultsCustomAndRemovedKeysRouteOnlyToTheirOwner() {
        val defaults = CompanionFunctions()
        assertTrue(defaults.startup)
        assertEquals(CompanionFunction.LIVE_TV, defaults.owner(170))
        assertEquals(CompanionFunction.GUIDE, defaults.owner(172))
        assertEquals(CompanionFunction.GUIDE, defaults.owner(4001))
        assertNull(defaults.owner(85))
        val custom = defaults.assign(CompanionFunction.GUIDE, ButtonBinding.Custom(85)).functions
            .assign(CompanionFunction.LIVE_TV, ButtonBinding.NotSet).functions
        assertNull(custom.owner(170))
        assertNull(custom.owner(172))
        assertNull(custom.owner(4001))
        assertEquals(CompanionFunction.GUIDE, custom.owner(85))
        for (code in listOf(170, 172, 4001, 85, 4)) {
            val assigned = custom.owner(code) != null
            assertEquals(if (assigned) ApplianceKeyDecision.LAUNCH_AND_CONSUME else ApplianceKeyDecision.PASS_THROUGH,
                ApplianceEntryPolicy.keyEvent(assigned, true, 0))
            assertEquals(if (assigned) ApplianceKeyDecision.CONSUME else ApplianceKeyDecision.PASS_THROUGH,
                ApplianceEntryPolicy.keyEvent(assigned, true, 1))
            assertEquals(if (assigned) ApplianceKeyDecision.CONSUME else ApplianceKeyDecision.PASS_THROUGH,
                ApplianceEntryPolicy.keyEvent(assigned, false, 0))
        }
    }

    @Test fun captureMovesOneDefaultKeyAndRestoringDefaultMovesItBack() {
        val moved = CompanionFunctions().assign(CompanionFunction.LIVE_TV, ButtonBinding.Custom(172))
        assertEquals(listOf(MovedButton(CompanionFunction.GUIDE, 172)), moved.moved)
        assertEquals(ButtonBinding.Custom(4001), moved.functions.guide)
        assertEquals("Button 4001", moved.functions.buttonLabel(CompanionFunction.GUIDE))
        val restored = moved.functions.assign(CompanionFunction.GUIDE, ButtonBinding.Default)
        assertEquals(listOf(MovedButton(CompanionFunction.LIVE_TV, 172)), restored.moved)
        assertEquals(ButtonBinding.NotSet, restored.functions.liveTv)
        assertEquals(setOf(172, 4001), restored.functions.keys(CompanionFunction.GUIDE))
        assertEquals("Guide button", restored.functions.buttonLabel(CompanionFunction.GUIDE))
    }

    @Test fun movingTheLastCustomKeyTurnsThePreviousFunctionOff() {
        val change = CompanionFunctions().assign(CompanionFunction.GUIDE, ButtonBinding.Custom(170))
        assertEquals(ButtonBinding.NotSet, change.functions.liveTv)
        assertEquals(listOf(MovedButton(CompanionFunction.LIVE_TV, 170)), change.moved)
    }

    @Test fun smallNamesTableDoesNotExposeAndroidOrVendorNames() {
        val names = mapOf(170 to "TV button", 172 to "Guide button", 183 to "Red button", 184 to "Green button",
            185 to "Yellow button", 186 to "Blue button", 165 to "Info button", 166 to "Channel up button",
            167 to "Channel down button", 85 to "Play/pause button", 178 to "Input button", 82 to "Menu button",
            176 to "Settings button", 173 to "Recordings button", 4001 to "Button 4001", 174 to "Button 174")
        names.forEach { (code, name) -> assertEquals(name, buttonName(code)) }
    }

    @Test fun bodyUsesOnlyTheEnabledWaysAndOnlyMentionsHomeWhenDefault() {
        assertEquals("This TV opens Tvheadend Player at startup, on HOME and with the TV and Guide buttons.",
            CompanionFunctions().allSetBody(true))
        assertEquals("This TV opens Tvheadend Player at startup and with the TV and Guide buttons.",
            CompanionFunctions().allSetBody(false))
        val noButtons = CompanionFunctions(false, ButtonBinding.NotSet, ButtonBinding.NotSet)
        assertEquals("Nothing is set to open Tvheadend Player.", noButtons.allSetBody(false))
        assertEquals("This TV opens Tvheadend Player on HOME.", noButtons.allSetBody(true))
        assertEquals("This TV opens Tvheadend Player on HOME and with Button 4001.",
            noButtons.copy(guide = ButtonBinding.Custom(4001)).allSetBody(true))
        assertEquals("This TV opens Tvheadend Player at startup.", noButtons.copy(startup = true).allSetBody(false))
    }
}
