package at.bernhardberger.tvhplayer.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ApplianceLaunchRequest(val id: Long)

data class ApplianceLaunchTarget(
    val request: ApplianceLaunchRequest,
    val channelId: Int,
)

class ApplianceLaunchRequests {
    private var nextRequestId = 0L
    private val _pending = MutableStateFlow<ApplianceLaunchRequest?>(null)
    val pending = _pending.asStateFlow()

    init {
        request()
    }

    fun request() {
        _pending.value = ApplianceLaunchRequest(++nextRequestId)
    }

    fun resolve(orderedIds: List<Int>, persistedId: Int?): ApplianceLaunchTarget? {
        val request = _pending.value ?: return null
        val channelId = LastPlayedChannelPolicy.resolve(orderedIds, persistedId) ?: return null
        return ApplianceLaunchTarget(request, channelId)
    }

    fun consume(request: ApplianceLaunchRequest): Boolean =
        _pending.compareAndSet(request, null)

    fun cancel(request: ApplianceLaunchRequest): Boolean =
        _pending.compareAndSet(request, null)
}
