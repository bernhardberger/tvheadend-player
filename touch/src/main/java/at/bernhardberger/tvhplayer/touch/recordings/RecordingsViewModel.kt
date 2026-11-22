package at.bernhardberger.tvhplayer.touch.recordings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class RecordingsViewModel(
    session: TvheadendSession,
    private val viewStore: RecordingsViewStore,
    private val applicationScope: CoroutineScope,
) : ViewModel() {
    val observation: StateFlow<SessionObservation> = session.observation
    private val mutableView = MutableStateFlow<DvrArchiveView?>(null)
    val view: StateFlow<DvrArchiveView?> = mutableView.asStateFlow()

    init {
        viewModelScope.launch { mutableView.value = viewStore.read() }
    }

    fun setView(view: DvrArchiveView) {
        if (mutableView.value == null) return
        mutableView.value = view
        // The disk write should finish even if the activity closes.
        applicationScope.launch {
            try {
                viewStore.write(view)
            } catch (_: IOException) {
                Log.w("RecordingsViewModel", "Could not save the recording view options")
            }
        }
    }
}
