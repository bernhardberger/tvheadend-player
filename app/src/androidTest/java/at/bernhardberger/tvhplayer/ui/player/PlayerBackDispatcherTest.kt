package at.bernhardberger.tvhplayer.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyPress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PlayerBackDispatcherTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hardwareDownRepeatAndUpUnwindOnceAheadOfFocusedChild() {
        val counts = BackDispatcherCounts()
        awaitWindowFocus()
        composeRule.setContent {
            BackDispatcherHarness(
                focusChild = true,
                counts = counts,
                onBack = { counts.backActions++ },
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BACK_CHILD).assertIsFocused()

        dispatchBack(BACK_CHILD, AndroidKeyEvent.ACTION_DOWN)
        dispatchBack(BACK_CHILD, AndroidKeyEvent.ACTION_DOWN, repeatCount = 1)
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        dispatchBack(BACK_CHILD, AndroidKeyEvent.ACTION_UP)

        composeRule.runOnIdle {
            assertEquals(1, counts.backActions)
            assertEquals(0, counts.childBackEvents)
        }

        pressBack(BACK_CHILD)
        composeRule.runOnIdle { assertEquals(2, counts.backActions) }
    }

    @Test
    fun systemOrAccessibilityBackWorksWithoutAFocusedKeyTarget() {
        val counts = BackDispatcherCounts()
        awaitWindowFocus()
        composeRule.setContent {
            BackDispatcherHarness(
                focusChild = false,
                requestRootFocus = false,
                counts = counts,
                onBack = { counts.backActions++ },
            )
        }

        composeRule.activity.onBackPressedDispatcher.onBackPressed()

        composeRule.runOnIdle { assertEquals(1, counts.backActions) }
    }

    @Test
    fun disposingRouteBeforeKeyUpCannotApplyAStaleBackAction() {
        val mounted = mutableStateOf(true)
        val counts = BackDispatcherCounts()
        awaitWindowFocus()
        composeRule.setContent {
            if (mounted.value) {
                BackDispatcherHarness(
                    focusChild = false,
                    counts = counts,
                    onBack = { counts.backActions++ },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BACK_ROOT).assertIsFocused()

        dispatchBack(BACK_ROOT, AndroidKeyEvent.ACTION_DOWN)
        composeRule.runOnIdle { mounted.value = false }
        composeRule.runOnIdle { assertEquals(0, counts.backActions) }

        composeRule.runOnIdle { mounted.value = true }
        composeRule.waitForIdle()
        pressBack(BACK_ROOT)
        composeRule.runOnIdle { assertEquals(1, counts.backActions) }
    }

    private fun pressBack(nodeTag: String) {
        dispatchBack(nodeTag, AndroidKeyEvent.ACTION_DOWN)
        dispatchBack(nodeTag, AndroidKeyEvent.ACTION_UP)
    }

    private fun dispatchBack(
        nodeTag: String,
        action: Int,
        repeatCount: Int = 0,
    ) {
        val eventTime = android.os.SystemClock.uptimeMillis()
        composeRule.onNodeWithTag(nodeTag).performKeyPress(
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

private class BackDispatcherCounts {
    var backActions = 0
    var childBackEvents = 0
}

@Composable
private fun BackDispatcherHarness(
    focusChild: Boolean,
    counts: BackDispatcherCounts,
    onBack: () -> Unit,
    requestRootFocus: Boolean = true,
) {
    val rootFocus = remember { FocusRequester() }
    val childFocus = remember { FocusRequester() }
    val dispatchBack = rememberPlayerBackDispatcher(onBack)

    LaunchedEffect(focusChild, requestRootFocus) {
        when {
            focusChild -> childFocus.requestFocus()
            requestRootFocus -> rootFocus.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(BACK_ROOT)
            .onPreviewKeyEvent(dispatchBack)
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        if (focusChild) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(BACK_CHILD)
                    .focusRequester(childFocus)
                    .onPreviewKeyEvent {
                        if (it.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                            counts.childBackEvents++
                        }
                        false
                    }
                    .focusable(),
            )
        }
    }
}

private const val BACK_ROOT = "player-back-root"
private const val BACK_CHILD = "player-back-child"
