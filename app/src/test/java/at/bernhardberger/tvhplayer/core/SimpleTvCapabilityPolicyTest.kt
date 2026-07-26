package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleTvCapabilityPolicyTest {
    @Test
    fun inactiveModeAllowsEveryCapability() {
        SimpleTvCapability.entries.forEach {
            assertTrue(simpleTvProfile(SimpleTvSettings(enabled = true), active = false).allows(it))
        }
    }

    @Test
    fun activeModeIsConfinedToLiveTvAndExit() {
        val profile = simpleTvProfile(SimpleTvSettings(enabled = true), active = true)

        assertTrue(profile.allows(SimpleTvCapability.LIVE_TV))
        assertTrue(profile.allows(SimpleTvCapability.UNLOCK))
        assertFalse(profile.allows(SimpleTvCapability.CHANNEL_LIST))
        assertFalse(profile.allows(SimpleTvCapability.EPG))
        assertFalse(profile.allows(SimpleTvCapability.RECORDINGS))
        assertFalse(profile.allows(SimpleTvCapability.TIMESHIFT))
        assertFalse(profile.allows(SimpleTvCapability.STOP))
        assertFalse(profile.allows(SimpleTvCapability.SETTINGS))
        assertFalse(profile.allows(SimpleTvCapability.APP_EXIT))
    }

    @Test
    fun activeModeOnlyHonorsTimeshiftOption() {
        val profile = simpleTvProfile(
            SimpleTvSettings(
                enabled = true,
                epg = true,
                recordings = true,
                timeshift = true,
                stop = true,
                settings = true,
                appExit = true,
            ),
            active = true,
        )

        assertTrue(profile.allows(SimpleTvCapability.TIMESHIFT))
        assertFalse(profile.allows(SimpleTvCapability.EPG))
        assertFalse(profile.allows(SimpleTvCapability.RECORDINGS))
        assertFalse(profile.allows(SimpleTvCapability.STOP))
        assertFalse(profile.allows(SimpleTvCapability.SETTINGS))
        assertFalse(profile.allows(SimpleTvCapability.APP_EXIT))
    }

    @Test
    fun routeGuardRejectsDisabledDeepLinks() {
        val profile = simpleTvProfile(SimpleTvSettings(enabled = true), active = true)

        assertTrue(profile.allowsRoute(SimpleTvRoute.PLAYER))
        assertTrue(profile.allowsRoute(SimpleTvRoute.UNLOCK))
        assertFalse(profile.allowsRoute(SimpleTvRoute.CHANNELS))
        assertFalse(profile.allowsRoute(SimpleTvRoute.EPG))
        assertFalse(profile.allowsRoute(SimpleTvRoute.RECORDINGS))
        assertFalse(profile.allowsRoute(SimpleTvRoute.RECORDING_PLAYER))
        assertFalse(profile.allowsRoute(SimpleTvRoute.SETTINGS))
    }

    @Test
    fun pinHashIsSaltedAndVerifiable() {
        val firstSalt = ByteArray(16) { it.toByte() }
        val secondSalt = ByteArray(16) { (it + 1).toByte() }
        val first = simpleTvPinHash("1234", firstSalt)
        val second = simpleTvPinHash("1234", secondSalt)

        assertTrue(isValidSimpleTvPin("1234"))
        assertFalse(isValidSimpleTvPin("123"))
        assertFalse(first.contentEquals(second))
        assertTrue(verifySimpleTvPin("1234", firstSalt, first))
        assertFalse(verifySimpleTvPin("9999", firstSalt, first))
    }
}
