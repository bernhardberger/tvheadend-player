@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.os.Looper
import androidx.media3.common.SimpleBasePlayer
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ActivityMediaSessionLifecycleTest {
    @Test fun startStopStartBuildsAndReleasesTheSessionAndObservation() {
        val lifecycle = ActivityMediaSessionLifecycle()
        val context = ApplicationProvider.getApplicationContext<Application>()
        var releases = 0
        repeat(2) {
            val player = object : SimpleBasePlayer(Looper.getMainLooper()) {
                override fun getState(): State = State.Builder().build()
            }
            val observation = Job()
            // Media3 rejects duplicate active session IDs, so the second start also proves release.
            lifecycle.start(context, player, observation) { releases++ }
            lifecycle.stop()
            lifecycle.stop()
            assertTrue(observation.isCancelled)
            assertEquals(it + 1, releases)
        }
    }

    @Test fun overlappingActivitySessionsHaveIndependentIdsAndLifetimes() {
        val first = ActivityMediaSessionLifecycle()
        val second = ActivityMediaSessionLifecycle()
        val context = ApplicationProvider.getApplicationContext<Application>()
        fun player() = object : SimpleBasePlayer(Looper.getMainLooper()) {
            override fun getState(): State = State.Builder().build()
        }
        val firstObservation = Job()
        val secondObservation = Job()
        var releases = 0
        try {
            first.start(context, player(), firstObservation) { releases++ }
            // New activity onStart may precede the old activity's onStop.
            second.start(context, player(), secondObservation) { releases++ }
            first.stop()
            assertTrue(firstObservation.isCancelled)
            assertTrue(secondObservation.isActive)
            assertEquals(1, releases)
        } finally {
            first.stop()
            second.stop()
        }
        assertTrue(secondObservation.isCancelled)
        assertEquals(2, releases)
    }
}
