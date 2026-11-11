package at.bernhardberger.tvhplayer.accessibility

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityService
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import at.bernhardberger.tvhplayer.appliance.ApplianceEntryPolicy
import at.bernhardberger.tvhplayer.appliance.BuildConfig
import at.bernhardberger.tvhplayer.appliance.CompanionActivity
import at.bernhardberger.tvhplayer.appliance.CompanionFunction
import at.bernhardberger.tvhplayer.appliance.CompanionServiceState
import at.bernhardberger.tvhplayer.appliance.ButtonBinding
import at.bernhardberger.tvhplayer.appliance.CaptureState
import at.bernhardberger.tvhplayer.appliance.FunctionStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAccessibilityService
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [ApplianceEntryAccessibilityServiceTest.ConnectedServiceShadow::class])
class ApplianceEntryAccessibilityServiceTest {
    @Before fun resetServiceState() {
        CompanionServiceState.setConnected(false)
        CompanionServiceState.dismissCapture()
    }

    // Robolectric has no system accessibility connection from which getServiceInfo can fetch its manifest info.
    @Implements(AccessibilityService::class)
    class ConnectedServiceShadow : ShadowAccessibilityService() {
        private val info = AccessibilityServiceInfo()
        @Implementation fun getServiceInfo(): AccessibilityServiceInfo = info
    }
    @Test
    fun guideTargetsPlayerGuardedEntryWithOriginalFlags() {
        val starts = dispatchGuide()
        assertEquals(1, starts.size)
        assertEquals(ComponentName(BuildConfig.PLAYER_PACKAGE, ApplianceEntryPolicy.ENTRY_ALIAS_CLASS_NAME), starts[0].component)
        assertEquals(ApplianceEntryPolicy.ACTION_APPLIANCE_ENTRY, starts[0].action)
        assertEquals(ApplianceEntryPolicy.DESTINATION_GUIDE,
            starts[0].getStringExtra(ApplianceEntryPolicy.EXTRA_APPLIANCE_DESTINATION))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            starts[0].flags)
    }

    @Test fun tvUsesLiveDestinationAndUnassignedKeysPassThrough() {
        val controller = Robolectric.buildService(ApplianceEntryAccessibilityService::class.java).create()
        val service = controller.get()
        assertFalse(dispatch(service, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_INFO))
        assertFalse(dispatch(service, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BOOKMARK))
        assertFalse(dispatch(service, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BOOKMARK))
        for (code in listOf(KeyEvent.KEYCODE_TV, ApplianceEntryPolicy.TCL_TV_KEY_CODE)) {
            assertTrue(dispatch(service, KeyEvent.ACTION_DOWN, code))
            val launch = shadowOf(service).nextStartedActivity
            assertEquals(ApplianceEntryPolicy.DESTINATION_LIVE,
                launch.getStringExtra(ApplianceEntryPolicy.EXTRA_APPLIANCE_DESTINATION))
            assertTrue(dispatch(service, KeyEvent.ACTION_UP, code))
            assertNull(shadowOf(service).nextStartedActivity)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1_500))
        }
        controller.destroy()
    }

    @Test fun recordingsLaunchesItsDestinationAndConsumesTheWholeCycle() {
        val controller = Robolectric.buildService(ApplianceEntryAccessibilityService::class.java).create()
        val service = controller.get()
        assertTrue(dispatch(service, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DVR))
        val launch = shadowOf(service).nextStartedActivity
        assertEquals(ApplianceEntryPolicy.DESTINATION_RECORDINGS,
            launch.getStringExtra(ApplianceEntryPolicy.EXTRA_APPLIANCE_DESTINATION))
        assertEquals(ComponentName(BuildConfig.PLAYER_PACKAGE, ApplianceEntryPolicy.ENTRY_ALIAS_CLASS_NAME), launch.component)
        assertTrue(dispatch(service, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DVR))
        assertNull(shadowOf(service).nextStartedActivity)
        controller.destroy()
    }

    @Test fun captureUsesTheServiceFilterPersistsTheMoveAndSwallowsUpWithoutLaunching() {
        val controller = Robolectric.buildService(ApplianceEntryAccessibilityService::class.java).create()
        val service = controller.get()
        FunctionStore(service).setStartup(false)
        connect(service)
        assertNull(shadowOf(service).nextStartedActivity)
        assertTrue(CompanionServiceState.startCapture(CompanionFunction.LIVE_TV))
        assertTrue(dispatch(service, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_GUIDE))
        assertEquals(CaptureState.Captured(CompanionFunction.LIVE_TV, 172,
            at.bernhardberger.tvhplayer.appliance.MovedButton(CompanionFunction.GUIDE, 172)),
            CompanionServiceState.captureState.value)
        assertEquals(ButtonBinding.Custom(172), FunctionStore(service).functions.liveTv)
        assertEquals(ButtonBinding.NotSet, FunctionStore(service).functions.guide)
        assertTrue(dispatch(service, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_GUIDE))
        assertNull(shadowOf(service).nextStartedActivity)
        controller.destroy()
    }

    @Test fun captureRequiresConnectionAndUnbindDestroyAndScreenOffCancelIt() {
        CompanionServiceState.setConnected(false)
        assertFalse(CompanionServiceState.startCapture(CompanionFunction.GUIDE))
        val controller = Robolectric.buildService(ApplianceEntryAccessibilityService::class.java).create()
        val service = controller.get()
        FunctionStore(service).setStartup(false)
        connect(service)
        assertTrue(CompanionServiceState.connected.value)
        assertTrue(CompanionServiceState.startCapture(CompanionFunction.GUIDE))
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(CaptureState.Cancelled, CompanionServiceState.captureState.value)
        assertTrue(CompanionServiceState.startCapture(CompanionFunction.GUIDE))
        service.onUnbind(Intent())
        assertFalse(CompanionServiceState.connected.value)
        assertEquals(CaptureState.Cancelled, CompanionServiceState.captureState.value)
        connect(service)
        assertTrue(CompanionServiceState.startCapture(CompanionFunction.GUIDE))
        controller.destroy()
        assertFalse(CompanionServiceState.connected.value)
        assertEquals(CaptureState.Cancelled, CompanionServiceState.captureState.value)
    }

    @Test fun aLostCaptureUpBeforeReconnectDoesNotSwallowAFreshUnassignedPress() {
        val controller = Robolectric.buildService(ApplianceEntryAccessibilityService::class.java).create()
        val service = controller.get()
        val store = FunctionStore(service)
        store.setStartup(false)
        connect(service)
        assertTrue(CompanionServiceState.startCapture(CompanionFunction.LIVE_TV))
        assertTrue(dispatch(service, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_INFO))
        service.onUnbind(Intent()) // No UP reaches the filter before it reconnects.
        store.assign(CompanionFunction.LIVE_TV, ButtonBinding.NotSet)
        connect(service)
        assertFalse(dispatch(service, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_INFO))
        assertFalse(dispatch(service, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_INFO))
        assertNull(shadowOf(service).nextStartedActivity)
        controller.destroy()
    }

    @Test fun captureTimerExpiresWithoutAnyMoreKeyEvents() {
        CompanionServiceState.setConnected(true)
        assertTrue(CompanionServiceState.startCapture(CompanionFunction.GUIDE))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(10))
        assertEquals(CaptureState.TimedOut(CompanionFunction.GUIDE), CompanionServiceState.captureState.value)
        CompanionServiceState.setConnected(false)
    }

    @Test fun dismissCaptureKeepsWaitingAndItsTimerButClearsTheResultAndRefusalTimer() {
        CompanionServiceState.setConnected(true)
        assertTrue(CompanionServiceState.startCapture(CompanionFunction.GUIDE))
        val waiting = CompanionServiceState.captureState.value
        CompanionServiceState.dismissCapture()
        assertEquals(waiting, CompanionServiceState.captureState.value)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(10))
        assertEquals(CaptureState.TimedOut(CompanionFunction.GUIDE), CompanionServiceState.captureState.value)
        CompanionServiceState.dismissCapture()
        assertEquals(CaptureState.Idle, CompanionServiceState.captureState.value)
        assertTrue(CompanionServiceState.startCapture(CompanionFunction.GUIDE))
        CompanionServiceState.captureKey(RuntimeEnvironment.getApplication(), KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN))
        assertTrue(CompanionServiceState.captureState.value is CaptureState.Refused)
        CompanionServiceState.dismissCapture()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(10))
        assertEquals(CaptureState.Idle, CompanionServiceState.captureState.value)
        assertTrue(CompanionServiceState.captureKey(RuntimeEnvironment.getApplication(),
            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_UNKNOWN)))
        CompanionServiceState.setConnected(false)
    }

    @Test fun startupStaysOffUntilChosenAndStandbyNeverLaunches() {
        val controller = Robolectric.buildService(ApplianceEntryAccessibilityService::class.java).create()
        val service = controller.get()
        // A fresh setup: turning the service on must not start the player.
        connect(service)
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(service).nextStartedActivity)
        FunctionStore(service).setStartup(true)
        shadowOf(service.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(service).nextStartedActivity)
        shadowOf(service.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ApplianceEntryPolicy.DESTINATION_LIVE,
            shadowOf(service).nextStartedActivity.getStringExtra(ApplianceEntryPolicy.EXTRA_APPLIANCE_DESTINATION))
        controller.destroy()
    }

    private fun connect(service: ApplianceEntryAccessibilityService) {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            "${BuildConfig.APPLICATION_ID}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        service.serviceInfo = AccessibilityServiceInfo()
        ReflectionHelpers.callInstanceMethod<Unit>(service, "onServiceConnected")
    }

    private fun dispatch(service: ApplianceEntryAccessibilityService, action: Int, code: Int): Boolean =
        ReflectionHelpers.callInstanceMethod(service, "onKeyEvent",
            ReflectionHelpers.ClassParameter.from(KeyEvent::class.java, KeyEvent(action, code)))

    @Test
    fun missingOrInaccessiblePlayerFallsBackToCompanionInsteadOfCrashing() {
        for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
            val starts = dispatchGuide(failure)
            assertEquals(2, starts.size)
            assertEquals(ComponentName(BuildConfig.APPLICATION_ID, CompanionActivity::class.java.name), starts[1].component)
            assertNull(starts[1].action) // Fallback must show the screen, not trigger HOME routing.
            assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                starts[1].flags)
        }
    }

    private fun dispatchGuide(failure: RuntimeException? = null): List<Intent> {
        val starts = mutableListOf<Intent>()
        val controller = Robolectric.buildService(ApplianceEntryAccessibilityService::class.java).create()
        val service = controller.get()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startActivity(intent: Intent) {
                starts += intent
                if (intent.component?.packageName == BuildConfig.PLAYER_PACKAGE) failure?.let { throw it }
            }
        }
        ReflectionHelpers.setField(service, "mBase", context)
        val consumed = ReflectionHelpers.callInstanceMethod<Boolean>(service, "onKeyEvent",
            ReflectionHelpers.ClassParameter.from(KeyEvent::class.java, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_GUIDE)))
        assertTrue(consumed)
        controller.destroy()
        return starts
    }
}
