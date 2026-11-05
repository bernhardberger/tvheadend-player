package at.bernhardberger.tvhplayer.appliance

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CompanionActivityTest {
    @Test fun stoppingTheActivityCancelsAnActiveCapture() {
        val controller = Robolectric.buildActivity(CompanionActivity::class.java).create().start().resume()
        CompanionServiceState.setConnected(true)
        assertTrue(CompanionServiceState.startCapture(CompanionFunction.GUIDE))
        controller.pause().stop()
        assertEquals(CaptureState.Cancelled, CompanionServiceState.captureState.value)
        controller.destroy()
        CompanionServiceState.setConnected(false)
    }

    @Test fun blockedDetectionUsesTheEnabledComponentEvenWhenTheGlobalAccessibilityFlagIsOff() {
        val context = RuntimeEnvironment.getApplication()
        val component = ComponentName(context, at.bernhardberger.tvhplayer.accessibility.ApplianceEntryAccessibilityService::class.java)
        android.provider.Settings.Secure.putString(context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, component.flattenToShortString())
        android.provider.Settings.Secure.putInt(context.contentResolver, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        CompanionServiceState.setConnected(false)
        val controller = Robolectric.buildActivity(CompanionActivity::class.java).create().start().resume()
        assertEquals(CompanionServiceStatus.BLOCKED,
            ReflectionHelpers.callInstanceMethod<CompanionServiceStatus>(controller.get(), "getAccessibilityStatus"))
        CompanionServiceState.setConnected(true)
        controller.pause().resume()
        assertEquals(CompanionServiceStatus.ON,
            ReflectionHelpers.callInstanceMethod<CompanionServiceStatus>(controller.get(), "getAccessibilityStatus"))
        controller.pause().stop().destroy()
        CompanionServiceState.setConnected(false)
    }
    private fun onRow(activity: CompanionActivity, row: CompanionRow) {
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "onRow",
            ReflectionHelpers.ClassParameter.from(CompanionRow::class.java, row))
    }

    @Test fun theStartupRowTogglesTheStoredSwitch() {
        val context = RuntimeEnvironment.getApplication()
        val controller = Robolectric.buildActivity(CompanionActivity::class.java).create().start().resume()
        assertTrue(FunctionStore(context).functions.startup)
        onRow(controller.get(), CompanionRow.STARTUP)
        assertFalse(FunctionStore(context).functions.startup)
        onRow(controller.get(), CompanionRow.STARTUP)
        assertTrue(FunctionStore(context).functions.startup)
        controller.pause().stop().destroy()
    }

    @Test fun okOnABlockedServiceChecksAgainWithoutLeavingForSettings() {
        val context = RuntimeEnvironment.getApplication()
        val component = ComponentName(context, at.bernhardberger.tvhplayer.accessibility.ApplianceEntryAccessibilityService::class.java)
        android.provider.Settings.Secure.putString(context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, component.flattenToShortString())
        CompanionServiceState.setConnected(false)
        val controller = Robolectric.buildActivity(CompanionActivity::class.java).create().start().resume()
        val activity = controller.get()
        fun status() = ReflectionHelpers.callInstanceMethod<CompanionServiceStatus>(activity, "getAccessibilityStatus")
        assertEquals(CompanionServiceStatus.BLOCKED, status())
        onRow(activity, CompanionRow.ACCESSIBILITY)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(CompanionServiceStatus.BLOCKED, status())
        CompanionServiceState.setConnected(true)
        onRow(activity, CompanionRow.ACCESSIBILITY)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(CompanionServiceStatus.ON, status())
        controller.pause().stop().destroy()
        CompanionServiceState.setConnected(false)
    }

    @Test fun okOnAnOffServiceOpensAndroidSettings() {
        CompanionServiceState.setConnected(false)
        val controller = Robolectric.buildActivity(CompanionActivity::class.java).create().start().resume()
        onRow(controller.get(), CompanionRow.ACCESSIBILITY)
        assertEquals(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,
            shadowOf(controller.get()).nextStartedActivity.action)
        controller.pause().stop().destroy()
    }

    private val playerActivity = ComponentName(BuildConfig.PLAYER_PACKAGE, "player.Launcher")

    @Test
    fun freshHomeOpensNormalPlayerLauncherWithLauncherFlagsAndNoEntryExtra() {
        installPlayer()
        val controller = Robolectric.buildActivity(CompanionActivity::class.java, home()).create()
        assertPlayerLaunch(shadowOf(controller.get()).nextStartedActivity)
        controller.destroy()
    }

    @Test
    fun launcherShowsCompanionWithoutOpeningPlayer() {
        installPlayer()
        val controller = Robolectric.buildActivity(CompanionActivity::class.java,
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)).create()
        assertNull(shadowOf(controller.get()).nextStartedActivity)
        controller.destroy()
    }

    @Test
    fun recreationWithHomeIntentAndSubsequentResumeDoNotReopenPlayer() {
        installPlayer()
        val controller = Robolectric.buildActivity(CompanionActivity::class.java, home())
            .create(Bundle()).start().resume()
        assertNull(shadowOf(controller.get()).nextStartedActivity)
        controller.pause().resume()
        assertNull(shadowOf(controller.get()).nextStartedActivity)
        controller.pause().stop().destroy()
    }

    @Test
    fun newHomeIntentOpensPlayerButPlainResumeAfterReturnDoesNot() {
        installPlayer()
        val controller = Robolectric.buildActivity(CompanionActivity::class.java, Intent(Intent.ACTION_MAIN))
            .create().start().resume()
        controller.newIntent(home())
        assertPlayerLaunch(shadowOf(controller.get()).nextStartedActivity)
        controller.pause().resume()
        assertNull(shadowOf(controller.get()).nextStartedActivity)
        controller.pause().stop().destroy()
    }

    @Test
    fun missingPlayerLeavesHomeOnCompanionScreen() {
        val controller = Robolectric.buildActivity(CompanionActivity::class.java, home()).create().start().resume()
        assertNull(shadowOf(controller.get()).nextStartedActivity)
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun standardLauncherIsUsedWhenLeanbackLauncherIsUnavailable() {
        installPlayer(Intent.CATEGORY_LAUNCHER)
        val controller = Robolectric.buildActivity(CompanionActivity::class.java, home()).create()
        assertPlayerLaunch(shadowOf(controller.get()).nextStartedActivity)
        controller.destroy()
    }

    @Test
    fun defaultHomeConsumesBackInsteadOfFinishingAndRestartingHome() {
        val pm = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = BuildConfig.APPLICATION_ID
                name = CompanionActivity::class.java.name
                applicationInfo = ApplicationInfo().apply { packageName = BuildConfig.APPLICATION_ID }
            }
        }
        pm.addResolveInfoForIntent(home(), info)
        val controller = Robolectric.buildActivity(CompanionActivity::class.java).create().start().resume()
        assertTrue(controller.get().onBackPressedDispatcher.hasEnabledCallbacks())
        controller.get().onBackPressedDispatcher.onBackPressed()
        assertFalse(controller.get().isFinishing)
        assertNull(shadowOf(controller.get()).nextStartedActivity)
        controller.pause().stop().destroy()
    }

    @Test
    fun thirdPartyHomesIncludingOtherCompanionDoNotExposeSystemHome() {
        registerHome(ComponentName("test.thirdparty", "test.thirdparty.Home"))
        registerHome(ComponentName("at.bernhardberger.tvhplayer.appliance", CompanionActivity::class.java.name))
        val controller = Robolectric.buildActivity(CompanionActivity::class.java).create()
        assertNull(ReflectionHelpers.callInstanceMethod<ComponentName?>(controller.get(), "getSystemHome"))
        assertNull(shadowOf(controller.get()).nextStartedActivity)
        controller.destroy()
    }

    @Test
    fun systemHomeRemainsEligible() {
        assertSystemHomeEligible(ApplicationInfo.FLAG_SYSTEM)
    }

    @Test
    fun updatedSystemHomeRemainsEligible() {
        assertSystemHomeEligible(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)
    }

    private fun assertSystemHomeEligible(flags: Int) {
        val component = ComponentName("test.systemhome", "test.systemhome.Home")
        registerHome(component, flags)
        val controller = Robolectric.buildActivity(CompanionActivity::class.java).create()
        assertEquals(component,
            ReflectionHelpers.callInstanceMethod<ComponentName?>(controller.get(), "getSystemHome"))
        controller.destroy()
    }

    private fun registerHome(component: ComponentName, flags: Int = 0) {
        shadowOf(RuntimeEnvironment.getApplication().packageManager).addResolveInfoForIntent(home(),
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = component.packageName
                    name = component.className
                    exported = true
                    enabled = true
                    applicationInfo = ApplicationInfo().apply {
                        packageName = component.packageName
                        this.flags = flags
                    }
                }
            })
    }

    private fun home() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    private fun installPlayer(category: String = Intent.CATEGORY_LEANBACK_LAUNCHER) {
        val app = ApplicationInfo().apply { packageName = BuildConfig.PLAYER_PACKAGE }
        val activity = ActivityInfo().apply {
            packageName = BuildConfig.PLAYER_PACKAGE
            name = playerActivity.className
            applicationInfo = app
            exported = true
            enabled = true
        }
        val pm = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        pm.installPackage(PackageInfo().apply {
            packageName = BuildConfig.PLAYER_PACKAGE
            versionName = "1.2.3"
            applicationInfo = app
            activities = arrayOf(activity)
        })
        pm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(category)
            .setPackage(BuildConfig.PLAYER_PACKAGE), ResolveInfo().apply { activityInfo = activity })
    }

    private fun assertPlayerLaunch(intent: Intent?) {
        requireNotNull(intent)
        assertEquals(playerActivity, intent.component)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            intent.flags)
        assertTrue(intent.extras == null || intent.extras!!.isEmpty)
    }
}
