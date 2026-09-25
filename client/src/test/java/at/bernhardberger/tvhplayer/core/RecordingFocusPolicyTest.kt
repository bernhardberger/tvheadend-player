package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingFocusPolicyTest {
    @Test
    fun focusTargetRestoresTheSelectedItemByIdentity() {
        assertEquals("recording:2", recordingFocusTargetKey(
            orderedKeys = listOf("recording:1", "recording:2"),
            selectedKey = "recording:2",
        ))
    }

    @Test
    fun focusTargetFallsBackToTheFirstItemWhenSelectionDisappears() {
        assertEquals("recording:1", recordingFocusTargetKey(
            orderedKeys = listOf("recording:1", "recording:2"),
            selectedKey = "recording:3",
        ))
    }

    @Test
    fun focusTargetIsAbsentForAnEmptyContainer() {
        assertNull(recordingFocusTargetKey(emptyList(), selectedKey = "recording:1"))
    }
}
