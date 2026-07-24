package at.bernhardberger.tvhplayer

import android.app.Application
import at.bernhardberger.tvhplayer.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

open class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Without a planted tree Timber is a no-op, so debug logging never reaches
        // logcat. Plant a DebugTree for debug builds only.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
    }
}
