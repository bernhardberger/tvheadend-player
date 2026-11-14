package at.bernhardberger.tvhplayer.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlayerStatsLevel {
    OFF, STREAM_PATH, DEBUG;

    val enabled: Boolean get() = this != OFF
}

/** Shared by both players for this process only; deliberately not a saved preference. */
class PlayerStatsStore {
    private val _level = MutableStateFlow(PlayerStatsLevel.OFF)
    val level = _level.asStateFlow()

    fun setLevel(level: PlayerStatsLevel) {
        _level.value = level
    }
}
