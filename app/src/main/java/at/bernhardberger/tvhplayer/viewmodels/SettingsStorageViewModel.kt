package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.SessionCache
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.notifications.AppNoticeKind
import at.bernhardberger.tvhplayer.ui.notifications.AppNoticeQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CacheClearState { IDLE, CLEARING, CLEARED, FAILED }

class SettingsStorageViewModel(private val cache: SessionCache, private val notices: AppNoticeQueue) : ViewModel() {
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
                    mutableClearState.value = CacheClearState.CLEARED
                    notices.post("cache-clear", R.string.cache_notice_cleared, AppNoticeKind.SUCCESS, context)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableClearState.value = CacheClearState.FAILED
                    notices.post("cache-clear", R.string.cache_notice_failed, AppNoticeKind.FAILURE, context)
                }
            }
            delay(4_000)
            mutableClearState.compareAndSet(CacheClearState.CLEARED, CacheClearState.IDLE)
        }
    }
}
