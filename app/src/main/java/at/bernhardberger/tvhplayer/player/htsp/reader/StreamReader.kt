package at.bernhardberger.tvhplayer.player.htsp.reader

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorOutput
import at.bernhardberger.tvhplayer.htsp.HtspMessage

@OptIn(UnstableApi::class)
interface StreamReader {

    fun createTracks(stream: HtspMessage, output: ExtractorOutput)

    fun consume(message: HtspMessage)
}