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
import at.bernhardberger.tvhplayer.core.DvrConfigChoice
import at.bernhardberger.tvhplayer.core.chooseDvrConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EpgGridObservationPolicyTest {
    @Test
    fun currentGenerationCannotSelectAStaleConfigurationId() {
        val staleConfiguration = configuration("generation-a")
        val observation = currentObservation(
            DvrConfigurationsState.Stale.create(listOf(staleConfiguration))
        )

        assertNotNull(observation.currentSession)
        assertEquals(
            DvrConfigChoice.Automatic(configId = null),
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
            DvrConfigChoice.Automatic(currentConfiguration.id),
            chooseDvrConfig(observation.currentDvrConfigurations()),
        )
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
