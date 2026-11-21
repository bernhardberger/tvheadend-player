package at.bernhardberger.tvhplayer.touch.player

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.PowerManager

/**
 * One count of started activities over the whole app, so playback goes to the background only
 * when the last of them stops and comes back when the first starts. Going from Channels to the
 * player (or back) starts one before the other stops, so it changes nothing. An activity stopped
 * for a configuration change is recreated and started again at once: that stop and start are
 * not a trip to the background.
 */
internal class StartedActivities(
    private val onFirstStart: () -> Unit,
    private val onLastStop: () -> Unit,
) {
    private var started = 0
    private var recreating = false

    fun started() {
        started += 1
        if (started != 1) return
        if (recreating) recreating = false else onFirstStart()
    }

    fun stopped(changingConfigurations: Boolean) {
        started -= 1
        if (started != 0) return
        if (changingConfigurations) recreating = true else onLastStop()
    }
}

/** Feeds [StartedActivities] from the app's activities; [onLastStop] learns whether the screen is on. */
internal fun Application.countStartedActivities(onFirstStart: () -> Unit, onLastStop: (interactive: Boolean) -> Unit) {
    val power = getSystemService(PowerManager::class.java)
    val count = StartedActivities(onFirstStart) { onLastStop(power.isInteractive) }
    registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) = count.started()
        override fun onActivityStopped(activity: Activity) = count.stopped(activity.isChangingConfigurations)
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    })
}
