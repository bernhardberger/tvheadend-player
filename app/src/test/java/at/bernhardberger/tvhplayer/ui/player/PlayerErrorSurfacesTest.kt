package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
    fun channelMessageShowsTitleReasonHintAndNumberAndNothingFocusable() {
        show { PlayerChannelMessage(visible = true, condition = AppErrorCondition.NO_FREE_ADAPTER) }

        assertInMessage(string(R.string.error_201_title))
        assertInMessage("${string(R.string.error_201_reason)} ${string(R.string.error_hint_try_later_or_other_channel)}")
        assertInMessage("Error 201")
        // Only those three lines: no raw detail line.
        assertEquals(3, compose.onAllNodes(hasAnyAncestor(hasTestTag(PlayerChannelMessageTag))).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused)).fetchSemanticsNodes().size)
    }

    @Test
    fun connectionAlertShowsTheCatalogButtonsInOrderWithTheFirstFocused() {
        var closed = 0
        var settings = 0
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
                onConnectionSettings = { settings++ },
                onClose = { closed++ },
            )
        }

        compose.onNode(hasText(string(R.string.error_101_title))).assertExists()
        compose.onNode(hasText(string(R.string.error_101_reason))).assertExists()
        compose.onNode(hasText(string(R.string.error_hint_check_server))).assertExists()
        compose.onNodeWithTag("tv-recovery-number").assertExists().assert(hasText("Error 101"))
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Retry")).assertIsFocused()
        compose.onNodeWithTag("tv-recovery-secondary").assert(hasText("Connection settings"))
        compose.onNodeWithTag("tv-recovery-tertiary").assert(hasText("Close"))

        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(PlaybackRetryCommand.RECONNECT), retries)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("tv-recovery-secondary").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, settings)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("tv-recovery-tertiary").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("tv-recovery-tertiary").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, closed)
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithTag("tv-recovery-secondary").assertIsFocused()
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
        compose.onNodeWithTag("tv-recovery-number").assert(hasText("Error 208"))
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
        compose.onNode(hasText("Reconnecting in 3 s…")).assertExists()
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Close"))
        assertEquals(0, compose.onAllNodes(hasTestTag("tv-recovery-secondary")).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasTestTag("tv-recovery-number")).fetchSemanticsNodes().size)

        compose.mainClock.advanceTimeBy(1_000L)
        compose.onNode(hasText("Reconnecting in 2 s…")).assertExists()
        compose.mainClock.advanceTimeBy(2_000L)
        compose.onNode(hasText("Reconnecting…")).assertExists()
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
