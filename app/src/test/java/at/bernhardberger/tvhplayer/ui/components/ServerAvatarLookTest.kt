package at.bernhardberger.tvhplayer.ui.components

import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvhplayer.ui.TvServerAvatarColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAvatarLookTest {
    @Test
    fun theSameBoxGivesTheSameColorWhateverTheAccount() {
        val anna = serverAvatarLook("tvheadend.home.arpa", 9982, "Living room", "anna")
        val kids = serverAvatarLook(" TVHeadend.Home.Arpa ", 9982, null, "kids")
        val anonymous = serverAvatarLook("tvheadend.home.arpa", 9982, null, null)

        assertEquals(anna.colorIndex, kids.colorIndex)
        assertEquals(anna.colorIndex, anonymous.colorIndex)
        assertTrue(anna.colorIndex in TvServerAvatarColors.indices)
    }

    @Test
    fun colorsSpreadAcrossBoxesAndStayInRange() {
        val indices = (1..40).map { serverAvatarLook("192.168.1.$it", 9982, null, null).colorIndex }

        assertTrue(indices.all { it in TvServerAvatarColors.indices })
        assertTrue("expected several of the six colors", indices.toSet().size > 3)
        assertEquals(6, TvServerAvatarColors.size)
    }

    @Test
    fun aDifferentPortIsADifferentBox() {
        val ports = (9982..9991).map { serverAvatarLook("tvheadend.home.arpa", it, null, null).colorIndex }

        assertNotEquals(1, ports.toSet().size)
    }

    @Test
    fun theLetterComesFromTheLabelElseTheUserName() {
        assertEquals("L", serverAvatarLook("h", 9982, "living room", "anna").letter)
        assertEquals("A", serverAvatarLook("h", 9982, null, "anna").letter)
        assertEquals("A", serverAvatarLook("h", 9982, "  ", "anna").letter)
        assertEquals("K", serverAvatarLook("h", 9982, "  (Kids)", "anna").letter)
        assertEquals("2", serverAvatarLook("h", 9982, "2nd floor", "anna").letter)
        assertEquals("Ö", serverAvatarLook("h", 9982, "östlich", "anna").letter)
    }

    @Test
    fun aProfileWithoutSignInShowsTheServerGlyph() {
        assertNull(serverAvatarLook("h", 9982, "Cabin", null).letter)
        assertNull(serverAvatarLook("h", 9982, "Cabin", " ").letter)

        val anonymous = ServerProfileSummary.create(
            ServerProfileId.generate(), "Cabin", "cabin.home.arpa", 9982,
            ServerProfileAuthenticationMode.ANONYMOUS,
        )
        assertNull(serverAvatarLook(anonymous).letter)

        val signedIn = ServerProfileSummary.create(
            ServerProfileId.generate(), null, "cabin.home.arpa", 9982,
            ServerProfileAuthenticationMode.PASSWORD, username = "anna", credentialsAvailable = false,
        )
        assertEquals("A", serverAvatarLook(signedIn).letter)
        assertEquals(serverAvatarLook(anonymous).colorIndex, serverAvatarLook(signedIn).colorIndex)
    }
}
