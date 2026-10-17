package at.bernhardberger.tvhplayer.testutil

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import org.junit.rules.ExternalResource

/**
 * The image loader composables and samplers get from Coil's singleton in a test: [loader] or a later
 * [install] when the test needs interceptors, otherwise Coil's default loader. An installed loader is
 * shut down and dropped after the test, so it never reaches the next test of the same JVM.
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
        loader?.let { install(it()) }
    }

    override fun after() {
        SingletonImageLoader.reset()
        installed?.shutdown()
        installed = null
    }
}
