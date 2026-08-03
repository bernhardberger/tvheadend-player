package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.core.EpgEventEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgrammePresentationTest {
    @Test
    fun summaryFallsBackToDescription() {
        val event = sample(summary = null, description = "Full synopsis")
        assertEquals("Full synopsis", programmeSummaryText(event))
    }

    @Test
    fun summaryPrefersSummaryOverDescription() {
        val event = sample(summary = "Short", description = "Longer body")
        assertEquals("Short", programmeSummaryText(event))
    }

    @Test
    fun detailsBodyCombinesDistinctSummaryAndDescription() {
        val event = sample(summary = "Short", description = "Longer body")
        assertEquals("Short\n\nLonger body", programmeDetailsBody(event))
    }

    @Test
    fun detailsBodyDoesNotDuplicateIdenticalText() {
        val event = sample(summary = "Same", description = "Same")
        assertEquals("Same", programmeDetailsBody(event))
    }

    @Test
    fun escapedLineBreaksAreRenderedAsLineBreaks() {
        val event = sample(
            summary = "First line\\nSecond line",
            description = "First line\\nSecond line",
        )

        assertEquals("First line\nSecond line", programmeSummaryText(event))
        assertEquals("First line\nSecond line", programmeDetailsBody(event))
    }

    @Test
    fun emptyMetadataYieldsNullBody() {
        assertNull(programmeSummaryText(sample(summary = "  ", description = null)))
        assertNull(programmeDetailsBody(sample(summary = null, description = null)))
    }

    @Test
    fun airedDetectionUsesStopTime() {
        val event = sample(start = 100, stop = 200)
        assertTrue(programmeHasAired(event, nowSec = 200))
        assertFalse(programmeHasAired(event, nowSec = 199))
    }

    private fun sample(
        summary: String? = null,
        description: String? = null,
        start: Long = 0,
        stop: Long = 60,
    ) = EpgEventEntry(
        eventId = 1,
        channelId = 2,
        start = start,
        stop = stop,
        title = "Title",
        summary = summary,
        description = description,
    )
}
