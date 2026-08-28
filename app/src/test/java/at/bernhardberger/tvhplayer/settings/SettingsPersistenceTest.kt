package at.bernhardberger.tvhplayer.settings

import androidx.datastore.preferences.core.preferencesOf
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
class SettingsPersistenceTest {
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
