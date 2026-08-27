package at.bernhardberger.tvhplayer.di

import at.bernhardberger.tvhplayer.BuildConfig
import coil3.ImageLoader
import at.bernhardberger.tvheadend.client.ChannelEpgRuntime
import at.bernhardberger.tvheadend.client.DvrRuntime
import at.bernhardberger.tvheadend.client.HtspClientIdentity
import at.bernhardberger.tvheadend.client.TvheadendClient
import at.bernhardberger.tvheadend.playback.PlaybackPreferencesProvider
import at.bernhardberger.tvheadend.playback.PlaybackRuntime
import at.bernhardberger.tvheadend.playback.createMedia3PlaybackRuntime
import at.bernhardberger.tvhplayer.images.buildImageLoader
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsPlaybackPreferencesProvider
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.SecurePasswordStore
import at.bernhardberger.tvhplayer.settings.ServerSettingsStore
import at.bernhardberger.tvhplayer.settings.SimpleTvSettingsStore
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.stores.SimpleTvSession
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.MainStartupViewModel
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

val appModule = module {
    single<CoroutineDispatcher>(qualifier = named("io")) { Dispatchers.IO }

    single {
        HtspClientIdentity(
            clientName = "TVHeadend Player / ${BuildConfig.VERSION_NAME}",
            clientVersion = BuildConfig.VERSION_NAME,
        )
    }
    single {
        val client = TvheadendClient(
            ioDispatcher = get(named("io")),
            clientIdentity = get(),
        )
        val playbackRuntime = createMedia3PlaybackRuntime(
            context = androidContext(),
            client = client,
            preferencesProvider = get(),
        )
        SdkRuntimeOwner(client, playbackRuntime)
    } onClose { owner -> owner?.requestClose() }
    single<TvheadendClient> { get<SdkRuntimeOwner>().client }
    single<AppPlaybackRuntime> { get<SdkRuntimeOwner>().playbackRuntime }
    single<PlaybackRuntime> { get<SdkRuntimeOwner>().legacyPlaybackRuntime }
    single<ChannelEpgRuntime> { get<TvheadendClient>() }
    single<DvrRuntime> { get<TvheadendClient>() }

    single { ServerSettingsStore(context = get()) }
    single { SecurePasswordStore(context = get()) }
    single { PlayerSettingsStore(context = get()) }
    single<PlaybackPreferencesProvider> {
        PlayerSettingsPlaybackPreferencesProvider(settingsStore = get())
    }
    single { ChannelTagSettingsStore(context = get()) }
    single { UiSettingsStore(context = get()) }
    single { SimpleTvSettingsStore(context = get()) }

    single { ChannelSelectionStore() }
    single { LastPlayedChannelStore(context = get()) }
    single { GuidePositionStore() }
    single { SimpleTvSession() }

    single<ImageLoader> {
        buildImageLoader(
            context = androidContext(),
            client = get(),
        )
    }

    viewModel {
        AppConnectionViewModel(
            client = get(),
            settings = get(),
            passwords = get(),
        )
    }
    viewModel {
        MainStartupViewModel(
            serverSettingsStore = get(),
            uiSettingsStore = get(),
            simpleTvSettingsStore = get(),
            simpleTvSession = get(),
            savedStateHandle = get(),
        )
    }
    viewModel { VideoPlayerViewModel(playbackRuntime = get(), channelRuntime = get(), client = get()) }
    viewModel { ChannelsViewModel(runtime = get(), tagSettings = get()) }
    viewModel {
        SettingsPlayerViewModel(
            settingsStore = get(),
            playbackRuntime = get(),
            client = get(),
        )
    }
}
