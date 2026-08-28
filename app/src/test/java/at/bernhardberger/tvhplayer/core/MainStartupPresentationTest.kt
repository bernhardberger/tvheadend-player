package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.data.SubscriptionFailureKind
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
                simpleTvActive = true,
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
                simpleTvActive = true,
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
                simpleTvActive = true,
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
                simpleTvActive = false,
            ),
        )
        assertEquals(
            expected,
            mainStartupPresentation(
                startupState = readyBootstrap,
                launchState = pending,
                connectionState = ConnectionUiState.Ready,
                currentChannelReadiness = CurrentChannelReadiness.Ready(listOf(Channel.create(ChannelId(1)))),
                simpleTvActive = true,
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
                simpleTvActive = false,
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
    fun everyConnectionAndSubscriptionFailureIsRetryableInNormalMode() {
        ConnectionFailureKind.entries.forEach { kind ->
            assertEquals(
                MainStartupPresentation.Actionable(
                    MainStartupMessageKind.RETRYABLE_FAILURE,
                    normalActions,
                ),
                presentation(connectionState = ConnectionUiState.Error(kind)),
            )
        }
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
    fun everyActionableFailureUsesRetryAndExitSimpleTvWhenSimpleTvIsActive() {
        val failures = buildList<ConnectionUiState> {
            add(ConnectionUiState.NeedsConfiguration)
            add(ConnectionUiState.CredentialUnavailable)
            add(ConnectionUiState.Ready)
            ConnectionFailureKind.entries.forEach { add(ConnectionUiState.Error(it)) }
            SubscriptionFailureKind.entries.forEach { add(ConnectionUiState.SubscriptionError(it)) }
        }

        failures.forEach { connectionState ->
            assertEquals(
                MainStartupPresentation.Actionable(
                    MainStartupMessageKind.SIMPLE_TV_FAILURE,
                    simpleTvActions,
                ),
                presentation(
                    connectionState = connectionState,
                    currentChannelReadiness = CurrentChannelReadiness.Ready(emptyList()),
                    simpleTvActive = true,
                ),
            )
        }
    }

    @Test
    fun stableActionIdsHaveTheSpecifiedOrder() {
        assertEquals(
            listOf(
                MainStartupActionId.RETRY,
                MainStartupActionId.CONNECTION_SETTINGS,
                MainStartupActionId.EXIT_SIMPLE_TV,
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
        assertEquals(
            listOf(MainStartupActionId.RETRY, MainStartupActionId.EXIT_SIMPLE_TV),
            simpleTvActions,
        )
    }

    private fun presentation(
        connectionState: ConnectionUiState,
        currentChannelReadiness: CurrentChannelReadiness = CurrentChannelReadiness.Waiting,
        simpleTvActive: Boolean = false,
    ): MainStartupPresentation = mainStartupPresentation(
        startupState = readyBootstrap,
        launchState = pending(),
        connectionState = connectionState,
        currentChannelReadiness = currentChannelReadiness,
        simpleTvActive = simpleTvActive,
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
            startSimpleTv = false,
        )
        val normalActions = listOf(
            MainStartupActionId.RETRY,
            MainStartupActionId.CONNECTION_SETTINGS,
        )
        val simpleTvActions = listOf(
            MainStartupActionId.RETRY,
            MainStartupActionId.EXIT_SIMPLE_TV,
        )
    }
}
