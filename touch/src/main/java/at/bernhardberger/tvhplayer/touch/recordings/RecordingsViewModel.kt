package at.bernhardberger.tvhplayer.touch.recordings

import androidx.lifecycle.ViewModel
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Recordings over the session's DVR entries. The view options last for the app's session: the
 * TV keeps them in its own `UiSettingsStore`, which is not shared.
 */
internal class RecordingsViewModel(session: TvheadendSession) : ViewModel() {
    val observation: StateFlow<SessionObservation> = session.observation
    private val mutableView = MutableStateFlow(DvrArchiveView())
    val view: StateFlow<DvrArchiveView> = mutableView.asStateFlow()

    fun setView(view: DvrArchiveView) {
        mutableView.value = view
    }
}
