package at.bernhardberger.tvhplayer.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.BackAction
import at.bernhardberger.tvhplayer.core.rootBackAction
import at.bernhardberger.tvhplayer.htsp.ConnectionState
import at.bernhardberger.tvhplayer.player.PlaybackSessionState
import at.bernhardberger.tvhplayer.player.PlayerSession
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.ServerSettingsStore
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.components.ContentContainer
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import at.bernhardberger.tvhplayer.ui.player.VideoPlayerScreen
import at.bernhardberger.tvhplayer.ui.player.PlayerVideoSurface
import at.bernhardberger.tvhplayer.ui.screens.ChannelsScreen
import at.bernhardberger.tvhplayer.ui.screens.EpgGridScreen
import at.bernhardberger.tvhplayer.ui.screens.OnboardingScreen
import at.bernhardberger.tvhplayer.ui.screens.RecordingsScreen
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreen
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

object Routes {
    const val CHANNELS = "channels"
    const val EPG = "epg"
    const val RECORDINGS = "recordings"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    fun player(channelId: Int, serviceId: Int, channelName: String) =
        "player/$channelId/$serviceId/${android.net.Uri.encode(channelName)}"
}

@Composable
fun AppRoot(
    applianceLaunchRequests: ApplianceLaunchRequests,
    onPlayerVisibilityChanged: (Boolean) -> Unit,
) {
    val serverSettingsStore: ServerSettingsStore = koinInject()
    var serverSettings by remember { mutableStateOf<ServerSettings?>(null) }
    LaunchedEffect(serverSettingsStore) {
        serverSettingsStore.serverSettings.collect { serverSettings = it }
    }
    val currentServerSettings = serverSettings
    if (currentServerSettings == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.tv.material3.MaterialTheme.colorScheme.background)
        )
        return
    }
    if (currentServerSettings.host.isBlank()) {
        OnboardingScreen()
        return
    }

    val nav = rememberNavController()
    val context = LocalContext.current
    val activity = context as? Activity
    val focusManager = LocalFocusManager.current

    val appVm: AppConnectionViewModel = koinViewModel()
    val connectionUiState by appVm.uiState.collectAsStateWithLifecycle()
    val connectionState by appVm.connectionState.collectAsStateWithLifecycle()
    val channelsVm: ChannelsViewModel = koinViewModel()
    val lastPlayedChannelStore: LastPlayedChannelStore = koinInject()
    val playerSession: PlayerSession = koinInject()
    val playerSettingsStore: PlayerSettingsStore = koinInject()
    val playbackState by playerSession.state.collectAsStateWithLifecycle()
    val activeServiceId by playerSession.activeServiceId.collectAsStateWithLifecycle()
    val playerSettings by playerSettingsStore.playerSettings.collectAsStateWithLifecycle(
        initialValue = PlayerSettings(profile = "", audioLanguage = null, subtitleLanguage = null)
    )
    val uiSettingsStore: UiSettingsStore = koinInject()
    val uiSettings by uiSettingsStore.settings.collectAsStateWithLifecycle(initialValue = UiSettings())
    val applianceLaunchRequest by applianceLaunchRequests.pending.collectAsStateWithLifecycle()

    val backStackEntry by nav.currentBackStackEntryAsState()

    val currentRoute = backStackEntry?.destination?.route
    val topRoute = currentRoute?.substringBefore("/")
    val showRail = topRoute != Routes.PLAYER

    val isPlayer = currentRoute?.startsWith(Routes.PLAYER) == true

    LaunchedEffect(isPlayer) {
        onPlayerVisibilityChanged(isPlayer)
    }

    LaunchedEffect(applianceLaunchRequest) {
        if (applianceLaunchRequest == null) return@LaunchedEffect

        val persistedId = lastPlayedChannelStore.channelId.first()
        val channels = channelsVm.allChannels.filter { it.isNotEmpty() }.first()
        val target = applianceLaunchRequests.resolve(channels.map { it.id }, persistedId)
            ?: return@LaunchedEffect
        val channel = channels.firstOrNull { it.id == target.channelId }
            ?: return@LaunchedEffect

        if (applianceLaunchRequests.consume(target.request)) {
            nav.navigate(Routes.player(channel.id, channel.id, channel.name))
        }
    }

    BackHandler {
        val pendingRequest = applianceLaunchRequest
        if (pendingRequest != null) {
            applianceLaunchRequests.cancel(pendingRequest)
            return@BackHandler
        }

        when (rootBackAction(
            isStartDestination = currentRoute == Routes.CHANNELS,
            hasActivePlayback = activeServiceId != null,
        )) {
            BackAction.FINISH_ACTIVITY -> activity?.finish()
            BackAction.POP_NAVIGATION -> {
                if (!nav.popBackStack()) activity?.finish()
            }
            BackAction.RETURN_TO_PARENT -> Unit
            BackAction.RETURN_TO_PLAYER -> {
                val serviceId = activeServiceId ?: return@BackHandler
                val channel = channelsVm.allChannels.value.firstOrNull { it.id == serviceId }
                nav.navigate(
                    Routes.player(
                        channelId = channel?.id ?: serviceId,
                        serviceId = serviceId,
                        channelName = channel?.name.orEmpty(),
                    )
                ) {
                    launchSingleTop = true
                }
            }
        }
    }
    val content: @Composable () -> Unit = {
            Box(
                Modifier.fillMaxSize()
            ) {
                NavHost(
                    navController = nav,
                    startDestination = Routes.CHANNELS,
                ) {

                    composable(Routes.CHANNELS) {
                        ContentContainer {
                            ChannelsScreen(
                                connectionUiState = connectionUiState,
                                onRetryConnection = appVm::reconnectNow,
                                onOpenConnectionSettings = {
                                    nav.navigate(Routes.SETTINGS) { launchSingleTop = true }
                                },
                                onPlay = { channelId, serviceId, name ->
                                    nav.navigate(Routes.player(channelId, serviceId, name))
                                }
                            )
                        }
                    }

                    composable(Routes.EPG) {
                        ContentContainer {
                            EpgGridScreen(
                                connectionUiState = connectionUiState,
                                onRetry = appVm::reconnectNow,
                                onPlay = { channelId, serviceId, name ->
                                    nav.navigate(Routes.player(channelId, serviceId, name))
                                }
                            )
                        }
                    }

                    composable(Routes.RECORDINGS) {
                        ContentContainer {
                            RecordingsScreen(
                                connectionUiState = connectionUiState,
                                onRetry = appVm::reconnectNow,
                            )
                        }
                    }

                    composable(Routes.SETTINGS) {
                        ContentContainer {
                            SettingsScreen(onBack = { nav.popBackStack() })
                        }
                    }

                    composable(
                        route = "${Routes.PLAYER}/{channelId}/{serviceId}/{channelName}",
                        arguments = listOf(
                            navArgument("channelId") { type = NavType.IntType },
                            navArgument("serviceId") { type = NavType.IntType },
                            navArgument("channelName") { type = NavType.StringType },
                        )
                    ) { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getInt("channelId") ?: 0
                        val serviceId = backStackEntry.arguments?.getInt("serviceId") ?: 0
                        val channelName = backStackEntry.arguments?.getString("channelName") ?: ""

                        VideoPlayerScreen(
                            channelId = channelId,
                            channelName = channelName,
                            serviceId = serviceId,
                            onClose = { nav.popBackStack() }
                        )
                    }
                }

                TvRecoveryOverlay(
                    visible = applianceLaunchRequest != null && !isPlayer,
                    message = stringResource(
                        if (connectionState is ConnectionState.Connected) {
                            R.string.appliance_starting_tv
                        } else {
                            R.string.appliance_connection_recovering
                        }
                    ),
                    hint = stringResource(R.string.appliance_back_for_menu),
                )
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.tv.material3.MaterialTheme.colorScheme.background)
    ) {
        if (playbackState !is PlaybackSessionState.Idle) {
            PlayerVideoSurface(
                player = playerSession.getOrCreatePlayer(context),
                aspectRatio = playerSettings.aspectRatio,
                modifier = Modifier.fillMaxSize(),
            )
            if (showRail) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = TvNavigationScrimAlpha))
                )
            }
        }

        if (showRail) {
            SideRail(
                currentRoute = topRoute,
                showEpgMenu = uiSettings.showEpgMenu,
                onNavigate = { route ->
                    val current = nav.currentBackStackEntry?.destination?.route
                    if (current == route) {
                        focusManager.moveFocus(FocusDirection.Right)
                    } else {
                        nav.navigate(route) {
                            popUpTo(Routes.CHANNELS) { inclusive = false }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                content = content,
            )
        } else {
            content()
        }
    }
}
