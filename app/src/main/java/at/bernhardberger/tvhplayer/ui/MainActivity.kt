package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvhplayer.profiling.profileTrace

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.session.MediaSession
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.playback.SessionPlaybackPlayer
import kotlinx.coroutines.Job
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackNotice
import at.bernhardberger.tvhplayer.ui.notifications.AppNoticeQueue
import at.bernhardberger.tvhplayer.ui.notifications.AppNoticeKind
import at.bernhardberger.tvhplayer.accessibility.ApplianceEntryAccessibilityService
import at.bernhardberger.tvhplayer.core.ApplianceEntryPolicy
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.ui.player.stopPlaybackAndClose
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyCycleOwner
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyDecision
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyMode
import at.bernhardberger.tvhplayer.viewmodels.MainStartupViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

data class MainStartupActivityKeyContract(
    val mode: MainStartupKeyMode,
)

internal class MainStartupActivityKeyDispatcher(
    private val owner: MainStartupKeyCycleOwner,
) {
    private val forwardedActivationKeyCodes = mutableSetOf<Int>()

    fun dispatch(contract: MainStartupActivityKeyContract, event: KeyEvent): Boolean = dispatch(
        contract = contract,
        keyCode = event.keyCode,
        action = event.action,
        repeatCount = event.repeatCount,
    )

    fun dispatch(
        contract: MainStartupActivityKeyContract,
        keyCode: Int,
        action: Int,
        repeatCount: Int = 0,
    ): Boolean {
        val decision = owner.keyEvent(
            mode = contract.mode,
            keyCode = keyCode,
            action = action,
            repeatCount = repeatCount,
        )
        when (decision) {
            MainStartupKeyDecision.CONSUME -> return true
            MainStartupKeyDecision.PASS_THROUGH -> Unit
        }
        if (action == KeyEvent.ACTION_UP && keyCode in forwardedActivationKeyCodes) {
            forwardedActivationKeyCodes.remove(keyCode)
            return contract.mode !is MainStartupKeyMode.Actionable
        }
        if (
            action == KeyEvent.ACTION_DOWN &&
            repeatCount == 0 &&
            contract.mode is MainStartupKeyMode.Actionable &&
            keyCode.isStartupActivationKey()
        ) {
            forwardedActivationKeyCodes += keyCode
        }
        return false
    }
}

internal fun dispatchMainStartupKeyEvent(
    owner: MainStartupKeyCycleOwner,
    contract: MainStartupActivityKeyContract,
    event: KeyEvent,
): Boolean = MainStartupActivityKeyDispatcher(owner).dispatch(contract, event)

private fun Int.isStartupActivationKey(): Boolean = when (this) {
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER -> true
    else -> false
}

