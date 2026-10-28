package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.ui.test.junit4.ComposeTestRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher

/** BrowsePreparationKt.preparationDispatcher, read without a production seam. */
internal val browsePreparationLane: CoroutineDispatcher by lazy {
    try {
        Class.forName("at.bernhardberger.tvhplayer.ui.components.BrowsePreparationKt")
            .getDeclaredField("preparationDispatcher").apply { isAccessible = true }
            .get(null) as CoroutineDispatcher
    } catch (failure: ReflectiveOperationException) {
        throw AssertionError("Cannot read BrowsePreparationKt.preparationDispatcher", failure)
    }
}

/**
 * Waits until the serial preparation lane has finished the work queued so far. That work takes real
 * time, which neither waiting for idle nor the test clock sees.
 */
internal fun awaitBrowsePreparationLane() {
    val drained = CountDownLatch(1)
    browsePreparationLane.dispatch(EmptyCoroutineContext) { drained.countDown() }
    check(drained.await(10, TimeUnit.SECONDS)) { "The browse preparation lane did not drain" }
}

/** Lets composition queue its preparations, waits for them, then applies their results. */
internal fun ComposeTestRule.awaitBrowsePreparation() {
    waitForIdle()
    awaitBrowsePreparationLane()
    waitForIdle()
}
