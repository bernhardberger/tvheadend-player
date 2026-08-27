package at.bernhardberger.tvhplayer.core

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ApplianceLaunchRequest(val id: Long)

data class ApplianceLaunchTarget(
    val request: ApplianceLaunchRequest,
    val channelId: Int,
    val channelName: String,
) {
    fun matchesPlayer(
        channelId: Int,
        channelName: String,
    ): Boolean =
        this.channelId == channelId &&
            this.channelName == channelName
}

sealed interface ApplianceLaunchState {
    data object Idle : ApplianceLaunchState
    data class Pending(val request: ApplianceLaunchRequest) : ApplianceLaunchState
    data class Entering(val target: ApplianceLaunchTarget) : ApplianceLaunchState
}

class ApplianceLaunchRequests(
    restoredRequestId: Long? = null,
    private val onRetainedRequestIdChanged: (Long?) -> Unit = {},
) {
    private val nextRequestId = AtomicLong(restoredRequestId?.coerceAtLeast(0L) ?: 0L)
    private val _state = MutableStateFlow<ApplianceLaunchState>(
        restoredRequestId?.let { requestId ->
            ApplianceLaunchState.Pending(ApplianceLaunchRequest(requestId))
        } ?: ApplianceLaunchState.Idle,
    )
    val state = _state.asStateFlow()

    @Synchronized
    fun request() {
        while (true) {
            if (_state.value != ApplianceLaunchState.Idle) return

            val pending = ApplianceLaunchState.Pending(
                ApplianceLaunchRequest(
                    nextRequestId.updateAndGet { currentId ->
                        check(currentId < Long.MAX_VALUE) { "Launch request ID exhausted" }
                        currentId + 1L
                    }
                )
            )
            if (transition(ApplianceLaunchState.Idle, pending, pending.request.id)) return
        }
    }

    fun requestStartup(autoStartPlayback: Boolean) {
        if (autoStartPlayback) request()
    }

    @Synchronized
    fun resolve(
        request: ApplianceLaunchRequest,
        readiness: CurrentChannelReadiness,
        persistedId: Int?,
    ): ApplianceLaunchTarget? {
        val ready = readiness as? CurrentChannelReadiness.Ready ?: return null
        val channelId = LastPlayedChannelPolicy.resolve(
            orderedIds = ready.channels.map { it.channelId },
            persistedId = persistedId,
        ) ?: return null
        val channel = ready.channels.firstOrNull { it.channelId == channelId } ?: return null
        val target = ApplianceLaunchTarget(
            request = request,
            channelId = channel.channelId,
            channelName = channel.name,
        )
        val pending = ApplianceLaunchState.Pending(request)

        return if (transition(pending, ApplianceLaunchState.Entering(target), request.id)) {
            target
        } else {
            null
        }
    }

    @Synchronized
    fun cancel(expectedState: ApplianceLaunchState): Boolean {
        if (expectedState == ApplianceLaunchState.Idle) return false
        return transition(expectedState, ApplianceLaunchState.Idle, retainedRequestId = null)
    }

    fun isEntering(target: ApplianceLaunchTarget): Boolean =
        _state.value == ApplianceLaunchState.Entering(target)

    @Synchronized
    fun completePlayerVisibility(
        target: ApplianceLaunchTarget,
        channelId: Int,
        channelName: String,
    ): Boolean {
        if (!target.matchesPlayer(channelId, channelName)) return false
        return transition(
            ApplianceLaunchState.Entering(target),
            ApplianceLaunchState.Idle,
            retainedRequestId = null,
        )
    }

    private fun transition(
        expectedState: ApplianceLaunchState,
        newState: ApplianceLaunchState,
        retainedRequestId: Long?,
    ): Boolean {
        if (!_state.compareAndSet(expectedState, newState)) return false
        onRetainedRequestIdChanged(retainedRequestId)
        return true
    }
}
