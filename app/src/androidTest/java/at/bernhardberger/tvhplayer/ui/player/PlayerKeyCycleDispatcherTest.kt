package at.bernhardberger.tvhplayer.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyPress
import at.bernhardberger.tvhplayer.core.PlayerKeyAction
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.core.RecordingPlaybackKeyAction
import at.bernhardberger.tvhplayer.core.playerKeyAction
import at.bernhardberger.tvhplayer.core.playerKeyActionStartsOpeningCycle
import at.bernhardberger.tvhplayer.core.playbackSuppressesRevealingKey
import at.bernhardberger.tvhplayer.core.recordingKeyActionStartsOpeningCycle
import at.bernhardberger.tvhplayer.core.recordingPlaybackKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PlayerKeyCycleDispatcherTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun liveCenterVariantsConsumeTheirCompleteOpeningCycles() {
        assertOpeningCycles(
            mode = DispatcherMode.LIVE_TIMESHIFT,
            openingKeyCodes = listOf(
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
            ),
            expectedPlaybackActions = { 1 },
        )
    }

    @Test
    fun liveVerticalRevealKeysConsumeTheirCompleteOpeningCycles() {
        assertOpeningCycles(
            mode = DispatcherMode.LIVE,
            openingKeyCodes = listOf(
                AndroidKeyEvent.KEYCODE_DPAD_UP,
                AndroidKeyEvent.KEYCODE_DPAD_DOWN,
            ),
        )
    }

    @Test
    fun liveSurfaceOpeningKeysConsumeTheirCompleteCycles() {
        assertOpeningCycles(
            mode = DispatcherMode.LIVE,
            openingKeyCodes = listOf(
                AndroidKeyEvent.KEYCODE_INFO,
                AndroidKeyEvent.KEYCODE_TV_CONTENTS_MENU,
                AndroidKeyEvent.KEYCODE_TV_NUMBER_ENTRY,
                AndroidKeyEvent.KEYCODE_BOOKMARK,
                AndroidKeyEvent.KEYCODE_DPAD_LEFT,
            ),
        )
    }

    @Test
    fun liveDrawerKeysReplaceVisibleControlsBeforeFocusingTheDrawer() {
        var generation by mutableIntStateOf(0)
        awaitWindowFocus()
        composeRule.setContent {
            key(generation) {
                DispatcherHarness(
                    mode = DispatcherMode.LIVE,
                    counts = DispatcherCounts(),
                    initialControlsVisible = true,
                )
            }
        }

        listOf(
            AndroidKeyEvent.KEYCODE_TV_CONTENTS_MENU,
            AndroidKeyEvent.KEYCODE_TV_NUMBER_ENTRY,
            AndroidKeyEvent.KEYCODE_BOOKMARK,
        ).forEach { keyCode ->
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(CONTROLS_VISIBLE).assertExists()
            composeRule.onNodeWithTag(DRAWER_VISIBLE).assertDoesNotExist()

            dispatchKey(OPENED_TARGET, AndroidKeyEvent.ACTION_DOWN, keyCode)
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CONTROLS_VISIBLE).assertDoesNotExist()
            composeRule.onNodeWithTag(DRAWER_VISIBLE).assertExists()
            dispatchKey(OPENED_TARGET, AndroidKeyEvent.ACTION_UP, keyCode)

            composeRule.runOnIdle { generation++ }
        }
    }

    @Test
    fun recordingRevealAndInfoKeysHaveLiveParity() {
        assertOpeningCycles(
            mode = DispatcherMode.RECORDING,
            openingKeyCodes = listOf(
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                AndroidKeyEvent.KEYCODE_DPAD_UP,
                AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                AndroidKeyEvent.KEYCODE_INFO,
            ),
            expectedPlaybackActions = { keyCode ->
                if (
                    keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                    keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                ) {
                    1
                } else {
                    0
                }
            },
        )
    }

    @Test
    fun seekableHorizontalRepeatsRemainDeliberateInput() {
        val counts = DispatcherCounts()
        awaitWindowFocus()
        composeRule.setContent {
            DispatcherHarness(
                mode = DispatcherMode.LIVE_TIMESHIFT,
                counts = counts,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DISPATCHER_ROOT).assertIsFocused()

        dispatchKey(DISPATCHER_ROOT, AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_LEFT)
        dispatchKey(
            DISPATCHER_ROOT,
            AndroidKeyEvent.ACTION_DOWN,
            AndroidKeyEvent.KEYCODE_DPAD_LEFT,
            repeatCount = 1,
        )
        dispatchKey(DISPATCHER_ROOT, AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DPAD_LEFT)

        composeRule.runOnIdle {
            assertEquals(2, counts.seekActions)
            assertEquals(0, counts.openingActions)
        }
    }

    @Test
    fun disposingRouteBeforeKeyUpClearsOpeningSuppression() {
        val mounted = mutableStateOf(true)
        val counts = DispatcherCounts()
        awaitWindowFocus()
        composeRule.setContent {
            if (mounted.value) {
                DispatcherHarness(mode = DispatcherMode.LIVE, counts = counts)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DISPATCHER_ROOT).assertIsFocused()

        dispatchKey(DISPATCHER_ROOT, AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_INFO)
        composeRule.runOnIdle { mounted.value = false }
        composeRule.runOnIdle { mounted.value = true }
        composeRule.waitForIdle()
        pressKey(DISPATCHER_ROOT, AndroidKeyEvent.KEYCODE_INFO)

        composeRule.runOnIdle { assertEquals(2, counts.openingActions) }
    }

    private fun assertOpeningCycles(
        mode: DispatcherMode,
        openingKeyCodes: List<Int>,
        expectedPlaybackActions: (Int) -> Int = { 0 },
    ) {
        val counts = DispatcherCounts()
        var generation by mutableIntStateOf(0)
        awaitWindowFocus()
        composeRule.setContent {
            key(generation) {
                DispatcherHarness(mode = mode, counts = counts)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DISPATCHER_ROOT).assertIsFocused()

        openingKeyCodes.forEach { openingKeyCode ->
            val openingActionsBefore = counts.openingActions
            val playbackActionsBefore = counts.playbackActions
            val targetEventsBefore = counts.openedTargetEvents

            dispatchKey(DISPATCHER_ROOT, AndroidKeyEvent.ACTION_DOWN, openingKeyCode)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(OPENED_TARGET).assertIsFocused()
            dispatchKey(
                OPENED_TARGET,
                AndroidKeyEvent.ACTION_DOWN,
                openingKeyCode,
                repeatCount = 1,
            )
            dispatchKey(OPENED_TARGET, AndroidKeyEvent.ACTION_UP, openingKeyCode)

            composeRule.runOnIdle {
                assertEquals(openingActionsBefore + 1, counts.openingActions)
                assertEquals(
                    playbackActionsBefore + expectedPlaybackActions(openingKeyCode),
                    counts.playbackActions,
                )
                assertEquals(targetEventsBefore, counts.openedTargetEvents)
            }

            pressKey(OPENED_TARGET, openingKeyCode)
            composeRule.runOnIdle {
                assertEquals(openingActionsBefore + 1, counts.openingActions)
                assertEquals(
                    playbackActionsBefore + expectedPlaybackActions(openingKeyCode),
                    counts.playbackActions,
                )
                assertTrue(counts.openedTargetEvents > targetEventsBefore)
                generation++
            }
            composeRule.waitForIdle()
        }
    }

    private fun pressKey(nodeTag: String, keyCode: Int) {
        dispatchKey(nodeTag, AndroidKeyEvent.ACTION_DOWN, keyCode)
        dispatchKey(nodeTag, AndroidKeyEvent.ACTION_UP, keyCode)
    }

    private fun awaitWindowFocus() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.activity.hasWindowFocus()
        }
    }

    private fun dispatchKey(
        nodeTag: String,
        action: Int,
        keyCode: Int,
        repeatCount: Int = 0,
    ) {
        val eventTime = android.os.SystemClock.uptimeMillis()
        composeRule.onNodeWithTag(nodeTag).performKeyPress(
            ComposeKeyEvent(
                AndroidKeyEvent(
                    eventTime,
                    eventTime,
                    action,
                    keyCode,
                    repeatCount,
                )
            )
        )
    }
}

