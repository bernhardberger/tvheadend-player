@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.profiling

import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.android.TvheadendServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.EpgCoverageAcquisitionResult
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendRenderersFactory
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.di.SdkRuntimeOwner
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.createPlaybackLoadControl
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.LegacyCredentialSource
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.ChannelsKey
import at.bernhardberger.tvhplayer.ui.GuideKey
import at.bernhardberger.tvhplayer.ui.SIDEBAR_SCENE_DESTINATION
import at.bernhardberger.tvhplayer.ui.appDestinationContentTransform
import at.bernhardberger.tvhplayer.ui.destination
import at.bernhardberger.tvhplayer.ui.navigateTopLevel
import at.bernhardberger.tvhplayer.ui.rememberAppNavBackStack
import at.bernhardberger.tvhplayer.ui.rememberSidebarGuideSceneStrategy
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.screens.ChannelsScreen
import at.bernhardberger.tvhplayer.ui.screens.EpgGridScreen
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import coil3.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock

/** Isolated SDK-domain fixtures feeding the unchanged production screens and state owners. */
class JourneyProfileActivity : AppCompatActivity() {
    private var runtimeOwner: SdkRuntimeOwner? = null
    private var images: ImageLoader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dataset = intent.getStringExtra("dataset") ?: "normal"
        val journey = intent.getStringExtra("journey") ?: "channels"
        require(dataset in setOf("normal", "stress"))
        require(journey in setOf("channels", "guide"))
        val epoch = intent.getLongExtra(
            "datasetEpochSeconds", Clock.System.now().epochSeconds / 1800 * 1800 - 3600,
        )
        lifecycleScope.launch {
            val observation = withContext(Dispatchers.Default) {
                profileObservation(dataset == "stress", epoch)
            }
            val session = FakeTvheadendSession(observation)
            session.epgRepository.scriptCoverage(
                EpgCoverageAcquisitionResult.CoveredWithData(observation),
            )
            val settings = PlayerSettingsStore(this@JourneyProfileActivity)
            val tags = ChannelTagSettingsStore(this@JourneyProfileActivity)
            tags.selectTag(null)
            val profileStore = TvheadendServerProfileStore(this@JourneyProfileActivity)
            // Only the isolated .profile UID sees this anonymous fixture identity.
            check(profileStore.storeAnonymous("offline.invalid", 9982) is ServerProfileReadResult.Available)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val profiles = AppProfileOwner(
                this@JourneyProfileActivity, session, profileStore,
                LegacyCredentialSource(this@JourneyProfileActivity), settings, Dispatchers.IO,
            )
            val player = ExoPlayer.Builder(this@JourneyProfileActivity)
                .setRenderersFactory(createTvheadendRenderersFactory(this@JourneyProfileActivity))
                .setLoadControl(createPlaybackLoadControl())
                .build()
            val coordinator = createTvheadendPlaybackCoordinator(player)
            val runtime = AppPlaybackRuntime(player, session, coordinator, settings, profiles, scope)
            runtimeOwner = SdkRuntimeOwner.create(session, runtime, profiles, coordinator, player, scope)
            withTimeout(5_000) { profiles.serverProfile.filterNotNull().first() }
            session.publish(observation)
            val catalog = ViewModelProvider.create(
                this@JourneyProfileActivity,
                viewModelFactory { initializer { ChannelsViewModel(session, tags) } },
            )[ChannelsViewModel::class]
            val selection = ChannelSelectionStore()
            val guidePosition = GuidePositionStore()
            val lastPlayed = LastPlayedChannelStore(this@JourneyProfileActivity)
            val imageLoader = ImageLoader(this@JourneyProfileActivity).also { images = it }
            setContent {
                val backStack = rememberAppNavBackStack(if (journey == "guide") GuideKey else ChannelsKey)
                val route = backStack.last()
                val browseBack = remember { mutableStateOf<() -> Unit>({ finish() }) }
                BackHandler { browseBack.value() }
                TVHeadendPlayerTheme {
                    SideRail(
                        currentRoute = route.destination,
                        showEpgMenu = true,
                        availableDestinations = setOf(AppDestination.CHANNELS, AppDestination.GUIDE),
                        onRootBack = { finish() },
                        onBackHandlerChanged = { browseBack.value = it },
                        onNavigate = {
                            backStack.navigateTopLevel(if (it == AppDestination.GUIDE) GuideKey else ChannelsKey)
                        },
                    ) { padding, drawerActive ->
                        NavDisplay(
                            backStack = backStack,
                            onBack = { browseBack.value() },
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                            sceneStrategies = listOf(rememberSidebarGuideSceneStrategy(drawerActive, route)),
                            transitionSpec = { appDestinationContentTransform() },
                            popTransitionSpec = { appDestinationContentTransform() },
                            entryProvider = entryProvider {
                                entry<GuideKey>(metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.GUIDE)) {
                                    EpgGridScreen(
                                        contentPadding = padding, initialFocusEnabled = !drawerActive && route == GuideKey,
                                        channelViewModel = catalog, selection = selection, session = session,
                                        playerSession = runtime, lastPlayedStore = lastPlayed,
                                        guidePositionStore = guidePosition, imageLoader = imageLoader,
                                        onPlay = { _, _ -> },
                                    )
                                }
                                entry<ChannelsKey>(metadata = mapOf(SIDEBAR_SCENE_DESTINATION to AppDestination.CHANNELS)) {
                                    ChannelsScreen(
                                        contentPadding = padding, initialFocusEnabled = !drawerActive,
                                        channelViewModel = catalog, selection = selection, imageLoader = imageLoader,
                                        playingChannelId = null, connectionUiState = ConnectionUiState.Ready,
                                        onRetryConnection = {}, onOpenConnectionSettings = {}, onPlay = { _, _ -> },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = profileTrace(
        if (event.action == KeyEvent.ACTION_DOWN) "P44:input:down" else "P44:input:other",
    ) {
        super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        // The fixture runtime is activity-owned, including on configuration recreation.
        // Do not retain a catalog that still observes the previous session.
        viewModelStore.clear()
        super.onDestroy()
        runtimeOwner?.requestClose()
        images?.shutdown()
    }
}
