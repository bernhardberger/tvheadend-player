package at.bernhardberger.tvhplayer.touch

import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.ImageLoader
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.text.format.DateFormat
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.di.SdkRuntimeOwner
import at.bernhardberger.tvhplayer.touch.di.touchModule
import at.bernhardberger.tvhplayer.touch.player.countStartedActivities
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TouchApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TouchApplication)
            modules(touchModule)
        }
        val runtime = get<SdkRuntimeOwner>().playbackRuntime
        countStartedActivities(runtime::onAppForegrounded) { interactive -> runtime.onAppBackgrounded(interactive) }
        followDeviceClockFormat()
    }

    /**
     * The shared clock texts follow the device's 12/24-hour setting, read again whenever an activity
     * resumes since it can change while the app is away (as the TV's MainActivity.onResume does).
     */
    private fun followDeviceClockFormat() {
        AppClock.systemUses24Hour = DateFormat.is24HourFormat(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                AppClock.systemUses24Hour = DateFormat.is24HourFormat(activity)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** Coil's composables load with the app's artwork loader from the Koin graph, as on TV. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = get()
}
