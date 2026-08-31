package at.bernhardberger.tvhplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
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
import at.bernhardberger.tvhplayer.core.MainStartupActionId
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.core.PlaybackRecoverySecondaryAction
import at.bernhardberger.tvhplayer.core.mainStartupPresentation
import at.bernhardberger.tvhplayer.core.applianceProductProfile
import at.bernhardberger.tvhplayer.core.ProductProfile
import at.bernhardberger.tvhplayer.core.rootBackAction
import at.bernhardberger.tvhplayer.core.serverSettingsForRuntime
import at.bernhardberger.tvhplayer.core.showGlobalNavigationRail
import at.bernhardberger.tvhplayer.core.SimpleTvCapability
import at.bernhardberger.tvhplayer.core.SimpleTvRoute
import at.bernhardberger.tvhplayer.core.RecordingFinishedAction
import at.bernhardberger.tvhplayer.core.recordingFinishedAction
import at.bernhardberger.tvhplayer.core.allows
import at.bernhardberger.tvhplayer.core.allowsRoute
import at.bernhardberger.tvhplayer.core.shouldMountPersistentPlayerSurface
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.LivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.stores.SimpleTvSession
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.player.PlayerVideoSurface
import at.bernhardberger.tvhplayer.ui.screens.OnboardingScreen
import at.bernhardberger.tvhplayer.ui.screens.RecordingsScreenState
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyMode
import at.bernhardberger.tvhplayer.ui.startup.MainStartupScreen
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

internal data class MainStartupCompositionState(
    val presentation: MainStartupPresentation,
    val navigationStartDestination: AppNavKey?,
    val navigationAllowed: Boolean,
    val contentAllowed: Boolean = presentation == MainStartupPresentation.Inactive,
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
): Boolean = requests.completePlayerVisibility(target, channelId, channelName)

internal fun shouldShowMainNavigationRail(
    simpleTvActive: Boolean,
    currentDestination: AppNavKey?,
    navigationStartDestination: AppNavKey?,
): Boolean = showGlobalNavigationRail(
    simpleTvActive = simpleTvActive,
    playerVisible = when (currentDestination ?: navigationStartDestination) {
        is LivePlayerKey, is RecordingPlayerKey -> true
        else -> false
    },
)

@Composable
internal fun MainNavigationShell(
    showRail: Boolean,
    rail: @Composable () -> Unit,
    fullScreen: @Composable () -> Unit,
) {
    if (showRail) rail() else fullScreen()
}

