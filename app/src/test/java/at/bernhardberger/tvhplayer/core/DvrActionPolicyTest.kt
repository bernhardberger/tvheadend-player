package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DvrActionPolicyTest {
    @Test
    fun serverErrorsAreReducedToDistinctSafeOutcomes() {
        assertEquals(
            DvrActionFailure.PERMISSION_DENIED,
            dvrActionFailure(mapOf("noaccess" to 1)),
        )
        assertEquals(
            DvrActionFailure.CONNECTION_LIMIT,
            dvrActionFailure(mapOf("noaccess" to 1, "connlimit" to 1)),
        )
        assertEquals(
            DvrActionFailure.CONFLICT,
            dvrActionFailure(mapOf("error" to "DVR conflict: no free tuner")),
        )
        assertEquals(
            DvrActionFailure.REJECTED,
            dvrActionFailure(mapOf("error" to "invalid event")),
        )
        assertEquals(null, dvrActionFailure(mapOf("success" to 1)))
    }
}
