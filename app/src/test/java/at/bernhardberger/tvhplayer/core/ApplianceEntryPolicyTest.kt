package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplianceEntryPolicyTest {
    @Test
    fun playerEntryContractLiteralsArePinned() {
        assertEquals("at.bernhardberger.tvhplayer.action.APPLIANCE_ENTRY", ApplianceEntryPolicy.ACTION_APPLIANCE_ENTRY)
        assertEquals("at.bernhardberger.tvhplayer.extra.APPLIANCE_DESTINATION", ApplianceEntryPolicy.EXTRA_APPLIANCE_DESTINATION)
        assertEquals("live", ApplianceEntryPolicy.DESTINATION_LIVE)
        assertEquals("guide", ApplianceEntryPolicy.DESTINATION_GUIDE)
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

    @Test
    fun onlyTheExactGuideValueChangesTheLiveDefault() {
        assertEquals(ApplianceEntryDestination.GUIDE, ApplianceEntryPolicy.destination("guide"))
        listOf(null, "live", "", "unknown", "GUIDE").forEach {
            assertEquals(ApplianceEntryDestination.LIVE, ApplianceEntryPolicy.destination(it))
        }
    }

    @Test
    fun onlyColdGuideEntryWaitsForReadiness() {
        for (warm in listOf(false, true)) {
            assertEquals(
                if (warm) ApplianceGuideEntryAction.OPEN_GUIDE else ApplianceGuideEntryAction.WAIT,
                ApplianceEntryPolicy.guideEntryAction(ConnectionUiState.Ready, CurrentChannelReadiness.Waiting, warm, false),
            )
        }
    }

    @Test
    fun guideWaitsForStartupAndDropsForSetupErrorsOrConnectionSettings() {
        val ready = CurrentChannelReadiness.Ready(emptyList())
        fun action(connection: ConnectionUiState, warm: Boolean = false, settings: Boolean = false) =
            ApplianceEntryPolicy.guideEntryAction(connection, ready, warm, settings)
        assertEquals(ApplianceGuideEntryAction.WAIT, action(ConnectionUiState.Connecting))
        assertEquals(ApplianceGuideEntryAction.WAIT, action(ConnectionUiState.SyncingChannels))
        assertEquals(ApplianceGuideEntryAction.DROP, action(ConnectionUiState.NeedsConfiguration))
        assertEquals(ApplianceGuideEntryAction.DROP, action(ConnectionUiState.CredentialUnavailable))
        assertEquals(ApplianceGuideEntryAction.DROP, action(ConnectionUiState.Ready, settings = true))
        assertEquals(ApplianceGuideEntryAction.DROP, action(ConnectionUiState.Error(
            at.bernhardberger.tvhplayer.data.ConnectionFailureKind.UNREACHABLE,
            at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition.EXPLICIT_RETRY,
        )))
        assertEquals(ApplianceGuideEntryAction.OPEN_GUIDE, action(ConnectionUiState.Ready))
        assertEquals(ApplianceGuideEntryAction.OPEN_GUIDE, action(ConnectionUiState.Connecting, warm = true))
        assertEquals(ApplianceGuideEntryAction.OPEN_GUIDE, action(ConnectionUiState.Reconnecting, warm = true))
        assertEquals(ApplianceGuideEntryAction.OPEN_GUIDE, action(ConnectionUiState.SyncingChannels, warm = true))
        assertEquals(ApplianceGuideEntryAction.DROP, action(ConnectionUiState.CredentialUnavailable, warm = true))
        assertEquals(ApplianceGuideEntryAction.DROP, action(ConnectionUiState.Ready, warm = true, settings = true))
    }
}
