package at.bernhardberger.tvhplayer.player.htsp.reader

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.AacUtil.buildAacLcAudioSpecificConfig
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.TrackOutput
import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.player.htsp.utils.TvhMappings

@OptIn(UnstableApi::class)
internal class AacStreamReader : StreamReader {

    private var mTrackOutput: TrackOutput? = null

    override fun createTracks(stream: HtspMessage, output: ExtractorOutput) {
        val streamIndex = stream.int("index")!!
        mTrackOutput = output.track(streamIndex, C.TRACK_TYPE_AUDIO)
        mTrackOutput!!.format(buildFormat(streamIndex, stream))
    }

    override fun consume(message: HtspMessage) {
        val pts = message.long("pts") ?: return
        val payload = message.bin("payload") ?: return

        // TVHeadend ships AAC in ADTS framing. media3/MediaCodec decode AAC as raw
        // access units (RAW transport) using the AudioSpecificConfig (csd-0), so strip
        // the ADTS header before handing the frame to the decoder.
        val pba = ParsableByteArray(payload)
        val skipLength: Int =
            if (hasCrc(payload[1])) ADTS_HEADER_SIZE + ADTS_CRC_SIZE else ADTS_HEADER_SIZE
        pba.skipBytes(skipLength)
        val aacFrameLength = payload.size - skipLength

        mTrackOutput!!.sampleData(pba, aacFrameLength)
        mTrackOutput!!.sampleMetadata(pts, C.BUFFER_FLAG_KEY_FRAME, aacFrameLength, 0, null)
    }

    private fun buildFormat(streamIndex: Int, stream: HtspMessage): Format {
        var rate = Format.NO_VALUE
        if (stream.fields.contains("rate")) {
            rate = TvhMappings.sriToRate(stream.int("rate")!!)
        }

        val channels = stream.int("channels") ?: Format.NO_VALUE

        val meta = if (stream.fields.contains("meta")) stream.bin("meta") else null
        val initData = meta?.let { listOf(it) }
            ?: listOf(buildAacLcAudioSpecificConfig(rate, channels))

        return Format.Builder()
            .setId(streamIndex.toString())
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(channels)
            .setSampleRate(rate)
            .setInitializationData(initData)
            .setSelectionFlags(C.SELECTION_FLAG_AUTOSELECT)
            .setLanguage(stream.str("language") ?: "und")
            .build()
    }

    private fun hasCrc(b: Byte): Boolean {
        val data = b.toInt() and 0xFF
        return (data and 0x1) == 0
    }

    companion object {
        private const val ADTS_HEADER_SIZE = 7
        private const val ADTS_CRC_SIZE = 2
    }
}
