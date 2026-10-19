@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DevicePlaybackFaultTest {
    private fun error(code: Int) = PlaybackException("test", null, code)

    @Test fun decodingAudioOutputParsingAndPlayerChecksAreThisDevicesFault() {
        for (code in listOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
            PlaybackException.ERROR_CODE_UNSPECIFIED,
        )) assertTrue("$code", isDevicePlaybackFault(error(code)))
    }

    @Test fun inputOutputFailuresAndNoErrorAreNot() {
        assertFalse(isDevicePlaybackFault(error(PlaybackException.ERROR_CODE_IO_UNSPECIFIED)))
        assertFalse(isDevicePlaybackFault(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)))
        assertFalse(isDevicePlaybackFault(null))
    }
}
