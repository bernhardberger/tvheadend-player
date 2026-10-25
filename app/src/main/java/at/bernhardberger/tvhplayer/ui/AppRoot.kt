package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvhplayer.ui.components.ProvideWatchingProgress

import at.bernhardberger.tvhplayer.profiling.profileRouteDraw

import androidx.activity.compose.BackHandler
import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.ApplianceLaunchState
import at.bernhardberger.tvhplayer.core.ApplianceLaunchTarget
import at.bernhardberger.tvhplayer.core.BackAction
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupLoadingTiming
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.MainStartupPlaybackOutcome
import at.bernhardberger.tvhplayer.core.mainStartupReturningToPlayback
import at.bernhardberger.tvhplayer.core.MainStartupReveal
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.core.mainStartupPresentation
import at.bernhardberger.tvhplayer.core.rootBackAction
import at.bernhardberger.tvhplayer.core.serverSettingsForRuntime
import at.bernhardberger.tvhplayer.core.showGlobalNavigationRail
import at.bernhardberger.tvhplayer.core.RecordingFinishedAction
import at.bernhardberger.tvhplayer.core.recordingFinishedAction
import at.bernhardberger.tvhplayer.core.shouldMountPersistentPlayerSurface
import at.bernhardberger.tvhplayer.core.shouldShowWarmPlaybackScrim
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateRequest
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.currentLivePlaybackSelection
import at.bernhardberger.tvhplayer.ui.startup.rememberColdStart
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.ui.components.ProvideAppArtwork
import at.bernhardberger.tvhplayer.ui.components.SideRail
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame
import at.bernhardberger.tvhplayer.ui.player.PlayerVideoSurface
import at.bernhardberger.tvhplayer.ui.player.leaving
import at.bernhardberger.tvhplayer.ui.player.rememberVideoCoverFadeMillis
import at.bernhardberger.tvhplayer.ui.screens.OnboardingScreen
import at.bernhardberger.tvhplayer.ui.screens.PlayerReturnFocus
import at.bernhardberger.tvhplayer.ui.screens.RecordingsScreenState
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyMode
import at.bernhardberger.tvhplayer.ui.startup.MainStartupScreen
import at.bernhardberger.tvhplayer.ui.startup.startupLoadingFeedback
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

internal data class MainStartupCompositionState(
    val presentation: MainStartupPresentation,
    val navigationStartDestination: AppNavKey?,
    val navigationAllowed: Boolean,
    val contentAllowed: Boolean = presentation == MainStartupPresentation.Inactive,
    val requestId: Long? = null,
    val loadingFeedbackEnabled: Boolean = true,
    val revealRequestId: Long? = null,
    val returningToPlayback: Boolean = false,
    val hideBranding: Boolean = returningToPlayback,
)

private data class MainStartupOutgoingVisual(
    val presentation: MainStartupPresentation,
    val feedback: at.bernhardberger.tvhplayer.core.MainStartupLoadingFeedback,
    val brandingVisible: Boolean,
)

internal fun enteringNavigationAllowed(
    hasBackStackEntry: Boolean,
    navigationStartDestination: AppNavKey?,
    exactStartDestination: AppNavKey,
    matchingVisiblePlayer: Boolean,
): Boolean = if (hasBackStackEntry) matchingVisiblePlayer else navigationStartDestination == exactStartDestination

internal fun completeEnteringPlayerVisibility(
    requests: ApplianceLaunchRequests,
    target: ApplianceLaunchTarget,
    channelId: ChannelId,
    channelName: String,
    outcome: MainStartupPlaybackOutcome?,
): Boolean = outcome != null && outcome != MainStartupPlaybackOutcome.RECOVERY &&
    requests.completePlayerVisibility(target, channelId, channelName)

internal fun shouldShowMainNavigationRail(
    currentDestination: AppNavKey?,
    navigationStartDestination: AppNavKey?,
): Boolean = showGlobalNavigationRail(
    playerVisible = when (currentDestination ?: navigationStartDestination) {
        is LivePlayerKey, is RecordingPlayerKey -> true
        else -> false
    },
)

/**
 * Browse with the rail, or a full-screen player. With [motionEnabled] the two cross-fade like the
 * player destination, so the rail and its page fade with the player instead of leaving in one
 * frame; the leaving shell keeps its layout but gives up focus, semantics and Back at once.
 * Otherwise the leaving shell is removed in the frame the other one appears.
 */
@Composable
internal fun MainNavigationShell(
    showRail: Boolean,
    rail: @Composable () -> Unit,
    fullScreen: @Composable () -> Unit,
    motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
) {
    AnimatedContent(
        targetState = showRail,
        transitionSpec = {
            if (!motionEnabled) EnterTransition.None togetherWith ExitTransition.None using null
            else if (targetState) playerExitContentTransform(motionEnabled = true) using null
            else playerDestinationContentTransform(motionEnabled = true) using null
        },
        label = "navigation-shell",
    ) { railShown ->
        val exiting = leaving
        if (exiting && !motionEnabled) return@AnimatedContent
        val backOwner = rememberNavigationEventDispatcherOwner(enabled = !exiting)
        // The owner applies `enabled` from an effect; a leaving shell stops answering Back as it commits.
        SideEffect { backOwner.navigationEventDispatcher.isEnabled = !exiting }
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides backOwner) {
            // A move reversed mid-fade leaves this shell's entry focus spent: its frame restores it.
            PlayerMotionFrame(leaving = exiting, restoreFocusOnReturn = true) { if (railShown) rail() else fullScreen() }
        }
    }
}

/**
 * The back stack a shell shows: the live one while its top belongs to the shell, else the one it
 * showed last, so a fading shell keeps its page instead of following the navigation.
 */
@Composable
private fun rememberShellBackStack(backStack: List<AppNavKey>, playerShell: Boolean): List<AppNavKey> {
    val shown = remember { ShellBackStack() }
    val top = backStack.lastOrNull()
    val own = top == null || (top is LivePlayerKey || top is RecordingPlayerKey) == playerShell
    SideEffect { if (own) shown.keys = backStack.toList() }
    return if (own || shown.keys.isEmpty()) backStack else shown.keys
}

private class ShellBackStack {
    var keys: List<AppNavKey> = emptyList()
}

