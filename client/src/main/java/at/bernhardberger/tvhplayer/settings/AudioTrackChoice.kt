package at.bernhardberger.tvhplayer.settings

import kotlinx.serialization.Serializable

/** App-private semantic choice, never a Media3 group reference or track index. */
@Serializable
data class AudioTrackChoice(
    val language: String?,
    val mimeType: String?,
    val roleFlags: Int,
    val channelCount: Int,
    val sampleRate: Int,
)
