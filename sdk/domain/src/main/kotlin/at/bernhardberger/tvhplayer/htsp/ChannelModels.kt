package at.bernhardberger.tvhplayer.htsp

data class ChannelUi(
    val id: Int,
    val name: String,
    val number: Int?,
    val icon: String?,
    val tagIds: Set<Int> = emptySet(),
)

data class ChannelTagUi(
    val id: Int,
    val name: String,
    val index: Int,
)