@Composable
internal fun MainStartupComposition(
    state: MainStartupCompositionState,
    onBack: () -> Unit,
    onAction: (AppErrorAction) -> Unit,
    registerActivityKeyContract: (MainStartupActivityKeyContract) -> (() -> Unit),
    modifier: Modifier = Modifier,
    loadingTiming: MainStartupLoadingTiming = remember { MainStartupLoadingTiming() },
    showWarmPlaybackScrim: Boolean = false,
    warmPlaybackScrimAlpha: Float = WarmPlaybackScrimAlpha,
    persistentSurface: @Composable BoxScope.() -> Unit = {},
    navigation: @Composable BoxScope.(AppNavKey, Boolean) -> Unit = { _, _ -> },
    notices: @Composable BoxScope.() -> Unit = {},
    motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
    onRevealed: () -> Unit = {},
) {
    val livePresentation = when (state.presentation) {
        is MainStartupPresentation.Enter -> MainStartupPresentation.Passive(
            if (state.returningToPlayback) MainStartupMessageKind.RESUMING_PLAYBACK
            else MainStartupMessageKind.STARTING_TELEVISION,
        )
        MainStartupPresentation.Inactive -> MainStartupPresentation.Inactive
        else -> if (state.returningToPlayback && state.presentation ==
            MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION)) {
            MainStartupPresentation.Passive(MainStartupMessageKind.RESUMING_PLAYBACK)
        } else state.presentation
    }
    val revealRequestId = state.revealRequestId.takeIf { state.presentation == MainStartupPresentation.Inactive }
    val revealAlpha = remember(revealRequestId) { Animatable(1f) }
    val latestOnRevealed by androidx.compose.runtime.rememberUpdatedState(onRevealed)
    LaunchedEffect(revealRequestId, motionEnabled) {
        if (revealRequestId == null) return@LaunchedEffect
        if (motionEnabled) {
            revealAlpha.animateTo(0f, tween(StartupRevealMs, PlayerMotion.PictureHoldMs, PlayerMotion.Standard))
        }
        latestOnRevealed()
    }
    val loadingFeedback = startupLoadingFeedback(
        presentation = livePresentation,
        timing = loadingTiming,
        requestId = state.requestId,
        enabled = state.loadingFeedbackEnabled,
    )
    var outgoing by remember { mutableStateOf<MainStartupOutgoingVisual?>(null) }
    val retained = remember(revealRequestId) { if (revealRequestId != null) outgoing else null }
    val renderedPresentation = retained?.presentation ?: livePresentation
    SideEffect {
        if (revealRequestId == null && livePresentation != MainStartupPresentation.Inactive) {
            outgoing = MainStartupOutgoingVisual(
                livePresentation, loadingFeedback,
                !state.hideBranding,
            )
        }
    }
    val keyMode = when (renderedPresentation) {
        MainStartupPresentation.Inactive -> MainStartupKeyMode.Inactive
        is MainStartupPresentation.Passive -> MainStartupKeyMode.Passive
        is MainStartupPresentation.Actionable -> MainStartupKeyMode.Actionable
        is MainStartupPresentation.Enter -> error("Enter is rendered as passive startup")
    }
    val revealScope = rememberCoroutineScope()
    val keyContract = remember(keyMode, revealRequestId) {
        MainStartupActivityKeyContract(
            mode = keyMode,
            // A key during the reveal ends it at once; the next key reaches the revealed content.
            onConsumedKeyDown = revealRequestId?.let {
                {
                    revealScope.launch { revealAlpha.snapTo(0f) }
                    latestOnRevealed()
                }
            },
        )
    }

    DisposableEffect(registerActivityKeyContract, keyContract) {
        val unregister = registerActivityKeyContract(keyContract)
        onDispose(unregister)
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .semantics { testTagsAsResourceId = true }) {
        persistentSurface()
        if (showWarmPlaybackScrim) WarmPlaybackScrim(targetAlpha = warmPlaybackScrimAlpha)
        val startDestination = state.navigationStartDestination
        if (state.navigationAllowed && startDestination != null) {
            CompositionLocalProvider(LocalWarmPlayback provides showWarmPlaybackScrim) {
                navigation(
                    startDestination,
                    state.contentAllowed,
                )
            }
        }
        if (renderedPresentation != MainStartupPresentation.Inactive) {
            MainStartupScreen(
                presentation = renderedPresentation,
                contentPadding = TvFullScreenPadding,
                onAction = onAction,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = if (revealRequestId != null) revealAlpha.value else 1f
                },
                loadingFeedback = retained?.feedback ?: loadingFeedback,
                // Pending cold art has zero-alpha tracks, but keeps its final layout.
                // Only the existing-playback classification removes branding.
                brandingVisible = retained?.brandingVisible ?: !state.hideBranding,
                motionEnabled = motionEnabled,
            )
        }
        notices()
    }

    BackHandler(
        enabled = renderedPresentation != MainStartupPresentation.Inactive,
        onBack = onBack,
    )
}

/**
 * Autoplay handoff: the full-screen startup screen fades into the first video frames over the
 * M3 long2 duration with M3 standard easing, after the first picture's [PlayerMotion.PictureHoldMs].
 */
internal const val StartupRevealMs = 500

/** True while live playback continues behind the browse screens under [WarmPlaybackScrim]. */
internal val LocalWarmPlayback = compositionLocalOf { false }

@Composable
internal fun WarmPlaybackScrim(
    modifier: Modifier = Modifier,
    targetAlpha: Float = WarmPlaybackScrimAlpha,
) {
    val alpha = androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        label = "warmPlaybackScrimAlpha",
    )
    Box(modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = alpha.value)) }
        .testTag("warm-playback-scrim"))
}

internal fun cancelStartupAndSelectRoot(
    requests: ApplianceLaunchRequests,
    expectedState: ApplianceLaunchState,
    destination: AppNavKey,
    selectRoot: (AppNavKey) -> Unit,
): Boolean {
    if (!requests.cancel(expectedState)) return false
    selectRoot(destination)
    return true
}

internal fun closeNormalLivePlayer(
    popBackStack: () -> Boolean,
    selectRoot: (AppNavKey) -> Unit,
) {
    if (!popBackStack()) selectRoot(ChannelsKey)
}

internal fun performMainStartupBack(
    requests: ApplianceLaunchRequests,
    expectedState: ApplianceLaunchState,
    selectRoot: (AppNavKey) -> Unit,
) {
    cancelStartupAndSelectRoot(
        requests = requests,
        expectedState = expectedState,
        destination = ChannelsKey,
        selectRoot = selectRoot,
    )
}

internal enum class DeferredResolvingBackAction {
    NONE,
    CANCEL_TO_CHANNELS,
}

