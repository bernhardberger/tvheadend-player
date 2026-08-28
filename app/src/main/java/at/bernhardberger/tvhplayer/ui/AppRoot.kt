package at.bernhardberger.tvhplayer.ui

import android.app.Activity
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
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.ApplianceLaunchState
import at.bernhardberger.tvhplayer.core.ApplianceLaunchTarget
import at.bernhardberger.tvhplayer.core.ApplianceLaunchBackAction
import at.bernhardberger.tvhplayer.core.BackAction
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.MainStartupActionId
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.core.mainStartupPresentation
import at.bernhardberger.tvhplayer.core.WarmPlaybackTarget
import at.bernhardberger.tvhplayer.core.WarmReturnOpportunity
import at.bernhardberger.tvhplayer.core.armWarmReturn
import at.bernhardberger.tvhplayer.core.applianceLaunchBackAction
import at.bernhardberger.tvhplayer.core.clearWarmReturn
import at.bernhardberger.tvhplayer.core.consumeWarmReturn
import at.bernhardberger.tvhplayer.core.rearmWarmReturn
import at.bernhardberger.tvhplayer.core.rearmWarmReturnForPlaybackSelection
import at.bernhardberger.tvhplayer.core.rootBackAction
import at.bernhardberger.tvhplayer.core.serverSettingsForRuntime
import at.bernhardberger.tvhplayer.core.showGlobalNavigationRail
import at.bernhardberger.tvhplayer.core.SimpleTvCapability
import at.bernhardberger.tvhplayer.core.SimpleTvProfile
import at.bernhardberger.tvhplayer.core.SimpleTvRoute
import at.bernhardberger.tvhplayer.core.SimpleTvRouteGuardAction
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvhplayer.core.RecordingFinishedAction
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import at.bernhardberger.tvhplayer.core.recordingFinishedAction
import at.bernhardberger.tvhplayer.core.simpleTvProfile
import at.bernhardberger.tvhplayer.core.simpleTvRouteGuardAction
import at.bernhardberger.tvhplayer.core.shouldMountPersistentPlayerSurface
import at.bernhardberger.tvhplayer.core.warmPlaybackTarget
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.settings.SimpleTvSettingsStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.stores.SimpleTvSession
import at.bernhardberger.tvhplayer.ui.components.ContentContainer
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.player.VideoPlayerScreen
import at.bernhardberger.tvhplayer.ui.player.RecordingPlayerScreen
import at.bernhardberger.tvhplayer.ui.player.PlayerVideoSurface
import at.bernhardberger.tvhplayer.ui.screens.ChannelsScreen
import at.bernhardberger.tvhplayer.ui.screens.EpgGridScreen
import at.bernhardberger.tvhplayer.ui.screens.OnboardingScreen
import at.bernhardberger.tvhplayer.ui.screens.RecordingsScreen
import at.bernhardberger.tvhplayer.ui.screens.RecordingsScreenState
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreen
import at.bernhardberger.tvhplayer.ui.screens.SettingsRoutes
import at.bernhardberger.tvhplayer.ui.screens.SimpleTvUnlockScreen
import at.bernhardberger.tvhplayer.ui.startup.MainStartupBackProfile
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyMode
import at.bernhardberger.tvhplayer.ui.startup.MainStartupScreen
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

object Routes {
    const val CHANNELS = "channels"
    const val EPG = "epg"
    const val RECORDINGS = "recordings"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val RECORDING_PLAYER = "recording-player"
    const val UNLOCK = "unlock"
    fun epg(category: ProgrammeCategory): String =
        "$EPG/${programmeCategoryRouteValue(category)}"
    fun player(channelId: ChannelId, channelName: String) =
        "player/${channelId.value}/${android.net.Uri.encode(channelName)}"
    fun recordingPlayer(
        recordingId: DvrEntryId,
        start: RecordingPlaybackStart = RecordingPlaybackStart.RESUME,
    ): String {
        val mode = when (start) {
            RecordingPlaybackStart.START_OVER -> "beginning"
            RecordingPlaybackStart.RESUME -> "resume"
        }
        return "recording-player/${recordingId.value}/$mode"
    }
}

