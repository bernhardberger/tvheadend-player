package at.bernhardberger.tvhplayer.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared by both players for this process only; deliberately not a saved preference. */
class PlayerStatsStore {
    private val _visible = MutableStateFlow(false)
    val visible = _visible.asStateFlow()

    fun setVisible(visible: Boolean) {
        _visible.value = visible
    }
}
