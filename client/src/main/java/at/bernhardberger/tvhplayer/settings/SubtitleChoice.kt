package at.bernhardberger.tvhplayer.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A semantic text choice, independent of the stream's track-group identity. */
@Serializable
sealed interface SubtitleChoice {
    @Serializable
    @SerialName("off")
    data object Off : SubtitleChoice

    @Serializable
    @SerialName("track")
    data class Track(val language: String?, val mimeType: String?, val roleFlags: Int) : SubtitleChoice
}
