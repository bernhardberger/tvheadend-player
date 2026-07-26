package at.bernhardberger.tvhplayer.stores

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SimpleTvSession {
    private val _active = MutableStateFlow(false)
    val active = _active.asStateFlow()

    fun start() {
        _active.value = true
    }

    fun exit() {
        _active.value = false
    }
}
