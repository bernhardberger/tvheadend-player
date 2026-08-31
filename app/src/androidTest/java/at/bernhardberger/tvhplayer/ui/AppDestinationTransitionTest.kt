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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppDestinationTransitionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun forwardPlayerShellEdgesRemoveTheOutgoingFocusTreeAfterCrossfade() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            TransitionHarness(navController)
        }
        assertOnlyFocused(SHELL_A_TAG)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { navController.navigate("player/1") }
        composeRule.finishDestinationCrossfade()

        composeRule.onNodeWithTag(SHELL_A_TAG).assertDoesNotExist()
        assertOnlyFocused(PLAYER_TAG)
    }

    @Test
    fun popPlayerShellEdgesRemoveTheOutgoingFocusTreeAfterCrossfade() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            TransitionHarness(navController)
        }
        composeRule.runOnIdle { navController.navigate("player/1") }
        assertOnlyFocused(PLAYER_TAG)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.finishDestinationCrossfade()

        composeRule.onNodeWithTag(PLAYER_TAG).assertDoesNotExist()
        assertOnlyFocused(SHELL_A_TAG)
    }

    @Test
    fun sameFamilyEdgesCrossfadeWithoutDelayingContentOwnedFocus() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            TransitionHarness(navController)
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { navController.navigate(SHELL_B_ROUTE) }
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
        composeRule.runOnIdle { navController.navigate("player/1") }
        assertOnlyFocused(PLAYER_TAG)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { navController.navigate("recording-player/2") }
        composeRule.finishDestinationCrossfade()

        composeRule.onNodeWithTag(PLAYER_TAG).assertDoesNotExist()
        assertOnlyFocused(RECORDING_PLAYER_TAG)
    }

    @Test
    fun frameSeparatedSameFamilyNavigationKeepsOnlyTheLatestDestination() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            TransitionHarness(navController)
        }
        assertOnlyFocused(SHELL_A_TAG)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { navController.navigate(SHELL_B_ROUTE) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertOnlyFocused(SHELL_B_TAG)

        composeRule.runOnIdle { navController.navigate(SHELL_C_ROUTE) }
        composeRule.finishDestinationCrossfade()

        composeRule.onNodeWithTag(SHELL_A_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SHELL_B_TAG).assertDoesNotExist()
        assertOnlyFocused(SHELL_C_TAG)
    }

    @Test
    fun sameFrameRapidNavigationAlwaysComposesTheLatestDestination() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            TransitionHarness(navController)
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            navController.navigate(SHELL_B_ROUTE)
            navController.navigate(SHELL_C_ROUTE)
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
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            val navigationFocusRequester = remember { FocusRequester() }
            LaunchedEffect(navigationFocusRequester) {
                navigationFocusRequester.requestFocus()
            }
            Box {
                TransitionHarness(
                    navController = navController,
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
        composeRule.runOnIdle { navController.navigate(SHELL_B_ROUTE) }
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
    navController: NavHostController,
    destinationFocusEnabled: Boolean = true,
) {
    NavHost(
        navController = navController,
        startDestination = SHELL_A_ROUTE,
        enterTransition = {
            appDestinationEnterTransition()
        },
        exitTransition = {
            appDestinationExitTransition()
        },
        popEnterTransition = {
            appDestinationEnterTransition()
        },
        popExitTransition = {
            appDestinationExitTransition()
        },
    ) {
        composable(SHELL_A_ROUTE) {
            FocusedDestination(SHELL_A_TAG, destinationFocusEnabled)
        }
        composable(SHELL_B_ROUTE) {
            FocusedDestination(SHELL_B_TAG, destinationFocusEnabled)
        }
        composable(SHELL_C_ROUTE) {
            FocusedDestination(SHELL_C_TAG, destinationFocusEnabled)
        }
        composable("player/{id}") {
            FocusedDestination(PLAYER_TAG, destinationFocusEnabled)
        }
        composable("recording-player/{id}") {
            FocusedDestination(RECORDING_PLAYER_TAG, destinationFocusEnabled)
        }
    }
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

private const val SHELL_A_ROUTE = "shell-a"
private const val SHELL_B_ROUTE = "shell-b"
private const val SHELL_C_ROUTE = "shell-c"
private const val SHELL_A_TAG = "shell-a-focus"
private const val SHELL_B_TAG = "shell-b-focus"
private const val SHELL_C_TAG = "shell-c-focus"
private const val PLAYER_TAG = "player-focus"
private const val RECORDING_PLAYER_TAG = "recording-player-focus"
private const val NAVIGATION_FOCUS_TAG = "navigation-focus"
