package at.bernhardberger.tvhplayer.core

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ApplianceLaunchRequest(val id: Long)

data class ApplianceLaunchTarget(
    val request: ApplianceLaunchRequest,
    val channelId: Int,
    val serviceId: Int,
    val channelName: String,
) {
    fun matchesPlayer(
        channelId: Int,
        serviceId: Int,
        channelName: String,
    ): Boolean =
        this.channelId == channelId &&
            this.serviceId == serviceId &&
            this.channelName == channelName
}

sealed interface ApplianceLaunchState {
    data object Idle : ApplianceLaunchState
    data class Pending(val request: ApplianceLaunchRequest) : ApplianceLaunchState
    data class Entering(val target: ApplianceLaunchTarget) : ApplianceLaunchState
}

class ApplianceLaunchRequests {
    private val nextRequestId = AtomicLong(0L)
    private val _state = MutableStateFlow<ApplianceLaunchState>(ApplianceLaunchState.Idle)
    val state = _state.asStateFlow()

    fun request() {
        while (true) {
            if (_state.value != ApplianceLaunchState.Idle) return

            val pending = ApplianceLaunchState.Pending(
                ApplianceLaunchRequest(nextRequestId.incrementAndGet())
            )
            if (_state.compareAndSet(ApplianceLaunchState.Idle, pending)) return
        }
    }

    fun requestStartup(autoStartPlayback: Boolean) {
        if (autoStartPlayback) request()
    }

    fun resolve(
        request: ApplianceLaunchRequest,
        readiness: CurrentChannelReadiness,
        persistedId: Int?,
    ): ApplianceLaunchTarget? {
        val ready = readiness as? CurrentChannelReadiness.Ready ?: return null
        val channelId = LastPlayedChannelPolicy.resolve(
            orderedIds = ready.channels.map { it.id },
            persistedId = persistedId,
        ) ?: return null
        val channel = ready.channels.firstOrNull { it.id == channelId } ?: return null
        val target = ApplianceLaunchTarget(
            request = request,
            channelId = channel.id,
            serviceId = channel.id,
            channelName = channel.name,
        )
        val pending = ApplianceLaunchState.Pending(request)

        return if (_state.compareAndSet(pending, ApplianceLaunchState.Entering(target))) {
            target
        } else {
            null
        }
    }

    fun cancel(expectedState: ApplianceLaunchState): Boolean {
        if (expectedState == ApplianceLaunchState.Idle) return false
        return _state.compareAndSet(expectedState, ApplianceLaunchState.Idle)
    }

    fun isEntering(target: ApplianceLaunchTarget): Boolean =
        _state.value == ApplianceLaunchState.Entering(target)

    fun completePlayerVisibility(
        target: ApplianceLaunchTarget,
        channelId: Int,
        serviceId: Int,
        channelName: String,
    ): Boolean {
        if (!target.matchesPlayer(channelId, serviceId, channelName)) return false
        return _state.compareAndSet(
            ApplianceLaunchState.Entering(target),
            ApplianceLaunchState.Idle,
        )
    }
}
