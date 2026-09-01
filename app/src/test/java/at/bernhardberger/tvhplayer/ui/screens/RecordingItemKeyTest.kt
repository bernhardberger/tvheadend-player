package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.ui.screens.recordings.recordingItemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecordingItemKeyTest {
    @Test
    fun itemKeyUsesTheBundleSaveableRecordingValue() {
        val key: Long = recordingItemKey(DvrEntryId(7))

        assertEquals(7L, key)
        assertNotEquals(key, recordingItemKey(DvrEntryId(8)))
    }
}
