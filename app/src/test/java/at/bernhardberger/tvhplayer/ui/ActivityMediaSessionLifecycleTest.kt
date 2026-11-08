@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.os.Looper
import androidx.media3.session.MediaSession
import androidx.media3.common.SimpleBasePlayer
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ActivityMediaSessionLifecycleTest {
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
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
            val session = ActivityMediaSessionLifecycle::class.java.getDeclaredField("session").let { field ->
                field.isAccessible = true
                field.get(lifecycle) as MediaSession
            }
            val activity = requireNotNull(session.sessionActivity)
            val intent = shadowOf(activity).savedIntent
            assertTrue(activity.isImmutable)
            assertEquals(MainActivity::class.java.name, intent.component?.className)
            assertEquals(context.packageName, intent.`package`)
            assertEquals(ACTION_SESSION_ACTIVITY, intent.action)
            assertTrue(intent.extras == null || intent.extras!!.isEmpty)
            val labels = AppSessionMetadataLabels(context)
            for ((kind, resource) in listOf(
                at.bernhardberger.tvhplayer.playback.SessionPlaybackErrorKind.RECORDING_READ to at.bernhardberger.tvhplayer.R.string.session_recording_read_failed,
                at.bernhardberger.tvhplayer.playback.SessionPlaybackErrorKind.CHANNEL_UNAVAILABLE to at.bernhardberger.tvhplayer.R.string.session_channel_unavailable,
                at.bernhardberger.tvhplayer.playback.SessionPlaybackErrorKind.PLAYBACK to at.bernhardberger.tvhplayer.R.string.session_playback_failed,
            )) assertEquals(context.getString(resource), labels.error(kind))
            assertEquals("12.3", labels.channelNumber(at.bernhardberger.tvheadend.sdk.core.Channel.create(
                at.bernhardberger.tvheadend.sdk.core.ChannelId(1), number = 12, numberMinor = 3)))
            val bitmap = at.bernhardberger.tvhplayer.testutil.FixtureArt.picon("harbor-sport")
            val bytes = java.io.ByteArrayOutputStream().also {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }.toByteArray()
            val decoded = session.bitmapLoader.decodeBitmap(bytes).get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(decoded.width > 0 && decoded.height > 0)
            val uri = android.net.Uri.parse("data:image/png;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
            org.junit.Assert.assertThrows(java.util.concurrent.ExecutionException::class.java) {
                session.bitmapLoader.loadBitmap(uri).get(5, java.util.concurrent.TimeUnit.SECONDS)
            }
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
