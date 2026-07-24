package at.bernhardberger.tvhplayer.stores

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SimpleTvSession {
    private val _unlocked = MutableStateFlow(false)
    val unlocked = _unlocked.asStateFlow()

    fun unlock() {
        _unlocked.value = true
    }

    fun lock() {
        _unlocked.value = false
    }
}
