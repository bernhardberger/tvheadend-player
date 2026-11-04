package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplianceEntryPolicyTest {
    @Test
    fun playerEntryContractLiteralsArePinned() {
        assertEquals("at.bernhardberger.tvhplayer.action.APPLIANCE_ENTRY", ApplianceEntryPolicy.ACTION_APPLIANCE_ENTRY)
        assertEquals("at.bernhardberger.tvhplayer.ui.ApplianceEntryActivity", ApplianceEntryPolicy.ENTRY_ALIAS_CLASS_NAME)
    }

    @Test
    fun entryRequiresTheActionAndThePlayersGuardedAlias() {
        val player = "at.bernhardberger.tvhplayer.debug"
        fun accepts(action: String?, pkg: String?, name: String?) =
            ApplianceEntryPolicy.acceptsEntryIntent(action, pkg, name, player)
        assertTrue(accepts(ApplianceEntryPolicy.ACTION_APPLIANCE_ENTRY, player,
            ApplianceEntryPolicy.ENTRY_ALIAS_CLASS_NAME))
        assertFalse(accepts(ApplianceEntryPolicy.ACTION_APPLIANCE_ENTRY, player,
            "at.bernhardberger.tvhplayer.ui.MainActivity"))
        assertFalse(accepts(ApplianceEntryPolicy.ACTION_APPLIANCE_ENTRY, "other.package",
            ApplianceEntryPolicy.ENTRY_ALIAS_CLASS_NAME))
        assertFalse(accepts("android.intent.action.MAIN", player,
            ApplianceEntryPolicy.ENTRY_ALIAS_CLASS_NAME))
        assertFalse(accepts(null, player, ApplianceEntryPolicy.ENTRY_ALIAS_CLASS_NAME))
        assertFalse(accepts(ApplianceEntryPolicy.ACTION_APPLIANCE_ENTRY, null, null))
    }

    @Test
    fun entryIntent_requestsPlaybackOnlyWhenPlayerIsNotAlreadyVisible() {
        assertTrue(ApplianceEntryPolicy.shouldCreateLaunchRequest(isPlayerVisible = false))
        assertFalse(ApplianceEntryPolicy.shouldCreateLaunchRequest(isPlayerVisible = true))
    }
}
