package at.bernhardberger.tvhplayer.stores

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GuidePosition(
    val channelId: Int,
    val eventId: Int,
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
