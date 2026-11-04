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
