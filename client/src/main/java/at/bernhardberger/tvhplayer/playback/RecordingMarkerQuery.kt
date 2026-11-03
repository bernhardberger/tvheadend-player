@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.core.DvrCutpoint
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointsResult
import at.bernhardberger.tvheadend.sdk.core.PlaybackBinding
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Optional metadata for the installed binding; cancellation alone is not a stale-reply fence. */
internal class RecordingMarkerQuery {
    private var binding: PlaybackBinding.Recording? = null
    // Metadata revisions dismiss navigation, but must not invalidate a pending cutpoint fetch.
    private var installation = 0L
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()
    private val _cutpoints = MutableStateFlow<List<DvrCutpoint>>(emptyList())
    val cutpoints = _cutpoints.asStateFlow()
    private val _chapterStartsMs = MutableStateFlow<List<Long>>(emptyList())
    val chapterStartsMs = _chapterStartsMs.asStateFlow()

    fun use(value: PlaybackBinding.Recording?) {
        installation++
        binding = value
        _cutpoints.value = emptyList()
        _chapterStartsMs.value = emptyList()
        _revision.value++
    }

    fun updateChapters(startsMs: List<Long>) {
        if (!isCurrent() || startsMs == _chapterStartsMs.value) return
        _chapterStartsMs.value = startsMs
        _revision.value++
    }

    @OptIn(ExperimentalTvheadendApi::class)
    fun isCurrent(value: PlaybackBinding.Recording): Boolean = binding === value && when (value.admission) {
        is RecordingPlaybackAdmission.Completed, is RecordingPlaybackAdmission.Growing -> true
        else -> false
    }

    fun isCurrent(): Boolean = binding?.let(::isCurrent) == true

    fun retire(value: PlaybackBinding.Recording) {
        if (binding === value) use(null)
    }

    suspend fun refresh(value: PlaybackBinding.Recording) {
        if (!isCurrent(value)) return
        val expectedInstallation = installation
        val result = try {
            value.cutpoints()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Optional metadata must never interrupt playback or expose a raw server error.
            null
        }
        currentCoroutineContext().ensureActive()
        if (expectedInstallation == installation && isCurrent(value)) {
            val points = (result as? DvrCutpointsResult.Available)?.cutpoints.orEmpty()
            if (points != _cutpoints.value) {
                _cutpoints.value = points
                _revision.value++
            }
        }
    }
}
