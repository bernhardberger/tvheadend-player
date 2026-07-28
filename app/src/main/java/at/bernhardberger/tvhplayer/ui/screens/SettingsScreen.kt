package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import at.bernhardberger.tvhplayer.ui.components.SettingsSubRail
import at.bernhardberger.tvhplayer.ui.TvFullScreenPadding
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.core.SettingsBackAction
import at.bernhardberger.tvhplayer.core.settingsBackAction
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
    initialFocusEnabled: Boolean = true,
    contentPadding: PaddingValues = TvFullScreenPadding,
    backEnabled: Boolean = true,
    onStartSimpleTv: () -> Unit,
) {
    val nav = rememberNavController()
    val settingsRoutes = remember {
        listOf(
            SettingsRoutes.GENERAL,
            SettingsRoutes.OPTIONS,
            SettingsRoutes.CHANNEL_TAGS,
            SettingsRoutes.CONNECTION,
            SettingsRoutes.PLAYER,
            SettingsRoutes.APPLIANCE,
            SettingsRoutes.SIMPLE_TV,
        )
    }
    val categoryFocus = remember(settingsRoutes) {
        settingsRoutes.associateWith { FocusRequester() }
    }
    val contentFocus = remember(settingsRoutes) {
        settingsRoutes.associateWith { FocusRequester() }
    }
    var contentPaneFocused by remember { mutableStateOf(false) }
    val simpleTvSession: SimpleTvSession = koinInject()
    val simpleTvActive by simpleTvSession.active.collectAsStateWithLifecycle()
    val showSimpleTvSettings = !simpleTvActive

    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val backAction = settingsBackAction(
        contentPaneFocused = contentPaneFocused,
    )
    val focusCurrentCategory: () -> Unit = {
        (categoryFocus[currentRoute]
            ?: categoryFocus[SettingsRoutes.GENERAL])?.requestFocus()
    }
    BackHandler(
        enabled = backEnabled && backAction == SettingsBackAction.FOCUS_CURRENT_CATEGORY,
    ) {
        focusCurrentCategory()
    }
    LaunchedEffect(currentRoute, showSimpleTvSettings) {
        if (currentRoute == SettingsRoutes.SIMPLE_TV && !showSimpleTvSettings) {
            nav.navigate(SettingsRoutes.GENERAL) {
                popUpTo(nav.graph.id) {
                    inclusive = false
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
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
                // The shell owns the global rail; Settings owns its inner panel spacing.
                .padding(contentPadding)
        ) {
            SettingsSubRail(
                currentRoute = currentRoute,
                categoryFocusRequesters = categoryFocus,
                contentFocusRequesters = contentFocus,
                initialFocusEnabled = initialFocusEnabled,
                showSimpleTv = showSimpleTvSettings,
                onNavigate = { route ->
                    nav.navigate(route) {
                        popUpTo(nav.graph.id) {
                            inclusive = false
                            saveState = true
                        }
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
                    .onFocusChanged { contentPaneFocused = it.hasFocus }
                    .onKeyEvent { event ->
                        if (
                            event.key != Key.Back ||
                            event.type != KeyEventType.KeyUp ||
                            !backEnabled ||
                            backAction != SettingsBackAction.FOCUS_CURRENT_CATEGORY
                        ) {
                            return@onKeyEvent false
                        }
                        focusCurrentCategory()
                        true
                    }
                    .focusGroup()
            ) {
                NavHost(
                    navController = nav,
                    startDestination = SettingsRoutes.GENERAL,
                ) {
                    composable(SettingsRoutes.GENERAL) {
                        SettingsLanguage(contentFocus.getValue(SettingsRoutes.GENERAL))
                    }

                    composable(SettingsRoutes.CONNECTION) {
                        SettingsConnection(contentFocus.getValue(SettingsRoutes.CONNECTION))
                    }

                    composable(SettingsRoutes.OPTIONS) {
                        SettingsOptions(contentFocus.getValue(SettingsRoutes.OPTIONS))
                    }

                    composable(SettingsRoutes.CHANNEL_TAGS) {
                        SettingsChannelTags(contentFocus.getValue(SettingsRoutes.CHANNEL_TAGS))
                    }

                    composable(SettingsRoutes.PLAYER) {
                        SettingsPlayer(contentFocus.getValue(SettingsRoutes.PLAYER))
                    }

                    composable(SettingsRoutes.APPLIANCE) {
                        SettingsAppliance(contentFocus.getValue(SettingsRoutes.APPLIANCE))
                    }

                    composable(SettingsRoutes.SIMPLE_TV) {
                        SettingsSimpleTv(
                            initialFocusRequester = contentFocus.getValue(
                                SettingsRoutes.SIMPLE_TV
                            ),
                            onStartSimpleTv = onStartSimpleTv,
                        )
                    }
                }
            }
        }
    }
}
