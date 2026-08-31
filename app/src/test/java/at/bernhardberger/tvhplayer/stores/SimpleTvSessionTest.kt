package at.bernhardberger.tvhplayer.stores

import at.bernhardberger.tvhplayer.core.ProductProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class SimpleTvSessionTest {
    @Test
    fun applianceEntryAndExitOnlyChangeCurrentProductProfile() {
        val session = SimpleTvSession()
        val appliance = ProductProfile.Appliance(timeshiftAllowed = true)

        assertEquals(ProductProfile.Standard, session.profile.value)

        session.enter(appliance)
        assertEquals(appliance, session.profile.value)

        session.exit()
        assertEquals(ProductProfile.Standard, session.profile.value)
    }
}