private enum class DispatcherMode {
    LIVE,
    LIVE_TIMESHIFT,
    RECORDING,
}

private class DispatcherCounts {
    var openingActions = 0
    var playbackActions = 0
    var seekActions = 0
    var openedTargetEvents = 0
}

@Composable
private fun DispatcherHarness(
    mode: DispatcherMode,
    counts: DispatcherCounts,
    initialControlsVisible: Boolean = false,
) {
    val rootFocus = remember { FocusRequester() }
    val openedFocus = remember { FocusRequester() }
    var openingKeyCode by remember { mutableStateOf<Int?>(null) }
    var controlsVisible by remember { mutableStateOf(initialControlsVisible) }
    var infoOpen by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    val opened = controlsVisible || infoOpen || drawerOpen

    LaunchedEffect(Unit) { rootFocus.requestFocus() }
    LaunchedEffect(opened) {
        if (opened) openedFocus.requestFocus()
    }

    Box(
        modifier = Modifier
            .testTag(DISPATCHER_ROOT)
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                if (playbackSuppressesRevealingKey(openingKeyCode, keyCode)) {
                    if (event.type == KeyEventType.KeyUp) openingKeyCode = null
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (infoOpen || drawerOpen) return@onPreviewKeyEvent false

                when (mode) {
                    DispatcherMode.LIVE,
                    DispatcherMode.LIVE_TIMESHIFT -> {
                        val action = playerKeyAction(
                            PlayerKeyContext(
                                surface = PlayerSurface.LIVE,
                                controlsVisible = controlsVisible,
                                seekbarFocused = false,
                                timeshiftAvailable = mode == DispatcherMode.LIVE_TIMESHIFT,
                                infoOpen = infoOpen,
                                drawerOpen = drawerOpen,
                            ),
                            keyCode = keyCode,
                        )
                        if (playerKeyActionStartsOpeningCycle(action)) {
                            openingKeyCode = keyCode
                            counts.openingActions++
                        }
                        when (action) {
                            PlayerKeyAction.REVEAL_CONTROLS -> controlsVisible = true
                            PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE -> {
                                controlsVisible = true
                                counts.playbackActions++
                            }
                            PlayerKeyAction.OPEN_CHANNELS -> {
                                controlsVisible = false
                                drawerOpen = true
                            }
                            PlayerKeyAction.OPEN_INFO -> infoOpen = true
                            PlayerKeyAction.SEEK_BACK,
                            PlayerKeyAction.SEEK_FORWARD -> counts.seekActions++
                            else -> Unit
                        }
                        action != PlayerKeyAction.PASS_THROUGH
                    }

                    DispatcherMode.RECORDING -> {
                        val action = recordingPlaybackKeyAction(
                            controlsVisible = controlsVisible,
                            keyCode = keyCode,
                        )
                        if (recordingKeyActionStartsOpeningCycle(action)) {
                            openingKeyCode = keyCode
                            counts.openingActions++
                        }
                        when (action) {
                            RecordingPlaybackKeyAction.REVEAL_CONTROLS -> controlsVisible = true
                            RecordingPlaybackKeyAction.REVEAL_AND_TOGGLE_PAUSE -> {
                                controlsVisible = true
                                counts.playbackActions++
                            }
                            RecordingPlaybackKeyAction.OPEN_INFO -> infoOpen = true
                            RecordingPlaybackKeyAction.SEEK_BACK,
                            RecordingPlaybackKeyAction.SEEK_FORWARD -> counts.seekActions++
                            else -> Unit
                        }
                        action != RecordingPlaybackKeyAction.PASS_THROUGH
                    }
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        if (controlsVisible) {
            Box(Modifier.testTag(CONTROLS_VISIBLE))
        }
        if (drawerOpen && !controlsVisible) {
            Box(Modifier.testTag(DRAWER_VISIBLE))
        }
        if (opened) {
            Box(
                modifier = Modifier
                    .testTag(OPENED_TARGET)
                    .fillMaxSize()
                    .focusRequester(openedFocus)
                    .onPreviewKeyEvent {
                        counts.openedTargetEvents++
                        false
                    }
                    .focusable(),
            )
        }
    }
}

private const val DISPATCHER_ROOT = "player-key-dispatcher-root"
private const val OPENED_TARGET = "player-key-opened-target"
private const val CONTROLS_VISIBLE = "player-key-controls-visible"
private const val DRAWER_VISIBLE = "player-key-drawer-visible"
