package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleTvCapabilityPolicyTest {
    @Test
    fun standardProfileOwnsUnrestrictedProductAccess() {
        SimpleTvCapability.entries.forEach { capability ->
            assertTrue(ProductProfile.Standard.allows(capability))
        }
        SimpleTvRoute.entries.forEach { route ->
            assertTrue(ProductProfile.Standard.allowsRoute(route))
        }
    }

    @Test
    fun applianceProfileOwnsPlayerOnlyProductAccess() {
        val profile = ProductProfile.Appliance(timeshiftAllowed = true)

        assertTrue(profile.allows(SimpleTvCapability.LIVE_TV))
        assertTrue(profile.allows(SimpleTvCapability.UNLOCK))
        assertTrue(profile.allows(SimpleTvCapability.TIMESHIFT))
        assertFalse(profile.allows(SimpleTvCapability.CHANNEL_LIST))
        assertFalse(profile.allows(SimpleTvCapability.EPG))
        assertFalse(profile.allows(SimpleTvCapability.RECORDINGS))
        assertFalse(profile.allows(SimpleTvCapability.STOP))
        assertFalse(profile.allows(SimpleTvCapability.SETTINGS))
        assertFalse(profile.allows(SimpleTvCapability.APP_EXIT))
        assertFalse(profile.allows(SimpleTvCapability.PLAYER_CLOSE))
        assertFalse(profile.allows(SimpleTvCapability.FULL_PLAYBACK_OPTIONS))
    }

    @Test
    fun startupProfileRequiresConfiguredServerAndPersistedEnablement() {
        val enabled = SimpleTvSettings(enabled = true, timeshift = true)

        assertEquals(
            ProductProfile.Appliance(timeshiftAllowed = true),
            startupProductProfile(serverConfigured = true, settings = enabled),
        )
        assertEquals(
            ProductProfile.Standard,
            startupProductProfile(serverConfigured = false, settings = enabled),
        )
        assertEquals(
            ProductProfile.Standard,
            startupProductProfile(serverConfigured = true, settings = SimpleTvSettings()),
        )
    }

    @Test
    fun routeGuardRejectsDisabledDeepLinks() {
        val profile = ProductProfile.Appliance(timeshiftAllowed = false)

        assertTrue(profile.allowsRoute(SimpleTvRoute.PLAYER))
        assertTrue(profile.allowsRoute(SimpleTvRoute.UNLOCK))
        assertFalse(profile.allowsRoute(SimpleTvRoute.CHANNELS))
        assertFalse(profile.allowsRoute(SimpleTvRoute.EPG))
        assertFalse(profile.allowsRoute(SimpleTvRoute.RECORDINGS))
        assertFalse(profile.allowsRoute(SimpleTvRoute.RECORDING_PLAYER))
        assertFalse(profile.allowsRoute(SimpleTvRoute.SETTINGS))
    }

    @Test
    fun restrictedRouteGuardRedirectsToLiveAndSerializesActiveRecordingTeardown() {
        val active = ProductProfile.Appliance(timeshiftAllowed = false)
        val inactive = ProductProfile.Standard

        assertEquals(
            SimpleTvRouteGuardAction.ALLOW,
            simpleTvRouteGuardAction(inactive, SimpleTvRoute.RECORDING_PLAYER, recordingActive = true),
        )
        assertEquals(
            SimpleTvRouteGuardAction.ALLOW,
            simpleTvRouteGuardAction(active, SimpleTvRoute.PLAYER, recordingActive = true),
        )
        assertEquals(
            SimpleTvRouteGuardAction.REDIRECT_TO_LIVE,
            simpleTvRouteGuardAction(active, SimpleTvRoute.RECORDINGS, recordingActive = false),
        )
        assertEquals(
            SimpleTvRouteGuardAction.STOP_RECORDING_AND_REDIRECT_TO_LIVE,
            simpleTvRouteGuardAction(
                active,
                SimpleTvRoute.RECORDING_PLAYER,
                recordingActive = true,
            ),
        )
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
