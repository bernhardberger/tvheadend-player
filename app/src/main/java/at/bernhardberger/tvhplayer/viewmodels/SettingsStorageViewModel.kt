package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.SessionCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CacheClearState { IDLE, CLEARING, CLEARED, FAILED }

class SettingsStorageViewModel(private val cache: SessionCache) : ViewModel() {
    val statistics = cache.statistics
    private val mutableClearState = MutableStateFlow(CacheClearState.IDLE)
    val clearState = mutableClearState.asStateFlow()
    private var clearJob: Job? = null

    fun clearCache() {
        if (mutableClearState.value == CacheClearState.CLEARING) return
        clearJob?.cancel()
        mutableClearState.value = CacheClearState.CLEARING
        clearJob = viewModelScope.launch {
            try {
                // Finish the accepted disk operation even if Settings is closed meanwhile.
                withContext(NonCancellable) { cache.clear() }
                mutableClearState.value = CacheClearState.CLEARED
                delay(4_000)
                mutableClearState.compareAndSet(CacheClearState.CLEARED, CacheClearState.IDLE)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableClearState.value = CacheClearState.FAILED
            }
        }
    }
}