internal fun deferredResolvingBackAction(
    cancellationRequested: Boolean,
): DeferredResolvingBackAction = when {
    !cancellationRequested -> DeferredResolvingBackAction.NONE
    else -> DeferredResolvingBackAction.CANCEL_TO_CHANNELS
}

internal fun applyDeferredResolvingBack(
    action: DeferredResolvingBackAction,
    requests: ApplianceLaunchRequests,
    expectedState: ApplianceLaunchState,
    selectRoot: (AppNavKey) -> Unit,
): Boolean = when (action) {
    DeferredResolvingBackAction.NONE -> false
    DeferredResolvingBackAction.CANCEL_TO_CHANNELS -> cancelStartupAndSelectRoot(
        requests = requests,
        expectedState = expectedState,
        destination = ChannelsKey,
        selectRoot = selectRoot,
    )
}

internal fun performMainStartupAction(
    action: AppErrorAction,
    onRetry: () -> Unit,
    onConnectionSettings: () -> Unit,
) {
    when (action) {
        AppErrorAction.RETRY -> onRetry()
        AppErrorAction.CONNECTION_SETTINGS -> onConnectionSettings()
        // Startup errors are not closable; Back leaves startup.
        AppErrorAction.CLOSE -> Unit
    }
}

@Composable
fun AppRoot(
    startupState: MainStartupState,
    runtimeServerSettings: ServerSettings?,
    applianceLaunchRequests: ApplianceLaunchRequests,
    debugVideoBackdropVisible: Boolean = false,
    onPlayerVisibilityChanged: (Boolean) -> Unit,
    onRequestExit: () -> Unit,
    registerActivityKeyContract: (MainStartupActivityKeyContract) -> (() -> Unit) = { {} },
) {
    val applianceLaunchState by applianceLaunchRequests.state.collectAsStateWithLifecycle()
    val startupLoadingTiming = remember { MainStartupLoadingTiming() }
    var cancelBootstrapLaunchWhenReady by rememberSaveable { mutableStateOf(false) }
    val serverSettings = when (startupState) {
        MainStartupState.ResolvingLocal -> {
            val expectedLaunchState = applianceLaunchState
            val resolvingBack = remember(expectedLaunchState) {
                {
                    cancelBootstrapLaunchWhenReady = true
                    if (expectedLaunchState != ApplianceLaunchState.Idle) {
                        applianceLaunchRequests.cancel(expectedLaunchState)
                    }
                    Unit
                }
            }
            MainStartupComposition(
                state = MainStartupCompositionState(
                    presentation = MainStartupPresentation.Passive(
                        MainStartupMessageKind.PREPARING,
                    ),
                    navigationStartDestination = null,
                    navigationAllowed = false,
                    loadingFeedbackEnabled = !cancelBootstrapLaunchWhenReady,
                ),
                loadingTiming = startupLoadingTiming,
                onBack = resolvingBack,
                onAction = {},
                registerActivityKeyContract = registerActivityKeyContract,
            )
            return
        }
        is MainStartupState.Ready ->
            startupState.serverSettingsForRuntime(runtimeServerSettings)
    }
    val readyStartupState = startupState
    val deferredBackAction = deferredResolvingBackAction(
        cancellationRequested = cancelBootstrapLaunchWhenReady,
    )
    if (serverSettings.host.isBlank()) {
        MainStartupComposition(
            state = MainStartupCompositionState(
                presentation = MainStartupPresentation.Inactive,
                navigationStartDestination = ChannelsKey,
                navigationAllowed = true,
                contentAllowed = true,
            ),
            onBack = {},
            onAction = {},
            registerActivityKeyContract = registerActivityKeyContract,
            loadingTiming = startupLoadingTiming,
            navigation = { _, _ -> OnboardingScreen() },
        )
        return
    }

    var navigationStartDestination by remember {
        mutableStateOf<AppNavKey?>(
            when (val launchState = applianceLaunchState) {
                ApplianceLaunchState.Idle -> ChannelsKey
                is ApplianceLaunchState.Pending -> if (
                    deferredBackAction ==
                    DeferredResolvingBackAction.CANCEL_TO_CHANNELS
                ) {
                    ChannelsKey
                } else {
                    null
                }
                is ApplianceLaunchState.Entering -> if (
                    deferredBackAction ==
                    DeferredResolvingBackAction.CANCEL_TO_CHANNELS
                ) {
                    ChannelsKey
                } else {
                    LivePlayerKey(
                        channelId = launchState.target.channelId.value,
                        channelName = launchState.target.channelName,
                    )
                }
            },
        )
    }
    val backStack = rememberAppNavBackStack(
        *listOfNotNull(navigationStartDestination).toTypedArray(),
    )
    LaunchedEffect(navigationStartDestination) {
        val destination = navigationStartDestination
        if (destination != null && backStack.isEmpty()) {
            backStack.add(destination)
        }
    }
    LaunchedEffect(deferredBackAction, applianceLaunchState) {
        if (deferredBackAction == DeferredResolvingBackAction.NONE) {
            return@LaunchedEffect
        }
        val expectedState = applianceLaunchState
        applyDeferredResolvingBack(
            action = deferredBackAction,
            requests = applianceLaunchRequests,
            expectedState = expectedState,
            selectRoot = { navigationStartDestination = it },
        )
        cancelBootstrapLaunchWhenReady = false
    }
    val navigateTopLevel: (AppNavKey) -> Unit = { destination ->
        backStack.navigateTopLevel(destination)
    }
    val recordingsScreenState = remember { RecordingsScreenState() }
    val focusManager = LocalFocusManager.current

    val appVm: AppConnectionViewModel = koinViewModel()
    val channelsVm: at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel = koinViewModel()
    val connectionPresentation by appVm.uiState.collectAsStateWithLifecycle()
    val connectionUiState = connectionPresentation.connection
    val connectionState by appVm.connectionState.collectAsStateWithLifecycle()
    val lastPlayedChannelStore: LastPlayedChannelStore = koinInject()
    val playbackRuntime: AppPlaybackRuntime = koinInject()
    val tvheadendSession: TvheadendSession = koinInject()
    val playbackSelectionScope = rememberCoroutineScope()
    val playerEntry = remember { PlayerEntryHandoff() }
    val playbackOrchestrator = remember { AppRootPlaybackOrchestrator() }
    val playerSettingsStore: PlayerSettingsStore = koinInject()
    val playbackState by playbackRuntime.state.collectAsStateWithLifecycle()
    val activeTarget by playbackRuntime.activeTarget.collectAsStateWithLifecycle()
    val videoPresentation by playbackRuntime.videoPresentation.collectAsStateWithLifecycle()
    val activeChannelId = (activeTarget as? AppPlaybackTarget.Live)?.channelId
    val activeRecordingId = (activeTarget as? AppPlaybackTarget.Recording)?.recordingId
    val recordingSelection by playbackRuntime.recordingSelection.collectAsStateWithLifecycle()
    val playbackObservation by tvheadendSession.observation.collectAsStateWithLifecycle()
    val warmRecordingId = warmRecordingId(activeTarget, recordingSelection, playbackObservation.currentSession)
    val playerSettings by playerSettingsStore.playerSettings.collectAsStateWithLifecycle(
        initialValue = PlayerSettings()
    )
    val uiSettingsStore: UiSettingsStore = koinInject()
    val uiSettings by uiSettingsStore.settings.collectAsStateWithLifecycle(initialValue = UiSettings())
    LaunchedEffect(uiSettings.clockFormat) { AppClock.format = uiSettings.clockFormat }
    val profileOwner: AppProfileOwner = koinInject()
    val profileGeneration by profileOwner.configurationGeneration.collectAsStateWithLifecycle()
    val browseFailure = at.bernhardberger.tvhplayer.ui.components.rememberBrowseConnectionFailure(
        connectionUiState, profileGeneration, appVm::reconnectNow)
    val browseConnectionFailure = browseFailure.failure
    val retryBrowseConnection = browseFailure.retry
    val currentChannelReadiness = connectionPresentation.currentChannelReadiness
    var startupReveal by remember { mutableStateOf<MainStartupReveal?>(null) }
    val revealingTarget = startupReveal?.targetFor(profileGeneration)
    val startupPresentation = mainStartupPresentation(
        startupState = startupState,
        launchState = applianceLaunchState,
        connectionState = connectionUiState,
        currentChannelReadiness = currentChannelReadiness,
    )

    val currentDestination = backStack.lastOrNull()
    // Live player Back targets the browse destination it returned to, once.
    var livePlayerReturn by remember { mutableStateOf<Pair<AppNavKey, PlayerReturnFocus>?>(null) }
    var recordingPlayerReturnToGuide by remember { mutableStateOf(false) }
    var guideChannelRequest by remember { mutableStateOf<PlayerReturnFocus?>(null) }
    LaunchedEffect(currentDestination) {
        if (livePlayerReturn?.first != currentDestination) livePlayerReturn = null
        if (currentDestination != GuideKey) recordingPlayerReturnToGuide = false
        if (currentDestination != GuideKey) guideChannelRequest = null
    }
    val playerReturnFor = { destination: AppNavKey ->
        livePlayerReturn?.takeIf { it.first == destination }?.second
    }
    val showRail = shouldShowMainNavigationRail(
        currentDestination = currentDestination,
        navigationStartDestination = navigationStartDestination,
    )

    val isPlayer = currentDestination is LivePlayerKey ||
        currentDestination is RecordingPlayerKey
    // Warm live playback behind browse has no player screen to retune it after a reconnect;
    // a channel paused there is stopped, since the restart ended what its Pause held.
    LaunchedEffect(isPlayer, activeChannelId) {
        if (isPlayer || activeChannelId == null) return@LaunchedEffect
        tvheadendSession.observation.map { it.currentSession }.distinctUntilChanged { old, new -> old === new }
            .filterNotNull().collect {
                // Opening the player right after the reconnect must not abandon the retune halfway.
                withContext(NonCancellable) { playbackRuntime.retuneAfterReconnect(stopIfPaused = true) }
            }
    }
    val enteringLaunchTarget =
        (applianceLaunchState as? ApplianceLaunchState.Entering)?.target
    val launchRequest = (applianceLaunchState as? ApplianceLaunchState.Pending)?.request ?: enteringLaunchTarget?.request
    val enteringProfileGeneration = remember(launchRequest) { profileGeneration }
    val visibleLivePlayer = currentDestination as? LivePlayerKey
    val visibleLivePlayerChannelId = visibleLivePlayer?.channelId?.let(::ChannelId)
    val visibleLivePlayerName = visibleLivePlayer?.channelName
    val matchingEnteringPlayerVisible = enteringLaunchTarget?.let { target ->
        visibleLivePlayerChannelId != null &&
            visibleLivePlayerName != null &&
            target.matchesPlayer(
                channelId = visibleLivePlayerChannelId,
                channelName = visibleLivePlayerName,
            )
    } == true
    val coldStart = rememberColdStart(
        launchIdle = applianceLaunchState == ApplianceLaunchState.Idle,
        connectionState = connectionUiState,
        currentChannelReadiness = currentChannelReadiness,
        loadingTiming = startupLoadingTiming,
    )
    val coldStartVisible = applianceLaunchState == ApplianceLaunchState.Idle &&
        coldStart.presentation != MainStartupPresentation.Inactive
    val effectiveStartupPresentation = if (coldStartVisible) coldStart.presentation else startupPresentation
    val applianceLaunchActive =
        effectiveStartupPresentation != MainStartupPresentation.Inactive || revealingTarget != null
    val navigationAllowed = when (val launchState = applianceLaunchState) {
        ApplianceLaunchState.Idle ->
            effectiveStartupPresentation == MainStartupPresentation.Inactive &&
                navigationStartDestination != null &&
                backStack.isNotEmpty()
        is ApplianceLaunchState.Pending -> false
        is ApplianceLaunchState.Entering -> {
            val exactStart = LivePlayerKey(
                channelId = launchState.target.channelId.value,
                channelName = launchState.target.channelName,
            )
            backStack.isNotEmpty() && enteringNavigationAllowed(
                hasBackStackEntry = backStack.isNotEmpty(),
                navigationStartDestination = navigationStartDestination,
                exactStartDestination = exactStart,
                matchingVisiblePlayer = matchingEnteringPlayerVisible,
            )
        }
    }
    val selectRoot = remember(backStack) {
        { destination: AppNavKey ->
            navigationStartDestination = destination
            if (backStack.isNotEmpty()) {
                backStack.replaceRoot(destination)
            }
        }
    }

    // One-shot warm-player return: armed when a service/recording becomes active
    // or the user navigates deliberately while playback remains warm; consumed
    // before returning to the player so root Back cannot loop. Player Back alone
    // does not re-arm.
    val warmReturn = playbackOrchestrator.warmReturn
    LaunchedEffect(activeChannelId, activeRecordingId) {
        playbackOrchestrator.activePlaybackChanged(activeChannelId, activeRecordingId)
    }

    LaunchedEffect(playbackState, activeRecordingId, currentDestination) {
        when (
            recordingFinishedAction(
                recordingFinished = playbackState is AppPlaybackState.Finished,
                activeRecordingId = activeRecordingId,
                recordingPlayerVisible = currentDestination is RecordingPlayerKey,
            )
        ) {
            RecordingFinishedAction.NONE -> Unit
            RecordingFinishedAction.STOP -> playbackRuntime.stop()
            RecordingFinishedAction.STOP_AND_CLOSE_PLAYER -> {
                playbackRuntime.stop()
                backStack.popNavigation()
            }
        }
    }

    SideEffect { onPlayerVisibilityChanged(isPlayer) }

    LaunchedEffect(startupReveal, profileGeneration, currentDestination, applianceLaunchState) {
        if (startupReveal != null && revealingTarget == null ||
            revealingTarget?.matchesPlayerKey(currentDestination) == false ||
            applianceLaunchState is ApplianceLaunchState.Pending ||
            (applianceLaunchState is ApplianceLaunchState.Entering && enteringLaunchTarget != revealingTarget)
        ) startupReveal = null
    }
    LaunchedEffect(applianceLaunchState, profileGeneration) {
        if (applianceLaunchState != ApplianceLaunchState.Idle && enteringProfileGeneration != profileGeneration) {
            applianceLaunchRequests.cancel(applianceLaunchState)
        }
    }

    val enterDirective = startupPresentation as? MainStartupPresentation.Enter
    LaunchedEffect(applianceLaunchState, navigationStartDestination) {
        if (applianceLaunchState == ApplianceLaunchState.Idle && navigationStartDestination == null) {
            selectRoot(ChannelsKey)
        }
    }
    LaunchedEffect(enterDirective, currentChannelReadiness) {
        val directive = enterDirective ?: return@LaunchedEffect
        val readiness = currentChannelReadiness as? CurrentChannelReadiness.Ready
            ?: return@LaunchedEffect
        val persistedId = lastPlayedChannelStore.channelId.first()
        val target = applianceLaunchRequests.resolve(
            request = directive.request,
            readiness = readiness,
            persistedId = persistedId,
        ) ?: return@LaunchedEffect
        val targetDestination = LivePlayerKey(
            channelId = target.channelId.value,
            channelName = target.channelName,
        )
        if (backStack.isEmpty() && navigationStartDestination == null) {
            // Fresh autoplay starts at the exact player without composing browse content.
            navigationStartDestination = targetDestination
        } else if (!target.matchesPlayerKey(currentDestination)) {
            // Startup entry owns a root replacement. Clearing the prior graph
            // prevents browse destinations from composing during the handoff.
            backStack.replaceRoot(targetDestination)
        }
    }

    val startupBack = remember(applianceLaunchState, revealingTarget, selectRoot) {
        val expectedState = applianceLaunchState
        {
            if (revealingTarget != null) {
                startupReveal = null
                selectRoot(ChannelsKey)
            } else performMainStartupBack(
                requests = applianceLaunchRequests,
                expectedState = expectedState,
                selectRoot = selectRoot,
            )
        }
    }
    val handleRootBack: () -> Unit = rootBack@{
        if (applianceLaunchActive) {
            startupBack()
            return@rootBack
        }

        when (
            rootBackAction(
                isStartDestination = currentDestination == ChannelsKey,
                warmReturn = warmReturn,
            )
        ) {
            BackAction.FINISH_ACTIVITY -> onRequestExit()
            BackAction.POP_NAVIGATION -> {
                if (!backStack.popNavigation()) onRequestExit()
            }
            BackAction.RETURN_TO_PARENT -> Unit
            BackAction.RETURN_TO_PLAYER -> {
                when (
                    val playerTarget = playbackOrchestrator.consumeWarmPlayerTarget(
                        activeChannelId = activeChannelId,
                        activeRecordingId = activeRecordingId,
                        currentChannelReadiness = currentChannelReadiness,
                        noteViewingIntent = { playbackRuntime.notePlaybackIntent() },
                    )
                ) {
                    is PlayerRouteTarget.Live -> {
                        backStack.pushTransient(
                            LivePlayerKey(
                                channelId = playerTarget.channelId.value,
                                channelName = playerTarget.channelName,
                            ),
                        )
                    }
                    is PlayerRouteTarget.Recording -> {
                        backStack.pushTransient(
                            RecordingPlayerKey(recordingId = playerTarget.recordingId.value),
                        )
                    }
                    null -> Unit
                }
            }
        }
    }
    val browseBackHandler = remember { mutableStateOf(handleRootBack) }
    // NavDisplay does not intercept Back for a singleton root. The browse shell
    // still owns its focus layers before MainActivity's exit fallback may run.
    BackHandler(enabled = navigationAllowed && !applianceLaunchActive) {
        if (showRail) browseBackHandler.value() else handleRootBack()
    }
    // Every live start from browse: before the session is playable the player opens at once and
    // waits for it, then its entry start tunes this channel.
    val requestLivePlayer: (ChannelId, String) -> Unit = start@{ channelId, name ->
        // One request at a time: a repeated activation is consumed before it notes another intent.
        if (playerEntry.pending) return@start
        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        val selection = currentLivePlaybackSelection(tvheadendSession.observation.value, channelId)
        val viewingIntent = playbackRuntime.notePlaybackIntent()
        playerEntry.request(playbackSelectionScope) {
            val target = playbackOrchestrator.requestLivePlayer(
                activeChannelId = activeChannelId,
                activeRecordingId = activeRecordingId,
                requestedChannelId = channelId,
                requestedChannelName = name,
                viewingIntent = viewingIntent,
                startPlayback = { intent -> selection?.let { playbackRuntime.playLive(it, intent) } },
            ) ?: return@request
            // A player already open for another channel is replaced.
            if (backStack.lastOrNull() is LivePlayerKey) backStack.popNavigation()
            backStack.pushTransient(
                LivePlayerKey(target.channelId.value, target.channelName),
            )
        }
    }
    val content: @Composable (PaddingValues, Boolean, Boolean, List<AppNavKey>) -> Unit = {
            contentPadding, drawerActive, contentAllowed, shellBackStack ->
            ProvideWatchingProgress(playbackRuntime) {
            Box(Modifier.fillMaxSize().profileRouteDraw(currentDestination?.destination?.name ?: "NONE", drawerActive)) {
                // The full-screen shell only ever shows players: its edges are player transitions.
                val playerTransition = shellBackStack.lastOrNull().let { it is LivePlayerKey || it is RecordingPlayerKey }
                NavDisplay(
                    backStack = shellBackStack,
                    sceneStrategies = listOf(
                        rememberSidebarGuideSceneStrategy(drawerActive, currentDestination),
                    ),
                    onBack = {
                        if (showRail) browseBackHandler.value() else handleRootBack()
                    },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    transitionSpec = { appRouteContentTransform(playerTransition) },
                    popTransitionSpec = { appRouteContentTransform(playerTransition) },
                    predictivePopTransitionSpec = { appRouteContentTransform(playerTransition) },
                    entryProvider = entryProvider {
                    entry<ChannelsKey>(
                        metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.CHANNELS),
                    ) {
                        ChannelsRouteContent(
                            onShowGuide = { channelId, programme ->
                                guideChannelRequest = PlayerReturnFocus(channelId, channelId,
                                    programme?.id, programme?.start?.epochSeconds)
                                navigateTopLevel(GuideKey)
                            },
                            channelsVm = channelsVm,
                            contentAllowed = contentAllowed,
                            contentPadding = contentPadding,
                            initialFocusEnabled = !drawerActive && currentDestination == ChannelsKey,
                            playingChannelId = activeChannelId,
                            playerReturn = playerReturnFor(ChannelsKey),
                            view = uiSettings.channelsView,
                            connectionUiState = connectionUiState,
                            connectionFailure = browseConnectionFailure,
                            onRetryConnection = retryBrowseConnection,
                            onOpenConnectionSettings = {
                                navigateTopLevel(SettingsKey(SettingsSection.CONNECTION))
                            },
                            onPlay = requestLivePlayer,
                            onPlayRecording = { selection ->
                                playerEntry.request(playbackSelectionScope) {
                                    val target = playbackOrchestrator.requestRecordingPlayer(
                                        activeChannelId = activeChannelId,
                                        activeRecordingId = activeRecordingId,
                                        requestedRecordingId = selection.recordingId,
                                        startPlayback = { playbackRuntime.playRecording(selection, RecordingPlaybackStart.START_OVER) },
                                    ) ?: return@request
                                    backStack.pushTransient(recordingPlayerKey(target.recordingId, RecordingPlaybackStart.START_OVER))
                                }
                            },
                        )
                    }

                    entry<GuideKey>(
                        metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.GUIDE),
                    ) {
                        GuideRouteContent(
                            guideActive = currentDestination == GuideKey,
                            channelsVm = channelsVm,
                            contentAllowed = contentAllowed,
                            contentPadding = contentPadding,
                            initialFocusEnabled = !drawerActive && currentDestination == GuideKey,
                            playerReturn = guideChannelRequest ?: playerReturnFor(GuideKey),
                            browseReturn = recordingPlayerReturnToGuide,
                            view = uiSettings.guideView,
                            connectionUiState = connectionUiState,
                            connectionFailure = browseConnectionFailure,
                            onRetry = retryBrowseConnection,
                            onOpenConnectionSettings = {
                                navigateTopLevel(SettingsKey(SettingsSection.CONNECTION))
                            },
                            onPlayRecording = { playbackSelection ->
                                val recordingId = playbackSelection.recordingId
                                playerEntry.request(playbackSelectionScope) {
                                    val target = playbackOrchestrator.requestRecordingPlayer(
                                        activeChannelId = activeChannelId,
                                        activeRecordingId = activeRecordingId,
                                        requestedRecordingId = recordingId,
                                        startPlayback = {
                                            playbackRuntime.playRecording(
                                                playbackSelection,
                                                RecordingPlaybackStart.START_OVER,
                                            )
                                        },
                                    ) ?: return@request
                                    backStack.pushTransient(
                                        recordingPlayerKey(
                                            recordingId = target.recordingId,
                                            start = RecordingPlaybackStart.START_OVER,
                                        ),
                                    )
                                }
                            },
                            onPlay = requestLivePlayer,
                        )
                    }

                    entry<RecordingsKey>(
                        metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.RECORDINGS),
                    ) {
                        RecordingsRouteContent(
                            onShowGuide = { channelId, programme ->
                                guideChannelRequest = PlayerReturnFocus(channelId, channelId,
                                    programme?.id, programme?.start?.epochSeconds)
                                navigateTopLevel(GuideKey)
                            },
                            playingRecordingId = warmRecordingId,
                            onReturnToRecording = { id ->
                                val currentTarget = playbackRuntime.activeTarget.value
                                val currentSelection = playbackRuntime.recordingSelection.value
                                val stillWarm = warmRecordingId(currentTarget, currentSelection,
                                    tvheadendSession.observation.value.currentSession) == id
                                if (stillWarm) {
                                    playbackRuntime.notePlaybackIntent()
                                    playerEntry.request(playbackSelectionScope) {
                                        val target = playbackOrchestrator.requestRecordingPlayer(
                                            activeChannelId = null, activeRecordingId = id, requestedRecordingId = id,
                                            restartPlayback = false,
                                            startPlayback = {
                                                playbackRuntime.playRecording(requireNotNull(currentSelection), RecordingPlaybackStart.RESUME)
                                            },
                                        ) ?: return@request
                                        backStack.pushTransient(recordingPlayerKey(target.recordingId, RecordingPlaybackStart.RESUME))
                                    }
                                }
                                stillWarm
                            },
                            onWatchChannel = requestLivePlayer,
                            contentAllowed = contentAllowed,
                            contentPadding = contentPadding,
                            initialFocusEnabled = !drawerActive && currentDestination == RecordingsKey,
                            backEnabled = !applianceLaunchActive,
                            connectionUiState = connectionUiState,
                            connectionFailure = browseConnectionFailure,
                            onRetry = retryBrowseConnection,
                            onOpenConnectionSettings = {
                                navigateTopLevel(SettingsKey(SettingsSection.CONNECTION))
                            },
                            onPlayRecording = { playbackSelection, intent ->
                                val recordingId = playbackSelection.recordingId
                                playerEntry.request(playbackSelectionScope) {
                                    val target = playbackOrchestrator.requestRecordingPlayer(
                                        activeChannelId = activeChannelId,
                                        activeRecordingId = activeRecordingId,
                                        requestedRecordingId = recordingId,
                                        startPlayback = {
                                            playbackRuntime.playRecording(
                                                playbackSelection,
                                                intent,
                                            )
                                        },
                                    ) ?: return@request
                                    backStack.pushTransient(
                                        recordingPlayerKey(target.recordingId, intent),
                                    )
                                }
                            },
                            state = recordingsScreenState,
                        )
                    }

                    entry<SettingsKey>(
                        metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.SETTINGS),
                    ) { destination ->
                        SettingsRouteContent(
                            channelsVm = channelsVm,
                            connectionPresentation = connectionPresentation,
                            onRetry = { appVm.reconnectNow(allowAutomaticBackoff = true) },
                            contentAllowed = contentAllowed,
                            section = destination.section,
                            isCurrent = currentDestination == destination,
                            initialFocusEnabled = !drawerActive && currentDestination == destination,
                            contentPadding = contentPadding,
                            backEnabled = !applianceLaunchActive,
                        )
                    }

                    entry<LivePlayerKey> { destination ->
                        LivePlayerRouteContent(
                            onPlayRecording = { selection ->
                                playerEntry.request(playbackSelectionScope) {
                                    val target = playbackOrchestrator.requestRecordingPlayer(
                                        activeChannelId, activeRecordingId, selection.recordingId,
                                        startPlayback = { playbackRuntime.playRecording(selection, RecordingPlaybackStart.START_OVER) },
                                    ) ?: return@request
                                    backStack.pushTransient(recordingPlayerKey(target.recordingId, RecordingPlaybackStart.START_OVER))
                                }
                            },
                            onShowGuide = { channelId, programme ->
                                guideChannelRequest = PlayerReturnFocus(channelId, channelId,
                                    programme?.id, programme?.start?.epochSeconds)
                                navigateTopLevel(GuideKey)
                            },
                            channelsVm = channelsVm,
                            contentAllowed = contentAllowed,
                            channelId = ChannelId(destination.channelId),
                            channelName = destination.channelName,
                            onReconnect = { appVm.reconnectNow(allowAutomaticBackoff = true) },
                            onOpenConnectionSettings = {
                                navigateTopLevel(SettingsKey(SettingsSection.CONNECTION))
                            },
                            connectionUiState = connectionUiState,
                            startupTarget = (enteringLaunchTarget ?: revealingTarget)
                                ?.takeIf { it.matchesPlayerKey(destination) },
                            onStartupOutcome = { target, outcome ->
                                if (profileOwner.configurationGeneration.value != enteringProfileGeneration) {
                                    applianceLaunchRequests.cancel(ApplianceLaunchState.Entering(target))
                                    if (startupReveal?.target == target) startupReveal = null
                                    false
                                } else if (outcome == MainStartupPlaybackOutcome.RECOVERY) {
                                    val accepted = applianceLaunchRequests.cancel(ApplianceLaunchState.Entering(target)) ||
                                        startupReveal?.target == target
                                    if (startupReveal?.target == target) startupReveal = null
                                    accepted
                                } else {
                                    val completed = completeEnteringPlayerVisibility(
                                        requests = applianceLaunchRequests,
                                        target = target,
                                        channelId = ChannelId(destination.channelId),
                                        channelName = destination.channelName,
                                        outcome = outcome,
                                    )
                                    if (completed) {
                                        startupReveal = if (outcome == MainStartupPlaybackOutcome.PRESENTED) {
                                            MainStartupReveal(target, enteringProfileGeneration)
                                        } else null
                                    }
                                    completed
                                }
                            },
                            onClose = {
                                enteringLaunchTarget?.let {
                                    applianceLaunchRequests.cancel(ApplianceLaunchState.Entering(it))
                                }
                                startupReveal = null
                                val originId = ChannelId(destination.channelId)
                                val playingId = (playbackRuntime.activeTarget.value as? AppPlaybackTarget.Live)
                                    ?.channelId ?: originId
                                closeNormalLivePlayer(
                                    popBackStack = backStack::popNavigation,
                                    selectRoot = selectRoot,
                                )
                                livePlayerReturn = backStack.lastOrNull()?.let {
                                    it to PlayerReturnFocus(playingId, originId)
                                }
                            },
                        )
                    }

                    entry<RecordingPlayerKey> { destination ->
                        RecordingPlayerRouteContent(
                            onShowGuide = { channelId, programme ->
                                guideChannelRequest = PlayerReturnFocus(channelId, channelId,
                                    programme?.id, programme?.start?.epochSeconds)
                                navigateTopLevel(GuideKey)
                            },
                            contentAllowed = contentAllowed,
                            recordingId = DvrEntryId(destination.recordingId),
                            playbackStart = destination.start.toPlaybackStart(),
                            connectionState = connectionState,
                            onReconnect = appVm::reconnectNow,
                            onClose = {
                                backStack.popNavigation()
                                recordingPlayerReturnToGuide = backStack.lastOrNull() == GuideKey
                            },
                            onOpenConnectionSettings = {
                                navigateTopLevel(SettingsKey(SettingsSection.CONNECTION))
                            },
                        )
                    }
                },
                )

            }
            }
    }

    val startupAction = remember(applianceLaunchState, selectRoot, appVm) {
        val expectedState = applianceLaunchState
        { action: AppErrorAction ->
            performMainStartupAction(
                action = action,
                onRetry = appVm::reconnectNow,
                onConnectionSettings = {
                    cancelStartupAndSelectRoot(
                        requests = applianceLaunchRequests,
                        expectedState = expectedState,
                        destination = SettingsKey(SettingsSection.CONNECTION),
                        selectRoot = selectRoot,
                    )
                },
            )
        }
    }
    val startupRequestId = when (val launch = applianceLaunchState) {
        ApplianceLaunchState.Idle -> revealingTarget?.request?.id
        is ApplianceLaunchState.Pending -> launch.request.id
        is ApplianceLaunchState.Entering -> launch.target.request.id
    }
    // Capture the existing service at request entry, not the autoplay it subsequently starts.
    val priorLiveChannelValue by rememberSaveable(startupRequestId, profileGeneration) {
        // Save the nullable value inside state so a saved null does not re-run initialization.
        mutableStateOf(activeChannelId?.value.takeIf { startupRequestId != null })
    }
    val priorLiveChannelId = priorLiveChannelValue?.let(::ChannelId)
    val returningToPlayback = mainStartupReturningToPlayback(
        priorLiveChannelId, enteringLaunchTarget ?: revealingTarget,
    )
    // Logos and programme pictures load with the latest current session; the provider recomposes
    // nothing for guide or recording updates.
    ProvideAppArtwork(tvheadendSession) {
    MainStartupComposition(
        state = MainStartupCompositionState(
            presentation = effectiveStartupPresentation,
            navigationStartDestination = navigationStartDestination,
            navigationAllowed = navigationAllowed,
            contentAllowed =
                applianceLaunchState == ApplianceLaunchState.Idle &&
                    effectiveStartupPresentation == MainStartupPresentation.Inactive &&
                    revealingTarget == null,
            requestId = startupRequestId,
            revealRequestId = revealingTarget?.request?.id,
            returningToPlayback = returningToPlayback,
            hideBranding = priorLiveChannelId != null,
        ),
        loadingTiming = startupLoadingTiming,
        onBack = if (coldStartVisible) onRequestExit else startupBack,
        onAction = if (coldStartVisible) { action ->
            performMainStartupAction(
                action = action,
                onRetry = {
                    coldStart.retry()
                    appVm.reconnectNow(allowAutomaticBackoff = true)
                },
                onConnectionSettings = {
                    coldStart.settle()
                    selectRoot(SettingsKey(SettingsSection.CONNECTION))
                },
            )
        } else startupAction,
        registerActivityKeyContract = registerActivityKeyContract,
        onRevealed = { startupReveal = null },
        showWarmPlaybackScrim = shouldShowWarmPlaybackScrim(
            hasActivePlayback = playbackState !is AppPlaybackState.Idle,
            isPlayerRoute = isPlayer,
        ),
        notices = { at.bernhardberger.tvhplayer.ui.notifications.AppShellNoticeHost() },
        persistentSurface = {
            if (shouldMountPersistentPlayerSurface(
                    hasActivePlayback = playbackState !is AppPlaybackState.Idle,
                    isPlayerRoute = isPlayer,
                )
            ) {
                val requestedFrameRate by playbackRuntime.frameRate.requested.collectAsStateWithLifecycle()
                PlayerVideoSurface(
                    player = playbackRuntime.player,
                    aspectRatio = playerSettings.aspectRatio,
                    videoVisible = videoPresentation.visible,
                    debugVideoBackdropVisible = debugVideoBackdropVisible,
                    modifier = Modifier.fillMaxSize(),
                    // The startup screen and its reveal own an autoplay's first picture.
                    coverFadeMillis = rememberVideoCoverFadeMillis(
                        playerShown = isPlayer,
                        videoVisible = videoPresentation.visible,
                        instant = applianceLaunchActive || !ValueAnimator.areAnimatorsEnabled(),
                    ),
                    // Browse screens over warm playback leave the TV in its own mode.
                    frameRate = if (isPlayer) requestedFrameRate else SurfaceFrameRateRequest(),
                )
            }
        },
        navigation = { _, contentAllowed ->
            MainNavigationShell(
                showRail = showRail,
                // The startup screen and its reveal own the way into an autoplay.
                motionEnabled = !applianceLaunchActive && ValueAnimator.areAnimatorsEnabled(),
                rail = {
                    val railBackStack = rememberShellBackStack(backStack, playerShell = false)
                    PlayerEntryOverlays(playerEntry,
                        playerShown = { !shouldShowMainNavigationRail(backStack.lastOrNull(), navigationStartDestination) }) {
                    SideRail(
                        // A fading rail keeps marking the page it fades with.
                        currentRoute = (railBackStack.lastOrNull() ?: currentDestination)?.destination,
                        connectionDegraded = connectionPresentation.degraded,
                        rootRoute = AppDestination.CHANNELS,
                        showEpgMenu = uiSettings.showEpgMenu,
                        availableDestinations = buildSet {
                            add(AppDestination.CHANNELS)
                            add(AppDestination.GUIDE)
                            add(AppDestination.RECORDINGS)
                            add(AppDestination.SETTINGS)
                        },
                        rootBackPriority = applianceLaunchActive,
                        onRootBack = handleRootBack,
                        onBackHandlerChanged = { browseBackHandler.value = it },
                        onNavigate = { destination ->
                            if (currentDestination?.destination == destination) {
                                focusManager.moveFocus(FocusDirection.Right)
                            } else {
                                // Deliberate rail navigation re-arms one warm return while
                                // playback remains active. Returning from the player via
                                // Back does not go through this path and must not re-arm.
                                playbackOrchestrator.browseNavigationSelected(
                                    activeChannelId = activeChannelId,
                                    activeRecordingId = activeRecordingId,
                                )
                                navigateTopLevel(
                                    destination.toTopLevelKey(
                                        settingsSection = backStack.lastSettingsSection(),
                                    ),
                                )
                            }
                        },
                        content = { contentPadding, drawerActive ->
                            content(contentPadding, drawerActive, contentAllowed, railBackStack)
                        },
                    )
                    }
                },
                fullScreen = {
                    content(TvFullScreenPadding, false, contentAllowed, rememberShellBackStack(backStack, playerShell = true))
                },
            )
        },
    )
    }
}

