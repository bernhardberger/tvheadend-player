package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgrammeCategoryPolicyTest {
    @Test
    fun everyContentTypeByteKeepsTheLegacyHighNibbleMapping() {
        // Legacy app table keyed by the high nibble of the raw byte; deliberately not derived from SDK codes.
        val legacyByHighNibble: Map<Int, ProgrammeCategory?> = mapOf(
            0x0 to null,
            0x1 to ProgrammeCategory.FILM_DRAMA,
            0x2 to ProgrammeCategory.NEWS,
            0x3 to ProgrammeCategory.ENTERTAINMENT,
            0x4 to ProgrammeCategory.SPORT,
            0x5 to ProgrammeCategory.CHILDREN,
            0x6 to ProgrammeCategory.MUSIC,
            0x7 to ProgrammeCategory.ARTS_CULTURE,
            0x8 to ProgrammeCategory.SOCIETY_POLITICS,
            0x9 to ProgrammeCategory.EDUCATION_FACTUAL,
            0xa to ProgrammeCategory.LIFESTYLE_LEISURE,
            0xb to null,
            0xc to null,
            0xd to null,
            0xe to null,
            0xf to null,
        )
        assertEquals((0x0..0xf).toSet(), legacyByHighNibble.keys)

        for (value in 0x00..0xff) {
            assertEquals(
                "contentType=0x%02x".format(value),
                legacyByHighNibble.getValue(value ushr 4),
                programmeCategory(event(value.toLong())),
            )
        }
    }

    @Test
    fun missingAndOutOfByteRangeContentTypesRemainUncategorised() {
        assertNull(programmeCategory(event(null)))
        assertNull(programmeCategory(event(0x100L)))
        assertNull(programmeCategory(event(0xFFFF_FFFFL)))
        // The SDK rejects values outside unsigned 32 bits, so they can never reach the policy.
        listOf(-1L, Long.MIN_VALUE, Long.MAX_VALUE).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { event(value) }
        }
    }

    @Test
    fun unknownAndSpecialValuesRemainUncategorised() {
        assertNull(programmeCategory(event(null)))
        assertNull(programmeCategory(event(0)))
        assertNull(programmeCategory(event(0x4)))
        assertNull(programmeCategory(event(0xb0)))
        assertNull(programmeCategory(event(0xc0)))
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

    private fun event(contentType: Long?) = EpgEvent.create(
        id = EventId(1),
        channelId = ChannelId(1),
        start = Instant.fromEpochSeconds(100),
        stop = Instant.fromEpochSeconds(200),
        title = "Programme",
        contentType = contentType,
    )
}
