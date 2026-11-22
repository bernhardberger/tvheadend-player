package at.bernhardberger.tvhplayer.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface AudioChoice {
    @Serializable
    @SerialName("automatic")
    data object Automatic : AudioChoice
}

/** App-private semantic choice, never a Media3 group reference or track index. */
@Serializable
@SerialName("track")
data class AudioTrackChoice(
    val language: String?,
    val mimeType: String?,
    val roleFlags: Int,
    val channelCount: Int,
    val sampleRate: Int,
) : AudioChoice
