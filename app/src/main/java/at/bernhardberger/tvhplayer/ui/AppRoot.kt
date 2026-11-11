package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvhplayer.ui.components.ProvideWatchingProgress
import at.bernhardberger.tvhplayer.ui.components.railNowPlaying

import at.bernhardberger.tvhplayer.profiling.profileRouteDraw

import androidx.activity.compose.BackHandler
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvhplayer.BuildConfig
import kotlinx.coroutines.Dispatchers
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import at.bernhardberger.tvhplayer.core.WarmReturnOpportunity
import at.bernhardberger.tvhplayer.core.ApplianceEntryDestination
import at.bernhardberger.tvhplayer.core.ApplianceEntryPolicy
import at.bernhardberger.tvhplayer.core.ApplianceGuideEntryAction
import at.bernhardberger.tvhplayer.core.BackAction
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupLoadingTiming
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.MainStartupPlaybackOutcome
import at.bernhardberger.tvhplayer.core.mainStartupReturningToPlayback
import at.bernhardberger.tvhplayer.core.MainStartupReveal
import at.bernhardberger.tvhplayer.core.channelsAvailable
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.core.mainStartupPresentation
import at.bernhardberger.tvhplayer.core.rootBackAction
import at.bernhardberger.tvhplayer.core.serverSettingsForRuntime
import at.bernhardberger.tvhplayer.core.showGlobalNavigationRail
import at.bernhardberger.tvhplayer.core.RecordingFinishedAction
import at.bernhardberger.tvhplayer.core.recordingFinishedAction
import at.bernhardberger.tvhplayer.core.shouldMountPersistentPlayerSurface
import at.bernhardberger.tvhplayer.core.shouldShowWarmPlaybackScrim
import at.bernhardberger.tvhplayer.core.shouldStopPlaybackWhileBrowsing
import at.bernhardberger.tvhplayer.core.shouldCoverWarmPlayback
import at.bernhardberger.tvhplayer.settings.WarmPlaybackMode
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateRequest
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.currentLivePlaybackSelection
import at.bernhardberger.tvhplayer.ui.startup.rememberColdStart
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
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
import at.bernhardberger.tvhplayer.ui.player.ChannelZapPacer
import at.bernhardberger.tvhplayer.ui.player.LocalSessionChannelStepAllowed
import at.bernhardberger.tvhplayer.ui.player.scheduleChannelZap
import at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame
import at.bernhardberger.tvhplayer.ui.player.PlayerVideoSurface
import at.bernhardberger.tvhplayer.ui.player.leaving
import at.bernhardberger.tvhplayer.ui.player.rememberTargetAspect
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

@Composable
internal fun ApplyUiPresentationSettings(settings: UiSettings) {
    LaunchedEffect(settings.clockFormat, settings.showChannelNumbers) {
        AppClock.format = settings.clockFormat
        at.bernhardberger.tvhplayer.ui.components.AppChannelNumbers.shown = settings.showChannelNumbers
    }
}

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
    coverBrowse: Boolean = false,
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
            PlayerMotionFrame(leaving = exiting, restoreFocusOnReturn = true) {
                if (railShown) {
                    // The cover belongs to browse, including its outgoing overlays and fade.
                    Box(Modifier.fillMaxSize()) {
                        if (coverBrowse) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        rail()
                    }
                } else fullScreen()
            }
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

/** True only when the live or recording picture is shown behind browse under [WarmPlaybackScrim]. */
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
    if (!popBackStack()) selectRoot(HomeKey)
}

internal fun performMainStartupBack(
    requests: ApplianceLaunchRequests,
    expectedState: ApplianceLaunchState,
    selectRoot: (AppNavKey) -> Unit,
) {
    cancelStartupAndSelectRoot(
        requests = requests,
        expectedState = expectedState,
        destination = HomeKey,
        selectRoot = selectRoot,
    )
}

internal enum class DeferredResolvingBackAction {
    NONE,
    CANCEL_TO_HOME,
}

internal fun deferredResolvingBackAction(
    cancellationRequested: Boolean,
): DeferredResolvingBackAction = when {
    !cancellationRequested -> DeferredResolvingBackAction.NONE
    else -> DeferredResolvingBackAction.CANCEL_TO_HOME
}

