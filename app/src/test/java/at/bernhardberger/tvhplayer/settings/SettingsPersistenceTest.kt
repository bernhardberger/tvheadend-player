package at.bernhardberger.tvhplayer.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
class SettingsPersistenceTest {
    private val profileUuidKey = stringPreferencesKey("profileUuid")
    private val legacyProfileNameKey = stringPreferencesKey("profile")
    @Test
    fun legacyOnlyEvidenceLoadsWithoutClaimingUuidMigration() {
        val settings = playerSettingsFromPreferences(preferencesOf(legacyProfileNameKey to "pass"))
        assertEquals("", settings.profile)
        assertEquals("pass", settings.legacyProfileName)
    }
    @Test
    fun profileSelectionWritesUuidAndExactLegacyName() {
        val preferences = mutablePreferencesOf()
        preferences.writeProfileSelection(profileUuid = "uuid-pass", legacyProfileName = " Pass Profile ")
        assertEquals("uuid-pass", preferences[profileUuidKey])
        assertEquals(" Pass Profile ", preferences[legacyProfileNameKey])
    }
    @Test
    fun legacyAndPersistedIdsRoundTripAsUnsignedU32() = runBlocking {
        val migrated = activeTagIdMigration().migrate(preferencesOf(legacyActiveTagKey to -1))
        assertEquals(UInt.MAX_VALUE.toLong(), migrated[activeTagKey])
        assertEquals(1L shl 31, persistedIdToLongOrNull(Int.MIN_VALUE.toString()))
        assertEquals(null, persistedIdToLongOrNull("-2147483649"))
        val persistedTag = persistedTagScope(ChannelTagId(UInt.MAX_VALUE.toLong()))
        assertEquals("tag:4294967295", persistedTag)
        assertEquals(UInt.MAX_VALUE.toLong(), persistedIdToLongOrNull(persistedTag.removePrefix("tag:")))
    }

    @Test
    fun persistedIdsRejectValuesOutsideTheSdkUnsignedU32Domain() {
        assertEquals(null, persistedIdToLongOrNull("-2147483649"))
        assertEquals(null, persistedIdToLongOrNull("4294967296"))
        assertEquals(null, persistedIdToLongOrNull(Long.MAX_VALUE.toString()))
        assertEquals(null, sdkU32IdOrNull(-1L))
        assertEquals(null, sdkU32IdOrNull(4_294_967_296L))
        assertEquals(UInt.MAX_VALUE.toLong(), sdkU32IdOrNull(UInt.MAX_VALUE.toLong()))
    }
}
