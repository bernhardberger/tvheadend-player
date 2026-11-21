package at.bernhardberger.tvhplayer.touch

import android.app.Application
import at.bernhardberger.tvhplayer.touch.di.touchModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TouchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TouchApplication)
            modules(touchModule)
        }
    }
}
