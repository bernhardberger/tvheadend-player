package at.bernhardberger.tvhplayer.di

import coil3.ImageLoader
import at.bernhardberger.tvhplayer.htsp.HtspService
import at.bernhardberger.tvhplayer.htsp.HtspConnectionProbe
import at.bernhardberger.tvhplayer.htsp.buildImageLoader
import at.bernhardberger.tvhplayer.player.PlayerSession
import at.bernhardberger.tvhplayer.repositories.TvhRepository
import at.bernhardberger.tvhplayer.repositories.DvrRepository
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
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
import at.bernhardberger.tvhplayer.viewmodels.HomeViewModel
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {
    single<CoroutineDispatcher>(qualifier = named("io")) { Dispatchers.IO }

    single { HtspService(ioDispatcher = get(named("io"))) }
    single { HtspConnectionProbe(ioDispatcher = get(named("io"))) }
    single {
        TvhRepository(
            htsp = get(), ioDispatcher = get(named("io")),
        )
    }
    single {
        DvrRepository(
            htsp = get(), ioDispatcher = get(named("io")),
        )
    }

    single { ServerSettingsStore(context = get()) }
    single { SecurePasswordStore(context = get()) }
    single { PlayerSettingsStore(context = get()) }
    single { ChannelTagSettingsStore(context = get()) }
    single { UiSettingsStore(context = get()) }
    single { SimpleTvSettingsStore(context = get()) }

    single { ChannelSelectionStore() }
    single { LastPlayedChannelStore(context = get()) }
    single { GuidePositionStore() }
    single { SimpleTvSession() }

    single { PlayerSession(htsp = get(), playerSettingsStore = get()) }

    single<ImageLoader> {
        buildImageLoader(
            context = androidContext(),
            htsp = get<HtspService>()
        )
    }

    viewModel {
        AppConnectionViewModel(
            htsp = get(),
            repo = get(),
            dvrRepository = get(),
            settings = get(),
            passwords = get(),
        )
    }
    viewModel { VideoPlayerViewModel(playerSession = get(), repo = get(), htspService = get()) }
    viewModel { ChannelsViewModel(repo = get(), tagSettings = get()) }
    viewModel {
        HomeViewModel(
            repo = get(),
            tagSettings = get(),
            dvrRepository = get(),
            playerSession = get(),
            lastPlayedStore = get(),
        )
    }
    viewModel {
        SettingsPlayerViewModel(
            settingsStore = get(),
            htsp = get(),
            io = get(named("io"))
        )
    }
}
