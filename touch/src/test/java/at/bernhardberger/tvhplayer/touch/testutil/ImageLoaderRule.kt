package at.bernhardberger.tvhplayer.touch.testutil

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment

/**
 * Coil's singleton for a test, as the TV's `ImageLoaderRule` installs it: a loader that never
 * fetches. Without a [response], requests end as errors so screens keep their drawn fallback.
 * [requested] lists the models the screens asked for.
 */
@OptIn(DelicateCoilApi::class)
class ImageLoaderRule(private val response: Interceptor? = null) : ExternalResource() {
    val requested: MutableList<Any> = CopyOnWriteArrayList()
    private var loader: ImageLoader? = null

    override fun before() {
        SingletonImageLoader.reset()
        val recording = Interceptor { chain ->
            requested += chain.request.data
            response?.intercept(chain) ?: ErrorResult(null, chain.request, IllegalStateException("No image loading in tests"))
        }
        val installed = ImageLoader.Builder(RuntimeEnvironment.getApplication())
            .apply { if (response != null) coroutineContext(Dispatchers.Unconfined) }
            .components { add(recording) }
            .build()
        loader = installed
        SingletonImageLoader.setUnsafe(installed)
    }

    override fun after() {
        SingletonImageLoader.reset()
        loader?.shutdown()
        loader = null
    }
}
