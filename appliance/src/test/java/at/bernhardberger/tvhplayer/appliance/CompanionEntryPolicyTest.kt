package at.bernhardberger.tvhplayer.appliance

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionEntryPolicyTest {
    @Test
    fun onlyFreshMainHomeDeliveryOpensThePlayer() {
        for (main in listOf(false, true)) {
            for (home in listOf(false, true)) {
                for (saved in listOf(false, true)) {
                    assertEquals("main=$main home=$home saved=$saved", main && home && !saved,
                        shouldOpenPlayerForHome(main, home, saved))
                }
            }
        }
    }

    @Test
    fun rootBackIsConsumedOnlyWhenCompanionIsDefaultHome() {
        assertEquals(true, shouldConsumeBack(isDefaultHome = true))
        assertEquals(false, shouldConsumeBack(isDefaultHome = false))
    }
}