@Composable
internal fun MainStartupComposition(
    state: MainStartupCompositionState,
    onBack: () -> Unit,
    onAction: (MainStartupActionId) -> Unit,
    registerActivityKeyContract: (MainStartupActivityKeyContract) -> (() -> Unit),
    modifier: Modifier = Modifier,
    persistentSurface: @Composable BoxScope.() -> Unit = {},
    navigation: @Composable BoxScope.(AppNavKey, Boolean) -> Unit = { _, _ -> },
) {
    val renderedPresentation = when (state.presentation) {
        is MainStartupPresentation.Enter -> MainStartupPresentation.Passive(
            MainStartupMessageKind.STARTING_TELEVISION,
        )
        else -> state.presentation
    }
    val keyMode = when (renderedPresentation) {
        MainStartupPresentation.Inactive -> MainStartupKeyMode.Inactive
        is MainStartupPresentation.Passive -> MainStartupKeyMode.Passive
        is MainStartupPresentation.Actionable -> MainStartupKeyMode.Actionable
        is MainStartupPresentation.Enter -> error("Enter is rendered as passive startup")
    }
    val keyContract = remember(keyMode) {
        MainStartupActivityKeyContract(mode = keyMode)
    }

    DisposableEffect(registerActivityKeyContract, keyContract) {
        val unregister = registerActivityKeyContract(keyContract)
        onDispose(unregister)
    }

    Box(modifier = modifier.fillMaxSize()) {
        persistentSurface()
        val startDestination = state.navigationStartDestination
        if (state.navigationAllowed && startDestination != null) {
            navigation(
                startDestination,
                state.contentAllowed,
            )
        }
        if (renderedPresentation != MainStartupPresentation.Inactive) {
            MainStartupScreen(
                presentation = renderedPresentation,
                contentPadding = TvFullScreenPadding,
                onAction = onAction,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    BackHandler(
        enabled = renderedPresentation != MainStartupPresentation.Inactive,
        onBack = onBack,
    )
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
    playerCloseAllowed: Boolean,
    popBackStack: () -> Boolean,
    selectRoot: (AppNavKey) -> Unit,
) {
    if (!playerCloseAllowed) return
    if (!popBackStack()) selectRoot(ChannelsKey)
}

internal fun performMainStartupBack(
    productProfile: ProductProfile,
    requests: ApplianceLaunchRequests,
    expectedState: ApplianceLaunchState,
    selectRoot: (AppNavKey) -> Unit,
) {
    if (!productProfile.allows(SimpleTvCapability.APP_EXIT)) return
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
    CONTAIN_SIMPLE_TV,
}

internal fun deferredResolvingBackAction(
    cancellationRequested: Boolean,
    readyState: MainStartupState.Ready,
): DeferredResolvingBackAction = when {
    !cancellationRequested -> DeferredResolvingBackAction.NONE
    readyState.startupProfile is ProductProfile.Appliance ->
        DeferredResolvingBackAction.CONTAIN_SIMPLE_TV
    else -> DeferredResolvingBackAction.CANCEL_TO_CHANNELS
}

internal fun applyDeferredResolvingBack(
    action: DeferredResolvingBackAction,
    requests: ApplianceLaunchRequests,
    expectedState: ApplianceLaunchState,
    selectRoot: (AppNavKey) -> Unit,
): Boolean = when (action) {
    DeferredResolvingBackAction.NONE,
    DeferredResolvingBackAction.CONTAIN_SIMPLE_TV -> false
    DeferredResolvingBackAction.CANCEL_TO_CHANNELS -> cancelStartupAndSelectRoot(
        requests = requests,
        expectedState = expectedState,
        destination = ChannelsKey,
        selectRoot = selectRoot,
    )
}

internal fun performMainStartupAction(
    action: MainStartupActionId,
    onRetry: () -> Unit,
    onConnectionSettings: () -> Unit,
    onExitSimpleTv: () -> Unit,
) {
    when (action) {
        MainStartupActionId.RETRY -> onRetry()
        MainStartupActionId.CONNECTION_SETTINGS -> onConnectionSettings()
        MainStartupActionId.EXIT_SIMPLE_TV -> onExitSimpleTv()
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
                ),
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
        readyState = readyStartupState,
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
    val connectionUiState by appVm.uiState.collectAsStateWithLifecycle()
    val connectionState by appVm.connectionState.collectAsStateWithLifecycle()
    val lastPlayedChannelStore: LastPlayedChannelStore = koinInject()
    val playbackRuntime: AppPlaybackRuntime = koinInject()
    val playbackSelectionScope = rememberCoroutineScope()
    val playbackOrchestrator = remember { AppRootPlaybackOrchestrator() }
    val playerSettingsStore: PlayerSettingsStore = koinInject()
    val playbackState by playbackRuntime.state.collectAsStateWithLifecycle()
    val activeTarget by playbackRuntime.activeTarget.collectAsStateWithLifecycle()
    val videoPresentation by playbackRuntime.videoPresentation.collectAsStateWithLifecycle()
    val activeChannelId = (activeTarget as? AppPlaybackTarget.Live)?.channelId
    val activeRecordingId = (activeTarget as? AppPlaybackTarget.Recording)?.recordingId
    val playerSettings by playerSettingsStore.playerSettings.collectAsStateWithLifecycle(
        initialValue = PlayerSettings(audioLanguage = null, subtitleLanguage = null)
    )
    val uiSettingsStore: UiSettingsStore = koinInject()
    val uiSettings by uiSettingsStore.settings.collectAsStateWithLifecycle(initialValue = UiSettings())
    val simpleTvSession: SimpleTvSession = koinInject()
    val productProfile by simpleTvSession.profile.collectAsStateWithLifecycle()
    val simpleTvActive = productProfile is ProductProfile.Appliance
    val playerCloseAllowed = productProfile.allows(SimpleTvCapability.PLAYER_CLOSE)
    val fullPlaybackOptionsAvailable =
        productProfile.allows(SimpleTvCapability.FULL_PLAYBACK_OPTIONS)
    val recoverySecondaryAction = if (playerCloseAllowed) {
        PlaybackRecoverySecondaryAction.CLOSE
    } else {
        PlaybackRecoverySecondaryAction.EXIT_SIMPLE_TV
    }
    val currentChannelReadiness by
        appVm.currentChannelReadiness.collectAsStateWithLifecycle()
    val startupPresentation = mainStartupPresentation(
        startupState = startupState,
        launchState = applianceLaunchState,
        connectionState = connectionUiState,
        currentChannelReadiness = currentChannelReadiness,
        simpleTvActive = simpleTvActive,
    )

    val currentDestination = backStack.lastOrNull()
    val showRail = shouldShowMainNavigationRail(
        simpleTvActive = simpleTvActive,
        currentDestination = currentDestination,
        navigationStartDestination = navigationStartDestination,
    )

    val isPlayer = currentDestination is LivePlayerKey ||
        currentDestination is RecordingPlayerKey
    val enteringLaunchTarget =
        (applianceLaunchState as? ApplianceLaunchState.Entering)?.target
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
    val effectiveStartupPresentation = startupPresentation
    val applianceLaunchActive =
        effectiveStartupPresentation != MainStartupPresentation.Inactive
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

    SimpleTvRouteGuardEffect(
        route = currentDestination?.toSimpleTvRoute(),
        profile = productProfile,
        recordingActive = activeRecordingId != null,
        orchestrator = playbackOrchestrator,
        stopRecording = playbackRuntime::stop,
        redirectToLive = {
            applianceLaunchRequests.requestStartup(autoStartPlayback = true)
        },
    )

    SideEffect { onPlayerVisibilityChanged(isPlayer) }

    LaunchedEffect(
        enteringLaunchTarget,
        visibleLivePlayerChannelId,
        visibleLivePlayerName,
    ) {
        val target = enteringLaunchTarget
        val channelId = visibleLivePlayerChannelId
        val channelName = visibleLivePlayerName
        if (
            target != null &&
            channelId != null &&
            channelName != null
        ) {
            completeEnteringPlayerVisibility(
                requests = applianceLaunchRequests,
                target = target,
                channelId = channelId,
                channelName = channelName,
            )
        }
    }

    val enterDirective = startupPresentation as? MainStartupPresentation.Enter
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

    val startupBack = remember(applianceLaunchState, productProfile, selectRoot) {
        val expectedState = applianceLaunchState
        {
            performMainStartupBack(
                productProfile = productProfile,
                requests = applianceLaunchRequests,
                expectedState = expectedState,
                selectRoot = selectRoot,
            )
        }
    }
    val handleRootBack: () -> Unit = rootBack@{
        val currentLaunchState = applianceLaunchRequests.state.value
        if (currentLaunchState != ApplianceLaunchState.Idle && applianceLaunchActive) {
            startupBack()
            return@rootBack
        }

        when (
            rootBackAction(
                isStartDestination = currentDestination == ChannelsKey,
                warmReturn = warmReturn,
            )
        ) {
            BackAction.FINISH_ACTIVITY -> {
                if (productProfile.allows(SimpleTvCapability.APP_EXIT)) {
                    onRequestExit()
                }
                // Simple TV never exits through Back; ignore when exit is gated.
            }
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
    BackHandler(
        enabled = navigationAllowed && !applianceLaunchActive && !showRail,
        onBack = handleRootBack,
    )
    val browseBackHandler = remember { mutableStateOf(handleRootBack) }
    val requestLivePlayer: (LivePlaybackSelection, String) -> Unit = { selection, name ->
        playbackSelectionScope.launch {
            val target = playbackOrchestrator.requestLivePlayer(
                activeChannelId = activeChannelId,
                activeRecordingId = activeRecordingId,
                requestedChannelId = selection.channelId,
                requestedChannelName = name,
                startPlayback = { playbackRuntime.playLive(selection) },
            ) ?: return@launch
            backStack.pushTransient(
                LivePlayerKey(target.channelId.value, target.channelName),
            )
        }
    }
    val content: @Composable (PaddingValues, Boolean, Boolean) -> Unit = {
            contentPadding, drawerActive, contentAllowed ->
            Box(Modifier.fillMaxSize()) {
                NavDisplay(
                    backStack = backStack,
                    onBack = {
                        if (showRail) browseBackHandler.value() else handleRootBack()
                    },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    transitionSpec = { appDestinationContentTransform() },
                    popTransitionSpec = { appDestinationContentTransform() },
                    predictivePopTransitionSpec = { appDestinationContentTransform() },
                    entryProvider = entryProvider {
                    entry<ChannelsKey> {
                        ChannelsRouteContent(
                            contentAllowed = contentAllowed,
                            routeAllowed =
                                productProfile.allowsRoute(SimpleTvRoute.CHANNELS),
                            contentPadding = contentPadding,
                            initialFocusEnabled = !drawerActive,
                            playingChannelId = activeChannelId,
                            connectionUiState = connectionUiState,
                            onRetryConnection = appVm::reconnectNow,
                            onOpenConnectionSettings = {
                                navigateTopLevel(SettingsKey(SettingsSection.CONNECTION))
                            },
                            onPlay = requestLivePlayer,
                        )
                    }

                    entry<GuideKey> {
                        GuideRouteContent(
                            contentAllowed = contentAllowed,
                            routeAllowed = productProfile.allowsRoute(SimpleTvRoute.EPG),
                            contentPadding = contentPadding,
                            initialFocusEnabled = !drawerActive,
                            connectionUiState = connectionUiState,
                            onRetry = appVm::reconnectNow,
                            onOpenConnectionSettings = {
                                navigateTopLevel(SettingsKey(SettingsSection.CONNECTION))
                            },
                            timeshiftAllowed =
                                productProfile.allows(SimpleTvCapability.TIMESHIFT),
                            recordingsAllowed =
                                productProfile.allows(SimpleTvCapability.RECORDINGS),
                            onPlayRecording = { playbackSelection ->
                                val recordingId = playbackSelection.recordingId
                                playbackSelectionScope.launch {
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
                                    ) ?: return@launch
                                    backStack.pushTransient(
                                        RecordingPlayerKey(target.recordingId.value),
                                    )
                                }
                            },
                            onPlay = requestLivePlayer,
                        )
                    }

                    entry<RecordingsKey> {
                        RecordingsRouteContent(
                            contentAllowed = contentAllowed,
                            routeAllowed =
                                productProfile.allowsRoute(SimpleTvRoute.RECORDINGS),
                            contentPadding = contentPadding,
                            initialFocusEnabled = !drawerActive,
                            backEnabled = !applianceLaunchActive,
                            connectionUiState = connectionUiState,
                            onRetry = appVm::reconnectNow,
                            onPlayRecording = { playbackSelection, intent ->
                                val recordingId = playbackSelection.recordingId
                                playbackSelectionScope.launch {
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
                                    ) ?: return@launch
                                    backStack.pushTransient(
                                        RecordingPlayerKey(
                                            recordingId = target.recordingId.value,
                                            start = intent.toRecordingStartMode(),
                                        ),
                                    )
                                }
                            },
                            state = recordingsScreenState,
                        )
                    }

                    entry<SettingsKey> { destination ->
                        SettingsRouteContent(
                            contentAllowed = contentAllowed,
                            routeAllowed = productProfile.allowsRoute(SimpleTvRoute.SETTINGS),
                            section = destination.section,
                            initialFocusEnabled = !drawerActive,
                            contentPadding = contentPadding,
                            backEnabled = !applianceLaunchActive,
                            showSimpleTvSettings = productProfile is ProductProfile.Standard,
                            onNavigate = { section ->
                                navigateTopLevel(SettingsKey(section))
                            },
                            onStartSimpleTv = { settings ->
                                simpleTvSession.enter(applianceProductProfile(settings))
                            },
                        )
                    }

                    entry<UnlockKey> {
                        UnlockRouteContent(
                            contentAllowed = contentAllowed,
                            backEnabled = !drawerActive && !applianceLaunchActive,
                            onExited = {
                                navigateTopLevel(ChannelsKey)
                            },
                            onBack = {
                                backStack.popNavigation()
                                if (simpleTvActive && activeChannelId == null) {
                                    applianceLaunchRequests.request()
                                }
                            },
                        )
                    }

                    entry<LivePlayerKey> { destination ->
                        LivePlayerRouteContent(
                            contentAllowed = contentAllowed,
                            channelId = ChannelId(destination.channelId),
                            channelName = destination.channelName,
                            timeshiftAllowed =
                                productProfile.allows(SimpleTvCapability.TIMESHIFT),
                            showStop = productProfile.allows(SimpleTvCapability.STOP),
                            recordingActionsAllowed =
                                productProfile.allows(SimpleTvCapability.RECORDINGS),
                            playerCloseAllowed = playerCloseAllowed,
                            fullPlaybackOptionsAvailable = fullPlaybackOptionsAvailable,
                            recoverySecondaryAction = recoverySecondaryAction,
                            onReconnect = appVm::reconnectNow,
                            onUnlock = { backStack.pushTransient(UnlockKey) },
                            onClose = {
                                closeNormalLivePlayer(
                                    playerCloseAllowed = playerCloseAllowed,
                                    popBackStack = backStack::popNavigation,
                                    selectRoot = selectRoot,
                                )
                            },
                        )
                    }

                    entry<RecordingPlayerKey> { destination ->
                        RecordingPlayerRouteContent(
                            contentAllowed = contentAllowed,
                            routeAllowed =
                                productProfile.allowsRoute(SimpleTvRoute.RECORDING_PLAYER),
                            recordingId = DvrEntryId(destination.recordingId),
                            playbackStart = destination.start.toPlaybackStart(),
                            showStop = productProfile.allows(SimpleTvCapability.STOP),
                            showSimpleTvExit = !playerCloseAllowed,
                            playerCloseAllowed = playerCloseAllowed,
                            fullPlaybackOptionsAvailable = fullPlaybackOptionsAvailable,
                            connectionAvailable = connectionState is ConnectionState.Connected,
                            onReconnect = appVm::reconnectNow,
                            onUnlock = { backStack.pushTransient(UnlockKey) },
                            onClose = { backStack.popNavigation() },
                        )
                    }
                },
                )

            }
    }

    val startupAction = remember(applianceLaunchState, selectRoot, appVm) {
        val expectedState = applianceLaunchState
        { action: MainStartupActionId ->
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
                    Unit
                },
                onExitSimpleTv = {
                    cancelStartupAndSelectRoot(
                        requests = applianceLaunchRequests,
                        expectedState = expectedState,
                        destination = UnlockKey,
                        selectRoot = selectRoot,
                    )
                    Unit
                },
            )
        }
    }
    MainStartupComposition(
        state = MainStartupCompositionState(
            presentation = effectiveStartupPresentation,
            navigationStartDestination = navigationStartDestination,
            navigationAllowed = navigationAllowed,
            contentAllowed =
                applianceLaunchState == ApplianceLaunchState.Idle &&
                    effectiveStartupPresentation == MainStartupPresentation.Inactive,
        ),
        onBack = startupBack,
        onAction = startupAction,
        registerActivityKeyContract = registerActivityKeyContract,
        persistentSurface = {
            if (shouldMountPersistentPlayerSurface(
                    hasActivePlayback = playbackState !is AppPlaybackState.Idle,
                    isPlayerRoute = isPlayer,
                )
            ) {
                PlayerVideoSurface(
                    player = playbackRuntime.player,
                    aspectRatio = playerSettings.aspectRatio,
                    videoVisible = videoPresentation.visible,
                    debugVideoBackdropVisible = debugVideoBackdropVisible,
                    modifier = Modifier.fillMaxSize(),
                )
                if (navigationAllowed && showRail) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = TvScrimNavigationAlpha))
                    )
                }
            }
        },
        navigation = { _, contentAllowed ->
            MainNavigationShell(
                showRail = showRail,
                rail = {
                    SideRail(
                        currentRoute = currentDestination?.destination,
                        rootRoute = AppDestination.CHANNELS,
                        showEpgMenu = uiSettings.showEpgMenu,
                        availableDestinations = buildSet {
                            if (productProfile.allowsRoute(SimpleTvRoute.CHANNELS)) {
                                add(AppDestination.CHANNELS)
                            }
                            if (productProfile.allowsRoute(SimpleTvRoute.EPG)) {
                                add(AppDestination.GUIDE)
                            }
                            if (productProfile.allowsRoute(SimpleTvRoute.RECORDINGS)) {
                                add(AppDestination.RECORDINGS)
                            }
                            if (productProfile.allowsRoute(SimpleTvRoute.SETTINGS)) {
                                add(AppDestination.SETTINGS)
                            }
                            if (productProfile is ProductProfile.Appliance) {
                                add(AppDestination.UNLOCK)
                            }
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
                                if (destination == AppDestination.UNLOCK) {
                                    backStack.pushTransient(UnlockKey)
                                } else {
                                    navigateTopLevel(
                                        destination.toTopLevelKey(
                                            settingsSection = backStack.lastSettingsSection(),
                                        ),
                                    )
                                }
                            }
                        },
                        content = { contentPadding, drawerActive ->
                            content(contentPadding, drawerActive, contentAllowed)
                        },
                    )
                },
                fullScreen = {
                    content(TvFullScreenPadding, false, contentAllowed)
                },
            )
        },
    )
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
    AppDestination.UNLOCK,
    AppDestination.LIVE_PLAYER,
    AppDestination.RECORDING_PLAYER -> error("Transient destination is not top-level")
}

private fun RecordingPlaybackStart.toRecordingStartMode(): RecordingStartMode = when (this) {
    RecordingPlaybackStart.RESUME -> RecordingStartMode.RESUME
    RecordingPlaybackStart.START_OVER -> RecordingStartMode.START_OVER
}

private fun RecordingStartMode.toPlaybackStart(): RecordingPlaybackStart = when (this) {
    RecordingStartMode.RESUME -> RecordingPlaybackStart.RESUME
    RecordingStartMode.START_OVER -> RecordingPlaybackStart.START_OVER
}

private fun AppNavKey.toSimpleTvRoute(): SimpleTvRoute = when (this) {
    ChannelsKey -> SimpleTvRoute.CHANNELS
    GuideKey -> SimpleTvRoute.EPG
    RecordingsKey -> SimpleTvRoute.RECORDINGS
    is SettingsKey -> SimpleTvRoute.SETTINGS
    is LivePlayerKey -> SimpleTvRoute.PLAYER
    is RecordingPlayerKey -> SimpleTvRoute.RECORDING_PLAYER
    UnlockKey -> SimpleTvRoute.UNLOCK
}
