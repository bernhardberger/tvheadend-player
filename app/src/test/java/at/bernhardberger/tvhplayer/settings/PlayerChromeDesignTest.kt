package at.bernhardberger.tvhplayer.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlayerChromeDesignTest {
    @Test fun currentIsDefaultAndUnknownValuesAreSafe() {
        assertEquals(PlayerChromeDesign.CURRENT, UiSettings().playerChromeDesign)
        assertEquals(PlayerChromeDesign.CURRENT, resolvePlayerChromeDesign(null))
        assertEquals(PlayerChromeDesign.CURRENT, resolvePlayerChromeDesign("unknown"))
        assertEquals(PlayerChromeDesign.NEW, resolvePlayerChromeDesign("NEW"))
    }

    @Test fun persistsNewAndCurrentAcrossStoreInstances() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val store = UiSettingsStore(context)
        store.setPlayerChromeDesign(PlayerChromeDesign.NEW)
        assertEquals(PlayerChromeDesign.NEW, UiSettingsStore(context).settings.first().playerChromeDesign)
        store.setPlayerChromeDesign(PlayerChromeDesign.CURRENT)
        assertEquals(PlayerChromeDesign.CURRENT, UiSettingsStore(context).settings.first().playerChromeDesign)
    }
}