private fun ApplianceLaunchTarget.matchesPlayerKey(destination: AppNavKey?): Boolean {
    val player = destination as? LivePlayerKey ?: return false
    return matchesPlayer(
        channelId = ChannelId(player.channelId),
        channelName = player.channelName,
    )
}

private fun AppDestination.toTopLevelKey(
    settingsSection: SettingsSection?,
): AppNavKey = when (this) {
    AppDestination.CHANNELS -> ChannelsKey
    AppDestination.GUIDE -> GuideKey
    AppDestination.RECORDINGS -> RecordingsKey
    AppDestination.SETTINGS -> SettingsKey(settingsSection ?: SettingsSection.GENERAL)
    AppDestination.LIVE_PLAYER,
    AppDestination.RECORDING_PLAYER -> error("Transient destination is not top-level")
}

internal fun recordingPlayerKey(
    recordingId: DvrEntryId,
    start: RecordingPlaybackStart,
): RecordingPlayerKey = RecordingPlayerKey(
    recordingId = recordingId.value,
    start = when (start) {
        RecordingPlaybackStart.RESUME -> RecordingStartMode.RESUME
        RecordingPlaybackStart.START_OVER -> RecordingStartMode.START_OVER
    },
)

private fun RecordingStartMode.toPlaybackStart(): RecordingPlaybackStart = when (this) {
    RecordingStartMode.RESUME -> RecordingPlaybackStart.RESUME
    RecordingStartMode.START_OVER -> RecordingPlaybackStart.START_OVER
}
