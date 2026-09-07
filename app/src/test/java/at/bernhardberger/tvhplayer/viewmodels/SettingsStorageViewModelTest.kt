package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModelStore
import at.bernhardberger.tvheadend.sdk.core.CacheStatistics
import at.bernhardberger.tvheadend.sdk.core.SessionCache
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.cacheSizeMegabytes
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStorageViewModelTest {
    @Test
    fun acceptedClearCompletesWhenSettingsViewModelIsDestroyed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val sdkCache = FakeTvheadendSession().cache
            sdkCache.scriptStatistics(CacheStatistics(1, 2, 3))
            val release = CompletableDeferred<Unit>()
            val cache = object : SessionCache by sdkCache {
                override suspend fun clear() {
                    release.await()
                    sdkCache.clear()
                }
            }
            val model = SettingsStorageViewModel(cache)
            val store = ViewModelStore().apply { put("storage", model) }
            model.clearCache()
            runCurrent()
            store.clear()
            release.complete(Unit)
            runCurrent()
            assertEquals(CacheStatistics.EMPTY, sdkCache.statistics.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun formatsCombinedBytesWithLocaleAndWithoutLongOverflow() {
        assertEquals("0.0", cacheSizeMegabytes(CacheStatistics.EMPTY, Locale.US))
        assertEquals("12.5", cacheSizeMegabytes(CacheStatistics(500_000, 12_000_000, 340), Locale.US))
        assertEquals("12,5", cacheSizeMegabytes(CacheStatistics(500_000, 12_000_000, 340), Locale.GERMAN))
        assertEquals("18446744073709.6", cacheSizeMegabytes(CacheStatistics(Long.MAX_VALUE, Long.MAX_VALUE, 0), Locale.US))
    }

    @Test
    fun clearUsesSdkCacheStatisticsAndConfirmationExpires() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val cache = FakeTvheadendSession().cache
            cache.scriptStatistics(CacheStatistics(500_000, 12_000_000, 340))
            val model = SettingsStorageViewModel(cache)
            assertEquals(340, model.statistics.value.artworkEntryCount)
            model.clearCache()
            assertEquals(CacheClearState.CLEARING, model.clearState.value)
            runCurrent()
            assertEquals(CacheStatistics.EMPTY, model.statistics.value)
            assertEquals(CacheClearState.CLEARED, model.clearState.value)
            advanceTimeBy(4_000)
            runCurrent()
            assertEquals(CacheClearState.IDLE, model.clearState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun repeatedActivationIsCoalescedAndFailureCanBeRetried() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val sdkCache = FakeTvheadendSession().cache
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val cache = object : SessionCache by sdkCache {
                override suspend fun clear() {
                    calls++
                    if (calls == 1) {
                        release.await()
                        throw IOException("synthetic failure")
                    }
                    sdkCache.clear()
                }
            }
            val model = SettingsStorageViewModel(cache)
            model.clearCache()
            runCurrent()
            model.clearCache()
            assertEquals(1, calls)
            release.complete(Unit)
            runCurrent()
            assertEquals(CacheClearState.FAILED, model.clearState.value)
            model.clearCache()
            runCurrent()
            assertEquals(2, calls)
            assertEquals(CacheClearState.CLEARED, model.clearState.value)
            advanceTimeBy(2_000)
            model.clearCache()
            runCurrent()
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(CacheClearState.CLEARED, model.clearState.value)
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(CacheClearState.IDLE, model.clearState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
