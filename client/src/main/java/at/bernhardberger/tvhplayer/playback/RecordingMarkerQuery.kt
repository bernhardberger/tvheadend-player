@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.core.DvrCutpoint
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointsResult
import at.bernhardberger.tvheadend.sdk.core.PlaybackBinding
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Optional metadata for the installed binding; cancellation alone is not a stale-reply fence. */
internal class RecordingMarkerQuery {
    private var binding: PlaybackBinding.Recording? = null
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()
    private val _cutpoints = MutableStateFlow<List<DvrCutpoint>>(emptyList())
    val cutpoints = _cutpoints.asStateFlow()

    fun use(value: PlaybackBinding.Recording?) {
        _revision.value++
        binding = value
        _cutpoints.value = emptyList()
    }

    fun isCurrent(value: PlaybackBinding.Recording): Boolean = binding === value && when (value.admission) {
        is RecordingPlaybackAdmission.Completed, is RecordingPlaybackAdmission.GrowingStartOverOnly -> true
        else -> false
    }

    fun isCurrent(): Boolean = binding?.let(::isCurrent) == true

    fun retire(value: PlaybackBinding.Recording) {
        if (binding === value) use(null)
    }

    suspend fun refresh(value: PlaybackBinding.Recording) {
        if (!isCurrent(value)) return
        val expectedRevision = revision.value
        val result = try {
            value.cutpoints()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Optional metadata must never interrupt playback or expose a raw server error.
            null
        }
        currentCoroutineContext().ensureActive()
        if (expectedRevision == revision.value && isCurrent(value)) {
            _cutpoints.value = (result as? DvrCutpointsResult.Available)?.cutpoints.orEmpty()
        }
    }
}
