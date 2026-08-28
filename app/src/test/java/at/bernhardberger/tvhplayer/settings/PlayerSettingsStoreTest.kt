package at.bernhardberger.tvhplayer.settings

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.StreamProfile
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerSettingsStoreTest {
    private val profileUuidKey = stringPreferencesKey("profileUuid")
    private val legacyProfileNameKey = stringPreferencesKey("profile")
    private val directId = StreamProfileId("11111111111111111111111111111111")
    private val passId = StreamProfileId("22222222222222222222222222222222")
    private val staleId = StreamProfileId("33333333333333333333333333333333")
    private val profiles = listOf(
        StreamProfile(directId, "htsp", ""),
        StreamProfile(passId, "pass", ""),
    )

    @Test
    fun serverDefaultAndExplicitSelectionPersistWithoutAFirstProfileFallback() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val store = PlayerSettingsStore(dataStore)

        assertNull(store.resolveStreamProfileSelection(profiles) { true })
        store.setStreamProfile(passId)
        assertEquals(
            passId,
            PlayerSettingsStore(dataStore).resolveStreamProfileSelection(profiles) { true },
        )

        store.setStreamProfile(null)
        assertNull(PlayerSettingsStore(dataStore).resolveStreamProfileSelection(profiles) { true })
        assertFalse(dataStore.data.first().contains(profileUuidKey))
    }

    @Test
    fun oneExactCaseSensitiveLegacyNameMigratesOnceToTheReleasedId() = runTest {
        val dataStore = InMemoryPreferencesDataStore(
            preferencesOf(legacyProfileNameKey to "pass"),
        )

        assertEquals(
            passId,
            PlayerSettingsStore(dataStore).resolveStreamProfileSelection(profiles) { true },
        )
        val persisted = dataStore.data.first()
        assertEquals(passId.value, persisted[profileUuidKey])
        assertFalse(persisted.contains(legacyProfileNameKey))
    }

    @Test
    fun duplicateAndNonExactLegacyNamesAreConsumedWithoutSelecting() = runTest {
        val duplicateData = InMemoryPreferencesDataStore(
            preferencesOf(legacyProfileNameKey to "pass"),
        )
        val duplicates = profiles + StreamProfile(staleId, "pass", "")
        assertNull(
            PlayerSettingsStore(duplicateData).resolveStreamProfileSelection(duplicates) { true },
        )
        assertFalse(duplicateData.data.first().contains(legacyProfileNameKey))

        val nonExactData = InMemoryPreferencesDataStore(
            preferencesOf(legacyProfileNameKey to "PASS"),
        )
        assertNull(
            PlayerSettingsStore(nonExactData).resolveStreamProfileSelection(profiles) { true },
        )
        assertFalse(nonExactData.data.first().contains(legacyProfileNameKey))
    }

    @Test
    fun stalePersistedUuidRemainsDurableButInactiveUntilRediscovered() = runTest {
        val dataStore = InMemoryPreferencesDataStore(
            preferencesOf(
                profileUuidKey to staleId.value,
                legacyProfileNameKey to "pass",
            ),
        )
        val store = PlayerSettingsStore(dataStore)

        assertNull(store.resolveStreamProfileSelection(profiles) { true })
        val stale = dataStore.data.first()
        assertEquals(staleId.value, stale[profileUuidKey])
        assertFalse(stale.contains(legacyProfileNameKey))

        assertEquals(
            staleId,
            store.resolveStreamProfileSelection(
                profiles + StreamProfile(staleId, "restored", ""),
            ) { true },
        )
    }
}
