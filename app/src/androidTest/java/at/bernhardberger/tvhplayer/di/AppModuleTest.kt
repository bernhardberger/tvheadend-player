package at.bernhardberger.tvhplayer.di

import androidx.test.ext.junit.runners.AndroidJUnit4
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class AppModuleTest {
    @Test
    fun sdkSessionAndRuntimeBindingsComeFromTheSingleProcessOwner() {
        val koin = GlobalContext.get()
        val owner = koin.get<SdkRuntimeOwner>()

        assertSame(owner, koin.get<SdkRuntimeOwner>())
        assertSame(owner.session, koin.get<TvheadendSession>())
        assertSame(owner.playbackRuntime, koin.get<AppPlaybackRuntime>())
        assertSame(owner.appProfileOwner, koin.get<AppProfileOwner>())
    }
}
