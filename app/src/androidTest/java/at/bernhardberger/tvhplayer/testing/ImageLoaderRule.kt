package at.bernhardberger.tvhplayer.testing

import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import org.junit.rules.ExternalResource

/**
 * The image loader composables and samplers get from Coil's singleton in a test: [loader], or a fresh
 * plain [ImageLoader] when none is given, so a test never reaches the app's production loader. A later
 * [install] replaces it when the test needs interceptors. The installed loader is shut down and the
 * singleton reset after the test, so nothing reaches the next test of the same JVM.
 */
@OptIn(DelicateCoilApi::class)
class ImageLoaderRule(private val loader: (() -> ImageLoader)? = null) : ExternalResource() {
    private var installed: ImageLoader? = null

    fun install(loader: ImageLoader) {
        installed?.shutdown()
        installed = loader
        SingletonImageLoader.setUnsafe(loader)
    }

    override fun before() {
        SingletonImageLoader.reset()
        install(loader?.invoke() ?: ImageLoader(InstrumentationRegistry.getInstrumentation().targetContext))
    }

    override fun after() {
        SingletonImageLoader.reset()
        installed?.shutdown()
        installed = null
    }
}
