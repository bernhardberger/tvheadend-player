package at.bernhardberger.tvhplayer.player.htsp

import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer

@OptIn(UnstableApi::class)
internal class LegacyRenderer(context: android.content.Context) : DefaultRenderersFactory(context) {

    init {
        // The Amlogic platform MPEG-audio decoder (c2.amlogic.audio.decoder.mp2)
        // advertises support for MP1/MP2 but fails valid DVB/IPTV frames at runtime
        // with "Invalid data frame", and because it claims support ExoPlayer never
        // falls back. Hide it for MP1/MP2 only, so those codecs drop to the bundled
        // FFmpeg software decoder while AAC (5.1) and AC3/EAC3 (passthrough) keep using
        // hardware. MP3 already has no platform decoder on these boxes and falls back
        // to FFmpeg on its own, so it is left untouched.
        setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            if (mimeType == MimeTypes.AUDIO_MPEG_L1 || mimeType == MimeTypes.AUDIO_MPEG_L2) {
                emptyList()
            } else {
                MediaCodecSelector.DEFAULT.getDecoderInfos(
                    mimeType, requiresSecureDecoder, requiresTunnelingDecoder
                )
            }
        }
    }

    @OptIn(ExperimentalApi::class)
    override fun buildTextRenderers(
        context: android.content.Context,
        output: TextOutput,
        outputLooper: android.os.Looper,
        extensionRendererMode: Int,
        out: ArrayList<androidx.media3.exoplayer.Renderer>
    ) {
        val textRenderer = TextRenderer(output, outputLooper)
        @Suppress("DEPRECATION")
        textRenderer.experimentalSetLegacyDecodingEnabled(true)
        out.add(textRenderer)
    }
}
