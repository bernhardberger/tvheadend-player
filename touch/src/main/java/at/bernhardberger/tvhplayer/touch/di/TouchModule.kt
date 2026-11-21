@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)

package at.bernhardberger.tvhplayer.touch.di

import at.bernhardberger.tvhplayer.images.ChannelAccents
import at.bernhardberger.tvhplayer.images.DetailsArtColors
import at.bernhardberger.tvhplayer.images.RemoteLogoCache
import at.bernhardberger.tvhplayer.images.buildImageLoader
import coil3.ImageLoader
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.android.TvheadendServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.createTvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.TvheadendLiveStartBuffer
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendLoadControl
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendRenderersFactory
import at.bernhardberger.tvhplayer.core.GUIDE_EPG_COVERAGE_POLICY
import at.bernhardberger.tvhplayer.core.appMetadataCachePolicy
import at.bernhardberger.tvhplayer.di.SdkRuntimeOwner
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.playback.AndroidPlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.AndroidPlaybackAudioOutputs
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.playback.StartupBufferController
import at.bernhardberger.tvhplayer.playback.startupBufferIdentity
import at.bernhardberger.tvhplayer.profiling.ProfilePlaybackTrace
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelAccentStore
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import at.bernhardberger.tvhplayer.notices.ConnectionNoticeSource
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.touch.guide.GuideViewModel
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsViewModel
import at.bernhardberger.tvhplayer.touch.ui.withArtworkTrace
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

private val Io = named("io")
internal val Application = named("application")
private val Channels = named("channels")

/**
 * The TV app's session, profile and playback graph (its AppModule), built the same way because
 * [SdkRuntimeOwner] owns connect, reconnect and close together with the player. The player stays
 * unused until playback is wired.
 */
val touchModule = module {
    single<CoroutineDispatcher>(qualifier = Io) { Dispatchers.IO }
    single { PlayerSettingsStore(androidContext()) }
    single {
        NoticeCenter(SystemClock::elapsedRealtime) {
            NoticeContext(
                get<AppProfileOwner>().configurationGeneration.value,
                get<SdkRuntimeOwner>().session.observation.value.currentSession?.generationIdentity,
            )
        }
    }
    single(qualifier = Application) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    // Eager, so the saved connection connects at app start as on TV.
    single(createdAtStart = true) {
        val applicationScope = get<CoroutineScope>(qualifier = Application)
        val context = androidContext()
        val session = createTvheadendSession(GUIDE_EPG_COVERAGE_POLICY, appMetadataCachePolicy(context.cacheDir))
        val playerSettings = get<PlayerSettingsStore>()
        val profileOwner = AppProfileOwner(
            session = session,
            profileStore = TvheadendServerProfileStore(context),
            playerSettings = playerSettings,
            ioDispatcher = get(qualifier = Io),
            noticeCenter = { get() },
        )
        val audioOutput = TvheadendAudioOutputProvider(context)
        val liveStartBuffer = TvheadendLiveStartBuffer(500.milliseconds)
        val loadControl = createTvheadendLoadControl(liveStartBuffer)
        val player = loadControl.configure(ExoPlayer.Builder(context))
            .setRenderersFactory(createTvheadendRenderersFactory(context, audioOutput))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                false,
            )
            .setHandleAudioBecomingNoisy(false)
            .build()
        val startupBuffer = StartupBufferController(
            liveStartBuffer = liveStartBuffer,
            settings = playerSettings,
            serverIdentity = profileOwner.startupBufferIdentity(),
            scope = applicationScope,
        ).also { it.attach(player) }
        lateinit var playbackRuntime: AppPlaybackRuntime
        val coordinator = createTvheadendPlaybackCoordinator(
            player = player,
            loadControl = loadControl,
            onRecoveryRequired = { reason -> playbackRuntime.onRecoveryRequired(reason) },
        )
        playbackRuntime = AppPlaybackRuntime(
            player = player,
            session = session,
            coordinator = coordinator,
            settings = playerSettings,
            profileOwner = profileOwner,
            scope = applicationScope,
            audioOutput = audioOutput,
            audioFocus = AndroidPlaybackAudioFocus(context, player.applicationLooper),
            audioOutputs = AndroidPlaybackAudioOutputs(context),
            // The TV app turns seek diagnostics on in its debug build; off until playback is wired here.
            policy = PlaybackRuntimePolicy.fromPlayerSettings(trace = ProfilePlaybackTrace, seekDiagnostics = false),
            startupBuffer = startupBuffer,
            noticeCenter = get(),
        )
        applicationScope.launch {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_SCREEN_OFF) playbackRuntime.onDeviceStandby()
                }
            }
            ContextCompat.registerReceiver(
                context, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            try {
                awaitCancellation()
            } finally {
                context.unregisterReceiver(receiver)
            }
        }
        SdkRuntimeOwner.create(
            session = session,
            playbackRuntime = playbackRuntime,
            appProfileOwner = profileOwner,
            coordinator = coordinator,
            player = player,
            applicationScope = applicationScope,
        )
    } onClose { owner -> owner?.requestClose() }

    single { get<SdkRuntimeOwner>().session }
    single { get<SdkRuntimeOwner>().appProfileOwner }
    single(createdAtStart = true) {
        ConnectionNoticeSource(
            get<SdkRuntimeOwner>().session.observation, get<AppProfileOwner>().configurationGeneration, get(),
            get<AppProfileOwner>().switchInProgress,
        ).also { source -> get<CoroutineScope>(qualifier = Application).launch { source.run() } }
    }
    viewModel { AppConnectionViewModel(session = get(), profileOwner = get(), connectionNotices = get()) }
    single { ChannelTagSettingsStore(androidContext()) }
    // Browsing and playback share a scope for the life of the application, not either activity.
    single(Channels) { ViewModelStore() } onClose { it?.clear() }
    single {
        val factory = viewModelFactory {
            initializer {
                ChannelsViewModel(
                    session = get(), tagSettings = get(), liveProfile = get<AppProfileOwner>().liveProfile,
                )
            }
        }
        ViewModelProvider.create(get<ViewModelStore>(Channels), factory)[ChannelsViewModel::class]
    }
    viewModel { RecordingsViewModel(session = get()) }
    viewModel { GuideViewModel(session = get()) }

    // The TV's loader (AppModule): server artwork with the session, remote logos through the shared cache.
    single { RemoteLogoCache(androidContext(), onClear = DetailsArtColors::clear) }
    single { ChannelAccentStore(androidContext()) }
    single {
        val context = androidContext()
        val session = get<SdkRuntimeOwner>().session
        ChannelAccents(get(), session.artwork::cacheKey).also { accents ->
            get<CoroutineScope>(qualifier = Application).launch(Dispatchers.Default) {
                accents.fillIn(session.observation, context)
            }
        }
    }
    single<ImageLoader> {
        buildImageLoader(
            androidContext(), get<SdkRuntimeOwner>().session, get(),
            userAgent = remoteLogoUserAgent(androidContext()), crossfadeMillis = ARTWORK_CROSSFADE_MS,
        ).withArtworkTrace(androidContext())
    }
}

private const val ARTWORK_CROSSFADE_MS = 200

/**
 * The TV's user agent for remote logo hosts (app RemoteLogoUserAgent.kt), with touch's name and its
 * installed version when it has one.
 */
private fun remoteLogoUserAgent(context: Context): String {
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    val product = if (version.isNullOrBlank()) "TvheadendPlayerTouch" else "TvheadendPlayerTouch/$version"
    return "$product (+https://github.com/bernhardberger/tvheadend-player)"
}
