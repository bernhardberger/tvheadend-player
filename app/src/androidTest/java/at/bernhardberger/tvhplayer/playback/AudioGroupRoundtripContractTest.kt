@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.trackselection.FixedTrackSelection
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.extractor.ExtractorOutput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith

/** Bounded probe of the consumed SDK binary, not a replacement media period or protocol fake. */
@RunWith(AndroidJUnit4::class)
class AudioGroupRoundtripContractTest {
    @Test
    fun equalFreshAudioGroupAcceptsAnEarlierExplicitSelection() {
        val format = Format.Builder().setId("1").setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setLanguage("en").setChannelCount(2).setSampleRate(48_000).build()
        val earlierGroup = TrackGroup(format)
        val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
        val outputClass = Class.forName("at.bernhardberger.tvheadend.sdk.media3.QueueExtractorOutput")
        val output = outputClass.declaredConstructors.single().apply { isAccessible = true }
            .newInstance(allocator) as ExtractorOutput
        output.track(1, C.TRACK_TYPE_AUDIO).format(format)
        val currentGroup = outputClass.getDeclaredField("trackGroup").apply { isAccessible = true }
            .get(output) as TrackGroup
        assertEquals(earlierGroup, currentGroup)
        assertNotSame(earlierGroup, currentGroup)

        val periodClass = Class.forName("at.bernhardberger.tvheadend.sdk.media3.TvheadendLiveMediaPeriod")
        val period = periodClass.declaredConstructors.single { it.parameterTypes.size == 6 }
            .apply { isAccessible = true }
            .newInstance(allocator, null, { _: Any -> Unit }, Dispatchers.IO, { null }, { _: Any -> Unit }) as MediaPeriod
        @Suppress("UNCHECKED_CAST")
        val outputs = periodClass.getDeclaredField("outputs").apply { isAccessible = true }
            .get(period) as MutableList<Any>
        outputs.add(output)
        try {
            // Current identity is the control; the equal earlier override is the roundtrip case.
            select(period, currentGroup)
            select(period, earlierGroup)
        } finally {
            periodClass.declaredMethods.single { it.name.startsWith("release$") }
                .apply { isAccessible = true }.invoke(period)
        }
    }

    private fun select(period: MediaPeriod, group: TrackGroup) {
        period.selectTracks(
            arrayOf(FixedTrackSelection(group, 0)), booleanArrayOf(false),
            arrayOfNulls<SampleStream>(1), booleanArrayOf(false), 0L,
        )
    }
}
