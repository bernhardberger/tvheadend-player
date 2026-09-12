package at.bernhardberger.tvhplayer.settings

import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvhplayer.core.ChannelScopeVisibility
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
class SettingsPersistenceTest {
    @Test
    fun currentTagSettingsRestoreLosslesslyIncludingTheFullUnsignedRange() = runTest {
        val data = InMemoryPreferencesDataStore()
        assertEquals(ChannelTagPreferences(), ChannelTagSettingsStore(data).settings.first())
        val tag = ChannelTagId(UInt.MAX_VALUE.toLong())
        val expected = ChannelTagPreferences(
            activeTagId = tag,
            visibility = ChannelScopeVisibility(
                configured = true,
                allChannelsVisible = false,
                visibleTagIds = setOf(tag, ChannelTagId(1L shl 31)),
            ),
        )
        ChannelTagSettingsStore(data).save(expected)
        assertEquals(expected, ChannelTagSettingsStore(data).settings.first())
    }

    @Test
    fun persistedIdsRejectValuesOutsideTheSdkUnsignedU32Domain() {
        assertEquals(null, persistedIdToLongOrNull("-1"))
        assertEquals(null, persistedIdToLongOrNull(Int.MIN_VALUE.toString()))
        assertEquals(null, persistedIdToLongOrNull("-2147483649"))
        assertEquals(null, persistedIdToLongOrNull("4294967296"))
        assertEquals(null, persistedIdToLongOrNull(Long.MAX_VALUE.toString()))
        assertEquals(null, sdkU32IdOrNull(-1L))
        assertEquals(null, sdkU32IdOrNull(4_294_967_296L))
        assertEquals(UInt.MAX_VALUE.toLong(), sdkU32IdOrNull(UInt.MAX_VALUE.toLong()))
        assertEquals(0L, persistedIdToLongOrNull("0"))
        assertEquals(1L shl 31, persistedIdToLongOrNull("2147483648"))
        assertEquals(UInt.MAX_VALUE.toLong(), persistedIdToLongOrNull("4294967295"))
        assertEquals(null, persistedIdToLongOrNull("not-an-id"))
    }
}