internal data class MainStartupCompositionState(
    val presentation: MainStartupPresentation,
    val navigationStartDestination: String?,
    val navigationAllowed: Boolean,
    val contentAllowed: Boolean = presentation == MainStartupPresentation.Inactive,
)

internal data class WarmLivePlayerTarget(
    val channelId: ChannelId,
    val channelName: String,
)

internal fun warmLivePlayerTarget(
    activeChannelId: ChannelId,
    readiness: CurrentChannelReadiness,
): WarmLivePlayerTarget {
    val channel = (readiness as? CurrentChannelReadiness.Ready)
        ?.channels
        ?.firstOrNull { it.id == activeChannelId }
    return WarmLivePlayerTarget(
        channelId = channel?.id ?: activeChannelId,
        channelName = channel?.name.orEmpty(),
    )
}

internal fun enteringNavigationAllowed(
    hasBackStackEntry: Boolean,
    navigationStartDestination: String?,
    exactStartDestination: String,
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
    currentTopRoute: String?,
    navigationStartDestination: String?,
): Boolean = showGlobalNavigationRail(
    simpleTvActive = simpleTvActive,
    topRoute = currentTopRoute ?: navigationStartDestination?.substringBefore("/"),
    playerRoute = Routes.PLAYER,
    recordingPlayerRoute = Routes.RECORDING_PLAYER,
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
internal fun StartupGatedChannelsContent(
    contentAllowed: Boolean,
    channelsContent: @Composable () -> Unit,
) {
    if (contentAllowed) channelsContent()
}

@Composable
internal fun StartupGatedPlayerContent(
    contentAllowed: Boolean,
    playerContent: @Composable () -> Unit,
) {
    if (contentAllowed) playerContent()
}

@Composable
internal fun MainStartupComposition(
    state: MainStartupCompositionState,
    simpleTvActive: Boolean,
    onBack: () -> Unit,
    onAction: (MainStartupActionId) -> Unit,
    registerActivityKeyContract: (MainStartupActivityKeyContract) -> (() -> Unit),
    modifier: Modifier = Modifier,
    persistentSurface: @Composable BoxScope.() -> Unit = {},
    navigation: @Composable BoxScope.(String, Boolean) -> Unit = { _, _ -> },
) {
    val renderedPresentation = when (state.presentation) {
        is MainStartupPresentation.Enter -> MainStartupPresentation.Passive(
            MainStartupMessageKind.STARTING_TELEVISION,
        )
        else -> state.presentation
    }
    val backProfile = if (simpleTvActive) {
        MainStartupBackProfile.SIMPLE_TV
    } else {
        MainStartupBackProfile.NORMAL
    }
    val keyMode = when (renderedPresentation) {
        MainStartupPresentation.Inactive -> MainStartupKeyMode.Inactive
        is MainStartupPresentation.Passive -> MainStartupKeyMode.Passive(backProfile)
        is MainStartupPresentation.Actionable -> MainStartupKeyMode.Actionable(backProfile)
        is MainStartupPresentation.Enter -> error("Enter is rendered as passive startup")
    }
    val keyContract = remember(keyMode, onBack) {
        MainStartupActivityKeyContract(
            mode = keyMode,
            cancelNormalStartup = if (
                keyMode != MainStartupKeyMode.Inactive &&
                backProfile == MainStartupBackProfile.NORMAL
            ) {
                onBack
            } else {
                null
            },
        )
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
    destination: String,
    selectRoot: (String) -> Unit,
): Boolean {
    if (!requests.cancel(expectedState)) return false
    selectRoot(destination)
    return true
}

internal fun closeNormalLivePlayer(
    simpleTvActive: Boolean,
    popBackStack: () -> Boolean,
    selectRoot: (String) -> Unit,
) {
    if (simpleTvActive) return
    if (!popBackStack()) selectRoot(Routes.CHANNELS)
}

internal fun performMainStartupBack(
    simpleTvActive: Boolean,
    requests: ApplianceLaunchRequests,
    expectedState: ApplianceLaunchState,
    selectRoot: (String) -> Unit,
) {
    when (applianceLaunchBackAction(simpleTvActive)) {
        ApplianceLaunchBackAction.CANCEL_REQUEST -> {
            cancelStartupAndSelectRoot(
                requests = requests,
                expectedState = expectedState,
                destination = Routes.CHANNELS,
                selectRoot = selectRoot,
            )
        }
        ApplianceLaunchBackAction.CONSUME_WITHOUT_CHANGE -> Unit
    }
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
    readyState.startSimpleTv -> DeferredResolvingBackAction.CONTAIN_SIMPLE_TV
    else -> DeferredResolvingBackAction.CANCEL_TO_CHANNELS
}

internal fun applyDeferredResolvingBack(
    action: DeferredResolvingBackAction,
    requests: ApplianceLaunchRequests,
    expectedState: ApplianceLaunchState,
    selectRoot: (String) -> Unit,
): Boolean = when (action) {
    DeferredResolvingBackAction.NONE,
    DeferredResolvingBackAction.CONTAIN_SIMPLE_TV -> false
    DeferredResolvingBackAction.CANCEL_TO_CHANNELS -> cancelStartupAndSelectRoot(
        requests = requests,
        expectedState = expectedState,
        destination = Routes.CHANNELS,
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
                simpleTvActive = false,
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
                navigationStartDestination = Routes.CHANNELS,
                navigationAllowed = true,
                contentAllowed = true,
            ),
            simpleTvActive = false,
            onBack = {},
            onAction = {},
            registerActivityKeyContract = registerActivityKeyContract,
            navigation = { _, _ -> OnboardingScreen() },
        )
        return
    }

    val nav = rememberNavController()
    var navigationStartDestination by rememberSaveable {
        mutableStateOf(
            when (val launchState = applianceLaunchState) {
                ApplianceLaunchState.Idle -> Routes.CHANNELS
                is ApplianceLaunchState.Pending -> if (
                    deferredBackAction ==
                    DeferredResolvingBackAction.CANCEL_TO_CHANNELS
                ) {
                    Routes.CHANNELS
                } else {
                    null
                }
                is ApplianceLaunchState.Entering -> if (
                    deferredBackAction ==
                    DeferredResolvingBackAction.CANCEL_TO_CHANNELS
                ) {
                    Routes.CHANNELS
                } else {
                    Routes.player(
                        channelId = launchState.target.channelId,
                        channelName = launchState.target.channelName,
                    )
                }
            },
        )
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
    val navigateTopLevel: (String) -> Unit = { route ->
        nav.navigate(route) {
            // Top-level drawer destinations are siblings, not a Back history.
            // Keeping one Compose destination also prevents NavHost from owning
            // Back ahead of the focus-driven TV shell.
            popUpTo(nav.graph.id) {
                inclusive = false
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    val recordingsScreenState = remember { RecordingsScreenState() }
    val context = LocalContext.current
    val activity = context as? Activity
    val focusManager = LocalFocusManager.current

    val appVm: AppConnectionViewModel = koinViewModel()
    val connectionUiState by appVm.uiState.collectAsStateWithLifecycle()
    val connectionState by appVm.connectionState.collectAsStateWithLifecycle()
    val lastPlayedChannelStore: LastPlayedChannelStore = koinInject()
    val playbackRuntime: AppPlaybackRuntime = koinInject()
    val playbackSelectionScope = rememberCoroutineScope()
    val playerSettingsStore: PlayerSettingsStore = koinInject()
    val playbackState by playbackRuntime.state.collectAsStateWithLifecycle()
    val activeTarget by playbackRuntime.activeTarget.collectAsStateWithLifecycle()
    val activeChannelId = (activeTarget as? AppPlaybackTarget.Live)?.channelId
    val activeRecordingId = (activeTarget as? AppPlaybackTarget.Recording)?.recordingId
    val playerSettings by playerSettingsStore.playerSettings.collectAsStateWithLifecycle(
        initialValue = PlayerSettings(audioLanguage = null, subtitleLanguage = null)
    )
    val uiSettingsStore: UiSettingsStore = koinInject()
    val uiSettings by uiSettingsStore.settings.collectAsStateWithLifecycle(initialValue = UiSettings())
    val simpleTvStore: SimpleTvSettingsStore = koinInject()
    val simpleTvSession: SimpleTvSession = koinInject()
    val simpleTvSettings by simpleTvStore.settings.collectAsStateWithLifecycle(
        initialValue = SimpleTvSettings()
    )
    val simpleTvActive by simpleTvSession.active.collectAsStateWithLifecycle()
    val capabilityProfile = simpleTvProfile(simpleTvSettings, simpleTvActive)
    val currentChannelReadiness by
        appVm.currentChannelReadiness.collectAsStateWithLifecycle()
    val startupPresentation = mainStartupPresentation(
        startupState = startupState,
        launchState = applianceLaunchState,
        connectionState = connectionUiState,
        currentChannelReadiness = currentChannelReadiness,
        simpleTvActive = simpleTvActive,
    )

    val backStackEntry by nav.currentBackStackEntryAsState()

    val currentRoute = backStackEntry?.destination?.route
    val topRoute = currentRoute?.substringBefore("/")
    val showRail = shouldShowMainNavigationRail(
        simpleTvActive = simpleTvActive,
        currentTopRoute = topRoute,
        navigationStartDestination = navigationStartDestination,
    )

    val isPlayer = topRoute == Routes.PLAYER || topRoute == Routes.RECORDING_PLAYER
    val enteringLaunchTarget =
        (applianceLaunchState as? ApplianceLaunchState.Entering)?.target
    val visibleLivePlayerChannelId = if (topRoute == Routes.PLAYER) {
        backStackEntry?.arguments?.getLong("channelId")?.let(::ChannelId)
    } else {
        null
    }
    val visibleLivePlayerName = if (topRoute == Routes.PLAYER) {
        backStackEntry?.arguments?.getString("channelName")
    } else {
        null
    }
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
                navigationStartDestination != null
        is ApplianceLaunchState.Pending -> false
        is ApplianceLaunchState.Entering -> {
            val exactStart = Routes.player(
                channelId = launchState.target.channelId,
                channelName = launchState.target.channelName,
            )
            enteringNavigationAllowed(
                hasBackStackEntry = nav.currentBackStackEntry != null,
                navigationStartDestination = navigationStartDestination,
                exactStartDestination = exactStart,
                matchingVisiblePlayer = matchingEnteringPlayerVisible,
            )
        }
    }
    val selectRoot = remember(nav) {
        { destination: String ->
            navigationStartDestination = destination
            if (nav.currentBackStackEntry != null) {
                replaceNavigationRoot(nav, destination)
            }
        }
    }

    // One-shot warm-player return: armed when a service/recording becomes active
    // or the user navigates deliberately while playback remains warm; consumed
    // before returning to the player so root Back cannot loop. Player Back alone
    // does not re-arm.
    var warmReturn by remember { mutableStateOf(WarmReturnOpportunity()) }
    val currentWarmTarget = warmPlaybackTarget(activeChannelId, activeRecordingId)
    LaunchedEffect(activeChannelId, activeRecordingId) {
        warmReturn = when {
            activeChannelId != null -> armWarmReturn(WarmPlaybackTarget.LIVE)
            activeRecordingId != null -> armWarmReturn(WarmPlaybackTarget.RECORDING)
            else -> clearWarmReturn()
        }
    }

    LaunchedEffect(playbackState, activeRecordingId, topRoute) {
        when (
            recordingFinishedAction(
                recordingFinished = playbackState is AppPlaybackState.Finished,
                activeRecordingId = activeRecordingId,
                recordingPlayerVisible = topRoute == Routes.RECORDING_PLAYER,
            )
        ) {
            RecordingFinishedAction.NONE -> Unit
            RecordingFinishedAction.STOP -> playbackRuntime.stop()
            RecordingFinishedAction.STOP_AND_CLOSE_PLAYER -> {
                playbackRuntime.stop()
                nav.popBackStack()
            }
        }
    }

    SimpleTvRouteGuardEffect(
        topRoute = topRoute,
        profile = capabilityProfile,
        recordingActive = activeRecordingId != null,
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
        val targetRoute = Routes.player(
            channelId = target.channelId,
            channelName = target.channelName,
        )
        if (nav.currentBackStackEntry == null && navigationStartDestination == null) {
            // The first NavHost mount for fresh autoplay starts at the exact player.
            navigationStartDestination = targetRoute
        } else if (!target.matchesPlayerEntry(nav.currentBackStackEntry)) {
            // Startup entry owns a root replacement. Clearing the prior graph
            // prevents browse destinations from composing during the handoff.
            replaceNavigationRoot(nav, targetRoute)
        }
    }

    val startupBack = remember(applianceLaunchState, simpleTvActive, selectRoot) {
        val expectedState = applianceLaunchState
        {
            performMainStartupBack(
                simpleTvActive = simpleTvActive,
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
                isStartDestination = currentRoute == Routes.CHANNELS,
                warmReturn = warmReturn,
            )
        ) {
            BackAction.FINISH_ACTIVITY -> {
                if (capabilityProfile.allows(SimpleTvCapability.APP_EXIT)) {
                    activity?.finish()
                }
                // Simple TV never exits through Back; ignore when exit is gated.
            }
            BackAction.POP_NAVIGATION -> {
                if (!nav.popBackStack()) activity?.finish()
            }
            BackAction.RETURN_TO_PARENT -> Unit
            BackAction.RETURN_TO_PLAYER -> {
                // Consume before navigation so player→browse→Back cannot loop.
                val target = warmReturn.target
                warmReturn = consumeWarmReturn(warmReturn)
                when (target) {
                    WarmPlaybackTarget.LIVE -> {
                        val channelId = activeChannelId ?: return@rootBack
                        val playerTarget = warmLivePlayerTarget(
                            activeChannelId = channelId,
                            readiness = currentChannelReadiness,
                        )
                        nav.navigate(
                            Routes.player(
                                channelId = playerTarget.channelId,
                                channelName = playerTarget.channelName,
                            )
                        ) {
                            launchSingleTop = true
                        }
                    }
                    WarmPlaybackTarget.RECORDING -> {
                        val recordingId = activeRecordingId ?: return@rootBack
                        nav.navigate(Routes.recordingPlayer(recordingId)) {
                            launchSingleTop = true
                        }
                    }
                    WarmPlaybackTarget.NONE -> Unit
                }
            }
        }
    }
    BackHandler(
        enabled = navigationAllowed && !applianceLaunchActive && !showRail,
        onBack = handleRootBack,
    )
    val content: @Composable (PaddingValues, Boolean, String, Boolean) -> Unit = {
            contentPadding, drawerActive, startDestination, contentAllowed ->
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = nav,
                    startDestination = startDestination,
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
                    composable(Routes.CHANNELS) {
                        StartupGatedChannelsContent(contentAllowed = contentAllowed) {
                            ContentContainer {
                                ChannelsScreen(
                                    contentPadding = contentPadding,
                                    initialFocusEnabled = !drawerActive,
                                    playingChannelId = activeChannelId,
                                    connectionUiState = connectionUiState,
                                    onRetryConnection = appVm::reconnectNow,
                                    onOpenConnectionSettings = {
                                        navigateTopLevel(Routes.SETTINGS)
                                    },
                                    onPlay = { playbackSelection, name ->
                                        val channelId = playbackSelection.channelId
                                        warmReturn = rearmWarmReturnForPlaybackSelection(
                                            current = warmReturn,
                                            currentWarmTarget = currentWarmTarget,
                                            requestedTarget = WarmPlaybackTarget.LIVE,
                                            currentIdentity = activeChannelId,
                                            requestedIdentity = channelId,
                                        )
                                        playbackSelectionScope.launch {
                                            playbackRuntime.playLive(playbackSelection)
                                            nav.navigate(Routes.player(channelId, name))
                                        }
                                    }
                                )
                            }
                        }
                    }

                    composable(Routes.EPG) {
                        if (contentAllowed && capabilityProfile.allowsRoute(SimpleTvRoute.EPG)) {
                            ContentContainer {
                                    EpgGridScreen(
                                        contentPadding = contentPadding,
                                        initialFocusEnabled = !drawerActive,
                                        connectionUiState = connectionUiState,
                                        onRetry = appVm::reconnectNow,
                                        onOpenConnectionSettings = {
                                            navigateTopLevel(Routes.SETTINGS)
                                        },
                                        onClearCategory = {},
                                        simpleTvProfile = capabilityProfile,
                                        onPlayRecording = { playbackSelection ->
                                            val recordingId = playbackSelection.recordingId
                                            warmReturn = rearmWarmReturnForPlaybackSelection(
                                                current = warmReturn,
                                                currentWarmTarget = currentWarmTarget,
                                                requestedTarget = WarmPlaybackTarget.RECORDING,
                                                currentIdentity = activeRecordingId,
                                                requestedIdentity = recordingId,
                                            )
                                            playbackSelectionScope.launch {
                                                playbackRuntime.playRecording(
                                                    playbackSelection,
                                                    RecordingPlaybackStart.START_OVER,
                                                )
                                                nav.navigate(Routes.recordingPlayer(recordingId))
                                            }
                                        },
                                        onPlay = { playbackSelection, name ->
                                            val channelId = playbackSelection.channelId
                                            warmReturn = rearmWarmReturnForPlaybackSelection(
                                                current = warmReturn,
                                                currentWarmTarget = currentWarmTarget,
                                                requestedTarget = WarmPlaybackTarget.LIVE,
                                                currentIdentity = activeChannelId,
                                                requestedIdentity = channelId,
                                            )
                                            playbackSelectionScope.launch {
                                                playbackRuntime.playLive(playbackSelection)
                                                nav.navigate(Routes.player(channelId, name))
                                            }
                                        }
                                    )
                            }
                        }
                    }

                    composable(
                        route = "${Routes.EPG}/{category}",
                        arguments = listOf(
                            navArgument("category") { type = NavType.StringType },
                        ),
                    ) { entry ->
                        if (contentAllowed && capabilityProfile.allowsRoute(SimpleTvRoute.EPG)) {
                            val category = programmeCategoryFromRoute(
                                entry.arguments?.getString("category")
                            )
                            ContentContainer {
                                    EpgGridScreen(
                                        contentPadding = contentPadding,
                                        initialFocusEnabled = !drawerActive,
                                        category = category,
                                        connectionUiState = connectionUiState,
                                        onRetry = appVm::reconnectNow,
                                        onOpenConnectionSettings = {
                                            navigateTopLevel(Routes.SETTINGS)
                                        },
                                        onClearCategory = {
                                            navigateTopLevel(Routes.EPG)
                                        },
                                        simpleTvProfile = capabilityProfile,
                                        onPlayRecording = { playbackSelection ->
                                            val recordingId = playbackSelection.recordingId
                                            warmReturn = rearmWarmReturnForPlaybackSelection(
                                                current = warmReturn,
                                                currentWarmTarget = currentWarmTarget,
                                                requestedTarget = WarmPlaybackTarget.RECORDING,
                                                currentIdentity = activeRecordingId,
                                                requestedIdentity = recordingId,
                                            )
                                            playbackSelectionScope.launch {
                                                playbackRuntime.playRecording(
                                                    playbackSelection,
                                                    RecordingPlaybackStart.START_OVER,
                                                )
                                                nav.navigate(Routes.recordingPlayer(recordingId))
                                            }
                                        },
                                        onPlay = { playbackSelection, name ->
                                            val channelId = playbackSelection.channelId
                                            warmReturn = rearmWarmReturnForPlaybackSelection(
                                                current = warmReturn,
                                                currentWarmTarget = currentWarmTarget,
                                                requestedTarget = WarmPlaybackTarget.LIVE,
                                                currentIdentity = activeChannelId,
                                                requestedIdentity = channelId,
                                            )
                                            playbackSelectionScope.launch {
                                                playbackRuntime.playLive(playbackSelection)
                                                nav.navigate(Routes.player(channelId, name))
                                            }
                                        },
                                    )
                            }
                        }
                    }

                    composable(Routes.RECORDINGS) {
                        if (contentAllowed && capabilityProfile.allowsRoute(SimpleTvRoute.RECORDINGS)) {
                            ContentContainer {
                                    RecordingsScreen(
                                        contentPadding = contentPadding,
                                        initialFocusEnabled = !drawerActive,
                                        backEnabled = !applianceLaunchActive,
                                        connectionUiState = connectionUiState,
                                        onRetry = appVm::reconnectNow,
                                        onPlayRecording = { playbackSelection, intent ->
                                            val recordingId = playbackSelection.recordingId
                                            warmReturn = rearmWarmReturnForPlaybackSelection(
                                                current = warmReturn,
                                                currentWarmTarget = currentWarmTarget,
                                                requestedTarget = WarmPlaybackTarget.RECORDING,
                                                currentIdentity = activeRecordingId,
                                                requestedIdentity = recordingId,
                                            )
                                            playbackSelectionScope.launch {
                                                playbackRuntime.playRecording(
                                                    playbackSelection,
                                                    intent,
                                                )
                                                nav.navigate(
                                                    Routes.recordingPlayer(recordingId, intent)
                                                )
                                            }
                                        },
                                        state = recordingsScreenState,
                                    )
                            }
                        }
                    }

                    composable(Routes.SETTINGS) {
                        if (contentAllowed && capabilityProfile.allowsRoute(SimpleTvRoute.SETTINGS)) {
                            ContentContainer {
                                    SettingsScreen(
                                        startRoute = if (
                                            navigationStartDestination == Routes.SETTINGS
                                        ) {
                                            SettingsRoutes.CONNECTION
                                        } else {
                                            SettingsRoutes.GENERAL
                                        },
                                        initialFocusEnabled = !drawerActive,
                                        contentPadding = contentPadding,
                                        backEnabled = !applianceLaunchActive,
                                        onStartSimpleTv = {
                                            simpleTvSession.start()
                                        },
                                    )
                            }
                        }
                    }

                    composable(Routes.UNLOCK) {
                        if (contentAllowed) ContentContainer {
                                SimpleTvUnlockScreen(
                                    backEnabled =
                                        !drawerActive && !applianceLaunchActive,
                                    onExited = {
                                        navigateTopLevel(Routes.CHANNELS)
                                    },
                                    onBack = {
                                        nav.popBackStack()
                                        if (simpleTvActive && activeChannelId == null) {
                                            applianceLaunchRequests.request()
                                        }
                                    },
                                )
                        }
                    }

                    composable(
                        route = "${Routes.PLAYER}/{channelId}/{channelName}",
                        arguments = listOf(
                            navArgument("channelId") { type = NavType.LongType },
                            navArgument("channelName") { type = NavType.StringType },
                        )
                    ) { entry ->
                        val channelId = ChannelId(entry.arguments?.getLong("channelId") ?: 0L)
                        val channelName = entry.arguments?.getString("channelName") ?: ""

                        StartupGatedPlayerContent(contentAllowed = contentAllowed) {
                            VideoPlayerScreen(
                                channelId = channelId,
                                channelName = channelName,
                                simpleTvProfile = capabilityProfile,
                                onReconnect = appVm::reconnectNow,
                                onUnlock = { nav.navigate(Routes.UNLOCK) },
                                onClose = {
                                    closeNormalLivePlayer(
                                        simpleTvActive = simpleTvActive,
                                        popBackStack = nav::popBackStack,
                                        selectRoot = selectRoot,
                                    )
                                },
                            )
                        }
                    }

                    composable(
                        route = "${Routes.RECORDING_PLAYER}/{recordingId}/{startMode}",
                        arguments = listOf(
                            navArgument("recordingId") { type = NavType.LongType },
                            navArgument("startMode") { type = NavType.StringType },
                        ),
                    ) { entry ->
                        val recordingId = DvrEntryId(
                            entry.arguments?.getLong("recordingId") ?: 0L
                        )
                        val start = when (entry.arguments?.getString("startMode")) {
                            "beginning" -> RecordingPlaybackStart.START_OVER
                            else -> RecordingPlaybackStart.RESUME
                        }
                        if (
                            contentAllowed &&
                            capabilityProfile.allowsRoute(SimpleTvRoute.RECORDING_PLAYER)
                        ) {
                            RecordingPlayerScreen(
                                recordingId = recordingId,
                                playbackStart = start,
                                simpleTvProfile = capabilityProfile,
                                connectionAvailable = connectionState is ConnectionState.Connected,
                                onReconnect = appVm::reconnectNow,
                                onUnlock = { nav.navigate(Routes.UNLOCK) },
                                onClose = { nav.popBackStack() },
                            )
                        }
                    }
                }

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
                        destination = Routes.SETTINGS,
                        selectRoot = selectRoot,
                    )
                    Unit
                },
                onExitSimpleTv = {
                    cancelStartupAndSelectRoot(
                        requests = applianceLaunchRequests,
                        expectedState = expectedState,
                        destination = Routes.UNLOCK,
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
        simpleTvActive = simpleTvActive,
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
        navigation = { startDestination, contentAllowed ->
            MainNavigationShell(
                showRail = showRail,
                rail = {
                    SideRail(
                        currentRoute = topRoute,
                        rootRoute = Routes.CHANNELS,
                        showEpgMenu = uiSettings.showEpgMenu,
                        simpleTvProfile = capabilityProfile,
                        rootBackPriority = applianceLaunchActive,
                        onRootBack = handleRootBack,
                        onNavigate = { route ->
                            val current = nav.currentBackStackEntry?.destination?.route
                            if (current == route) {
                                focusManager.moveFocus(FocusDirection.Right)
                            } else {
                                // Deliberate rail navigation re-arms one warm return while
                                // playback remains active. Returning from the player via
                                // Back does not go through this path and must not re-arm.
                                if (currentWarmTarget != WarmPlaybackTarget.NONE) {
                                    warmReturn = rearmWarmReturn(currentWarmTarget)
                                }
                                if (route == Routes.UNLOCK) {
                                    nav.navigate(route) { launchSingleTop = true }
                                } else {
                                    navigateTopLevel(route)
                                }
                            }
                        },
                        content = { contentPadding, drawerActive ->
                            content(contentPadding, drawerActive, startDestination, contentAllowed)
                        },
                    )
                },
                fullScreen = {
                    content(TvFullScreenPadding, false, startDestination, contentAllowed)
                },
            )
        },
    )
}

private fun ApplianceLaunchTarget.matchesPlayerEntry(entry: NavBackStackEntry?): Boolean {
    if (entry?.destination?.route?.substringBefore("/") != Routes.PLAYER) return false
    val arguments = entry.arguments ?: return false
    val channelName = arguments.getString("channelName") ?: return false
    return matchesPlayer(
        channelId = ChannelId(arguments.getLong("channelId")),
        channelName = channelName,
    )
}

private fun replaceNavigationRoot(
    nav: NavHostController,
    destination: String,
) {
    nav.navigate(destination) {
        popUpTo(nav.graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
internal fun SimpleTvRouteGuardEffect(
    topRoute: String?,
    profile: SimpleTvProfile,
    recordingActive: Boolean,
    stopRecording: suspend () -> Unit,
    redirectToLive: () -> Unit,
) {
    LaunchedEffect(topRoute, profile.active) {
        val route = topRoute?.substringBefore("/").toSimpleTvRoute()
            ?: return@LaunchedEffect
        when (simpleTvRouteGuardAction(profile, route, recordingActive)) {
            SimpleTvRouteGuardAction.ALLOW -> Unit
            SimpleTvRouteGuardAction.REDIRECT_TO_LIVE -> redirectToLive()
            SimpleTvRouteGuardAction.STOP_RECORDING_AND_REDIRECT_TO_LIVE -> {
                stopRecording()
                redirectToLive()
            }
        }
    }
}

private fun String?.toSimpleTvRoute(): SimpleTvRoute? = when (this) {
    Routes.CHANNELS -> SimpleTvRoute.CHANNELS
    Routes.EPG -> SimpleTvRoute.EPG
    Routes.RECORDINGS -> SimpleTvRoute.RECORDINGS
    Routes.SETTINGS -> SimpleTvRoute.SETTINGS
    Routes.PLAYER -> SimpleTvRoute.PLAYER
    Routes.RECORDING_PLAYER -> SimpleTvRoute.RECORDING_PLAYER
    Routes.UNLOCK -> SimpleTvRoute.UNLOCK
    else -> null
}
