package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrConfigurationsState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvhplayer.core.AppError
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.core.chooseDvrConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EpgGridObservationPolicyTest {
    @Test
    fun readyTransportDoesNotPresentEmptyBeforeTheChannelCatalogIsCurrent() {
        assertEquals(
            R.string.epg_loading,
            guideEmptyMessageRes(
                isEmptyTag = false,
                connectionUiState = ConnectionUiState.Ready,
                channelCatalogCurrent = false,
            ),
        )
    }

    @Test
    fun currentEmptyChannelCatalogIsTheNoChannelsAlert() {
        assertEquals(
            AppError(AppErrorCondition.NO_CHANNELS, listOf(AppErrorAction.RETRY, AppErrorAction.CONNECTION_SETTINGS)),
            guideAppError(hasChannels = false, isEmptyTag = false, ConnectionUiState.Ready, channelCatalogCurrent = true),
        )
        // Before the catalog is current, and for an empty tag, the Guide stays passive.
        assertNull(guideAppError(hasChannels = false, isEmptyTag = false, ConnectionUiState.Ready, channelCatalogCurrent = false))
        assertNull(guideAppError(hasChannels = false, isEmptyTag = true, ConnectionUiState.Ready, channelCatalogCurrent = true))
    }

    @Test
    fun connectionFailuresWithoutChannelsAreTheCatalogAlertAndNeverBlockSavedChannels() {
        val unreachable = ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY)
        val backoff = ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.AUTOMATIC_BACKOFF)
        val denied = ConnectionUiState.Error(ConnectionFailureKind.PERMISSION_DENIED, SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED)
        val expected = mapOf(
            unreachable to AppError(AppErrorCondition.UNREACHABLE, listOf(AppErrorAction.RETRY, AppErrorAction.CONNECTION_SETTINGS)),
            backoff to AppError(AppErrorCondition.RECONNECTING, listOf(AppErrorAction.CONNECTION_SETTINGS)),
            denied to AppError(AppErrorCondition.PERMISSION_DENIED, listOf(AppErrorAction.CONNECTION_SETTINGS)),
            ConnectionUiState.NeedsConfiguration to
                AppError(AppErrorCondition.NOT_CONFIGURED, listOf(AppErrorAction.CONNECTION_SETTINGS)),
            ConnectionUiState.CredentialUnavailable to
                AppError(AppErrorCondition.CREDENTIAL_UNAVAILABLE, listOf(AppErrorAction.CONNECTION_SETTINGS)),
        )
        for ((connection, error) in expected) {
            for (isEmptyTag in listOf(false, true)) {
                assertEquals(error, guideAppError(hasChannels = false, isEmptyTag, connection, channelCatalogCurrent = true))
            }
            assertNull(guideAppError(hasChannels = true, isEmptyTag = false, connection, channelCatalogCurrent = true))
        }
    }

    @Test
    fun aHeldFailureStaysThroughAnAutomaticAttempt() {
        val backoff = ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.AUTOMATIC_BACKOFF)
        assertEquals(AppError(AppErrorCondition.RECONNECTING, listOf(AppErrorAction.CONNECTION_SETTINGS)),
            guideAppError(hasChannels = false, isEmptyTag = false, ConnectionUiState.Connecting,
                channelCatalogCurrent = true, connectionFailure = backoff))
        assertNull(guideAppError(hasChannels = true, isEmptyTag = false, ConnectionUiState.Connecting,
            channelCatalogCurrent = true, connectionFailure = backoff))
    }

    @Test
    fun loadingAndReconnectingStayPassive() {
        for (connection in listOf(ConnectionUiState.Connecting, ConnectionUiState.SyncingChannels, ConnectionUiState.Reconnecting)) {
            assertNull(guideAppError(hasChannels = false, isEmptyTag = false, connection, channelCatalogCurrent = true))
        }
        assertEquals(R.string.epg_loading, guideEmptyMessageRes(false, ConnectionUiState.Connecting, false))
        assertEquals(R.string.epg_loading, guideEmptyMessageRes(false, ConnectionUiState.SyncingChannels, false))
        assertEquals(R.string.epg_reconnecting, guideEmptyMessageRes(false, ConnectionUiState.Reconnecting, true))
    }

    @Test
    fun activeTagIsEmptyOnlyAfterTheChannelCatalogIsCurrent() {
        assertEquals(
            R.string.epg_loading,
            guideEmptyMessageRes(
                isEmptyTag = true,
                connectionUiState = ConnectionUiState.Ready,
                channelCatalogCurrent = false,
            ),
        )
        assertEquals(
            R.string.empty_channel_tag,
            guideEmptyMessageRes(
                isEmptyTag = true,
                connectionUiState = ConnectionUiState.Ready,
                channelCatalogCurrent = true,
            ),
        )
    }

    @Test
    fun currentGenerationCannotSelectAStaleConfigurationId() {
        val staleConfiguration = configuration("generation-a")
        val observation = currentObservation(
            DvrConfigurationsState.Stale.create(listOf(staleConfiguration))
        )

        assertNotNull(observation.currentSession)
        assertEquals(
            null,
            chooseDvrConfig(observation.currentDvrConfigurations()),
        )
    }

    @Test
    fun currentGenerationCanSelectItsCurrentConfigurationId() {
        val currentConfiguration = configuration("generation-b")
        val observation = currentObservation(
            DvrConfigurationsState.Current.create(listOf(currentConfiguration))
        )

        assertEquals(
            currentConfiguration.id,
            chooseDvrConfig(observation.currentDvrConfigurations()),
        )
    }

    @Test fun recordingDefaultPrefersUnnamedOtherwiseFirstProfile() {
        val first = configuration("first")
        val unnamed = DvrConfiguration(DvrConfigId("default"), "", "")
        assertEquals(unnamed.id, chooseDvrConfig(listOf(first, unnamed)))
        assertEquals(first.id, chooseDvrConfig(listOf(first, configuration("second"))))
        assertEquals(null, chooseDvrConfig(emptyList()))
    }

    @Test fun recordingProfileUsesSavedIdOnlyWhileOfferedByCurrentServer() {
        val first = configuration("first")
        val unnamed = DvrConfiguration(DvrConfigId("default"), "", "")
        assertEquals(first.id, chooseDvrConfig(listOf(first, unnamed), first.id))
        assertEquals(unnamed.id, chooseDvrConfig(listOf(first, unnamed), DvrConfigId("removed")))
        assertEquals(first.id, chooseDvrConfig(listOf(first), DvrConfigId("removed")))
        assertEquals(null, chooseDvrConfig(emptyList(), first.id))
    }

    private fun currentObservation(
        configurationsState: DvrConfigurationsState,
    ): SessionObservation = SessionObservation.create(
        sessionState = SessionState.Ready(
            ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED,
                dvrWrite = CapabilityAccess.ALLOWED,
            )
        ),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        dvrConfigurationsState = configurationsState,
    )

    private fun configuration(id: String) = DvrConfiguration(
        id = DvrConfigId(id),
        name = id,
        comment = "",
    )
}
