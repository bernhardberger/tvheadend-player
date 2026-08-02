package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgrammeCategoryPolicyTest {
    @Test
    fun mapsFullDvbContentTypeByHighNibble() {
        assertEquals(ProgrammeCategory.FILM_DRAMA, programmeCategory(event(0x10)))
        assertEquals(ProgrammeCategory.NEWS, programmeCategory(event(0x2f)))
        assertEquals(ProgrammeCategory.SPORT, programmeCategory(event(0x43)))
        assertEquals(ProgrammeCategory.LIFESTYLE_LEISURE, programmeCategory(event(0xa1)))
    }

    @Test
    fun lowNibbleOnlyValuesRemainUncategorised() {
        assertNull(programmeCategory(event(0x4)))
        assertNull(programmeCategory(event(0x9)))
    }

    @Test
    fun unknownAndSpecialValuesRemainUncategorised() {
        assertNull(programmeCategory(event(null)))
        assertNull(programmeCategory(event(0)))
        assertNull(programmeCategory(event(0xb0)))
        assertNull(programmeCategory(event(0xf2)))
        assertNull(programmeCategory(event(0x100)))
    }

    @Test
    fun allMatchesUncategorisedButSpecificCategoryDoesNot() {
        val event = event(null)
        assertTrue(event.matchesProgrammeCategory(ProgrammeCategory.ALL))
        assertFalse(event.matchesProgrammeCategory(ProgrammeCategory.SPORT))
    }

    @Test
    fun availableCategoriesAreStableAndAlwaysStartWithAll() {
        val categories = availableProgrammeCategories(
            listOf(event(0x40), event(0x20), event(0x4f), event(null)),
        )
        assertEquals(
            listOf(ProgrammeCategory.ALL, ProgrammeCategory.NEWS, ProgrammeCategory.SPORT),
            categories,
        )
    }

    private fun event(contentType: Int?) = EpgEventEntry(
        eventId = 1,
        channelId = 1,
        start = 100,
        stop = 200,
        title = "Programme",
        contentType = contentType,
    )
}
