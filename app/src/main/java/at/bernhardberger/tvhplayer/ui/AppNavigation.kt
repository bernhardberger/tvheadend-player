package at.bernhardberger.tvhplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

@Serializable
internal sealed interface AppNavKey : NavKey

internal enum class AppDestination {
    CHANNELS,
    GUIDE,
    RECORDINGS,
    SETTINGS,
    UNLOCK,
    LIVE_PLAYER,
    RECORDING_PLAYER,
}

@Serializable
internal data object ChannelsKey : AppNavKey

@Serializable
internal data object GuideKey : AppNavKey

@Serializable
internal data object RecordingsKey : AppNavKey

@Serializable
internal data class SettingsKey(
    val section: SettingsSection = SettingsSection.GENERAL,
) : AppNavKey

@Serializable
internal data object UnlockKey : AppNavKey

@Serializable
internal data class LivePlayerKey(
    val channelId: Long,
    val channelName: String,
) : AppNavKey {
    init {
        require(channelId > 0) { "Live player channel ID must be positive" }
    }
}

@Serializable
internal data class RecordingPlayerKey(
    val recordingId: Long,
    val start: RecordingStartMode = RecordingStartMode.RESUME,
) : AppNavKey {
    init {
        require(recordingId > 0) { "Recording player ID must be positive" }
    }
}

@Serializable
internal enum class SettingsSection {
    GENERAL,
    OPTIONS,
    CHANNEL_TAGS,
    CONNECTION,
    PLAYER,
    APPLIANCE,
    SIMPLE_TV,
}

@Serializable
internal enum class RecordingStartMode {
    RESUME,
    START_OVER,
}

internal val AppNavKey.destination: AppDestination
    get() = when (this) {
        ChannelsKey -> AppDestination.CHANNELS
        GuideKey -> AppDestination.GUIDE
        RecordingsKey -> AppDestination.RECORDINGS
        is SettingsKey -> AppDestination.SETTINGS
        UnlockKey -> AppDestination.UNLOCK
        is LivePlayerKey -> AppDestination.LIVE_PLAYER
        is RecordingPlayerKey -> AppDestination.RECORDING_PLAYER
    }

@Composable
internal fun rememberAppNavBackStack(vararg elements: AppNavKey): NavBackStack<AppNavKey> =
    rememberSerializable(serializer = serializer()) {
        NavBackStack(*elements)
    }

internal fun MutableList<AppNavKey>.navigateTopLevel(destination: AppNavKey) {
    require(!destination.isTransientDestination())
    removeAll(AppNavKey::isTransientDestination)
    remove(destination)
    add(destination)
}

internal fun MutableList<AppNavKey>.pushTransient(destination: AppNavKey) {
    require(destination.isTransientDestination())
    if (lastOrNull() != destination) add(destination)
}

internal fun MutableList<AppNavKey>.replaceRoot(destination: AppNavKey) {
    clear()
    add(destination)
}

internal fun MutableList<AppNavKey>.popNavigation(): Boolean {
    if (size <= 1) return false
    removeAt(lastIndex)
    return true
}

internal fun List<AppNavKey>.lastSettingsSection(): SettingsSection? =
    filterIsInstance<SettingsKey>().lastOrNull()?.section

internal fun List<AppNavKey>.hasTransientDestinationBelowTop(): Boolean =
    dropLast(1).any(AppNavKey::isTransientDestination)

internal fun AppNavKey.isTransientDestination(): Boolean = when (this) {
    is LivePlayerKey,
    is RecordingPlayerKey,
    UnlockKey -> true
    ChannelsKey,
    GuideKey,
    RecordingsKey,
    is SettingsKey -> false
}
