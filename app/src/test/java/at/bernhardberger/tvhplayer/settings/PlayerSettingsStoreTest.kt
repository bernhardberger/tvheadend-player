package at.bernhardberger.tvhplayer.settings

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import at.bernhardberger.tvheadend.sdk.core.StreamProfile
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerSettingsStoreTest {
    private val profileUuidKey = stringPreferencesKey("profileUuid")
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
    fun resolvingThePersistedSelectionIsReadOnly() = runTest {
        val dataStore = InMemoryPreferencesDataStore(
            initial = preferencesOf(profileUuidKey to passId.value),
            beforeUpdate = { error("Discovery must not rewrite preferences") },
        )
        assertEquals(
            passId,
            PlayerSettingsStore(dataStore).resolveStreamProfileSelection(profiles) { true },
        )
    }

    @Test
    fun expiredObservationCannotReturnSelectionAfterARead() = runTest {
        var current = true
        val base = InMemoryPreferencesDataStore(preferencesOf(profileUuidKey to passId.value))
        val data = object : DataStore<Preferences> by base {
            override val data = flow {
                current = false
                emit(base.data.first())
            }
        }
        assertNull(PlayerSettingsStore(data).resolveStreamProfileSelection(profiles) { current })
    }

    @Test
    fun stalePersistedUuidRemainsDurableButInactiveUntilRediscovered() = runTest {
        val dataStore = InMemoryPreferencesDataStore(
            preferencesOf(
                profileUuidKey to staleId.value,
            ),
        )
        val store = PlayerSettingsStore(dataStore)

        assertNull(store.resolveStreamProfileSelection(profiles) { true })
        val stale = dataStore.data.first()
        assertEquals(staleId.value, stale[profileUuidKey])

        assertEquals(
            staleId,
            store.resolveStreamProfileSelection(
                profiles + StreamProfile(staleId, "restored", ""),
            ) { true },
        )
    }
}
