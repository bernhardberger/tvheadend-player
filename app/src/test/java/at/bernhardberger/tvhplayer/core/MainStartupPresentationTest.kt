package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import org.junit.Assert.assertEquals
import org.junit.Test

class MainStartupPresentationTest {
    @Test
    fun configuredDisconnectedTransportDoesNotPresentSetupDuringStartup() {
        val connection = at.bernhardberger.tvheadend.sdk.core.SessionState.Disconnected.toConnectionUiState()
        assertEquals(ConnectionUiState.Connecting, connection)
        assertEquals(
            MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING),
            presentation(connection),
        )
        assertEquals(ConnectionRecoveryAction.NONE, connection.primaryRecoveryAction())
        assertEquals(ConnectionRecoveryAction.SETTINGS, ConnectionUiState.NeedsConfiguration.primaryRecoveryAction())
        assertEquals(ConnectionRecoveryAction.SETTINGS, ConnectionUiState.CredentialUnavailable.primaryRecoveryAction())
    }

    @Test
    fun retainedCatalogDoesNotHideActionableConnectionFailures() {
        val readiness = CurrentChannelReadiness.Browsable(listOf(Channel.create(ChannelId(1))))
        for (connection in listOf(
            ConnectionUiState.NeedsConfiguration,
            ConnectionUiState.CredentialUnavailable,
            ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY),
            ConnectionUiState.Error(ConnectionFailureKind.AUTHENTICATION, SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED),
        )) {
            assertEquals(presentation(connection), presentation(connection, readiness))
        }
    }

    @Test
    fun seededCatalogKeepsAutoplayWaitingForCurrentMetadata() {
        val channels = listOf(Channel.create(ChannelId(1)))
        val readiness = CurrentChannelReadiness.Browsable(channels)
        for (connection in listOf(
            ConnectionUiState.Connecting,
            ConnectionUiState.SyncingChannels,
            ConnectionUiState.Reconnecting,
            ConnectionUiState.Ready,
        )) {
            val uncached = presentation(connection, CurrentChannelReadiness.Waiting) as MainStartupPresentation.Passive
            val cached = presentation(connection, readiness) as MainStartupPresentation.Passive
            assertEquals(uncached.messageKind, cached.messageKind)
            assertEquals(uncached, cached)
            assertEquals(MainStartupLoadingFeedback.HIDDEN, mainStartupLoadingFeedback(cached, 0L))
        }
        val requests = ApplianceLaunchRequests()
        requests.request()
        val pending = requests.state.value as ApplianceLaunchState.Pending
        assertEquals(null, requests.resolve(pending.request, readiness, channels.first().id))
        assertEquals(pending, requests.state.value)
    }

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
            MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_METADATA),
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
    fun retainedInventoryNeverChangesTheCurrentMetadataStage() {
        val retained = CurrentChannelReadiness.Browsable(listOf(Channel.create(ChannelId(1))))
        assertEquals(
            MainStartupPresentation.Passive(MainStartupMessageKind.WAITING_FOR_CURRENT_CHANNEL_METADATA),
            presentation(ConnectionUiState.Ready, retained),
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
    fun readyEmptyIsNoChannelsWithRetryThenConnectionSettings() {
        assertEquals(
            actionable(AppErrorCondition.NO_CHANNELS, *retryAndSettings),
            presentation(
                connectionState = ConnectionUiState.Ready,
                currentChannelReadiness = CurrentChannelReadiness.Ready(emptyList()),
            ),
        )
    }

    @Test
    fun notConfiguredAndCredentialUnavailableExposeOnlyConnectionSettings() {
        assertEquals(
            actionable(AppErrorCondition.NOT_CONFIGURED, AppErrorAction.CONNECTION_SETTINGS),
            presentation(connectionState = ConnectionUiState.NeedsConfiguration),
        )
        assertEquals(
            actionable(AppErrorCondition.CREDENTIAL_UNAVAILABLE, AppErrorAction.CONNECTION_SETTINGS),
            presentation(connectionState = ConnectionUiState.CredentialUnavailable),
        )
    }

    @Test
    fun sessionFailureActionsFollowSdkRecoveryGuidance() {
        assertEquals(
            actionable(AppErrorCondition.LOAD_FAILED, *retryAndSettings),
            presentation(
                connectionState = ConnectionUiState.Error(
                    ConnectionFailureKind.OTHER,
                    SessionRecoveryDisposition.EXPLICIT_RETRY,
                ),
            ),
        )
        assertEquals(
            actionable(AppErrorCondition.AUTHENTICATION_REJECTED, AppErrorAction.CONNECTION_SETTINGS),
            presentation(
                connectionState = ConnectionUiState.Error(
                    ConnectionFailureKind.AUTHENTICATION,
                    SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED,
                ),
            ),
        )
        // 110: the SDK retries by itself, so no Retry; the alert's text says it is reconnecting.
        assertEquals(
            actionable(AppErrorCondition.RECONNECTING, AppErrorAction.CONNECTION_SETTINGS),
            presentation(
                connectionState = ConnectionUiState.Error(
                    ConnectionFailureKind.UNREACHABLE,
                    SessionRecoveryDisposition.AUTOMATIC_BACKOFF,
                ),
            ),
        )
    }

    @Test
    fun everyFailureKindHasItsConditionAndTheSdkActionPolicy() {
        val conditions = mapOf(
            ConnectionFailureKind.UNREACHABLE to AppErrorCondition.UNREACHABLE,
            ConnectionFailureKind.TIMEOUT to AppErrorCondition.TIMEOUT,
            ConnectionFailureKind.AUTHENTICATION to AppErrorCondition.AUTHENTICATION_REJECTED,
            ConnectionFailureKind.PERMISSION_DENIED to AppErrorCondition.PERMISSION_DENIED,
            ConnectionFailureKind.INCOMPATIBLE_SERVER to AppErrorCondition.INCOMPATIBLE_SERVER,
            ConnectionFailureKind.OTHER to AppErrorCondition.LOAD_FAILED,
        )
        for ((kind, condition) in conditions) {
            assertEquals(
                actionable(condition, *retryAndSettings),
                presentation(ConnectionUiState.Error(kind, SessionRecoveryDisposition.EXPLICIT_RETRY)),
            )
            assertEquals(
                actionable(condition, AppErrorAction.CONNECTION_SETTINGS),
                presentation(ConnectionUiState.Error(kind, SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED)),
            )
        }
    }

    @Test
    fun emptyRetainedInventoryCannotAuthorizeReadyEntry() {
        val emptyRetained = CurrentChannelReadiness.Browsable(emptyList())
        val syncing = presentation(ConnectionUiState.SyncingChannels, emptyRetained) as MainStartupPresentation.Passive
        assertEquals(MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_METADATA), syncing)
        val current = CurrentChannelReadiness.Ready(listOf(Channel.create(ChannelId(1))))
        val ready = presentation(ConnectionUiState.Ready, current)
        assertEquals(MainStartupLoadingFeedback.HIDDEN, mainStartupLoadingFeedback(ready, 0L))
    }


    private fun actionable(condition: AppErrorCondition, vararg actions: AppErrorAction) =
        MainStartupPresentation.Actionable(AppError(condition, actions.toList()))

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
        val retryAndSettings = arrayOf(AppErrorAction.RETRY, AppErrorAction.CONNECTION_SETTINGS)
    }
}
