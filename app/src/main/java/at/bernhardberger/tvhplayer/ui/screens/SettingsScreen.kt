package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import at.bernhardberger.tvhplayer.core.BackAction
import at.bernhardberger.tvhplayer.core.nestedBackAction
import at.bernhardberger.tvhplayer.ui.components.SettingsSubRail
import at.bernhardberger.tvhplayer.ui.TvFullScreenPadding
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.screens.settings.SettingsAppliance
import at.bernhardberger.tvhplayer.ui.screens.settings.SettingsConnection
import at.bernhardberger.tvhplayer.ui.screens.settings.SettingsChannelTags
import at.bernhardberger.tvhplayer.ui.screens.settings.SettingsLanguage
import at.bernhardberger.tvhplayer.ui.screens.settings.SettingsOptions
import at.bernhardberger.tvhplayer.ui.screens.settings.SettingsPlayer
import at.bernhardberger.tvhplayer.ui.screens.settings.SettingsSimpleTv
import at.bernhardberger.tvhplayer.stores.SimpleTvSession
import org.koin.compose.koinInject

object SettingsRoutes {
    const val GENERAL = "settings/general"
    const val PLAYER = "settings/player"
    const val OPTIONS = "settings/options"
    const val CHANNEL_TAGS = "settings/channel-tags"
    const val CONNECTION = "settings/connection"
    const val APPLIANCE = "settings/appliance"
    const val SIMPLE_TV = "settings/simple-tv"
    const val ABOUT = "settings/about"
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onStartSimpleTv: () -> Unit,
) {
    val nav = rememberNavController()
    val simpleTvSession: SimpleTvSession = koinInject()
    val simpleTvActive by simpleTvSession.active.collectAsStateWithLifecycle()
    val showSimpleTvSettings = !simpleTvActive

    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(currentRoute, showSimpleTvSettings) {
        if (currentRoute == SettingsRoutes.SIMPLE_TV && !showSimpleTvSettings) {
            nav.navigate(SettingsRoutes.GENERAL) {
                popUpTo(SettingsRoutes.GENERAL) { inclusive = true }
            }
        }
    }
    BackHandler {
        when (nestedBackAction(hasPreviousEntry = nav.previousBackStackEntry != null)) {
            BackAction.POP_NAVIGATION -> nav.popBackStack()
            BackAction.RETURN_TO_PARENT -> onBack()
            BackAction.RETURN_TO_PLAYER -> Unit
            BackAction.FINISH_ACTIVITY -> Unit
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                // Settings drops the global rail; use symmetric full-screen safe margins.
                .padding(TvFullScreenPadding)
        ) {
            SettingsSubRail(
                currentRoute = currentRoute,
                showSimpleTv = showSimpleTvSettings,
                onNavigate = { route ->
                    nav.navigate(route) {
                        popUpTo(SettingsRoutes.GENERAL) { inclusive = false }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )

            Spacer(Modifier.width(32.dp))

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                NavHost(
                    navController = nav,
                    startDestination = SettingsRoutes.GENERAL,
                ) {
                    composable(SettingsRoutes.GENERAL) {
                        SettingsLanguage()
                    }

                    composable(SettingsRoutes.CONNECTION) {
                        SettingsConnection()
                    }

                    composable(SettingsRoutes.OPTIONS) {
                        SettingsOptions()
                    }

                    composable(SettingsRoutes.CHANNEL_TAGS) {
                        SettingsChannelTags()
                    }

                    composable(SettingsRoutes.PLAYER) {
                        SettingsPlayer()
                    }

                    composable(SettingsRoutes.APPLIANCE) {
                        SettingsAppliance()
                    }

                    composable(SettingsRoutes.SIMPLE_TV) {
                        SettingsSimpleTv(onStartSimpleTv = onStartSimpleTv)
                    }
                }
            }
        }
    }
}
