package at.bernhardberger.tvhplayer.ui

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequest
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.ApplianceLaunchState
import at.bernhardberger.tvhplayer.core.ApplianceLaunchTarget
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.MainStartupActionId
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.ui.startup.MainStartupBackProfile
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyCycleOwner
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class MainStartupCompositionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun startupGatedChannelsContentInvokesTheProductionDestinationOnlyWhenAllowed() {
        var contentAllowed by mutableStateOf(false)
        var channelsContentInvocations = 0
        composeRule.setContent {
            StartupGatedChannelsContent(contentAllowed = contentAllowed) {
                channelsContentInvocations++
            }
        }

        composeRule.runOnIdle {
            assertEquals(0, channelsContentInvocations)
            contentAllowed = true
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, channelsContentInvocations) }
    }

    @Test
    fun startupGatedPlayerContentInvokesTheProductionDestinationOnlyAfterCommit() {
        var contentAllowed by mutableStateOf(false)
        var playerContentInvocations = 0
        composeRule.setContent {
            StartupGatedPlayerContent(contentAllowed = contentAllowed) {
                playerContentInvocations++
            }
        }

        composeRule.runOnIdle {
            assertEquals(0, playerContentInvocations)
            contentAllowed = true
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, playerContentInvocations) }
    }

    @Test
    fun resolvingAndPendingOwnTheRootWithoutNavigationChannelsOrRail() {
        var navigationCompositions = 0
        var channelCompositions = 0
        var railCompositions = 0
        var state by mutableStateOf(
            MainStartupCompositionState(
                presentation = MainStartupPresentation.Passive(
                    MainStartupMessageKind.PREPARING,
                ),
                navigationStartDestination = Routes.CHANNELS,
                navigationAllowed = false,
            ),
        )

        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(
                    state = state,
                    simpleTvActive = false,
                    onBack = {},
                    onAction = {},
                    registerActivityKeyContract = { {} },
                    navigation = { _, _ ->
                        navigationCompositions++
                        railCompositions++
                        channelCompositions++
                    },
                )
            }
        }

        composeRule.onNodeWithText("Preparing TVHeadend Player…").assertExists()
        composeRule.runOnIdle {
            assertEquals(0, navigationCompositions)
            assertEquals(0, channelCompositions)
            assertEquals(0, railCompositions)
            state = MainStartupCompositionState(
                presentation = MainStartupPresentation.Actionable(
                    messageKind = MainStartupMessageKind.AUTHORITATIVE_NO_CHANNELS,
                    actions = listOf(
                        MainStartupActionId.RETRY,
                        MainStartupActionId.CONNECTION_SETTINGS,
                    ),
                ),
                navigationStartDestination = Routes.CHANNELS,
                navigationAllowed = false,
            )
        }

        composeRule.onNodeWithText("Retry").assertExists()
        composeRule.runOnIdle {
            assertEquals(0, navigationCompositions)
            assertEquals(0, channelCompositions)
            assertEquals(0, railCompositions)
        }
    }

    @Test
    fun enterDirectiveStaysStartingAndFreshEnteringStartsAtExactPlayerWithoutChannels() {
        val target = target(requestId = 7, channelId = 42, name = "News / HD")
        val exactRoute = Routes.player(target.channelId, target.serviceId, target.channelName)
        var playerCompositions = 0
        var channelCompositions = 0
        var railCompositions = 0
        var observedStartDestination: String? = null
        var state by mutableStateOf(
            MainStartupCompositionState(
                presentation = MainStartupPresentation.Enter(target.request),
                navigationStartDestination = null,
                navigationAllowed = false,
            ),
        )

        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(
                    state = state,
                    simpleTvActive = false,
                    onBack = {},
                    onAction = {},
                    registerActivityKeyContract = { {} },
                    navigation = { startDestination, contentAllowed ->
                        observedStartDestination = startDestination
                        MainNavigationShell(
                            showRail = shouldShowMainNavigationRail(
                                simpleTvActive = false,
                                currentTopRoute = null,
                                navigationStartDestination = startDestination,
                            ),
                            rail = { railCompositions++ },
                            fullScreen = {
                                StartupNavigationCounterHost(
                                    startDestination = startDestination,
                                    contentAllowed = contentAllowed,
                                    onChannelsComposed = { channelCompositions++ },
                                    onPlayerComposed = { playerCompositions++ },
                                )
                            },
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithText("Starting television…").assertExists()
        composeRule.runOnIdle {
            assertNull(observedStartDestination)
            state = MainStartupCompositionState(
                presentation = MainStartupPresentation.Passive(
                    MainStartupMessageKind.STARTING_TELEVISION,
                ),
                navigationStartDestination = exactRoute,
                navigationAllowed = true,
            )
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(exactRoute, observedStartDestination)
            assertEquals(0, playerCompositions)
            assertEquals(0, channelCompositions)
            assertEquals(0, railCompositions)
            state = MainStartupCompositionState(
                presentation = MainStartupPresentation.Inactive,
                navigationStartDestination = exactRoute,
                navigationAllowed = true,
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(playerCompositions > 0)
        }
    }

    @Test
    fun inactiveNormalStartupStartsChannels() {
        var channels = 0
        var railCompositions = 0
        var observedStart: String? = null
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(
                    state = MainStartupCompositionState(
                        presentation = MainStartupPresentation.Inactive,
                        navigationStartDestination = Routes.CHANNELS,
                        navigationAllowed = true,
                    ),
                    simpleTvActive = false,
                    onBack = {},
                    onAction = {},
                    registerActivityKeyContract = { {} },
                    navigation = { start, contentAllowed ->
                        observedStart = start
                        MainNavigationShell(
                            showRail = shouldShowMainNavigationRail(
                                simpleTvActive = false,
                                currentTopRoute = null,
                                navigationStartDestination = start,
                            ),
                            rail = {
                                railCompositions++
                                StartupNavigationCounterHost(
                                    startDestination = start,
                                    contentAllowed = contentAllowed,
                                    onChannelsComposed = { channels++ },
                                    onPlayerComposed = {},
                                )
                            },
                            fullScreen = {},
                        )
                    },
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(Routes.CHANNELS, observedStart)
            assertTrue(channels > 0)
            assertTrue(railCompositions > 0)
            assertTrue(
                shouldShowMainNavigationRail(
                    simpleTvActive = false,
                    currentTopRoute = null,
                    navigationStartDestination = Routes.SETTINGS,
                ),
            )
        }
    }

    @Test
    fun exactNormalCancelSelectsChannelsWithoutRearmingAndDirectCloseFallsBack() {
        val requests = ApplianceLaunchRequests().apply { request() }
        val expected = requests.state.value
        var selectedRoot: String? = null

        assertTrue(
            cancelStartupAndSelectRoot(
                requests = requests,
                expectedState = expected,
                destination = Routes.CHANNELS,
                selectRoot = { selectedRoot = it },
            ),
        )
        assertEquals(ApplianceLaunchState.Idle, requests.state.value)
        assertEquals(Routes.CHANNELS, selectedRoot)
        assertFalse(requests.cancel(expected))

        var retainedRoot = Routes.player(1, 1, "One")
        closeNormalLivePlayer(
            simpleTvActive = false,
            popBackStack = { false },
            selectRoot = { retainedRoot = it },
        )
        assertEquals(Routes.CHANNELS, retainedRoot)

        retainedRoot = Routes.player(2, 2, "Two")
        closeNormalLivePlayer(
            simpleTvActive = false,
            popBackStack = { true },
            selectRoot = { retainedRoot = it },
        )
        assertEquals(Routes.player(2, 2, "Two"), retainedRoot)
    }

    @Test
    fun exactEnteringRouteSuppressesPlayerUntilCasPublishesIdle() {
        val entering = ApplianceLaunchState.Entering(
            target(requestId = 15, channelId = 15, name = "Exact"),
        )
        val passive = MainStartupPresentation.Passive(
            MainStartupMessageKind.STARTING_TELEVISION,
        )
        var launchCommitted by mutableStateOf(false)
        var registered: MainStartupActivityKeyContract? = null
        var playerContentCompositions = 0

        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(
                    state = MainStartupCompositionState(
                        presentation = if (launchCommitted) {
                            MainStartupPresentation.Inactive
                        } else {
                            passive
                        },
                        navigationStartDestination = Routes.player(
                            entering.target.channelId,
                            entering.target.serviceId,
                            entering.target.channelName,
                        ),
                        navigationAllowed = true,
                    ),
                    simpleTvActive = false,
                    onBack = {},
                    onAction = {},
                    registerActivityKeyContract = { contract ->
                        registered = contract
                        { if (registered === contract) registered = null }
                    },
                    navigation = { _, contentAllowed ->
                        if (contentAllowed) playerContentCompositions++
                    },
                )
            }
        }

        composeRule.onNodeWithText("Starting television…").assertExists()
        composeRule.runOnIdle {
            assertEquals(
                MainStartupKeyMode.Passive(MainStartupBackProfile.NORMAL),
                registered?.mode,
            )
            assertEquals(0, playerContentCompositions)
            launchCommitted = true
        }

        composeRule.onNodeWithText("Starting television…").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(MainStartupKeyMode.Inactive, registered?.mode)
            assertTrue(playerContentCompositions > 0)
        }
    }

    @Test
    fun mismatchedVisiblePlayerCannotCommitEnteringGeneration() {
        val requests = ApplianceLaunchRequests().apply { request() }
        val pending = requests.state.value as ApplianceLaunchState.Pending
        val target = requests.resolve(
            request = pending.request,
            readiness = CurrentChannelReadiness.Ready(listOf(channel(11, "Eleven"))),
            persistedId = 11,
        )!!

        assertFalse(
            completeEnteringPlayerVisibility(
                requests = requests,
                target = target,
                channelId = target.channelId,
                serviceId = target.serviceId + 1,
                channelName = target.channelName,
            ),
        )
        assertEquals(ApplianceLaunchState.Entering(target), requests.state.value)
        assertTrue(
            completeEnteringPlayerVisibility(
                requests = requests,
                target = target,
                channelId = target.channelId,
                serviceId = target.serviceId,
                channelName = target.channelName,
            ),
        )
    }

    @Test
    fun mismatchedEnteringRouteSuppressesNavigationAndPlayerContent() {
        var navigationCompositions = 0
        var playerContentCompositions = 0
        var registered: MainStartupActivityKeyContract? = null
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(
                    state = MainStartupCompositionState(
                        presentation = MainStartupPresentation.Passive(
                            MainStartupMessageKind.STARTING_TELEVISION,
                        ),
                        navigationStartDestination = Routes.player(91, 91, "Wrong target"),
                        navigationAllowed = false,
                    ),
                    simpleTvActive = false,
                    onBack = {},
                    onAction = {},
                    registerActivityKeyContract = { contract ->
                        registered = contract
                        { if (registered === contract) registered = null }
                    },
                    navigation = { _, contentAllowed ->
                        navigationCompositions++
                        if (contentAllowed) playerContentCompositions++
                    },
                )
            }
        }

        composeRule.onNodeWithText("Starting television…").assertExists()
        composeRule.runOnIdle {
            assertEquals(0, navigationCompositions)
            assertEquals(0, playerContentCompositions)
            assertEquals(
                MainStartupKeyMode.Passive(MainStartupBackProfile.NORMAL),
                registered?.mode,
            )
        }
        assertFalse(
            enteringNavigationAllowed(
                hasBackStackEntry = true,
                navigationStartDestination = null,
                exactStartDestination = Routes.player(1, 1, "Exact"),
                matchingVisiblePlayer = false,
            ),
        )
        assertTrue(
            enteringNavigationAllowed(
                hasBackStackEntry = false,
                navigationStartDestination = Routes.player(1, 1, "Exact"),
                exactStartDestination = Routes.player(1, 1, "Exact"),
                matchingVisiblePlayer = false,
            ),
        )
    }

    @Test
    fun startupActionsRouteWithoutRetryChangingTheGeneration() {
        var retries = 0
        var settings = 0
        var exits = 0

        performMainStartupAction(
            action = MainStartupActionId.RETRY,
            onRetry = { retries++ },
            onConnectionSettings = { settings++ },
            onExitSimpleTv = { exits++ },
        )
        performMainStartupAction(
            action = MainStartupActionId.CONNECTION_SETTINGS,
            onRetry = { retries++ },
            onConnectionSettings = { settings++ },
            onExitSimpleTv = { exits++ },
        )
        performMainStartupAction(
            action = MainStartupActionId.EXIT_SIMPLE_TV,
            onRetry = { retries++ },
            onConnectionSettings = { settings++ },
            onExitSimpleTv = { exits++ },
        )

        assertEquals(1, retries)
        assertEquals(1, settings)
        assertEquals(1, exits)
    }

    @Test
    fun deferredResolvingBackIsContainedForSimpleTvAndCancelsNormalStartupExactly() {
        val simpleRequests = ApplianceLaunchRequests().apply { request() }
        val simplePending = simpleRequests.state.value
        var simpleRoot: String? = null
        val simpleAction = deferredResolvingBackAction(
            cancellationRequested = true,
            readyState = readyStartup(startSimpleTv = true),
        )

        assertEquals(DeferredResolvingBackAction.CONTAIN_SIMPLE_TV, simpleAction)
        assertFalse(
            applyDeferredResolvingBack(
                action = simpleAction,
                requests = simpleRequests,
                expectedState = simplePending,
                selectRoot = { simpleRoot = it },
            ),
        )
        assertEquals(simplePending, simpleRequests.state.value)
        assertNull(simpleRoot)

        val normalRequests = ApplianceLaunchRequests().apply { request() }
        val normalPending = normalRequests.state.value
        var normalRoot: String? = null
        val normalAction = deferredResolvingBackAction(
            cancellationRequested = true,
            readyState = readyStartup(startSimpleTv = false),
        )

        assertEquals(DeferredResolvingBackAction.CANCEL_TO_CHANNELS, normalAction)
        assertTrue(
            applyDeferredResolvingBack(
                action = normalAction,
                requests = normalRequests,
                expectedState = normalPending,
                selectRoot = { normalRoot = it },
            ),
        )
        assertEquals(ApplianceLaunchState.Idle, normalRequests.state.value)
        assertEquals(Routes.CHANNELS, normalRoot)
        assertFalse(normalRequests.cancel(normalPending))
    }

    @Test
    fun activityKeyContractPublishesExactProfileCleansUpAndSystemBackMatchesIt() {
        var registered: MainStartupActivityKeyContract? = null
        var showStartup by mutableStateOf(true)
        var normalCancels = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                if (showStartup) {
                    MainStartupComposition(
                        state = MainStartupCompositionState(
                            presentation = MainStartupPresentation.Passive(
                                MainStartupMessageKind.CONNECTING,
                            ),
                            navigationStartDestination = null,
                            navigationAllowed = false,
                        ),
                        simpleTvActive = false,
                        onBack = { normalCancels++ },
                        onAction = {},
                        registerActivityKeyContract = { contract ->
                            registered = contract
                            { if (registered === contract) registered = null }
                        },
                    )
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(
                MainStartupKeyMode.Passive(MainStartupBackProfile.NORMAL),
                registered?.mode,
            )
            val contract = requireNotNull(registered)
            assertTrue(
                dispatchMainStartupKeyEvent(
                    owner = MainStartupKeyCycleOwner(),
                    contract = contract,
                    event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK),
                ),
            )
            assertEquals(1, normalCancels)
        }

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.runOnIdle {
            assertEquals(2, normalCancels)
            showStartup = false
        }
        composeRule.runOnIdle { assertNull(registered) }
    }

    @Test
    fun simpleTvSystemBackUsesProductionPolicyAndLeavesLaunchUnchanged() {
        val requests = ApplianceLaunchRequests().apply { request() }
        val expectedState = requests.state.value
        var contract: MainStartupActivityKeyContract? = null
        var selectedRoot: String? = null
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(
                    state = MainStartupCompositionState(
                        presentation = MainStartupPresentation.Actionable(
                            MainStartupMessageKind.SIMPLE_TV_FAILURE,
                            listOf(MainStartupActionId.RETRY, MainStartupActionId.EXIT_SIMPLE_TV),
                        ),
                        navigationStartDestination = null,
                        navigationAllowed = false,
                    ),
                    simpleTvActive = true,
                    onBack = {
                        performMainStartupBack(
                            simpleTvActive = true,
                            requests = requests,
                            expectedState = expectedState,
                            selectRoot = { selectedRoot = it },
                        )
                    },
                    onAction = {},
                    registerActivityKeyContract = {
                        contract = it
                        { contract = null }
                    },
                )
            }
        }

        composeRule.runOnIdle {
            assertEquals(
                MainStartupKeyMode.Actionable(MainStartupBackProfile.SIMPLE_TV),
                contract?.mode,
            )
        }
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.runOnIdle {
            assertEquals(expectedState, requests.state.value)
            assertNull(selectedRoot)
        }
    }

    @Composable
    private fun StartupNavigationCounterHost(
        startDestination: String,
        contentAllowed: Boolean,
        onChannelsComposed: () -> Unit,
        onPlayerComposed: () -> Unit,
    ) {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = startDestination) {
            composable(Routes.CHANNELS) {
                if (contentAllowed) onChannelsComposed()
            }
            composable(
                route = "${Routes.PLAYER}/{channelId}/{serviceId}/{channelName}",
                arguments = listOf(
                    navArgument("channelId") { type = NavType.IntType },
                    navArgument("serviceId") { type = NavType.IntType },
                    navArgument("channelName") { type = NavType.StringType },
                ),
            ) {
                if (contentAllowed) onPlayerComposed()
            }
        }
    }

    private fun target(requestId: Long, channelId: Int, name: String) = ApplianceLaunchTarget(
        request = ApplianceLaunchRequest(requestId),
        channelId = channelId,
        serviceId = channelId,
        channelName = name,
    )

    private fun channel(id: Int, name: String) = ChannelUi(
        id = id,
        name = name,
        number = id,
        icon = null,
    )

    private fun readyStartup(startSimpleTv: Boolean) =
        at.bernhardberger.tvhplayer.core.MainStartupState.Ready(
            server = at.bernhardberger.tvhplayer.settings.ServerSettings(host = "tvh.invalid"),
            autoStartPlayback = true,
            startSimpleTv = startSimpleTv,
        )
}
