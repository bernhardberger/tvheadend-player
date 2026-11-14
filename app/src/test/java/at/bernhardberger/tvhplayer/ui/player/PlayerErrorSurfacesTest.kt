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
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackFailureCategory
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.LivePlayerAlert
import at.bernhardberger.tvhplayer.core.PlaybackRetryCommand
import at.bernhardberger.tvhplayer.core.RecordingFailureDetail
import at.bernhardberger.tvhplayer.core.deviceFaultDetail
import at.bernhardberger.tvhplayer.core.livePlayerAlert
import at.bernhardberger.tvhplayer.core.recordingFailureDetail
import at.bernhardberger.tvhplayer.core.recordingPlayerAlert
import at.bernhardberger.tvhplayer.core.toAppError
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
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
import androidx.compose.ui.test.onNodeWithText
import at.bernhardberger.tvhplayer.ui.common.formatRecordingDate
import kotlin.time.Instant
import android.os.Looper
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import java.time.Duration

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
        var stats by mutableStateOf<AppPlaybackState.Failed?>(null)
        var rows by mutableStateOf(emptyList<String>())
        var detail by mutableStateOf("DECODER_INIT_FAILED (4001)")
        show {
            PlayerChannelMessage(visible = true, condition = condition, deviceFaultDetail = detail,
                statsFailure = stats, statsRows = { rows })
        }

        assertInMessage(string(R.string.error_210_title))
        compose.onNodeWithTag(PlayerChannelMessageDetailTag).assert(hasText("DECODER_INIT_FAILED (4001)"))

        compose.runOnIdle { condition = AppErrorCondition.STREAM_STOPPED }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasTestTag(PlayerChannelMessageDetailTag)).fetchSemanticsNodes().size)

        // Without a failure, Stats lend the message their live path rows, one per line.
        compose.runOnIdle { rows = listOf("Tuner: No lock • 75% signal", "Tvheadend: 250 ms queue") }
        compose.onNodeWithTag(PlayerChannelMessageDetailTag).assert(hasText("Tuner: No lock • 75% signal\nTvheadend: 250 ms queue"))

        // With Stats on, the same faint line carries the failure's rows, after the player's own error
        // and without the error code that error already names.
        val code = "${string(R.string.stats_error_code)}: ERROR_CODE_DECODER_INIT_FAILED"
        val classification = "${string(R.string.stats_error_classification)}: MediaCodecRenderer.DecoderInitializationException"
        compose.runOnIdle {
            stats = AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER, playerErrorCode = "ERROR_CODE_DECODER_INIT_FAILED",
                playerErrorClassification = "MediaCodecRenderer.DecoderInitializationException", deviceFault = true)
        }
        compose.onNodeWithTag(PlayerChannelMessageDetailTag).assert(hasText("$code\n$classification"))
        compose.runOnIdle { condition = AppErrorCondition.CHANNEL_PLAYBACK_FAILED }
        compose.onNodeWithTag(PlayerChannelMessageDetailTag).assert(hasText("DECODER_INIT_FAILED (4001)\n$classification"))

        // Only the error code row repeats the detail, and only as the whole code: neither a recovery
        // reason inside a longer detail token nor UNSPECIFIED inside IO_UNSPECIFIED is a repeat.
        val reason = "${string(R.string.stats_recovery_reason)}: LIVE_BUFFER_EXHAUSTED"
        compose.runOnIdle {
            detail = "IO_UNSPECIFIED (2000) · LIVE_BUFFER_EXHAUSTED_RETIREMENT_FAILED"
            stats = AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER, playerErrorCode = "ERROR_CODE_UNSPECIFIED",
                recoveryReason = PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED, deviceFault = true)
        }
        compose.onNodeWithTag(PlayerChannelMessageDetailTag)
            .assert(hasText("$detail\n$reason\n${string(R.string.stats_error_code)}: ERROR_CODE_UNSPECIFIED"))
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
    fun aConnectionThatRetriesByItselfShowsTheRingAndAnAlertThatNeedsTheViewerTheIcon() {
        compose.mainClock.autoAdvance = false
        var connection by mutableStateOf<ConnectionState>(
            ConnectionState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.AUTOMATIC_BACKOFF),
        )
        show {
            LivePlayerAlertOverlay(
                visible = true,
                alert = livePlayerAlert(connection, AppPlaybackState.Playing, null),
                onRetry = {},
                onConnectionSettings = {},
                onClose = {},
            )
        }
        val ring = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Close"))
        assertEquals(1, compose.onAllNodes(ring).fetchSemanticsNodes().size)

        compose.runOnIdle {
            connection = ConnectionState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Retry"))
        assertEquals(0, compose.onAllNodes(ring).fetchSemanticsNodes().size)
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
                statsFailure = AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER,
                    recoveryReason = PlaybackRecoveryReason.LIVE_ENDED),
            )
        }

        compose.onNode(hasText(string(R.string.error_208_title))).assertExists()
        // With Stats on, the live alert carries the failure's raw rows like the recording one.
        compose.onNodeWithTag("tv-recovery-footnote").assert(hasText("${string(R.string.stats_recovery_reason)}: LIVE_ENDED"))
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
        compose.onNodeWithTag("tv-recovery-status").assert(hasText("Next attempt in 3s"))
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Close"))
        assertEquals(0, compose.onAllNodes(hasTestTag("tv-recovery-secondary")).fetchSemanticsNodes().size)

        compose.mainClock.advanceTimeBy(1_000L)
        compose.onNodeWithTag("tv-recovery-status").assert(hasText("Next attempt in 2s"))
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
        compose.onNodeWithTag("tv-recovery-status").assert(hasText("Next attempt in 11s"))
        compose.mainClock.advanceTimeBy(1_000L)
        compose.onNodeWithTag("tv-recovery-status").assert(hasText("Next attempt in 10s"))

        // A layer over the alert hides it while the attempt's time runs on.
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_800L)
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500L)
        compose.onNodeWithTag("tv-recovery-status").assert(hasText("Next attempt in 6s"))
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
        compose.onNodeWithTag("tv-recovery-status").assert(hasText("Next attempt in 5s"))
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
    fun recordingInterruptionRetriesTheRecordingThenCloses() {
        var closed = 0
        val retries = mutableListOf<PlaybackRetryCommand>()
        show {
            LivePlayerAlertOverlay(
                visible = true,
                alert = recordingAlert(AppPlaybackState.Failed(AppPlaybackFailureReason.RECORDING_READ_FAILED)),
                onRetry = { retries += it },
                onConnectionSettings = {},
                onClose = { closed++ },
                deviceFaultDetail = "DECODER_INIT_FAILED (4001)",
            )
        }

        compose.onNodeWithTag("tv-recovery-title").assert(hasText(string(R.string.error_301_title)))
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Retry")).assertIsFocused()
        compose.onNodeWithTag("tv-recovery-secondary").assert(hasText("Close"))
        compose.onNodeWithTag("tv-recovery-tertiary").assertDoesNotExist()
        // Not this device's player: no raw error line.
        compose.onNodeWithTag("tv-recovery-footnote").assertDoesNotExist()

        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(PlaybackRetryCommand.RESUME_RECORDING), retries)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("tv-recovery-secondary").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, closed)
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithTag("tv-recovery-primary").assertIsFocused()
    }

    @OptIn(ExperimentalTvheadendApi::class)
    @Test
    fun recordingPlayerFailureShowsThePlayersOwnErrorOutsideTheLiveRegion() {
        var failed by mutableStateOf(AppPlaybackState.Failed(
            AppPlaybackFailureReason.RECORDING_READ_FAILED,
            playerErrorCode = "ERROR_CODE_DECODER_INIT_FAILED",
            playerErrorNumber = 4001,
            deviceFault = true,
        ))
        var alert by mutableStateOf(recordingAlert(failed))
        var sdkFailure by mutableStateOf<RecordingFailureDetail?>(null)
        var stats by mutableStateOf(false)
        show {
            LivePlayerAlertOverlay(
                visible = true,
                alert = alert,
                onRetry = {},
                onConnectionSettings = {},
                onClose = {},
                deviceFaultDetail = failed.recordingFailureDetail(sdkFailure),
                statsFailure = failed.takeIf { stats },
            )
        }

        compose.onNodeWithTag("tv-recovery-title").assert(hasText(string(R.string.error_306_title)))
        compose.onNodeWithTag("tv-recovery-footnote")
            .assert(hasText("DECODER_INIT_FAILED (4001)"))
            .assert(!hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)))
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Retry")).assertIsFocused()
        compose.onNodeWithTag("tv-recovery-secondary").assert(hasText("Close"))

        // SDK notification can arrive after the player's failure; it updates the existing alert.
        val detail = "IO_UNSPECIFIED: Top bit not zero"
        compose.runOnIdle {
            failed = failed.copy(
                deviceFault = false,
                playerErrorCode = "ERROR_CODE_IO_UNSPECIFIED",
                playerErrorNumber = 2000,
            )
            sdkFailure = RecordingFailureDetail(RecordingPlaybackFailureCategory.MALFORMED_MEDIA, "ERROR_CODE_$detail")
            alert = recordingPlayerAlert(
                ConnectionState.Connected, false, failed,
                DvrEntry.create(DvrEntryId(1), state = DvrEntryState.COMPLETED), null,
                PlaybackRetryCommand.RESUME_RECORDING, sdkFailure,
            )
        }
        compose.onNodeWithTag("tv-recovery-title").assert(hasText(string(R.string.error_307_title)))
        compose.onNodeWithTag("tv-recovery-footnote")
            .assert(hasText(detail))
            .assert(!hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)))
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Close")).assertIsFocused()
        compose.onNodeWithTag("tv-recovery-secondary").assertDoesNotExist()

        // Stats add the failure's rows to the same footnote, except the code the SDK's detail already names.
        compose.runOnIdle {
            stats = true
            failed = failed.copy(playerErrorClassification = "IllegalStateException • Example.read:12")
        }
        compose.onNodeWithTag("tv-recovery-footnote").assert(hasText(
            "$detail\n${string(R.string.stats_error_classification)}: IllegalStateException • Example.read:12"))

        // A lost connection replaces the alert: the player's error goes with it.
        compose.runOnIdle {
            stats = false // The playback state is no longer Failed either.
            alert = recordingPlayerAlert(
                ConnectionState.Disconnected, true, AppPlaybackState.Idle, null, null, PlaybackRetryCommand.NONE,
            )
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tv-recovery-title").assert(hasText(string(R.string.error_110_title)))
        compose.onNodeWithTag("tv-recovery-footnote").assertDoesNotExist()
    }

    @Test
    fun recordingWithoutAFileOffersCloseOnly() {
        var closed = 0
        show {
            LivePlayerAlertOverlay(
                visible = true,
                alert = recordingAlert(
                    AppPlaybackState.Idle,
                    DvrEntry.create(DvrEntryId(1), state = DvrEntryState.RECORDING),
                    RecordingPlaybackAdmission.TargetUnavailable,
                ),
                onRetry = {},
                onConnectionSettings = {},
                onClose = { closed++ },
            )
        }

        compose.onNodeWithTag("tv-recovery-title").assert(hasText(string(R.string.error_303_title)))
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Close")).assertIsFocused()
        compose.onNodeWithTag("tv-recovery-secondary").assertDoesNotExist()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, closed)
    }

    @Test
    fun recordingThatHasNotStartedSaysWhenItStarts() {
        val start = Instant.fromEpochSeconds(1_792_000_000L)
        show {
            LivePlayerAlertOverlay(
                visible = true,
                alert = recordingAlert(
                    AppPlaybackState.Idle,
                    DvrEntry.create(DvrEntryId(1), state = DvrEntryState.SCHEDULED, start = start),
                    RecordingPlaybackAdmission.TargetUnavailable,
                ),
                onRetry = {},
                onConnectionSettings = {},
                onClose = {},
            )
        }

        compose.onNodeWithTag("tv-recovery-title").assert(hasText(string(R.string.error_302_title)))
        val context = ApplicationProvider.getApplicationContext<Application>()
        compose.onNodeWithText(context.getString(R.string.error_302_reason, formatRecordingDate(start.epochSeconds)), substring = true)
            .assertExists()
    }

    @Test
    fun countdownRoundsUpAndEndsWhenTheAttemptIsDue() {
        assertNull(reconnectCountdownSeconds(0L))
        assertNull(reconnectCountdownSeconds(-5L))
        assertEquals(1, reconnectCountdownSeconds(1L))
        assertEquals(1, reconnectCountdownSeconds(1_000L))
        assertEquals(3, reconnectCountdownSeconds(2_500L))
    }

    /** Equal diagnostics are dropped upstream, so the minute must end without a new observation. */
    @Test
    fun aStatsGrowthFactLeavesTheCardWhenItsMinuteEndsWithoutANewObservation() {
        var diagnostics by mutableStateOf(statsLiveDiagnostics())
        show { PlaybackStatsOverlay(diagnostics, AspectRatioMode.FIT) }
        compose.runOnIdle { diagnostics = statsLiveDiagnostics(serverDrops = 5) }
        val growth = hasText("+5 drops", substring = true)
        val window = hasText("last 60 s")
        val card = compose.onNodeWithTag("playback-stats-overlay")
        val flagged = SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)
        compose.onNode(growth, useUnmergedTree = true).assertExists()
        compose.onNode(window, useUnmergedTree = true).assertExists()
        // The flag is a state, not a "!" read aloud as part of the values.
        card.assert(flagged).assert(!hasText("!", substring = true))

        compose.runOnIdle { diagnostics = statsLiveDiagnostics(serverDrops = 5) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        compose.mainClock.advanceTimeBy(30_000)
        compose.onNode(growth, useUnmergedTree = true).assertExists()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
        compose.mainClock.advanceTimeBy(31_000)
        compose.onNode(growth, useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(window, useUnmergedTree = true).assertDoesNotExist()
        card.assert(!flagged)
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

    private fun recordingAlert(
        state: AppPlaybackState,
        entry: DvrEntry? = DvrEntry.create(DvrEntryId(1), state = DvrEntryState.COMPLETED),
        admission: RecordingPlaybackAdmission? = null,
    ) = recordingPlayerAlert(ConnectionState.Connected, false, state, entry, admission, PlaybackRetryCommand.RESUME_RECORDING)

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
