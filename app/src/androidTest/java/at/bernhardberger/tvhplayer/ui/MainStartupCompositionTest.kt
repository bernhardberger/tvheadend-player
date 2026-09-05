package at.bernhardberger.tvhplayer.ui

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequest
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.ApplianceLaunchState
import at.bernhardberger.tvhplayer.core.ApplianceLaunchTarget
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.MainStartupActionId
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
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
            StartupGatedChannelsContent(
                contentAllowed = contentAllowed,
            ) {
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
                navigationStartDestination = ChannelsKey,
                navigationAllowed = false,
            ),
        )

        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(
                    state = state,
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
                navigationStartDestination = ChannelsKey,
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
        val exactRoute = LivePlayerKey(target.channelId.value, target.channelName)
        var playerCompositions = 0
        var channelCompositions = 0
        var railCompositions = 0
        var observedStartDestination: AppNavKey? = null
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
                    onBack = {},
                    onAction = {},
                    registerActivityKeyContract = { {} },
                    navigation = { startDestination, contentAllowed ->
                        observedStartDestination = startDestination
                        MainNavigationShell(
                            showRail = shouldShowMainNavigationRail(
                                currentDestination = null,
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
        var observedStart: AppNavKey? = null
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(
                    state = MainStartupCompositionState(
                        presentation = MainStartupPresentation.Inactive,
                        navigationStartDestination = ChannelsKey,
                        navigationAllowed = true,
                    ),
                    onBack = {},
                    onAction = {},
                    registerActivityKeyContract = { {} },
                    navigation = { start, contentAllowed ->
                        observedStart = start
                        MainNavigationShell(
                            showRail = shouldShowMainNavigationRail(
                                currentDestination = null,
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
            assertEquals(ChannelsKey, observedStart)
            assertTrue(channels > 0)
            assertTrue(railCompositions > 0)
            assertTrue(
                shouldShowMainNavigationRail(
                    currentDestination = null,
                    navigationStartDestination = SettingsKey(),
                ),
            )
        }
    }

    @Test
    fun exactNormalCancelSelectsChannelsWithoutRearmingAndDirectCloseFallsBack() {
        val requests = ApplianceLaunchRequests().apply { request() }
        val expected = requests.state.value
        var selectedRoot: AppNavKey? = null

        assertTrue(
            cancelStartupAndSelectRoot(
                requests = requests,
                expectedState = expected,
                destination = ChannelsKey,
                selectRoot = { selectedRoot = it },
            ),
        )
        assertEquals(ApplianceLaunchState.Idle, requests.state.value)
        assertEquals(ChannelsKey, selectedRoot)
        assertFalse(requests.cancel(expected))

        var retainedRoot: AppNavKey = LivePlayerKey(channelId = 1, channelName = "One")
        closeNormalLivePlayer(
            popBackStack = { false },
            selectRoot = { retainedRoot = it },
        )
        assertEquals(ChannelsKey, retainedRoot)

        retainedRoot = LivePlayerKey(channelId = 2, channelName = "Two")
        closeNormalLivePlayer(
            popBackStack = { true },
            selectRoot = { retainedRoot = it },
        )
        assertEquals(LivePlayerKey(channelId = 2, channelName = "Two"), retainedRoot)
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
                        navigationStartDestination = LivePlayerKey(
                            channelId = entering.target.channelId.value,
                            channelName = entering.target.channelName,
                        ),
                        navigationAllowed = true,
                    ),
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
                MainStartupKeyMode.Passive,
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
            persistedId = ChannelId(11),
        )!!

        assertFalse(
            completeEnteringPlayerVisibility(
                requests = requests,
                target = target,
                channelId = ChannelId(target.channelId.value + 1),
                channelName = target.channelName,
            ),
        )
        assertEquals(ApplianceLaunchState.Entering(target), requests.state.value)
        assertTrue(
            completeEnteringPlayerVisibility(
                requests = requests,
                target = target,
                channelId = target.channelId,
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
                        navigationStartDestination = LivePlayerKey(91, "Wrong target"),
                        navigationAllowed = false,
                    ),
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
                MainStartupKeyMode.Passive,
                registered?.mode,
            )
        }
        assertFalse(
            enteringNavigationAllowed(
                hasBackStackEntry = true,
                navigationStartDestination = null,
                exactStartDestination = LivePlayerKey(1, "Exact"),
                matchingVisiblePlayer = false,
            ),
        )
        assertTrue(
            enteringNavigationAllowed(
                hasBackStackEntry = false,
                navigationStartDestination = LivePlayerKey(1, "Exact"),
                exactStartDestination = LivePlayerKey(1, "Exact"),
                matchingVisiblePlayer = false,
            ),
        )
    }

    @Test
    fun startupActionsRouteWithoutRetryChangingTheGeneration() {
        var retries = 0
        var settings = 0

        performMainStartupAction(
            action = MainStartupActionId.RETRY,
            onRetry = { retries++ },
            onConnectionSettings = { settings++ },
        )
        performMainStartupAction(
            action = MainStartupActionId.CONNECTION_SETTINGS,
            onRetry = { retries++ },
            onConnectionSettings = { settings++ },
        )

        assertEquals(1, retries)
        assertEquals(1, settings)
    }

    @Test
    fun deferredResolvingBackCancelsStartupExactlyOnce() {
        val normalRequests = ApplianceLaunchRequests().apply { request() }
        val normalPending = normalRequests.state.value
        var normalRoot: AppNavKey? = null
        val normalAction = deferredResolvingBackAction(
            cancellationRequested = true,
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
        assertEquals(ChannelsKey, normalRoot)
        assertFalse(normalRequests.cancel(normalPending))
    }

    @Test
    fun activityKeyContractPassesBackToTheSystemOwnerAndCleansUp() {
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
                MainStartupKeyMode.Passive,
                registered?.mode,
            )
            val contract = requireNotNull(registered)
            assertFalse(
                dispatchMainStartupKeyEvent(
                    owner = MainStartupKeyCycleOwner(),
                    contract = contract,
                    event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK),
                ),
            )
            assertEquals(0, normalCancels)
        }

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.runOnIdle {
            assertEquals(1, normalCancels)
            showStartup = false
        }
        composeRule.runOnIdle { assertNull(registered) }
    }



    @Composable
    private fun StartupNavigationCounterHost(
        startDestination: AppNavKey,
        contentAllowed: Boolean,
        onChannelsComposed: () -> Unit,
        onPlayerComposed: () -> Unit,
    ) {
        val backStack = rememberAppNavBackStack(startDestination)
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.popNavigation() },
            entryProvider = entryProvider {
                entry<ChannelsKey> {
                    if (contentAllowed) onChannelsComposed()
                }
                entry<LivePlayerKey> {
                    if (contentAllowed) onPlayerComposed()
                }
            },
        )
    }

    private fun target(requestId: Long, channelId: Int, name: String) = ApplianceLaunchTarget(
        request = ApplianceLaunchRequest(requestId),
        channelId = ChannelId(channelId.toLong()),
        channelName = name,
    )

    private fun channel(id: Int, name: String) = Channel.create(
        id = ChannelId(id.toLong()),
        name = name,
        number = id.toLong(),
    )

}
