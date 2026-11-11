package at.bernhardberger.tvhplayer.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.os.PowerManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import at.bernhardberger.tvhplayer.appliance.BuildConfig
import at.bernhardberger.tvhplayer.appliance.CompanionActivity
import at.bernhardberger.tvhplayer.appliance.ApplianceEntryLaunchGate
import at.bernhardberger.tvhplayer.appliance.ApplianceEntryPolicy
import at.bernhardberger.tvhplayer.appliance.ApplianceKeyDecision
import at.bernhardberger.tvhplayer.appliance.CompanionFunction
import at.bernhardberger.tvhplayer.appliance.CompanionServiceState
import at.bernhardberger.tvhplayer.appliance.FunctionStore
import at.bernhardberger.tvhplayer.appliance.update.CompanionUpdateJob
import at.bernhardberger.tvhplayer.appliance.update.UpdatePolicy
import at.bernhardberger.tvhplayer.appliance.update.UpdateStore

class ApplianceEntryAccessibilityService : AccessibilityService() {
    private val launchGate = ApplianceEntryLaunchGate(minimumIntervalMillis = 1_500)
    private var screenOnReceiverRegistered = false
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    CompanionUpdateJob.cancel(context)
                    if (FunctionStore(context).functions.startup) launchApp()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    CompanionServiceState.cancelCapture()
                    CompanionUpdateJob.schedule(context)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        CompanionServiceState.setConnected(true)
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        registerScreenOnReceiver()
        val updates = UpdateStore(this)
        updates.reconcileInstalledVersion()
        val launchOnConnect = UpdatePolicy.shouldLaunchOnConnect(getSystemService(PowerManager::class.java).isInteractive,
            updates.consumeConnectSuppression())
        if (launchOnConnect && FunctionStore(this).functions.startup) launchApp()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (CompanionServiceState.captureKey(this, event)) return true
        val function = FunctionStore(this).functions.owner(event.keyCode)
        val decision = ApplianceEntryPolicy.keyEvent(
            isApplianceEntryKey = function != null,
            isDown = event.action == KeyEvent.ACTION_DOWN,
            repeatCount = event.repeatCount,
        )
        if (decision == ApplianceKeyDecision.LAUNCH_AND_CONSUME) launchApp(
            when (function) {
                CompanionFunction.GUIDE -> ApplianceEntryPolicy.DESTINATION_GUIDE
                CompanionFunction.RECORDINGS -> ApplianceEntryPolicy.DESTINATION_RECORDINGS
                else -> ApplianceEntryPolicy.DESTINATION_LIVE
            }, explicit = true)
        return decision != ApplianceKeyDecision.PASS_THROUGH
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        CompanionServiceState.setConnected(false)
        unregisterScreenOnReceiver()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        CompanionServiceState.setConnected(false)
        unregisterScreenOnReceiver()
        super.onDestroy()
    }

    private fun registerScreenOnReceiver() {
        if (screenOnReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            screenOnReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenOnReceiverRegistered = true
    }

    private fun unregisterScreenOnReceiver() {
        if (!screenOnReceiverRegistered) return
        unregisterReceiver(screenOnReceiver)
        screenOnReceiverRegistered = false
    }

    private fun launchApp(destination: String = ApplianceEntryPolicy.DESTINATION_LIVE, explicit: Boolean = false) {
        if (!getSystemService(PowerManager::class.java).isInteractive) return
        if (!launchGate.shouldLaunch(SystemClock.elapsedRealtime(), destination, explicit)) return
        try {
            startActivity(Intent().setClassName(BuildConfig.PLAYER_PACKAGE, ApplianceEntryPolicy.ENTRY_ALIAS_CLASS_NAME).apply {
                action = ACTION_APPLIANCE_ENTRY
                putExtra(ApplianceEntryPolicy.EXTRA_APPLIANCE_DESTINATION, destination)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        } catch (_: ActivityNotFoundException) {
            openCompanion()
        } catch (_: SecurityException) {
            openCompanion()
        }
    }

    private fun openCompanion() {
        startActivity(Intent(this, CompanionActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    companion object {
        const val ACTION_APPLIANCE_ENTRY =
            ApplianceEntryPolicy.ACTION_APPLIANCE_ENTRY
    }
}
