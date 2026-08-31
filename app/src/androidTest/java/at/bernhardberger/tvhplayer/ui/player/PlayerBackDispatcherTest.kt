package at.bernhardberger.tvhplayer.ui.player

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvhplayer.core.PlayerBackAction
import at.bernhardberger.tvhplayer.core.PlayerForegroundContext
import at.bernhardberger.tvhplayer.core.PlayerSeekPreviewPhase
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.core.playerBackAction
import at.bernhardberger.tvhplayer.core.playerForegroundLayer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PlayerBackDispatcherTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nestedDispatcherOwnerRunsBeforeThePlayerOwner() {
        val counts = BackDispatcherCounts()
        awaitWindowFocus()
        composeRule.setContent {
            BackDispatcherHarness(
                focusChild = true,
                counts = counts,
                onBack = { counts.playerActions++ },
            )
        }
        composeRule.onNodeWithTag(BACK_CHILD).assertIsFocused()

        dispatchBack()

        composeRule.runOnIdle {
            assertEquals(0, counts.playerActions)
            assertEquals(1, counts.nestedActions)
        }
        sendEvidence("nestedBackOwnerTrace", "nested>player(0)")
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
                onBack = { counts.playerActions++ },
            )
        }

        dispatchBack()

        composeRule.runOnIdle { assertEquals(1, counts.playerActions) }
    }

    @Test
    fun disposedPlayerOwnerCannotApplyAStaleBackAction() {
        val mounted = mutableStateOf(true)
        val counts = BackDispatcherCounts()
        awaitWindowFocus()
        composeRule.setContent {
            BackHandler { counts.rootActions++ }
            if (mounted.value) {
                PlayerBackHandler { counts.playerActions++ }
            }
        }

        dispatchBack()
        composeRule.runOnIdle {
            assertEquals(1, counts.playerActions)
            assertEquals(0, counts.rootActions)
            mounted.value = false
        }

        dispatchBack()
        composeRule.runOnIdle {
            assertEquals(1, counts.playerActions)
            assertEquals(1, counts.rootActions)
        }
    }

    @Test
    fun rapidDispatcherBackActionsReadTheCurrentPlayerLayerWithoutRecomposition() {
        val confirmationVisible = mutableStateOf(true)
        val infoVisible = mutableStateOf(true)
        val actions = listOf("confirmation", "info", "player")
        val trace = mutableListOf<String>()
        awaitWindowFocus()
        composeRule.setContent {
            PlayerBackHandler {
                val layer = playerForegroundLayer(
                    PlayerForegroundContext(
                        confirmationVisible = confirmationVisible.value,
                        infoVisible = infoVisible.value,
                        optionsPage = null,
                        numberEntryVisible = false,
                        channelDrawerVisible = false,
                        recoveryVisible = false,
                        terminalErrorVisible = false,
                        seekPreviewPhase = PlayerSeekPreviewPhase.NONE,
                        controlsVisible = false,
                        statsEnabled = false,
                    )
                )
                when (playerBackAction(PlayerSurface.LIVE, false, layer)) {
                    PlayerBackAction.DISMISS_CONFIRMATION -> {
                        trace += "confirmation"
                        confirmationVisible.value = false
                    }
                    PlayerBackAction.CLOSE_INFO -> {
                        trace += "info"
                        infoVisible.value = false
                    }
                    PlayerBackAction.CLOSE_PLAYER -> trace += "player"
                    else -> error("Unexpected rapid Back action for $layer")
                }
            }
        }

        composeRule.runOnIdle {
            repeat(actions.size) {
                composeRule.activity.onBackPressedDispatcher.onBackPressed()
            }
            assertEquals(actions, trace)
        }
        sendEvidence("backDispatchApiLevel", Build.VERSION.SDK_INT.toString())
        sendEvidence("rapidBackActionTrace", trace.joinToString(">"))
    }

    private fun dispatchBack() {
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun sendEvidence(key: String, value: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(
            EVIDENCE_STATUS_CODE,
            Bundle().apply { putString(key, value) },
        )
    }

    private fun awaitWindowFocus() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.activity.hasWindowFocus()
        }
    }
}

private class BackDispatcherCounts {
    var playerActions = 0
    var nestedActions = 0
    var rootActions = 0
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
    PlayerBackHandler(onBack)

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
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        if (focusChild) {
            BackHandler { counts.nestedActions++ }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(BACK_CHILD)
                    .focusRequester(childFocus)
                    .focusable(),
            )
        }
    }
}

private const val EVIDENCE_STATUS_CODE = 2
private const val BACK_ROOT = "player-back-root"
private const val BACK_CHILD = "player-back-child"
