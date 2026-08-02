package at.bernhardberger.tvhplayer.htsp

enum class DvrState {
    SCHEDULED,
    RECORDING,
    COMPLETED,
    FAILED,
    CANCELLED,
    UNKNOWN,
}

data class DvrFile(
    val id: Int? = null,
    val path: String? = null,
    val size: Long? = null,
)

data class DvrConfig(
    val id: String,
    val name: String,
    val comment: String? = null,
    val enabled: Boolean = true,
)

data class DvrEntry(
    val id: Int,
    val eventId: Int?,
    val channelId: Int,
    val start: Long,
    val stop: Long,
    val title: String,
    val subtitle: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val state: DvrState,
    val failureReason: String? = null,
    val configId: String? = null,
    val files: List<DvrFile> = emptyList(),
    val owner: String? = null,
    val creator: String? = null,
    val path: String? = null,
    val channelName: String? = null,
    val image: String? = null,
    val fanartImage: String? = null,
    val playPosition: Long? = null,
    val playCount: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeCount: Int? = null,
    val partNumber: Int? = null,
    val partCount: Int? = null,
    val autorecId: String? = null,
    val timerecId: String? = null,
)
