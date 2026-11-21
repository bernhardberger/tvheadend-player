package at.bernhardberger.tvhplayer.touch.testutil

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.notices.ConnectionNoticeSource
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.touch.guide.GuideViewModel
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsViewModel
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The app's session and profile owner on the SDK's fakes, as the composition root builds them.
 * Everything runs on the main looper, so Robolectric idling settles it; [close] after each test.
 * [readForEditing] is what the editor reads for the connection in use (the fake store has no
 * editing read of its own).
 */
@OptIn(TvheadendTestingApi::class)
internal class FakeConnection(
    readForEditing: ServerProfileEditReadResult = ServerProfileEditReadResult.Missing,
) {
    val session = FakeTvheadendSession()
    val store = FakeServerProfileStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val owner = AppProfileOwner(
        session, store, PlayerSettingsStore(MemoryPreferences()), Dispatchers.Main,
        { readForEditing },
    )
    private val notices = ConnectionNoticeSource(
        session.observation, owner.configurationGeneration,
        NoticeCenter({ 0L }) { NoticeContext(owner.configurationGeneration.value, null) },
        owner.switchInProgress,
    )

    /** Starts the owner, which reads the saved connection as the app does at start. */
    fun start() = apply {
        scope.launch { owner.run() }
        scope.launch { notices.run() }
    }

    /** The connection state the app shows, as the composition root's view model gives it. */
    fun presentation() = AppConnectionViewModel(session, owner, notices)

    /** The Channels destination's state, as the composition root's view model gives it. */
    fun channels() = ChannelsViewModel(session, ChannelTagSettingsStore(MemoryPreferences()), owner.liveProfile)

    /** The Recordings destination's state, as the composition root's view model gives it. */
    fun recordings() = RecordingsViewModel(session)

    /** The Guide on Main, so a coverage request settles with the looper. */
    fun guide() = GuideViewModel(session, EmptyCoroutineContext)

    fun close() = scope.cancel()
}

private class MemoryPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(data.value).also { data.value = it }
}
