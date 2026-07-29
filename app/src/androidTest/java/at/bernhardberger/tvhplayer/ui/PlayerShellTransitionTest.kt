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

class PlayerShellTransitionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun forwardPlayerShellEdgesReplaceTheOutgoingFocusTreeImmediately() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            TransitionHarness(navController)
        }
        assertOnlyFocused(SHELL_A_TAG)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { navController.navigate("player/1") }
        composeRule.advanceNavigationFrames()

        composeRule.onNodeWithTag(SHELL_A_TAG).assertDoesNotExist()
        assertOnlyFocused(PLAYER_TAG)
    }

    @Test
    fun popPlayerShellEdgesReplaceTheOutgoingFocusTreeImmediately() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            TransitionHarness(navController)
        }
        composeRule.runOnIdle { navController.navigate("player/1") }
        assertOnlyFocused(PLAYER_TAG)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.advanceNavigationFrames()

        composeRule.onNodeWithTag(PLAYER_TAG).assertDoesNotExist()
        assertOnlyFocused(SHELL_A_TAG)
    }

    @Test
    fun sameFamilyEdgesRetainTheDefaultDestinationFade() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            TransitionHarness(navController)
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { navController.navigate(SHELL_B_ROUTE) }
        composeRule.advanceNavigationFrames()

        composeRule.onNodeWithTag(SHELL_A_TAG).assertExists()
        assertOnlyFocused(SHELL_B_TAG)

        composeRule.mainClock.advanceTimeBy(640)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SHELL_A_TAG).assertExists()

        composeRule.mainClock.advanceTimeBy(64)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SHELL_A_TAG).assertDoesNotExist()

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.runOnIdle { navController.navigate("player/1") }
        assertOnlyFocused(PLAYER_TAG)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { navController.navigate("recording-player/2") }
        composeRule.advanceNavigationFrames()

        composeRule.onNodeWithTag(PLAYER_TAG).assertExists()
        assertOnlyFocused(RECORDING_PLAYER_TAG)
    }

    private fun assertOnlyFocused(tag: String) {
        composeRule.onNodeWithTag(tag).assertIsFocused()
        assertEquals(1, composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().size)
    }
}

@Composable
private fun TransitionHarness(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = SHELL_A_ROUTE,
        enterTransition = {
            playerShellEnterTransition(
                initialRoute = initialState.destination.route,
                targetRoute = targetState.destination.route,
            )
        },
        exitTransition = {
            playerShellExitTransition(
                initialRoute = initialState.destination.route,
                targetRoute = targetState.destination.route,
            )
        },
        popEnterTransition = {
            playerShellEnterTransition(
                initialRoute = initialState.destination.route,
                targetRoute = targetState.destination.route,
            )
        },
        popExitTransition = {
            playerShellExitTransition(
                initialRoute = initialState.destination.route,
                targetRoute = targetState.destination.route,
            )
        },
    ) {
        composable(SHELL_A_ROUTE) { FocusedDestination(SHELL_A_TAG) }
        composable(SHELL_B_ROUTE) { FocusedDestination(SHELL_B_TAG) }
        composable("player/{id}") { FocusedDestination(PLAYER_TAG) }
        composable("recording-player/{id}") { FocusedDestination(RECORDING_PLAYER_TAG) }
    }
}

@Composable
private fun FocusedDestination(tag: String) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(tag)
            .focusRequester(focusRequester)
            .focusable(),
    )
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.advanceNavigationFrames() {
    mainClock.advanceTimeByFrame()
    mainClock.advanceTimeByFrame()
    waitForIdle()
}

private const val SHELL_A_ROUTE = "shell-a"
private const val SHELL_B_ROUTE = "shell-b"
private const val SHELL_A_TAG = "shell-a-focus"
private const val SHELL_B_TAG = "shell-b-focus"
private const val PLAYER_TAG = "player-focus"
private const val RECORDING_PLAYER_TAG = "recording-player-focus"
