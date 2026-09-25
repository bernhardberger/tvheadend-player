package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaybackAudioFocusTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = shadowOf(context.getSystemService(AudioManager::class.java))
    private val focus = AndroidPlaybackAudioFocus(context, Looper.getMainLooper())
    private val noisy = Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

    @Test fun requestsMovieMediaGainWithoutDelayedGainOrPauseWhenDucked() {
        assertTrue(focus.request {})
        val request = manager.lastAudioFocusRequest.audioFocusRequest
        assertEquals(AudioManager.AUDIOFOCUS_GAIN, request.focusGain)
        assertEquals(AudioAttributes.USAGE_MEDIA, request.audioAttributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_MOVIE, request.audioAttributes.contentType)
        assertFalse(request.acceptsDelayedFocusGain())
        assertFalse(request.willPauseWhenDucked())
        focus.abandon()
        assertSame(request, manager.lastAbandonedAudioFocusRequest)
    }

    @Test fun noisyReceiverExistsOnlyWhileFocusHeldAndOldRequestsAreIgnored() {
        val events = mutableListOf<AudioInterruption>()
        focus.request(events::add)
        val old = manager.lastAudioFocusRequest.listener
        assertTrue(shadowOf(context).hasReceiverForIntent(noisy))
        context.sendBroadcast(noisy)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(AudioInterruption.NOISY), events)
        old.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertFalse(shadowOf(context).hasReceiverForIntent(noisy))
        old.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertTrue(shadowOf(context).hasReceiverForIntent(noisy))
        focus.abandon()
        focus.request(events::add)
        val size = events.size
        old.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(size, events.size)
        focus.abandon()
        assertFalse(shadowOf(context).hasReceiverForIntent(noisy))
    }

    @Test fun deniedRequestDoesNotRegisterNoisyReceiver() {
        val events = mutableListOf<AudioInterruption>()
        manager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        assertFalse(focus.request(events::add))
        manager.lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertTrue(events.isEmpty())
        assertFalse(shadowOf(context).hasReceiverForIntent(noisy))
        focus.abandon()
    }

    @Test fun heldFocusReusesFrameworkRequestButReplacesCallback() {
        val oldEvents = mutableListOf<AudioInterruption>()
        val newEvents = mutableListOf<AudioInterruption>()
        assertTrue(focus.request(oldEvents::add))
        val original = manager.lastAudioFocusRequest.audioFocusRequest
        assertTrue(focus.request(newEvents::add))
        assertSame(original, manager.lastAudioFocusRequest.audioFocusRequest)
        assertNull(manager.lastAbandonedAudioFocusRequest)
        context.sendBroadcast(noisy)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(oldEvents.isEmpty())
        assertEquals(listOf(AudioInterruption.NOISY), newEvents)
        manager.lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertTrue(focus.request {})
        assertNotSame(original, manager.lastAudioFocusRequest.audioFocusRequest)
        focus.abandon()
    }
}
