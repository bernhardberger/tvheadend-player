package at.bernhardberger.tvhplayer

import android.app.Application
import android.content.Context
import at.bernhardberger.tvhplayer.di.appModule
import coil3.ImageLoader
import coil3.SingletonImageLoader
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** Coil's singleton is the app's one artwork loader; composables and samplers reach it through Coil. */
open class App : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
    }

    override fun newImageLoader(context: Context): ImageLoader = get()
}
