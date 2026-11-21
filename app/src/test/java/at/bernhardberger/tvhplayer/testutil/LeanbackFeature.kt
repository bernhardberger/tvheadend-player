package at.bernhardberger.tvhplayer.testutil

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.robolectric.Shadows.shadowOf

/** Opts a Robolectric test into the platform TV focus policy until the test ends. */
class LeanbackFeature : ExternalResource() {
    private var restore: (() -> Unit)? = null
    val isEnabled: Boolean get() = restore != null

    fun enable() {
        if (isEnabled) return
        val manager = ApplicationProvider.getApplicationContext<Application>().packageManager
        val previous = manager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val shadow = shadowOf(manager)
        restore = { shadow.setSystemFeature(PackageManager.FEATURE_LEANBACK, previous) }
        shadow.setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
    }

    override fun after() {
        restore?.invoke()
        restore = null
    }
}
