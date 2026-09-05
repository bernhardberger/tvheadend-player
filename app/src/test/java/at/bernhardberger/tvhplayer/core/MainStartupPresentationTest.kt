package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.data.SubscriptionFailureKind
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import org.junit.Assert.assertEquals
import org.junit.Test

class MainStartupPresentationTest {

    @Test
    fun resolvingLocal_takesPrecedenceAndIsPassivePreparing() {
        assertEquals(
            MainStartupPresentation.Passive(MainStartupMessageKind.PREPARING),
            mainStartupPresentation(
                startupState = MainStartupState.ResolvingLocal,
                launchState = entering(),
                connectionState = ConnectionUiState.Ready,
                currentChannelReadiness = CurrentChannelReadiness.Ready(listOf(Channel.create(ChannelId(1)))),
            ),
        )
    }

    @Test
    fun readyBootstrapWithIdleLaunch_isInactive() {
        assertEquals(
            MainStartupPresentation.Inactive,
            mainStartupPresentation(
                startupState = readyBootstrap,
                launchState = ApplianceLaunchState.Idle,
                connectionState = ConnectionUiState.Ready,
                currentChannelReadiness = CurrentChannelReadiness.Ready(listOf(Channel.create(ChannelId(1)))),
            ),
        )
    }

    @Test
    fun entering_takesPrecedenceAndIsPassiveStartingTelevision() {
        assertEquals(
            MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION),
            mainStartupPresentation(
                startupState = readyBootstrap,
                launchState = entering(),
                connectionState = ConnectionUiState.Ready,
                currentChannelReadiness = CurrentChannelReadiness.Ready(listOf(Channel.create(ChannelId(1)))),
            ),
        )
    }

    @Test
    fun pendingTransportPhases_arePassiveWithNoActions() {
        assertEquals(
            MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING),
            presentation(connectionState = ConnectionUiState.Connecting),
        )
        assertEquals(
            MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_CHANNELS),
            presentation(connectionState = ConnectionUiState.SyncingChannels),
        )
        assertEquals(
            MainStartupPresentation.Passive(MainStartupMessageKind.RECONNECTING),
            presentation(connectionState = ConnectionUiState.Reconnecting),
        )
    }

    @Test
    fun readyTransportWaitingForCurrentMetadata_isPassiveAndNeverEnters() {
        assertEquals(
            MainStartupPresentation.Passive(
                MainStartupMessageKind.WAITING_FOR_CURRENT_CHANNEL_METADATA,
            ),
            presentation(
                connectionState = ConnectionUiState.Ready,
                currentChannelReadiness = CurrentChannelReadiness.Waiting,
            ),
        )
    }

    @Test
    fun enterRequiresPendingReadyTransportAndNonEmptyCurrentSnapshot() {
        val pending = pending()
        val expected = MainStartupPresentation.Enter(pending.request)

        assertEquals(
            expected,
            mainStartupPresentation(
                startupState = readyBootstrap,
                launchState = pending,
                connectionState = ConnectionUiState.Ready,
                currentChannelReadiness = CurrentChannelReadiness.Ready(listOf(Channel.create(ChannelId(1)))),
            ),
        )
        assertEquals(
            MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING),
            presentation(
                connectionState = ConnectionUiState.Connecting,
                currentChannelReadiness = CurrentChannelReadiness.Ready(listOf(Channel.create(ChannelId(1)))),
            ),
        )
        assertEquals(
            expected,
            mainStartupPresentation(
                startupState = readyBootstrap,
                launchState = pending,
                connectionState = ConnectionUiState.Ready,
                currentChannelReadiness = CurrentChannelReadiness.Ready(listOf(Channel.create(ChannelId(99)))),
            ),
        )
    }

    @Test
    fun readyEmptyIsAuthoritativeNoChannelsWithNormalActionsInOrder() {
        assertEquals(
            MainStartupPresentation.Actionable(
                messageKind = MainStartupMessageKind.AUTHORITATIVE_NO_CHANNELS,
                actions = normalActions,
            ),
            presentation(
                connectionState = ConnectionUiState.Ready,
                currentChannelReadiness = CurrentChannelReadiness.Ready(emptyList()),
            ),
        )
    }

    @Test
    fun normalConfigurationAndCredentialFailuresExposeOnlyConnectionSettings() {
        assertEquals(
            MainStartupPresentation.Actionable(
                MainStartupMessageKind.CONFIGURATION_REQUIRED,
                listOf(MainStartupActionId.CONNECTION_SETTINGS),
            ),
            presentation(connectionState = ConnectionUiState.NeedsConfiguration),
        )
        assertEquals(
            MainStartupPresentation.Actionable(
                MainStartupMessageKind.CREDENTIAL_UNAVAILABLE,
                listOf(MainStartupActionId.CONNECTION_SETTINGS),
            ),
            presentation(connectionState = ConnectionUiState.CredentialUnavailable),
        )
    }

    @Test
    fun sessionFailureActionsFollowSdkRecoveryGuidanceInNormalMode() {
        assertEquals(
            MainStartupPresentation.Actionable(
                MainStartupMessageKind.RETRYABLE_FAILURE,
                normalActions,
            ),
            presentation(
                connectionState = ConnectionUiState.Error(
                    ConnectionFailureKind.ZERO_CHANNELS,
                    SessionRecoveryDisposition.EXPLICIT_RETRY,
                ),
            ),
        )
        assertEquals(
            MainStartupPresentation.Actionable(
                MainStartupMessageKind.RETRYABLE_FAILURE,
                listOf(MainStartupActionId.CONNECTION_SETTINGS),
            ),
            presentation(
                connectionState = ConnectionUiState.Error(
                    ConnectionFailureKind.AUTHENTICATION,
                    SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED,
                ),
            ),
        )
        assertEquals(
            MainStartupPresentation.Passive(MainStartupMessageKind.RECONNECTING),
            presentation(
                connectionState = ConnectionUiState.Error(
                    ConnectionFailureKind.UNREACHABLE,
                    SessionRecoveryDisposition.AUTOMATIC_BACKOFF,
                ),
            ),
        )
        SubscriptionFailureKind.entries.forEach { kind ->
            assertEquals(
                MainStartupPresentation.Actionable(
                    MainStartupMessageKind.RETRYABLE_FAILURE,
                    normalActions,
                ),
                presentation(connectionState = ConnectionUiState.SubscriptionError(kind)),
            )
        }
    }





    @Test
    fun stableActionIdsHaveTheSpecifiedOrder() {
        assertEquals(
            listOf(
                MainStartupActionId.RETRY,
                MainStartupActionId.CONNECTION_SETTINGS,
            ),
            MainStartupActionId.entries,
        )
        assertEquals(
            listOf(
                MainStartupActionId.RETRY,
                MainStartupActionId.CONNECTION_SETTINGS,
            ),
            normalActions,
        )
    }

    private fun presentation(
        connectionState: ConnectionUiState,
        currentChannelReadiness: CurrentChannelReadiness = CurrentChannelReadiness.Waiting,
    ): MainStartupPresentation = mainStartupPresentation(
        startupState = readyBootstrap,
        launchState = pending(),
        connectionState = connectionState,
        currentChannelReadiness = currentChannelReadiness,
    )

    private fun pending() = ApplianceLaunchState.Pending(ApplianceLaunchRequest(1))

    private fun entering() = ApplianceLaunchState.Entering(
        ApplianceLaunchTarget(
            request = ApplianceLaunchRequest(2),
            channelId = ChannelId(2),
            channelName = "Two",
        ),
    )

    private companion object {
        val readyBootstrap = MainStartupState.Ready(
            server = at.bernhardberger.tvhplayer.settings.ServerSettings(host = "tvh.invalid"),
            autoStartPlayback = true,
        )
        val normalActions = listOf(
            MainStartupActionId.RETRY,
            MainStartupActionId.CONNECTION_SETTINGS,
        )
    }
}
