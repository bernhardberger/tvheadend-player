package at.bernhardberger.tvhplayer.stores

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EventId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GuidePosition(
    val channelId: ChannelId,
    val eventId: EventId,
    val eventStartSec: Long,
    val windowStartSec: Long,
    val firstVisibleColumn: Int,
)

class GuidePositionStore {
    private val _position = MutableStateFlow<GuidePosition?>(null)
    val position = _position.asStateFlow()

    fun save(position: GuidePosition) {
        _position.value = position
    }

    fun clear() {
        _position.value = null
    }
}
