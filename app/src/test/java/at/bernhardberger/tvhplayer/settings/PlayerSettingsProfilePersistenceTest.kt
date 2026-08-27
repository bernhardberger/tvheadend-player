package at.bernhardberger.tvhplayer.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSettingsProfilePersistenceTest {
    private val profileUuidKey = stringPreferencesKey("profileUuid")
    private val legacyProfileNameKey = stringPreferencesKey("profile")

    @Test
    fun legacyOnlyEvidenceLoadsWithoutClaimingUuidMigration() {
        val settings = playerSettingsFromPreferences(
            preferencesOf(legacyProfileNameKey to "pass"),
        )

        assertEquals("", settings.profile)
        assertEquals("pass", settings.legacyProfileName)
    }

    @Test
    fun profileSelectionWritesUuidAndExactLegacyName() {
        val preferences = mutablePreferencesOf()

        preferences.writeProfileSelection(
            profileUuid = "uuid-pass",
            legacyProfileName = " Pass Profile ",
        )

        assertEquals("uuid-pass", preferences[profileUuidKey])
        assertEquals(" Pass Profile ", preferences[legacyProfileNameKey])
    }

}
