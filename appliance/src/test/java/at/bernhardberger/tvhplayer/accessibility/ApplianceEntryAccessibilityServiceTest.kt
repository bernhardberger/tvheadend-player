package at.bernhardberger.tvhplayer.accessibility

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.view.KeyEvent
import at.bernhardberger.tvhplayer.appliance.ApplianceEntryPolicy
import at.bernhardberger.tvhplayer.appliance.BuildConfig
import at.bernhardberger.tvhplayer.appliance.CompanionActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ApplianceEntryAccessibilityServiceTest {
    @Test
    fun guideTargetsPlayerGuardedEntryWithOriginalFlags() {
        val starts = dispatchGuide()
        assertEquals(1, starts.size)
        assertEquals(ComponentName(BuildConfig.PLAYER_PACKAGE, ApplianceEntryPolicy.ENTRY_ALIAS_CLASS_NAME), starts[0].component)
        assertEquals(ApplianceEntryPolicy.ACTION_APPLIANCE_ENTRY, starts[0].action)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            starts[0].flags)
    }

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
