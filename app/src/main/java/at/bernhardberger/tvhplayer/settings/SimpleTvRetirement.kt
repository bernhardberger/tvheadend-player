package at.bernhardberger.tvhplayer.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences

internal fun simpleTvRetirementMigration() = object : DataMigration<Preferences> {
    private val retiredKeys = setOf(
        "simpleTv.enabled", "simpleTv.timeshift", "simpleTv.pinSalt", "simpleTv.pinHash",
        "simpleTv.epg", "simpleTv.recordings", "simpleTv.stop", "simpleTv.settings", "simpleTv.appExit",
    )

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.asMap().keys.any { it.name in retiredKeys }

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            // Retirement never derives ordinary autoplay from the old mode.
            currentData.asMap().keys.filter { it.name in retiredKeys }.forEach { remove(it) }
        }

    override suspend fun cleanUp() = Unit
}
