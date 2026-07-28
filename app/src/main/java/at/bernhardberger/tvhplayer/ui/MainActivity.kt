package at.bernhardberger.tvhplayer.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.core.ApplianceEntryPolicy
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.player.PlayerSession
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {
    private val applianceLaunchRequests = ApplianceLaunchRequests()
    private val playerSession: PlayerSession by inject()
    private var isPlayerVisible = false
    private var debugVideoBackdropVisible by mutableStateOf(false)
    private var debugVideoBackdropReceiverRegistered = false
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
        super.onCreate(savedInstanceState)
        setContent {
            TVHeadendPlayerTheme {
                AppRoot(
                    applianceLaunchRequests = applianceLaunchRequests,
                    applyStartupMode = savedInstanceState == null,
                    debugVideoBackdropVisible = debugVideoBackdropVisible,
                    onPlayerVisibilityChanged = { isPlayerVisible = it },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
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
        if (ApplianceEntryPolicy.shouldCreateLaunchRequest(isPlayerVisible)) {
            applianceLaunchRequests.request()
        }
    }

    override fun onStop() {
        if (debugVideoBackdropReceiverRegistered) {
            unregisterReceiver(debugVideoBackdropReceiver)
            debugVideoBackdropReceiverRegistered = false
        }
        debugVideoBackdropVisible = false
        super.onStop()
        lifecycleScope.launch { playerSession.stop() }
    }

    private companion object {
        const val ACTION_DEBUG_VIDEO_BACKDROP =
            "at.bernhardberger.tvhplayer.action.DEBUG_VIDEO_BACKDROP"
        const val EXTRA_DEBUG_VIDEO_BACKDROP_VISIBLE = "visible"
    }
}
