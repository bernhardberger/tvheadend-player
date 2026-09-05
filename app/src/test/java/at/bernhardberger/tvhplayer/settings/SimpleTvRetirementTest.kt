package at.bernhardberger.tvhplayer.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleTvRetirementTest {
    @Test
    fun retirementRemovesOnlyModeKeysAndPreservesAutoplayOffOnAndAbsent() = runTest {
        for (autoplay in listOf(false, true, null)) {
            for (enabled in listOf(false, true)) {
                val ordinary = preferencesOf(
                    stringPreferencesKey("visibleChannelScopes") to "all",
                    stringPreferencesKey("audioLanguage") to "de",
                    booleanPreferencesKey("showEpgMenu") to false,
                ).toMutablePreferences().apply {
                    if (autoplay != null) this[booleanPreferencesKey("autoStartPlayback")] = autoplay
                }
                val previous = ordinary.toMutablePreferences().apply {
                    this[booleanPreferencesKey("simpleTv.enabled")] = enabled
                    this[booleanPreferencesKey("simpleTv.timeshift")] = true
                    this[stringPreferencesKey("simpleTv.pinSalt")] = "fictional-test-salt"
                    this[stringPreferencesKey("simpleTv.pinHash")] = "fictional-test-hash"
                    for (key in listOf("epg", "recordings", "stop", "settings", "appExit")) {
                        this[booleanPreferencesKey("simpleTv.$key")] = true
                    }
                }
                val migration = simpleTvRetirementMigration()
                assertTrue(migration.shouldMigrate(previous))
                val retired = migration.migrate(previous)
                assertEquals(ordinary, retired)
                assertFalse(migration.shouldMigrate(retired))
                assertEquals(retired, migration.migrate(retired))
            }
        }
    }
}
