package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamProfilePresentationTest {
    @Test
    fun htspUsesDirectStreamingPrimaryWithExactNameSecondary() {
        val presentation = streamProfilePresentation(
            profileName = "htsp",
            directStreamingLabel = "Direct streaming",
        )
        assertEquals("Direct streaming", presentation.primaryLabel)
        assertEquals("htsp", presentation.secondaryLabel)
    }

    @Test
    fun htspMatchIsCaseInsensitive() {
        val presentation = streamProfilePresentation(
            profileName = "HTSP",
            directStreamingLabel = "Direct streaming",
        )
        assertEquals("Direct streaming", presentation.primaryLabel)
        assertEquals("HTSP", presentation.secondaryLabel)
    }

    @Test
    fun arbitraryProfilesKeepExactNameWithoutGuessing() {
        val presentation = streamProfilePresentation(
            profileName = "webtv-h264-aac-matroska",
            directStreamingLabel = "Direct streaming",
        )
        assertEquals("webtv-h264-aac-matroska", presentation.primaryLabel)
        assertNull(presentation.secondaryLabel)
    }
}