class MainActivity : AppCompatActivity() {
    private val startupViewModel: MainStartupViewModel by viewModel()
    private val playbackRuntime: AppPlaybackRuntime by inject()
    private val notices: AppNoticeQueue by inject()
    private val tvheadendSession: TvheadendSession by inject()
    private val mediaSessionLifecycle = ActivityMediaSessionLifecycle()
    private val playbackLifecycle = MainActivityPlaybackLifecycle(
        onAppForegrounded = { playbackRuntime.onAppForegrounded() },
        onAppBackgrounded = { playbackRuntime.onAppBackgrounded(getSystemService(PowerManager::class.java).isInteractive) },
        stopPlayback = { playbackRuntime.stop() },
        finishActivity = ::finish,
    )
    private var isPlayerVisible = false
    private var debugVideoBackdropVisible by mutableStateOf(false)
    private var debugVideoBackdropReceiverRegistered = false
    private val mainStartupKeyCycleOwner = MainStartupKeyCycleOwner()
    private val mainStartupKeyDispatcher =
        MainStartupActivityKeyDispatcher(mainStartupKeyCycleOwner)
    private var mainStartupActivityKeyContract = MainStartupActivityKeyContract(
        mode = MainStartupKeyMode.Inactive,
    )
    private val debugVideoBackdropReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (
                BuildConfig.DEBUG &&
                intent?.action == ACTION_DEBUG_VIDEO_BACKDROP
            ) {
                debugVideoBackdropVisible = intent.getBooleanExtra(
                    EXTRA_DEBUG_VIDEO_BACKDROP_VISIBLE,
                    false,
                )
                setResultCode(Activity.RESULT_OK)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        val platformSplashDeadlineUptimeMillis =
            SystemClock.uptimeMillis() + MAX_PLATFORM_SPLASH_HOLD_MILLIS
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playbackRuntime.backgroundNotice.collect { notice ->
                    if (notice != null) {
                        notices.post("background-playback", when (notice) {
                            BackgroundPlaybackNotice.LIMIT_EXPIRED -> R.string.background_live_stopped
                            BackgroundPlaybackNotice.TUNER_LOST -> R.string.background_tuner_lost
                        }, AppNoticeKind.SUCCESS, notices.context())
                        playbackRuntime.consumeBackgroundNotice(notice)
                    }
                }
            }
        }
        splashScreen.setKeepOnScreenCondition {
            startupViewModel.state.value is MainStartupState.ResolvingLocal &&
                SystemClock.uptimeMillis() < platformSplashDeadlineUptimeMillis
        }
        if (startupViewModel.shouldHandleInitialActivityIntent(savedInstanceState != null)) {
            requestApplianceEntry(intent)
        }
        onBackPressedDispatcher.addCallback(this) { requestRootExit() }
        setContent {
            val startupState by startupViewModel.state.collectAsStateWithLifecycle()
            val runtimeServerSettings by
                startupViewModel.runtimeServerSettings.collectAsStateWithLifecycle()
            TVHeadendPlayerTheme {
                AppRoot(
                    startupState = startupState,
                    runtimeServerSettings = runtimeServerSettings,
                    applianceLaunchRequests = startupViewModel.applianceLaunchRequests,
                    debugVideoBackdropVisible = debugVideoBackdropVisible,
                    onPlayerVisibilityChanged = { isPlayerVisible = it },
                    onRequestExit = ::requestRootExit,
                    registerActivityKeyContract = ::registerMainStartupActivityKeyContract,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playbackLifecycle.onActivityStarted()
        val sessionPlayer = SessionPlaybackPlayer(playbackRuntime)
        mediaSessionLifecycle.start(this, sessionPlayer, lifecycleScope.launch {
            sessionPlayer.observe(tvheadendSession.observation)
        }, sessionPlayer::close)
        if (BuildConfig.DEBUG && !debugVideoBackdropReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                debugVideoBackdropReceiver,
                IntentFilter(ACTION_DEBUG_VIDEO_BACKDROP),
                ContextCompat.RECEIVER_EXPORTED,
            )
            debugVideoBackdropReceiverRegistered = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestApplianceEntry(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = profileTrace(
        if (event.action == KeyEvent.ACTION_DOWN) "P44:input:down" else "P44:input:other",
    ) {
        at.bernhardberger.tvhplayer.profiling.profileNavigationInput(event)
        if (mainStartupKeyDispatcher.dispatch(mainStartupActivityKeyContract, event)) {
            true
        } else {
            super.dispatchKeyEvent(event)
        }
    }

    override fun onStop() {
        mediaSessionLifecycle.stop()
        if (debugVideoBackdropReceiverRegistered) {
            unregisterReceiver(debugVideoBackdropReceiver)
            debugVideoBackdropReceiverRegistered = false
        }
        debugVideoBackdropVisible = false
        playbackLifecycle.onActivityStopped()
        super.onStop()
    }

    private fun requestRootExit() {
        val startupState = startupViewModel.state.value
        lifecycleScope.launch {
            playbackLifecycle.onRootExitRequested(startupState)
        }
    }

    private fun requestApplianceEntry(intent: Intent?) {
        if (intent?.action != ApplianceEntryAccessibilityService.ACTION_APPLIANCE_ENTRY) return
        if (ApplianceEntryPolicy.shouldCreateLaunchRequest(isPlayerVisible)) {
            startupViewModel.applianceLaunchRequests.request()
        }
    }

    private fun registerMainStartupActivityKeyContract(
        contract: MainStartupActivityKeyContract,
    ): () -> Unit {
        mainStartupActivityKeyContract = contract
        return {
            if (mainStartupActivityKeyContract === contract) {
                mainStartupActivityKeyContract = MainStartupActivityKeyContract(
                    mode = MainStartupKeyMode.Inactive,
                )
            }
        }
    }

    private companion object {
        const val MAX_PLATFORM_SPLASH_HOLD_MILLIS = 1_000L
        const val ACTION_DEBUG_VIDEO_BACKDROP =
            "at.bernhardberger.tvhplayer.action.DEBUG_VIDEO_BACKDROP"
        const val EXTRA_DEBUG_VIDEO_BACKDROP_VISIBLE = "visible"
    }
}

/** Activity-only session: no service, notification or background controller ownership. */
internal class ActivityMediaSessionLifecycle {
    private val sessionId = java.util.UUID.randomUUID().toString()
    private var session: MediaSession? = null
    private var observation: Job? = null
    private var releasePlayer: (() -> Unit)? = null

    fun start(context: Context, player: androidx.media3.common.Player, observation: Job, releasePlayer: () -> Unit) {
        check(session == null)
        this.observation = observation
        this.releasePlayer = releasePlayer
        session = MediaSession.Builder(context, player).setId(sessionId).build()
    }

    fun stop() {
        observation?.cancel()
        observation = null
        val previous = session ?: return
        session = null
        previous.release()
        releasePlayer?.invoke()
        releasePlayer = null
    }
}

internal class MainActivityPlaybackLifecycle(
    private val onAppForegrounded: () -> Unit,
    private val onAppBackgrounded: () -> Unit,
    private val stopPlayback: suspend () -> Unit,
    private val finishActivity: () -> Unit,
) {
    private val rootExitMutex = Mutex()
    private var rootExitStarted = false

    fun onActivityStarted() = onAppForegrounded()

    fun onActivityStopped() = onAppBackgrounded()

    suspend fun onRootExitRequested(
        startupState: MainStartupState,
    ) {
        if (startupState !is MainStartupState.Ready) return
        rootExitMutex.withLock {
            if (rootExitStarted) return@withLock
            rootExitStarted = true
            stopPlaybackAndClose(
                stopPlayback = stopPlayback,
                closePlayer = finishActivity,
            )
        }
    }
}
