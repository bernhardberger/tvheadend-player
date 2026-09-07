package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.ApplianceLaunchState
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.core.mainStartupPresentation
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.settings.ServerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedStartupTransitionTest {
    @Test
    fun cachedStartupSelectsChannelsOnceAndCannotLaterAutoplayTheCancelledRequest() {
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
            assertEquals(MainStartupPresentation.Inactive, presentation)
            var destination: AppNavKey? = null
            var selections = 0
            val selectRoot: (AppNavKey) -> Unit = { destination = it; selections++ }
            assertFalse(enterCachedChannelList(presentation, CurrentChannelReadiness.Waiting, requests, pending, selectRoot))
            assertTrue(enterCachedChannelList(presentation, readiness, requests, pending, selectRoot))
            assertEquals(ChannelsKey, destination)
            assertEquals(ApplianceLaunchState.Idle, requests.state.value)
            assertFalse(enterCachedChannelList(presentation, readiness, requests, pending, selectRoot))
            assertEquals(1, selections)
            assertNull(requests.resolve(pending.request, CurrentChannelReadiness.Ready(channels), channels.first().id))
            assertEquals(ApplianceLaunchState.Idle, requests.state.value)
        }
    }
}
