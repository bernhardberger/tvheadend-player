package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainStartupEntryTest {
    @Test fun onlyFirstNonRestoredProcessOpportunityCanAssemble() {
        val process = MainStartupProcessEntry()
        assertTrue(process.claim(false))
        assertFalse(process.claim(false))
        assertFalse(MainStartupProcessEntry().apply { claim(true) }.claim(false))
    }

    @Test fun continuingConnectingWaitAtGraceAdmitsAssembly() {
        val pending = MainStartupEntry(true)
        pending.observe(ConnectionUiState.Connecting)
        assertEquals(MainStartupBrandDecision.PENDING, pending.decision)
        pending.afterGrace()
        assertEquals(MainStartupBrandDecision.ASSEMBLE, pending.decision)
        val preparing = MainStartupEntry(true)
        preparing.afterGrace()
        preparing.observe(ConnectionUiState.SyncingChannels)
        preparing.afterGrace()
        assertEquals(MainStartupBrandDecision.ASSEMBLE, preparing.decision)
    }

    @Test fun metadataReadyBeforeGraceDoesNotReplaceActualWaitAdmission() {
        val entry = MainStartupEntry(true)
        entry.observe(ConnectionUiState.Ready)
        entry.afterGrace()
        assertEquals(MainStartupBrandDecision.ASSEMBLE, entry.decision)
    }

    @Test fun readinessBeforeGraceLaterActivityRecoveryAndCancellationCannotReplay() {
        listOf(MainStartupEntry(false), MainStartupEntry(true).apply { cancel() },
            MainStartupEntry(true).apply { observe(ConnectionUiState.Reconnecting) },
        ).forEach {
            it.observe(ConnectionUiState.SyncingChannels)
            it.afterGrace()
            assertEquals(MainStartupBrandDecision.SETTLED, it.decision)
        }
    }

    @Test fun admittedAssemblySurvivesMetadataButNotRecoveryOrCancellation() {
        val exits = listOf(
            ConnectionUiState.Connecting, ConnectionUiState.SyncingChannels,
            ConnectionUiState.Reconnecting, ConnectionUiState.CredentialUnavailable,
            ConnectionUiState.NeedsConfiguration,
            ConnectionUiState.Error(at.bernhardberger.tvhplayer.data.ConnectionFailureKind.UNREACHABLE,
                at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition.EXPLICIT_RETRY),
        )
        exits.forEach { exit ->
            val entry = MainStartupEntry(true)
            entry.observe(ConnectionUiState.SyncingChannels)
            entry.afterGrace()
            entry.observe(ConnectionUiState.Ready)
            assertEquals(MainStartupBrandDecision.ASSEMBLE, entry.decision)
            entry.observe(exit)
            assertEquals(MainStartupBrandDecision.SETTLED, entry.decision)
            entry.observe(ConnectionUiState.SyncingChannels)
            entry.afterGrace()
            assertEquals(MainStartupBrandDecision.SETTLED, entry.decision)
        }
    }
}
