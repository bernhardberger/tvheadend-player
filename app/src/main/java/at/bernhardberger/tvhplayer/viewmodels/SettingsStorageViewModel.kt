package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.SessionCache
import at.bernhardberger.tvhplayer.stores.ChannelAccentStore
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CacheClearState { IDLE, CLEARING, CLEARED, FAILED }

class SettingsStorageViewModel(
    private val cache: SessionCache,
    private val notices: NoticeCenter,
    /** Derived from cached picons, so cleared with them. */
    private val channelAccents: ChannelAccentStore,
) : ViewModel() {
    val statistics = cache.statistics
    private val mutableClearState = MutableStateFlow(CacheClearState.IDLE)
    val clearState = mutableClearState.asStateFlow()
    private var clearJob: Job? = null

    fun clearCache() {
        if (mutableClearState.value == CacheClearState.CLEARING) return
        clearJob?.cancel()
        mutableClearState.value = CacheClearState.CLEARING
        val context = notices.context()
        clearJob = viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    // Finish and publish the accepted outcome even after leaving Settings.
                    cache.clear()
                    channelAccents.clear()
                    mutableClearState.value = CacheClearState.CLEARED
                    notices.post(Notice.CacheClear(success = true), context)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableClearState.value = CacheClearState.FAILED
                    notices.post(Notice.CacheClear(success = false), context)
                }
            }
            delay(4_000)
            mutableClearState.compareAndSet(CacheClearState.CLEARED, CacheClearState.IDLE)
        }
    }
}
