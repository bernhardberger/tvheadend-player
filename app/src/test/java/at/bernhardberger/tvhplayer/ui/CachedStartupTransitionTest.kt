package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.ApplianceLaunchState
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.core.StartupBootstrapCoordinator
import at.bernhardberger.tvhplayer.core.mainStartupPresentation
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.UiSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedStartupTransitionTest {
    @Test
    fun enabledBootstrapWaitsThroughCachedReconnectThenEntersRememberedCurrentChannel() = runTest {
        val requests = ApplianceLaunchRequests()
        val bootstrap = StartupBootstrapCoordinator(
            requests,
            loadServerSettings = { ServerSettings(host = "offline.invalid") },
            loadUiSettings = { UiSettings(autoStartPlayback = true) },
        )
        bootstrap.bootstrap()
        val pending = requests.state.value as ApplianceLaunchState.Pending
        val channels = listOf(Channel.create(ChannelId(1)), Channel.create(ChannelId(2)))
        for (connection in listOf(ConnectionUiState.SyncingChannels, ConnectionUiState.Reconnecting)) {
            val cached = CurrentChannelReadiness.Browsable(channels)
            assertTrue(mainStartupPresentation(bootstrap.state.value, requests.state.value, connection, cached)
                is MainStartupPresentation.Passive)
            assertNull(requests.resolve(pending.request, cached, ChannelId(2)))
        }
        val current = CurrentChannelReadiness.Ready(channels)
        assertEquals(MainStartupPresentation.Enter(pending.request),
            mainStartupPresentation(bootstrap.state.value, requests.state.value, ConnectionUiState.Ready, current))
        assertEquals(ChannelId(2), requests.resolve(pending.request, current, ChannelId(2))?.channelId)
        assertNull(requests.resolve(pending.request, current, ChannelId(2)))
    }

    @Test
    fun profileReplacementCancelsPendingEntryAndCannotRearmOnReconnect() {
        val requests = ApplianceLaunchRequests()
        val profile = ServerProfileReadResult.anonymous("offline.invalid")
        requests.observeProfile(profile)
        requests.requestStartup(true)
        val pending = requests.state.value as ApplianceLaunchState.Pending
        requests.observeProfile(profile)
        assertEquals(pending, requests.state.value)
        requests.observeProfile(ServerProfileReadResult.anonymous("replacement.invalid"))
        val ready = CurrentChannelReadiness.Ready(listOf(Channel.create(ChannelId(1))))
        assertNull(requests.resolve(pending.request, ready, ChannelId(1)))
        assertEquals(ApplianceLaunchState.Idle, requests.state.value)
    }

    @Test
    fun cachedStartupWaitsAndExplicitBackPreventsLaterAutoplay() {
        val channels = listOf(Channel.create(ChannelId(1)))
        for (connection in listOf(
            ConnectionUiState.SyncingChannels,
            ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.AUTOMATIC_BACKOFF),
        )) {
            val requests = ApplianceLaunchRequests()
            requests.request()
            val pending = requests.state.value as ApplianceLaunchState.Pending
            val readiness = CurrentChannelReadiness.Browsable(channels)
            val presentation = mainStartupPresentation(
                    startupState = MainStartupState.Ready(ServerSettings(host = "offline-fixture"), autoStartPlayback = true),
                    launchState = pending,
                    connectionState = connection,
                    currentChannelReadiness = readiness,
                )
            assertTrue(presentation is MainStartupPresentation.Passive)
            var destination: AppNavKey? = null
            var selections = 0
            val selectRoot: (AppNavKey) -> Unit = { destination = it; selections++ }
            assertNull(destination)
            assertEquals(pending, requests.state.value)
            assertNull(requests.resolve(pending.request, readiness, channels.first().id))
            performMainStartupBack(requests, pending, selectRoot)
            assertEquals(ChannelsKey, destination)
            assertEquals(ApplianceLaunchState.Idle, requests.state.value)
            performMainStartupBack(requests, pending, selectRoot)
            assertEquals(1, selections)
            assertNull(requests.resolve(pending.request, CurrentChannelReadiness.Ready(channels), channels.first().id))
            assertEquals(ApplianceLaunchState.Idle, requests.state.value)
        }
    }
}
