package at.bernhardberger.tvhplayer.touch

import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.ImageLoader
import android.app.Application
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
    }

    /** Coil's composables load with the app's artwork loader from the Koin graph, as on TV. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = get()
}
