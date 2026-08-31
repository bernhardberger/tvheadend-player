package at.bernhardberger.tvhplayer.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
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
import at.bernhardberger.tvhplayer.BuildConfig
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
    private val playbackLifecycle = MainActivityPlaybackLifecycle(
        onAppForegrounded = { playbackRuntime.onAppForegrounded() },
        onAppBackgrounded = { playbackRuntime.onAppBackgrounded() },
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (mainStartupKeyDispatcher.dispatch(mainStartupActivityKeyContract, event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        if (debugVideoBackdropReceiverRegistered) {
            unregisterReceiver(debugVideoBackdropReceiver)
            debugVideoBackdropReceiverRegistered = false
        }
        debugVideoBackdropVisible = false
        playbackLifecycle.onActivityStopped()
        super.onStop()
    }

    private fun requestRootExit() {
        lifecycleScope.launch {
            playbackLifecycle.onRootExitRequested()
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

    suspend fun onRootExitRequested() {
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
