@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.di

import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.android.TvheadendServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.createTvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendRenderersFactory
import at.bernhardberger.tvhplayer.core.StreamProfileDiscovery
import at.bernhardberger.tvhplayer.images.buildImageLoader
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.LegacyCredentialSource
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.ServerSettingsStore
import at.bernhardberger.tvhplayer.settings.ServerProfileMigration
import at.bernhardberger.tvhplayer.settings.SimpleTvSettingsStore
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.stores.SimpleTvSession
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.MainStartupViewModel
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import coil3.ImageLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

val appModule = module {
    single<CoroutineDispatcher>(qualifier = named("io")) { Dispatchers.IO }
    single { TvheadendServerProfileStore(androidContext()) }
    single { ServerSettingsStore(androidContext(), get()) }
    single { LegacyCredentialSource(androidContext()) }
    single { ServerProfileMigration(get(), get(), get()) }
    single { PlayerSettingsStore(androidContext()) }
    single { ChannelTagSettingsStore(androidContext()) }
    single { UiSettingsStore(androidContext()) }
    single { SimpleTvSettingsStore(androidContext()) }

    single {
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val session = createTvheadendSession()
        val streamProfileDiscovery = StreamProfileDiscovery(
            session = session,
            ioDispatcher = get(qualifier = named("io")),
        )
        val player = ExoPlayer.Builder(androidContext())
            .setRenderersFactory(createTvheadendRenderersFactory(androidContext()))
            .build()
        lateinit var playbackRuntime: AppPlaybackRuntime
        val coordinator = createTvheadendPlaybackCoordinator(
            player = player,
            onRecoveryRequired = { reason -> playbackRuntime.onRecoveryRequired(reason) },
        )
        playbackRuntime = AppPlaybackRuntime(
            player = player,
            session = session,
            coordinator = coordinator,
            settings = get(),
            scope = applicationScope,
        )
        SdkRuntimeOwner.create(
            session = session,
            playbackRuntime = playbackRuntime,
            streamProfileDiscovery = streamProfileDiscovery,
            coordinator = coordinator,
            player = player,
            applicationScope = applicationScope,
        )
    } onClose { owner -> owner?.requestClose() }

    single { get<SdkRuntimeOwner>().session }
    single { get<SdkRuntimeOwner>().streamProfileDiscovery }
    single { get<SdkRuntimeOwner>().session.epgRepository }
    single { get<SdkRuntimeOwner>().playbackRuntime }

    single { ChannelSelectionStore() }
    single { LastPlayedChannelStore(androidContext()) }
    single { GuidePositionStore() }
    single { SimpleTvSession() }

    single<ImageLoader> { buildImageLoader(androidContext(), get<SdkRuntimeOwner>().session) }

    viewModel {
        AppConnectionViewModel(
            session = get(),
            profileStore = get(),
            profileMigration = get(),
            serverSettings = get(),
            playerSettings = get(),
            streamProfileDiscovery = get(),
        )
    }
    viewModel {
        MainStartupViewModel(
            serverSettingsStore = get(),
            serverProfileMigration = get(),
            uiSettingsStore = get(),
            simpleTvSettingsStore = get(),
            simpleTvSession = get(),
            savedStateHandle = get(),
        )
    }
    viewModel { VideoPlayerViewModel(playbackRuntime = get(), session = get()) }
    viewModel { ChannelsViewModel(session = get(), tagSettings = get()) }
    viewModel {
        SettingsPlayerViewModel(
            settingsStore = get(),
            playbackRuntime = get(),
            session = get(),
            streamProfileDiscovery = get(),
        )
    }
}
