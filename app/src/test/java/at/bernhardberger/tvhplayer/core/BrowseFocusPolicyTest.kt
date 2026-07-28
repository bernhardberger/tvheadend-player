package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseFocusPolicyTest {
    @Test
    fun `empty Channels action waits until content entry is enabled`() {
        assertFalse(
            shouldRequestEmptyChannelsAction(
                initialFocusEnabled = false,
                hasPrimaryAction = true,
            )
        )
        assertTrue(
            shouldRequestEmptyChannelsAction(
                initialFocusEnabled = true,
                hasPrimaryAction = true,
            )
        )
    }

    @Test
    fun `Guide entry prefers programme then retry then header`() {
        assertEquals(
            GuideEntryFocusTarget.PROGRAMME,
            guideEntryFocusTarget(hasProgrammeTarget = true, hasRetryAction = true),
        )
        assertEquals(
            GuideEntryFocusTarget.RETRY,
            guideEntryFocusTarget(hasProgrammeTarget = false, hasRetryAction = true),
        )
        assertEquals(
            GuideEntryFocusTarget.HEADER,
            guideEntryFocusTarget(hasProgrammeTarget = false, hasRetryAction = false),
        )
    }

    @Test
    fun `Guide scope exit never moves backward to the header`() {
        assertEquals(
            GuideScopeExitFocusTarget.PROGRAMME,
            guideScopeExitFocusTarget(hasProgrammeTarget = true, hasRetryAction = true),
        )
        assertEquals(
            GuideScopeExitFocusTarget.RETRY,
            guideScopeExitFocusTarget(hasProgrammeTarget = false, hasRetryAction = true),
        )
        assertEquals(
            GuideScopeExitFocusTarget.STAY_ON_SCOPE,
            guideScopeExitFocusTarget(hasProgrammeTarget = false, hasRetryAction = false),
        )
    }
}
