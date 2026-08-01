package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingWriteCapabilityTest {
    @Test
    fun onlyAllowedEnablesWriteUi() {
        assertFalse(RecordingWriteCapability.Unknown.canModifyRecordings())
        assertTrue(RecordingWriteCapability.Allowed.canModifyRecordings())
        assertFalse(RecordingWriteCapability.Denied.canModifyRecordings())
    }
}
