package at.bernhardberger.tvhplayer.settings

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import at.bernhardberger.tvheadend.sdk.core.StreamProfile
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvhplayer.playback.StartupBufferVerdict.CLEAN
import at.bernhardberger.tvhplayer.playback.StartupBufferVerdict.TROUBLE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSettingsStoreTest {
    @Test
    fun keepChannelDefaultsToTwentyAndPersistsEveryChoice() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val store = PlayerSettingsStore(dataStore)
        assertEquals(20, store.playerSettings.first().keepChannelMinutes)
        for (minutes in listOf(0, 10, 20, 30)) {
            store.setKeepChannelMinutes(minutes)
            assertEquals(minutes, PlayerSettingsStore(dataStore).playerSettings.first().keepChannelMinutes)
        }
    }

    @Test
    fun invalidKeepChannelValueUsesDefault() = runTest {
        val store = PlayerSettingsStore(InMemoryPreferencesDataStore(preferencesOf(
            androidx.datastore.preferences.core.intPreferencesKey("keepChannelMinutes") to -1,
        )))
        assertEquals(20, store.playerSettings.first().keepChannelMinutes)
    }

    @Test
    fun startupBufferDefaultsToAutomaticAndPersistsEveryChoice() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val store = PlayerSettingsStore(dataStore)
        assertEquals(STARTUP_BUFFER_AUTOMATIC, store.playerSettings.first().startupBufferMillis)
        for (millis in STARTUP_BUFFER_FIXED_MILLIS + STARTUP_BUFFER_AUTOMATIC) {
            store.setStartupBufferMillis(millis)
            assertEquals(millis, PlayerSettingsStore(dataStore).playerSettings.first().startupBufferMillis)
        }
    }

    @Test
    fun invalidStartupBufferValueUsesAutomatic() = runTest {
        val store = PlayerSettingsStore(InMemoryPreferencesDataStore(preferencesOf(
            androidx.datastore.preferences.core.intPreferencesKey("startupBufferMillis") to 700,
        )))
        assertEquals(STARTUP_BUFFER_AUTOMATIC, store.playerSettings.first().startupBufferMillis)
    }

    @Test
    fun startupBufferLearningRoundTripsWithoutChangingPlayerSettings() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val store = PlayerSettingsStore(dataStore)
        val settings = store.playerSettings.first()
        assertEquals(StartupBufferLearningState(), store.startupBufferLearning.first())
        val learned = StartupBufferLearningState("server-a", 2500, listOf(TROUBLE, CLEAN, CLEAN))
        store.updateStartupBufferLearning { learned }
        assertEquals(learned, PlayerSettingsStore(dataStore).startupBufferLearning.first())
        assertEquals(settings, store.playerSettings.first())
    }

    @Test
    fun startupBufferLearningFromEarlierRulesRestartsAtHalfASecond() = runTest {
        for (policy in listOf(null, 1)) {
            val stored = mutablePreferencesOf(
                stringPreferencesKey("startupBufferLearnedProfile") to "server-a",
                intPreferencesKey("startupBufferLearnedMillis") to 2000,
                intPreferencesKey("startupBufferCleanWindows") to 7,
            )
            if (policy != null) stored[intPreferencesKey("startupBufferLearningPolicy")] = policy
            val dataStore = InMemoryPreferencesDataStore(stored)
            val store = PlayerSettingsStore(dataStore)
            assertEquals(StartupBufferLearningState("server-a", 500), store.startupBufferLearning.first())

            // The next verdict stores the current rules, so the reset happens once.
            store.updateStartupBufferLearning { it.copy(levelMillis = 1000, recentVerdicts = listOf(TROUBLE)) }
            assertEquals(StartupBufferLearningState("server-a", 1000, listOf(TROUBLE)),
                PlayerSettingsStore(dataStore).startupBufferLearning.first())
        }
    }

    @Test
    fun invalidRecentVerdictsDecodeAsAnEmptyHistory() = runTest {
        for (verdicts in listOf(null, "", "TX", "t", "TCCCCC")) {
            val stored = mutablePreferencesOf(
                stringPreferencesKey("startupBufferLearnedProfile") to "server-a",
                intPreferencesKey("startupBufferLearnedMillis") to 1500,
                intPreferencesKey("startupBufferLearningPolicy") to 2,
            )
            if (verdicts != null) stored[stringPreferencesKey("startupBufferRecentVerdicts")] = verdicts
            val store = PlayerSettingsStore(InMemoryPreferencesDataStore(stored))
            assertEquals(StartupBufferLearningState("server-a", 1500), store.startupBufferLearning.first())
        }
    }

    @Test
    fun audioPassthroughDefaultsOnAndPersistsBothChoices() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val store = PlayerSettingsStore(dataStore)
        assertTrue(store.playerSettings.first().audioPassthroughEnabled)
        store.setAudioPassthroughEnabled(false)
        assertFalse(PlayerSettingsStore(dataStore).playerSettings.first().audioPassthroughEnabled)
        store.setAudioPassthroughEnabled(true)
        assertTrue(PlayerSettingsStore(dataStore).playerSettings.first().audioPassthroughEnabled)
    }

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
