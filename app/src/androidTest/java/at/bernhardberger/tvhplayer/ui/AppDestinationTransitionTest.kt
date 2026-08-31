package at.bernhardberger.tvhplayer.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppDestinationTransitionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun forwardPlayerShellEdgesRemoveTheOutgoingFocusTreeAfterCrossfade() {
        lateinit var backStack: MutableList<AppNavKey>
        composeRule.setContent {
            backStack = rememberAppNavBackStack(ChannelsKey)
            TransitionHarness(backStack)
        }
        assertOnlyFocused(SHELL_A_TAG)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            backStack.pushTransient(LivePlayerKey(channelId = 1, channelName = "Player"))
        }
        composeRule.finishDestinationCrossfade()

        composeRule.onNodeWithTag(SHELL_A_TAG).assertDoesNotExist()
        assertOnlyFocused(PLAYER_TAG)
    }

    @Test
    fun popPlayerShellEdgesRemoveTheOutgoingFocusTreeAfterCrossfade() {
        lateinit var backStack: MutableList<AppNavKey>
        composeRule.setContent {
            backStack = rememberAppNavBackStack(ChannelsKey)
            TransitionHarness(backStack)
        }
        composeRule.runOnIdle {
            backStack.pushTransient(LivePlayerKey(channelId = 1, channelName = "Player"))
        }
        assertOnlyFocused(PLAYER_TAG)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { backStack.popNavigation() }
        composeRule.finishDestinationCrossfade()

        composeRule.onNodeWithTag(PLAYER_TAG).assertDoesNotExist()
        assertOnlyFocused(SHELL_A_TAG)
    }

    @Test
    fun sameFamilyEdgesCrossfadeWithoutDelayingContentOwnedFocus() {
        lateinit var backStack: MutableList<AppNavKey>
        composeRule.setContent {
            backStack = rememberAppNavBackStack(ChannelsKey)
            TransitionHarness(backStack)
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { backStack.navigateTopLevel(GuideKey) }
        composeRule.waitForIdle()

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(
            APP_DESTINATION_CROSSFADE_DURATION_MILLIS.toLong() / 2,
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SHELL_A_TAG).assertExists()
        assertOnlyFocused(SHELL_B_TAG)

        composeRule.finishDestinationCrossfade()

        composeRule.onNodeWithTag(SHELL_A_TAG).assertDoesNotExist()
        assertOnlyFocused(SHELL_B_TAG)

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            backStack.pushTransient(LivePlayerKey(channelId = 1, channelName = "Player"))
        }
        assertOnlyFocused(PLAYER_TAG)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            backStack.pushTransient(RecordingPlayerKey(recordingId = 2))
        }
        composeRule.finishDestinationCrossfade()

        composeRule.onNodeWithTag(PLAYER_TAG).assertDoesNotExist()
        assertOnlyFocused(RECORDING_PLAYER_TAG)
    }

    @Test
    fun frameSeparatedSameFamilyNavigationKeepsOnlyTheLatestDestination() {
        lateinit var backStack: MutableList<AppNavKey>
        composeRule.setContent {
            backStack = rememberAppNavBackStack(ChannelsKey)
            TransitionHarness(backStack)
        }
        assertOnlyFocused(SHELL_A_TAG)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { backStack.navigateTopLevel(GuideKey) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertOnlyFocused(SHELL_B_TAG)

        composeRule.runOnIdle { backStack.navigateTopLevel(RecordingsKey) }
        composeRule.finishDestinationCrossfade()

        composeRule.onNodeWithTag(SHELL_A_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SHELL_B_TAG).assertDoesNotExist()
        assertOnlyFocused(SHELL_C_TAG)
    }

    @Test
    fun sameFrameRapidNavigationAlwaysComposesTheLatestDestination() {
        lateinit var backStack: MutableList<AppNavKey>
        composeRule.setContent {
            backStack = rememberAppNavBackStack(ChannelsKey)
            TransitionHarness(backStack)
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            backStack.navigateTopLevel(GuideKey)
            backStack.navigateTopLevel(RecordingsKey)
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SHELL_B_TAG).assertDoesNotExist()
        assertOnlyFocused(SHELL_C_TAG)

        composeRule.finishDestinationCrossfade()

        composeRule.onNodeWithTag(SHELL_A_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SHELL_B_TAG).assertDoesNotExist()
        assertOnlyFocused(SHELL_C_TAG)
    }

    @Test
    fun crossfadePreservesAnExternalNavigationFocusOwner() {
        lateinit var backStack: MutableList<AppNavKey>
        composeRule.setContent {
            backStack = rememberAppNavBackStack(ChannelsKey)
            val navigationFocusRequester = remember { FocusRequester() }
            LaunchedEffect(navigationFocusRequester) {
                navigationFocusRequester.requestFocus()
            }
            Box {
                TransitionHarness(
                    backStack = backStack,
                    destinationFocusEnabled = false,
                )
                Box(
                    modifier = Modifier
                        .testTag(NAVIGATION_FOCUS_TAG)
                        .focusRequester(navigationFocusRequester)
                        .focusable(),
                )
            }
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { backStack.navigateTopLevel(GuideKey) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(
            APP_DESTINATION_CROSSFADE_DURATION_MILLIS.toLong() / 2,
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SHELL_A_TAG).assertExists()
        composeRule.onNodeWithTag(SHELL_B_TAG).assertExists()
        assertOnlyFocused(NAVIGATION_FOCUS_TAG)
    }

    private fun assertOnlyFocused(tag: String) {
        composeRule.onNodeWithTag(tag).assertIsFocused()
        assertEquals(1, composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().size)
    }
}

@Composable
private fun TransitionHarness(
    backStack: MutableList<AppNavKey>,
    destinationFocusEnabled: Boolean = true,
) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.popNavigation() },
        transitionSpec = { appDestinationContentTransform() },
        popTransitionSpec = { appDestinationContentTransform() },
        predictivePopTransitionSpec = { appDestinationContentTransform() },
        entryProvider = entryProvider {
            entry<ChannelsKey> {
                FocusedDestination(SHELL_A_TAG, destinationFocusEnabled)
            }
            entry<GuideKey> {
                FocusedDestination(SHELL_B_TAG, destinationFocusEnabled)
            }
            entry<RecordingsKey> {
                FocusedDestination(SHELL_C_TAG, destinationFocusEnabled)
            }
            entry<LivePlayerKey> {
                FocusedDestination(PLAYER_TAG, destinationFocusEnabled)
            }
            entry<RecordingPlayerKey> {
                FocusedDestination(RECORDING_PLAYER_TAG, destinationFocusEnabled)
            }
        },
    )
}

@Composable
private fun FocusedDestination(
    tag: String,
    initialFocusEnabled: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester, initialFocusEnabled) {
        if (initialFocusEnabled) {
            focusRequester.requestFocus()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(tag)
            .focusRequester(focusRequester)
            .focusable(),
    )
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.finishDestinationCrossfade() {
    waitForIdle()
    mainClock.advanceTimeByFrame()
    waitForIdle()
    mainClock.advanceTimeBy(
        APP_DESTINATION_CROSSFADE_DURATION_MILLIS.toLong() + 32L,
    )
    waitForIdle()
}

private const val SHELL_A_TAG = "shell-a-focus"
private const val SHELL_B_TAG = "shell-b-focus"
private const val SHELL_C_TAG = "shell-c-focus"
private const val PLAYER_TAG = "player-focus"
private const val RECORDING_PLAYER_TAG = "recording-player-focus"
private const val NAVIGATION_FOCUS_TAG = "navigation-focus"