internal fun applyDeferredResolvingBack(
    action: DeferredResolvingBackAction,
    requests: ApplianceLaunchRequests,
    expectedState: ApplianceLaunchState,
    selectRoot: (AppNavKey) -> Unit,
): Boolean = when (action) {
    DeferredResolvingBackAction.NONE -> false
    DeferredResolvingBackAction.CANCEL_TO_HOME -> cancelStartupAndSelectRoot(
        requests = requests,
        expectedState = expectedState,
        destination = HomeKey,
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
    /**
     * At least one server is stored. First-run setup is only for a TV with none; with servers
     * stored but none usable, the app opens Settings > Connection. Judged from the stored list,
     * not the active profile, so removing the server in use never passes through first run.
     */
    serversStored: Boolean = false,
) {
    val applianceLaunchState by applianceLaunchRequests.state.collectAsStateWithLifecycle()
    val guideRequest = (applianceLaunchState as? ApplianceLaunchState.Pending)
        ?.takeIf { it.request.destination != ApplianceEntryDestination.LIVE }
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
    val noServerInUse = serversStored && serverSettings.host.isBlank()
    if (serverSettings.host.isBlank()) {
        LaunchedEffect(guideRequest) {
            guideRequest?.let { applianceLaunchRequests.cancel(it) }
        }
    }
    if (serverSettings.host.isBlank() && !serversStored) {
        MainStartupComposition(
            state = MainStartupCompositionState(
                presentation = MainStartupPresentation.Inactive,
                navigationStartDestination = HomeKey,
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
            if (noServerInUse) SettingsKey(SettingsSection.CONNECTION)
            else when (val launchState = applianceLaunchState) {
                ApplianceLaunchState.Idle -> HomeKey
                is ApplianceLaunchState.Pending -> if (
                    deferredBackAction ==
                    DeferredResolvingBackAction.CANCEL_TO_HOME
                ) {
                    HomeKey
                } else {
                    null
                }
                is ApplianceLaunchState.Entering -> if (
                    deferredBackAction ==
                    DeferredResolvingBackAction.CANCEL_TO_HOME
                ) {
                    HomeKey
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
        } else if (destination == null && backStack.isNotEmpty()) {
            navigationStartDestination = backStack.first()
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
    var browseStop by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val playerEntry = remember { PlayerEntryHandoff() }
    val browseSteps = remember { BrowseChannelStepHandoff() }
    val playbackOrchestrator = remember { AppRootPlaybackOrchestrator() }
    val playerSettingsStore: PlayerSettingsStore = koinInject()
    val warmPlaybackMode by remember(playerSettingsStore) {
        playerSettingsStore.playerSettings.map { it.warmPlaybackMode }.distinctUntilChanged()
    }.collectAsStateWithLifecycle<WarmPlaybackMode?>(initialValue = null)
    val playbackState by playbackRuntime.state.collectAsStateWithLifecycle()
    val livePause by playbackRuntime.livePause.collectAsStateWithLifecycle()
    val activeTarget by playbackRuntime.activeTarget.collectAsStateWithLifecycle()
    val videoPresentation by playbackRuntime.videoPresentation.collectAsStateWithLifecycle()
    val activeChannelId = (activeTarget as? AppPlaybackTarget.Live)?.channelId
    // A failed or ended target stays active for Retry; browse shows and returns to it only while it plays.
    val browsePlaying = playingTarget(activeTarget, playbackState)
    val playingChannelId = (browsePlaying as? AppPlaybackTarget.Live)?.channelId
    val playingRecordingId = (browsePlaying as? AppPlaybackTarget.Recording)?.recordingId
    val activeRecordingId = (activeTarget as? AppPlaybackTarget.Recording)?.recordingId
    val recordingSelection by playbackRuntime.recordingSelection.collectAsStateWithLifecycle()
    val playbackObservation by tvheadendSession.observation.collectAsStateWithLifecycle()
    val warmRecordingId = warmRecordingId(browsePlaying, recordingSelection, playbackObservation.currentSession)
    val playingNow = { playingIds(playbackRuntime.activeTarget.value, playbackRuntime.state.value) }
    // Created once here: the surface and both player screens read this one value.
    val targetAspect = rememberTargetAspect(activeTarget, playerSettingsStore) {
        playbackObservation.dvrEntry(it)?.channelId
    }
    val uiSettingsStore: UiSettingsStore = koinInject()
    val context = LocalContext.current
    val uiSettings by uiSettingsStore.settings.collectAsStateWithLifecycle(initialValue = UiSettings())
    var lastUpdateTime by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(uiSettingsStore) {
        val packageInfo = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }
        uiSettingsStore.initializeWhatsNew(BuildConfig.VERSION_CODE, packageInfo.firstInstallTime, packageInfo.lastUpdateTime)
        lastUpdateTime = packageInfo.lastUpdateTime
    }
    ApplyUiPresentationSettings(uiSettings)
    val profileOwner: AppProfileOwner = koinInject()
    val profileGeneration by profileOwner.configurationGeneration.collectAsStateWithLifecycle()
    val browseFailure = at.bernhardberger.tvhplayer.ui.components.rememberBrowseConnectionFailure(
        connectionUiState, profileGeneration, appVm::reconnectNow)
    val browseConnectionFailure = browseFailure.failure
    val retryBrowseConnection = browseFailure.retry
    val currentChannelReadiness = connectionPresentation.currentChannelReadiness
    val landingCatalogAvailable = currentChannelReadiness.channelsAvailable() || currentChannelReadiness is CurrentChannelReadiness.Ready
    var startupReveal by remember { mutableStateOf<MainStartupReveal?>(null) }
    val revealingTarget = startupReveal?.targetFor(profileGeneration)
    val startupPresentation = mainStartupPresentation(
        startupState = startupState,
        // A warm Guide entry does not obscure or steal focus from the current screen.
        launchState = if (guideRequest != null && backStack.isNotEmpty()) ApplianceLaunchState.Idle else applianceLaunchState,
        connectionState = connectionUiState,
        currentChannelReadiness = currentChannelReadiness,
    )

    val currentDestination = backStack.lastOrNull()
    // Live player Back targets the browse destination it returned to, once.
    var livePlayerReturn by remember { mutableStateOf<Pair<AppNavKey, PlayerReturnFocus>?>(null) }
    var recordingPlayerReturnToGuide by remember { mutableStateOf(false) }
    var guideChannelRequest by remember { mutableStateOf<PlayerReturnFocus?>(null) }
    var guideGenreRequest by remember { mutableStateOf<at.bernhardberger.tvhplayer.core.ProgrammeCategory?>(null) }
    var guideGenre by rememberSaveable { mutableStateOf(at.bernhardberger.tvhplayer.core.ProgrammeCategory.ALL) }
    val homeFocus = remember(profileGeneration) { mutableStateOf<at.bernhardberger.tvhplayer.core.HomeFocusKey?>(null) }
    LaunchedEffect(currentDestination, GuideKey in backStack) {
        if (livePlayerReturn?.first != currentDestination) livePlayerReturn = null
        if (currentDestination != GuideKey) recordingPlayerReturnToGuide = false
        if (currentDestination != GuideKey) guideChannelRequest = null
        if (currentDestination != GuideKey) guideGenreRequest = null
        if (GuideKey !in backStack) {
            guideGenre = at.bernhardberger.tvhplayer.core.ProgrammeCategory.ALL
        }
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
    val browseChannelStepEnabled = !isPlayer &&
        (warmPlaybackMode == WarmPlaybackMode.ON || warmPlaybackMode == WarmPlaybackMode.AUDIO_ONLY)
    SideEffect { browseSteps.playbackChanged(activeChannelId) }
    LaunchedEffect(playbackRuntime, browseSteps) {
        playbackRuntime.sessionStops.collect { browseSteps.withdrawPending() }
    }
    DisposableEffect(playbackRuntime, browseChannelStepEnabled) {
        val pacer = ChannelZapPacer()
        var pendingZap: kotlinx.coroutines.Job? = null
        var pendingIntent: Long? = null
        val unregister = if (browseChannelStepEnabled) {
            playbackRuntime.registerSessionChannelStep { direction ->
                if (playerEntry.pending) return@registerSessionChannelStep
                browseSteps.playbackChanged((playbackRuntime.activeTarget.value as? AppPlaybackTarget.Live)?.channelId)
                val channelCursor = browseSteps.channelCursor
                    ?: return@registerSessionChannelStep
                val channels = channelsVm.scope.value.scope.visibleChannels
                val adjacentId = ChannelNavigation.adjacentId(
                    orderedIds = channels.map { it.id },
                    currentId = channelCursor,
                    direction = direction,
                ) ?: return@registerSessionChannelStep
                @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
                val selection = currentLivePlaybackSelection(tvheadendSession.observation.value, adjacentId)
                    ?: return@registerSessionChannelStep
                val intent = playbackRuntime.notePlaybackIntent()
                val step = browseSteps.queue(adjacentId) {
                    pendingZap?.cancel()
                    playbackRuntime.abandonLiveSelection(intent)
                    if (pendingIntent == intent) {
                        pendingIntent = null
                    }
                }
                playbackRuntime.noteLiveSelection(intent)
                pendingIntent = intent
                pendingZap = playbackSelectionScope.scheduleChannelZap(
                    pendingZap, pacer.settleDelayMs(SystemClock.uptimeMillis(), repeat = false),
                ) {
                    browseSteps.start(
                        playbackSelectionScope, step, entryPending = { playerEntry.pending },
                        onFinished = {
                            playbackRuntime.abandonLiveSelection(intent)
                            if (pendingIntent == intent) {
                                pendingIntent = null
                            }
                        },
                    ) {
                        browseStop?.join()
                        if (playbackRuntime.isPlaybackIntentStopped(intent)) return@start
                        playbackOrchestrator.startLiveChannelFromBrowse(adjacentId, intent) {
                            playbackRuntime.playLive(selection, it)
                        }
                    }
                }
            }
        } else null
        onDispose {
            unregister?.invoke()
            browseSteps.withdrawPending()
        }
    }
    LaunchedEffect(currentDestination != null, isPlayer, warmPlaybackMode, playbackRuntime) {
        if (currentDestination != null && shouldStopPlaybackWhileBrowsing(isPlayer, warmPlaybackMode)) {
            // An accepted stop outlives route/setting changes; entry waits before reading the target.
            browseStop = playbackSelectionScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                withContext(NonCancellable) { playbackRuntime.stop() }
            }
        }
    }
    // Warm live playback behind browse has no player screen to retune it after a reconnect;
    // a channel paused there is stopped, since the restart ended what its Pause held.
    LaunchedEffect(isPlayer, activeChannelId, warmPlaybackMode) {
        if (isPlayer || activeChannelId == null || warmPlaybackMode == null || warmPlaybackMode == WarmPlaybackMode.OFF) return@LaunchedEffect
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
    var homeContentDrawn by remember(profileGeneration) { mutableStateOf(false) }
    val coldStart = rememberColdStart(
        // Nothing connects while no server is in use, so there is no connection to wait for.
        launchIdle = applianceLaunchState == ApplianceLaunchState.Idle && !noServerInUse,
        connectionState = connectionUiState,
        currentChannelReadiness = currentChannelReadiness,
        loadingTiming = startupLoadingTiming,
        landingContentDrawn = homeContentDrawn,
    )
    val coldStartVisible = applianceLaunchState == ApplianceLaunchState.Idle &&
        coldStart.presentation != MainStartupPresentation.Inactive
    val effectiveStartupPresentation = if (coldStartVisible) coldStart.presentation else startupPresentation
    val applianceLaunchActive =
        effectiveStartupPresentation != MainStartupPresentation.Inactive || revealingTarget != null
    // Pending Guide replaces Entering and can remove its startup cover. Route against the
    // foreground before that request, not a live destination exposed by the request itself.
    var guideForegroundPlayer by remember(profileGeneration) { mutableStateOf<LivePlayerKey?>(null) }
    SideEffect {
        if (guideRequest == null) guideForegroundPlayer = visibleLivePlayer.takeUnless { applianceLaunchActive }
    }
    val guideInLivePlayer = visibleLivePlayer != null && guideForegroundPlayer == visibleLivePlayer &&
        guideRequest?.request?.destination != ApplianceEntryDestination.RECORDINGS &&
        effectiveStartupPresentation == MainStartupPresentation.Inactive && revealingTarget == null
    val navigationAllowed = when (val launchState = applianceLaunchState) {
        ApplianceLaunchState.Idle ->
            ((coldStartVisible && landingCatalogAvailable) || effectiveStartupPresentation == MainStartupPresentation.Inactive) &&
                navigationStartDestination != null &&
                backStack.isNotEmpty()
        is ApplianceLaunchState.Pending -> guideRequest != null && backStack.isNotEmpty()
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
    val guideEntryAction = ApplianceEntryPolicy.guideEntryAction(
        connectionState = connectionUiState,
        readiness = currentChannelReadiness,
        warm = backStack.isNotEmpty(),
        connectionSettingsVisible = currentDestination == SettingsKey(SettingsSection.CONNECTION),
    )
    LaunchedEffect(guideRequest, guideEntryAction, currentDestination, guideInLivePlayer) {
        val pending = guideRequest ?: return@LaunchedEffect
        // The visible live player owns GUIDE, including its options/recovery no-op guards.
        if (guideInLivePlayer) return@LaunchedEffect
        if (guideEntryAction == ApplianceGuideEntryAction.WAIT) return@LaunchedEffect
        if (!applianceLaunchRequests.cancel(pending)) return@LaunchedEffect
        when (guideEntryAction) {
            ApplianceGuideEntryAction.OPEN_GUIDE -> {
                coldStart.settle()
                val destination = if (pending.request.destination == ApplianceEntryDestination.RECORDINGS)
                    RecordingsKey else GuideKey
                if (currentDestination != destination) {
                    playbackOrchestrator.browseNavigationSelected(playingChannelId, playingRecordingId)
                    if (backStack.isEmpty()) navigationStartDestination = destination
                    navigateTopLevel(destination)
                }
            }
            ApplianceGuideEntryAction.DROP,
            ApplianceGuideEntryAction.WAIT -> Unit
        }
    }
    LaunchedEffect(playingChannelId, playingRecordingId) {
        playbackOrchestrator.activePlaybackChanged(playingChannelId, playingRecordingId)
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
            if (backStack.isNotEmpty()) navigationStartDestination = backStack.first()
            else selectRoot(HomeKey)
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
                selectRoot(HomeKey)
            } else performMainStartupBack(
                requests = applianceLaunchRequests,
                expectedState = expectedState,
                selectRoot = selectRoot,
            )
        }
    }
    // Back with no player to return to: what rootBackAction does for an unarmed return.
    val backWithoutWarmPlayer: () -> Unit = {
        val action = rootBackAction(currentDestination == HomeKey, WarmReturnOpportunity())
        when (action) {
            BackAction.FINISH_ACTIVITY -> onRequestExit()
            BackAction.POP_NAVIGATION -> if (!backStack.navigateBackTowardHome()) onRequestExit()
            else -> Unit
        }
    }
    val returnToWarmPlayer: (() -> Unit) -> Unit = { onUnavailable ->
        playerEntry.request(playbackSelectionScope) {
            browseSteps.withdrawPending()
            browseSteps.joinStart()
            if (browseStop?.isCompleted == false) return@request
            val (channelId, recordingId) = playingNow()
            if (!playbackOrchestrator.canReturnToPlayer(channelId, recordingId)) {
                onUnavailable()
                return@request
            }
            playbackOrchestrator.browseNavigationSelected(channelId, recordingId)
            when (val target = playbackOrchestrator.consumeWarmPlayerTarget(
                activeChannelId = channelId,
                activeRecordingId = recordingId,
                currentChannelReadiness = currentChannelReadiness,
                noteViewingIntent = { playbackRuntime.notePlaybackIntent() },
            )) {
                is PlayerRouteTarget.Live -> backStack.pushTransient(LivePlayerKey(target.channelId.value, target.channelName))
                is PlayerRouteTarget.Recording -> backStack.pushTransient(RecordingPlayerKey(target.recordingId.value))
                null -> Unit
            }
        }
    }
    val handleRootBack: () -> Unit = rootBack@{
        if (applianceLaunchActive) {
            startupBack()
            return@rootBack
        }
        if (currentDestination == HomeKey && browseSteps.starting) {
            returnToWarmPlayer(backWithoutWarmPlayer)
            return@rootBack
        }

        when (
            rootBackAction(
                isStartDestination = currentDestination == HomeKey,
                warmReturn = playingNow().let { (channelId, recordingId) ->
                    if (playbackOrchestrator.canReturnToPlayer(channelId, recordingId)) warmReturn
                    else WarmReturnOpportunity()
                },
            )
        ) {
            BackAction.FINISH_ACTIVITY -> onRequestExit()
            BackAction.POP_NAVIGATION -> {
                if (!backStack.navigateBackTowardHome()) onRequestExit()
            }
            BackAction.RETURN_TO_PARENT -> Unit
            BackAction.RETURN_TO_PLAYER -> returnToWarmPlayer(backWithoutWarmPlayer)
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
            browseSteps.withdrawPending()
            browseSteps.joinStart()
            browseStop?.join()
            val target = playbackOrchestrator.requestLivePlayer(
                activeChannelId = playingNow().first,
                activeRecordingId = playingNow().second,
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
    // Returns to the recording that still plays behind browse, without restarting it.
    val returnToWarmRecording: (DvrEntryId, () -> Unit) -> Boolean = warmRecording@{ id, beforeRequest ->
        // No recording can be adopted while a browse live start is changing the target.
        // Returning false leaves the caller's ordinary recording selection to join that start.
        if (playerEntry.pending || browseSteps.starting) return@warmRecording false
        browseSteps.withdrawPending()
        val currentTarget = playbackRuntime.activeTarget.value
        val currentSelection = playbackRuntime.recordingSelection.value
        val stillWarm = browseStop?.isCompleted != false && warmRecordingId(
            playingTarget(currentTarget, playbackRuntime.state.value), currentSelection,
            tvheadendSession.observation.value.currentSession) == id
        if (stillWarm) {
            beforeRequest()
            playbackRuntime.notePlaybackIntent()
            playerEntry.request(playbackSelectionScope) {
                browseSteps.withdrawPending()
                browseSteps.joinStart()
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
    }
    val content: @Composable (PaddingValues, Boolean, Boolean, List<AppNavKey>) -> Unit = {
            contentPadding, drawerActive, contentAllowed, shellBackStack ->
            Box(Modifier.fillMaxSize().profileRouteDraw(currentDestination?.destination?.name ?: "NONE", drawerActive)
                .then(if (coldStartVisible) Modifier.clearAndSetSemantics {} else Modifier)) {
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
                    entry<HomeKey>(
                        metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.HOME),
                    ) {
                        HomeRouteContent(
                            lastUpdateTime = lastUpdateTime,
                            homeFocus = homeFocus,
                            contentAllowed = contentAllowed || coldStartVisible,
                            contentPadding = contentPadding,
                            initialFocusEnabled = !coldStartVisible && !drawerActive && currentDestination == HomeKey,
                            onContentDrawn = {
                                if (landingCatalogAvailable) {
                                    homeContentDrawn = true
                                }
                            },
                            channelsVm = channelsVm,
                            playingChannelId = playingChannelId,
                            playingRecordingId = warmRecordingId,
                            connectionUiState = connectionUiState,
                            connectionFailure = browseConnectionFailure,
                            onRetry = retryBrowseConnection,
                            onOpenConnectionSettings = { navigateTopLevel(SettingsKey(SettingsSection.CONNECTION)) },
                            onBrowseChannels = {
                                playbackOrchestrator.browseNavigationSelected(playingChannelId, playingRecordingId)
                                navigateTopLevel(ChannelsKey)
                            },
                            onPlay = requestLivePlayer,
                            onReturnToRecording = returnToWarmRecording,
                            onRecordings = { mode, entryId -> recordingsScreenState.open(mode, entryId); navigateTopLevel(RecordingsKey) },
                            onGenre = { category -> guideGenreRequest = category; navigateTopLevel(GuideKey) },
                            onShowGuide = { channelId, programme ->
                                guideChannelRequest = PlayerReturnFocus(channelId, channelId, programme?.id, programme?.start?.epochSeconds)
                                navigateTopLevel(GuideKey)
                            },
                            onPlayRecording = { selection, intent ->
                                playerEntry.request(playbackSelectionScope) {
                                    browseSteps.withdrawPending()
                                    browseSteps.joinStart()
                                    browseStop?.join()
                                    val target = playbackOrchestrator.requestRecordingPlayer(
                                        activeChannelId = playingNow().first,
                                        activeRecordingId = playingNow().second,
                                        requestedRecordingId = selection.recordingId,
                                        startPlayback = { playbackRuntime.playRecording(selection, intent) },
                                    ) ?: return@request
                                    backStack.pushTransient(recordingPlayerKey(target.recordingId, intent))
                                }
                            },
                        )
                    }

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
                            playingChannelId = playingChannelId,
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
                                    browseSteps.withdrawPending()
                                    browseSteps.joinStart()
                                    browseStop?.join()
                                    val target = playbackOrchestrator.requestRecordingPlayer(
                                        activeChannelId = playingNow().first,
                                        activeRecordingId = playingNow().second,
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
                            genreRequest = guideGenreRequest,
                            genre = guideGenre,
                            onGenreChange = { guideGenre = it },
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
                                    browseSteps.withdrawPending()
                                    browseSteps.joinStart()
                                    browseStop?.join()
                                    val target = playbackOrchestrator.requestRecordingPlayer(
                                        activeChannelId = playingNow().first,
                                        activeRecordingId = playingNow().second,
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
                            onReturnToRecording = returnToWarmRecording,
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
                                    browseSteps.withdrawPending()
                                    browseSteps.joinStart()
                                    browseStop?.join()
                                    val target = playbackOrchestrator.requestRecordingPlayer(
                                        activeChannelId = playingNow().first,
                                        activeRecordingId = playingNow().second,
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
                            contentAllowed = contentAllowed || coldStartVisible,
                            section = destination.section,
                            isCurrent = currentDestination == destination,
                            initialFocusEnabled = !drawerActive && currentDestination == destination,
                            contentPadding = contentPadding,
                            backEnabled = !applianceLaunchActive,
                            onGoHome = { navigateTopLevel(HomeKey) },
                        )
                    }

                    entry<LivePlayerKey> { destination ->
                        CompositionLocalProvider(LocalSessionChannelStepAllowed provides (currentDestination == destination)) {
                            LivePlayerRouteContent(
                                guideEntryRequestId = guideRequest?.request?.id
                                    ?.takeIf { currentDestination == destination && guideInLivePlayer },
                                consumeGuideEntry = {
                                    guideRequest?.let(applianceLaunchRequests::cancel) == true
                                },
                                onPlayRecording = { selection ->
                                    playerEntry.request(playbackSelectionScope) {
                                        browseSteps.withdrawPending()
                                        browseSteps.joinStart()
                                        browseStop?.join()
                                        val target = playbackOrchestrator.requestRecordingPlayer(
                                            playingNow().first,
                                            playingNow().second,
                                            selection.recordingId,
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
                                targetAspect = targetAspect,
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
                    }

                    entry<RecordingPlayerKey> { destination ->
                        RecordingPlayerRouteContent(
                            onShowGuide = { channelId, programme ->
                                guideChannelRequest = PlayerReturnFocus(channelId, channelId,
                                    programme?.id, programme?.start?.epochSeconds)
                                navigateTopLevel(GuideKey)
                            },
                            targetAspect = targetAspect,
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

    // Launch, HOME, GUIDE and wake have no channel to play while no server is in use; they open
    // the server list instead.
    LaunchedEffect(noServerInUse, applianceLaunchState, selectRoot) {
        if (!noServerInUse || applianceLaunchState == ApplianceLaunchState.Idle) return@LaunchedEffect
        cancelStartupAndSelectRoot(
            requests = applianceLaunchRequests,
            expectedState = applianceLaunchState,
            destination = SettingsKey(SettingsSection.CONNECTION),
            selectRoot = selectRoot,
        )
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
        is ApplianceLaunchState.Pending -> launch.request.id.takeIf { guideRequest == null }
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
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        if (key.keyCode == android.view.KeyEvent.KEYCODE_DVR) {
            if (key.action == android.view.KeyEvent.ACTION_DOWN && key.repeatCount == 0 &&
                currentDestination != RecordingsKey && navigationAllowed) {
                playbackOrchestrator.browseNavigationSelected(playingChannelId, playingRecordingId)
                navigateTopLevel(RecordingsKey)
            }
            true
        } else false
    }.onKeyEvent { it.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MEDIA_RECORD }) {
    MainStartupComposition(
        state = MainStartupCompositionState(
            presentation = effectiveStartupPresentation,
            navigationStartDestination = navigationStartDestination,
            navigationAllowed = navigationAllowed,
            contentAllowed =
                (applianceLaunchState == ApplianceLaunchState.Idle || guideRequest != null && backStack.isNotEmpty()) &&
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
            mode = warmPlaybackMode,
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
                    aspectRatio = targetAspect.mode,
                    videoVisible = videoPresentation.visible,
                    debugVideoBackdropVisible = debugVideoBackdropVisible,
                    modifier = Modifier.fillMaxSize(),
                    // The startup screen and its reveal own an autoplay's first picture.
                    coverFadeMillis = rememberVideoCoverFadeMillis(
                        playerShown = isPlayer,
                        videoVisible = videoPresentation.visible,
                        instant = applianceLaunchActive || !ValueAnimator.areAnimatorsEnabled(),
                    ),
                    // Browse drops seamless requests, but avoids another HDMI blank for non-seamless ones.
                    frameRate = requestedFrameRate,
                    playerShown = isPlayer,
                )
            }
        },
        navigation = { _, contentAllowed ->
            MainNavigationShell(
                showRail = showRail,
                // Use the browse role, not the live destination: the outgoing shell still covers video.
                coverBrowse = shouldCoverWarmPlayback(isPlayerRoute = false, mode = warmPlaybackMode),
                // The startup screen and its reveal own the way into an autoplay.
                motionEnabled = !applianceLaunchActive && ValueAnimator.areAnimatorsEnabled(),
                rail = {
                    val railBackStack = rememberShellBackStack(backStack, playerShell = false)
                    // Hidden from the moment browse stops playback, not only once the stop completes.
                    val nowPlaying = railNowPlaying(activeTarget, playbackState, playbackObservation, warmRecordingId,
                        stopWhileBrowsing = shouldStopPlaybackWhileBrowsing(isPlayerRoute = false, mode = warmPlaybackMode))
                    PlayerEntryOverlays(playerEntry,
                        playerShown = { !shouldShowMainNavigationRail(backStack.lastOrNull(), navigationStartDestination) }) {
                    ProvideWatchingProgress(playbackRuntime) {
                    SideRail(
                        modifier = if (coldStartVisible) Modifier.clearAndSetSemantics {} else Modifier,
                        // A fading rail keeps marking the page it fades with.
                        currentRoute = (railBackStack.lastOrNull() ?: currentDestination)?.destination,
                        connectionDegraded = connectionPresentation.degraded,
                        rootRoute = AppDestination.HOME,
                        availableDestinations = buildSet {
                            add(AppDestination.HOME)
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
                                    activeChannelId = playingChannelId,
                                    activeRecordingId = playingRecordingId,
                                )
                                navigateTopLevel(
                                    destination.toTopLevelKey(
                                        settingsSection = backStack.lastSettingsSection(),
                                    ),
                                )
                            }
                        },
                        nowPlaying = nowPlaying,
                        nowPlayingPaused = at.bernhardberger.tvhplayer.ui.components.drawerControlPaused(
                            nowPlaying?.target, activeTarget, livePause,
                            at.bernhardberger.tvhplayer.ui.components.rememberPlaybackIntent(playbackRuntime.player).value,
                        ) == true,
                        livePauseAvailable = livePause.availability == at.bernhardberger.tvhplayer.playback.LivePauseAvailability.READY ||
                            livePause.availability == at.bernhardberger.tvhplayer.playback.LivePauseAvailability.STARTING,
                        onToggleNowPlayingPause = {
                            val target = nowPlaying?.target
                            val playWhenReady = at.bernhardberger.tvhplayer.ui.components.drawerTogglePlayWhenReady(
                                target, playbackRuntime.activeTarget.value,
                                playbackRuntime.livePause.value, playbackRuntime.player.playWhenReady,
                            )
                            if (playWhenReady != null) {
                                // Same local/server transport as player keys, with the runtime's intent fence.
                                playbackRuntime.setSessionPlayWhenReady(playWhenReady) { playbackRuntime.activeTarget.value == target }
                            }
                        },
                        onStopNowPlaying = {
                            val target = nowPlaying?.target
                            if (at.bernhardberger.tvhplayer.ui.components.shouldStopDrawerPlayback(
                                    target, playbackRuntime.activeTarget.value, browseStop?.isCompleted == false,
                                )) {
                                browseStop = playbackSelectionScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                                    withContext(NonCancellable) { playbackRuntime.stop() }
                                }
                            }
                        },
                        // Read from the collected observation so a guide that arrives later reaches the slot.
                        programmeAt = { channelId, atSec ->
                            playbackObservation.eventAt(channelId, kotlin.time.Instant.fromEpochSeconds(atSec))
                        },
                        // The same warm return as Continue watching: the player opens on what plays.
                        onOpenNowPlaying = { returnToWarmPlayer {} },
                        content = { contentPadding, drawerActive ->
                            content(contentPadding, drawerActive, contentAllowed, railBackStack)
                        },
                    )
                    }
                    }
                },
                fullScreen = {
                    ProvideWatchingProgress(playbackRuntime) {
                        content(TvFullScreenPadding, false, contentAllowed, rememberShellBackStack(backStack, playerShell = true))
                    }
                },
            )
        },
    )
    }
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
    AppDestination.HOME -> HomeKey
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
