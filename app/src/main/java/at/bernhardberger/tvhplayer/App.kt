package at.bernhardberger.tvhplayer

import android.app.Application
import at.bernhardberger.tvhplayer.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

open class App : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
    }
}
