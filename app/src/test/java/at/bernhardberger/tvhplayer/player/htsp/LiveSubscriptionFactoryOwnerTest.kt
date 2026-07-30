package at.bernhardberger.tvhplayer.player.htsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LiveSubscriptionFactoryOwnerTest {
    @Test
    fun retirementReturnsEveryCreatedSourceAndFencesLateLoaderCreation() {
        val owner = LiveSubscriptionFactoryOwner<String>()
        owner.create { "first" }
        owner.create { "second" }

        assertEquals("second", owner.current())
        assertEquals(listOf("first", "second"), owner.retire())
        owner.releaseSettled("first")
        assertEquals(listOf("second"), owner.retire())
        owner.releaseSettled("second")
        assertEquals(emptyList<String>(), owner.retire())
        assertThrows(IllegalStateException::class.java) { owner.create { "late" } }
    }
}
