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
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import at.bernhardberger.tvhplayer.ui.components.SettingsSubRail
import at.bernhardberger.tvhplayer.ui.appDestinationEnterTransition
import at.bernhardberger.tvhplayer.ui.appDestinationExitTransition
import at.bernhardberger.tvhplayer.ui.TvFullScreenPadding
import at.bernhardberger.tvhplayer.ui.TvSpacing32
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
    startRoute: String = SettingsRoutes.GENERAL,
    initialFocusEnabled: Boolean = true,
    contentPadding: PaddingValues = TvFullScreenPadding,
    backEnabled: Boolean = true,
    onStartSimpleTv: () -> Unit,
) {
    val simpleTvSession: SimpleTvSession = koinInject()
    val simpleTvActive by simpleTvSession.active.collectAsStateWithLifecycle()
    SettingsScreenNavigation(
        startRoute = startRoute,
        initialFocusEnabled = initialFocusEnabled,
        contentPadding = contentPadding,
        backEnabled = backEnabled,
        showSimpleTvSettings = !simpleTvActive,
    ) { route, initialFocusRequester ->
        when (route) {
            SettingsRoutes.GENERAL -> SettingsLanguage(initialFocusRequester)
            SettingsRoutes.CONNECTION -> SettingsConnection(initialFocusRequester)
            SettingsRoutes.OPTIONS -> SettingsOptions(initialFocusRequester)
            SettingsRoutes.CHANNEL_TAGS -> SettingsChannelTags(initialFocusRequester)
            SettingsRoutes.PLAYER -> SettingsPlayer(initialFocusRequester)
            SettingsRoutes.APPLIANCE -> SettingsAppliance(initialFocusRequester)
            SettingsRoutes.SIMPLE_TV -> SettingsSimpleTv(
                initialFocusRequester = initialFocusRequester,
                onStartSimpleTv = onStartSimpleTv,
            )
        }
    }
}

@Composable
internal fun SettingsScreenNavigation(
    startRoute: String,
    initialFocusEnabled: Boolean = true,
    contentPadding: PaddingValues = TvFullScreenPadding,
    backEnabled: Boolean = true,
    showSimpleTvSettings: Boolean,
    destinationContent: @Composable (String, FocusRequester) -> Unit,
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
    val resolvedStartRoute = startRoute.takeIf { route ->
        route in settingsRoutes &&
            (route != SettingsRoutes.SIMPLE_TV || showSimpleTvSettings)
    } ?: SettingsRoutes.GENERAL
    val categoryFocus = remember(settingsRoutes) {
        settingsRoutes.associateWith { FocusRequester() }
    }
    val contentFocus = remember(settingsRoutes) {
        settingsRoutes.associateWith { FocusRequester() }
    }
    var contentPaneFocused by remember { mutableStateOf(false) }
    var initialCategoryFocusHandled by remember { mutableStateOf(false) }

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
    LaunchedEffect(initialFocusEnabled, currentRoute) {
        if (!initialFocusEnabled) {
            initialCategoryFocusHandled = false
            return@LaunchedEffect
        }
        if (!initialCategoryFocusHandled && currentRoute != null) {
            categoryFocus.getValue(currentRoute).requestFocus()
            initialCategoryFocusHandled = true
        }
    }
    LaunchedEffect(currentRoute, showSimpleTvSettings) {
        if (currentRoute == SettingsRoutes.SIMPLE_TV && !showSimpleTvSettings) {
            val restoreContentFocus = contentPaneFocused
            nav.navigate(SettingsRoutes.GENERAL) {
                popUpTo(nav.graph.id) {
                    inclusive = false
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            withFrameNanos { }
            val requester = if (restoreContentFocus) {
                contentFocus.getValue(SettingsRoutes.GENERAL)
            } else {
                categoryFocus.getValue(SettingsRoutes.GENERAL)
            }
            runCatching { requester.requestFocus() }
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
                // Settings owns route-aware entry so a direct Connection start
                // never briefly focuses General while NavHost initializes.
                initialFocusEnabled = false,
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
                },
            )

            Spacer(Modifier.width(TvSpacing32))

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onFocusChanged { contentPaneFocused = it.hasFocus }
                    .focusGroup()
            ) {
                NavHost(
                    navController = nav,
                    startDestination = resolvedStartRoute,
                    enterTransition = { appDestinationEnterTransition() },
                    exitTransition = { appDestinationExitTransition() },
                    popEnterTransition = { appDestinationEnterTransition() },
                    popExitTransition = { appDestinationExitTransition() },
                ) {
                    composable(SettingsRoutes.GENERAL) {
                        destinationContent(
                            SettingsRoutes.GENERAL,
                            contentFocus.getValue(SettingsRoutes.GENERAL),
                        )
                    }

                    composable(SettingsRoutes.CONNECTION) {
                        destinationContent(
                            SettingsRoutes.CONNECTION,
                            contentFocus.getValue(SettingsRoutes.CONNECTION),
                        )
                    }

                    composable(SettingsRoutes.OPTIONS) {
                        destinationContent(
                            SettingsRoutes.OPTIONS,
                            contentFocus.getValue(SettingsRoutes.OPTIONS),
                        )
                    }

                    composable(SettingsRoutes.CHANNEL_TAGS) {
                        destinationContent(
                            SettingsRoutes.CHANNEL_TAGS,
                            contentFocus.getValue(SettingsRoutes.CHANNEL_TAGS),
                        )
                    }

                    composable(SettingsRoutes.PLAYER) {
                        destinationContent(
                            SettingsRoutes.PLAYER,
                            contentFocus.getValue(SettingsRoutes.PLAYER),
                        )
                    }

                    composable(SettingsRoutes.APPLIANCE) {
                        destinationContent(
                            SettingsRoutes.APPLIANCE,
                            contentFocus.getValue(SettingsRoutes.APPLIANCE),
                        )
                    }

                    composable(SettingsRoutes.SIMPLE_TV) {
                        destinationContent(
                            SettingsRoutes.SIMPLE_TV,
                            contentFocus.getValue(SettingsRoutes.SIMPLE_TV),
                        )
                    }
                }
            }
        }
    }
}
