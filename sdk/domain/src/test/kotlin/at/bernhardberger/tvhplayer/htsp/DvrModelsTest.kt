package at.bernhardberger.tvhplayer.htsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DvrModelsTest {
    @Test
    fun modelsKeepNeutralDefaults() {
        val entry = DvrEntry(
            id = 7,
            eventId = null,
            channelId = 3,
            start = 100L,
            stop = 200L,
            title = "Programme",
            state = DvrState.UNKNOWN,
        )

        assertTrue(entry.files.isEmpty())
        assertNull(entry.playPosition)
        assertNull(entry.playCount)
        assertEquals(true, DvrConfig(id = "default", name = "Default").enabled)
        assertEquals(DvrFile(), DvrFile())
    }
}
