package at.bernhardberger.tvhplayer.appliance

import org.junit.Assert.*
import org.junit.Test

class CompanionFunctionsTest {
    @Test fun assigningItsSingleDefaultKeyNormalizesToDefaultButDoesNotExpandLiveTv() {
        val functions = CompanionFunctions(guide = ButtonBinding.NotSet)
            .assign(CompanionFunction.LIVE_TV, ButtonBinding.Custom(172)).functions
        val restored = functions.assign(CompanionFunction.GUIDE, ButtonBinding.Custom(172))
        assertEquals(ButtonBinding.Default, restored.functions.guide)
        assertEquals(ButtonBinding.NotSet, restored.functions.liveTv)
        assertEquals(listOf(MovedButton(CompanionFunction.LIVE_TV, 172)), restored.moved)
        for (key in listOf(170, 4001)) {
            val live = restored.functions.assign(CompanionFunction.LIVE_TV, ButtonBinding.Custom(key)).functions
            assertEquals(ButtonBinding.Custom(key), live.liveTv)
            assertEquals(setOf(key), live.keys(CompanionFunction.LIVE_TV))
        }
    }

    @Test fun defaultBindingsRouteTclTvToLiveAndStandardGuideToGuideWithoutClaimingList() {
        val defaults = CompanionFunctions()
        assertEquals(setOf(170, 4001), defaults.keys(CompanionFunction.LIVE_TV))
        assertEquals(setOf(172), defaults.keys(CompanionFunction.GUIDE))
        assertEquals(CompanionFunction.LIVE_TV, defaults.owner(4001))
        assertEquals(CompanionFunction.GUIDE, defaults.owner(172))
        assertNull(defaults.owner(174))
    }

    @Test fun defaultsCustomAndRemovedKeysRouteOnlyToTheirOwner() {
        val defaults = CompanionFunctions()
        assertTrue(defaults.startup)
        assertEquals(CompanionFunction.LIVE_TV, defaults.owner(170))
        assertEquals(CompanionFunction.GUIDE, defaults.owner(172))
        assertEquals(CompanionFunction.LIVE_TV, defaults.owner(4001))
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
        for ((key, remaining) in listOf(170 to 4001, 4001 to 170)) {
            val moved = CompanionFunctions().assign(CompanionFunction.GUIDE, ButtonBinding.Custom(key))
            assertEquals(listOf(MovedButton(CompanionFunction.LIVE_TV, key)), moved.moved)
            assertEquals(ButtonBinding.Custom(remaining), moved.functions.liveTv)
            assertEquals("TV button ($remaining)", moved.functions.buttonLabel(CompanionFunction.LIVE_TV))
            assertEquals("TV button ($key)", moved.functions.buttonLabel(CompanionFunction.GUIDE))
            val restored = moved.functions.assign(CompanionFunction.LIVE_TV, ButtonBinding.Default)
            assertEquals(listOf(MovedButton(CompanionFunction.GUIDE, key)), restored.moved)
            assertEquals(ButtonBinding.NotSet, restored.functions.guide)
            assertEquals(setOf(170, 4001), restored.functions.keys(CompanionFunction.LIVE_TV))
            assertEquals("TV button", restored.functions.buttonLabel(CompanionFunction.LIVE_TV))
        }
    }

    @Test fun movingTheLastCustomKeyTurnsThePreviousFunctionOff() {
        val change = CompanionFunctions(liveTv = ButtonBinding.Custom(170))
            .assign(CompanionFunction.GUIDE, ButtonBinding.Custom(170))
        assertEquals(ButtonBinding.NotSet, change.functions.liveTv)
        assertEquals(listOf(MovedButton(CompanionFunction.LIVE_TV, 170)), change.moved)
    }

    @Test fun smallNamesTableDoesNotExposeAndroidOrVendorNames() {
        val names = mapOf(170 to "TV button", 172 to "Guide button", 183 to "Red button", 184 to "Green button",
            185 to "Yellow button", 186 to "Blue button", 165 to "Info button", 166 to "Channel up button",
            167 to "Channel down button", 85 to "Play/pause button", 178 to "Input button", 82 to "Menu button",
            176 to "Settings button", 173 to "Recordings button", 4001 to "TV button", 174 to "Button 174")
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
        assertEquals("This TV opens Tvheadend Player on HOME and with TV button.",
            noButtons.copy(guide = ButtonBinding.Custom(4001)).allSetBody(true))
        assertEquals("This TV opens Tvheadend Player at startup.", noButtons.copy(startup = true).allSetBody(false))
    }

    @Test fun twoTvButtonCodesShowTheirCodesOnlyWhereTheNamesWouldMatch() {
        val split = CompanionFunctions().assign(CompanionFunction.GUIDE, ButtonBinding.Custom(4001)).functions
        assertEquals(ButtonBinding.Custom(170), split.liveTv)
        assertEquals("TV button (170)", split.buttonLabel(CompanionFunction.LIVE_TV))
        assertEquals("TV button (4001)", split.buttonLabel(CompanionFunction.GUIDE))
        assertEquals("This TV opens Tvheadend Player at startup and with TV button (170) and TV button (4001).",
            split.allSetBody(false))
        assertEquals("TV button", buttonName(170))
        assertEquals("TV button (4001)", buttonNameWithCode(4001))
        assertEquals("TV button", CompanionFunctions().buttonLabel(CompanionFunction.LIVE_TV))
        assertEquals("Guide button", CompanionFunctions().buttonLabel(CompanionFunction.GUIDE))
    }
}
