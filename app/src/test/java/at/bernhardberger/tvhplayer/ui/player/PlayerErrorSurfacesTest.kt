package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.LivePlayerAlert
import at.bernhardberger.tvhplayer.core.PlaybackRetryCommand
import at.bernhardberger.tvhplayer.core.livePlayerAlert
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.playback.AppPlaybackDiagnostics
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackSource
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.common.hintRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
class PlayerErrorSurfacesTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var view: View

    @Before
    fun televisionFeature() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>().packageManager)
            .setSystemFeature("android.software.leanback", true)
    }

    @Test
    fun channelMessageShowsTitleReasonAndHintWithoutANumberAndNothingFocusable() {
        show { PlayerChannelMessage(visible = true, condition = AppErrorCondition.NO_FREE_ADAPTER) }

        assertInMessage(string(R.string.error_201_title))
        assertInMessage("${string(R.string.error_201_reason)} ${string(R.string.error_hint_try_later_or_other_channel)}")
        // Only those two lines: no error number, no raw detail line.
        assertEquals(2, compose.onAllNodes(hasAnyAncestor(hasTestTag(PlayerChannelMessageTag))).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused)).fetchSemanticsNodes().size)
    }

    @Test
    fun channelMessageShowsThePlayersOwnErrorOnlyWhenThisDevicesPlayerFailed() {
        var condition by mutableStateOf(AppErrorCondition.CHANNEL_PLAYBACK_FAILED)
        show { PlayerChannelMessage(visible = true, condition = condition, deviceFaultDetail = "DECODER_INIT_FAILED (4001)") }

        assertInMessage(string(R.string.error_210_title))
        compose.onNodeWithTag(PlayerChannelMessageDetailTag).assert(hasText("DECODER_INIT_FAILED (4001)"))

        compose.runOnIdle { condition = AppErrorCondition.STREAM_STOPPED }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasTestTag(PlayerChannelMessageDetailTag)).fetchSemanticsNodes().size)
    }

    @Test
    fun connectionAlertShowsTheCatalogButtonsInOrderWithTheFirstFocused() {
        var closed = 0
        val retries = mutableListOf<PlaybackRetryCommand>()
        val alert = livePlayerAlert(
            ConnectionState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY),
            AppPlaybackState.Playing,
            null,
        )
        show {
            LivePlayerAlertOverlay(
                visible = true,
                alert = alert,
                onRetry = { retries += it },
                onConnectionSettings = {},
                onClose = { closed++ },
            )
        }

        compose.onNode(hasText(string(R.string.error_101_title))).assertExists()
        compose.onNode(hasText(string(R.string.error_101_reason), substring = true)).assertExists()
        compose.onNode(hasText(string(R.string.error_hint_check_server), substring = true)).assertExists()
        // An alert has at most two buttons: the first fix and Close.
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Retry")).assertIsFocused()
        compose.onNodeWithTag("tv-recovery-secondary").assert(hasText("Close"))
        compose.onNodeWithTag("tv-recovery-tertiary").assertDoesNotExist()

        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(PlaybackRetryCommand.RECONNECT), retries)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("tv-recovery-secondary").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("tv-recovery-secondary").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, closed)
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithTag("tv-recovery-primary").assertIsFocused()
    }

    @Test
    fun signInAlertOffersConnectionSettingsAndClose() {
        var settings = 0
        val alert = livePlayerAlert(
            ConnectionState.Error(ConnectionFailureKind.AUTHENTICATION, SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED),
            AppPlaybackState.Playing,
            null,
        )
        show {
            LivePlayerAlertOverlay(
                visible = true,
                alert = alert,
                onRetry = {},
                onConnectionSettings = { settings++ },
                onClose = {},
            )
        }

        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Connection settings")).assertIsFocused()
        compose.onNodeWithTag("tv-recovery-secondary").assert(hasText("Close"))
        compose.onNodeWithTag("tv-recovery-tertiary").assertDoesNotExist()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, settings)
    }

    @Test
    fun userLimitAlertRetriesTheChannel() {
        val retries = mutableListOf<PlaybackRetryCommand>()
        show {
            LivePlayerAlertOverlay(
                visible = true,
                alert = livePlayerAlert(ConnectionState.Connected, AppPlaybackState.Playing, AppErrorCondition.USER_LIMIT),
                onRetry = { retries += it },
                onConnectionSettings = {},
                onClose = {},
            )
        }

        compose.onNode(hasText(string(R.string.error_208_title))).assertExists()
        assertEquals(0, compose.onAllNodes(hasText("208", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Retry")).assertIsFocused()
        compose.onNodeWithTag("tv-recovery-secondary").assert(hasText("Close"))
        assertEquals(0, compose.onAllNodes(hasTestTag("tv-recovery-tertiary")).fetchSemanticsNodes().size)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(PlaybackRetryCommand.RETRY_LIVE), retries)
    }

    @Test
    fun liveRecoveryCountsDownWithCloseOnly() {
        compose.mainClock.autoAdvance = false
        show {
            LivePlayerAlertOverlay(
                visible = true,
                alert = LivePlayerAlert.Recovering(retryDelayMillis = 2_500L),
                onRetry = {},
                onConnectionSettings = {},
                onClose = {},
            )
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("tv-recovery-title").assert(hasText("Reconnecting…"))
        compose.onNodeWithTag("tv-recovery-status").assert(hasText("Next attempt in 3 s"))
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Close"))
        assertEquals(0, compose.onAllNodes(hasTestTag("tv-recovery-secondary")).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasTestTag("tv-recovery-number")).fetchSemanticsNodes().size)

        compose.mainClock.advanceTimeBy(1_000L)
        compose.onNodeWithTag("tv-recovery-status").assert(hasText("Next attempt in 2 s"))
        compose.mainClock.advanceTimeBy(2_000L)
        compose.onNodeWithTag("tv-recovery-title").assert(hasText("Reconnecting…"))
        assertEquals(0, compose.onAllNodes(hasTestTag("tv-recovery-status")).fetchSemanticsNodes().size)
    }

    @Test
    fun hidingTheAlertDoesNotRestartTheCountdown() {
        compose.mainClock.autoAdvance = false
        val visible = mutableStateOf(true)
        show {
            LivePlayerAlertOverlay(
                visible = visible.value,
                alert = LivePlayerAlert.Recovering(retryDelayMillis = 10_500L),
                onRetry = {},
                onConnectionSettings = {},
                onClose = {},
            )
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("tv-recovery-status").assert(hasText("Next attempt in 11 s"))
        compose.mainClock.advanceTimeBy(1_000L)
        compose.onNodeWithTag("tv-recovery-status").assert(hasText("Next attempt in 10 s"))

        // A layer over the alert hides it while the attempt's time runs on.
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_800L)
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500L)
        compose.onNodeWithTag("tv-recovery-status").assert(hasText("Next attempt in 6 s"))
    }

    @Test
    fun theAlertTextIsOneLiveRegionAndTheCountdownStaysOutsideIt() {
        compose.mainClock.autoAdvance = false
        show {
            LivePlayerAlertOverlay(
                visible = true,
                alert = LivePlayerAlert.Recovering(retryDelayMillis = 5_500L),
                onRetry = {},
                onConnectionSettings = {},
                onClose = {},
            )
        }
        compose.mainClock.advanceTimeByFrame()
        val liveRegion = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
        compose.onNodeWithTag("tv-recovery-announcement").assert(liveRegion)
        compose.onNodeWithTag("tv-recovery-title").assert(hasAnyAncestor(hasTestTag("tv-recovery-announcement")))
        compose.onNodeWithTag("tv-recovery-status")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
            .assert(!hasAnyAncestor(liveRegion))
        // The pane's title is the stable text too, so the countdown never renames the pane.
        compose.onNodeWithTag("tv-recovery-overlay")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Reconnecting…"))
        compose.mainClock.advanceTimeBy(1_000L)
        compose.onNodeWithTag("tv-recovery-status").assert(hasText("Next attempt in 5 s"))
        compose.onNodeWithTag("tv-recovery-overlay")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Reconnecting…"))
    }

    @Test
    fun theLiveRegionHoldsTheTitleReasonHintAndNumber() {
        show {
            LivePlayerAlertOverlay(
                visible = true,
                alert = livePlayerAlert(ConnectionState.Connected, AppPlaybackState.Playing, AppErrorCondition.USER_LIMIT),
                onRetry = {},
                onConnectionSettings = {},
                onClose = {},
            )
        }
        val inLiveRegion = hasAnyAncestor(
            hasTestTag("tv-recovery-announcement") and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion),
        )
        for (text in listOf(string(R.string.error_208_title), string(R.string.error_208_reason)) +
            listOfNotNull(AppErrorCondition.USER_LIMIT.hintRes()?.let(::string))) {
            compose.onNode(hasText(text, substring = true) and inLiveRegion).assertExists()
        }
        // The buttons are not part of it.
        compose.onNodeWithTag("tv-recovery-primary").assert(!hasAnyAncestor(hasTestTag("tv-recovery-announcement")))
    }

    @Test
    fun countdownRoundsUpAndEndsWhenTheAttemptIsDue() {
        assertNull(reconnectCountdownSeconds(0L))
        assertNull(reconnectCountdownSeconds(-5L))
        assertEquals(1, reconnectCountdownSeconds(1L))
        assertEquals(1, reconnectCountdownSeconds(1_000L))
        assertEquals(3, reconnectCountdownSeconds(2_500L))
    }

    @Test
    fun statsOverlayShowsEveryRawValueOfAFailure() {
        val failed = AppPlaybackState.Failed(
            reason = AppPlaybackFailureReason.OTHER,
            targetResult = PlaybackTargetResult.SHUT_DOWN,
            playerErrorCode = "ERROR_CODE_IO_UNSPECIFIED",
            playerErrorClassification = "IllegalStateException · Example.run:12",
            recoveryReason = PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED,
        )
        show {
            PlaybackStatsOverlay(
                diagnostics = AppPlaybackDiagnostics(source = AppPlaybackSource.LIVE_TV, state = failed),
                aspectRatio = AspectRatioMode.FIT,
            )
        }

        for (value in listOf(
            PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED.toString(),
            "ERROR_CODE_IO_UNSPECIFIED",
            "IllegalStateException · Example.run:12",
            PlaybackTargetResult.SHUT_DOWN.toString(),
        )) {
            compose.onNode(hasText(value)).assertExists()
        }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun assertInMessage(text: String) {
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(PlayerChannelMessageTag))).assertExists()
    }

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private fun press(keyCode: Int) {
        compose.runOnUiThread {
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        compose.waitForIdle()
    }
}
