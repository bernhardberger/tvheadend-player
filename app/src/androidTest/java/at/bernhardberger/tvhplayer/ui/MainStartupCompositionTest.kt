package at.bernhardberger.tvhplayer.ui

import android.animation.ValueAnimator
import android.view.KeyEvent
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
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
import at.bernhardberger.tvhplayer.core.MainStartupLoadingTiming
import at.bernhardberger.tvhplayer.core.MainStartupPlaybackOutcome
import at.bernhardberger.tvhplayer.core.MainStartupReveal
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun startupGatedPlayerContentMountsOnlyTheEnteringLiveRouteBeforeReveal() {
        var contentAllowed by mutableStateOf(false)
        var enteringPlayer by mutableStateOf(false)
        var playerContentInvocations = 0
        composeRule.setContent {
            StartupGatedPlayerContent(contentAllowed = contentAllowed, enteringPlayer = enteringPlayer) {
                playerContentInvocations++
            }
        }

        composeRule.runOnIdle {
            assertEquals(0, playerContentInvocations)
            enteringPlayer = true
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, playerContentInvocations)
            contentAllowed = true
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue(playerContentInvocations >= 1) }
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

        composeRule.onNodeWithTag("main-startup-root").assertExists()
        composeRule.onNodeWithTag("main-startup-status").assertDoesNotExist()
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

        composeRule.onNodeWithTag("main-startup-root").assertExists()
        composeRule.onNodeWithTag("main-startup-status").assertDoesNotExist()
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
            assertTrue(playerCompositions > 0)
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
    fun exactEnteringRouteMountsBehindStartupWhileChromeAndActivityKeysRemainBlocked() {
        val entering = ApplianceLaunchState.Entering(
            target(requestId = 15, channelId = 15, name = "Exact"),
        )
        val passive = MainStartupPresentation.Passive(
            MainStartupMessageKind.STARTING_TELEVISION,
        )
        var launchCommitted by mutableStateOf(false)
        var registered: MainStartupActivityKeyContract? = null
        var playerContentCompositions = 0
        var playerChromeCompositions = 0

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
                        StartupGatedPlayerContent(contentAllowed, enteringPlayer = true) {
                            playerContentCompositions++
                            if (contentAllowed) playerChromeCompositions++
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("main-startup-root").assertExists()
        composeRule.runOnIdle {
            assertEquals(
                MainStartupKeyMode.Passive,
                registered?.mode,
            )
            assertTrue(playerContentCompositions > 0)
            assertEquals(0, playerChromeCompositions)
            launchCommitted = true
        }

        composeRule.onNodeWithText("Starting playback").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(MainStartupKeyMode.Inactive, registered?.mode)
            assertTrue(playerContentCompositions > 0)
            assertTrue(playerChromeCompositions > 0)
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
                outcome = MainStartupPlaybackOutcome.PRESENTED,
            ),
        )
        assertEquals(ApplianceLaunchState.Entering(target), requests.state.value)
        assertFalse(completeEnteringPlayerVisibility(requests, target, target.channelId, target.channelName, null))
        assertFalse(completeEnteringPlayerVisibility(requests, target, target.channelId, target.channelName, MainStartupPlaybackOutcome.RECOVERY))
        assertTrue(
            completeEnteringPlayerVisibility(
                requests = requests,
                target = target,
                channelId = target.channelId,
                channelName = target.channelName,
                outcome = MainStartupPlaybackOutcome.PRESENTED,
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

        composeRule.onNodeWithTag("main-startup-root").assertExists()
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

        composeRule.runOnIdle {
            assertTrue(composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.runOnIdle {
            assertEquals(1, normalCancels)
            showStartup = false
        }
        composeRule.runOnIdle { assertNull(registered) }
    }

    @Test
    fun successfulPresentationFadesOnlyTheCoverAndHeldOkCannotActivateTheRevealedPlayer() {
        var state by mutableStateOf(MainStartupCompositionState(
            MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION),
            LivePlayerKey(1L, "One"), true, contentAllowed = false,
        ))
        var contract: MainStartupActivityKeyContract? = null
        val keyOwner = MainStartupKeyCycleOwner()
        var surfaceMounts = 0
        var surfaceDisposals = 0
        var revealed = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(
                    state, {}, {}, { next -> contract = next; {} },
                    motionEnabled = true,
                    onRevealed = {
                        revealed++
                        state = state.copy(revealRequestId = null, contentAllowed = true)
                    },
                    persistentSurface = {
                        DisposableEffect(Unit) {
                            surfaceMounts++
                            onDispose { surfaceDisposals++ }
                        }
                        Box(Modifier.fillMaxSize().background(Color(0xFF35506D)))
                    },
                )
            }
        }
        composeRule.onNodeWithTag("main-startup-root").assertExists()
        composeRule.runOnIdle {
            assertTrue(dispatchMainStartupKeyEvent(keyOwner, requireNotNull(contract), KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)))
            state = state.copy(presentation = MainStartupPresentation.Inactive, revealRequestId = 1L)
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(80L, ignoreFrameDuration = true)
        val mid = composeRule.onRoot().captureToImage().toPixelMap()[8, 8]
        assertTrue("video exists below a partially faded cover", mid.blue > 20f / 255f && mid.blue < 109f / 255f)
        composeRule.runOnIdle {
            assertEquals(MainStartupKeyMode.Passive, contract?.mode)
            assertEquals(0, revealed)
            assertTrue(dispatchMainStartupKeyEvent(keyOwner, requireNotNull(contract),
                KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1)))
        }
        composeRule.mainClock.advanceTimeBy(240L)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("main-startup-root").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, revealed)
            assertEquals(1, surfaceMounts)
            assertEquals(0, surfaceDisposals)
            assertEquals(MainStartupKeyMode.Inactive, contract?.mode)
            assertTrue(dispatchMainStartupKeyEvent(keyOwner, requireNotNull(contract),
                KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 2)))
            assertTrue(dispatchMainStartupKeyEvent(keyOwner, requireNotNull(contract), KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)))
            assertFalse(dispatchMainStartupKeyEvent(keyOwner, requireNotNull(contract), KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)))
        }
    }

    @Test
    fun backDuringRevealImmediatelyRemovesCoverAndTheCancelledFadeCannotReturn() {
        var state by mutableStateOf(MainStartupCompositionState(
            MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION),
            LivePlayerKey(1L, "One"), true, contentAllowed = false,
        ))
        var revealed = 0
        var back = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(
                    state,
                    onBack = {
                        back++
                        state = MainStartupCompositionState(MainStartupPresentation.Inactive, ChannelsKey, true)
                    },
                    onAction = {}, registerActivityKeyContract = { {} },
                    motionEnabled = true, onRevealed = { revealed++ },
                )
            }
        }
        composeRule.onNodeWithTag("main-startup-root").assertExists()
        composeRule.runOnIdle { state = state.copy(presentation = MainStartupPresentation.Inactive, revealRequestId = 1L) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("main-startup-root").assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.runOnIdle {
            assertEquals(1, back)
            assertEquals(ChannelsKey, state.navigationStartDestination)
            assertEquals(0, revealed)
        }
    }

    @Test
    fun profileReplacementDuringFadeReleasesChromeAndKeysWhileSessionObservationStillLags() {
        var profileGeneration by mutableStateOf(4L)
        var ready by mutableStateOf(false)
        val delayedObservation = SessionObservation.create()
        val observations = MutableStateFlow(delayedObservation)
        val reveal = MainStartupReveal(ApplianceLaunchTarget(ApplianceLaunchRequest(1L), ChannelId(1L), "One"), 4L)
        var contract: MainStartupActivityKeyContract? = null
        val keyOwner = MainStartupKeyCycleOwner()
        var clicks = 0
        var fadeCompleted = 0
        var surfaceMounts = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            TVHeadendPlayerTheme {
                val target = reveal.targetFor(profileGeneration)
                MainStartupComposition(
                    MainStartupCompositionState(
                        if (!ready) MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION)
                        else MainStartupPresentation.Inactive, LivePlayerKey(1L, "One"), true,
                        contentAllowed = target == null, revealRequestId = if (ready) target?.request?.id else null),
                    {}, {}, { next -> contract = next; {} }, motionEnabled = true,
                    onRevealed = { fadeCompleted++ },
                    persistentSurface = {
                        DisposableEffect(Unit) { surfaceMounts++; onDispose { } }
                        Box(Modifier.fillMaxSize().background(Color(0xFF35506D)))
                    },
                    navigation = { _, allowed ->
                        StartupGatedPlayerContent(allowed, enteringPlayer = true) {
                            if (allowed) {
                                val focus = remember { FocusRequester() }
                                LaunchedEffect(Unit) { focus.requestFocus() }
                                androidx.tv.material3.Button(onClick = { clicks++ }, modifier = Modifier
                                    .focusRequester(focus).testTag("revealed-player-control")) {
                                    androidx.tv.material3.Text("Play")
                                }
                            }
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithTag("main-startup-root").assertExists()
        composeRule.runOnIdle { ready = true }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("main-startup-root").assertExists()
        composeRule.onNodeWithTag("revealed-player-control").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(dispatchMainStartupKeyEvent(keyOwner, requireNotNull(contract), KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)))
        }
        composeRule.mainClock.advanceTimeBy(80L)
        composeRule.runOnIdle { profileGeneration = 5L }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("main-startup-root").assertDoesNotExist()
        composeRule.onNodeWithTag("revealed-player-control").assertIsFocused()
        composeRule.runOnIdle {
            assertTrue("the new session publication is deliberately still delayed", observations.value === delayedObservation)
            assertEquals(MainStartupKeyMode.Inactive, contract?.mode)
            assertTrue(dispatchMainStartupKeyEvent(keyOwner, requireNotNull(contract),
                KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1)))
            assertTrue(dispatchMainStartupKeyEvent(keyOwner, requireNotNull(contract), KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)))
            assertEquals(0, clicks)
            assertEquals(1, surfaceMounts)
        }
        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.runOnIdle { assertEquals("withdrawn fade never finishes later", 0, fadeCompleted) }
        composeRule.onNodeWithTag("revealed-player-control").performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.DirectionCenter)
        }
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun reducedMotionReleasesImmediatelyAndRecoveryCancelsRatherThanFades() {
        var state by mutableStateOf(MainStartupCompositionState(
            MainStartupPresentation.Inactive, LivePlayerKey(1L, "One"), true,
            contentAllowed = false, revealRequestId = 1L,
        ))
        var motion by mutableStateOf(false)
        var revealed = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(
                    state, {}, {}, { {} }, motionEnabled = motion,
                    onRevealed = {
                        revealed++
                        state = state.copy(revealRequestId = null, contentAllowed = true)
                    },
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("main-startup-root").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, revealed)
            motion = true
            state = state.copy(revealRequestId = 2L, contentAllowed = false)
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            state = state.copy(revealRequestId = null, presentation = MainStartupPresentation.Actionable(
                MainStartupMessageKind.RETRYABLE_FAILURE, listOf(MainStartupActionId.RETRY),
            ))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("main-startup-action-RETRY").assertIsFocused()
        val recovery = composeRule.onRoot().captureToImage().toPixelMap()[8, 8]
        assertEquals(Color(0xFF0F1014), recovery)
        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.runOnIdle { assertEquals(1, revealed) }
    }

    @Test
    fun hiddenFeedbackCannotAppearDuringRevealAfterRealGrace() {
        val intro = at.bernhardberger.tvhplayer.ui.startup.StartupBrandIntro(
            at.bernhardberger.tvhplayer.core.MainStartupProcessEntry().claim(false))
        composeRule.runOnUiThread {
            intro.resumed(true); intro.focused(true); intro.entranceReady()
        }
        var state by mutableStateOf(MainStartupCompositionState(
            MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING), null, false,
        ))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { androidx.compose.runtime.CompositionLocalProvider(
            at.bernhardberger.tvhplayer.ui.startup.LocalStartupBrandIntro provides intro,
        ) { TVHeadendPlayerTheme {
            MainStartupComposition(state, {}, {}, { {} }, motionEnabled = true)
        } } }
        composeRule.onNodeWithTag("main-startup-status").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals("Opening-frame regression requires enabled animation", 0f, intro.millis)
        }
        val before = composeRule.onNodeWithTag("main-startup-mark").captureToImage().toPixelMap()
        composeRule.runOnIdle { state = state.copy(presentation = MainStartupPresentation.Inactive, revealRequestId = 1L) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val handoff = SystemClock.uptimeMillis()
        composeRule.waitUntil(1_500L) { SystemClock.uptimeMillis() - handoff >= 450L }
        composeRule.onNodeWithTag("main-startup-root").assertExists()
        composeRule.onNodeWithTag("main-startup-status").assertDoesNotExist()
        composeRule.onNodeWithTag("main-startup-loader").assertDoesNotExist()
        val after = composeRule.onNodeWithTag("main-startup-mark").captureToImage().toPixelMap()
        var newlyFilled = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            if (before[x, y].red < 0.15f && after[x, y].red > 0.3f) newlyFilled++
        }
        assertEquals("pending opening art must not snap to its endpoint", 0, newlyFilled)
        composeRule.runOnIdle { assertFalse(intro.running) }
    }

    @Test
    fun partialAssemblyAndExistingStatusAreRetainedAtReadiness() {
        val intro = at.bernhardberger.tvhplayer.ui.startup.StartupBrandIntro(true)
        composeRule.runOnUiThread {
            intro.observe(at.bernhardberger.tvhplayer.core.ConnectionUiState.SyncingChannels)
            intro.resumed(true); intro.focused(true); intro.entranceReady()
            intro.passive(true, gracePassed = true)
            intro.frame(900f)
            assertTrue("This handoff regression requires animator_duration_scale > 0", intro.running)
            assertEquals(900f, intro.millis)
        }
        var state by mutableStateOf(MainStartupCompositionState(
            MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_METADATA), null, false,
        ))
        val timing = MainStartupLoadingTiming().apply { observe(true, null, SystemClock.uptimeMillis() - 500L) }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                at.bernhardberger.tvhplayer.ui.startup.LocalStartupBrandIntro provides intro,
            ) { TVHeadendPlayerTheme {
                MainStartupComposition(state, {}, {}, { {} }, loadingTiming = timing, motionEnabled = true)
            } }
        }
        composeRule.onNodeWithTag("main-startup-status").assertTextEquals("Syncing channels and guide")
        // Production publishes metadata before it enters the passive video-tune wait.
        composeRule.runOnIdle {
            intro.observe(at.bernhardberger.tvhplayer.core.ConnectionUiState.Ready)
            assertTrue(intro.running)
            assertEquals(900f, intro.millis)
            state = state.copy(presentation = MainStartupPresentation.Enter(ApplianceLaunchRequest(1L)))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            state = state.copy(presentation = MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            assertTrue(intro.running)
            assertEquals(900f, intro.millis)
        }
        composeRule.onNodeWithTag("main-startup-status").assertTextEquals("Starting playback")
        val before = composeRule.onNodeWithTag("main-startup-mark").captureToImage().toPixelMap()
        composeRule.runOnIdle { state = state.copy(presentation = MainStartupPresentation.Inactive, revealRequestId = 1L) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val after = composeRule.onNodeWithTag("main-startup-mark").captureToImage().toPixelMap()
        composeRule.onNodeWithTag("main-startup-status").assertTextEquals("Starting playback")
        composeRule.onNodeWithText("Syncing channels and guide").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1850f, intro.millis)
            assertEquals(900f, intro.outgoingMillis)
        }
        // A fade can darken outgoing pixels; it must not fill the dark gaps with settled artwork.
        var newlyFilled = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            if (before[x, y].red < 0.15f && after[x, y].red > 0.3f) newlyFilled++
        }
        assertEquals("assembly gaps remain frozen during dismissal", 0, newlyFilled)
    }

    @Test
    fun connectingColdOpeningAndEarliestFeedbackKeepFinalBrandedCoordinates() {
        val intro = at.bernhardberger.tvhplayer.ui.startup.StartupBrandIntro(
            at.bernhardberger.tvhplayer.core.MainStartupProcessEntry().claim(false))
        composeRule.runOnUiThread {
            intro.observe(at.bernhardberger.tvhplayer.core.ConnectionUiState.Connecting)
            intro.resumed(true); intro.focused(true)
        }
        val timing = MainStartupLoadingTiming().apply {
            observe(true, null, SystemClock.uptimeMillis() - 500L)
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                at.bernhardberger.tvhplayer.ui.startup.LocalStartupBrandIntro provides intro,
            ) { TVHeadendPlayerTheme {
                MainStartupComposition(
                    MainStartupCompositionState(
                        MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING), null, false),
                    {}, {}, { {} }, loadingTiming = timing, motionEnabled = true,
                )
            } }
        }
        // Admission goes through the production SideEffect, not a fixture's passive(true).
        // Entrance is deliberately pending: even this opening frame must retain the mark slot.
        val openingMark = composeRule.onNodeWithTag("main-startup-mark").fetchSemanticsNode().boundsInRoot
        val openingLoader = composeRule.onNodeWithTag("main-startup-loader").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("main-startup-status").assertTextEquals("Connecting")
        composeRule.runOnIdle {
            assertFalse(intro.running)
            assertEquals(0f, intro.millis)
            intro.entranceReady()
            assertTrue("This regression requires animator_duration_scale > 0", intro.running)
            intro.frame(900f)
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(openingMark, composeRule.onNodeWithTag("main-startup-mark").fetchSemanticsNode().boundsInRoot)
        assertEquals(openingLoader, composeRule.onNodeWithTag("main-startup-loader").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun existingPlaybackWaitHasNoBrandReplayAndFreshTuneIsNotResume() {
        var state by mutableStateOf(MainStartupCompositionState(
            MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING), null, false,
            hideBranding = true,
        ))
        val timing = MainStartupLoadingTiming().apply { observe(true, null, SystemClock.uptimeMillis() - 500L) }
        composeRule.setContent { TVHeadendPlayerTheme {
            MainStartupComposition(state, {}, {}, { {} }, loadingTiming = timing, motionEnabled = true)
        } }
        composeRule.onNodeWithTag("main-startup-mark").assertDoesNotExist()
        composeRule.onNodeWithTag("main-startup-status").assertTextEquals("Connecting")
        composeRule.runOnIdle { state = state.copy(
            presentation = MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION),
            returningToPlayback = true,
        ) }
        composeRule.onNodeWithTag("main-startup-mark").assertDoesNotExist()
        composeRule.onNodeWithTag("main-startup-status").assertTextEquals("Resuming playback")
        composeRule.runOnIdle { state = state.copy(returningToPlayback = false) }
        composeRule.onNodeWithTag("main-startup-mark").assertDoesNotExist()
        composeRule.onNodeWithTag("main-startup-status").assertTextEquals("Starting playback")
        composeRule.runOnIdle { state = state.copy(hideBranding = false) }
        composeRule.onNodeWithTag("main-startup-mark").assertExists()
        composeRule.onNodeWithTag("main-startup-status").assertTextEquals("Starting playback")
    }

    @Test
    fun localToCachedBranchReplacementAndTuningKeepOneFeedbackClockAndIndicator() {
        val timing = MainStartupLoadingTiming()
        var branch by mutableStateOf(0)
        var state by mutableStateOf(MainStartupCompositionState(
            presentation = MainStartupPresentation.Inactive,
            navigationStartDestination = null,
            navigationAllowed = false,
        ))
        composeRule.setContent {
            TVHeadendPlayerTheme {
                key(branch) {
                    MainStartupComposition(state, {}, {}, { {} }, loadingTiming = timing)
                }
            }
        }
        val startedAt = composeRule.runOnIdle {
            state = state.copy(presentation = MainStartupPresentation.Passive(MainStartupMessageKind.PREPARING))
            SystemClock.uptimeMillis()
        }
        composeRule.onNodeWithTag("main-startup-root").assertExists()
        // Compose's clock is not the Handler clock used for the real 400ms grace.
        composeRule.mainClock.advanceTimeBy(5_000L)
        composeRule.waitUntil(1_500L) {
            val feedbackTag = if (ValueAnimator.areAnimatorsEnabled()) {
                "main-startup-loader"
            } else {
                "main-startup-status"
            }
            composeRule.onAllNodesWithTag(feedbackTag).fetchSemanticsNodes().isNotEmpty()
        }
        assertWaitingFeedback("Preparing app")
        val elapsedBeforeReplacement = composeRule.runOnIdle {
            timing.elapsedMillis(true, null, SystemClock.uptimeMillis())
        }
        assertTrue(elapsedBeforeReplacement >= 400L)
        val continuousStartedAt = composeRule.runOnIdle {
            val now = SystemClock.uptimeMillis()
            now - timing.elapsedMillis(true, null, now)
        }
        composeRule.runOnIdle {
            branch = 1 // Same owner above the resolving/configured composition boundary.
            state = state.copy(
                presentation = MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_METADATA),
                requestId = 1L,
            )
        }
        assertWaitingFeedback("Syncing channels and guide")
        composeRule.runOnIdle {
            val now = SystemClock.uptimeMillis()
            assertEquals(now - continuousStartedAt, timing.elapsedMillis(true, 1L, now))
        }
        listOf(MainStartupMessageKind.RECONNECTING, MainStartupMessageKind.SYNCING_METADATA, MainStartupMessageKind.STARTING_TELEVISION).forEach { stage ->
            composeRule.runOnIdle {
                state = state.copy(presentation = MainStartupPresentation.Passive(
                    stage,
                ))
            }
            composeRule.runOnIdle {
                val now = SystemClock.uptimeMillis()
                assertEquals(now - continuousStartedAt, timing.elapsedMillis(true, 1L, now))
            }
        }
        composeRule.waitUntil(2_500L) { SystemClock.uptimeMillis() - startedAt >= 2_000L }
        assertWaitingFeedback("Starting playback")
        composeRule.runOnIdle {
            assertTrue(timing.elapsedMillis(true, 1L, SystemClock.uptimeMillis()) >= 2_000L)
            assertTrue(SystemClock.uptimeMillis() - startedAt < 3_000L)
        }
    }

    @Test
    fun readinessOrBackCannotBeOverlaidByAnOldDelayedFeedbackCallback() {
        val timing = MainStartupLoadingTiming()
        val cached = MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
        var state by mutableStateOf(MainStartupCompositionState(cached, null, false, requestId = 1L))
        var entered = 0
        var backCalls = 0
        fun enterContent() {
            state = MainStartupCompositionState(MainStartupPresentation.Inactive, ChannelsKey, true)
        }
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(
                    state, { backCalls++; enterContent() }, {}, { {} }, loadingTiming = timing,
                    navigation = { _, allowed -> if (allowed) entered++ },
                )
            }
        }
        composeRule.onNodeWithTag("main-startup-root").assertExists()
        composeRule.runOnIdle { enterContent() }
        composeRule.onNodeWithTag("main-startup-root").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(entered > 0)
            state = MainStartupCompositionState(cached, null, false, requestId = 2L)
        }
        // The fresh passive branch must apply BackHandler before dispatching to its main-thread owner.
        composeRule.onNodeWithTag("main-startup-root").assertExists()
        composeRule.runOnIdle {
            assertTrue(composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
            assertEquals(1, backCalls)
        }
        composeRule.onNodeWithTag("main-startup-root").assertDoesNotExist()
        val leftAt = SystemClock.uptimeMillis()
        composeRule.waitUntil(3_000L) { SystemClock.uptimeMillis() - leftAt >= 2_100L }
        composeRule.onNodeWithTag("main-startup-root").assertDoesNotExist()
        composeRule.onAllNodesWithTag("main-startup-loader").assertCountEquals(0)
    }

    @Test
    fun recoveryRetryStartsFreshGraceAndResolvingBackSuppressesPendingFeedback() {
        val timing = MainStartupLoadingTiming().apply {
            observe(true, 1L, SystemClock.uptimeMillis() - 2_000L)
        }
        val cached = MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_METADATA)
        var state by mutableStateOf(MainStartupCompositionState(cached, null, false, requestId = 1L))
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(
                    state, {}, { state = state.copy(presentation = cached) }, { {} }, loadingTiming = timing,
                )
            }
        }
        assertWaitingFeedback("Syncing channels and guide")
        composeRule.runOnIdle {
            state = state.copy(presentation = MainStartupPresentation.Actionable(
                MainStartupMessageKind.RETRYABLE_FAILURE, listOf(MainStartupActionId.RETRY),
            ))
        }
        composeRule.onNodeWithTag("main-startup-action-RETRY").assertIsFocused().performClick()
        composeRule.onNodeWithTag("main-startup-status").assertDoesNotExist()
        composeRule.onNodeWithTag("main-startup-loader").assertDoesNotExist()
        composeRule.runOnIdle { state = state.copy(loadingFeedbackEnabled = false) }
        val cancelledAt = SystemClock.uptimeMillis()
        composeRule.waitUntil(3_000L) { SystemClock.uptimeMillis() - cancelledAt >= 2_100L }
        composeRule.onNodeWithTag("main-startup-status").assertDoesNotExist()
        composeRule.onNodeWithTag("main-startup-loader").assertDoesNotExist()
    }

    private fun assertWaitingFeedback(status: String) {
        if (ValueAnimator.areAnimatorsEnabled()) {
            composeRule.onNodeWithTag("main-startup-loader").assertIsDisplayed()
        } else {
            composeRule.onNodeWithTag("main-startup-loader").assertDoesNotExist()
        }
        composeRule.onNodeWithTag("main-startup-status").assertIsDisplayed().assertTextEquals(status)
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
                    StartupGatedPlayerContent(contentAllowed, enteringPlayer = true) { onPlayerComposed() }
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
