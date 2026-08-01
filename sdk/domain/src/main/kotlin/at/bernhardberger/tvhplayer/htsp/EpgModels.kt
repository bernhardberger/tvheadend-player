package at.bernhardberger.tvhplayer.htsp

data class EpgEventEntry(
    val eventId: Int,
    val channelId: Int,
    val start: Long,
    val stop: Long,
    val title: String,
    val summary: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val contentType: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeCount: Int? = null,
    val partNumber: Int? = null,
    val partCount: Int? = null,
    val episodeId: Int? = null,
    val seriesLinkId: Int? = null,
)
