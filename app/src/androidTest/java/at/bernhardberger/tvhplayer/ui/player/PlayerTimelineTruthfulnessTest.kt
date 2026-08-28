package at.bernhardberger.tvhplayer.ui.player

import android.content.res.Configuration
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import at.bernhardberger.tvhplayer.core.PlayerBackAction
import at.bernhardberger.tvhplayer.core.PlayerForegroundContext
import at.bernhardberger.tvhplayer.core.PlayerSeekPreviewPhase
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.core.SeekbarDomain
import at.bernhardberger.tvhplayer.core.SeekbarRange
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.core.playerBackAction
import at.bernhardberger.tvhplayer.core.playerForegroundLayer
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import java.util.Locale
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PlayerTimelineTruthfulnessTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun liveAndBehindLivePresentationShareTheActionBoundary() {
        var positionMs by mutableLongStateOf(-5_000L)
        setLiveOverlay(positionMs = { positionMs })

        composeRule.onNodeWithText("Live").assertIsDisplayed()
        composeRule.onNodeWithTag("player-go-live").assertDoesNotExist()
        composeRule.onNodeWithTag("player-seekbar")
            .assertContentDescriptionEquals(
                "Timeshift position Live. Buffer starts 10:00 behind live."
            )

        composeRule.runOnIdle { positionMs = -6_000L }

        composeRule.onNodeWithText("0:06 behind live").assertIsDisplayed()
        composeRule.onNodeWithTag("player-go-live").assertExists()
        composeRule.onNodeWithTag("player-seekbar")
            .assertContentDescriptionEquals(
                "Timeshift position 0:06 behind live. Buffer starts 10:00 behind live."
            )
    }

    @Test
    fun hiddenTimeshiftPreviewShowsTargetDeltaBoundaryAndLiveEdgeInsideTvBounds() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                TVHeadendPlayerTheme {
                    Box(
                        Modifier
                            .size(960.dp, 540.dp)
                            .testTag("timeline-test-viewport")
                    ) {
                        val state = AppTimeshiftState(
                            available = true,
                            bufferStartMs = -900_000L,
                            positionMs = -90_000L,
                            liveEdgeMs = 0L,
                        )
                        TimeshiftSeekPreview(
                            state = state,
                            decision = TimeshiftSeekDecision(
                                targetMs = -120_000L,
                                deltaMs = -30_000L,
                                clamped = false,
                            ),
                            nowEpochSec = 5_400L,
                            programmeStartSec = 3_600L,
                            programmeStopSec = 7_200L,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }

        val viewport = composeRule.onNodeWithTag("timeline-test-viewport")
            .fetchSemanticsNode().boundsInRoot
        val preview = composeRule.onNodeWithTag("timeshift-seek-preview")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(960f, viewport.width, 1f)
        assertEquals(540f, viewport.height, 1f)
        assertTrue(preview.left >= viewport.left)
        assertTrue(preview.top >= viewport.top)
        assertTrue(preview.right <= viewport.right)
        assertTrue(preview.bottom <= viewport.bottom)
        composeRule.onNodeWithTag("timeshift-preview-target", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("−0:30", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("2:00 behind live", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Live", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(
            "timeshift-preview-rewindable-boundary",
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag("timeshift-preview-live-edge", useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithTag("timeshift-seek-preview")
            .assertContentDescriptionEquals(
                "Seek target 28:00. Cumulative change −0:30. " +
                    "2:00 behind live. Buffer starts 15:00 behind live."
            )
    }

    @Test
    fun preProgrammeTimeshiftTargetUsesTruthfulOffsetAndBoundaryState() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                val state = AppTimeshiftState(
                    available = true,
                    bufferStartMs = -3_600_000L,
                    positionMs = -1_900_000L,
                    liveEdgeMs = 0L,
                )
                TimeshiftSeekPreview(
                    state = state,
                    decision = TimeshiftSeekDecision(
                        targetMs = -2_000_000L,
                        deltaMs = -100_000L,
                        clamped = false,
                    ),
                    nowEpochSec = 5_400L,
                    programmeStartSec = 3_600L,
                    programmeStopSec = 7_200L,
                )
            }
        }

        composeRule.onNodeWithText("−33:20", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Buffer: 1:00:00 ago", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("0:00", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(
            "timeshift-preview-rewindable-boundary",
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag(
            "timeshift-preview-rewindable-overflow",
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun focusedSeekbarHandlesDpadAndExposesAccessibilitySeekActions() {
        var targetMs = -1L
        composeRule.setContent {
            TVHeadendPlayerTheme {
                PlaybackSeekbar(
                    range = SeekbarRange(
                        domain = SeekbarDomain.RECORDING,
                        startMs = 0L,
                        endMs = 120_000L,
                        positionMs = 60_000L,
                    ),
                    onSeekTo = { targetMs = it },
                    modifier = Modifier.testTag("focused-seekbar"),
                )
            }
        }

        val seekbar = composeRule.onNodeWithTag("focused-seekbar").requestFocus()
        seekbar.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionLeft) }
        composeRule.runOnIdle { assertEquals(30_000L, targetMs) }
        seekbar.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionRight) }
        composeRule.runOnIdle { assertEquals(90_000L, targetMs) }

        val actions = seekbar.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("−30 seconds", "+30 seconds"), actions.map { it.label })
        composeRule.runOnIdle {
            assertTrue(actions.first().action())
            assertEquals(30_000L, targetMs)
        }
    }

    @Test
    fun accessibilitySeekActionsFollowRecordingEndpointsAndLiveTolerance() {
        var range by mutableStateOf(
            SeekbarRange(SeekbarDomain.RECORDING, 0L, 120_000L, 0L)
        )
        composeRule.setContent {
            TVHeadendPlayerTheme {
                PlaybackSeekbar(
                    range = range,
                    onSeekTo = {},
                    modifier = Modifier.testTag("boundary-seekbar"),
                )
            }
        }

        assertEquals(listOf("+30 seconds"), accessibilityActionLabels("boundary-seekbar"))
        composeRule.runOnIdle {
            range = range.copy(positionMs = range.endMs)
        }
        assertEquals(listOf("−30 seconds"), accessibilityActionLabels("boundary-seekbar"))

        composeRule.runOnIdle {
            range = SeekbarRange(
                SeekbarDomain.TIMESHIFT,
                startMs = -120_000L,
                endMs = 0L,
                positionMs = -5_000L,
            )
        }
        assertEquals(listOf("−30 seconds"), accessibilityActionLabels("boundary-seekbar"))
        val liveProgress = composeRule.onNodeWithTag("boundary-seekbar")
            .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(range.progress, liveProgress.current, 0.001f)

        composeRule.runOnIdle { range = range.copy(positionMs = -6_000L) }
        assertEquals(
            listOf("−30 seconds", "+30 seconds"),
            accessibilityActionLabels("boundary-seekbar"),
        )
        composeRule.runOnIdle { range = range.copy(positionMs = range.startMs) }
        assertEquals(listOf("+30 seconds"), accessibilityActionLabels("boundary-seekbar"))
    }

    @Test
    fun hiddenSeekBackCancelsPendingThenOnlyDismissesDispatchedFeedback() {
        var phase by mutableStateOf(PlayerSeekPreviewPhase.PENDING)
        var cancelled = 0
        var dismissed = 0
        var closed = 0
        awaitWindowFocus()
        composeRule.setContent {
            val rootFocus = remember { FocusRequester() }
            val layer = playerForegroundLayer(previewContext(phase))
            val dispatchBack = rememberPlayerBackDispatcher {
                when (playerBackAction(PlayerSurface.LIVE, false, layer)) {
                    PlayerBackAction.CANCEL_PENDING_SEEK -> {
                        cancelled++
                        phase = PlayerSeekPreviewPhase.NONE
                    }
                    PlayerBackAction.DISMISS_SEEK_FEEDBACK -> {
                        dismissed++
                        phase = PlayerSeekPreviewPhase.NONE
                    }
                    PlayerBackAction.CLOSE_PLAYER -> closed++
                    else -> Unit
                }
            }
            LaunchedEffect(Unit) { rootFocus.requestFocus() }
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("timeshift-preview-root")
                    .onPreviewKeyEvent(dispatchBack)
                    .focusRequester(rootFocus)
                    .focusable()
            )
        }

        dispatchBack(AndroidKeyEvent.ACTION_DOWN)
        dispatchBack(AndroidKeyEvent.ACTION_DOWN, repeatCount = 1)
        dispatchBack(AndroidKeyEvent.ACTION_UP)
        composeRule.runOnIdle {
            assertEquals(1, cancelled)
            assertEquals(0, dismissed)
            assertEquals(0, closed)
            phase = PlayerSeekPreviewPhase.DISPATCHED
        }

        dispatchBack(AndroidKeyEvent.ACTION_DOWN)
        dispatchBack(AndroidKeyEvent.ACTION_UP)
        composeRule.runOnIdle {
            assertEquals(1, cancelled)
            assertEquals(1, dismissed)
            assertEquals(0, closed)
        }

        dispatchBack(AndroidKeyEvent.ACTION_DOWN)
        dispatchBack(AndroidKeyEvent.ACTION_UP)
        composeRule.runOnIdle { assertEquals(1, closed) }
    }

    @Test
    fun recordingUnknownDurationsArePassiveAndKnownDurationRestoresSeekFocus() {
        var durationMs by mutableLongStateOf(C.TIME_UNSET)
        var growing by mutableStateOf(true)
        setRecordingOverlay(durationMs = { durationMs }, growing = { growing })

        composeRule.onNodeWithText("Still recording", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("recording-seekbar").assertDoesNotExist()
        assertEquals(0, progressSemanticsCount())
        composeRule.onNodeWithTag("recording-play-pause").requestFocus().performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.DirectionUp)
        }
        composeRule.onNodeWithTag("recording-play-pause").assertIsFocused()

        composeRule.runOnIdle {
            growing = false
        }
        composeRule.onNodeWithText("Duration unavailable", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("recording-seekbar").assertDoesNotExist()
        assertEquals(0, progressSemanticsCount())

        composeRule.runOnIdle { durationMs = 120_000L }
        composeRule.onNodeWithTag("recording-playback-options").requestFocus()
        composeRule.runOnIdle { durationMs = C.TIME_UNSET }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("recording-playback-options").assertIsFocused()

        composeRule.runOnIdle { durationMs = 120_000L }
        composeRule.onNodeWithTag("recording-play-pause").requestFocus().performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.DirectionUp)
        }
        composeRule.onNodeWithTag("recording-seekbar").assertIsFocused()
        assertEquals(1, progressSemanticsCount())

        composeRule.runOnIdle { durationMs = C.TIME_UNSET }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("recording-play-pause").assertIsFocused()
        assertEquals(0, progressSemanticsCount())
    }

    @Test
    fun unknownRecordingSeekPreviewAnnouncesTargetDeltaAndDurationStatus() {
        var growing by mutableStateOf(true)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                RecordingSeekPreview(
                    targetMs = 75_000L,
                    originMs = 45_000L,
                    durationMs = C.TIME_UNSET,
                    growing = growing,
                )
            }
        }

        composeRule.onNodeWithTag("recording-seek-preview")
            .assertContentDescriptionEquals(
                "Seek target 1:15. Cumulative change +0:30. Still recording."
            )

        composeRule.runOnIdle { growing = false }
        composeRule.onNodeWithTag("recording-seek-preview")
            .assertContentDescriptionEquals(
                "Seek target 1:15. Cumulative change +0:30. Duration unavailable."
            )
    }

    @Test
    fun germanUnknownRecordingStatusFitsExactTvViewportAtLargeFontScale() {
        composeRule.setContent {
            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val germanConfiguration = remember(configuration) {
                Configuration(configuration).apply { setLocale(Locale.GERMAN) }
            }
            val germanContext = remember(context, germanConfiguration) {
                context.createConfigurationContext(germanConfiguration)
            }
            CompositionLocalProvider(
                LocalContext provides germanContext,
                LocalConfiguration provides germanConfiguration,
                LocalResources provides germanContext.resources,
                LocalDensity provides Density(1f, 1.3f),
            ) {
                val imageLoader = ImageLoader.Builder(LocalContext.current).build()
                TVHeadendPlayerTheme {
                    Box(
                        Modifier
                            .size(960.dp, 540.dp)
                            .testTag("recording-timeline-viewport")
                    ) {
                        RecordingOverlayControls(
                            imageLoader = imageLoader,
                            piconPath = null,
                            title = "Eine außergewöhnlich lange Aufnahmebezeichnung",
                            subtitle = null,
                            channelName = "Das Erste",
                            positionMs = 3_661_000L,
                            durationMs = C.TIME_UNSET,
                            growing = false,
                            nowSec = 5_400L,
                            isPlaying = true,
                            controlsVisible = true,
                            optionsOpen = false,
                            onTogglePlayPause = {},
                            onSeek = {},
                            onStopPlayback = {},
                            onUserInteraction = {},
                            showStop = true,
                            onOpenOptions = {},
                            onOpenInfo = {},
                        )
                    }
                }
            }
        }

        val viewport = composeRule.onNodeWithTag("recording-timeline-viewport")
            .fetchSemanticsNode().boundsInRoot
        val status = composeRule.onNodeWithTag("recording-duration-status")
            .fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithText("Dauer nicht verfügbar", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("1:01:01", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(status.left >= viewport.left)
        assertTrue(status.top >= viewport.top)
        assertTrue(status.right <= viewport.right)
        assertTrue(status.bottom <= viewport.bottom)
    }

    private fun setLiveOverlay(positionMs: () -> Long) {
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                OverlayControlsTv(
                    imageLoader = imageLoader,
                    channelNumber = 1,
                    channelName = "Channel",
                    piconPath = null,
                    nowEvent = EpgEvent.create(
                        id = EventId(1),
                        channelId = ChannelId(1),
                        start = Instant.fromEpochSeconds(3_600L),
                        stop = Instant.fromEpochSeconds(7_200L),
                        title = "Programme",
                    ),
                    nextEvent = null,
                    nowSec = 5_400L,
                    controlsVisible = true,
                    optionsOpen = false,
                    onOpenChannels = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    onOpenOptions = {},
                    timeshiftState = AppTimeshiftState(
                        available = true,
                        bufferStartMs = -600_000L,
                        positionMs = positionMs(),
                        liveEdgeMs = 0L,
                    ),
                    timeshiftFeedback = null,
                    onToggleTimeshiftPause = {},
                    onSeekTimeshift = {},
                    onGoLive = {},
                )
            }
        }
    }

    private fun setRecordingOverlay(durationMs: () -> Long, growing: () -> Boolean) {
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                RecordingOverlayControls(
                    imageLoader = imageLoader,
                    piconPath = null,
                    title = "Recording",
                    subtitle = null,
                    channelName = "Channel",
                    positionMs = 45_000L,
                    durationMs = durationMs(),
                    growing = growing(),
                    nowSec = 5_400L,
                    isPlaying = true,
                    controlsVisible = true,
                    optionsOpen = false,
                    onTogglePlayPause = {},
                    onSeek = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    showStop = true,
                    onOpenOptions = {},
                    onOpenInfo = {},
                )
            }
        }
    }

    private fun progressSemanticsCount(): Int = composeRule.onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
    ).fetchSemanticsNodes().size

    private fun accessibilityActionLabels(tag: String): List<String> =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .map { it.label }

    private fun previewContext(phase: PlayerSeekPreviewPhase) = PlayerForegroundContext(
        confirmationVisible = false,
        infoVisible = false,
        optionsPage = null,
        numberEntryVisible = false,
        channelDrawerVisible = false,
        recoveryVisible = false,
        terminalErrorVisible = false,
        seekPreviewPhase = phase,
        controlsVisible = false,
        statsEnabled = false,
    )

    private fun dispatchBack(action: Int, repeatCount: Int = 0) {
        val eventTime = android.os.SystemClock.uptimeMillis()
        composeRule.onNodeWithTag("timeshift-preview-root").performKeyPress(
            ComposeKeyEvent(
                AndroidKeyEvent(
                    eventTime,
                    eventTime,
                    action,
                    AndroidKeyEvent.KEYCODE_BACK,
                    repeatCount,
                )
            )
        )
    }

    private fun awaitWindowFocus() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.activity.hasWindowFocus()
        }
    }
}
