package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.rememberBrowseConnectionFailure
import at.bernhardberger.tvhplayer.viewmodels.resolveChannelScopeState
import at.bernhardberger.tvhplayer.ui.components.awaitBrowsePreparation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Channels without saved channels shows the catalog alert inside its content area: its first button takes
 * focus, Left reaches the rail and Right returns, and arriving channels take focus back to the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class BrowseErrorAlertTest {
    @get:Rule val compose = createComposeRule()
    private var observation by mutableStateOf(SessionObservation.create(
        sessionState = SessionState.Connecting,
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(emptyList())),
    ))
    private var connection by mutableStateOf<ConnectionUiState>(ConnectionUiState.Ready)
    private var profileGeneration by mutableStateOf(0L)
    private val actions = mutableListOf<AppErrorAction>()

    @Test fun emptyChannelsShowTheNoChannelsAlertWithTheRailBesideIt() {
        show()
        awaitFocus("tv-recovery-primary")
        compose.onNode(hasTestTag("tv-recovery-primary") and hasText("Retry", substring = true)).assertIsFocused()
        compose.onNodeWithTag("tv-recovery-title", useUnmergedTree = true).assertExists()
        key(Key.DirectionRight)
        compose.onNode(hasTestTag("tv-recovery-secondary") and hasText("Connection settings", substring = true)).assertIsFocused()
        key(Key.DirectionCenter)
        assertEquals(listOf(AppErrorAction.CONNECTION_SETTINGS), actions)
        key(Key.DirectionLeft)
        compose.onNodeWithTag("tv-recovery-primary").assertIsFocused()
        key(Key.DirectionLeft)
        awaitFocus("nav-channels")
        key(Key.DirectionRight)
        awaitFocus("tv-recovery-primary")
        key(Key.DirectionCenter)
        assertEquals(listOf(AppErrorAction.CONNECTION_SETTINGS, AppErrorAction.RETRY), actions)
    }

    @Test fun aFailureWithoutSavedChannelsIsItsCatalogAlertAndChannelsTakeFocusBack() {
        connection = ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY)
        observation = SessionObservation.create(sessionState = SessionState.Connecting)
        show()
        awaitFocus("tv-recovery-primary")
        compose.onNode(hasTestTag("tv-recovery-title") and hasText("Can’t reach the server", substring = true),
            useUnmergedTree = true).assertExists()
        // Synchronization means the server answered: the alert leaves, and focus stays in Channels
        // instead of falling to the rail.
        compose.runOnIdle { connection = ConnectionUiState.SyncingChannels }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasTestTag("tv-recovery-overlay")).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasTestTag("nav-channels") and isFocused()).fetchSemanticsNodes().size)
        compose.onNode(hasContentDescription("View options") and isFocused()).assertExists()
        compose.runOnIdle {
            connection = ConnectionUiState.Ready
            observation = SessionObservation.create(sessionState = SessionState.Connecting,
                channelState = ChannelRepositoryState.Current(ChannelCatalog.create(
                    (1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) })))
        }
        awaitFocus("channel-row-1")
    }

    @Test fun channelsReplacingTheFocusedAlertTakeItsFocus() {
        show()
        awaitFocus("tv-recovery-primary")
        compose.runOnIdle {
            observation = SessionObservation.create(sessionState = SessionState.Connecting,
                channelState = ChannelRepositoryState.Current(ChannelCatalog.create(
                    (1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) })))
        }
        compose.waitForIdle()
        awaitFocus("channel-row-1")
        assertEquals(0, compose.onAllNodes(hasTestTag("nav-channels") and isFocused()).fetchSemanticsNodes().size)
    }

    @Test fun channelsKeepTheAlertAndItsFocusThroughAutomaticAttempts() {
        connection = backoff()
        show()
        assertAutomaticAttemptsKeepFocus()
    }

    @Test fun recordingsKeepTheAlertAndItsFocusThroughAutomaticAttempts() {
        connection = backoff()
        show(recordings = true)
        assertAutomaticAttemptsKeepFocus()
    }

    @Test fun recordingsFocusAuthenticationFailureAfterPassiveLoading() {
        assertRecordingsFocusFailureAfterLoading(ConnectionUiState.Error(
            ConnectionFailureKind.AUTHENTICATION, SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED))
    }

    @Test fun recordingsFocusBackoffAfterPassiveLoading() {
        assertRecordingsFocusFailureAfterLoading(backoff())
    }

    @Test fun recordingsDoNotTakeFocusBackFromTheRailWhenAnAlertAppears() {
        connection = ConnectionUiState.Connecting
        show(recordings = true)
        key(Key.DirectionLeft)
        awaitFocus("nav-recordings")
        compose.runOnIdle { connection = backoff() }
        compose.waitForIdle()
        compose.onNodeWithTag("nav-recordings").assertIsFocused()
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        awaitFocus("tv-recovery-primary")
    }

    @Test fun recordingsDoNotTakeFocusFromADeliberatelySelectedHeaderTab() {
        connection = ConnectionUiState.Connecting
        show(recordings = true)
        key(Key.DirectionRight)
        compose.onNode(hasText("Schedule") and isFocused()).assertExists()
        compose.runOnIdle { connection = backoff() }
        compose.waitForIdle()
        compose.onNode(hasText("Schedule") and isFocused()).assertExists()
        key(Key.DirectionDown)
        awaitFocus("tv-recovery-primary")
    }

    @Test fun viewerRetryHidesTheOldFailureBeforeTheConnectionObservationChanges() {
        connection = ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY)
        show()
        awaitFocus("tv-recovery-primary")
        key(Key.DirectionCenter)
        assertEquals(listOf(AppErrorAction.RETRY), actions)
        compose.onNodeWithTag("tv-recovery-primary").assertDoesNotExist()
        compose.runOnIdle { connection = ConnectionUiState.Connecting }
        compose.waitForIdle()
        compose.onNodeWithTag("tv-recovery-primary").assertDoesNotExist()
        compose.runOnIdle { connection = backoff() }
        awaitFocus("tv-recovery-primary")
    }

    @Test fun aProfileChangeDropsTheOldProfilesFailureUntilTheNewOneFails() {
        val rejected = ConnectionUiState.Error(ConnectionFailureKind.AUTHENTICATION,
            SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED)
        connection = rejected
        show()
        awaitFocus("tv-recovery-primary")
        // The generation moves before the new profile's connection replaces the old failure.
        compose.runOnIdle { profileGeneration = 1L }
        compose.waitForIdle()
        compose.onNodeWithTag("tv-recovery-primary").assertDoesNotExist()
        compose.runOnIdle { connection = ConnectionUiState.Connecting }
        compose.waitForIdle()
        compose.onNodeWithTag("tv-recovery-primary").assertDoesNotExist()
        compose.runOnIdle { connection = backoff() }
        awaitFocus("tv-recovery-primary")
        compose.runOnIdle { connection = ConnectionUiState.Connecting }
        compose.waitForIdle()
        compose.onNode(hasTestTag("tv-recovery-title") and hasText("Can’t reach the server", substring = true),
            useUnmergedTree = true).assertExists()
    }

    @Test fun recordingsKeepFocusOnTheirTabsWhenTheFocusedAlertLeavesAndReturns() {
        connection = backoff()
        show(recordings = true)
        awaitFocus("tv-recovery-primary")
        compose.runOnIdle { connection = ConnectionUiState.SyncingChannels }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasTestTag("tv-recovery-overlay")).fetchSemanticsNodes().size)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasAnyAncestor(hasTestTag("recordings-mode-tabs")) and isFocused())
                .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasTestTag("recordings-mode-tabs") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { connection = backoff() }
        awaitFocus("tv-recovery-primary")
    }

    private fun assertRecordingsFocusFailureAfterLoading(failure: ConnectionUiState) {
        connection = ConnectionUiState.Connecting
        show(recordings = true)
        compose.runOnIdle { connection = failure }
        awaitFocus("tv-recovery-primary")
        compose.onNode(hasTestTag("tv-recovery-primary") and hasText("Connection settings", substring = true)).assertIsFocused()
    }

    private fun assertAutomaticAttemptsKeepFocus() {
        awaitFocus("tv-recovery-primary")
        listOf(ConnectionUiState.Connecting, backoff(), ConnectionUiState.Reconnecting,
            backoff()).forEach { next ->
            compose.runOnIdle { connection = next }
            compose.waitForIdle()
            compose.onNode(hasTestTag("tv-recovery-primary") and hasText("Connection settings", substring = true)).assertIsFocused()
        }
    }

    private fun backoff() = ConnectionUiState.Error(
        ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.AUTOMATIC_BACKOFF)

    private fun show(recordings: Boolean = false) {
        val session = FakeTvheadendSession(observation)
        val mutations = DvrMutationActions(session.dvrRepository)
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) { NoticeContext(0L, null) }
        compose.setContent {
            val browseFailure = rememberBrowseConnectionFailure(connection, profileGeneration) {
                actions += AppErrorAction.RETRY
            }
            val failure = browseFailure.failure
            val retry = browseFailure.retry
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    SideRail(currentRoute = if (recordings) AppDestination.RECORDINGS else AppDestination.CHANNELS,
                        showEpgMenu = true, onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                        if (recordings) RecordingsScreenContent(
                            observation = observation,
                            contentPadding = padding,
                            initialFocusEnabled = !drawerActive,
                            connectionUiState = connection,
                            connectionFailure = failure,
                            onRetry = retry,
                            dvrMutationActions = mutations,
                            notices = notices,
                        ) else ChannelsScreenContent(
                            view = ChannelsView(ChannelsLayout.LIST),
                            contentPadding = padding,
                            initialFocusEnabled = !drawerActive,
                            channelScopeState = resolveChannelScopeState(observation.channelState, null),
                            observation = observation,
                            tagNotice = false,
                            selectedId = { null },
                            playingChannelId = null,
                            connectionUiState = connection,
                            connectionFailure = failure,
                            onSelectChannel = {},
                            onSelectTag = {},
                            onDismissTagNotice = {},
                            onRetryConnection = retry,
                            onOpenConnectionSettings = { actions += AppErrorAction.CONNECTION_SETTINGS },
                            onPlay = { _, _ -> },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun awaitFocus(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
    }

    // A key reaches the screen once it has prepared what it shows, as on a device where that takes milliseconds.
    private fun key(key: Key) {
        compose.awaitBrowsePreparation()
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
}
