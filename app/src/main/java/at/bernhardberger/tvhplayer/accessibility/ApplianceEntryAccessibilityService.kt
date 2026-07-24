package at.bernhardberger.tvhplayer.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import at.bernhardberger.tvhplayer.core.ApplianceEntryLaunchGate
import at.bernhardberger.tvhplayer.core.ApplianceEntryPolicy
import at.bernhardberger.tvhplayer.core.ApplianceKeyDecision
import at.bernhardberger.tvhplayer.ui.MainActivity

class ApplianceEntryAccessibilityService : AccessibilityService() {
    private val launchGate = ApplianceEntryLaunchGate(minimumIntervalMillis = 1_500)
    private var screenOnReceiverRegistered = false
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) launchApp()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        registerScreenOnReceiver()
        launchApp()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val decision = ApplianceEntryPolicy.keyEvent(
            isApplianceEntryKey = ApplianceEntryPolicy.isApplianceEntryKey(
                keyCode = event.keyCode,
                androidGuideKeyCode = KeyEvent.KEYCODE_GUIDE,
            ),
            isDown = event.action == KeyEvent.ACTION_DOWN,
            repeatCount = event.repeatCount,
        )
        if (decision == ApplianceKeyDecision.LAUNCH_AND_CONSUME) launchApp()
        return decision != ApplianceKeyDecision.PASS_THROUGH
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        unregisterScreenOnReceiver()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        unregisterScreenOnReceiver()
        super.onDestroy()
    }

    private fun registerScreenOnReceiver() {
        if (screenOnReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            screenOnReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenOnReceiverRegistered = true
    }

    private fun unregisterScreenOnReceiver() {
        if (!screenOnReceiverRegistered) return
        unregisterReceiver(screenOnReceiver)
        screenOnReceiverRegistered = false
    }

    private fun launchApp() {
        if (!launchGate.shouldLaunch(SystemClock.elapsedRealtime())) return
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_APPLIANCE_ENTRY
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
    }

    companion object {
        const val ACTION_APPLIANCE_ENTRY =
            "at.bernhardberger.tvhplayer.action.APPLIANCE_ENTRY"
    }
}
